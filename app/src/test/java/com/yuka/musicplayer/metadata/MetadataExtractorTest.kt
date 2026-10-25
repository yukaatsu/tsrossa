package com.yuka.musicplayer.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class MetadataExtractorTest {

    @Test
    fun testNonExistentFileFallback() {
        val dummyFile = File("/storage/emulated/0/Music/does_not_exist_track.flac")
        val track = extractMetadata(dummyFile)

        assertEquals("does_not_exist_track", track.title)
        assertEquals("Unknown Artist", track.artist)
        assertEquals("Unknown Album", track.album)
        assertEquals("----", track.year)
        assertEquals(0.0, track.durationSeconds, 0.001)
        assertEquals("FLAC", track.codec)
        assertNotNull(track.dominantColor)
    }

    @Test
    fun testWavExtensionResolution() {
        val wavFile = File("/music/symphony.wav")
        val track = extractMetadata(wavFile)

        assertEquals("symphony", track.title)
        assertEquals("WAV", track.codec)
        assertNotNull(track.dominantColor)
    }

    @Test
    fun testWaveExtensionResolution() {
        val waveFile = File("/music/master_audio.wave")
        val track = extractMetadata(waveFile)

        assertEquals("master_audio", track.title)
        assertEquals("WAV", track.codec)
        assertNotNull(track.dominantColor)
    }

    @Test
    fun testMqaDetectionByFilename() {
        val mqaFile1 = File("/music/Hotel_California [MQA].flac")
        val track1 = extractMetadata(mqaFile1)
        assertEquals(true, track1.isMqa)

        val mqaFile2 = File("/music/track.mqa.flac")
        val track2 = extractMetadata(mqaFile2)
        assertEquals(true, track2.isMqa)

        val standardFlac = File("/music/standard_song.flac")
        val trackStandard = extractMetadata(standardFlac)
        assertEquals(false, trackStandard.isMqa)
    }

    @Test
    fun testCorruptFileHandling() {
        val tempCorruptFile = File.createTempFile("corrupt_test", ".flac")
        try {
            tempCorruptFile.writeBytes(byteArrayOf(0x66, 0x4C, 0x61, 0x43, -1, -1, -1, -1))
            val track = extractMetadata(tempCorruptFile)
            assertNotNull(track)
            assertNotNull(track.dominantColor)
        } finally {
            tempCorruptFile.delete()
        }
    }
}
