# Plan 05: Telemetri Nyata, Integrasi macOS (`MediaPlayer`, `IOPMAssertion`) & Paritas Fitur Queue/Playlist

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Menghapus seluruh data telemetri palsu/statis di `TelemetryModalView.swift` dan menggantinya dengan JSON telemetri perangkat keras C++ real-time serta sistem log terstruktur (`AppLogger.swift`), mengintegrasikan aplikasi secara native dengan ekosistem macOS (`MPNowPlayingInfoCenter`, `MPRemoteCommandCenter` untuk tombol media `F7`/`F8`/`F9`, `IOPMAssertion` untuk `keepScreenAwake`, dan perbaikan pembajakan tombol panah keyboard), serta mem-porting `QueueHelper.swift` (3-state Repeat Mode, Shuffle Deck anti-ulang, Playback History Stack) dan manajemen Playlist lengkap.

**Architecture:** Memperluas `tsrossa_get_telemetry_json()` di `TsrossaAudioBridge.cpp` agar menghasilkan JSON terstruktur yang didekode oleh `HardwareTelemetrySnapshot` (`Codable`) di Swift. Membangun `AppLogger.swift` sebagai *ring-buffer* 500 entri dengan timestamp pembentukan event tetap (tidak dibuat ulang saat render UI) dan dukungan ekspor `NSSavePanel` / `NSSharingServicePicker`. Mengisolasi logika antrean ke dalam `QueueHelper.swift` murni yang diuji penuh melalui `./run_tests.sh`.

**Tech Stack:** Swift 5.9, `MediaPlayer` (`MPNowPlayingInfoCenter`, `MPRemoteCommandCenter`, `MPMediaItemArtwork`), `IOKit.pwr_mgt` (`IOPMAssertionCreateWithName`), `AppKit` (`NSEvent`, `NSSavePanel`, `NSSharingServicePicker`).

---

## File Structure

- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/AppLogger.swift` — Ring-buffer logger (500 entri) dengan `id`, `timestamp: Date` tetap, `level` (`INFO`, `DSP`, `COREAUDIO`, `LIBRARY`, `WARN`, `ERROR`), filter pencarian, salin ke clipboard, dan ekspor file `.log`.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/QueueHelper.swift` — Porting `QueueHelper.kt` Android:
  - `enum RepeatMode: String, Codable, CaseIterable { case off, all, one }`
  - `ShuffleDeck`: dek indeks teracak *Fisher-Yates* yang menjamin semua lagu diputar satu kali sebelum diacak ulang dan tidak mengulang lagu terakhir saat reshuffle
  - `playbackHistory: [AudioTrack]` untuk tombol `Previous` yang akurat (kembali ke `0.0s` jika `currentTime > 3.0s`, atau pop dari history stack)
  - Preempsi `priorityQueue` di atas antrean reguler
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/TsrossaAudioBridge.cpp:95-145` — Menyusun JSON telemetri lengkap berisi `deviceName`, `hogModeStatus`, `hoggerPid`, `streamSampleRate`, `actualHardwareSampleRate`, `bitsPerSample`, `channels`, `streamIsInteger`, `bufferFrames`, `bufferProfileIndex`, `ditherEnabled`, `ramLockActive`, `ringBufferFillPercent`, `gaplessNextReady`, dan `targetGainDb`.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/TelemetryModalView.swift:1-315` — Menghapus seluruh string statis (`"CoreAudio IOProc (HAL)"`, `"Hog Mode (Exclusive)"`, `"256 frames (5.3ms)"`, dan `Date()` palsu di dalam body View), menggantinya dengan `HardwareTelemetrySnapshot` + `AppLogger.shared`.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/AudioEngine.swift` — Menambahkan integrasi `MPNowPlayingInfoCenter` + `MPRemoteCommandCenter`, manajemen `IOPMAssertionID` untuk `keepScreenAwake`, serta delegasi `nextTrack()` / `prevTrack()` ke `QueueHelper`.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/AppViewModel.swift` & `/Users/yuka/Documents/Projects/tsrossa-macos/macos/main.swift` — Memperbaiki `setupKeyboardMonitor()` agar tidak membajak tombol panah mentah tanpa modifier, menambahkan shortcut `Cmd + U`, `Cmd + [`, `Cmd + ]`, `Esc`, auto-folder playlist (`makeFolderAsPlaylist`), serta aksi Rename, Pin, dan Clear pada Playlist.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/tests/swift/SortAndFuzzySearchTests.swift` — Menambahkan unit test untuk `QueueHelper` (Repeat Off/All/One, Shuffle Deck anti-ulang, Priority Queue, dan History Stack).

---

### Task 1: Tulis Unit Test & Implementasikan `QueueHelper.swift` (3-State Repeat, Shuffle Deck, Priority Queue, History Stack)

**Files:**
- Create: `macos/QueueHelper.swift`
- Modify: `tests/swift/SortAndFuzzySearchTests.swift`

- [ ] **Step 1: Tambahkan pengujian unit `QueueHelperTests` di `tests/swift/SortAndFuzzySearchTests.swift`**

```swift
    TestSuite.run("QueueHelper RepeatMode.one repeats current track, RepeatMode.off stops at end, RepeatMode.all wraps") {
        let tracks = [
            AudioTrack.makeTestTrack(title: "Track 1", artist: "A", album: "B", duration: 100, dateAdded: 1, dateModified: 1, fileSize: 1),
            AudioTrack.makeTestTrack(title: "Track 2", artist: "A", album: "B", duration: 100, dateAdded: 2, dateModified: 2, fileSize: 1)
        ]
        var q = PlaybackQueueState(tracks: tracks, currentIndex: 1, repeatMode: .one, isShuffle: false)
        // Repeat One saat auto-advance harus mengembalikan lagu yang sama (index 1)
        let nextOne = QueueHelper.resolveNextTrack(state: &q, userInitiated: false)
        try TestSuite.assertEqual(nextOne?.title, "Track 2")

        // Repeat Off pada lagu terakhir harus mengembalikan nil (berhenti di akhir)
        q.repeatMode = .off
        let nextOff = QueueHelper.resolveNextTrack(state: &q, userInitiated: false)
        try TestSuite.assertEqual(nextOff, nil)

        // Repeat All pada lagu terakhir harus membungkus kembali ke Track 1 (index 0)
        q.repeatMode = .all
        let nextAll = QueueHelper.resolveNextTrack(state: &q, userInitiated: false)
        try TestSuite.assertEqual(nextAll?.title, "Track 1")
    }

    TestSuite.run("QueueHelper ShuffleDeck visits every track exactly once before reshuffling") {
        let tracks = (1...6).map { i in
            AudioTrack.makeTestTrack(title: "Song \(i)", artist: "A", album: "B", duration: 100, dateAdded: Double(i), dateModified: 1, fileSize: 1)
        }
        var q = PlaybackQueueState(tracks: tracks, currentIndex: 0, repeatMode: .all, isShuffle: true)
        var visited: Set<String> = [tracks[0].title]
        for _ in 1..<6 {
            if let next = QueueHelper.resolveNextTrack(state: &q, userInitiated: true) {
                visited.insert(next.title)
            }
        }
        try TestSuite.assertEqual(visited.count, 6, "All 6 tracks must be visited without repetition in one deck cycle")
    }
```

- [ ] **Step 2: Implementasikan `macos/QueueHelper.swift`**

Implementasikan `RepeatMode`, `PlaybackQueueState`, dan `QueueHelper` yang menangani:
1. Prioritas penuh untuk `priorityQueue`: jika `!state.priorityQueue.isEmpty`, `resolveNextTrack` selalu mengambil elemen pertama dari `priorityQueue` sebelum melanjutkan antrean utama.
2. `RepeatMode.one`: jika `!userInitiated` (transisi otomatis akhir lagu), tetap di `currentIndex`.
3. `isShuffle == true`: memelihara `shuffleDeck: [Int]` (daftar indeks lagu yang belum diputar pada siklus ini). Ketika `shuffleDeck` habis dan `repeatMode == .all`, bangun ulang dek acak dengan memastikan elemen pertama dek baru `!= currentIndex`.
4. `resolvePreviousTrack(state: &PlaybackQueueState, currentTime: Double) -> (track: AudioTrack?, restartOnly: Bool)`: jika `currentTime > 3.0`, kembalikan `(nil, true)` untuk seek ke `0.0`; jika `<= 3.0`, pop dari `historyStack` atau mundur 1 indeks.

- [ ] **Step 3: Jalankan `./run_tests.sh` untuk memverifikasi `QueueHelperTests` lulus**

```bash
./run_tests.sh
```

Expected: Seluruh test `QueueHelper` lulus.

---

### Task 2: Buat `AppLogger.swift`, Lengkapi JSON Telemetri C++, dan Perbarui `TelemetryModalView.swift`

**Files:**
- Create: `macos/AppLogger.swift`
- Modify: `src/nativeMain/cpp/TsrossaAudioBridge.cpp:95-145`
- Modify: `macos/TelemetryModalView.swift:1-315`

- [ ] **Step 1: Buat `macos/AppLogger.swift` dengan timestamp event permanen, filter kategori/level, dan ekspor file**

```swift
import Foundation
import AppKit

