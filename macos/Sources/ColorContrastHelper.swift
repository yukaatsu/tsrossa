import Foundation
import AppKit

public struct RGBColor: Equatable {
    public var r: Double
    public var g: Double
    public var b: Double

    public init(r: Double, g: Double, b: Double) {
        self.r = r
        self.g = g
        self.b = b
    }

    public var hexString: String {
        let ri = Int(round(min(max(r, 0.0), 1.0) * 255.0))
        let gi = Int(round(min(max(g, 0.0), 1.0) * 255.0))
        let bi = Int(round(min(max(b, 0.0), 1.0) * 255.0))
        return String(format: "#%02X%02X%02X", ri, gi, bi)
    }

    public var nsColor: NSColor {
        NSColor(srgbRed: r, green: g, blue: b, alpha: 1.0)
    }
}

public enum ColorContrastHelper {
    public static func calculateLuminance(_ color: RGBColor) -> Double {
        func channelLum(_ c: Double) -> Double {
            return c <= 0.03928 ? (c / 12.92) : pow((c + 0.055) / 1.055, 2.4)
        }
        let r = channelLum(color.r)
        let g = channelLum(color.g)
        let b = channelLum(color.b)
        return (0.2126 * r + 0.7152 * g + 0.0722 * b)
    }

    public static func contrastRatio(_ c1: (r: Double, g: Double, b: Double), _ c2: (r: Double, g: Double, b: Double)) -> Double {
        let l1 = calculateLuminance(RGBColor(r: c1.r, g: c1.g, b: c1.b))
        let l2 = calculateLuminance(RGBColor(r: c2.r, g: c2.g, b: c2.b))
        let lighter = max(l1, l2)
        let darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    public static func ensureContrast(_ color: (r: Double, g: Double, b: Double), against bg: (r: Double, g: Double, b: Double), minRatio: Double = 4.5) -> (r: Double, g: Double, b: Double) {
        var current = color
        var ratio = contrastRatio(current, bg)
        if ratio >= minRatio { return current }

        let bgLum = calculateLuminance(RGBColor(r: bg.r, g: bg.g, b: bg.b))
        let shouldLighten = bgLum < 0.5

        for _ in 0..<30 {
            if ratio >= minRatio { break }
            if shouldLighten {
                current.r = min(1.0, current.r + 0.03)
                current.g = min(1.0, current.g + 0.03)
                current.b = min(1.0, current.b + 0.03)
            } else {
                current.r = max(0.0, current.r - 0.03)
                current.g = max(0.0, current.g - 0.03)
                current.b = max(0.0, current.b - 0.03)
            }
            ratio = contrastRatio(current, bg)
        }
        return current
    }

    /// Porting v4 12-Bin Hue-Family Chromatic Clustering dari Android ColorContrastHelper.kt
    public static func extractDominantAccentRGB(from pixels: [(r: UInt8, g: UInt8, b: UInt8)]) -> RGBColor {
        if pixels.isEmpty { return RGBColor(r: 0.85, g: 0.86, b: 0.89) }

        var swatchMap: [UInt32: Int] = [:]
        for p in pixels {
            // Quantize to 5-bit per channel to cluster nearby colors
            let qR = (UInt32(p.r) >> 3) << 3
            let qG = (UInt32(p.g) >> 3) << 3
            let qB = (UInt32(p.b) >> 3) << 3
            let key = (qR << 16) | (qG << 8) | qB
            swatchMap[key, default: 0] += 1
        }

        struct ProcessedSwatch {
            let r: Double
            let g: Double
            let b: Double
            let population: Int
            let h: Double // 0..360
            let s: Double // 0..1
            let v: Double // 0..1
            let chroma: Double
        }

        var processed: [ProcessedSwatch] = []
        var totalPop = 0
        var chromaticPopulation = 0
        var weightedChromaSum = 0.0

        for (rgbKey, pop) in swatchMap {
            totalPop += pop
            let r = Double((rgbKey >> 16) & 0xFF) / 255.0
            let g = Double((rgbKey >> 8) & 0xFF) / 255.0
            let b = Double(rgbKey & 0xFF) / 255.0

            let maxVal = max(r, max(g, b))
            let minVal = min(r, min(g, b))
            let chroma = maxVal - minVal

            var h = 0.0
            let s = maxVal == 0.0 ? 0.0 : (chroma / maxVal)
            let v = maxVal

            if chroma > 1e-5 {
                if maxVal == r {
                    h = 60.0 * fmod((g - b) / chroma, 6.0)
                } else if maxVal == g {
                    h = 60.0 * (((b - r) / chroma) + 2.0)
                } else {
                    h = 60.0 * (((r - g) / chroma) + 4.0)
                }
                if h < 0.0 { h += 360.0 }
            }

            if chroma >= 0.14 && v >= 0.18 && s >= 0.18 {
                chromaticPopulation += pop
            }
            weightedChromaSum += chroma * Double(pop)
            processed.append(ProcessedSwatch(r: r, g: g, b: b, population: pop, h: h, s: s, v: v, chroma: chroma))
        }

        let totalPopD = max(Double(totalPop), 1.0)
        let chromaticRatio = Double(chromaticPopulation) / totalPopD
        let weightedAvgChroma = weightedChromaSum / totalPopD

        // True Monochrome Gate
        if chromaticRatio < 0.012 && weightedAvgChroma < 0.065 {
            return RGBColor(r: 0.85, g: 0.86, b: 0.89)
        }

        var candidates = processed.filter {
            $0.chroma >= 0.14 && $0.v >= 0.18 && $0.s >= 0.18 && !($0.v > 0.94 && $0.s < 0.18)
        }
        if candidates.isEmpty {
            candidates = processed.filter { $0.v >= 0.18 && $0.v <= 0.96 && $0.s >= 0.08 }
        }
        if candidates.isEmpty {
            candidates = processed
        }

        // 12-Bin Hue Family Dominance (30 deg each)
        var binScores = [Double](repeating: 0.0, count: 12)
        for c in candidates {
            let bin = Int(c.h / 30.0) % 12
            binScores[bin] += Double(c.population) * pow(c.chroma, 1.2)
        }

        var smoothBinScores = [Double](repeating: 0.0, count: 12)
        for b in 0..<12 {
            let left = binScores[(b + 11) % 12]
            let center = binScores[b]
            let right = binScores[(b + 1) % 12]
            smoothBinScores[b] = left * 0.25 + center * 0.50 + right * 0.25
        }
        let maxBinScore = max(smoothBinScores.max() ?? 1.0, 1.0)
        let maxCandidatePop = max(Double(candidates.map { $0.population }.max() ?? 1), 1.0)

        var bestScore = -1.0
        var bestCandidate = candidates[0]

        for c in candidates {
            let popRatio = Double(c.population) / maxCandidatePop
            let bin = Int(c.h / 30.0) % 12
            let familyRatio = min(max(smoothBinScores[bin] / maxBinScore, 0.1), 1.0)

            let brightnessWeight: Double
            if c.v < 0.25 {
                brightnessWeight = pow(c.v / 0.25, 2.0)
            } else if c.v >= 0.35 && c.v <= 0.92 {
                brightnessWeight = 1.0
            } else if c.v > 0.92 {
                brightnessWeight = min(max(1.0 - (c.v - 0.92) * 4.0, 0.2), 1.0)
            } else {
                brightnessWeight = 0.7 + ((c.v - 0.25) / 0.10) * 0.3
            }

            let score = pow(popRatio, 0.30) * pow(familyRatio, 0.45) * pow(c.chroma, 1.35) * brightnessWeight
            if score > bestScore {
                bestScore = score
                bestCandidate = c
            }
        }

        return RGBColor(r: bestCandidate.r, g: bestCandidate.g, b: bestCandidate.b)
    }

    public static func extractDominantAccent(from image: NSImage) -> RGBColor {
        guard let tiff = image.tiffRepresentation,
              let rep = NSBitmapImageRep(data: tiff) else {
            return RGBColor(r: 0.85, g: 0.86, b: 0.89)
        }

        let width = rep.pixelsWide
        let height = rep.pixelsHigh
        guard width > 0 && height > 0 else { return RGBColor(r: 0.85, g: 0.86, b: 0.89) }

        let stepX = max(1, width / 40)
        let stepY = max(1, height / 40)
        var pixels: [(r: UInt8, g: UInt8, b: UInt8)] = []

        for y in stride(from: 0, to: height, by: stepY) {
            for x in stride(from: 0, to: width, by: stepX) {
                if let color = rep.colorAt(x: x, y: y)?.usingColorSpace(.sRGB) {
                    let r = UInt8(min(max(color.redComponent * 255.0, 0.0), 255.0))
                    let g = UInt8(min(max(color.greenComponent * 255.0, 0.0), 255.0))
                    let b = UInt8(min(max(color.blueComponent * 255.0, 0.0), 255.0))
                    pixels.append((r, g, b))
                }
            }
        }
        return extractDominantAccentRGB(from: pixels)
    }
}
