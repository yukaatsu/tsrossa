# Audit & Analisa Aspek 1: Arsitektur Audio Engine, Signal Path, DSP, & CoreAudio HAL

**Target Audit:** `/Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/` (`CoreAudioSink.cpp`, `CoreAudioSink.h`, `MacAudioDecoder.h`, `MacAudioCommon.h`, `TsrossaAudioBridge.cpp`)  
**Acuan Ground-Truth (Read-Only):** `/Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/` (`AudioStreamer.cpp`, `UsbDacController.cpp`, `AudioEngineState.h`, `AudioDecoder.h`, `AudioCommon.h`, `native-lib.cpp`)

---

## 1. Ringkasan Temuan Kritis pada Audio Engine

Meskipun antarmuka pengguna `tsrossa-macos` menampilkan klaim seperti **`BIT-PERFECT`**, **`Float64 Zero-Copy`**, **`0.0 dBFS Bit-Perfect`**, dan **`HOG MODE ACTIVE`**, hasil audit kode sumber C++ menunjukkan bahwa **seluruh jalur pemrosesan sinyal (signal path) pada versi macOS mengalami degradasi presisi dan kehilangan fitur inti dibandingkan mesin C++ asli di Android (`tsrossa-ujicoba`)**.

| Komponen / Fitur DSP | Android Asli (`tsrossa-ujicoba`) | Porting macOS (`tsrossa-macos`) | Status Paritas / Keparahan |
| :--- | :--- | :--- | :--- |
| **Format Stream Output Hardware** | Integer Murni (`int32_t` / `S24_3LE` / `S16_LE`) via USB Isochronous URB atau AAudio `PCM_I32` / `PCM_I24_PACKED` | Dipaksa ke **32-bit IEEE 754 Float** (`kAudioFormatFlagIsFloat`, `mBitsPerChannel = 32`) | **KRITIS (Bukan Bit-Perfect Murni)** |
| **Jalur Bit-Perfect 0 dBFS** | `bitPerfectDirectPassActive`: `memcpy` langsung `int32_t` tanpa konversi floating-point (`AudioStreamer.cpp:L682-L702`) | Tetap dibagi `2147483648.0f` dan di-cast ke `float` 32-bit di setiap sampel (`CoreAudioSink.cpp:L630-L634`) | **KRITIS (Truncated 24-bit Mantissa)** |
| **Presisi Attenuasi Volume Digital** | **64-bit `double`** (`targetGain`, `currentGain`, `stepGain` dalam `double`) (`AudioStreamer.cpp:L656-L751`) | **32-bit `float`** (`std::atomic<float> m_gainLinear`) meskipun UI mengklaim `"Float64 Zero-Copy"` (`MainAppView.swift:L765`) | **TINGGI (Klaim UI Palsu & Degradasi Presisi)** |
| **TPDF Dithering (Kuantisasi)** | Dual-channel decorrelated **SplitMix64 PRNG TPDF Dither** (`fast_tpdf_dither(ch)`) pada skala LSB target (`16-bit` = `65536.0`, `24-bit` = `256.0`) (`AudioStreamer.cpp:L231-L260, L741-L744`) | **TIDAK ADA** (0 baris kode dithering; pemotongan langsung ke `float`) | **KRITIS (Distorsi Kuantisasi Nonlinear)** |
| **Pembulatan Kuantisasi Akhir** | Mid-tread unbiased rounding via `std::llrint()` (`AudioStreamer.cpp:L748`) | **TIDAK ADA** (bergantung pada konversi implisit `float` di dalam mixer CoreAudio) | **TINGGI** |
| **Volume Ramping / Anti-Zipper Noise** | Frame-locked linear gain interpolation per-sampel (`currentGain += stepGain`) (`AudioStreamer.cpp:L667-L674, L733-L735`) | **TIDAK ADA** (`m_gainLinear.load()` dibaca sekali per buffer callback; perubahan volume melompat instan antar-buffer) | **TINGGI (Potensi Klik / Zipper Noise)** |
| **Kurva Volume & Kontrol Halus** | Kurva psikoakustik studio kontinu `0.0..1.0` (`dB = -48.0 * (1-v) * sqrt(1-v)`) + Hybrid Hardware UAC Volume (`AudioStreamer.cpp:L164-L215`) | Hanya tombol kasar `-` / `+` per **1 dB bulat** (`-60 dB` s/d `0 dB`), tanpa slider, tanpa mute, tanpa kontrol volume hardware CoreAudio | **SEDANG-TINGGI** |
| **Negosiasi Sample Rate Hardware** | Mencocokkan `supportedRates` descriptor USB / AAudio; fallback otomatis ke kelipatan terdekat jika DAC tidak mendukung rate asli (`UsbDacController.cpp:L680-L745`) | Memanggil `setDeviceSampleRate(m_sampleRate)` secara buta tanpa memeriksa `DeviceInfo::supportedRates` (`CoreAudioSink.cpp:L312-L338, L524`) | **TINGGI (Resampling OS Tersembunyi)** |
| **Exclusive Access (Hog Mode)** | Kernel USB Interface Claim (`USBDEVFS_CLAIMINTERFACE`) / AAudio `EXCLUSIVE` mode | Melewati `kAudioDevicePropertyHogMode` secara diam-diam pada port Built-In namun tetap melaporkan `true` ke UI (`CoreAudioSink.cpp:L355-L359`) | **SEDANG (Indikator Telemetri Menyesatkan)** |
| **DAC Warmup / Anti-Pop PLL Silence** | Pre-fill silence terukur pada inisialisasi stream + micro fade-in saat pergantian sample rate | Hanya `usleep(100000)` (100ms blocking sleep di thread pemutaran) saat sample rate berubah (`CoreAudioSink.cpp:L334`) | **SEDANG** |

