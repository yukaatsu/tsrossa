import SwiftUI

public struct LiquidTransportBarView: View {
    @ObservedObject var engine = AudioEngine.shared
    @ObservedObject var vm = AppViewModel.shared
    public var theme: LiquidGlassTheme

    public init(theme: LiquidGlassTheme) {
        self.theme = theme
    }

    public var body: some View {
        HStack(alignment: .center, spacing: 10) {
            // 1. Left: Artwork & Track Info
            leftTrackInfo
                .frame(minWidth: 96, idealWidth: 155, maxWidth: 195, alignment: .leading)

            // Subtle vertical divider
            Rectangle()
                .fill(Color.white.opacity(0.08))
                .frame(width: 1, height: 26)

            // 2. Center-Left: 5 Transport Buttons (Inline Horizontal — Never Stacked!)
            transportButtons
                .fixedSize(horizontal: true, vertical: false)

            // 3. Center-Right: Dedicated Inline Seekbar Scrubber (Zero Overlap with Buttons!)
            PlaybackScrubberView(accentColor: theme.primary, compact: true)
                .frame(minWidth: 80, idealWidth: 240, maxWidth: .infinity)
                .layoutPriority(1)

            // Subtle vertical divider
            Rectangle()
                .fill(Color.white.opacity(0.08))
                .frame(width: 1, height: 26)

            // 4. Right: Hardware Status Badge, Volume & Expand
            rightControls
                .fixedSize(horizontal: true, vertical: false)
        }
        .padding(.horizontal, 14)
        .frame(height: 62)
        .liquidGlass(role: .transportCapsule, cornerRadius: 20, tint: theme.primary)
        .simultaneousGesture(
            DragGesture(minimumDistance: 6, coordinateSpace: .named("MainWindowCoordSpace"))
                .onChanged { val in
                    if val.translation.height < 0 {
                        let prog = min(1.0, max(0.0, -val.translation.height / 380.0))
                        NowPlayingDrawerState.shared.setInteractiveProgress(prog, wasOpenAtStart: false) { shouldOpen in
                            if shouldOpen { vm.openNowPlayingStage() } else { vm.closeNowPlayingStage() }
                        }
                    }
                }
                .onEnded { val in
                    let shouldOpen = val.translation.height < -25 || val.predictedEndTranslation.height < -50
                    if shouldOpen {
                        vm.openNowPlayingStage()
                    } else {
                        vm.closeNowPlayingStage()
                    }
                }
        )
    }

