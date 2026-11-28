package com.yuka.musicplayer.ui.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.yuka.musicplayer.BuildConfig
import com.yuka.musicplayer.audio.AudioEngine
import com.yuka.musicplayer.audio.AudioPlayerManager
import com.yuka.musicplayer.audio.PeqConfiguration
import com.yuka.musicplayer.audio.PeqPreferencesHelper
import com.yuka.musicplayer.audio.UserPeqPreset
import com.yuka.musicplayer.audio.compressor.CompressorConfig
import com.yuka.musicplayer.audio.compressor.CompressorPreferencesHelper
import com.yuka.musicplayer.audio.compressor.SavedCustomCompressorProfile
import com.yuka.musicplayer.audio.rememberDacVolumeState
import com.yuka.musicplayer.core.prefs.AppPreferences
import com.yuka.musicplayer.model.LibrarySortMode
import com.yuka.musicplayer.model.PlaybackSource
import com.yuka.musicplayer.model.Playlist
import com.yuka.musicplayer.model.ViewState
import com.yuka.musicplayer.player.rememberLibraryStateHolder
import com.yuka.musicplayer.player.rememberPlaybackStateHolder
import com.yuka.musicplayer.ui.components.AppHeader
import com.yuka.musicplayer.ui.components.BottomNavigationBar
import com.yuka.musicplayer.ui.components.MiniPlayerView
import com.yuka.musicplayer.ui.dialogs.AppModalActionHandler
import com.yuka.musicplayer.ui.dialogs.QueuePanel
import com.yuka.musicplayer.ui.dialogs.WarmupConfirmDialog
import com.yuka.musicplayer.ui.diagnostics.rememberDacTelemetry
import com.yuka.musicplayer.ui.theme.KewMobileTheme
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.LocalBgMode
import com.yuka.musicplayer.ui.theme.LocalIsDarkTheme
import com.yuka.musicplayer.ui.theme.LocalModalBlurCount
import com.yuka.musicplayer.ui.theme.LocalModalBlurEnabled
import com.yuka.musicplayer.ui.theme.LocalSurfaceStyle
import com.yuka.musicplayer.ui.theme.ModalBlurContainer
import com.yuka.musicplayer.ui.theme.m3SurfaceBase
import com.yuka.musicplayer.ui.views.AppBackgroundContainer
import com.yuka.musicplayer.ui.views.AppScreenContent
import com.yuka.musicplayer.ui.views.TrackView
import com.yuka.musicplayer.ui.views.resolveActiveNavViewState
import com.yuka.musicplayer.ui.views.settings.SettingsSubPage
import com.yuka.musicplayer.update.AppUpdateManager
import com.yuka.musicplayer.update.DownloadState
import com.yuka.musicplayer.update.ReleaseInfo
import com.yuka.musicplayer.update.UpdateCheckState
import com.yuka.musicplayer.util.ColorContrastHelper
import com.yuka.musicplayer.util.MusicScannerHelper
import com.yuka.musicplayer.util.PlaylistStorageHelper
import com.yuka.musicplayer.util.TrackDeleteHelper
import com.yuka.musicplayer.util.findComponentActivity
import com.yuka.musicplayer.util.rememberAppPermissionState
import com.yuka.musicplayer.util.rememberScreenPinningState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.absoluteValue

@Composable
fun rememberIsKeyboardOpen(): Boolean {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    return imeBottom > 0
}

/**
 * Resolves the active backdrop bitmap for Album Artwork canvas without leaking
 * the cold-start library preview onto actively playing tracks that lack embedded art.
 */
