# Plan 04: Binary Metadata Parser, SQLite Cache, Sorting, Fuzzy Search & Persistence

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mengganti pemindai metadata dangkal di `MetadataScanner.swift` dengan binary parser `AudioTagReader.swift` berfitur lengkap (FLAC Vorbis Comments & Picture Block, WAV LIST/INFO & ID3, ID3v2.2/2.3/2.4, Lirik, dan deteksi MQA), menambahkan database cache inkremental **SQLite (`MusicLibraryDatabase.swift`)** untuk *cold-start* `< 20 ms`, **AlbumArtCache + 12-Bin Hue-Family ColorContrastHelper**, memperbaiki seluruh **12 mode Sortir (`SortField` × `SortOrder`)**, mem-porting **FuzzySearchHelper**, serta menyimpan seluruh pengaturan & memori volume per-DAC ke `UserDefaults`.

**Architecture:** Seluruh ekstraksi tag biner dilakukan dalam Swift murni menggunakan `FileHandle` (tanpa membaca seluruh file audio ke memori), persis seperti `AudioTagReader.kt` di Android. Hasil ekstraksi disimpan ke database SQLite lokal (`~/Library/Application Support/tsrossa/music_library_cache.db`) dengan pemeriksaan diferensial (`dateModified` & `fileSize`). Artwork diekstrak satu kali, dikecilkan menjadi thumbnail `.jpg` di disk (`~/Library/Caches/com.yuka.tsrossa.macos/art_thumbs/`), dan diekstrak palet warnanya menggunakan algoritma **v4 12-Bin Hue-Family Chromatic Clustering**.

**Tech Stack:** Swift 5.9, `SQLite3` C API (`import SQLite3`), `CryptoKit` (SHA-256 untuk kunci cache sampul), `AppKit` (`NSImage`, `NSBitmapImageRep`), `UserDefaults`.

---

## File Structure

- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/AudioTagReader.swift` — Binary parser lengkap untuk:
  - FLAC: Block 0 (`STREAMINFO`), Block 4 (`VORBIS_COMMENT`: `TITLE`, `ARTIST`, `ALBUM`, `ALBUMARTIST`, `GENRE`, `DATE`/`YEAR`, `TRACKNUMBER`, `COMPOSER`, `LYRICS`/`UNSYNCEDLYRICS`, `MQAENCODER`/`ORIGINALSAMPLERATE`), dan Block 6 (`METADATA_BLOCK_PICTURE`)
  - WAV: `fmt `, `data`, `LIST`/`INFO` (`INAM`, `IART`, `IPRD`, `IGNR`, `ICRD`, `ITRK`), dan chunk `ID3 `
  - ID3v2.2 / v2.3 / v2.4: Synchsafe integer, `TIT2`/`TT2`, `TPE1`/`TP1`, `TALB`/`TAL`, `TPE2`, `TCON`, `TRCK`, `TYER`/`TDRC`, `TCOM`, `USLT`, `APIC`/`PIC` dengan encoding `ISO-8859-1`, `UTF-16`, `UTF-16BE`, dan `UTF-8`
  - `TrackTitleParser.parse(fileNameWithoutExtension:)` yang memisahkan `"Artist - Title"` pada kemunculan **terakhir** `" - "` (`lastIndexOf(" - ")`)
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/MusicLibraryDatabase.swift` — Wrapper SQLite3 native (`tracks` table + indeks) untuk pemuatan instan `< 20 ms` dan diffing inkremental.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/ColorContrastHelper.swift` — Porting algoritma **v4 12-Bin Hue-Family Chromatic Clustering & WCAG Contrast** dari `ColorContrastHelper.kt` Android.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/AlbumArtCache.swift` — Cache sampul album 3 tingkat (`NSCache` RAM + Disk Thumbnail SHA-256 + palet warna dominan).
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/FuzzySearchHelper.swift` — Pencarian 3 lapis: substring langsung, multi-token *out-of-order*, dan *character subsequence matching*.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/MetadataScanner.swift:1-215` — Memperluas `AudioTrack` dengan field lengkap (`albumArtist`, `genre`, `composer`, `year`, `trackNumber`, `dateModified`, `dateAdded`, `fileSize`, `isMqa`, `embeddedLyrics`, `dominantColorHex`) dan mengintegrasikan `AudioTagReader` + `MusicLibraryDatabase` + `AlbumArtCache`.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/AppViewModel.swift` — Mengimplementasikan enum `SortField` (6 jenis) × `SortOrder` (`ascending`/`descending`), memanggil ulang `applySorting()` seusai background enrichment, mengintegrasikan `FuzzySearchHelper`, serta mem-persist seluruh 10+ pengaturan dan memori volume per-DAC (`vol_dac_<name>_<mode>`) ke `UserDefaults`.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/tests/swift/AudioTagReaderTests.swift` — Unit test untuk parser FLAC Vorbis/Picture, ID3v2 synchsafe/UTF-16, MQA, dan `TrackTitleParser`.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/tests/swift/SortAndFuzzySearchTests.swift` — Unit test untuk 12 kombinasi sorting, `FuzzySearchHelper`, dan `ColorContrastHelper`.

---

### Task 1: Tulis Failing Tests & Implementasikan `TrackTitleParser` + Binary Parser `AudioTagReader.swift`

**Files:**
- Create: `tests/swift/AudioTagReaderTests.swift`
- Create: `macos/AudioTagReader.swift`
- Modify: `run_tests.sh`

- [ ] **Step 1: Buat `tests/swift/AudioTagReaderTests.swift` untuk menguji `TrackTitleParser` (multi-hyphen), FLAC Vorbis Comment & Picture Block sintetis, serta deteksi MQA**

```swift
import Foundation

