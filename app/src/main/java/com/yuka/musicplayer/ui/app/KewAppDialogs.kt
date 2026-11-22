package com.yuka.musicplayer.ui.app

import android.content.Context
import android.content.SharedPreferences
import android.hardware.usb.UsbManager
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuka.musicplayer.audio.AudioEngine
import com.yuka.musicplayer.audio.DacVolumeState
import com.yuka.musicplayer.audio.PeqConfiguration
import com.yuka.musicplayer.audio.PeqPreferencesHelper
import com.yuka.musicplayer.audio.UserPeqPreset
import com.yuka.musicplayer.model.Playlist
import com.yuka.musicplayer.model.ViewState
import com.yuka.musicplayer.player.LibraryStateHolder
import com.yuka.musicplayer.player.PlaybackStateHolder
import com.yuka.musicplayer.ui.components.CyberBottomSheet
import com.yuka.musicplayer.ui.dialogs.AppModalActionHandler
import com.yuka.musicplayer.ui.dialogs.AppModalSheetsHost
import com.yuka.musicplayer.ui.dialogs.CreatePlaylistDialog
import com.yuka.musicplayer.ui.dialogs.FolderPickerDialog
import com.yuka.musicplayer.ui.dialogs.ParametricEqModalSheet
import com.yuka.musicplayer.ui.dialogs.PeqDspSwitchNoticeDialog
import com.yuka.musicplayer.ui.dialogs.RenamePlaylistDialog
import com.yuka.musicplayer.ui.dialogs.SelectPlaylistDialog
import com.yuka.musicplayer.ui.dialogs.WarmupConfirmDialog
import com.yuka.musicplayer.ui.dialogs.WarmupHudDialog
import com.yuka.musicplayer.ui.diagnostics.DacTelemetryState
import com.yuka.musicplayer.ui.views.SettingsView
import com.yuka.musicplayer.ui.views.settings.SettingsSubPage
import com.yuka.musicplayer.update.DownloadState
import com.yuka.musicplayer.update.ReleaseInfo
import com.yuka.musicplayer.update.UpdateCheckState
import com.yuka.musicplayer.util.MusicScannerHelper
import com.yuka.musicplayer.util.PlaylistStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pure helper determining whether enabling Parametric EQ (PEQ) must prompt the user
 * to switch from Hybrid Hardware UAC to Universal 64-Bit DSP.
 * Returns true ONLY when PEQ is transitioning from OFF -> ON AND Hybrid Hardware UAC mode is currently selected.
 * When Master Output Gain is already in Universal 64-Bit DSP mode (isHardwareVolumeEnabled == false), returns false.
 */
