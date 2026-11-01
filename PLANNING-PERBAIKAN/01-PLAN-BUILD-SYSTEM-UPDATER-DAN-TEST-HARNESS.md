# Plan 01: Build System Mandiri, Keamanan Updater & Test Harness

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Membebaskan repositori `/Users/yuka/Documents/Projects/tsrossa-macos` dari ketergantungan path lokal `/Users/alen/Downloads/tsrossa-perfect/...`, mengamankan mekanisme auto-update di `UpdateManager.swift` dengan parser SemVer pre-release & verifikasi `codesign`, serta membangun kerangka pengujian otomatis (`./run_tests.sh`) untuk C++ dan Swift.

**Architecture:** Menyalin pustaka header-only `dr_flac.h` dan `dr_wav.h` serta aset ikon resmi dari direktori referensi Android (`/Users/yuka/Documents/Projects/tsrossa-ujicoba` — **READ-ONLY**) ke dalam repositori macOS. Memperbarui `build_mac.sh` agar menggunakan path relatif `$DIR` dan mendukung flag kompilasi modern (`-mmacosx-version-min=13.0`, `-lsqlite3`, `-framework MediaPlayer`, `-framework IOKit`). Mengekstrak logika pembanding versi dan validasi rilis updater ke fungsi murni yang diuji secara ketat melalui `./run_tests.sh`.

**Tech Stack:** Bash/Zsh (`build_mac.sh`, `run_tests.sh`), Clang/Clang++17, Swift 5.9 (`swiftc`), macOS `codesign` & `FileManager`.

---

## File Structure

- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/dr_flac.h` — Header dekoder FLAC disalin utuh dari referensi Android.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/dr_wav.h` — Header dekoder WAV disalin utuh dari referensi Android.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/Resources/ic_launcher.png` — Ikon utama aplikasi disalin dari referensi Android.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/Resources/ic_launcher_foreground.png` — Ikon foreground untuk about/watermark disalin dari referensi Android.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/dr_impl.c:1-6` — Mengganti `#include "/Users/alen/..."` menjadi `#include "dr_flac.h"` dan `#include "dr_wav.h"`.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/MacAudioDecoder.h:1-15` — Mengganti `#include "/Users/alen/..."` menjadi `#include "dr_flac.h"` dan `#include "dr_wav.h"`.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/Theme.swift:370-405` — Menghapus fallback path `/Users/alen/...` pada `AppLogoImage` dan `AboutAppLogoImage`, memuat dari `Bundle.main.resourceURL`.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/AppViewModel.swift:55-68` — Menghapus fallback path `/Users/alen/...` pada `loadAppIcon()`.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/build_mac.sh:1-110` — Menghapus semua referensi `/Users/alen/...`, menyalin `macos/Resources/*` ke `Contents/Resources/`, menautkan `-lsqlite3 -framework MediaPlayer -framework IOKit`, dan menambahkan opsi `--universal`.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/UpdateManager.swift:1-208` — Memperbaiki konstanta repo GitHub, mem-porting `isNewerVersion` + `comparePreReleaseSuffix` (`alpha < beta < rc < stable`, natural number sort `beta10 > beta2`), memperketat pemilihan aset khusus macOS, mengganti `/tmp/tsrossa_update` dengan `cachesDirectory/UUID`, dan memverifikasi `CFBundleIdentifier` serta `codesign --verify --deep --strict`.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/run_tests.sh` — Script eksekusi unit test C++ dan Swift.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/tests/swift/TestRunner.swift` — Harness penguji asersi Swift ringan tanpa ketergantungan Xcode penuh.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/tests/swift/UpdateVersionTests.swift` — Suite pengujian SemVer pre-release, filter aset macOS, dan validasi bundle.

---

### Task 1: Salin Header `dr_libs` & Aset Ikon serta Hapus Path Hardcoded `/Users/alen` di C++ dan Swift

**Files:**
- Create: `src/nativeMain/cpp/dr_flac.h`
- Create: `src/nativeMain/cpp/dr_wav.h`
- Create: `macos/Resources/ic_launcher.png`
- Create: `macos/Resources/ic_launcher_foreground.png`
- Modify: `src/nativeMain/cpp/dr_impl.c:1-6`
- Modify: `src/nativeMain/cpp/MacAudioDecoder.h:1-15`
- Modify: `macos/Theme.swift:370-405`
- Modify: `macos/AppViewModel.swift:55-68`

- [ ] **Step 1: Salin `dr_flac.h`, `dr_wav.h`, dan ikon dari proyek referensi secara read-only**