func runAudioTagReaderTests() {
    print("==> Running AudioTagReaderTests...")

    TestSuite.run("TrackTitleParser splits on LAST occurrence of ' - ' and strips track number prefix") {
        let parsed1 = TrackTitleParser.parse("01 - AC-DC - Back In Black")
        try TestSuite.assertEqual(parsed1.artist, "01 - AC-DC")
        try TestSuite.assertEqual(parsed1.title, "Back In Black")

        let parsed2 = TrackTitleParser.parse("04. Pink Floyd - Shine On You Crazy Diamond")
        try TestSuite.assertEqual(parsed2.artist, "Pink Floyd")
        try TestSuite.assertEqual(parsed2.title, "Shine On You Crazy Diamond")

        let parsed3 = TrackTitleParser.parse("UnformattedTrackName")
        try TestSuite.assertEqual(parsed3.artist, "Unknown Artist")
        try TestSuite.assertEqual(parsed3.title, "UnformattedTrackName")
    }

    TestSuite.run("AudioTagReader parses synthetic FLAC with STREAMINFO, VORBIS_COMMENT, MQA, and Lyrics") {
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent("test_\(UUID().uuidString).flac")
        defer { try? FileManager.default.removeItem(at: tempURL) }

        let flacData = SyntheticFlacBuilder.buildSampleFlac(
            sampleRate: 96000,
            channels: 2,
            bitsPerSample: 24,
            totalSamples: 96000 * 180,
            comments: [
                "TITLE=Hotel California (Live)",
                "ARTIST=Eagles",
                "ALBUM=Hell Freezes Over",
                "ALBUMARTIST=Eagles",
                "GENRE=Rock",
                "DATE=1994",
                "TRACKNUMBER=6/15",
                "COMPOSER=Don Felder",
                "LYRICS=[00:12.00]On a dark desert highway",
                "MQAENCODER=MQAEncode v1.1"
            ]
        )
        try flacData.write(to: tempURL)

        let meta = AudioTagReader.readMetadata(from: tempURL)
        try TestSuite.assertEqual(meta.title, "Hotel California (Live)")
        try TestSuite.assertEqual(meta.artist, "Eagles")
        try TestSuite.assertEqual(meta.album, "Hell Freezes Over")
        try TestSuite.assertEqual(meta.albumArtist, "Eagles")
        try TestSuite.assertEqual(meta.genre, "Rock")
        try TestSuite.assertEqual(meta.year, 1994)
        try TestSuite.assertEqual(meta.trackNumber, 6)
        try TestSuite.assertEqual(meta.composer, "Don Felder")
        try TestSuite.assertEqual(meta.sampleRate, 96000)
        try TestSuite.assertEqual(meta.bitDepth, 24)
        try TestSuite.assertTrue(meta.isMqa)
        try TestSuite.assertEqual(meta.lyrics, "[00:12.00]On a dark desert highway")
    }
}
```

- [ ] **Step 2: Buat `macos/AudioTagReader.swift` yang mengimplementasikan `TrackTitleParser`, `ExtractedAudioMetadata`, dan parser biner FLAC / WAV / ID3v2**

Implementasikan `macos/AudioTagReader.swift` dengan rincian berikut:
1. `TrackTitleParser.parse(_ rawName: String) -> (artist: String, title: String)`:
   - Mencari `range(of: " - ", options: .backwards)` (kemunculan **terakhir**, persis `lastIndexOf(" - ")` di `TrackTitleParser.kt`).
   - Jika bagian kiri hanya berisi nomor trek (atau memiliki prefix `"04. "` pada skema satu tanda hubung), bersihkan nomor trek di awal.
2. `AudioTagReader.readMetadata(from url: URL, extractArtworkData: Bool = false) -> ExtractedAudioMetadata`:
   - **FLAC (`fLaC`)**:
     - Block 0 (`STREAMINFO`): membaca `sampleRate`, `channels`, `bitDepth`, `totalSamples`.
     - Block 4 (`VORBIS_COMMENT`): membaca little-endian `vendorLength`, `userCommentListLength`, lalu setiap pasangan `KEY=VALUE` UTF-8 untuk `TITLE`, `ARTIST`, `ALBUM`, `ALBUMARTIST`, `GENRE`, `DATE`/`YEAR`, `TRACKNUMBER`, `COMPOSER`, `LYRICS`/`UNSYNCEDLYRICS`, dan mendeteksi MQA bila terdapat `MQAENCODER` atau `ORIGINALSAMPLERATE` atau kata `"MQA"` pada komentar teknis.
     - Block 6 (`METADATA_BLOCK_PICTURE`): melewati `pictureType`, `mimeLength` + `mime`, `descLength` + `desc`, `width`, `height`, `depth`, `colors`, lalu membaca `dataLength` byte gambar jika `extractArtworkData == true`.
   - **WAV (`RIFF....WAVE`)**:
     - Membaca chunk `fmt ` dan `data` untuk durasi & format PCM.
     - Membaca chunk `LIST` bertipe `INFO` (`INAM` -> Title, `IART` -> Artist, `IPRD` -> Album, `IGNR` -> Genre, `ICRD` -> Year, `ITRK` -> TrackNumber).
     - Membaca chunk `ID3 ` atau `id3 ` dengan mendelegasikannya ke `parseID3v2(data:)`.
   - **ID3v2 (`ID3` di awal file atau di dalam chunk WAV)**:
     - Mendukung v2.2 (frame 3 huruf: `TT2`, `TP1`, `TAL`, `TRK`, `TYE`, `ULT`, `PIC`), v2.3, dan v2.4 (frame 4 huruf: `TIT2`, `TPE1`, `TALB`, `TPE2`, `TCON`, `TRCK`, `TYER`/`TDRC`, `TCOM`, `USLT`, `APIC`) dengan dekode teks berdasarkan byte encoding (`0 = ISO-8859-1`, `1 = UTF-16 with BOM`, `2 = UTF-16BE`, `3 = UTF-8`).

- [ ] **Step 3: Daftarkan `AudioTagReader.swift` dan `AudioTagReaderTests.swift` ke dalam `run_tests.sh` lalu jalankan `./run_tests.sh`**

```bash
./run_tests.sh
```

Expected: `AudioTagReaderTests` lulus 100%.

---

### Task 2: Implementasikan `ColorContrastHelper.swift` (12-Bin Hue-Family) & `AlbumArtCache.swift`

**Files:**
- Create: `macos/ColorContrastHelper.swift`
- Create: `macos/AlbumArtCache.swift`
- Create: `tests/swift/ColorAndThemeTests.swift`

- [ ] **Step 1: Buat `tests/swift/ColorAndThemeTests.swift` untuk menguji ekstraksi palet warna 12-Bin Hue-Family dan kontras WCAG**

```swift
import Foundation
import AppKit

