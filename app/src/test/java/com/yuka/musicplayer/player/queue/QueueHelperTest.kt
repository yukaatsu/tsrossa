package com.yuka.musicplayer.player.queue

import com.yuka.musicplayer.model.RepeatMode
import com.yuka.musicplayer.model.TrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class QueueHelperTest {

    private val fileA = File("/music/track_a.flac")
    private val fileB = File("/music/track_b.flac")
    private val fileC = File("/music/track_c.flac")
    private val activeList = listOf(fileA, fileB, fileC)

    private fun createTrackInfo(file: File): TrackInfo {
        return TrackInfo(
            file = file,
            title = file.nameWithoutExtension,
            artist = "Test Artist",
            album = "Test Album",
            year = "2026",
            durationSeconds = 180.0,
            coverArt = null,
            dominantColor = androidx.compose.ui.graphics.Color(0xFF00FF00)
        )
    }

    @Test
    fun testPriorityQueuePreemptsNormalActiveList() {
        val currentTrack = createTrackInfo(fileA)
        val priorityQueue = listOf(fileC)

        val result = QueueHelper.resolveNextTrack(
            isAutoAdvance = true,
            consumeQueue = true,
            priorityQueue = priorityQueue,
            currentTrack = currentTrack,
            repeatMode = RepeatMode.OFF,
            activeList = activeList,
            isShuffleEnabled = false,
            shuffledList = emptyList(),
            currentShuffleIndex = -1
        )

        assertEquals(fileC, result.nextFile)
        assertEquals(emptyList<File>(), result.updatedQueue)
    }

    @Test
    fun testLinearPlaybackAdvancesToNext() {
        val currentTrack = createTrackInfo(fileA)

        val result = QueueHelper.resolveNextTrack(
            isAutoAdvance = true,
            consumeQueue = false,
            priorityQueue = emptyList(),
            currentTrack = currentTrack,
            repeatMode = RepeatMode.OFF,
            activeList = activeList,
            isShuffleEnabled = false,
            shuffledList = emptyList(),
            currentShuffleIndex = -1
        )

        assertEquals(fileB, result.nextFile)
    }

    @Test
    fun testRepeatOneReturnsCurrentTrackOnAutoAdvance() {
        val currentTrack = createTrackInfo(fileB)

        val result = QueueHelper.resolveNextTrack(
            isAutoAdvance = true,
            consumeQueue = false,
            priorityQueue = emptyList(),
            currentTrack = currentTrack,
            repeatMode = RepeatMode.ONE,
            activeList = activeList,
            isShuffleEnabled = false,
            shuffledList = emptyList(),
            currentShuffleIndex = -1
        )

        assertEquals(fileB, result.nextFile)
    }

    @Test
    fun testRepeatOffStopsAtEndOfList() {
        val currentTrack = createTrackInfo(fileC)

        val result = QueueHelper.resolveNextTrack(
            isAutoAdvance = true,
            consumeQueue = false,
            priorityQueue = emptyList(),
            currentTrack = currentTrack,
            repeatMode = RepeatMode.OFF,
            activeList = activeList,
            isShuffleEnabled = false,
            shuffledList = emptyList(),
            currentShuffleIndex = -1
        )

        assertNull(result.nextFile)
    }

    @Test
    fun testRepeatAllWrapsToFirstTrack() {
        val currentTrack = createTrackInfo(fileC)

        val result = QueueHelper.resolveNextTrack(
            isAutoAdvance = true,
            consumeQueue = false,
            priorityQueue = emptyList(),
            currentTrack = currentTrack,
            repeatMode = RepeatMode.ALL,
            activeList = activeList,
            isShuffleEnabled = false,
            shuffledList = emptyList(),
            currentShuffleIndex = -1
        )

        assertEquals(fileA, result.nextFile)
    }

    @Test
    fun testEndOfPlaylistLinearStopsWhenRepeatOff() {
        val track1 = File("/music/p1_track1.flac")
        val track2 = File("/music/p1_track2.flac")
        val playlistTracks = listOf(track1, track2)
        val currentTrack = createTrackInfo(track2)

        val result = QueueHelper.resolveNextTrack(
            isAutoAdvance = true,
            consumeQueue = false,
            priorityQueue = emptyList(),
            currentTrack = currentTrack,
            repeatMode = RepeatMode.OFF,
            activeList = playlistTracks,
            isShuffleEnabled = false,
            shuffledList = emptyList(),
            currentShuffleIndex = -1
        )

        assertNull(result.nextFile)
    }

    @Test
    fun testEndOfPlaylistShuffleStopsWhenRepeatOff() {
        val track1 = File("/music/p1_track1.flac")
        val track2 = File("/music/p1_track2.flac")
        val playlistTracks = listOf(track1, track2)
        val shuffledDeck = listOf(track2, track1)
        val currentTrack = createTrackInfo(track1)

        val result = QueueHelper.resolveNextTrack(
            isAutoAdvance = true,
            consumeQueue = false,
            priorityQueue = emptyList(),
            currentTrack = currentTrack,
            repeatMode = RepeatMode.OFF,
            activeList = playlistTracks,
            isShuffleEnabled = true,
            shuffledList = shuffledDeck,
            currentShuffleIndex = 1 // Last index in shuffled deck
        )

        assertNull(result.nextFile)
    }

    @Test
    fun testResolveNextTrackDoesNotMatchDifferentFolderTrackWithSameName() {
        val albumATrack1 = File("/music/AlbumA/01 - Intro.flac")
        val albumBTrack1 = File("/music/AlbumB/01 - Intro.flac")
        val albumBTrack2 = File("/music/AlbumB/02 - Song.flac")
        val albumBTracks = listOf(albumBTrack1, albumBTrack2)
        val currentTrackFromAlbumA = createTrackInfo(albumATrack1)

        val result = QueueHelper.resolveNextTrack(
            isAutoAdvance = false,
            consumeQueue = false,
            priorityQueue = emptyList(),
            currentTrack = currentTrackFromAlbumA,
            repeatMode = RepeatMode.OFF,
            activeList = albumBTracks,
            isShuffleEnabled = false,
            shuffledList = emptyList(),
            currentShuffleIndex = -1
        )

        // When current track from AlbumA is not in AlbumB, it should not match AlbumB's track 1 as current and jump to track 2
        // Since currentIndex is -1, it plays the first track of AlbumB (not skipping to track 2)
        assertEquals(albumBTrack1, result.nextFile)
    }
}

