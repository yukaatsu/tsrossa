package com.yuka.musicplayer.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.yuka.musicplayer.audio.AudioEngine
import com.yuka.musicplayer.model.RefusedTrackEntry
import com.yuka.musicplayer.model.ViewState
import com.yuka.musicplayer.ui.components.GlyphClipboard
import com.yuka.musicplayer.ui.components.ModalCloseButton
import com.yuka.musicplayer.ui.components.TactileBox
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    isHardwareVolumeActive: Boolean = false,
    isPeqEnabled: Boolean = false,
    onClose: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var dacName by remember { mutableStateOf("External USB DAC") }
    LaunchedEffect(isDacConnected, viewState) {
        withContext(Dispatchers.IO) {
            val devices = runCatching { usbManager.deviceList.values }.getOrNull() ?: emptyList()
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
            dacName = audioDevice?.productName ?: if (isDacConnected) "USB Audio Device" else "No DAC Attached"
        }
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
                            if (isDacConnected && !isDeviceWedged) LocalAccentColor.current else Color.Red,
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
            TactileBox(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            AppLogger.syncFromProcessLogcat(350)
                            val procLogcat = AppLogger.readProcessLogcat(300)
                            val currentAppLogs = AppLogger.entries.value
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
                                includeLogcat = true,
                                appLogs = currentAppLogs,
                                logcatLines = procLogcat,
                                context = context
                            )
                            withContext(Dispatchers.Main) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("tsrossa_diagnostics", report)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Report copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Failed to copy report: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    GlyphClipboard(
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
                                logcatLines = procLogcat,
                                context = context
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

        // --- Scrollable Diagnostics Body ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            // Section 0: Storage & Library Scanner
            LogSectionHeader("STORAGE & LIBRARY SCANNER")
            val kewSp = remember { com.yuka.musicplayer.core.prefs.AppPreferences.get(context) }
            val legacySp = remember { context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE) }
            val scanDiag = com.yuka.musicplayer.util.MusicScannerHelper.lastScanDiagnostics
            val musicRoot = remember(kewSp, legacySp, scanDiag?.rootPath) {
                resolveConfiguredMusicRootPath(
                    kewPrefsRoot = kewSp.getString("music_root_directory", null),
                    legacyPrefsRoot = legacySp.getString("music_root_directory", null),
                    legacyTreePath = legacySp.getString("music_root_tree_path", null) ?: kewSp.getString("music_root_tree_path", null),
                    lastScanRootPath = scanDiag?.rootPath
                )
            }
            val isBasicGranted = remember { com.yuka.musicplayer.util.checkStoragePermission(context) }
            val isAllFilesGranted = remember { com.yuka.musicplayer.util.isAllFilesAccessGranted(context) }

            LogItem("Music Root", if (musicRoot.length > 30) "..." + musicRoot.takeLast(28) else musicRoot)
            LogItem("Basic Permission", if (isBasicGranted) "GRANTED" else "RESTRICTED", if (isBasicGranted) LocalAccentColor.current else Color.Red)
            LogItem("All Files Access", if (isAllFilesGranted) "GRANTED (FUSE Bypass)" else "NOT GRANTED", if (isAllFilesGranted) LocalAccentColor.current else TerminalGray)
            if (scanDiag != null) {
                LogItem("Last Scan Found", "${scanDiag.totalDiscovered} tracks (${scanDiag.elapsedMs}ms)")
                LogItem("Scan Breakdown", "L1:${scanDiag.layer1WalkCount} L2-Sync:${scanDiag.layer2ReindexTriggered} L3-MS:+${scanDiag.layer3MediaStoreAdded} L4-SAF:+${scanDiag.layer4SafBridgeAdded}")
            } else {
                LogItem("Last Scan Status", "No scan recorded yet")
            }

            // Section 1: Device & Hardware
            LogSectionHeader("DEVICE & HARDWARE")
            LogItem("DAC Model", dacName)
            if (isDeviceWedged) {
                LogItem("USB Status", "ERROR / WEDGED", Color.Red)
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
                    fontWeight = FontWeight.Bold,
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
                
                val srState = evaluateSampleRateTransmission(
                    isPlaying = isPlaying,
                    sourceSampleRate = sourceSampleRate,
                    outputSampleRate = outputSampleRate,
                    isSampleRateUnverified = isSampleRateUnverified
                )
                val transmissionLabel = resolveTransmissionUiLabel(
                    sourceSampleRate = sourceSampleRate,
                    outputSampleRate = outputSampleRate,
                    isSampleRateUnverified = isSampleRateUnverified,
                    isPlaying = isPlaying
                )
                val transmissionColor = when (srState) {
                    SampleRateTransmissionState.NO_STREAM -> TerminalGray
                    SampleRateTransmissionState.UNKNOWN_INSUFFICIENT_DATA,
                    SampleRateTransmissionState.UNKNOWN_UNVERIFIED_CLOCK -> LocalAccentColor.current
                    SampleRateTransmissionState.BIT_PERFECT -> Color(0xFF55FF55)
                    SampleRateTransmissionState.RESAMPLED -> Color(0xFFFF5555)
                }
                LogItem("Transmission", transmissionLabel, transmissionColor)

                val bdState = evaluateBitDepthPath(sourceBitDepth, outputBitDepth)
                val bitDepthLabel = resolveBitDepthUiLabel(sourceBitDepth, outputBitDepth)
                val bitDepthColor = when (bdState) {
                    BitDepthPathState.UNKNOWN,
                    BitDepthPathState.DOWNCONVERTED -> LocalAccentColor.current
                    BitDepthPathState.NATIVE_EXACT,
                    BitDepthPathState.LOSSLESS_CONTAINER -> Color(0xFF55FF55)
                }
                LogItem("Bit Depth Path", bitDepthLabel, bitDepthColor)
                
                val rateText = if (isSampleRateUnverified) {
                    "${negotiatedSampleRate / 1000.0} kHz (Unverified)"
                } else {
                    "${negotiatedSampleRate / 1000.0} kHz"
                }
                LogItem("Hardware Clock", rateText, if (isSampleRateUnverified) LocalAccentColor.current else Color.Unspecified)
                LogItem("I/O Error Count", "$recentErrorCount", if (recentErrorCount == 0) Color.Unspecified else Color.Red)
            } else {
                LogItem("Stream State", "STOPPED / STANDBY", TerminalGray)
                if (isDacConnected && negotiatedSampleRate > 0) {
                    LogItem("Last Active Clock", "${negotiatedSampleRate / 1000.0} kHz", TerminalGray)
                }
                LogItem("I/O Error Count", "$recentErrorCount", if (recentErrorCount == 0) TerminalGray else Color.Red)
            }

            // Section 3: Engine Configuration
            LogSectionHeader("ENGINE CONFIGURATION")
            LogItem("RAM Playback", "ACTIVE (Zero Jitter)", LocalAccentColor.current)
            LogItem("Gapless Engine", "ACTIVE (Direct Handover)", LocalAccentColor.current)
            LogItem(
                "Volume Engine",
                if (isHardwareVolumeActive) "32-BIT HARDWARE CHIP (UAC)" else "UNIVERSAL 64-BIT DSP",
                LocalAccentColor.current
            )
            LogItem(
                "DSP Pipeline",
                when {
                    isPeqEnabled -> "ACTIVE (64-Bit 10-Band PEQ)"
                    !isHardwareVolumeActive -> "ACTIVE (64-Bit Float Gain)"
                    else -> "BYPASSED (100% Bit-Perfect)"
                },
                LocalAccentColor.current
            )

            // Section 4: USB Hardware & Audit
            LogSectionHeader("USB HARDWARE & AUDIT")
            var nativeAudit by remember { mutableStateOf("Loading USB diagnostic...") }
            var javaAudit by remember { mutableStateOf("Reading framework trace...") }
            LaunchedEffect(isDacConnected) {
                withContext(Dispatchers.IO) {
                    val nAudit = try {
                        audioEngine.getLastUsbDiagnostic()
                    } catch (e: Exception) {
                        "Error: ${e.message}"
                    }
                    val jAudit = com.yuka.musicplayer.audio.UsbAudioController.getTrace()
                    nativeAudit = nAudit
                    javaAudit = jAudit
                }
            }

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
                color = if (isDacConnected) m3OnSurface() else Color(0xFFFF8888),
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
                            color = Color.Red,
                            fontFamily = TerminalFont,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Section 5: Logcat
            LogSectionHeader("LOGCAT")
            AppLogsSection(context = context)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