func runColorAndThemeTests() {
    print("==> Running ColorAndThemeTests...")

    TestSuite.run("12-Bin Hue-Family clustering ignores near-black background and picks vibrant accent") {
        // 80% piksel hitam pekat (#101012), 20% piksel merah-oranye terang (#FF4500)
        var pixels: [(r: UInt8, g: UInt8, b: UInt8)] = Array(repeating: (16, 16, 18), count: 80)
        pixels.append(contentsOf: Array(repeating: (255, 69, 0), count: 20))

        let extracted = ColorContrastHelper.extractDominantAccentRGB(from: pixels)
        // Harus memilih keluarga warna merah/oranye (R jauh lebih tinggi dari B), bukan abu-abu gelap
        try TestSuite.assertTrue(extracted.r > 0.70, "Expected vibrant red channel > 0.70, got \(extracted.r)")
        try TestSuite.assertTrue(extracted.b < 0.35, "Expected low blue channel < 0.35, got \(extracted.b)")
    }

    TestSuite.run("WCAG contrast helper guarantees readable foreground on both dark and light surfaces") {
        let darkBg = (r: 0.08, g: 0.08, b: 0.10)
        let adjustedForDark = ColorContrastHelper.ensureContrast((r: 0.15, g: 0.20, b: 0.25), against: darkBg, minRatio: 4.5)
        let ratioDark = ColorContrastHelper.contrastRatio(adjustedForDark, darkBg)
        try TestSuite.assertTrue(ratioDark >= 4.49, "Contrast on dark bg was \(ratioDark)")

        let lightBg = (r: 0.95, g: 0.95, b: 0.97)
        let adjustedForLight = ColorContrastHelper.ensureContrast((r: 0.85, g: 0.90, b: 0.92), against: lightBg, minRatio: 4.5)
        let ratioLight = ColorContrastHelper.contrastRatio(adjustedForLight, lightBg)
        try TestSuite.assertTrue(ratioLight >= 4.49, "Contrast on light bg was \(ratioLight)")
    }
}
```

- [ ] **Step 2: Implementasikan `macos/ColorContrastHelper.swift` (Porting v4 12-Bin Hue-Family Chromatic Clustering dari Android)**

Implementasikan `ColorContrastHelper.swift` yang:
1. Mengonversi sampel piksel `(r, g, b)` ke HSL `(h, s, l)`.
2. Memfilter piksel terlalu gelap (`l < 0.10`), terlalu pucat/putih (`l > 0.92`), atau hampir tidak berwarna (`s < 0.12`).
3. Mengelompokkan piksel kromatik ke dalam **12 Hue Bins** (masing-masing `30°`), menghitung bobot signifikansi kromatik (`weight = pow(saturation, 1.3) * (1.0 - abs(2.0 * lightness - 1.0) * 0.6)`), dan memilih rata-rata tertimbang dari bin terbaik.
4. Menyediakan `relativeLuminance`, `contrastRatio`, dan `ensureContrast(_:against:minRatio:)` untuk menjamin keterbacaan teks pada Dark Mode maupun Light Mode.

- [ ] **Step 3: Implementasikan `macos/AlbumArtCache.swift` untuk menyimpan thumbnail sampul di disk (`SHA-256`) dan `NSCache` di RAM**

`AlbumArtCache.shared` menyediakan:
- `thumbnailImage(for trackPath: String) -> NSImage?`
- `storeAndProcessArtwork(rawData: Data?, for trackURL: URL) -> (thumbnailPath: String?, dominantHex: String?)` yang mencari artwork tersemat (`rawData`) atau file gambar folder (`cover.jpg`, `folder.jpg`, `front.png`), membuat thumbnail `300x300` JPEG di `~/Library/Caches/com.yuka.tsrossa.macos/art_thumbs/<sha256>.jpg`, serta menghitung hex warna dominan menggunakan `ColorContrastHelper`.

---

### Task 3: Implementasikan SQLite Incremental Cache (`MusicLibraryDatabase.swift`)

**Files:**
- Create: `macos/MusicLibraryDatabase.swift`
- Modify: `macos/MetadataScanner.swift`

- [ ] **Step 1: Buat `macos/MusicLibraryDatabase.swift` menggunakan `import SQLite3`**

Implementasikan `MusicLibraryDatabase` yang membuka/membuat database di `~/Library/Application Support/tsrossa/music_library_cache.db` dengan skema tabel `tracks`:

```sql
CREATE TABLE IF NOT EXISTS tracks (
    path TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    artist TEXT NOT NULL,
    album TEXT NOT NULL,
    album_artist TEXT NOT NULL,
    genre TEXT NOT NULL,
    composer TEXT NOT NULL,
    year INTEGER NOT NULL,
    track_number INTEGER NOT NULL,
    duration REAL NOT NULL,
    sample_rate INTEGER NOT NULL,
    bit_depth INTEGER NOT NULL,
    channels INTEGER NOT NULL,
    format TEXT NOT NULL,
    file_size INTEGER NOT NULL,
    date_modified REAL NOT NULL,
    date_added REAL NOT NULL,
    is_mqa INTEGER NOT NULL,
    lyrics TEXT,
    artwork_thumb_path TEXT,
    dominant_color_hex TEXT
);
CREATE INDEX IF NOT EXISTS idx_tracks_album ON tracks(album);
CREATE INDEX IF NOT EXISTS idx_tracks_artist ON tracks(artist);
```

Sediakan metode:
- `loadAllCachedTracks() -> [String: CachedTrackRecord]` (dijalankan saat startup untuk menampilkan perpustakaan dalam `< 20 ms`).
- `upsertTracks(_ records: [CachedTrackRecord])` dalam satu transaksi `BEGIN IMMEDIATE ... COMMIT`.
- `removeDeletedPaths(_ paths: [String])`.

- [ ] **Step 2: Perbarui `macos/MetadataScanner.swift` untuk melakukan Incremental Diffing**

Saat memindai folder perpustakaan:
1. Bandingkan `fileSize` dan `contentModificationDate` setiap file di disk dengan `CachedTrackRecord` dari `MusicLibraryDatabase`.
2. Jika file **tidak berubah**, gunakan langsung record dari SQLite tanpa membuka `FileHandle` atau mem-parse ulang header FLAC/WAV!
3. Hanya file **baru atau berubah** yang diparse dengan `AudioTagReader.readMetadata(from:)` dan di-upsert ke SQLite.
4. Hapus entri SQLite untuk file yang sudah tidak ada di disk.

---

### Task 4: Perbaiki 12 Mode Sortir (`SortField` × `SortOrder`), Porting `FuzzySearchHelper.swift`, dan Persistensi `UserDefaults` Lengkap

**Files:**
- Create: `macos/FuzzySearchHelper.swift`
- Create: `tests/swift/SortAndFuzzySearchTests.swift`
- Modify: `macos/AppViewModel.swift`

- [ ] **Step 1: Buat `tests/swift/SortAndFuzzySearchTests.swift` untuk menguji Fuzzy Search 3-lapis dan 12 kombinasi Sortir**

```swift
import Foundation

