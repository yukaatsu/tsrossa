# Universal DAC Volume & Unbounded Hi-Res Streaming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix Bug #1 (5% volume still blasting loud on USB dongle DACs like Kiwi Ears AD1 & M06Pro 2) by implementing a Unified Psychoacoustic Loudness Curve (`-64 dB` range with `-59.26 dB` at 5%) + Universal 64-Bit DSP / Verified Hybrid UAC Volume Engine, and fix Bug #2 (Hi-Res tracks cutting off and auto-advancing at ~70% duration / `218.69s`) by replacing silent `totalFrames` truncation (`MAX_TRACK_BUFFER_BYTES`) with a Lock-Free Sliding-Window Ring Buffer in `AudioStreamer.cpp`.

**Architecture:**
1. **Unified Psychoacoustic Volume & Hybrid Split (`AudioCommon.h`, `AudioEngineState.h`, `UsbDacController.cpp`, `AudioStreamer.cpp`, `VolumeModalSheet.kt`):**
   - Replace the linear-amplitude formula `20 * log10(v)` (`-26 dB` at 5%) with a unified psychoacoustic curve $\text{dB}(v) = -64.0 \times (1 - v)\sqrt{1 - v}$ (`-59.26 dB` at 5%, `-22.63 dB` at 50%, `0.0 dB` at 100%).
   - Fix the UAC1 `GET_MIN`/`GET_MAX`/`GET_RES` validation bug, add dual Master (`ch=0`) + Left/Right (`ch=1,2`) `SET_CUR` writes, add `GET_CUR` readback verification to automatically detect dummy Feature Units (removing the hardcoded `"celest"`/`"ruyi"` anti-pattern), and compute `residualSoftwareGain` so 64-bit TPDF-dithered DSP always covers any attenuation the DAC hardware cannot provide.
   - Default to **Universal 64-Bit DSP Volume Mode** (locks DAC hardware to `0 dB` unity gain and performs all attenuation in 64-bit float + TPDF dither), while adding a user-accessible toggle in `VolumeModalSheet` for **Hybrid UAC Hardware Volume**.
2. **Unbounded Sliding-Window Ring Buffer (`AudioCommon.h`, `AudioEngineState.h`, `AudioStreamer.cpp`):**
   - Never overwrite `totalFrames = targetFrames` when a track exceeds RAM limits.
   - Cap physical RAM allocation of `pcmBuffer` to `MAX_RING_BUFFER_BYTES` (`64 MB` = `8,388,608` stereo frames, ~87s @ 96kHz) using modulo frame indexing `(frameIndex % pcmBufferCapacityFrames) * channels`.
   - Throttle `decoder_thread_func` so `decodedFrames - pcmIndex < pcmBufferCapacityFrames - RING_BUFFER_SAFETY_FRAMES`, streaming tracks of any duration and sample rate from start (`0%`) to true finish (`100%`).

**Tech Stack:** C++17 (NDK, `libusb`, `std::atomic`, 64-bit DSP with TPDF Dither), Kotlin (Android View UI, JNI, JUnit 4).

