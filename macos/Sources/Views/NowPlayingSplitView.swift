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
                let artSize = min(440.0, max(135.0, min(availableW * 0.38, availableH - 285.0)))

                HStack(alignment: .top, spacing: max(16, min(36, availableW * 0.03))) {
                    // Left: Hero Artwork & Audio Attributes & Studio Transport (Full Height — Flush with Right Column!)
                    leftStageView(availableW: availableW, availableH: availableH, artSize: artSize)
                        .frame(maxWidth: max(280, min(460, availableW * 0.45)), maxHeight: .infinity)

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
                // Layer 1: Ambient Blurred Artwork Glow (Dual-layer smooth 820ms crossfade)
                AmbientCrossfadeArtworkView(opacity: 0.32)

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
            .buttonStyle(TsrossaScaleButtonStyle())
            .help("Hide Now Playing (Esc / ⌘P)")

            Spacer()

            // Track Details Modal Trigger
            Button(action: {
                withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) {
                    vm.showTrackDetailsModal = true
                }
            }) {
                Image(systemName: "info.circle")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.primary)
                    .padding(6)
                    .background(Color.white.opacity(0.08))
                    .continuousSquircle(8)
            }
            .buttonStyle(TsrossaScaleButtonStyle())
            .help("Track Details (⌘I)")

            // Shortcuts Modal Trigger
            Button(action: {
                withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) {
                    vm.showKeyboardShortcutsModal = true
                }
            }) {
                Image(systemName: "keyboard")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.primary)
                    .padding(6)
                    .background(Color.white.opacity(0.08))
                    .continuousSquircle(8)
            }
            .buttonStyle(TsrossaScaleButtonStyle())
            .help("Keyboard Shortcuts (⌘/)")
        }
        .padding(.horizontal, 18)
        .padding(.top, 10)
        .padding(.bottom, 6)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 6, coordinateSpace: .named("MainWindowCoordSpace"))
                .onChanged { val in
                    if val.translation.height > 0 {
                        let prog = min(1.0, max(0.0, 1.0 - (val.translation.height / 380.0)))
                        NowPlayingDrawerState.shared.setInteractiveProgress(prog, wasOpenAtStart: true) { shouldOpen in
                            if shouldOpen { vm.openNowPlayingStage() } else { vm.closeNowPlayingStage() }
                        }
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

    // MARK: - Left Stage View
    private func leftStageView(availableW: CGFloat, availableH: CGFloat, artSize: CGFloat) -> some View {
        VStack(spacing: max(6, min(12, availableH * 0.016))) {
            Spacer(minLength: 4)

            // Hero Artwork (Non-interactive visual display)
            let isPlayingActive = engine.isPlaying && !engine.isPaused
            let cornerRad = max(14, artSize * 0.08)

            ZStack {
                // Soft theme-colored ambient glow behind artwork
                RoundedRectangle(cornerRadius: cornerRad, style: .continuous)
                    .fill(theme.primary.opacity(isPlayingActive ? 0.22 : 0.06))
                    .frame(width: artSize * 0.88, height: artSize * 0.88)
                    .blur(radius: isPlayingActive ? 32 : 16)
                    .offset(y: isPlayingActive ? 12 : 6)
                    .animation(.spring(response: 0.48, dampingFraction: 0.78), value: isPlayingActive)

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
                .id(engine.currentTrack?.path ?? "empty-art")
                .transition(.opacity.combined(with: .scale(scale: 0.96)))
                .frame(width: artSize, height: artSize)
                .continuousSquircle(cornerRad)
                .overlay(
                    RoundedRectangle(cornerRadius: cornerRad, style: .continuous)
                        .strokeBorder(
                            LinearGradient(
                                colors: [Color.white.opacity(0.40), Color.white.opacity(0.06), theme.primary.opacity(0.25)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1.25
                        )
                )
                .shadow(
                    color: Color.black.opacity(isPlayingActive ? 0.45 : 0.22),
                    radius: isPlayingActive ? 28 : 14,
                    x: 0,
                    y: isPlayingActive ? 14 : 7
                )
                // Scale effect applied LAST - whole physical object scales together without ghost outline!
                .scaleEffect(isPlayingActive ? 1.0 : 0.91)
                .animation(.spring(response: 0.45, dampingFraction: 0.76), value: isPlayingActive)
                .animation(.spring(response: 0.42, dampingFraction: 0.84), value: engine.currentTrack?.path)
            }

            Spacer(minLength: 4)

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
                .id(engine.currentTrack?.path ?? "no-track-info")
                .animation(.spring(response: 0.38, dampingFraction: 0.84), value: engine.currentTrack?.path)

                Spacer(minLength: 4)

                if let track = engine.currentTrack {
                    Button(action: {
                        withAnimation(.spring(response: 0.30, dampingFraction: 0.65)) {
                            vm.toggleLike(track: track)
                        }
                    }) {
                        Image(systemName: vm.isLiked(track: track) ? "heart.fill" : "heart")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(vm.isLiked(track: track) ? Color.pink : Color.secondary)
                            .frame(width: 30, height: 30)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(TsrossaScaleButtonStyle(scaleAmount: 0.85))
                    .help("Favorite / Like Track")
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // Scrubber Bar
            PlaybackScrubberView(accentColor: theme.primary)
                .padding(.vertical, 2)

            // 5-Button Studio Transport Deck
            transportDeckView

            // Volume, DAC & Live Signal Footer Card
            volumeAndDacDeckView
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
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
            .buttonStyle(TsrossaScaleButtonStyle())
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
            .buttonStyle(TsrossaScaleButtonStyle())
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
            .buttonStyle(TsrossaScaleButtonStyle(scaleAmount: 0.92))
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
            .buttonStyle(TsrossaScaleButtonStyle())
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
            .buttonStyle(TsrossaScaleButtonStyle())
            .help("Repeat Mode (⌘R)")
        }
    }

    // MARK: - Volume, DAC & Live Signal Footer Card
    private var volumeAndDacDeckView: some View {
        VStack(spacing: 8) {
            // Row 1: Volume Slider + Percentage + DAC Device Selector
            HStack(spacing: 10) {
                Button(action: {
                    if engine.volume > 0 {
                        engine.volume = 0.0
                    } else {
                        engine.volume = 0.7
                    }
                }) {
                    Image(systemName: engine.volume <= 0.01 ? "speaker.slash.fill" : (engine.volume < 0.5 ? "speaker.wave.1.fill" : "speaker.wave.2.fill"))
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(engine.volume <= 0.01 ? Color.red.opacity(0.85) : .secondary)
                        .frame(width: 20, height: 20)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())
                .help("Mute / Unmute Volume")

                Slider(value: $engine.volume, in: 0.0...1.0)
                    .accentColor(theme.primary)
                    .frame(minWidth: 80, maxWidth: .infinity)

                Text("\(Int((engine.volume * 100).rounded()))%")
                    .font(.system(size: 10.5, weight: .bold, design: .monospaced))
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
                    .frame(width: 34, alignment: .trailing)

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
                            .foregroundStyle(theme.primary)
                        Text(engine.devices.first(where: { $0.id == engine.selectedDeviceId })?.name ?? "Default Output")
                            .font(.system(size: 10.5, weight: .semibold))
                            .lineLimit(1)
                    }
                    .foregroundStyle(.primary)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 5)
                    .background(Color.white.opacity(0.08))
                    .continuousSquircle(8)
                }
                .menuStyle(BorderlessButtonMenuStyle())
                .frame(maxWidth: 145, alignment: .trailing)
            }

            Divider()
                .background(Color.white.opacity(0.08))

            // Row 2: Dense Audiophile Signal & Format Strip (Eliminates Empty Bottom Space!)
            HStack(spacing: 8) {
                let ext = engine.currentTrack.map { URL(fileURLWithPath: $0.path).pathExtension.uppercased() } ?? "IDLE"
                let srText = engine.currentTrack.map { String(format: "%.1f kHz", Double($0.sampleRate) / 1000.0) } ?? "—"
                let bitText = engine.currentTrack.map { "\($0.bitDepth)-bit" } ?? "—"
                let chText = engine.currentTrack.map { $0.channels == 1 ? "Mono" : ($0.channels == 2 ? "Stereo" : "\($0.channels)ch") } ?? "Stereo"

                // Codec Badge
                HStack(spacing: 4) {
                    Image(systemName: "waveform")
                        .font(.system(size: 9.5, weight: .bold))
                        .foregroundStyle(theme.primary)
                    Text(ext.isEmpty ? "AUDIO" : ext)
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundStyle(.primary)
                }
                .padding(.horizontal, 7)
                .padding(.vertical, 3.5)
                .background(theme.primary.opacity(0.14))
                .continuousSquircle(6)

                // Sample Rate & Bit Depth
                Text("\(srText) • \(bitText) • \(chText)")
                    .font(.system(size: 10, weight: .medium, design: .monospaced))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                Spacer(minLength: 4)

                // Bit-Perfect / Telemetry Trigger Pill
                Button(action: {
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) {
                        vm.showTelemetry = true
                    }
                }) {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(engine.telemetrySnapshot.hogModeStatus == "EXCLUSIVE_HOG" ? Color.green : Color.mint)
                            .frame(width: 5.5, height: 5.5)
                        Text(engine.telemetrySnapshot.streamIsInteger ? "INT MODE" : "FLOAT32")
                            .font(.system(size: 9.5, weight: .bold, design: .monospaced))
                            .foregroundStyle(.secondary)
                    }
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3.5)
                    .background(Color.white.opacity(0.05))
                    .continuousSquircle(6)
                }
                .buttonStyle(PlainButtonStyle())
                .help("Inspect CoreAudio Hardware Telemetry")
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(
            ZStack {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(theme.surfaceContainer.opacity(0.55))
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(.ultraThinMaterial)
                    .opacity(0.40)
            }
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(
                    theme.outlineVariant.opacity(0.45),
                    lineWidth: 1
                )
        )
        .continuousSquircle(16)
    }
}
