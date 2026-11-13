package com.yuka.musicplayer.audio

import android.content.SharedPreferences
import kotlin.math.*

enum class PeqFilterType(val id: Int, val label: String, val fullName: String) {
    PEAKING(0, "PK", "Peaking"),
    LOW_SHELF(1, "LS", "Low Shelf"),
    HIGH_SHELF(2, "HS", "High Shelf");

    companion object {
        fun fromId(id: Int): PeqFilterType = values().firstOrNull { it.id == id } ?: PEAKING
    }
}

data class AcousticRole(
    val shortLabel: String,
    val zoneName: String,
    val description: String
)

val ACOUSTIC_ROLES = listOf(
    AcousticRole("SUB", "Sub-Bass", "Getaran bass paling dalam (rumble bioskop & sub-bass elektronik)."),
    AcousticRole("BASS", "Deep Bass / Punch", "Hentakan kick drum dan tenaga utama bassline agar terasa powerful."),
    AcousticRole("WARM", "Upper Bass / Warmth", "Kehangatan dan ketebalan bodi instrumen. Turunkan jika terasa boomy."),
    AcousticRole("BODY", "Low-Mid / Body", "Kepadatan vokal bawah & gitar. Kurangi 1–2 dB jika terdengar keruh (muddy)."),
    AcousticRole("MID", "Center Midrange", "Inti suara piano, gitar, dan snare. Mengatur keseimbangan penuh atau tipisnya musik."),
    AcousticRole("VOCAL", "Vocal Focus", "Posisi maju-mundurnya vokal penyanyi. Naikkan agar vokal lebih dekat di depan."),
    AcousticRole("CLEAR", "Upper-Mid / Clarity", "Artikulasi bibir vokal, petikan senar akustik, dan ketegasan instrumen."),
    AcousticRole("PRES", "Presence / Attack", "Detail serangan instrumen (transient attack) & kejelasan tepi suara."),
    AcousticRole("BRIL", "Brilliance / Treble", "Kilau simbal, biola, dan gemerincing hi-hat. Turunkan jika terasa menusuk (sibilance)."),
    AcousticRole("AIR", "Air & Soundstage", "Udara, kesan panggung luas (soundstage), gema ruangan, dan micro-detail halus.")
)

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

