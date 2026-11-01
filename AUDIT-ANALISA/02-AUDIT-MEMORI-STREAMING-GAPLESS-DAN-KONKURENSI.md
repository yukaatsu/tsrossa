# Audit & Analisa Aspek 2: Arsitektur Memori, Streaming, Gapless Playback, & Konkurensi C++/Swift

**Target Audit:**
- [`CoreAudioSink.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp)
- [`CoreAudioSink.h`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.h)
- [`TsrossaAudioBridge.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/TsrossaAudioBridge.cpp)
- [`AudioEngine.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AudioEngine.swift)

**Acuan Ground-Truth (Read-Only):**
- [`AudioCommon.h`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioCommon.h)
- [`AudioEngineState.h`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioEngineState.h)
- [`AudioStreamer.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp)

---

## 1. Perbandingan Arsitektur Memori & Streaming (Android vs macOS)

| Dimensi Arsitektur | Android Asli (`tsrossa-ujicoba`) | Porting macOS (`tsrossa-macos`) | Risiko & Keparahan |
| :--- | :--- | :--- | :--- |
| **Model Buffering Audio** | **64 MB Sliding-Window Ring Buffer** (`MAX_RING_BUFFER_BYTES = 64 * 1024 * 1024`) dengan thread decode background `TsrossaDecode` (`AudioCommon.h:L22`, `AudioStreamer.cpp:L263-L415`) | **Full-File Synchronous RAM Allocation** (`m_pcmBuffer.resize(m_totalFrames * m_channels)`) — seluruh file didekode habis sebelum suara mulai keluar (`CoreAudioSink.cpp:L501-L506`) | **KRITIS (Batas Memori Tak Terbatas & Latensi Start Tinggi)** |
| **Waktu Mulai Putar (Time-to-First-Byte)** | **< 50 ms** (hanya mendekode 5 detik pertama di awal, sisanya di-stream di background: `AudioStreamer.cpp:L1239-L1256`) | **Blocking ratusan ms s/d beberapa detik** tergantung durasi/sample rate file karena mendekode 100% file di `engineQueue` sebelum `AudioOutputUnitStart` | **TINGGI (Jeda Saat Ganti Lagu)** |
| **Manajemen `mlock()` & Dealokasi RAM** | Hanya mengunci ring buffer terbatas (`<= 64 MB`) dan langsung `munlock` + membersihkan state saat berhenti | Mengunci seluruh `m_pcmBuffer` tanpa cek `RLIMIT_MEMLOCK`; **`stop()` tidak pernah membebaskan kapasitas `m_pcmBuffer`** (`CoreAudioSink.cpp:L564-L579`) | **KRITIS (Memory Bloat & Risiko Kegagalan `mlock`)** |
| **Mesin Gapless Playback** | **Pre-buffer 32 MB (`pcmBufferNext`) + `nextActiveDecoder`** dengan transisi *sample-accurate* di dalam callback real-time tanpa membongkar stream (`AudioStreamer.cpp:L792-L856, L973-L1075`) | **0% (Tidak Ada Sama Sekali)** — pergantian lagu memakai `Timer` 250ms di Main Thread + bongkar pasang `AudioUnit` + `usleep(100000)` (`AudioEngine.swift:L346-L363`) | **KRITIS (Fitur Hilang Total meski Diklaim Aktif di Telemetri)** |
| **Keamanan Thread (Thread Safety)** | `std::mutex stateMutex`, `std::atomic` flags, pemisahan tegas antara RT callback dan decode thread (`AudioEngineState.h:L15-L220`) | **Data Race** antara `playTrack()` dan `handleRender()` + variabel global statis tanpa mutex di `TsrossaAudioBridge.cpp` (`L6-L7, L19-L57, L124-L127`) | **KRITIS (Potensi Crash `EXC_BAD_ACCESS` / Heap Corruption)** |

---

## 2. Bedah Teknis Temuan demi Temuan

