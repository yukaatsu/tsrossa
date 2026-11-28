package com.yuka.musicplayer.util

import androidx.compose.ui.graphics.Color
import com.yuka.musicplayer.ui.views.resolveArtworkScrimAlphas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumArtThumbnailCacheTest {

    private fun contrastRatio(fg: Color, bg: Color): Float {
        val l1 = ColorContrastHelper.calculateLuminance(fg)
        val l2 = ColorContrastHelper.calculateLuminance(bg)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun compositeOver(fg: Color, bg: Color): Color {
        val a = fg.alpha
        return Color(
            red = fg.red * a + bg.red * (1f - a),
            green = fg.green * a + bg.green * (1f - a),
            blue = fg.blue * a + bg.blue * (1f - a),
            alpha = 1f
        )
    }

    @Test
    fun testCacheKeyConsistency() {
        val path1 = "/storage/emulated/0/Music/track1.flac"
        val time1 = 1710000000000L

        val key1 = AlbumArtThumbnailCache.cacheKey(path1, time1)
        val key2 = AlbumArtThumbnailCache.cacheKey(path1, time1)
        assertEquals("Identical file and timestamp must yield same cache key", key1, key2)

        val keyDiffTime = AlbumArtThumbnailCache.cacheKey(path1, time1 + 5000L)
        assertNotEquals("Modified timestamp must yield different cache key", key1, keyDiffTime)

        val keyDiffPath = AlbumArtThumbnailCache.cacheKey("/storage/emulated/0/Music/track2.flac", time1)
        assertNotEquals("Different file path must yield different cache key", key1, keyDiffPath)
    }

    @Test
    fun testColorCachingMemoryLevel() {
        val path = "/storage/emulated/0/Music/test.mp3"
        val time = 123456789L
        val testColor = Color(0xFF00FF00)

        AlbumArtThumbnailCache.putCachedDominantColor(path, time, testColor)
        val retrieved = AlbumArtThumbnailCache.getCachedDominantColor(path, time)

        assertNotNull(retrieved)
        assertEquals(testColor, retrieved)
    }

    @Test
    fun testSoftKneeLumaCompression_isContinuousAndMonotonic() {
        var prev = WallpaperBlurHelper.compressHighlightLuma(0)
        assertEquals(0, prev)
        assertEquals(108, WallpaperBlurHelper.compressHighlightLuma(108))

        for (luma in 1..255) {
            val cur = WallpaperBlurHelper.compressHighlightLuma(luma)
            val delta = cur - prev
            assertTrue(
                "compressHighlightLuma must be monotonic non-decreasing and step <= 1 (at luma=$luma: prev=$prev, cur=$cur)",
                delta in 0..1
            )
            prev = cur
        }

        assertEquals("Max luma at 255 must be capped cleanly at 148", 148, WallpaperBlurHelper.compressHighlightLuma(255))
    }

    @Test
    fun testArtworkScrimProfile_preservesVibrancyAndWcagContrast() {
        // Dark Mode: vibrant transparency (upperMid <= 0.36f) while keeping WCAG AA contrast on pure white cover (luma capped at 148)
        val darkScrim = resolveArtworkScrimAlphas(isDark = true)
        assertTrue("Dark mode upperMid scrim should keep alpha <= 0.36f so artwork hues shine through", darkScrim.upperMidAlpha <= 0.36f)
        val maxDarkCoverC = 148f / 255f
        val worstDarkBackdrop = Color(maxDarkCoverC, maxDarkCoverC, maxDarkCoverC, 1f)
        val darkFg = Color(0xFFF5F5F5)
        val darkEdge = Color(0xFF07070A)
        val darkMid = Color(0xFF0A0A0E)
        listOf(
            darkEdge.copy(alpha = darkScrim.topAlpha),
            darkMid.copy(alpha = darkScrim.upperMidAlpha),
            darkMid.copy(alpha = darkScrim.lowerMidAlpha),
            darkEdge.copy(alpha = darkScrim.bottomAlpha)
        ).forEach { scrim ->
            val finalBg = compositeOver(scrim, worstDarkBackdrop)
            val ratio = contrastRatio(darkFg, finalBg)
            assertTrue("Dark mode WCAG AA contrast failed (ratio=$ratio)", ratio >= 4.5f)
        }

        // Light Mode: richer cover presence (upperMid <= 0.56f) while keeping WCAG AA contrast on pure black cover
        val lightScrim = resolveArtworkScrimAlphas(isDark = false)
        assertTrue("Light mode upperMid scrim should keep alpha <= 0.56f so pastel cover tint differs from Tonal M3", lightScrim.upperMidAlpha <= 0.56f)
        val worstLightBackdrop = Color(0f, 0f, 0f, 1f)
        val lightFg = Color(0xFF141419)
        val lightEdge = Color(0xFFFFFFFF)
        val lightMid = Color(0xFFF8F8FC)
        listOf(
            lightEdge.copy(alpha = lightScrim.topAlpha),
            lightMid.copy(alpha = lightScrim.upperMidAlpha),
            lightMid.copy(alpha = lightScrim.lowerMidAlpha),
            lightEdge.copy(alpha = lightScrim.bottomAlpha)
        ).forEach { scrim ->
            val finalBg = compositeOver(scrim, worstLightBackdrop)
            val ratio = contrastRatio(lightFg, finalBg)
            assertTrue("Light mode WCAG AA contrast failed (ratio=$ratio)", ratio >= 4.5f)
        }
    }

    @Test
    fun testResolveEffectiveBackdropBitmap_neverLeaksLibraryPreviewOntoActiveTrackWithoutArt() {
        // Cold start (no active track): library preview is shown
        assertEquals(
            "preview_green",
            com.yuka.musicplayer.ui.app.resolveEffectiveBackdropBitmap(
                activeTrackPath = "",
                currentCoverBitmap = null,
                lastValidCoverBitmap = "stale_song",
                libraryPreviewBitmap = "preview_green"
            )
        )

        // Active track missing embedded art: MUST return null, never leak library preview
        assertNull(
            com.yuka.musicplayer.ui.app.resolveEffectiveBackdropBitmap(
                activeTrackPath = "/music/no_art.flac",
                currentCoverBitmap = null,
                lastValidCoverBitmap = null,
                libraryPreviewBitmap = "preview_green"
            )
        )

        // Active track with art: prefers current cover art
        assertEquals(
            "song_art",
            com.yuka.musicplayer.ui.app.resolveEffectiveBackdropBitmap(
                activeTrackPath = "/music/song.flac",
                currentCoverBitmap = "song_art",
                lastValidCoverBitmap = "previous_art",
                libraryPreviewBitmap = "preview_green"
            )
        )
    }
}