    // MARK: - Left: Track Info
    private var leftTrackInfo: some View {
        Button(action: {
            vm.openNowPlayingStage()
        }) {
            HStack(spacing: 10) {
                if let track = engine.currentTrack {
                    Group {
                        if let art = track.artwork {
                            Image(nsImage: art)
                                .resizable()
                                .scaledToFill()
                        } else {
                            ZStack {
                                Color.white.opacity(0.08)
                                Image(systemName: "music.note")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .frame(width: 40, height: 40)
                    .continuousSquircle(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .strokeBorder(Color.white.opacity(0.12), lineWidth: 1)
                    )

                    VStack(alignment: .leading, spacing: 1) {
                        Text(track.title)
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(.primary)
                            .lineLimit(1)

                        Text(track.artist)
                            .font(.system(size: 10.5))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .layoutPriority(-1)
                } else {
                    HStack(spacing: 8) {
                        TsrossaBrandIcon(size: 34, cornerRadius: 8)
                        VStack(alignment: .leading, spacing: 1) {
                            Text("tsrossa")
                                .font(.system(size: 12.5, weight: .bold))
                                .foregroundStyle(.primary)
                            Text("Ready to Play")
                                .font(.system(size: 10.5))
                                .foregroundStyle(.secondary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .id(engine.currentTrack?.path ?? "idle-track")
            .animation(.spring(response: 0.38, dampingFraction: 0.84), value: engine.currentTrack?.path)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }

    // MARK: - Center: Inline Transport Buttons (Shuffle, Prev, Play/Pause, Next, Repeat)
    private var transportButtons: some View {
        HStack(alignment: .center, spacing: 6) {
            // Shuffle Button
            Button(action: { vm.isShuffle.toggle() }) {
                Image(systemName: "shuffle")
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(vm.isShuffle ? theme.primary : .secondary)
                    .frame(width: 26, height: 26)
                    .background(vm.isShuffle ? theme.primary.opacity(0.14) : Color.clear)
                    .clipShape(Circle())
                    .contentShape(Circle())
            }
            .buttonStyle(TsrossaScaleButtonStyle())
            .help("Toggle Shuffle (⌘S)")

            // Previous Track Button
            Button(action: { vm.playPreviousTrack() }) {
                Image(systemName: "backward.fill")
                    .font(.system(size: 12.5))
                    .foregroundStyle(.primary)
                    .frame(width: 26, height: 26)
                    .contentShape(Rectangle())
            }
            .buttonStyle(TsrossaScaleButtonStyle())
            .help("Previous Track (⌘←)")

            // Play / Pause Circle Button
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
                        .frame(width: 32, height: 32)
                        .shadow(color: theme.primary.opacity(0.35), radius: 4, y: 1)
                    Image(systemName: engine.isPlaying && !engine.isPaused ? "pause.fill" : "play.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(theme.onPrimary)
                        .offset(x: engine.isPlaying && !engine.isPaused ? 0 : 1)
                }
                .frame(width: 34, height: 34)
                .contentShape(Circle())
            }
            .buttonStyle(TsrossaScaleButtonStyle(scaleAmount: 0.90))
            .help("Play / Pause (Space)")

            // Next Track Button
            Button(action: { vm.playNextTrack(userInitiated: true) }) {
                Image(systemName: "forward.fill")
                    .font(.system(size: 12.5))
                    .foregroundStyle(.primary)
                    .frame(width: 26, height: 26)
                    .contentShape(Rectangle())
            }
            .buttonStyle(TsrossaScaleButtonStyle())
            .help("Next Track (⌘→)")

            // Repeat 3-State Button
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
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(vm.repeatMode != .off ? theme.primary : .secondary)
                    .frame(width: 26, height: 26)
                    .background(vm.repeatMode != .off ? theme.primary.opacity(0.14) : Color.clear)
                    .clipShape(Circle())
                    .contentShape(Circle())
            }
            .buttonStyle(TsrossaScaleButtonStyle())
            .help("Toggle Repeat (⌘T)")
        }
    }

    // MARK: - Right: Controls
    private var rightControls: some View {
        HStack(spacing: 8) {
            if let track = engine.currentTrack {
                // Compact Single-Row Audio Spec Pill
                HStack(spacing: 4) {
                    Circle()
                        .fill(engine.telemetrySnapshot.hogModeStatus == "EXCLUSIVE_HOG" ? Color.green : Color.orange)
                        .frame(width: 5, height: 5)
                    Text(engine.telemetrySnapshot.streamIsInteger ? "BIT-PERFECT" : "\(track.sampleRate / 1000)k/\(track.bitDepth)b")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                .padding(.horizontal, 7)
                .padding(.vertical, 3.5)
                .background(Color.white.opacity(0.06))
                .continuousSquircle(6)
            }

            // Volume Slider
            HStack(spacing: 5) {
                Image(systemName: vm.volumeDb <= -60 ? "speaker.slash.fill" : (vm.volumeDb < -20 ? "speaker.1.fill" : "speaker.wave.2.fill"))
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)

                Slider(
                    value: Binding(
                        get: { Double(vm.volumeDb) },
                        set: { vm.volumeDb = Int($0) }
                    ),
                    in: -60...0
                )
                .frame(width: 52)
                .tint(theme.primary)

                Text("\(vm.volumeDb) dB")
                    .font(.system(size: 9.5, design: .monospaced))
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
                    .frame(width: 30, alignment: .trailing)
            }

            // Expand Chevron Button
            Button(action: {
                vm.toggleNowPlayingStage()
            }) {
                Image(systemName: "chevron.up")
                    .font(.system(size: 10.5, weight: .bold))
                    .foregroundStyle(.secondary)
                    .frame(width: 24, height: 24)
                    .background(Color.white.opacity(0.06))
                    .continuousSquircle(6)
                    .contentShape(Rectangle())
            }
            .buttonStyle(TsrossaScaleButtonStyle())
            .help("Open Full Now Playing Stage (⌘P)")
        }
    }
}
