import Foundation
import Combine
import AppKit
import SwiftUI

public enum AppTab: String, CaseIterable {
    case library = "Library"
    case playlist = "Playlist"
    case nowPlaying = "Now Playing"
    case settings = "Settings"
}

public struct UserPlaylist: Identifiable, Codable, Hashable {
    public var id: String
    public var name: String
    /// Optional smart filter keyword (e.g. "Polyphia") that automatically matches track title/artist/album
    public var filterKeyword: String
    /// Explicitly added track file paths
    public var trackPaths: [String]
    
    public init(id: String = UUID().uuidString, name: String, filterKeyword: String = "", trackPaths: [String] = []) {
        self.id = id
        self.name = name
        self.filterKeyword = filterKeyword
        self.trackPaths = trackPaths
    }
    
    public func matches(track: TrackMetadata) -> Bool {
        if trackPaths.contains(track.path) {
            return true
        }
        let kw = filterKeyword.trimmingCharacters(in: .whitespacesAndNewlines)
        if !kw.isEmpty {
            return track.title.localizedCaseInsensitiveContains(kw) ||
                   track.artist.localizedCaseInsensitiveContains(kw) ||
                   track.album.localizedCaseInsensitiveContains(kw)
        }
        return false
    }
}

public class AppViewModel: ObservableObject {
    public static let shared = AppViewModel()
    
    @Published public var activeTab: AppTab = .nowPlaying {
        didSet {
            if activeTab == .settings {
                AudioEngine.shared.refreshDevices()
            }
        }
    }
    @Published public var tracks: [TrackMetadata] = []
    @Published public var searchText: String = "" {
        didSet {
            searchSelectedIndex = 0
        }
    }
    @Published public var searchSelectedIndex: Int = 0
    @Published public var showTelemetry: Bool = false
    @Published public var currentFolder: String = FileManager.default.urls(for: .musicDirectory, in: .userDomainMask).first?.path ?? "/Users/alen/Music"
    @Published public var copiedTelemetry: Bool = false
    
    // Playlists & New Playlist Modal State
    @Published public var playlists: [UserPlaylist] = []
    @Published public var selectedPlaylistId: String? = nil
    @Published public var showNewPlaylistModal: Bool = false
    @Published public var newPlaylistName: String = ""
    @Published public var newPlaylistKeyword: String = ""
    @Published public var newPlaylistSelectedPaths: Set<String> = []
    
    // Dynamic Play Queue & Up Next State (Google / Spotify style queue)
    @Published public var upNextQueue: [TrackMetadata] = []
    @Published public var toastMessage: String = ""
    @Published public var showSaveQueueModal: Bool = false
    @Published public var saveQueuePlaylistName: String = ""
    
    // Liked Songs / Favorites State (YouTube Music style)
    @Published public var likedTrackPaths: Set<String> = []
    
    public var likedTracksCount: Int {
        return tracks.filter { likedTrackPaths.contains($0.path) }.count
    }
    
    // Lyrics State & Real-Time Synchronization (YouTube Music style)
    @Published public var currentLyrics: [LyricLine] = []
    @Published public var currentLyricIndex: Int = -1
    @Published public var queueTabMode: String = "QUEUE" // "QUEUE" or "LYRICS"
    @Published public var isLoadingLyrics: Bool = false
    
    // Theater / Fullscreen Mode (YouTube Music style Cmd + F)
    @Published public var isTheaterMode: Bool = false
    @Published public var theaterTabMode: String = "LYRICS" // "LYRICS" or "QUEUE"
    
    // Autoplay Infinite Radio (YouTube Music / Spotify style)
    @Published public var isAutoplayRadio: Bool = true {
        didSet {
            UserDefaults.standard.set(isAutoplayRadio, forKey: "tsrossa.isAutoplayRadio")
        }
    }
    
    public var hasLyrics: Bool {
        return !currentLyrics.isEmpty
    }
    
