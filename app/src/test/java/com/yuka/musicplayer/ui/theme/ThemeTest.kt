package com.yuka.musicplayer.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {

    @Test
    fun testResolveM3TonalSurfaceHasGlassmorphicAlphaByDefault() {
        val base = Color(0xFF101014)
        val accent = Color(0xFF00FF00)
        val resolved = resolveM3TonalSurface(base, accent)

        assertEquals("Surface alpha should be 0.85f by default for glassmorphism", 0.85f, resolved.alpha, 0.01f)
        assertTrue("Surface red should blend between base and accent", resolved.red > 0f || resolved.green > 0f)
    }

    @Test
    fun testResolveButtonGlassColorHasGlassmorphicAlpha() {
        val accent = Color(0xFF00E5FF)
        val darkUnselected = resolveButtonGlassColor(accent = accent, isDark = true, isSelected = false)
        val darkSelected = resolveButtonGlassColor(accent = accent, isDark = true, isSelected = true)

        assertEquals(0.85f, darkUnselected.alpha, 0.01f)
        assertEquals(0.85f, darkSelected.alpha, 0.01f)
        assertTrue("Selected button should have higher accent tint than unselected", darkSelected.blue >= darkUnselected.blue)
    }

    @Test
    fun testArtworkBackgroundModeHasStandardOpaqueModalContainerAlphaLikeSignature() {
        val accent = Color(0xFF00E5FF)

        val artworkBase = computeRawM3SurfaceBase("ARTWORK", accent, isDark = true, style = "BLUR_OPACITY")
        val signatureBase = computeRawM3SurfaceBase("SIGNATURE", accent, isDark = true, style = "BLUR_OPACITY")
        assertEquals("ARTWORK base surface should retain standard alpha 0.65f", 0.65f, artworkBase.alpha, 0.01f)
        assertEquals(signatureBase.alpha, artworkBase.alpha, 0.001f)
        assertTrue("ARTWORK base should carry stronger accent tint than SIGNATURE", artworkBase.blue > signatureBase.blue)

        val artworkLow = computeRawM3SurfaceContainerLow("ARTWORK", accent, isDark = true, style = "BLUR_OPACITY")
        val signatureLow = computeRawM3SurfaceContainerLow("SIGNATURE", accent, isDark = true, style = "BLUR_OPACITY")
        assertEquals("ARTWORK containerLow should retain standard alpha 0.68f", 0.68f, artworkLow.alpha, 0.01f)
        assertEquals(signatureLow.alpha, artworkLow.alpha, 0.001f)

        val artworkMid = computeRawM3SurfaceContainer("ARTWORK", accent, isDark = true, style = "BLUR_OPACITY")
        val signatureMid = computeRawM3SurfaceContainer("SIGNATURE", accent, isDark = true, style = "BLUR_OPACITY")
        assertEquals("ARTWORK container should retain standard alpha 0.72f", 0.72f, artworkMid.alpha, 0.01f)
        assertEquals(signatureMid.alpha, artworkMid.alpha, 0.001f)

        val artworkHigh = computeRawM3SurfaceContainerHigh("ARTWORK", accent, isDark = true, style = "BLUR_OPACITY")
        val signatureHigh = computeRawM3SurfaceContainerHigh("SIGNATURE", accent, isDark = true, style = "BLUR_OPACITY")
        assertEquals("ARTWORK containerHigh should retain standard alpha 0.76f", 0.76f, artworkHigh.alpha, 0.01f)
        assertEquals(signatureHigh.alpha, artworkHigh.alpha, 0.001f)

        // SOLID style should remain 1.0f opaque even when ARTWORK is selected
        val solidArtworkBase = computeRawM3SurfaceBase("ARTWORK", accent, isDark = true, style = "SOLID")
        assertEquals(1.0f, solidArtworkBase.alpha, 0.001f)
    }
}