func runSortAndFuzzySearchTests() {
    print("==> Running SortAndFuzzySearchTests...")

    TestSuite.run("FuzzySearchHelper matches direct substring, multi-token out-of-order, and subsequence") {
        // Direct & multi-token out-of-order
        try TestSuite.assertTrue(FuzzySearchHelper.matches(query: "hotel eagles", fields: ["Hotel California", "Eagles", "Hell Freezes Over"]) > 0)
        // Subsequence match ("hkfl" -> "Hotel California")
        try TestSuite.assertTrue(FuzzySearchHelper.matches(query: "htlcal", fields: ["Hotel California", "Eagles"]) > 0)
        // Non-matching query
        try TestSuite.assertEqual(FuzzySearchHelper.matches(query: "xyz999", fields: ["Hotel California", "Eagles"]), 0)
    }

    TestSuite.run("TrackSorter correctly sorts all 6 SortFields in both Ascending and Descending orders") {
        let t1 = AudioTrack.makeTestTrack(title: "Alpha", artist: "Zed", album: "B_Album", duration: 120, dateAdded: 1000, dateModified: 5000, fileSize: 10_000)
        let t2 = AudioTrack.makeTestTrack(title: "Beta", artist: "Amy", album: "A_Album", duration: 300, dateAdded: 2000, dateModified: 3000, fileSize: 50_000)
        let list = [t1, t2]

        try TestSuite.assertEqual(TrackSorter.sort(list, by: .title, order: .ascending).first?.title, "Alpha")
        try TestSuite.assertEqual(TrackSorter.sort(list, by: .title, order: .descending).first?.title, "Beta")
        try TestSuite.assertEqual(TrackSorter.sort(list, by: .artist, order: .ascending).first?.artist, "Amy")
        try TestSuite.assertEqual(TrackSorter.sort(list, by: .album, order: .ascending).first?.album, "A_Album")
        try TestSuite.assertEqual(TrackSorter.sort(list, by: .duration, order: .descending).first?.duration, 300)
        try TestSuite.assertEqual(TrackSorter.sort(list, by: .dateAdded, order: .descending).first?.dateAdded, 2000)
        try TestSuite.assertEqual(TrackSorter.sort(list, by: .dateModified, order: .descending).first?.dateModified, 5000)
        try TestSuite.assertEqual(TrackSorter.sort(list, by: .fileSize, order: .descending).first?.fileSize, 50_000)
    }
}
```

- [ ] **Step 2: Implementasikan `macos/FuzzySearchHelper.swift`**

Implementasikan `FuzzySearchHelper.matches(query: String, fields: [String]) -> Int` yang mengembalikan skor kecocokan (`0` = tidak cocok, `100` = kecocokan persis/awalan, `80` = substring langsung, `60` = semua token kata ditemukan terlepas dari urutan, `30` = *character subsequence* berurutan untuk kueri `>= 3` karakter).

- [ ] **Step 3: Implementasikan `TrackSorter` (6+ `SortField` × `SortOrder`) dan Persistensi `UserDefaults` Lengkap di `macos/AppViewModel.swift` & `macos/AudioEngine.swift`**

Di `AppViewModel.swift` dan `AudioEngine.swift`:
1. Definisikan enum strongly-typed:
   ```swift
   enum SortField: String, Codable, CaseIterable, Identifiable {
       case title = "Title"
       case artist = "Artist"
       case album = "Album"
       case duration = "Duration"
       case dateAdded = "Date Added"
       case dateModified = "Date Modified"
       case fileSize = "File Size"
       var id: String { rawValue }
   }

   enum SortOrder: String, Codable, CaseIterable, Identifiable {
       case ascending = "Ascending"
       case descending = "Descending"
       var id: String { rawValue }
   }
   ```
2. Panggil `applySorting()` baik saat pemuatan awal dari SQLite maupun setelah background enrichment selesai sehingga urutan pengguna tidak pernah tertimpa.
3. Simpan dan muat otomatis dari `UserDefaults.standard` seluruh pengaturan yang sebelumnya hilang saat restart:
   - `sortField`, `sortOrder`, `selectedCategoryFilter`
   - `makeFolderAsPlaylist`, `keepScreenAwake`, `GlobalPinnedTrackIds`
   - `hogModeEnabled`, `bitPerfectEnabled`, `ditherEnabled`, `ramLockEnabled`, `gaplessEnabled`, `hardwareVolumeEnabled`, `bufferProfileIndex`, `dacWarmupEnabled`
   - Memori volume per-DAC: kunci `vol_dac_\(sanitizedDeviceName)_\(hogModeEnabled ? "excl" : "shared")` dengan nilai default aman (`0.35` untuk perangkat baru di mode Exclusive, `0.75` untuk built-in speaker).

- [ ] **Step 4: Jalankan `./run_tests.sh` dan `./build_mac.sh` untuk memverifikasi seluruh test lulus**

```bash
./run_tests.sh && ./build_mac.sh
```

Expected: Seluruh suite test C++ & Swift (`UpdateVersionTests`, `AudioTagReaderTests`, `ColorAndThemeTests`, `SortAndFuzzySearchTests`) lulus 100%.

- [ ] **Step 5: Commit hasil Plan 04**

```bash
git add macos/AudioTagReader.swift macos/ColorContrastHelper.swift macos/AlbumArtCache.swift macos/MusicLibraryDatabase.swift macos/FuzzySearchHelper.swift macos/MetadataScanner.swift macos/AppViewModel.swift macos/AudioEngine.swift tests/swift/ run_tests.sh
git commit -m "feat(library): add binary FLAC/WAV/ID3v2 tag reader, SQLite cache, 12-bin color clustering, 12-mode sort, and fuzzy search"
```

---

## Review Focus

1. **Zero-Full-Read Binary Parsing (`AudioTagReader.swift`):** Saat memindai ribuan file FLAC/WAV berukuran ratusan MB, `FileHandle` hanya membaca blok metadata di awal file (dan melewati blok `AUDIO DATA` dengan `seek(toFileOffset:)`), sehingga pemindaian awal sangat cepat dan tidak membebani memori.
2. **Cold Start `< 20 ms` dengan `MusicLibraryDatabase.swift`:** Pada peluncuran kedua dan seterusnya, aplikasi langsung merender daftar lagu dari SQLite dan hanya melakukan *incremental diff* terhadap file yang memiliki `dateModified` atau `fileSize` berbeda.