    public var selectedPlaylist: UserPlaylist? {
        guard let id = selectedPlaylistId else { return nil }
        return playlists.first(where: { $0.id == id })
    }
    
    // Audiophile Hardware Filter State (Format, Bit Depth, Sample Rate kHz)
    @Published public var selectedFormatFilter: String = "ALL" // "ALL", "FLAC", "WAV"
    @Published public var selectedBitDepthFilter: UInt32 = 0   // 0 = All, 24, 16, 8, 32
    @Published public var selectedSampleRateFilter: UInt32 = 0 // 0 = All, 44100, 48000, 88200, 96000, 192000
    
    @Published public var volumeDb: Int = 0 { // 0 dBFS unity gain
        didSet {
            let db = Int32(volumeDb)
            AudioEngine.shared.engineQueue.async {
                tsrossa_set_volume_db(db)
            }
        }
    }
    @Published public var isShuffle: Bool = false
    @Published public var isRepeat: Bool = false
    @Published public var sortMode: String = "DATE (NEWEST)"
    
    // Material 3 Dynamic Theming & Artwork State
    @Published public var currentArtwork: NSImage? = nil
    @Published public var currentTheme: DynamicMaterialTheme = .defaultTheme
    
    // Audiophile & CoreAudio Engine Settings (Wired to C++ CoreAudioSink on engineQueue)
    @Published public var isExclusiveHogMode: Bool = true {
        didSet {
            let enabled = isExclusiveHogMode
            AudioEngine.shared.engineQueue.async {
                tsrossa_set_hog_mode_enabled(enabled)
            }
        }
    }
    @Published public var isAutoClockSwitch: Bool = true {
        didSet {
            let enabled = isAutoClockSwitch
            AudioEngine.shared.engineQueue.async {
                tsrossa_set_auto_clock_enabled(enabled)
            }
        }
    }
    @Published public var isRamPlayback: Bool = true {
        didSet {
            let enabled = isRamPlayback
            AudioEngine.shared.engineQueue.async {
                tsrossa_set_ram_lock_enabled(enabled)
            }
        }
    }
    @Published public var keepScreenAwake: Bool = true
    @Published public var bufferProfile: String = "192ms Jitter Safe"
    
    // Appearance & Customization Settings
    @Published public var themeMode: String = "DARK" // DARK, OLED, LIGHT
    @Published public var bgCanvasMode: String = "DYNAMIC" // DYNAMIC, AURA, BLACK
    @Published public var accentColorMode: String = "DYNAMIC" // DYNAMIC, ROSE, MATCHA, GOLD, CYAN
    
    private var keyMonitor: Any?
    private let playlistsStorageKey = "tsrossa.userPlaylists.v1"
    
    public init() {
        if UserDefaults.standard.object(forKey: "tsrossa.isAutoplayRadio") != nil {
            isAutoplayRadio = UserDefaults.standard.bool(forKey: "tsrossa.isAutoplayRadio")
        }
        loadSavedPlaylists()
        loadSavedLikedTracks()
        setupKeyboardMonitor()
    }
    
    deinit {
        if let monitor = keyMonitor {
            NSEvent.removeMonitor(monitor)
        }
    }
    
    // =========================================================================
    // COMPUTED FILTERED TRACKS (Playlist + Format + BitDepth + kHz + Search)
    // =========================================================================
    public var activePlaylist: UserPlaylist? {
        guard let id = selectedPlaylistId, id != "LIKED_SONGS" else { return nil }
        return playlists.first(where: { $0.id == id })
    }
    
    public var hasActiveFilters: Bool {
        return selectedPlaylistId != nil ||
               selectedFormatFilter != "ALL" ||
               selectedBitDepthFilter != 0 ||
               selectedSampleRateFilter != 0 ||
               !searchText.isEmpty
    }
    
