import SwiftUI

public struct NowPlayingSplitView: View {
    @ObservedObject var engine = AudioEngine.shared
    @ObservedObject var vm = AppViewModel.shared
    public var theme: LiquidGlassTheme

    public init(theme: LiquidGlassTheme) {
        self.theme = theme
    }

    public var body: some View {
        HStack(spacing: 28) {
            // Left: Hero Artwork & Audio Attributes
            VStack(spacing: 20) {
                Spacer()

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
                .frame(maxWidth: 320, maxHeight: 320)
                .continuousSquircle(24)
                .overlay(
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .strokeBorder(
                            LinearGradient(
                                colors: [Color.white.opacity(0.40), Color.white.opacity(0.05), theme.primary.opacity(0.3)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1
                        )
                )
                .shadow(color: Color.black.opacity(0.4), radius: 24, x: 0, y: 12)

                // Track Identity
                if let track = engine.currentTrack {
                    VStack(spacing: 4) {
                        Text(track.title)
                            .font(.system(size: 20, weight: .bold))
                            .foregroundStyle(.primary)
                            .lineLimit(1)

                        Text(track.artist)
                            .font(.system(size: 14))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)

                        Text(track.album)
                            .font(.system(size: 12))
                            .foregroundStyle(.tertiary)
                            .lineLimit(1)
                    }

                    // Audiophile Badges
                    HStack(spacing: 8) {
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
                    .padding(.top, 4)
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

                Spacer()
            }
            .frame(maxWidth: 420)
            .padding(.leading, 24)

            // Right: Integrated Queue & Lyrics View
            QueueLyricsView(theme: theme)
                .frame(maxWidth: .infinity)
                .padding(.trailing, 24)
        }
        .padding(.bottom, 16)
    }

    private func badgeView(text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 10, weight: .bold, design: .monospaced))
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.12))
            .continuousSquircle(6)
    }
}
