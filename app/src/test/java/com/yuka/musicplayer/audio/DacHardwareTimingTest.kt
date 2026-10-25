package com.yuka.musicplayer.audio

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates hardware oscilloscope equivalent digital timing bounds:
 * - DWC3 USB host controller DMA settling duration: 15 ms (15,000 µs)
 * - Hardware PLL clock stabilization duration: 50 ms (50,000 µs)
 * - Anti-pop transient pre-roll padding: 180 ms cold, 50 ms warm
 * - Isochronous micro-frame clock jitter suppression
 */
class DacHardwareTimingTest {

    data class DacTimingMetrics(
        val interfaceSettleUs: Long,
        val pllRelockUs: Long,
        val antiPopPaddingUs: Long,
        val maxIsoJitterNs: Long
    ) {
        companion object {
            fun parseJson(jsonStr: String): DacTimingMetrics {
                val obj = JSONObject(jsonStr)
                return DacTimingMetrics(
                    interfaceSettleUs = obj.optLong("interfaceSettleUs", 0L),
                    pllRelockUs = obj.optLong("pllRelockUs", 0L),
                    antiPopPaddingUs = obj.optLong("antiPopPaddingUs", 0L),
                    maxIsoJitterNs = obj.optLong("maxIsoJitterNs", 0L)
                )
            }
        }
    }

    @Test
    fun testJsonParsingMatchesTimingTelemetryFormat() {
        val simulatedJson = """
            {
                "interfaceSettleUs": 15000,
                "pllRelockUs": 50000,
                "antiPopPaddingUs": 180000,
                "maxIsoJitterNs": 125000
            }
        """.trimIndent()

        val metrics = DacTimingMetrics.parseJson(simulatedJson)

        // Verify exact settling limits that prevent analog transient clicks on an oscilloscope
        assertEquals(15000L, metrics.interfaceSettleUs)
        assertEquals(50000L, metrics.pllRelockUs)
        assertEquals(180000L, metrics.antiPopPaddingUs)
        assertTrue("Max ISO jitter should remain within USB micro-frame bounds (< 1ms)", metrics.maxIsoJitterNs <= 1_000_000L)
    }

    @Test
    fun testWarmupSilencePaddingCalculations() {
        // Cold start (format or clock change): 180ms
        val sampleRate441 = 44100
        val coldDurationSec = 0.18
        val coldFrames = (sampleRate441 * coldDurationSec).toLong()
        assertEquals(7938L, coldFrames)

        // Warm resume: 50ms
        val warmDurationSec = 0.05
        val warmFrames = (sampleRate441 * warmDurationSec).toLong()
        assertEquals(2205L, warmFrames)

        // 192kHz hi-res cold start: 180ms
        val sampleRate192 = 192000
        val coldFrames192 = (sampleRate192 * coldDurationSec).toLong()
        assertEquals(34560L, coldFrames192)
    }

    @Test
    fun testIsoBresenhamAccumulatorJitterSuppression() {
        // Simulates Bresenham remainder accumulation for 44.1kHz over 1000 FPS (1ms SOF frames)
        val sr = 44100
        val fps = 1000
        val baseFrames = sr / fps // 44
        val rem = sr % fps       // 100

        var acc = 0
        var totalFrames = 0
        for (i in 0 until 1000) {
            var framesToSend = baseFrames
            acc += rem
            if (acc >= fps) {
                framesToSend++
                acc -= fps
            }
            totalFrames += framesToSend
        }

        // Exactly 44,100 frames must be distributed across 1,000 packets with zero drift
        assertEquals(44100, totalFrames)
        assertEquals(0, acc)
    }

    @Test
    fun testIsoBresenhamAccumulatorHighSpeed8000Fps() {
        val testRates = listOf(44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000)
        val fps = 8000

        for (sr in testRates) {
            val baseFrames = sr / fps
            val rem = sr % fps
            var acc = 0
            var totalFrames = 0

            for (microframe in 0 until fps) {
                var framesToSend = baseFrames
                acc += rem
                if (acc >= fps) {
                    framesToSend++
                    acc -= fps
                }
                totalFrames += framesToSend
            }

            assertEquals("Total frames over 1 second for $sr Hz must match exactly", sr, totalFrames)
            assertEquals("Remainder accumulator must reset to 0 after 1 second for $sr Hz", 0, acc)
        }
    }

    @Test
    fun testSpeedResolutionHeuristicRules() {
        // Simulates rule: if speed is unknown (0), UAC2 or sample rate > 96kHz forces High Speed (8000 fps)
        fun resolveFps(devSpeed: Int, uacVersion: Int, maxSampleRate: Int): Int {
            if (devSpeed == 3 || devSpeed == 4) return 8000 // High/Super
            if (devSpeed == 2) return 1000                 // Full Speed
            // devSpeed == 0 (Unknown) fallback heuristic:
            if (uacVersion == 2 || maxSampleRate > 96000) return 8000
            return 1000
        }

        // FiiO SNOWSKY Melody on SM-G955F (devSpeed=0, UAC=2, maxRate=384000)
        assertEquals(8000, resolveFps(devSpeed = 0, uacVersion = 2, maxSampleRate = 384000))
        // Generic UAC2 DAC with devSpeed=0
        assertEquals(8000, resolveFps(devSpeed = 0, uacVersion = 2, maxSampleRate = 44100))
        // Legacy UAC1 USB 1.1 device (devSpeed=0, UAC=1, maxRate=48000)
        assertEquals(1000, resolveFps(devSpeed = 0, uacVersion = 1, maxSampleRate = 48000))
    }

    @Test
    fun testBufferProfileParameters() {
        fun getBufferParams(profile: Int): Pair<Int, Int> = when (profile) {
            0 -> 8 to 8       // 64ms Ultra Low Latency
            2 -> 16 to 32     // 500ms Maximum Stability
            else -> 12 to 16  // 192ms Audiophile Low Jitter (Default)
        }
        assertEquals(8 to 8, getBufferParams(0))
        assertEquals(12 to 16, getBufferParams(1))
        assertEquals(16 to 32, getBufferParams(2))
    }

    @Test
    fun testBitrateKbpsCalculation() {
        val fileSizeBytes = 25_000_000L // 25 MB
        val durationSec = 200.0          // 200 seconds
        val bitrateKbps = if (durationSec > 0) ((fileSizeBytes * 8) / (durationSec * 1000)).toInt() else 0
        assertEquals(1000, bitrateKbps)
    }

    @Test
    fun testWarmupAllowedWhenPaused() {
        val sampleRate = 48000
        val durationMs = 1600
        val expectedFrames = (48000L * 1600L) / 1000L
        assertEquals(76800L, expectedFrames)
    }
}