    public var filteredTracks: [TrackMetadata] {
        var result = tracks
        
        // 1. Liked Songs or Playlist filter (Smart Keyword or Explicit Track Paths)
        if selectedPlaylistId == "LIKED_SONGS" {
            result = result.filter { likedTrackPaths.contains($0.path) }
        } else if let playlist = activePlaylist {
            result = result.filter { playlist.matches(track: $0) }
        }
        
        // 2. Audio Format filter (FLAC, WAV)
        if selectedFormatFilter != "ALL" {
            let targetExt = selectedFormatFilter.lowercased()
            result = result.filter { $0.path.lowercased().hasSuffix(".\(targetExt)") }
        }
        
        // 3. Bit Depth filter (24-bit, 16-bit, 8-bit, 32-bit)
        if selectedBitDepthFilter != 0 {
            result = result.filter { $0.bitDepth == selectedBitDepthFilter }
        }
        
        // 4. Sample Rate (kHz) filter (44.1k, 48k, 88.2k, 96k, 192k)
        if selectedSampleRateFilter != 0 {
            result = result.filter { $0.sampleRate == selectedSampleRateFilter }
        }
        
        // 5. Typeahead Search filter
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        if !query.isEmpty {
            result = result.filter {
                $0.title.localizedCaseInsensitiveContains(query) ||
                $0.artist.localizedCaseInsensitiveContains(query) ||
                $0.album.localizedCaseInsensitiveContains(query)
            }
        }
        
        return result
    }
    
    public var distinctArtists: [String] {
        var seen = Set<String>()
        var list: [String] = []
        for t in tracks {
            let primary = t.artist.components(separatedBy: ",").first?.trimmingCharacters(in: .whitespacesAndNewlines) ?? t.artist
            if !primary.isEmpty && primary != "Unknown Artist" && !seen.contains(primary) {
                seen.insert(primary)
                list.append(primary)
            }
        }
        return list.sorted()
    }
    
    public func resetAllFilters() {
        selectedPlaylistId = nil
        selectedFormatFilter = "ALL"
        selectedBitDepthFilter = 0
        selectedSampleRateFilter = 0
        searchText = ""
    }
    
    /// Spotify-style Submit-to-Play (Enter in Search Box plays the highlighted / Top Hit track)
    public func playSelectedSearchResult() {
        let list = filteredTracks
        guard !list.isEmpty else { return }
        let idx = min(max(0, searchSelectedIndex), list.count - 1)
        AudioEngine.shared.play(track: list[idx])
        NSApp.keyWindow?.makeFirstResponder(nil)
    }
    
    // =========================================================================
    // PLAYLIST MANAGEMENT
    // =========================================================================
    private func loadSavedPlaylists() {
        if let data = UserDefaults.standard.data(forKey: playlistsStorageKey),
           let decoded = try? JSONDecoder().decode([UserPlaylist].self, from: data),
           !decoded.isEmpty {
            playlists = decoded
        } else {
            // Seed default smart playlists including Polyphia
            playlists = [
                UserPlaylist(name: "Polyphia", filterKeyword: "Polyphia"),
                UserPlaylist(name: "Nadin Amizah", filterKeyword: "Nadin Amizah"),
                UserPlaylist(name: "Chase & Status", filterKeyword: "Chase & Status")
            ]
            savePlaylists()
        }
    }
    
    public func savePlaylists() {
        if let data = try? JSONEncoder().encode(playlists) {
            UserDefaults.standard.set(data, forKey: playlistsStorageKey)
        }
    }
    
    public func openCreatePlaylistModal() {
        newPlaylistName = ""
        newPlaylistKeyword = ""
        newPlaylistSelectedPaths = []
        showNewPlaylistModal = true
    }
    
    public func createPlaylistFromModal() {
        let trimmedName = newPlaylistName.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedKeyword = newPlaylistKeyword.trimmingCharacters(in: .whitespacesAndNewlines)
        let finalName = !trimmedName.isEmpty ? trimmedName : (!trimmedKeyword.isEmpty ? trimmedKeyword : "New Playlist")
        
        let created = UserPlaylist(
            name: finalName,
            filterKeyword: trimmedKeyword,
            trackPaths: Array(newPlaylistSelectedPaths)
        )
        playlists.append(created)
        savePlaylists()
        selectedPlaylistId = created.id
        activeTab = .library
        showNewPlaylistModal = false
    }
    
