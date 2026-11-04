import SwiftUI
import AppKit
import AVFoundation

// ==============================================================================
// STRONGLY-TYPED UI/UX THEME ENUMS (Apple Human Interface Guidelines)
// ==============================================================================

public enum AppThemeMode: String, Codable, CaseIterable, Identifiable {
    case system = "System Auto"
    case dark = "Dark Glass"
    case light = "Light Glass"

    public var id: String { rawValue }

    public var preferredColorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .dark: return .dark
        case .light: return .light
        }
    }
}

public enum BackgroundCanvasMode: String, Codable, CaseIterable, Identifiable {
    case liquidRefraction = "Liquid Glass Refraction"
    case ambientMesh = "Ambient Artwork Mesh"
    case pureGlass = "Pure Minimal Glass"

    public var id: String { rawValue }
}

public struct ThemePaletteOption: Identifiable, Hashable {
    public let id: String
    public let name: String
    public let color: Color
    public let nsColor: NSColor

    public static func == (lhs: ThemePaletteOption, rhs: ThemePaletteOption) -> Bool {
        lhs.id == rhs.id
    }
    public func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }

    public static let allOptions: [ThemePaletteOption] = [
        ThemePaletteOption(id: "DYNAMIC", name: "Dynamic Artwork", color: Color(red: 0.95, green: 0.25, blue: 0.45), nsColor: NSColor(srgbRed: 0.95, green: 0.25, blue: 0.45, alpha: 1.0)),
        ThemePaletteOption(id: "ROSE", name: "Rose Ruby", color: Color(red: 1.0, green: 0.18, blue: 0.33), nsColor: NSColor(srgbRed: 1.0, green: 0.18, blue: 0.33, alpha: 1.0)),
        ThemePaletteOption(id: "MATCHA", name: "Matcha Emerald", color: Color(red: 0.62, green: 0.82, blue: 0.38), nsColor: NSColor(srgbRed: 0.62, green: 0.82, blue: 0.38, alpha: 1.0)),
        ThemePaletteOption(id: "GOLD", name: "Studio Gold", color: Color(red: 1.0, green: 0.84, blue: 0.0), nsColor: NSColor(srgbRed: 1.0, green: 0.84, blue: 0.0, alpha: 1.0)),
        ThemePaletteOption(id: "CYAN", name: "Audiophile Cyan", color: Color(red: 0.0, green: 0.90, blue: 1.0), nsColor: NSColor(srgbRed: 0.0, green: 0.90, blue: 1.0, alpha: 1.0))
    ]
}

public enum AccentColorMode: String, Codable, CaseIterable, Identifiable {
    case dynamicArtwork = "Dynamic Artwork"
    case systemBlue = "Apple Blue"
    case studioAmber = "Studio Amber"
    case emeraldHiRes = "Hi-Res Emerald"
    case amethyst = "Royal Amethyst"

    public var id: String { rawValue }
}

public enum MainWorkspaceTab: String, Codable, CaseIterable, Identifiable {
    case nowPlaying = "Now Playing"
    case library = "Library"
    case playlists = "Playlists"
    case settings = "Settings"

    public var id: String { rawValue }
}

public enum QueuePanelTab: String, Codable, CaseIterable, Identifiable {
    case upNext = "Up Next"
    case lyrics = "Lyrics"

    public var id: String { rawValue }
}

// ==============================================================================
// LIQUID GLASS ADAPTIVE THEME SYSTEM (Light / Dark / High-Contrast WCAG)
// ==============================================================================

fileprivate struct HSL {
    var h: Double // 0..<360
    var s: Double // 0...1
    var l: Double // 0...1
}

fileprivate func rgbToHsl(r: Double, g: Double, b: Double) -> HSL {
    let maxV = max(r, max(g, b))
    let minV = min(r, min(g, b))
    let delta = maxV - minV
    var h: Double = 0
    var s: Double = 0
    let l: Double = (maxV + minV) / 2.0
    
    if delta != 0 {
        s = l < 0.5 ? delta / (maxV + minV) : delta / (2.0 - maxV - minV)
        if maxV == r {
            h = ((g - b) / delta).truncatingRemainder(dividingBy: 6)
        } else if maxV == g {
            h = (b - r) / delta + 2
        } else {
            h = (r - g) / delta + 4
        }
        h *= 60
        if h < 0 { h += 360 }
    }
    return HSL(h: h, s: s, l: l)
}

