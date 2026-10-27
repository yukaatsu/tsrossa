package com.yuka.musicplayer

import com.yuka.musicplayer.model.*
import com.yuka.musicplayer.util.*
import com.yuka.musicplayer.metadata.*
import com.yuka.musicplayer.ui.components.*
import com.yuka.musicplayer.ui.diagnostics.*
import com.yuka.musicplayer.ui.dialogs.*
import com.yuka.musicplayer.ui.views.*
import com.yuka.musicplayer.ui.theme.TerminalFont

import android.widget.Toast
import android.content.Intent
import org.json.JSONArray
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.app.Activity
import android.app.ActivityManager
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory

import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.app.NotificationManager
import android.os.Environment
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.yuka.musicplayer.update.*

import android.media.AudioFocusRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.yuka.musicplayer.audio.AudioEngine
import com.yuka.musicplayer.ui.theme.KewMobileTheme
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.TerminalWhite
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.blendWithWhite
import com.yuka.musicplayer.ui.theme.SignatureDeepNavy
import com.yuka.musicplayer.ui.theme.SignatureSurfaceNavy
import com.yuka.musicplayer.ui.theme.SakuraPink
import com.yuka.musicplayer.ui.theme.VividViolet
import com.yuka.musicplayer.ui.theme.SubtleMint
import com.yuka.musicplayer.ui.dialogs.WarmupConfirmDialog
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.roundToInt

@Composable
fun rememberIsKeyboardOpen(): Boolean {
    val view = androidx.compose.ui.platform.LocalView.current
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var isKeyboardVisibleByLayout by remember { mutableStateOf(false) }

    DisposableEffect(view) {
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            val rootInsets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
            val imeVisibleByInsets = rootInsets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            isKeyboardVisibleByLayout = imeVisibleByInsets || (screenHeight > 0 && keypadHeight > screenHeight * 0.15)
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
    return isKeyboardVisibleByLayout || imeBottom > 0
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        // Prevent launcher bug where opening app creates a duplicate MainActivity instance
        if (!isTaskRoot && intent.hasCategory(Intent.CATEGORY_LAUNCHER) && intent.action != null && intent.action == Intent.ACTION_MAIN) {
            finish()
            return
        }


        com.yuka.musicplayer.audio.AudioPlayerManager.initialize(applicationContext)
        com.yuka.musicplayer.util.AlbumArtThumbnailCache.init(applicationContext)

        setContent {
            KewMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    KewApp(com.yuka.musicplayer.audio.AudioPlayerManager.audioEngine)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            android.util.Log.i("MainActivity", "onNewIntent: USB device attached event. Scanning DAC...")
            com.yuka.musicplayer.audio.AudioPlayerManager.usbAudioController.scanAndRequestPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        if (com.yuka.musicplayer.audio.AudioPlayerManager.isInitialized &&
            !com.yuka.musicplayer.audio.AudioPlayerManager.isDacConnected) {
            com.yuka.musicplayer.audio.AudioPlayerManager.usbAudioController.scanAndRequestPermission()
        }
    }

    override fun onTrimMemory(level: Int) {
        // Prevent aggressive UI cache eviction / GC pause from starving native USB audio thread
        if (level == android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            val isAudioActive = com.yuka.musicplayer.audio.AudioPlayerManager.isPlaying ||
                (com.yuka.musicplayer.audio.AudioPlayerManager.isInitialized && com.yuka.musicplayer.audio.AudioPlayerManager.audioEngine.isPlaying())
            if (isAudioActive) {
                return
            }
        }
        super.onTrimMemory(level)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            val hasActiveTrack = com.yuka.musicplayer.audio.AudioPlayerManager.currentTrack != null
            val isStillPlaying = com.yuka.musicplayer.audio.AudioPlayerManager.isPlaying ||
                (com.yuka.musicplayer.audio.AudioPlayerManager.isInitialized && com.yuka.musicplayer.audio.AudioPlayerManager.audioEngine.isPlaying())
            if (!isStillPlaying && !hasActiveTrack) {
                android.util.Log.i("MainActivity", "onDestroy: Activity is finishing and no track active. Stopping AudioForegroundService.")
                val stopIntent = Intent(this, com.yuka.musicplayer.audio.AudioForegroundService::class.java).apply {
                    action = com.yuka.musicplayer.audio.AudioForegroundService.ACTION_STOP
                }
                startService(stopIntent)
            }
        }
    }
}