    public func deletePlaylist(id: String) {
        playlists.removeAll { $0.id == id }
        if selectedPlaylistId == id {
            selectedPlaylistId = nil
        }
        savePlaylists()
    }
    
    public func toggleTrackInPlaylist(trackPath: String, playlistId: String) {
        guard let idx = playlists.firstIndex(where: { $0.id == playlistId }) else { return }
        if let pathIdx = playlists[idx].trackPaths.firstIndex(of: trackPath) {
            playlists[idx].trackPaths.remove(at: pathIdx)
        } else {
            playlists[idx].trackPaths.append(trackPath)
        }
        savePlaylists()
    }
    
    public func updateThemeForTrack(track: TrackMetadata) {
        ArtworkExtractor.loadArtworkAndTheme(for: track.path) { [weak self] artwork, theme in
            guard let self = self else { return }
            withAnimation(.easeInOut(duration: 0.50)) {
                self.currentArtwork = artwork
                if self.accentColorMode == "DYNAMIC" {
                    self.currentTheme = theme
                }
            }
        }
    }
    
    public func setAccentColorMode(_ mode: String) {
        accentColorMode = mode
        withAnimation(.easeInOut(duration: 0.35)) {
            switch mode {
            case "ROSE":
                currentTheme = DynamicMaterialTheme(seedR: 1.0, seedG: 0.18, seedB: 0.33)
            case "MATCHA":
                currentTheme = DynamicMaterialTheme(seedR: 0.62, seedG: 0.82, seedB: 0.38)
            case "GOLD":
                currentTheme = DynamicMaterialTheme(seedR: 1.0, seedG: 0.84, seedB: 0.0)
            case "CYAN":
                currentTheme = DynamicMaterialTheme(seedR: 0.0, seedG: 0.90, seedB: 1.0)
            default: // DYNAMIC
                if let track = AudioEngine.shared.currentTrack {
                    updateThemeForTrack(track: track)
                } else {
                    currentTheme = DynamicMaterialTheme.defaultTheme
                }
            }
        }
    }
    
    // =========================================================================
    // DYNAMIC PLAY QUEUE & UP NEXT MANAGEMENT (Google / Spotify Style)
    // =========================================================================
    public func addToPlayNext(track: TrackMetadata) {
        if let first = upNextQueue.first, first.path == track.path {
            showToast("'\(track.title)' is already playing next")
            return
        }
        upNextQueue.removeAll { $0.path == track.path }
        upNextQueue.insert(track, at: 0)
        showToast("Set to Play Next: \(track.title)")
    }
    
    public func addToQueueEnd(track: TrackMetadata) {
        if !upNextQueue.contains(where: { $0.path == track.path }) {
            upNextQueue.append(track)
            showToast("Added to Queue (#\(upNextQueue.count)): \(track.title)")
        } else {
            if let pos = upNextQueue.firstIndex(where: { $0.path == track.path }) {
                showToast("Track is already in queue (#\(pos + 1))")
            }
        }
    }
    
    public func removeFromQueue(at index: Int) {
        guard index >= 0 && index < upNextQueue.count else { return }
        let removed = upNextQueue.remove(at: index)
        showToast("Removed '\(removed.title)' from queue")
    }
    
    public func moveInQueue(from: Int, to: Int) {
        guard from >= 0 && from < upNextQueue.count && to >= 0 && to < upNextQueue.count && from != to else { return }
        let item = upNextQueue.remove(at: from)
        upNextQueue.insert(item, at: to)
    }
    
    public func clearQueue() {
        upNextQueue.removeAll()
        showToast("Queue cleared")
    }
    