public enum LogCategory: String, Codable, CaseIterable {
    case audio = "AUDIO"
    case dsp = "DSP"
    case hardware = "DAC/HAL"
    case library = "LIBRARY"
    case system = "SYSTEM"
}

public enum LogLevel: String, Codable, CaseIterable {
    case info = "INFO"
    case notice = "NOTICE"
    case warning = "WARN"
    case error = "ERROR"
}

public struct LogEntry: Identifiable, Codable, Equatable {
    public let id: UUID
    public let timestamp: Date
    public let category: LogCategory
    public let level: LogLevel
    public let message: String

    private static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss.SSS"
        return f
    }()

    public var formattedTimestamp: String {
        Self.formatter.string(from: timestamp)
    }
}

public final class AppLogger: ObservableObject {
    public static let shared = AppLogger()
    private let maxEntries = 500
    private let lock = NSLock()

    @Published public private(set) var entries: [LogEntry] = []

    private init() {
        log(.system, .info, "TSRossa macOS Telemetry & Diagnostic Logger initialized")
    }

    public func log(_ category: LogCategory, _ level: LogLevel = .info, _ message: String) {
        let entry = LogEntry(id: UUID(), timestamp: Date(), category: category, level: level, message: message)
        DispatchQueue.main.async {
            self.lock.lock()
            self.entries.append(entry)
            if self.entries.count > self.maxEntries {
                self.entries.removeFirst(self.entries.count - self.maxEntries)
            }
            self.lock.unlock()
        }
    }

    public func clear() {
        lock.lock()
        entries.removeAll()
        lock.unlock()
    }

