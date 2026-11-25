package com.yuka.musicplayer.audio

import kotlin.math.*

object PeqEvaluator {
    private const val REFERENCE_SAMPLE_RATE = 48000.0

    /**
     * Calculates the composite frequency response curve in dB for given [frequenciesHz].
     */
    fun evaluateCurve(
        bands: List<PeqBand>,
        frequenciesHz: FloatArray,
        preampDb: Float = 0f,
        sampleRateHz: Double = REFERENCE_SAMPLE_RATE
    ): FloatArray {
        val result = FloatArray(frequenciesHz.size)
        val fs = sampleRateHz.coerceAtLeast(8000.0)

        for (i in frequenciesHz.indices) {
            val f = frequenciesHz[i].toDouble().coerceIn(10.0, fs * 0.49)
            var totalGainDb = preampDb.toDouble()

            for (band in bands) {
                if (!band.enabled || abs(band.gainDb) < 0.005f) continue
                val gain = evaluateSingleBandDb(band, f, fs)
                totalGainDb += gain
            }
            result[i] = totalGainDb.toFloat()
        }
        return result
    }

    /**
     * Evaluates peak boost dB across the audible spectrum (both 44.1 kHz and 48 kHz)
     * for anti-clipping headroom.
     */
    fun calculateMaxBoost(bands: List<PeqBand>): Float {
        // 128 logarithmic frequency points from 20 Hz to 20,000 Hz across both 44.1 kHz & 48 kHz
        val numPoints = 128
        val logMin = log10(20.0)
        val logMax = log10(20000.0)
        val testFreqs = FloatArray(numPoints) { i ->
            val frac = i.toDouble() / (numPoints - 1)
            10.0.pow(logMin + frac * (logMax - logMin)).toFloat()
        }
        var maxVal = 0.0f
        for (fs in doubleArrayOf(44100.0, 48000.0)) {
            val curve = evaluateCurve(bands, testFreqs, 0f, fs)
            for (g in curve) {
                if (g > maxVal) maxVal = g
            }
        }
        return maxVal
    }

    private fun evaluateSingleBandDb(band: PeqBand, f: Double, fs: Double): Double {
        val f0 = band.freqHz.toDouble().coerceIn(10.0, fs * 0.45)
        val gainDb = band.gainDb.toDouble()
        val q = band.q.toDouble().coerceIn(0.1, 18.0)

        val w0 = 2.0 * Math.PI * (f0 / fs)
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val aVal = 10.0.pow(gainDb / 40.0)
        // Pure Robert Bristow-Johnson Audio EQ Cookbook bandwidth matching C++ engine
        val alpha = sinW0 / (2.0 * q)

        val b0: Double; val b1: Double; val b2: Double
        val a0: Double; val a1: Double; val a2: Double

        when (band.filterType) {
            PeqFilterType.LOW_SHELF -> {
                val twoSqrtAAlpha = 2.0 * sqrt(aVal) * alpha
                b0 = aVal * ((aVal + 1.0) - (aVal - 1.0) * cosW0 + twoSqrtAAlpha)
                b1 = 2.0 * aVal * ((aVal - 1.0) - (aVal + 1.0) * cosW0)
                b2 = aVal * ((aVal + 1.0) - (aVal - 1.0) * cosW0 - twoSqrtAAlpha)
                a0 = (aVal + 1.0) + (aVal - 1.0) * cosW0 + twoSqrtAAlpha
                a1 = -2.0 * ((aVal - 1.0) + (aVal + 1.0) * cosW0)
                a2 = (aVal + 1.0) + (aVal - 1.0) * cosW0 - twoSqrtAAlpha
            }
            PeqFilterType.HIGH_SHELF -> {
                val twoSqrtAAlpha = 2.0 * sqrt(aVal) * alpha
                b0 = aVal * ((aVal + 1.0) + (aVal - 1.0) * cosW0 + twoSqrtAAlpha)
                b1 = -2.0 * aVal * ((aVal - 1.0) + (aVal + 1.0) * cosW0)
                b2 = aVal * ((aVal + 1.0) + (aVal - 1.0) * cosW0 - twoSqrtAAlpha)
                a0 = (aVal + 1.0) - (aVal - 1.0) * cosW0 + twoSqrtAAlpha
                a1 = 2.0 * ((aVal - 1.0) - (aVal + 1.0) * cosW0)
                a2 = (aVal + 1.0) - (aVal - 1.0) * cosW0 - twoSqrtAAlpha
            }
            PeqFilterType.PEAKING -> {
                b0 = 1.0 + alpha * aVal
                b1 = -2.0 * cosW0
                b2 = 1.0 - alpha * aVal
                a0 = 1.0 + alpha / aVal
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha / aVal
            }
        }

        val w = 2.0 * Math.PI * (f / fs)
        val cosW = cos(w)
        val cos2W = cos(2.0 * w)
        val sinW = sin(w)
        val sin2W = sin(2.0 * w)

        val numReal = b0 + b1 * cosW + b2 * cos2W
        val numImag = -b1 * sinW - b2 * sin2W
        val denReal = a0 + a1 * cosW + a2 * cos2W
        val denImag = -a1 * sinW - a2 * sin2W

        val numMagSq = numReal * numReal + numImag * numImag
        val denMagSq = denReal * denReal + denImag * denImag

        return if (denMagSq > 1e-12) {
            10.0 * log10(numMagSq / denMagSq)
        } else {
            0.0
        }
    }
}