    public func showToast(_ message: String) {
        withAnimation(.easeInOut(duration: 0.25)) {
            toastMessage = message
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { [weak self] in
            guard let self = self else { return }
            if self.toastMessage == message {
                withAnimation(.easeInOut(duration: 0.25)) {
                    self.toastMessage = ""
                }
            }
        }
    }
    
    public func openSaveQueueModal() {
        if let current = AudioEngine.shared.currentTrack {
            saveQueuePlaylistName = "\(current.artist) Mix"
        } else {
            saveQueuePlaylistName = "Queued Mix"
        }
        showSaveQueueModal = true
    }
    
    public func saveCurrentQueueAsPlaylist() {
        var paths: [String] = []
        if let current = AudioEngine.shared.currentTrack {
            paths.append(current.path)
        }
        for t in upNextQueue {
            if !paths.contains(t.path) {
                paths.append(t.path)
            }
        }
        guard !paths.isEmpty else {
            showToast("No tracks in queue to save")
            showSaveQueueModal = false
            return
        }
        let trimmedName = saveQueuePlaylistName.trimmingCharacters(in: .whitespacesAndNewlines)
        let name = !trimmedName.isEmpty ? trimmedName : "Queue Playlist"
        let newPl = UserPlaylist(name: name, filterKeyword: "", trackPaths: paths)
        playlists.append(newPl)
        savePlaylists()
        selectedPlaylistId = newPl.id
        showSaveQueueModal = false
        showToast("Saved \(paths.count) tracks to playlist '\(name)'")
    }
    
    // =========================================================================
    // LIKED SONGS & FAVORITES MANAGEMENT (YouTube Music Style)
    // =========================================================================
    private let likedTracksStorageKey = "tsrossa.likedTracks.v1"
    
    private func loadSavedLikedTracks() {
        if let saved = UserDefaults.standard.stringArray(forKey: likedTracksStorageKey) {
            likedTrackPaths = Set(saved)
        }
    }
    
    public func saveLikedTracks() {
        UserDefaults.standard.set(Array(likedTrackPaths), forKey: likedTracksStorageKey)
    }
    
    public func isTrackLiked(_ path: String) -> Bool {
        return likedTrackPaths.contains(path)
    }
    
    public func toggleLike(track: TrackMetadata) {
        if likedTrackPaths.contains(track.path) {
            likedTrackPaths.remove(track.path)
            showToast("Removed from Liked Songs: \(track.title)")
        } else {
            likedTrackPaths.insert(track.path)
            showToast("Added to Liked Songs: \(track.title)")
        }
        saveLikedTracks()
    }
    
    // =========================================================================
    // SYNCHRONIZED LYRICS ENGINE (YouTube Music Style)
    // =========================================================================
    public func loadLyrics(for track: TrackMetadata) {
        isLoadingLyrics = true
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let lines = LyricsManager.shared.fetchLyrics(for: track)
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.currentLyrics = lines
                self.currentLyricIndex = -1
                self.isLoadingLyrics = false
            }
        }
    }
    
    public func updateLyricIndex(currentTime: Double) {
        guard !currentLyrics.isEmpty else { return }
        guard currentLyrics.first?.isSynced == true else { return }
        
        var targetIndex = -1
        for (idx, line) in currentLyrics.enumerated() {
            if line.timestampSeconds <= currentTime {
                targetIndex = idx
            } else {
                break
            }
        }
        if targetIndex != currentLyricIndex {
            currentLyricIndex = targetIndex
        }
    }
    
    // =========================================================================
    // AUTOPLAY INFINITE RADIO & SMART CONTINUATION (YouTube Music Style)
    // =========================================================================
    public func getAutoplayRecommendations() -> [TrackMetadata] {
        guard let current = AudioEngine.shared.currentTrack else { return [] }
        var pool: [TrackMetadata] = []
        var seenPaths = Set<String>()
        seenPaths.insert(current.path)
        for t in upNextQueue { seenPaths.insert(t.path) }
        
        // 1. Same artist
        let artistMatches = tracks.filter {
            !seenPaths.contains($0.path) &&
            !$0.artist.isEmpty &&
            $0.artist != "Unknown Artist" &&
            $0.artist.localizedCaseInsensitiveContains(current.artist)
        }
        for t in artistMatches {
            seenPaths.insert(t.path)
            pool.append(t)
        }
        
        // 2. Same album
        if !current.album.isEmpty && current.album != "Unknown Album" {
            let albumMatches = tracks.filter {
                !seenPaths.contains($0.path) &&
                $0.album.localizedCaseInsensitiveContains(current.album)
            }
            for t in albumMatches {
                seenPaths.insert(t.path)
                pool.append(t)
            }
        }
        
        // 3. Same audiophile bit depth and sample rate
        let formatMatches = tracks.filter {
            !seenPaths.contains($0.path) &&
            $0.bitDepth == current.bitDepth &&
            $0.sampleRate == current.sampleRate
        }
        for t in formatMatches {
            seenPaths.insert(t.path)
            pool.append(t)
        }
        
        // 4. Remaining tracks from library
        let remaining = tracks.filter { !seenPaths.contains($0.path) }.shuffled()
        for t in remaining {
            if pool.count >= 15 { break }
            pool.append(t)
        }
        
        return Array(pool.prefix(10))
    }
    
    public func playNextTrack() {
        // 1. If user queued tracks manually (Play Next / Add to Queue), play the queue first!
        if !upNextQueue.isEmpty {
            let nextTrack = upNextQueue.removeFirst()
            AudioEngine.shared.play(track: nextTrack)
            return
        }
        
        // 2. Otherwise fall back to library / playlist context
        let contextList = !filteredTracks.isEmpty ? filteredTracks : tracks
        guard !contextList.isEmpty else { return }
        
        if let current = AudioEngine.shared.currentTrack, let idx = contextList.firstIndex(where: { $0.path == current.path }) {
            if idx < contextList.count - 1 {
                if isShuffle && contextList.count > 1 {
                    var rand = Int.random(in: 0..<contextList.count)
                    if rand == idx { rand = (idx + 1) % contextList.count }
                    AudioEngine.shared.play(track: contextList[rand])
                } else {
                    AudioEngine.shared.play(track: contextList[idx + 1])
                }
            } else {
                // Reached the end of current playlist / context!
                if isRepeat {
                    AudioEngine.shared.play(track: contextList[0])
                } else if isAutoplayRadio {
                    let recommendations = getAutoplayRecommendations()
                    if let radioTrack = recommendations.first {
                        AudioEngine.shared.play(track: radioTrack)
                        showToast("Autoplay Radio: Playing '\(radioTrack.title)' by \(radioTrack.artist)")
                    } else {
                        AudioEngine.shared.play(track: contextList[0])
                    }
                } else {
                    AudioEngine.shared.play(track: contextList[0])
                }
            }
        } else if let first = contextList.first {
            AudioEngine.shared.play(track: first)
        }
    }
    
    public func playPreviousTrack() {
        let contextList = !filteredTracks.isEmpty ? filteredTracks : tracks
        guard !contextList.isEmpty else { return }
        if let current = AudioEngine.shared.currentTrack, let idx = contextList.firstIndex(where: { $0.path == current.path }) {
            let prevIdx = (idx - 1 + contextList.count) % contextList.count
            AudioEngine.shared.play(track: contextList[prevIdx])
        } else if let first = contextList.first {
            AudioEngine.shared.play(track: first)
        }
    }
    
    public func volumeUp() {
        volumeDb = min(0, volumeDb + 1)
    }
    
    public func volumeDown() {
        volumeDb = max(-60, volumeDb - 1)
    }
    
    public func openFolderPicker() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = false
        panel.canChooseDirectories = true
        panel.allowsMultipleSelection = false
        panel.prompt = "Select Music Directory"
        
        if panel.runModal() == .OK, let url = panel.url {
            currentFolder = url.path
            UserDefaults.standard.set(url.path, forKey: "tsrossa.currentFolder")
            scanFolderAsync(url: url, selectFirstIfIdle: false)
        }
    }
    
    public func rescanCurrentFolder() {
        let url = URL(fileURLWithPath: currentFolder)
        scanFolderAsync(url: url, selectFirstIfIdle: false)
    }
    
    public func loadInitialMusicDirectory() {
        if let saved = UserDefaults.standard.string(forKey: "tsrossa.currentFolder"),
           FileManager.default.fileExists(atPath: saved) {
            currentFolder = saved
        }
        let defaultMusicUrl = URL(fileURLWithPath: currentFolder)
        // Delay slightly so the main macOS window renders & activates BEFORE any TCC / disk I/O
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { [weak self] in
            self?.scanFolderAsync(url: defaultMusicUrl, selectFirstIfIdle: true)
        }
    }
    
    private func scanFolderAsync(url: URL, selectFirstIfIdle: Bool) {
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard FileManager.default.fileExists(atPath: url.path) else { return }
            let initialTracks = MetadataScanner.fastScanDirectory(url: url)
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.tracks = initialTracks
                if selectFirstIfIdle, let first = initialTracks.first, AudioEngine.shared.currentTrack == nil {
                    AudioEngine.shared.currentTrack = first
                    self.updateThemeForTrack(track: first)
                }
                // Enrich durations and FLAC/ID3 tags in the background without blocking UI
                MetadataScanner.enrichMetadataInBackground(tracks: initialTracks) { [weak self] enriched in
                    guard let self = self else { return }
                    self.tracks = enriched
                }
            }
        }
    }
    
    public func toggleSort() {
        if sortMode == "DATE (NEWEST)" {
            sortMode = "A-Z TITLE"
            tracks.sort { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
        } else if sortMode == "A-Z TITLE" {
            sortMode = "ARTIST"
            tracks.sort { $0.artist.localizedCaseInsensitiveCompare($1.artist) == .orderedAscending }
        } else {
            sortMode = "DATE (NEWEST)"
            tracks.reverse()
        }
    }
    
    public func formatTime(_ seconds: Double) -> String {
        guard !seconds.isNaN && seconds >= 0 else { return "00:00" }
        let mins = Int(seconds) / 60
        let secs = Int(seconds) % 60
        return String(format: "%02d:%02d", mins, secs)
    }
    
    // Desktop Keyboard Shortcut Monitor (supports Typeahead Search ↑/↓/Enter + Global Media Shortcuts)
    public func setupKeyboardMonitor() {
        guard keyMonitor == nil else { return }
        keyMonitor = NSEvent.addLocalMonitorForEvents(matching: [.keyDown, .keyUp]) { [weak self] event in
            guard let self = self else { return event }
            
            let firstResponder = NSApp.keyWindow?.firstResponder
            let isEditingText = firstResponder is NSTextView && (firstResponder as? NSTextView)?.isEditable == true
            
            if isEditingText {
                if event.type == .keyDown {
                    // Escape -> unfocus text field, close modal, or exit theater
                    if event.keyCode == 53 {
                        if self.isTheaterMode {
                            self.isTheaterMode = false
                            return nil
                        }
                        NSApp.keyWindow?.makeFirstResponder(nil)
                        return nil
                    }
                    // When searching in the top Omnibar (not in New Playlist modal):
                    if !self.showNewPlaylistModal && !self.searchText.isEmpty {
                        let count = self.filteredTracks.count
                        if (event.keyCode == 36 || event.keyCode == 76) && count > 0 { // Return / Enter -> Play selected search result!
                            self.playSelectedSearchResult()
                            return nil
                        } else if event.keyCode == 125 && count > 0 { // Down Arrow -> Move search highlight down
                            self.searchSelectedIndex = min(count - 1, self.searchSelectedIndex + 1)
                            return nil
                        } else if event.keyCode == 126 && count > 0 { // Up Arrow -> Move search highlight up
                            self.searchSelectedIndex = max(0, self.searchSelectedIndex - 1)
                            return nil
                        }
                    }
                }
                return event
            }
            
            // Swallow keyUp for Spacebar so focused SwiftUI buttons don't re-trigger on Space release
            if event.type == .keyUp {
                if event.keyCode == 49 {
                    return nil
                }
                return event
            }
            
            if event.keyCode == 53 { // Global Escape
                if self.isTheaterMode {
                    self.isTheaterMode = false
                    return nil
                }
                if self.showNewPlaylistModal {
                    self.showNewPlaylistModal = false
                    return nil
                }
                if self.showSaveQueueModal {
                    self.showSaveQueueModal = false
                    return nil
                }
                if self.showTelemetry {
                    self.showTelemetry = false
                    return nil
                }
            }
            
            let isCommand = event.modifierFlags.contains(.command)
            
            if isCommand {
                switch event.keyCode {
                case 3: // Cmd + F -> Toggle Theater Mode (YouTube Music style)
                    self.isTheaterMode.toggle()
                    return nil
                case 18: // Cmd + 1 -> Now Playing
                    self.activeTab = .nowPlaying
                    return nil
                case 19: // Cmd + 2 -> Library
                    self.activeTab = .library
                    return nil
                case 20: // Cmd + 3 -> Play Queue
                    self.activeTab = .playlist
                    return nil
                case 21: // Cmd + 4 -> Settings
                    self.activeTab = .settings
                    return nil
                case 45: // Cmd + N -> New Playlist Modal
                    self.openCreatePlaylistModal()
                    return nil
                case 2: // Cmd + D -> Diagnostics
                    self.showTelemetry.toggle()
                    return nil
                case 15: // Cmd + R -> Rescan
                    self.rescanCurrentFolder()
                    return nil
                case 1: // Cmd + S -> Shuffle
                    self.isShuffle.toggle()
                    return nil
                case 17: // Cmd + T -> Repeat
                    self.isRepeat.toggle()
                    return nil
                case 37: // Cmd + L -> Toggle Like on current track
                    if let cur = AudioEngine.shared.currentTrack {
                        self.toggleLike(track: cur)
                    }
                    return nil
                case 126: // Cmd + Up Arrow -> Volume Up
                    self.volumeUp()
                    return nil
                case 125: // Cmd + Down Arrow -> Volume Down
                    self.volumeDown()
                    return nil
                case 124: // Cmd + Right Arrow -> Next track
                    self.playNextTrack()
                    return nil
                case 123: // Cmd + Left Arrow -> Prev track
                    self.playPreviousTrack()
                    return nil
                default:
                    break
                }
            } else {
                switch event.keyCode {
                case 49: // Spacebar -> Play / Pause
                    NSApp.keyWindow?.makeFirstResponder(nil)
                    AudioEngine.shared.togglePlayPause()
                    return nil
                case 36, 76: // Return / Enter -> Play highlighted search result if searching
                    if !self.searchText.isEmpty && !self.filteredTracks.isEmpty {
                        self.playSelectedSearchResult()
                        return nil
                    }
                case 124: // Right Arrow -> Next track
                    self.playNextTrack()
                    return nil
                case 123: // Left Arrow -> Prev track
                    self.playPreviousTrack()
                    return nil
                case 126: // Up Arrow -> Volume Up
                    self.volumeUp()
                    return nil
                case 125: // Down Arrow -> Volume Down
                    self.volumeDown()
                    return nil
                case 53: // Escape
                    if self.showNewPlaylistModal {
                        self.showNewPlaylistModal = false
                        return nil
                    }
                    if self.showTelemetry {
                        self.showTelemetry = false
                        return nil
                    }
                default:
                    break
                }
            }
            
            return event
        }
    }
}
