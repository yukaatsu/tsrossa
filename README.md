<p align="center">
  <img src="screenshots/tsrossa_icon.png" alt="tsrossa App Icon" width="128"/>
</p>

<h1 align="center">tsrossa</h1>

<p align="center">
  <strong>Direct-to-DAC, Bit-Perfect USB Music Player for Android</strong><br/>
  Bypasses Android's audio framework to stream bit-perfect PCM straight to external USB DACs.
</p>

<p align="center">
  <a href="https://github.com/yukaatsu/tsrossa/releases"><img src="https://img.shields.io/badge/Release-v1.8.2--BETA-orange.svg" alt="Release"></a>
  <a href="#"><img src="https://img.shields.io/badge/Android-8.0%2B-2ea44f.svg" alt="Android Version"></a>
  <a href="#"><img src="https://img.shields.io/badge/Engine-C%2B%2B17%20%7C%20libusb-0052cc.svg" alt="Engine"></a>
  <a href="#"><img src="https://img.shields.io/badge/Audio-UAC1%20%2F%20UAC2-success.svg" alt="Audio Protocols"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg" alt="License"></a>
  <a href="https://bagibagi.co/Yukaatsu"><img src="https://img.shields.io/badge/Support-BagiBagi-FFDD00.svg?logo=buy-me-a-coffee&logoColor=black" alt="Support"></a>
</p>

