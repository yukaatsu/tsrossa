package com.yuka.musicplayer.ui.diagnostics

enum class SampleRateTransmissionState {
    NO_STREAM,
    UNKNOWN_INSUFFICIENT_DATA,
    UNKNOWN_UNVERIFIED_CLOCK,
    BIT_PERFECT,
    RESAMPLED
}

fun evaluateSampleRateTransmission(
    isPlaying: Boolean,
    sourceSampleRate: Int,
    outputSampleRate: Int,
    isSampleRateUnverified: Boolean = false
): SampleRateTransmissionState {
    if (!isPlaying) {
        return SampleRateTransmissionState.NO_STREAM
    }
    if (sourceSampleRate <= 0 || outputSampleRate <= 0) {
        return SampleRateTransmissionState.UNKNOWN_INSUFFICIENT_DATA
    }
    if (isSampleRateUnverified) {
        return SampleRateTransmissionState.UNKNOWN_UNVERIFIED_CLOCK
    }
    return if (sourceSampleRate == outputSampleRate) {
        SampleRateTransmissionState.BIT_PERFECT
    } else {
        SampleRateTransmissionState.RESAMPLED
    }
}

fun resolveTransmissionUiLabel(
    sourceSampleRate: Int,
    outputSampleRate: Int,
    isSampleRateUnverified: Boolean = false,
    isPlaying: Boolean = true
): String {
    return when (evaluateSampleRateTransmission(isPlaying, sourceSampleRate, outputSampleRate, isSampleRateUnverified)) {
        SampleRateTransmissionState.NO_STREAM -> "[ ⋯ NO STREAM ]"
        SampleRateTransmissionState.UNKNOWN_INSUFFICIENT_DATA,
        SampleRateTransmissionState.UNKNOWN_UNVERIFIED_CLOCK -> "[ ? UNKNOWN ]"
        SampleRateTransmissionState.BIT_PERFECT -> "[ ✓ BIT-PERFECT ]"
        SampleRateTransmissionState.RESAMPLED -> "[ ✗ RESAMPLED ]"
    }
}

fun resolveTransmissionReportStatus(
    sourceSampleRate: Int,
    outputSampleRate: Int,
    isSampleRateUnverified: Boolean = false,
    isPlaying: Boolean = true
): String {
    return when (evaluateSampleRateTransmission(isPlaying, sourceSampleRate, outputSampleRate, isSampleRateUnverified)) {
        SampleRateTransmissionState.NO_STREAM -> "STANDBY [NO STREAM]"
        SampleRateTransmissionState.UNKNOWN_INSUFFICIENT_DATA -> "UNKNOWN [INSUFFICIENT DATA]"
        SampleRateTransmissionState.UNKNOWN_UNVERIFIED_CLOCK -> "UNKNOWN [UNVERIFIED CLOCK]"
        SampleRateTransmissionState.BIT_PERFECT -> "BIT-PERFECT [PASS]"
        SampleRateTransmissionState.RESAMPLED -> "RESAMPLED [FAIL]"
    }
}

enum class BitDepthPathState {
    UNKNOWN,
    NATIVE_EXACT,
    LOSSLESS_CONTAINER,
    DOWNCONVERTED
}

fun evaluateBitDepthPath(
    sourceBitDepth: Int,
    outputBitDepth: Int
): BitDepthPathState {
    if (sourceBitDepth <= 0 || outputBitDepth <= 0) {
        return BitDepthPathState.UNKNOWN
    }
    return when {
        outputBitDepth == sourceBitDepth -> BitDepthPathState.NATIVE_EXACT
        outputBitDepth > sourceBitDepth -> BitDepthPathState.LOSSLESS_CONTAINER
        else -> BitDepthPathState.DOWNCONVERTED
    }
}

fun resolveBitDepthUiLabel(
    sourceBitDepth: Int,
    outputBitDepth: Int
): String {
    return when (evaluateBitDepthPath(sourceBitDepth, outputBitDepth)) {
        BitDepthPathState.UNKNOWN -> "[ ? UNKNOWN ]"
        BitDepthPathState.NATIVE_EXACT -> "[ ✓ NATIVE ${outputBitDepth}-BIT ]"
        BitDepthPathState.LOSSLESS_CONTAINER -> "[ ✓ ${sourceBitDepth}→${outputBitDepth}-BIT CONTAINER ]"
        BitDepthPathState.DOWNCONVERTED -> "[ ! ${sourceBitDepth}→${outputBitDepth}-BIT DOWNCONVERT ]"
    }
}

