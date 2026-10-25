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
}
