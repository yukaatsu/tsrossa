import Foundation
import Combine
import SwiftUI
import QuartzCore

public final class NowPlayingDrawerState: ObservableObject {
    public static let shared = NowPlayingDrawerState()

    /// Continuous progress from 0.0 (fully closed at bottom) to 1.0 (fully open at top).
    @Published public var revealProgress: CGFloat = 0.0

    /// True while two fingers are actively dragging the drawer on the trackpad.
    @Published public var isDragging: Bool = false

    /// Real-time frame of the Right List (Queue / Lyrics ScrollView) in MainWindowCoordSpace
    public var queuePanelFrameInWindow: CGRect = .zero

    private var animTimer: DispatchSourceTimer?
    private var watchdogTimer: DispatchSourceTimer?

    private init() {}

    /// Smoothly animates revealProgress from its current position to target (0.0 or 1.0) at 120Hz.
    public func animateTo(_ target: CGFloat, duration: TimeInterval = 0.46) {
        cancelWatchdog()
        animTimer?.cancel()
        animTimer = nil
        isDragging = false

        let clampedTarget = min(1.0, max(0.0, target))
        let startValue = revealProgress
        let distance = abs(clampedTarget - startValue)

        if distance < 0.002 {
            revealProgress = clampedTarget
            return
        }

        // Scale duration proportionally to remaining distance (min 0.30s, max duration)
        // so even after a partial drag, the remaining glide is clearly visible and graceful!
        let effectiveDuration = max(0.30, duration * Double(max(0.65, distance)))
        let startTime = CACurrentMediaTime()

        let timer = DispatchSource.makeTimerSource(flags: .strict, queue: .main)
        timer.schedule(deadline: .now(), repeating: .microseconds(8333), leeway: .microseconds(400))

        timer.setEventHandler { [weak self] in
            guard let self = self else { return }
            let elapsed = CACurrentMediaTime() - startTime
            let rawT = min(1.0, max(0.0, elapsed / effectiveDuration))

            // Smooth S-curve blended with cubic ease-out for silky deceleration
            let eased = CGFloat(0.5 - 0.5 * cos(Double.pi * pow(rawT, 0.82)))
            self.revealProgress = startValue + (clampedTarget - startValue) * eased

            if rawT >= 1.0 {
                self.animTimer?.cancel()
                self.animTimer = nil
                self.revealProgress = clampedTarget
            }
        }

        animTimer = timer
        timer.resume()
    }

    /// Updates revealProgress during active finger drag and arms the failsafe watchdog.
    public func setInteractiveProgress(_ next: CGFloat, wasOpenAtStart: Bool, onAutoSnap: @escaping (Bool) -> Void) {
        animTimer?.cancel()
        animTimer = nil
        isDragging = true
        revealProgress = min(1.0, max(0.0, next))
        armWatchdog(wasOpenAtStart: wasOpenAtStart, onAutoSnap: onAutoSnap)
    }

    public func cancelWatchdog() {
        watchdogTimer?.cancel()
        watchdogTimer = nil
    }

    private func armWatchdog(wasOpenAtStart: Bool, onAutoSnap: @escaping (Bool) -> Void) {
        watchdogTimer?.cancel()
        let timer = DispatchSource.makeTimerSource(queue: .main)
        timer.schedule(deadline: .now() + .milliseconds(150))
        timer.setEventHandler { [weak self] in
            guard let self = self else { return }
            self.watchdogTimer?.cancel()
            self.watchdogTimer = nil
            if self.isDragging || (self.revealProgress > 0.001 && self.revealProgress < 0.999) {
                self.isDragging = false
                let shouldOpen: Bool
                if wasOpenAtStart {
                    shouldOpen = self.revealProgress > 0.82
                } else {
                    shouldOpen = self.revealProgress > 0.18
                }
                onAutoSnap(shouldOpen)
            }
        }
        watchdogTimer = timer
        timer.resume()
    }
}
