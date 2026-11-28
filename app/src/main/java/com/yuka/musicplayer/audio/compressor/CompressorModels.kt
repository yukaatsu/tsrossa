package com.yuka.musicplayer.audio.compressor

import androidx.compose.runtime.Immutable
import kotlin.math.abs

@Immutable
data class CompressorConfig(
    val enabled: Boolean = false,
    val selectedPresetId: String = "balanced",
    val downThresholdDb: Float = -16.0f,
    val downRatio: Float = 2.8f,
    val upThresholdDb: Float = -36.0f,
    val upRatio: Float = 2.2f,
    val maxUpwardBoostDb: Float = 6.0f,
    val floorThresholdDb: Float = -65.0f,
    val kneeDb: Float = 6.0f,
    val attackMs: Float = 15.0f,
    val releaseMs: Float = 250.0f,
    val makeupGainDb: Float = 0.0f,
    val sidechainHpfHz: Float = 90.0f,
    val useRmsDetector: Boolean = true
) {
    /**
     * Clamps parameters identically to C++ `DynamicsCompressorState::updateCoefficientsLocked`
     * so UI, Transfer Curve, and native DSP coefficients remain bit-exact and monotonic.
     */
    fun clamped(): CompressorConfig {
        val clampedDownThresh = downThresholdDb.coerceIn(-40.0f, 0.0f)
        val clampedDownRatio = downRatio.coerceIn(1.0f, 20.0f)
        val clampedUpThresh = upThresholdDb.coerceIn(-54.0f, minOf(clampedDownThresh, -6.0f))
        val clampedUpRatio = upRatio.coerceIn(1.0f, 10.0f)
        val clampedMaxBoost = maxUpwardBoostDb.coerceIn(0.0f, 12.0f)
        val clampedFloor = floorThresholdDb.coerceIn(-80.0f, minOf(clampedUpThresh - 6.0f, -30.0f))
        val clampedKnee = kneeDb.coerceIn(0.0f, 12.0f)
        val clampedAttack = attackMs.coerceIn(0.5f, 100.0f)
        val clampedRelease = releaseMs.coerceIn(20.0f, 1000.0f)
        val clampedMakeup = makeupGainDb.coerceIn(-6.0f, 12.0f)
        val clampedHpf = if (sidechainHpfHz <= 1.0f) 0.0f else sidechainHpfHz.coerceIn(20.0f, 250.0f)

        return copy(
            downThresholdDb = clampedDownThresh,
            downRatio = clampedDownRatio,
            upThresholdDb = clampedUpThresh,
            upRatio = clampedUpRatio,
            maxUpwardBoostDb = clampedMaxBoost,
            floorThresholdDb = clampedFloor,
            kneeDb = clampedKnee,
            attackMs = clampedAttack,
            releaseMs = clampedRelease,
            makeupGainDb = clampedMakeup,
            sidechainHpfHz = clampedHpf
        )
    }

    /**
     * Compares all 12 acoustic parameters (ignoring `enabled` and `selectedPresetId`)
     * so toggling master bypass never falsely marks a saved Custom profile as modified.
     */
    fun matchesAcoustics(other: CompressorConfig): Boolean {
        val a = this.clamped()
        val b = other.clamped()
        val eps = 0.005f
        return abs(a.downThresholdDb - b.downThresholdDb) < eps &&
            abs(a.downRatio - b.downRatio) < eps &&
            abs(a.upThresholdDb - b.upThresholdDb) < eps &&
            abs(a.upRatio - b.upRatio) < eps &&
            abs(a.maxUpwardBoostDb - b.maxUpwardBoostDb) < eps &&
            abs(a.floorThresholdDb - b.floorThresholdDb) < eps &&
            abs(a.kneeDb - b.kneeDb) < eps &&
            abs(a.attackMs - b.attackMs) < eps &&
            abs(a.releaseMs - b.releaseMs) < eps &&
            abs(a.makeupGainDb - b.makeupGainDb) < eps &&
            abs(a.sidechainHpfHz - b.sidechainHpfHz) < eps &&
            a.useRmsDetector == b.useRmsDetector
    }
}

@Immutable
data class CompressorPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val config: CompressorConfig
)

@Immutable
data class SavedCustomCompressorProfile(
    val name: String = "Custom",
    val config: CompressorConfig = CompressorConfig(
        enabled = true,
        selectedPresetId = CompressorPresets.CUSTOM_ID
    ),
    val updatedAt: Long = 0L
)

object CompressorPresets {
    const val CUSTOM_ID = "custom"
    val NIGHT_MODE = CompressorPreset(
        id = "night_mode",
        name = "Night Mode",
        subtitle = "Tames loud peaks & lifts whisper detail",
        config = CompressorConfig(
            enabled = true,
            selectedPresetId = "night_mode",
            downThresholdDb = -20.0f,
            downRatio = 4.0f,
            upThresholdDb = -34.0f,
            upRatio = 2.5f,
            maxUpwardBoostDb = 7.0f,
            floorThresholdDb = -64.0f,
            kneeDb = 6.0f,
            attackMs = 8.0f,
            releaseMs = 260.0f,
            makeupGainDb = 0.0f,
            sidechainHpfHz = 90.0f,
            useRmsDetector = true
        )
    )

    val LOW_VOLUME_DETAIL = CompressorPreset(
        id = "low_volume_detail",
        name = "Low-Volume Detail",
        subtitle = "Reveals subtle harmonics at quiet listening",
        config = CompressorConfig(
            enabled = true,
            selectedPresetId = "low_volume_detail",
            downThresholdDb = -14.0f,
            downRatio = 2.0f,
            upThresholdDb = -38.0f,
            upRatio = 2.0f,
            maxUpwardBoostDb = 5.5f,
            floorThresholdDb = -66.0f,
            kneeDb = 6.0f,
            attackMs = 18.0f,
            releaseMs = 240.0f,
            makeupGainDb = 0.0f,
            sidechainHpfHz = 90.0f,
            useRmsDetector = true
        )
    )

    val BALANCED_DYNAMICS = CompressorPreset(
        id = "balanced",
        name = "Balanced Dynamics",
        subtitle = "Natural studio glue with transparent headroom",
        config = CompressorConfig(
            enabled = true,
            selectedPresetId = "balanced",
            downThresholdDb = -16.0f,
            downRatio = 2.8f,
            upThresholdDb = -36.0f,
            upRatio = 2.2f,
            maxUpwardBoostDb = 6.0f,
            floorThresholdDb = -65.0f,
            kneeDb = 6.0f,
            attackMs = 15.0f,
            releaseMs = 250.0f,
            makeupGainDb = 0.0f,
            sidechainHpfHz = 90.0f,
            useRmsDetector = true
        )
    )

    val ALL: List<CompressorPreset> = listOf(
        NIGHT_MODE,
        LOW_VOLUME_DETAIL,
        BALANCED_DYNAMICS
    )

    fun findById(id: String): CompressorPreset? = ALL.firstOrNull { it.id == id }
}