internal fun <T> resolveEffectiveBackdropBitmap(
    activeTrackPath: String,
    currentCoverBitmap: T?,
    lastValidCoverBitmap: T?,
    libraryPreviewBitmap: T?
): T? {
    return if (activeTrackPath.isEmpty()) {
        libraryPreviewBitmap
    } else {
        currentCoverBitmap ?: lastValidCoverBitmap
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KewApp(audioEngine: AudioEngine) {
    val context = LocalContext.current
    val sharedPref = remember { AppPreferences.get(context) }
    var fontScale by remember { mutableStateOf(sharedPref.getFloat("font_scale", 1.0f)) }
    var hapticEnabled by remember { mutableStateOf(sharedPref.getBoolean("haptic_enabled", true)) }
    var keepAwake by remember { mutableStateOf(sharedPref.getBoolean("keep_awake", false)) }
    var defaultScreenStr by remember {
        val saved = sharedPref.getString("default_screen", "LIBRARY") ?: "LIBRARY"
        val normalized = if (saved == "TRACK") "LIBRARY" else saved
        if (saved == "TRACK") {
            sharedPref.edit().putString("default_screen", "LIBRARY").apply()
        }
        mutableStateOf(normalized)
    }
    var bgMode by remember {
        val savedBg = sharedPref.getString("bg_mode", "BLACK") ?: "BLACK"
        mutableStateOf(if (savedBg == "BLUR") "ARTWORK" else savedBg)
    }
    var themeMode by remember { mutableStateOf(sharedPref.getString("theme_mode", "DARK") ?: "DARK") }
    var accentMode by remember { mutableStateOf(sharedPref.getString("accent_mode", "DYNAMIC") ?: "DYNAMIC") }
    var accentFixedColorStr by remember { mutableStateOf(sharedPref.getString("accent_fixed_color", "#00FF00") ?: "#00FF00") }
    var usbBufferProfile by remember { mutableStateOf(sharedPref.getInt("usb_buffer_profile", 1)) }
    var peqConfig by remember {
        mutableStateOf(PeqPreferencesHelper.load(sharedPref))
    }
    var userPeqPresets by remember {
        mutableStateOf(PeqPreferencesHelper.loadUserPresets(sharedPref))
    }
    var compressorConfig by remember {
        mutableStateOf(CompressorPreferencesHelper.load(sharedPref))
    }
    var savedCustomCompressorProfile by remember {
        mutableStateOf(CompressorPreferencesHelper.loadSavedCustomProfile(sharedPref))
    }

    LaunchedEffect(Unit) {
        AudioEngine.applyBufferProfile(usbBufferProfile)
        PeqPreferencesHelper.applyToEngine(audioEngine, peqConfig)
        CompressorPreferencesHelper.applyToEngine(audioEngine, compressorConfig)
    }

    val permState = rememberAppPermissionState(context)
    val isNotificationGranted = permState.isNotificationGranted
    val isStorageGranted = permState.isStorageGranted
    val isAllFilesAccessGranted = permState.isAllFilesAccessGranted
    val isBatteryOptimizationIgnored = permState.isBatteryOptimizationIgnored
    val requestNotificationPermission = permState.requestNotificationPermission
    val requestStoragePermission = permState.requestStoragePermission
    val requestAllFilesAccessPermission = permState.requestAllFilesAccessPermission
    val requestBatteryOptimizationPermission = permState.requestBatteryOptimizationPermission

    val coroutineScope = rememberCoroutineScope()
    val hasCompletedOnboarding = remember {
        sharedPref.getBoolean("completed_permission_onboarding", false)
    }
    var showPermissionDialog by remember {
        mutableStateOf(!isStorageGranted || (!isNotificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasCompletedOnboarding))
    }

    val initialViewState = remember {
        val parsed = try { ViewState.valueOf(defaultScreenStr) } catch (_: Exception) { ViewState.LIBRARY }
        if (parsed == ViewState.PLAYLIST) ViewState.PLAYLIST else ViewState.LIBRARY
    }
    val mainPagerState = rememberPagerState(
        initialPage = if (initialViewState == ViewState.PLAYLIST) 1 else 0,
        pageCount = { 2 }
    )
    val isPagerDraggedState = mainPagerState.interactionSource.collectIsDraggedAsState()
    val pendingNavTargetPage = remember { mutableStateOf<Int?>(null) }
    var navScrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var activeNavToken by remember { mutableIntStateOf(0) }

    // If user starts dragging the pager mid-navbar-animation, immediately yield control to the gesture
    LaunchedEffect(mainPagerState) {
        snapshotFlow { isPagerDraggedState.value }.collect { dragged ->
            if (dragged && pendingNavTargetPage.value != null) {
                activeNavToken += 1
                navScrollJob?.cancel()
                navScrollJob = null
                pendingNavTargetPage.value = null
            }
        }
    }

    // Single Source of Truth (SSOT): active main tab derived atomically from mainPagerState
    val viewState by remember(mainPagerState) {
        derivedStateOf {
            resolveActiveNavViewState(
                pendingNavTargetPage = pendingNavTargetPage.value,
                isPagerDragged = isPagerDraggedState.value,
                currentPage = mainPagerState.currentPage,
                currentPageOffsetFraction = mainPagerState.currentPageOffsetFraction
            )
        }
    }

    val navigateToTab: (ViewState) -> Unit = { targetView ->
        if (targetView == ViewState.LIBRARY || targetView == ViewState.PLAYLIST) {
            val targetPage = if (targetView == ViewState.PLAYLIST) 1 else 0
            val myToken = activeNavToken + 1
            activeNavToken = myToken
            navScrollJob?.cancel()
            pendingNavTargetPage.value = targetPage
            navScrollJob = coroutineScope.launch {
                try {
                    if (mainPagerState.currentPage != targetPage ||
                        mainPagerState.currentPageOffsetFraction.absoluteValue > 0.0005f
                    ) {
                        mainPagerState.animateScrollToPage(
                            page = targetPage,
                            pageOffsetFraction = 0f,
                            animationSpec = tween(
                                durationMillis = 220,
                                easing = FastOutSlowInEasing
                            )
                        )
                    } else if (mainPagerState.isScrollInProgress) {
                        mainPagerState.scrollToPage(targetPage, 0f)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    runCatching { mainPagerState.scrollToPage(targetPage, 0f) }
                } finally {
                    if (activeNavToken == myToken) {
                        pendingNavTargetPage.value = null
                        navScrollJob = null
                    }
                }
            }
        }
    }
    var surfaceStyle by remember {
        mutableStateOf(sharedPref.getString("surface_style", "BLUR_OPACITY") ?: "BLUR_OPACITY")
    }
    var modalBlurEnabled by remember {
        mutableStateOf(sharedPref.getBoolean("modal_blur_enabled", false))
    }

    var showSystemLogs by remember { mutableStateOf(false) }
    var showHelpPanel by remember { mutableStateOf(false) }
    var showQueuePanel by remember { mutableStateOf(false) }
    var queueRevealFraction by remember { mutableFloatStateOf(0f) }
    var nowPlayingCollapseDragFraction by remember { mutableFloatStateOf(0f) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var showWarmupConfirmModal by remember { mutableStateOf(false) }
    var updateCheckState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var trackActionTarget by remember { mutableStateOf<File?>(null) }
    var initialMultiSelectFile by remember { mutableStateOf<File?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var showSelectPlaylistDialog by remember { mutableStateOf(false) }
    var trackToAddForPlaylist by remember { mutableStateOf<File?>(null) }
    var trackDetailsTarget by remember { mutableStateOf<File?>(null) }
    var tracksToDelete by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedTracksForPlaylist by remember { mutableStateOf<List<File>>(emptyList()) }
    var settingsInitialSubPage by remember { mutableStateOf(SettingsSubPage.ROOT) }
    var showPeqModal by remember { mutableStateOf(false) }
    var showSignalPathModal by remember { mutableStateOf(false) }
    var showVolumeModal by remember { mutableStateOf(false) }
    var pendingImportPeqEnableConfig by remember { mutableStateOf<PeqConfiguration?>(null) }
    var pendingDeleteFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var handleTracksDeletedRef by remember { mutableStateOf<((List<File>) -> Unit)?>(null) }

    val deleteIntentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val confirmed = pendingDeleteFiles
            pendingDeleteFiles = emptyList()
            handleTracksDeletedRef?.invoke(confirmed)
        } else {
            pendingDeleteFiles = emptyList()
            Toast.makeText(context, "Track deletion cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    var pendingPeqExportJson by remember { mutableStateOf<String?>(null) }
    val exportPeqJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && pendingPeqExportJson != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(pendingPeqExportJson!!.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "PEQ preset file saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save preset file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        pendingPeqExportJson = null
    }

    val screenPinning = rememberScreenPinningState()
    val isTaskLocked = screenPinning.isTaskLocked
    val toggleLockTask = screenPinning.toggleLockTask
    val activity = remember(context) { context.findComponentActivity() }

    var showWarmupHud by remember { mutableStateOf(false) }
    var warmupDacName by remember { mutableStateOf("USB DAC") }
    var isWarmingUpState by remember { mutableStateOf(false) }

    var isDacConnected by remember { mutableStateOf(audioEngine.isDacConnected() || AudioPlayerManager.isDacConnected) }

    val triggerWarmupProcess: (String) -> Unit = { targetName ->
        val isStreaming = audioEngine.isPlaying()
        val dacAttached = audioEngine.isDacConnected() || AudioPlayerManager.isDacConnected
        if (!dacAttached) {
            Toast.makeText(context, "USB DAC is not connected", Toast.LENGTH_SHORT).show()
        } else if (isStreaming) {
            Toast.makeText(context, "DAC is currently playing audio", Toast.LENGTH_SHORT).show()
        } else if (isWarmingUpState || audioEngine.isWarmingUp()) {
            audioEngine.cancelWarmup()
            isWarmingUpState = false
            showWarmupHud = false
            Toast.makeText(context, "Warmup cancelled", Toast.LENGTH_SHORT).show()
        } else {
            warmupDacName = targetName
            showWarmupHud = true
            isWarmingUpState = true
            Thread({
                audioEngine.triggerWarmup(1600)
            }, "TsrossaWarmupBg").start()
        }
    }

    LaunchedEffect(Unit) {
        val dacAttached = audioEngine.isDacConnected() || AudioPlayerManager.isDacConnected
        if (dacAttached) {
            isDacConnected = true
        } else {
            isDacConnected = false
            AudioPlayerManager.usbAudioController.scanAndRequestPermission()
        }
    }

    var showFolderPickerDialog by remember { mutableStateOf(false) }

    // Library State Holder
    val libraryState = rememberLibraryStateHolder(
        context = context,
        sharedPref = sharedPref,
        isStorageGranted = isStorageGranted,
        isAllFilesAccessGranted = isAllFilesAccessGranted,
        onShowFolderPicker = { showFolderPickerDialog = true }
    )

    // DAC Volume State Holder
    val dacVolume = rememberDacVolumeState(
        audioEngine = audioEngine,
        sharedPref = sharedPref,
        isDacConnected = isDacConnected,
        isPeqEnabled = peqConfig.enabled,
        isCompressorEnabled = compressorConfig.enabled
    )

    // Playback State Holder
    val playbackState = rememberPlaybackStateHolder(
        audioEngine = audioEngine,
        libraryState = libraryState,
        dacVolumeState = dacVolume,
        sharedPref = sharedPref,
        context = context
    )
    handleTracksDeletedRef = playbackState.handleTracksDeleted

    // Welcome Modals State Holder
    val welcomeModalCycle = rememberWelcomeModalCycleState(
        sharedPref = sharedPref,
        isStorageGranted = isStorageGranted,
        isMusicDirectoryConfigured = libraryState.isMusicDirectoryConfigured,
        showPermissionDialog = showPermissionDialog,
        showFolderPickerDialog = showFolderPickerDialog,
        isPlaying = playbackState.isPlaying,
        currentTrack = playbackState.currentTrack,
        updateCheckState = updateCheckState
    )

    val accentFixedColor = remember(accentFixedColorStr) {
        try { Color(AndroidColor.parseColor(accentFixedColorStr)) } catch (_: Exception) { Color(0xFF00FF00) }
    }

    val wallpaperAccent = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                dynamicDarkColorScheme(context).primary
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    val isDarkThemeActive = themeMode != "LIGHT"
    val targetAccentColor = remember(accentMode, accentFixedColor, playbackState.currentTrack?.dominantColor, wallpaperAccent, isDarkThemeActive) {
        val rawAccent = ColorContrastHelper.resolveAccent(
            accentMode = accentMode,
            trackDominantColor = playbackState.currentTrack?.dominantColor,
            userFixedColor = accentFixedColor,
            wallpaperColor = wallpaperAccent
        )
        ColorContrastHelper.ensureThemeContrast(rawAccent, isDarkTheme = isDarkThemeActive)
    }

    val currentAccentColor by animateColorAsState(
        targetValue = targetAccentColor,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "dynamicAccentCrossfade"
    )

    var searchQuery by remember { mutableStateOf("") }

    val importPeqJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val rawText = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().use { it.readText() }
                } ?: ""
                val (updatedList, newlyImported) = PeqPreferencesHelper.importUserPresets(sharedPref, rawText)
                if (newlyImported.isNotEmpty()) {
                    userPeqPresets = updatedList
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
                            isHardwareVolumeEnabled = dacVolume.isHardwareVolumeEnabled
                        )
                    ) {
                        val savedOffConfig = updatedConfig.copy(enabled = false)
                        peqConfig = savedOffConfig
                        PeqPreferencesHelper.save(sharedPref, savedOffConfig)
                        pendingImportPeqEnableConfig = updatedConfig
                    } else {
                        peqConfig = updatedConfig
                        PeqPreferencesHelper.save(sharedPref, updatedConfig)
                        PeqPreferencesHelper.applyToEngine(audioEngine, updatedConfig)
                    }
                    Toast.makeText(context, "Imported ${newlyImported.size} preset(s) ('${active.name}')", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Unrecognized PEQ preset file format", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var librarySortMode by remember { mutableStateOf(LibrarySortMode.NAME_ASC) }

    val selectLibrarySortMode: (LibrarySortMode) -> Unit = { mode ->
        librarySortMode = mode
        libraryState.scannedLibraryItems = com.yuka.musicplayer.model.sortScannedMusicItems(libraryState.scannedLibraryItems, mode)
        if (playbackState.currentPlaybackSourceStr == PlaybackSource.LIBRARY.name && playbackState.playbackLibraryFiles.isNotEmpty()) {
            playbackState.playbackLibraryFiles = com.yuka.musicplayer.model.sortLibraryFiles(playbackState.playbackLibraryFiles, mode)
            playbackState.syncPreparedNextTrack()
        }
        val msg = when (mode) {
            LibrarySortMode.DATE_DESC -> "Sorted: Date (Newest first)"
            LibrarySortMode.DATE_ASC -> "Sorted: Date (Oldest first)"
            LibrarySortMode.NAME_ASC -> "Sorted: Name (A to Z)"
            LibrarySortMode.NAME_DESC -> "Sorted: Name (Z to A)"
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    val cycleLibrarySortMode = {
        val nextMode = librarySortMode.next()
        selectLibrarySortMode(nextMode)
    }

    val triggerCheckUpdate = {
        coroutineScope.launch {
            updateCheckState = UpdateCheckState.Checking
            updateCheckState = AppUpdateManager.checkForUpdates(BuildConfig.VERSION_NAME)
        }
    }

    val triggerDownloadAndInstall: (ReleaseInfo) -> Unit = { release ->
        coroutineScope.launch {
            downloadState = DownloadState.Downloading(0f, 0L, release.apkSize)
            val result = AppUpdateManager.downloadApk(
                context = context,
                downloadUrl = release.apkDownloadUrl,
                expectedSize = release.apkSize,
                onProgress = { progress, downloaded, total ->
                    downloadState = DownloadState.Downloading(progress, downloaded, total)
                }
            )
            result.onSuccess { apkFile ->
                downloadState = DownloadState.ReadyToInstall(apkFile)
                AppUpdateManager.installApk(context, apkFile)
            }.onFailure { err ->
                downloadState = DownloadState.Error(err.message ?: "Download failed")
            }
        }
    }

    LaunchedEffect(Unit) {
        val autoCheck = sharedPref.getBoolean("auto_check_update", true)
        if (autoCheck) {
            updateCheckState = UpdateCheckState.Checking
            updateCheckState = AppUpdateManager.checkForUpdates(BuildConfig.VERSION_NAME)
        }
    }

    LaunchedEffect(viewState, playbackState.isNowPlayingExpanded, playbackState.isPlaying, keepAwake) {
        if (keepAwake && (playbackState.isNowPlayingExpanded || viewState == ViewState.TRACK) && playbackState.isPlaying) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val usbAudioController = AudioPlayerManager.usbAudioController
    DisposableEffect(usbAudioController) {
        AudioPlayerManager.onDacReady = { dacName ->
            coroutineScope.launch(Dispatchers.Main) {
                isDacConnected = true
                dacVolume.onDacReadyVolumeSync(dacName)
                Toast.makeText(context, "USB DAC Ready: $dacName", Toast.LENGTH_SHORT).show()
            }
        }
        AudioPlayerManager.onDacDetached = {
            coroutineScope.launch(Dispatchers.Main) {
                playbackState.isPlaying = false
                isDacConnected = false
                dacVolume.onDacDetachedVolumeReset()
                showWarmupHud = false
                isWarmingUpState = false
                Toast.makeText(context, "USB DAC disconnected. Playback paused.", Toast.LENGTH_SHORT).show()
            }
        }
        usbAudioController.onDeviceAttached = {
            coroutineScope.launch(Dispatchers.Main) {
                Toast.makeText(context, "USB DAC detected. Initializing...", Toast.LENGTH_SHORT).show()
            }
        }
        onDispose {
            AudioPlayerManager.onDacReady = null
            AudioPlayerManager.onDacDetached = null
            usbAudioController.onDeviceAttached = null
        }
    }

    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager }
    val telemetry = rememberDacTelemetry(
        audioEngine = audioEngine,
        isDacConnected = isDacConnected,
        isDiagnosticsOpen = showSystemLogs,
        currentTrack = playbackState.currentTrack,
        isPlaying = playbackState.isPlaying,
        isHardwareVolumeEnabled = dacVolume.isHardwareVolumeEnabled,
        isHardwareVolumeActive = dacVolume.isHardwareVolumeActive,
        isPeqEnabled = peqConfig.enabled,
        isCompressorEnabled = compressorConfig.enabled
    )

    val currentDensity = LocalDensity.current
    val modalBlurCount = remember { mutableIntStateOf(0) }

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale * fontScale
        ),
        LocalAccentColor provides currentAccentColor,
        LocalBgMode provides bgMode,
        LocalIsDarkTheme provides (themeMode != "LIGHT"),
        LocalSurfaceStyle provides surfaceStyle,
        LocalModalBlurEnabled provides modalBlurEnabled,
        LocalModalBlurCount provides modalBlurCount
    ) {
        KewMobileTheme(isDarkTheme = themeMode != "LIGHT") {
            var isLibraryLocalModalOpen by remember { mutableStateOf(false) }

            var lastValidMainCoverBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
            val activeTrackPath = playbackState.currentTrack?.file?.absolutePath ?: ""
            var asyncPreparedBackdrop by remember(activeTrackPath) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
            var libraryPreviewBackdrop by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

            val currentCoverBitmap = remember(
                activeTrackPath,
                playbackState.currentTrack?.blurredCoverArt,
                asyncPreparedBackdrop
            ) {
                com.yuka.musicplayer.util.AlbumArtThumbnailCache.getBlurredBackdropImageBitmap(activeTrackPath)
                    ?: playbackState.currentTrack?.blurredCoverArt?.asImageBitmap()
                    ?: asyncPreparedBackdrop
            }

            LaunchedEffect(activeTrackPath, currentCoverBitmap) {
                if (activeTrackPath.isEmpty()) {
                    lastValidMainCoverBitmap = null
                } else if (currentCoverBitmap != null) {
                    lastValidMainCoverBitmap = currentCoverBitmap
                } else {
                    val prepared = com.yuka.musicplayer.util.AlbumArtThumbnailCache.prepareTrackVisuals(activeTrackPath)
                    if (prepared != null) {
                        asyncPreparedBackdrop = prepared
                        lastValidMainCoverBitmap = prepared
                    } else if (com.yuka.musicplayer.util.AlbumArtThumbnailCache.isKnownMissingArt(activeTrackPath)) {
                        lastValidMainCoverBitmap = null
                    }
                }
            }

            // When Album Artwork canvas is selected and no track has been played yet (e.g. fresh start),
            // automatically prepare a preview backdrop from the first library track with embedded artwork.
            LaunchedEffect(bgMode, activeTrackPath, libraryState.scannedLibraryItems) {
                if ((bgMode == "ARTWORK" || bgMode == "BLUR") && activeTrackPath.isEmpty() && libraryPreviewBackdrop == null) {
                    val candidates = libraryState.scannedLibraryItems.asSequence().filter { it.isSupported }.take(12).toList()
                    for (item in candidates) {
                        val path = item.filePath.ifEmpty { item.file.absolutePath }
                        val bmp = com.yuka.musicplayer.util.AlbumArtThumbnailCache.prepareTrackVisuals(path, item.dateModified)
                        if (bmp != null) {
                            libraryPreviewBackdrop = bmp
                            break
                        }
                    }
                }
            }

            val effectiveMainCoverBitmap = resolveEffectiveBackdropBitmap(
                activeTrackPath = activeTrackPath,
                currentCoverBitmap = currentCoverBitmap,
                lastValidCoverBitmap = lastValidMainCoverBitmap,
                libraryPreviewBitmap = libraryPreviewBackdrop
            )

            AppBackgroundContainer(
                bgMode = bgMode,
                coverArtBitmap = effectiveMainCoverBitmap
            ) {
                val connectedDacIdentity = remember(isDacConnected) {
                    if (isDacConnected) {
                        try {
                            val info = org.json.JSONObject(audioEngine.getDacInfo())
                            com.yuka.musicplayer.ui.diagnostics.ConnectedDacIdentity(
                                productName = info.optString("productName", ""),
                                manufacturerName = info.optString("manufacturerName", ""),
                                vid = info.optInt("vid", 0),
                                pid = info.optInt("pid", 0)
                            )
                        } catch (_: Exception) {
                            com.yuka.musicplayer.ui.diagnostics.ConnectedDacIdentity(productName = "USB DAC")
                        }
                    } else {
                        com.yuka.musicplayer.ui.diagnostics.ConnectedDacIdentity()
                    }
                }
                val connectedDacName = remember(isDacConnected, connectedDacIdentity) {
                    if (isDacConnected) {
                        connectedDacIdentity.productName.ifBlank { "USB DAC" }
                    } else {
                        ""
                    }
                }

                val isQueueVisible = showQueuePanel || queueRevealFraction > 0.01f
                val upcomingTracks = remember(
                    isQueueVisible,
                    playbackState.priorityQueue,
                    playbackState.currentTrack,
                    playbackState.sequenceAnchorTrackFile,
                    playbackState.isShuffleEnabled,
                    playbackState.shuffledList,
                    playbackState.currentShuffleIndex,
                    playbackState.repeatMode
                ) {
                    if (!isQueueVisible) {
                        emptyList()
                    } else {
                        val activeList = playbackState.getActiveTrackList(playbackState.currentPlaybackSource)
                        com.yuka.musicplayer.player.queue.QueueHelper.calculateUpcomingTracks(
                            currentTrack = playbackState.currentTrack,
                            activeList = activeList,
                            isShuffleEnabled = playbackState.isShuffleEnabled,
                            shuffledList = playbackState.shuffledList,
                            currentShuffleIndex = playbackState.currentShuffleIndex,
                            repeatMode = playbackState.repeatMode,
                            sequenceAnchorFile = playbackState.sequenceAnchorTrackFile
                        )
                    }
                }

                val modalActionHandler = remember(
                    context, coroutineScope, sharedPref, playbackState.priorityQueue,
                    libraryState.allPlaylists, libraryState.selectedPlaylistId,
                    playbackState.currentPlaybackSourceStr, playbackState.isShuffleEnabled,
                    playbackState.shuffledList
                ) {
                    AppModalActionHandler(
                        context = context,
                        coroutineScope = coroutineScope,
                        sharedPref = sharedPref,
                        getPriorityQueue = { playbackState.priorityQueue },
                        setPriorityQueue = { playbackState.priorityQueue = it },
                        getAllPlaylists = { libraryState.allPlaylists },
                        setAllPlaylists = { libraryState.allPlaylists = it },
                        getSelectedPlaylistId = { libraryState.selectedPlaylistId ?: "" },
                        setSelectedPlaylistId = { libraryState.selectedPlaylistId = it },
                        getPlaylistPaths = { libraryState.playlistPaths },
                        getCurrentPlaybackSourceStr = { playbackState.currentPlaybackSourceStr },
                        isShuffleEnabled = { playbackState.isShuffleEnabled },
                        getShuffledList = { playbackState.shuffledList },
                        setShuffledList = { playbackState.shuffledList = it },
                        syncPreparedNextTrack = playbackState.syncPreparedNextTrack,
                        playTrack = { f, auto, rec, src, pId, tSrc, seek, fromQueue ->
                            playbackState.playTrack(f, auto, rec, src, pId, tSrc, seek, fromQueue)
                        },
                        onDismissPermissionDialog = { showPermissionDialog = false },
                        onDismissQueuePanel = { showQueuePanel = false },
                        onDismissTrackOptions = { trackActionTarget = null },
                        onShowSelectPlaylistDialog = { file ->
                            trackToAddForPlaylist = file
                            showSelectPlaylistDialog = true
                        }
                    )
                }

                ModalBlurContainer(
                    modifier = Modifier.fillMaxSize(),
                    extraBlurActive = isLibraryLocalModalOpen,
                    suppressBlur = playbackState.isNowPlayingVisible,
                    maxBlurRadius = 3.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AppHeader(
                            viewState = viewState,
                            isHardwareVolumeActive = dacVolume.isHardwareVolumeActive,
                            isForceSoftwareVolume = telemetry.isForceSoftwareVolume,
                            isHardwareVolumeLockedBySystem = telemetry.isHardwareVolumeLockedBySystem,
                            onToggleLogs = { showSystemLogs = !showSystemLogs },
                            onToggleUpdate = {
                                welcomeModalCycle.showUpdatePanel = !welcomeModalCycle.showUpdatePanel
                                if (updateCheckState is UpdateCheckState.Idle) {
                                    triggerCheckUpdate()
                                }
                            },
                            hasUpdateAvailable = updateCheckState is UpdateCheckState.UpdateAvailable,
                            onDonationClick = { welcomeModalCycle.showDonationModal = true },
                            onAboutClick = {
                                settingsInitialSubPage = SettingsSubPage.ABOUT
                                showSettingsModal = true
                            },
                            onSettingsClick = {
                                settingsInitialSubPage = SettingsSubPage.ROOT
                                showSettingsModal = !showSettingsModal
                            },
                            connectedDacName = connectedDacName,
                            isDacConnected = isDacConnected,
                            onDacPillLongClick = {
                                if (isDacConnected) {
                                    showWarmupConfirmModal = true
                                } else {
                                    Toast.makeText(context, "No USB DAC connected", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onToggleLockTask = toggleLockTask,
                            isTaskLocked = isTaskLocked
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            AppScreenContent(
                                pagerState = mainPagerState,
                                modifier = Modifier.fillMaxSize(),
                                musicRootDirectory = libraryState.musicRootDirectory,
                                scannedItems = libraryState.scannedLibraryItems,
                                isScanningLibrary = libraryState.isScanningLibrary,
                                isMusicDirectoryConfigured = libraryState.isMusicDirectoryConfigured,
                                isAllFilesAccessGranted = isAllFilesAccessGranted,
                                onRequestAllFilesAccess = requestAllFilesAccessPermission,
                                onChangeDirectory = { showFolderPickerDialog = true },
                                currentTrack = playbackState.currentTrack,
                                playlistSet = libraryState.playlistSet,
                                searchQuery = searchQuery,
                                onSearchChange = { searchQuery = it },
                                librarySortMode = librarySortMode,
                                librarySortField = libraryState.librarySortField,
                                librarySortOrder = libraryState.librarySortOrder,
                                onCycleSort = cycleLibrarySortMode,
                                onSelectSort = selectLibrarySortMode,
                                onSelectSortField = { field, order ->
                                    libraryState.librarySortField = field
                                    libraryState.librarySortOrder = order
                                },
                                onShuffleLibrary = {
                                    val playable = libraryState.playableFiles
                                    if (playable.isNotEmpty()) {
                                        playbackState.isShuffleEnabled = true
                                        val randomTrack = playable.random()
                                        playbackState.currentPlaybackSourceStr = PlaybackSource.LIBRARY.name
                                        playbackState.playbackLibraryFiles = playable
                                        playbackState.playTrack(randomTrack, false, true, PlaybackSource.LIBRARY, null, null, -1.0)
                                    }
                                },
                                onBulkAddToPlaylist = { files ->
                                    selectedTracksForPlaylist = files
                                    trackToAddForPlaylist = null
                                    showSelectPlaylistDialog = true
                                },
                                onBulkDelete = { files ->
                                    tracksToDelete = files
                                },
                                onRefreshLibrary = libraryState.refreshLibrary,
                                onTrackActionTarget = { file -> trackActionTarget = file },
                                onSelectLibraryItem = { scannedItem ->
                                    playbackState.playTrack(scannedItem.file, false, true, PlaybackSource.LIBRARY, null, null, -1.0)
                                },
                                onPlayFolderTracks = { files, shuffle ->
                                    val playable = files.filter { MusicScannerHelper.isSupportedAudioFile(it) }
                                    if (playable.isNotEmpty()) {
                                        playbackState.playbackLibraryFiles = playable
                                        playbackState.currentPlaybackSourceStr = PlaybackSource.LIBRARY.name
                                        if (shuffle) {
                                            playbackState.isShuffleEnabled = true
                                            val randomTrack = playable.random()
                                            playbackState.playTrack(randomTrack, false, true, PlaybackSource.LIBRARY, null, null, -1.0)
                                        } else {
                                            playbackState.playTrack(playable.first(), false, true, PlaybackSource.LIBRARY, null, null, -1.0)
                                        }
                                    }
                                },
                                onLibraryModalOpenChanged = { isOpen -> isLibraryLocalModalOpen = isOpen },
                                initialSelectedFile = initialMultiSelectFile,
                                onConsumedInitialSelectedFile = { initialMultiSelectFile = null },
                                playlists = libraryState.effectivePlaylists,
                                selectedPlaylistId = libraryState.selectedPlaylistId ?: "",
                                activePlayingPlaylistId = playbackState.activePlayingPlaylistId,
                                onSelectPlaylistTab = { id -> libraryState.selectedPlaylistId = id },
                                onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                                onRenamePlaylistClick = { playlist -> playlistToRename = playlist },
                                onDeletePlaylistClick = { playlist ->
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val updated = PlaylistStorageHelper.deletePlaylist(context, playlist.id)
                                        if (updated != null) {
                                            withContext(Dispatchers.Main) {
                                                libraryState.allPlaylists = updated
                                                if (libraryState.selectedPlaylistId == playlist.id) {
                                                    libraryState.selectedPlaylistId = updated.firstOrNull()?.id
                                                }
                                                Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                isShuffleEnabled = playbackState.isShuffleEnabled,
                                onToggleShuffle = playbackState.toggleShuffle,
                                repeatMode = playbackState.repeatMode,
                                onCycleRepeat = playbackState.cycleRepeat,
                                priorityQueueSize = playbackState.priorityQueue.size,
                                onOpenQueue = { showQueuePanel = true },
                                onPlayPlaylistFile = { file, playlistId ->
                                    playbackState.playTrack(file, false, true, PlaybackSource.PLAYLIST, playlistId, PlaybackSource.PLAYLIST, -1.0)
                                },
                                onPlaylistFileRemoved = { playlistId, trackPath ->
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val updated = PlaylistStorageHelper.removeTrackFromPlaylist(context, playlistId, trackPath)
                                        if (updated != null) {
                                            withContext(Dispatchers.Main) {
                                                libraryState.allPlaylists = updated
                                                Toast.makeText(context, "Track removed from playlist", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onClearPlaylistAction = { playlistId ->
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val updated = PlaylistStorageHelper.clearPlaylistTracks(context, playlistId)
                                        if (updated != null) {
                                            withContext(Dispatchers.Main) {
                                                libraryState.allPlaylists = updated
                                                if (playbackState.currentPlaybackSourceStr == PlaybackSource.PLAYLIST.name && playbackState.activePlayingPlaylistId == playlistId) {
                                                    playbackState.shuffledList = emptyList()
                                                    playbackState.currentShuffleIndex = -1
                                                    audioEngine.clearNextTrack()
                                                    audioEngine.stopAudio()
                                                    playbackState.isPlaying = false
                                                }
                                                Toast.makeText(context, "Playlist cleared", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onPlayPlaylistSequential = { playlist ->
                                    val supportedMap = libraryState.supportedFileMap
                                    val tracks = com.yuka.musicplayer.player.queue.QueueHelper.resolvePlaylistTracks(
                                        playlistTrackPaths = playlist.trackPaths,
                                        fallbackTrackPaths = emptyList(),
                                        supportedFileMap = supportedMap
                                    )
                                    if (tracks.isNotEmpty()) {
                                        playbackState.isShuffleEnabled = false
                                        playbackState.playTrack(tracks.first(), false, true, PlaybackSource.PLAYLIST, playlist.id, PlaybackSource.PLAYLIST, -1.0)
                                    } else {
                                        Toast.makeText(context, "No playable tracks in this playlist", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onPlayPlaylistShuffled = { playlist ->
                                    val supportedMap = libraryState.supportedFileMap
                                    val tracks = com.yuka.musicplayer.player.queue.QueueHelper.resolvePlaylistTracks(
                                        playlistTrackPaths = playlist.trackPaths,
                                        fallbackTrackPaths = emptyList(),
                                        supportedFileMap = supportedMap
                                    )
                                    if (tracks.isNotEmpty()) {
                                        playbackState.isShuffleEnabled = true
                                        playbackState.playTrack(tracks.random(), false, true, PlaybackSource.PLAYLIST, playlist.id, PlaybackSource.PLAYLIST, -1.0)
                                    } else {
                                        Toast.makeText(context, "No playable tracks in this playlist", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onTogglePinPlaylist = { playlist ->
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val updated = PlaylistStorageHelper.togglePinPlaylist(context, playlist.id)
                                        if (updated != null) {
                                            withContext(Dispatchers.Main) {
                                                libraryState.allPlaylists = updated
                                                val msg = if (!playlist.isPinned) "Pinned '${playlist.name}'" else "Unpinned '${playlist.name}'"
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                scannedItemsMap = libraryState.scannedItemsMap,
                                isNowPlayingVisible = playbackState.isNowPlayingVisible
                            )

                            val isKeyboardOpen = rememberIsKeyboardOpen()
                            if (!isKeyboardOpen) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !playbackState.isNowPlayingVisible,
                                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.Bottom
                                    ) {
                                        if (playbackState.currentTrack != null) {
                                            MiniPlayerView(
                                                track = playbackState.currentTrack!!,
                                                isPlaying = playbackState.isPlaying,
                                                positionProvider = { playbackState.playbackPositionHolder.positionSeconds },
                                                durationSeconds = playbackState.currentTrack!!.durationSeconds,
                                                bgMode = bgMode,
                                                onTogglePlay = playbackState.sharedOnTogglePlay,
                                                onNavigateToNowPlaying = { playbackState.isNowPlayingExpanded = true },
                                                onStopAndDismiss = {
                                                    audioEngine.stopAudio()
                                                    audioEngine.clearNextTrack()
                                                    playbackState.isPlaying = false
                                                    playbackState.currentTrack = null
                                                }
                                            )
                                        }

                                        BottomNavigationBar(
                                            currentView = viewState,
                                            bgMode = bgMode,
                                            onNavClick = navigateToTab
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Full-screen Now Playing
                AnimatedVisibility(
                    visible = playbackState.isNowPlayingVisible,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        playbackState.currentTrack?.let { track ->
                            TrackView(
                                track = track,
                                positionProvider = { playbackState.playbackPositionHolder.positionSeconds },
                                isPlaying = playbackState.isPlaying,
                                currentVolume = dacVolume.currentVolume,
                                isShuffleEnabled = playbackState.isShuffleEnabled,
                                repeatMode = playbackState.repeatMode,
                                priorityQueueSize = playbackState.priorityQueue.size,
                                outputBitDepth = telemetry.outputBitDepth,
                                outputSampleRate = telemetry.outputSampleRate,
                                isDacConnected = isDacConnected,
                                isPeqActive = peqConfig.enabled,
                                isCompressorActive = compressorConfig.enabled,
                                isBitPerfectDirectPass = telemetry.isBitPerfectDirectPass,
                                isSampleRateUnverified = telemetry.isSampleRateUnverified,
                                isDeviceWedged = telemetry.isDeviceWedged,
                                isHardwareVolumeLockedBySystem = telemetry.isHardwareVolumeLockedBySystem,
                                onOpenSignalPath = { showSignalPathModal = true },
                                onOpenVolumeModal = { showVolumeModal = true },
                                onOpenPeq = { showPeqModal = true },
                                onTogglePlay = playbackState.sharedOnTogglePlay,
                                onVolumeChange = dacVolume.onVolumeChange,
                                onPlayNext = { playbackState.playNext(false) },
                                onPlayPrev = playbackState.playPrev,
                                onToggleShuffle = playbackState.toggleShuffle,
                                onCycleRepeat = playbackState.cycleRepeat,
                                onOpenQueue = { showQueuePanel = true },
                                onQueueDragFractionChanged = { frac -> queueRevealFraction = frac },
                                onCollapseDragFractionChanged = { frac -> nowPlayingCollapseDragFraction = frac },
                                onSeekTo = { targetSec ->
                                    audioEngine.seekTo(targetSec)
                                    playbackState.playbackPositionHolder.forceSet(targetSec)
                                },
                                isHardwareVolumeActive = dacVolume.isHardwareVolumeActive,
                                isHardwareVolumeSupported = dacVolume.isHardwareVolumeSupported,
                                isHardwareVolumeEnabled = dacVolume.isHardwareVolumeEnabled,
                                onToggleHardwareVolume = dacVolume.onToggleHardwareVolume,
                                onCollapse = { playbackState.isNowPlayingExpanded = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (queueRevealFraction > 0.01f && !showQueuePanel) {
                            val sheetHeightFraction = 0.84f
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = (0.55f * queueRevealFraction).coerceIn(0f, 0.55f)))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .fillMaxHeight(sheetHeightFraction)
                                        .statusBarsPadding()
                                        .graphicsLayer {
                                            translationY = (1f - queueRevealFraction) * size.height
                                            alpha = queueRevealFraction.coerceIn(0f, 1f)
                                        }
                                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                        .background(m3SurfaceBase().copy(alpha = 1f))
                                        .navigationBarsPadding()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    QueuePanel(
                                        currentTrack = playbackState.currentTrack,
                                        priorityQueue = playbackState.priorityQueue,
                                        upcomingTracks = upcomingTracks,
                                        onRemoveFromQueue = modalActionHandler::onRemoveFromQueue,
                                        onClearQueue = modalActionHandler::onClearQueue,
                                        onSelectTrack = modalActionHandler::onSelectQueueTrack,
                                        onClose = { queueRevealFraction = 0f },
                                        scannedItemsMap = libraryState.scannedItemsMap
                                    )
                                }
                            }
                        }
                    }
                }

                // All modal dialogs hosted in KewAppDialogs
                KewAppDialogs(
                    context = context,
                    audioEngine = audioEngine,
                    usbManager = usbManager,
                    sharedPref = sharedPref,
                    libraryState = libraryState,
                    playbackState = playbackState,
                    dacVolumeState = dacVolume,
                    welcomeModalState = welcomeModalCycle,
                    telemetry = telemetry,
                    viewState = viewState,
                    onViewStateChange = navigateToTab,
                    modalActionHandler = modalActionHandler,
                    upcomingTracks = upcomingTracks,
                    isDacConnected = isDacConnected,
                    dacIdentity = connectedDacIdentity,
                    showSystemLogs = showSystemLogs,
                    onDismissSystemLogs = { showSystemLogs = false },
                    onOpenSystemLogs = { showSystemLogs = true },
                    showSignalPathModal = showSignalPathModal,
                    onDismissSignalPathModal = { showSignalPathModal = false },
                    showVolumeModal = showVolumeModal,
                    onDismissVolumeModal = { showVolumeModal = false },
                    onOpenVolumeModal = { showVolumeModal = true },
                    pendingPeqEnableConfig = pendingImportPeqEnableConfig,
                    onPendingPeqEnableConfigChange = { pendingImportPeqEnableConfig = it },
                    showHelpPanel = showHelpPanel,
                    onDismissHelpPanel = { showHelpPanel = false },
                    onOpenHelpPanel = { showHelpPanel = true },
                    showQueuePanel = showQueuePanel,
                    onDismissQueuePanel = { showQueuePanel = false },
                    showSettingsModal = showSettingsModal,
                    onDismissSettingsModal = { showSettingsModal = false },
                    onOpenSettingsModal = { showSettingsModal = true },
                    showPeqModal = showPeqModal,
                    onDismissPeqModal = { showPeqModal = false },
                    onOpenPeqModal = { showPeqModal = true },
                    showFolderPickerDialog = showFolderPickerDialog,
                    onDismissFolderPickerDialog = { showFolderPickerDialog = false },
                    onOpenFolderPickerDialog = { showFolderPickerDialog = true },
                    showCreatePlaylistDialog = showCreatePlaylistDialog,
                    onDismissCreatePlaylistDialog = { showCreatePlaylistDialog = false },
                    onOpenCreatePlaylistDialog = { showCreatePlaylistDialog = true },
                    playlistToRename = playlistToRename,
                    onDismissRenamePlaylist = { playlistToRename = null },
                    onOpenRenamePlaylist = { playlistToRename = it },
                    showSelectPlaylistDialog = showSelectPlaylistDialog,
                    onDismissSelectPlaylistDialog = { showSelectPlaylistDialog = false },
                    onOpenSelectPlaylistDialog = { showSelectPlaylistDialog = true },
                    trackToAddForPlaylist = trackToAddForPlaylist,
                    onTrackToAddForPlaylistChange = { trackToAddForPlaylist = it },
                    selectedTracksForPlaylist = selectedTracksForPlaylist,
                    onSelectedTracksForPlaylistChange = { selectedTracksForPlaylist = it },
                    trackDetailsTarget = trackDetailsTarget,
                    onDismissTrackDetails = { trackDetailsTarget = null },
                    onOpenTrackDetails = { trackDetailsTarget = it },
                    tracksToDelete = tracksToDelete,
                    onDismissConfirmDelete = { tracksToDelete = emptyList() },
                    onRequestDeleteTracks = { tracksToDelete = it },
                    onConfirmDeleteTracks = { files ->
                        tracksToDelete = emptyList()
                        val result = TrackDeleteHelper.deleteTracks(context, files)
                        if (result.immediatelyDeletedFiles.isNotEmpty()) {
                            playbackState.handleTracksDeleted(result.immediatelyDeletedFiles)
                        }
                        if (result.pendingIntentSender != null) {
                            pendingDeleteFiles = result.pendingFilesForPrompt
                            try {
                                val request = IntentSenderRequest.Builder(result.pendingIntentSender).build()
                                deleteIntentSenderLauncher.launch(request)
                            } catch (e: Exception) {
                                android.util.Log.e("KewApp", "Failed to launch delete request", e)
                            }
                        } else if (result.immediatelyDeletedFiles.isEmpty()) {
                            Toast.makeText(context, "Unable to delete selected track(s)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    trackActionTarget = trackActionTarget,
                    onDismissTrackAction = { trackActionTarget = null },
                    onSelectMultiple = { file ->
                        initialMultiSelectFile = file
                        navigateToTab(ViewState.LIBRARY)
                    },
                    showWarmupConfirmModal = showWarmupConfirmModal,
                    onDismissWarmupConfirm = { showWarmupConfirmModal = false },
                    onConfirmWarmup = { name -> triggerWarmupProcess(name) },
                    showWarmupHud = showWarmupHud,
                    warmupDacName = warmupDacName,
                    onWarmupFinished = {
                        showWarmupHud = false
                        isWarmingUpState = false
                    },
                    settingsInitialSubPage = settingsInitialSubPage,
                    showPermissionDialog = showPermissionDialog,
                    onDismissPermissionDialog = {
                        if (isStorageGranted) {
                            modalActionHandler.onCompletePermissions()
                            if (!libraryState.isMusicDirectoryConfigured) {
                                showFolderPickerDialog = true
                            }
                        }
                    },
                    onOpenPermissionDialog = { showPermissionDialog = true },
                    isNotificationGranted = isNotificationGranted,
                    isStorageGranted = isStorageGranted,
                    isAllFilesAccessGranted = isAllFilesAccessGranted,
                    isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                    onRequestNotification = requestNotificationPermission,
                    onRequestStorage = requestStoragePermission,
                    onRequestAllFilesAccess = requestAllFilesAccessPermission,
                    onRequestBatteryOptimization = requestBatteryOptimizationPermission,
                    updateCheckState = updateCheckState,
                    downloadState = downloadState,
                    onTriggerCheckUpdate = { triggerCheckUpdate() },
                    onTriggerDownloadAndInstall = { release -> triggerDownloadAndInstall(release) },
                    peqConfig = peqConfig,
                    onPeqConfigChange = { peqConfig = it },
                    userPeqPresets = userPeqPresets,
                    onUserPeqPresetsChange = { userPeqPresets = it },
                    compressorConfig = compressorConfig,
                    onCompressorConfigChange = { compressorConfig = it },
                    savedCustomCompressorProfile = savedCustomCompressorProfile,
                    onSavedCustomCompressorProfileChange = { savedCustomCompressorProfile = it },
                    onExportJsonFileRequest = { jsonContent, suggestedFileName ->
                        pendingPeqExportJson = jsonContent
                        exportPeqJsonLauncher.launch(suggestedFileName)
                    },
                    onImportJsonFileRequest = {
                        importPeqJsonLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                    },
                    bgMode = bgMode,
                    onBgModeChange = {
                        bgMode = it
                        sharedPref.edit().putString("bg_mode", it).apply()
                    },
                    fontScale = fontScale,
                    onFontScaleChange = {
                        fontScale = it
                        sharedPref.edit().putFloat("font_scale", it).apply()
                    },
                    hapticEnabled = hapticEnabled,
                    onHapticChange = {
                        hapticEnabled = it
                        sharedPref.edit().putBoolean("haptic_enabled", it).apply()
                    },
                    keepAwake = keepAwake,
                    onKeepAwakeChange = {
                        keepAwake = it
                        sharedPref.edit().putBoolean("keep_awake", it).apply()
                    },
                    defaultScreenStr = defaultScreenStr,
                    onDefaultScreenChange = {
                        defaultScreenStr = it
                        sharedPref.edit().putString("default_screen", it).apply()
                    },
                    accentMode = accentMode,
                    onAccentModeChange = {
                        accentMode = it
                        sharedPref.edit().putString("accent_mode", it).apply()
                    },
                    accentFixedColorStr = accentFixedColorStr,
                    onAccentFixedColorChange = {
                        accentFixedColorStr = it
                        sharedPref.edit().putString("accent_fixed_color", it).apply()
                    },
                    themeMode = themeMode,
                    onThemeModeChange = {
                        themeMode = it
                        sharedPref.edit().putString("theme_mode", it).apply()
                    },
                    usbBufferProfile = usbBufferProfile,
                    onUsbBufferProfileChange = { newProfile ->
                        usbBufferProfile = newProfile
                        sharedPref.edit().putInt("usb_buffer_profile", newProfile).apply()
                        AudioEngine.applyBufferProfile(newProfile)
                    },
                    surfaceStyle = surfaceStyle,
                    onSurfaceStyleChange = { newStyle ->
                        surfaceStyle = newStyle
                        sharedPref.edit().putString("surface_style", newStyle).apply()
                    },
                    modalBlurEnabled = modalBlurEnabled,
                    onModalBlurEnabledChange = { enabled ->
                        modalBlurEnabled = enabled
                        sharedPref.edit().putBoolean("modal_blur_enabled", enabled).apply()
                    },
                    makeFolderAsPlaylist = libraryState.makeFolderAsPlaylist,
                    onMakeFolderAsPlaylistChange = { enabled ->
                        libraryState.makeFolderAsPlaylist = enabled
                        sharedPref.edit().putBoolean("make_folder_as_playlist", enabled).apply()
                    }
                )

                BackHandler(enabled = playbackState.isNowPlayingVisible) {
                    playbackState.isNowPlayingExpanded = false
                }
            }
        }
    }
}
