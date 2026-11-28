package com.yuka.musicplayer.audio

import android.content.SharedPreferences
import com.yuka.musicplayer.audio.compressor.CompressorConfig
import com.yuka.musicplayer.audio.compressor.CompressorEvaluator
import com.yuka.musicplayer.audio.compressor.CompressorPreferencesHelper
import com.yuka.musicplayer.audio.compressor.CompressorPresets
import com.yuka.musicplayer.audio.compressor.CompressorUiHelper
import com.yuka.musicplayer.ui.app.shouldPromptDspModeSwitch
import com.yuka.musicplayer.ui.diagnostics.resolveDspPipelineStatusLabel
import com.yuka.musicplayer.ui.diagnostics.resolveEffectiveOutputBitDepth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 18 Verification Gate:
 * Validates Dynamics Compressor presets, default-OFF safety + SharedPreferences serialization round-trip,
 * Hardware UAC <-> Universal 64-Bit DSP mutual exclusion UX guard, and telemetry/bit-depth elevation.
 */
class CompressorModelsTest {

    @Test
    fun `built-in presets are valid monotonic and invariant under clamping`() {
        assertEquals(3, CompressorPresets.ALL.size)
        val ids = CompressorPresets.ALL.map { it.id }
        assertTrue(ids.containsAll(listOf("night_mode", "low_volume_detail", "balanced")))

        for (preset in CompressorPresets.ALL) {
            val c = preset.config
            assertTrue("${preset.id}: floor < upThreshold", c.floorThresholdDb < c.upThresholdDb)
            assertTrue("${preset.id}: upThreshold < downThreshold", c.upThresholdDb < c.downThresholdDb)
            assertTrue("${preset.id}: downRatio > 1", c.downRatio > 1.0f)
            assertTrue("${preset.id}: upRatio >= 1", c.upRatio >= 1.0f)
            assertTrue("${preset.id}: attack < release", c.attackMs < c.releaseMs)
            assertEquals("${preset.id}: preset must already be within clamping bounds", c, c.clamped())
            assertTrue("${preset.id}: must report active processing", CompressorEvaluator.hasActiveProcessing(c))
        }
    }

    @Test
    fun `default config is disabled and SharedPreferences round-trip preserves all fields`() {
        val prefs = InMemorySharedPreferences()

        // Default load MUST be OFF (enabled = false) so fresh installs preserve Bit-Perfect path
        val freshLoad = CompressorPreferencesHelper.load(prefs)
        assertFalse(freshLoad.enabled)
        assertEquals("balanced", freshLoad.selectedPresetId)

        val custom = CompressorConfig(
            enabled = true,
            selectedPresetId = "night_mode",
            downThresholdDb = -21.5f,
            downRatio = 4.25f,
            upThresholdDb = -35.0f,
            upRatio = 2.6f,
            maxUpwardBoostDb = 7.5f,
            floorThresholdDb = -63.0f,
            kneeDb = 4.5f,
            attackMs = 7.5f,
            releaseMs = 280.0f,
            makeupGainDb = 1.5f,
            sidechainHpfHz = 110.0f,
            useRmsDetector = false
        )
        CompressorPreferencesHelper.save(prefs, custom)
        val restored = CompressorPreferencesHelper.load(prefs)
        assertEquals(custom, restored)
    }

