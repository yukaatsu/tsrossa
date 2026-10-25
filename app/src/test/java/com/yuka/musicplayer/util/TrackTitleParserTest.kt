package com.yuka.musicplayer.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackTitleParserTest {

    @Test
    fun parseStandardTitleArtist() {
        val result = TrackTitleParser.parse("40oz - Polyphia")
        assertEquals("40oz", result.first)
        assertEquals("Polyphia", result.second)
    }

    @Test
    fun parseTitleWithInternalHyphen() {
        val result = TrackTitleParser.parse("足跡 - Brave the unknown - - 9Lana")
        assertEquals("足跡 - Brave the unknown -", result.first)
        assertEquals("9Lana", result.second)
    }

    @Test
    fun parseTitleWithoutArtistSeparator() {
        val result = TrackTitleParser.parse("SoloTrack")
        assertEquals("SoloTrack", result.first)
        assertEquals("", result.second)
    }

    @Test
    fun parseTitleWithMultipleHyphensInTitle() {
        val result = TrackTitleParser.parse("Track - Part 1 - Part 2 - Artist Name")
        assertEquals("Track - Part 1 - Part 2", result.first)
        assertEquals("Artist Name", result.second)
    }
}
