package com.yuka.musicplayer.ui.diagnostics

import com.yuka.musicplayer.metadata.AudioTagReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SignalPathBadgeClassifierTest {

    @Test
    fun classifyAudioQualityBadge_classifiesLosslessHiResMqaFloatAndFallbackAccurately() {
        // 1. Standard CD 16/44.1 -> Lossless
        val cd = classifyAudioQualityBadge("FLAC", 16, 44100, isMqa = false, isFloatPcm = false)
        assertEquals(AudioQualityTier.LOSSLESS, cd.tier)
        assertEquals("Lossless", cd.primaryLabel)
        assertFalse(cd.showMqaMicroTag)

        // 2. 16/48 -> Lossless
        val dat = classifyAudioQualityBadge("WAV", 16, 48000)
        assertEquals(AudioQualityTier.LOSSLESS, dat.tier)
        assertEquals("Lossless", dat.primaryLabel)

        // 3. 24/48 -> Hi-Res
        val hiRes2448 = classifyAudioQualityBadge("FLAC", 24, 48000)
        assertEquals(AudioQualityTier.HI_RES, hiRes2448.tier)
        assertEquals("Hi-Res", hiRes2448.primaryLabel)

        // 4. 24/96 & 16/96 -> Hi-Res
        val hiRes2496 = classifyAudioQualityBadge("FLAC", 24, 96000)
        assertEquals(AudioQualityTier.HI_RES, hiRes2496.tier)
        val hiRes1696 = classifyAudioQualityBadge("WAV", 16, 96000)
        assertEquals(AudioQualityTier.HI_RES, hiRes1696.tier)

        // 5. 32-bit IEEE Float WAV -> Hi-Res
        val floatWav = classifyAudioQualityBadge("WAV", 32, 48000, isFloatPcm = true)
        assertEquals(AudioQualityTier.HI_RES, floatWav.tier)
        assertEquals("Hi-Res", floatWav.primaryLabel)

        // 6. MQA flagged keeps base tier + micro tag
        val mqaTrack = classifyAudioQualityBadge("FLAC", 24, 48000, isMqa = true)
        assertEquals(AudioQualityTier.HI_RES, mqaTrack.tier)
        assertTrue(mqaTrack.showMqaMicroTag)

        // 7. Empty metadata fallback -> Codec uppercase
        val emptyMeta = classifyAudioQualityBadge("flac", 0, 0)
        assertEquals(AudioQualityTier.CODEC_FALLBACK, emptyMeta.tier)
        assertEquals("FLAC", emptyMeta.primaryLabel)
    }

    @Test
    fun resolveSignalPathStatus_respectsStrictPriorityOrder() {
        // 1. Disconnected overrides all
        assertEquals(
            SignalPathStatus.NO_DAC,
            resolveSignalPathStatus(
                isDacConnected = false,
                isPlaying = true,
                isBitPerfectDirectPass = true,
                isHardwareVolumeActive = true,
                sampleRateState = SampleRateTransmissionState.BIT_PERFECT,
                isDeviceWedged = false,
                isHardwareVolumeLockedBySystem = false
            )
        )

        // 2. Warning overrides Bit-Perfect / HW UAC / DSP
        assertEquals(
            SignalPathStatus.WARNING,
            resolveSignalPathStatus(
                isDacConnected = true,
                isPlaying = true,
                isBitPerfectDirectPass = false,
                isHardwareVolumeActive = false,
                sampleRateState = SampleRateTransmissionState.RESAMPLED,
                isDeviceWedged = false,
                isHardwareVolumeLockedBySystem = false
            )
        )
        assertEquals(
            SignalPathStatus.WARNING,
            resolveSignalPathStatus(
                isDacConnected = true,
                isPlaying = false,
                isBitPerfectDirectPass = true,
                isHardwareVolumeActive = false,
                sampleRateState = SampleRateTransmissionState.NO_STREAM,
                isDeviceWedged = true,
                isHardwareVolumeLockedBySystem = false
            )
        )
        assertEquals(
            SignalPathStatus.WARNING,
            resolveSignalPathStatus(
                isDacConnected = true,
                isPlaying = true,
                isBitPerfectDirectPass = false,
                isHardwareVolumeActive = false,
                sampleRateState = SampleRateTransmissionState.BIT_PERFECT,
                isDeviceWedged = false,
                isHardwareVolumeLockedBySystem = true
            )
        )

        // 3. Bit-Perfect Direct Pass (Playing + BIT_PERFECT or Standby when DAC connected)
        assertEquals(
            SignalPathStatus.BIT_PERFECT,
            resolveSignalPathStatus(
                isDacConnected = true,
                isPlaying = true,
                isBitPerfectDirectPass = true,
                isHardwareVolumeActive = true,
                sampleRateState = SampleRateTransmissionState.BIT_PERFECT,
                isDeviceWedged = false,
                isHardwareVolumeLockedBySystem = false
            )
        )

        // 4. Hybrid HW UAC Fine-Trim
        assertEquals(
            SignalPathStatus.HYBRID_UAC_TRIM,
            resolveSignalPathStatus(
                isDacConnected = true,
                isPlaying = true,
                isBitPerfectDirectPass = false,
                isHardwareVolumeActive = true,
                sampleRateState = SampleRateTransmissionState.BIT_PERFECT,
                isDeviceWedged = false,
                isHardwareVolumeLockedBySystem = false
            )
        )

        // 5. Universal 64-Bit DSP Active
        assertEquals(
            SignalPathStatus.UNIVERSAL_DSP,
            resolveSignalPathStatus(
                isDacConnected = true,
                isPlaying = true,
                isBitPerfectDirectPass = false,
                isHardwareVolumeActive = false,
                sampleRateState = SampleRateTransmissionState.BIT_PERFECT,
                isDeviceWedged = false,
                isHardwareVolumeLockedBySystem = false
            )
        )
    }

    @Test
    fun liveSignalPathSnapshot_decodesBitmaskQuantizesDbAndHandlesMuteSafely() {
        val allFlags =
            LiveSignalPathSnapshot.FLAG_BIT_PERFECT or
                LiveSignalPathSnapshot.FLAG_HW_ACTIVE or
                LiveSignalPathSnapshot.FLAG_PEQ_EFFECTIVE or
                LiveSignalPathSnapshot.FLAG_COMP_EFFECTIVE or
                LiveSignalPathSnapshot.FLAG_HAS_NEXT_TRACK or
                LiveSignalPathSnapshot.FLAG_IS_ASYNC or
                LiveSignalPathSnapshot.FLAG_MLOCK_ACTIVE or
                LiveSignalPathSnapshot.FLAG_RT_SCHED_FIFO or
                LiveSignalPathSnapshot.FLAG_SOURCE_FLOAT_PCM

        val floats = floatArrayOf(
            -6.24f,   // appliedHwDb -> -6.2f
            -128.0f,  // dspGainDb mute -> -128.0f
            +2.46f,   // compGainDb -> +2.5f
            -1.34f,   // limiterGrDb -> -1.3f
            0.5f,     // resVolumeDb -> 0.5f
            0.0f,     // maxVolumeDb -> 0.0f
            193.46f   // latency -> 193.5f
        )
        val ints = intArrayOf(allFlags, 24, 2, 24, 64, 8000)

        val snap = LiveSignalPathSnapshot.fromRawArrays(floats, ints)
        assertEquals(-6.2f, snap.appliedHwDb, 0.001f)
        assertEquals(-128.0f, snap.dspGainDb, 0.001f)
        assertEquals(2.5f, snap.compGainDb, 0.001f)
        assertEquals(-1.3f, snap.limiterGrDb, 0.001f)
        assertEquals(193.5f, snap.estimatedLatencyMs, 0.001f)
        assertEquals("-∞ dB", formatDbReadout(snap.dspGainDb))

        assertTrue(snap.bitPerfectDirectPass)
        assertTrue(snap.hwVolumeActive)
        assertTrue(snap.peqEffective)
        assertTrue(snap.compEffective)
        assertTrue(snap.hasNextTrack)
        assertTrue(snap.isAsyncIso)
        assertTrue(snap.isMlockActive)
        assertTrue(snap.isRtSchedFifoActive)
        assertTrue(snap.sourceIsFloatPcm)
        assertEquals(24, snap.negotiatedBitResolution)
        assertEquals(2, snap.liveChannels)
        assertEquals(24, snap.activeIsoTransfers)
        assertEquals(64, snap.activeIsoPacketsPerTransfer)
        assertEquals(8000, snap.usbFramesPerSec)
    }

    @Test
    fun buildActiveDspSummaryTags_andAudioTagReader_wavFloatDetection() {
        assertEquals("Standby", buildActiveDspSummaryTags(false, false, false, true, true, true))
        assertEquals("Bypass", buildActiveDspSummaryTags(true, true, false, false, false, false))
        assertEquals("Fine-Trim • TPDF", buildActiveDspSummaryTags(true, false, true, false, false, true))
        assertEquals("PEQ • DYN • VOL • LIM • TPDF", buildActiveDspSummaryTags(true, false, false, true, true, true))
        assertEquals("VOL • TPDF", buildActiveDspSummaryTags(true, false, false, false, false, true))

        // Verify RIFF WAV wFormatTag == 3 (IEEE_FLOAT) and wFormatTag == 0xFFFE (EXTENSIBLE IEEE_FLOAT)
        val floatWav = File.createTempFile("test_float_", ".wav")
        val pcmWav = File.createTempFile("test_pcm_", ".wav")
        try {
            floatWav.writeBytes(buildMinimalWavHeader(formatTag = 3, bitsPerSample = 32, extensibleSubFormat = null))
            pcmWav.writeBytes(buildMinimalWavHeader(formatTag = 1, bitsPerSample = 24, extensibleSubFormat = null))

            val floatTags = AudioTagReader.readTags(floatWav)
            assertTrue(floatTags.isFloatPcm)
            assertEquals(32, floatTags.bitsPerSample)
            assertEquals(96000, floatTags.sampleRate)

            val pcmTags = AudioTagReader.readTags(pcmWav)
            assertFalse(pcmTags.isFloatPcm)
            assertEquals(24, pcmTags.bitsPerSample)

            // Extensible IEEE Float (0xFFFE with subformat = 3 at offset 24)
            floatWav.writeBytes(buildMinimalWavHeader(formatTag = 0xFFFE, bitsPerSample = 32, extensibleSubFormat = 3))
            assertTrue(AudioTagReader.readTags(floatWav).isFloatPcm)
        } finally {
            floatWav.delete()
            pcmWav.delete()
        }
    }

    private fun buildMinimalWavHeader(formatTag: Int, bitsPerSample: Int, extensibleSubFormat: Int?): ByteArray {
        val fmtSize = if (extensibleSubFormat != null) 40 else 16
        val buf = ByteBuffer.allocate(12 + 8 + fmtSize + 8).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(4 + 8 + fmtSize + 8)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(fmtSize)
        buf.putShort(formatTag.toShort())
        buf.putShort(2.toShort()) // channels
        buf.putInt(96000)         // sample rate
        buf.putInt(96000 * 2 * (bitsPerSample / 8))
        buf.putShort((2 * (bitsPerSample / 8)).toShort())
        buf.putShort(bitsPerSample.toShort())
        if (extensibleSubFormat != null) {
            buf.putShort(22.toShort()) // cbSize
            buf.putShort(bitsPerSample.toShort())
            buf.putInt(3) // channelMask
            buf.putShort(extensibleSubFormat.toShort())
            buf.put(ByteArray(14))
        }
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(0)
        return buf.array()
    }
}
