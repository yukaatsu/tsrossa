package com.yuka.musicplayer.audio.compressor

object CompressorUiHelper {
    const val DEFAULT_SIDECHAIN_HPF_HZ = 90.0f
    const val MIN_SIDECHAIN_HPF_HZ = 20.0f
    const val MAX_SIDECHAIN_HPF_HZ = 250.0f

    const val MIN_DOWN_THRESHOLD_DB = -40.0f
    const val MAX_DOWN_THRESHOLD_DB = 0.0f
    const val MIN_UP_THRESHOLD_DB = -54.0f
    const val MAX_UP_THRESHOLD_DB = -6.0f
    const val MIN_FLOOR_THRESHOLD_DB = -80.0f
    const val MAX_FLOOR_THRESHOLD_DB = -30.0f
    const val FLOOR_TO_UP_MARGIN_DB = 6.0f

    fun isSidechainHpfEnabled(hpfHz: Float): Boolean = hpfHz > 1.0f

    fun resolveRestoredHpfHz(lastActiveHpfHz: Float): Float {
        return if (lastActiveHpfHz in MIN_SIDECHAIN_HPF_HZ..MAX_SIDECHAIN_HPF_HZ) {
            lastActiveHpfHz
        } else {
            DEFAULT_SIDECHAIN_HPF_HZ
        }
    }

    fun toggleSidechainHpf(
        config: CompressorConfig,
        enable: Boolean,
        lastActiveHpfHz: Float
    ): CompressorConfig {
        val nextHpf = if (enable) resolveRestoredHpfHz(lastActiveHpfHz) else 0.0f
        return config.copy(
            sidechainHpfHz = nextHpf,
            selectedPresetId = CompressorPresets.CUSTOM_ID
        ).clamped()
    }

    /**
     * Safe dynamic upper bound for Upward Threshold slider (always > MIN_UP_THRESHOLD_DB).
     */
    fun effectiveUpThresholdMax(downThresholdDb: Float): Float {
        val clampedDown = downThresholdDb.coerceIn(MIN_DOWN_THRESHOLD_DB, MAX_DOWN_THRESHOLD_DB)
        return minOf(clampedDown, MAX_UP_THRESHOLD_DB).coerceAtLeast(MIN_UP_THRESHOLD_DB + 0.5f)
    }

    /**
     * Safe dynamic upper bound for Noise-Floor Gate slider (always > MIN_FLOOR_THRESHOLD_DB).
     */
    fun effectiveFloorThresholdMax(upThresholdDb: Float): Float {
        val clampedUp = upThresholdDb.coerceIn(MIN_UP_THRESHOLD_DB, MAX_UP_THRESHOLD_DB)
        return minOf(clampedUp - FLOOR_TO_UP_MARGIN_DB, MAX_FLOOR_THRESHOLD_DB)
            .coerceAtLeast(MIN_FLOOR_THRESHOLD_DB + 0.5f)
    }
}