    @Test
    fun `UX guard mutual-exclusion matrix and DSP pipeline telemetry status labels`() {
        // Enabling OFF -> ON while Hybrid Hardware UAC is selected MUST prompt
        assertTrue(
            shouldPromptDspModeSwitch(
                wasModuleEnabled = false,
                willEnableModule = true,
                isHardwareVolumeEnabled = true
            )
        )
        // Already ON -> slider drag MUST NOT prompt repeatedly
        assertFalse(
            shouldPromptDspModeSwitch(
                wasModuleEnabled = true,
                willEnableModule = true,
                isHardwareVolumeEnabled = true
            )
        )
        // Turning OFF -> MUST NOT prompt
        assertFalse(
            shouldPromptDspModeSwitch(
                wasModuleEnabled = true,
                willEnableModule = false,
                isHardwareVolumeEnabled = true
            )
        )
        // Already in Universal 64-Bit DSP (isHardwareVolumeEnabled = false) -> NO prompt
        assertFalse(
            shouldPromptDspModeSwitch(
                wasModuleEnabled = false,
                willEnableModule = true,
                isHardwareVolumeEnabled = false
            )
        )

        assertEquals(
            "ACTIVE (64-Bit Dynamics Compressor)",
            resolveDspPipelineStatusLabel(
                isBitPerfectDirectPass = false,
                isPeqEnabled = false,
                isCompressorEnabled = true,
                isHardwareVolumeActive = false
            )
        )
        assertEquals(
            "ACTIVE (64-Bit PEQ + Dynamics)",
            resolveDspPipelineStatusLabel(
                isBitPerfectDirectPass = false,
                isPeqEnabled = true,
                isCompressorEnabled = true,
                isHardwareVolumeActive = false
            )
        )
    }

    @Test
    fun `telemetry elevates container bit depth to 32-bit when Dynamics Compressor is active`() {
        val bitDepthNativeBypass = resolveEffectiveOutputBitDepth(
            sourceBitDepth = 24,
            rawNegotiatedBitDepth = 24,
            supportedBitDepths = "16, 24, 32",
            isHardwareVolumeActive = true,
            isPeqEnabled = false,
            isCompressorEnabled = false,
            isPlaying = false
        )
        assertEquals(24, bitDepthNativeBypass)

        val bitDepthWithCompressor = resolveEffectiveOutputBitDepth(
            sourceBitDepth = 24,
            rawNegotiatedBitDepth = 24,
            supportedBitDepths = "16, 24, 32",
            isHardwareVolumeActive = true,
            isPeqEnabled = false,
            isCompressorEnabled = true,
            isPlaying = false
        )
        assertEquals(32, bitDepthWithCompressor)
    }

    @Test
    fun `saved custom profile round-trip persists independently from active preset switches`() {
        val prefs = InMemorySharedPreferences()
        val myConfig = CompressorConfig(
            enabled = true,
            selectedPresetId = CompressorPresets.CUSTOM_ID,
            downThresholdDb = -18.5f,
            downRatio = 3.4f,
            upThresholdDb = -39.0f,
            upRatio = 2.4f,
            maxUpwardBoostDb = 6.5f,
            floorThresholdDb = -67.0f,
            kneeDb = 5.0f,
            attackMs = 11.0f,
            releaseMs = 290.0f,
            makeupGainDb = 1.25f,
            sidechainHpfHz = 85.0f,
            useRmsDetector = false
        )

        val saved = CompressorPreferencesHelper.saveCustomProfile(
            prefs = prefs,
            name = "My IEM Night",
            config = myConfig,
            timestampMs = 1732500000000L
        )
        assertEquals("My IEM Night", saved.name)
        assertEquals(1732500000000L, saved.updatedAt)

        // Simulate A/B comparison switch to built-in Night Mode
        CompressorPreferencesHelper.save(prefs, CompressorPresets.NIGHT_MODE.config)
        val loadedActive = CompressorPreferencesHelper.load(prefs)
        assertEquals(CompressorPresets.NIGHT_MODE.id, loadedActive.selectedPresetId)

        // Saved custom slot MUST stay completely untouched & intact across active switches
        val loadedCustom = CompressorPreferencesHelper.loadSavedCustomProfile(prefs)
        requireNotNull(loadedCustom)
        assertEquals("My IEM Night", loadedCustom.name)
        assertTrue(loadedCustom.config.matchesAcoustics(myConfig))
        assertEquals(CompressorPresets.CUSTOM_ID, loadedCustom.config.selectedPresetId)
    }

