package com.yuka.musicplayer.ui.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yuka.musicplayer.audio.AudioEngine
import com.yuka.musicplayer.model.RefusedTrackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Encapsulates live USB DAC hardware telemetry state & continuous polling loop.
 */
@Immutable
data class DacTelemetryState(
    val uacVersion: Int,
    val claimedInterfaces: String,
    val negotiatedSampleRate: Int,
    val negotiatedBitDepth: Int,
    val sourceSampleRate: Int,
    val sourceBitDepth: Int,
    val outputBitDepth: Int,
    val outputSampleRate: Int,
    val recentErrorCount: Int,
    val isSampleRateUnverified: Boolean,
    val isDeviceWedged: Boolean,
    val isHardwareVolumeLockedBySystem: Boolean,
    val isForceSoftwareVolume: Boolean,
    val supportedBitDepths: String,
    val supportedSampleRates: String,
    val refusedTrackHistory: List<RefusedTrackEntry>
)

@Composable
fun rememberDacTelemetry(
    audioEngine: AudioEngine,
    isDacConnected: Boolean,
    isDiagnosticsOpen: Boolean = false,
    currentTrack: com.yuka.musicplayer.model.TrackInfo? = null,
    isPlaying: Boolean = false,
    isHardwareVolumeEnabled: Boolean = false,
    isHardwareVolumeActive: Boolean = false,
    isPeqEnabled: Boolean = false
): DacTelemetryState {
    var uacVersion by remember { mutableIntStateOf(0) }
    var claimedInterfaces by remember { mutableStateOf("") }
    var negotiatedSampleRate by remember { mutableIntStateOf(0) }
    var negotiatedBitDepth by remember { mutableIntStateOf(0) }
    var sourceSampleRate by remember { mutableIntStateOf(0) }
    var sourceBitDepth by remember { mutableIntStateOf(0) }
    var recentErrorCount by remember { mutableIntStateOf(0) }
    var outputBitDepth by remember { mutableIntStateOf(0) }
    var outputSampleRate by remember { mutableIntStateOf(0) }
    var isHardwareVolumeLockedBySystem by remember { mutableStateOf(false) }
    var isForceSoftwareVolume by remember { mutableStateOf(false) }
    var isDeviceWedged by remember { mutableStateOf(false) }
    var isSampleRateUnverified by remember { mutableStateOf(false) }
    var supportedBitDepths by remember { mutableStateOf("") }
    var supportedSampleRates by remember { mutableStateOf("") }
    var refusedTrackHistory by remember { mutableStateOf<List<RefusedTrackEntry>>(emptyList()) }

    LaunchedEffect(isDacConnected) {
        if (isDacConnected) {
            withContext(Dispatchers.IO) {
                uacVersion = audioEngine.getUacVersion()
                claimedInterfaces = audioEngine.getClaimedInterfaces()
                isHardwareVolumeLockedBySystem = audioEngine.isHardwareVolumeLockedBySystem()
                isForceSoftwareVolume = audioEngine.isForceSoftwareVolume()
                supportedBitDepths = audioEngine.getSupportedBitDepths()
                supportedSampleRates = audioEngine.getSupportedSampleRates()
                val rawNegBit = audioEngine.getNegotiatedBitDepth()
                val rawRate = audioEngine.getSampleRate()
                val rawSrcBit = audioEngine.getSourceBitDepth().takeIf { it > 0 } ?: (currentTrack?.bitDepth ?: 0)
                val effOutBit = resolveEffectiveOutputBitDepth(
                    sourceBitDepth = rawSrcBit,
                    rawNegotiatedBitDepth = rawNegBit,
                    supportedBitDepths = supportedBitDepths,
                    isHardwareVolumeActive = isHardwareVolumeActive,
                    isPeqEnabled = isPeqEnabled
                )
                sourceBitDepth = rawSrcBit
                sourceSampleRate = rawRate.takeIf { it > 0 } ?: (currentTrack?.sampleRateHz ?: 0)
                negotiatedBitDepth = effOutBit
                negotiatedSampleRate = rawRate.takeIf { it > 0 } ?: (currentTrack?.sampleRateHz ?: 0)
                outputBitDepth = effOutBit
                outputSampleRate = negotiatedSampleRate
            }
        } else {
            uacVersion = 0
            claimedInterfaces = ""
            negotiatedSampleRate = 0
            negotiatedBitDepth = 0
            sourceSampleRate = 0
            sourceBitDepth = 0
            outputBitDepth = 0
            outputSampleRate = 0
            supportedBitDepths = ""
            supportedSampleRates = ""
            refusedTrackHistory = emptyList()
            recentErrorCount = 0
        }
    }

    // Immediate reactive telemetry sync whenever track, playback, volume mode, or PEQ changes
    // (works even when System Logs & Diagnostics modal is closed!)
    LaunchedEffect(
        isDacConnected,
        currentTrack?.file?.absolutePath,
        currentTrack?.bitDepth,
        currentTrack?.sampleRateHz,
        isPlaying,
        isHardwareVolumeEnabled,
        isHardwareVolumeActive,
        isPeqEnabled,
        supportedBitDepths
    ) {
        if (isDacConnected) {
            withContext(Dispatchers.IO) {
                val rawRate = audioEngine.getSampleRate()
                val rawNegBit = audioEngine.getNegotiatedBitDepth()
                val rawSrcBit = audioEngine.getSourceBitDepth()
                val effectiveSrcBit = if (rawSrcBit > 0) rawSrcBit else (currentTrack?.bitDepth ?: 0)
                val effectiveRate = if (rawRate > 0) rawRate else (currentTrack?.sampleRateHz ?: 0)
                val effectiveOutBit = resolveEffectiveOutputBitDepth(
                    sourceBitDepth = effectiveSrcBit,
                    rawNegotiatedBitDepth = rawNegBit,
                    supportedBitDepths = supportedBitDepths,
                    isHardwareVolumeActive = isHardwareVolumeActive,
                    isPeqEnabled = isPeqEnabled
                )
                val hwLocked = audioEngine.isHardwareVolumeLockedBySystem()
                val forceSw = audioEngine.isForceSoftwareVolume()

                sourceBitDepth = effectiveSrcBit
                sourceSampleRate = effectiveRate
                negotiatedBitDepth = effectiveOutBit
                negotiatedSampleRate = effectiveRate
                outputBitDepth = effectiveOutBit
                outputSampleRate = effectiveRate
                isHardwareVolumeLockedBySystem = hwLocked
                isForceSoftwareVolume = forceSw
            }
        }
    }

    LaunchedEffect(isDacConnected, isDiagnosticsOpen, isHardwareVolumeActive, isPeqEnabled) {
        if (isDacConnected && isDiagnosticsOpen) {
            // Defer telemetry loop by 220ms so CyberBottomSheet entrance spring animation completes smoothly at 60/120 FPS
            delay(220L)
            while (isDacConnected && isDiagnosticsOpen) {
                withContext(Dispatchers.IO) {
                    val nSampleRate = audioEngine.getSampleRate().takeIf { it > 0 } ?: (currentTrack?.sampleRateHz ?: 0)
                    val nBitDepth = audioEngine.getNegotiatedBitDepth()
                    val sBitDepth = audioEngine.getSourceBitDepth().takeIf { it > 0 } ?: (currentTrack?.bitDepth ?: 0)
                    val effOutBit = resolveEffectiveOutputBitDepth(
                        sourceBitDepth = sBitDepth,
                        rawNegotiatedBitDepth = nBitDepth,
                        supportedBitDepths = supportedBitDepths,
                        isHardwareVolumeActive = isHardwareVolumeActive,
                        isPeqEnabled = isPeqEnabled
                    )
                    val errCount = audioEngine.getRecentErrorCount()
                    val unverified = audioEngine.isSampleRateUnverified()
                    val wedged = audioEngine.isDeviceWedged()
                    val historyList = try {
                        val historyStr = audioEngine.getRefusedTrackHistory()
                        if (historyStr.isNotEmpty() && historyStr != "[]") {
                            val array = JSONArray(historyStr)
                            val list = mutableListOf<RefusedTrackEntry>()
                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)
                                list.add(
                                    RefusedTrackEntry(
                                        obj.getString("file"),
                                        obj.getInt("bits"),
                                        obj.getInt("rate"),
                                        obj.getString("reason")
                                    )
                                )
                            }
                            list
                        } else {
                            emptyList()
                        }
                    } catch (_: Exception) {
                        emptyList<RefusedTrackEntry>()
                    }

                    negotiatedSampleRate = nSampleRate
                    negotiatedBitDepth = effOutBit
                    sourceSampleRate = nSampleRate
                    sourceBitDepth = sBitDepth
                    outputBitDepth = effOutBit
                    outputSampleRate = nSampleRate
                    recentErrorCount = errCount
                    isSampleRateUnverified = unverified
                    isDeviceWedged = wedged
                    refusedTrackHistory = historyList
                }
                delay(1000L)
            }
        }
    }

    return remember(
        uacVersion,
        claimedInterfaces,
        negotiatedSampleRate,
        negotiatedBitDepth,
        sourceSampleRate,
        sourceBitDepth,
        outputBitDepth,
        outputSampleRate,
        recentErrorCount,
        isSampleRateUnverified,
        isDeviceWedged,
        isHardwareVolumeLockedBySystem,
        isForceSoftwareVolume,
        supportedBitDepths,
        supportedSampleRates,
        refusedTrackHistory
    ) {
        DacTelemetryState(
            uacVersion = uacVersion,
            claimedInterfaces = claimedInterfaces,
            negotiatedSampleRate = negotiatedSampleRate,
            negotiatedBitDepth = negotiatedBitDepth,
            sourceSampleRate = sourceSampleRate,
            sourceBitDepth = sourceBitDepth,
            outputBitDepth = outputBitDepth,
            outputSampleRate = outputSampleRate,
            recentErrorCount = recentErrorCount,
            isSampleRateUnverified = isSampleRateUnverified,
            isDeviceWedged = isDeviceWedged,
            isHardwareVolumeLockedBySystem = isHardwareVolumeLockedBySystem,
            isForceSoftwareVolume = isForceSoftwareVolume,
            supportedBitDepths = supportedBitDepths,
            supportedSampleRates = supportedSampleRates,
            refusedTrackHistory = refusedTrackHistory
        )
    }
}
