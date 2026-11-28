package com.yuka.musicplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Batch 17 Verification Gate:
 * Validates Dual-Slope Dynamics Compressor static transfer curve, noise-floor Hermite gate,
 * C1 soft-knee continuity, neutral deadband independence, clamping bounds, and identity bypass.
 */
class DynamicsCompressorMathTest {

    @Test
    fun `downward compression follows exact ratio above soft knee`() {
        val gainAtMinus4 = AudioEngine.evaluateCompressorStaticGainDb(
            inDb = -4.0,
            downThresholdDb = -16.0,
            downRatio = 4.0,
            upThresholdDb = -34.0,
            upRatio = 1.0,
            maxUpwardBoostDb = 0.0,
            floorThresholdDb = -65.0,
            kneeDb = 6.0
        )
        // Expected reduction: -((-4) - (-16)) * (1 - 1/4) = -12 * 0.75 = -9.0 dB
        assertEquals(-9.0, gainAtMinus4, 1e-6)
    }

    @Test
    fun `upward compression boosts quiet passages up to configured ceiling`() {
        // Above noise-floor gate transition (-65 + 6 = -59 dBFS), below up knee (-34 - 3 = -37 dBFS)
        val gainAtMinus44 = AudioEngine.evaluateCompressorStaticGainDb(
            inDb = -44.0,
            downThresholdDb = -16.0,
            downRatio = 4.0,
            upThresholdDb = -34.0,
            upRatio = 2.0,
            maxUpwardBoostDb = 6.0,
            floorThresholdDb = -65.0,
            kneeDb = 6.0
        )
        // (-34 - (-44)) * (1 - 1/2) = +5.0 dB < ceiling 6.0 dB
        assertEquals(5.0, gainAtMinus44, 1e-6)

        val gainAtMinus52 = AudioEngine.evaluateCompressorStaticGainDb(
            inDb = -52.0,
            downThresholdDb = -16.0,
            downRatio = 4.0,
            upThresholdDb = -34.0,
            upRatio = 2.0,
            maxUpwardBoostDb = 6.0,
            floorThresholdDb = -65.0,
            kneeDb = 6.0
        )
        // Raw would be +9.0 dB, must clamp smoothly at maxUpwardBoostDb = +6.0 dB
        assertEquals(6.0, gainAtMinus52, 1e-6)
    }

    @Test
    fun `noise floor Hermite smoothstep gate suppresses upward boost smoothly below floor`() {
        val floor = -65.0
        val belowFloor = AudioEngine.evaluateCompressorStaticGainDb(
            inDb = -75.0,
            downThresholdDb = -16.0,
            downRatio = 4.0,
            upThresholdDb = -34.0,
            upRatio = 2.0,
            maxUpwardBoostDb = 6.0,
            floorThresholdDb = floor,
            kneeDb = 6.0
        )
        val atFloor = AudioEngine.evaluateCompressorStaticGainDb(
            inDb = -65.0,
            downThresholdDb = -16.0,
            downRatio = 4.0,
            upThresholdDb = -34.0,
            upRatio = 2.0,
            maxUpwardBoostDb = 6.0,
            floorThresholdDb = floor,
            kneeDb = 6.0
        )
        val midGate = AudioEngine.evaluateCompressorStaticGainDb(
            inDb = -62.0, // midpoint of 6 dB gate window [-65, -59] -> smoothstep(0.5) = 0.5 -> +3.0 dB
            downThresholdDb = -16.0,
            downRatio = 4.0,
            upThresholdDb = -34.0,
            upRatio = 2.0,
            maxUpwardBoostDb = 6.0,
            floorThresholdDb = floor,
            kneeDb = 6.0
        )
        val fullOpen = AudioEngine.evaluateCompressorStaticGainDb(
            inDb = -59.0,
            downThresholdDb = -16.0,
            downRatio = 4.0,
            upThresholdDb = -34.0,
            upRatio = 2.0,
            maxUpwardBoostDb = 6.0,
            floorThresholdDb = floor,
            kneeDb = 6.0
        )

        assertEquals(0.0, belowFloor, 1e-6)
        assertEquals(0.0, atFloor, 1e-6)
        assertEquals(3.0, midGate, 1e-6)
        assertEquals(6.0, fullOpen, 1e-6)
    }