Jalankan perintah berikut dari `/Users/yuka/Documents/Projects/tsrossa-macos`:

```bash
mkdir -p src/nativeMain/cpp macos/Resources
cp /Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/dr_flac.h src/nativeMain/cpp/dr_flac.h
cp /Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/cpp/dr_wav.h src/nativeMain/cpp/dr_wav.h
cp /Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png macos/Resources/ic_launcher.png
cp /Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png macos/Resources/ic_launcher_foreground.png
```

- [ ] **Step 2: Perbarui `src/nativeMain/cpp/dr_impl.c` agar menggunakan header lokal**

Ubah isi `src/nativeMain/cpp/dr_impl.c` menjadi:

```c
#define DR_FLAC_IMPLEMENTATION
#include "dr_flac.h"

#define DR_WAV_IMPLEMENTATION
#include "dr_wav.h"
```

- [ ] **Step 3: Perbarui `src/nativeMain/cpp/MacAudioDecoder.h` agar menggunakan header lokal**

Di bagian atas `src/nativeMain/cpp/MacAudioDecoder.h`, ganti blok `#include`:

```cpp
#pragma once

#include <string>
#include <vector>
#include <cstdint>
#include <cstring>
#include <algorithm>
#include <iostream>

#include "dr_flac.h"
#include "dr_wav.h"
```

- [ ] **Step 4: Hapus path `/Users/alen/...` di `macos/Theme.swift` dan `macos/AppViewModel.swift`**

Di `macos/Theme.swift` (sekitar baris 370-405), ganti `AppLogoImage` dan `AboutAppLogoImage` agar memuat dari `Bundle.main` dan direktori relatif kerja saat pengembangan:

```swift
struct AppLogoImage: View {
    var size: CGFloat = 28
    var body: some View {
        if let img = Self.loadBundledImage(named: "ic_launcher_foreground.png") ?? Self.loadBundledImage(named: "AppIcon.png") {
            Image(nsImage: img)
                .resizable()
                .interpolation(.high)
                .antialiased(true)
                .scaledToFit()
                .frame(width: size, height: size)
        } else {
            Image(systemName: "waveform.circle.fill")
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
        }
    }

    static func loadBundledImage(named name: String) -> NSImage? {
        if let resourceURL = Bundle.main.resourceURL?.appendingPathComponent(name),
           let image = NSImage(contentsOf: resourceURL) {
            return image
        }
        let fallbackURL = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
            .appendingPathComponent("macos/Resources")
            .appendingPathComponent(name)
        return NSImage(contentsOf: fallbackURL)
    }
}

struct AboutAppLogoImage: View {
    var size: CGFloat = 84
    var body: some View {
        AppLogoImage(size: size)
    }
}
```

Di `macos/AppViewModel.swift` (sekitar baris 55-68), perbarui `loadAppIcon()`:

```swift
    private func loadAppIcon() {
        if let img = AppLogoImage.loadBundledImage(named: "AppIcon.png") ?? AppLogoImage.loadBundledImage(named: "ic_launcher.png") {
            NSApplication.shared.applicationIconImage = img
        }
    }
```

- [ ] **Step 5: Verifikasi bahwa tidak ada lagi string `/Users/alen` di kode C++ dan Swift**

Jalankan:

```bash
grep -rn "/Users/alen" src/ macos/
```

Expected: Output kosong (exit code 1).

---

### Task 2: Perbarui `build_mac.sh` untuk Build Mandiri, Link Framework macOS, dan Dukungan Universal Binary

**Files:**
- Modify: `build_mac.sh:1-110`

- [ ] **Step 1: Perbarui `build_mac.sh` secara menyeluruh**

Ganti isi `build_mac.sh` dengan script berikut yang menggunakan `$DIR` lokal, menautkan `sqlite3`, `MediaPlayer`, dan `IOKit`, serta mendukung argumen `--universal`:

```bash
#!/bin/bash
set -euo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BUILD_DIR="$DIR/build"
APP_NAME="TSRossa"
APP_BUNDLE="$BUILD_DIR/$APP_NAME.app"
CONTENTS_DIR="$APP_BUNDLE/Contents"
MACOS_DIR="$CONTENTS_DIR/MacOS"
RESOURCES_DIR="$CONTENTS_DIR/Resources"

ARCH_FLAGS=""
SWIFT_ARCH_FLAGS=()
if [[ "${1:-}" == "--universal" ]]; then
    echo "==> Mode Universal Binary (arm64 + x86_64) aktif"
    ARCH_FLAGS="-arch arm64 -arch x86_64"
    SWIFT_ARCH_FLAGS=(-target arm64-apple-macos13.0 -target x86_64-apple-macos13.0)
else
    HOST_ARCH="$(uname -m)"
    ARCH_FLAGS="-arch $HOST_ARCH"
    SWIFT_ARCH_FLAGS=(-target "${HOST_ARCH}-apple-macos13.0")
fi

echo "==> [1/5] Membersihkan direktori build..."
rm -rf "$BUILD_DIR"
mkdir -p "$MACOS_DIR" "$RESOURCES_DIR"

echo "==> [2/5] Mengompilasi C/C++ Native Audio Engine..."
clang -c -O3 -ffast-math -mmacosx-version-min=13.0 $ARCH_FLAGS \
    -I"$DIR/src/nativeMain/cpp" \
    "$DIR/src/nativeMain/cpp/dr_impl.c" \
    -o "$BUILD_DIR/dr_impl.o"

clang++ -c -std=c++17 -O3 -ffast-math -mmacosx-version-min=13.0 $ARCH_FLAGS \
    -I"$DIR/src/nativeMain/cpp" \
    "$DIR/src/nativeMain/cpp/CoreAudioSink.cpp" \
    -o "$BUILD_DIR/CoreAudioSink.o"

clang++ -c -std=c++17 -O3 -ffast-math -mmacosx-version-min=13.0 $ARCH_FLAGS \
    -I"$DIR/src/nativeMain/cpp" \
    "$DIR/src/nativeMain/cpp/TsrossaAudioBridge.cpp" \
    -o "$BUILD_DIR/TsrossaAudioBridge.o"

libtool -static -o "$BUILD_DIR/libtsrossa_audio.a" \
    "$BUILD_DIR/dr_impl.o" \
    "$BUILD_DIR/CoreAudioSink.o" \
    "$BUILD_DIR/TsrossaAudioBridge.o"

echo "==> [3/5] Mengompilasi Swift UI & Application Layer..."
SWIFT_FILES=()
while IFS= read -r -d '' file; do
    SWIFT_FILES+=("$file")
done < <(find "$DIR/macos" -name "*.swift" -print0 | sort -z)

swiftc -O "${SWIFT_ARCH_FLAGS[0]}" \
    -import-objc-header "$DIR/src/nativeMain/cpp/TsrossaAudioBridge.h" \
    "${SWIFT_FILES[@]}" \
    "$BUILD_DIR/libtsrossa_audio.a" \
    -lc++ -lsqlite3 \
    -framework Cocoa \
    -framework SwiftUI \
    -framework CoreAudio \
    -framework AudioToolbox \
    -framework AudioUnit \
    -framework Accelerate \
    -framework UniformTypeIdentifiers \
    -framework MediaPlayer \
    -framework IOKit \
    -o "$MACOS_DIR/$APP_NAME"

echo "==> [4/5] Menyalin Resources & Membuat Info.plist..."
if [ -f "$DIR/macos/Resources/ic_launcher.png" ]; then
    cp "$DIR/macos/Resources/ic_launcher.png" "$RESOURCES_DIR/AppIcon.png"
    cp "$DIR/macos/Resources/ic_launcher.png" "$RESOURCES_DIR/ic_launcher.png"
fi
if [ -f "$DIR/macos/Resources/ic_launcher_foreground.png" ]; then
    cp "$DIR/macos/Resources/ic_launcher_foreground.png" "$RESOURCES_DIR/ic_launcher_foreground.png"
fi

cat > "$CONTENTS_DIR/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>$APP_NAME</string>
    <key>CFBundleIconFile</key>
    <string>AppIcon.png</string>
    <key>CFBundleIdentifier</key>
    <string>com.yuka.tsrossa.macos</string>
    <key>CFBundleName</key>
    <string>$APP_NAME</string>
    <key>CFBundleDisplayName</key>
    <string>TSRossa Audio Player</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>5.4</string>
    <key>CFBundleVersion</key>
    <string>62</string>
    <key>LSMinimumSystemVersion</key>
    <string>13.0</string>
    <key>NSHighResolutionCapable</key>
    <true/>
</dict>
</plist>
EOF

echo "==> [5/5] Ad-hoc Code Signing Bundle..."
codesign --force --deep --sign - "$APP_BUNDLE"

echo "==> Build selesai: $APP_BUNDLE"
```

- [ ] **Step 2: Jalankan `./build_mac.sh` dan pastikan tidak ada lagi `/Users/alen` di seluruh repositori**

