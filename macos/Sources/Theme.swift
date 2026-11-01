import SwiftUI
import AppKit
import AVFoundation

// ==============================================================================
// MATERIAL 3 / MATERIAL YOU DYNAMIC COLOR ENGINE (Pure Native Swift / Apple Silicon)
// Replicates Android 12/13/14 Monet Dynamic Color Extraction & Tonal Palettes
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

public struct DynamicMaterialTheme: Equatable {
    public let seedColor: Color
    
    // Core Material 3 Dark Palette
    public let primary: Color                  // Tone 80 - High contrast vibrant accent
    public let onPrimary: Color                // Tone 20 - Deep contrast text on primary
    public let primaryContainer: Color         // Tone 30 - Pill / badge background
    public let onPrimaryContainer: Color       // Tone 90 - Text on primaryContainer
    
    public let secondary: Color                // Tone 80 (Chroma reduced)
    public let secondaryContainer: Color       // Tone 30
    public let onSecondaryContainer: Color     // Tone 90
    
    public let tertiary: Color                 // Tone 80 (Hue shifted +60deg)
    public let tertiaryContainer: Color        // Tone 30
    
    // Surface Tonal System (Subtly tinted with seed color)
    public let surface: Color                  // Tone 6
    public let surfaceContainerLowest: Color   // Tone 4 - Deepest app background
    public let surfaceContainerLow: Color      // Tone 9 - Cards / list rows
    public let surfaceContainer: Color         // Tone 12 - Nav bar / modal surfaces
    public let surfaceContainerHigh: Color     // Tone 16 - Floating pills / headers
    public let surfaceContainerHighest: Color  // Tone 21 - Input fields / hover
    
    public let outline: Color                  // Tone 35 - Card strokes
    public let outlineVariant: Color           // Tone 20 - Subtle dividers
    
    public let textPrimary: Color
    public let textSecondary: Color
    public let textMuted: Color
    public var error: Color { Color(red: 1.0, green: 0.36, blue: 0.36) }
    
    public static func == (lhs: DynamicMaterialTheme, rhs: DynamicMaterialTheme) -> Bool {
        return lhs.primary == rhs.primary && lhs.surface == rhs.surface
    }
    
    // Tsrossa's signature Crimson Rose fallback (#FF2E55) matching Android ic_launcher
    public static let defaultTheme = DynamicMaterialTheme(seedR: 1.0, seedG: 0.18, seedB: 0.33)
    
    public init(seedR: Double, seedG: Double, seedB: Double) {
        let hsl = rgbToHsl(r: seedR, g: seedG, b: seedB)
        self.seedColor = Color(red: seedR, green: seedG, blue: seedB)
        
        let hue = hsl.h
        let sat = min(1.0, max(0.40, hsl.s))
        let neutralSat = min(0.12, sat * 0.22)
        
        // M3 Accent Colors
        self.primary = hslToColor(h: hue, s: min(0.85, sat * 1.05), l: 0.78)
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
        
        // M3 Dark Surface Palette
        self.surface = hslToColor(h: hue, s: neutralSat, l: 0.065)
        self.surfaceContainerLowest = hslToColor(h: hue, s: neutralSat, l: 0.038)
        self.surfaceContainerLow = hslToColor(h: hue, s: neutralSat, l: 0.085)
        self.surfaceContainer = hslToColor(h: hue, s: neutralSat, l: 0.115)
        self.surfaceContainerHigh = hslToColor(h: hue, s: neutralSat, l: 0.155)
        self.surfaceContainerHighest = hslToColor(h: hue, s: neutralSat, l: 0.205)
        
        self.outline = hslToColor(h: hue, s: neutralSat * 1.5, l: 0.32)
        self.outlineVariant = hslToColor(h: hue, s: neutralSat * 1.2, l: 0.18)
        
        self.textPrimary = Color(white: 0.96)
        self.textSecondary = Color(white: 0.65)
        self.textMuted = Color(white: 0.42)
    }
}

// ==============================================================================
// ARTWORK & COLOR EXTRACTION UTILITIES
// ==============================================================================

public class ArtworkExtractor {
    private static let imageCache = NSCache<NSString, NSImage>()
    private static var themeCache: [String: DynamicMaterialTheme] = [:]
    
    public static func loadArtworkAndTheme(for path: String, completion: @escaping (NSImage?, DynamicMaterialTheme) -> Void) {
        if let cachedImage = imageCache.object(forKey: path as NSString),
           let cachedTheme = themeCache[path] {
            completion(cachedImage, cachedTheme)
            return
        }
        
        DispatchQueue.global(qos: .userInitiated).async {
            let artwork = extractArtwork(from: path)
            var theme: DynamicMaterialTheme = .defaultTheme
            
            if let artwork = artwork {
                imageCache.setObject(artwork, forKey: path as NSString)
                if let seed = extractSeedColor(from: artwork) {
                    theme = DynamicMaterialTheme(seedR: seed.r, seedG: seed.g, seedB: seed.b)
                }
            }
            themeCache[path] = theme
            
            DispatchQueue.main.async {
                completion(artwork, theme)
            }
        }
    }
    
