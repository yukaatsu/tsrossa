# Audit & Analisa Aspek 3: Metadata, Database Library, Sistem Sorting, Pencarian, & Lirik

**Target Audit:**
- [`MetadataScanner.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/MetadataScanner.swift)
- [`LyricsManager.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/LyricsManager.swift)
- [`AppViewModel.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift)
- [`Theme.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Theme.swift) (`ArtworkExtractor`)

**Acuan Ground-Truth (Read-Only):**
- [`AudioTagReader.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/metadata/AudioTagReader.kt)
- [`MetadataExtractor.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/metadata/MetadataExtractor.kt)
- [`MusicLibraryDatabaseHelper.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/util/MusicLibraryDatabaseHelper.kt)
- [`MusicScannerHelper.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/util/MusicScannerHelper.kt)
- [`TrackTitleParser.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/util/TrackTitleParser.kt)
- [`AlbumArtThumbnailCache.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/util/AlbumArtThumbnailCache.kt)
- [`ColorContrastHelper.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/util/ColorContrastHelper.kt)
- [`FuzzySearchHelper.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/util/FuzzySearchHelper.kt)
- [`LyricsHelper.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/util/LyricsHelper.kt)
- [`PlaylistStorageHelper.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/util/PlaylistStorageHelper.kt)

---

## 1. Perbandingan Komponen Data, Metadata, & Library (Android vs macOS)

