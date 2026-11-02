import Foundation
import Combine
import CoreAudio
import MediaPlayer
import IOKit.pwr_mgt

public struct OutputDevice: Identifiable, Hashable {
    public let id: UInt32
    public let name: String
    public let isUsb: Bool
    public let isDefault: Bool
    public let sampleRate: Double
}

public struct HardwareTelemetrySnapshot: Codable {
    public var activeDeviceId: UInt32 = 0
    public var sampleRate: UInt32 = 0
    public var actualHardwareSampleRate: Double = 0.0
    public var bitsPerSample: UInt32 = 0
    public var channels: UInt32 = 0
    public var streamIsInteger: Bool = false
    public var hogModeStatus: String = "SHARED_DISABLED"
    public var hoggerPid: Int32 = -1
    public var bufferProfileIndex: Int = 2
    public var ditherEnabled: Bool = true
    public var isMemoryLocked: Bool = false
    public var ringBufferFillPercent: Double = 0.0
    public var gaplessNextReady: Bool = false
    public var isPlaying: Bool = false
    public var isPaused: Bool = false
    public var currentSeconds: Double = 0.0
    public var totalSeconds: Double = 0.0
    public var filePath: String = ""

    public init() {}
}

public final class AudioProgressModel: ObservableObject {
    @Published public var currentTime: Double = 0.0
    @Published public var totalDuration: Double = 0.0
    @Published public var isSeeking: Bool = false
    public init() {}
}

public class AudioEngine: ObservableObject {
    public static let shared = AudioEngine()
    
    /// Dedicated background serial queue for all CoreAudio HAL / C++ calls.
    /// Keeping CoreAudio HAL mutations off the Main Thread prevents CFRunLoopGetMain() deadlocks!
    public let engineQueue = DispatchQueue(label: "com.yuka.tsrossa.coreaudio.hal", qos: .userInitiated)
    
    public let progress = AudioProgressModel()

    public var currentTime: Double {
        get { progress.currentTime }
        set { progress.currentTime = newValue }
    }
    public var totalDuration: Double {
        get { progress.totalDuration }
        set { progress.totalDuration = newValue }
    }
    public var isSeeking: Bool {
        get { progress.isSeeking }
        set { progress.isSeeking = newValue }
    }

    @Published public var isPlaying: Bool = false
    @Published public var isPaused: Bool = false
    @Published public var currentTrack: TrackMetadata? = nil
    @Published public var devices: [OutputDevice] = []
    @Published public var selectedDeviceId: UInt32 = 0
    @Published public var telemetryJson: String = "{}" {
        didSet {
            if let data = telemetryJson.data(using: .utf8),
               let snap = try? JSONDecoder().decode(HardwareTelemetrySnapshot.self, from: data) {
                self.telemetrySnapshot = snap
            }
        }
    }
    private(set) public var telemetrySnapshot: HardwareTelemetrySnapshot = HardwareTelemetrySnapshot()

    @Published public var hardwareVolumeEnabled: Bool = true
    @Published public var dacWarmupEnabled: Bool = true
    @Published public var bufferProfileIndex: Int = 1
    @Published public var volume: Double = 1.0 {
        didSet {
            setVolume(sliderValue: volume)
        }
    }
    
    private var progressTimer: Timer?
    private var hotplugTimer: Timer?
    private var isAdvancingTrack: Bool = false
    private var lastSeekTimestamp: TimeInterval = 0
    private var hasPreparedGaplessNext: Bool = false
    private var powerAssertionID: IOPMAssertionID = 0
    private var hasPowerAssertion: Bool = false
    
    // Hardware hot-plug tracking state (accessed on engineQueue)
    private var lastKnownDeviceIds: Set<UInt32> = []
    private var lastKnownSystemDefaultId: UInt32 = 0
    private var hasInitializedDevices: Bool = false
    
    // Debounce state to prevent double-clicking a track row from loading the FLAC file twice
    private var lastPlayRequestPath: String = ""
    private var lastPlayRequestTime: TimeInterval = 0
    
