package com.yuka.musicplayer.audio

import androidx.compose.runtime.Immutable

enum class PeqFilterType(val id: Int, val label: String, val fullName: String) {
    PEAKING(0, "PK", "Peaking"),
    LOW_SHELF(1, "LS", "Low Shelf"),
    HIGH_SHELF(2, "HS", "High Shelf");

    companion object {
        fun fromId(id: Int): PeqFilterType = values().firstOrNull { it.id == id } ?: PEAKING
    }
}

@Immutable
data class AcousticRole(
    val shortLabel: String,
    val zoneName: String,
    val description: String
)

val ACOUSTIC_ROLES = listOf(
    AcousticRole("SUB", "Sub-Bass", "Deepest sub-bass rumble and low-end extension."),
    AcousticRole("BASS", "Deep Bass / Punch", "Kick drum impact and primary bassline authority."),
    AcousticRole("WARM", "Upper Bass / Warmth", "Instrument body fullness and warmth. Cut slightly if boomy."),
    AcousticRole("BODY", "Low-Mid / Body", "Lower vocal and guitar weight. Cut 1–2 dB if the mix sounds muddy."),
    AcousticRole("MID", "Center Midrange", "Core timbre of piano, guitars, and snare. Balances fullness vs. thinness."),
    AcousticRole("VOCAL", "Vocal Focus", "Lead vocal forwardness and intimacy in the soundstage."),
    AcousticRole("CLEAR", "Upper-Mid / Clarity", "Vocal articulation, acoustic string pluck, and instrument definition."),
    AcousticRole("PRES", "Presence / Attack", "Transient attack sharpness and immediate detail edge."),
    AcousticRole("BRIL", "Brilliance / Treble", "Cymbal shimmer, violin overtones, and hi-hat crispness. Cut to tame sibilance."),
    AcousticRole("AIR", "Air & Soundstage", "Top-octave air, spaciousness, room decay, and fine micro-detail.")
)

@Immutable
data class PeqBand(
    val index: Int,
    val freqHz: Float,
    val gainDb: Float,
    val q: Float = 0.75f,
    val filterType: PeqFilterType = PeqFilterType.PEAKING,
    val enabled: Boolean = true
) {
    val shortLabel: String
        get() = ACOUSTIC_ROLES.getOrNull(index)?.shortLabel ?: "EQ"

    val zoneName: String
        get() = ACOUSTIC_ROLES.getOrNull(index)?.zoneName ?: "Parametric EQ"

    val acousticDescription: String
        get() = ACOUSTIC_ROLES.getOrNull(index)?.description ?: ""
}

@Immutable
data class PeqConfiguration(
    val enabled: Boolean = false,
    val preampDb: Float = 0.0f,
    val autoPreampEnabled: Boolean = true,
    val selectedPresetId: String = "flat",
    val bands: List<PeqBand> = createDefaultBands()
) {
    val effectivePreampDb: Float
        get() = if (autoPreampEnabled) {
            val hasAnyActiveBand = bands.any { it.enabled && kotlin.math.abs(it.gainDb) >= 0.05f }
            if (!hasAnyActiveBand) {
                0.0f // 100% Bit-Perfect unity when all bands are flat
            } else {
                val maxPeak = PeqEvaluator.calculateMaxBoost(bands)
                // Full 100% peak boost compensation + 1.0 dB minimum-phase IIR overshoot & True-Peak (ISP) headroom
                // Protects dense DR4/DR5 brickwalled tracks ("Drown - BMTH") even under pure-cut EQ!
                val rawPreamp = if (maxPeak > 0.05f) -(maxPeak + 1.0f) else -1.0f
                rawPreamp.coerceAtLeast(-24.0f)
            }
        } else {
            preampDb
        }

    companion object {
        val DEFAULT_FREQUENCIES = floatArrayOf(
            31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
        )

        fun defaultQForBandIndex(idx: Int): Float = when (idx) {
            0, 9 -> 0.707f // Butterworth shelf (smooth tilt, zero ringing)
            else -> 1.41f  // True 1-octave Constant-Q (surgical bass/mid/treble separation, zero mid-bass mud)
        }

        fun createDefaultBands(): List<PeqBand> = DEFAULT_FREQUENCIES.mapIndexed { idx, freq ->
            PeqBand(
                index = idx,
                freqHz = freq,
                gainDb = 0.0f,
                q = defaultQForBandIndex(idx),
                filterType = when (idx) {
                    0 -> PeqFilterType.LOW_SHELF
                    9 -> PeqFilterType.HIGH_SHELF
                    else -> PeqFilterType.PEAKING
                },
                enabled = true
            )
        }
    }
}

@Immutable
data class PeqPreset(
    val id: String,
    val name: String,
    val bands: List<PeqBand>,
    val preampDb: Float = 0.0f
)

@Immutable
data class UserPeqPreset(
    val id: String,
    val name: String,
    val preampDb: Float = 0.0f,
    val autoPreampEnabled: Boolean = false,
    val bands: List<PeqBand> = PeqConfiguration.createDefaultBands(),
    val createdAt: Long = System.currentTimeMillis()
)

object PeqPresets {
    // Seluruh preset bawaan pabrik dihilangkan sesuai spesifikasi #NOTE 1.
    // Hanya menggunakan Reset Flat dan UserPeqPreset (Simpan / Export / Import).
    val ALL: List<PeqPreset> = emptyList()
}
