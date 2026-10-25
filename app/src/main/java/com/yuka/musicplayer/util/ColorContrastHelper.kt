package com.yuka.musicplayer.util

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Ensures dynamic accent colors extracted from album art have sufficient luminance,
 * saturation, and contrast against dark UI backgrounds (#000000 / #070A12).
 */
object ColorContrastHelper {

    /**
     * Calculates WCAG relative luminance of a Compose Color.
     */
    fun calculateLuminance(color: Color): Float {
        fun channelLum(c: Float): Float {
            return if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055) / 1.055), 2.4).toFloat()
        }
        val r = channelLum(color.red)
        val g = channelLum(color.green)
        val b = channelLum(color.blue)
        return (0.2126f * r + 0.7152f * g + 0.0722f * b)
    }

    data class SwatchData(val rgb: Int, val population: Int)

    /**
     * Hue-Family Chromatic Clustering & Accurate Dominant Swatch Selection (v4)
     * - Prevents dark shadows (e.g. #101028) and pale glare (#D0C8E0) from overriding true focal colors.
     * - True Monochrome detection: requires < 1.2% chromatic area and avgChroma < 0.065.
     * - 12-Bin Hue Family clustering (30° bins) to reward colors belonging to dominant color families.
     */
    fun selectDominantSwatchColor(swatches: List<SwatchData>): Color {
        if (swatches.isEmpty()) return Color(0xFFD8DCE4)
        if (swatches.size == 1) return Color(swatches[0].rgb)

        val totalPop = swatches.sumOf { it.population.toLong() }.coerceAtLeast(1L)
        val hsv = FloatArray(3)

        class ProcessedSwatch(
            val rgb: Int,
            val population: Int,
            val h: Float,
            val s: Float,
            val v: Float,
            val chroma: Float
        )

        var weightedChromaSum = 0.0
        var chromaticPopulation = 0L
        val processed = ArrayList<ProcessedSwatch>(swatches.size)

        for (swatch in swatches) {
            val r = (swatch.rgb shr 16 and 0xFF) / 255f
            val g = (swatch.rgb shr 8 and 0xFF) / 255f
            val b = (swatch.rgb and 0xFF) / 255f
            val maxVal = max(r, max(g, b))
            val minVal = min(r, min(g, b))
            val chroma = maxVal - minVal

            rgbToHsv(r, g, b, hsv)
            val h = hsv[0]
            val s = hsv[1]
            val v = hsv[2]

            val isVisiblyChromatic = (chroma >= 0.14f && v >= 0.18f && s >= 0.18f)
            if (isVisiblyChromatic) {
                chromaticPopulation += swatch.population
            }
            weightedChromaSum += chroma.toDouble() * swatch.population
            processed.add(ProcessedSwatch(swatch.rgb, swatch.population, h, s, v, chroma))
        }

        val weightedAvgChroma = (weightedChromaSum / totalPop).toFloat()
        val chromaticRatio = chromaticPopulation.toDouble() / totalPop

        // True Monochrome Gate:
        // Only classify as monochrome if visibly chromatic pixels are under 1.2% AND weighted average chroma < 0.065
        if (chromaticRatio < 0.012 && weightedAvgChroma < 0.065f) {
            return Color(0xFFD8DCE4)
        }

        // Two-Stage Selection:
        // Stage A: Filter Candidates
        // Filter out pitch-black shadows (v < 0.18f) and near-white glare (v > 0.94f && s < 0.18f)
        var candidates = processed.filter {
            it.chroma >= 0.14f && it.v >= 0.18f && it.s >= 0.18f && !(it.v > 0.94f && it.s < 0.18f)
        }
        if (candidates.isEmpty()) {
            candidates = processed.filter { it.v in 0.18f..0.96f && it.s >= 0.08f }
        }
        if (candidates.isEmpty()) {
            candidates = processed
        }

        // Stage B: 12-Bin Hue Family Dominance (30° each)
        val binScores = DoubleArray(12)
        for (c in candidates) {
            val bin = ((c.h / 30f).toInt()) % 12
            binScores[bin] += c.population.toDouble() * Math.pow(c.chroma.toDouble(), 1.2)
        }

        // Circular smoothing across adjacent bins (25% left, 50% center, 25% right)
        val smoothBinScores = DoubleArray(12)
        for (b in 0 until 12) {
            val left = binScores[(b + 11) % 12]
            val center = binScores[b]
            val right = binScores[(b + 1) % 12]
            smoothBinScores[b] = left * 0.25 + center * 0.50 + right * 0.25
        }
        val maxBinScore = smoothBinScores.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

        val maxCandidatePop = candidates.maxOf { it.population }.toDouble().coerceAtLeast(1.0)
        var bestScore = -1.0
        var bestCandidate = candidates[0]

        for (c in candidates) {
            val popRatio = c.population.toDouble() / maxCandidatePop
            val bin = ((c.h / 30f).toInt()) % 12
            val familyRatio = (smoothBinScores[bin] / maxBinScore).coerceIn(0.1, 1.0)

            val brightnessWeight = when {
                c.v < 0.25f -> Math.pow((c.v / 0.25f).toDouble(), 2.0)
                c.v in 0.35f..0.92f -> 1.0
                c.v > 0.92f -> (1.0 - (c.v - 0.92f) * 4.0).toDouble().coerceIn(0.2, 1.0)
                else -> 0.7 + ((c.v - 0.25f) / 0.10f) * 0.3
            }

            // Score balances area population, color family dominance, rich chroma, and brightness weight
            val score = Math.pow(popRatio, 0.30) *
                        Math.pow(familyRatio, 0.45) *
                        Math.pow(c.chroma.toDouble(), 1.35) *
                        brightnessWeight

            if (score > bestScore) {
                bestScore = score
                bestCandidate = c
            }
        }

        return Color(bestCandidate.rgb)
    }

    /**
     * Normalizes an extracted album art color:
     * - Clamps minimum luminance so text and button borders are never invisible against black.
     * - If saturation is near-zero (greyscale/monochrome art), infuses fallback tint.
     * - Clamps minimum brightness/lightness in HSV space.
     */
    @Suppress("UNUSED_PARAMETER")
    fun normalizeDynamicAccent(
        color: Color,
        fallbackColor: Color = Color(0xFFFF85A1)
    ): Color {
        val r = color.red
        val g = color.green
        val b = color.blue

        val maxVal = max(r, max(g, b))
        val minVal = min(r, min(g, b))
        val delta = maxVal - minVal

        // Case 1: Achromatic / Monochrome album art (black & white / greyscale)
        // If chroma is negligible, preserve pure elegant silver platinum
        if (delta < 0.08f) {
            val silver = (0.86f + maxVal * 0.12f).coerceIn(0.86f, 0.98f)
            return Color(silver, silver, silver, 1.0f)
        }

        // Case 2: Chromatic album art -> Strictly preserve exact Hue in HSV space
        val hsv = FloatArray(3)
        rgbToHsv(r, g, b, hsv)

        // hsv[0] is Hue (0..360) -> strictly preserved
        // hsv[1] is Saturation (0..1) -> keep vibrant but prevent muddy oversaturation or washed-out pale tones
        val origSat = hsv[1]
        hsv[1] = if (origSat >= 0.20f) {
            hsv[1].coerceIn(0.35f, 0.88f)
        } else {
            hsv[1].coerceIn(0.22f, 0.45f)
        }

        // hsv[2] is Value (0..1) -> calibrate brightness for dark theme (#000000 / #070A12)
        // Ensure contrast floor: minimum brightness 0.74f, maximum 0.95f
        hsv[2] = hsv[2].coerceIn(0.74f, 0.95f)

        return hsvToColor(hsv[0], hsv[1], hsv[2])
    }

    private fun rgbToHsv(r: Float, g: Float, b: Float, outHsv: FloatArray) {
        val maxVal = max(r, max(g, b))
        val minVal = min(r, min(g, b))
        val delta = maxVal - minVal

        outHsv[2] = maxVal // Value

        if (maxVal == 0f || delta == 0f) {
            outHsv[0] = 0f
            outHsv[1] = 0f
            return
        }

        outHsv[1] = delta / maxVal // Saturation

        var hue = when (maxVal) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        if (hue < 0f) hue += 360f
        outHsv[0] = hue
    }

    private fun hsvToColor(h: Float, s: Float, v: Float): Color {
        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c

        val (rPrime, gPrime, bPrime) = when (((h / 60f).toInt()) % 6) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        return Color(
            (rPrime + m).coerceIn(0f, 1f),
            (gPrime + m).coerceIn(0f, 1f),
            (bPrime + m).coerceIn(0f, 1f),
            1.0f
        )
    }

    /**
     * Resolves accent color considering user mode (WALLPAPER, DYNAMIC, FIXED),
     * missing art, wallpaper Material You color, and fallback palette.
     */
    fun resolveAccent(
        accentMode: String,
        trackDominantColor: Color?,
        userFixedColor: Color,
        wallpaperColor: Color? = null
    ): Color {
        return when (accentMode) {
            "WALLPAPER" -> wallpaperColor ?: userFixedColor
            "FIXED" -> userFixedColor
            "DYNAMIC" -> {
                if (trackDominantColor == null || trackDominantColor == Color(0xFF00FF00)) {
                    wallpaperColor ?: userFixedColor
                } else {
                    normalizeDynamicAccent(trackDominantColor, userFixedColor)
                }
            }
            else -> {
                if (trackDominantColor != null && trackDominantColor != Color(0xFF00FF00)) {
                    normalizeDynamicAccent(trackDominantColor, userFixedColor)
                } else {
                    wallpaperColor ?: userFixedColor
                }
            }
        }
    }

    /**
     * Ensures an accent color has appropriate contrast for the active theme (Dark vs Light).
     * In Light mode, bright whites, pastel yellows, or neon greens are darkened to ensure legibility.
     */
    fun ensureThemeContrast(color: Color, isDarkTheme: Boolean): Color {
        if (isDarkTheme) {
            val lum = calculateLuminance(color)
            if (lum < 0.18f) {
                return normalizeDynamicAccent(color)
            }
            return color
        } else {
            val r = color.red
            val g = color.green
            val b = color.blue
            val maxVal = max(r, max(g, b))
            val minVal = min(r, min(g, b))
            val delta = maxVal - minVal

            if (delta < 0.08f) {
                val darkVal = (0.16f + (1f - maxVal) * 0.12f).coerceIn(0.12f, 0.30f)
                return Color(darkVal, darkVal, darkVal, 1.0f)
            }

            val hsv = FloatArray(3)
            rgbToHsv(r, g, b, hsv)
            hsv[1] = hsv[1].coerceIn(0.55f, 0.95f)
            hsv[2] = hsv[2].coerceIn(0.30f, 0.60f)

            var candidate = hsvToColor(hsv[0], hsv[1], hsv[2])
            while (calculateLuminance(candidate) >= 0.35f && hsv[2] > 0.20f) {
                hsv[2] -= 0.04f
                candidate = hsvToColor(hsv[0], hsv[1], hsv[2])
            }
            return candidate
        }
    }
}
