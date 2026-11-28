package com.yuka.musicplayer.ui.diagnostics

import androidx.compose.runtime.Immutable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

enum class AudioQualityTier {
    HI_RES,
    LOSSLESS,
    CODEC_FALLBACK
}

enum class SignalPathStatus {
    BIT_PERFECT,
    HYBRID_UAC_TRIM,
    UNIVERSAL_DSP,
    WARNING,
    NO_DAC
}

@Immutable
data class AudioQualityBadgeModel(
    val tier: AudioQualityTier,
    val primaryLabel: String,
    val showMqaMicroTag: Boolean = false
)

@Immutable
data class ConnectedDacIdentity(
    val productName: String = "",
    val manufacturerName: String = "",
    val vid: Int = 0,
    val pid: Int = 0
) {
    val displayName: String
        get() = listOf(manufacturerName.trim(), productName.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifBlank { "USB Audio DAC" }

    val vidPidHex: String
        get() = if (vid > 0 || pid > 0) {
            String.format(Locale.US, "%04X:%04X", vid and 0xFFFF, pid and 0xFFFF)
        } else {
            "—"
        }
}

@Immutable
data class LiveSignalPathSnapshot(
    val appliedHwDb: Float = 0.0f,
    val dspGainDb: Float = 0.0f,
    val compGainDb: Float = 0.0f,
    val limiterGrDb: Float = 0.0f,
    val resVolumeDb: Float = 1.0f,
    val maxVolumeDb: Float = 0.0f,
    val estimatedLatencyMs: Float = 0.0f,
    val bitPerfectDirectPass: Boolean = false,
    val hwVolumeActive: Boolean = false,
    val peqEffective: Boolean = false,
    val compEffective: Boolean = false,
    val hasNextTrack: Boolean = false,
    val isAsyncIso: Boolean = false,
    val isMlockActive: Boolean = false,
    val isRtSchedFifoActive: Boolean = false,
    val sourceIsFloatPcm: Boolean = false,
    val negotiatedBitResolution: Int = 0,
    val liveChannels: Int = 0,
    val activeIsoTransfers: Int = 0,
    val activeIsoPacketsPerTransfer: Int = 0,
    val usbFramesPerSec: Int = 0
) {
    companion object {
        const val FLOAT_ARRAY_SIZE = 7
        const val INT_ARRAY_SIZE = 6

        const val FLAG_BIT_PERFECT = 1 shl 0
        const val FLAG_HW_ACTIVE = 1 shl 1
        const val FLAG_PEQ_EFFECTIVE = 1 shl 2
        const val FLAG_COMP_EFFECTIVE = 1 shl 3
        const val FLAG_HAS_NEXT_TRACK = 1 shl 4
        const val FLAG_IS_ASYNC = 1 shl 5
        const val FLAG_MLOCK_ACTIVE = 1 shl 6
        const val FLAG_RT_SCHED_FIFO = 1 shl 7
        const val FLAG_SOURCE_FLOAT_PCM = 1 shl 8

        val EMPTY = LiveSignalPathSnapshot()

        fun quantizeTenthDb(db: Float): Float {
            if (!db.isFinite() || db <= -120f) return -128.0f
            return round(db * 10.0f) / 10.0f
        }

        fun fromRawArrays(floats: FloatArray, ints: IntArray): LiveSignalPathSnapshot {
            if (floats.size < FLOAT_ARRAY_SIZE || ints.size < INT_ARRAY_SIZE) {
                return EMPTY
            }
            val flags = ints[0]
            val rawLatency = floats[6]
            return LiveSignalPathSnapshot(
                appliedHwDb = quantizeTenthDb(floats[0]),
                dspGainDb = quantizeTenthDb(floats[1]),
                compGainDb = quantizeTenthDb(floats[2]),
                limiterGrDb = quantizeTenthDb(floats[3]),
                resVolumeDb = quantizeTenthDb(floats[4]),
                maxVolumeDb = quantizeTenthDb(floats[5]),
                estimatedLatencyMs = if (rawLatency.isFinite() && rawLatency > 0f) {
                    round(rawLatency * 10.0f) / 10.0f
                } else {
                    0.0f
                },
                bitPerfectDirectPass = (flags and FLAG_BIT_PERFECT) != 0,
                hwVolumeActive = (flags and FLAG_HW_ACTIVE) != 0,
                peqEffective = (flags and FLAG_PEQ_EFFECTIVE) != 0,
                compEffective = (flags and FLAG_COMP_EFFECTIVE) != 0,
                hasNextTrack = (flags and FLAG_HAS_NEXT_TRACK) != 0,
                isAsyncIso = (flags and FLAG_IS_ASYNC) != 0,
                isMlockActive = (flags and FLAG_MLOCK_ACTIVE) != 0,
                isRtSchedFifoActive = (flags and FLAG_RT_SCHED_FIFO) != 0,
                sourceIsFloatPcm = (flags and FLAG_SOURCE_FLOAT_PCM) != 0,
                negotiatedBitResolution = ints[1].coerceAtLeast(0),
                liveChannels = ints[2].coerceAtLeast(0),
                activeIsoTransfers = ints[3].coerceAtLeast(0),
                activeIsoPacketsPerTransfer = ints[4].coerceAtLeast(0),
                usbFramesPerSec = ints[5].coerceAtLeast(0)
            )
        }
    }
}

fun classifyAudioQualityBadge(
    codec: String,
    bitDepth: Int,
    sampleRateHz: Int,
    isMqa: Boolean = false,
    isFloatPcm: Boolean = false
): AudioQualityBadgeModel {
    val tier: AudioQualityTier
    val label: String
    when {
        bitDepth >= 24 || sampleRateHz > 48000 || isFloatPcm -> {
            tier = AudioQualityTier.HI_RES
            label = "Hi-Res Lossless"
        }
        bitDepth > 0 || sampleRateHz > 0 -> {
            tier = AudioQualityTier.LOSSLESS
            label = "Lossless"
        }
        else -> {
            tier = AudioQualityTier.CODEC_FALLBACK
            label = codec.trim().uppercase().ifEmpty { "AUDIO" }
        }
    }
    return AudioQualityBadgeModel(
        tier = tier,
        primaryLabel = label,
        showMqaMicroTag = isMqa
    )
}

fun buildAudioQualityAccessibilityLabel(badgeModel: AudioQualityBadgeModel): String {
    val mqaPart = if (badgeModel.showMqaMicroTag) " MQA" else ""
    return "${badgeModel.primaryLabel}$mqaPart audio. Open Signal Path Inspector"
}

fun resolveSignalPathStatus(
    isDacConnected: Boolean,
    isPlaying: Boolean,
    isBitPerfectDirectPass: Boolean,
    isHardwareVolumeActive: Boolean,
    sampleRateState: SampleRateTransmissionState,
    isDeviceWedged: Boolean,
    isHardwareVolumeLockedBySystem: Boolean
): SignalPathStatus {
    if (!isDacConnected) {
        return SignalPathStatus.NO_DAC
    }
    if (isDeviceWedged ||
        isHardwareVolumeLockedBySystem ||
        sampleRateState == SampleRateTransmissionState.RESAMPLED ||
        sampleRateState == SampleRateTransmissionState.UNKNOWN_UNVERIFIED_CLOCK
    ) {
        return SignalPathStatus.WARNING
    }
    if (isBitPerfectDirectPass &&
        (!isPlaying || sampleRateState == SampleRateTransmissionState.BIT_PERFECT)
    ) {
        return SignalPathStatus.BIT_PERFECT
    }
    if (isHardwareVolumeActive) {
        return SignalPathStatus.HYBRID_UAC_TRIM
    }
    return SignalPathStatus.UNIVERSAL_DSP
}

fun buildActiveDspSummaryTags(
    isDacConnected: Boolean,
    isBitPerfectDirectPass: Boolean,
    isHardwareVolumeActive: Boolean,
    isPeqEffective: Boolean,
    isCompEffective: Boolean,
    isVolumeAttenuating: Boolean
): String {
    if (!isDacConnected) return "Standby"
    if (isBitPerfectDirectPass) return "Bypass"
    if (isHardwareVolumeActive) return "Fine-Trim • TPDF"

    val tags = ArrayList<String>(5)
    if (isPeqEffective) tags.add("PEQ")
    if (isCompEffective) tags.add("DYN")
    if (isVolumeAttenuating) tags.add("VOL")
    if (isPeqEffective || isCompEffective) tags.add("LIM")
    tags.add("TPDF")
    return tags.joinToString(" • ")
}

fun formatDbReadout(db: Float, showPositivePlus: Boolean = true): String {
    if (!db.isFinite() || db <= -120f) return "-∞ dB"
    if (abs(db) < 0.05f) return "0.0 dB"
    return if (db > 0f && showPositivePlus) {
        String.format(Locale.US, "+%.1f dB", db)
    } else {
        String.format(Locale.US, "%.1f dB", db)
    }
}

fun formatSampleRateKhz(hz: Int): String {
    if (hz <= 0) return "—"
    return if (hz % 1000 == 0) {
        "${hz / 1000} kHz"
    } else {
        String.format(Locale.US, "%.1f kHz", hz / 1000.0)
    }
}

fun formatUsbBufferProfileLabel(profile: Int): String = when (profile) {
    0 -> "Low 64ms"
    2 -> "High 192ms"
    else -> "Balanced 160ms"
}
