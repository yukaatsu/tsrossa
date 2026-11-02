import SwiftUI

public struct NowPlayingSplitView: View {
    @ObservedObject var engine = AudioEngine.shared
    @ObservedObject var vm = AppViewModel.shared
    public var theme: LiquidGlassTheme

    public init(theme: LiquidGlassTheme) {
        self.theme = theme
    }

    public var body: some View {
        GeometryReader { geo in
            let availableH = geo.size.height
            let availableW = geo.size.width
            let artSize = min(300.0, max(140.0, min(availableW * 0.38, availableH - 180.0)))

            HStack(spacing: max(16, min(32, availableW * 0.03))) {
                // Left: Hero Artwork & Audio Attributes
                VStack(spacing: max(10, min(20, availableH * 0.03))) {
                    Spacer(minLength: 8)

                    // Hero Artwork with Specular Rim & Liquid Glass glow
                    Group {
                        if let track = engine.currentTrack, let art = track.artwork {
                            Image(nsImage: art)
                                .resizable()
                                .scaledToFit()
                        } else {
                            ZStack {
                                Color.white.opacity(0.06)
                                TsrossaBrandIcon(size: 80, cornerRadius: 20)
                            }
                        }
                    }
                    .frame(width: artSize, height: artSize)
                    .scaleEffect(engine.isPlaying && !engine.isPaused ? 1.0 : 0.94)
                    .animation(.spring(response: 0.45, dampingFraction: 0.75), value: engine.isPlaying && !engine.isPaused)
                    .continuousSquircle(max(14, artSize * 0.075))
                    .overlay(
                        RoundedRectangle(cornerRadius: max(14, artSize * 0.075), style: .continuous)
                            .strokeBorder(
                                LinearGradient(
                                    colors: [Color.white.opacity(0.40), Color.white.opacity(0.05), theme.primary.opacity(0.3)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                ),
                                lineWidth: 1
                            )
                    )
                    .shadow(color: Color.black.opacity(engine.isPlaying && !engine.isPaused ? 0.45 : 0.20), radius: 24, x: 0, y: 12)

                    // Track Identity
                    if let track = engine.currentTrack {
                        VStack(spacing: 3) {
                            Text(track.title)
                                .font(.system(size: max(15, min(20, availableW * 0.022)), weight: .bold))
                                .foregroundStyle(.primary)
                                .lineLimit(1)

                            Text(track.artist)
                                .font(.system(size: max(12, min(14, availableW * 0.016))))
                                .foregroundStyle(.secondary)
                                .lineLimit(1)

                            Text(track.album)
                                .font(.system(size: 11))
                                .foregroundStyle(.tertiary)
                                .lineLimit(1)
                        }

                        // Audiophile Badges (Single line, fixedSize to never stack)
                        HStack(spacing: 6) {
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
                        }
                        .padding(.top, 2)
                    } else {
                        VStack(spacing: 4) {
                            Text("No Track Playing")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundStyle(.primary)
                            Text("Select a song from your library to begin.")
                                .font(.system(size: 12))
                                .foregroundStyle(.secondary)
                        }
                    }

                    Spacer(minLength: 8)
                }
                .frame(maxWidth: max(240, min(420, availableW * 0.46)))
                .padding(.leading, max(12, min(24, availableW * 0.025)))

                // Right: Integrated Queue & Lyrics View
                QueueLyricsView(theme: theme)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(.trailing, max(12, min(24, availableW * 0.025)))
            }
            .frame(width: availableW, height: availableH)
        }
        .padding(.bottom, 16)
    }

    private func badgeView(text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 9.5, weight: .bold, design: .monospaced))
            .foregroundStyle(color)
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(color.opacity(0.12))
            .continuousSquircle(6)
    }
}