    @Test
    fun `saveCustomProfile clamps out-of-range values and sanitizes blank or oversized name`() {
        val prefs = InMemorySharedPreferences()
        val extremeConfig = CompressorConfig(
            enabled = false,
            selectedPresetId = "balanced",
            downThresholdDb = 15.0f,
            downRatio = 99.0f,
            upThresholdDb = -120.0f,
            upRatio = 0.2f,
            maxUpwardBoostDb = 40.0f,
            floorThresholdDb = -150.0f,
            kneeDb = 30.0f,
            attackMs = 0.01f,
            releaseMs = 5000.0f,
            makeupGainDb = 50.0f,
            sidechainHpfHz = 999.0f,
            useRmsDetector = true
        )

        val blankNameSaved = CompressorPreferencesHelper.saveCustomProfile(prefs, "   ", extremeConfig)
        assertEquals("Custom", blankNameSaved.name)
        assertTrue(blankNameSaved.config.enabled)
        assertEquals(CompressorPresets.CUSTOM_ID, blankNameSaved.config.selectedPresetId)
        assertEquals(extremeConfig.clamped().downRatio, blankNameSaved.config.downRatio, 1e-5f)
        assertEquals(extremeConfig.clamped().attackMs, blankNameSaved.config.attackMs, 1e-5f)
        assertEquals(extremeConfig.clamped().makeupGainDb, blankNameSaved.config.makeupGainDb, 1e-5f)

        val longName = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890EXTRALONG"
        val longNameSaved = CompressorPreferencesHelper.saveCustomProfile(prefs, longName, extremeConfig)
        assertEquals(28, longNameSaved.name.length)
        assertEquals(longName.take(28), longNameSaved.name)
    }

    @Test
    fun `clearSavedCustomProfile deletes only saved slot without touching active config`() {
        val prefs = InMemorySharedPreferences()
        val activeCustom = CompressorConfig(
            enabled = true,
            selectedPresetId = CompressorPresets.CUSTOM_ID,
            downThresholdDb = -19.0f,
            downRatio = 3.2f
        )
        CompressorPreferencesHelper.save(prefs, activeCustom)
        CompressorPreferencesHelper.saveCustomProfile(prefs, "Personal Ref", activeCustom)

        assertEquals("Personal Ref", CompressorPreferencesHelper.loadSavedCustomProfile(prefs)?.name)

        CompressorPreferencesHelper.clearSavedCustomProfile(prefs)
        assertEquals(null, CompressorPreferencesHelper.loadSavedCustomProfile(prefs))

        // Active config remains intact
        val activeRestored = CompressorPreferencesHelper.load(prefs)
        assertTrue(activeRestored.enabled)
        assertEquals(CompressorPresets.CUSTOM_ID, activeRestored.selectedPresetId)
        assertEquals(-19.0f, activeRestored.downThresholdDb, 1e-5f)
    }

    @Test
    fun `loadSavedCustomProfile handles missing and corrupted JSON safely`() {
        val prefs = InMemorySharedPreferences()
        assertEquals(null, CompressorPreferencesHelper.loadSavedCustomProfile(prefs))

        prefs.edit().putString("comp_saved_custom_profile_json", "{not-valid-json").apply()
        assertEquals(null, CompressorPreferencesHelper.loadSavedCustomProfile(prefs))
    }

    @Test
    fun `matchesAcoustics detects parameter edits while ignoring enabled and preset id`() {
        val a = CompressorConfig(
            enabled = true,
            selectedPresetId = CompressorPresets.CUSTOM_ID,
            downThresholdDb = -16.0f,
            attackMs = 15.0f,
            useRmsDetector = true
        )
        val bToggledBypassAndPreset = a.copy(
            enabled = false,
            selectedPresetId = "balanced"
        )
        assertTrue(a.matchesAcoustics(bToggledBypassAndPreset))

        val cEditedAttack = a.copy(attackMs = 15.5f)
        assertFalse(a.matchesAcoustics(cEditedAttack))

        val dEditedDetector = a.copy(useRmsDetector = false)
        assertFalse(a.matchesAcoustics(dEditedDetector))
    }

    @Test
    fun `sidechain HPF toggle OFF sets 0 Hz and toggle ON restores last active frequency`() {
        val initial = CompressorConfig(enabled = true, sidechainHpfHz = 135.0f, selectedPresetId = "balanced")
        val turnedOff = CompressorUiHelper.toggleSidechainHpf(initial, enable = false, lastActiveHpfHz = 135.0f)
        assertEquals(0.0f, turnedOff.sidechainHpfHz, 1e-5f)
        assertFalse(CompressorUiHelper.isSidechainHpfEnabled(turnedOff.sidechainHpfHz))

        val turnedOnAgain = CompressorUiHelper.toggleSidechainHpf(turnedOff, enable = true, lastActiveHpfHz = 135.0f)
        assertEquals(135.0f, turnedOnAgain.sidechainHpfHz, 1e-5f)
        assertTrue(CompressorUiHelper.isSidechainHpfEnabled(turnedOnAgain.sidechainHpfHz))
    }

