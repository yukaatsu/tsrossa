# Audit & Analisa Aspek 4: UI/UX, Telemetri Palsu (Placebo UI), Fitur yang Hilang, & Integrasi Desktop macOS

**Target Audit:**
- [`MainAppView.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/MainAppView.swift) (3.579 baris)
- [`AppViewModel.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift) (866 baris)
- [`TelemetryModalView.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift) (219 baris)
- [`Theme.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Theme.swift) (474 baris)
- [`main.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/main.swift) (101 baris)

**Acuan Ground-Truth (Read-Only):**
- [`MainActivity.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/MainActivity.kt)
- [`DacTelemetryState.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/diagnostics/DacTelemetryState.kt)
- [`SystemLogsPanel.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/diagnostics/SystemLogsPanel.kt)
- [`TrackView.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/views/TrackView.kt)
- [`LibraryView.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/views/LibraryView.kt)
- [`PlaylistView.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/views/PlaylistView.kt)
- [`SettingsView.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/views/SettingsView.kt)
- [`QueueHelper.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/player/queue/QueueHelper.kt)

---

## 1. Temuan Utama: "Placebo UI" & Telemetri Palsu di `TelemetryModalView.swift`

Salah satu temuan paling serius dalam audit reverse engineering `tsrossa-macos` adalah fakta bahwa **jendela *Hardware & Engine Telemetry* (`TelemetryModalView.swift`) dan kartu *Signal Path* (`MainAppView.swift`) menampilkan teks statis (hardcoded) yang tidak mencerminkan kondisi mesin C++ yang sebenarnya**.

### 1.1. Perbandingan Telemetri Android Asli vs macOS Port

| Metrik Diagnostik | Android Asli (`DacTelemetryState.kt` & `SystemLogsPanel.kt`) | Porting macOS (`TelemetryModalView.swift`) | Bukti Baris Kode macOS |
| :--- | :--- | :--- | :--- |
| **Sumber Data UI** | Membaca JSON live setiap 1 detik dari `audioEngine.getDacInfo()`, `getRefusedTrackHistory()`, `getTimingTelemetry()`, dan ring buffer 500 entri `AppLogger` + `logcat` asli | **`engine.telemetryJson` SAMA SEKALI TIDAK DIPARSE OLEH UI!** Seluruh baris metrik menggunakan string literal statis | [`TelemetryModalView.swift:L122-L162`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift#L122-L162) |
| **Exclusive Lock** | Melaporkan status klaim interface USB aktual (`ACTIVE (Exclusive)` vs `DISCONNECTED` vs `ERROR / WEDGED`) | Hardcoded **`"HOG MODE ACTIVE"`** bahkan ketika pengguna **mematikan** toggle *Exclusive Hog Mode* (`vm.isExclusiveHogMode == false`)! | [`TelemetryModalView.swift:L126`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift#L126) |
| **I/O Error Count** | Membaca counter atomik C++ `getRecentErrorCount()` dari transfer URB/AAudio | Hardcoded string **`"0"`** (tidak ada counter error di C++ maupun Swift) | [`TelemetryModalView.swift:L128`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift#L128) |
| **RAM Playback** | Melaporkan status buffer aktual | Hardcoded **`"ACTIVE (Zero Jitter / mlock)"`** bahkan saat toggle *RAM Allocation* dimatikan (`vm.isRamPlayback == false`)! | [`TelemetryModalView.swift:L134`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift#L134) |
| **Gapless Engine** | Melaporkan status pre-buffer `prepareNextTrack` aktual | Hardcoded **`"ACTIVE (Direct Handover)"`** padahal fitur Gapless **sama sekali belum diimplementasikan** di kode C++ maupun Swift! | [`TelemetryModalView.swift:L135`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift#L135) |
| **DSP Pipeline** | Melaporkan `isBitPerfectIntegerPassActive()` (berubah menjadi `"64-bit TPDF Dithered DSP"` saat volume `< 100%`) | Hardcoded **`"BYPASSED (Bit-Perfect 0 dBFS)"`** bahkan ketika pengguna menurunkan volume ke `-30 dB`! | [`TelemetryModalView.swift:L136`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift#L136) |
| **Framework Log** | Membaca log kejadian nyata dengan timestamp saat event terjadi (`AppLogger.entries` + `UsbAudioController.getTrace()`) | Mencetak **5 kalimat palsu hardcoded** dengan memanggil `currentTimestamp()` (`Date()`) saat render view! | [`TelemetryModalView.swift:L153-L157`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift#L153-L157) |

### 1.2. Bug Konyol pada `FRAMEWORK LOG` di `TelemetryModalView.swift`
Perhatikan kode [`TelemetryModalView.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift#L152-L158):
```swift
// macOS: TelemetryModalView.swift:L152-L158
VStack(alignment: .leading, spacing: 6) {
    logLine(time: currentTimestamp(), tag: "INFO/AUDIO", msg: "CoreAudio HAL initialized successfully.", color: .green)
    logLine(time: currentTimestamp(), tag: "INFO/DAC", msg: "Connected to \(engine.currentDeviceName)", color: .cyan)
    logLine(time: currentTimestamp(), tag: "INFO/LOCK", msg: "kAudioDevicePropertyHogMode engaged.", color: .green)
    logLine(time: currentTimestamp(), tag: "INFO/MEM", msg: "PCM Buffer mlock() succeeded. Zero page-fault guarantee.", color: .orange)
    logLine(time: currentTimestamp(), tag: "INFO/CLOCK", msg: "Hardware nominal sample rate locked to \(engine.currentTrack?.sampleRate ?? 44100) Hz.", color: .green)
}
```
Karena `TelemetryModalView` mengamati `@ObservedObject var engine = AudioEngine.shared`, dan `engine.currentTime` diperbarui setiap **0,25 detik** selama lagu diputar, SwiftUI merender ulang `TelemetryModalView` 4 kali per detik. Setiap kali dirender ulang, `currentTimestamp()` memanggil `Date()`, sehingga **jam pada kelima baris log historis tersebut ikut berjalan maju setiap detik mengikuti jam dinding saat ini**!
Selain itu, tombol **"Share Report"** ([`TelemetryModalView.swift:L77-L95`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift#L77-L95)) memiliki aksi yang **100% identik dengan tombol "Copy Report"** (hanya menyalin ke clipboard tanpa membuka `NSSharingServicePicker` atau dialog simpan file `.log`).

---

## 2. Properti & Pengaturan "Mati" (Dead Code / Unwired State) di `AppViewModel.swift`

Di dalam [`AppViewModel.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L155-L160), terdapat tiga `@Published` variabel state yang dideklarasikan seolah-olah di-porting dari Android, namun **tidak pernah dihubungkan ke logika maupun UI mana pun**:

```swift
// macOS: AppViewModel.swift:L156-L160
// App Preferences
@Published var keepScreenAwake: Bool = true
@Published var isAutoplayRadio: Bool = true
@Published var bufferProfile: String = "192ms Jitter Safe"
@Published var themeMode: String = "DARK" // DARK, OLED, LIGHT
```

1. **`keepScreenAwake`:** Tidak pernah memanggil API macOS `IOPMAssertionCreateWithName(kIOPMAssertionTypeNoIdleSleep / kIOPMAssertionTypeNoDisplaySleep, ...)` dan tidak ada tombolnya di halaman Settings. Akibatnya, Mac dapat masuk ke mode *sleep* saat memutar album panjang di latar belakang.
2. **`bufferProfile` (`"192ms Jitter Safe"`):** Di Android (`AudioEngine.kt:L20-L28`, `PlaybackLaunchSection.kt:L30`), pengguna dapat memilih 3 profil buffer (`64ms Low Latency`, `160ms Balanced Robust`, `192ms Max Stability`) yang mengubah jumlah transfer/paket atau ukuran frame buffer hardware. Di `tsrossa-macos`, variabel `bufferProfile` tidak pernah dibaca di mana pun, dan `CoreAudioSink.cpp` tidak pernah mengatur `kAudioDevicePropertyBufferFrameSize`.
3. **`themeMode` (`"DARK"`, `"OLED"`, `"LIGHT"`):** Di Android (`Theme.kt`, `ThemeSettingsSection.kt`), aplikasi mendukung penuh **Light Mode** (`#F7F8FC` dengan penyesuaian kontras WCAG otomatis via `ColorContrastHelper.ensureThemeContrast`), **Dark Mode**, dan **AMOLED Mode**. Di `tsrossa-macos`, variabel `themeMode` mati total; aplikasi terkunci secara kaku pada warna teks putih (`Color.white`) dan latar gelap.

---

## 3. Bug Integrasi Desktop macOS & Input Pengguna

### 3.1. Global Keyboard Monitor Membajak Tombol Panah & Spasi di Seluruh Aplikasi
- **Lokasi Kode macOS:** [`AppViewModel.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L708-L865) & [`main.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/main.swift#L30-L69)
- **Analisa Reverse Engineering:**
  `AppViewModel.setupKeyboardShortcuts()` memasang `NSEvent.addLocalMonitorForEvents(matching: .keyDown)` yang mencegat tombol keyboard tanpa modifier (`cmd == false`):
  - `keyCode 49` (`Spacebar`) -> Play/Pause (`L828-L831`)
  - `keyCode 123` (`Left Arrow` polos) -> Previous Track (`L833-L835`)
  - `keyCode 124` (`Right Arrow` polos) -> Next Track (`L837-L839`)
  - `keyCode 126` (`Up Arrow` polos) -> Volume +1 dB (`L841-L843`)
  - `keyCode 125` (`Down Arrow` polos) -> Volume -1 dB (`L845-L847`)
- **Dampak Buruk bagi UX macOS:**
  1. Ketika pengguna sedang menelusuri daftar lagu di Library, Playlist, atau Settings dan menekan tombol panah **Atas / Bawah** untuk menggulir daftar, aplikasi justru menaikkan/menurunkan volume!
  2. Ketika pengguna menekan panah **Kiri / Kanan**, lagu yang sedang diputar langsung berganti secara tidak sengaja!
  3. Di `main.swift:L30-L69`, `CommandMenu("Playback")` sudah mendaftarkan shortcut standar (`Space`, `Cmd+Left`, `Cmd+Right`, `Cmd+Up`, `Cmd+Down`, `Cmd+S`, `Cmd+T`), sehingga pemasangan monitor lokal di `AppViewModel.swift` untuk kombinasi `Cmd` menjadi duplikat.
  4. **Ketidaksesuaian Dokumentasi `README.md`:** [`README.md`](file:///Users/yuka/Documents/Projects/tsrossa-macos/README.md#L109-L110) mendokumentasikan shortcut `Cmd + U` (*Check for Updates*) dan `Cmd + [` / `Cmd + ]` (*Prev / Next Track*), namun **ketiga shortcut tersebut sama sekali tidak ada di kode `main.swift` maupun `AppViewModel.swift`**!

---

### 3.2. Absennya Integrasi macOS `MPNowPlayingInfoCenter` & Hardware Media Keys (`F7`/`F8`/`F9`)
- **Lokasi Kode macOS:** Seluruh direktori `macos/Sources/` (Tidak ada `import MediaPlayer`)
- **Lokasi Acuan Android:** [`AudioForegroundService.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/audio/AudioForegroundService.kt#L104-L217) (`MediaSessionCompat`)
- **Analisa Reverse Engineering:**
  Di Android, `AudioForegroundService.kt` mengintegrasikan `MediaSessionCompat` secara penuh sehingga tombol headphone Bluetooth/TWS, layar kunci (Lock Screen), Android Auto, dan panel notifikasi menampilkan judul lagu, artis, sampul album, posisi waktu, serta menerima perintah Play/Pause/Next/Prev/Seek.
  Di `tsrossa-macos`, **tidak ada integrasi `MPNowPlayingInfoCenter` maupun `MPRemoteCommandCenter`**. Akibatnya:
  1. Menekan tombol fisik **Play/Pause (`F8`)**, **Previous (`F7`)**, atau **Next (`F9`)** pada keyboard Mac (ketika aplikasi berada di background atau tidak fokus) **tidak berfungsi sama sekali**!
  2. Kontrol dari AirPods / headphone eksternal dan widget **Now Playing di macOS Control Center / Menu Bar** kosong dan tidak mengenali `tsrossa-macos` sebagai pemutar media aktif.

---

### 3.3. Inkonsistensi Logika `isRepeat` (1-State Bool vs 3-State `RepeatMode`) & Antrian Shuffle
- **Lokasi Kode macOS:**
  - [`AppViewModel.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L124) & [`AppViewModel.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L555-L625)
  - [`AudioEngine.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AudioEngine.swift#L355-L359)
- **Lokasi Acuan Android:** [`QueueHelper.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/player/queue/QueueHelper.kt#L230-L420)
- **Analisa Reverse Engineering:**
  1. **Kontradiksi Perilaku `isRepeat`:**
     Di Android, `RepeatMode` memiliki 3 state yang jelas: `OFF`, `ALL` (ulang seluruh daftar/playlist), dan `ONE` (ulang 1 lagu).
     Di `tsrossa-macos`, `isRepeat` hanya berupa `Bool` tunggal, dan perilakunya **saling bertentangan** antaraสอง file:
     - Di [`AudioEngine.swift:L355-L359`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AudioEngine.swift#L355-L359), saat lagu selesai secara alami:
       ```swift
       if AppViewModel.shared.isRepeat, let current = self.currentTrack {
           self.playTrack(current) // <--- Berperilaku sebagai REPEAT ONE!
       } else {
           AppViewModel.shared.nextTrack()
       }
       ```
     - Namun di [`AppViewModel.swift:L598-L600`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L598-L600), di dalam `nextTrack()`:
       ```swift
       } else if isRepeat && !contextList.isEmpty {
           playFromContext(track: contextList[0], context: contextList) // <--- Berperilaku sebagai REPEAT ALL!
       }
       ```
     Akibatnya, pengguna di macOS **tidak bisa memilih `REPEAT ALL` saat pemutaran otomatis**, karena jika `isRepeat == true`, `AudioEngine.swift` selalu mencegat akhir lagu dan memutar ulang lagu yang sama (`REPEAT ONE`) tanpa pernah memanggil `nextTrack()`!
  2. **Algoritma Shuffle Naive (`randomElement()`) vs Deck Shuffle Bebas Duplikat (`QueueHelper.kt`):**
     Di Android (`QueueHelper.kt:L230-L343`), mode Shuffle membangun *shuffle deck* (`shuffledList`) terurut sehingga seluruh lagu dalam album/playlist diputar tepat satu kali sebelum diacak ulang (tanpa pengulangan lagu yang baru saja diputar).
     Di `tsrossa-macos` (`AppViewModel.swift:L589-L593`), `nextTrack()` hanya memanggil `candidates.randomElement()` secara acak buta di setiap pergantian lagu! Akibatnya, Lagu A -> Lagu B -> Lagu A dapat langsung terulang kembali meskipun masih ada 500 lagu lain yang belum diputar.

---

## 4. Daftar Fitur UI/UX Android (`tsrossa-ujicoba`) yang Hilang Total di `tsrossa-macos`

Selain perbedaan di atas, berikut adalah fitur-fitur yang ada di aplikasi Android asli namun **belum di-porting sama sekali** ke `tsrossa-macos`:

1. **Modal Inspeksi Detail File (`TrackDetailsModalSheet.kt`):** Menampilkan 14 baris metadata lengkap (Album Artist, Genre, Year, Track Number, Composer, Bitrate kbps, Ukuran File MB, Path lengkap monospace).
2. **Filter Multi-Kategori di Library (`LibraryView.kt:L768-L876` & `FilterSelectionModalSheet.kt`):** Filter chip interaktif berdasarkan **Artist**, **Genre**, **Album**, **Year**, dan **Format** beserta jumlah lagu per kategori.
3. **Mode Multi-Select Batch Action di Library (`LibraryView.kt:L427-L518`):** Memilih banyak lagu sekaligus untuk ditambahkan ke Playlist atau dihapus secara batch.
4. **Fast Scroller A–Z dengan Indikator Huruf Mengambang (`LibraryFastScroller.kt`):** Navigasi cepat alfabetis untuk koleksi ribuan lagu.
5. **Manajemen Playlist Lengkap (`PlaylistView.kt` & `PlaylistStorageHelper.kt`):**
   - **Rename Playlist** (`RenamePlaylistDialog.kt`) — di macOS playlist yang sudah dibuat tidak bisa diubah namanya.
   - **Pin Playlist** (`togglePinPlaylist`) — menyematkan playlist favorit di urutan teratas.
   - **Clear Playlist Tracks** — mengosongkan isi playlist tanpa menghapus wadah playlist.
   - **Pencarian di Dalam Playlist** (`PlaylistView.kt:L751-L785`).
   - **Auto Folder Playlists** (`"Make Folder as Playlist"` di `PlaylistSettingsSection.kt`).
6. **Kontrol Volume Kontinu, Slider Halus, & Hybrid Hardware Volume (`VolumeModalSheet.kt`):**
   - Di macOS tidak ada slider volume sama sekali (hanya tombol `-` dan `+` 1 dB), tidak ada tombol Mute instan, dan tidak ada mode *Hardware Volume* (`kAudioDevicePropertyVolumeScalar` / `kAudioHardwareServiceDeviceProperty_VirtualMainVolume`).
7. **Fitur DAC Hardware Warm-up (`WarmupConfirmDialog.kt` & `WarmupHudDialog.kt`):** Siklus pemanasan osilator clock & tahapan analog DAC (`triggerWarmup`).
8. **Auto-Resume Lagu & Posisi Terakhir Saat Aplikasi Dibuka (`PlaybackLaunchSection.kt` & `MainActivity.kt`):** Mengingat lagu terakhir dan detik terakhir (`resume_file_path`, `resume_position_sec`) saat aplikasi dijalankan kembali.
9. **Skala Font Global (`TypographyHapticsSection.kt`):** Pengaturan ukuran teks UI (`0.85x` s/d `1.20x`).
10. **Pencarian di Dalam Antrian (`QueuePanel.kt`):** Mencari lagu di dalam daftar *Up Next / Priority Queue*.
11. **Panduan Pengguna Interaktif (`HelpPanel.kt`) & Info Pengembang / Donasi (`UpdateHelpSections.kt`).**

---

## 5. Rekomendasi Perbaikan Teknis (Actionable Fixes)

1. **Bongkar Total `TelemetryModalView.swift` agar Membaca Data Nyata:**
   - Parse JSON dari `tsrossa_get_telemetry_json()` dan tambahkan ring buffer logger nyata di Swift/C++ (seperti `AppLogger.kt`) yang mencatat timestamp saat peristiwa *benar-benar terjadi*, bukan memanggil `Date()` di dalam `View.body`.
   - Hubungkan tombol `"Share Report"` ke `NSSharingServicePicker` atau `NSSavePanel` untuk mengekspor file `.log`.
2. **Perbaiki Keyboard Monitor & Tambahkan `MPNowPlayingInfoCenter`:**
   - Hapus pencegatan tombol panah polos (`123`, `124`, `125`, `126`) tanpa modifier di `AppViewModel.setupKeyboardShortcuts()`. Gunakan `CommandMenu` di `main.swift` (tambahkan `Cmd+U`, `Cmd+[`, `Cmd+]`).
   - Integrasikan framework `MediaPlayer` (`MPNowPlayingInfoCenter.default()` & `MPRemoteCommandCenter.shared()`) di `AudioEngine.swift` agar tombol media Mac dan AirPods berfungsi.
3. **Ubah `isRepeat` Menjadi Enum 3-State (`off`, `all`, `one`) & Implementasikan Shuffle Deck:**
   - Porting logika `RepeatMode` (`OFF`, `ALL`, `ONE`) dan `ensureShuffleDeck` dari [`QueueHelper.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/player/queue/QueueHelper.kt#L230-L358) ke `AppViewModel.swift`.
4. **Hubungkan Pengaturan Mati (`keepScreenAwake`, `themeMode`, `bufferProfile`):**
   - Gunakan `IOPMAssertionCreateWithName` saat `isPlaying && keepScreenAwake`.
   - Ekspos pengaturan ukuran buffer CoreAudio (`kAudioDevicePropertyBufferFrameSize`: `64`, `256`, `512`, `1024`, `2048` frame) dari `bufferProfile` ke `CoreAudioSink.cpp`.