data class PeqConfiguration(
    val enabled: Boolean = false,
    val preampDb: Float = 0.0f,
    val autoPreampEnabled: Boolean = true,
    val selectedPresetId: String = "flat",
    val bands: List<PeqBand> = createDefaultBands()
) {
    val effectivePreampDb: Float
        get() = if (autoPreampEnabled) {
            val maxPeak = PeqEvaluator.calculateMaxBoost(bands)
            val hasAnyActiveFilter = bands.any { it.enabled && abs(it.gainDb) >= 0.05f }
            if (maxPeak > 0.05f) {
                // High-headroom anti-congestion compensation: 85% peak attenuation + 0.6 dB phase-rotation margin
                (-(maxPeak * 0.85f + 0.6f)).coerceAtLeast(-10.0f)
            } else if (hasAnyActiveFilter) {
                // Even pure cuts rotate biquad phase and cause ~0.5 dB time-domain peak overshoot on dense tracks
                -0.6f
            } else {
                0.0f
            }
        } else {
            preampDb
        }

    companion object {
        val DEFAULT_FREQUENCIES = floatArrayOf(
            31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
        )

        fun defaultQForBandIndex(idx: Int): Float = when (idx) {
            0, 9 -> 0.707f // Butterworth shelf
            1, 2 -> 0.85f  // Punchy bass warmth
            else -> 1.05f  // 1-octave proportional Q for clean instrument separation in mids & highs
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

data class PeqPreset(
    val id: String,
    val name: String,
    val description: String,
    val bands: List<PeqBand>
)

data class UserPeqPreset(
    val id: String,
    val name: String,
    val preampDb: Float,
    val autoPreampEnabled: Boolean,
    val bands: List<PeqBand>,
    val createdAt: Long = System.currentTimeMillis()
)

object PeqPresets {
    // Seluruh preset bawaan pabrik dihilangkan sesuai spesifikasi #NOTE 1.
    // Hanya menggunakan Reset Flat dan UserPeqPreset (Simpan / Export / Import).
    val ALL: List<PeqPreset> = emptyList()
}

object PeqEvaluator {
    private const val REFERENCE_SAMPLE_RATE = 48000.0

    /**
     * Calculates the composite frequency response curve in dB for given [frequenciesHz].
     */
    fun evaluateCurve(
        bands: List<PeqBand>,
        frequenciesHz: FloatArray,
        preampDb: Float = 0f
    ): FloatArray {
        val result = FloatArray(frequenciesHz.size)
        val fs = REFERENCE_SAMPLE_RATE

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
     * Evaluates peak boost dB across the audible spectrum for anti-clipping headroom.
     */
    fun calculateMaxBoost(bands: List<PeqBand>): Float {
        // 64 logarithmic frequency points from 20 Hz to 20,000 Hz for high-resolution peak evaluation
        val numPoints = 64
        val logMin = log10(20.0)
        val logMax = log10(20000.0)
        val testFreqs = FloatArray(numPoints) { i ->
            val frac = i.toDouble() / (numPoints - 1)
            10.0.pow(logMin + frac * (logMax - logMin)).toFloat()
        }
        val curve = evaluateCurve(bands, testFreqs, 0f)
        var maxVal = 0.0f
        for (g in curve) {
            if (g > maxVal) maxVal = g
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
        var alpha = sinW0 / (2.0 * q)

        // Matches C++ Nyquist bandwidth stabilization for high-frequency peaking filters
        if (band.filterType == PeqFilterType.PEAKING && f0 > fs * 0.18) {
            val cosHalfW0 = cos(w0 * 0.5)
            if (cosHalfW0 > 0.05) {
                alpha *= cosHalfW0.pow(0.35)
            }
        }

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

object PeqPreferencesHelper {
    private const val PREF_PEQ_ENABLED = "peq_enabled"
    private const val PREF_PEQ_PREAMP_DB = "peq_preamp_db"
    private const val PREF_PEQ_AUTO_PREAMP = "peq_auto_preamp"
    private const val PREF_PEQ_PRESET = "peq_preset"
    private const val PREF_PEQ_USER_PRESETS_JSON = "peq_user_presets_json"
    private const val PREF_PEQ_LAST_CUSTOM_SNAPSHOT_JSON = "peq_last_custom_snapshot_json"

    fun load(prefs: SharedPreferences): PeqConfiguration {
        val enabled = prefs.getBoolean(PREF_PEQ_ENABLED, false)
        val preampDb = prefs.getFloat(PREF_PEQ_PREAMP_DB, 0.0f)
        val autoPreamp = prefs.getBoolean(PREF_PEQ_AUTO_PREAMP, true)
        val loadedPresetId = prefs.getString(PREF_PEQ_PRESET, "flat") ?: "flat"

        val defaultBands = PeqConfiguration.createDefaultBands()
        val loadedBands = defaultBands.map { band ->
            val pfx = "peq_band_${band.index}_"
            val freq = prefs.getFloat("${pfx}freq", band.freqHz)
            val gain = prefs.getFloat("${pfx}gain", band.gainDb)
            val q = prefs.getFloat("${pfx}q", band.q)
            val typeId = prefs.getInt("${pfx}type", band.filterType.id)
            val bandEnabled = prefs.getBoolean("${pfx}enabled", band.enabled)

            band.copy(
                freqHz = freq,
                gainDb = gain,
                q = q,
                filterType = PeqFilterType.fromId(typeId),
                enabled = bandEnabled
            )
        }

        val userPresets = loadUserPresets(prefs)
        val presetId = if (loadedPresetId == "flat" || loadedPresetId == "custom" || userPresets.any { it.id == loadedPresetId }) {
            loadedPresetId
        } else {
            if (loadedBands.any { abs(it.gainDb) >= 0.05f }) "custom" else "flat"
        }

        return PeqConfiguration(
            enabled = enabled,
            preampDb = preampDb,
            autoPreampEnabled = autoPreamp,
            selectedPresetId = presetId,
            bands = loadedBands
        )
    }

    fun save(prefs: SharedPreferences, config: PeqConfiguration) {
        val editor = prefs.edit()
        editor.putBoolean(PREF_PEQ_ENABLED, config.enabled)
        editor.putFloat(PREF_PEQ_PREAMP_DB, config.preampDb)
        editor.putBoolean(PREF_PEQ_AUTO_PREAMP, config.autoPreampEnabled)
        editor.putString(PREF_PEQ_PRESET, config.selectedPresetId)

        config.bands.forEach { band ->
            val pfx = "peq_band_${band.index}_"
            editor.putFloat("${pfx}freq", band.freqHz)
            editor.putFloat("${pfx}gain", band.gainDb)
            editor.putFloat("${pfx}q", band.q)
            editor.putInt("${pfx}type", band.filterType.id)
            editor.putBoolean("${pfx}enabled", band.enabled)
        }
        editor.apply()

        // Auto snapshot saat config adalah custom agar eksperimen tidak hilang tertimpa
        if (config.selectedPresetId == "custom") {
            saveLastCustomSnapshot(prefs, config)
        }
    }

    fun serializeBandsToJson(bands: List<PeqBand>): org.json.JSONArray {
        val arr = org.json.JSONArray()
        bands.forEach { b ->
            val obj = org.json.JSONObject().apply {
                put("index", b.index)
                put("freqHz", b.freqHz.toDouble())
                put("gainDb", b.gainDb.toDouble())
                put("q", b.q.toDouble())
                put("filterType", b.filterType.id)
                put("enabled", b.enabled)
            }
            arr.put(obj)
        }
        return arr
    }

    fun deserializeBandsFromJson(arr: org.json.JSONArray?): List<PeqBand> {
        val defaults = PeqConfiguration.createDefaultBands()
        if (arr == null || arr.length() == 0) return defaults
        val mapByIdx = mutableMapOf<Int, PeqBand>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val idx = obj.optInt("index", i)
            val def = defaults.getOrElse(idx) { defaults.first() }
            mapByIdx[idx] = def.copy(
                index = idx,
                freqHz = obj.optDouble("freqHz", def.freqHz.toDouble()).toFloat(),
                gainDb = obj.optDouble("gainDb", def.gainDb.toDouble()).toFloat(),
                q = obj.optDouble("q", def.q.toDouble()).toFloat(),
                filterType = PeqFilterType.fromId(obj.optInt("filterType", def.filterType.id)),
                enabled = obj.optBoolean("enabled", true)
            )
        }
        return defaults.map { d -> mapByIdx[d.index] ?: d }
    }

    fun loadUserPresets(prefs: SharedPreferences): List<UserPeqPreset> {
        val raw = prefs.getString(PREF_PEQ_USER_PRESETS_JSON, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<UserPeqPreset>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                if (id.isBlank()) continue
                val name = obj.optString("name", "Custom Preset").trim().ifBlank { "Custom Preset" }
                val preampDb = obj.optDouble("preampDb", 0.0).toFloat()
                val autoPreamp = obj.optBoolean("autoPreampEnabled", false)
                val createdAt = obj.optLong("createdAt", 0L)
                val bands = deserializeBandsFromJson(obj.optJSONArray("bands"))
                list.add(
                    UserPeqPreset(
                        id = id,
                        name = name,
                        preampDb = preampDb,
                        autoPreampEnabled = autoPreamp,
                        bands = bands,
                        createdAt = createdAt
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveUserPresets(prefs: SharedPreferences, presets: List<UserPeqPreset>) {
        val arr = org.json.JSONArray()
        presets.forEach { p ->
            val obj = org.json.JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("preampDb", p.preampDb.toDouble())
                put("autoPreampEnabled", p.autoPreampEnabled)
                put("createdAt", p.createdAt)
                put("bands", serializeBandsToJson(p.bands))
            }
            arr.put(obj)
        }
        prefs.edit().putString(PREF_PEQ_USER_PRESETS_JSON, arr.toString()).apply()
    }

    fun saveOrUpdateUserPreset(
        prefs: SharedPreferences,
        name: String,
        config: PeqConfiguration,
        overwritePresetId: String? = null
    ): Pair<List<UserPeqPreset>, UserPeqPreset> {
        val cleanName = name.trim().ifBlank { "My EQ Preset" }
        val existing = loadUserPresets(prefs).toMutableList()
        val targetIdx = if (overwritePresetId != null) {
            existing.indexOfFirst { it.id == overwritePresetId }
        } else {
            existing.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
        }

        val savedPreset = if (targetIdx >= 0) {
            val old = existing[targetIdx]
            old.copy(
                name = cleanName,
                preampDb = config.preampDb,
                autoPreampEnabled = config.autoPreampEnabled,
                bands = config.bands
            ).also { existing[targetIdx] = it }
        } else {
            UserPeqPreset(
                id = "user_${System.currentTimeMillis()}_${(100..999).random()}",
                name = cleanName,
                preampDb = config.preampDb,
                autoPreampEnabled = config.autoPreampEnabled,
                bands = config.bands
            ).also { existing.add(0, it) } // Taruh preset terbaru di paling depan
        }

        saveUserPresets(prefs, existing)
        return existing to savedPreset
    }

    fun deleteUserPreset(prefs: SharedPreferences, presetId: String): List<UserPeqPreset> {
        val updated = loadUserPresets(prefs).filterNot { it.id == presetId }
        saveUserPresets(prefs, updated)
        return updated
    }

    fun saveLastCustomSnapshot(prefs: SharedPreferences, config: PeqConfiguration) {
        try {
            val obj = org.json.JSONObject().apply {
                put("preampDb", config.preampDb.toDouble())
                put("autoPreampEnabled", config.autoPreampEnabled)
                put("bands", serializeBandsToJson(config.bands))
            }
            prefs.edit().putString(PREF_PEQ_LAST_CUSTOM_SNAPSHOT_JSON, obj.toString()).apply()
        } catch (_: Exception) {}
    }

    fun loadLastCustomSnapshot(prefs: SharedPreferences): PeqConfiguration? {
        val raw = prefs.getString(PREF_PEQ_LAST_CUSTOM_SNAPSHOT_JSON, null) ?: return null
        return try {
            val obj = org.json.JSONObject(raw)
            val preampDb = obj.optDouble("preampDb", 0.0).toFloat()
            val autoPreamp = obj.optBoolean("autoPreampEnabled", false)
            val bands = deserializeBandsFromJson(obj.optJSONArray("bands"))
            PeqConfiguration(
                enabled = true,
                preampDb = preampDb,
                autoPreampEnabled = autoPreamp,
                selectedPresetId = "custom",
                bands = bands
            )
        } catch (_: Exception) {
            null
        }
    }

    const val SHARE_CODE_PREFIX = "TSROSSA-PEQ1:"

    fun createPresetFromCurrentConfig(name: String, config: PeqConfiguration): UserPeqPreset {
        val cleanName = name.trim().ifBlank { "Custom EQ" }
        return UserPeqPreset(
            id = "user_${System.currentTimeMillis()}_${(100..999).random()}",
            name = cleanName,
            preampDb = config.preampDb,
            autoPreampEnabled = config.autoPreampEnabled,
            bands = config.bands,
            createdAt = System.currentTimeMillis()
        )
    }

    private fun serializePresetToJson(preset: UserPeqPreset): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("id", preset.id)
            put("name", preset.name)
            put("preampDb", preset.preampDb.toDouble())
            put("autoPreampEnabled", preset.autoPreampEnabled)
            put("createdAt", preset.createdAt)
            put("bands", serializeBandsToJson(preset.bands))
        }
    }

    private fun deserializePresetFromJson(obj: org.json.JSONObject): UserPeqPreset? {
        val name = obj.optString("name", "").trim().ifBlank { obj.optString("presetName", "").trim() }
        if (name.isBlank() && !obj.has("bands")) return null
        val id = obj.optString("id", "").ifBlank { "user_${System.currentTimeMillis()}_${(100..999).random()}" }
        val finalName = if (name.isBlank()) "Imported EQ" else name
        val preampDb = obj.optDouble("preampDb", 0.0).toFloat().coerceIn(-12f, 6f)
        val autoPreamp = obj.optBoolean("autoPreampEnabled", true)
        val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
        val bands = deserializeBandsFromJson(obj.optJSONArray("bands"))
        return UserPeqPreset(
            id = id,
            name = finalName,
            preampDb = preampDb,
            autoPreampEnabled = autoPreamp,
            bands = bands,
            createdAt = createdAt
        )
    }

    /**
     * Exports a preset to a compact single-line share code format: TSROSSA-PEQ1:<Base64>
     */
    fun exportPresetToShareCode(preset: UserPeqPreset, includeHeader: Boolean = true): String {
        val obj = serializePresetToJson(preset)
        val jsonBytes = obj.toString().toByteArray(Charsets.UTF_8)
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(jsonBytes)
        val code = "$SHARE_CODE_PREFIX$b64"
        return if (includeHeader) {
            "🎛️ TSROSSA PEQ Preset: \"${preset.name}\"\n$code"
        } else {
            code
        }
    }

    /**
     * Exports presets to standard JSON string format.
     */
    fun exportPresetsToJsonString(presets: List<UserPeqPreset>, prettyPrint: Boolean = true): String {
        val root = org.json.JSONObject().apply {
            put("app", "TSROSSA")
            put("formatVersion", 1)
            put("exportedAt", System.currentTimeMillis())
            val arr = org.json.JSONArray()
            presets.forEach { arr.put(serializePresetToJson(it)) }
            put("presets", arr)
        }
        return if (prettyPrint) root.toString(2) else root.toString()
    }

    /**
     * Robust parser for TSROSSA-PEQ1 code, JSON single preset, or JSON bundle.
     * Can extract TSROSSA-PEQ1 codes even if surrounded by chat text or discord messages.
     */
    fun parsePresetsFromImportText(rawText: String): List<UserPeqPreset> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val results = mutableListOf<UserPeqPreset>()

        // 1. Try finding all TSROSSA-PEQ1: codes via Regex
        val codeRegex = Regex("${Regex.escape(SHARE_CODE_PREFIX)}([A-Za-z0-9+/=_-]+)")
        val matches = codeRegex.findAll(trimmed)
        for (m in matches) {
            val payload = m.groupValues[1]
            try {
                val decodedBytes = try {
                    java.util.Base64.getUrlDecoder().decode(payload)
                } catch (_: Exception) {
                    java.util.Base64.getDecoder().decode(payload)
                }
                val jsonStr = String(decodedBytes, Charsets.UTF_8)
                val obj = org.json.JSONObject(jsonStr)
                val preset = deserializePresetFromJson(obj)
                if (preset != null) results.add(preset)
            } catch (_: Exception) {}
        }
        if (results.isNotEmpty()) return results

        // 2. Try parsing as JSON object or JSON array
        try {
            if (trimmed.startsWith("{")) {
                val root = org.json.JSONObject(trimmed)
                if (root.has("presets")) {
                    val arr = root.optJSONArray("presets")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val p = deserializePresetFromJson(obj)
                            if (p != null) results.add(p)
                        }
                    }
                } else if (root.has("bands")) {
                    val p = deserializePresetFromJson(root)
                    if (p != null) results.add(p)
                }
            } else if (trimmed.startsWith("[")) {
                val arr = org.json.JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val p = deserializePresetFromJson(obj)
                    if (p != null) results.add(p)
                }
            }
        } catch (_: Exception) {}

        return results
    }

    /**
     * Imports presets from raw text (code or JSON) and saves them into SharedPreferences.
     * Returns Pair(allUpdatedUserPresets, newlyImportedPresets).
     */
    fun importUserPresets(
        prefs: SharedPreferences,
        rawText: String
    ): Pair<List<UserPeqPreset>, List<UserPeqPreset>> {
        val parsed = parsePresetsFromImportText(rawText)
        if (parsed.isEmpty()) return loadUserPresets(prefs) to emptyList()

        val existing = loadUserPresets(prefs).toMutableList()
        val importedList = mutableListOf<UserPeqPreset>()

        for (incoming in parsed) {
            val idx = existing.indexOfFirst {
                it.id == incoming.id || it.name.equals(incoming.name, ignoreCase = true)
            }
            if (idx >= 0) {
                val updated = incoming.copy(id = existing[idx].id)
                existing[idx] = updated
                importedList.add(updated)
            } else {
                existing.add(0, incoming)
                importedList.add(incoming)
            }
        }

        saveUserPresets(prefs, existing)
        return existing to importedList
    }

    fun applyToEngine(engine: AudioEngine, config: PeqConfiguration) {
        val freqs = FloatArray(config.bands.size) { config.bands[it].freqHz }
        val gains = FloatArray(config.bands.size) { if (config.bands[it].enabled) config.bands[it].gainDb else 0f }
        val qs = FloatArray(config.bands.size) { config.bands[it].q }
        val types = IntArray(config.bands.size) { config.bands[it].filterType.id }

        // Atomic update of enabled, preamp, and all 10 biquad coefficient banks
        engine.setPeqFullConfig(
            config.enabled,
            config.effectivePreampDb,
            freqs,
            gains,
            qs,
            types
        )
    }
}
