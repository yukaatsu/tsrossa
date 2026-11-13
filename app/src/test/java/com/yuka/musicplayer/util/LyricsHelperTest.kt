package com.yuka.musicplayer.util

import org.junit.Assert.*
import org.junit.Test

class LyricsHelperTest {

    @Test
    fun testParseLrcSyncedLyrics() {
        val lrcContent = """
            [ti:Bohemian Rhapsody]
            [ar:Queen]
            [00:01.50]Is this the real life?
            [00:05.20]Is this just fantasy?
            [00:10.00]Caught in a landslide
            [00:13.80]No escape from reality
        """.trimIndent()

        val parsed = LyricsHelper.parseLrc(lrcContent)
        assertTrue(parsed.isSynced)
        assertEquals(4, parsed.lines.size)
        assertEquals(1.5, parsed.lines[0].timeSec, 0.01)
        assertEquals("Is this the real life?", parsed.lines[0].text)
        assertEquals(5.2, parsed.lines[1].timeSec, 0.01)
        assertEquals("Is this just fantasy?", parsed.lines[1].text)
        assertEquals(10.0, parsed.lines[2].timeSec, 0.01)
        assertEquals("Caught in a landslide", parsed.lines[2].text)
        assertEquals(13.8, parsed.lines[3].timeSec, 0.01)
        assertEquals("No escape from reality", parsed.lines[3].text)
    }

    @Test
    fun testParseLrcThreeDigitMilliseconds() {
        val lrcContent = """
            [01:23.456]Three digit millisecond line
        """.trimIndent()

        val parsed = LyricsHelper.parseLrc(lrcContent)
        assertTrue(parsed.isSynced)
        assertEquals(1, parsed.lines.size)
        assertEquals(83.456, parsed.lines[0].timeSec, 0.001)
        assertEquals("Three digit millisecond line", parsed.lines[0].text)
    }

    @Test
    fun testParsePlainTextLyrics() {
        val plainText = """
            Verse 1:
            This is plain text with no timestamps.
            Just simple lyrics.
        """.trimIndent()

        val parsed = LyricsHelper.parseLrc(plainText)
        assertFalse(parsed.isSynced)
        assertEquals(plainText, parsed.plainText)
    }

    @Test
    fun testFindActiveLineIndex() {
        val lines = listOf(
            LyricLine(2.0, "Line 1"),
            LyricLine(5.0, "Line 2"),
            LyricLine(8.0, "Line 3")
        )
        assertEquals(-1, LyricsHelper.findActiveLineIndex(lines, 1.0))
        assertEquals(0, LyricsHelper.findActiveLineIndex(lines, 2.0))
        assertEquals(0, LyricsHelper.findActiveLineIndex(lines, 4.9))
        assertEquals(1, LyricsHelper.findActiveLineIndex(lines, 5.0))
        assertEquals(1, LyricsHelper.findActiveLineIndex(lines, 7.5))
        assertEquals(2, LyricsHelper.findActiveLineIndex(lines, 8.0))
        assertEquals(2, LyricsHelper.findActiveLineIndex(lines, 20.0))
    }

    @Test
    fun testParseEnhancedLrcExactBugReport() {
        val lrcBugReport = """
            v1: <00:23.146>Touch <00:23.831>my <00:24.114>body

            <00:24.762>tender<00:25.552>

            v1: <00:27.671>'Cause <00:28.050>the <00:28.300>feeling <00:29.303>makes <00:29.636>me

            <00:29.919>weak<00:30.765>

            v1: <00:33.482>Kicking <00:34.526>off <00:34.842>the
        """.trimIndent()

        val parsed = LyricsHelper.parseLrc(lrcBugReport)
        assertTrue("Enhanced LRC must be recognized as synced", parsed.isSynced)
        assertTrue("Enhanced LRC must be recognized as word-synced", parsed.isWordSynced)
        assertEquals(3, parsed.lines.size)

        // Line 1: 'Touch my body tender'
        val line1 = parsed.lines[0]
        assertEquals(23.146, line1.timeSec, 0.001)
        assertEquals("Touch my body tender", line1.text)
        assertEquals(VoiceType.PRIMARY, line1.voiceType)
        assertEquals(4, line1.words.size)
        assertEquals("Touch ", line1.words[0].text)
        assertEquals(23.146, line1.words[0].startTimeSec, 0.001)
        assertEquals(23.831, line1.words[0].endTimeSec, 0.001)
        assertEquals("tender", line1.words[3].text)
        assertEquals(24.762, line1.words[3].startTimeSec, 0.001)
        assertEquals(25.552, line1.words[3].endTimeSec, 0.001)
        assertEquals(25.552, line1.endTimeSec, 0.001)

        // Line 2: ''Cause the feeling makes me weak'
        val line2 = parsed.lines[1]
        assertEquals(27.671, line2.timeSec, 0.001)
        assertEquals("'Cause the feeling makes me weak", line2.text)
        assertEquals(VoiceType.PRIMARY, line2.voiceType)
        assertEquals(6, line2.words.size)
        assertEquals("weak", line2.words[5].text)
        assertEquals(30.765, line2.endTimeSec, 0.001)

        // Line 3: 'Kicking off the'
        val line3 = parsed.lines[2]
        assertEquals(33.482, line3.timeSec, 0.001)
        assertEquals("Kicking off the", line3.text)
        assertEquals(3, line3.words.size)
    }