    private init() {
        engineQueue.async { [weak self] in
            guard let self = self else { return }
            _ = tsrossa_init()
            self.refreshDevicesOnEngineQueue(checkHotplug: false)
            self.setupCoreAudioHotplugListeners()
        }
        setupRemoteCommands()
        startHotplugMonitorTimer()
    }
    
    deinit {
        stopProgressTimer()
        hotplugTimer?.invalidate()
        updatePowerAssertion(keepAwake: false)
        tsrossa_shutdown()
    }
    
    /// Registers native macOS CoreAudio HAL listeners for instant 3.5mm jack & USB DAC plug/unplug events
    private func setupCoreAudioHotplugListeners() {
        let notifyHotplug: () -> Void = { [weak self] in
            guard let self = self else { return }
            self.refreshDevicesOnEngineQueue(checkHotplug: true)
            // Apple Silicon 3.5mm jack impedance sense takes ~150-350ms to finish exposing streams
            self.engineQueue.asyncAfter(deadline: .now() + 0.25) { [weak self] in
                self?.refreshDevicesOnEngineQueue(checkHotplug: true)
            }
            self.engineQueue.asyncAfter(deadline: .now() + 0.60) { [weak self] in
                self?.refreshDevicesOnEngineQueue(checkHotplug: true)
            }
        }
        
        var devicesAddr = AudioObjectPropertyAddress(
            mSelector: kAudioHardwarePropertyDevices,
            mScope: kAudioObjectPropertyScopeGlobal,
            mElement: kAudioObjectPropertyElementMain
        )
        AudioObjectAddPropertyListenerBlock(
            AudioObjectID(kAudioObjectSystemObject),
            &devicesAddr,
            engineQueue
        ) { _, _ in
            notifyHotplug()
        }
        
        var defaultOutAddr = AudioObjectPropertyAddress(
            mSelector: kAudioHardwarePropertyDefaultOutputDevice,
            mScope: kAudioObjectPropertyScopeGlobal,
            mElement: kAudioObjectPropertyElementMain
        )
        AudioObjectAddPropertyListenerBlock(
            AudioObjectID(kAudioObjectSystemObject),
            &defaultOutAddr,
            engineQueue
        ) { _, _ in
            notifyHotplug()
        }
    }
    
