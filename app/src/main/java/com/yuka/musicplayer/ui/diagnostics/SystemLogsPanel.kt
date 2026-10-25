package com.yuka.musicplayer.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.yuka.musicplayer.utils.AppLogger
import com.yuka.musicplayer.utils.AppLogEntry
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.audio.AudioEngine
import com.yuka.musicplayer.model.RefusedTrackEntry
import com.yuka.musicplayer.model.ViewState
import com.yuka.musicplayer.ui.components.GlyphClose
import com.yuka.musicplayer.ui.components.ModalCloseButton
import com.yuka.musicplayer.ui.components.TactileBox
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SurfaceContainer
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh

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
fun LogItem(label: String, value: String, valueColor: Color = Color.Unspecified) {
    val actualValueColor = if (valueColor != Color.Unspecified) valueColor else m3OnSurface()
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
            color = actualValueColor,
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
    refusedTrackHistory: List<RefusedTrackEntry>,
    includeLogcat: Boolean = false,
    logcatLines: List<String> = emptyList()
): String {
    val sb = StringBuilder()
    sb.appendLine("=== TSROSSA AUDIO ENGINE DIAGNOSTICS ===")
    sb.appendLine("App Version: ${com.yuka.musicplayer.BuildConfig.VERSION_NAME}")
    sb.appendLine("Device Model: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
    sb.appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
    sb.appendLine()

    // 1. Device & Hardware
    sb.appendLine("[DEVICE & HARDWARE]")
    sb.appendLine("DAC Model: $dacName")
    val usbStatus = if (isDeviceWedged) "ERROR / WEDGED" else if (isDacConnected) "ACTIVE (Exclusive UAC$uacVersion)" else "DISCONNECTED"
    sb.appendLine("USB Status: $usbStatus")
    if (isDacConnected && !isDeviceWedged) {
        sb.appendLine("Driver Engine: libusb UAC2 (Native)")
        sb.appendLine("UAC Protocol: UAC$uacVersion")
    }
    sb.appendLine("Claimed Ifaces: ${if (claimedInterfaces.isNotEmpty()) claimedInterfaces else "--"}")
    if (isDacConnected) {
        sb.appendLine("Supported Rates: ${if (supportedSampleRates.isNotEmpty()) supportedSampleRates else "--"}")
        sb.appendLine("Supported Bits: ${if (supportedBitDepths.isNotEmpty()) supportedBitDepths else "--"}")
    }
    sb.appendLine()

    // 2. Signal Path
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
    } else if (isDacConnected && negotiatedSampleRate > 0) {
        sb.appendLine("Last Active Clock: ${negotiatedSampleRate / 1000.0} kHz")
    }
    sb.appendLine("I/O Error Count: $recentErrorCount")
    sb.appendLine()

    // 3. Engine Configuration
    sb.appendLine("[ENGINE CONFIGURATION]")
    sb.appendLine("RAM Playback: ACTIVE (Zero Jitter)")
    sb.appendLine("Gapless Engine: ACTIVE (Direct Handover)")
    sb.appendLine("DSP Pipeline: BYPASSED (Bit-Perfect)")
    sb.appendLine()

    // 4. USB Hardware & Audit
    sb.appendLine("[USB HARDWARE & AUDIT]")
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
    if (refusedTrackHistory.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("--- Refused Tracks (${refusedTrackHistory.size}) ---")
        refusedTrackHistory.forEachIndexed { i, t ->
            sb.appendLine("${i + 1}. \"${t.filename}\" (${t.bitDepth}-Bit / ${t.sampleRate / 1000.0} kHz) -> Reason: ${t.reason}")
        }
    }

    // 5. Logcat (ONLY included when includeLogcat == true, i.e., for Share Report)
    if (includeLogcat) {
        sb.appendLine()
        sb.appendLine("[LOGCAT]")
        sb.appendLine("--- Application Event Logs ---")
        val appLogs = com.yuka.musicplayer.utils.AppLogger.entries.value
        if (appLogs.isNotEmpty()) {
            appLogs.forEach { entry ->
                sb.appendLine(entry.formattedLine())
            }
        } else {
            sb.appendLine("No application event logs recorded.")
        }
        if (logcatLines.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("--- Process Logcat Output ---")
            logcatLines.forEach { line ->
                sb.appendLine(line)
            }
        }
    }

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
    val coroutineScope = rememberCoroutineScope()
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
                Column {
                    Text(
                        text = "System Logs & Diagnostics", 
                        color = m3OnSurface(), 
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModernSansFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Bit-perfect UAC2 & hardware audit",
                        color = m3OnSurfaceVariant(),
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont
                    )
                }
            }

            ModalCloseButton(onClick = onClose)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- Action Toolbar (Row 2: Copy & Share Report) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Copy Report: ONLY Device & Hardware, Signal Path, Engine Configuration, and USB Hardware & Audit
            TactileBox(
                onClick = {
                    val report = generateDiagnosticReport(
                        dacName = dacName,
                        isDacConnected = isDacConnected,
                        isDeviceWedged = isDeviceWedged,
                        uacVersion = uacVersion,
                        claimedInterfaces = claimedInterfaces,
                        isPlaying = isPlaying,
                        sourceBitDepth = sourceBitDepth,
                        sourceSampleRate = sourceSampleRate,
                        outputBitDepth = outputBitDepth,
                        outputSampleRate = outputSampleRate,
                        negotiatedSampleRate = negotiatedSampleRate,
                        isSampleRateUnverified = isSampleRateUnverified,
                        recentErrorCount = recentErrorCount,
                        supportedSampleRates = supportedSampleRates,
                        supportedBitDepths = supportedBitDepths,
                        refusedTrackHistory = refusedTrackHistory,
                        includeLogcat = false
                    )
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("tsrossa_diagnostics", report)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Report copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                pressedScale = 0.94f,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(m3SurfaceContainerHigh())
                    .padding(vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    com.yuka.musicplayer.ui.components.GlyphClipboard(
                        color = LocalAccentColor.current,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Copy Report",
                        color = m3OnSurface(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = ModernSansFont
                    )
                }
            }

            // Share Report: ALL 5 sections including Logcat, saved as a temporary .txt file
            TactileBox(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            AppLogger.syncFromProcessLogcat(350)
                            val procLogcat = AppLogger.readProcessLogcat(300)
                            val fullReport = generateDiagnosticReport(
                                dacName = dacName,
                                isDacConnected = isDacConnected,
                                isDeviceWedged = isDeviceWedged,
                                uacVersion = uacVersion,
                                claimedInterfaces = claimedInterfaces,
                                isPlaying = isPlaying,
                                sourceBitDepth = sourceBitDepth,
                                sourceSampleRate = sourceSampleRate,
                                outputBitDepth = outputBitDepth,
                                outputSampleRate = outputSampleRate,
                                negotiatedSampleRate = negotiatedSampleRate,
                                isSampleRateUnverified = isSampleRateUnverified,
                                recentErrorCount = recentErrorCount,
                                supportedSampleRates = supportedSampleRates,
                                supportedBitDepths = supportedBitDepths,
                                refusedTrackHistory = refusedTrackHistory,
                                includeLogcat = true,
                                logcatLines = procLogcat
                            )
                            val diagDir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
                            diagDir.listFiles()?.forEach { it.delete() }
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            val reportFile = File(diagDir, "tsrossa_diagnostics_$timestamp.txt")
                            reportFile.writeText(fullReport, Charsets.UTF_8)

                            val fileUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                reportFile
                            )
                            withContext(Dispatchers.Main) {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, fileUri)
                                    clipData = ClipData.newRawUri("tsrossa_diagnostics", fileUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share tsrossa Diagnostics (.txt)")
                                context.startActivity(shareIntent)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Failed to share report: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                pressedScale = 0.94f,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(m3SurfaceContainerHigh())
                    .padding(vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Share Report",
                        color = LocalAccentColor.current,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = ModernSansFont
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Divider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

        // --- Scrollable Diagnostics Body (Single Unified List with 5 Sections) ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            // Section 1: Device & Hardware
            LogSectionHeader("DEVICE & HARDWARE")
            LogItem("DAC Model", dacName)
            if (isDeviceWedged) {
                LogItem("USB Status", "ERROR / WEDGED", androidx.compose.ui.graphics.Color.Red)
            } else if (isDacConnected) {
                LogItem("USB Status", "ACTIVE (Exclusive)", LocalAccentColor.current)
                LogItem("Driver Engine", "libusb UAC2 (Native)")
                LogItem("UAC Protocol", "UAC$uacVersion")
                LogItem("Claimed Ifaces", if (claimedInterfaces.isNotEmpty()) claimedInterfaces else "--")
                val formattedRates = if (supportedSampleRates.isNotEmpty()) {
                    supportedSampleRates.split(",").mapNotNull { it.trim().toIntOrNull() }
                        .map { if (it % 1000 == 0) "${it / 1000}k" else "${it / 1000.0}k" }
                        .joinToString(", ")
                } else "--"
                LogMultilineItem("Supported Rates", formattedRates, LocalAccentColor.current)
                LogItem("Supported Bit", if (supportedBitDepths.isNotEmpty()) supportedBitDepths else "--", LocalAccentColor.current)
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
                LogItem("Input Stream", srcStr)
                LogItem("DAC Output", outStr)
                
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
                LogItem("Hardware Clock", rateText, if (isSampleRateUnverified) LocalAccentColor.current else Color.Unspecified)
                LogItem("I/O Error Count", "$recentErrorCount", if (recentErrorCount == 0) Color.Unspecified else androidx.compose.ui.graphics.Color.Red)
            } else {
                LogItem("Stream State", "STOPPED / STANDBY", TerminalGray)
                if (isDacConnected && negotiatedSampleRate > 0) {
                    LogItem("Last Active Clock", "${negotiatedSampleRate / 1000.0} kHz", TerminalGray)
                }
                LogItem("I/O Error Count", "$recentErrorCount", if (recentErrorCount == 0) TerminalGray else androidx.compose.ui.graphics.Color.Red)
            }

            // Section 3: Engine Configuration
            LogSectionHeader("ENGINE CONFIGURATION")
            LogItem("RAM Playback", "ACTIVE (Zero Jitter)", LocalAccentColor.current)
            LogItem("Gapless Engine", "ACTIVE (Direct Handover)", LocalAccentColor.current)
            LogItem("DSP Pipeline", "BYPASSED (Bit-Perfect)", LocalAccentColor.current)

            // Section 4: USB Hardware & Audit
            LogSectionHeader("USB HARDWARE & AUDIT")
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
                color = m3OnSurface(),
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
                color = if (isDacConnected) m3OnSurface() else androidx.compose.ui.graphics.Color(0xFFFF8888),
                fontSize = 9.sp,
                lineHeight = 13.sp,
                fontFamily = TerminalFont,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            if (refusedTrackHistory.isNotEmpty()) {
                LogSectionHeader("REFUSED TRACKS (${refusedTrackHistory.size})")
                refusedTrackHistory.forEachIndexed { index, track ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            text = "${index + 1}. \"${track.filename}\"",
                            color = m3OnSurface(),
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

            if (isDacConnected) {
                Spacer(modifier = Modifier.height(10.dp))
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

            // Section 5: Logcat
            LogSectionHeader("LOGCAT")
            AppLogsSection(context = context)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AppLogsSection(context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val allLogs by AppLogger.entries.collectAsState()
    var selectedCategory by remember { mutableStateOf("ALL") }
    val categories = listOf("ALL", "AUDIO", "USB", "PLAYBACK", "UI", "SYSTEM", "LOGCAT")
    
    var logcatLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingLogcat by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCategory) {
        if (selectedCategory == "LOGCAT") {
            isLoadingLogcat = true
            logcatLines = AppLogger.readProcessLogcat(250)
            isLoadingLogcat = false
        } else {
            isRefreshing = true
            AppLogger.syncFromProcessLogcat(350)
            isRefreshing = false
        }
    }

    val filteredLogs = remember(allLogs, selectedCategory) {
        if (selectedCategory == "ALL") allLogs
        else allLogs.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Horizontal filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                val isSel = selectedCategory == cat
                TactileBox(
                    onClick = { selectedCategory = cat },
                    pressedScale = 0.94f,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) LocalAccentColor.current.copy(alpha = 0.22f) else m3SurfaceContainerHigh())
                        .border(
                            width = 1.dp,
                            color = if (isSel) LocalAccentColor.current.copy(alpha = 0.6f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSel) LocalAccentColor.current else TerminalGray,
                        fontFamily = ModernSansFont,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Sub-toolbar: Copy & Clear / Refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedCategory == "LOGCAT") "${logcatLines.size} logcat entries" else "${filteredLogs.size} events",
                color = TerminalGray,
                fontSize = 10.5.sp,
                fontFamily = TerminalFont
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TactileBox(
                    onClick = {
                        coroutineScope.launch {
                            if (selectedCategory == "LOGCAT") {
                                isLoadingLogcat = true
                                logcatLines = AppLogger.readProcessLogcat(250)
                                isLoadingLogcat = false
                            } else {
                                isRefreshing = true
                                AppLogger.syncFromProcessLogcat(350)
                                isRefreshing = false
                            }
                        }
                    },
                    pressedScale = 0.94f,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(m3SurfaceContainerHigh())
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isLoadingLogcat || isRefreshing) "..." else "Refresh",
                        color = LocalAccentColor.current,
                        fontFamily = ModernSansFont,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (selectedCategory != "LOGCAT") {
                    TactileBox(
                        onClick = { AppLogger.clear() },
                        pressedScale = 0.94f,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Red.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Clear",
                            color = Color(0xFFFF5252),
                            fontFamily = ModernSansFont,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                TactileBox(
                    onClick = {
                        val text = if (selectedCategory == "LOGCAT") {
                            logcatLines.joinToString("\n")
                        } else {
                            filteredLogs.joinToString("\n") { it.formattedLine() }
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("tsrossa_logs", text))
                        Toast.makeText(context, "Logs copied!", Toast.LENGTH_SHORT).show()
                    },
                    pressedScale = 0.94f,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(LocalAccentColor.current.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Copy",
                        color = LocalAccentColor.current,
                        fontFamily = ModernSansFont,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Log content box with fixed height (280.dp) so it embeds smoothly into the parent scrollable list
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF070A0F))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (selectedCategory == "LOGCAT") {
                if (isLoadingLogcat) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Reading process logcat...", color = TerminalGray, fontFamily = TerminalFont, fontSize = 11.sp)
                    }
                } else if (logcatLines.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No logcat output found.", color = TerminalGray, fontFamily = TerminalFont, fontSize = 11.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(logcatLines) { line ->
                            val color = when {
                                line.contains(" E/") || line.contains(" E ") -> Color(0xFFFF5252)
                                line.contains(" W/") || line.contains(" W ") -> Color(0xFFFFD700)
                                line.contains("Tsrossa") || line.contains("AudioEngine") -> LocalAccentColor.current
                                else -> m3OnSurface().copy(alpha = 0.85f)
                            }
                            Text(
                                text = line,
                                color = color,
                                fontFamily = TerminalFont,
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            } else {
                if (filteredLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No log events in '$selectedCategory'.", color = TerminalGray, fontFamily = TerminalFont, fontSize = 11.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { entry ->
                            val levelColor = when (entry.level) {
                                "ERR" -> Color(0xFFFF5252)
                                "WARN" -> Color(0xFFFFD700)
                                "DEBUG" -> Color(0xFF888888)
                                else -> LocalAccentColor.current
                            }
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = entry.timestamp,
                                        color = TerminalGray,
                                        fontFamily = TerminalFont,
                                        fontSize = 9.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "[${entry.level}/${entry.category}]",
                                        color = levelColor,
                                        fontFamily = TerminalFont,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = entry.message,
                                    color = m3OnSurface(),
                                    fontFamily = TerminalFont,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