    @Test
    fun testParseEnhancedLrcWithDuetAndBackgroundVocals() {
        val lrcDuet = """
            [00:10.00]v1: <00:10.00>First <00:11.00>voice
            [00:15.00]v2: <00:15.00>Second <00:16.00>voice
            [00:20.00]v3: <00:20.00>All <00:21.00>together
            [00:25.00]Main vocal [bg: <00:26.00>echo <00:27.00>sound]
        """.trimIndent()

        val parsed = LyricsHelper.parseLrc(lrcDuet)
        assertTrue(parsed.isSynced)
        assertTrue(parsed.isWordSynced)
        assertEquals(4, parsed.lines.size)

        assertEquals(VoiceType.PRIMARY, parsed.lines[0].voiceType)
        assertEquals("First voice", parsed.lines[0].text)

        assertEquals(VoiceType.SECONDARY, parsed.lines[1].voiceType)
        assertEquals("Second voice", parsed.lines[1].text)

        assertEquals(VoiceType.ENSEMBLE, parsed.lines[2].voiceType)
        assertEquals("All together", parsed.lines[2].text)

        assertEquals(VoiceType.PRIMARY, parsed.lines[3].voiceType)
        assertEquals("Main vocal", parsed.lines[3].text)
        assertEquals("echo sound", parsed.lines[3].backgroundText)
    }

    @Test
    fun testFindLyricsForTrack_extractsEmbeddedFlacVorbisCommentLyrics() {
        val tempFlac = createSyntheticFlacWithVorbisComments(
            mapOf(
                "TITLE" to "Test Song",
                "ARTIST" to "Test Artist",
                "LYRICS" to "[00:05.00]Embedded line 1\n[00:10.00]Embedded line 2"
            )
        )
        try {
            val parsed = LyricsHelper.findLyricsForTrack(tempFlac)
            assertNotNull("Lyrics must be extracted from embedded FLAC Vorbis Comment tags", parsed)
            assertTrue(parsed!!.isSynced)
            assertEquals(2, parsed.lines.size)
            assertEquals("Embedded line 1", parsed.lines[0].text)
            assertEquals("Embedded line 2", parsed.lines[1].text)
        } finally {
            tempFlac.delete()
        }
    }

    private fun createSyntheticFlacWithVorbisComments(comments: Map<String, String>): java.io.File {
        val tempFile = java.io.File.createTempFile("test_embedded_", ".flac")
        val bos = java.io.ByteArrayOutputStream()
        bos.write("fLaC".toByteArray(Charsets.US_ASCII))

        // Block 0: STREAMINFO (34 bytes), not last
        bos.write(0x00)
        bos.write(0x00); bos.write(0x00); bos.write(34)
        bos.write(ByteArray(34))

        // Block 1: VORBIS_COMMENT (type 4), is last block (0x84)
        val vcBos = java.io.ByteArrayOutputStream()
        val vendorBytes = "MusicPlayerUnitTest".toByteArray(Charsets.UTF_8)
        writeLittleEndianInt(vcBos, vendorBytes.size)
        vcBos.write(vendorBytes)

        writeLittleEndianInt(vcBos, comments.size)
        for ((k, v) in comments) {
            val entry = "$k=$v".toByteArray(Charsets.UTF_8)
            writeLittleEndianInt(vcBos, entry.size)
            vcBos.write(entry)
        }

        val vcData = vcBos.toByteArray()
        bos.write(0x84)
        bos.write((vcData.size shr 16) and 0xFF)
        bos.write((vcData.size shr 8) and 0xFF)
        bos.write(vcData.size and 0xFF)
        bos.write(vcData)

        tempFile.writeBytes(bos.toByteArray())
        return tempFile
    }

    private fun writeLittleEndianInt(os: java.io.OutputStream, value: Int) {
        os.write(value and 0xFF)
        os.write((value shr 8) and 0xFF)
        os.write((value shr 16) and 0xFF)
        os.write((value shr 24) and 0xFF)
    }
}
