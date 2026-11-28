package com.yuka.musicplayer.audio.compressor

import com.yuka.musicplayer.audio.AudioEngine
import kotlin.math.abs

object CompressorEvaluator {

    /**
     * Evaluates static input -> gain delta (dB) matching C++ `DynamicsCompressor::computeStaticGainDb`.
     */
    fun evaluateStaticGainDb(
        inDb: Double,
        config: CompressorConfig,
        includeMakeup: Boolean = true
    ): Double {
        return AudioEngine.evaluateCompressorStaticGainDb(
            inDb = inDb,
            downThresholdDb = config.downThresholdDb.toDouble(),
            downRatio = config.downRatio.toDouble(),
            upThresholdDb = config.upThresholdDb.toDouble(),
            upRatio = config.upRatio.toDouble(),
            maxUpwardBoostDb = config.maxUpwardBoostDb.toDouble(),
            floorThresholdDb = config.floorThresholdDb.toDouble(),
            kneeDb = config.kneeDb.toDouble(),
            makeupGainDb = config.makeupGainDb.toDouble(),
            includeMakeup = includeMakeup
        )
    }

    /**
     * Generates `(inputDb, outputDb)` points for the interactive transfer curve Canvas.
     */
    fun evaluateTransferCurve(
        config: CompressorConfig,
        minDb: Double = -72.0,
        maxDb: Double = 0.0,
        stepDb: Double = 2.0
    ): List<Pair<Float, Float>> {
        val points = mutableListOf<Pair<Float, Float>>()
        var inDb = minDb
        while (inDb <= maxDb + 0.001) {
            val clampedIn = inDb.coerceAtMost(maxDb)
            val gainDb = evaluateStaticGainDb(clampedIn, config, includeMakeup = true)
            val outDb = (clampedIn + gainDb).coerceIn(minDb - 6.0, maxDb + 6.0)
            points.add(clampedIn.toFloat() to outDb.toFloat())
            inDb += stepDb
        }
        return points
    }

    /**
     * Mirrors C++ `CompressorCoeffBank::hasActiveProcessing`.
     */
    fun hasActiveProcessing(config: CompressorConfig): Boolean {
        val c = config.clamped()
        return (c.downRatio > 1.001f) ||
            (c.upRatio > 1.001f && c.maxUpwardBoostDb > 0.01f) ||
            (abs(c.makeupGainDb) > 0.01f)
    }
}
