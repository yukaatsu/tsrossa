import Foundation
import Combine
import CoreAudio

public struct OutputDevice: Identifiable, Hashable {
    public let id: UInt32
    public let name: String
    public let isUsb: Bool
    public let isDefault: Bool
    public let sampleRate: Double
}

public struct TrackMetadata: Identifiable, Hashable {
    public let id: String
    public let title: String
    public let artist: String
    public let album: String
    public let path: String
    public let sampleRate: UInt32
    public let bitDepth: UInt32
    public let channels: UInt32
    public let durationSeconds: Double
    
    public init(path: String, title: String, artist: String, album: String, sampleRate: UInt32, bitDepth: UInt32, channels: UInt32, durationSeconds: Double) {
        self.id = path
        self.path = path
        self.title = title
        self.artist = artist
        self.album = album
        self.sampleRate = sampleRate
        self.bitDepth = bitDepth
        self.channels = channels
        self.durationSeconds = durationSeconds
    }
}

public class AudioEngine: ObservableObject {
    public static let shared = AudioEngine()
    
    /// Dedicated background serial queue for all CoreAudio HAL / C++ calls.
    /// Keeping CoreAudio HAL mutations off the Main Thread prevents CFRunLoopGetMain() deadlocks!
    public let engineQueue = DispatchQueue(label: "com.yuka.tsrossa.coreaudio.hal", qos: .userInitiated)
    
    @Published public var isPlaying: Bool = false
    @Published public var isPaused: Bool = false
    @Published public var currentTime: Double = 0.0
    @Published public var totalDuration: Double = 0.0
    @Published public var currentTrack: TrackMetadata? = nil
    @Published public var devices: [OutputDevice] = []
    @Published public var selectedDeviceId: UInt32 = 0
    @Published public var telemetryJson: String = "{}"
    
    private var progressTimer: Timer?
    private var hotplugTimer: Timer?
    private var isAdvancingTrack: Bool = false
    
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
        startHotplugMonitorTimer()
    }
    
    deinit {
        stopProgressTimer()
        hotplugTimer?.invalidate()
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
            guard ok else { return }
            
            let realRate = tsrossa_get_track_sample_rate()
            let realBits = tsrossa_get_track_bit_depth()
            let realChannels = tsrossa_get_track_channels()
            let realDuration = tsrossa_get_total_duration()
            let json = String(cString: tsrossa_get_telemetry_json())
            self.refreshDevicesOnEngineQueue(checkHotplug: false)
            
            DispatchQueue.main.async {
                let updatedTrack = TrackMetadata(
                    path: track.path,
                    title: track.title,
                    artist: track.artist,
                    album: track.album,
                    sampleRate: realRate > 0 ? realRate : track.sampleRate,
                    bitDepth: realBits > 0 ? realBits : track.bitDepth,
                    channels: realChannels > 0 ? realChannels : track.channels,
                    durationSeconds: realDuration > 0 ? realDuration : track.durationSeconds
                )
                self.currentTrack = updatedTrack
                self.isPlaying = true
                self.isPaused = false
                self.totalDuration = realDuration
                self.telemetryJson = json
                self.isAdvancingTrack = false
                
                if let idx = AppViewModel.shared.tracks.firstIndex(where: { $0.path == track.path }) {
                    AppViewModel.shared.tracks[idx] = updatedTrack
                }
                self.startProgressTimer()
            }
        }
    }
    
    public func togglePlayPause() {
        if isPlaying {
            if isPaused {
                isPaused = false
                engineQueue.async {
                    tsrossa_resume()
                }
            } else {
                isPaused = true
                engineQueue.async {
                    tsrossa_pause()
                }
            }
            updateTelemetry()
        } else if let track = currentTrack {
            play(track: track)
        }
    }
    
    public func stop() {
        isPlaying = false
        isPaused = false
        currentTime = 0.0
        stopProgressTimer()
        engineQueue.async { [weak self] in
            tsrossa_stop()
            let json = String(cString: tsrossa_get_telemetry_json())
            DispatchQueue.main.async {
                self?.telemetryJson = json
            }
        }
    }
    
    public func seek(to seconds: Double) {
        self.currentTime = seconds
        engineQueue.async {
            _ = tsrossa_seek(seconds)
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
    
    private func startProgressTimer() {
        stopProgressTimer()
        self.progressTimer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            if self.isPlaying && !self.isPaused && !self.isAdvancingTrack {
                let t = tsrossa_get_current_time()
                let stillPlaying = tsrossa_is_playing()
                self.currentTime = t
                AppViewModel.shared.updateLyricIndex(currentTime: t)
                if !stillPlaying && self.totalDuration > 0 && t >= self.totalDuration - 0.5 {
                    self.isAdvancingTrack = true
                    if AppViewModel.shared.isRepeat, let current = self.currentTrack {
                        self.play(track: current)
                    } else {
                        AppViewModel.shared.playNextTrack()
                    }
                }
            }
        }
    }
    
    private func stopProgressTimer() {
        progressTimer?.invalidate()
        progressTimer = nil
    }
}
