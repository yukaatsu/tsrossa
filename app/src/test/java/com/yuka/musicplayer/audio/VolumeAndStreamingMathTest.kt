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
}