```bash
chmod +x build_mac.sh
./build_mac.sh
grep -rn "/Users/alen" build_mac.sh src/ macos/
```

Expected: Build menghasilkan `build/TSRossa.app` dengan sukses dan `grep` mengembalikan kosong.

---

### Task 3: Buat Test Harness (`run_tests.sh` & `TestRunner.swift`) dan Tulis Failing Test untuk `UpdateManager`

**Files:**
- Create: `run_tests.sh`
- Create: `tests/swift/TestRunner.swift`
- Create: `tests/swift/UpdateVersionTests.swift`

- [ ] **Step 1: Buat `tests/swift/TestRunner.swift` sebagai runner pengujian unit Swift mandiri**

```swift
import Foundation

struct TestFailure: Error, CustomStringConvertible {
    let message: String
    let file: String
    let line: Int
    var description: String { "\(file):\(line) - \(message)" }
}

final class TestSuite {
    static var passedCount = 0
    static var failedCount = 0

    static func run(_ name: String, _ block: () throws -> Void) {
        do {
            try block()
            passedCount += 1
            print("  [PASS] \(name)")
        } catch {
            failedCount += 1
            print("  [FAIL] \(name): \(error)")
        }
    }

    static func assertEqual<T: Equatable>(_ actual: T, _ expected: T, _ message: String = "", file: String = #fileID, line: Int = #line) throws {
        if actual != expected {
            throw TestFailure(message: "Expected \(expected), got \(actual). \(message)", file: file, line: line)
        }
    }

    static func assertTrue(_ condition: Bool, _ message: String = "", file: String = #fileID, line: Int = #line) throws {
        if !condition {
            throw TestFailure(message: "Assertion failed. \(message)", file: file, line: line)
        }
    }

    static func assertFalse(_ condition: Bool, _ message: String = "", file: String = #fileID, line: Int = #line) throws {
        if condition {
            throw TestFailure(message: "Expected false, got true. \(message)", file: file, line: line)
        }
    }
}
```

- [ ] **Step 2: Buat `tests/swift/UpdateVersionTests.swift` untuk menguji SemVer pre-release & filter aset macOS**

```swift
import Foundation

func runUpdateVersionTests() {
    print("==> Running UpdateVersionTests...")

    TestSuite.run("Stable release is newer than lower stable release") {
        try TestSuite.assertTrue(VersionComparator.isNewerVersion(latestTag: "v5.5.0", currentVersion: "5.4.0"))
        try TestSuite.assertFalse(VersionComparator.isNewerVersion(latestTag: "v5.4.0", currentVersion: "5.4.0"))
        try TestSuite.assertFalse(VersionComparator.isNewerVersion(latestTag: "v5.3.9", currentVersion: "5.4.0"))
    }

    TestSuite.run("Stable release is newer than pre-release of same numeric version") {
        try TestSuite.assertTrue(VersionComparator.isNewerVersion(latestTag: "v5.4.0", currentVersion: "5.4.0-rc1"))
        try TestSuite.assertTrue(VersionComparator.isNewerVersion(latestTag: "5.4.0", currentVersion: "5.4.0-beta2"))
        try TestSuite.assertFalse(VersionComparator.isNewerVersion(latestTag: "5.4.0-rc1", currentVersion: "5.4.0"))
    }

    TestSuite.run("Pre-release ordering alpha < beta < rc and natural number beta2 < beta10") {
        try TestSuite.assertTrue(VersionComparator.isNewerVersion(latestTag: "5.4.0-beta1", currentVersion: "5.4.0-alpha9"))
        try TestSuite.assertTrue(VersionComparator.isNewerVersion(latestTag: "5.4.0-rc1", currentVersion: "5.4.0-beta10"))
        try TestSuite.assertTrue(VersionComparator.isNewerVersion(latestTag: "5.4.0-beta10", currentVersion: "5.4.0-beta2"))
        try TestSuite.assertFalse(VersionComparator.isNewerVersion(latestTag: "5.4.0-beta2", currentVersion: "5.4.0-beta10"))
    }

    TestSuite.run("macOS Asset Selector rejects Android/generic zips and accepts only macOS assets") {
        let androidAssets = [
            GitHubAssetInfo(name: "tsrossa-v5.5-android-universal.apk", browserDownloadUrl: "https://example.com/a.apk"),
            GitHubAssetInfo(name: "tsrossa-v5.5-android-debug-symbols.zip", browserDownloadUrl: "https://example.com/b.zip")
        ]
        try TestSuite.assertEqual(VersionComparator.selectMacOSAsset(from: androidAssets), nil)

        let mixedAssets = androidAssets + [
            GitHubAssetInfo(name: "TSRossa-macOS-v5.5-universal.zip", browserDownloadUrl: "https://example.com/mac.zip")
        ]
        let selected = VersionComparator.selectMacOSAsset(from: mixedAssets)
        try TestSuite.assertEqual(selected?.name, "TSRossa-macOS-v5.5-universal.zip")
    }
}
```

