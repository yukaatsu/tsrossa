package com.yuka.musicplayer.audio

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

object PeqPresetCodec {

    const val SHARE_CODE_PREFIX = "TSROSSA-PEQ1:"

    fun serializeBandsToJson(bands: List<PeqBand>): JSONArray {
        val arr = JSONArray()
        bands.forEach { b ->
            val obj = JSONObject().apply {
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

    fun deserializeBandsFromJson(arr: JSONArray?): List<PeqBand> {
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

    fun serializePresetToJson(preset: UserPeqPreset): JSONObject {
        return JSONObject().apply {
            put("id", preset.id)
            put("name", preset.name)
            put("preampDb", preset.preampDb.toDouble())
            put("autoPreampEnabled", preset.autoPreampEnabled)
            put("createdAt", preset.createdAt)
            put("bands", serializeBandsToJson(preset.bands))
        }
    }

    fun deserializePresetFromJson(obj: JSONObject): UserPeqPreset? {
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

    fun exportPresetToShareCode(preset: UserPeqPreset, includeHeader: Boolean = true): String {
        val obj = serializePresetToJson(preset)
        val jsonBytes = obj.toString().toByteArray(Charsets.UTF_8)
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(jsonBytes)
        val code = "$SHARE_CODE_PREFIX$b64"
        return if (includeHeader) {
            "🎛️ TSROSSA PEQ Preset: \"${preset.name}\"\n$code"
        } else {
            code
        }
    }

    fun exportPresetsToJsonString(presets: List<UserPeqPreset>, prettyPrint: Boolean = true): String {
        val root = JSONObject().apply {
            put("app", "TSROSSA")
            put("formatVersion", 1)
            put("exportedAt", System.currentTimeMillis())
            val arr = JSONArray()
            presets.forEach { arr.put(serializePresetToJson(it)) }
            put("presets", arr)
        }
        return if (prettyPrint) root.toString(2) else root.toString()
    }

    fun parsePresetsFromImportText(rawText: String): List<UserPeqPreset> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val results = mutableListOf<UserPeqPreset>()

        val codeRegex = Regex("${Regex.escape(SHARE_CODE_PREFIX)}([A-Za-z0-9+/=_-]+)")
        val matches = codeRegex.findAll(trimmed)
        for (m in matches) {
            val payload = m.groupValues[1]
            try {
                val decodedBytes = try {
                    Base64.getUrlDecoder().decode(payload)
                } catch (_: Exception) {
                    Base64.getDecoder().decode(payload)
                }
                val jsonStr = String(decodedBytes, Charsets.UTF_8)
                val obj = JSONObject(jsonStr)
                val preset = deserializePresetFromJson(obj)
                if (preset != null) results.add(preset)
            } catch (_: Exception) {}
        }
        if (results.isNotEmpty()) return results

        try {
            if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
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
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val p = deserializePresetFromJson(obj)
                    if (p != null) results.add(p)
                }
            }
        } catch (_: Exception) {}

        return results
    }
}