@Composable
fun KewApp(audioEngine: AudioEngine) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("KewMobilePrefs", android.content.Context.MODE_PRIVATE) }
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

    // Initialize USB buffer profile in C++ engine on app launch
    LaunchedEffect(Unit) {
        com.yuka.musicplayer.audio.AudioEngine.applyBufferProfile(usbBufferProfile)
    }
    
    val permState = com.yuka.musicplayer.util.rememberAppPermissionState(context)
    val isNotificationGranted = permState.isNotificationGranted
    val isStorageGranted = permState.isStorageGranted
    val isDndGranted = permState.isDndGranted
    val isBatteryOptimizationIgnored = permState.isBatteryOptimizationIgnored
    val requestNotificationPermission = permState.requestNotificationPermission
    val requestStoragePermission = permState.requestStoragePermission
    val requestDndPermission = permState.requestDndPermission
    val requestBatteryOptimizationPermission = permState.requestBatteryOptimizationPermission

    val coroutineScope = rememberCoroutineScope()

    val hasCompletedOnboarding = remember {
        sharedPref.getBoolean("completed_permission_onboarding", false)
    }
    var showPermissionDialog by remember {
        mutableStateOf(!isStorageGranted || (!isNotificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasCompletedOnboarding))
    }

    val initialViewState = remember {
        val parsed = try { ViewState.valueOf(defaultScreenStr) } catch(e:Exception) { ViewState.LIBRARY }
        if (parsed == ViewState.TRACK) ViewState.LIBRARY else parsed
    }
    var viewState by remember { mutableStateOf(initialViewState) }
    var lastMainTab by remember {
        mutableStateOf(if (initialViewState == ViewState.PLAYLIST) ViewState.PLAYLIST else ViewState.LIBRARY)
    }
    LaunchedEffect(viewState) {
        if (viewState == ViewState.LIBRARY || viewState == ViewState.PLAYLIST) {
            lastMainTab = viewState
        }
    }
    var surfaceStyle by remember {
        mutableStateOf(sharedPref.getString("surface_style", "BLUR_OPACITY") ?: "BLUR_OPACITY")
    }
    var isNowPlayingExpanded by remember {
        mutableStateOf(
            ((com.yuka.musicplayer.audio.AudioPlayerManager.isPlaying || audioEngine.isPlaying()) && com.yuka.musicplayer.audio.AudioPlayerManager.currentTrack != null)
        )
    }
    var showSystemLogs by remember { mutableStateOf(false) }
    var showHelpPanel by remember { mutableStateOf(false) }
    var showQueuePanel by remember { mutableStateOf(false) }
    var queueRevealFraction by remember { mutableFloatStateOf(0f) }
    var nowPlayingCollapseDragFraction by remember { mutableFloatStateOf(0f) }
    var showUpdatePanel by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var showWarmupConfirmModal by remember { mutableStateOf(false) }
    var updateCheckState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var trackActionTarget by remember { mutableStateOf<File?>(null) }
    var initialMultiSelectFile by remember { mutableStateOf<File?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<com.yuka.musicplayer.model.Playlist?>(null) }
    var showSelectPlaylistDialog by remember { mutableStateOf(false) }
    var trackToAddForPlaylist by remember { mutableStateOf<File?>(null) }
    var trackDetailsTarget by remember { mutableStateOf<File?>(null) }
    var tracksToDelete by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedTracksForPlaylist by remember { mutableStateOf<List<File>>(emptyList()) }
    var settingsInitialSubPage by remember { mutableStateOf(com.yuka.musicplayer.ui.views.settings.SettingsSubPage.ROOT) }

    // Screen Pinning / Lock Task Mode State
    fun Context.findComponentActivity(): ComponentActivity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is ComponentActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    val activity = context.findComponentActivity()
    val activityManager = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }
    fun checkIsTaskLocked(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activityManager?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            false
        }
    }
    var isTaskLocked by remember { mutableStateOf(checkIsTaskLocked()) }

    val lifecycleOwnerForLock = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwnerForLock) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isTaskLocked = checkIsTaskLocked()
            }
        }
        lifecycleOwnerForLock.lifecycle.addObserver(observer)
        onDispose { lifecycleOwnerForLock.lifecycle.removeObserver(observer) }
    }

    val toggleLockTask: () -> Unit = {
        activity?.let { act ->
            try {
                if (checkIsTaskLocked()) {
                    act.stopLockTask()
                    isTaskLocked = false
                    Toast.makeText(act, "APP LOCK DEACTIVATED", Toast.LENGTH_SHORT).show()
                } else {
                    act.startLockTask()
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(600)
                        val locked = checkIsTaskLocked()
                        isTaskLocked = locked
                        if (locked) {
                            Toast.makeText(act, "APP LOCKED (SCREEN PINNED)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(act, "Enable 'App Pinning' in Android Security Settings first", Toast.LENGTH_LONG).show()
                            try {
                                act.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(act, "Lock task error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Hardware DAC Warmup State & Animation
    var showWarmupHud by remember { mutableStateOf(false) }
    var warmupDacName by remember { mutableStateOf("USB DAC") }
    var isWarmingUpState by remember { mutableStateOf(false) }

    val usbAudioController = com.yuka.musicplayer.audio.AudioPlayerManager.usbAudioController
    var isDacConnected by remember { mutableStateOf(audioEngine.isDacConnected() || com.yuka.musicplayer.audio.AudioPlayerManager.isDacConnected) }

    val triggerWarmupProcess: (String) -> Unit = { targetName ->
        val isStreaming = audioEngine.isPlaying()
        val dacAttached = audioEngine.isDacConnected() || com.yuka.musicplayer.audio.AudioPlayerManager.isDacConnected
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

    // Only scan USB DAC if not connected; do NOT auto-warmup
    LaunchedEffect(Unit) {
        val dacAttached = audioEngine.isDacConnected() || com.yuka.musicplayer.audio.AudioPlayerManager.isDacConnected
        if (dacAttached) {
            isDacConnected = true
        } else {
            isDacConnected = false
            com.yuka.musicplayer.audio.AudioPlayerManager.usbAudioController.scanAndRequestPermission()
        }
    }

    var isShuffleEnabled by remember { mutableStateOf(sharedPref.getBoolean("shuffle_enabled", false)) }
    var repeatMode by remember {
        mutableStateOf(
            try {
                RepeatMode.valueOf(sharedPref.getString("repeat_mode", RepeatMode.OFF.name) ?: RepeatMode.OFF.name)
            } catch (e: Exception) {
                RepeatMode.OFF
            }
        )
    }
    var priorityQueue by remember { mutableStateOf<List<File>>(com.yuka.musicplayer.audio.AudioPlayerManager.priorityQueue) }
    var playbackLibraryFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var shuffledList by remember { mutableStateOf<List<File>>(emptyList()) }
    var currentShuffleIndex by remember { mutableIntStateOf(-1) }
    val playbackHistory = remember { mutableListOf<File>() }

    LaunchedEffect(isShuffleEnabled) {
        sharedPref.edit().putBoolean("shuffle_enabled", isShuffleEnabled).apply()
    }
    LaunchedEffect(repeatMode) {
        sharedPref.edit().putString("repeat_mode", repeatMode.name).apply()
    }
    LaunchedEffect(priorityQueue) {
        com.yuka.musicplayer.audio.AudioPlayerManager.priorityQueue = priorityQueue
    }
    
    var allPlaylists by remember { mutableStateOf<List<com.yuka.musicplayer.model.Playlist>>(emptyList()) }
    var selectedPlaylistId by remember { mutableStateOf("") }
    var activePlayingPlaylistId by remember { mutableStateOf<String?>(com.yuka.musicplayer.audio.AudioPlayerManager.activePlayingPlaylistId) }
    
    var currentPlaybackSourceStr by rememberSaveable { mutableStateOf(com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource.name) }
    val currentPlaybackSource = PlaybackSource.valueOf(currentPlaybackSourceStr)

    LaunchedEffect(currentPlaybackSourceStr) {
        com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource = currentPlaybackSource
    }
    LaunchedEffect(activePlayingPlaylistId) {
        com.yuka.musicplayer.audio.AudioPlayerManager.activePlayingPlaylistId = activePlayingPlaylistId
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (!sharedPref.getBoolean("playlists_v2_migrated", false)) {
                com.yuka.musicplayer.util.PlaylistStorageHelper.performOneTimeLegacyReset(context, sharedPref)
            }
            val loaded = com.yuka.musicplayer.util.PlaylistStorageHelper.loadAllPlaylists(context)
            withContext(Dispatchers.Main) {
                allPlaylists = loaded
                if (loaded.isNotEmpty()) {
                    selectedPlaylistId = loaded.first().id
                }
            }
        }
    }

    var currentTrack by remember { mutableStateOf<TrackInfo?>(com.yuka.musicplayer.audio.AudioPlayerManager.currentTrack) }
    val isNowPlayingVisible = isNowPlayingExpanded && currentTrack != null

    androidx.activity.compose.BackHandler(enabled = viewState == ViewState.SETTINGS && !isNowPlayingVisible) {
        viewState = lastMainTab
    }
    
    LaunchedEffect(currentTrack) {
        if (currentTrack == null) {
            isNowPlayingExpanded = false
        }
        com.yuka.musicplayer.audio.AudioPlayerManager.currentTrack = currentTrack
        com.yuka.musicplayer.audio.AudioPlayerManager.notifyStateChanged()
        val intent = Intent(context, com.yuka.musicplayer.audio.AudioForegroundService::class.java).apply {
            action = com.yuka.musicplayer.audio.AudioForegroundService.ACTION_UPDATE
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Service update error: ${e.message}")
        }
    }
    
    val accentFixedColor = remember(accentFixedColorStr) {
        try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(accentFixedColorStr)) } catch(e:Exception) { androidx.compose.ui.graphics.Color(0xFF00FF00) }
    }

    val wallpaperAccent = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                androidx.compose.material3.dynamicDarkColorScheme(context).primary
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
    
    val isDarkThemeActive = themeMode != "LIGHT"
    val targetAccentColor = remember(accentMode, accentFixedColor, currentTrack?.dominantColor, wallpaperAccent, isDarkThemeActive) {
        val rawAccent = com.yuka.musicplayer.util.ColorContrastHelper.resolveAccent(
            accentMode = accentMode,
            trackDominantColor = currentTrack?.dominantColor,
            userFixedColor = accentFixedColor,
            wallpaperColor = wallpaperAccent
        )
        com.yuka.musicplayer.util.ColorContrastHelper.ensureThemeContrast(
            color = rawAccent,
            isDarkTheme = isDarkThemeActive
        )
    }

    val currentAccentColor by animateColorAsState(
        targetValue = targetAccentColor,
        animationSpec = tween(durationMillis = 650, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "dynamicAccentCrossfade"
    )

    var searchQuery by remember { mutableStateOf("") }

    var isHardwareVolumeActive by remember { mutableStateOf(false) }
    var isHardwareVolumeSupported by remember { mutableStateOf(false) }
    var isHardwareVolumeEnabled by remember {
        mutableStateOf(sharedPref.getBoolean("dac_hw_volume_enabled", false))
    }

    val onToggleHardwareVolume: (Boolean) -> Unit = remember {
        { enabled ->
            isHardwareVolumeEnabled = enabled
            sharedPref.edit().putBoolean("dac_hw_volume_enabled", enabled).apply()
            audioEngine.setHardwareVolumeEnabled(enabled)
            isHardwareVolumeActive = audioEngine.isHardwareVolumeActive()
            isHardwareVolumeSupported = audioEngine.isHardwareVolumeSupported()
        }
    }

    LaunchedEffect(isHardwareVolumeEnabled) {
        audioEngine.setHardwareVolumeEnabled(isHardwareVolumeEnabled)
    }

    LaunchedEffect(Unit) {
        audioEngine.setHardwareVolumeEnabled(isHardwareVolumeEnabled)
        while(true) {
            isHardwareVolumeActive = audioEngine.isHardwareVolumeActive()
            isHardwareVolumeSupported = audioEngine.isHardwareVolumeSupported()
            delay(500)
        }
    }

    val defaultMusicDir = remember {
        val savedPath = sharedPref.getString("music_root_directory", null)
        if (savedPath != null && File(savedPath).exists()) {
            File(savedPath)
        } else {
            val standardMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (standardMusic != null && standardMusic.exists()) standardMusic
            else File(Environment.getExternalStorageDirectory(), "Music")
        }
    }

    var musicRootDirectory by remember { mutableStateOf(defaultMusicDir) }
    var isScanningLibrary by remember { mutableStateOf(false) }
    var scannedLibraryItems by remember { mutableStateOf<List<com.yuka.musicplayer.model.ScannedMusicItem>>(emptyList()) }

    var makeFolderAsPlaylist by remember {
        mutableStateOf(sharedPref.getBoolean("make_folder_as_playlist", false))
    }

    var folderPlaylists by remember { mutableStateOf<List<com.yuka.musicplayer.model.Playlist>>(emptyList()) }
    LaunchedEffect(makeFolderAsPlaylist, musicRootDirectory, scannedLibraryItems) {
        folderPlaylists = if (makeFolderAsPlaylist) {
            withContext(Dispatchers.Default) {
                com.yuka.musicplayer.util.MusicScannerHelper.buildFolderPlaylists(
                    rootDir = musicRootDirectory,
                    scannedItems = scannedLibraryItems
                )
            }
        } else {
            emptyList()
        }
    }

    val effectivePlaylists = remember(allPlaylists, folderPlaylists) {
        allPlaylists + folderPlaylists
    }

    val selectedPlaylist = remember(effectivePlaylists, selectedPlaylistId) {
        effectivePlaylists.firstOrNull { it.id == selectedPlaylistId } ?: effectivePlaylists.firstOrNull()
    }

    val playlistPaths = remember(selectedPlaylist) {
        selectedPlaylist?.trackPaths ?: emptyList()
    }
    val playlistSet = remember(effectivePlaylists) {
        effectivePlaylists.flatMap { it.trackPaths }.toSet()
    }

    var showFolderPickerDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(musicRootDirectory) {
        sharedPref.edit().putString("music_root_directory", musicRootDirectory.absolutePath).apply()
    }

    var librarySortMode by remember { mutableStateOf(LibrarySortMode.NAME_ASC) }
    var librarySortField by remember {
        mutableStateOf(
            runCatching {
                com.yuka.musicplayer.model.SortField.valueOf(
                    sharedPref.getString("library_sort_field", com.yuka.musicplayer.model.SortField.TITLE.name)
                        ?: com.yuka.musicplayer.model.SortField.TITLE.name
                )
            }.getOrDefault(com.yuka.musicplayer.model.SortField.TITLE)
        )
    }
    var librarySortOrder by remember {
        mutableStateOf(
            runCatching {
                com.yuka.musicplayer.model.SortOrder.valueOf(
                    sharedPref.getString("library_sort_order", com.yuka.musicplayer.model.SortOrder.ASCENDING.name)
                        ?: com.yuka.musicplayer.model.SortOrder.ASCENDING.name
                )
            }.getOrDefault(com.yuka.musicplayer.model.SortOrder.ASCENDING)
        )
    }
    LaunchedEffect(librarySortField, librarySortOrder) {
        sharedPref.edit()
            .putString("library_sort_field", librarySortField.name)
            .putString("library_sort_order", librarySortOrder.name)
            .apply()
        if (scannedLibraryItems.isNotEmpty()) {
            val resorted = withContext(Dispatchers.Default) {
                com.yuka.musicplayer.model.sortScannedItemsByField(scannedLibraryItems, librarySortField, librarySortOrder)
            }
            scannedLibraryItems = resorted
        }
    }
    var libraryRefreshTrigger by remember { mutableIntStateOf(0) }

    // Two-stage Library Loader:
    // Stage 1: Load SQLite cached tracks in <20ms so Library renders immediately without a loading spinner.
    // Stage 2: Run incremental filesystem diff in the background and update state only if files changed.
    LaunchedEffect(musicRootDirectory, isStorageGranted, libraryRefreshTrigger) {
        if (isStorageGranted) {
            withContext(Dispatchers.IO) {
                val db = com.yuka.musicplayer.util.MusicLibraryDatabaseHelper.getInstance(context)
                val cached = db.getCachedTracksForRoot(musicRootDirectory)
                if (cached.isNotEmpty()) {
                    val sortedCached = com.yuka.musicplayer.model.sortScannedItemsByField(
                        cached,
                        librarySortField,
                        librarySortOrder
                    )
                    withContext(Dispatchers.Main) {
                        scannedLibraryItems = sortedCached
                        isScanningLibrary = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isScanningLibrary = true
                    }
                }

                val scanResult = com.yuka.musicplayer.util.MusicScannerHelper.scanMusicDirectoryIncremental(
                    context = context,
                    rootDir = musicRootDirectory,
                    cachedItems = cached.takeIf { it.isNotEmpty() }
                )
                if (scanResult.hasChanges || cached.isEmpty()) {
                    val sortedFresh = com.yuka.musicplayer.model.sortScannedItemsByField(
                        scanResult.items,
                        librarySortField,
                        librarySortOrder
                    )
                    withContext(Dispatchers.Main) {
                        scannedLibraryItems = sortedFresh
                        isScanningLibrary = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isScanningLibrary = false
                    }
                }
            }
        }
    }

    LaunchedEffect(scannedLibraryItems) {
        if (scannedLibraryItems.isNotEmpty()) {
            com.yuka.musicplayer.util.AlbumArtThumbnailCache.prewarmLibraryThumbnails(scannedLibraryItems)
        }
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
                expectedSize = release.apkSize
            ) { progress, downloadedBytes, totalBytes ->
                downloadState = DownloadState.Downloading(progress, downloadedBytes, totalBytes)
            }
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

    var isPlaying by remember { mutableStateOf(com.yuka.musicplayer.audio.AudioPlayerManager.isPlaying || audioEngine.isPlaying()) }
    var pausedByTransientLoss by remember { mutableStateOf(false) }
    var playbackPosition by remember { mutableStateOf(0.0) }
    var playJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var playRequestId by remember { mutableLongStateOf(0L) }
    var metadataJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var prepareJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var playTrackRef by remember { mutableStateOf<((File, Boolean, Boolean) -> Unit)?>(null) }
    fun resolveConnectedDacProductName(): String {
        if (!isDacConnected) return ""
        return try {
            val info = org.json.JSONObject(audioEngine.getDacInfo())
            info.optString("productName", "").trim()
        } catch (_: Exception) {
            ""
        }
    }

    fun getDefaultVolumeForMode(isDac: Boolean, isHwVol: Boolean): Float = when {
        isDac && isHwVol -> 1.00f   // 32-bit Hardware Chip = 100%
        isDac && !isHwVol -> 0.20f  // 64-bit Bit-Perfect DSP = 20%
        else -> 0.05f               // Else = 5%
    }

    fun buildVolumePrefKey(dacName: String, isDac: Boolean, isHwVol: Boolean): String {
        if (!isDac || dacName.isBlank()) return "vol_mode_internal_else"
        val cleanName = dacName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val modeTag = if (isHwVol) "hw32" else "dsp64"
        return "vol_dac_${cleanName}_$modeTag"
    }

    var activeVolumePrefKey by remember {
        val initDacName = resolveConnectedDacProductName()
        mutableStateOf(buildVolumePrefKey(initDacName, isDacConnected, isHardwareVolumeActive))
    }

    var currentVolume by remember {
        val initDacName = resolveConnectedDacProductName()
        val key = buildVolumePrefKey(initDacName, isDacConnected, isHardwareVolumeActive)
        val fallback = getDefaultVolumeForMode(isDacConnected, isHardwareVolumeActive)
        mutableFloatStateOf(
            if (isDacConnected && initDacName.isNotBlank() && sharedPref.contains(key)) {
                sharedPref.getFloat(key, fallback).coerceIn(0f, 1f)
            } else {
                fallback
            }
        )
    }

    var isProgrammaticVolumeSwitch by remember { mutableStateOf(false) }

    LaunchedEffect(isDacConnected, isHardwareVolumeActive) {
        val dacName = resolveConnectedDacProductName()
        val newKey = buildVolumePrefKey(dacName, isDacConnected, isHardwareVolumeActive)
        if (activeVolumePrefKey != newKey) {
            activeVolumePrefKey = newKey
            val fallback = getDefaultVolumeForMode(isDacConnected, isHardwareVolumeActive)
            val targetVol = if (isDacConnected && dacName.isNotBlank() && sharedPref.contains(newKey)) {
                sharedPref.getFloat(newKey, fallback).coerceIn(0f, 1f)
            } else {
                fallback
            }
            isProgrammaticVolumeSwitch = true
            currentVolume = targetVol
            audioEngine.setSoftwareVolume(targetVol)
        }
    }

    var volumeSaveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    LaunchedEffect(currentVolume) {
        audioEngine.setSoftwareVolume(currentVolume)
        if (isProgrammaticVolumeSwitch) {
            isProgrammaticVolumeSwitch = false
            return@LaunchedEffect
        }
        val keyToSave = activeVolumePrefKey
        val canPersistForDac = isDacConnected && keyToSave.startsWith("vol_dac_")
        volumeSaveJob?.cancel()
        volumeSaveJob = coroutineScope.launch {
            kotlinx.coroutines.delay(350L)
            if (canPersistForDac) {
                sharedPref.edit().putFloat(keyToSave, currentVolume).apply()
            }
            sharedPref.edit().putFloat("last_volume", currentVolume).apply()
        }
    }

    LaunchedEffect(isPlaying) {
        com.yuka.musicplayer.audio.AudioPlayerManager.isPlaying = isPlaying
        com.yuka.musicplayer.audio.AudioPlayerManager.notifyStateChanged()
        val intent = Intent(context, com.yuka.musicplayer.audio.AudioForegroundService::class.java).apply {
            action = if (isPlaying) "ACTION_PLAY" else "ACTION_PAUSE"
        }
        try {
            if (isPlaying) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.w("MusicPlayer", "Service intent error: ${e.message}")
        }
    }

    LaunchedEffect(viewState, isNowPlayingExpanded, isPlaying, keepAwake) {
        if (keepAwake && (isNowPlayingExpanded || viewState == ViewState.TRACK) && isPlaying) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var formatIncompatibleError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(usbAudioController) {
        com.yuka.musicplayer.audio.AudioPlayerManager.onDacReady = { dacName ->
            coroutineScope.launch(Dispatchers.Main) {
                isDacConnected = true
                audioEngine.setHardwareVolumeEnabled(isHardwareVolumeEnabled)
                isHardwareVolumeActive = audioEngine.isHardwareVolumeActive()
                isHardwareVolumeSupported = audioEngine.isHardwareVolumeSupported()
                Toast.makeText(context, "USB DAC Ready: $dacName", Toast.LENGTH_SHORT).show()
            }
        }
        com.yuka.musicplayer.audio.AudioPlayerManager.onDacDetached = {
            coroutineScope.launch(Dispatchers.Main) {
                isPlaying = false
                isDacConnected = false
                isHardwareVolumeActive = false
                isHardwareVolumeSupported = false
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
        audioEngine.onUsbStallFaultCallback = {
            // C++ reported a hard stall. Re-init the DAC.
            audioEngine.pauseAudio()
            isPlaying = false
            usbAudioController.scanAndRequestPermission()
        }
        audioEngine.onDeviceForceDisconnectedCallback = {
            // C++ reported surprise removal (hotplug).
            com.yuka.musicplayer.audio.AudioPlayerManager.handleDacDetached()
        }
        audioEngine.onFormatIncompatibleCallback = { filename, _, _, reason ->
            coroutineScope.launch(Dispatchers.Main) {
                formatIncompatibleError = "$filename: $reason"
            }
        }
        onDispose {
            com.yuka.musicplayer.audio.AudioPlayerManager.onDacDetached = null
            audioEngine.onUsbStallFaultCallback = null
            audioEngine.onDeviceForceDisconnectedCallback = null
            audioEngine.onFormatIncompatibleCallback = null
        }
    }


    val usbManager = remember { context.getSystemService(android.content.Context.USB_SERVICE) as android.hardware.usb.UsbManager }
    // Audio focus is managed robustly by AudioForegroundService to survive gesture transitions

    val scannedItemsMap = remember(scannedLibraryItems) {
        scannedLibraryItems.associateBy { it.file.absolutePath }
    }

    fun getActiveTrackList(source: PlaybackSource = com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource): List<File> {
        val liveFileMap = scannedLibraryItems.map { it.file }.associateBy { it.absolutePath }
        return when (source) {
            PlaybackSource.LIBRARY -> {
                if (playbackLibraryFiles.isNotEmpty()) {
                    playbackLibraryFiles
                } else {
                    val playable = com.yuka.musicplayer.util.MusicScannerHelper.filterPlayableFiles(scannedLibraryItems)
                    playbackLibraryFiles = playable
                    playable
                }
            }
            PlaybackSource.PLAYLIST -> {
                val livePlaylists = allPlaylists + folderPlaylists
                val activeId = activePlayingPlaylistId
                    ?: com.yuka.musicplayer.audio.AudioPlayerManager.activePlayingPlaylistId
                    ?: selectedPlaylistId.ifEmpty { null }
                val playingPlaylist = if (activeId != null) {
                    livePlaylists.firstOrNull { it.id == activeId }
                } else {
                    livePlaylists.firstOrNull()
                }
                com.yuka.musicplayer.player.queue.QueueHelper.resolvePlaylistTracks(
                    playlistTrackPaths = playingPlaylist?.trackPaths,
                    fallbackTrackPaths = com.yuka.musicplayer.audio.AudioPlayerManager.playlistPaths,
                    supportedFileMap = liveFileMap
                )
            }
        }
    }

    val activeList = remember(currentPlaybackSource, playbackLibraryFiles, scannedLibraryItems, effectivePlaylists, activePlayingPlaylistId, selectedPlaylistId) {
        getActiveTrackList(currentPlaybackSource)
    }

    fun ensureShuffleDeck(activeList: List<File>, currentFile: File?) {
        val (deck, idx) = com.yuka.musicplayer.player.queue.QueueHelper.ensureShuffleDeck(
            activeList = activeList,
            currentFile = currentFile,
            currentShuffledList = shuffledList,
            currentShuffleIndex = currentShuffleIndex
        )
        shuffledList = deck
        currentShuffleIndex = idx
    }

    fun getNextTrackFile(isAutoAdvance: Boolean, consumeQueue: Boolean): File? {
        val currentSource = try {
            PlaybackSource.valueOf(currentPlaybackSourceStr)
        } catch (e: Exception) {
            com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource
        }
        val result = com.yuka.musicplayer.player.queue.QueueHelper.resolveNextTrack(
            isAutoAdvance = isAutoAdvance,
            consumeQueue = consumeQueue,
            priorityQueue = priorityQueue,
            currentTrack = currentTrack,
            repeatMode = repeatMode,
            activeList = getActiveTrackList(currentSource),
            isShuffleEnabled = isShuffleEnabled,
            shuffledList = shuffledList,
            currentShuffleIndex = currentShuffleIndex
        )
        if (consumeQueue) {
            priorityQueue = result.updatedQueue
            com.yuka.musicplayer.audio.AudioPlayerManager.priorityQueue = result.updatedQueue
            shuffledList = result.updatedShuffledList
            currentShuffleIndex = result.updatedShuffleIndex
        }
        return result.nextFile
    }

    fun peekNextTrackFile(): File? = getNextTrackFile(isAutoAdvance = true, consumeQueue = false)

    fun syncPreparedNextTrack() {
        if (!isPlaying || currentTrack == null) {
            prepareJob?.cancel()
            audioEngine.clearNextTrack()
            return
        }
        val nextFile = peekNextTrackFile()
        if (nextFile != null) {
            // Avoid redundant background preparation if already buffering this track
            prepareJob?.cancel()
            prepareJob = coroutineScope.launch(Dispatchers.IO) {
                delay(2500L) // Wait for current track to stabilize before reading next file
                audioEngine.prepareNextTrack(nextFile.absolutePath)
            }
        } else {
            prepareJob?.cancel()
            audioEngine.clearNextTrack()
        }
    }

    fun playNext(isAutoAdvance: Boolean) {
        val nextFile = getNextTrackFile(isAutoAdvance, consumeQueue = true)
        if (nextFile != null) {
            playTrackRef?.invoke(nextFile, isAutoAdvance, true)
        } else if (isAutoAdvance) {
            audioEngine.stopAudio()
            isPlaying = false
        }
    }

    fun playTrack(
        file: File,
        isAutoAdvance: Boolean = false,
        recordHistory: Boolean = true,
        source: PlaybackSource? = null,
        playlistId: String? = activePlayingPlaylistId ?: com.yuka.musicplayer.audio.AudioPlayerManager.activePlayingPlaylistId ?: selectedPlaylistId.ifEmpty { null },
        targetSource: PlaybackSource? = null
    ) {
        val resolvedSource = targetSource ?: source ?: com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource
        val resolvedPlaylistId = playlistId ?: (if (resolvedSource == PlaybackSource.PLAYLIST) (activePlayingPlaylistId ?: com.yuka.musicplayer.audio.AudioPlayerManager.activePlayingPlaylistId ?: selectedPlaylistId.ifEmpty { null }) else null)
        val sourceChanged = resolvedSource != com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource
        val playlistChanged = resolvedSource == PlaybackSource.PLAYLIST && resolvedPlaylistId != null && resolvedPlaylistId != activePlayingPlaylistId
        
        com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource = resolvedSource
        currentPlaybackSourceStr = resolvedSource.name
        
        if (resolvedSource == PlaybackSource.PLAYLIST) {
            if (resolvedPlaylistId != null) {
                activePlayingPlaylistId = resolvedPlaylistId
                com.yuka.musicplayer.audio.AudioPlayerManager.activePlayingPlaylistId = resolvedPlaylistId
                val livePlaylists = allPlaylists + folderPlaylists
                val playlistObj = livePlaylists.firstOrNull { it.id == resolvedPlaylistId }
                if (playlistObj != null) {
                    com.yuka.musicplayer.audio.AudioPlayerManager.playlistPaths = playlistObj.trackPaths
                }
            }
        } else {
            activePlayingPlaylistId = null
            com.yuka.musicplayer.audio.AudioPlayerManager.activePlayingPlaylistId = null
        }
        
        if (sourceChanged || playlistChanged) {
            shuffledList = emptyList()
            currentShuffleIndex = -1
            audioEngine.clearNextTrack()
        }

        if (resolvedSource == PlaybackSource.LIBRARY && (playbackLibraryFiles.isEmpty() || playbackLibraryFiles.none { it.absolutePath == file.absolutePath })) {
            playbackLibraryFiles = com.yuka.musicplayer.util.MusicScannerHelper.filterPlayableFiles(scannedLibraryItems)
        }

        if (recordHistory && currentTrack?.file != null && currentTrack?.file?.absolutePath != file.absolutePath) {
            playbackHistory.add(currentTrack!!.file)
            if (playbackHistory.size > 50) {
                playbackHistory.removeAt(0)
            }
        }

        val currentActiveList = getActiveTrackList(resolvedSource)
        if (isShuffleEnabled) {
            val trackIdx = com.yuka.musicplayer.player.queue.QueueHelper.indexOfTrack(shuffledList, file)
            val deckPaths = shuffledList.map { it.absolutePath }.toSet()
            val activePaths = currentActiveList.map { it.absolutePath }.toSet()
            if (shuffledList.isNotEmpty() && trackIdx != -1 && deckPaths == activePaths) {
                currentShuffleIndex = trackIdx
            } else {
                val remaining = currentActiveList.filter { it.absolutePath != file.absolutePath }.shuffled()
                shuffledList = listOf(file) + remaining
                currentShuffleIndex = 0
            }
        }

        if (!isAutoAdvance) {
            isNowPlayingExpanded = true
        }
        
        // Display skeleton so Now Playing screen isn't empty while metadata loads
        val cachedDominant = com.yuka.musicplayer.util.AlbumArtThumbnailCache.getCachedDominantColor(file.absolutePath, file.lastModified())
        val initialTrack = TrackInfo(
            file = file,
            title = file.nameWithoutExtension,
            artist = "Loading...",
            album = "",
            year = "",
            durationSeconds = 0.0,
            coverArt = null,
            dominantColor = cachedDominant ?: currentTrack?.dominantColor ?: Color(0xFF00FF00)
        )
        com.yuka.musicplayer.utils.AppLogger.i("PLAYBACK", "playTrack: ${file.name} (source=$targetSource, auto=$isAutoAdvance)")
        isPlaying = true
        com.yuka.musicplayer.audio.AudioPlayerManager.isPlaying = true
        currentTrack = initialTrack
        com.yuka.musicplayer.audio.AudioPlayerManager.currentTrack = initialTrack
        com.yuka.musicplayer.audio.AudioPlayerManager.notifyStateChanged()
        playbackPosition = 0.0

        val currentPlayId = ++playRequestId
        // 1. Play audio INSTANTLY (without waiting for metadata extraction)
        playJob?.cancel()
        playJob = coroutineScope.launch(Dispatchers.IO) {
            var result = audioEngine.playAudio(file.absolutePath)
            
            // Retry once on transient negotiation failure
            if (result == -3) {
                delay(200)
                result = audioEngine.playAudio(file.absolutePath)
            }
            
            when {
                result == 0 -> {
                    if (currentPlayId != playRequestId) return@launch
                    // Success
                    com.yuka.musicplayer.utils.AppLogger.i("AUDIO", "playAudio SUCCESS: ${file.name}")
                    withContext(Dispatchers.Main) {
                        if (currentPlayId != playRequestId) return@withContext
                        isPlaying = true
                        com.yuka.musicplayer.audio.AudioPlayerManager.isPlaying = true
                        formatIncompatibleError = null
                        isHardwareVolumeActive = audioEngine.isHardwareVolumeActive()
                        isHardwareVolumeSupported = audioEngine.isHardwareVolumeSupported()
                        syncPreparedNextTrack()
                    }
                }
                result == -2 -> {
                    if (currentPlayId != playRequestId) return@launch
                    // Format incompatible — refuse
                    com.yuka.musicplayer.utils.AppLogger.w("AUDIO", "playAudio FORMAT REFUSED: ${file.name} ($formatIncompatibleError)")
                    withContext(Dispatchers.Main) {
                        if (currentPlayId != playRequestId) return@withContext
                        isPlaying = false
                        com.yuka.musicplayer.audio.AudioPlayerManager.isPlaying = false
                        Toast.makeText(context, formatIncompatibleError ?: "Format not supported by DAC", Toast.LENGTH_SHORT).show()
                        if (isAutoAdvance) playNext(true)
                    }
                    return@launch
                }
                result == -1 -> {
                    if (currentPlayId != playRequestId) return@launch
                    // -1: File open/decode error (Permission denied or unsupported format)
                    com.yuka.musicplayer.utils.AppLogger.e("AUDIO", "playAudio FILE ERROR: ${file.name}")
                    withContext(Dispatchers.Main) {
                        if (currentPlayId != playRequestId) return@withContext
                        isPlaying = false
                        com.yuka.musicplayer.audio.AudioPlayerManager.isPlaying = false
                        val lastUsbDiag = audioEngine.getLastUsbDiagnostic()
                        val isPermissionIssue = lastUsbDiag.contains("Permission denied", ignoreCase = true) || !checkStoragePermission(context)
                        val errorMsg = if (isPermissionIssue) {
                            "Permission denied! Enable 'Music & Audio Access' for tsrossa in Android Settings."
                        } else {
                            "Failed to open audio file. Ensure it is a valid FLAC or WAV file."
                        }
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                else -> {
                    if (currentPlayId != playRequestId) return@launch
                    // -3 (negotiation failed after retry), -4 (device wedged), or unknown
                    com.yuka.musicplayer.utils.AppLogger.e("AUDIO", "playAudio FAILED code=$result: ${file.name}")
                    withContext(Dispatchers.Main) {
                        if (currentPlayId != playRequestId) return@withContext
                        isPlaying = false
                        com.yuka.musicplayer.audio.AudioPlayerManager.isPlaying = false
                        val msg = when (result) {
                            -3 -> "USB negotiation failed"
                            -4 -> "USB DAC wedged. Unplug and reconnect the DAC."
                            else -> "Playback failed (error code $result)"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
            }
        }
        
        // 2. Extract metadata asynchronously
        metadataJob?.cancel()
        metadataJob = coroutineScope.launch(Dispatchers.IO) {
            val metadata = extractMetadata(file)
            com.yuka.musicplayer.utils.AppLogger.i("AUDIO", "Decoded ${file.extension.uppercase()} stream: ${file.name} (${metadata.sampleRateHz} Hz, ${metadata.bitDepth}-bit, ${metadata.bitrateKbps} kbps)")
            withContext(Dispatchers.Main) {
                if (currentPlayId != playRequestId) return@withContext
                currentTrack = metadata
                com.yuka.musicplayer.audio.AudioPlayerManager.currentTrack = metadata
                com.yuka.musicplayer.audio.AudioPlayerManager.notifyStateChanged()
            }
        }
    }
    
    playTrackRef = { file, auto, rec ->
        val currentSource = try {
            PlaybackSource.valueOf(currentPlaybackSourceStr)
        } catch (e: Exception) {
            com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource
        }
        playTrack(
            file = file,
            isAutoAdvance = auto,
            recordHistory = rec,
            targetSource = currentSource,
            playlistId = activePlayingPlaylistId ?: com.yuka.musicplayer.audio.AudioPlayerManager.activePlayingPlaylistId ?: selectedPlaylistId.ifEmpty { null }
        )
    }

    fun playPrev() {
        val currentPos = if (audioEngine.isPlaying()) audioEngine.getPosition() else playbackPosition
        if (currentPos > 3.0) {
            playbackPosition = 0.0
            audioEngine.seekTo(0.0)
            return
        }
        val currentSource = com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource
        val currentActiveList = getActiveTrackList(currentSource)
        val result = com.yuka.musicplayer.player.queue.QueueHelper.resolvePrevTrack(
            playbackHistory = playbackHistory,
            activeList = currentActiveList,
            currentTrackFile = currentTrack?.file,
            isShuffleEnabled = isShuffleEnabled,
            shuffledList = shuffledList,
            currentShuffleIndex = currentShuffleIndex,
            repeatMode = repeatMode
        )
        currentShuffleIndex = result.updatedShuffleIndex
        if (result.prevFile != null) {
            playTrack(result.prevFile, isAutoAdvance = false, recordHistory = false, source = currentSource)
        } else {
            playbackPosition = 0.0
            audioEngine.seekTo(0.0)
        }
    }

    val toggleShuffle = {
        val newState = !isShuffleEnabled
        isShuffleEnabled = newState
        coroutineScope.launch(Dispatchers.IO) {
            audioEngine.clearNextTrack()
        }
        if (newState) {
            val currentActiveList = getActiveTrackList(currentPlaybackSource)
            ensureShuffleDeck(currentActiveList, currentTrack?.file)
        }
        syncPreparedNextTrack()
    }

    val cycleRepeat = {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        coroutineScope.launch(Dispatchers.IO) {
            audioEngine.clearNextTrack()
        }
        syncPreparedNextTrack()
    }

    val selectLibrarySortMode: (LibrarySortMode) -> Unit = { mode ->
        librarySortMode = mode
        scannedLibraryItems = sortScannedMusicItems(scannedLibraryItems, mode)
        if (currentPlaybackSourceStr == PlaybackSource.LIBRARY.name && playbackLibraryFiles.isNotEmpty()) {
            playbackLibraryFiles = sortLibraryFiles(playbackLibraryFiles, mode)
            syncPreparedNextTrack()
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

    val refreshLibrary = {
        libraryRefreshTrigger++
        Toast.makeText(context, "Scanning music directory...", Toast.LENGTH_SHORT).show()
    }

    val currentPlayNext by rememberUpdatedState(::playNext)
    
    DisposableEffect(Unit) {
        com.yuka.musicplayer.audio.AudioPlayerManager.onPlayNext = {
            coroutineScope.launch(Dispatchers.Main) {
                playNext(false)
            }
        }
        com.yuka.musicplayer.audio.AudioPlayerManager.onPlayPrev = {
            coroutineScope.launch(Dispatchers.Main) {
                playPrev()
            }
        }
        com.yuka.musicplayer.audio.AudioPlayerManager.onSeekTo = { targetSec ->
            coroutineScope.launch(Dispatchers.Main) {
                audioEngine.seekTo(targetSec)
                playbackPosition = targetSec
            }
        }
        com.yuka.musicplayer.audio.AudioPlayerManager.onTogglePlay = {
            coroutineScope.launch(Dispatchers.Main) {
                if (isPlaying) {
                    audioEngine.pauseAudio()
                    playbackPosition = audioEngine.getPosition()
                    isPlaying = false
                } else {
                    val resumed = audioEngine.resumeAudio()
                    if (resumed) {
                        isPlaying = true
                    } else {
                        val activeSource = com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource
                        currentTrack?.file?.let { file ->
                            playTrack(file, false, source = activeSource)
                        } ?: run {
                            val active = getActiveTrackList(activeSource)
                            if (active.isNotEmpty()) {
                                playTrack(active.first(), false, source = activeSource)
                            }
                        }
                    }
                }
            }
        }
        audioEngine.onTrackFinishedCallback = { 
            android.util.Log.i("MainActivity", "audioEngine.onTrackFinishedCallback received -> playNext(true)")
            coroutineScope.launch(Dispatchers.Main) {
                currentPlayNext(true) 
            }
        }
        audioEngine.onTrackTransitionCallback = { path ->
            coroutineScope.launch(Dispatchers.Main) {
                audioEngine.cleanGarbage()
                val nextFile = File(path)

                // Safety guard: if native engine somehow transitioned to a file not in active list (e.g. stale prepared track),
                // correct playback immediately using current playNext.
                val activeTracks = getActiveTrackList(com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource)
                val matchesPath = { f: File -> f.absolutePath == path || f.canonicalPath == nextFile.canonicalPath }
                if (priorityQueue.none(matchesPath) && activeTracks.none(matchesPath)) {
                    android.util.Log.w("MainActivity", "Track transition leaked out of source ($path). Redirecting.")
                    currentPlayNext(true)
                    return@launch
                }

                if (currentTrack?.file != null && currentTrack?.file?.absolutePath != nextFile.absolutePath) {
                    playbackHistory.add(currentTrack!!.file)
                    if (playbackHistory.size > 50) {
                        playbackHistory.removeAt(0)
                    }
                }

                if (isShuffleEnabled && shuffledList.isNotEmpty()) {
                    val idx = com.yuka.musicplayer.player.queue.QueueHelper.indexOfTrack(shuffledList, nextFile)
                    val effectiveIdx = if (idx != -1) idx else shuffledList.indexOfFirst { matchesPath(it) }
                    if (effectiveIdx != -1) {
                        currentShuffleIndex = effectiveIdx
                    }
                }
                
                if (priorityQueue.isNotEmpty() && matchesPath(priorityQueue.first())) {
                    priorityQueue = priorityQueue.drop(1)
                    com.yuka.musicplayer.audio.AudioPlayerManager.priorityQueue = priorityQueue
                }

                // INSTANT SKELETON UPDATE: Prevent UI delay during gapless transition & fix next button repeating
                val cachedDominant = com.yuka.musicplayer.util.AlbumArtThumbnailCache.getCachedDominantColor(nextFile.absolutePath, nextFile.lastModified())
                val initialTrack = TrackInfo(
                    file = nextFile,
                    title = nextFile.nameWithoutExtension,
                    artist = "Loading...",
                    album = "",
                    year = "",
                    durationSeconds = 0.0,
                    coverArt = null,
                    dominantColor = cachedDominant ?: currentTrack?.dominantColor ?: Color(0xFF00FF00)
                )
                isPlaying = true
                com.yuka.musicplayer.audio.AudioPlayerManager.isPlaying = true
                currentTrack = initialTrack
                com.yuka.musicplayer.audio.AudioPlayerManager.currentTrack = initialTrack
                com.yuka.musicplayer.audio.AudioPlayerManager.notifyStateChanged()
                playbackPosition = 0.0
                
                metadataJob?.cancel()
                metadataJob = coroutineScope.launch(Dispatchers.IO) {
                    val nextMetadata = extractMetadata(nextFile)
                    
                    withContext(Dispatchers.Main) {
                        currentTrack = nextMetadata
                        com.yuka.musicplayer.audio.AudioPlayerManager.currentTrack = nextMetadata
                        com.yuka.musicplayer.audio.AudioPlayerManager.notifyStateChanged()

                        playbackPosition = audioEngine.getPosition()
                        syncPreparedNextTrack()
                    }
                }
            }
        }
        onDispose {
            if (!com.yuka.musicplayer.audio.AudioPlayerManager.isPlaying) {
                com.yuka.musicplayer.audio.AudioPlayerManager.onPlayNext = null
                com.yuka.musicplayer.audio.AudioPlayerManager.onPlayPrev = null
                com.yuka.musicplayer.audio.AudioPlayerManager.onSeekTo = null
                com.yuka.musicplayer.audio.AudioPlayerManager.onTogglePlay = null
                audioEngine.onTrackFinishedCallback = null
                audioEngine.onTrackTransitionCallback = null
            }
        }
    }

    val telemetry = com.yuka.musicplayer.ui.diagnostics.rememberDacTelemetry(audioEngine, isDacConnected)



    LaunchedEffect(currentTrack, isPlaying) {
        while (isPlaying && currentTrack != null) {
            playbackPosition = audioEngine.getPosition()
            delay(200)
        }
    }

    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val modalBlurCount = remember { mutableIntStateOf(0) }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale * fontScale
        ),
        LocalAccentColor provides currentAccentColor,
        com.yuka.musicplayer.ui.theme.LocalBgMode provides bgMode,
        com.yuka.musicplayer.ui.theme.LocalIsDarkTheme provides (themeMode != "LIGHT"),
        com.yuka.musicplayer.ui.theme.LocalSurfaceStyle provides surfaceStyle,
        com.yuka.musicplayer.ui.theme.LocalModalBlurCount provides modalBlurCount
    ) {
        KewMobileTheme(isDarkTheme = themeMode != "LIGHT") {
            var isLibraryLocalModalOpen by remember { mutableStateOf(false) }

            var lastValidMainCoverBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
            val currentCoverBitmap = remember(currentTrack?.file?.absolutePath, currentTrack?.blurredCoverArt) {
                currentTrack?.blurredCoverArt?.asImageBitmap() ?: currentTrack?.coverArt?.asImageBitmap()
            }
            LaunchedEffect(currentCoverBitmap, currentTrack?.artist) {
                if (currentCoverBitmap != null) {
                    lastValidMainCoverBitmap = currentCoverBitmap
                } else if (currentTrack?.artist != "Loading...") {
                    lastValidMainCoverBitmap = null
                }
            }
            val effectiveMainCoverBitmap = currentCoverBitmap ?: lastValidMainCoverBitmap

            AppBackgroundContainer(
                bgMode = bgMode,
                coverArtBitmap = effectiveMainCoverBitmap
            ) {
            val sharedOnTogglePlay: () -> Unit = {
                if (isPlaying) {
                    audioEngine.pauseAudio()
                    playbackPosition = audioEngine.getPosition()
                    isPlaying = false
                    pausedByTransientLoss = false
                } else {
                    val resumed = audioEngine.resumeAudio()
                    if (resumed) {
                        isPlaying = true
                    } else {
                        currentTrack?.file?.let { file ->
                            playTrack(file, false, source = currentPlaybackSource)
                        }
                    }
                }
            }

            val connectedDacName = remember(isDacConnected) {
                if (isDacConnected) {
                    try {
                        val info = org.json.JSONObject(audioEngine.getDacInfo())
                        val name = info.optString("productName", "")
                        if (name.isNotBlank()) name else "USB DAC"
                    } catch (_: Exception) {
                        "USB DAC"
                    }
                } else {
                    ""
                }
            }

            val upcomingTracks = remember(priorityQueue, currentTrack, activeList, isShuffleEnabled, shuffledList, currentShuffleIndex, repeatMode) {
                com.yuka.musicplayer.player.queue.QueueHelper.calculateUpcomingTracks(
                    currentTrack = currentTrack,
                    activeList = activeList,
                    isShuffleEnabled = isShuffleEnabled,
                    shuffledList = shuffledList,
                    currentShuffleIndex = currentShuffleIndex,
                    repeatMode = repeatMode
                )
            }

            val modalActionHandler = remember(
                context, coroutineScope, sharedPref, priorityQueue, allPlaylists, selectedPlaylistId,
                currentPlaybackSourceStr, isShuffleEnabled, shuffledList
            ) {
                com.yuka.musicplayer.ui.dialogs.AppModalActionHandler(
                    context = context,
                    coroutineScope = coroutineScope,
                    sharedPref = sharedPref,
                    getPriorityQueue = { priorityQueue },
                    setPriorityQueue = { priorityQueue = it },
                    getAllPlaylists = { allPlaylists },
                    setAllPlaylists = { allPlaylists = it },
                    getSelectedPlaylistId = { selectedPlaylistId },
                    setSelectedPlaylistId = { selectedPlaylistId = it },
                    getPlaylistPaths = { playlistPaths },
                    getCurrentPlaybackSourceStr = { currentPlaybackSourceStr },
                    isShuffleEnabled = { isShuffleEnabled },
                    getShuffledList = { shuffledList },
                    setShuffledList = { shuffledList = it },
                    syncPreparedNextTrack = ::syncPreparedNextTrack,
                    playTrack = { f, auto, rec, src, pId -> playTrack(f, auto, rec, src, pId) },
                    onDismissPermissionDialog = { showPermissionDialog = false },
                    onDismissQueuePanel = { showQueuePanel = false },
                    onDismissTrackOptions = { trackActionTarget = null },
                    onShowSelectPlaylistDialog = { file ->
                        trackToAddForPlaylist = file
                        showSelectPlaylistDialog = true
                    }
                )
            }

            com.yuka.musicplayer.ui.theme.ModalBlurContainer(
                modifier = Modifier.fillMaxSize(),
                extraBlurActive = isLibraryLocalModalOpen,
                maxBlurRadius = 3.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AppHeader(
                        viewState = viewState,
                        isHardwareVolumeActive = isHardwareVolumeActive,
                        isForceSoftwareVolume = telemetry.isForceSoftwareVolume,
                        isHardwareVolumeLockedBySystem = telemetry.isHardwareVolumeLockedBySystem,
                        onToggleLogs = { showSystemLogs = !showSystemLogs },
                        onToggleUpdate = {
                            showUpdatePanel = !showUpdatePanel
                            if (updateCheckState is UpdateCheckState.Idle) {
                                triggerCheckUpdate()
                            }
                        },
                        hasUpdateAvailable = updateCheckState is UpdateCheckState.UpdateAvailable,
                        onAboutClick = {
                            settingsInitialSubPage = com.yuka.musicplayer.ui.views.settings.SettingsSubPage.ABOUT
                            showSettingsModal = true
                        },
                        onSettingsClick = {
                            settingsInitialSubPage = com.yuka.musicplayer.ui.views.settings.SettingsSubPage.ROOT
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

                    WarmupConfirmDialog(
                        visible = showWarmupConfirmModal,
                        dacName = connectedDacName.ifBlank { "USB DAC" },
                        onDismissRequest = { showWarmupConfirmModal = false },
                        onConfirm = {
                            showWarmupConfirmModal = false
                            triggerWarmupProcess(connectedDacName.ifBlank { "USB DAC" })
                        }
                    )
        
        if (showWarmupHud) {
            WarmupHudDialog(
                dacName = warmupDacName,
                onWarmupFinished = {
                    showWarmupHud = false
                    isWarmingUpState = false
                }
            )
        }

        // Modal Sheets Host (Logs, Help, Update, Permissions, Queue, Track Options)
        com.yuka.musicplayer.ui.dialogs.AppModalSheetsHost(
            context = context,
            viewState = viewState,
            audioEngine = audioEngine,
            usbManager = usbManager,
            isDacConnected = isDacConnected,
            isPlaying = isPlaying,
            telemetry = telemetry,
            showSystemLogs = showSystemLogs,
            onDismissSystemLogs = { showSystemLogs = false },
            showHelpPanel = showHelpPanel,
            onDismissHelpPanel = { showHelpPanel = false },
            showUpdatePanel = showUpdatePanel,
            onDismissUpdatePanel = { showUpdatePanel = false },
            showPermissionDialog = showPermissionDialog,
            onDismissPermissionDialog = {
                if (isStorageGranted) {
                    modalActionHandler.onCompletePermissions()
                }
            },
            isNotificationGranted = isNotificationGranted,
            isStorageGranted = isStorageGranted,
            isDndGranted = isDndGranted,
            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
            onRequestNotification = requestNotificationPermission,
            onRequestStorage = requestStoragePermission,
            onRequestDnd = requestDndPermission,
            onRequestBatteryOptimization = requestBatteryOptimizationPermission,
            onCompletePermissions = modalActionHandler::onCompletePermissions,
            updateCheckState = updateCheckState,
            downloadState = downloadState,
            onTriggerCheckUpdate = { triggerCheckUpdate() },
            onTriggerDownloadAndInstall = { rel -> triggerDownloadAndInstall(rel) },
            showQueuePanel = showQueuePanel,
            onDismissQueuePanel = { showQueuePanel = false },
            currentTrack = currentTrack,
            priorityQueue = priorityQueue,
            upcomingTracks = upcomingTracks,
            scannedItemsMap = scannedItemsMap,
            onRemoveFromQueue = modalActionHandler::onRemoveFromQueue,
            onClearQueue = modalActionHandler::onClearQueue,
            onSelectQueueTrack = modalActionHandler::onSelectQueueTrack,
            trackActionTarget = trackActionTarget,
            onDismissTrackOptions = { trackActionTarget = null },
            playlistSet = playlistSet,
            onPlayTrackAction = modalActionHandler::onPlayTrackAction,
            onPlayNextInQueue = modalActionHandler::onPlayNextInQueue,
            onAddToQueue = modalActionHandler::onAddToQueue,
            onOpenAddToPlaylist = { file ->
                trackToAddForPlaylist = file
                selectedTracksForPlaylist = emptyList()
                showSelectPlaylistDialog = true
            },
            onSelectMultiple = { file ->
                initialMultiSelectFile = file
                viewState = ViewState.LIBRARY
            },
            onShowDetails = { file -> trackDetailsTarget = file },
            onDeleteTrack = { file -> tracksToDelete = listOf(file) },
            trackDetailsTarget = trackDetailsTarget,
            onDismissTrackDetails = { trackDetailsTarget = null },
            tracksToDelete = tracksToDelete,
            onDismissConfirmDelete = { tracksToDelete = emptyList() },
            onConfirmDeleteTracks = { files ->
                tracksToDelete = emptyList()
                coroutineScope.launch(Dispatchers.IO) {
                    var deletedCount = 0
                    files.forEach { file ->
                        if (file.delete()) {
                            deletedCount++
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "$deletedCount track${if (deletedCount == 1) "" else "s"} deleted", Toast.LENGTH_SHORT).show()
                        refreshLibrary()
                    }
                }
            }
        )

        // Playlist CRUD Dialogs
        CreatePlaylistDialog(
            visible = showCreatePlaylistDialog,
            onDismissRequest = { showCreatePlaylistDialog = false },
            onConfirmCreate = { name ->
                showCreatePlaylistDialog = false
                coroutineScope.launch(Dispatchers.IO) {
                    val (created, updated) = com.yuka.musicplayer.util.PlaylistStorageHelper.createPlaylist(context, name)
                    withContext(Dispatchers.Main) {
                        allPlaylists = updated
                        selectedPlaylistId = created.id
                        Toast.makeText(context, "Playlist '${created.name}' created", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        RenamePlaylistDialog(
            visible = playlistToRename != null,
            currentName = playlistToRename?.name ?: "",
            onDismissRequest = { playlistToRename = null },
            onConfirmRename = { newName ->
                val p = playlistToRename
                playlistToRename = null
                if (p != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val updated = com.yuka.musicplayer.util.PlaylistStorageHelper.renamePlaylist(context, p.id, newName)
                        if (updated != null) {
                            withContext(Dispatchers.Main) {
                                allPlaylists = updated
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
            playlists = allPlaylists,
            onDismissRequest = {
                showSelectPlaylistDialog = false
                trackToAddForPlaylist = null
                selectedTracksForPlaylist = emptyList()
            },
            onSelectPlaylist = { playlist ->
                showSelectPlaylistDialog = false
                val filesToAdd = if (selectedTracksForPlaylist.isNotEmpty()) {
                    selectedTracksForPlaylist
                } else if (trackToAddForPlaylist != null) {
                    listOf(trackToAddForPlaylist!!)
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
                trackToAddForPlaylist = null
                selectedTracksForPlaylist = emptyList()
            },
            onCreateNewPlaylist = {
                showSelectPlaylistDialog = false
                showCreatePlaylistDialog = true
            }
        )

        // Settings Modal Sheet (CyberBottomSheet)
        CyberBottomSheet(
            visible = showSettingsModal,
            onDismissRequest = { showSettingsModal = false },
            maxHeightFraction = 0.88f,
            maxWidth = 440.dp
        ) {
            com.yuka.musicplayer.ui.views.SettingsView(
                prefs = sharedPref,
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
                defaultScreen = defaultScreenStr,
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
                isNotificationGranted = isNotificationGranted,
                isStorageGranted = isStorageGranted,
                isDndGranted = isDndGranted,
                isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                onRequestNotification = requestNotificationPermission,
                onRequestStorage = requestStoragePermission,
                onRequestDnd = requestDndPermission,
                onRequestBatteryOptimization = requestBatteryOptimizationPermission,
                onOpenPermissionDialog = { showPermissionDialog = true },
                onOpenHelp = { showHelpPanel = true },
                updateCheckState = updateCheckState,
                onOpenUpdateDialog = { showUpdatePanel = true },
                onOpenLogs = { showSystemLogs = true },
                usbBufferProfile = usbBufferProfile,
                onUsbBufferProfileChange = { newProfile ->
                    usbBufferProfile = newProfile
                    sharedPref.edit().putInt("usb_buffer_profile", newProfile).apply()
                    com.yuka.musicplayer.audio.AudioEngine.applyBufferProfile(newProfile)
                },
                initialSubPage = settingsInitialSubPage,
                surfaceStyle = surfaceStyle,
                onSurfaceStyleChange = { newStyle ->
                    surfaceStyle = newStyle
                    sharedPref.edit().putString("surface_style", newStyle).apply()
                },
                makeFolderAsPlaylist = makeFolderAsPlaylist,
                onMakeFolderAsPlaylistChange = { enabled ->
                    makeFolderAsPlaylist = enabled
                    sharedPref.edit().putBoolean("make_folder_as_playlist", enabled).apply()
                },
                onBackFromRoot = { showSettingsModal = false },
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AppScreenContent(
                viewState = viewState,
                isNowPlayingVisible = isNowPlayingVisible,
                modifier = Modifier.fillMaxSize(),
                onNavChange = { targetState -> viewState = targetState },
                musicRootDirectory = musicRootDirectory,
                scannedItems = scannedLibraryItems,
                isScanningLibrary = isScanningLibrary,
                onChangeDirectory = { showFolderPickerDialog = true },
                currentTrack = currentTrack,
                playlistSet = playlistSet,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                librarySortMode = librarySortMode,
                librarySortField = librarySortField,
                librarySortOrder = librarySortOrder,
                onCycleSort = cycleLibrarySortMode,
                onSelectSort = selectLibrarySortMode,
                onSelectSortField = { field, order ->
                    librarySortField = field
                    librarySortOrder = order
                },
                onShuffleLibrary = {
                    if (scannedLibraryItems.isNotEmpty()) {
                        val playable = com.yuka.musicplayer.util.MusicScannerHelper.filterPlayableFiles(scannedLibraryItems)
                        if (playable.isNotEmpty()) {
                            isShuffleEnabled = true
                            val randomTrack = playable.random()
                            currentPlaybackSourceStr = PlaybackSource.LIBRARY.name
                            playbackLibraryFiles = playable
                            playTrack(randomTrack, false, source = PlaybackSource.LIBRARY)
                            Toast.makeText(context, "Shuffle Library", Toast.LENGTH_SHORT).show()
                        }
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
                onRefreshLibrary = refreshLibrary,
                onLibraryModalOpenChanged = { isLibraryLocalModalOpen = it },
                initialSelectedFile = initialMultiSelectFile,
                onConsumedInitialSelectedFile = { initialMultiSelectFile = null },
                onTrackActionTarget = { file -> trackActionTarget = file },
            onSelectLibraryItem = { item ->
                if (item.isSupported) {
                    currentPlaybackSourceStr = PlaybackSource.LIBRARY.name
                    playbackLibraryFiles = com.yuka.musicplayer.util.MusicScannerHelper.filterPlayableFiles(scannedLibraryItems)
                    playTrack(item.file, false, source = PlaybackSource.LIBRARY)
                } else {
                    Toast.makeText(
                        context,
                        "Format ${item.formatLabel} is not supported by the Bit-Perfect Direct USB Engine (FLAC & WAV only).",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            playlists = effectivePlaylists,
            selectedPlaylistId = selectedPlaylistId,
            activePlayingPlaylistId = activePlayingPlaylistId,
            onSelectPlaylistTab = { id -> selectedPlaylistId = id },
            onCreatePlaylistClick = { showCreatePlaylistDialog = true },
            onRenamePlaylistClick = { playlist -> playlistToRename = playlist },
            onDeletePlaylistClick = { playlist ->
                coroutineScope.launch(Dispatchers.IO) {
                    val updated = com.yuka.musicplayer.util.PlaylistStorageHelper.deletePlaylist(context, playlist.id)
                    if (updated != null) {
                        withContext(Dispatchers.Main) {
                            allPlaylists = updated
                            if (selectedPlaylistId == playlist.id) {
                                selectedPlaylistId = updated.firstOrNull()?.id ?: ""
                            }
                            if (activePlayingPlaylistId == playlist.id) {
                                activePlayingPlaylistId = null
                                com.yuka.musicplayer.audio.AudioPlayerManager.activePlayingPlaylistId = null
                                if (currentPlaybackSourceStr == PlaybackSource.PLAYLIST.name) {
                                    audioEngine.stopAudio()
                                    isPlaying = false
                                }
                            }
                            Toast.makeText(context, "Playlist '${playlist.name}' deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            isShuffleEnabled = isShuffleEnabled,
            onToggleShuffle = toggleShuffle,
            repeatMode = repeatMode,
            onCycleRepeat = cycleRepeat,
            priorityQueueSize = priorityQueue.size,
            onOpenQueue = { showQueuePanel = true },
            onPlayPlaylistFile = { file, playlistId ->
                val livePlaylists = allPlaylists + folderPlaylists
                val targetPlaylist = livePlaylists.firstOrNull { it.id == playlistId }
                if (targetPlaylist != null) {
                    com.yuka.musicplayer.audio.AudioPlayerManager.playlistPaths = targetPlaylist.trackPaths
                }
                playTrack(
                    file,
                    isAutoAdvance = false,
                    targetSource = PlaybackSource.PLAYLIST,
                    playlistId = playlistId
                )
            },
            onPlaylistFileRemoved = { path, playlistId ->
                modalActionHandler.onRemoveFromPlaylist(playlistId, path)
            },
            onPlayPlaylistSequential = { playlist ->
                val liveMap = scannedLibraryItems.map { it.file }.associateBy { it.absolutePath }
                val tracks = com.yuka.musicplayer.player.queue.QueueHelper.resolvePlaylistTracks(
                    playlistTrackPaths = playlist.trackPaths,
                    fallbackTrackPaths = emptyList(),
                    supportedFileMap = liveMap
                )
                val firstTrack = tracks.firstOrNull()
                if (firstTrack != null) {
                    isShuffleEnabled = false
                    shuffledList = emptyList()
                    currentShuffleIndex = -1
                    com.yuka.musicplayer.audio.AudioPlayerManager.playlistPaths = playlist.trackPaths
                    playTrack(
                        firstTrack,
                        isAutoAdvance = false,
                        targetSource = PlaybackSource.PLAYLIST,
                        playlistId = playlist.id
                    )
                    Toast.makeText(context, "Playing playlist '${playlist.name}'", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Playlist '${playlist.name}' is empty", Toast.LENGTH_SHORT).show()
                }
            },
            onPlayPlaylistShuffled = { playlist ->
                val liveMap = scannedLibraryItems.map { it.file }.associateBy { it.absolutePath }
                val tracks = com.yuka.musicplayer.player.queue.QueueHelper.resolvePlaylistTracks(
                    playlistTrackPaths = playlist.trackPaths,
                    fallbackTrackPaths = emptyList(),
                    supportedFileMap = liveMap
                )
                if (tracks.isNotEmpty()) {
                    isShuffleEnabled = true
                    shuffledList = emptyList()
                    currentShuffleIndex = -1
                    com.yuka.musicplayer.audio.AudioPlayerManager.playlistPaths = playlist.trackPaths
                    val randomTrack = tracks.random()
                    playTrack(
                        randomTrack,
                        isAutoAdvance = false,
                        targetSource = PlaybackSource.PLAYLIST,
                        playlistId = playlist.id
                    )
                    Toast.makeText(context, "Shuffle playlist '${playlist.name}'", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Playlist '${playlist.name}' is empty", Toast.LENGTH_SHORT).show()
                }
            },
            onTogglePinPlaylist = { playlist ->
                coroutineScope.launch(Dispatchers.IO) {
                    val updated = com.yuka.musicplayer.util.PlaylistStorageHelper.togglePinPlaylist(context, playlist.id)
                    if (updated != null) {
                        withContext(Dispatchers.Main) {
                            allPlaylists = updated
                            val msg = if (!playlist.isPinned) "Pinned '${playlist.name}'" else "Unpinned '${playlist.name}'"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onClearPlaylistAction = { playlistId ->
                coroutineScope.launch(Dispatchers.IO) {
                    val updated = com.yuka.musicplayer.util.PlaylistStorageHelper.clearPlaylistTracks(context, playlistId)
                    if (updated != null) {
                        withContext(Dispatchers.Main) {
                            allPlaylists = updated
                            if (currentPlaybackSourceStr == PlaybackSource.PLAYLIST.name && activePlayingPlaylistId == playlistId) {
                                shuffledList = emptyList()
                                currentShuffleIndex = -1
                                audioEngine.clearNextTrack()
                                audioEngine.stopAudio()
                                isPlaying = false
                            }
                            Toast.makeText(context, "Playlist cleared", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
        
            val isKeyboardOpen = rememberIsKeyboardOpen()

            if (!isKeyboardOpen) {
                // Floating Bottom Dock Overlay (100% transparent container floating above content)
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isNowPlayingVisible,
                    enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (currentTrack != null) {
                            com.yuka.musicplayer.ui.components.MiniPlayerView(
                                track = currentTrack!!,
                                isPlaying = isPlaying,
                                playbackPosition = playbackPosition,
                                durationSeconds = currentTrack!!.durationSeconds,
                                bgMode = bgMode,
                                onTogglePlay = sharedOnTogglePlay,
                                onNavigateToNowPlaying = { isNowPlayingExpanded = true },
                                onStopAndDismiss = {
                                    audioEngine.stopAudio()
                                    audioEngine.clearNextTrack()
                                    isPlaying = false
                                    currentTrack = null
                                }
                            )
                        }

                        BottomNavigationBar(
                            currentView = viewState,
                            bgMode = bgMode,
                            onNavClick = { viewState = it }
                        )
                    }
                }
            }
        } // End Box wrapper around screen content and floating dock
    } // End Column (AppHeader + screen content)

    // Dedicated Now Playing full-screen page (zero overshoot on open, smooth fly-out + fade on close)
    androidx.compose.animation.AnimatedVisibility(
        visible = isNowPlayingVisible,
        enter = androidx.compose.animation.slideInVertically(
            initialOffsetY = { it },
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 280,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        ) + androidx.compose.animation.fadeIn(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 220,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        ),
        exit = androidx.compose.animation.slideOutVertically(
            targetOffsetY = { it },
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 260,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        ) + androidx.compose.animation.fadeOut(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 220,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            currentTrack?.let { track ->
                com.yuka.musicplayer.ui.views.TrackView(
                    track = track,
                    playbackPosition = playbackPosition,
                    isPlaying = isPlaying,
                    currentVolume = currentVolume,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    priorityQueueSize = priorityQueue.size,
                    onTogglePlay = sharedOnTogglePlay,
                    onVolumeChange = { vol -> currentVolume = vol },
                    onPlayNext = { playNext(false) },
                    onPlayPrev = { playPrev() },
                    onToggleShuffle = toggleShuffle,
                    onCycleRepeat = cycleRepeat,
                    onOpenQueue = { showQueuePanel = true },
                    onQueueDragFractionChanged = { frac -> queueRevealFraction = frac },
                    onCollapseDragFractionChanged = { frac -> nowPlayingCollapseDragFraction = frac },
                    onSeekTo = { targetSec ->
                        audioEngine.seekTo(targetSec)
                        playbackPosition = targetSec
                    },
                    isHardwareVolumeActive = isHardwareVolumeActive,
                    isHardwareVolumeSupported = isHardwareVolumeSupported,
                    isHardwareVolumeEnabled = isHardwareVolumeEnabled,
                    onToggleHardwareVolume = onToggleHardwareVolume,
                    onCollapse = { isNowPlayingExpanded = false },
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
                            .background(com.yuka.musicplayer.ui.theme.m3SurfaceBase().copy(alpha = 1f))
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        com.yuka.musicplayer.ui.dialogs.QueuePanel(
                            currentTrack = currentTrack,
                            priorityQueue = priorityQueue,
                            upcomingTracks = upcomingTracks,
                            onRemoveFromQueue = modalActionHandler::onRemoveFromQueue,
                            onClearQueue = modalActionHandler::onClearQueue,
                            onSelectTrack = modalActionHandler::onSelectQueueTrack,
                            onClose = { queueRevealFraction = 0f },
                            scannedItemsMap = scannedItemsMap
                        )
                    }
                }
            }
        }
    }

    FolderPickerDialog(
        visible = showFolderPickerDialog,
        currentPath = musicRootDirectory,
        onDismissRequest = { showFolderPickerDialog = false },
        onSelectDirectory = { dir ->
            showFolderPickerDialog = false
            musicRootDirectory = dir
            Toast.makeText(context, "Scanning ${dir.name}...", Toast.LENGTH_SHORT).show()
        }
    )

    // Top-most BackHandler for Now Playing sheet - closing Now Playing before any underlying screens
    androidx.activity.compose.BackHandler(enabled = isNowPlayingVisible) {
        isNowPlayingExpanded = false
    }
    } // End root Box wrapper inside AppBackgroundContainer
        } // End AppBackgroundContainer
        } // End KewMobileTheme
    } // End CompositionLocalProvider
} // End KewApp