> 💡 **Looking for the macOS desktop version?**  
> We keep each platform in its own dedicated branch. Switch to the [`macos` branch](https://github.com/yukaatsu/tsrossa/tree/macos) or run:  
> `git clone -b macos --single-branch https://github.com/yukaatsu/tsrossa.git tsrossa-mac`

---

## Why tsrossa?

If you plug a USB DAC or dongle into Android, the operating system routes all audio through `AudioTrack` and `AudioFlinger`. Even if your file is 44.1 kHz, 96 kHz, or 192 kHz, Android forces it through a software resampler (SRC) to a fixed 48 kHz, runs it through internal digital volume attenuation, and adds latency.

**tsrossa bypasses the Android audio stack entirely.**

It asks Android only for the raw USB permission (`usbfs` file descriptor). From that moment on, a native C++17 engine talks directly to your DAC via `libusb`, tells the physical DAC clock which crystal oscillator frequency to switch to, pre-loads the audio into RAM with POSIX `mlock()`, and sends the raw PCM stream over USB Isochronous endpoints.

What's in your file is bit-for-bit what reaches your DAC's converter chip. No resampling, no mixer, no OS interference.

---

## Screenshots

<table align="center">
  <tr>
    <td align="center" width="33%">
      <img src="screenshots/01_now_playing.png" alt="Now Playing Screen" width="100%"/>
      <br/><b>Now Playing</b><br/>
      <sub>Adaptive album art theme, live Audio Quality Badge, precision scrubber, 0 dBFS volume pill, and quick lyrics/DSP/queue controls</sub>
    </td>
    <td align="center" width="33%">
      <img src="screenshots/04_signal_path.png" alt="Signal Path Inspector" width="100%"/>
      <br/><b>Signal Path Inspector</b><br/>
      <sub>End-to-end live audit from source FLAC/WAV through native decoder, bit-perfect pass, UAC volume, and USB DAC clock</sub>
    </td>
    <td align="center" width="33%">
      <img src="screenshots/05_audio_dsp_studio.png" alt="Audio DSP Studio" width="100%"/>
      <br/><b>Audio DSP Studio</b><br/>
      <sub>64-bit 10-band Parametric EQ with live frequency graph, Auto-Clip preamp, preset export/import, and Dynamics Compressor</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="33%">
      <img src="screenshots/02_library.png" alt="Music Library" width="100%"/>
      <br/><b>Library & Folder Browser</b><br/>
      <sub>Fast cached browser with live DAC badge, instant search, All Songs / Folders tabs, and 11 sort modes</sub>
    </td>
    <td align="center" width="33%">
      <img src="screenshots/03_system_logs.png" alt="Hardware Telemetry" width="100%"/>
      <br/><b>Hardware Telemetry</b><br/>
      <sub>Live UAC1/UAC2 endpoint audit, supported rates up to 384 kHz, RAM/gapless engine status, and 1-tap diagnostic report export</sub>
    </td>
    <td align="center" width="33%">
      <img src="screenshots/06_settings.png" alt="Settings" width="100%"/>
      <br/><b>Settings & Customization</b><br/>
      <sub>Theme & glassmorphism surfaces, USB buffer profiles, playlist rules, permissions, and in-app updates</sub>
    </td>
  </tr>
</table>

---

## Highlights

- **Direct Kernel-Level USB Audio (`usbfs` + `libusb-1.0`)**  
  Zero `AudioTrack`, zero `AudioFlinger`. The C++17 engine manages asynchronous USB Isochronous transfers directly.
- **Physical Clock Switching (UAC1 & UAC2)**  
  Reads your DAC's USB descriptors to configure the hardware oscillator via `CS_SAMPLING_FREQ_CONTROL`. A 44.1 kHz track plays at 44.1 kHz; a 192 kHz track plays at 192 kHz.
- **Live Audio Quality Badge & Signal Path Inspector**  
  Tap the quality pill on Now Playing (`Hi-Res`, `Lossless`, `MQA`, `Float`) to inspect every stage of the live native pipeline—source format, decoder buffer, DSP/bit-perfect bypass status, volume stage, and active USB DAC clock.
- **64-bit Audio DSP Studio (Optional & Zero-Click Bypassable)**  
  - **10-Band Parametric EQ:** Peaking, Low Shelf, and High Shelf biquad filters with live response curve, automatic anti-clipping preamp, custom presets, and share-code / JSON export & import.
  - **Dual-Region Dynamics Compressor & Peak Limiter:** Real-time dynamics control with smooth bypass ramping—turning DSP off returns cleanly to 100% bit-perfect integer direct pass.
- **RAM Pre-Caching with Memory Pinning (`mlock`)**  
  Decodes audio files into a linear PCM buffer in RAM and locks the pages so the Linux kernel never swaps them to flash storage, eliminating micro-stutters.
- **Dual Volume Architecture**  
  - **Hardware UAC Mode:** Controls the DAC's internal volume registers via `FU_VOLUME_CONTROL`. At 100%, it engages bit-perfect True Unity Gain (0.0 dBFS).
  - **64-bit Studio-Taper DSP:** Calibrated logarithmic curve with decorrelated SplitMix64 TPDF dither and mid-tread quantization (`std::llrint`) for DACs without hardware mixers.
- **Gapless Playback**  
  Background worker decodes the upcoming track into a secondary RAM buffer and seamlessly hands over USB packets across track boundaries without pops or gaps.
- **Modern Material 3 Interface**  
  Built with Jetpack Compose. Dynamic color palettes extracted from album artwork, glassmorphic modal sheets, synchronized `.lrc` lyrics, folder browser & custom playlists, fast alphabet scroller, and a dedicated DAP (Screen Pinning) mode to turn older phones into dedicated audio transports.

---

## Format & Hardware Support

| Category | Details |
| :--- | :--- |
| **Supported Formats** | • **FLAC** (`.flac`): 16-bit, 24-bit, 32-bit PCM (44.1 kHz up to 384 kHz)<br/>• **WAV** (`.wav`, `.wave`): 16-bit, 24-bit, 32-bit Linear PCM, and 32-bit IEEE Float |
| **Target Hardware** | Any USB DAC connected via USB-C OTG (Dongle DACs, portable DAC/amps, desktop DACs) |
| **USB Class** | USB Audio Class 1.0 (UAC1) & USB Audio Class 2.0 (UAC2) |
| **Transfer Type** | Asynchronous and Adaptive Isochronous USB endpoints |
| **Android Version** | Android 8.0 (Oreo / API 26) through Android 15+ (API 35+) |

> **Note on Scope**: tsrossa is intentionally focused on bit-perfect offline playback for external USB DACs. Lossy formats (MP3, AAC) and internal phone speakers / Bluetooth are bypassed to keep the native audio pipeline clean and jitter-free.

---

## Quick Start

### 1. Download & Install
Grab the latest release `.apk` from the [Releases page](https://github.com/yukaatsu/tsrossa/releases) and install it on your Android device.

### 2. Connect Your DAC
1. Plug your USB DAC into your phone using an OTG cable or USB-C interconnect.
2. When Android shows the **"Allow tsrossa to access [Your DAC]?"** prompt, check *Always allow* and tap **OK**.
3. Pick a track from your library and enjoy bit-perfect playback.

### 3. Signal Path & Hardware Diagnostics
- Tap the **Audio Quality Badge** (`Hi-Res` / `Lossless`) on **Now Playing** to inspect the live end-to-end **Signal Path & Engine Audit**.
- Tap the **DAC status pill** in the top header to open **System Logs & Diagnostics** (supported sample rates, claimed USB interfaces, RAM/gapless engine status, and 1-tap report copy/share).

---

## Building from Source

If you prefer building tsrossa yourself:

### Prerequisites
- JDK 17 (e.g. OpenJDK 17)
- Android SDK (`compileSdk = 34`, `minSdk = 26`)
- Android NDK (r25+ / `25.1.8937393` or newer)
- CMake 3.22.1+

Make sure your `local.properties` points to your Android SDK:
```properties
sdk.dir=/path/to/your/Android/sdk
```

### Build Commands
```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk

# Run unit tests
./gradlew test
```

---

## Community & Support

tsrossa is an independent open-source project. If it brings your music to life or gives your portable setup new clarity, support is warmly appreciated:

[![Donate via BagiBagi](https://img.shields.io/badge/Donate-BagiBagi-FFDD00.svg?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black)](https://bagibagi.co/Yukaatsu)

Found a bug or have DAC compatibility data? Feel free to open an issue on [GitHub Issues](https://github.com/yukaatsu/tsrossa/issues) with your hardware log report attached!

---

## License

tsrossa is licensed under the [Apache License 2.0](LICENSE).