    public static func extractArtwork(from filePath: String) -> NSImage? {
        let url = URL(fileURLWithPath: filePath)
        
        // 1. Check embedded ID3 / FLAC metadata via AVFoundation
        let asset = AVURLAsset(url: url)
        let items = AVMetadataItem.metadataItems(from: asset.metadata, withKey: AVMetadataKey.commonKeyArtwork, keySpace: .common)
        for item in items {
            if let data = item.dataValue, let image = NSImage(data: data) {
                return image
            }
        }
        
        // 2. Fallback: check adjacent directory image files (cover.jpg, folder.jpg, artwork.png)
        let parentDir = url.deletingLastPathComponent()
        let commonNames = ["cover.jpg", "cover.png", "folder.jpg", "folder.png", "artwork.jpg", "artwork.png", "front.jpg"]
        for name in commonNames {
            let imgUrl = parentDir.appendingPathComponent(name)
            if FileManager.default.fileExists(atPath: imgUrl.path), let img = NSImage(contentsOf: imgUrl) {
                return img
            }
        }
        
        return nil
    }
    
    public static func extractSeedColor(from image: NSImage) -> (r: Double, g: Double, b: Double)? {
        guard let cgImage = image.cgImage(forProposedRect: nil, context: nil, hints: nil) else { return nil }
        
        let width = 32
        let height = 32
        var rawData = [UInt8](repeating: 0, count: width * height * 4)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo = CGImageAlphaInfo.premultipliedLast.rawValue
        
        guard let context = CGContext(
            data: &rawData,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: colorSpace,
            bitmapInfo: bitmapInfo
        ) else { return nil }
        
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        
        var bestColor: (r: Double, g: Double, b: Double)? = nil
        var maxScore: Double = -1.0
        
        for i in stride(from: 0, to: rawData.count, by: 4) {
            let r = Double(rawData[i]) / 255.0
            let g = Double(rawData[i+1]) / 255.0
            let b = Double(rawData[i+2]) / 255.0
            
            let maxC = max(r, max(g, b))
            let minC = min(r, min(g, b))
            let delta = maxC - minC
            let sat = maxC == 0 ? 0 : delta / maxC
            let lum = 0.299 * r + 0.587 * g + 0.114 * b
            
            // Android Monet Filter: prioritize vibrant saturated tones, ignore muddy darks and washed out lights
            if sat > 0.22 && lum > 0.16 && lum < 0.84 {
                let score = sat * 0.70 + (1.0 - abs(lum - 0.50)) * 0.30
                if score > maxScore {
                    maxScore = score
                    bestColor = (r, g, b)
                }
            }
        }
        return bestColor
    }
}

// ==============================================================================
// TACTILE INTERACTIVE CONTROLS (Guarantees 100% Full-Hit-Area Clickability on macOS)
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
                .contentShape(Circle()) // 100% of circle bounds intercepts mouse clicks!
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
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(TsrossaColors.surfaceContainer.opacity(opacity))
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
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
// AUTHENTIC ANDROID TSROSSA LOGO LOADER & VIEW (macOS Dock + Desktop UI)
// ==============================================================================

public enum TsrossaBrandLogo {
    public static let launcherImage: NSImage? = {
        if let bundlePath = Bundle.main.path(forResource: "ic_launcher", ofType: "png"),
           let img = NSImage(contentsOfFile: bundlePath) {
            return img
        }
        let repoPath = "/Users/alen/Music/tsrossa/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
        if FileManager.default.fileExists(atPath: repoPath),
           let img = NSImage(contentsOfFile: repoPath) {
            return img
        }
        return nil
    }()
    
    public static let foregroundImage: NSImage? = {
        if let bundlePath = Bundle.main.path(forResource: "ic_launcher_foreground", ofType: "png"),
           let img = NSImage(contentsOfFile: bundlePath) {
            return img
        }
        let repoPath = "/Users/alen/Music/tsrossa/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png"
        if FileManager.default.fileExists(atPath: repoPath),
           let img = NSImage(contentsOfFile: repoPath) {
            return img
        }
        return launcherImage
    }()
    
    public static func applyMacDockIcon() {
        if let icon = launcherImage {
            NSApplication.shared.applicationIconImage = icon
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
            // Exact radial background from Android ic_launcher_background.xml
            RoundedRectangle(cornerRadius: cornerRadius)
                .fill(
                    RadialGradient(
                        colors: [
                            Color(red: 0.118, green: 0.071, blue: 0.141), // #1E1224
                            Color(red: 0.063, green: 0.047, blue: 0.086), // #100C16
                            Color(red: 0.027, green: 0.020, blue: 0.039)  // #07050A
                        ],
                        center: .center,
                        startRadius: 2,
                        endRadius: size * 0.72
                    )
                )
                .frame(width: size, height: size)
            
            // Subtle crimson aura glow from ic_launcher_background.xml (#47E11D48)
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
                    .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            } else if let img = TsrossaBrandLogo.launcherImage {
                Image(nsImage: img)
                    .resizable()
                    .interpolation(.high)
                    .antialiased(true)
                    .aspectRatio(contentMode: .fill)
                    .frame(width: size, height: size)
                    .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            }
        }
        .frame(width: size, height: size)
        .overlay(
            RoundedRectangle(cornerRadius: cornerRadius)
                .stroke(Color.white.opacity(0.14), lineWidth: 1)
        )
    }
}