**Spec:** [`ANALISA-AUDIT/audit-report.md`](file:///Users/yuka/Projects/tsrossa-ujicoba/ANALISA-AUDIT/audit-report.md)

**Global Constraints:**
- Maintain zero heap allocations and zero mutex locks inside the real-time USB Isochronous callback (`iso_callback` / `fill_iso_packet_buffer`).
- Preserve 100% Bit-Perfect bypass when `currentVolume == 1.0f` (`0.0 dB`) and EQ is disabled.
- Preserve existing comments and documentation integrity.

**Review Focus:**
- Verify that `totalFrames` always reflects the true 100% frame count of the decoded file regardless of sample rate (`44.1kHz` to `768kHz`) or duration.
- Verify that modulo indexing `(frame % capacityFrames)` is used consistently across `fill_iso_packet_buffer`, `decoder_thread_func`, `seek_audio`, and `prepare_next_track_internal`.
- Verify that at 5% slider (`0.05f`), the effective attenuation is `-59.26 dB` (`linear gain ≈ 0.001089`), whether in Universal 64-Bit DSP mode or Hybrid UAC mode.

---

## Panduan Mentoring Senior ke Junior: Bagaimana Membaca Plan Ini

Halo Junior Dev! Setelah kita menemukan akar masalah di [`ANALISA-AUDIT/audit-report.md`](file:///Users/yuka/Projects/tsrossa-ujicoba/ANALISA-AUDIT/audit-report.md), sekarang saatnya kita menyusun **Rencana Eksekusi (*Implementation Plan*)**.

Ingat 3 aturan emas seorang Senior Engineer saat memperbaiki *core audio engine*:
1. **Satu Sumber Kebenaran (*Single Source of Truth*):** Jangan menulis rumus volume yang berbeda di C++ Hardware, C++ Software, dan Kotlin UI. Kita buat rumusnya identik dan diuji dengan *Unit Test*!
2. **Real-Time Safety:** Thread USB Isochronous (`fill_iso_packet_buffer`) dipanggil setiap `1 milidetik` oleh kernel. Di dalam fungsi itu **dilarang keras** melakukan `malloc`/`resize` vector atau mengunci `std::mutex`. Karena itu, *Sliding-Window Ring Buffer* kita hanya menggunakan operasi modulo/pembagian indeks dengan variabel `std::atomic<uint64_t>` (*Lock-Free Single-Producer Single-Consumer*).
3. **Zero Silent Failure:** Setiap kali kita mengirim perintah USB Control Transfer ke DAC (`GET_MIN`, `GET_MAX`, `SET_CUR`, `GET_CUR`), kita **wajib** mengecek nilai kembaliannya (`r >= 0`) dan memverifikasi hasilnya.

---

## Peta File yang Akan Dimodifikasi / Dibuat

| File | Peran & Perubahan |
| :--- | :--- |
| [`app/src/main/cpp/AudioCommon.h`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioCommon.h) | Menambahkan konstanta Ring Buffer (`MAX_RING_BUFFER_BYTES = 64 MB`) serta fungsi inline kurva psikoakustik `compute_perceptual_db(float)` dan `db_to_linear_gain(float)`. |
| [`app/src/main/cpp/AudioEngineState.h`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioEngineState.h) | Menambahkan state kapasitas Ring Buffer (`pcmBufferCapacityFrames`, `nextPcmBufferCapacityFrames`), state `nextActiveDecoder` untuk *streaming gapless*, serta state kontrol volume (`preferHardwareVolume`, `appliedHardwareDb`, `volumeMasterSupported`, `volumePerChannelSupported`). |
| [`app/src/main/cpp/UsbDacController.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp) | Memperbaiki validasi `GET_MIN`/`GET_MAX`/`GET_RES` UAC1 & UAC2, mengirim `SET_CUR` ke Master (`ch=0`) dan L/R (`ch=1,2`), memverifikasi lewat `GET_CUR`, dan menghitung `appliedHardwareDb` agar Software DSP menutup sisa atenuasi secara otomatis. |
| [`app/src/main/cpp/AudioStreamer.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp) | Mengimplementasikan *Lock-Free Sliding-Window Ring Buffer* (menghapus pemotongan `totalFrames = targetFrames`) dan mengintegrasikan *Hybrid Perceptual Volume Gain* di 64-bit DSP mixer. |
| [`app/src/main/cpp/native-lib.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/native-lib.cpp) | Menambahkan JNI bridge `setHardwareVolumeEnabled(boolean)` & `isHardwareVolumeEnabled()`. |
| [`app/src/main/java/com/yuka/musicplayer/audio/AudioEngine.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/audio/AudioEngine.kt) | Mengekspos fungsi `setHardwareVolumeEnabled` & helper kalkulasi dB psikoakustik untuk UI. |
| [`app/src/main/java/com/yuka/musicplayer/ui/dialogs/VolumeModalSheet.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/dialogs/VolumeModalSheet.kt) | Menampilkan angka dB psikoakustik yang akurat (`-59.3 dB` pada 5%) dan menambahkan switch **Universal 64-Bit DSP vs Hardware UAC Mode**. |
| [`app/src/test/java/com/yuka/musicplayer/audio/VolumeAndStreamingMathTest.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/test/java/com/yuka/musicplayer/audio/VolumeAndStreamingMathTest.kt) | Unit Test baru untuk memverifikasi kurva psikoakustik, pembagian gain *Hybrid*, dan matematika indeks *Sliding-Window Ring Buffer*. |

---

## Task 1: Unified Psychoacoustic Volume Curve & Ring Buffer Constants

**Files:**
- Modify: [`app/src/main/cpp/AudioCommon.h`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioCommon.h#L18-L35)
- Modify: [`app/src/main/cpp/AudioEngineState.h`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioEngineState.h#L115-L180)
- Modify: [`app/src/main/java/com/yuka/musicplayer/audio/AudioEngine.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/audio/AudioEngine.kt#L1-L122)
- Create: [`app/src/test/java/com/yuka/musicplayer/audio/VolumeAndStreamingMathTest.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/test/java/com/yuka/musicplayer/audio/VolumeAndStreamingMathTest.kt)

### Penjelasan Senior ke Junior (Mengapa Kita Mulai dari Rumus & State?)
Sebelum mengubah thread USB atau thread dekoder, kita letakkan fondasi matematikanya di [`AudioCommon.h`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioCommon.h) dan [`AudioEngine.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/audio/AudioEngine.kt):
- Rumus psikoakustik kita adalah:
  $$\text{dB}(v) = -64.0 \times (1.0 - v)\sqrt{1.0 - v}$$
  Di mana untuk $v = 0.05$ (5%), $\text{dB}(0.05) = -59.26\text{ dB}$, untuk $v = 0.50$ (50%), $\text{dB}(0.50) = -22.63\text{ dB}$, dan untuk $v = 1.00$ (100%), $\text{dB}(1.00) = 0.0\text{ dB}$ (Bit-Perfect).
- Untuk Ring Buffer, kita tetapkan `MAX_RING_BUFFER_BYTES = 64ULL * 1024 * 1024` (64 MB = `8.388.608` stereo frames) dan `RING_BUFFER_SAFETY_FRAMES = 65536` agar dekoder berhenti menulis tepat sebelum menyentuh ekor pembacaan `pcmIndex`.

- [ ] **Step 1: Write the failing unit test in `VolumeAndStreamingMathTest.kt`**

Buat file test [`app/src/test/java/com/yuka/musicplayer/audio/VolumeAndStreamingMathTest.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/test/java/com/yuka/musicplayer/audio/VolumeAndStreamingMathTest.kt):

```kotlin
package com.yuka.musicplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeAndStreamingMathTest {

    @Test
    fun `psychoacoustic curve produces -59_26 dB at 5 percent and 0 dB at 100 percent`() {
        assertEquals(Float.NEGATIVE_INFINITY, AudioEngine.sliderToPerceptualDb(0.0f), 0.001f)
        assertEquals(0.0f, AudioEngine.sliderToPerceptualDb(1.0f), 0.001f)

        val dbAt5Percent = AudioEngine.sliderToPerceptualDb(0.05f)
        // Must be around -59.26 dB (much quieter than old -26.02 dB!)
        assertEquals(-59.26f, dbAt5Percent, 0.1f)

        val dbAt50Percent = AudioEngine.sliderToPerceptualDb(0.50f)
        assertEquals(-22.63f, dbAt50Percent, 0.1f)

        val dbAt75Percent = AudioEngine.sliderToPerceptualDb(0.75f)
        assertEquals(-8.00f, dbAt75Percent, 0.05f)
    }

    @Test
    fun `linear gain at 100 percent is exact unity 1_0 for bit-perfect bypass`() {
        assertEquals(0.0f, AudioEngine.sliderToLinearGain(0.0f), 0.0f)
        assertEquals(1.0f, AudioEngine.sliderToLinearGain(1.0f), 0.0f)

        val gainAt5Percent = AudioEngine.sliderToLinearGain(0.05f)
        // 10^(-59.26 / 20) ≈ 0.001089
        assertTrue("Gain at 5% should be ~0.0011, got $gainAt5Percent", gainAt5Percent in 0.0010f..0.0012f)
    }
}
```

- [ ] **Step 2: Add `sliderToPerceptualDb`, `sliderToLinearGain`, and `setHardwareVolumeEnabled` to `AudioEngine.kt`**

Modifikasi [`app/src/main/java/com/yuka/musicplayer/audio/AudioEngine.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/audio/AudioEngine.kt) untuk menambahkan fungsi matematika psikoakustik dan deklarasi JNI mode volume:

```kotlin
    external fun setVolume(volume: Float)
    external fun isHardwareVolumeSupported(): Boolean
    external fun setHardwareVolumeEnabled(enabled: Boolean)
    external fun isHardwareVolumeEnabled(): Boolean

    /**
     * Converts a normalized UI slider value [0.0f..1.0f] into a perceptual attenuation in dB.
     * Uses the unified psychoacoustic curve: dB(v) = -64.0 * (1 - v) * sqrt(1 - v)
     * - 100% (1.00) ->   0.00 dB (Bit-Perfect Unity)
     * -  75% (0.75) ->  -8.00 dB
     * -  50% (0.50) -> -22.63 dB
     * -  10% (0.10) -> -54.64 dB
     * -   5% (0.05) -> -59.26 dB (Safe & quiet on sensitive IEMs + 2Vrms Dongle DACs)
     * -   1% (0.01) -> -63.04 dB
     * -   0% (0.00) -> -Infinity (Mute)
     */
    fun sliderToPerceptualDb(slider: Float): Float {
        if (slider <= 0.0f) return Float.NEGATIVE_INFINITY
        if (slider >= 1.0f) return 0.0f
        val complement = 1.0f - slider
        return -64.0f * complement * kotlin.math.sqrt(complement)
    }

    fun sliderToLinearGain(slider: Float): Float {
        if (slider <= 0.0f) return 0.0f
        if (slider >= 1.0f) return 1.0f
        val db = sliderToPerceptualDb(slider)
        return Math.pow(10.0, (db / 20.0)).toFloat()
    }
```

- [ ] **Step 3: Update `AudioCommon.h` and `AudioEngineState.h` with Ring Buffer constants & Psychoacoustic helpers**

Di [`app/src/main/cpp/AudioCommon.h:20-23`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioCommon.h#L20-L23), ganti konstanta lama yang memotong durasi lagu menjadi konstanta *Sliding-Window Ring Buffer* dan fungsi inline volume psikoakustik:

```cpp
// Security & Streaming Ring Buffer Constants
// 64 MB sliding-window ring buffer (~87 sec window @ 96kHz stereo, unbounded total track duration)
constexpr uint64_t MAX_RING_BUFFER_BYTES = 64ULL * 1024 * 1024;
constexpr uint64_t MAX_NEXT_RING_BUFFER_BYTES = 32ULL * 1024 * 1024;
constexpr uint64_t RING_BUFFER_SAFETY_FRAMES = 65536ULL; // Keep 64K frames safety gap before read pointer

// Legacy aliases kept for compatibility
constexpr uint64_t MAX_TRACK_BUFFER_BYTES = MAX_RING_BUFFER_BYTES;
constexpr uint64_t MAX_NEXT_TRACK_BUFFER_BYTES = MAX_NEXT_RING_BUFFER_BYTES;

// Unified Psychoacoustic Volume Curve: dB(v) = -64.0f * (1 - v) * sqrt(1 - v)
inline float compute_perceptual_db(float slider) {
  if (slider <= 0.0f) return -128.0f;
  if (slider >= 1.0f) return 0.0f;
  float comp = 1.0f - slider;
  return -64.0f * comp * std::sqrt(comp);
}

inline float db_to_linear_gain(float db) {
  if (db <= -127.0f) return 0.0f;
  if (db >= 0.0f) return 1.0f;
  return std::pow(10.0f, db / 20.0f);
}
```

Di [`app/src/main/cpp/AudioEngineState.h:115-175`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioEngineState.h#L115-L175), tambahkan variabel kapasitas ring buffer, `nextActiveDecoder`, dan state volume hybrid:

```cpp
  // Audio Buffer & Streaming Ring-Buffer State
  std::vector<int32_t> pcmBuffer;
  std::atomic<uint64_t> pcmBufferCapacityFrames{0};
  std::atomic<uint64_t> totalFrames{0};
  std::atomic<uint64_t> decodedFrames{0};
  std::atomic<uint64_t> pcmIndex{0};

  // Next Track Buffer (For Gapless Playback with Ring Buffer handoff)
  std::vector<int32_t> nextPcmBuffer;
  std::atomic<uint64_t> nextPcmBufferCapacityFrames{0};
  std::atomic<uint64_t> nextTotalFrames{0};
  std::atomic<uint64_t> nextDecodedFrames{0};
  std::unique_ptr<AudioDecoder> nextActiveDecoder;
```
Dan pada bagian `Hardware Volume Control State` di [`AudioEngineState.h:164-175`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioEngineState.h#L164-L175):
```cpp
  // Hardware & Universal Hybrid Volume Control State
  std::atomic<bool> isHardwareVolumeSupported{false};
  std::atomic<bool> isHardwareVolumeActive{false};
  // Default false = Universal 64-Bit DSP Mode (100% compatible with all DACs including AD1 & M06Pro 2)
  std::atomic<bool> preferHardwareVolume{false};
  std::atomic<float> appliedHardwareDb{0.0f}; // Actual dB currently absorbed by DAC hardware
  uint8_t featureUnitId = 0;
  uint8_t volumeChannel = 0;
  bool volumeMasterSupported = false;
  bool volumePerChannelSupported = false;
  int16_t minVolumeDb = -32768;
  int16_t maxVolumeDb = 0;
  int16_t resVolumeDb = 256;
  std::atomic<float> currentVolume{1.0f};
  float currentSoftwareVolume = 1.0f;
```

---

## Task 2: Universal UAC1/UAC2 Hardware Volume Hardening & Hybrid Gain Split

**Files:**
- Modify: [`app/src/main/cpp/UsbDacController.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L85-L155)
- Modify: [`app/src/main/cpp/UsbDacController.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L595-L625)
- Modify: [`app/src/main/cpp/UsbDacController.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L975-L1108)
- Modify: [`app/src/main/cpp/native-lib.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/native-lib.cpp#L150-L185)

### Penjelasan Senior ke Junior (Bagaimana Kita Menjinakkan Semua DAC di Dunia)
Di [`UsbDacController.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp), kita memperbaiki 4 cacat sekaligus:
1. **Descriptor Parsing (`baris 601-615`):** Kita rekam `volumeMasterSupported` (`ch=0`) **dan** `volumePerChannelSupported` (`ch=1,2`) secara terpisah, bukan memilih salah satu lalu melupakan yang lain.
2. **Validasi `GET_MIN` / `GET_MAX` / `GET_RES` di UAC1 & UAC2 (`baris 1039-1091`):**
   - Pada UAC1, kita cek apakah `libusb_control_transfer` benar-benar mengembalikan `2` byte (`rMin == 2 && rMax == 2`), dan kita baca juga `GET_RES (0x84)`.
   - Jika `minVolumeDb >= maxVolumeDb` atau rentangnya terlalu sempit (`maxVolumeDb - minVolumeDb < 6 * 256`, kurang dari 6 dB), kita tandai bahwa hardware range tidak valid.
3. **Kunci DAC ke `0 dB` (Unity Gain) Saat Mode Universal 64-Bit DSP Aktif:**
   - Saat DAC baru dicolok atau saat user memakai **Universal 64-Bit DSP Mode** (`preferHardwareVolume == false`), kita kirim perintah `SET_CUR = maxVolumeDb (0 dB)` sekali ke DAC agar register hardware DAC berada di posisi 100% murni, lalu `appliedHardwareDb = 0.0f` dan `isHardwareVolumeActive = false`. Seluruh pengecilan volume dilakukan oleh **64-bit Float + TPDF Dither** di `AudioStreamer.cpp`!
4. **Saat Mode Hardware UAC (`preferHardwareVolume == true`) Dipilih User:**
   - `control_thread_func` menghitung `desired_db = compute_perceptual_db(req.volume)` (misal `-59.26 dB` di 5%), membulatkannya ke kelipatan `resVolumeDb`, dan mengirim `SET_CUR` ke Master (`ch=0`) **serta** Left/Right (`ch=1,2`).
   - Kemudian dilakukan **Readback Check (`GET_CUR = 0x81`)**: jika DAC berhasil mengubah registernya, `appliedHardwareDb` mencatat berapa dB yang diserap hardware, dan jika masih kurang (misal DAC mentok di `-24 dB`), sisa `-35.26 dB` otomatis diserap oleh 64-bit DSP di `AudioStreamer.cpp`! Jika DAC gagal merespons `GET_CUR`, otomatis *fallback* penuh ke 64-bit DSP!

- [ ] **Step 1: Update Feature Unit descriptor parsing in `UsbDacController.cpp` (`lines 595-620`)**

Ganti blok pemilihan channel di [`UsbDacController.cpp:601-615`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L601-L615):

```cpp
                bool masterVol = (g_audioState.uacVersion == 1)
                                     ? ((master_controls & 0x02) != 0)
                                     : ((master_controls & 0x0C) != 0);
                bool ch1Vol = (num_channels >= 1) &&
                              ((g_audioState.uacVersion == 1)
                                   ? ((ch1_controls & 0x02) != 0)
                                   : ((ch1_controls & 0x0C) != 0));

                g_audioState.volumeMasterSupported = masterVol;
                g_audioState.volumePerChannelSupported = ch1Vol;

                if (masterVol || ch1Vol) {
                  g_audioState.isHardwareVolumeSupported = true;
                  g_audioState.volumeChannel = masterVol ? 0 : 1;
                }
```

- [ ] **Step 2: Harden `control_thread_func` and UAC1/UAC2 volume initialization in `UsbDacController.cpp`**

Perbarui `control_thread_func` ([`UsbDacController.cpp:85-155`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L85-L155)) agar menggunakan `compute_perceptual_db(req.volume)`, menulis ke Master dan L/R, memverifikasi `GET_CUR`, dan mengupdate `appliedHardwareDb`:

```cpp
    if (g_audioState.devHandle && g_audioState.isHardwareVolumeSupported &&
        g_audioState.featureUnitId > 0) {
      int16_t target_db = g_audioState.maxVolumeDb;
      float desired_db = 0.0f;

      if (!g_audioState.preferHardwareVolume.load()) {
        // In Universal 64-Bit DSP Mode, lock DAC hardware at 0dB (maxVolumeDb)
        target_db = g_audioState.maxVolumeDb;
        desired_db = 0.0f;
      } else if (req.volume <= 0.0f) {
        target_db = g_audioState.minVolumeDb;
        desired_db = -128.0f;
      } else {
        desired_db = compute_perceptual_db(req.volume);
        int32_t raw_target = (int32_t)g_audioState.maxVolumeDb +
                             (int32_t)std::lround(desired_db * 256.0f);
        int16_t step = (g_audioState.resVolumeDb > 0) ? g_audioState.resVolumeDb : 1;
        if (step > 1) {
          raw_target = ((raw_target - g_audioState.maxVolumeDb) / step) * step +
                       g_audioState.maxVolumeDb;
        }
        if (raw_target < g_audioState.minVolumeDb)
          raw_target = g_audioState.minVolumeDb;
        if (raw_target > g_audioState.maxVolumeDb)
          raw_target = g_audioState.maxVolumeDb;
        target_db = (int16_t)raw_target;
      }

      uint8_t vol_buf[2] = {(uint8_t)(target_db & 0xFF),
                            (uint8_t)((target_db >> 8) & 0xFF)};
      int r_master = -1;
      int r_ch = -1;

      if (g_audioState.volumeMasterSupported || g_audioState.volumeChannel == 0) {
        r_master = libusb_control_transfer(
            g_audioState.devHandle, 0x21, 0x01, (0x02 << 8) | 0x00,
            (g_audioState.featureUnitId << 8) | 0x00, vol_buf, 2, 200);
        if (r_master < 0) lib_usb_clear_halt_if_needed();
      }
      if (g_audioState.volumePerChannelSupported || g_audioState.volumeChannel != 0 || r_master < 0) {
        int r1 = libusb_control_transfer(
            g_audioState.devHandle, 0x21, 0x01, (0x02 << 8) | 0x01,
            (g_audioState.featureUnitId << 8) | 0x00, vol_buf, 2, 200);
        if (r1 < 0) lib_usb_clear_halt_if_needed();
        int r2 = libusb_control_transfer(
            g_audioState.devHandle, 0x21, 0x01, (0x02 << 8) | 0x02,
            (g_audioState.featureUnitId << 8) | 0x00, vol_buf, 2, 200);
        if (r2 < 0) lib_usb_clear_halt_if_needed();
        r_ch = (r1 >= 0 || r2 >= 0) ? 2 : -1;
      }

      if (r_master >= 0 || r_ch >= 0) {
        if (g_audioState.preferHardwareVolume.load()) {
          float actual_hw_db = (float)(target_db - g_audioState.maxVolumeDb) / 256.0f;
          g_audioState.appliedHardwareDb.store(actual_hw_db);
          g_audioState.isHardwareVolumeActive.store(true);
        } else {
          g_audioState.appliedHardwareDb.store(0.0f);
          g_audioState.isHardwareVolumeActive.store(false);
        }
      } else {
        LOGW("Hardware volume SET_CUR failed, falling back to 64-bit DSP volume");
        g_audioState.appliedHardwareDb.store(0.0f);
        g_audioState.isHardwareVolumeActive.store(false);
      }
    }
```

Dan di bagian inisialisasi UAC1/UAC2 ([`UsbDacController.cpp:1015-1095`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L1015-L1095)), perbaiki validasi `GET_MIN`/`GET_MAX`/`GET_RES` untuk UAC1 dan UAC2 secara seragam:

```cpp
  if (g_audioState.isHardwareVolumeSupported &&
      g_audioState.featureUnitId > 0) {
    bool rangeValid = false;
    if (g_audioState.uacVersion == 1) {
      uint8_t min_buf[2] = {0, 0};
      uint8_t max_buf[2] = {0, 0};
      uint8_t res_buf[2] = {0, 1};
      int rMin = libusb_control_transfer(
          g_audioState.devHandle, 0xA1, 0x82,
          (0x02 << 8) | g_audioState.volumeChannel,
          (g_audioState.featureUnitId << 8) | 0x00, min_buf, 2, 1000);
      if (rMin < 0) lib_usb_clear_halt_if_needed();

      int rMax = libusb_control_transfer(
          g_audioState.devHandle, 0xA1, 0x83,
          (0x02 << 8) | g_audioState.volumeChannel,
          (g_audioState.featureUnitId << 8) | 0x00, max_buf, 2, 1000);
      if (rMax < 0) lib_usb_clear_halt_if_needed();

      int rRes = libusb_control_transfer(
          g_audioState.devHandle, 0xA1, 0x84,
          (0x02 << 8) | g_audioState.volumeChannel,
          (g_audioState.featureUnitId << 8) | 0x00, res_buf, 2, 1000);
      if (rRes < 0) lib_usb_clear_halt_if_needed();

      if (rMin == 2 && rMax == 2) {
        g_audioState.minVolumeDb = (int16_t)((min_buf[1] << 8) | min_buf[0]);
        g_audioState.maxVolumeDb = (int16_t)((max_buf[1] << 8) | max_buf[0]);
        g_audioState.resVolumeDb =
            (rRes == 2) ? (int16_t)((res_buf[1] << 8) | res_buf[0]) : 256;
        if (g_audioState.resVolumeDb <= 0) g_audioState.resVolumeDb = 256;
        rangeValid = (g_audioState.minVolumeDb < g_audioState.maxVolumeDb) &&
                     ((g_audioState.maxVolumeDb - g_audioState.minVolumeDb) >= (6 * 256));
      }
    } else {
      uint8_t range_buf[64];
      memset(range_buf, 0, sizeof(range_buf));
      int r = libusb_control_transfer(
          g_audioState.devHandle, 0xA1, 0x82,
          (0x02 << 8) | g_audioState.volumeChannel,
          (g_audioState.featureUnitId << 8) | 0x00, range_buf,
          sizeof(range_buf), 1000);
      if (r >= 8) {
        uint16_t num_subranges = range_buf[0] | (range_buf[1] << 8);
        if (num_subranges >= 1) {
          g_audioState.minVolumeDb = (int16_t)(range_buf[2] | (range_buf[3] << 8));
          g_audioState.maxVolumeDb = (int16_t)(range_buf[4] | (range_buf[5] << 8));
          g_audioState.resVolumeDb = (int16_t)(range_buf[6] | (range_buf[7] << 8));
          if (g_audioState.resVolumeDb <= 0) g_audioState.resVolumeDb = 256;
          for (int i = 1; i < num_subranges && (2 + i * 6 + 5) < r; i++) {
            int16_t sub_min = (int16_t)(range_buf[2 + i * 6] | (range_buf[2 + i * 6 + 1] << 8));
            int16_t sub_max = (int16_t)(range_buf[2 + i * 6 + 2] | (range_buf[2 + i * 6 + 3] << 8));
            if (sub_min < g_audioState.minVolumeDb) g_audioState.minVolumeDb = sub_min;
            if (sub_max > g_audioState.maxVolumeDb) g_audioState.maxVolumeDb = sub_max;
          }
          rangeValid = (g_audioState.minVolumeDb < g_audioState.maxVolumeDb) &&
                       ((g_audioState.maxVolumeDb - g_audioState.minVolumeDb) >= (6 * 256));
        }
      } else {
        lib_usb_clear_halt_if_needed();
      }
    }

    if (!rangeValid) {
      LOGW("DAC reported invalid/shallow UAC volume range (min=%d, max=%d). Using standard fallback.",
           g_audioState.minVolumeDb, g_audioState.maxVolumeDb);
      g_audioState.minVolumeDb = -16384; // -64 dB
      g_audioState.maxVolumeDb = 0;      //   0 dB
      g_audioState.resVolumeDb = 256;    //   1 dB
    }
    set_volume(g_audioState.currentVolume.load());
  }
```

Tambahkan juga fungsi `set_hardware_volume_enabled(bool enabled)` dan `is_hardware_volume_enabled()` di [`UsbDacController.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp) dan daftarkan di [`native-lib.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/native-lib.cpp).

---

## Task 3: Hybrid Perceptual DSP Volume & Unbounded Sliding-Window Ring Buffer in `AudioStreamer.cpp`

**Files:**
- Modify: [`app/src/main/cpp/AudioStreamer.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L250-L315)
- Modify: [`app/src/main/cpp/AudioStreamer.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L475-L530)
- Modify: [`app/src/main/cpp/AudioStreamer.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L930-L965)
- Modify: [`app/src/main/cpp/AudioStreamer.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L1315-L1375)
- Modify: [`app/src/main/cpp/AudioStreamer.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L1405-L1435)
- Modify: [`app/src/test/java/com/yuka/musicplayer/audio/VolumeAndStreamingMathTest.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/test/java/com/yuka/musicplayer/audio/VolumeAndStreamingMathTest.kt)

### Penjelasan Senior ke Junior (Bagaimana Ring Buffer & Hybrid DSP Bekerja Tanpa Memotong Lagu)
Sekarang kita masuk ke jantung mesin audio di [`AudioStreamer.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp):

1. **Perbaikan Volume di `fill_iso_packet_buffer` (`baris 480-485`):**
   Lihat kode lama:
   ```cpp
   float target_vol = g_audioState.isHardwareVolumeActive.load()
                          ? 1.0f
                          : (raw_vol * raw_vol);
   ```
   Kita ganti dengan **Hybrid Perceptual Gain**:
   ```cpp
   float target_vol = 1.0f;
   if (raw_vol <= 0.0f) {
     target_vol = 0.0f;
   } else if (raw_vol < 1.0f) {
     float desired_db = compute_perceptual_db(raw_vol);
     float hw_db = g_audioState.isHardwareVolumeActive.load()
                       ? g_audioState.appliedHardwareDb.load()
                       : 0.0f;
     float residual_db = desired_db - hw_db; // Remaining attenuation for 64-bit DSP
     target_vol = (residual_db < -0.05f) ? db_to_linear_gain(residual_db) : 1.0f;
   }
   ```
   **Coba perhatikan betapa pintarnya 10 baris kode di atas:**
   - Jika sedang di **Mode Universal 64-Bit DSP** (`isHardwareVolumeActive == false`), maka `hw_db = 0.0f`, sehingga `residual_db = desired_db` (`-59.26 dB` pada 5%). Seluruh pengecilan dikerjakan oleh 64-bit float + TPDF dither!
   - Jika sedang di **Mode Hardware UAC** (`isHardwareVolumeActive == true`), dan DAC sanggup menyerap penuh `-59.26 dB`, maka `residual_db = 0.0f` dan `target_vol = 1.0f` (100% Bit-Perfect).
   - Jika DAC hanya sanggup menyerap `-24.0 dB`, maka `residual_db = -59.26 - (-24.0) = -35.26 dB`, dan 64-bit DSP otomatis mengerjakan sisa `-35.26 dB`-nya!
   - Jika slider di **100% (`raw_vol >= 1.0f`)**, `target_vol = 1.0f` (100% Bit-Perfect di semua mode!).

2. **Perbaikan Pemotongan Lagu di `start_audio_Internal` (`baris 933-959`):**
   - Kita **HAPUS** baris `totalFrames = targetFrames;`!
   - `totalFrames` tetap menyimpan jumlah frame lagu yang sebenarnya (100% durasi penuh!).
   - `pcmBufferCapacityFrames` disetel ke `std::min(totalFrames, maxRingFrames)` di mana `maxRingFrames = MAX_RING_BUFFER_BYTES / (newChannels * sizeof(int32_t))` (`8.388.608` frame untuk stereo = `64 MB`).
   - `g_audioState.pcmBuffer.resize(pcmBufferCapacityFrames * newChannels);`

3. **Perbaikan Pembacaan & Penulisan Melingkar (*Ring Buffer Modulo Indexing*):**
   - Di `decoder_thread_func` (`baris 259-290`):
     - Sebelum mendekode chunk berikutnya (`CHUNK_FRAMES = 16384`), cek ruang yang tersedia di ring buffer:
       ```cpp
       uint64_t capacity = g_audioState.pcmBufferCapacityFrames.load();
       uint64_t readIdx = g_audioState.pcmIndex.load();
       if (capacity > 0 && current >= readIdx &&
           (current - readIdx) + CHUNK_FRAMES + RING_BUFFER_SAFETY_FRAMES > capacity) {
         // Ring buffer sudah penuh di depan read pointer; tunggu 5ms sampai USB memutar sebagian frame
         g_audioState.decoderCv.wait_for(lock, std::chrono::milliseconds(5));
         continue;
       }
       ```
     - Agar `activeDecoder->decodeFrames(...)` tidak pernah menulis melewati ujung vector ketika membungkus (*wrap-around*) di batas `capacity`, batasi `framesToDecode` agar tidak melewati batas akhir ring buffer:
       ```cpp
       uint64_t writeSlot = (capacity > 0) ? (current % capacity) : current;
       uint64_t contiguousSlots = (capacity > 0) ? (capacity - writeSlot) : framesToDecode;
       if (framesToDecode > contiguousSlots) {
         framesToDecode = contiguousSlots;
       }
       int32_t *writePtr = g_audioState.pcmBuffer.data() + (writeSlot * channels);
       ```
     - Lihat betapa elegannya trik `contiguousSlots` di atas! Dengan membatasi `framesToDecode <= capacity - writeSlot`, pointer `writePtr` **dijamin 100% kontigu di memori** dan otomatis membungkus (*wrap around*) kembali ke indeks `0` pada iterasi berikutnya tanpa perlu menyalin buffer tambahan!
   - Di `fill_iso_packet_buffer` (`baris 519-522`):
     - Saat membaca sampel dari `g_audioState.pcmBuffer`, ganti `pcmIndex * channels` menjadi:
       ```cpp
       uint64_t cap = g_audioState.pcmBufferCapacityFrames.load();
       uint64_t bufFrame = (cap > 0) ? (pcmIndex % cap) : pcmIndex;
       size_t baseIdx = (size_t)(bufFrame * channels);
       ```
   - Di `seek_audio` (`baris 1406-1431`):
     - Jika user menggeser *scrubber* (`seek_audio`) pada lagu yang menggunakan Ring Buffer (`totalFrames > pcmBufferCapacityFrames`), kita kunci `decoderMutex`, panggil `g_audioState.activeDecoder->seek(targetFrame)`, lalu setel `decodedFrames = targetFrame` dan `pcmIndex = targetFrame`, kemudian bangunkan `decoderCv.notify_one()`!

- [ ] **Step 1: Add Ring Buffer wrap-around & backpressure tests to `VolumeAndStreamingMathTest.kt`**

Tambahkan test verifikasi logika *Ring Buffer* di [`VolumeAndStreamingMathTest.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/test/java/com/yuka/musicplayer/audio/VolumeAndStreamingMathTest.kt):

```kotlin
    @Test
    fun `ring buffer modulo indexing streams 300s 96kHz track to 100 percent without truncation`() {
        val sampleRate = 96_000L
        val durationSec = 300L // 5 minutes (like ReoNa - Katappo no Kutsushita)
        val totalFrames = sampleRate * durationSec // 28,800,000 frames (230.4 MB)
        val ringCapacityFrames = (64L * 1024L * 1024L) / 8L // 8,388,608 frames (64 MB)

        var pcmIndex = 0L
        var decodedFrames = 0L
        val chunkFrames = 16_384L
        val safetyFrames = 65_536L

        // Simulate streaming until track finishes
        while (pcmIndex < totalFrames) {
            // Producer (decoder_thread_func) fills ring buffer up to capacity
            while (decodedFrames < totalFrames &&
                (decodedFrames - pcmIndex) + chunkFrames + safetyFrames <= ringCapacityFrames
            ) {
                val writeSlot = decodedFrames % ringCapacityFrames
                val contiguous = ringCapacityFrames - writeSlot
                val toDecode = minOf(chunkFrames, totalFrames - decodedFrames, contiguous)
                assertTrue(writeSlot + toDecode <= ringCapacityFrames)
                decodedFrames += toDecode
            }
            // Consumer (fill_iso_packet_buffer) advances playback
            val step = minOf(48_000L, decodedFrames - pcmIndex)
            assertTrue("Decoder must stay ahead of playback", step > 0)
            pcmIndex += step
        }

        assertEquals(totalFrames, decodedFrames)
        assertEquals(totalFrames, pcmIndex)
    }
```

- [ ] **Step 2: Implement Hybrid Perceptual Gain & Sliding-Window Ring Buffer in `AudioStreamer.cpp`**

Terapkan perubahan pada:
1. `decoder_thread_func` ([`AudioStreamer.cpp:255-305`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L255-L305))
2. `fill_iso_packet_buffer` ([`AudioStreamer.cpp:480-595`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L480-L595))
3. `start_audio_Internal` ([`AudioStreamer.cpp:933-975`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L933-L975))
4. `prepare_next_track_internal` ([`AudioStreamer.cpp:1325-1375`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L1325-L1375))
5. `seek_audio` ([`AudioStreamer.cpp:1406-1432`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L1406-L1432))

Pastikan saat transisi *gapless* di `fill_iso_packet_buffer` (`baris 550-580`), `pcmBufferCapacityFrames` juga di-swap dari `nextPcmBufferCapacityFrames`, dan jika lagu berikutnya menggunakan *partial pre-buffering* (`nextDecodedFrames < nextTotalFrames`), `g_audioState.activeDecoder = std::move(g_audioState.nextActiveDecoder)` serta `g_audioState.decoderCv.notify_one()` dipanggil agar dekoder langsung melanjutkan dekode sisa lagu berikutnya tanpa jeda!

---

## Task 4: UI Volume Modal Sheet Synchronization & Universal Volume Mode Switch

**Files:**
- Modify: [`app/src/main/java/com/yuka/musicplayer/ui/dialogs/VolumeModalSheet.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/dialogs/VolumeModalSheet.kt#L60-L150)
- Modify: [`app/src/main/java/com/yuka/musicplayer/MainActivity.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/MainActivity.kt#L580-L605)

### Penjelasan Senior ke Junior (Transparansi & Kontrol Penuh bagi Audiophile)
Di [`VolumeModalSheet.kt:71-75`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/dialogs/VolumeModalSheet.kt#L71-L75), sebelumnya UI menghitung tampilan dB dengan rumus lama `20f * log10(level)` sehingga di 5% layar menampilkan `-26.0 dB`.
Kita perbarui agar:
1. Memanggil `AudioEngine.sliderToPerceptualDb(level)` sehingga di 5% layar menampilkan **`-59.3 dB`** (dan di 100% menampilkan **`0.0 dB (Bit-Perfect)`**).
2. Menampilkan badge status yang jelas:
   - **`UNIVERSAL 64-BIT DSP (TPDF)`** (Default — dijamin berfungsi di semua DAC termasuk Kiwi Ears AD1 & M06Pro 2).
   - **`HYBRID HARDWARE UAC + 64-BIT DSP`** (Opsional — bisa diklik/di-*toggle* langsung oleh user jika ingin mencoba kontrol register UAC hardware pada DAC desktop).
3. Menyimpan preferensi mode volume user di `SharedPreferences` (`"dac_hw_volume_enabled"`, default `false`) di `MainActivity.kt`.

- [ ] **Step 1: Update `VolumeModalSheet.kt` to use `AudioEngine.sliderToPerceptualDb` and provide a clickable mode toggle chip**

Modifikasi [`VolumeModalSheet.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/dialogs/VolumeModalSheet.kt#L68-L150) untuk menampilkan nilai dB psikoakustik dari `AudioEngine.sliderToPerceptualDb(level)` serta chip interaktif untuk mengganti mode antara `UNIVERSAL 64-BIT DSP` dan `HYBRID UAC HARDWARE`.

- [ ] **Step 2: Sync `preferHardwareVolume` preference in `MainActivity.kt`**

Saat aplikasi dibuka atau DAC terhubung di [`MainActivity.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/MainActivity.kt), sinkronkan setting `AudioEngine.setHardwareVolumeEnabled(prefs.getBoolean("dac_hw_volume_enabled", false))`.

---

## Task 5: End-to-End Verification & Regression Suite

**Files:**
- Modify: [`app/src/test/java/com/yuka/musicplayer/audio/AudioEngineEventTest.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/test/java/com/yuka/musicplayer/audio/AudioEngineEventTest.kt)
- Modify: [`app/src/test/java/com/yuka/musicplayer/audio/VolumeAndStreamingMathTest.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/test/java/com/yuka/musicplayer/audio/VolumeAndStreamingMathTest.kt)

- [ ] **Step 1: Run all JVM unit tests and native C++ build verification**

Run:
```bash
./gradlew testDebugUnitTest assembleDebug
```
Expected:
- Semua test di `VolumeAndStreamingMathTest` dan `AudioEngineEventTest` berstatus **PASS**.
- Kompilasi C++ NDK (`CMakeLists.txt` -> `libmusicplayer.so`) selesai tanpa error maupun warning.

- [ ] **Step 2: Checklist Verifikasi Fisik di Device & DAC (`Kiwi Ears AD1` / `M06Pro 2`)**

1. Colokkan DAC **Kiwi Ears AD1** atau **M06Pro 2**, setel volume ke **5%**:
   - Pastikan label di `VolumeModalSheet` menunjukkan **`-59.3 dB`** dan mode **`UNIVERSAL 64-BIT DSP`**.
   - Pastikan suara di IEM sangat pelan dan nyaman (~60 dB SPL), tidak lagi keras!
2. Putar lagu Hi-Res 96 kHz / 192 kHz berdurasi > 4 menit (seperti **ReoNa - かたっぽの靴下.flac** atau **Kalafina - Heavenly Blue.flac**):
   - Pastikan lagu melewati titik **72.8% (`3 menit 38 detik`)** dengan mulus dan terus berputar sampai **100% selesai** baru pindah ke lagu berikutnya!
   - Coba lakukan *Seek* (geser scrubber) ke menit ke-4 (85%), pastikan langsung berbunyi tanpa terputus!
