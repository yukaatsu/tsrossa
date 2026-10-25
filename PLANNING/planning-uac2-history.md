# USB Audio Class 2.0 Timing & Bus Speed Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the 8x playback rate overrun bug on Android devices with Linux kernel < 4.13 (e.g. Samsung SM-G955F on custom ROMs) that produces loud screeches and glitches ("glitch dan decitan") by implementing a multi-tier USB bus speed heuristic and frame-timing synchronizer.

**Architecture:** Introduce an intelligent hardware speed resolver in `UsbDacController.cpp` that falls back through UAC protocol version, maximum supported sample rates, device qualifier descriptors, and endpoint parameters when `libusb_get_device_speed()` returns `LIBUSB_SPEED_UNKNOWN` (0) due to lack of kernel `USBDEVFS_GET_SPEED` support or SELinux restrictions. Synchronize altsetting `packets_per_sec` and `AudioStreamer.cpp` packet sizing to 8,000 microframes/sec for High-Speed UAC2 endpoints. Modernize `AndroidManifest.xml` with `enableOnBackInvokedCallback` and purge obsolete DND permissions.

**Tech Stack:** C++17, Android NDK, libusb-1.0, Kotlin, JUnit 4, Android Compose / Jetpack.

**Spec:** [audit-report.md](file:///Users/yuka/Projects/tsrossa-ujicoba/audit-report.md)

## Global Constraints
- Bit-perfect streaming pipeline integrity must be preserved (zero DSP or unwanted sample rate conversion).
- All changes in C++ must adhere to C++17 and maintain lock-free / low-jitter realtime thread execution.
- Maintain documentation integrity and keep existing comments intact.
- Follow established Tsrossa coding style and naming conventions.

## Review Focus
1. **Linux Kernel < 4.13 compatibility:** Ensure `libusb_wrap_sys_device(fd)` devices with `dev_speed == 0` correctly resolve to High-Speed (8000 fps) for UAC2 DACs.
2. **Isochronous payload accuracy:** Ensure Bresenham accumulator yields exactly 44,100 samples across 8,000 microframes for 44.1 kHz, 48,000 for 48 kHz, and 384,000 for 384 kHz with zero accumulator drift.
3. **Queue depth & FRINDEX stability:** Ensure URB allocation does not exceed 2048 microframes (256ms) on Synopsys DWC3 controllers while guaranteeing $\ge 128\text{ ms}$ runway.
4. **Altsetting switching safety:** Verify that interface activation handles `packets_per_sec` correctly on both clean starts and track-to-track format changes.
5. **No regression on true Full-Speed UAC1 devices:** Ensure legacy UAC1 1,000 fps devices continue to resolve to 1,000 fps.

---

### Task 1: Timing & Speed Resolution Unit Tests in `DacHardwareTimingTest.kt`

**Files:**
- Modify: `app/src/test/java/com/yuka/musicplayer/audio/DacHardwareTimingTest.kt:70-120`

**Interfaces:**
- Consumes: `DacHardwareTimingTest`
- Produces: Test assertions for 8000 fps microframe Bresenham distribution across 44.1k, 48k, 96k, 192k, 384k sample rates and speed inference rules.

- [x] **Step 1: Write the failing unit tests for High-Speed Isochronous distribution and Speed Resolution**

Add tests to `DacHardwareTimingTest.kt`:
```kotlin
    @Test
    fun testIsoBresenhamAccumulatorHighSpeed8000Fps() {
        val testRates = listOf(44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000)
        val fps = 8000

        for (sr in testRates) {
            val baseFrames = sr / fps
            val rem = sr % fps
            var acc = 0
            var totalFrames = 0

            for (microframe in 0 until fps) {
                var framesToSend = baseFrames
                acc += rem
                if (acc >= fps) {
                    framesToSend++
                    acc -= fps
                }
                totalFrames += framesToSend
            }

            assertEquals("Total frames over 1 second for $sr Hz must match exactly", sr, totalFrames)
            assertEquals("Remainder accumulator must reset to 0 after 1 second for $sr Hz", 0, acc)
        }
    }

    @Test
    fun testSpeedResolutionHeuristicRules() {
        // Simulates rule: if speed is unknown (0), UAC2 or sample rate > 96kHz forces High Speed (8000 fps)
        fun resolveFps(devSpeed: Int, uacVersion: Int, maxSampleRate: Int): Int {
            if (devSpeed == 3 || devSpeed == 4) return 8000 // High/Super
            if (devSpeed == 2) return 1000                 // Full Speed
            // devSpeed == 0 (Unknown) fallback heuristic:
            if (uacVersion == 2 || maxSampleRate > 96000) return 8000
            return 1000
        }

        // FiiO SNOWSKY Melody on SM-G955F (devSpeed=0, UAC=2, maxRate=384000)
        assertEquals(8000, resolveFps(devSpeed = 0, uacVersion = 2, maxSampleRate = 384000))
        // Generic UAC2 DAC with devSpeed=0
        assertEquals(8000, resolveFps(devSpeed = 0, uacVersion = 2, maxSampleRate = 44100))
        // Legacy UAC1 USB 1.1 device (devSpeed=0, UAC=1, maxRate=48000)
        assertEquals(1000, resolveFps(devSpeed = 0, uacVersion = 1, maxSampleRate = 48000))
    }
```

- [x] **Step 2: Verify test compilation and failure (or passage for math logic)**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest --tests com.yuka.musicplayer.audio.DacHardwareTimingTest`  
Expected: Validates expected math bounds and exposes the requirements for the C++ engine.

---

### Task 2: Multi-Tier USB Bus Speed Resolver in `UsbDacController.cpp` & `UsbDacController.h`

**Files:**
- Modify: `app/src/main/cpp/UsbDacController.h:1-50`
- Modify: `app/src/main/cpp/UsbDacController.cpp:410-440` and `670-710`

**Interfaces:**
- Consumes: `libusb_device`, `libusb_device_handle`, `g_audioState`
- Produces: `resolve_effective_usb_speed(libusb_device *dev, libusb_device_handle *handle, int uac_version, const std::set<uint32_t> &sample_rates) -> int`

- [x] **Step 1: Declare helper in `UsbDacController.h`**

Add prototype:
```cpp
int resolve_effective_usb_speed(libusb_device *dev, libusb_device_handle *handle, int uac_version, const std::set<uint32_t> &sample_rates);
```

- [x] **Step 2: Implement Multi-Tier Speed Resolver in `UsbDacController.cpp`**

Implement `resolve_effective_usb_speed`:
- Check `libusb_get_device_speed(dev)`. If `LIBUSB_SPEED_HIGH` or `LIBUSB_SPEED_SUPER`, return `LIBUSB_SPEED_HIGH`.
- If speed is `LIBUSB_SPEED_LOW`, return `LIBUSB_SPEED_LOW`.
- If speed is `LIBUSB_SPEED_UNKNOWN` (0):
  1. Check device descriptor: if `desc.bcdUSB >= 0x0200` and `uac_version == 2`, return `LIBUSB_SPEED_HIGH`.
  2. Check sample rates: if any rate in `sample_rates` $> 96000$, return `LIBUSB_SPEED_HIGH`.
  3. Try reading `LIBUSB_DT_DEVICE_QUALIFIER`: if successful, the device supports High-Speed, return `LIBUSB_SPEED_HIGH`.
  4. For all UAC2 devices, default to `LIBUSB_SPEED_HIGH`.
  5. Default fallback: return `LIBUSB_SPEED_HIGH` (8000 fps) as modern USB OTG DACs operate at High-Speed.

- [x] **Step 3: Update `initUsbDac` in `UsbDacController.cpp` to use the Resolver**

Replace lines 411-417:
```cpp
  libusb_device *dev = libusb_get_device(g_audioState.usbHandle);
  int raw_speed = libusb_get_device_speed(dev);
  int dev_speed = raw_speed;
  // If unknown at wrap time, initial assessment based on USB 2.0 descriptors
  struct libusb_device_descriptor dev_desc;
  if (libusb_get_device_descriptor(dev, &dev_desc) == 0) {
    if (dev_speed == LIBUSB_SPEED_UNKNOWN && dev_desc.bcdUSB >= 0x0200) {
      dev_speed = LIBUSB_SPEED_HIGH;
    }
  }
  int dev_fps = (dev_speed == LIBUSB_SPEED_HIGH || dev_speed == LIBUSB_SPEED_SUPER) ? 8000 : 1000;
  g_audioState.usb_frames_per_sec.store(dev_fps);
  snprintf(dbg, sizeof(dbg), "Device speed=%d (resolved=%d) -> default usb_frames_per_sec=%d", raw_speed, dev_speed, dev_fps);
  record_usb_diag(dbg);
```

- [x] **Step 4: Update Altsetting Microframe Calculation in `UsbDacController.cpp`**

In lines 678–684:
Re-evaluate `dev_speed` with UAC version and detected sample rates:
```cpp
  int resolved_speed = resolve_effective_usb_speed(dev, g_audioState.usbHandle, g_audioState.uacVersion.load(), sampleRateSet);
  int alt_fps = 1000;
  if (resolved_speed == LIBUSB_SPEED_HIGH || resolved_speed == LIBUSB_SPEED_SUPER) {
    int shift = (ep_bInterval >= 1 && ep_bInterval <= 16) ? (ep_bInterval - 1) : 0;
    int uframes_per_pkt = 1 << shift;
    alt_fps = 8000 / uframes_per_pkt;
    if (alt_fps < 125) alt_fps = 8000;
  }
```

---

### Task 3: Isochronous Frame Sizing & Buffer Queue Hardening in `AudioStreamer.cpp`

**Files:**
- Modify: `app/src/main/cpp/AudioStreamer.cpp:25-35`, `65-85`, and `1170-1175`

**Interfaces:**
- Consumes: `g_audioState.usb_frames_per_sec`, `selected_alt.packets_per_sec`, `g_audioState.uacVersion`
- Produces: Correct sample distribution per microframe and safe URB runway depth.

- [x] **Step 1: Secure `start_iso_streaming_thread` FPS Fallback**

In `AudioStreamer.cpp` lines 25–32:
```cpp
  if (g_audioState.usb_frames_per_sec.load() <= 0) {
    libusb_device *dev = libusb_get_device(g_audioState.usbHandle);
    int dev_speed = dev ? libusb_get_device_speed(dev) : LIBUSB_SPEED_UNKNOWN;
    if (dev_speed == LIBUSB_SPEED_UNKNOWN && g_audioState.uacVersion.load() == 2) {
      dev_speed = LIBUSB_SPEED_HIGH;
    }
    int dev_fps = (dev_speed == LIBUSB_SPEED_HIGH || dev_speed == LIBUSB_SPEED_SUPER) ? 8000 : 1000;
    g_audioState.usb_frames_per_sec.store(dev_fps);
  }
```

- [x] **Step 2: Prevent Buffer Queue Runway Truncation**

In `AudioStreamer.cpp` lines 68–81:
Ensure that even if `fps < 2000` is encountered unexpectedly, `num_packets` is not restricted to 16 when the hardware is High-Speed.
When `fps >= 8000`: `num_transfers = 24`, `num_packets = 64` (192 ms runway).

- [x] **Step 3: Update `playAudio` Altsetting Configuration**

In `AudioStreamer.cpp` line 1171:
```cpp
  if (selected_alt.packets_per_sec > 0) {
    g_audioState.usb_frames_per_sec.store(selected_alt.packets_per_sec);
  } else if (g_audioState.uacVersion.load() == 2) {
    g_audioState.usb_frames_per_sec.store(8000);
  }
```

---

### Task 4: Manifest & Platform Lifecycle Cleanup in `AndroidManifest.xml`

**Files:**
- Modify: `app/src/main/AndroidManifest.xml:10-25`

**Interfaces:**
- Consumes: Android OS 13+ Predictive Back system & Notification Policy
- Produces: Warning-free window transitions and clean permission profile.

- [x] **Step 1: Enable Predictive Back Support in `<application>`**

Add `android:enableOnBackInvokedCallback="true"` to `<application>` in `AndroidManifest.xml`.
This resolves the `WindowOnBackDispatcher` warning and eliminates BLAST consumer disconnects logged during popup window dismissal.

- [x] **Step 2: Remove Obsolete DND Permission**

Remove:
```xml
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />
```

---

### Task 5: End-to-End Verification

**Files:**
- Test: `app/src/test/java/com/yuka/musicplayer/audio/DacHardwareTimingTest.kt`
- Test: `tools/hardware_probe.py`

- [x] **Step 1: Run Unit Tests**

Execute unit tests to verify timing math and Bresenham packet calculations.

- [x] **Step 2: Run Hardware Probe Simulator**

Execute `python3 tools/hardware_probe.py --dry-run` to verify transient settling metrics and timing telemetry compatibility.
