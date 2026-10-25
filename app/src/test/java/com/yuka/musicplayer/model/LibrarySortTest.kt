package com.yuka.musicplayer.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class LibrarySortTest {

    private val itemA = ScannedMusicItem(
        file = File("/music/A.flac"),
        isSupported = true,
        formatLabel = "FLAC",
        breadcrumb = "",
        title = "Alpha",
        artist = "Charlie",
        album = "Beta Album",
        albumArtist = "Main Artist",
        genre = "Rock",
        trackNumber = 2,
        durationMs = 240000L,
        year = 2020,
        composer = "Mozart",
        dateAdded = 1000L,
        dateModified = 5000L
    )

    private val itemB = ScannedMusicItem(
        file = File("/music/B.flac"),
        isSupported = true,
        formatLabel = "FLAC",
        breadcrumb = "",
        title = "Beta",
        artist = "Alice",
        album = "Alpha Album",
        albumArtist = "Various",
        genre = "Pop",
        trackNumber = 1,
        durationMs = 180000L,
        year = 2018,
        composer = "Beethoven",
        dateAdded = 2000L,
        dateModified = 4000L
    )

    private val list = listOf(itemA, itemB)

    @Test
    fun testDefaultSortIsTitleAscending() {
        val sorted = sortScannedItemsByField(list, SortField.TITLE, SortOrder.ASCENDING)
        assertEquals("Alpha", sorted[0].title)
        assertEquals("Beta", sorted[1].title)
    }

    @Test
    fun testSortTitleDescending() {
        val sorted = sortScannedItemsByField(list, SortField.TITLE, SortOrder.DESCENDING)
        assertEquals("Beta", sorted[0].title)
        assertEquals("Alpha", sorted[1].title)
    }

    @Test
    fun testSortByArtistAscending() {
        val sorted = sortScannedItemsByField(list, SortField.ARTIST, SortOrder.ASCENDING)
        assertEquals("Alice", sorted[0].artist)
        assertEquals("Charlie", sorted[1].artist)
    }

    @Test
    fun testSortByAlbumAscending() {
        val sorted = sortScannedItemsByField(list, SortField.ALBUM, SortOrder.ASCENDING)
        assertEquals("Alpha Album", sorted[0].album)
        assertEquals("Beta Album", sorted[1].album)
    }

    @Test
    fun testSortByTrackNumber() {
        val sorted = sortScannedItemsByField(list, SortField.TRACK_NUMBER, SortOrder.ASCENDING)
        assertEquals(1, sorted[0].trackNumber)
        assertEquals(2, sorted[1].trackNumber)
    }

    @Test
    fun testSortByDuration() {
        val sorted = sortScannedItemsByField(list, SortField.DURATION, SortOrder.ASCENDING)
        assertEquals(180000L, sorted[0].durationMs)
        assertEquals(240000L, sorted[1].durationMs)
    }

    @Test
    fun testSortByYear() {
        val sorted = sortScannedItemsByField(list, SortField.YEAR, SortOrder.ASCENDING)
        assertEquals(2018, sorted[0].year)
        assertEquals(2020, sorted[1].year)
    }
}
