package com.yuka.musicplayer.util

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AlbumArtThumbnailCacheTest {

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
}