fileprivate func hslToColor(h: Double, s: Double, l: Double) -> Color {
    let c = (1.0 - abs(2.0 * l - 1.0)) * s
    let x = c * (1.0 - abs((h / 60.0).truncatingRemainder(dividingBy: 2) - 1.0))
    let m = l - c / 2.0
    var r1 = 0.0, g1 = 0.0, b1 = 0.0
    if h < 60 { r1 = c; g1 = x; b1 = 0 }
    else if h < 120 { r1 = x; g1 = c; b1 = 0 }
    else if h < 180 { r1 = 0; g1 = c; b1 = x }
    else if h < 240 { r1 = 0; g1 = x; b1 = c }
    else if h < 300 { r1 = x; g1 = 0; b1 = c }
    else { r1 = c; g1 = 0; b1 = x }
    return Color(red: r1 + m, green: g1 + m, blue: b1 + m)
}

public struct LiquidGlassTheme: Equatable {
    public let isDark: Bool
    public let reduceTransparency: Bool
    public let specularTopOpacity: Double
    public let rimOpacity: Double

    public let seedColor: Color
    public let primary: Color
    public let onPrimary: Color
    public let primaryContainer: Color
    public let onPrimaryContainer: Color
    
    public let secondary: Color
    public let secondaryContainer: Color
    public let onSecondaryContainer: Color
    
    public let tertiary: Color
    public let tertiaryContainer: Color
    
    public let surface: Color
    public let surfaceContainerLowest: Color
    public let surfaceContainerLow: Color
    public let surfaceContainer: Color
    public let surfaceContainerHigh: Color
    public let surfaceContainerHighest: Color
    
    public let outline: Color
    public let outlineVariant: Color
    
    public let textPrimary: Color
    public let textSecondary: Color
    public let textMuted: Color
    public var error: Color { Color(red: 1.0, green: 0.36, blue: 0.36) }

    public static func == (lhs: LiquidGlassTheme, rhs: LiquidGlassTheme) -> Bool {
        lhs.isDark == rhs.isDark && lhs.primary == rhs.primary && lhs.surface == rhs.surface
    }

    public static let defaultTheme = LiquidGlassTheme(
        accentNSColor: NSColor(srgbRed: 1.0, green: 0.18, blue: 0.33, alpha: 1.0),
        isDark: true,
        reduceTransparency: false
    )