    public func exportText(telemetryHeader: String) -> String {
        var lines = [
            "=== TSROSSA MACOS DIAGNOSTIC REPORT ===",
            "Generated: \(ISO8601DateFormatter().string(from: Date()))",
            telemetryHeader,
            "=== EVENT LOG (\(entries.count) entries) ==="
        ]
        for e in entries {
            lines.append("[\(e.formattedTimestamp)] [\(e.level.rawValue)] [\(e.category.rawValue)] \(e.message)")
        }
        return lines.joined(separator: "\n")
    }
}
```

- [ ] **Step 2: Perbarui `tsrossa_get_telemetry_json()` di `src/nativeMain/cpp/TsrossaAudioBridge.cpp` agar menyertakan metrik perangkat keras nyata**

Perbarui `tsrossa_get_telemetry_json()` (dengan perlindungan `std::lock_guard<std::mutex> lock(s_bridgeMutex)`) agar mengembalikan JSON valid dengan field:
- `isPlaying`, `sampleRate` (stream), `actualHardwareSampleRate`, `channels`, `bitsPerSample`
- `hogModeStatus` (`"EXCLUSIVE_HOG"`, `"SHARED_DISABLED"`, atau `"SHARED_BUILTIN_OR_FALLBACK"`), `hoggerPid`
- `streamIsInteger` (`true` jika direct 32-bit signed integer, `false` jika 64-bit DSP -> 32-bit float)
- `ditherEnabled`, `ramLockActive`, `bufferProfileIndex`, `bufferDurationMs`
- `decodedFrames`, `totalFrames`, `currentFrame`, `gaplessNextReady`

- [ ] **Step 3: Tulis ulang `macos/TelemetryModalView.swift` agar membaca `HardwareTelemetrySnapshot` dari JSON C++ dan menampilkan `AppLogger.shared.entries`**

Hapus seluruh string palsu di `TelemetryModalView.swift` (`"CoreAudio IOProc (HAL)"`, `"Hog Mode (Exclusive)"`, `"256 frames (5.3ms)"`, `"64-bit Float -> 32-bit Int"`, dan pembuatan `Date()` di dalam `logLine`). Ganti dengan:
- Kartu metrik yang didekode langsung dari `engine.telemetrySnapshot`:
  - **Exclusive Access**: Menampilkan `"Hog Mode Active (PID \(snap.hoggerPid))"` dengan indikator hijau **hanya jika** `snap.hogModeStatus == "EXCLUSIVE_HOG"`, atau `"Shared CoreAudio Mixer"` dengan indikator kuning jika shared/built-in.
  - **Signal Path**: Menampilkan `"32-Bit Signed Integer Direct"` jika `snap.streamIsInteger` bernilai `true`, atau `"64-Bit Double DSP -> 32-Bit Float (TPDF Dither: \(snap.ditherEnabled ? "ON" : "OFF"))"`.
  - **Sample Rate Lock**: Menampilkan `"Stream \(snap.sampleRate) Hz -> DAC \(Int(snap.actualHardwareSampleRate)) Hz"`.
  - **Ring Buffer & Gapless**: Menampilkan persentase buffer terisi, status `mlock()`, dan kesiapan pre-buffer Gapless.
- Filter log berdasarkan `LogCategory` & pencarian kata kunci, tombol **Copy Report**, dan tombol **Export `.log` File** menggunakan `NSSavePanel`.

---

### Task 3: Integrasikan `MPNowPlayingInfoCenter`, `MPRemoteCommandCenter` (Media Keys), `IOPMAssertion` (Anti-Sleep), dan Perbaiki Keyboard Monitor

**Files:**
- Modify: `macos/AudioEngine.swift`
- Modify: `macos/AppViewModel.swift`
- Modify: `macos/main.swift`

- [ ] **Step 1: Tambahkan integrasi `MediaPlayer` (`MPNowPlayingInfoCenter` & `MPRemoteCommandCenter`) dan `IOPMAssertion` di `macos/AudioEngine.swift`**

Di `macos/AudioEngine.swift`:
1. Tambahkan `import MediaPlayer` dan `import IOKit.pwr_mgt`.
2. Buat metode `setupRemoteCommands()` yang dipanggil pada `init()`:
   - Mengaktifkan `commandCenter.playCommand`, `pauseCommand`, `togglePlayPauseCommand`, `nextTrackCommand`, `previousTrackCommand`, dan `changePlaybackPositionCommand`.
   - Menghubungkan masing-masing handler ke `togglePlayPause()`, `nextTrack()`, `prevTrack()`, dan `seek(to:)`.
3. Buat metode `updateNowPlayingInfo()` yang dipanggil saat lagu berganti, play/pause berubah, atau seek dilakukan:
   - Mengisi `MPMediaItemPropertyTitle`, `MPMediaItemPropertyArtist`, `MPMediaItemPropertyAlbumTitle`, `MPMediaItemPropertyPlaybackDuration`, `MPNowPlayingInfoPropertyElapsedPlaybackTime`, `MPNowPlayingInfoPropertyPlaybackRate` (`isPlaying ? 1.0 : 0.0`), dan `MPMediaItemPropertyArtwork` dari sampul lagu aktif.
   - Mengatur `MPNowPlayingInfoCenter.default().playbackState = isPlaying ? .playing : .paused`.
4. Buat metode `updatePowerAssertion(keepAwake: Bool)`:
   - Saat `isPlaying && keepAwake` bernilai `true`, buat assertion `IOPMAssertionCreateWithName(kIOPMAssertionTypeNoIdleDisplaySleep as CFString, IOPMAssertionLevel(kIOPMAssertionLevelOn), "TSRossa Active Hi-Res Audio Playback" as CFString, &powerAssertionID)`.
   - Saat playback berhenti atau toggle dimatikan, lepaskan dengan `IOPMAssertionRelease(powerAssertionID)`.

- [ ] **Step 2: Perbaiki pembajakan tombol panah di `AppViewModel.setupKeyboardMonitor()` dan lengkapi shortcut di `main.swift`**

Di `macos/AppViewModel.swift` (sekitar baris 124-182):
- Pastikan di dalam handler `NSEvent.addLocalMonitorForEvents(matching: .keyDown)`:
  1. Jika fokus sedang berada pada `NSTextView` / `NSTextField` (misal kolom pencarian atau dialog buat playlist), langsung `return event`.
  2. Jika tombol `Esc` (`keyCode == 53`) ditekan dan ada modal terbuka (`showTelemetryModal || showTrackDetailsModal || showCreatePlaylistModal`), tutup modal tersebut dan `return nil`.
  3. **Hapus pembajakan tombol panah mentah tanpa modifier**: ubah shortcut seek (`123` Left / `124` Right) dan volume (`125` Down / `126` Up) agar mensyaratkan tombol **Command (`flags.contains(.command)`)** atau **Option (`flags.contains(.option)`)**, sehingga tombol panah biasa tetap berfungsi normal untuk navigasi daftar lagu!

Di `macos/main.swift`:
- Tambahkan `CommandMenu("Playback")` dan item menu untuk:
  - `Cmd + U` -> Buka Audio Pipeline Telemetry Modal
  - `Cmd + [` dan `Cmd + ]` -> Navigasi tab utama sebelumnya/berikutnya
  - `Cmd + L` -> Toggle panel Lyrics / Queue

---

### Task 4: Tambahkan Manajemen Playlist Lengkap (Rename, Pin, Clear, Auto Folder Playlists)

**Files:**
- Modify: `macos/AppViewModel.swift`

- [ ] **Step 1: Tambahkan fungsi `renamePlaylist`, `togglePinPlaylist`, `clearPlaylistTracks`, dan `rebuildFolderPlaylistsIfNeeded` di `macos/AppViewModel.swift`**

Di `macos/AppViewModel.swift`:
1. Perbarui `PlaylistItem` agar memiliki properti `var isPinned: Bool = false` dan `var isAutoFolder: Bool = false`.
2. Tambahkan metode:
   - `renamePlaylist(id: UUID, newName: String)`
   - `togglePinPlaylist(id: UUID)` (mengurutkan playlist yang di-pin di bagian atas daftar)
   - `clearPlaylistTracks(id: UUID)`
   - `rebuildFolderPlaylists()` yang otomatis mengelompokkan lagu berdasarkan nama folder induk (`url.deletingLastPathComponent().lastPathComponent`) ketika `makeFolderAsPlaylist == true`.
3. Hubungkan `AppLogger.shared.log(...)` pada setiap peristiwa penting (ganti DAC, aktifkan Hog Mode, mulai putar lagu, scan selesai, error file).

- [ ] **Step 2: Jalankan `./run_tests.sh` dan `./build_mac.sh` untuk memverifikasi seluruh test dan kompilasi**

```bash
./run_tests.sh && ./build_mac.sh
```

Expected: Seluruh unit test C++ & Swift lulus dan `TSRossa.app` ter-build tanpa error.

- [ ] **Step 3: Commit hasil Plan 05**

```bash
git add src/nativeMain/cpp/TsrossaAudioBridge.cpp macos/AppLogger.swift macos/QueueHelper.swift macos/TelemetryModalView.swift macos/AudioEngine.swift macos/AppViewModel.swift macos/main.swift tests/swift/SortAndFuzzySearchTests.swift
git commit -m "feat(macos): add real hardware telemetry & logger, MediaPlayer NowPlaying keys, IOPMAssertion, and QueueHelper"
```

---

## Review Focus

1. **Kejujuran Telemetri 100%:** `TelemetryModalView.swift` kini tidak memiliki satu pun angka statis. Status *Exclusive Hog Mode*, *Sample Rate DAC aktual*, *format stream Integer vs Float*, dan log diagnostik semuanya mencerminkan kondisi real-time mesin CoreAudio C++.
2. **Integrasi Media Keys & Keyboard Ergonomis:** Pengguna kini dapat mengontrol musik dari tombol fisik `F7`/`F8`/`F9` MacBook, AirPods, atau Control Center macOS walaupun jendela TSRossa sedang diminimalkan, sementara tombol panah di dalam aplikasi tidak lagi membajak fokus navigasi daftar.
