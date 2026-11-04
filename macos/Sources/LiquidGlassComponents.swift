import SwiftUI
import AppKit
import QuartzCore

public enum LiquidGlassRole: Sendable {
    case navigationSidebar
    case floatingPillBar
    case transportCapsule
    case bentoCard
    case modalSheet
    case interactiveChip
}

public enum ConcentricSquircle {
    public static func innerRadius(outerRadius: CGFloat, padding: CGFloat, minimum: CGFloat = 6) -> CGFloat {
        max(minimum, outerRadius - padding)
    }
}

public struct MacVisualEffectView: NSViewRepresentable {
    public var material: NSVisualEffectView.Material
    public var blendingMode: NSVisualEffectView.BlendingMode
    public var state: NSVisualEffectView.State

    public init(
        material: NSVisualEffectView.Material = .hudWindow,
        blendingMode: NSVisualEffectView.BlendingMode = .withinWindow,
        state: NSVisualEffectView.State = .followsWindowActiveState
    ) {
        self.material = material
        self.blendingMode = blendingMode
        self.state = state
    }

    public func makeNSView(context: Context) -> NSVisualEffectView {
        let view = NSVisualEffectView()
        view.material = material
        view.blendingMode = blendingMode
        view.state = state
        view.isEmphasized = true
        return view
    }

    public func updateNSView(_ nsView: NSVisualEffectView, context: Context) {
        nsView.material = material
        nsView.blendingMode = blendingMode
        nsView.state = state
    }
}