- [ ] **Step 3: Buat `run_tests.sh` untuk menjalankan seluruh suite pengujian**

```bash
#!/bin/bash
set -euo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
TEST_BUILD_DIR="$DIR/build/tests"
mkdir -p "$TEST_BUILD_DIR"

echo "========================================="
echo "  TSRossa macOS Automated Test Runner"
echo "========================================="

# 1. C++ Unit Tests (jika ada di tests/cpp/)
if ls "$DIR/tests/cpp/"*.cpp >/dev/null 2>&1; then
    for cpp_test in "$DIR/tests/cpp/"*.cpp; do
        test_name="$(basename "$cpp_test" .cpp)"
        echo "==> Compiling C++ test: $test_name"
        clang++ -std=c++17 -O2 -I"$DIR/src/nativeMain/cpp" \
            "$DIR/src/nativeMain/cpp/dr_impl.c" \
            "$cpp_test" \
            -framework CoreAudio -framework AudioToolbox -framework AudioUnit \
            -o "$TEST_BUILD_DIR/$test_name"
        "$TEST_BUILD_DIR/$test_name"
    done
fi

# 2. Swift Unit Tests
echo "==> Compiling Swift tests..."
cat > "$TEST_BUILD_DIR/main_test.swift" <<EOF
import Foundation

@main
struct MainTestRunner {
    static func main() {
        runUpdateVersionTests()
        if TestSuite.failedCount > 0 {
            print("\nRESULT: \(TestSuite.failedCount) FAILED, \(TestSuite.passedCount) PASSED")
            Foundation.exit(1)
        } else {
            print("\nRESULT: ALL \(TestSuite.passedCount) SWIFT TESTS PASSED")
        }
    }
}
EOF

swiftc -O \
    "$DIR/macos/UpdateManager.swift" \
    "$DIR/tests/swift/TestRunner.swift" \
    "$DIR/tests/swift/UpdateVersionTests.swift" \
    "$TEST_BUILD_DIR/main_test.swift" \
    -framework Cocoa \
    -o "$TEST_BUILD_DIR/swift_tests"

"$TEST_BUILD_DIR/swift_tests"
```

- [ ] **Step 4: Jalankan `./run_tests.sh` untuk memverifikasi kegagalan awal (Red Phase TDD)**

```bash
chmod +x run_tests.sh
./run_tests.sh
```

Expected: Gagal kompilasi karena `VersionComparator` dan `GitHubAssetInfo` belum didefinisikan di `macos/UpdateManager.swift`.

---

### Task 4: Implementasikan Perbaikan Keamanan & SemVer Pre-Release di `UpdateManager.swift`

**Files:**
- Modify: `macos/UpdateManager.swift:1-208`

- [ ] **Step 1: Perbarui `macos/UpdateManager.swift` dengan `VersionComparator`, seleksi aset macOS ketat, Cache directory unik, serta verifikasi `CFBundleIdentifier` dan `codesign`**

Ganti isi `macos/UpdateManager.swift` dengan implementasi berikut:

