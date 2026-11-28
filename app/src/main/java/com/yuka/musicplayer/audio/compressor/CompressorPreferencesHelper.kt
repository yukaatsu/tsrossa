package com.yuka.musicplayer.audio.compressor

import android.content.SharedPreferences
import com.yuka.musicplayer.audio.AudioEngine
import org.json.JSONObject

object CompressorPreferencesHelper {
    private const val PREF_COMP_ENABLED = "comp_enabled"
    private const val PREF_COMP_PRESET = "comp_preset"
    private const val PREF_COMP_DOWN_THRESH = "comp_down_threshold_db"
    private const val PREF_COMP_DOWN_RATIO = "comp_down_ratio"
    private const val PREF_COMP_UP_THRESH = "comp_up_threshold_db"
    private const val PREF_COMP_UP_RATIO = "comp_up_ratio"
    private const val PREF_COMP_MAX_UP_BOOST = "comp_max_upward_boost_db"
    private const val PREF_COMP_FLOOR_THRESH = "comp_floor_threshold_db"
    private const val PREF_COMP_KNEE_DB = "comp_knee_db"
    private const val PREF_COMP_ATTACK_MS = "comp_attack_ms"
    private const val PREF_COMP_RELEASE_MS = "comp_release_ms"
    private const val PREF_COMP_MAKEUP_DB = "comp_makeup_gain_db"
    private const val PREF_COMP_SC_HPF_HZ = "comp_sidechain_hpf_hz"
    private const val PREF_COMP_USE_RMS = "comp_use_rms_detector"
    private const val PREF_COMP_SAVED_CUSTOM_JSON = "comp_saved_custom_profile_json"

    fun load(prefs: SharedPreferences): CompressorConfig {
        val defaults = CompressorConfig()
        val loadedPreset = prefs.getString(PREF_COMP_PRESET, defaults.selectedPresetId) ?: defaults.selectedPresetId
        val validPresetId = if (loadedPreset == CompressorPresets.CUSTOM_ID || CompressorPresets.findById(loadedPreset) != null) {
            loadedPreset
        } else {
            defaults.selectedPresetId
        }

        return CompressorConfig(
            enabled = prefs.getBoolean(PREF_COMP_ENABLED, defaults.enabled),
            selectedPresetId = validPresetId,
            downThresholdDb = prefs.getFloat(PREF_COMP_DOWN_THRESH, defaults.downThresholdDb),
            downRatio = prefs.getFloat(PREF_COMP_DOWN_RATIO, defaults.downRatio),
            upThresholdDb = prefs.getFloat(PREF_COMP_UP_THRESH, defaults.upThresholdDb),
            upRatio = prefs.getFloat(PREF_COMP_UP_RATIO, defaults.upRatio),
            maxUpwardBoostDb = prefs.getFloat(PREF_COMP_MAX_UP_BOOST, defaults.maxUpwardBoostDb),
            floorThresholdDb = prefs.getFloat(PREF_COMP_FLOOR_THRESH, defaults.floorThresholdDb),
            kneeDb = prefs.getFloat(PREF_COMP_KNEE_DB, defaults.kneeDb),
            attackMs = prefs.getFloat(PREF_COMP_ATTACK_MS, defaults.attackMs),
            releaseMs = prefs.getFloat(PREF_COMP_RELEASE_MS, defaults.releaseMs),
            makeupGainDb = prefs.getFloat(PREF_COMP_MAKEUP_DB, defaults.makeupGainDb),
            sidechainHpfHz = prefs.getFloat(PREF_COMP_SC_HPF_HZ, defaults.sidechainHpfHz),
            useRmsDetector = prefs.getBoolean(PREF_COMP_USE_RMS, defaults.useRmsDetector)
        ).clamped()
    }

    fun save(prefs: SharedPreferences, config: CompressorConfig) {
        val c = config.clamped()
        prefs.edit()
            .putBoolean(PREF_COMP_ENABLED, c.enabled)
            .putString(PREF_COMP_PRESET, c.selectedPresetId)
            .putFloat(PREF_COMP_DOWN_THRESH, c.downThresholdDb)
            .putFloat(PREF_COMP_DOWN_RATIO, c.downRatio)
            .putFloat(PREF_COMP_UP_THRESH, c.upThresholdDb)
            .putFloat(PREF_COMP_UP_RATIO, c.upRatio)
            .putFloat(PREF_COMP_MAX_UP_BOOST, c.maxUpwardBoostDb)
            .putFloat(PREF_COMP_FLOOR_THRESH, c.floorThresholdDb)
            .putFloat(PREF_COMP_KNEE_DB, c.kneeDb)
            .putFloat(PREF_COMP_ATTACK_MS, c.attackMs)
            .putFloat(PREF_COMP_RELEASE_MS, c.releaseMs)
            .putFloat(PREF_COMP_MAKEUP_DB, c.makeupGainDb)
            .putFloat(PREF_COMP_SC_HPF_HZ, c.sidechainHpfHz)
            .putBoolean(PREF_COMP_USE_RMS, c.useRmsDetector)
            .apply()
    }

