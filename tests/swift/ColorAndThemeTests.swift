import Foundation
import AppKit

public func runColorAndThemeTests() {
    print("==> Running ColorAndThemeTests...")

    TestSuite.run("12-Bin Hue-Family clustering ignores near-black background and picks vibrant accent") {
        // 80% piksel hitam pekat (#101012), 20% piksel merah-oranye terang (#FF4500)
        let darkPixel: (r: UInt8, g: UInt8, b: UInt8) = (16, 16, 18)
        let redPixel: (r: UInt8, g: UInt8, b: UInt8) = (255, 69, 0)
        var pixels: [(r: UInt8, g: UInt8, b: UInt8)] = Array(repeating: darkPixel, count: 80)
        pixels.append(contentsOf: Array(repeating: redPixel, count: 20))

        let extracted = ColorContrastHelper.extractDominantAccentRGB(from: pixels)
        // Harus memilih keluarga warna merah/oranye (R jauh lebih tinggi dari B), bukan abu-abu gelap
        try TestSuite.assertTrue(extracted.r > 0.70, "Expected vibrant red channel > 0.70, got \(extracted.r)")
        try TestSuite.assertTrue(extracted.b < 0.35, "Expected low blue channel < 0.35, got \(extracted.b)")
    }

    TestSuite.run("WCAG contrast helper guarantees readable foreground on both dark and light surfaces") {
        let darkBg = (r: 0.08, g: 0.08, b: 0.10)
        let adjustedForDark = ColorContrastHelper.ensureContrast((r: 0.15, g: 0.20, b: 0.25), against: darkBg, minRatio: 4.5)
        let ratioDark = ColorContrastHelper.contrastRatio(adjustedForDark, darkBg)
        try TestSuite.assertTrue(ratioDark >= 4.49, "Contrast on dark bg was \(ratioDark)")

        let lightBg = (r: 0.95, g: 0.95, b: 0.97)
        let adjustedForLight = ColorContrastHelper.ensureContrast((r: 0.85, g: 0.90, b: 0.92), against: lightBg, minRatio: 4.5)
        let ratioLight = ColorContrastHelper.contrastRatio(adjustedForLight, lightBg)
        try TestSuite.assertTrue(ratioLight >= 4.49, "Contrast on light bg was \(ratioLight)")
    }
}
