package com.yuka.musicplayer.ui.dialogs

import android.content.Context
import android.hardware.usb.UsbManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.yuka.musicplayer.BuildConfig
import com.yuka.musicplayer.audio.AudioEngine
import com.yuka.musicplayer.audio.AudioPlayerManager
import com.yuka.musicplayer.model.PlaybackSource
import com.yuka.musicplayer.model.RefusedTrackEntry
import com.yuka.musicplayer.model.RepeatMode
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.model.ViewState
import com.yuka.musicplayer.ui.components.CyberBottomSheet
import com.yuka.musicplayer.ui.diagnostics.SystemLogsPanel
import com.yuka.musicplayer.update.AppUpdateManager
import com.yuka.musicplayer.update.DownloadState
import com.yuka.musicplayer.update.ReleaseInfo
import com.yuka.musicplayer.update.UpdateCheckState
import java.io.File

/**
 * Host component managing all modal bottom sheet panels in KewApp:
 * 1. System Logs
 * 2. User Guide / Help
 * 3. App Updates
 * 4. Permission Setup Onboarding
 * 5. Priority Queue & Upcoming
 * 6. Track Context Options
 */
@Composable
fun AppModalSheetsHost(
    context: Context,
    viewState: ViewState,
    audioEngine: AudioEngine,
    usbManager: UsbManager,
    isDacConnected: Boolean,
    isPlaying: Boolean,
    telemetry: com.yuka.musicplayer.ui.diagnostics.DacTelemetryState,
    // Dialog visibility states
    showSystemLogs: Boolean,
    onDismissSystemLogs: () -> Unit,
    showHelpPanel: Boolean,
    onDismissHelpPanel: () -> Unit,
    showUpdatePanel: Boolean,
    onDismissUpdatePanel: () -> Unit,
    showPermissionDialog: Boolean,
    onDismissPermissionDialog: () -> Unit,
    isNotificationGranted: Boolean,
    isStorageGranted: Boolean,
    isDndGranted: Boolean,
    onRequestNotification: () -> Unit,
    onRequestStorage: () -> Unit,
    onRequestDnd: () -> Unit,
    onCompletePermissions: () -> Unit,
    // Update states
    updateCheckState: UpdateCheckState,
    downloadState: DownloadState,
    onTriggerCheckUpdate: () -> Unit,
    onTriggerDownloadAndInstall: (ReleaseInfo) -> Unit,
    // Queue states
    showQueuePanel: Boolean,
    onDismissQueuePanel: () -> Unit,
    currentTrack: TrackInfo?,
    priorityQueue: List<File>,
    upcomingTracks: List<File>,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onSelectQueueTrack: (File) -> Unit,
    // Track options
    trackActionTarget: File?,
    onDismissTrackOptions: () -> Unit,
    playlistSet: Set<String>,
    onPlayTrackAction: (File, PlaybackSource) -> Unit,
    onPlayNextInQueue: (File) -> Unit,
    onAddToQueue: (File) -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onRemoveFromPlaylist: (String) -> Unit
) {
    // 1. System Logs Panel
    CyberBottomSheet(
        visible = showSystemLogs,
        onDismissRequest = onDismissSystemLogs,
        maxHeightFraction = 0.82f,
        maxWidth = 440.dp
    ) {
        SystemLogsPanel(
            audioEngine = audioEngine,
            viewState = viewState,
            context = context,
            usbManager = usbManager,
            isDeviceWedged = telemetry.isDeviceWedged,
            isDacConnected = isDacConnected,
            isPlaying = isPlaying,
            sourceSampleRate = telemetry.sourceSampleRate,
            sourceBitDepth = telemetry.sourceBitDepth,
            outputSampleRate = telemetry.outputSampleRate,
            outputBitDepth = telemetry.outputBitDepth,
            uacVersion = telemetry.uacVersion,
            claimedInterfaces = telemetry.claimedInterfaces,
            isSampleRateUnverified = telemetry.isSampleRateUnverified,
            negotiatedSampleRate = telemetry.negotiatedSampleRate,
            recentErrorCount = telemetry.recentErrorCount,
            supportedSampleRates = telemetry.supportedSampleRates,
            supportedBitDepths = telemetry.supportedBitDepths,
            refusedTrackHistory = telemetry.refusedTrackHistory,
            onClose = onDismissSystemLogs
        )
    }

    // 2. Help Panel
    CyberBottomSheet(
        visible = showHelpPanel,
        onDismissRequest = onDismissHelpPanel,
        maxHeightFraction = 0.72f,
        maxWidth = 420.dp
    ) {
        HelpPanel(onClose = onDismissHelpPanel)
    }

    // 3. Update Panel
    CyberBottomSheet(
        visible = showUpdatePanel,
        onDismissRequest = onDismissUpdatePanel,
        maxHeightFraction = 0.58f,
        maxWidth = 380.dp,
        wrapHeight = true
    ) {
        UpdatePanel(
            currentVersion = BuildConfig.VERSION_NAME,
            updateCheckState = updateCheckState,
            downloadState = downloadState,
            onCheckForUpdate = onTriggerCheckUpdate,
            onDownloadAndInstall = onTriggerDownloadAndInstall,
            onInstallFile = { file -> AppUpdateManager.installApk(context, file) },
            onOpenPermissionSettings = { AppUpdateManager.openInstallPermissionSettings(context) },
            canInstallPackages = AppUpdateManager.canInstallPackages(context),
            onClose = onDismissUpdatePanel
        )
    }

    // 4. Permission Setup Onboarding Dialog
    if (showPermissionDialog) {
        CyberBottomSheet(
            visible = showPermissionDialog,
            onDismissRequest = onDismissPermissionDialog,
            maxHeightFraction = 0.78f,
            maxWidth = 420.dp
        ) {
            PermissionSetupDialog(
                isNotificationGranted = isNotificationGranted,
                isStorageGranted = isStorageGranted,
                isDndGranted = isDndGranted,
                onRequestNotification = onRequestNotification,
                onRequestStorage = onRequestStorage,
                onRequestDnd = onRequestDnd,
                onComplete = onCompletePermissions
            )
        }
    }

    // 5. Queue Panel
    CyberBottomSheet(
        visible = showQueuePanel,
        onDismissRequest = onDismissQueuePanel,
        maxHeightFraction = 0.65f,
        maxWidth = 420.dp
    ) {
        QueuePanel(
            currentTrack = currentTrack,
            priorityQueue = priorityQueue,
            upcomingTracks = upcomingTracks,
            onRemoveFromQueue = onRemoveFromQueue,
            onClearQueue = onClearQueue,
            onSelectTrack = onSelectQueueTrack,
            onClose = onDismissQueuePanel
        )
    }

    // 6. Track Options Bottom Sheet
    TrackOptionsBottomSheet(
        target = trackActionTarget,
        onDismissRequest = onDismissTrackOptions,
        playlistSet = playlistSet,
        viewState = viewState,
        onPlayTrack = onPlayTrackAction,
        onPlayNextInQueue = onPlayNextInQueue,
        onAddToQueue = onAddToQueue,
        onAddToPlaylist = onAddToPlaylist,
        onRemoveFromPlaylist = onRemoveFromPlaylist
    )
}