| Fitur / Subsistem | Android Asli (`tsrossa-ujicoba`) | Porting macOS (`tsrossa-macos`) | Status Paritas / Keparahan |
| :--- | :--- | :--- | :--- |
| **Database Cache Library** | **SQLite Persistent Cache** (`music_library_cache.db`, `MusicLibraryDatabaseHelper.kt`) — *cold start* `< 20 ms` + *incremental diff scan* berdasarkan `date_modified` & `file_size` | **TIDAK ADA DATABASE** — melakukan *full directory traversal* dan membaca ulang header setiap file dari disk setiap kali aplikasi dibuka (`MetadataScanner.swift:L12-L63`) | **TINGGI (Lambat pada Koleksi Besar)** |
| **Binary Tag Parser (FLAC/WAV/ID3)** | Custom binary parser `AudioTagReader.kt` (488 baris): membedah blok `STREAMINFO`, `VORBIS_COMMENT`, `PICTURE` FLAC, chunk `LIST/INFO` & `ID3` WAV, serta frame ID3v2.2/2.3/2.4 (`UTF-8`, `UTF-16`, `ISO-8859-1`) | Hanya mengandalkan `AVURLAsset(url:).metadata` (`MetadataScanner.swift:L144-L162`), yang di macOS sering gagal membaca *Vorbis Comments* dan *Picture Block* pada file `.flac` | **KRITIS (Metadata & Cover Art FLAC Sering Kosong)** |
| **Atribut Metadata Track** | 16 atribut: `title`, `artist`, `album`, `albumArtist`, `genre`, `trackNumber`, `year`, `composer`, `durationMs`, `fileSize`, `sampleRateHz`, `bitDepth`, `bitrateKbps`, `dateAdded`, `dateModified`, `isMqa` | Hanya 8 atribut dasar: `title`, `artist`, `album`, `duration`, `format`, `sampleRate`, `bitDepth`, `channels` (`AudioEngine.swift:L7-L30`) | **TINGGI (Kehilangan Separuh Metadata)** |
| **Deteksi MQA (Master Quality Authenticated)** | `detectMqa()` (`MetadataExtractor.kt:L135-L159`): memeriksa nama file & memindai 8 KB pertama header FLAC untuk tag `MQAENCODER` / `ORIGINALSAMPLERATE` | **TIDAK ADA** (0 baris kode deteksi MQA) | **SEDANG** |
| **Parser Nama File (`TrackTitleParser`)** | Memecah berdasarkan kemunculan **terakhir** `" - "` (`lastIndexOf(" - ")`) agar judul lagu yang mengandung tanda hubung tidak terpotong (`TrackTitleParser.kt:L10-L19`) | Memecah dengan `components(separatedBy: " - ")` dan hanya mengambil `parts[0]` dan `parts[1]`, membuang `parts[2...]` jika ada lebih dari satu `" - "` (`MetadataScanner.swift:L36-L42`) | **SEDANG (Judul/Artis Terpotong)** |
| **Cache Thumbnail & Ekstraksi Warna Dominan** | `AlbumArtThumbnailCache.kt` (disk cache MD5 + RAM cache + Semaphore 4-thread) + `ColorContrastHelper.kt` (12-Bin Hue-Family Chromatic Clustering v4 + normalisasi kontras WCAG) | Hanya `NSCache` in-memory 150 `NSImage` ukuran penuh + rata-rata piksel mentah 40×40 (`Theme.swift:L150-L249`) yang menghasilkan warna kusam/cokelat lumpur pada sampul berwarna kontras | **SEDANG-TINGGI** |
| **Mesin Pencarian (Search)** | `FuzzySearchHelper.kt`: mendukung *substring*, *multi-token out-of-order* (misal `"led iv"` cocok dengan `"Led Zeppelin IV"`), dan *subsequence fuzzy match* (`"bhm"` cocok dengan `"bohemian"`) | Hanya `localizedCaseInsensitiveContains(query)` (`AppViewModel.swift:L226-L231`) — gagal total jika kata kunci tidak berurutan persis | **SEDANG-TINGGI** |
| **Sistem Pengurutan (Sorting)** | 10+ kategori sort (`TITLE`, `ARTIST`, `ALBUM`, `ALBUM_ARTIST`, `YEAR`, `DURATION`, `DATE_ADDED`, `DATE_MODIFIED`, `FILE_SIZE`, `TRACK_NUMBER`) × (`ASCENDING` / `DESCENDING`) + Filter Chips (`Artist`, `Genre`, `Album`, `Year`, `Format`) | Hanya 3 mode (`TITLE (A-Z)`, `ARTIST (A-Z)`, `DATE (NEWEST)`), dan **mode `DATE (NEWEST)` rusak total** karena hanya memanggil `tracks.reverse()` tanpa membaca tanggal file (`AppViewModel.swift:L687-L698`)! | **TINGGI (Bug Logika Sort)** |
| **Folder Playlists Otomatis** | `buildFolderPlaylists()` (`MusicScannerHelper.kt:L179-L213`) + opsi `"Make Folder as Playlist"` di Settings | **TIDAK ADA** | **SEDANG** |
| **Penyimpanan Playlist & Pinning** | `PlaylistStorageHelper.kt`: penyimpanan atomik via file sementara `playlists_v2.json.tmp` -> `renameTo`, mendukung **Pin Playlist**, **Rename Playlist**, **Clear Tracks** | Disimpan di `UserDefaults`, tidak mendukung Pin Playlist, tidak ada fitur Rename Playlist di UI (`AppViewModel.swift:L480-L553`) | **SEDANG** |
| **Persistensi Pengaturan Aplikasi** | Semua pengaturan disimpan di `SharedPreferences` (termasuk volume per-DAC unik `vol_dac_<nama>_<mode>`) | **10 pengaturan utama TIDAK pernah disimpan ke `UserDefaults`** dan selalu ter-reset saat aplikasi ditutup (`AppViewModel.swift:L115-L185`) | **TINGGI (State Hilang Saat Restart)** |

---

## 2. Bedah Teknis Temuan demi Temuan