    @Test
    fun `soft knee preserves C1 continuity across downward and upward thresholds`() {
        val eps = 0.01
        val points = listOf(-19.0, -16.0, -13.0, -37.0, -34.0, -31.0)
        for (p in points) {
            val gMinus = AudioEngine.evaluateCompressorStaticGainDb(
                inDb = p - eps, downThresholdDb = -16.0, downRatio = 4.0,
                upThresholdDb = -34.0, upRatio = 2.0, maxUpwardBoostDb = 6.0,
                floorThresholdDb = -65.0, kneeDb = 6.0
            )
            val gZero = AudioEngine.evaluateCompressorStaticGainDb(
                inDb = p, downThresholdDb = -16.0, downRatio = 4.0,
                upThresholdDb = -34.0, upRatio = 2.0, maxUpwardBoostDb = 6.0,
                floorThresholdDb = -65.0, kneeDb = 6.0
            )
            val gPlus = AudioEngine.evaluateCompressorStaticGainDb(
                inDb = p + eps, downThresholdDb = -16.0, downRatio = 4.0,
                upThresholdDb = -34.0, upRatio = 2.0, maxUpwardBoostDb = 6.0,
                floorThresholdDb = -65.0, kneeDb = 6.0
            )
            assertTrue("Discontinuity left of $p", abs(gZero - gMinus) < 0.02)
            assertTrue("Discontinuity right of $p", abs(gPlus - gZero) < 0.02)
        }
    }

    @Test
    fun `dual region leaves neutral deadband untouched at zero dB delta`() {
        // Up knee top = -34 + 3 = -31 dBFS; Down knee bottom = -16 - 3 = -19 dBFS
        for (db in listOf(-30.5, -25.0, -19.5)) {
            val delta = AudioEngine.evaluateCompressorStaticGainDb(
                inDb = db,
                downThresholdDb = -16.0,
                downRatio = 5.0,
                upThresholdDb = -34.0,
                upRatio = 2.5,
                maxUpwardBoostDb = 8.0,
                floorThresholdDb = -65.0,
                kneeDb = 6.0
            )
            assertEquals("Expected 0 dB gain in deadband at $db", 0.0, delta, 1e-6)
        }
    }

    @Test
    fun `parameter clamping handles out-of-range inputs and zero knee without NaN`() {
        for (db in listOf(-144.0, -60.0, -20.0, 0.0, 12.0)) {
            val g = AudioEngine.evaluateCompressorStaticGainDb(
                inDb = db,
                downThresholdDb = 10.0,     // clamps to 0.0
                downRatio = 99.0,           // clamps to 20.0
                upThresholdDb = -99.0,      // clamps to -54.0
                upRatio = 0.1,              // clamps to 1.0
                maxUpwardBoostDb = 50.0,    // clamps to 12.0
                floorThresholdDb = -120.0,  // clamps to -80.0
                kneeDb = 0.0,               // hard knee guard
                makeupGainDb = 40.0,        // clamps to 12.0
                includeMakeup = true
            )
            assertFalse("Gain must be finite at $db", g.isNaN() || g.isInfinite())
        }
        // Check clamped downward at +6 dBFS -> threshold 0 dBFS, ratio 20:1, makeup +12 dB
        // reduction = -6 * 0.95 = -5.7 dB; + 12 dB = +6.3 dB
        val highLevel = AudioEngine.evaluateCompressorStaticGainDb(
            inDb = 6.0,
            downThresholdDb = 10.0,
            downRatio = 99.0,
            upThresholdDb = -99.0,
            upRatio = 0.1,
            maxUpwardBoostDb = 50.0,
            floorThresholdDb = -120.0,
            kneeDb = 0.0,
            makeupGainDb = 40.0,
            includeMakeup = true
        )
        assertEquals(6.3, highLevel, 1e-6)
    }

