import SwiftUI

public final class ScrubberState: ObservableObject {
    @Published public var dragFraction: Double? = nil
    @Published public var isHovering: Bool = false

    public init() {}
}

public struct PlaybackScrubberView: View {
    @ObservedObject var engine = AudioEngine.shared
    @ObservedObject var state = ScrubberState()
    public var accentColor: Color

    public init(accentColor: Color = .pink) {
        self.accentColor = accentColor
    }

    public var body: some View {
        let duration = max(engine.totalDuration, 0.001)
        let displayedTime = state.dragFraction.map { $0 * duration } ?? engine.currentTime
        let progress = min(max(displayedTime / duration, 0.0), 1.0)

        HStack(spacing: 8) {
            Text(formatTime(displayedTime))
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .monospacedDigit()
                .foregroundStyle(.secondary)
                .frame(width: 44, alignment: .trailing)

            GeometryReader { geo in
                let width = max(geo.size.width, 1)
                ZStack(alignment: .leading) {
                    Capsule(style: .continuous)
                        .fill(Color.primary.opacity(0.12))
                        .frame(height: state.isHovering || state.dragFraction != nil ? 6 : 4)

                    Capsule(style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [accentColor, accentColor.opacity(0.85)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: width * CGFloat(progress), height: state.isHovering || state.dragFraction != nil ? 6 : 4)

                    if state.isHovering || state.dragFraction != nil {
                        Circle()
                            .fill(Color.white)
                            .frame(width: 12, height: 12)
                            .shadow(color: Color.black.opacity(0.35), radius: 3, x: 0, y: 1)
                            .offset(x: max(0, min(width - 12, width * CGFloat(progress) - 6)))
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
                            engine.isSeeking = true
                            let fraction = min(max(0.0, Double(value.location.x / width)), 1.0)
                            state.dragFraction = fraction
                        }
                        .onEnded { value in
                            let fraction = min(max(0.0, Double(value.location.x / width)), 1.0)
                            let targetTime = fraction * engine.totalDuration
                            state.dragFraction = nil
                            engine.isSeeking = false
                            engine.seek(to: targetTime)
                        }
                )
            }
            .frame(height: 18)

            Text(formatTime(engine.totalDuration))
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .monospacedDigit()
                .foregroundStyle(.secondary)
                .frame(width: 44, alignment: .leading)
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
