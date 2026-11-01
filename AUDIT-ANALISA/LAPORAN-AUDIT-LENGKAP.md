# Laporan Audit & Analisa Reverse Engineering Lengkap: TSRossa Android (`tsrossa-ujicoba`) ke macOS (`tsrossa-macos`)

> **Dokumen Induk Terpadu (Master Audit Report)**  
> Dokumen ini merangkum hasil audit menyeluruh dari segala aspek terhadap aplikasi **`tsrossa-macos`** (`/Users/yuka/Documents/Projects/tsrossa-macos`) dengan mengacu pada proyek asli **`tsrossa-ujicoba`** (`/Users/yuka/Documents/Projects/tsrossa-ujicoba` — diakses secara *strictly read-only*).
>
> Untuk rincian mendalam per aspek, Anda juga dapat membuka dokumen modular di dalam folder ini:
> - [`00-RINGKASAN-EKSEKUTIF-DAN-MATRIKS-PARITAS.md`](file:///Users/yuka/Documents/Projects/tsrossa-macos/AUDIT-ANALISA/00-RINGKASAN-EKSEKUTIF-DAN-MATRIKS-PARITAS.md)
> - [`01-AUDIT-AUDIO-ENGINE-DSP-DAN-COREAUDIO.md`](file:///Users/yuka/Documents/Projects/tsrossa-macos/AUDIT-ANALISA/01-AUDIT-AUDIO-ENGINE-DSP-DAN-COREAUDIO.md)
> - [`02-AUDIT-MEMORI-STREAMING-GAPLESS-DAN-KONKURENSI.md`](file:///Users/yuka/Documents/Projects/tsrossa-macos/AUDIT-ANALISA/02-AUDIT-MEMORI-STREAMING-GAPLESS-DAN-KONKURENSI.md)
> - [`03-AUDIT-METADATA-DATABASE-LIBRARY-DAN-SEARCH.md`](file:///Users/yuka/Documents/Projects/tsrossa-macos/AUDIT-ANALISA/03-AUDIT-METADATA-DATABASE-LIBRARY-DAN-SEARCH.md)
> - [`04-AUDIT-UI-UX-TELEMETRI-PALSU-DAN-INTEGRASI-MACOS.md`](file:///Users/yuka/Documents/Projects/tsrossa-macos/AUDIT-ANALISA/04-AUDIT-UI-UX-TELEMETRI-PALSU-DAN-INTEGRASI-MACOS.md)
> - [`05-AUDIT-BUILD-SYSTEM-KEAMANAN-UPDATER-DAN-TESTING.md`](file:///Users/yuka/Documents/Projects/tsrossa-macos/AUDIT-ANALISA/05-AUDIT-BUILD-SYSTEM-KEAMANAN-UPDATER-DAN-TESTING.md)
> - [`06-AUDIT-UI-UX-THEMES-SWIFT-HIG-DAN-LIQUID-GLASS.md`](file:///Users/yuka/Documents/Projects/tsrossa-macos/AUDIT-ANALISA/06-AUDIT-UI-UX-THEMES-SWIFT-HIG-DAN-LIQUID-GLASS.md)

---

## Daftar Isi
1. [Metodologi & Cakupan Audit](#1-metodologi--cakupan-audit)
2. [Aspek 1: Audio Engine, Signal Path, DSP Mathematics & CoreAudio HAL](#2-aspek-1-audio-engine-signal-path-dsp-mathematics--coreaudio-hal)
3. [Aspek 2: Arsitektur Memori, Streaming, Gapless Playback & Konkurensi](#3-aspek-2-arsitektur-memori-streaming-gapless-playback--konkurensi)
4. [Aspek 3: Metadata, Database Library, Sorting, Pencarian & Lirik](#4-aspek-3-metadata-database-library-sorting-pencarian--lirik)
5. [Aspek 4: UI/UX, Telemetri Palsu (Placebo), Fitur yang Hilang & Integrasi macOS](#5-aspek-4-uiux-telemetri-palsu-placebo-fitur-yang-hilang--integrasi-macos)
6. [Aspek 5: Build System, Packaging, Keamanan Updater & Unit Testing](#6-aspek-5-build-system-packaging-keamanan-updater--unit-testing)
7. [Aspek 6: UI/UX Themes, Standar Pemrograman Swift, Apple HIG & Desain "Liquid Glass"](#7-aspek-6-uiux-themes-standar-pemrograman-swift-apple-hig--desain-liquid-glass)
8. [Peta Jalan Perbaikan Prioritas (Remediation Roadmap)](#8-peta-jalan-perbaikan-prioritas-remediation-roadmap)

---

## 1. Metodologi & Cakupan Audit

Audit ini membandingkan secara langsung kode sumber tingkat rendah (C/C++) dan lapisan aplikasi (SwiftUI/AppKit vs Kotlin/Jetpack Compose):

- **Proyek Acuan Android (`tsrossa-ujicoba`):**
  - **C++ Native Engine (`app/src/main/cpp/`):** `AudioCommon.h` (140 baris), `AudioDecoder.h` (111 baris), `AudioEngineState.h` (221 baris), `AudioEngineState.cpp` (26 baris), `AudioStreamer.cpp` (1.954 baris), `UsbDacController.cpp` (1.265 baris), `native-lib.cpp` (315 baris), `dr_flac.h`, `dr_wav.h`.
  - **Kotlin Application & UI (`app/src/main/java/`):** 68 file produksi (~20.900 baris) + **17 file Unit Test (`app/src/test/java/`)** (~2.100 baris).
- **Proyek Target macOS (`tsrossa-macos`):**
  - **C++ Native Engine (`src/nativeMain/cpp/`):** `dr_impl.c` (7 baris), `MacAudioCommon.h` (57 baris), `MacAudioDecoder.h` (119 baris), `CoreAudioSink.h` (107 baris), `CoreAudioSink.cpp` (662 baris), `TsrossaAudioBridge.h` (46 baris), `TsrossaAudioBridge.cpp` (129 baris).
  - **SwiftUI Application (`macos/Sources/`):** `main.swift` (101 baris), `AudioEngine.swift` (369 baris), `AppViewModel.swift` (866 baris), `MainAppView.swift` (3.579 baris), `LyricsManager.swift` (164 baris), `MetadataScanner.swift` (182 baris), `TelemetryModalView.swift` (219 baris), `Theme.swift` (474 baris), `UpdateManager.swift` (300 baris).
  - **Build Scripts:** `build_mac.sh` (85 baris), `package_dmg.sh` (30 baris), **0 file Unit Test**.

---

## 2. Aspek 1: Audio Engine, Signal Path, DSP Mathematics & CoreAudio HAL

### 2.1. Konversi Paksa ke `float` 32-Bit Menghancurkan Presisi Integer 32-Bit (Bukan Bit-Perfect Murni)
- **File & Baris:** [`CoreAudioSink.cpp:L425-L437`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L425-L437) dan [`CoreAudioSink.cpp:L630-L634`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L630-L634).
- **Masalah:**
  Di Android ([`AudioStreamer.cpp:L682-L702`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L682-L702)), jalur *Bit-Perfect* (`targetGain >= 0.99999f`) menyalin sampel integer 32-bit (`int32_t`) dari decoder langsung ke buffer DMA/USB menggunakan `memcpy` tanpa konversi floating-point.
  Di `tsrossa-macos`, `setupAudioUnit` menyetel format stream ke `kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked` dengan `mBitsPerChannel = 32`, lalu di `handleRender()` mengonversi setiap sampel `int32_t` menjadi `float` 32-bit:
  ```cpp
  const float scale = m_gainLinear.load() / 2147483648.0f;
  for (uint32_t i = 0; i < framesToCopy * m_channels; ++i) {
      outBuffer[i] = (float)src[i] * scale;
  }
  ```
  Karena IEEE 754 `float` 32-bit hanya memiliki **24-bit mantissa (25-bit signed precision)**, file audio 32-bit kehilangan 7–8 bit terendahnya (LSB) akibat pemotongan mantissa. Lebih jauh lagi, klaim UI **`"Float64 Zero-Copy"`** di [`MainAppView.swift:L765`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/MainAppView.swift#L765) adalah keliru karena tipe data yang dipakai adalah `float` 32-bit (bukan `double` 64-bit) dan bukan *Zero-Copy*.

### 2.2. Hilangnya Mesin DSP 64-Bit `double`, SplitMix64 TPDF Dithering, & Pembulatan `std::llrint`
- **File & Baris:** [`CoreAudioSink.cpp:L630-L634`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L630-L634) vs [`AudioStreamer.cpp:L231-L260, L728-L772`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L231-L260).
- **Masalah:**
  Saat volume diturunkan di bawah `0 dB`, Android menghitung attenuasi dalam presisi **64-bit `double`**, menginjeksi **TPDF Dither** menggunakan PRNG `SplitMix64` yang didekorelasi antar-kanal (`fast_tpdf_dither(ch)`), dan membulatkan dengan `std::llrint()`. Di `tsrossa-macos`, tidak ada satu baris pun kode dithering atau kalkulasi `double` 64-bit.

### 2.3. Hilangnya Volume Ramping Per-Sampel (Menimbulkan Klik / Zipper Noise) & Tidak Ada Slider Volume
- **File & Baris:** [`CoreAudioSink.cpp:L303-L306, L631`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L303-L306) & [`MainAppView.swift:L3374-L3406`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/MainAppView.swift#L3374-L3406).
- **Masalah:**
  Di Android ([`AudioStreamer.cpp:L667-L674`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L667-L674)), perubahan gain diinterpolasi secara halus di setiap sampel (`currentGain += stepGain`). Di `tsrossa-macos`, `m_gainLinear.load()` dibaca satu kali per blok callback, menciptakan lompatan tangga amplitudo yang memicu bunyi klik pada perubahan volume. Selain itu, macOS hanya menyediakan tombol `-` / `+` per 1 dB tanpa slider volume kontinu, tanpa kurva *Audiophile Studio Taper* (`-48.0 * (1-v) * sqrt(1-v)`), tanpa tombol Mute, dan tanpa dukungan *Hardware Volume* DAC.

### 2.4. Tidak Ada Validasi `supportedRates` Saat Mengunci Sample Rate Perangkat Keras
- **File & Baris:** [`CoreAudioSink.cpp:L312-L338, L524`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L312-L338).
- **Masalah:**
  `setDeviceSampleRate(m_sampleRate)` langsung memanggil `AudioObjectSetPropertyData(..., kAudioDevicePropertyNominalSampleRate, ...)` tanpa memeriksa apakah sample rate lagu ada di dalam daftar `supportedRates` perangkat. Jika pengguna memutar lagu `192 kHz` pada speaker MacBook atau DAC yang maksimal mendukung `96 kHz`, penguncian hardware gagal namun `setupAudioUnit` tetap berjalan pada `192 kHz` (memicu resampling otomatis oleh macOS), sementara UI tetap mengklaim **`192.0 kHz Crystal Lock • BIT-PERFECT`**.

### 2.5. Bypass Diam-Diam "Hog Mode" pada Speaker/Headphone Built-In Mac
- **File & Baris:** [`CoreAudioSink.cpp:L355-L359`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L355-L359).
- **Masalah:**
  Pada perangkat `kAudioDeviceTransportTypeBuiltIn`, fungsi `setHogMode(true)` langsung mengembalikan `true` dan menyetel `m_isHogged = true` tanpa pernah memanggil `kAudioDevicePropertyHogMode`, namun UI dan jendela telemetri mengklaim perangkat berhasil dikunci secara eksklusif (`HOG MODE ACTIVE`).

---

## 3. Aspek 2: Arsitektur Memori, Streaming, Gapless Playback & Konkurensi

### 3.1. Dekode Penuh Sinkron ke RAM Tanpa Batas vs 64 MB Sliding-Window Ring Buffer
- **File & Baris:** [`CoreAudioSink.cpp:L501-L515`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L501-L515) vs [`AudioStreamer.cpp:L263-L415, L1239-L1256`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L263-L415).
- **Masalah:**
  Android menggunakan ring buffer terbatas **64 MB** dengan thread produsen background `TsrossaDecode` dan hanya mendekode 5 detik pertama di awal agar pemutaran dimulai dalam `< 50 ms`.
  Sebaliknya, `tsrossa-macos` mengalokasikan `m_pcmBuffer.resize(m_totalFrames * m_channels)` dan mendekode **100% isi file secara sinkron sebelum memulai pemutaran**.
  - Lagu atau rekaman konser 60 menit pada `192 kHz / 24-bit` akan mengalokasikan **~5,53 GB RAM** sekaligus dan membuat aplikasi membeku beberapa detik sebelum suara keluar.
  - Fungsi `stop()` ([`CoreAudioSink.cpp:L564-L579`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L564-L579)) hanya melakukan `munlock()` tetapi **tidak pernah mengosongkan/mengecilkan `m_pcmBuffer`**, sehingga ratusan MB hingga GB RAM tetap tertahan setelah pemutaran dihentikan.
  - Mematikan toggle RAM Lock saat lagu berjalan (`setRamLockEnabled(false)` di [`CoreAudioSink.cpp:L308-L310`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L308-L310)) tidak memanggil `munlock()`.

### 3.2. Absennya Gapless Playback (0% Implementasi) & Bug Lagu Pendek Macet Selamanya
- **File & Baris:** [`AudioEngine.swift:L346-L363`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AudioEngine.swift#L346-L363) & [`TelemetryModalView.swift:L135`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift#L135).
- **Masalah:**
  Meskipun jendela telemetri menampilkan **`Gapless Engine: ACTIVE (Direct Handover)`**, tidak ada implementasi gapless sama sekali di `tsrossa-macos`. Pergantian lagu dipicu oleh timer `0.25` detik di Main Thread yang mengecek:
  ```swift
  if !stillPlaying && self.totalDuration > 0 && t >= self.totalDuration - 0.5
  ```
  Selain menimbulkan jeda hening ratusan milidetik di antara setiap lagu, kondisi `t >= self.totalDuration - 0.5` menyebabkan **pemutaran berhenti selamanya (macet)** apabila lagu berdurasi `< 0.5` detik atau jumlah sampel aktual berbeda `> 0.5` detik dari estimasi header!

### 3.3. Data Race pada Callback Real-Time, Hilangnya CAS Saat Seek, & Pointer Global Tidak Aman
- **File & Baris:** [`CoreAudioSink.cpp:L482-L520, L590-L637`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L482-L520) & [`TsrossaAudioBridge.cpp:L6-L57, L124-L127`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/TsrossaAudioBridge.cpp#L6-L57).
- **Masalah:**
  1. `playTrack()` memodifikasi `m_sampleRate`, `m_channels`, `m_totalFrames` (ketiganya non-atomik) dan `m_pcmBuffer.resize()` **sebelum** memanggil `teardownAudioUnit()`.
  2. `handleRender()` memajukan `m_currentFrame` dengan `.store(currentFrame + framesToCopy)` biasa alih-alih `compare_exchange_strong` (seperti di Android [`VolumeAndStreamingMathTest.kt:L456-L470`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/test/java/com/yuka/musicplayer/audio/VolumeAndStreamingMathTest.kt#L456-L470)), sehingga `seekTo()` yang terjadi bersamaan dapat tertimpa kembali ke posisi lama.
  3. `s_cachedDevices` dan `s_lastTelemetry` di `TsrossaAudioBridge.cpp` adalah variabel statis global tanpa mutex yang mengembalikan pointer mentah `.c_str()` lintas thread.
  4. `progressTimer` di `AudioEngine.swift:L349` tidak mengecek flag `isSeeking`, menyebabkan slider seek melompat balik saat digeser.

---

## 4. Aspek 3: Metadata, Database Library, Sorting, Pencarian & Lirik

### 4.1. Ketiadaan SQLite Cache & Kegagalan `AVURLAsset` pada Tag Vorbis / Artwork / Lirik FLAC
- **File & Baris:** [`MetadataScanner.swift:L12-L181`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/MetadataScanner.swift#L12-L181), [`Theme.swift:L168-L176`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Theme.swift#L168-L176), [`LyricsManager.swift:L49-L64`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/LyricsManager.swift#L49-L64).
- **Masalah:**
  - Tidak ada SQLite cache seperti `MusicLibraryDatabaseHelper.kt` (`< 20 ms` cold start + incremental diff scan). Setiap kali aplikasi dibuka, seluruh direktori dipindai ulang dari nol.
  - Parser biner di `MetadataScanner.swift` hanya membaca blok `STREAMINFO` (sample rate & bit depth), sementara pembacaan tag teks (`Title`, `Artist`, `Album`), gambar sampul (`ArtworkExtractor`), dan lirik embedded (`LyricsManager`) diserahkan ke `AVURLAsset`. Di macOS, `AVURLAsset` sering gagal memetakan Vorbis Comments dan `METADATA_BLOCK_PICTURE` pada file `.flac` ke `.commonKey`, sehingga judul/artis/sampul/lirik embedded FLAC gagal terbaca.
  - Atribut metadata penting seperti `albumArtist`, `genre`, `trackNumber`, `year`, `composer`, `bitrateKbps`, dan deteksi **MQA** (`detectMqa()` di `MetadataExtractor.kt:L135-L159`) hilang seluruhnya.

### 4.2. Tiga Bug Logika pada Sistem Pengurutan (Sorting) Library
- **File & Baris:** [`MetadataScanner.swift:L60, L178`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/MetadataScanner.swift#L60) & [`AppViewModel.swift:L125, L679-L698`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L125).
- **Masalah:**
  1. Saat startup, `sortMode` bertuliskan `"DATE (NEWEST)"`, padahal `fastScanDirectory()` mengurutkan lagu berdasarkan **Judul A–Z**.
  2. Ketika pengguna menekan tombol sort hingga kembali ke `"DATE (NEWEST)"` (`AppViewModel.swift:L695-L697`), kode hanya memanggil `tracks.reverse()` pada array yang sebelumnya diurutkan berdasarkan **Artis A–Z** (menghasilkan urutan **Artis Z–A**, sama sekali bukan berdasarkan tanggal file!).
  3. Saat `enrichMetadataInBackground` selesai berjalan di background (`AppViewModel.swift:L681`), ia menimpa `self.tracks = enriched` dan menghapus urutan sort yang baru saja dipilih pengguna.

### 4.3. Pencarian Substring Biasa vs `FuzzySearchHelper` & 10 Pengaturan Tidak Tersimpan
- **File & Baris:** [`AppViewModel.swift:L115-L185, L225-L232`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L115-L185).
- **Masalah:**
  - Pencarian hanya menggunakan `localizedCaseInsensitiveContains(query)`, tidak mendukung *multi-token out-of-order* maupun *subsequence fuzzy match* seperti [`FuzzySearchHelper.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/util/FuzzySearchHelper.kt).
  - 10 pengaturan utama (`volumeDb`, `isExclusiveHogMode`, `isAutoClockSwitch`, `isRamPlayback`, `accentColorMode`, `bgCanvasMode`, `isShuffle`, `isRepeat`, `sortMode`, `selectedDeviceId`) **tidak pernah disimpan ke `UserDefaults`** dan selalu kembali ke default setiap kali aplikasi dijalankan ulang.

---

## 5. Aspek 4: UI/UX, Telemetri Palsu (Placebo), Fitur yang Hilang & Integrasi macOS

### 5.1. Jendela Telemetri (`TelemetryModalView.swift`) 90% Berisi Teks Palsu Hardcoded
- **File & Baris:** [`TelemetryModalView.swift:L122-L162`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift#L122-L162).
- **Masalah:**
  - UI tidak pernah mem-parse `engine.telemetryJson`.
  - Menampilkan `"HOG MODE ACTIVE"` meskipun Hog Mode dimatikan, `"ACTIVE (Zero Jitter / mlock)"` meskipun RAM Lock dimatikan, `"Gapless Engine: ACTIVE (Direct Handover)"` meskipun fitur gapless tidak ada, dan `"DSP Pipeline: BYPASSED (Bit-Perfect 0 dBFS)"` meskipun volume sedang dikecilkan.
  - Bagian `FRAMEWORK LOG` mencetak 5 kalimat statis dengan memanggil `currentTimestamp()` (`Date()`) di dalam fungsi render view, sehingga setiap `0,25` detik (saat progress bar lagu bergerak), **timestamp pada kelima baris log ikut berubah maju mengikuti jam saat ini**.

### 5.2. Variabel State Mati (`keepScreenAwake`, `bufferProfile`, `themeMode`)
- **File & Baris:** [`AppViewModel.swift:L156-L160`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L156-L160).
- **Masalah:** Ketiga properti `@Published` tersebut dideklarasikan tetapi tidak pernah dihubungkan ke `IOPMAssertionCreateWithName`, `kAudioDevicePropertyBufferFrameSize`, maupun sistem tema Light/OLED.

### 5.3. Pembajakan Tombol Panah Keyboard, Absennya Media Keys macOS, & Konflik `isRepeat`
- **File & Baris:** [`AppViewModel.swift:L589-L600, L708-L865`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L589-L600) & [`AudioEngine.swift:L355-L359`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AudioEngine.swift#L355-L359).
- **Masalah:**
  - `NSEvent.addLocalMonitorForEvents` membajak tombol panah Kiri/Kanan/Atas/Bawah tanpa modifier di seluruh aplikasi, merusak navigasi daftar standar macOS.
  - Tidak ada integrasi `MPNowPlayingInfoCenter` / `MPRemoteCommandCenter` (tombol media `F7`/`F8`/`F9`, AirPods, dan Control Center tidak berfungsi).
  - `isRepeat` di `AudioEngine.swift:L355` berperilaku sebagai `REPEAT ONE`, sedangkan di `AppViewModel.swift:L598` berperilaku sebagai `REPEAT ALL`, membuat pengguna tidak bisa memutar ulang satu playlist/album secara otomatis (`REPEAT ALL`).

---

## 6. Aspek 5: Build System, Packaging, Keamanan Updater & Unit Testing

### 6.1. Kegagalan Build Mandiri (`build_mac.sh`) & Hardcoded Path `/Users/alen`
- **File & Baris:** [`build_mac.sh:L4-L16, L42-L44`](file:///Users/yuka/Documents/Projects/tsrossa-macos/build_mac.sh#L4-L16), [`dr_impl.c:L3-L7`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/dr_impl.c#L3-L7), [`MacAudioDecoder.h:L13-L16`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/MacAudioDecoder.h#L13-L16), [`Theme.swift:L378-L392`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Theme.swift#L378-L392).
- **Masalah:**
  - `dr_flac.h` dan `dr_wav.h` tidak ada di dalam repositori `tsrossa-macos` (masih mengarah ke `../../app/src/main/cpp/`), sehingga `./build_mac.sh` langsung gagal kompilasi.
  - Path fallback ikon di `Theme.swift` dan folder musik di `AppViewModel.swift` di-hardcode ke `/Users/alen/...`, menyebabkan aplikasi kehilangan seluruh ikon dan logo brand pada komputer pengguna lain.
  - `build_mac.sh` tidak menyertakan flag `-arch arm64 -arch x86_64` meskipun `README.md` mengklaim mendukung Apple Silicon dan Intel.

### 6.2. Kerentanan & Bug Logika pada `UpdateManager.swift` + 0 Unit Test
- **File & Baris:** [`UpdateManager.swift:L8-L9, L109-L119, L149-L169, L189-L298`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/UpdateManager.swift#L8-L9).
- **Masalah:**
  - Mengarah ke repositori `RazerZS-oss/tsrossa` (berbeda dari repo Android `yukaatsu/tsrossa` dan `README.md` `yukars19/tsrossa-music-player`).
  - Jika rilis GitHub tidak memiliki asset bertuliskan `"mac"`, fallback di baris 109–119 akan mengunduh **file `.zip` apa saja** yang ada di rilis tersebut.
  - Menggunakan direktori statis `/tmp/tsrossa_update/restart.sh` dan menimpa `/Applications/tsrossa.app` tanpa memeriksa `codesign` maupun `CFBundleIdentifier`.
  - Membuang suffix pre-release (`-beta1`) saat membandingkan versi, sehingga gagal mendeteksi pembaruan dari versi beta ke versi stabil.
  - Memiliki **0 file unit test**, berbanding terbalik dengan 17 test suite di Android.

---

## 7. Aspek 6: UI/UX Themes, Standar Pemrograman Swift, Apple HIG & Desain "Liquid Glass"

### 7.1. Krisis Identitas Desain ("Material 3 Android" vs Apple HIG "Liquid Glass") & Kaca Palsu
- **File & Baris:** [`Theme.swift:L5-L130, L344-L366`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Theme.swift#L344-L366) & [`MainAppView.swift:L29-L77, L3005-L3030, L3478, L3494`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/MainAppView.swift#L3005-L3030).
- **Masalah:**
  - Aplikasi menyalin sistem warna permukaan bertingkat **Google Android Material 3 ("Material You / Monet")** dengan warna solid/semi-pekat, bahkan memberi label `"MATERIAL YOU & DESKTOP APPEARANCE"`, `"Auto (Monet)"`, dan `"Tonal M3"` pada halaman Settings macOS.
  - `GlassmorphicSurface` di `Theme.swift:L344-L366` sama sekali tidak memakai `.ultraThinMaterial`, `.thinMaterial`, ataupun `NSVisualEffectView` (hanya `RoundedRectangle.fill(surfaceContainer.opacity(0.85))` tanpa blur optik), dan modifier `.glassmorphic()` tersebut bahkan **tidak pernah dipanggil satu kali pun (0 referensi)** di seluruh aplikasi.
  - Struktur ruang kerja memakai `VStack` / `HStack` kaku yang memisahkan Top Bar, konten `ScrollView`, dan Bottom Transport Bar dengan garis `Rectangle()` 1pt (`MainAppView.swift:L29-L77`). Karena konten daftar lagu/sampul album tidak pernah mengalir di bawah (*scroll underneath*) bar atas maupun bar bawah, efek pembiasan cahaya (**Liquid Glass refraction**) tidak dapat terjadi.
  - Tidak ada **Anisotropic Specular Rim Highlight** (gradien kilau cahaya di bibir kaca) dan tidak ada satupun penggunaan geometri **Apple Squircle (`style: .continuous`)** — seluruh 60+ sudut lengkung memakai modifier `.cornerRadius()` yang sudah *deprecated* dengan busur `.circular` biasa.

### 7.2. Pelanggaran Aturan Pemrograman Swift/SwiftUI ("God View" 3.579 Baris & API Deprecated)
- **File & Baris:** [`MainAppView.swift:L4-L154`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/MainAppView.swift#L4-L154) & [`AppViewModel.swift:L87-L162`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L87-L162).
- **Masalah:**
  - **Anti-Pattern "God View":** Seluruh tampilan aplikasi ditumpuk di dalam satu `struct MainAppView: View` sepanjang **3.579 baris** yang mengamati `@ObservedObject private var engine = AudioEngine.shared`. Karena `engine.currentTime` diperbarui setiap `0.25` detik selama pemutaran, SwiftUI menginvalidasi dan mengevaluasi ulang `body` `MainAppView` secara keseluruhan 4 kali per detik.
  - **Ratusan API SwiftUI Deprecated & Stringly-Typed State:** Terdapat 200+ pemanggilan `.foregroundColor()` (seharusnya `.foregroundStyle()` dengan dukungan vibrancy `.primary`/`.secondary`/`.tertiary`), 60+ `.cornerRadius()`, `.accentColor()` (seharusnya `.tint()`), state bertipe `String` mentah (seharusnya `enum` Swift `Codable, CaseIterable`), serta modal `ZStack` manual yang tidak bisa ditutup dengan tombol `Esc` dan tidak mendukung `@Environment(\.accessibilityReduceTransparency)` maupun `@Environment(\.colorScheme)`.

---

## 8. Peta Jalan Perbaikan Prioritas (Remediation Roadmap)

1. **Tahap 1 — Perbaikan Build Mandiri & Keamanan Dasar (1 Hari):**
   - Salin `dr_flac.h` dan `dr_wav.h` ke `src/nativeMain/cpp/` serta ikon ke `macos/Resources/`. Perbaiki path di `build_mac.sh`, `dr_impl.c`, `MacAudioDecoder.h`, `Theme.swift`, dan `AppViewModel.swift`.
   - Perbaiki `UpdateManager.swift` (gunakan direktori cache unik per-UUID, wajibkan filter asset macOS, verifikasi `CFBundleIdentifier` & `codesign`, dan porting `comparePreReleaseSuffix`).
2. **Tahap 2 — Rekonstruksi Audio Engine C++ & Memori Ring Buffer (2–3 Hari):**
   - Porting **64 MB Sliding-Window Ring Buffer**, thread decode background `TsrossaDecode`, dan pre-buffer **Gapless 32 MB (`prepareNextTrack`)** dari `AudioStreamer.cpp` ke `CoreAudioSink.cpp`.
   - Implementasikan jalur DSP **64-bit `double`**, **Frame-Locked Volume Ramping**, **SplitMix64 Dual-Channel TPDF Dithering**, dan `std::llrint()`, serta negosiasi format integer fisik (`kAudioFormatFlagIsSignedInteger`) pada USB DAC.
   - Tambahkan validasi `supportedRates` dengan fallback kelipatan terdekat di `setDeviceSampleRate()`, gunakan `compare_exchange_strong` pada `m_currentFrame`, dan tambahkan `std::mutex` di `TsrossaAudioBridge.cpp`.
3. **Tahap 3 — Parser Biner Metadata, SQLite Cache & Perbaikan State/UI (2 Hari):**
   - Porting parser biner `AudioTagReader.kt` (Vorbis Comment FLAC blok 4, Picture blok 6, RIFF INFO/ID3 WAV, dan deteksi MQA) ke `MetadataScanner.swift` agar tidak bergantung pada `AVURLAsset`.
   - Tambahkan SQLite cache inkremental, perbaiki logika `toggleSortMode()` (simpan `dateModified`), porting `FuzzySearchHelper` dan `ColorContrastHelper` (12-Bin Hue-Family clustering), serta simpan seluruh 10 pengaturan yang hilang ke `UserDefaults`.
   - Ganti seluruh teks palsu di `TelemetryModalView.swift` dengan parser JSON telemetri nyata + ring buffer logger, ubah `isRepeat` menjadi enum 3-state (`off`, `all`, `one`) dengan shuffle deck, hapus pembajakan tombol panah polos di keyboard monitor, dan integrasikan `MPNowPlayingInfoCenter` / `MPRemoteCommandCenter` serta `IOPMAssertionCreateWithName`.
4. **Tahap 4 — Transformasi Desain "Liquid Glass" Apple HIG & Modernisasi Kode SwiftUI (2 Hari):**
   - Ganti `DynamicMaterialTheme` (Material 3 Android) di `Theme.swift` dengan arsitektur **Apple Liquid Glass** (`LiquidGlassModifier` berbasis `.ultraThinMaterial` / `NSVisualEffectView`, *anisotropic specular rim stroke*, *surface specular sheen*, geometri squircle `style: .continuous`, dan adaptasi `@Environment(\.colorScheme)` + `accessibilityReduceTransparency`).
   - Ubah layout `MainAppView` menjadi `NavigationSplitView` dengan `.safeAreaInset(edge: .top / .bottom)` agar konten album/daftar lagu mengalir dan terbiaskan di balik kapsul kaca Transport Bar.
   - Pecah `MainAppView.swift` (3.579 baris) menjadi sub-view modular terisolasi dengan `@Observable`, ganti state `String` dengan `enum` bertipe kuat, dan migrasi seluruh modifier deprecated (`.cornerRadius`, `.foregroundColor`, `.accentColor`) ke standar SwiftUI terbaru.
