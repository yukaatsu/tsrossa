package com.yuka.musicplayer.audio

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

fun resolveConnectedDacProductName(audioEngine: AudioEngine, isDacConnected: Boolean): String {
    if (!isDacConnected) return ""
    return try {
        val info = JSONObject(audioEngine.getDacInfo())
        info.optString("productName", "").trim()
    } catch (_: Exception) {
        ""
    }
}

fun getDefaultVolumeForMode(isDac: Boolean, @Suppress("UNUSED_PARAMETER") isHwVol: Boolean = false): Float = when {
    isDac -> 0.50f // Safe comfortable reference for BOTH Hybrid Hardware UAC & 64-bit DSP
    else -> 0.05f  // Internal speaker: 5%
}

fun buildVolumePrefKey(dacName: String, isDac: Boolean): String {
    if (!isDac || dacName.isBlank()) return "vol_mode_internal_else"
    val cleanName = dacName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    return "vol_dac_$cleanName"
}

fun loadSavedVolumeForDac(sharedPref: SharedPreferences, dacName: String, isDac: Boolean): Float {
    val fallback = getDefaultVolumeForMode(isDac, false)
    if (!isDac || dacName.isBlank()) return fallback
    val unifiedKey = buildVolumePrefKey(dacName, true)
    if (sharedPref.contains(unifiedKey)) {
        return sharedPref.getFloat(unifiedKey, fallback).coerceIn(0f, 1f)
    }
    val cleanName = dacName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    val legacyDspKey = "vol_dac_${cleanName}_dsp64"
    if (sharedPref.contains(legacyDspKey)) {
        return sharedPref.getFloat(legacyDspKey, fallback).coerceIn(0f, 1f)
    }
    return fallback
}

@Stable
class DacVolumeState(
    initialVolume: Float,
    initialHwActive: Boolean,
    initialHwSupported: Boolean,
    initialHwEnabled: Boolean
) {
    var currentVolume by mutableFloatStateOf(initialVolume)
    var isHardwareVolumeActive by mutableStateOf(initialHwActive)
    var isHardwareVolumeSupported by mutableStateOf(initialHwSupported)
    var isHardwareVolumeEnabled by mutableStateOf(initialHwEnabled)
    var showPeqDspNoticeDialog by mutableStateOf(false)
    var peqDspNoticeMessage by mutableStateOf("")

    lateinit var applyVolumeModeAtomic: (Boolean) -> Unit
    lateinit var onToggleHardwareVolume: (Boolean) -> Unit
    lateinit var onVolumeChange: (Float) -> Unit
    lateinit var onDacReadyVolumeSync: (String) -> Unit
    lateinit var onDacDetachedVolumeReset: () -> Unit
    lateinit var notifyPeqDspModeSwitched: () -> Unit
    lateinit var dismissPeqDspNoticeDialog: () -> Unit
}

