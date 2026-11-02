import SwiftUI
import AppKit

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

public struct AmbientArtworkMeshBackground: View {
    public var artwork: NSImage?
    public var accentColor: Color
    public var canvasMode: BackgroundCanvasMode
    public var isDark: Bool

    public init(
        artwork: NSImage? = nil,
        accentColor: Color = .pink,
        canvasMode: BackgroundCanvasMode = .liquidRefraction,
        isDark: Bool = true
    ) {
        self.artwork = artwork
        self.accentColor = accentColor
        self.canvasMode = canvasMode
        self.isDark = isDark
    }

    public var body: some View {
        GeometryReader { geo in
            ZStack {
                // Base background
                if isDark {
                    Color(red: 0.05, green: 0.05, blue: 0.07)
                } else {
                    Color(red: 0.95, green: 0.96, blue: 0.98)
                }

                if canvasMode == .liquidRefraction || canvasMode == .ambientMesh {
                    if let img = artwork {
                        Image(nsImage: img)
                            .resizable()
                            .scaledToFill()
                            .frame(width: geo.size.width, height: geo.size.height)
                            .blur(radius: 80)
                            .opacity(isDark ? 0.38 : 0.20)
                            .clipped()
                    } else {
                        // Ambient gradient mesh fallback
                        LinearGradient(
                            colors: [
                                accentColor.opacity(isDark ? 0.35 : 0.20),
                                Color.indigo.opacity(isDark ? 0.20 : 0.10),
                                Color.clear
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                        .blur(radius: 60)
                    }
                }

                // Subtle dark/light vignette
                RadialGradient(
                    gradient: Gradient(colors: [
                        Color.clear,
                        isDark ? Color.black.opacity(0.45) : Color.white.opacity(0.25)
                    ]),
                    center: .center,
                    startRadius: 180,
                    endRadius: max(geo.size.width, geo.size.height) * 0.75
                )
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .clipped()
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }
}

// ==============================================================================
// WINDOW ZOOM & FIT-TO-SCREEN MANAGER (macOS Double-Tap / Double-Click Zoom)
// ==============================================================================

public final class WindowZoomManager: NSObject, NSWindowDelegate {
    public static let shared = WindowZoomManager()
    private var savedRestoreFrames: [Int: NSRect] = [:]
    private var mouseMonitor: Any?

    public func configure(window: NSWindow) {
        window.delegate = self
        window.minSize = NSSize(width: 880, height: 600)
        window.contentMinSize = NSSize(width: 880, height: 600)
        window.titlebarAppearsTransparent = true
        window.titleVisibility = .hidden
        installTopBarDoubleTapMonitorIfNeeded()
    }

    /// Toggles Fit-to-Screen (Zoom to NSScreen.visibleFrame or restores previous size)
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

        if isAlreadyFitted {
            if let restore = savedRestoreFrames[winNum] {
                window.setFrame(restore, display: true, animate: true)
            } else {
                let defaultWidth = min(1220.0, visibleFrame.width * 0.82)
                let defaultHeight = min(780.0, visibleFrame.height * 0.82)
                let centered = NSRect(
                    x: visibleFrame.midX - defaultWidth / 2.0,
                    y: visibleFrame.midY - defaultHeight / 2.0,
                    width: defaultWidth,
                    height: defaultHeight
                )
                window.setFrame(centered, display: true, animate: true)
            }
        } else {
            savedRestoreFrames[winNum] = currentFrame
            window.setFrame(visibleFrame, display: true, animate: true)
        }
    }

    public func windowWillUseStandardFrame(_ window: NSWindow, defaultFrame newFrame: NSRect) -> NSRect {
        return window.screen?.visibleFrame ?? newFrame
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

            // Exclude text search fields or buttons
            if let hitView = contentView.hitTest(loc) {
                if hitView is NSTextView || hitView is NSTextField || hitView is NSControl {
                    return event
                }
            }

            DispatchQueue.main.async {
                self.toggleFitToScreen(for: window)
            }
            return event
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