### 2.1. Kegagalan Ekstraksi Metadata, Artwork, & Lirik Embedded pada File FLAC di macOS
- **Lokasi Kode macOS:**
  - [`MetadataScanner.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/MetadataScanner.swift#L144-L162)
  - [`Theme.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Theme.swift#L168-L176) (`ArtworkExtractor.extractArtwork`)
  - [`LyricsManager.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/LyricsManager.swift#L49-L64)
- **Lokasi Acuan Android:** [`AudioTagReader.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/metadata/AudioTagReader.kt#L58-L214)
- **Analisa Reverse Engineering:**
  Di Android, `AudioTagReader.kt` memiliki parser biner mandiri (`readFlacTags`, `parseVorbisComments`, `readWavTags`, `readId3Tags`) yang membaca langsung blok metadata FLAC (tipe blok `4` untuk Vorbis Comments dan tipe blok `6` untuk `METADATA_BLOCK_PICTURE`) serta chunk RIFF/ID3 pada WAV.
  Sebaliknya, di `tsrossa-macos`, `MetadataScanner.swift` hanya memiliki parser biner untuk header teknis (`STREAMINFO` tipe `0` di `parseFlacHeader` baris 67–94 untuk mengambil sample rate, channels, bit depth, dan total samples), namun **melewatkan seluruh blok Vorbis Comment (tipe `4`) dan Picture (tipe `6`)**!
  Untuk membaca judul, artis, album, sampul album, dan lirik embedded, `tsrossa-macos` menyerahkannya ke `AVURLAsset(url: url).metadata`:
  ```swift
  // macOS: MetadataScanner.swift:L144-L162
  let asset = AVURLAsset(url: url)
  let metadata = asset.metadata
  for item in metadata {
      guard let commonKey = item.commonKey else { continue }
      if commonKey == .commonKeyTitle, let val = item.stringValue, !val.isEmpty {
          newTitle = val
      } // ...
  }
  ```
- **Dampak Nyata:**
  1. Pada banyak versi macOS, `AVFoundation` (`AVURLAsset`) tidak memetakan Vorbis Comments standar FLAC (seperti `ARTIST`, `ALBUM`, `DATE`, `GENRE`, `LYRICS`, `UNSYNCEDLYRICS`) maupun blok `METADATA_BLOCK_PICTURE` ke `AVMetadataKeySpace.common`. Akibatnya, file FLAC yang memiliki tag Vorbis lengkap dan sampul album beresolusi tinggi sering kali tampil sebagai `"Unknown Artist"` / `"Single / Local Storage"` dan **tanpa gambar sampul album** kecuali ada file `cover.jpg` eksternal di dalam folder yang sama!
  2. Di [`LyricsManager.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/LyricsManager.swift#L50), kode hanya memeriksa `asset.lyrics` (properti khusus iTunes/M4A/MP3 `USLT`) dan item dengan `commonKey.rawValue.lowercased().contains("lyrics")`. Karena `AVMetadataKey.commonKey` tidak memiliki key `"lyrics"` untuk Vorbis Comment FLAC, **lirik embedded di dalam file `.flac` hampir selalu gagal terbaca**.

---

### 2.2. Bug Fatal pada Logika Sorting & Race Condition `enrichMetadataInBackground`
- **Lokasi Kode macOS:**
  - [`MetadataScanner.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/MetadataScanner.swift#L60) & [`MetadataScanner.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/MetadataScanner.swift#L132-L181)
  - [`AppViewModel.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L125) & [`AppViewModel.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L679-L698)
- **Analisa Reverse Engineering:**
  Terdapat **tiga bug logika sekaligus** pada sistem pengurutan library di `tsrossa-macos`:
  1. **Ketidaksesuaian Label Sort Default vs Urutan Aktual Saat Startup:**
     Di `AppViewModel.swift:L125`, variabel state diinisialisasi dengan `sortMode: String = "DATE (NEWEST)"`. Akan tetapi, saat `scanLibrary()` memanggil `MetadataScanner.fastScanDirectory()` (`MetadataScanner.swift:L60`), hasil scan justru diurutkan berdasarkan **Judul A–Z**:
     ```swift
     // macOS: MetadataScanner.swift:L60
     return results.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
     ```
     Artinya, saat aplikasi baru dibuka, tombol UI menampilkan **`SORT: DATE (NEWEST)`**, padahal daftar lagu sebenarnya sedang diurutkan berdasarkan **`TITLE (A-Z)`**!
  2. **Mode `"DATE (NEWEST)"` Sama Sekali Tidak Mengurutkan Berdasarkan Tanggal:**
     Perhatikan implementasi `toggleSortMode()` di `AppViewModel.swift:L687-L698`:
     ```swift
     // macOS: AppViewModel.swift:L687-L698
     func toggleSortMode() {
         if sortMode == "DATE (NEWEST)" {
             sortMode = "TITLE (A-Z)"
             tracks.sort { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
         } else if sortMode == "TITLE (A-Z)" {
             sortMode = "ARTIST (A-Z)"
             tracks.sort { $0.artist.localizedCaseInsensitiveCompare($1.artist) == .orderedAscending }
         } else {
             sortMode = "DATE (NEWEST)"
             tracks.reverse() // <--- BUG! Hanya membalik urutan ARTIST (Z-A)!
         }
     }
     ```
     Ketika beralih dari `"ARTIST (A-Z)"` ke `"DATE (NEWEST)"`, kode hanya memanggil `tracks.reverse()`, yang berarti mengurutkan berdasarkan **Artis Z ke A**, bukan tanggal modifikasi/penambahan file! Model `AudioTrack` (`AudioEngine.swift:L7-L30`) bahkan tidak menyimpan `dateModified` atau `dateAdded`.
  3. **Background Enrichment Menimpa Urutan Sort Pengguna (`AppViewModel.swift:L679-L682`):**
     Saat `scanLibrary()` berjalan, `MetadataScanner.enrichMetadataInBackground(tracks: results)` dijalankan di background thread dengan salinan array awal. Jika pengguna menekan tombol Sort sementara proses enrichment sedang berjalan, begitu enrichment selesai, baris 681 (`self.tracks = enriched`) **menimpa kembali `self.tracks` dengan urutan lama**, menghapus pengurutan yang baru saja dipilih pengguna!

---

### 2.3. Sepuluh Pengaturan Aplikasi Tidak Pernah Disimpan ke `UserDefaults`
- **Lokasi Kode macOS:** [`AppViewModel.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L115-L185) & [`AppViewModel.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L371-L392)
- **Analisa Reverse Engineering:**
  Di `AppViewModel.init()`, hanya 4 data yang dimuat dari `UserDefaults.standard`:
  1. `currentFolder` (`"savedFolder"`)
  2. `isAutoplayRadio` (`"isAutoplayRadio"`)
  3. `playlists` (`"tsrossa_saved_playlists_v1"`)
  4. `likedTrackPaths` (`"tsrossa_liked_tracks_v1"`)

  Seluruh pengaturan penting lainnya **tidak pernah disimpan atau dimuat dari `UserDefaults`**:
  - `volumeDb` (selalu kembali ke `0.0 dBFS` / volume penuh 100% setiap kali aplikasi dibuka — **berbahaya bagi pendengaran/IEM sensitif**! Bandingkan dengan Android `MainActivity.kt:L766-L777` yang menyimpan volume per-DAC dan memberi nilai awal aman `50%` untuk mode DSP atau `5%` untuk speaker internal).
  - `isExclusiveHogMode`
  - `isAutoClockSwitch`
  - `isRamPlayback`
  - `accentColorMode` (`"DYNAMIC"` / `"GREEN"` / `"CYAN"` / `"AMBER"` / `"PINK"` / `"PURPLE"` / `"WHITE"`)
  - `bgCanvasMode` (`"ALBUM_GLOW"` / `"OLED_VOID"` / `"OBSIDIAN"` / `"CYBER_GRID"`)
  - `isShuffle`
  - `isRepeat`
  - `sortMode`
  - `selectedDeviceId` (perangkat DAC pilihan pengguna tidak pernah diingat saat aplikasi dibuka kembali).

---

### 2.4. Kelemahan Algoritma Ekstraksi Warna Dominan (`ArtworkExtractor` vs `ColorContrastHelper`)
- **Lokasi Kode macOS:** [`Theme.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Theme.swift#L196-L249)
- **Lokasi Acuan Android:** [`ColorContrastHelper.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/util/ColorContrastHelper.kt#L11-L304)
- **Analisa Reverse Engineering:**
  Di Android (`ColorContrastHelper.kt`), ekstraksi warna aksen dari sampul album menggunakan algoritma **12-Bin Hue-Family Chromatic Clustering (v4)**:
  - Mengelompokkan swatch ke dalam 12 keluarga hue (masing-masing 30°), memfilter bayangan gelap (`#101028`) dan pantulan silau putih (`#D0C8E0`), mendeteksi sampul monokrom sejati (`< 1.2%` area kromatik), serta menormalisasi luminansi relatif WCAG (`calculateLuminance >= 0.18f` pada tema gelap).
  Di `tsrossa-macos` (`Theme.swift:L196-L249`), `extractDominantColor` hanya mengecilkan gambar menjadi `40x40` piksel lalu **menjumlahkan rata-rata aritmatika seluruh `R`, `G`, dan `B` (`totalR / count`, `totalG / count`, `totalB / count`)**.
  - **Masalah Matematis:** Jika sebuah sampul album memiliki warna komplementer yang kuat (misalnya separuh biru terang dan separuh oranye/merah), rata-rata aritmatika RGB dari biru + oranye menghasilkan warna **abu-abu kecokelatan yang keruh**, bukan warna fokal sampul tersebut!

---

## 3. Rekomendasi Perbaikan Teknis (Actionable Fixes)

1. **Perluas Parser Biner FLAC/WAV di `MetadataScanner.swift` (Porting `AudioTagReader.kt`):**
   - Di dalam `parseFlacHeader()` (`MetadataScanner.swift:L67-L94`), jangan berhenti pada `blockType == 0` (`STREAMINFO`). Lanjutkan membaca `blockType == 4` (`VORBIS_COMMENT`) untuk mengekstrak `TITLE`, `ARTIST`, `ALBUM`, `ALBUMARTIST`, `GENRE`, `DATE`/`YEAR`, `TRACKNUMBER`, `COMPOSER`, `LYRICS`, dan `MQAENCODER`, serta `blockType == 6` (`PICTURE`) untuk sampul album. Dengan demikian, metadata, lirik, dan artwork FLAC akan bekerja 100% akurat dalam satu kali baca file tanpa bergantung pada `AVURLAsset`.
2. **Tambahkan Cache SQLite atau JSON Binary Index Inkremental:**
   - Simpan hasil scan beserta `modificationDate` dan `fileSize` di `~/Library/Application Support/tsrossa/library_cache.sqlite` (atau `.json`), sehingga saat aplikasi dibuka kembali hanya file baru/berubah yang perlu dipindai ulang.
3. **Perbaiki Logika Sorting & Simpan `dateModified` pada `AudioTrack`:**
   - Tambahkan field `dateModified: Date` dan `fileSize: Int64` pada `AudioTrack`.
   - Urutkan `DATE (NEWEST)` berdasarkan `$0.dateModified > $1.dateModified` (bukan `tracks.reverse()`), dan panggil fungsi `applyCurrentSort()` setelah `enrichMetadataInBackground` selesai.
4. **Porting `FuzzySearchHelper` & Persistensi `UserDefaults` Lengkap:**
   - Porting fungsi `FuzzySearchHelper.matchesAny()` (85 baris di [`FuzzySearchHelper.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/util/FuzzySearchHelper.kt#L10-L84)) ke Swift.
   - Simpan seluruh preferensi (`volumeDb` per perangkat, `isExclusiveHogMode`, `isAutoClockSwitch`, `isRamPlayback`, `accentColorMode`, `bgCanvasMode`, `isShuffle`, `isRepeat`, `sortMode`, `selectedDeviceId`) ke `UserDefaults` melalui `didSet`.