    public init(accentNSColor: NSColor, isDark: Bool = true, reduceTransparency: Bool = false) {
        self.isDark = isDark
        self.reduceTransparency = reduceTransparency

        let srgb = accentNSColor.usingColorSpace(.sRGB) ?? NSColor(srgbRed: 1.0, green: 0.18, blue: 0.33, alpha: 1.0)
        let r = Double(srgb.redComponent)
        let g = Double(srgb.greenComponent)
        let b = Double(srgb.blueComponent)
        let hsl = rgbToHsl(r: r, g: g, b: b)

        self.seedColor = Color(nsColor: srgb)
        self.specularTopOpacity = isDark ? 0.16 : 0.42
        self.rimOpacity = isDark ? 0.44 : 0.72

        let hue = hsl.h
        let sat = min(1.0, max(0.40, hsl.s))

        if isDark {
            self.primary = hslToColor(h: hue, s: min(0.85, sat * 1.05), l: 0.76)
            self.onPrimary = hslToColor(h: hue, s: sat, l: 0.14)
            self.primaryContainer = hslToColor(h: hue, s: sat * 0.75, l: 0.24)
            self.onPrimaryContainer = hslToColor(h: hue, s: sat * 0.85, l: 0.92)

            let secHue = (hue + 18).truncatingRemainder(dividingBy: 360)
            self.secondary = hslToColor(h: secHue, s: sat * 0.55, l: 0.75)
            self.secondaryContainer = hslToColor(h: secHue, s: sat * 0.45, l: 0.26)
            self.onSecondaryContainer = hslToColor(h: secHue, s: sat * 0.60, l: 0.90)

            let tertHue = (hue + 60).truncatingRemainder(dividingBy: 360)
            self.tertiary = hslToColor(h: tertHue, s: sat * 0.70, l: 0.80)
            self.tertiaryContainer = hslToColor(h: tertHue, s: sat * 0.50, l: 0.28)

            let surfaceSat = min(0.32, max(0.14, sat * 0.40))
            self.surface = hslToColor(h: hue, s: surfaceSat, l: 0.082)
            self.surfaceContainerLowest = hslToColor(h: hue, s: surfaceSat, l: 0.045)
            self.surfaceContainerLow = hslToColor(h: hue, s: surfaceSat, l: 0.095)
            self.surfaceContainer = hslToColor(h: hue, s: surfaceSat, l: 0.13)
            self.surfaceContainerHigh = hslToColor(h: hue, s: surfaceSat, l: 0.17)
            self.surfaceContainerHighest = hslToColor(h: hue, s: surfaceSat, l: 0.22)

            self.outline = hslToColor(h: hue, s: surfaceSat * 1.5, l: 0.32)
            self.outlineVariant = hslToColor(h: hue, s: surfaceSat * 1.2, l: 0.18)

            self.textPrimary = Color(white: 0.96)
            self.textSecondary = Color(white: 0.65)
            self.textMuted = Color(white: 0.42)
        } else {
            // Light Glass Palette
            self.primary = hslToColor(h: hue, s: min(0.95, sat * 1.10), l: 0.38)
            self.onPrimary = Color.white
            self.primaryContainer = hslToColor(h: hue, s: sat * 0.70, l: 0.90)
            self.onPrimaryContainer = hslToColor(h: hue, s: sat * 0.85, l: 0.15)

            let secHue = (hue + 18).truncatingRemainder(dividingBy: 360)
            self.secondary = hslToColor(h: secHue, s: sat * 0.55, l: 0.40)
            self.secondaryContainer = hslToColor(h: secHue, s: sat * 0.45, l: 0.90)
            self.onSecondaryContainer = hslToColor(h: secHue, s: sat * 0.60, l: 0.18)

            let tertHue = (hue + 60).truncatingRemainder(dividingBy: 360)
            self.tertiary = hslToColor(h: tertHue, s: sat * 0.70, l: 0.35)
            self.tertiaryContainer = hslToColor(h: tertHue, s: sat * 0.50, l: 0.90)

            let lightSurfaceSat = min(0.24, max(0.10, sat * 0.25))
            self.surface = hslToColor(h: hue, s: lightSurfaceSat, l: 0.95)
            self.surfaceContainerLowest = Color.white
            self.surfaceContainerLow = hslToColor(h: hue, s: lightSurfaceSat, l: 0.93)
            self.surfaceContainer = hslToColor(h: hue, s: lightSurfaceSat, l: 0.89)
            self.surfaceContainerHigh = hslToColor(h: hue, s: lightSurfaceSat, l: 0.85)
            self.surfaceContainerHighest = hslToColor(h: hue, s: lightSurfaceSat, l: 0.81)

            self.outline = hslToColor(h: hue, s: lightSurfaceSat * 1.5, l: 0.65)
            self.outlineVariant = hslToColor(h: hue, s: lightSurfaceSat * 1.2, l: 0.80)

            self.textPrimary = Color(white: 0.10)
            self.textSecondary = Color(white: 0.38)
            self.textMuted = Color(white: 0.60)
        }
    }

    // DynamicMaterialTheme backward compatibility constructor
    public init(seedR: Double, seedG: Double, seedB: Double) {
        let ns = NSColor(srgbRed: CGFloat(seedR), green: CGFloat(seedG), blue: CGFloat(seedB), alpha: 1.0)
        self.init(accentNSColor: ns, isDark: true, reduceTransparency: false)
    }
}

public typealias DynamicMaterialTheme = LiquidGlassTheme

// ==============================================================================
// ARTWORK & COLOR EXTRACTION UTILITIES
// ==============================================================================

public extension Color {
    func toNSColor() -> NSColor {
        NSColor(self)
    }
}

public class ArtworkExtractor {
    private static let imageCache = NSCache<NSString, NSImage>()
    private static var themeCache: [String: LiquidGlassTheme] = [:]
    
