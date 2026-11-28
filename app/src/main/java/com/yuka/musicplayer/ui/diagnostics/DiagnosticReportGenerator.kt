package com.yuka.musicplayer.ui.diagnostics

import android.content.Context
import android.os.Build
import com.yuka.musicplayer.model.RefusedTrackEntry
import com.yuka.musicplayer.utils.AppLogEntry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    includeLogcat: Boolean = true,
    isPeqEnabled: Boolean = false,
    isCompressorEnabled: Boolean = false,
    isHardwareVolumeActive: Boolean = false,
    isBitPerfectDirectPass: Boolean = false,
    isGaplessReady: Boolean = false,
    appLogs: List<AppLogEntry> = com.yuka.musicplayer.utils.AppLogger.entries.value,
    logcatLines: List<String> = emptyList(),
    context: Context? = null
): String {
    val sb = StringBuilder()
    sb.appendLine("=== TSROSSA AUDIO ENGINE DIAGNOSTICS ===")
    sb.appendLine("App Version: ${com.yuka.musicplayer.BuildConfig.VERSION_NAME}")
    sb.appendLine("Device Model: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
    sb.appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
    sb.appendLine()

    // 1. Storage & Library Scanner State
    sb.appendLine("[STORAGE & LIBRARY SCANNER]")
    if (context != null) {
        val kewSp = com.yuka.musicplayer.core.prefs.AppPreferences.get(context)
        val legacySp = context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
        val scanDiag = com.yuka.musicplayer.util.MusicScannerHelper.lastScanDiagnostics
        val musicRoot = resolveConfiguredMusicRootPath(
            kewPrefsRoot = kewSp.getString("music_root_directory", null),
            legacyPrefsRoot = legacySp.getString("music_root_directory", null),
            legacyTreePath = legacySp.getString("music_root_tree_path", null) ?: kewSp.getString("music_root_tree_path", null),
            lastScanRootPath = scanDiag?.rootPath
        )
        val treeUri = legacySp.getString("music_root_tree_uri", null)
            ?: kewSp.getString("music_root_tree_uri", null)
            ?: "(None)"
        val isBasicGranted = com.yuka.musicplayer.util.checkStoragePermission(context)
        val isAllFilesGranted = com.yuka.musicplayer.util.isAllFilesAccessGranted(context)
        val rootF = File(musicRoot)
        val exists = rootF.exists()
        val isDir = rootF.isDirectory
        val canRead = rootF.canRead()
        val hasNomedia = exists && File(rootF, ".nomedia").exists()

        sb.appendLine("Basic Media Access: ${if (isBasicGranted) "GRANTED" else "RESTRICTED"}")
        sb.appendLine("All Files Access (MANAGE_EXTERNAL_STORAGE): ${if (isAllFilesGranted) "GRANTED (FUSE .nomedia Bypass Active)" else "NOT GRANTED"}")
        sb.appendLine("Configured Music Root: $musicRoot")
        sb.appendLine("Root Status: exists=$exists, isDir=$isDir, canRead=$canRead, hasNomedia=$hasNomedia")
        sb.appendLine("SAF Tree URI: $treeUri")

        val blockingNomedia = if (exists) com.yuka.musicplayer.util.MusicScannerHelper.findBlockingNomediaFiles(rootF) else emptyList()
        sb.appendLine("Ancestor .nomedia Blocks: ${if (blockingNomedia.isEmpty()) "None" else blockingNomedia.joinToString()}")
        if (scanDiag != null) {
            sb.appendLine("Last Scan Diagnostics: Root=${scanDiag.rootPath}, Total=${scanDiag.totalDiscovered}, Duration=${scanDiag.elapsedMs}ms")
            sb.appendLine("  L1(FUSE Direct)=${scanDiag.layer1WalkCount}, L2(MediaStore Sync Triggered)=${scanDiag.layer2ReindexTriggered} (Post-L2=${scanDiag.layer2AfterReindexCount}), L3(MediaStore Query Added)=${scanDiag.layer3MediaStoreAdded}, L4(SAF Document Bridge Added)=${scanDiag.layer4SafBridgeAdded}")
        }
    } else {
        sb.appendLine("Storage context unavailable")
    }
    sb.appendLine()

    // 2. Device & Hardware
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

    // 3. Signal Path
    sb.appendLine("[SIGNAL PATH]")
    sb.appendLine("Stream State: ${if (isPlaying) "PLAYING" else "STOPPED / STANDBY"}")
    if (isPlaying) {
        sb.appendLine("Input Stream: $sourceBitDepth-Bit / ${sourceSampleRate / 1000.0} kHz")
        sb.appendLine("DAC Output: $outputBitDepth-Bit / ${outputSampleRate / 1000.0} kHz")
        val transmissionStatus = resolveTransmissionReportStatus(
            sourceSampleRate = sourceSampleRate,
            outputSampleRate = outputSampleRate,
            isSampleRateUnverified = isSampleRateUnverified,
            isPlaying = isPlaying
        )
        val bitDepthStatus = resolveBitDepthReportStatus(
            sourceBitDepth = sourceBitDepth,
            outputBitDepth = outputBitDepth
        )
        sb.appendLine("Transmission: $transmissionStatus")
        sb.appendLine("Bit Depth Path: $bitDepthStatus")
        val rateText = if (isSampleRateUnverified) "${negotiatedSampleRate / 1000.0} kHz (Unverified)" else "${negotiatedSampleRate / 1000.0} kHz"
        sb.appendLine("Hardware Clock: $rateText")
    } else if (isDacConnected && negotiatedSampleRate > 0) {
        sb.appendLine("Last Active Clock: ${negotiatedSampleRate / 1000.0} kHz")
    }
    sb.appendLine("I/O Error Count: $recentErrorCount")
    sb.appendLine()

    // 4. Engine Configuration
    sb.appendLine("[ENGINE CONFIGURATION]")
    sb.appendLine("RAM Playback: ACTIVE (Zero Jitter)")
    sb.appendLine(
        "Gapless Engine: " +
            if (isGaplessReady) "READY (Next Track Pre-Buffered)" else "IDLE (No Next Track Buffered)"
    )
    val dspStatusLabel = resolveDspPipelineStatusLabel(
        isBitPerfectDirectPass = isBitPerfectDirectPass,
        isPeqEnabled = isPeqEnabled,
        isCompressorEnabled = isCompressorEnabled,
        isHardwareVolumeActive = isHardwareVolumeActive
    )
    sb.appendLine("DSP Pipeline: $dspStatusLabel")
    sb.appendLine()

    // 5. USB Hardware & Audit
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

    // 6. Logcat
    if (includeLogcat) {
        sb.appendLine()
        sb.appendLine("[LOGCAT]")
        sb.appendLine("--- Application Event Logs (ALL: ${appLogs.size} events) ---")
        if (appLogs.isNotEmpty()) {
            appLogs.forEach { entry ->
                sb.appendLine(entry.formattedLine())
            }
        } else {
            sb.appendLine("No application event logs recorded.")
        }
        if (logcatLines.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("--- Process Logcat Output (${logcatLines.size} lines) ---")
            logcatLines.forEach { line ->
                sb.appendLine(line)
            }
        }
    }

    sb.appendLine("=========================================")
    return sb.toString()
}