fun resolveBitDepthReportStatus(
    sourceBitDepth: Int,
    outputBitDepth: Int
): String {
    return when (evaluateBitDepthPath(sourceBitDepth, outputBitDepth)) {
        BitDepthPathState.UNKNOWN -> "UNKNOWN [INSUFFICIENT DATA]"
        BitDepthPathState.NATIVE_EXACT -> "NATIVE 1:1 (${outputBitDepth}-BIT) [EXACT]"
        BitDepthPathState.LOSSLESS_CONTAINER -> "LOSSLESS CONTAINER (${sourceBitDepth}-BIT -> ${outputBitDepth}-BIT) [PASS]"
        BitDepthPathState.DOWNCONVERTED -> "DOWNCONVERTED (${sourceBitDepth}-BIT -> ${outputBitDepth}-BIT) [TRUNCATED]"
    }
}

/**
 * Resolves the effective USB DAC output bit-depth based on the active volume/DSP pipeline mode:
 * - When Hybrid Hardware UAC is active ([isHardwareVolumeActive] == true && ![isPeqEnabled]):
 *   Prioritizes 1:1 Native Bit-Depth match ([sourceBitDepth], e.g. 24-bit -> 24-bit Native) if supported by the DAC.
 * - When Universal 64-Bit DSP or PEQ is active (![isHardwareVolumeActive] || [isPeqEnabled]):
 *   Prioritizes the highest supported container bit-depth >= [sourceBitDepth] (e.g. 32-bit container)
 *   so 64-bit floating-point attenuation and biquad filtering retain maximum dynamic range.
 */
fun resolveEffectiveOutputBitDepth(
    sourceBitDepth: Int,
    rawNegotiatedBitDepth: Int,
    supportedBitDepths: String,
    isHardwareVolumeActive: Boolean,
    isPeqEnabled: Boolean,
    isCompressorEnabled: Boolean = false,
    isPlaying: Boolean = false
): Int {
    if (isPlaying && rawNegotiatedBitDepth > 0) {
        return rawNegotiatedBitDepth
    }
    val supportedSet = supportedBitDepths
        .split(Regex("[,/|\\s]+"))
        .mapNotNull { token ->
            token.trim().removeSuffix("-bit").removeSuffix("-Bit").removeSuffix("bit").trim().toIntOrNull()
        }
        .filter { it > 0 }
        .toSet()

    val effectiveSource = if (sourceBitDepth > 0) sourceBitDepth else rawNegotiatedBitDepth
    if (effectiveSource <= 0) return rawNegotiatedBitDepth

    val usePureOneToOneNative = isHardwareVolumeActive && !isPeqEnabled && !isCompressorEnabled
    return if (usePureOneToOneNative) {
        when {
            supportedSet.contains(effectiveSource) -> effectiveSource
            supportedSet.isNotEmpty() -> {
                supportedSet.filter { it >= effectiveSource }.minOrNull()
                    ?: supportedSet.maxOrNull()
                    ?: rawNegotiatedBitDepth.takeIf { it > 0 }
                    ?: effectiveSource
            }
            else -> effectiveSource
        }
    } else {
        when {
            supportedSet.isNotEmpty() -> {
                supportedSet.filter { it >= effectiveSource }.maxOrNull()
                    ?: supportedSet.maxOrNull()
                    ?: rawNegotiatedBitDepth.takeIf { it > 0 }
                    ?: effectiveSource
            }
            rawNegotiatedBitDepth > effectiveSource -> rawNegotiatedBitDepth
            else -> effectiveSource
        }
    }
}

fun resolveDspPipelineStatusLabel(
    isBitPerfectDirectPass: Boolean,
    isPeqEnabled: Boolean,
    isCompressorEnabled: Boolean = false,
    isHardwareVolumeActive: Boolean = false
): String {
    return when {
        isPeqEnabled && isCompressorEnabled -> "ACTIVE (64-Bit PEQ + Dynamics)"
        isPeqEnabled -> "ACTIVE (64-Bit 10-Band PEQ)"
        isCompressorEnabled -> "ACTIVE (64-Bit Dynamics Compressor)"
        isBitPerfectDirectPass -> "BYPASSED (100% Bit-Perfect)"
        !isHardwareVolumeActive -> "ACTIVE (64-Bit Float Gain)"
        else -> "BYPASSED (100% Bit-Perfect)"
    }
}

fun resolveConfiguredMusicRootPath(
    kewPrefsRoot: String?,
    legacyPrefsRoot: String?,
    legacyTreePath: String?,
    lastScanRootPath: String?
): String {
    return kewPrefsRoot?.takeIf { it.isNotBlank() }
        ?: legacyPrefsRoot?.takeIf { it.isNotBlank() }
        ?: legacyTreePath?.takeIf { it.isNotBlank() }
        ?: lastScanRootPath?.takeIf { it.isNotBlank() }
        ?: "(Not Configured)"
}
