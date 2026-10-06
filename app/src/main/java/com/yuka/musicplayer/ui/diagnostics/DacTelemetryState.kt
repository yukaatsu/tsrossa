package com.yuka.musicplayer.ui.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yuka.musicplayer.audio.AudioEngine
import com.yuka.musicplayer.model.RefusedTrackEntry
import kotlinx.coroutines.delay
import org.json.JSONArray

/**
 * Encapsulates live USB DAC hardware telemetry state & continuous polling loop.
 */
class DacTelemetryState(
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
    isDacConnected: Boolean
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
            uacVersion = audioEngine.getUacVersion()
            claimedInterfaces = audioEngine.getClaimedInterfaces()
            isHardwareVolumeLockedBySystem = audioEngine.isHardwareVolumeLockedBySystem()
            isForceSoftwareVolume = audioEngine.isForceSoftwareVolume()
            supportedBitDepths = audioEngine.getSupportedBitDepths()
            supportedSampleRates = audioEngine.getSupportedSampleRates()

            while (isDacConnected) {
                negotiatedSampleRate = audioEngine.getSampleRate()
                negotiatedBitDepth = audioEngine.getNegotiatedBitDepth()
                sourceSampleRate = audioEngine.getSourceSampleRate()
                sourceBitDepth = audioEngine.getSourceBitDepth()
                outputBitDepth = audioEngine.getOutputBitDepth()
                outputSampleRate = audioEngine.getOutputSampleRate()
                recentErrorCount = audioEngine.getRecentErrorCount()
                isSampleRateUnverified = audioEngine.isSampleRateUnverified()
                isDeviceWedged = audioEngine.isDeviceWedged()

                try {
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
                        refusedTrackHistory = list
                    } else {
                        refusedTrackHistory = emptyList()
                    }
                } catch (_: Exception) {}

                delay(1000)
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

    return DacTelemetryState(
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