    /// Lightweight 1-second background poll as a fallback to guarantee jack plug detection
    private func startHotplugMonitorTimer() {
        DispatchQueue.main.async { [weak self] in
            self?.hotplugTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
                guard let self = self else { return }
                self.engineQueue.async { [weak self] in
                    self?.refreshDevicesOnEngineQueue(checkHotplug: true)
                }
            }
        }
    }
    
    public func refreshDevices() {
        engineQueue.async { [weak self] in
            self?.refreshDevicesOnEngineQueue(checkHotplug: true)
        }
    }
    
    private func refreshDevicesOnEngineQueue(checkHotplug: Bool) {
        let count = tsrossa_get_device_count()
        var list: [OutputDevice] = []
        var defaultId: UInt32 = 0
        for i in 0..<count {
            let name = String(cString: tsrossa_get_device_name(i))
            let id = tsrossa_get_device_id(i)
            let isUsb = tsrossa_get_device_is_usb(i)
            let isDef = tsrossa_get_device_is_default(i)
            let rate = tsrossa_get_device_sample_rate(i)
            list.append(OutputDevice(id: id, name: name, isUsb: isUsb, isDefault: isDef, sampleRate: rate))
            if isDef {
                defaultId = id
            }
        }
        
        let currentIds = Set(list.map { $0.id })
        
        if !hasInitializedDevices {
            hasInitializedDevices = true
            lastKnownDeviceIds = currentIds
            lastKnownSystemDefaultId = defaultId
            let initialTarget = defaultId != 0 ? defaultId : (list.first?.id ?? 0)
            if initialTarget != 0 {
                _ = tsrossa_select_device(initialTarget)
            }
            let json = String(cString: tsrossa_get_telemetry_json())
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                self.devices = list
                if self.selectedDeviceId == 0 {
                    self.selectedDeviceId = initialTarget
                }
                self.telemetryJson = json
            }
            return
        }
        
        let devicesChanged = (currentIds != lastKnownDeviceIds)
        let defaultChanged = (defaultId != 0 && defaultId != lastKnownSystemDefaultId)
        
        if checkHotplug && (devicesChanged || defaultChanged) {
            let newlyAdded = currentIds.subtracting(lastKnownDeviceIds)
            lastKnownDeviceIds = currentIds
            lastKnownSystemDefaultId = defaultId
            
            // Determine target device for hot-plug event:
            // 1. If a new headset/USB DAC was just plugged in, route to it immediately
            // 2. Otherwise if macOS changed its default output device (jack sense), route to new default
            // 3. Otherwise if the active device was unplugged, fall back to default
            var targetId: UInt32 = 0
            if let addedId = newlyAdded.first {
                targetId = addedId
            } else if defaultChanged {
                targetId = defaultId
            } else if !currentIds.contains(selectedDeviceId) {
                targetId = defaultId != 0 ? defaultId : (list.first?.id ?? 0)
            }
            
            if targetId != 0 {
                _ = tsrossa_select_device(targetId)
                let json = String(cString: tsrossa_get_telemetry_json())
                DispatchQueue.main.async { [weak self] in
                    guard let self = self else { return }
                    self.devices = list
                    self.selectedDeviceId = targetId
                    self.telemetryJson = json
                }
                return
            }
        }
        
        lastKnownDeviceIds = currentIds
        lastKnownSystemDefaultId = defaultId
        
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            if self.devices != list {
                self.devices = list
            }
            if self.selectedDeviceId == 0 && defaultId != 0 {
                self.selectedDeviceId = defaultId
            }
        }
    }
    
    public func selectDevice(id: UInt32) {
        // Optimistically highlight the selected device in UI immediately
        self.selectedDeviceId = id
        engineQueue.async { [weak self] in
            guard let self = self else { return }
            _ = tsrossa_select_device(id)
            self.refreshDevicesOnEngineQueue(checkHotplug: false)
            let json = String(cString: tsrossa_get_telemetry_json())
            DispatchQueue.main.async {
                self.telemetryJson = json
            }
        }
    }
    
    public func play(track: TrackMetadata) {
        let now = ProcessInfo.processInfo.systemUptime
        if track.path == lastPlayRequestPath && (now - lastPlayRequestTime) < 0.45 {
            return
        }
        lastPlayRequestPath = track.path
        lastPlayRequestTime = now
        hasPreparedGaplessNext = false
        stopProgressTimer()
        
        // Update UI track identity and artwork immediately without waiting for FLAC decode
        self.currentTrack = track
        self.currentTime = 0.0
        AppViewModel.shared.updateThemeForTrack(track: track)
        AppViewModel.shared.loadLyrics(for: track)
        
        let trackPath = track.path
        engineQueue.async { [weak self] in
            guard let self = self else { return }
            let ok = tsrossa_play(trackPath)
            guard ok else {
                AppLogger.shared.log(.audio, .error, "Failed to decode/open audio file: \(trackPath)")
                return
            }
            
            let realRate = tsrossa_get_track_sample_rate()
            let realBits = tsrossa_get_track_bit_depth()
            let realChannels = tsrossa_get_track_channels()
            let realDuration = tsrossa_get_total_duration()
            let json = String(cString: tsrossa_get_telemetry_json())
            self.refreshDevicesOnEngineQueue(checkHotplug: false)
            
            DispatchQueue.main.async {
                var updatedTrack = track
                if realRate > 0 { updatedTrack.sampleRate = realRate }
                if realBits > 0 { updatedTrack.bitDepth = realBits }
                if realChannels > 0 { updatedTrack.channels = realChannels }
                if realDuration > 0 { updatedTrack.durationSeconds = realDuration }
                self.currentTrack = updatedTrack
                self.isPlaying = true
                self.isPaused = false
                self.totalDuration = realDuration
                self.telemetryJson = json
                self.isAdvancingTrack = false
                
                if let idx = AppViewModel.shared.tracks.firstIndex(where: { $0.path == track.path }) {
                    AppViewModel.shared.tracks[idx] = updatedTrack
                }
                AppLogger.shared.log(.audio, .info, "Playing: \(track.title) (\(realRate) Hz, \(realBits)-bit)")
                self.updateNowPlayingInfo()
                self.updatePowerAssertion(keepAwake: AppViewModel.shared.keepScreenAwake)
                self.startProgressTimer()
            }
        }
    }
    
    public func pause() {
        guard isPlaying && !isPaused else { return }
        isPaused = true
        engineQueue.async {
            tsrossa_pause()
        }
        AppLogger.shared.log(.audio, .info, "Playback paused at \(String(format: "%.1f", currentTime))s")
        updateNowPlayingInfo()
        updatePowerAssertion(keepAwake: AppViewModel.shared.keepScreenAwake)
        updateTelemetry()
    }

    public func resume() {
        guard isPlaying && isPaused else { return }
        isPaused = false
        engineQueue.async {
            tsrossa_resume()
        }
        AppLogger.shared.log(.audio, .info, "Playback resumed")
        updateNowPlayingInfo()
        updatePowerAssertion(keepAwake: AppViewModel.shared.keepScreenAwake)
        updateTelemetry()
    }

    public func togglePlayPause() {
        if isPlaying {
            if isPaused {
                resume()
            } else {
                pause()
            }
        } else if let track = currentTrack {
            play(track: track)
        } else if let selPath = AppViewModel.shared.selectedTrackPath,
                  let selTrack = AppViewModel.shared.filteredTracks.first(where: { $0.path == selPath }) {
            play(track: selTrack)
        } else if let first = AppViewModel.shared.filteredTracks.first ?? AppViewModel.shared.tracks.first {
            play(track: first)
        }
    }
    
    public func stop() {
        isPlaying = false
        isPaused = false
        currentTime = 0.0
        stopProgressTimer()
        AppLogger.shared.log(.audio, .info, "Playback stopped")
        updateNowPlayingInfo()
        updatePowerAssertion(keepAwake: false)
        engineQueue.async { [weak self] in
            tsrossa_stop()
            let json = String(cString: tsrossa_get_telemetry_json())
            DispatchQueue.main.async {
                self?.telemetryJson = json
            }
        }
    }
    
    public func seek(to seconds: Double) {
        let safeMax = max(0.0, totalDuration - 0.25)
        let clamped = min(max(0.0, seconds), safeMax)
        lastSeekTimestamp = ProcessInfo.processInfo.systemUptime
        self.currentTime = clamped
        updateNowPlayingInfo()
        engineQueue.async {
            _ = tsrossa_seek(clamped)
        }
    }

    public func setVolume(sliderValue: Double) {
        let clamped = min(max(0.0, sliderValue), 1.0)
        let hwVol = self.hardwareVolumeEnabled
        engineQueue.async {
            tsrossa_set_slider_volume(clamped, hwVol)
        }
    }

    public func setBufferProfile(_ index: Int) {
        self.bufferProfileIndex = index
        engineQueue.async {
            tsrossa_set_buffer_profile(Int32(index))
        }
    }

    public func triggerDacWarmup(durationMs: UInt32 = 350) {
        engineQueue.async {
            tsrossa_trigger_warmup(durationMs)
        }
    }

    public func prepareNextTrack(path: String) {
        engineQueue.async {
            _ = tsrossa_prepare_next_track(path)
        }
    }

    public func clearNextTrack() {
        engineQueue.async {
            tsrossa_clear_next_track()
        }
    }
    
    public func updateTelemetry() {
        engineQueue.async { [weak self] in
            let json = String(cString: tsrossa_get_telemetry_json())
            DispatchQueue.main.async {
                self?.telemetryJson = json
            }
        }
    }
    
    private func setupRemoteCommands() {
        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.playCommand.isEnabled = true
        commandCenter.playCommand.addTarget { [weak self] _ in
            guard let self = self else { return .commandFailed }
            if self.isPaused {
                self.resume()
                return .success
            }
            return .commandFailed
        }

        commandCenter.pauseCommand.isEnabled = true
        commandCenter.pauseCommand.addTarget { [weak self] _ in
            guard let self = self else { return .commandFailed }
            if self.isPlaying && !self.isPaused {
                self.pause()
                return .success
            }
            return .commandFailed
        }

        commandCenter.togglePlayPauseCommand.isEnabled = true
        commandCenter.togglePlayPauseCommand.addTarget { [weak self] _ in
            guard let self = self else { return .commandFailed }
            self.togglePlayPause()
            return .success
        }

        commandCenter.nextTrackCommand.isEnabled = true
        commandCenter.nextTrackCommand.addTarget { _ in
            DispatchQueue.main.async {
                AppViewModel.shared.playNextTrack(userInitiated: true)
            }
            return .success
        }

        commandCenter.previousTrackCommand.isEnabled = true
        commandCenter.previousTrackCommand.addTarget { _ in
            DispatchQueue.main.async {
                AppViewModel.shared.playPreviousTrack()
            }
            return .success
        }

        commandCenter.changePlaybackPositionCommand.isEnabled = true
        commandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let self = self, let posEvent = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            self.seek(to: posEvent.positionTime)
            return .success
        }
    }

    public func updateNowPlayingInfo() {
        var info: [String: Any] = [:]
        if let track = currentTrack {
            info[MPMediaItemPropertyTitle] = track.title
            info[MPMediaItemPropertyArtist] = track.artist
            info[MPMediaItemPropertyAlbumTitle] = track.album
            info[MPMediaItemPropertyPlaybackDuration] = totalDuration > 0 ? totalDuration : track.durationSeconds
            info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime
            info[MPNowPlayingInfoPropertyPlaybackRate] = (isPlaying && !isPaused) ? 1.0 : 0.0

            if let img = track.artwork {
                info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: img.size) { _ in img }
            }
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        MPNowPlayingInfoCenter.default().playbackState = (isPlaying && !isPaused) ? .playing : (isPaused ? .paused : .stopped)
    }

    public func updatePowerAssertion(keepAwake: Bool) {
        if isPlaying && !isPaused && keepAwake {
            if !hasPowerAssertion {
                let reason = "TSRossa Active Hi-Res Audio Playback" as CFString
                let success = IOPMAssertionCreateWithName(
                    kIOPMAssertionTypePreventUserIdleDisplaySleep as CFString,
                    IOPMAssertionLevel(kIOPMAssertionLevelOn),
                    reason,
                    &powerAssertionID
                )
                hasPowerAssertion = (success == kIOReturnSuccess)
                if hasPowerAssertion {
                    AppLogger.shared.log(.system, .info, "IOPMAssertion display sleep prevention engaged")
                }
            }
        } else {
            if hasPowerAssertion {
                IOPMAssertionRelease(powerAssertionID)
                powerAssertionID = 0
                hasPowerAssertion = false
                AppLogger.shared.log(.system, .info, "IOPMAssertion released")
            }
        }
    }
    
    private func startProgressTimer() {
        stopProgressTimer()
        self.progressTimer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            if self.isPlaying && !self.isPaused && !self.isAdvancingTrack {
                let t = tsrossa_get_current_time()
                let elapsedSinceSeek = ProcessInfo.processInfo.systemUptime - self.lastSeekTimestamp
                if !self.isSeeking && elapsedSinceSeek > 0.8 {
                    self.currentTime = t
                }
                AppViewModel.shared.updateLyricIndex(currentTime: t)

                // Pre-buffer next track for gapless handover 5 seconds before EOF
                if self.totalDuration > 10.0 && (self.totalDuration - t) < 5.0 && !self.hasPreparedGaplessNext {
                    self.hasPreparedGaplessNext = true
                    if let next = AppViewModel.shared.peekNextTrack() {
                        self.prepareNextTrack(path: next.path)
                    }
                }

                if tsrossa_consume_gapless_transition() {
                    self.handleSeamlessGaplessAdvance()
                } else if tsrossa_is_track_finished() && self.totalDuration > 0 && elapsedSinceSeek > 0.4 {
                    self.isAdvancingTrack = true
                    AppViewModel.shared.playNextTrack(userInitiated: false)
                }
            }
        }
    }

    private func handleSeamlessGaplessAdvance() {
        DispatchQueue.main.async {
            AppViewModel.shared.playNextTrack(userInitiated: false)
        }
    }
    
    private func stopProgressTimer() {
        progressTimer?.invalidate()
        progressTimer = nil
    }
}
