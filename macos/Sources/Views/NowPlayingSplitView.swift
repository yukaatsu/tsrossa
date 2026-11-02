import SwiftUI

public struct NowPlayingSplitView: View {
    @ObservedObject var engine = AudioEngine.shared
    @ObservedObject var vm = AppViewModel.shared
    public var theme: LiquidGlassTheme

    public init(theme: LiquidGlassTheme) {
        self.theme = theme
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Top Drawer Header (Grabber Handle + Studio Top Bar)
            drawerHeader

            // Split Stage Content
            GeometryReader { geo in
                let availableH = geo.size.height
                let availableW = geo.size.width
                let artSize = min(320.0, max(140.0, min(availableW * 0.40, availableH - 240.0)))

                HStack(alignment: .top, spacing: max(16, min(36, availableW * 0.03))) {
                    // Left: Hero Artwork & Audio Attributes & Studio Transport
                    leftStageView(availableW: availableW, availableH: availableH, artSize: artSize)
                        .frame(maxWidth: max(260, min(440, availableW * 0.45)))

                    // Right: Integrated Queue & Lyrics View
                    QueueLyricsView(theme: theme)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .padding(.horizontal, max(16, min(28, availableW * 0.025)))
                .padding(.bottom, 16)
                .frame(width: availableW, height: availableH, alignment: .top)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            ZStack {
                // Layer 1: Ambient Blurred Artwork Glow
                if let track = engine.currentTrack, let art = track.artwork {
                    Image(nsImage: art)
                        .resizable()
                        .scaledToFill()
                        .blur(radius: 80)
                        .opacity(0.32)
                        .clipped()
                }

                // Layer 2: macOS Ultra Thin Glass Material
                Rectangle()
                    .fill(.ultraThinMaterial)

                // Layer 3: Subtle Studio Lighting Gradient
                LinearGradient(
                    colors: [
                        theme.primary.opacity(0.18),
                        theme.surface.opacity(0.85),
                        Color.black.opacity(0.92)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
        )
        .continuousSquircle(24)
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .strokeBorder(
                    LinearGradient(
                        colors: [
                            Color.white.opacity(0.35),
                            Color.white.opacity(0.08),
                            theme.primary.opacity(0.2)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 1
                )
        )
        .shadow(color: Color.black.opacity(0.55), radius: 36, x: 0, y: -8)
        .padding(.horizontal, 16)
        .padding(.top, 28)
        .padding(.bottom, 12)
    }

    // MARK: - Drawer Header
    private var drawerHeader: some View {
        VStack(spacing: 6) {
            // Pull-down Grabber Handle
            Capsule()
                .fill(Color.white.opacity(0.35))
                .frame(width: 44, height: 5)
                .padding(.top, 8)
                .contentShape(Rectangle())

            // Studio Top Bar
            HStack(spacing: 12) {
                // Minimize Button
                Button(action: {
                    vm.closeNowPlayingStage()
                }) {
                    HStack(spacing: 5) {
                        Image(systemName: "chevron.down")
                            .font(.system(size: 12, weight: .bold))
                        Text("Hide")
                            .font(.system(size: 12, weight: .semibold))
                    }
                    .foregroundStyle(.primary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.white.opacity(0.08))
                    .continuousSquircle(8)
                }
                .buttonStyle(PlainButtonStyle())
                .help("Hide Now Playing (Esc / ⌘P)")

                Spacer()

                // Center Live Signal Path Pill
                signalPathPill

                Spacer()

                // Track Details Modal Trigger
                Button(action: {
                    vm.showTrackDetailsModal = true
                }) {
                    Image(systemName: "info.circle")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(.primary)
                        .padding(6)
                        .background(Color.white.opacity(0.08))
                        .continuousSquircle(8)
                }
                .buttonStyle(PlainButtonStyle())
                .help("Track Details (⌘I)")

                // Shortcuts Modal Trigger
                Button(action: {
                    vm.showKeyboardShortcutsModal = true
                }) {
                    Image(systemName: "keyboard")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(.primary)
                        .padding(6)
                        .background(Color.white.opacity(0.08))
                        .continuousSquircle(8)
                }
                .buttonStyle(PlainButtonStyle())
                .help("Keyboard Shortcuts (⌘/)")
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 6)
        }
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 6, coordinateSpace: .named("MainWindowCoordSpace"))
                .onChanged { val in
                    if val.translation.height > 0 {
                        vm.nowPlayingDragOffset = val.translation.height
                    }
                }
                .onEnded { val in
                    let shouldClose = val.translation.height > 40 || val.predictedEndTranslation.height > 80
                    if shouldClose {
                        vm.closeNowPlayingStage()
                    } else {
                        vm.openNowPlayingStage()
                    }
                }
        )
    }

    // MARK: - Live Signal Path Pill
    private var signalPathPill: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(engine.telemetrySnapshot.hogModeStatus == "EXCLUSIVE_HOG" ? Color.green : (engine.telemetrySnapshot.streamIsInteger ? Color.mint : theme.primary))
                .frame(width: 7, height: 7)

            Text(engine.telemetrySnapshot.hogModeStatus == "EXCLUSIVE_HOG" ? "EXCLUSIVE HOG • BIT-PERFECT" : "SHARED MIXER HAL")
                .font(.system(size: 10, weight: .bold, design: .monospaced))
                .foregroundStyle(.primary)

            if let track = engine.currentTrack {
                Text("•")
                    .foregroundStyle(.secondary)
                Text("\(track.sampleRate / 1000) kHz / \(track.bitDepth)-BIT")
                    .font(.system(size: 10, weight: .semibold, design: .monospaced))
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 5)
        .background(Color.white.opacity(0.07))
        .continuousSquircle(12)
    }

    // MARK: - Left Stage View
    private func leftStageView(availableW: CGFloat, availableH: CGFloat, artSize: CGFloat) -> some View {
        VStack(spacing: max(8, min(14, availableH * 0.02))) {
            // Hero Artwork (Clickable to Play/Pause)
            Button(action: {
                if engine.currentTrack == nil {
                    vm.playSelectedOrHighlightedTrack()
                } else {
                    engine.togglePlayPause()
                }
            }) {
                ZStack {
                    Group {
                        if let track = engine.currentTrack, let art = track.artwork {
                            Image(nsImage: art)
                                .resizable()
                                .scaledToFit()
                        } else {
                            ZStack {
                                Color.white.opacity(0.06)
                                TsrossaBrandIcon(size: max(50, artSize * 0.4), cornerRadius: 18)
                            }
                        }
                    }
                    .frame(width: artSize, height: artSize)
                    .scaleEffect(engine.isPlaying && !engine.isPaused ? 1.0 : 0.94)
                    .animation(.spring(response: 0.45, dampingFraction: 0.75), value: engine.isPlaying && !engine.isPaused)
                    .continuousSquircle(max(14, artSize * 0.08))
                    .overlay(
                        RoundedRectangle(cornerRadius: max(14, artSize * 0.08), style: .continuous)
                            .strokeBorder(
                                LinearGradient(
                                    colors: [Color.white.opacity(0.40), Color.white.opacity(0.05), theme.primary.opacity(0.3)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                ),
                                lineWidth: 1.5
                            )
                    )
                    .shadow(color: Color.black.opacity(engine.isPlaying && !engine.isPaused ? 0.45 : 0.20), radius: 24, x: 0, y: 12)

                    // Paused overlay badge if paused while track loaded
                    if engine.currentTrack != nil && (!engine.isPlaying || engine.isPaused) {
                        Circle()
                            .fill(Color.black.opacity(0.4))
                            .frame(width: 48, height: 48)
                            .overlay(
                                Image(systemName: "play.fill")
                                    .font(.system(size: 20))
                                    .foregroundStyle(.white)
                                    .offset(x: 2)
                            )
                    }
                }
            }
            .buttonStyle(PlainButtonStyle())
            .help("Click to Play / Pause")

            // Track Identity & Favorite Button
            HStack(alignment: .center, spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    if let track = engine.currentTrack {
                        Text(track.title)
                            .font(.system(size: max(15, min(20, availableW * 0.022)), weight: .bold))
                            .foregroundStyle(.primary)
                            .lineLimit(1)

                        Text("\(track.artist) — \(track.album)")
                            .font(.system(size: max(11.5, min(13.5, availableW * 0.015)), weight: .medium))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    } else {
                        Text("No Track Playing")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.primary)
                        Text("Select a song from library or click Play below")
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                    }
                }

                Spacer(minLength: 4)

                if let track = engine.currentTrack {
                    Button(action: {
                        vm.toggleLike(track: track)
                    }) {
                        Image(systemName: vm.isLiked(track: track) ? "heart.fill" : "heart")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(vm.isLiked(track: track) ? Color.pink : Color.secondary)
                            .frame(width: 30, height: 30)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(PlainButtonStyle())
                    .help("Favorite / Like Track")
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // Audiophile Badges
            if let track = engine.currentTrack {
                HStack(spacing: 5) {
                    let snap = engine.telemetrySnapshot
                    badgeView(
                        text: snap.streamIsInteger ? "BIT-PERFECT" : "DSP ACTIVE",
                        color: snap.streamIsInteger ? Color.green : Color.orange
                    )
                    badgeView(
                        text: "\(track.sampleRate / 1000) kHz",
                        color: theme.primary
                    )
                    badgeView(
                        text: "\(track.bitDepth)-BIT",
                        color: theme.primary
                    )
                    if track.isMqa {
                        badgeView(text: "MQA", color: Color.purple)
                    }
                    let ext = track.path.split(separator: ".").last?.uppercased() ?? "AUDIO"
                    badgeView(text: String(ext), color: Color.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            // Scrubber Bar
            PlaybackScrubberView(accentColor: theme.primary)
                .padding(.vertical, 2)

            // 5-Button Studio Transport Deck
            transportDeckView

            // Volume & Hardware DAC Bar
            volumeAndDacDeckView
        }
    }

    // MARK: - 5-Button Studio Transport Deck
    private var transportDeckView: some View {
        HStack(spacing: 18) {
            // Shuffle
            Button(action: {
                vm.isShuffle.toggle()
            }) {
                Image(systemName: "shuffle")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(vm.isShuffle ? theme.primary : .secondary)
                    .frame(width: 32, height: 32)
                    .background(vm.isShuffle ? theme.primary.opacity(0.15) : Color.white.opacity(0.04))
                    .clipShape(Circle())
            }
            .buttonStyle(PlainButtonStyle())
            .help("Shuffle Mode (⌘S)")

            // Previous
            Button(action: {
                vm.playPreviousTrack()
            }) {
                Image(systemName: "backward.fill")
                    .font(.system(size: 17))
                    .foregroundStyle(.primary)
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(PlainButtonStyle())
            .help("Previous Track (⌘←)")

            // Hero Play / Pause Circle Button
            Button(action: {
                if engine.currentTrack == nil {
                    vm.playSelectedOrHighlightedTrack()
                } else {
                    engine.togglePlayPause()
                }
            }) {
                ZStack {
                    Circle()
                        .fill(theme.primary)
                        .frame(width: 50, height: 50)
                        .shadow(color: theme.primary.opacity(0.35), radius: 8, y: 2)

                    Image(systemName: engine.isPlaying && !engine.isPaused ? "pause.fill" : "play.fill")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(theme.onPrimary)
                        .offset(x: engine.isPlaying && !engine.isPaused ? 0 : 2)
                }
            }
            .buttonStyle(PlainButtonStyle())
            .help("Play / Pause (Space)")

            // Next
            Button(action: {
                vm.playNextTrack(userInitiated: true)
            }) {
                Image(systemName: "forward.fill")
                    .font(.system(size: 17))
                    .foregroundStyle(.primary)
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(PlainButtonStyle())
            .help("Next Track (⌘→)")

            // Repeat
            Button(action: {
                if vm.repeatMode == .off {
                    vm.repeatMode = .all
                } else if vm.repeatMode == .all {
                    vm.repeatMode = .one
                } else {
                    vm.repeatMode = .off
                }
            }) {
                Image(systemName: vm.repeatMode == .one ? "repeat.1" : "repeat")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(vm.repeatMode != .off ? theme.primary : .secondary)
                    .frame(width: 32, height: 32)
                    .background(vm.repeatMode != .off ? theme.primary.opacity(0.15) : Color.white.opacity(0.04))
                    .clipShape(Circle())
            }
            .buttonStyle(PlainButtonStyle())
            .help("Repeat Mode (⌘R)")
        }
    }

    // MARK: - Volume & DAC Deck
    private var volumeAndDacDeckView: some View {
        HStack(spacing: 10) {
            // Speaker Icon
            Image(systemName: engine.volume <= 0.01 ? "speaker.slash.fill" : (engine.volume < 0.5 ? "speaker.wave.1.fill" : "speaker.wave.2.fill"))
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
                .frame(width: 16)
                .onTapGesture {
                    if engine.volume > 0 {
                        engine.volume = 0.0
                    } else {
                        engine.volume = 0.7
                    }
                }

            // Volume Slider
            Slider(value: $engine.volume, in: 0.0...1.0)
                .accentColor(theme.primary)
                .frame(maxWidth: 140)

            Spacer()

            // DAC Output Device Menu
            Menu {
                ForEach(engine.devices) { device in
                    Button(action: {
                        engine.selectDevice(id: device.id)
                    }) {
                        HStack {
                            Text(device.name)
                            if device.id == engine.selectedDeviceId {
                                Image(systemName: "checkmark")
                            }
                        }
                    }
                }
            } label: {
                HStack(spacing: 5) {
                    Image(systemName: "hifispeaker.fill")
                        .font(.system(size: 10))
                    Text(engine.devices.first(where: { $0.id == engine.selectedDeviceId })?.name ?? "Default Audio")
                        .font(.system(size: 10.5, weight: .medium))
                        .lineLimit(1)
                }
                .foregroundStyle(.secondary)
                .padding(.horizontal, 7)
                .padding(.vertical, 3.5)
                .background(Color.white.opacity(0.06))
                .continuousSquircle(6)
            }
            .menuStyle(BorderlessButtonMenuStyle())
            .fixedSize()
        }
        .padding(.horizontal, 2)
    }

    private func badgeView(text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 9.5, weight: .bold, design: .monospaced))
            .foregroundStyle(color)
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
            .padding(.horizontal, 6)
            .padding(.vertical, 2.5)
            .background(color.opacity(0.12))
            .continuousSquircle(5)
    }
}