    public static func loadArtworkAndTheme(
        for path: String,
        knownThumbPath: String? = nil,
        dominantHex: String? = nil,
        completion: @escaping (_ artwork: NSImage?, _ blurred: NSImage?, _ theme: LiquidGlassTheme) -> Void
    ) {
        if let cachedImage = imageCache.object(forKey: path as NSString),
           let cachedTheme = themeCache[path] {
            let blurred = AlbumArtCache.shared.cachedBlurredCanvasImage(for: path, knownThumbPath: knownThumbPath)
            completion(cachedImage, blurred, cachedTheme)
            return
        }
        
        DispatchQueue.global(qos: .userInitiated).async {
            // First check fast disk thumbnail cache
            let cachedImage = AlbumArtCache.shared.thumbnailImage(for: path, knownThumbPath: knownThumbPath)
            let artwork = cachedImage ?? extractArtwork(from: path)
            let blurred = AlbumArtCache.shared.blurredCanvasImage(for: path, knownThumbPath: knownThumbPath)
            var theme: LiquidGlassTheme = .defaultTheme
            
            if let hex = dominantHex, let c = ColorContrastHelper.parseHexToNSColor(hex) {
                theme = LiquidGlassTheme(accentNSColor: c, isDark: true, reduceTransparency: false)
            } else if let artwork = artwork {
                let accent = ColorContrastHelper.extractDominantAccent(from: artwork)
                theme = LiquidGlassTheme(accentNSColor: accent.nsColor, isDark: true, reduceTransparency: false)
            }
            if let artwork = artwork {
                imageCache.setObject(artwork, forKey: path as NSString)
            }
            themeCache[path] = theme
            
            DispatchQueue.main.async {
                completion(artwork, blurred, theme)
            }
        }
    }

    public static func loadArtworkAndTheme(for path: String, completion: @escaping (NSImage?, LiquidGlassTheme) -> Void) {
        loadArtworkAndTheme(for: path, knownThumbPath: nil, dominantHex: nil) { art, _, theme in
            completion(art, theme)
        }
    }
    
    public static func extractArtwork(from filePath: String) -> NSImage? {
        let url = URL(fileURLWithPath: filePath)
        let folder = url.deletingLastPathComponent()
        let commonNames = ["cover.jpg", "cover.png", "folder.jpg", "front.jpg", "front.png", "artwork.jpg"]
        for name in commonNames {
            let imgURL = folder.appendingPathComponent(name)
            if FileManager.default.fileExists(atPath: imgURL.path), let img = NSImage(contentsOf: imgURL) {
                return img
            }
        }
        return nil
    }
}

// ==============================================================================
// TACTILE INTERACTIVE CONTROLS
// ==============================================================================

public struct TsrossaScaleButtonStyle: ButtonStyle {
    public var scaleAmount: CGFloat = 0.94
    
    public init(scaleAmount: CGFloat = 0.94) {
        self.scaleAmount = scaleAmount
    }
    
    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scaleAmount : 1.0)
            .opacity(configuration.isPressed ? 0.82 : 1.0)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

public struct TsrossaCircleButtonStyle: ButtonStyle {
    public var bg: Color
    public var strokeColor: Color?
    
    public init(bg: Color = Color.white.opacity(0.08), strokeColor: Color? = nil) {
        self.bg = bg
        self.strokeColor = strokeColor
    }
    
    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(
                Circle()
                    .fill(configuration.isPressed ? bg.opacity(1.4) : bg)
            )
            .overlay(
                Group {
                    if let stroke = strokeColor {
                        Circle().stroke(stroke, lineWidth: 1)
                    }
                }
            )
            .scaleEffect(configuration.isPressed ? 0.93 : 1.0)
            .opacity(configuration.isPressed ? 0.82 : 1.0)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

public struct InteractiveCircleButton: View {
    public let icon: String
    public var size: CGFloat = 40
    public var iconSize: CGFloat = 16
    public var bg: Color = Color.white.opacity(0.08)
    public var fg: Color = Color.white.opacity(0.90)
    public var strokeColor: Color? = nil
    public var action: () -> Void
    
