# Audit & Analisa Aspek 5: Build System, Packaging, Keamanan Updater, & Kualitas Pengujian (Testing)

**Target Audit:**
- [`build_mac.sh`](file:///Users/yuka/Documents/Projects/tsrossa-macos/build_mac.sh)
- [`package_dmg.sh`](file:///Users/yuka/Documents/Projects/tsrossa-macos/package_dmg.sh)
- [`dr_impl.c`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/dr_impl.c)
- [`MacAudioDecoder.h`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/MacAudioDecoder.h)
- [`Theme.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Theme.swift)
- [`AppViewModel.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift)
- [`UpdateManager.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/UpdateManager.swift)

**Acuan Ground-Truth (Read-Only):**
- `/Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/` (`dr_flac.h`, `dr_wav.h`, `CMakeLists.txt`)
- [`AppUpdateManager.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/update/AppUpdateManager.kt)
- `/Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/test/java/com/yuka/musicplayer/` (17 file unit test)

---

## 1. Ringkasan Temuan Build System, Keamanan, & Testing

| Kategori | Kondisi di `tsrossa-ujicoba` (Android) | Kondisi di `tsrossa-macos` (macOS) | Status / Keparahan |
| :--- | :--- | :--- | :--- |
| **Kemampuan Build Mandiri (Standalone Build)** | Self-contained (`CMakeLists.txt` + `dr_flac.h` + `dr_wav.h` lengkap di dalam repo) | **GAGAL KOMPILASI (`FATAL ERROR`)** — `dr_flac.h`, `dr_wav.h`, dan ikon aplikasi **hilang dari repositori** karena `build_mac.sh` mencari di `../app/src/main/` | **KRITIS (Build Rusak Total)** |
| **Hardcoded Path Mesin Developer Lain** | Menggunakan `Environment.getExternalStoragePublicDirectory` & resource bundle internal | Hardcoded **`/Users/alen/Music/...`** di `AppViewModel.swift` dan `Theme.swift`, menyebabkan ikon aplikasi hilang (`nil`) di komputer pengguna lain | **TINGGI** |
| **Dukungan Arsitektur Binary** | Multi-ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`) | `README.md` mengklaim Universal (`Apple Silicon & Intel`), tetapi `build_mac.sh` hanya mengompilasi arsitektur host lokal tanpa flag `-arch arm64 -arch x86_64` | **SEDANG-TINGGI** |
| **Target Repositori Auto-Updater** | `yukaatsu/tsrossa` (`AppUpdateManager.kt:L42`) dengan validasi ekstensi `.apk`, ukuran byte, dan verifikasi `packageName` | Mengarah ke repo yang berbeda (`RazerZS-oss/tsrossa`), memiliki fallback berbahaya yang mengunduh **file `.zip` apa saja**, dan mengeksekusi script di `/tmp/tsrossa_update/restart.sh` tanpa verifikasi code signature | **KRITIS (Celah Keamanan & Salah Repo)** |
| **Cakupan Unit Test (Automated Tests)** | **17 file test suite (80+ unit test)** mencakup matematika DSP, timing DAC, ring buffer, CAS seek, parser metadata, MQA, sort, queue, fuzzy search, LRC, dan updater | **0 file unit test (0% test coverage)** | **TINGGI (Tanpa Jaring Pengaman Regresi)** |

---

## 2. Bedah Teknis Temuan demi Temuan

### 2.1. Kegagalan Build Mandiri (`build_mac.sh`) Akibat Hilangnya `dr_flac.h`, `dr_wav.h`, dan Asset Ikon
- **Lokasi Kode macOS:**
  - [`build_mac.sh`](file:///Users/yuka/Documents/Projects/tsrossa-macos/build_mac.sh#L4-L16) & [`build_mac.sh`](file:///Users/yuka/Documents/Projects/tsrossa-macos/build_mac.sh#L41-L45)
  - [`dr_impl.c`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/dr_impl.c#L1-L7)
  - [`MacAudioDecoder.h`](file:///Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/MacAudioDecoder.h#L12-L17)
- **Analisa Reverse Engineering:**
  Skrip `build_mac.sh` ditulis dengan asumsi bahwa proyek macOS ini berada di dalam sub-direktori (misalnya `mac/`) di dalam repositori Android:
  ```bash
  # macOS: build_mac.sh:L4-L15
  DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  ROOT_DIR="$(cd "$DIR/.." && pwd)"
  ...
  clang -c -O3 "$CPP_DIR/dr_impl.c" \
      -I "$ROOT_DIR/app/src/main/cpp" \
      -o "$BUILD_DIR/dr_impl.o"
  ```
  Dan untuk pembuatan ikon `.icns` (`build_mac.sh:L42-L44`):
  ```bash
  ANDROID_ICON="$ROOT_DIR/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
  ANDROID_FG="$ROOT_DIR/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png"
  ```
  Karena `/Users/yuka/Documents/Projects/tsrossa-macos` adalah repositori terpisah (di mana `$ROOT_DIR` adalah `/Users/yuka/Documents/Projects/`), direktori `$ROOT_DIR/app/src/main/cpp` **tidak ada**!
  Selain itu, file `dr_flac.h` dan `dr_wav.h` **belum disalin** ke dalam `/Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/`.
- **Dampak Nyata:**
  Menjalankan `./build_mac.sh` langsung berhenti dengan error kompilasi C:
  `fatal error: '../../app/src/main/cpp/dr_flac.h' file not found` pada `dr_impl.c:L3` dan `MacAudioDecoder.h:L13`.

---

### 2.2. Hardcoded Path `/Users/alen/...` di Kode Swift (Menyebabkan Logo & Ikon Aplikasi Hilang)
- **Lokasi Kode macOS:**
  - [`Theme.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Theme.swift#L371-L398) (`TsrossaBrandLogo`)
  - [`AppViewModel.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L59-L63)
- **Analisa Reverse Engineering:**
  Perhatikan implementasi `TsrossaBrandLogo` di `Theme.swift:L371-L398`:
  ```swift
  // macOS: Theme.swift:L378-L392
  let devPath = "/Users/alen/Music/tsrossa/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
  if FileManager.default.fileExists(atPath: devPath), let img = NSImage(contentsOfFile: devPath) {
      return img
  }
  return nil
  ```
  Dan di `AppViewModel.swift:L59-L61`:
  ```swift
  let musicPath = FileManager.default.urls(for: .musicDirectory, in: .userDomainMask).first?.path ?? "/Users/alen/Music"
  ```
- **Dampak Nyata:**
  Karena `build_mac.sh` gagal menemukan `$ROOT_DIR/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` saat memaketkan bundle `.app`, dan `Theme.swift` mencari fallback di `/Users/alen/Music/tsrossa/...` (akun pengguna macOS lain), maka pada komputer pengguna mana pun selain `/Users/alen`:
  1. Bundle `tsrossa.app/Contents/Resources/` tidak memiliki `AppIcon.icns` maupun `ic_launcher.png`.
  2. `NSApplication.shared.applicationIconImage` gagal disetel (`main.swift:L12-L14`).
  3. Seluruh logo aplikasi di Sidebar dan halaman About/Settings (`TsrossaBrandLogo`) merender kotak kosong!

---

### 2.3. Celah Keamanan & Bug Fungsional pada `UpdateManager.swift`
- **Lokasi Kode macOS:** [`UpdateManager.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/UpdateManager.swift#L1-L300)
- **Lokasi Acuan Android:** [`AppUpdateManager.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/update/AppUpdateManager.kt#L42-L337)
- **Analisa Reverse Engineering:**
  1. **Ketidakcocokan Repositori GitHub (`UpdateManager.swift:L8-L9`):**
     - `UpdateManager.swift` memeriksa `https://api.github.com/repos/RazerZS-oss/tsrossa/releases` (dan `README.md:L61` merujuk ke `yukars19/tsrossa-music-player`, sedangkan Android `AppUpdateManager.kt:L42` merujuk ke `yukaatsu/tsrossa`).
  2. **Fallback Berbahaya yang Mengunduh Sembarang File `.zip` (`UpdateManager.swift:L109-L119`):**
     ```swift
     // macOS: UpdateManager.swift:L109-L119
     if macAssetUrl == nil {
         for asset in assets {
             if let name = asset["name"] as? String,
                let downloadUrl = asset["browser_download_url"] as? String,
                name.lowercased().hasSuffix(".zip") {
                 macAssetUrl = downloadUrl // <--- Mengambil .zip apa saja (misal source/aset Windows/Linux)!
                 break
             }
         }
     }
     ```
     Jika sebuah rilis di GitHub hanya berisi rilis Android + arsip `.zip` lain yang bukan aplikasi macOS, `UpdateManager` tetap menganggap ada update untuk macOS dan mengunduhnya!
  3. **Kerentanan Keamanan di `/tmp/tsrossa_update` & Tanpa Verifikasi Code Signature (`UpdateManager.swift:L189-L298`):**
     - `installAndRestart()` membuat direktori dengan nama statis yang dapat ditebak di `/tmp/tsrossa_update` (direktori *world-writable* `/tmp`) dan menulis script shell `/tmp/tsrossa_update/restart.sh` dengan permission `0755`, lalu menjalankannya di background (`nohup /tmp/tsrossa_update/restart.sh`).
     - Di lingkungan multi-user atau jika ada proses lokal lain, penggunaan path statis di `/tmp` rentan terhadap *symlink / race condition hijack* (seharusnya menggunakan `FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)` atau `~/Library/Caches/com.yuka.tsrossa.macos/`).
     - Lebih lanjut, sebelum menghapus `/Applications/tsrossa.app` (`rm -rf "$TARGET_APP"` di baris 269) dan menggantinya dengan hasil ekstraksi `.zip`, **tidak ada verifikasi `codesign --verify` maupun pemeriksaan `CFBundleIdentifier` (`com.yuka.tsrossa.macos`)** seperti yang dilakukan Android (`packageName == context.packageName` di `AppUpdateManager.kt`).
  4. **Perbandingan Versi Pre-Release Tidak Akurat (`UpdateManager.swift:L149-L169`):**
     - Di Android (`AppUpdateManager.kt:L256-L336` & diuji di `AppUpdateManagerTest.kt`), fungsi `isNewerVersion` membandingkan suffix pre-release secara cerdas (`alpha < beta < rc < stable`, serta `beta10 > beta2`).
     - Di `UpdateManager.swift:L155-L156`:
       ```swift
       let rParts = rClean.components(separatedBy: "-")[0].components(separatedBy: ".").compactMap { Int($0) }
       let cParts = cClean.components(separatedBy: "-")[0].components(separatedBy: ".").compactMap { Int($0) }
       ```
       Bagian setelah tanda `"-" ` (`components(separatedBy: "-")[1]`) **dibuang seluruhnya**! Akibatnya, jika pengguna menjalankan `1.5.0-beta1` dan rilis stabil `1.5.0` (atau `1.5.0-beta2`) keluar, `UpdateManager.swift` menganggap `1.5.0 == 1.5.0` dan melaporkan **`"You are running the latest version"`** (gagal mendeteksi update)!

---

### 2.4. Absennya Unit Test (0 Test di macOS vs 17 Test Suite di Android)
- **Lokasi Acuan Android:** `/Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/test/java/com/yuka/musicplayer/`
- **Analisa Reverse Engineering:**
  Proyek Android asli memiliki **17 file unit test (total ~2.100 baris kode pengujian)** yang memverifikasi:
  - Matematika kurva volume psikoakustik & kuantisasi UAC (`VolumeAndStreamingMathTest.kt`)
  - Modulo ring buffer 64MB pada lagu 2 jam 192kHz, proteksi CAS saat seek, dan gapless handoff (`VolumeAndStreamingMathTest.kt`)
  - Presisi timing osiloskop digital DAC & akumulator paket Bresenham 8000 FPS (`DacHardwareTimingTest.kt`)
  - Siklus callback JNI (`AudioEngineEventTest.kt`)
  - Deteksi MQA & ketahanan terhadap header FLAC korup (`MetadataExtractorTest.kt`)
  - 12 mode pengurutan library (`LibrarySortTest.kt`)
  - Logika antrian prioritas, shuffle deck, dan 3-state repeat (`QueueHelperTest.kt`)
  - Algoritma klasterisasi warna 12-Bin Hue-Family & kontras WCAG (`ColorContrastHelperTest.kt`)
  - Pencarian fuzzy multi-token & subsequence (`FuzzySearchHelperTest.kt`)
  - Parser LRC & ekstraksi lirik Vorbis Comment biner (`LyricsHelperTest.kt`)
  - Pemindaian inkremental & pembuatan folder playlist (`MusicScannerHelperTest.kt`)
  - Persistensi atomik `.tmp` playlist (`PlaylistStorageHelperTest.kt`)
  - Perbandingan versi semantik pre-release updater (`AppUpdateManagerTest.kt`)

  Sebaliknya, `tsrossa-macos` memiliki **0 unit test**. Ketiadaan pengujian otomatis inilah yang menjelaskan mengapa bug mendasar seperti **kompilasi `build_mac.sh` gagal**, **sort `DATE (NEWEST)` hanya memanggil `reverse()`**, **`isRepeat` konflik antara `RepeatOne` dan `RepeatAll`**, serta **versi `1.5.0-beta1` gagal update ke `1.5.0`** lolos tanpa terdeteksi.

---

## 3. Rekomendasi Perbaikan Teknis (Actionable Fixes)

1. **Jadikan Repositori `tsrossa-macos` 100% Mandiri (Self-Contained):**
   - Salin `dr_flac.h` dan `dr_wav.h` ke dalam `src/nativeMain/cpp/`, serta salin `ic_launcher.png` dan `ic_launcher_foreground.png` ke dalam direktori `macos/Resources/`.
   - Ubah `#include "../../app/src/main/cpp/dr_flac.h"` di `dr_impl.c` dan `MacAudioDecoder.h` menjadi `#include "dr_flac.h"`.
   - Perbarui `build_mac.sh` agar mereferensikan `$DIR/src/nativeMain/cpp` dan `$DIR/macos/Resources`, serta hapus seluruh hardcoded path `/Users/alen/...` di `Theme.swift` dan `AppViewModel.swift`.
2. **Tambahkan Dukungan Universal Binary (`arm64` + `x86_64`) di `build_mac.sh`:**
   - Tambahkan flag `-arch arm64 -arch x86_64 -mmacosx-version-min=12.0` pada pemanggilan `clang`, `clang++`, dan `swiftc` agar sesuai dengan klaim di `README.md`.
3. **Perbaiki Keamanan & Logika SemVer di `UpdateManager.swift`:**
   - Pastikan asset yang dipilih wajib mengandung kata `"mac"`, `"macos"`, `"darwin"`, atau `"osx"`, bukan sembarang `.zip`.
   - Gunakan direktori khusus di `FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)` dengan sub-folder `UUID().uuidString` (bukan `/tmp/tsrossa_update`), validasi `CFBundleIdentifier == "com.yuka.tsrossa.macos"` pada `Info.plist` hasil unzip sebelum menimpa aplikasi, dan porting logika `comparePreReleaseSuffix` dari `AppUpdateManager.kt`.
