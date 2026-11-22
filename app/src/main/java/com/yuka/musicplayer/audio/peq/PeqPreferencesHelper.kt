package com.yuka.musicplayer.audio

import android.content.SharedPreferences
import org.json.JSONArray
import kotlin.math.abs

object PeqPreferencesHelper {
    private const val PREF_PEQ_ENABLED = "peq_enabled"
    private const val PREF_PEQ_PREAMP_DB = "peq_preamp_db"
    private const val PREF_PEQ_AUTO_PREAMP = "peq_auto_preamp"
    private const val PREF_PEQ_PRESET = "peq_preset"
    private const val PREF_PEQ_USER_PRESETS_JSON = "peq_user_presets_json"
    private const val PREF_PEQ_LAST_CUSTOM_SNAPSHOT_JSON = "peq_last_custom_snapshot_json"
    private const val PREF_PEQ_Q_MIGRATED_V3 = "peq_q_migrated_v3"

    const val SHARE_CODE_PREFIX = PeqPresetCodec.SHARE_CODE_PREFIX

    fun load(prefs: SharedPreferences): PeqConfiguration {
        val enabled = prefs.getBoolean(PREF_PEQ_ENABLED, false)
        val preampDb = prefs.getFloat(PREF_PEQ_PREAMP_DB, 0.0f)
        val autoPreamp = prefs.getBoolean(PREF_PEQ_AUTO_PREAMP, true)
        val loadedPresetId = prefs.getString(PREF_PEQ_PRESET, "flat") ?: "flat"
        val needQMigration = !prefs.getBoolean(PREF_PEQ_Q_MIGRATED_V3, false)

        val defaultBands = PeqConfiguration.createDefaultBands()
        val loadedBands = defaultBands.map { band ->
            val pfx = "peq_band_${band.index}_"
            val freq = prefs.getFloat("${pfx}freq", band.freqHz)
            val gain = prefs.getFloat("${pfx}gain", band.gainDb)
            val savedQ = prefs.getFloat("${pfx}q", band.q)
            val typeId = prefs.getInt("${pfx}type", band.filterType.id)
            val bandEnabled = prefs.getBoolean("${pfx}enabled", band.enabled)

            val finalQ = if (needQMigration && band.index in 1..4 &&
                (abs(savedQ - 0.75f) < 0.02f || abs(savedQ - 0.85f) < 0.02f || abs(savedQ - 1.05f) < 0.02f || abs(savedQ - 1.41f) < 0.02f)
            ) {
                band.q // Upgrade to Multi-Zone Q (0.95 for bass, 1.25 for mid)
            } else {
                savedQ
            }

            band.copy(
                freqHz = freq,
                gainDb = gain,
                q = finalQ,
                filterType = PeqFilterType.fromId(typeId),
                enabled = bandEnabled
            )
        }

        if (needQMigration) {
            val editor = prefs.edit().putBoolean(PREF_PEQ_Q_MIGRATED_V3, true)
            loadedBands.forEach { b ->
                editor.putFloat("peq_band_${b.index}_q", b.q)
            }
            editor.apply()
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

    fun serializeBandsToJson(bands: List<PeqBand>): JSONArray =
        PeqPresetCodec.serializeBandsToJson(bands)

    fun deserializeBandsFromJson(arr: JSONArray?): List<PeqBand> =
        PeqPresetCodec.deserializeBandsFromJson(arr)

    fun loadUserPresets(prefs: SharedPreferences): List<UserPeqPreset> {
        val raw = prefs.getString(PREF_PEQ_USER_PRESETS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
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
        val arr = JSONArray()
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
            ).also { existing.add(0, it) }
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

    fun exportPresetToShareCode(preset: UserPeqPreset, includeHeader: Boolean = true): String =
        PeqPresetCodec.exportPresetToShareCode(preset, includeHeader)

    fun exportPresetsToJsonString(presets: List<UserPeqPreset>, prettyPrint: Boolean = true): String =
        PeqPresetCodec.exportPresetsToJsonString(presets, prettyPrint)

    fun parsePresetsFromImportText(rawText: String): List<UserPeqPreset> =
        PeqPresetCodec.parsePresetsFromImportText(rawText)

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