    public init(
        icon: String,
        size: CGFloat = 40,
        iconSize: CGFloat = 16,
        bg: Color = Color.white.opacity(0.08),
        fg: Color = Color.white.opacity(0.90),
        strokeColor: Color? = nil,
        action: @escaping () -> Void
    ) {
        self.icon = icon
        self.size = size
        self.iconSize = iconSize
        self.bg = bg
        self.fg = fg
        self.strokeColor = strokeColor
        self.action = action
    }
    
    public var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: iconSize, weight: .bold))
                .foregroundColor(fg)
                .frame(width: size, height: size)
                .contentShape(Circle())
        }
        .buttonStyle(TsrossaCircleButtonStyle(bg: bg, strokeColor: strokeColor))
    }
}

// Backwards compatibility layer
public struct TsrossaColors {
    public static let trueBlack = Color(red: 0.0, green: 0.0, blue: 0.0)
    public static let deepNavy = Color(red: 0.055, green: 0.051, blue: 0.075)
    public static let surfaceLow = Color(red: 0.078, green: 0.071, blue: 0.102)
    public static let surfaceContainer = Color(red: 0.106, green: 0.094, blue: 0.133)
    public static let surfaceHigh = Color(red: 0.141, green: 0.129, blue: 0.176)
    public static let surfaceHighest = Color(red: 0.184, green: 0.169, blue: 0.231)
    
    public static let accentGreen = Color(red: 0.62, green: 0.82, blue: 0.38)
    public static let terminalGreen = Color(red: 0.0, green: 1.0, blue: 0.0)
    public static let audiophileCyan = Color(red: 0.0, green: 0.898, blue: 1.0)
    public static let bitPerfectGold = Color(red: 1.0, green: 0.843, blue: 0.0)
    public static let errorRed = Color(red: 0.95, green: 0.26, blue: 0.21)
    
    public static let textPrimary = Color(white: 0.96)
    public static let textSecondary = Color(white: 0.60)
    public static let textMuted = Color(white: 0.40)
}

public struct GlassmorphicSurface: ViewModifier {
    var cornerRadius: CGFloat = 16
    var opacity: Double = 0.85
    var borderColor: Color = Color.white.opacity(0.10)
    
    public func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(TsrossaColors.surfaceContainer.opacity(opacity))
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(borderColor, lineWidth: 0.8)
            )
    }
}

extension View {
    public func glassmorphic(cornerRadius: CGFloat = 16, opacity: Double = 0.85, borderColor: Color = Color.white.opacity(0.10)) -> some View {
        self.modifier(GlassmorphicSurface(cornerRadius: cornerRadius, opacity: opacity, borderColor: borderColor))
    }
}

// ==============================================================================
// AUTHENTIC TSROSSA LOGO LOADER & VIEW (macOS Dock + Desktop UI)
// ==============================================================================

public enum TsrossaBrandLogo {
    public static func loadBundledImage(named name: String) -> NSImage? {
        if let resourceURL = Bundle.main.resourceURL?.appendingPathComponent(name),
           let img = NSImage(contentsOf: resourceURL) {
            return img
        }
        if let bundlePath = Bundle.main.path(forResource: (name as NSString).deletingPathExtension, ofType: (name as NSString).pathExtension),
           let img = NSImage(contentsOfFile: bundlePath) {
            return img
        }
        let devFallback = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
            .appendingPathComponent("macos/Resources")
            .appendingPathComponent(name)
        if FileManager.default.fileExists(atPath: devFallback.path),
           let img = NSImage(contentsOf: devFallback) {
            return img
        }
        return nil
    }

    public static let launcherImage: NSImage? = {
        loadBundledImage(named: "ic_launcher.png") ?? loadBundledImage(named: "AppIcon.png")
    }()
    
    public static let foregroundImage: NSImage? = {
        loadBundledImage(named: "ic_launcher_foreground.png") ?? launcherImage
    }()
    
