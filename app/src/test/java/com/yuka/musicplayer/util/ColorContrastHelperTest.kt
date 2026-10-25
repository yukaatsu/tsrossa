package com.yuka.musicplayer.util

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorContrastHelperTest {

    @Test
    fun testDarkColorIsNormalizedForContrast() {
        val darkBrown = Color(0xFF2B1D0C) // Very dark brown
        val normalized = ColorContrastHelper.normalizeDynamicAccent(darkBrown)
        val luminance = ColorContrastHelper.calculateLuminance(normalized)
        assertTrue("Normalized color must have sufficient luminance for dark backgrounds, got $luminance", luminance >= 0.18f)
    }

    @Test
    fun testMonochromeColorIsInfusedWithTint() {
        val pureGrey = Color(0xFF404040)
        val fallback = Color(0xFFFF85A1)
        val normalized = ColorContrastHelper.normalizeDynamicAccent(pureGrey, fallback)
        assertTrue("Zero saturation colors should blend with fallback tint", normalized != pureGrey)
    }

    @Test
    fun testNullOrDefaultArtworkFallsBackToUserAccent() {
        val userAccent = Color(0xFFA259FF)
        val chosen = ColorContrastHelper.resolveAccent(
            accentMode = "DYNAMIC",
            trackDominantColor = null,
            userFixedColor = userAccent
        )
        assertEquals(userAccent, chosen)
    }

    @Test
    fun testWallpaperModeResolvesWallpaperColor() {
        val userAccent = Color(0xFF00FF00)
        val wallpaperAccent = Color(0xFF00E5FF)
        val chosen = ColorContrastHelper.resolveAccent(
            accentMode = "WALLPAPER",
            trackDominantColor = Color(0xFFFF0000),
            userFixedColor = userAccent,
            wallpaperColor = wallpaperAccent
        )
        assertEquals(wallpaperAccent, chosen)
    }

    @Test
    fun testMonochromeFallbackPreservesAchromaticSilver() {
        val grey = Color(0xFF505050)
        val white = Color(0xFFFFFFFF)
        val normalized = ColorContrastHelper.normalizeDynamicAccent(grey, fallbackColor = white)
        // Red, green, blue should remain identical (pure silver-white)
        assertEquals(normalized.red, normalized.green, 0.001f)
        assertEquals(normalized.green, normalized.blue, 0.001f)
        assertTrue("Monochrome accent should be luminous (> 0.8f)", normalized.red >= 0.85f)
    }

    @Test
    fun testSelectDominantSwatch_SparklerNightCoverSelectsOrangeNotNavyShadow() {
        // Night background: near-black navy #101028 with huge population
        val navyShadow = ColorContrastHelper.SwatchData(0xFF101028.toInt(), 50000)
        // Glowing sparkler / orange hair: split across multiple swatches
        val orangeVivid = ColorContrastHelper.SwatchData(0xFFFF7A1A.toInt(), 4500)
        val orangeMid = ColorContrastHelper.SwatchData(0xFFE86010.toInt(), 3500)
        val amberGlow = ColorContrastHelper.SwatchData(0xFFFFAC30.toInt(), 2800)

        val swatches = listOf(navyShadow, orangeVivid, orangeMid, amberGlow)
        val selected = ColorContrastHelper.selectDominantSwatchColor(swatches)

        // Assert that the selected color is orange/amber (red > 0.7, blue < 0.4), NOT dark navy
        assertTrue("Selected color should be warm orange, not dark navy: R=${selected.red}, B=${selected.blue}",
            selected.red > 0.7f && selected.blue < 0.4f)
    }

    @Test
    fun testSelectDominantSwatch_KitsuneCoverSelectsCrimsonPinkNotMonochrome() {
        // Mostly dark background and white character robe
        val darkBg = ColorContrastHelper.SwatchData(0xFF18181A.toInt(), 30000)
        val whiteRobe = ColorContrastHelper.SwatchData(0xFFE2E4E8.toInt(), 25000)
        // Crimson-pink ear tips and rose petals split across swatches (~3.5% total)
        val crimson1 = ColorContrastHelper.SwatchData(0xFFDC244E.toInt(), 800)
        val crimson2 = ColorContrastHelper.SwatchData(0xFFC01840.toInt(), 700)
        val rosePink = ColorContrastHelper.SwatchData(0xFFE8486A.toInt(), 600)

        val swatches = listOf(darkBg, whiteRobe, crimson1, crimson2, rosePink)
        val selected = ColorContrastHelper.selectDominantSwatchColor(swatches)

        // Must not be fallback monochrome silver (#D8DCE4)
        assertTrue("Should not be monochrome silver", selected != Color(0xFFD8DCE4))
        // Must be crimson / rose (red > 0.65f, green < 0.4f)
        assertTrue("Selected color should be crimson/pink: R=${selected.red}, G=${selected.green}",
            selected.red > 0.65f && selected.green < 0.4f)
    }

    @Test
    fun testSelectDominantSwatch_FeastPeradabanSelectsGoldenYellow() {
        val darkFoliage1 = ColorContrastHelper.SwatchData(0xFF142416.toInt(), 35000)
        val darkFoliage2 = ColorContrastHelper.SwatchData(0xFF1A301E.toInt(), 20000)
        val goldenTitle = ColorContrastHelper.SwatchData(0xFFECC230.toInt(), 2500)
        val goldenCloth = ColorContrastHelper.SwatchData(0xFFD6A620.toInt(), 2000)

        val swatches = listOf(darkFoliage1, darkFoliage2, goldenTitle, goldenCloth)
        val selected = ColorContrastHelper.selectDominantSwatchColor(swatches)

        // Golden yellow has high red and green, low blue
        assertTrue("Selected color should be golden yellow: R=${selected.red}, G=${selected.green}, B=${selected.blue}",
            selected.red > 0.75f && selected.green > 0.55f && selected.blue < 0.35f)
    }

    @Test
    fun testSelectDominantSwatch_TrueMonochromeReturnsSilver() {
        val black = ColorContrastHelper.SwatchData(0xFF0F0F0F.toInt(), 40000)
        val midGrey = ColorContrastHelper.SwatchData(0xFF505050.toInt(), 30000)
        val lightGrey = ColorContrastHelper.SwatchData(0xFFB0B0B0.toInt(), 20000)
        val white = ColorContrastHelper.SwatchData(0xFFF5F5F5.toInt(), 10000)

        val swatches = listOf(black, midGrey, lightGrey, white)
        val selected = ColorContrastHelper.selectDominantSwatchColor(swatches)

        assertEquals("True monochrome must return platinum silver", Color(0xFFD8DCE4), selected)
    }

    @Test
    fun ensureThemeContrast_darkensBrightWhiteAndNeonInLightMode() {
        val whiteOnLight = ColorContrastHelper.ensureThemeContrast(Color.White, isDarkTheme = false)
        val greenOnLight = ColorContrastHelper.ensureThemeContrast(Color(0xFF00FF00), isDarkTheme = false)
        assertTrue(ColorContrastHelper.calculateLuminance(whiteOnLight) < 0.35f)
        assertTrue(ColorContrastHelper.calculateLuminance(greenOnLight) < 0.35f)
    }
}