    fun applyToEngine(engine: AudioEngine, config: CompressorConfig) {
        val c = config.clamped()
        engine.setCompressorFullConfig(
            enabled = c.enabled,
            downThresholdDb = c.downThresholdDb,
            downRatio = c.downRatio,
            upThresholdDb = c.upThresholdDb,
            upRatio = c.upRatio,
            maxUpwardBoostDb = c.maxUpwardBoostDb,
            floorThresholdDb = c.floorThresholdDb,
            kneeDb = c.kneeDb,
            attackMs = c.attackMs,
            releaseMs = c.releaseMs,
            makeupGainDb = c.makeupGainDb,
            sidechainHpfHz = c.sidechainHpfHz,
            useRmsDetector = c.useRmsDetector
        )
    }

    fun loadSavedCustomProfile(prefs: SharedPreferences): SavedCustomCompressorProfile? {
        val raw = prefs.getString(PREF_COMP_SAVED_CUSTOM_JSON, null)
        if (raw.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(raw)
            val defaults = CompressorConfig()
            val cleanName = obj.optString("name", "Custom").trim().take(28).ifBlank { "Custom" }
            val updatedAt = obj.optLong("updatedAt", 0L)
            val clampedConfig = CompressorConfig(
                enabled = true,
                selectedPresetId = CompressorPresets.CUSTOM_ID,
                downThresholdDb = obj.optDouble("downThresholdDb", defaults.downThresholdDb.toDouble()).toFloat(),
                downRatio = obj.optDouble("downRatio", defaults.downRatio.toDouble()).toFloat(),
                upThresholdDb = obj.optDouble("upThresholdDb", defaults.upThresholdDb.toDouble()).toFloat(),
                upRatio = obj.optDouble("upRatio", defaults.upRatio.toDouble()).toFloat(),
                maxUpwardBoostDb = obj.optDouble("maxUpwardBoostDb", defaults.maxUpwardBoostDb.toDouble()).toFloat(),
                floorThresholdDb = obj.optDouble("floorThresholdDb", defaults.floorThresholdDb.toDouble()).toFloat(),
                kneeDb = obj.optDouble("kneeDb", defaults.kneeDb.toDouble()).toFloat(),
                attackMs = obj.optDouble("attackMs", defaults.attackMs.toDouble()).toFloat(),
                releaseMs = obj.optDouble("releaseMs", defaults.releaseMs.toDouble()).toFloat(),
                makeupGainDb = obj.optDouble("makeupGainDb", defaults.makeupGainDb.toDouble()).toFloat(),
                sidechainHpfHz = obj.optDouble("sidechainHpfHz", defaults.sidechainHpfHz.toDouble()).toFloat(),
                useRmsDetector = obj.optBoolean("useRmsDetector", defaults.useRmsDetector)
            ).clamped()
            SavedCustomCompressorProfile(
                name = cleanName,
                config = clampedConfig,
                updatedAt = updatedAt
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveCustomProfile(
        prefs: SharedPreferences,
        name: String,
        config: CompressorConfig,
        timestampMs: Long = System.currentTimeMillis()
    ): SavedCustomCompressorProfile {
        val cleanName = name.trim().take(28).ifBlank { "Custom" }
        val clamped = config.copy(
            enabled = true,
            selectedPresetId = CompressorPresets.CUSTOM_ID
        ).clamped()

        val obj = JSONObject().apply {
            put("name", cleanName)
            put("updatedAt", timestampMs)
            put("downThresholdDb", clamped.downThresholdDb.toDouble())
            put("downRatio", clamped.downRatio.toDouble())
            put("upThresholdDb", clamped.upThresholdDb.toDouble())
            put("upRatio", clamped.upRatio.toDouble())
            put("maxUpwardBoostDb", clamped.maxUpwardBoostDb.toDouble())
            put("floorThresholdDb", clamped.floorThresholdDb.toDouble())
            put("kneeDb", clamped.kneeDb.toDouble())
            put("attackMs", clamped.attackMs.toDouble())
            put("releaseMs", clamped.releaseMs.toDouble())
            put("makeupGainDb", clamped.makeupGainDb.toDouble())
            put("sidechainHpfHz", clamped.sidechainHpfHz.toDouble())
            put("useRmsDetector", clamped.useRmsDetector)
        }

        prefs.edit().putString(PREF_COMP_SAVED_CUSTOM_JSON, obj.toString()).apply()
        return SavedCustomCompressorProfile(
            name = cleanName,
            config = clamped,
            updatedAt = timestampMs
        )
    }

    fun clearSavedCustomProfile(prefs: SharedPreferences) {
        prefs.edit().remove(PREF_COMP_SAVED_CUSTOM_JSON).apply()
    }
}