    @Test
    fun `sidechain HPF toggle ON falls back to 90 Hz when last frequency is out of valid range`() {
        val off = CompressorConfig(enabled = true, sidechainHpfHz = 0.0f)
        assertEquals(90.0f, CompressorUiHelper.toggleSidechainHpf(off, enable = true, lastActiveHpfHz = 0.0f).sidechainHpfHz, 1e-5f)
        assertEquals(90.0f, CompressorUiHelper.toggleSidechainHpf(off, enable = true, lastActiveHpfHz = 10.0f).sidechainHpfHz, 1e-5f)
        assertEquals(90.0f, CompressorUiHelper.toggleSidechainHpf(off, enable = true, lastActiveHpfHz = 500.0f).sidechainHpfHz, 1e-5f)
    }

    @Test
    fun `sidechain HPF toggle marks config as custom preset while preserving other acoustics`() {
        val base = CompressorPresets.NIGHT_MODE.config
        val toggledOff = CompressorUiHelper.toggleSidechainHpf(base, enable = false, lastActiveHpfHz = 90.0f)
        assertEquals(CompressorPresets.CUSTOM_ID, toggledOff.selectedPresetId)
        assertEquals(base.downThresholdDb, toggledOff.downThresholdDb, 1e-5f)
        assertEquals(base.upThresholdDb, toggledOff.upThresholdDb, 1e-5f)
        assertEquals(base.floorThresholdDb, toggledOff.floorThresholdDb, 1e-5f)
        assertEquals(base.attackMs, toggledOff.attackMs, 1e-5f)
        assertEquals(base.releaseMs, toggledOff.releaseMs, 1e-5f)
        assertEquals(base.useRmsDetector, toggledOff.useRmsDetector)
    }

    @Test
    fun `effectiveUpThresholdMax always stays strictly above -54 dBFS across full downThreshold range`() {
        for (down in -45..5 step 1) {
            val upMax = CompressorUiHelper.effectiveUpThresholdMax(down.toFloat())
            assertTrue("upMax ($upMax) must exceed MIN_UP (-54f) at down=$down", upMax > CompressorUiHelper.MIN_UP_THRESHOLD_DB)
            assertTrue("upMax ($upMax) must stay <= MAX_UP (-6f) at down=$down", upMax <= CompressorUiHelper.MAX_UP_THRESHOLD_DB)
        }
        assertEquals(-25.0f, CompressorUiHelper.effectiveUpThresholdMax(-25.0f), 1e-5f)
        assertEquals(-6.0f, CompressorUiHelper.effectiveUpThresholdMax(-2.0f), 1e-5f)
    }

    @Test
    fun `effectiveFloorThresholdMax always stays strictly above -80 dBFS with 6 dB upward margin`() {
        for (up in -60..0 step 1) {
            val floorMax = CompressorUiHelper.effectiveFloorThresholdMax(up.toFloat())
            assertTrue("floorMax ($floorMax) must exceed MIN_FLOOR (-80f) at up=$up", floorMax > CompressorUiHelper.MIN_FLOOR_THRESHOLD_DB)
            assertTrue("floorMax ($floorMax) must stay <= MAX_FLOOR (-30f) at up=$up", floorMax <= CompressorUiHelper.MAX_FLOOR_THRESHOLD_DB)
        }
        assertEquals(-42.0f, CompressorUiHelper.effectiveFloorThresholdMax(-36.0f), 1e-5f)
        assertEquals(-30.0f, CompressorUiHelper.effectiveFloorThresholdMax(-10.0f), 1e-5f)
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private val removed = hashSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) removed.add(key)
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                if (clearAll) data.clear()
                removed.forEach { data.remove(it) }
                data.putAll(pending)
                return true
            }
            override fun apply() { commit() }
        }
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }
}
