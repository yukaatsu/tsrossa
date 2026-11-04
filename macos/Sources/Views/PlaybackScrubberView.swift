import SwiftUI

public final class ScrubberState: ObservableObject {
    public static let compactShared = ScrubberState()
    public static let stageShared = ScrubberState()

    @Published public var dragFraction: Double? = nil
    @Published public var isHovering: Bool = false

    public init() {}
}

public struct PlaybackScrubberView: View {
    @ObservedObject var progress = AudioEngine.shared.progress
    @ObservedObject var state: ScrubberState
    public var accentColor: Color
    public var compact: Bool

    public init(accentColor: Color = .pink, compact: Bool = false) {
        self.accentColor = accentColor
        self.compact = compact
        self.state = compact ? ScrubberState.compactShared : ScrubberState.stageShared
    }

    public var body: some View {
        let duration = max(progress.totalDuration, 0.001)
        let displayedTime = state.dragFraction.map { $0 * duration } ?? progress.currentTime
        let progressFraction = min(max(displayedTime / duration, 0.0), 1.0)
        let timeWidth: CGFloat = compact ? 34 : 44
        let timeFontSize: CGFloat = compact ? 9.5 : 11
        let geoHeight: CGFloat = compact ? 12 : 18
        let barH: CGFloat = compact ? (state.isHovering || state.dragFraction != nil ? 4.8 : 3.0) : (state.isHovering || state.dragFraction != nil ? 6 : 4)
        let thumbDim: CGFloat = compact ? 10 : 12

        HStack(spacing: compact ? 6 : 8) {
            Text(formatTime(displayedTime))
                .font(.system(size: timeFontSize, weight: .medium, design: .monospaced))
                .monospacedDigit()
                .foregroundStyle(.secondary)
                .frame(width: timeWidth, alignment: .trailing)

            GeometryReader { geo in
                let width = max(geo.size.width, 1)
                ZStack(alignment: .leading) {
                    Capsule(style: .continuous)
                        .fill(Color.primary.opacity(0.12))
                        .frame(height: barH)

                    Capsule(style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [accentColor, accentColor.opacity(0.85)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: width * CGFloat(progressFraction), height: barH)

                    if state.isHovering || state.dragFraction != nil {
                        Circle()
                            .fill(Color.white)
                            .frame(width: thumbDim, height: thumbDim)
                            .shadow(color: Color.black.opacity(0.35), radius: 3, x: 0, y: 1)
                            .offset(x: max(0, min(width - thumbDim, width * CGFloat(progressFraction) - (thumbDim / 2))))
                    }
                }
                .frame(maxHeight: .infinity)
                .contentShape(Rectangle())
                .onHover { hovering in
                    withAnimation(.spring(response: 0.22, dampingFraction: 0.8)) {
                        state.isHovering = hovering
                    }
                }
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            progress.isSeeking = true
                            let fraction = min(max(0.0, Double(value.location.x / width)), 1.0)
                            state.dragFraction = fraction
                        }
                        .onEnded { value in
                            let fraction = min(max(0.0, Double(value.location.x / width)), 1.0)
                            let targetTime = fraction * progress.totalDuration
                            state.dragFraction = nil
                            progress.isSeeking = false
                            AudioEngine.shared.seek(to: targetTime)
                        }
                )
            }
            .frame(height: geoHeight)

            Text(formatTime(progress.totalDuration))
                .font(.system(size: timeFontSize, weight: .medium, design: .monospaced))
                .monospacedDigit()
                .foregroundStyle(.secondary)
                .frame(width: timeWidth, alignment: .leading)
        }
    }

    private func formatTime(_ seconds: Double) -> String {
        guard seconds.isFinite && seconds > 0 else { return "0:00" }
        let totalSec = Int(seconds.rounded(.down))
        let mins = totalSec / 60
        let secs = totalSec % 60
        return String(format: "%d:%02d", mins, secs)
    }
}