public struct LiquidGlassModifier: ViewModifier {
    public var role: LiquidGlassRole
    public var cornerRadius: CGFloat
    public var tint: Color
    public var isInteractive: Bool
    public var isHovered: Bool

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.controlActiveState) private var controlActiveState

    public init(
        role: LiquidGlassRole,
        cornerRadius: CGFloat,
        tint: Color,
        isInteractive: Bool = false,
        isHovered: Bool = false
    ) {
        self.role = role
        self.cornerRadius = cornerRadius
        self.tint = tint
        self.isInteractive = isInteractive
        self.isHovered = isHovered
    }

    public func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        let isDark = (colorScheme == .dark)
        let activeFactor: Double = (controlActiveState == .inactive) ? 0.65 : 1.0
        let hoverBoost: Double = (isInteractive && isHovered) ? 0.08 : 0.0

        content
            .background {
                if reduceTransparency {
                    shape.fill(Color(nsColor: .windowBackgroundColor))
                } else {
                    ZStack {
                        // Layer 1: Native SwiftUI/AppKit Optical Blur Material
                        shape.fill(swiftUIMaterial)

                        // Layer 2: Chromatic Artwork Refraction Tint
                        if role == .navigationSidebar {
                            LinearGradient(
                                colors: [
                                    tint.opacity((isDark ? 0.22 : 0.16) * activeFactor),
                                    tint.opacity((isDark ? 0.09 : 0.06) * activeFactor)
                                ],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        } else {
                            shape.fill(
                                tint.opacity((isDark ? 0.14 : 0.09) * activeFactor + hoverBoost)
                            )
                        }

                        // Layer 3: Top Surface Specular Sheen
                        shape.fill(
                            LinearGradient(
                                colors: [
                                    Color.white.opacity((isDark ? 0.16 : 0.42) * activeFactor + hoverBoost),
                                    Color.white.opacity(isDark ? 0.02 : 0.10),
                                    Color.clear
                                ],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                    }
                }
            }
            .clipShape(shape)
            .overlay {
                // Layer 4: Anisotropic Specular Rim Highlight (Top-Leading bright -> Bottom-Trailing tinted)
                shape.strokeBorder(
                    LinearGradient(
                        stops: [
                            .init(color: Color.white.opacity((isDark ? 0.44 : 0.72) * activeFactor + hoverBoost), location: 0.0),
                            .init(color: Color.white.opacity(isDark ? 0.10 : 0.22), location: 0.45),
                            .init(color: tint.opacity((isDark ? 0.28 : 0.20) * activeFactor), location: 1.0)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: role == .transportCapsule || role == .floatingPillBar ? 1.0 : 0.75
                )
            }
            .shadow(
                color: Color.black.opacity(shadowOpacity(isDark: isDark)),
                radius: shadowRadius,
                x: 0,
                y: shadowYOffset
            )
    }

    private var swiftUIMaterial: Material {
        switch role {
        case .navigationSidebar: return .ultraThinMaterial
        case .floatingPillBar: return .thinMaterial
        case .transportCapsule: return .ultraThinMaterial
        case .bentoCard: return .regularMaterial
        case .modalSheet: return .thickMaterial
        case .interactiveChip: return .ultraThinMaterial
        }
    }

    private var shadowRadius: CGFloat {
        switch role {
        case .transportCapsule, .modalSheet: return 24
        case .floatingPillBar: return 16
        case .bentoCard: return 12
        case .navigationSidebar, .interactiveChip: return 6
        }
    }

    private var shadowYOffset: CGFloat {
        switch role {
        case .transportCapsule, .modalSheet: return 10
        case .floatingPillBar, .bentoCard: return 6
        case .navigationSidebar, .interactiveChip: return 2
        }
    }

    private func shadowOpacity(isDark: Bool) -> Double {
        switch role {
        case .transportCapsule, .modalSheet: return isDark ? 0.42 : 0.16
        case .floatingPillBar, .bentoCard: return isDark ? 0.28 : 0.10
        case .navigationSidebar, .interactiveChip: return isDark ? 0.16 : 0.06
        }
    }
}

public final class AmbientCanvasState: ObservableObject {
    public static let shared = AmbientCanvasState()

    @Published public var previousArtwork: NSImage? = nil
    @Published public var currentArtwork: NSImage? = nil
    @Published public var crossfadeProgress: CGFloat = 1.0

    @Published public var seedR: Double = 1.0
    @Published public var seedG: Double = 0.18
    @Published public var seedB: Double = 0.33

    private var startSeedR: Double = 1.0
    private var startSeedG: Double = 0.18
    private var startSeedB: Double = 0.33
    private var targetSeedR: Double = 1.0
    private var targetSeedG: Double = 0.18
    private var targetSeedB: Double = 0.33

    private var lastTrackKey: String = ""
    private var fadeTimer: DispatchSourceTimer?

    private init() {}

    public var interpolatedAccentNSColor: NSColor {
        NSColor(srgbRed: CGFloat(seedR), green: CGFloat(seedG), blue: CGFloat(seedB), alpha: 1.0)
    }

    public func transitionTo(
        trackKey: String,
        artwork: NSImage?,
        targetR: Double,
        targetG: Double,
        targetB: Double,
        duration: TimeInterval = 0.82
    ) {
        fadeTimer?.cancel()
        fadeTimer = nil

        let isNewTrack = (trackKey != lastTrackKey)
        let isNewArt = (currentArtwork !== artwork)

        if isNewTrack || isNewArt {
            if crossfadeProgress > 0.4 || previousArtwork == nil {
                previousArtwork = currentArtwork
            }
            currentArtwork = artwork
            crossfadeProgress = (previousArtwork == nil && currentArtwork != nil) ? 1.0 : 0.0
            lastTrackKey = trackKey
        }

        startSeedR = seedR
        startSeedG = seedG
        startSeedB = seedB
        targetSeedR = targetR
        targetSeedG = targetG
        targetSeedB = targetB

        let startTime = CACurrentMediaTime()
        let timer = DispatchSource.makeTimerSource(flags: .strict, queue: .main)
        timer.schedule(deadline: .now(), repeating: .microseconds(16667), leeway: .microseconds(1000))

        timer.setEventHandler { [weak self] in
            guard let self = self else { return }
            let elapsed = CACurrentMediaTime() - startTime
            let rawT = min(1.0, max(0.0, elapsed / duration))
            // Smooth Cosine S-Curve for pure, buttery cross-dissolve
            let eased = 0.5 - 0.5 * cos(Double.pi * rawT)

            self.crossfadeProgress = CGFloat(eased)
            self.seedR = self.startSeedR + (self.targetSeedR - self.startSeedR) * eased
            self.seedG = self.startSeedG + (self.targetSeedG - self.startSeedG) * eased
            self.seedB = self.startSeedB + (self.targetSeedB - self.startSeedB) * eased

            if rawT >= 1.0 {
                self.fadeTimer?.cancel()
                self.fadeTimer = nil
                self.crossfadeProgress = 1.0
                self.previousArtwork = self.currentArtwork
                self.seedR = self.targetSeedR
                self.seedG = self.targetSeedG
                self.seedB = self.targetSeedB
            }
        }

        fadeTimer = timer
        timer.resume()
    }
}

public struct AmbientCrossfadeArtworkView: View {
    @ObservedObject var canvas = AmbientCanvasState.shared
    public var opacity: Double

    public init(opacity: Double = 0.35) {
        self.opacity = opacity
    }

    public var body: some View {
        ZStack {
            if let prev = canvas.previousArtwork {
                let prevFactor = (canvas.currentArtwork == nil) ? (1.0 - canvas.crossfadeProgress) : (1.0 - canvas.crossfadeProgress * 0.70)
                Image(nsImage: prev)
                    .resizable()
                    .interpolation(.high)
                    .scaledToFill()
                    .opacity(max(0.0, opacity * Double(prevFactor)))
            }

            if let curr = canvas.currentArtwork {
                let currFactor = canvas.crossfadeProgress
                Image(nsImage: curr)
                    .resizable()
                    .interpolation(.high)
                    .scaledToFill()
                    .opacity(max(0.0, opacity * Double(currFactor)))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipped()
        .allowsHitTesting(false)
    }
}

public struct AmbientArtworkMeshBackground: View {
    @ObservedObject var canvas = AmbientCanvasState.shared
    public var artwork: NSImage?
    public var accentColor: Color
    public var secondaryColor: Color
    public var surfaceColor: Color
    public var canvasMode: BackgroundCanvasMode
    public var isDark: Bool

    public init(
        artwork: NSImage? = nil,
        accentColor: Color = .pink,
        secondaryColor: Color = .indigo,
        surfaceColor: Color = Color(red: 0.05, green: 0.05, blue: 0.07),
        canvasMode: BackgroundCanvasMode = .liquidRefraction,
        isDark: Bool = true
    ) {
        self.artwork = artwork
        self.accentColor = accentColor
        self.secondaryColor = secondaryColor
        self.surfaceColor = surfaceColor
        self.canvasMode = canvasMode
        self.isDark = isDark
    }

    public var body: some View {
        ZStack {
            // Base Theme Surface
            surfaceColor

            switch canvasMode {
            case .pureGlass:
                // Minimal Clean Studio Mode ("BLACK")
                LinearGradient(
                    colors: [
                        accentColor.opacity(isDark ? 0.04 : 0.03),
                        Color.clear
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )

            case .ambientMesh:
                // Chromatic Mesh Aura Mode ("AURA"): Ambient multi-point light from active theme
                ZStack {
                    LinearGradient(
                        colors: [
                            accentColor.opacity(isDark ? 0.34 : 0.22),
                            secondaryColor.opacity(isDark ? 0.22 : 0.14),
                            surfaceColor.opacity(0.92)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    RadialGradient(
                        colors: [
                            accentColor.opacity(isDark ? 0.26 : 0.16),
                            Color.clear
                        ],
                        center: .topLeading,
                        startRadius: 20,
                        endRadius: 520
                    )
                }

            case .liquidRefraction:
                // Liquid Artwork Refraction Mode ("DYNAMIC"): Refracted artwork glow + theme
                ZStack {
                    AmbientCrossfadeArtworkView(opacity: isDark ? 0.40 : 0.22)

                    LinearGradient(
                        colors: [
                            accentColor.opacity(isDark ? 0.20 : 0.12),
                            secondaryColor.opacity(isDark ? 0.12 : 0.08),
                            surfaceColor.opacity(isDark ? 0.55 : 0.45)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                }
            }

            // Subtle dark/light vignette
            LinearGradient(
                colors: [
                    Color.clear,
                    isDark ? Color.black.opacity(0.32) : Color.white.opacity(0.18)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipped()
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }
}

// ==============================================================================
// WINDOW ZOOM & FIT-TO-SCREEN MANAGER (macOS Double-Tap / Double-Click Zoom)
// ==============================================================================

public final class WindowZoomManager: NSObject, NSWindowDelegate, ObservableObject {
    public static let shared = WindowZoomManager()
    @Published public var isZoomAnimating: Bool = false
    private var savedRestoreFrames: [Int: NSRect] = [:]
    private var mouseMonitor: Any?
    private var activeFrameTimer: DispatchSourceTimer?
    private var configuredWindowNumbers = Set<Int>()

    public func configure(window: NSWindow) {
        window.delegate = self
        window.minSize = NSSize(width: 880, height: 600)
        window.contentMinSize = NSSize(width: 880, height: 600)
        window.titlebarAppearsTransparent = true
        window.titleVisibility = .hidden

        let winNum = window.windowNumber
        if !configuredWindowNumbers.contains(winNum) {
            configuredWindowNumbers.insert(winNum)
            let currentF = window.frame
            if currentF.width <= 900 && currentF.height <= 620 {
                let screen = window.screen ?? NSScreen.main
                if let vf = screen?.visibleFrame {
                    let defW = min(1200.0, vf.width * 0.80)
                    let defH = min(760.0, vf.height * 0.80)
                    let initRect = NSRect(
                        x: round(vf.midX - defW / 2.0),
                        y: round(vf.midY - defH / 2.0),
                        width: defW,
                        height: defH
                    )
                    window.setFrame(initRect, display: true, animate: false)
                    savedRestoreFrames[winNum] = initRect
                }
            }
        }

        if let zoomBtn = window.standardWindowButton(.zoomButton) {
            zoomBtn.target = self
            zoomBtn.action = #selector(handleGreenZoomButton(_:))
        }

        installTopBarDoubleTapMonitorIfNeeded()
    }

    public func windowWillUseStandardFrame(_ window: NSWindow, defaultFrame newFrame: NSRect) -> NSRect {
        return window.screen?.visibleFrame ?? newFrame
    }

    public func windowShouldZoom(_ window: NSWindow, toFrame newFrame: NSRect) -> Bool {
        toggleFitToScreen(for: window)
        return false
    }

    public func animationResizeTime(_ window: NSWindow, newFrame: NSRect) -> TimeInterval {
        return 0.46
    }

    @objc private func handleGreenZoomButton(_ sender: Any?) {
        toggleFitToScreen()
    }

    /// Toggles Fit-to-Screen (Zoom to NSScreen.visibleFrame or restores previous size) with 120Hz smooth interpolation
    public func toggleFitToScreen(for targetWindow: NSWindow? = nil) {
        guard let window = targetWindow ?? NSApplication.shared.keyWindow ?? NSApplication.shared.windows.first,
              let screen = window.screen ?? NSScreen.main else { return }

        let visibleFrame = screen.visibleFrame
        let currentFrame = window.frame
        let winNum = window.windowNumber

        let isAlreadyFitted =
            abs(currentFrame.origin.x - visibleFrame.origin.x) <= 8 &&
            abs(currentFrame.origin.y - visibleFrame.origin.y) <= 8 &&
            abs(currentFrame.width - visibleFrame.width) <= 8 &&
            abs(currentFrame.height - visibleFrame.height) <= 8

        let targetFrame: NSRect
        if isAlreadyFitted {
            if let restore = savedRestoreFrames[winNum],
               restore.width >= 880 && restore.height >= 600 {
                targetFrame = restore
            } else {
                let defaultWidth = min(1200.0, visibleFrame.width * 0.80)
                let defaultHeight = min(760.0, visibleFrame.height * 0.80)
                targetFrame = NSRect(
                    x: round(visibleFrame.midX - defaultWidth / 2.0),
                    y: round(visibleFrame.midY - defaultHeight / 2.0),
                    width: defaultWidth,
                    height: defaultHeight
                )
            }
        } else {
            savedRestoreFrames[winNum] = currentFrame
            targetFrame = visibleFrame
        }

        animateWindowFrame(window: window, to: targetFrame, duration: 0.46)
    }

    private func animateWindowFrame(window: NSWindow, to targetFrame: NSRect, duration: TimeInterval = 0.46) {
        activeFrameTimer?.cancel()
        activeFrameTimer = nil

        let startFrame = window.frame
        if abs(startFrame.origin.x - targetFrame.origin.x) < 1 &&
           abs(startFrame.origin.y - targetFrame.origin.y) < 1 &&
           abs(startFrame.width - targetFrame.width) < 1 &&
           abs(startFrame.height - targetFrame.height) < 1 {
            isZoomAnimating = false
            window.setFrame(targetFrame, display: true, animate: false)
            return
        }

        isZoomAnimating = true
        let startTime = CACurrentMediaTime()
        let timer = DispatchSource.makeTimerSource(flags: .strict, queue: .main)
        timer.schedule(deadline: .now(), repeating: .microseconds(8333), leeway: .microseconds(400))

        let scale = max(1.0, window.screen?.backingScaleFactor ?? NSScreen.main?.backingScaleFactor ?? 2.0)
        func snapToPixel(_ val: CGFloat) -> CGFloat {
            (val * scale).rounded() / scale
        }

        timer.setEventHandler { [weak self, weak window] in
            guard let self = self, let window = window else {
                self?.isZoomAnimating = false
                self?.activeFrameTimer?.cancel()
                self?.activeFrameTimer = nil
                return
            }

            let elapsed = CACurrentMediaTime() - startTime
            let rawProgress = min(1.0, max(0.0, elapsed / duration))
            // Blended Quintic-Cosine S-Curve: zero initial acceleration kick & silky smooth touchdown
            let cosine = 0.5 - 0.5 * cos(Double.pi * rawProgress)
            let quintic = rawProgress * rawProgress * rawProgress * (rawProgress * (rawProgress * 6.0 - 15.0) + 10.0)
            let eased = CGFloat(0.40 * cosine + 0.60 * quintic)

            let minX = snapToPixel(startFrame.minX + (targetFrame.minX - startFrame.minX) * eased)
            let maxX = snapToPixel(startFrame.maxX + (targetFrame.maxX - startFrame.maxX) * eased)
            let minY = snapToPixel(startFrame.minY + (targetFrame.minY - startFrame.minY) * eased)
            let maxY = snapToPixel(startFrame.maxY + (targetFrame.maxY - startFrame.maxY) * eased)

            let currentRect = NSRect(
                x: minX,
                y: minY,
                width: max(880.0, maxX - minX),
                height: max(600.0, maxY - minY)
            )

            CATransaction.begin()
            CATransaction.setDisableActions(true)
            window.setFrame(currentRect, display: true, animate: false)
            CATransaction.commit()

            if rawProgress >= 1.0 {
                self.activeFrameTimer?.cancel()
                self.activeFrameTimer = nil
                self.isZoomAnimating = false
                CATransaction.begin()
                CATransaction.setDisableActions(true)
                window.setFrame(targetFrame, display: true, animate: false)
                CATransaction.commit()
            }
        }

        activeFrameTimer = timer
        timer.resume()
    }

    private func installTopBarDoubleTapMonitorIfNeeded() {
        guard mouseMonitor == nil else { return }
        mouseMonitor = NSEvent.addLocalMonitorForEvents(matching: [.leftMouseDown]) { [weak self] event in
            guard let self = self,
                  event.clickCount == 2,
                  let window = event.window,
                  let contentView = window.contentView else {
                return event
            }

            let loc = event.locationInWindow
            let topDistance = contentView.bounds.height - loc.y
            // 58pt top titlebar/toolbar boundary
            guard topDistance >= 0 && topDistance <= 58 else {
                return event
            }

            // Exclude traffic light controls (left 78pt, top 32pt)
            if loc.x < 78 && topDistance < 32 {
                return event
            }

            // Exclude text search fields, buttons, or controls
            if let hitView = contentView.hitTest(loc) {
                if hitView is NSTextView || hitView is NSTextField || hitView is NSControl {
                    return event
                }
            }

            DispatchQueue.main.async {
                self.toggleFitToScreen(for: window)
            }
            // CRITICAL: Consume the 2nd click event so NSTitlebarView doesn't double-trigger performZoom(nil)!
            return nil
        }
    }
}

public struct TsrossaInteractiveRowStyle: ButtonStyle {
    public var isSelected: Bool = false
    public var activeTint: Color = .pink
    public var cornerRadius: CGFloat = 8

    public init(isSelected: Bool = false, activeTint: Color = .pink, cornerRadius: CGFloat = 8) {
        self.isSelected = isSelected
        self.activeTint = activeTint
        self.cornerRadius = cornerRadius
    }

    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Rectangle())
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(
                        isSelected ? activeTint.opacity(0.18) :
                        (configuration.isPressed ? Color.white.opacity(0.10) : Color.clear)
                    )
            )
            .scaleEffect(configuration.isPressed ? 0.985 : 1.0)
            .animation(.spring(response: 0.18, dampingFraction: 0.8), value: configuration.isPressed)
            .animation(.spring(response: 0.24, dampingFraction: 0.8), value: isSelected)
    }
}

public extension View {
    func liquidGlass(
        role: LiquidGlassRole = .bentoCard,
        cornerRadius: CGFloat = 18,
        tint: Color = .accentColor,
        isInteractive: Bool = false,
        isHovered: Bool = false
    ) -> some View {
        modifier(
            LiquidGlassModifier(
                role: role,
                cornerRadius: cornerRadius,
                tint: tint,
                isInteractive: isInteractive,
                isHovered: isHovered
            )
        )
    }

    func continuousSquircle(_ radius: CGFloat) -> some View {
        clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
    }
}
