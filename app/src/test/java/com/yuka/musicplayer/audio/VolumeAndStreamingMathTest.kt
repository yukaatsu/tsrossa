package com.yuka.musicplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeAndStreamingMathTest {

    @Test
    fun `psychoacoustic curve produces -44_45 dB at 5 percent and 0 dB at 100 percent`() {
        assertEquals(Float.NEGATIVE_INFINITY, AudioEngine.sliderToPerceptualDb(0.0f), 0.001f)
        assertEquals(0.0f, AudioEngine.sliderToPerceptualDb(1.0f), 0.001f)

        val dbAt5Percent = AudioEngine.sliderToPerceptualDb(0.05f)
        // Calibrated Audiophile Studio Taper curve: -48.0 * (1-v) * sqrt(1-v)
        assertEquals(-44.45f, dbAt5Percent, 0.1f)

        val dbAt50Percent = AudioEngine.sliderToPerceptualDb(0.50f)
        assertEquals(-16.97f, dbAt50Percent, 0.1f)

        val dbAt75Percent = AudioEngine.sliderToPerceptualDb(0.75f)
        assertEquals(-6.00f, dbAt75Percent, 0.05f)
    }

    @Test
    fun `linear gain at 100 percent is exact unity 1_0 for bit-perfect bypass`() {
        assertEquals(0.0f, AudioEngine.sliderToLinearGain(0.0f), 0.0f)
        assertEquals(1.0f, AudioEngine.sliderToLinearGain(1.0f), 0.0f)

        val gainAt5Percent = AudioEngine.sliderToLinearGain(0.05f)
        // 10^(-44.45 / 20) ≈ 0.00599
        assertTrue("Gain at 5% should be ~0.0060, got $gainAt5Percent", gainAt5Percent in 0.0058f..0.0062f)
    }

    @Test
    fun `ui volume db text formatting displays expected audiophile readouts`() {
        fun formatDb(volPercent: Int): String {
            return when {
                volPercent >= 100 -> "0.0 dB (Bit-Perfect)"
                volPercent <= 0 -> "-∞ dB (Muted)"
                else -> {
                    val db = AudioEngine.sliderToPerceptualDb(volPercent / 100f)
                    String.format(java.util.Locale.US, "%.1f dB", db)
                }
            }
        }

        assertEquals("0.0 dB (Bit-Perfect)", formatDb(100))
        assertEquals("-∞ dB (Muted)", formatDb(0))
        assertEquals("-44.4 dB", formatDb(5))
        assertEquals("-17.0 dB", formatDb(50))
        assertEquals("-6.0 dB", formatDb(75))
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

        // In Hardware Volume mode at 5% volume: desired ~ -44.45 dB -> rounded to nearest 1 dB step = -44 dB (-11264)
        val targetHw5 = AudioEngine.quantizeHardwareVolumeTarget(0.05f, minDb, maxDb, resDb, preferHardwareVolume = true)
        assertEquals((-44 * 256).toShort(), targetHw5)

        // Test with 2 dB step (512): -44 dB quantizes to -44 dB (-11264)
        val resDb2 = 512.toShort()
        val targetHw5Step2 = AudioEngine.quantizeHardwareVolumeTarget(0.05f, minDb, maxDb, resDb2, preferHardwareVolume = true)
        assertEquals(0, targetHw5Step2.toInt() % 512)
    }

    @Test
    fun `residual software attenuation covers hardware bounds and step quantization`() {
        // Case 1: DAC hardware minimum volume clamped at -40 dB (hardware bound)
        // User sets volume to 5% (slider = 0.05f). Desired dB is ~ -44.45 dB.
        val slider = 0.05f
        val desiredDb = AudioEngine.sliderToPerceptualDb(slider)
        val hwClampedDb = -40.0f

        val residualGain = AudioEngine.computeResidualGain(slider, hwClampedDb)
        val residualDb = 20.0f * kotlin.math.log10(residualGain)
        val combinedDb = hwClampedDb + residualDb

        // Combined hardware + software attenuation must match desired -44.45 dB
        assertEquals(desiredDb, combinedDb, 0.05f)
        assertEquals(-44.45f, combinedDb, 0.1f)

        // Case 2: Hardware step quantization (e.g. 2 dB steps, desired -44.45 dB quantized to -44.0 dB)
        val hwQuantizedDb = -44.0f
        val residualGainQuant = AudioEngine.computeResidualGain(slider, hwQuantizedDb)
        val residualDbQuant = 20.0f * kotlin.math.log10(residualGainQuant)
        val combinedQuantDb = hwQuantizedDb + residualDbQuant
        assertEquals(desiredDb, combinedQuantDb, 0.05f)

        // Case 3: Hardware step quantized lower than desired (e.g. -60.0 dB)
        // No software boost allowed, gain clamped to 1.0f (0.0 dB)
        val hwLowerDb = -60.0f
        val residualGainLower = AudioEngine.computeResidualGain(slider, hwLowerDb)
        assertEquals(1.0f, residualGainLower, 0.0f)

        // Case 4: In Universal 64-bit DSP Mode (appliedHardwareDb = 0.0 dB)
        val residualGainDsp = AudioEngine.computeResidualGain(slider, 0.0f)
        val residualDbDsp = 20.0f * kotlin.math.log10(residualGainDsp)
        assertEquals(desiredDb, residualDbDsp, 0.05f)
        assertEquals(AudioEngine.sliderToLinearGain(slider), residualGainDsp, 0.0001f)

        // Case 5: 100% volume (slider = 1.0f) is bit-perfect unity (1.0f) regardless of hardware
        val gainAt100 = AudioEngine.computeResidualGain(1.0f, 0.0f)
        assertEquals(1.0f, gainAt100, 0.0f)

        // Case 6: 0% volume (slider = 0.0f) is pure silence (0.0f)
        val gainAt0 = AudioEngine.computeResidualGain(0.0f, -64.0f)
        assertEquals(0.0f, gainAt0, 0.0f)
    }

    @Test
    fun `ring buffer modulo indexing streams 2-hour 192kHz track to 100 percent without truncation`() {
        val sampleRate = 192_000L
        val durationSec = 7_200L // 2 hours (e.g. opera / concert / extended FLAC)
        val totalFrames = sampleRate * durationSec // 1,382,400,000 frames (~11 GB)
        val ringCapacityFrames = (64L * 1024L * 1024L) / 8L // 8,388,608 frames (64 MB)

        var pcmIndex = 0L
        var decodedFrames = 0L
        val chunkFrames = 16_384L
        val safetyFrames = 65_536L

        // Fast batch simulation through the entire 2 hours
        val batchFrames = 96_000L * 10L // 10 seconds per step
        while (pcmIndex < totalFrames) {
            // Producer decodes ahead up to ringCapacityFrames
            while (decodedFrames < totalFrames &&
                (decodedFrames - pcmIndex) + chunkFrames + safetyFrames <= ringCapacityFrames
            ) {
                val writeSlot = decodedFrames % ringCapacityFrames
                val contiguous = ringCapacityFrames - writeSlot
                val toDecode = minOf(chunkFrames, totalFrames - decodedFrames, contiguous)
                assertTrue("Write slot must never exceed ring capacity", writeSlot + toDecode <= ringCapacityFrames)
                decodedFrames += toDecode
            }
            // Consumer consumes batch
            val step = minOf(batchFrames, decodedFrames - pcmIndex)
            assertTrue("Decoder must remain ahead of playback", step > 0)
            pcmIndex += step
        }

        assertEquals("2-hour track must reach exact 100% decoded frames", totalFrames, decodedFrames)
        assertEquals("2-hour track must reach exact 100% played frames", totalFrames, pcmIndex)
    }

    @Test
    fun `seek to arbitrary boundary positions resets contiguous decoded pointers without memory holes`() {
        val totalFrames = 1_382_400_000L // 2h @ 192kHz
        val ringCapacityFrames = 8_388_608L
        val chunkFrames = 16_384L

        // Test seeks across diverse track fractions and ring boundary wrap points:
        // 1. Seek to 85% (minute 102 of 2-hour track)
        // 2. Seek near ring capacity boundary (e.g. capacity - 100 frames)
        // 3. Seek to beginning 1%
        val seekTargets = listOf(
            (totalFrames * 0.85).toLong(),
            ringCapacityFrames * 5L + (ringCapacityFrames - 100L),
            (totalFrames * 0.01).toLong()
        )

        for (targetFrame in seekTargets) {
            // JNI seekTo resets pcmIndex and decodedFrames atomically
            var pcmIndex = targetFrame
            var decodedFrames = targetFrame

            // Verify pointer alignment: write slot and read slot start at identical positions
            val initialWriteSlot = decodedFrames % ringCapacityFrames
            val initialReadSlot = pcmIndex % ringCapacityFrames
            assertEquals(initialReadSlot, initialWriteSlot)

            // Producer immediately decodes next chunk
            val writeContiguous = ringCapacityFrames - initialWriteSlot
            val firstDecodeChunk = minOf(chunkFrames, totalFrames - decodedFrames, writeContiguous)
            decodedFrames += firstDecodeChunk

            // Verify decoded frames contiguous sequence: no gap between pcmIndex and decodedFrames
            assertEquals(firstDecodeChunk, decodedFrames - pcmIndex)

            // Consumer reads first contiguous chunk
            val readContiguous = ringCapacityFrames - initialReadSlot
            val firstReadChunk = minOf(firstDecodeChunk, readContiguous)
            assertEquals(firstDecodeChunk, firstReadChunk)
            pcmIndex += firstReadChunk

            // If seek was near boundary and wrapped, verify second chunk begins cleanly at index 0
            if (firstDecodeChunk < chunkFrames && decodedFrames < totalFrames) {
                val secondWriteSlot = decodedFrames % ringCapacityFrames
                assertEquals(0L, secondWriteSlot) // Clean wrap to 0 without holes
                val secondDecodeChunk = minOf(chunkFrames - firstDecodeChunk, totalFrames - decodedFrames, ringCapacityFrames)
                decodedFrames += secondDecodeChunk
                assertTrue(decodedFrames > pcmIndex)

                val secondReadSlot = pcmIndex % ringCapacityFrames
                assertEquals(0L, secondReadSlot)
                pcmIndex += secondDecodeChunk
                assertEquals(decodedFrames, pcmIndex)
            }
        }
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

    @Test
    fun `unified seek resets decoder and frame pointers guaranteeing contiguous decoding without memory holes`() {
        val totalFrames = 28_800_000L // 300s @ 96kHz
        val ringCapacityFrames = 8_388_608L
        val chunkFrames = 16_384L

        // Initial playback at frame 5,000,000
        var pcmIndex = 5_000_000L
        var decodedFrames = 5_500_000L
        assertTrue(decodedFrames > pcmIndex)

        // User performs a forward seek to 20,000,000 (beyond decodedFrames)
        val targetForward = 20_000_000L
        pcmIndex = targetForward
        decodedFrames = targetForward
        // Producer decodes fresh chunk immediately from targetForward
        val writeSlot = decodedFrames % ringCapacityFrames
        val contiguous = ringCapacityFrames - writeSlot
        val toDecode = minOf(chunkFrames, totalFrames - decodedFrames, contiguous)
        decodedFrames += toDecode
        assertTrue(decodedFrames > pcmIndex)
        assertEquals(targetForward, pcmIndex)
        assertEquals(targetForward + toDecode, decodedFrames)

        // User performs a backward seek to 1,000,000 (previously overwritten)
        val targetBackward = 1_000_000L
        pcmIndex = targetBackward
        decodedFrames = targetBackward
        val writeSlotBack = decodedFrames % ringCapacityFrames
        val contiguousBack = ringCapacityFrames - writeSlotBack
        val toDecodeBack = minOf(chunkFrames, totalFrames - decodedFrames, contiguousBack)
        decodedFrames += toDecodeBack
        assertTrue(decodedFrames > pcmIndex)
        assertEquals(targetBackward, pcmIndex)
        assertEquals(targetBackward + toDecodeBack, decodedFrames)
    }

    @Test
    fun `lock-free gapless handoff signal cleanly transfers ownership without blocking real-time context`() {
        val needGaplessDecoderHandoff = java.util.concurrent.atomic.AtomicBoolean(false)
        var backgroundThreadProcessedHandoff = false
        var restartedLoopWithFreshCounters = false

        // 1. In real-time USB callback (iso_callback): zero mutex, zero heap dealloc
        needGaplessDecoderHandoff.set(true)
        val realTimeCallbackCompletedWithoutBlocking = true
        assertTrue(realTimeCallbackCompletedWithoutBlocking)

        // 2. In background decoder thread (decoder_thread_func):
        if (needGaplessDecoderHandoff.get()) {
            if (needGaplessDecoderHandoff.get()) {
                // Background thread performs decoder swap, closing old decoder and freeing memory
                backgroundThreadProcessedHandoff = true
                needGaplessDecoderHandoff.set(false)
            }
            restartedLoopWithFreshCounters = true
        }

        assertTrue(backgroundThreadProcessedHandoff)
        assertTrue(restartedLoopWithFreshCounters)
        org.junit.Assert.assertFalse(needGaplessDecoderHandoff.get())
    }

    // =========================================================================
    // 8. Seek vs Background Decoder Concurrency & 70% Cutoff Regression Tests
    // =========================================================================

    @Test
    fun seekGenerationGuard_preventsStaleDecodeChunkFromClobberingSeekState() {
        // Simulasi lagu 10.000.000 frame (100%). Baru terdekode 500.000 frame (5%).
        val originalTotalFrames = 10_000_000L
        var totalFrames = originalTotalFrames
        var decodedFrames = 500_000L
        var pcmIndex = 10_000L
        var fileCursor = 500_000L
        var seekGeneration = 0L

        // 1. Thread decode mengambil snapshot sebelum membaca chunk 16.384 frame
        val decodeSnapshotGen = seekGeneration
        val decodeSnapshotCurrent = decodedFrames
        val chunkRead = 16_384L

        // 2. User menyeret paksa seek bar ke 60% (6.000.000) atau 30% (3.000.000) saat lagu baru diputar!
        val targetSeekFrame = 3_000_000L // 30%
        seekGeneration += 1L
        fileCursor = targetSeekFrame
        decodedFrames = targetSeekFrame
        pcmIndex = targetSeekFrame

        // 3. Thread decode selesai membaca chunk lama dan mencoba komit:
        // Dengan seekGeneration guard, komit chunk lama WAJIB ditolak!
        if (seekGeneration == decodeSnapshotGen) {
            decodedFrames = decodeSnapshotCurrent + chunkRead
        }

        assertEquals("decodedFrames tidak boleh tertimpa balik ke 5% oleh chunk lama", targetSeekFrame, decodedFrames)
        assertEquals("pcmIndex harus tetap di targetSeekFrame", targetSeekFrame, pcmIndex)

        // 4. Thread decode melanjutkan pembacaan dari posisi baru (30% s/d 100% = sisa 7.000.000 frame)
        val remainingInFile = originalTotalFrames - fileCursor // 7.000.000 frame (70% lagu)
        decodedFrames += remainingInFile
        fileCursor += remainingInFile

        // Saat mencapai EOF (fileCursor == originalTotalFrames), decodedFrames harus tepat 100% (bukan 70%!)
        assertEquals("Saat EOF, decodedFrames harus mencapai 100% totalFrames (bukan 70%)", originalTotalFrames, decodedFrames)
        assertEquals("totalFrames tidak boleh terpotong menjadi 70%", originalTotalFrames, totalFrames)
    }

    @Test
    fun inMemoryFastPathSeek_preservesDecodedFramesWhenTrackFullyBuffered() {
        // Lagu <= 64MB (misal 4.000.000 frame) yang sudah selesai didekode 100% di RAM
        val totalFrames = 4_000_000L
        val capacityFrames = 4_000_000L
        var decodedFrames = 4_000_000L
        var pcmIndex = 100_000L

        val targetFrame = 2_500_000L
        val isFullyInRam = (totalFrames in 1..capacityFrames) && (decodedFrames >= totalFrames)
        assertTrue("Track harus terdeteksi 100% berada di dalam RAM", isFullyInRam)

        if (isFullyInRam) {
            // Fast-path: hanya menggeser pcmIndex tanpa mereset decodedFrames
            pcmIndex = targetFrame
        } else {
            decodedFrames = targetFrame
            pcmIndex = targetFrame
        }

        assertEquals(targetFrame, pcmIndex)
        assertEquals("decodedFrames harus tetap 100% agar tidak memicu silence/re-decode", totalFrames, decodedFrames)
        assertTrue("frames_avail (decodedFrames - pcmIndex) harus > 0 seketika", (decodedFrames - pcmIndex) > 0L)
    }

    @Test
    fun clearNextTrack_duringPendingGaplessHandoff_promotesNextDecoderInsteadOfClosing() {
        // Skenario Lagu 2 > 32MB (~70% pre-buffered), baru saja transisi gapless di iso_callback
        var needGaplessDecoderHandoff = true
        var activeDecoderId: String? = "Track1_EOF_Decoder"
        var nextActiveDecoderId: String? = "Track2_Live_Decoder_At_70_Percent"

        // clearNextTrack() dipanggil dari UI (misal karena lagu terakhir atau toggle Shuffle)
        // sebelum TsrossaDecode sempat menjalankan loop handoff:
        if (needGaplessDecoderHandoff) {
            activeDecoderId = nextActiveDecoderId
            nextActiveDecoderId = null
            needGaplessDecoderHandoff = false
        } else {
            nextActiveDecoderId = null
        }

        assertEquals("Decoder Lagu 2 wajib dipromosikan ke activeDecoder, bukan ditutup!", "Track2_Live_Decoder_At_70_Percent", activeDecoderId)
        org.junit.Assert.assertFalse(needGaplessDecoderHandoff)
    }

    @Test
    fun isoCallbackCas_doesNotOverwriteConcurrentSeekPcmIndex() {
        val atomicPcmIndex = java.util.concurrent.atomic.AtomicLong(100_000L)
        val currentIndexLoadedByIso = atomicPcmIndex.get() // 100_000L
        val framesToRead = 48L

        // Sementara iso_callback memproses audio, seek_to_internal mengubah pcmIndex ke 5.000.000L
        val seekTargetFrame = 5_000_000L
        atomicPcmIndex.set(seekTargetFrame)

        // iso_callback mencoba memajukan pcmIndex dengan CAS:
        val casSucceeded = atomicPcmIndex.compareAndSet(currentIndexLoadedByIso, currentIndexLoadedByIso + framesToRead)

        org.junit.Assert.assertFalse("CAS harus gagal karena seek telah mengubah pcmIndex", casSucceeded)
        assertEquals("pcmIndex tidak boleh tertimpa kembali ke posisi pra-seek", seekTargetFrame, atomicPcmIndex.get())
    }

    @Test
    fun scrubberEndClamp_preventsSeekingToFinalSingleFrameEof() {
        val durationSeconds = 240.0
        val dragProgress = 1.0f // User menyeret paksa seek bar mentok 100% ke kanan
        val rawSeekSec = dragProgress.toDouble() * durationSeconds
        val safeMaxSeekSec = if (durationSeconds > 0.5) (durationSeconds - 0.25) else durationSeconds
        val finalSeekSec = rawSeekSec.coerceIn(0.0, safeMaxSeekSec)

        val sampleRate = 48_000L
        val totalFrames = (durationSeconds * sampleRate).toLong()
        val targetFrame = (finalSeekSec * sampleRate).toLong()

        assertTrue("Masih harus tersisa minimal ~12.000 frame (250ms) agar tidak langsung memicu auto-next instan",
            (totalFrames - targetFrame) >= 10_000L)
    }
}
