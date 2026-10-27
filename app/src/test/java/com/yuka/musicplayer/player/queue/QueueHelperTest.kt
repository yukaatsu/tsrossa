package com.yuka.musicplayer.player.queue

import com.yuka.musicplayer.model.RepeatMode
import com.yuka.musicplayer.model.TrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

open class QueueHelperTest {

    private val track1 = File("/music/track1.flac")
    private val track2 = File("/music/track2.flac")
    private val track3 = File("/music/track3.flac")
    private val defaultList = listOf(track1, track2, track3)

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

    @Test
    fun `getNextTrackFile matches currentTrack by absolutePath even with distinct File instances`() {
        val list = listOf(File("/music/pl/song1.flac"), File("/music/pl/song2.flac"))
        val currentClone = File("/music/pl", "song1.flac")
        val next = QueueHelper.getNextTrackFile(
            isAutoAdvance = true,
            consumeQueue = true,
            repeatMode = 0,
            currentTrack = currentClone,
            upNextQueue = emptyList(),
            activeList = list,
            isShuffleEnabled = false,
            shuffledList = emptyList(),
            currentShuffleIndex = -1
        )
        assertEquals("/music/pl/song2.flac", next?.absolutePath)
    }

    @Test
    fun `getNextTrackFile in shuffle mode recovers index from shuffledList when currentShuffleIndex is stale`() {
        val shuffled = listOf(track2, track3, track1)
        // currentTrack is track2 (index 0 in shuffled), but currentShuffleIndex was left at -1
        val next = QueueHelper.getNextTrackFile(
            isAutoAdvance = true,
            consumeQueue = true,
            repeatMode = 0,
            currentTrack = track2,
            upNextQueue = emptyList(),
            activeList = defaultList,
            isShuffleEnabled = true,
            shuffledList = shuffled,
            currentShuffleIndex = -1
        )
        assertEquals(track3, next)
    }

    @Test
    fun `resolvePlaylistTracks resolves files from map and falls back to fallbackPaths`() {
        val map = mapOf(
            track1.absolutePath to track1,
            track2.absolutePath to track2,
            track3.absolutePath to track3
        )
        val resolved = QueueHelper.resolvePlaylistTracks(
            playlistTrackPaths = listOf(track1.absolutePath, track3.absolutePath),
            fallbackTrackPaths = emptyList(),
            supportedFileMap = map
        )
        assertEquals(listOf(track1, track3), resolved)
    }

    @Test
    fun `indexOfTrack finds index by absolutePath across distinct instances and returns -1 for missing or null`() {
        val list = listOf(File("/dir/a.mp3"), File("/dir/b.mp3"))
        val target = File("/dir", "b.mp3")
        assertEquals(1, QueueHelper.indexOfTrack(list, target))
        assertEquals(-1, QueueHelper.indexOfTrack(list, File("/dir/c.mp3")))
        assertEquals(-1, QueueHelper.indexOfTrack(list, null))
    }

    @Test
    fun `calculatePreviousTrack recovers stale shuffle index and returns previous track`() {
        val shuffled = listOf(track2, track3, track1)
        // currentTrack is track3 (index 1 in shuffled), but currentShuffleIndex is -1
        val prev = QueueHelper.calculatePreviousTrack(
            playbackHistory = mutableListOf(),
            activeList = defaultList,
            currentTrack = track3,
            isShuffleEnabled = true,
            shuffledList = shuffled,
            currentShuffleIndex = -1,
            repeatMode = 0
        )
        assertEquals(track2, prev)
    }

    @Test
    fun `resolvePlaylistTracks uses fallbackTrackPaths when playlistTrackPaths is null or empty`() {
        val map = mapOf(
            track2.absolutePath to track2
        )
        val resolved = QueueHelper.resolvePlaylistTracks(
            playlistTrackPaths = null,
            fallbackTrackPaths = listOf(track2.absolutePath),
            supportedFileMap = map
        )
        assertEquals(listOf(track2), resolved)
    }

    @Test
    fun `getNextTrackFile respects repeatMode All and One`() {
        // Repeat ALL (2) wraps to first track when at end of activeList
        val nextAll = QueueHelper.getNextTrackFile(
            isAutoAdvance = true,
            consumeQueue = true,
            repeatMode = 2,
            currentTrack = track3,
            upNextQueue = emptyList(),
            activeList = defaultList,
            isShuffleEnabled = false,
            shuffledList = emptyList(),
            currentShuffleIndex = -1
        )
        assertEquals(track1, nextAll)

        // Repeat ONE (1) returns current track when auto advance
        val nextOne = QueueHelper.getNextTrackFile(
            isAutoAdvance = true,
            consumeQueue = true,
            repeatMode = 1,
            currentTrack = track2,
            upNextQueue = emptyList(),
            activeList = defaultList,
            isShuffleEnabled = false,
            shuffledList = emptyList(),
            currentShuffleIndex = -1
        )
        assertEquals(track2, nextOne)
    }

    @Test
    fun `resolveNextTrack recovers shuffle index when currentShuffleIndex is stale or -1`() {
        val shuffled = listOf(track2, track3, track1)
        // currentTrack is track2 (index 0 in shuffled deck), but currentShuffleIndex is -1
        val currentTrackInfo = createTrackInfo(track2)
        val result = QueueHelper.resolveNextTrack(
            isAutoAdvance = true,
            consumeQueue = true,
            priorityQueue = emptyList(),
            currentTrack = currentTrackInfo,
            repeatMode = RepeatMode.OFF,
            activeList = defaultList,
            isShuffleEnabled = true,
            shuffledList = shuffled,
            currentShuffleIndex = -1
        )
        assertEquals(track3, result.nextFile)
        assertEquals(1, result.updatedShuffleIndex)
    }
}