```swift
import Foundation
import AppKit

public struct GitHubAssetInfo: Equatable, Codable {
    public let name: String
    public let browserDownloadUrl: String

    enum CodingKeys: String, CodingKey {
        case name
        case browserDownloadUrl = "browser_download_url"
    }

    public init(name: String, browserDownloadUrl: String) {
        self.name = name
        self.browserDownloadUrl = browserDownloadUrl
    }
}

public enum VersionComparator {
    /// Membandingkan versi SemVer termasuk suffix pre-release (alpha < beta < rc < stable)
    /// serta perbandingan angka natural (beta10 > beta2), selaras dengan AppUpdateManager.kt di Android.
    public static func isNewerVersion(latestTag: String, currentVersion: String) -> Bool {
        let cleanLatest = stripPrefix(latestTag)
        let cleanCurrent = stripPrefix(currentVersion)
        if cleanLatest.isEmpty || cleanCurrent.isEmpty || cleanLatest == cleanCurrent {
            return false
        }

        let (latestCore, latestSuffix) = splitCoreAndSuffix(cleanLatest)
        let (currentCore, currentSuffix) = splitCoreAndSuffix(cleanCurrent)

        let maxParts = max(latestCore.count, currentCore.count)
        for i in 0..<maxParts {
            let lVal = i < latestCore.count ? latestCore[i] : 0
            let cVal = i < currentCore.count ? currentCore[i] : 0
            if lVal != cVal {
                return lVal > cVal
            }
        }

        // Jika angka inti sama: rilis stabil (tanpa suffix) lebih baru daripada pre-release
        if latestSuffix.isEmpty && !currentSuffix.isEmpty { return true }
        if !latestSuffix.isEmpty && currentSuffix.isEmpty { return false }
        if latestSuffix.isEmpty && currentSuffix.isEmpty { return false }

        return comparePreReleaseSuffix(latestSuffix, currentSuffix) > 0
    }

    public static func selectMacOSAsset(from assets: [GitHubAssetInfo]) -> GitHubAssetInfo? {
        let macKeywords = ["macos", "mac", "darwin", "osx"]
        let bannedKeywords = ["android", "windows", "win64", "linux", "debug-symbols"]

        return assets.first { asset in
            let lower = asset.name.lowercased()
            let hasSupportedExt = lower.hasSuffix(".zip") || lower.hasSuffix(".dmg")
            let hasMacKeyword = macKeywords.contains { lower.contains($0) }
            let hasBannedKeyword = bannedKeywords.contains { lower.contains($0) }
            return hasSupportedExt && hasMacKeyword && !hasBannedKeyword
        }
    }

    private static func stripPrefix(_ raw: String) -> String {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("v") || s.hasPrefix("V") {
            s.removeFirst()
        }
        if let plusIdx = s.firstIndex(of: "+") {
            s = String(s[..<plusIdx])
        }
        return s
    }

    private static func splitCoreAndSuffix(_ version: String) -> ([Int], String) {
        let parts = version.split(separator: "-", maxSplits: 1, omittingEmptySubsequences: false)
        let coreStr = String(parts[0])
        let suffixStr = parts.count > 1 ? String(parts[1]).lowercased() : ""
        let coreNums = coreStr.split(separator: ".").map { part -> Int in
            let digits = part.prefix { $0.isNumber }
            return Int(digits) ?? 0
        }
        return (coreNums, suffixStr)
    }

    private static func comparePreReleaseSuffix(_ a: String, _ b: String) -> Int {
        if a == b { return 0 }
        let rankA = preReleaseStageRank(a)
        let rankB = preReleaseStageRank(b)
        if rankA != rankB {
            return rankA > rankB ? 1 : -1
        }
        return a.compare(b, options: [.numeric, .caseInsensitive]).rawValue
    }

    private static func preReleaseStageRank(_ suffix: String) -> Int {
        if suffix.hasPrefix("rc") { return 3 }
        if suffix.hasPrefix("beta") || suffix.hasPrefix("b") { return 2 }
        if suffix.hasPrefix("alpha") || suffix.hasPrefix("a") { return 1 }
        return 0
    }
}

class UpdateManager: ObservableObject {
    static let shared = UpdateManager()

    static let githubOwner = "alens"
    static let githubRepo = "tsrossa"
    static let expectedBundleIdentifier = "com.yuka.tsrossa.macos"

    @Published var isChecking: Bool = false
    @Published var isDownloading: Bool = false
    @Published var downloadProgress: Double = 0.0
    @Published var updateAvailable: Bool = false
    @Published var latestVersion: String = ""
    @Published var releaseNotes: String = ""
    @Published var downloadURL: URL? = nil
    @Published var assetFileName: String = ""
    @Published var statusMessage: String = ""
    @Published var errorMessage: String? = nil

    private var downloadTask: URLSessionDownloadTask?

    var currentVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "5.4"
    }

    func checkForUpdates(manual: Bool = false) {
        guard !isChecking else { return }
        isChecking = true
        errorMessage = nil
        if manual {
            statusMessage = "Memeriksa pembaruan di GitHub..."
        }

        let urlStr = "https://api.github.com/repos/\(Self.githubOwner)/\(Self.githubRepo)/releases/latest"
        guard let url = URL(string: urlStr) else {
            finishCheckWithError("URL repositori GitHub tidak valid.")
            return
        }

        var request = URLRequest(url: url)
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.setValue("TSRossa-macOS/\(currentVersion)", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 15

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.isChecking = false

                if let error = error {
                    if manual { self.errorMessage = "Gagal terhubung: \(error.localizedDescription)" }
                    self.statusMessage = ""
                    return
                }

                guard let httpResp = response as? HTTPURLResponse, httpResp.statusCode == 200, let data = data else {
                    if manual { self.errorMessage = "Belum ada rilis publik macOS yang tersedia di GitHub." }
                    self.statusMessage = ""
                    return
                }

                self.parseReleaseData(data, manual: manual)
            }
        }.resume()
    }

    private func parseReleaseData(_ data: Data, manual: Bool) {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let tagName = json["tag_name"] as? String else {
            if manual { errorMessage = "Respons JSON rilis tidak valid." }
            return
        }

        let body = json["body"] as? String ?? "Perbaikan performa dan peningkatan stabilitas audio."
        var parsedAssets: [GitHubAssetInfo] = []
        if let rawAssets = json["assets"] as? [[String: Any]] {
            for item in rawAssets {
                if let name = item["name"] as? String,
                   let dl = item["browser_download_url"] as? String {
                    parsedAssets.append(GitHubAssetInfo(name: name, browserDownloadUrl: dl))
                }
            }
        }

        if VersionComparator.isNewerVersion(latestTag: tagName, currentVersion: currentVersion) {
            guard let macAsset = VersionComparator.selectMacOSAsset(from: parsedAssets),
                  let assetURL = URL(string: macAsset.browserDownloadUrl) else {
                updateAvailable = false
                if manual {
                    statusMessage = "Rilis \(tagName) ditemukan, namun paket khusus macOS belum tersedia."
                }
                return
            }

            latestVersion = tagName
            releaseNotes = body
            downloadURL = assetURL
            assetFileName = macAsset.name
            updateAvailable = true
            statusMessage = "Pembaruan \(tagName) siap diunduh (\(macAsset.name))."
        } else {
            updateAvailable = false
            if manual {
                statusMessage = "Anda sudah menggunakan versi terbaru (v\(currentVersion))."
            }
        }
    }

    func downloadAndInstallUpdate() {
        guard let url = downloadURL, !isDownloading else { return }
        isDownloading = true
        downloadProgress = 0.0
        errorMessage = nil
        statusMessage = "Mengunduh \(assetFileName)..."

        let session = URLSession(configuration: .default)
        downloadTask = session.downloadTask(with: url) { [weak self] tempLocalURL, response, error in
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.isDownloading = false

                if let error = error {
                    self.errorMessage = "Unduhan gagal: \(error.localizedDescription)"
                    self.statusMessage = ""
                    return
                }

                guard let tempLocalURL = tempLocalURL else {
                    self.errorMessage = "File unduhan tidak ditemukan."
                    return
                }

                self.verifyAndStageUpdate(from: tempLocalURL)
            }
        }
        downloadTask?.resume()
    }

    private func verifyAndStageUpdate(from downloadedFile: URL) {
        let fm = FileManager.default
        guard let cachesRoot = fm.urls(for: .cachesDirectory, in: .userDomainMask).first else {
            errorMessage = "Tidak dapat mengakses direktori Caches pengguna."
            return
        }

        let stageDir = cachesRoot
            .appendingPathComponent("com.yuka.tsrossa.macos.updater", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)

        do {
            try fm.createDirectory(at: stageDir, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
            let archivePath = stageDir.appendingPathComponent(assetFileName.isEmpty ? "update.zip" : assetFileName)
            try fm.moveItem(at: downloadedFile, to: archivePath)

            if archivePath.pathExtension.lowercased() == "dmg" {
                statusMessage = "Membuka installer DMG..."
                NSWorkspace.shared.open(archivePath)
                return
            }

            statusMessage = "Mengekstrak dan memverifikasi tanda tangan digital..."
            let dittoProc = Process()
            dittoProc.executableURL = URL(fileURLWithPath: "/usr/bin/ditto")
            dittoProc.arguments = ["-x", "-k", archivePath.path, stageDir.path]
            try dittoProc.run()
            dittoProc.waitUntilExit()

            guard dittoProc.terminationStatus == 0 else {
                throw NSError(domain: "UpdateManager", code: 1, userInfo: [NSLocalizedDescriptionKey: "Gagal mengekstrak arsip ZIP."])
            }

            let contents = try fm.contentsOfDirectory(at: stageDir, includingPropertiesForKeys: nil)
            guard let extractedApp = contents.first(where: { $0.pathExtension == "app" }) else {
                throw NSError(domain: "UpdateManager", code: 2, userInfo: [NSLocalizedDescriptionKey: "Tidak ada bundle .app di dalam arsip pembaruan."])
            }

            try Self.verifyExtractedAppBundle(at: extractedApp)
            try replaceRunningAppAndRelaunch(with: extractedApp, stageDir: stageDir)
        } catch {
            try? fm.removeItem(at: stageDir)
            errorMessage = error.localizedDescription
            statusMessage = ""
        }
    }

    static func verifyExtractedAppBundle(at appURL: URL) throws {
        guard let bundle = Bundle(url: appURL),
              let bundleId = bundle.bundleIdentifier,
              bundleId == expectedBundleIdentifier else {
            throw NSError(
                domain: "UpdateManager",
                code: 3,
                userInfo: [NSLocalizedDescriptionKey: "Bundle identifier pembaruan tidak cocok dengan \(expectedBundleIdentifier)."]
            )
        }

        let codesignProc = Process()
        codesignProc.executableURL = URL(fileURLWithPath: "/usr/bin/codesign")
        codesignProc.arguments = ["--verify", "--deep", "--strict", appURL.path]
        try codesignProc.run()
        codesignProc.waitUntilExit()

        guard codesignProc.terminationStatus == 0 else {
            throw NSError(
                domain: "UpdateManager",
                code: 4,
                userInfo: [NSLocalizedDescriptionKey: "Verifikasi tanda tangan digital (codesign) gagal untuk paket pembaruan."]
            )
        }
    }

    private func replaceRunningAppAndRelaunch(with newAppURL: URL, stageDir: URL) throws {
        let currentAppURL = Bundle.main.bundleURL
        guard currentAppURL.pathExtension == "app" else {
            NSWorkspace.shared.activateFileViewerSelecting([newAppURL])
            statusMessage = "Paket terverifikasi di \(newAppURL.path)"
            return
        }

        let pid = ProcessInfo.processInfo.processIdentifier
        let scriptPath = stageDir.appendingPathComponent("apply_update.sh")
        let scriptContent = """
        #!/bin/bash
        while kill -0 \(pid) 2>/dev/null; do
            sleep 0.2
        done
        rm -rf "\(currentAppURL.path)"
        mv "\(newAppURL.path)" "\(currentAppURL.path)"
        xattr -cr "\(currentAppURL.path)" 2>/dev/null || true
        open "\(currentAppURL.path)"
        rm -rf "\(stageDir.path)"
        """
        try scriptContent.write(to: scriptPath, atomically: true, encoding: .utf8)
        try FileManager.default.setAttributes([.posixPermissions: 0o700], ofItemAtPath: scriptPath.path)

        let runner = Process()
        runner.executableURL = URL(fileURLWithPath: "/bin/bash")
        runner.arguments = [scriptPath.path]
        try runner.run()

        NSApplication.shared.terminate(nil)
    }

    private func finishCheckWithError(_ msg: String) {
        isChecking = false
        errorMessage = msg
        statusMessage = ""
    }
}
```

