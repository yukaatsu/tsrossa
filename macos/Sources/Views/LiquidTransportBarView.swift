import SwiftUI

public struct LiquidTransportBarView: View {
    @ObservedObject var engine = AudioEngine.shared
    @ObservedObject var vm = AppViewModel.shared
    public var theme: LiquidGlassTheme

    public init(theme: LiquidGlassTheme) {
        self.theme = theme
    }

    public var body: some View {
        HStack(spacing: 16) {
            // Left: Concentric Squircle Mini Artwork & Track Info
            HStack(spacing: 12) {
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
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .frame(width: 44, height: 44)
                    .continuousSquircle(ConcentricSquircle.innerRadius(outerRadius: 24, padding: 8))

                    VStack(alignment: .leading, spacing: 3) {
                        Text(track.title)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.primary)
                            .lineLimit(1)

                        Text(track.artist)
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: 160, alignment: .leading)
                } else {
                    HStack(spacing: 10) {
                        TsrossaBrandIcon(size: 38, cornerRadius: 10)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("TSRossa")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(.primary)
                            Text("Ready to Play")
                                .font(.system(size: 11))
                                .foregroundStyle(.secondary)
                        }
                    }
                    .frame(maxWidth: 160, alignment: .leading)
                }
            }

            // Center: Playback Controls & Scrubber Timeline
            VStack(spacing: 4) {
                HStack(spacing: 18) {
                    // Shuffle Button
                    Button(action: { vm.isShuffle.toggle() }) {
                        Image(systemName: "shuffle")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(vm.isShuffle ? theme.primary : .secondary)
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())

                    // Previous Track Button
                    Button(action: { vm.playPreviousTrack() }) {
                        Image(systemName: "backward.fill")
                            .font(.system(size: 14))
                            .foregroundStyle(.primary)
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())

                    // Play / Pause Hero Button
                    Button(action: { engine.togglePlayPause() }) {
                        ZStack {
                            Circle()
                                .fill(theme.primary)
                                .frame(width: 36, height: 36)
                            Image(systemName: engine.isPlaying && !engine.isPaused ? "pause.fill" : "play.fill")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(theme.onPrimary)
                                .offset(x: engine.isPlaying && !engine.isPaused ? 0 : 1)
                        }
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())

                    // Next Track Button
                    Button(action: { vm.playNextTrack(userInitiated: true) }) {
                        Image(systemName: "forward.fill")
                            .font(.system(size: 14))
                            .foregroundStyle(.primary)
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())

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
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(vm.repeatMode != .off ? theme.primary : .secondary)
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())
                }

                // Scrubber View
                PlaybackScrubberView(accentColor: theme.primary)
                    .frame(maxWidth: 440)
            }

            Spacer(minLength: 12)

            // Right: Hardware Status Badge & Studio Volume Slider
            HStack(spacing: 12) {
                if let track = engine.currentTrack {
                    VStack(alignment: .trailing, spacing: 2) {
                        HStack(spacing: 4) {
                            Circle()
                                .fill(engine.telemetrySnapshot.hogModeStatus == "EXCLUSIVE_HOG" ? Color.green : Color.orange)
                                .frame(width: 6, height: 6)
                            Text(engine.telemetrySnapshot.streamIsInteger ? "BIT-PERFECT" : "DSP ACTIVE")
                                .font(.system(size: 9, weight: .bold, design: .monospaced))
                                .foregroundStyle(.primary)
                        }
                        Text("\(track.sampleRate / 1000) kHz / \(track.bitDepth)-bit")
                            .font(.system(size: 10, design: .monospaced))
                            .monospacedDigit()
                            .foregroundStyle(.secondary)
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.white.opacity(0.06))
                    .continuousSquircle(8)
                }

                // Volume Slider
                HStack(spacing: 6) {
                    Image(systemName: vm.volumeDb <= -60 ? "speaker.slash.fill" : (vm.volumeDb < -20 ? "speaker.1.fill" : "speaker.wave.2.fill"))
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)

                    Slider(
                        value: Binding(
                            get: { Double(vm.volumeDb) },
                            set: { vm.volumeDb = Int($0) }
                        ),
                        in: -60...0
                    )
                    .frame(width: 80)
                    .tint(theme.primary)

                    Text("\(vm.volumeDb) dB")
                        .font(.system(size: 10, design: .monospaced))
                        .monospacedDigit()
                        .foregroundStyle(.secondary)
                        .frame(width: 36, alignment: .trailing)
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
        .liquidGlass(role: .transportCapsule, cornerRadius: 24, tint: theme.primary)
    }
}
