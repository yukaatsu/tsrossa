package com.yuka.musicplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeAndStreamingMathTest {

    @Test
    fun `psychoacoustic curve produces -59_26 dB at 5 percent and 0 dB at 100 percent`() {
        assertEquals(Float.NEGATIVE_INFINITY, AudioEngine.sliderToPerceptualDb(0.0f), 0.001f)
        assertEquals(0.0f, AudioEngine.sliderToPerceptualDb(1.0f), 0.001f)

        val dbAt5Percent = AudioEngine.sliderToPerceptualDb(0.05f)
        // Must be around -59.26 dB (much quieter than old -26.02 dB!)
        assertEquals(-59.26f, dbAt5Percent, 0.1f)

        val dbAt50Percent = AudioEngine.sliderToPerceptualDb(0.50f)
        assertEquals(-22.63f, dbAt50Percent, 0.1f)

        val dbAt75Percent = AudioEngine.sliderToPerceptualDb(0.75f)
        assertEquals(-8.00f, dbAt75Percent, 0.05f)
    }

    @Test
    fun `linear gain at 100 percent is exact unity 1_0 for bit-perfect bypass`() {
        assertEquals(0.0f, AudioEngine.sliderToLinearGain(0.0f), 0.0f)
        assertEquals(1.0f, AudioEngine.sliderToLinearGain(1.0f), 0.0f)

        val gainAt5Percent = AudioEngine.sliderToLinearGain(0.05f)
        // 10^(-59.26 / 20) ≈ 0.001089
        assertTrue("Gain at 5% should be ~0.0011, got $gainAt5Percent", gainAt5Percent in 0.0010f..0.0012f)
    }

    @Test
    fun `uac volume range validation preserves valid range and provides -64dB fallback for broken range`() {
        // Valid range: -96 dB to 0 dB, step 0.5 dB (128)
        val valid = AudioEngine.sanitizeUacVolumeRange((-24576).toShort(), 0.toShort(), 128.toShort())
        assertEquals((-24576).toShort(), valid.minDb)
        assertEquals(0.toShort(), valid.maxDb)
        assertEquals(128.toShort(), valid.resDb)

        // Invalid inverted range: min >= max
        val inverted = AudioEngine.sanitizeUacVolumeRange(0.toShort(), (-100).toShort(), 256.toShort())
        assertEquals((-16384).toShort(), inverted.minDb) // -64 dB fallback
        assertEquals(0.toShort(), inverted.maxDb)
        assertEquals(256.toShort(), inverted.resDb)

        // Invalid shallow range (< 6 dB, e.g. 4 dB)
        val shallow = AudioEngine.sanitizeUacVolumeRange((-1024).toShort(), 0.toShort(), 256.toShort())
        assertEquals((-16384).toShort(), shallow.minDb)
        assertEquals(0.toShort(), shallow.maxDb)

        // Invalid zero or negative step -> defaults to 256 (1 dB)
        val zeroStep = AudioEngine.sanitizeUacVolumeRange((-16384).toShort(), 0.toShort(), 0.toShort())
        assertEquals(256.toShort(), zeroStep.resDb)
    }

    @Test
    fun `hardware volume target locks to 0dB in 64-bit DSP mode and applies step quantization in hardware mode`() {
        val minDb = (-16384).toShort() // -64 dB
        val maxDb = 0.toShort()        //   0 dB
        val resDb = 256.toShort()      //   1 dB step

        // In 64-Bit DSP mode (preferHardwareVolume = false), hardware register is locked at 0 dB (maxDb)
        val targetDspMode = AudioEngine.quantizeHardwareVolumeTarget(0.5f, minDb, maxDb, resDb, preferHardwareVolume = false)
        assertEquals(maxDb, targetDspMode)

        // In Hardware Volume mode at 100% volume -> 0 dB (maxDb)
        val targetHw100 = AudioEngine.quantizeHardwareVolumeTarget(1.0f, minDb, maxDb, resDb, preferHardwareVolume = true)
        assertEquals(maxDb, targetHw100)

        // In Hardware Volume mode at 0% volume -> minDb (-64 dB)
        val targetHw0 = AudioEngine.quantizeHardwareVolumeTarget(0.0f, minDb, maxDb, resDb, preferHardwareVolume = true)
        assertEquals(minDb, targetHw0)

        // In Hardware Volume mode at 5% volume: desired ~ -59.26 dB -> rounded to nearest 1 dB step = -59 dB (-15104)
        val targetHw5 = AudioEngine.quantizeHardwareVolumeTarget(0.05f, minDb, maxDb, resDb, preferHardwareVolume = true)
        assertEquals((-59 * 256).toShort(), targetHw5)

        // Test with 2 dB step (512): -59 dB quantizes to -58 dB (-14848) or -60 dB (-15360)
        val resDb2 = 512.toShort()
        val targetHw5Step2 = AudioEngine.quantizeHardwareVolumeTarget(0.05f, minDb, maxDb, resDb2, preferHardwareVolume = true)
        assertEquals(0, targetHw5Step2.toInt() % 512)
    }

    @Test
    fun `ring buffer modulo indexing streams 300s 96kHz track to 100 percent without truncation`() {
        val sampleRate = 96_000L
        val durationSec = 300L // 5 minutes (like ReoNa - Katappo no Kutsushita)
        val totalFrames = sampleRate * durationSec // 28,800,000 frames (230.4 MB)
        val ringCapacityFrames = (64L * 1024L * 1024L) / 8L // 8,388,608 frames (64 MB)

        var pcmIndex = 0L
        var decodedFrames = 0L
        val chunkFrames = 16_384L
        val safetyFrames = 65_536L

        // Simulate streaming until track finishes
        while (pcmIndex < totalFrames) {
            // Producer (decoder_thread_func) fills ring buffer up to capacity
            while (decodedFrames < totalFrames &&
                (decodedFrames - pcmIndex) + chunkFrames + safetyFrames <= ringCapacityFrames
            ) {
                val writeSlot = decodedFrames % ringCapacityFrames
                val contiguous = ringCapacityFrames - writeSlot
                val toDecode = minOf(chunkFrames, totalFrames - decodedFrames, contiguous)
                assertTrue(writeSlot + toDecode <= ringCapacityFrames)
                decodedFrames += toDecode
            }
            // Consumer (fill_iso_packet_buffer) advances playback
            val step = minOf(48_000L, decodedFrames - pcmIndex)
            assertTrue("Decoder must stay ahead of playback", step > 0)
            pcmIndex += step
        }

        assertEquals(totalFrames, decodedFrames)
        assertEquals(totalFrames, pcmIndex)
    }

    @Test
    fun `ring buffer consumer contiguous slot calculation prevents boundary overshoot during wrap-around`() {
        val ringCapacityFrames = (64L * 1024L * 1024L) / 8L // 8,388,608 frames
        // Position consumer 50 frames before the ring boundary
        val pcmIndex = ringCapacityFrames - 50L
        val framesNeeded = 192L // e.g. 192 frames for 2ms @ 96kHz packet

        val bufFrame = pcmIndex % ringCapacityFrames
        val contiguous = ringCapacityFrames - bufFrame
        val firstChunk = minOf(framesNeeded, contiguous)

        // First read stops exactly at ring buffer end
        assertEquals(50L, firstChunk)
        assertEquals(ringCapacityFrames, bufFrame + firstChunk)

        // Second read wraps to 0
        val nextPcmIndex = pcmIndex + firstChunk
        val nextBufFrame = nextPcmIndex % ringCapacityFrames
        assertEquals(0L, nextBufFrame)
        val nextContiguous = ringCapacityFrames - nextBufFrame
        val secondChunk = minOf(framesNeeded - firstChunk, nextContiguous)
        assertEquals(142L, secondChunk)
        assertEquals(framesNeeded, firstChunk + secondChunk)
    }
}
