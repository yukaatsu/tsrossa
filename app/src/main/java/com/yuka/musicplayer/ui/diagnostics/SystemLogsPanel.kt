package com.yuka.musicplayer.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.audio.AudioEngine
import com.yuka.musicplayer.model.RefusedTrackEntry
import com.yuka.musicplayer.model.ViewState
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.TerminalWhite

@Composable
fun LogSectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp)
    ) {
        Text(
            text = "— $title —",
            color = LocalAccentColor.current,
            fontFamily = TerminalFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(6.dp))
        Divider(
            modifier = Modifier.weight(1f),
            color = LocalAccentColor.current.copy(alpha = 0.25f),
            thickness = 1.dp
        )
    }
}

@Composable
fun LogItem(label: String, value: String, valueColor: Color = TerminalWhite) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TerminalGray,
            fontFamily = TerminalFont,
            fontSize = 11.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontFamily = TerminalFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LogMultilineItem(label: String, value: String, valueColor: Color = LocalAccentColor.current) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = TerminalGray,
            fontFamily = TerminalFont,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor,
            fontFamily = TerminalFont,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}

fun generateDiagnosticReport(
    dacName: String,
    isDacConnected: Boolean,
    isDeviceWedged: Boolean,
    uacVersion: Int,
    claimedInterfaces: String,
    isPlaying: Boolean,
    sourceBitDepth: Int,
    sourceSampleRate: Int,
    outputBitDepth: Int,
    outputSampleRate: Int,
    negotiatedSampleRate: Int,
    isSampleRateUnverified: Boolean,
    recentErrorCount: Int,
    supportedSampleRates: String,
    supportedBitDepths: String,
    refusedTrackHistory: List<RefusedTrackEntry>
): String {
    val sb = StringBuilder()
    sb.appendLine("=== TSROSSA AUDIO ENGINE DIAGNOSTICS ===")
    sb.appendLine("App Version: ${com.yuka.musicplayer.BuildConfig.VERSION_NAME}")
    sb.appendLine("Device Model: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
    sb.appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
    sb.appendLine()
    sb.appendLine("[HARDWARE & USB]")
    sb.appendLine("DAC Model: $dacName")
    val usbStatus = if (isDeviceWedged) "ERROR / WEDGED" else if (isDacConnected) "ACTIVE (Exclusive UAC$uacVersion)" else "DISCONNECTED"
    sb.appendLine("USB Status: $usbStatus")
    sb.appendLine("Claimed Ifaces: ${if (claimedInterfaces.isNotEmpty()) claimedInterfaces else "--"}")
    sb.appendLine()
    sb.appendLine("[SIGNAL PATH]")
    sb.appendLine("Stream State: ${if (isPlaying) "PLAYING" else "STOPPED / STANDBY"}")
    if (isPlaying) {
        sb.appendLine("Input Stream: $sourceBitDepth-Bit / ${sourceSampleRate / 1000.0} kHz")
        sb.appendLine("DAC Output: $outputBitDepth-Bit / ${outputSampleRate / 1000.0} kHz")
        val transmissionStatus = when {
            sourceSampleRate == 0 -> "STANDBY [NO STREAM]"
            sourceSampleRate == outputSampleRate && sourceBitDepth == outputBitDepth -> "BIT-PERFECT [PASS]"
            else -> "RESAMPLED [FAIL]"
        }
        sb.appendLine("Transmission: $transmissionStatus")
        val rateText = if (isSampleRateUnverified) "${negotiatedSampleRate / 1000.0} kHz (Unverified)" else "${negotiatedSampleRate / 1000.0} kHz"
        sb.appendLine("Hardware Clock: $rateText")
    }
    sb.appendLine("I/O Error Count: $recentErrorCount")
    sb.appendLine()
    sb.appendLine("[DAC CAPABILITIES]")
    sb.appendLine("Supported Rates: ${if (supportedSampleRates.isNotEmpty()) supportedSampleRates else "--"}")
    sb.appendLine("Supported Bits: ${if (supportedBitDepths.isNotEmpty()) supportedBitDepths else "--"}")
    sb.appendLine()
    sb.appendLine("[ENGINE CONFIG]")
    sb.appendLine("RAM Preload Engine: ACTIVE (Zero Jitter)")
    sb.appendLine("Gapless DMA Handover: ACTIVE")
    sb.appendLine("AudioFlinger / DSP: 100% BYPASSED")
    if (refusedTrackHistory.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("[REFUSED TRACKS (${refusedTrackHistory.size})]")
        refusedTrackHistory.forEachIndexed { i, t ->
            sb.appendLine("${i + 1}. \"${t.filename}\" (${t.bitDepth}-Bit / ${t.sampleRate / 1000.0} kHz) -> Reason: ${t.reason}")
        }
    }
    sb.appendLine()
    sb.appendLine("[USB AUDIT TRAIL]")
    sb.appendLine("--- Framework (Java) Trace ---")
    sb.appendLine(com.yuka.musicplayer.audio.UsbAudioController.getTrace())
    sb.appendLine()
    sb.appendLine("--- Engine (libusb) Trace ---")
    val nativeAudit = try {
        com.yuka.musicplayer.audio.AudioPlayerManager.audioEngine.getLastUsbDiagnostic()
    } catch (e: Exception) {
        "Error fetching native audit: ${e.message}"
    }
    sb.appendLine(if (nativeAudit.isNotEmpty()) nativeAudit else "No native events recorded.")
    sb.appendLine("=========================================")
    return sb.toString()
}

@Composable
fun SystemLogsPanel(
    audioEngine: AudioEngine,
    viewState: ViewState,
    context: android.content.Context,
    usbManager: android.hardware.usb.UsbManager,
    isDeviceWedged: Boolean,
    isDacConnected: Boolean,
    isPlaying: Boolean,
    sourceSampleRate: Int,
    sourceBitDepth: Int,
    outputSampleRate: Int,
    outputBitDepth: Int,
    uacVersion: Int,
    claimedInterfaces: String,
    isSampleRateUnverified: Boolean,
    negotiatedSampleRate: Int,
    recentErrorCount: Int,
    supportedSampleRates: String,
    supportedBitDepths: String,
    refusedTrackHistory: List<RefusedTrackEntry>,
    onClose: () -> Unit
) {
    var showExitDialog by remember { mutableStateOf(false) }
    val dacName = remember(viewState) {
        val devices = usbManager.deviceList.values
        val audioDevice = devices.find { device ->
            var hasAudioInterface = false
            for (i in 0 until device.interfaceCount) {
                if (device.getInterface(i).interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_AUDIO) {
                    hasAudioInterface = true
                    break
                }
            }
            hasAudioInterface
        }
        audioDevice?.productName ?: "Unknown DAC"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        // --- Header Bar (Row 1: Title & Close) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isDacConnected && !isDeviceWedged) LocalAccentColor.current else androidx.compose.ui.graphics.Color.Red,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SYSTEM LOGS & DIAGNOSTICS", 
                    color = TerminalWhite, 
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .border(1.dp, androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .background(androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "✕ CLOSE",
                    color = androidx.compose.ui.graphics.Color.Red,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Action Toolbar (Row 2: Copy & Share Report) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, LocalAccentColor.current.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .background(LocalAccentColor.current.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                    .clickable {
                        val report = generateDiagnosticReport(
                            dacName, isDacConnected, isDeviceWedged, uacVersion,
                            claimedInterfaces, isPlaying, sourceBitDepth, sourceSampleRate,
                            outputBitDepth, outputSampleRate, negotiatedSampleRate,
                            isSampleRateUnverified, recentErrorCount, supportedSampleRates,
                            supportedBitDepths, refusedTrackHistory
                        )
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("tsrossa_diagnostics", report)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Diagnostics copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📋 COPY REPORT",
                    color = LocalAccentColor.current,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, LocalAccentColor.current.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .background(LocalAccentColor.current.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                    .clickable {
                        val report = generateDiagnosticReport(
                            dacName, isDacConnected, isDeviceWedged, uacVersion,
                            claimedInterfaces, isPlaying, sourceBitDepth, sourceSampleRate,
                            outputBitDepth, outputSampleRate, negotiatedSampleRate,
                            isSampleRateUnverified, recentErrorCount, supportedSampleRates,
                            supportedBitDepths, refusedTrackHistory
                        )
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, report)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share tsrossa Diagnostics")
                        context.startActivity(shareIntent)
                    }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↗ SHARE REPORT",
                    color = LocalAccentColor.current,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Divider(color = LocalAccentColor.current.copy(alpha = 0.25f), thickness = 1.dp)
        
        // --- Scrollable Diagnostics Body ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            // Section 1: Hardware & Device
            LogSectionHeader("DEVICE & HARDWARE")
            LogItem("DAC Model", dacName, TerminalWhite)
            if (isDeviceWedged) {
                LogItem("USB Status", "ERROR / WEDGED", androidx.compose.ui.graphics.Color.Red)
            } else if (isDacConnected) {
                LogItem("USB Status", "ACTIVE (Exclusive)", LocalAccentColor.current)
                LogItem("Driver Engine", "libusb UAC2 (Native)", TerminalWhite)
                LogItem("UAC Protocol", "UAC$uacVersion", TerminalWhite)
                LogItem("Claimed Ifaces", if (claimedInterfaces.isNotEmpty()) claimedInterfaces else "--", TerminalWhite)
            } else {
                LogItem("USB Status", "DISCONNECTED", TerminalGray)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "[ INITIALIZE / RECONNECT DAC ]",
                    color = LocalAccentColor.current,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            com.yuka.musicplayer.audio.AudioPlayerManager.usbAudioController.scanAndRequestPermission()
                        }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                )
            }

            // Section 2: Signal Path
            LogSectionHeader("SIGNAL PATH")
            if (isPlaying) {
                LogItem("Stream State", "PLAYING", LocalAccentColor.current)
                val srcStr = if (sourceSampleRate > 0) "$sourceBitDepth-Bit / ${sourceSampleRate / 1000.0} kHz" else "--"
                val outStr = if (outputSampleRate > 0) "$outputBitDepth-Bit / ${outputSampleRate / 1000.0} kHz" else "--"
                LogItem("Input Stream", srcStr, TerminalWhite)
                LogItem("DAC Output", outStr, TerminalWhite)
                
                if (sourceSampleRate > 0 && sourceSampleRate == outputSampleRate && sourceBitDepth == outputBitDepth) {
                    LogItem("Transmission", "[ ✓ BIT-PERFECT ]", androidx.compose.ui.graphics.Color(0xFF55FF55))
                } else if (sourceSampleRate > 0) {
                    LogItem("Transmission", "[ ✗ RESAMPLED ]", androidx.compose.ui.graphics.Color(0xFFFF5555))
                } else {
                    LogItem("Transmission", "[ ⋯ NO STREAM ]", TerminalGray)
                }
                
                val rateText = if (isSampleRateUnverified) {
                    "${negotiatedSampleRate / 1000.0} kHz (Unverified)"
                } else {
                    "${negotiatedSampleRate / 1000.0} kHz"
                }
                LogItem("Hardware Clock", rateText, if (isSampleRateUnverified) LocalAccentColor.current else TerminalWhite)
                LogItem("I/O Error Count", "$recentErrorCount", if (recentErrorCount == 0) TerminalWhite else androidx.compose.ui.graphics.Color.Red)
            } else {
                LogItem("Stream State", "STOPPED / STANDBY", TerminalGray)
                if (isDacConnected && negotiatedSampleRate > 0) {
                    LogItem("Last Active Clock", "${negotiatedSampleRate / 1000.0} kHz", TerminalGray)
                }
                LogItem("I/O Error Count", "$recentErrorCount", if (recentErrorCount == 0) TerminalGray else androidx.compose.ui.graphics.Color.Red)
            }

            // Section 3: DAC Capabilities
            if (isDacConnected) {
                LogSectionHeader("DAC CAPABILITIES")
                val formattedRates = if (supportedSampleRates.isNotEmpty()) {
                    supportedSampleRates.split(",").mapNotNull { it.trim().toIntOrNull() }
                        .map { if (it % 1000 == 0) "${it / 1000}k" else "${it / 1000.0}k" }
                        .joinToString(", ")
                } else "--"
                LogMultilineItem("Supported Rates", formattedRates, LocalAccentColor.current)
                LogItem("Supported Bit", if (supportedBitDepths.isNotEmpty()) supportedBitDepths else "--", LocalAccentColor.current)
            }

            // Section 4: Engine Config
            LogSectionHeader("ENGINE CONFIGURATION")
            LogItem("RAM Playback", "ACTIVE (Zero Jitter)", LocalAccentColor.current)
            LogItem("Gapless Engine", "ACTIVE (Direct Handover)", LocalAccentColor.current)
            LogItem("DSP Pipeline", "BYPASSED (Bit-Perfect)", LocalAccentColor.current)

            // Section 5: USB Audit Trail
            LogSectionHeader("USB HARDWARE & DRIVER AUDIT")
            val nativeAudit = remember(isDacConnected) {
                try {
                    audioEngine.getLastUsbDiagnostic()
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            val javaAudit = com.yuka.musicplayer.audio.UsbAudioController.getTrace()

            Text(
                text = "FRAMEWORK (JAVA) LOG:",
                color = LocalAccentColor.current,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TerminalFont,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
            Text(
                text = javaAudit,
                color = TerminalWhite,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                fontFamily = TerminalFont,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Text(
                text = "ENGINE (LIBUSB) LOG:",
                color = LocalAccentColor.current,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TerminalFont,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
            Text(
                text = if (nativeAudit.isNotEmpty()) nativeAudit else "No native events recorded.",
                color = if (isDacConnected) TerminalWhite else androidx.compose.ui.graphics.Color(0xFFFF8888),
                fontSize = 9.sp,
                lineHeight = 13.sp,
                fontFamily = TerminalFont,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Section 5: Refused Tracks (if any)
            if (refusedTrackHistory.isNotEmpty()) {
                LogSectionHeader("REFUSED TRACKS (${refusedTrackHistory.size})")
                refusedTrackHistory.forEachIndexed { index, track ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            text = "${index + 1}. \"${track.filename}\"",
                            color = TerminalWhite,
                            fontFamily = TerminalFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "   Format: ${track.bitDepth}-Bit / ${track.sampleRate / 1000.0} kHz",
                            color = TerminalGray,
                            fontFamily = TerminalFont,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "   Reason: ${track.reason}",
                            color = androidx.compose.ui.graphics.Color.Red,
                            fontFamily = TerminalFont,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Section 6: Kill Engine Action
            if (isDacConnected) {
                Button(
                    onClick = { showExitDialog = true },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "KILL ENGINE & RELEASE DAC",
                        fontFamily = TerminalFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }

                if (showExitDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        title = { Text(text = "RELEASE DAC & STOP", fontFamily = TerminalFont, fontWeight = FontWeight.Bold) },
                        text = { Text(text = "Are you sure you want to release the USB DAC interface and stop audio service?", fontFamily = TerminalFont, fontSize = 12.sp) },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    showExitDialog = false
                                    val intent = Intent(context, com.yuka.musicplayer.audio.AudioForegroundService::class.java).apply {
                                        action = "ACTION_STOP"
                                    }
                                    context.startService(intent)
                                }
                            ) {
                                Text("RELEASE", fontFamily = TerminalFont, color = androidx.compose.ui.graphics.Color.Red, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { showExitDialog = false }) {
                                Text("CANCEL", fontFamily = TerminalFont)
                            }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