### 2.1. Dekode Penuh Sinkron ke RAM Tanpa Batas vs 64 MB Sliding-Window Ring Buffer
- **Lokasi Kode macOS:** [`CoreAudioSink.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L501-L515)
- **Lokasi Acuan Android:**
  - [`AudioCommon.h`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioCommon.h#L22)
  - [`AudioStreamer.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L263-L415)
  - [`AudioStreamer.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L1239-L1256)
- **Analisa Reverse Engineering:**
  Pada `tsrossa-ujicoba` (Android), arsitektur streaming dirancang secara hati-hati menggunakan **Sliding-Window Ring Buffer** berukuran maksimum **64 MB** (`16.777.216` sampel `int32_t`). Ketika membuka lagu:
  1. Engine hanya mendekode **5 detik pertama** (`initialFrames = sampleRate * 5`) secara sinkron agar pemutaran langsung dimulai dalam `< 50 ms`.
  2. Thread background `TsrossaDecode` mengisi sisa ring buffer secara bertahap seiring majunya pointer baca (`currentFrame`), sehingga file FLAC/WAV berdurasi 3 jam sekalipun hanya memakai maksimal 64 MB RAM.

  Sebaliknya, pada `tsrossa-macos` (`CoreAudioSink::playTrack`):
  ```cpp
  // macOS: CoreAudioSink.cpp:L501-L515
  try {
      m_pcmBuffer.resize(m_totalFrames * m_channels);
      if (m_ramLockEnabled.load()) {
          // Unlock previous if any
          if (m_isMemoryLocked && !m_pcmBuffer.empty()) {
              munlock(m_pcmBuffer.data(), m_pcmBuffer.size() * sizeof(int32_t));
          }
      }
      uint64_t framesRead = m_decoder->readFrames(m_pcmBuffer.data(), m_totalFrames);
      m_totalFrames = framesRead;
      if (m_ramLockEnabled.load() && !m_pcmBuffer.empty()) {
          if (mlock(m_pcmBuffer.data(), m_pcmBuffer.size() * sizeof(int32_t)) == 0) {
              m_isMemoryLocked = true;
          }
      }
  ```
- **Dampak Nyata di Dunia Nyata:**
  1. **Ledakan Penggunaan RAM (Memory Spike):** Satu file konser/simfoni atau rekaman DSD/DXD yang dikonversi ke WAV/FLAC `192 kHz / 24-bit Stereo` berdurasi 60 menit memiliki `691.200.000` frame × 2 kanal × 4 byte (`int32_t`) = **~5,53 GB RAM murni** untuk satu lagu!
  2. **Blocking Lag Saat Memulai Lagu:** `m_decoder->readFrames(m_pcmBuffer.data(), m_totalFrames)` mendekode ratusan juta sampel secara sinkron sebelum memanggil `setupAudioUnit()` dan `AudioOutputUnitStart()`. Pengguna mengalami jeda hening yang terasa lambat setiap kali mengklik lagu berdurasi panjang atau resolusi tinggi (`96kHz`/`192kHz`).
  3. **Bug Urutan `munlock` Setelah `resize()` (`CoreAudioSink.cpp:L502-L506`):** Perhatikan baris 502–506 di atas: `m_pcmBuffer.resize(...)` dipanggil **sebelum** `munlock(m_pcmBuffer.data(), ...)`. Meskipun `stop()` di baris 482 sudah memanggil `munlock`, jika suatu jalur kode memanggil `resize()` saat memori masih terkunci, `std::vector::resize()` dapat mengalokasikan blok heap baru dan menyalin data lama, sehingga pemanggilan `munlock()` sesudahnya justru mengarah ke alamat baru dengan ukuran baru!
  4. **Memori Tidak Pernah Dibebaskan Saat `stop()` (`CoreAudioSink.cpp:L564-L579`):** Fungsi `CoreAudioSink::stop()` hanya melakukan `munlock()`, tetapi **tidak pernah memanggil `m_pcmBuffer.clear()` dan `m_pcmBuffer.shrink_to_fit()`**. Begitu pengguna memutar satu file besar (misal 1 GB), memori 1 GB tersebut tetap ditahan oleh proses aplikasi meskipun pemutaran sudah dihentikan.
  5. **`setRamLockEnabled(false)` Tidak Membuka Kunci Memori yang Sedang Aktif (`CoreAudioSink.cpp:L308-L310`):** Saat pengguna mematikan toggle *RAM Allocation / mlock* di Settings ketika lagu sedang berjalan, `setRamLockEnabled` hanya mengubah `m_ramLockEnabled = enable` tanpa memanggil `munlock()`.

---

### 2.2. Absennya Mesin Gapless Playback & Bug Polling Timer 250ms
- **Lokasi Kode macOS:**
  - [`AudioEngine.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AudioEngine.swift#L346-L363)
  - [`CoreAudioSink.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L622-L628)
  - [`TelemetryModalView.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift#L135)
- **Lokasi Acuan Android:**
  - [`AudioStreamer.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L792-L856) (AAudio real-time gapless handover)
  - [`AudioStreamer.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L973-L1075) (USB Isochronous real-time gapless handover)
  - [`AudioStreamer.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L1599-L1712) (`prepareNextTrack` & `promoteNextTrackLocked`)
- **Analisa Reverse Engineering:**
  Pada versi Android, *Gapless Playback* diimplementasikan secara lengkap di level C++:
  - Fungsi JNI `prepareNextTrack` membuka file lagu berikutnya di background dan mendekode awal lagu ke buffer kedua (`pcmBufferNext` berukuran hingga 32 MB).
  - Saat frame terakhir lagu saat ini habis di tengah-tengah buffer callback (`framesRead == 0` atau `currentFrame >= totalFrames`), callback audio **langsung menyambung sisa frame di buffer yang sama dari `pcmBufferNext`** jika sample rate sama, tanpa pernah menghentikan stream USB/AAudio.

  Pada `tsrossa-macos`:
  - **Tidak ada fungsi `prepareNextTrack`**, tidak ada `pcmBufferNext`, dan tidak ada antrian decoder kedua di `CoreAudioSink`.
  - Ketika lagu habis di `CoreAudioSink::handleRender` (`CoreAudioSink.cpp:L622-L628`), callback langsung mengisi buffer dengan `0` (hening) dan menyetel `m_isPlaying = false`.
  - Di sisi Swift (`AudioEngine.swift:L346-L363`), pergantian lagu bergantung sepenuhnya pada **`Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true)`** di Main Thread:
    ```swift
    // macOS: AudioEngine.swift:L346-L363
    progressTimer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
        guard let self = self else { return }
        let t = tsrossa_get_current_time()
        let stillPlaying = tsrossa_is_playing()
        self.currentTime = t

        if !stillPlaying && self.totalDuration > 0 && t >= self.totalDuration - 0.5 {
            self.isPlaying = false
            self.progressTimer?.invalidate()
            // ... memanggil AppViewModel.shared.nextTrack()
        }
    }
    ```
- **Dampak Nyata:**
  1. **Jeda Hening Antar-Lagu (Bukan Gapless):** Pada album konser live atau album konsep yang menyambung (seperti *The Dark Side of the Moon* atau *Abbey Road*), setiap pergantian track mengalami jeda:
     - Hingga **250 ms** menunggu `progressTimer` berdetak, ditambah
     - Waktu dekode penuh lagu berikutnya ke RAM (`readFrames`), ditambah
     - `teardownAudioUnit()` + `usleep(100000)` (100ms) + `setupAudioUnit()`.
     Namun `TelemetryModalView.swift:L135` menampilkan kebohongan: **`Gapless Engine: ACTIVE (Direct Handover)`**!
  2. **Bug Fatal Lagu Berhenti Sendiri (`t >= self.totalDuration - 0.5`):**
     Jika sebuah track sangat pendek (misal track intro/interlude `< 0.5` detik) atau terjadi ketidaksesuaian pembulatan durasi antara estimasi metadata vs jumlah frame aktual yang terdekode (`m_totalFrames` lebih pendek > 0.5 detik karena frame korup di akhir file), kondisi `t >= self.totalDuration - 0.5` bernilai `false` saat `!stillPlaying`. Akibatnya, `progressTimer` terus berputar selamanya dalam keadaan berhenti dan **antrian lagu macet total (tidak pernah pindah ke lagu berikutnya)**!

---

### 2.3. Data Race & Pelanggaran Thread-Safety di C++ Engine dan Bridge
- **Lokasi Kode macOS:**
  - [`CoreAudioSink.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L478-L506) vs [`CoreAudioSink.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp#L608-L645)
  - [`TsrossaAudioBridge.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/TsrossaAudioBridge.cpp#L6-L57) & [`TsrossaAudioBridge.cpp`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/TsrossaAudioBridge.cpp#L124-L128)
  - [`AudioEngine.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AudioEngine.swift#L32-L35)
- **Analisa Reverse Engineering:**
  1. **Data Race pada `m_pcmBuffer`, `m_totalFrames`, dan `m_channels`:**
     - `m_totalFrames` (`uint64_t`) dan `m_channels` (`uint32_t`) di `CoreAudioSink.h:L97-L98` **bukan variabel `std::atomic`**, dan `m_pcmBuffer` (`std::vector<int32_t>`) diakses langsung tanpa sinkronisasi di `handleRender()`.
     - Saat `playTrack()` dipanggil untuk mengganti lagu ketika lagu sebelumnya sedang berputar (`CoreAudioSink.cpp:L482`), ia memanggil `stop()` (yang hanya memanggil `AudioOutputUnitStop(m_audioUnit)` tanpa `AudioUnitUninitialize`), lalu **langsung memodifikasi `m_sampleRate`, `m_channels`, `m_totalFrames`, dan `m_pcmBuffer.resize(...)` (`L494-L502`)** sebelum `teardownAudioUnit()` dipanggil di baris 517!
     - Selain itu, `getCurrentTime()` (`CoreAudioSink.cpp:L599-L602`) dan `seekTo()` (`L581-L597`) dipanggil dari Main Thread (melalui `progressTimer` dan slider UI di `AudioEngine.swift:L294, L349`), membaca `m_sampleRate` dan `m_totalFrames` secara bersamaan ketika `engineQueue` sedang menjalankan `playTrack()` dan mengubah variabel non-atomik tersebut!
  2. **Variabel Global Statis Tanpa Lock di `TsrossaAudioBridge.cpp`:**
     ```cpp
     // macOS: TsrossaAudioBridge.cpp:L6-L7
     static std::vector<DeviceInfo> s_cachedDevices;
     static std::string s_lastTelemetry;
     ```
     - Fungsi `tsrossa_get_device_name(index)` dan `tsrossa_get_device_transport(index)` mengembalikan pointer mentah `s_cachedDevices[index].name.c_str()`. Jika `tsrossa_get_device_count()` dipanggil lagi (misal karena event hotplug perangkat CoreAudio di `CoreAudioSink::hardwareListenerProc` atau refresh UI), `s_cachedDevices` ditimpa tanpa mutex, yang dapat menyebabkan *dangling pointer* (`EXC_BAD_ACCESS`).
     - Fungsi `tsrossa_get_telemetry_json()` (`TsrossaAudioBridge.cpp:L124-L127`) menyalin hasil ke `static std::string s_lastTelemetry` dan mengembalikan `s_lastTelemetry.c_str()` tanpa mutex.
  3. **Race Condition pada `isSeeking` di `AudioEngine.swift` (`L35, L275-L299`):**
     - Variabel `private var isSeeking = false` di `AudioEngine.swift:L35` ditulis dari Main Thread (`seek(to:)`) dan dibaca/ditulis dari `engineQueue` tanpa lock, sementara `progressTimer` di Main Thread bahkan **tidak memeriksa `isSeeking` sama sekali** (`AudioEngine.swift:L349-L351`), sehingga saat pengguna menggeser slider seek, `progressTimer` setiap 0.25 detik langsung menimpa kembali `self.currentTime` ke posisi lama sebelum `engineQueue` selesai memproses `tsrossa_seek` (menyebabkan thumb slider melompat-lompat / *glitching*).
  4. **Absennya Atomic Compare-And-Swap (`compare_exchange_strong`) pada `m_currentFrame` Saat Seek (`CoreAudioSink.cpp:L596` vs `L617, L637`):**
     - Di aplikasi Android (`AudioStreamer.cpp` & diuji secara eksplisit di [`VolumeAndStreamingMathTest.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/test/java/com/yuka/musicplayer/audio/VolumeAndStreamingMathTest.kt#L456-L470)), callback real-time memajukan indeks frame menggunakan **CAS (`compare_exchange_strong`)**. Jika di tengah eksekusi callback pengguna melakukan `seekTo()`, CAS mendeteksi bahwa `m_currentFrame` telah berubah dan membatalkan penimpaan nilai lama.
     - Di `tsrossa-macos` (`CoreAudioSink::handleRender`), baris 617 membaca `uint64_t currentFrame = m_currentFrame.load(std::memory_order_acquire);`, menyalin sampel, lalu baris 637 memanggil `m_currentFrame.store(currentFrame + framesToCopy, std::memory_order_release);` secara membabi buta. Jika `seekTo()` (`L596`) dieksekusi di antara baris 617 dan 637, **`handleRender()` langsung menimpa kembali `m_currentFrame` ke posisi sebelum seek**, sehingga perintah seek pengguna hilang begitu saja!
  5. **Batas Clamp Seek Akhir Lagu Terlalu Mepet (`m_totalFrames - 1`) (`CoreAudioSink.cpp:L590`):**
     - Di Android ([`VolumeAndStreamingMathTest.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/test/java/com/yuka/musicplayer/audio/VolumeAndStreamingMathTest.kt#L473-L486)), ketika pengguna menyeret seek bar hingga mentok 100% ke kanan, target seek dibatasi pada `durationSeconds - 0.25` (menyisakan ~12.000 frame pada 48 kHz) agar tidak memicu *auto-next* seketika akibat *touch release jitter*.
     - Di `tsrossa-macos` (`CoreAudioSink.cpp:L590`), `if (targetFrame >= m_totalFrames) targetFrame = m_totalFrames > 0 ? m_totalFrames - 1 : 0;` hanya menyisakan **1 frame tunggal** (`0,02 milidetik` pada 48 kHz), sehingga menyeret scrubber ke ujung kanan langsung menghentikan lagu seketika.

---

## 3. Rekomendasi Perbaikan Teknis (Actionable Fixes)

1. **Porting 64 MB Sliding-Window Ring Buffer & Background Decode Thread dari Android:**
   - Ganti `m_pcmBuffer.resize(m_totalFrames * m_channels)` dengan ring buffer berukuran tetap (misal `64 MB` seperti `MAX_RING_BUFFER_BYTES` di [`AudioCommon.h`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioCommon.h#L22)) beserta thread produsen background sehingga pemutaran lagu dimulai dalam `< 50 ms` dan konsumsi RAM selalu terikat (bounded).
   - Pastikan `stop()` mereset/membebaskan buffer atau gunakan satu alokasi ring buffer tetap yang digunakan ulang lintas lagu.
2. **Implementasi Gapless Handover Sejati di `CoreAudioSink`:**
   - Tambahkan API `tsrossa_prepare_next_track(const char* path)` yang mendekode pre-buffer lagu berikutnya.
   - Di dalam `CoreAudioSink::handleRender()`, ketika `currentFrame + framesToCopy >= m_totalFrames`, langsung salin sisa frame yang dibutuhkan (`framesNeeded - framesToCopy`) dari pre-buffer lagu berikutnya apabila sample rate dan jumlah kanal identik, lalu picu callback notifikasi perubahan track ke Swift secara asinkron.
   - Untuk lagu yang tidak memiliki track gapless siap pakai, hapus syarat `t >= self.totalDuration - 0.5` yang rentan gagal pada lagu pendek; gunakan flag atomik eksplisit `m_trackReachedEOF` dari C++ untuk memberi tahu Swift bahwa pemutaran telah selesai secara alami.
3. **Perbaiki Thread-Safety di `CoreAudioSink` dan `TsrossaAudioBridge`:**
   - Jadikan `m_sampleRate`, `m_channels`, dan `m_totalFrames` sebagai `std::atomic`, serta panggil `teardownAudioUnit()` **sebelum** memutasi `m_pcmBuffer` di dalam `playTrack()`.
   - Lindungi `s_cachedDevices` dan `s_lastTelemetry` di `TsrossaAudioBridge.cpp` dengan `std::mutex`, serta periksa `guard !self.isSeeking` di dalam closure `progressTimer` pada `AudioEngine.swift`.