    public static func makeMacSquircleIcon(from source: NSImage, canvasSize: CGFloat = 512) -> NSImage {
        let targetSize = NSSize(width: canvasSize, height: canvasSize)
        let output = NSImage(size: targetSize)
        output.lockFocus()
        defer { output.unlockFocus() }

        guard let ctx = NSGraphicsContext.current?.cgContext else { return source }
        ctx.setAllowsAntialiasing(true)
        ctx.setShouldAntialias(true)
        ctx.interpolationQuality = .high

        // Standar Apple macOS Big Sur+ Icon Grid:
        // Badan squircle = 80.47% dari kanvas (824/1024), margin = 9.765% di tiap sisi.
        let margin = canvasSize * 0.09765625
        let bodySize = canvasSize - (margin * 2.0)
        let bodyRect = NSRect(x: margin, y: margin, width: bodySize, height: bodySize)
        let cornerRadius = bodySize * 0.225

        // 1. Gambar Drop Shadow standar macOS Dock di belakang squircle
        ctx.saveGState()
        let shadowOffset = CGSize(width: 0, height: -canvasSize * 0.018)
        let shadowBlur = canvasSize * 0.045
        ctx.setShadow(
            offset: shadowOffset,
            blur: shadowBlur,
            color: NSColor(calibratedWhite: 0.0, alpha: 0.38).cgColor
        )
        let shadowPath = NSBezierPath(roundedRect: bodyRect, xRadius: cornerRadius, yRadius: cornerRadius)
        NSColor.black.setFill()
        shadowPath.fill()
        ctx.restoreGState()

        // 2. Clip ke Continuous Squircle dan gambar artwork ikon
        ctx.saveGState()
        let clipPath = NSBezierPath(roundedRect: bodyRect, xRadius: cornerRadius, yRadius: cornerRadius)
        clipPath.addClip()
        source.draw(
            in: bodyRect,
            from: NSRect(origin: .zero, size: source.size),
            operation: .sourceOver,
            fraction: 1.0
        )

        // 3. Tambahkan Inner Specular Rim Highlight (1.5pt) khas Liquid Glass macOS
        let strokePath = NSBezierPath(roundedRect: bodyRect.insetBy(dx: 1.0, dy: 1.0), xRadius: cornerRadius - 1.0, yRadius: cornerRadius - 1.0)
        strokePath.lineWidth = max(1.5, canvasSize * 0.003)
        NSColor(calibratedWhite: 1.0, alpha: 0.22).setStroke()
        strokePath.stroke()
        ctx.restoreGState()

        return output
    }

    public static func applyMacDockIcon() {
        if let rawIcon = launcherImage {
            NSApplication.shared.applicationIconImage = makeMacSquircleIcon(from: rawIcon, canvasSize: 512)
        }
    }
}

public struct TsrossaBrandIcon: View {
    public var size: CGFloat = 36
    public var cornerRadius: CGFloat = 10
    
    public init(size: CGFloat = 36, cornerRadius: CGFloat = 10) {
        self.size = size
        self.cornerRadius = cornerRadius
    }
    
    public var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(
                    RadialGradient(
                        colors: [
                            Color(red: 0.118, green: 0.071, blue: 0.141),
                            Color(red: 0.063, green: 0.047, blue: 0.086),
                            Color(red: 0.027, green: 0.020, blue: 0.039)
                        ],
                        center: .center,
                        startRadius: 2,
                        endRadius: size * 0.72
                    )
                )
                .frame(width: size, height: size)
            
            Circle()
                .fill(
                    RadialGradient(
                        colors: [
                            Color(red: 0.882, green: 0.114, blue: 0.282).opacity(0.28),
                            Color.clear
                        ],
                        center: .center,
                        startRadius: 1,
                        endRadius: size * 0.42
                    )
                )
                .frame(width: size * 0.85, height: size * 0.85)
            
            if let fg = TsrossaBrandLogo.foregroundImage {
                Image(nsImage: fg)
                    .resizable()
                    .interpolation(.high)
                    .antialiased(true)
                    .aspectRatio(contentMode: .fit)
                    .frame(width: size * 1.18, height: size * 1.18)
                    .frame(width: size, height: size)
                    .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            } else if let img = TsrossaBrandLogo.launcherImage {
                Image(nsImage: img)
                    .resizable()
                    .interpolation(.high)
                    .antialiased(true)
                    .aspectRatio(contentMode: .fill)
                    .frame(width: size, height: size)
                    .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            }
        }
        .frame(width: size, height: size)
        .overlay(
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .stroke(Color.white.opacity(0.14), lineWidth: 1)
        )
    }
}