internal fun shouldPromptPeqDspSwitch(
    wasPeqEnabled: Boolean,
    willEnablePeq: Boolean,
    isHardwareVolumeEnabled: Boolean
): Boolean {
    return !wasPeqEnabled && willEnablePeq && isHardwareVolumeEnabled
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun KewAppDialogs(
    context: Context,
    audioEngine: AudioEngine,
    usbManager: UsbManager,
    sharedPref: SharedPreferences,
    libraryState: LibraryStateHolder,
    playbackState: PlaybackStateHolder,
    dacVolumeState: DacVolumeState,
    welcomeModalState: WelcomeModalCycleState,
    telemetry: DacTelemetryState,
    viewState: ViewState,
    onViewStateChange: (ViewState) -> Unit,
    modalActionHandler: AppModalActionHandler,
    upcomingTracks: List<File>,
    isDacConnected: Boolean = false,
    showSystemLogs: Boolean,
    onDismissSystemLogs: () -> Unit,
    onOpenSystemLogs: () -> Unit = {},
    showHelpPanel: Boolean,
    onDismissHelpPanel: () -> Unit,
    onOpenHelpPanel: () -> Unit = {},
    showQueuePanel: Boolean,
    onDismissQueuePanel: () -> Unit,
    showSettingsModal: Boolean,
    onDismissSettingsModal: () -> Unit,
    onOpenSettingsModal: () -> Unit,
    showPeqModal: Boolean,
    onDismissPeqModal: () -> Unit,
    onOpenPeqModal: () -> Unit,
    showFolderPickerDialog: Boolean,
    onDismissFolderPickerDialog: () -> Unit,
    onOpenFolderPickerDialog: () -> Unit,
    showCreatePlaylistDialog: Boolean,
    onDismissCreatePlaylistDialog: () -> Unit,
    onOpenCreatePlaylistDialog: () -> Unit,
    playlistToRename: Playlist?,
    onDismissRenamePlaylist: () -> Unit,
    onOpenRenamePlaylist: (Playlist) -> Unit,
    showSelectPlaylistDialog: Boolean,
    onDismissSelectPlaylistDialog: () -> Unit,
    onOpenSelectPlaylistDialog: () -> Unit = {},
    trackToAddForPlaylist: File?,
    onTrackToAddForPlaylistChange: (File?) -> Unit,
    selectedTracksForPlaylist: List<File>,
    onSelectedTracksForPlaylistChange: (List<File>) -> Unit,
    trackDetailsTarget: File?,
    onDismissTrackDetails: () -> Unit,
    onOpenTrackDetails: (File) -> Unit = {},
    tracksToDelete: List<File>,
    onDismissConfirmDelete: () -> Unit,
    onRequestDeleteTracks: (List<File>) -> Unit = {},
    onConfirmDeleteTracks: (List<File>) -> Unit,
    trackActionTarget: File?,
    onDismissTrackAction: () -> Unit,
    onSelectMultiple: (File) -> Unit,
    showWarmupConfirmModal: Boolean,
    onDismissWarmupConfirm: () -> Unit,
    onConfirmWarmup: (String) -> Unit,
    showWarmupHud: Boolean,
    warmupDacName: String,
    onWarmupFinished: () -> Unit,
    settingsInitialSubPage: SettingsSubPage,
    showPermissionDialog: Boolean,
    onDismissPermissionDialog: () -> Unit,
    onOpenPermissionDialog: () -> Unit,
    isNotificationGranted: Boolean,
    isStorageGranted: Boolean,
    isAllFilesAccessGranted: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    onRequestNotification: () -> Unit,
    onRequestStorage: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    updateCheckState: UpdateCheckState,
    downloadState: DownloadState,
    onTriggerCheckUpdate: () -> Unit,
    onTriggerDownloadAndInstall: (ReleaseInfo) -> Unit,
    peqConfig: PeqConfiguration,
    onPeqConfigChange: (PeqConfiguration) -> Unit,
    userPeqPresets: List<UserPeqPreset>,
    onUserPeqPresetsChange: (List<UserPeqPreset>) -> Unit,
    onExportJsonFileRequest: (String, String) -> Unit,
    onImportJsonFileRequest: () -> Unit,
    bgMode: String,
    onBgModeChange: (String) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    hapticEnabled: Boolean,
    onHapticChange: (Boolean) -> Unit,
    keepAwake: Boolean,
    onKeepAwakeChange: (Boolean) -> Unit,
    defaultScreenStr: String,
    onDefaultScreenChange: (String) -> Unit,
    accentMode: String,
    onAccentModeChange: (String) -> Unit,
    accentFixedColorStr: String,
    onAccentFixedColorChange: (String) -> Unit,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    usbBufferProfile: Int,
    onUsbBufferProfileChange: (Int) -> Unit,
    surfaceStyle: String,
    onSurfaceStyleChange: (String) -> Unit,
    modalBlurEnabled: Boolean,
    onModalBlurEnabledChange: (Boolean) -> Unit,
    makeFolderAsPlaylist: Boolean,
    onMakeFolderAsPlaylistChange: (Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // 1. Hardware Warmup Dialogs
    WarmupConfirmDialog(
        visible = showWarmupConfirmModal,
        dacName = warmupDacName,
        onDismissRequest = onDismissWarmupConfirm,
        onConfirm = {
            onDismissWarmupConfirm()
            onConfirmWarmup(warmupDacName)
        }
    )

    if (showWarmupHud) {
        WarmupHudDialog(
            dacName = warmupDacName,
            onWarmupFinished = onWarmupFinished
        )
    }

    // 2. Modal Sheets Host (Logs, Help, Update, Permissions, Queue, Track Options)
    AppModalSheetsHost(
        context = context,
        viewState = viewState,
        audioEngine = audioEngine,
        usbManager = usbManager,
        isDacConnected = isDacConnected,
        isPlaying = playbackState.isPlaying,
        telemetry = telemetry,
        isHardwareVolumeActive = dacVolumeState.isHardwareVolumeActive,
        isPeqEnabled = peqConfig.enabled,
        showSystemLogs = showSystemLogs,
        onDismissSystemLogs = onDismissSystemLogs,
        showHelpPanel = showHelpPanel,
        onDismissHelpPanel = onDismissHelpPanel,
        showUpdatePanel = welcomeModalState.showUpdatePanel,
        onDismissUpdatePanel = welcomeModalState.onDismissUpdatePanel,
        showDonationModal = welcomeModalState.showDonationModal,
        onDismissDonationModal = welcomeModalState.onDismissDonationModal,
        showDiscordModal = welcomeModalState.showDiscordModal,
        onDismissDiscordModal = welcomeModalState.onDismissDiscordModal,
        showPermissionDialog = showPermissionDialog,
        onDismissPermissionDialog = onDismissPermissionDialog,
        isNotificationGranted = isNotificationGranted,
        isStorageGranted = isStorageGranted,
        isAllFilesAccessGranted = isAllFilesAccessGranted,
        isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
        onRequestNotification = onRequestNotification,
        onRequestStorage = onRequestStorage,
        onRequestAllFilesAccess = onRequestAllFilesAccess,
        onRequestBatteryOptimization = onRequestBatteryOptimization,
        onCompletePermissions = modalActionHandler::onCompletePermissions,
        updateCheckState = updateCheckState,
        downloadState = downloadState,
        onTriggerCheckUpdate = onTriggerCheckUpdate,
        onTriggerDownloadAndInstall = onTriggerDownloadAndInstall,
        showQueuePanel = showQueuePanel,
        onDismissQueuePanel = onDismissQueuePanel,
        currentTrack = playbackState.currentTrack,
        priorityQueue = playbackState.priorityQueue,
        upcomingTracks = upcomingTracks,
        scannedItemsMap = libraryState.scannedItemsMap,
        onRemoveFromQueue = modalActionHandler::onRemoveFromQueue,
        onClearQueue = modalActionHandler::onClearQueue,
        onSelectQueueTrack = modalActionHandler::onSelectQueueTrack,
        trackActionTarget = trackActionTarget,
        onDismissTrackOptions = onDismissTrackAction,
        playlistSet = libraryState.playlistSet,
        onPlayTrackAction = modalActionHandler::onPlayTrackAction,
        onPlayNextInQueue = modalActionHandler::onPlayNextInQueue,
        onAddToQueue = modalActionHandler::onAddToQueue,
        onOpenAddToPlaylist = { file ->
            onDismissTrackAction()
            onTrackToAddForPlaylistChange(file)
            onSelectedTracksForPlaylistChange(emptyList())
            onOpenSelectPlaylistDialog()
        },
        onSelectMultiple = onSelectMultiple,
        onShowDetails = { file ->
            onDismissTrackAction()
            onOpenTrackDetails(file)
        },
        onDeleteTrack = { file ->
            onDismissTrackAction()
            onRequestDeleteTracks(listOf(file))
        },
        trackDetailsTarget = trackDetailsTarget,
        onDismissTrackDetails = onDismissTrackDetails,
        tracksToDelete = tracksToDelete,
        onDismissConfirmDelete = onDismissConfirmDelete,
        onConfirmDeleteTracks = onConfirmDeleteTracks
    )

    // 3. Playlist CRUD Dialogs
    CreatePlaylistDialog(
        visible = showCreatePlaylistDialog,
        onDismissRequest = onDismissCreatePlaylistDialog,
        onConfirmCreate = { name ->
            onDismissCreatePlaylistDialog()
            coroutineScope.launch(Dispatchers.IO) {
                val (created, updated) = PlaylistStorageHelper.createPlaylist(context, name)
                withContext(Dispatchers.Main) {
                    libraryState.allPlaylists = updated
                    libraryState.selectedPlaylistId = created.id
                    Toast.makeText(context, "Playlist '${created.name}' created", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    RenamePlaylistDialog(
        visible = playlistToRename != null,
        currentName = playlistToRename?.name ?: "",
        onDismissRequest = onDismissRenamePlaylist,
        onConfirmRename = { newName ->
            val p = playlistToRename
            onDismissRenamePlaylist()
            if (p != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    val updated = PlaylistStorageHelper.renamePlaylist(context, p.id, newName)
                    if (updated != null) {
                        withContext(Dispatchers.Main) {
                            libraryState.allPlaylists = updated
                            Toast.makeText(context, "Playlist renamed to '$newName'", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    )

    SelectPlaylistDialog(
        visible = showSelectPlaylistDialog,
        targetTrack = trackToAddForPlaylist,
        targetTracks = selectedTracksForPlaylist,
        playlists = libraryState.allPlaylists,
        onDismissRequest = onDismissSelectPlaylistDialog,
        onSelectPlaylist = { playlist ->
            onDismissSelectPlaylistDialog()
            val filesToAdd = if (selectedTracksForPlaylist.isNotEmpty()) {
                selectedTracksForPlaylist
            } else if (trackToAddForPlaylist != null) {
                listOf(trackToAddForPlaylist)
            } else emptyList()

            if (filesToAdd.isNotEmpty()) {
                coroutineScope.launch(Dispatchers.IO) {
                    var addedCount = 0
                    filesToAdd.forEach { file ->
                        if (!playlist.trackPaths.contains(file.absolutePath)) {
                            modalActionHandler.onAddToPlaylist(playlist.id, file.absolutePath)
                            addedCount++
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "$addedCount track${if (addedCount == 1) "" else "s"} added to '${playlist.name}'", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            onTrackToAddForPlaylistChange(null)
            onSelectedTracksForPlaylistChange(emptyList())
        },
        onCreateNewPlaylist = {
            onDismissSelectPlaylistDialog()
            onOpenCreatePlaylistDialog()
        }
    )

    // 4. Settings View inside CyberBottomSheet
    CyberBottomSheet(
        visible = showSettingsModal,
        onDismissRequest = onDismissSettingsModal,
        maxHeightFraction = 0.88f,
        maxWidth = 440.dp
    ) {
        SettingsView(
            prefs = sharedPref,
            bgMode = bgMode,
            onBgModeChange = onBgModeChange,
            fontScale = fontScale,
            onFontScaleChange = onFontScaleChange,
            hapticEnabled = hapticEnabled,
            onHapticChange = onHapticChange,
            keepAwake = keepAwake,
            onKeepAwakeChange = onKeepAwakeChange,
            defaultScreen = defaultScreenStr,
            onDefaultScreenChange = onDefaultScreenChange,
            accentMode = accentMode,
            onAccentModeChange = onAccentModeChange,
            accentFixedColorStr = accentFixedColorStr,
            onAccentFixedColorChange = onAccentFixedColorChange,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            isNotificationGranted = isNotificationGranted,
            isStorageGranted = isStorageGranted,
            isAllFilesAccessGranted = isAllFilesAccessGranted,
            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
            visible = showSettingsModal,
            onRequestNotification = onRequestNotification,
            onRequestStorage = onRequestStorage,
            onRequestAllFilesAccess = onRequestAllFilesAccess,
            onRequestBatteryOptimization = onRequestBatteryOptimization,
            onOpenPermissionDialog = {
                onDismissSettingsModal()
                onOpenPermissionDialog()
            },
            onOpenHelp = {
                onDismissSettingsModal()
                onOpenHelpPanel()
            },
            updateCheckState = updateCheckState,
            onOpenUpdateDialog = {
                onDismissSettingsModal()
                welcomeModalState.showUpdatePanel = true
            },
            onOpenLogs = {
                onDismissSettingsModal()
                onOpenSystemLogs()
            },
            onOpenDonationModal = {
                onDismissSettingsModal()
                welcomeModalState.showDonationModal = true
            },
            usbBufferProfile = usbBufferProfile,
            onUsbBufferProfileChange = onUsbBufferProfileChange,
            isPeqActive = peqConfig.enabled,
            onOpenPeq = {
                onDismissSettingsModal()
                onOpenPeqModal()
            },
            initialSubPage = settingsInitialSubPage,
            surfaceStyle = surfaceStyle,
            onSurfaceStyleChange = onSurfaceStyleChange,
            modalBlurEnabled = modalBlurEnabled,
            onModalBlurEnabledChange = onModalBlurEnabledChange,
            makeFolderAsPlaylist = makeFolderAsPlaylist,
            onMakeFolderAsPlaylistChange = onMakeFolderAsPlaylistChange,
            onBackFromRoot = onDismissSettingsModal,
            modifier = Modifier.fillMaxSize()
        )
    }

    val pendingPeqEnableConfigState = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<PeqConfiguration?>(null)
    }

    // 5. Parametric Equalizer Modal Sheet
    ParametricEqModalSheet(
        visible = showPeqModal,
        config = peqConfig,
        userPresets = userPeqPresets,
        onConfigChange = { newConfig ->
            if (shouldPromptPeqDspSwitch(
                    wasPeqEnabled = peqConfig.enabled,
                    willEnablePeq = newConfig.enabled,
                    isHardwareVolumeEnabled = dacVolumeState.isHardwareVolumeEnabled
                )
            ) {
                // Ask explicit Yes/No user consent to switch from Hybrid Hardware UAC -> Universal 64-Bit DSP
                pendingPeqEnableConfigState.value = newConfig
            } else {
                onPeqConfigChange(newConfig)
                PeqPreferencesHelper.save(sharedPref, newConfig)
                PeqPreferencesHelper.applyToEngine(audioEngine, newConfig)
            }
        },
        onSaveUserPreset = { name, overwriteId ->
            val (updatedList, saved) = PeqPreferencesHelper.saveOrUpdateUserPreset(
                prefs = sharedPref,
                name = name,
                config = peqConfig,
                overwritePresetId = overwriteId
            )
            onUserPeqPresetsChange(updatedList)
            val updatedConfig = peqConfig.copy(selectedPresetId = saved.id)
            onPeqConfigChange(updatedConfig)
            PeqPreferencesHelper.save(sharedPref, updatedConfig)
            Toast.makeText(context, "Preset '${saved.name}' saved", Toast.LENGTH_SHORT).show()
        },
        onDeleteUserPreset = { presetId ->
            val deletedName = userPeqPresets.find { it.id == presetId }?.name ?: "Preset"
            val updatedList = PeqPreferencesHelper.deleteUserPreset(sharedPref, presetId)
            onUserPeqPresetsChange(updatedList)
            if (peqConfig.selectedPresetId == presetId) {
                val updatedConfig = peqConfig.copy(selectedPresetId = "custom")
                onPeqConfigChange(updatedConfig)
                PeqPreferencesHelper.save(sharedPref, updatedConfig)
            }
            Toast.makeText(context, "Preset '$deletedName' deleted", Toast.LENGTH_SHORT).show()
        },
        onImportPresetsText = { rawText ->
            val (updatedList, newlyImported) = PeqPreferencesHelper.importUserPresets(sharedPref, rawText)
            if (newlyImported.isNotEmpty()) {
                onUserPeqPresetsChange(updatedList)
                val active = newlyImported.first()
                val updatedConfig = peqConfig.copy(
                    enabled = true,
                    selectedPresetId = active.id,
                    preampDb = active.preampDb,
                    autoPreampEnabled = active.autoPreampEnabled,
                    bands = active.bands
                )
                if (shouldPromptPeqDspSwitch(
                        wasPeqEnabled = peqConfig.enabled,
                        willEnablePeq = true,
                        isHardwareVolumeEnabled = dacVolumeState.isHardwareVolumeEnabled
                    )
                ) {
                    val savedOffConfig = updatedConfig.copy(enabled = false)
                    onPeqConfigChange(savedOffConfig)
                    PeqPreferencesHelper.save(sharedPref, savedOffConfig)
                    pendingPeqEnableConfigState.value = updatedConfig
                } else {
                    onPeqConfigChange(updatedConfig)
                    PeqPreferencesHelper.save(sharedPref, updatedConfig)
                    PeqPreferencesHelper.applyToEngine(audioEngine, updatedConfig)
                }
                Toast.makeText(context, "Imported ${newlyImported.size} preset(s) ('${active.name}')", Toast.LENGTH_SHORT).show()
                newlyImported.size
            } else {
                Toast.makeText(context, "Invalid PEQ preset code format", Toast.LENGTH_SHORT).show()
                0
            }
        },
        onExportJsonFileRequest = onExportJsonFileRequest,
        onImportJsonFileRequest = onImportJsonFileRequest,
        onDismissRequest = onDismissPeqModal
    )

    // 6a. PEQ Activation Consent Dialog (Yes / No to switch to Universal 64-Bit DSP)
    PeqDspSwitchNoticeDialog(
        visible = pendingPeqEnableConfigState.value != null && dacVolumeState.isHardwareVolumeEnabled,
        title = "Switch to Universal 64-Bit DSP?",
        subtitle = "Parametric EQ Activation Consent",
        message = "Enabling Parametric Equalizer (PEQ) will switch the audio output pipeline to Universal 64-Bit DSP mode so 10-band biquad filters can be processed with 64-bit floating-point precision.\n\nDo you want to switch to Universal 64-Bit DSP and enable PEQ?",
        confirmText = "Yes",
        cancelText = "No",
        onConfirm = {
            val targetConfig = pendingPeqEnableConfigState.value
            pendingPeqEnableConfigState.value = null
            if (targetConfig != null) {
                dacVolumeState.applyVolumeModeAtomic(false)
                onPeqConfigChange(targetConfig)
                PeqPreferencesHelper.save(sharedPref, targetConfig)
                PeqPreferencesHelper.applyToEngine(audioEngine, targetConfig)
            }
        },
        onDismissRequest = {
            pendingPeqEnableConfigState.value = null
        }
    )

    // 6b. PEQ DSP Info Notice Dialog
    PeqDspSwitchNoticeDialog(
        visible = dacVolumeState.showPeqDspNoticeDialog && pendingPeqEnableConfigState.value == null,
        message = dacVolumeState.peqDspNoticeMessage,
        onDismissRequest = { dacVolumeState.showPeqDspNoticeDialog = false }
    )

    // 6c. USB DAC Required Notice Dialog (shown when user taps a track or Play while DAC is DISCONNECTED)
    androidx.compose.runtime.LaunchedEffect(isDacConnected) {
        if (isDacConnected && playbackState.showDacRequiredNoticeDialog) {
            playbackState.showDacRequiredNoticeDialog = false
        }
    }

    PeqDspSwitchNoticeDialog(
        visible = playbackState.showDacRequiredNoticeDialog && !isDacConnected,
        title = "USB DAC Required for Playback",
        subtitle = "DIRECT USB HARDWARE AUDIO • DAC DISCONNECTED",
        message = "tsrossa is a dedicated Bit-Perfect USB DAC audio player that streams directly to external USB audio hardware (bypassing Android AudioFlinger and internal phone speakers).\n\nPlease connect a compatible USB DAC (Dongle or Desktop DAC) to your device to play music.",
        onDismissRequest = { playbackState.showDacRequiredNoticeDialog = false }
    )

    // 7. Folder Picker Dialog
    FolderPickerDialog(
        visible = showFolderPickerDialog,
        currentPath = libraryState.musicRootDirectory,
        onDismissRequest = onDismissFolderPickerDialog,
        onSelectDirectory = { dir ->
            val canonDir = MusicScannerHelper.canonicalizeFile(dir)
            libraryState.isMusicDirectoryConfigured = true
            libraryState.musicRootDirectory = canonDir
            val existingTree = sharedPref.getString("music_root_tree_uri", null)
            val treePath = sharedPref.getString("music_root_tree_path", null)
            val editor = sharedPref.edit()
                .putString("music_root_directory", canonDir.absolutePath)
                .putBoolean("music_directory_configured", true)

            if (existingTree != null && treePath != null && treePath != canonDir.absolutePath) {
                editor.remove("music_root_tree_uri")
                editor.remove("music_root_tree_path")
            }
            editor.apply()
            onDismissFolderPickerDialog()
            libraryState.triggerFullRescan()
        }
    )
}