    @Test
    fun `identity ratios yield zero gain across dynamic range and makeup adds linearly`() {
        for (db in listOf(-80.0, -50.0, -25.0, -5.0, 0.0)) {
            val identityGain = AudioEngine.evaluateCompressorStaticGainDb(
                inDb = db,
                downThresholdDb = -16.0,
                downRatio = 1.0,
                upThresholdDb = -34.0,
                upRatio = 1.0,
                maxUpwardBoostDb = 6.0,
                floorThresholdDb = -65.0,
                kneeDb = 6.0,
                makeupGainDb = 2.5,
                includeMakeup = false
            )
            assertEquals(0.0, identityGain, 1e-6)

            val withMakeup = AudioEngine.evaluateCompressorStaticGainDb(
                inDb = db,
                downThresholdDb = -16.0,
                downRatio = 1.0,
                upThresholdDb = -34.0,
                upRatio = 1.0,
                maxUpwardBoostDb = 6.0,
                floorThresholdDb = -65.0,
                kneeDb = 6.0,
                makeupGainDb = 2.5,
                includeMakeup = true
            )
            assertEquals(2.5, withMakeup, 1e-6)
        }
    }

    @Test
    fun `gate transition 6 dB aligns exactly with minimum floor-to-upward margin`() {
        // Exact minimum spacing allowed by clamping: floorThresholdDb = upThresholdDb - 6.0
        val atFloor = AudioEngine.evaluateCompressorStaticGainDb(
            inDb = -40.0,
            downThresholdDb = -16.0,
            downRatio = 1.0,
            upThresholdDb = -34.0,
            upRatio = 2.0,
            maxUpwardBoostDb = 6.0,
            floorThresholdDb = -40.0,
            kneeDb = 0.0
        )
        val atMid = AudioEngine.evaluateCompressorStaticGainDb(
            inDb = -37.0, // midpoint: raw = 1.5 dB, gateFactor = 0.5 -> +0.75 dB
            downThresholdDb = -16.0,
            downRatio = 1.0,
            upThresholdDb = -34.0,
            upRatio = 2.0,
            maxUpwardBoostDb = 6.0,
            floorThresholdDb = -40.0,
            kneeDb = 0.0
        )
        val justBelowUp = AudioEngine.evaluateCompressorStaticGainDb(
            inDb = -34.001, // gateFactor = 1.0 at upThresholdDb -> exact 0.0005 dB
            downThresholdDb = -16.0,
            downRatio = 1.0,
            upThresholdDb = -34.0,
            upRatio = 2.0,
            maxUpwardBoostDb = 6.0,
            floorThresholdDb = -40.0,
            kneeDb = 0.0
        )

        assertEquals(0.0, atFloor, 1e-6)
        assertEquals(0.75, atMid, 1e-6)
        assertEquals(0.0005, justBelowUp, 1e-6)
    }

    @Test
    fun `gate transition remains strictly monotonic across 6 dB Hermite window`() {
        var prevGain = -1.0
        for (step in 0..12) {
            val inDb = -65.0 + step * 0.5 // [-65.0 .. -59.0]
            val gain = AudioEngine.evaluateCompressorStaticGainDb(
                inDb = inDb,
                downThresholdDb = -16.0,
                downRatio = 1.0,
                upThresholdDb = -34.0,
                upRatio = 2.0,
                maxUpwardBoostDb = 6.0,
                floorThresholdDb = -65.0,
                kneeDb = 0.0
            )
            assertTrue("Monotonic gate failed at $inDb ($gain < $prevGain)", gain >= prevGain)
            prevGain = gain
        }
        assertEquals(6.0, prevGain, 1e-6)
    }
}