@Composable
fun rememberDacVolumeState(
    audioEngine: AudioEngine,
    sharedPref: SharedPreferences,
    isDacConnected: Boolean,
    isPeqEnabled: Boolean
): DacVolumeState {
    val coroutineScope = rememberCoroutineScope()

    val initialHwEnabled = remember { sharedPref.getBoolean("dac_hw_volume_enabled", false) }
    val initDacName = remember(isDacConnected) { resolveConnectedDacProductName(audioEngine, isDacConnected) }
    val initialVolume = remember(isDacConnected) { loadSavedVolumeForDac(sharedPref, initDacName, isDacConnected) }

    val state = remember {
        DacVolumeState(
            initialVolume = initialVolume,
            initialHwActive = false,
            initialHwSupported = false,
            initialHwEnabled = initialHwEnabled
        )
    }

    var activeVolumePrefKey by remember {
        mutableStateOf(buildVolumePrefKey(initDacName, isDacConnected))
    }

    var isProgrammaticVolumeSwitch by remember { mutableStateOf(false) }

    var modeSwitchJob by remember { mutableStateOf<Job?>(null) }

    val applyVolumeModeAtomic: (Boolean) -> Unit = remember(audioEngine, sharedPref, isDacConnected) {
        { enableHardwareUac ->
            state.isHardwareVolumeEnabled = enableHardwareUac
            sharedPref.edit().putBoolean("dac_hw_volume_enabled", enableHardwareUac).apply()

            val preservedVol = state.currentVolume.coerceIn(0f, 1f)
            val hwSupportedNow = state.isHardwareVolumeSupported || (isDacConnected && audioEngine.isHardwareVolumeSupported())
            state.isHardwareVolumeSupported = hwSupportedNow
            // Immediate optimistic state update so Now Playing & System Logs switch in 0ms
            state.isHardwareVolumeActive = enableHardwareUac && isDacConnected && hwSupportedNow

            modeSwitchJob?.cancel()
            modeSwitchJob = coroutineScope.launch(Dispatchers.IO) {
                audioEngine.setSoftwareVolume(preservedVol)
                audioEngine.setHardwareVolumeEnabled(enableHardwareUac)
                // Wait for C++ control_thread_func to finish EP0 SET_CUR + 15ms hardware PGA settle
                delay(45L)
                val activeAfterSettle = audioEngine.isHardwareVolumeActive()
                val supportedAfterSettle = audioEngine.isHardwareVolumeSupported()
                withContext(Dispatchers.Main) {
                    state.isHardwareVolumeSupported = supportedAfterSettle
                    state.isHardwareVolumeActive = if (enableHardwareUac && isDacConnected && supportedAfterSettle) {
                        activeAfterSettle || !audioEngine.isPlaying()
                    } else {
                        false
                    }
                }
                delay(70L)
                val finalActive = audioEngine.isHardwareVolumeActive()
                val finalSupported = audioEngine.isHardwareVolumeSupported()
                withContext(Dispatchers.Main) {
                    state.isHardwareVolumeSupported = finalSupported
                    state.isHardwareVolumeActive = if (enableHardwareUac && isDacConnected && finalSupported) {
                        finalActive || !audioEngine.isPlaying()
                    } else {
                        false
                    }
                }
            }
        }
    }
    state.applyVolumeModeAtomic = applyVolumeModeAtomic

    val onToggleHardwareVolume: (Boolean) -> Unit = remember(isPeqEnabled, applyVolumeModeAtomic) {
        { enabled ->
            if (enabled && isPeqEnabled) {
                state.peqDspNoticeMessage = "Hardware UAC mode cannot be enabled while Parametric Equalizer (PEQ) is active. Turn off PEQ first in the DSP menu to use Hardware UAC."
                state.showPeqDspNoticeDialog = true
            } else {
                applyVolumeModeAtomic(enabled)
            }
        }
    }
    state.onToggleHardwareVolume = onToggleHardwareVolume

    state.onVolumeChange = { vol -> state.currentVolume = vol }

    state.onDacReadyVolumeSync = { dacName ->
        val resolvedName = dacName.ifBlank { resolveConnectedDacProductName(audioEngine, true) }
        val newKey = buildVolumePrefKey(resolvedName, true)
        activeVolumePrefKey = newKey
        val savedVol = loadSavedVolumeForDac(sharedPref, resolvedName, true)
        isProgrammaticVolumeSwitch = true
        state.currentVolume = savedVol
        audioEngine.setSoftwareVolume(savedVol)
        audioEngine.setHardwareVolumeEnabled(state.isHardwareVolumeEnabled)
        state.isHardwareVolumeActive = audioEngine.isHardwareVolumeActive()
        state.isHardwareVolumeSupported = audioEngine.isHardwareVolumeSupported()
    }

    state.onDacDetachedVolumeReset = {
        state.isHardwareVolumeActive = false
        state.isHardwareVolumeSupported = false
    }

    state.notifyPeqDspModeSwitched = {
        if (state.isHardwareVolumeEnabled) {
            applyVolumeModeAtomic(false)
            state.peqDspNoticeMessage = "Audio mode automatically switched to Universal 64-Bit DSP because Parametric Equalizer (PEQ) was enabled. Hardware UAC mode is disabled to process digital audio filters bit-accurately."
            state.showPeqDspNoticeDialog = true
        }
    }

    state.dismissPeqDspNoticeDialog = {
        state.showPeqDspNoticeDialog = false
    }

    LaunchedEffect(isDacConnected) {
        val dacName = resolveConnectedDacProductName(audioEngine, isDacConnected)
        val newKey = buildVolumePrefKey(dacName, isDacConnected)
        if (activeVolumePrefKey != newKey) {
            activeVolumePrefKey = newKey
            val targetVol = loadSavedVolumeForDac(sharedPref, dacName, isDacConnected)
            isProgrammaticVolumeSwitch = true
            state.currentVolume = targetVol
            audioEngine.setSoftwareVolume(targetVol)
        }
        if (isDacConnected) {
            audioEngine.setHardwareVolumeEnabled(state.isHardwareVolumeEnabled)
            state.isHardwareVolumeActive = audioEngine.isHardwareVolumeActive()
            state.isHardwareVolumeSupported = audioEngine.isHardwareVolumeSupported()
        } else {
            state.isHardwareVolumeActive = false
            state.isHardwareVolumeSupported = false
        }
    }

    var volumeSaveJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(state.currentVolume) {
        audioEngine.setSoftwareVolume(state.currentVolume)
        if (isProgrammaticVolumeSwitch) {
            isProgrammaticVolumeSwitch = false
            return@LaunchedEffect
        }
        val keyToSave = activeVolumePrefKey
        val canPersistForDac = isDacConnected && keyToSave.startsWith("vol_dac_")
        volumeSaveJob?.cancel()
        volumeSaveJob = coroutineScope.launch {
            delay(350L)
            if (canPersistForDac) {
                sharedPref.edit().putFloat(keyToSave, state.currentVolume).apply()
            }
            sharedPref.edit().putFloat("last_volume", state.currentVolume).apply()
        }
    }

    return state
}