- [ ] **Step 2: Jalankan `./run_tests.sh` dan `./build_mac.sh` untuk memverifikasi semua test lulus**

```bash
./run_tests.sh && ./build_mac.sh
```

Expected: `RESULT: ALL 4 SWIFT TESTS PASSED` dan `build/TSRossa.app` berhasil dikompilasi tanpa warning/error.

- [ ] **Step 3: Commit hasil Plan 01**

```bash
git add build_mac.sh run_tests.sh src/nativeMain/cpp/dr_flac.h src/nativeMain/cpp/dr_wav.h src/nativeMain/cpp/dr_impl.c src/nativeMain/cpp/MacAudioDecoder.h macos/Resources/ macos/Theme.swift macos/AppViewModel.swift macos/UpdateManager.swift tests/
git commit -m "build & security: make repo self-contained, add test harness, harden UpdateManager"
```

---

## Review Focus

1. **Penyatuan Repositori GitHub (`alens/tsrossa`) dengan Filter Aset Ketat:** `VersionComparator.selectMacOSAsset(from:)` memastikan aplikasi macOS hanya memilih rilis yang memiliki ekstensi `.zip`/`.dmg` **dan** kata kunci `mac`/`macos`/`darwin`/`osx`, serta menolak mentah-mentah file `.apk` atau `.zip` simbol Android.
2. **Keamanan Ekstraksi & Instalasi Update:** Direktori `/tmp/tsrossa_update` yang dapat ditulis semua user diganti dengan `~/Library/Caches/com.yuka.tsrossa.macos.updater/<UUID>` berizin `0700`, ditambah pemeriksaan `CFBundleIdentifier == "com.yuka.tsrossa.macos"` dan `/usr/bin/codesign --verify --deep --strict` sebelum mengganti bundle aplikasi.
