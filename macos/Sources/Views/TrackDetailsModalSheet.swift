import SwiftUI

public struct TrackDetailsModalSheet: View {
    @ObservedObject var vm = AppViewModel.shared
    @ObservedObject var engine = AudioEngine.shared
    public var theme: LiquidGlassTheme

    public init(theme: LiquidGlassTheme) {
        self.theme = theme
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                HStack(spacing: 8) {
                    Image(systemName: "info.circle.fill")
                        .font(.system(size: 16))
                        .foregroundStyle(theme.primary)
                    Text("Audio Track Details")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.primary)
                }
                Spacer()
                Button(action: {
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) {
                        vm.showTrackDetailsModal = false
                    }
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(TsrossaScaleButtonStyle())
                .frame(width: 28, height: 28)
                .contentShape(Rectangle())
            }
            .padding(20)

            Divider()

            ScrollView {
                if let track = engine.currentTrack {
                    VStack(alignment: .leading, spacing: 16) {
                        // Artwork & Basic Info
                        HStack(spacing: 16) {
                            Group {
                                if let img = track.artwork {
                                    Image(nsImage: img)
                                        .resizable()
                                        .scaledToFit()
                                } else {
                                    Color.white.opacity(0.08)
                                }
                            }
                            .frame(width: 80, height: 80)
                            .continuousSquircle(12)

                            VStack(alignment: .leading, spacing: 4) {
                                Text(track.title)
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundStyle(.primary)
                                Text(track.artist)
                                    .font(.system(size: 13))
                                    .foregroundStyle(.secondary)
                                Text(track.album)
                                    .font(.system(size: 12))
                                    .foregroundStyle(.tertiary)
                            }
                        }

                        Divider()

                        // Technical Specifications Grid
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Technical Specifications")
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                .foregroundStyle(theme.primary)

                            specRow(label: "Sample Rate", value: "\(track.sampleRate) Hz (\(track.sampleRate / 1000) kHz)")
                            specRow(label: "Bit Depth", value: "\(track.bitDepth)-Bit Integer")
                            specRow(label: "Channels", value: track.channels == 2 ? "2 Channels (Stereo)" : "\(track.channels) Channels")
                            specRow(label: "Duration", value: String(format: "%.2f seconds", track.durationSeconds))
                            specRow(label: "File Size", value: formatFileSize(track.fileSize))
                            specRow(label: "File Path", value: track.path)
                            specRow(label: "MQA Authenticated", value: track.isMqa ? "YES (Original Master)" : "NO")
                        }
                    }
                    .padding(20)
                } else {
                    Text("No track currently selected.")
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                        .padding(40)
                }
            }
        }
        .frame(minWidth: 340, idealWidth: 460, maxWidth: 520, minHeight: 280, idealHeight: 420, maxHeight: 520)
        .background(theme.surface.opacity(0.94))
        .background(.ultraThinMaterial)
        .continuousSquircle(20)
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(Color.white.opacity(0.12), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.5), radius: 24, x: 0, y: 12)
        .padding(24)
    }

    private func specRow(label: String, value: String) -> some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.system(size: 11, design: .monospaced))
                .foregroundStyle(.secondary)
                .frame(width: 140, alignment: .leading)
            Text(value)
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .foregroundStyle(.primary)
                .textSelection(.enabled)
            Spacer()
        }
    }

    private func formatFileSize(_ bytes: Int64) -> String {
        guard bytes > 0 else { return "Unknown" }
        let mb = Double(bytes) / (1024.0 * 1024.0)
        return String(format: "%.2f MB (%d bytes)", mb, bytes)
    }
}