---

## 2. Bedah Teknis Temuan demi Temuan

### 2.1. Palsunya Jalur "Bit-Perfect" & Kehilangan Presisi 32-Bit Integer akibat Konversi Paksa ke `float` 32-Bit
- **Lokasi Kode macOS:**
  - [`CoreAudioSink.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L425-L437) (`CoreAudioSink::setupAudioUnit`)
  - [`CoreAudioSink.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L630-L634) (`CoreAudioSink::handleRender`)
- **Lokasi Acuan Android:**
  - [`AudioStreamer.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L682-L773) (`processUnifiedDsp`)
- **Analisa Reverse Engineering:**
  Pada aplikasi Android asli (`tsrossa-ujicoba`), decoder (`dr_flac` dan `dr_wav`) menghasilkan sampel PCM bertipe `int32_t` (left-justified / MSB-aligned 32-bit signed integer). Ketika volume berada pada `1.0f` (`0.0 dBFS`), Android mengaktifkan jalur **`bitPerfectDirectPassActive`**:
  ```cpp
  // Android: AudioStreamer.cpp:L682-L702
  const bool bitPerfectDirectPassActive =
      (targetGain >= 0.99999f && currentGain >= 0.99999f && !needsFade);
  if (bitPerfectDirectPassActive) {
      if (targetFormat == 1) { // PCM_I32
          memcpy(outputBuffer, rawPcm, numSamples * sizeof(int32_t));
      }
      // ...
  }
  ```
  Sebaliknya, pada `tsrossa-macos`, `CoreAudioSink::setupAudioUnit` mengonfigurasi `AudioStreamBasicDescription` (ASBD) input `HALOutput` AudioUnit sebagai **32-bit Floating Point**:
  ```cpp
  // macOS: CoreAudioSink.cpp:L425-L437
  AudioStreamBasicDescription streamFormat = {0};
  streamFormat.mSampleRate = (Float64)m_sampleRate;
  streamFormat.mFormatID = kAudioFormatLinearPCM;
  streamFormat.mFormatFlags = kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked;
  streamFormat.mFramesPerPacket = 1;
  streamFormat.mChannelsPerFrame = m_channels;
  streamFormat.mBitsPerChannel = 32;
  ```
  Kemudian di dalam callback real-time `CoreAudioSink::handleRender`:
  ```cpp
  // macOS: CoreAudioSink.cpp:L630-L634
  const int32_t* src = m_pcmBuffer.data() + (currentFrame * m_channels);
  const float scale = m_gainLinear.load() / 2147483648.0f;
  for (uint32_t i = 0; i < framesToCopy * m_channels; ++i) {
      outBuffer[i] = (float)src[i] * scale;
  }
  ```
- **Dampak Nyata:**
  1. **Hilangnya Presisi File 32-Bit Integer:** Format IEEE 754 `float` 32-bit hanya memiliki **23 bit mantissa eksplisit + 1 bit implisit + 1 bit tanda = 25 bit presisi signed integer**. Ketika file WAV/FLAC 32-bit (`int32_t`) dikonversi dengan `(float)src[i]`, **7 hingga 8 bit terbawah (LSB) langsung terpotong (truncated)** sebelum menyentuh CoreAudio HAL.
  2. **Kebohongan Indikator UI `"Float64 Zero-Copy"`:** Di [`MainAppView.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/MainAppView.swift#L765), layar *Signal Path* menampilkan teks statis `"Float64 Zero-Copy"`. Faktanya, tidak ada satu pun variabel `double` (`Float64`) yang dipakai dalam pemrosesan sampel di `CoreAudioSink.cpp` (semuanya `float` 32-bit), dan prosesnya bukan *Zero-Copy* melainkan menyalin sambil mengalikan setiap sampel di dalam loop CPU.
  3. **Bukan Integer Mode CoreAudio:** Untuk mencapai *true integer bit-perfect* di macOS pada USB DAC eksternal, aplikasi seharusnya mencoba mengatur `kAudioStreamPropertyPhysicalFormat` / `kAudioDevicePropertyStreamFormat` dengan `kAudioFormatFlagIsSignedInteger` (24-bit atau 32-bit packed integer) atau minimal melakukan kalkulasi DSP di domain `double` (64-bit float) dengan TPDF dither sebelum kuantisasi ke lebar bit fisik DAC.

---

### 2.2. Absennya SplitMix64 TPDF Dithering & Mid-Tread Rounding (`std::llrint`)
- **Lokasi Kode macOS:** [`CoreAudioSink.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L630-L634)
- **Lokasi Acuan Android:** [`AudioStreamer.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L231-L260) & [`AudioStreamer.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L728-L772)
- **Analisa Reverse Engineering:**
  Di Android (`AudioStreamer.cpp`), ketika pengguna mengecilkan volume (`currentGain < 0.99999`), setiap sampel diproses dalam presisi **64-bit `double`**, lalu ditambahkan **Triangular Probability Density Function (TPDF) dither** yang didekorelasi antar-kanal menggunakan generator PRNG `SplitMix64` (`fast_tpdf_dither(ch)`) tepat pada skala LSB target DAC (`65536.0` untuk DAC 16-bit, `256.0` untuk DAC 24-bit), kemudian dibulatkan secara simetris (*unbiased mid-tread rounding*) menggunakan `std::llrint(scaled)`.
  Di `tsrossa-macos`, seluruh mekanisme matematis tersebut **dihapus total**. Perkalian volume dilakukan langsung dalam `float` 32-bit tanpa dither sama sekali, lalu diserahkan ke CoreAudio untuk dipotong ke integer DAC. Hal ini menimbulkan distorsi harmonik kuantisasi (*quantization distortion*) dan *noise modulation* pada sinyal berlevel rendah (fade-out musik klasik/jazz) ketika volume berada di bawah `0 dB`.

---

### 2.3. Tidak Ada Volume Ramping / Smoothing (Menyebabkan Zipper Noise / Klik Audio)
- **Lokasi Kode macOS:** [`CoreAudioSink.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L303-L306) & [`CoreAudioSink.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L631-L634)
- **Lokasi Acuan Android:** [`AudioStreamer.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L664-L674)
- **Analisa Reverse Engineering:**
  Pada `CoreAudioSink::setVolumeDb(float db)`:
  ```cpp
  // macOS: CoreAudioSink.cpp:L303-L306
  void CoreAudioSink::setVolumeDb(float db) {
      if (db >= 0.0f) m_gainLinear = 1.0f;
      else if (db <= -60.0f) m_gainLinear = 0.0f;
      else m_gainLinear = std::pow(10.0f, db / 20.0f);
  }
  ```
  Di dalam `handleRender()`, nilai `m_gainLinear.load()` diambil satu kali untuk seluruh blok `inNumberFrames`. Ketika pengguna menekan tombol volume `-` atau `+`, amplitudo gelombang audio melompat secara diskontinu (tangga vertikal) tepat di batas pergantian buffer callback. Pada sinyal frekuensi rendah (bass/sub-bass), diskontinuitas fase/amplitudo ini menghasilkan bunyi **"klik" / *zipper noise*** yang terdengar jelas di headphone sensitif/IEM.
  Pada versi Android (`AudioStreamer.cpp:L667-L674`), perubahan gain diinterpolasi secara linear per-frame (`stepGain = (targetGain - currentGain) / numFrames; currentGain += stepGain;`), sehingga transisi gain selalu mulus dan bebas klik.

---

### 2.4. Kegagalan Fallback Sample Rate (Memicu Resampling OS Tersembunyi Sambil Mengklaim "Crystal Lock")
- **Lokasi Kode macOS:**
  - [`CoreAudioSink.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L186-L210) (`getAudioDevices` mengambil `supportedRates`)
  - [`CoreAudioSink.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L312-L338) (`setDeviceSampleRate`)
  - [`CoreAudioSink.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L523-L526) (`playTrack`)
- **Lokasi Acuan Android:** [`UsbDacController.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L680-L745)
- **Analisa Reverse Engineering:**
  Meskipun `CoreAudioSink::getAudioDevices()` sudah membaca `kAudioDevicePropertyAvailableNominalSampleRates` dan menyimpannya ke `DeviceInfo::supportedRates`, fungsi `CoreAudioSink::setDeviceSampleRate(uint32_t sampleRate)` **sama sekali tidak memeriksa apakah `sampleRate` file didukung oleh perangkat audio tujuan**:
  ```cpp
  // macOS: CoreAudioSink.cpp:L312-L338
  bool CoreAudioSink::setDeviceSampleRate(uint32_t sampleRate) {
      if (m_currentDevice == kAudioObjectUnknown) return false;
      Float64 targetRate = (Float64)sampleRate;
      // Langsung memanggil AudioObjectSetPropertyData tanpa kroscek supportedRates!
  ```
  Lebih buruk lagi, di `playTrack()` (`CoreAudioSink.cpp:L524`), nilai kembalian (`bool`) dari `setDeviceSampleRate(m_sampleRate)` **diabaikan begitu saja**, lalu `setupAudioUnit(m_sampleRate, m_channels)` tetap mengatur input AudioUnit ke `m_sampleRate` asli file!
- **Dampak Nyata:**
  Jika pengguna memutar lagu Hi-Res `192 kHz` atau `352.8 kHz` (DXD) pada speaker internal MacBook (yang hanya mendukung hingga `96 kHz`) atau DAC USB yang hanya mendukung hingga `96 kHz`:
  1. `setDeviceSampleRate(192000)` gagal dengan error CoreAudio, sehingga clock fisik hardware tetap terkunci di `48 kHz` atau `96 kHz`.
  2. `setupAudioUnit(192000, ...)` tetap memberi makan AudioUnit dengan stream `192 kHz`.
  3. CoreAudio secara otomatis mengaktifkan **software sample rate converter (SRC) milik macOS** di balik layar.
  4. Namun UI aplikasi (`MainAppView.swift:L753, L3050`) hanya membaca metadata track (`track.sampleRate`) dan menampilkan badge **`192.0 kHz Crystal Lock • BIT-PERFECT`** serta **`Hardware PLL Locked`** (`MainAppView.swift:L755`), tanpa pernah membaca balik `kAudioDevicePropertyNominalSampleRate` aktual dari perangkat!

---

### 2.5. Bypass Diam-Diam "Hog Mode" pada Perangkat Built-In Apple Silicon
- **Lokasi Kode macOS:** [`CoreAudioSink.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L340-L375)
- **Analisa Reverse Engineering:**
  Di dalam `CoreAudioSink::setHogMode(bool enable)`:
  ```cpp
  // macOS: CoreAudioSink.cpp:L355-L359
  if (transport == kAudioDeviceTransportTypeBuiltIn) {
      // Built-in Apple Silicon ports prefer standard shared access with integer PLL switching
      m_isHogged = enable;
      return true;
  }
  ```
  Ketika menggunakan speaker internal Mac atau port headphone 3.5mm bawaan MacBook (`kAudioDeviceTransportTypeBuiltIn`), kode ini **tidak pernah memanggil `AudioObjectSetPropertyData(..., kAudioDevicePropertyHogMode, ...)`**, tetapi langsung menyetel `m_isHogged = true` dan mengembalikan `true`. Akibatnya, aplikasi lain di macOS tetap bebas mencampur (mix) suara notifikasi ke output yang sama melalui mixer OS, sementara UI `tsrossa-macos` dan `TelemetryModalView` mengklaim secara keliru bahwa **`kAudioDevicePropertyHogMode engaged`** dan **`Exclusive Lock: HOG MODE ACTIVE`**.

---

## 3. Rekomendasi Perbaikan Teknis (Actionable Fixes)

1. **Implementasi Jalur DSP 64-Bit `double` + SplitMix64 TPDF Dithering + Volume Ramping:**
   - Porting fungsi `fast_tpdf_dither(ch)` dan interpolasi gain per-frame (`currentGain += stepGain`) dari [`AudioStreamer.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L231-L260) ke `CoreAudioSink::handleRender()`.
   - Untuk mode *Integer Device* pada USB DAC yang mendukung `kAudioFormatFlagIsSignedInteger` di `kAudioStreamPropertyPhysicalFormat`, negosiasikan ASBD integer (`24-bit` / `32-bit` signed integer) sehingga saat volume `0.0 dBFS`, `memcpy` bit-perfect sejati dapat dilakukan tanpa konversi `float`.
2. **Validasi & Fallback Sample Rate Hardware + Pembacaan Balik Rate Aktual:**
   - Di `setDeviceSampleRate()`, periksa `kAudioDevicePropertyAvailableNominalSampleRates`. Pilih sample rate tertinggi yang didukung dari kelipatan genap (`176.4k -> 88.2k -> 44.1k` atau `192k -> 96k -> 48k`), lalu baca kembali `kAudioDevicePropertyNominalSampleRate` aktual setelah penguncian PLL dan ekspos nilainya ke telemetri/UI agar pengguna mengetahui sample rate perangkat keras yang sesungguhnya.
3. **Kejujuran Status Hog Mode:**
   - Pisahkan flag `m_isHogged` dari preferensi `m_hogModeEnabled`. Jika perangkat adalah `kAudioDeviceTransportTypeBuiltIn` dan `kAudioDevicePropertyHogMode` dilewati, laporkan status sebagai `"SHARED (Built-In Apple Audio)"` alih-alih mengklaim `"HOG MODE ACTIVE"`.
