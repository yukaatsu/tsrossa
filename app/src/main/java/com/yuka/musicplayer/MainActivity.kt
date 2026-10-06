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
import com.yuka.musicplayer.ui.theme.PastelPurple
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent launcher bug where opening app creates a duplicate MainActivity instance
        if (!isTaskRoot && intent.hasCategory(Intent.CATEGORY_LAUNCHER) && intent.action != null && intent.action == Intent.ACTION_MAIN) {
            finish()
            return
        }


        com.yuka.musicplayer.audio.AudioPlayerManager.initialize(applicationContext)

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
        if (com.yuka.musicplayer.audio.AudioPlayerManager.isInitialized) {
            com.yuka.musicplayer.audio.AudioPlayerManager.usbAudioController.scanAndRequestPermission()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            val isStillPlaying = com.yuka.musicplayer.audio.AudioPlayerManager.isPlaying ||
                (com.yuka.musicplayer.audio.AudioPlayerManager.isInitialized && com.yuka.musicplayer.audio.AudioPlayerManager.audioEngine.isPlaying())
            if (!isStillPlaying) {
                android.util.Log.i("MainActivity", "onDestroy: Activity is finishing and playback is paused. Stopping AudioForegroundService.")
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
    var defaultScreenStr by remember { mutableStateOf(sharedPref.getString("default_screen", "LIBRARY") ?: "LIBRARY") }
    var bgMode by remember { mutableStateOf(sharedPref.getString("bg_mode", "BLACK") ?: "BLACK") }
    var accentMode by remember { mutableStateOf(sharedPref.getString("accent_mode", "DYNAMIC") ?: "DYNAMIC") }
    var accentFixedColorStr by remember { mutableStateOf(sharedPref.getString("accent_fixed_color", "#00FF00") ?: "#00FF00") }
    
    val permState = com.yuka.musicplayer.util.rememberAppPermissionState(context)
    val isNotificationGranted = permState.isNotificationGranted
    val isStorageGranted = permState.isStorageGranted
    val isDndGranted = permState.isDndGranted
    val requestNotificationPermission = permState.requestNotificationPermission
    val requestStoragePermission = permState.requestStoragePermission
    val requestDndPermission = permState.requestDndPermission

    val hasCompletedOnboarding = remember {
        sharedPref.getBoolean("completed_permission_onboarding", false)
    }
    var showPermissionDialog by remember {
        mutableStateOf(!isStorageGranted || (!isNotificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasCompletedOnboarding))
    }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val performHaptic = {
        if (hapticEnabled) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    }
    val initialViewState = remember {
        if ((com.yuka.musicplayer.audio.AudioPlayerManager.isPlaying || audioEngine.isPlaying()) && com.yuka.musicplayer.audio.AudioPlayerManager.currentTrack != null) {
            ViewState.TRACK
        } else {
            try { ViewState.valueOf(defaultScreenStr) } catch(e:Exception) { ViewState.LIBRARY }
        }
    }
    var viewState by remember { mutableStateOf(initialViewState) }
    var showSystemLogs by remember { mutableStateOf(false) }
    var showHelpPanel by remember { mutableStateOf(false) }
    var showQueuePanel by remember { mutableStateOf(false) }
    var showUpdatePanel by remember { mutableStateOf(false) }
    var updateCheckState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var trackActionTarget by remember { mutableStateOf<File?>(null) }

    // Screen Pinning / Lock Task Mode State
    val activity = context as? androidx.activity.ComponentActivity
    var isTaskLocked by remember { mutableStateOf(false) }
    val toggleLockTask: () -> Unit = {
        activity?.let { act ->
            try {
                if (isTaskLocked) {
                    act.stopLockTask()
                    isTaskLocked = false
                    Toast.makeText(act, "APP LOCK DEACTIVATED", Toast.LENGTH_SHORT).show()
                } else {
                    act.startLockTask()
                    isTaskLocked = true
                    Toast.makeText(act, "APP LOCKED (SCREEN PINNED)", Toast.LENGTH_SHORT).show()
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
        warmupDacName = targetName
        showWarmupHud = true
        isWarmingUpState = true
        audioEngine.triggerWarmup(1600)
    }

    // Wire automatic warmup when USB DAC is plugged in & ready, or if already attached at launch
    LaunchedEffect(Unit) {
        if (audioEngine.isDacConnected() || com.yuka.musicplayer.audio.AudioPlayerManager.isDacConnected) {
            isDacConnected = true
            val dacName = try {
                val infoJson = org.json.JSONObject(audioEngine.getDacInfo())
                infoJson.optString("productName", "USB DAC")
            } catch (e: Exception) {
                "USB DAC"
            }
            triggerWarmupProcess(dacName)
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
    
    var playlistPaths by remember { mutableStateOf(com.yuka.musicplayer.audio.AudioPlayerManager.playlistPaths) }
    val playlistSet = remember(playlistPaths) { playlistPaths.toSet() }
    var currentPlaybackSourceStr by rememberSaveable { mutableStateOf(com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource.name) }
    val getCurrentSource = { currentPlaybackSourceStr }
    val currentPlaybackSource = PlaybackSource.valueOf(currentPlaybackSourceStr)

    LaunchedEffect(currentPlaybackSourceStr) {
        com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource = currentPlaybackSource
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val loaded = loadPlaylist(context)
            playlistPaths = loaded
            com.yuka.musicplayer.audio.AudioPlayerManager.playlistPaths = loaded
        }
    }

    var currentTrack by remember { mutableStateOf<TrackInfo?>(com.yuka.musicplayer.audio.AudioPlayerManager.currentTrack) }
    
    LaunchedEffect(currentTrack) {
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
    
    val currentAccentColor = if (accentMode == "FIXED") {
        accentFixedColor
    } else {
        currentTrack?.dominantColor ?: androidx.compose.ui.graphics.Color(0xFF00FF00)
    }

    var searchQuery by remember { mutableStateOf("") }


    var isHardwareVolumeActive by remember { mutableStateOf(false) }

    var wallpaperBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(bgMode) {
        if (bgMode == "BLUR") {
            val bitmap = com.yuka.musicplayer.util.WallpaperBlurHelper.loadBlurredWallpaper(context)
            if (bitmap != null) {
                wallpaperBitmap = bitmap
            } else {
                bgMode = "BLACK"
                sharedPref.edit().putString("bg_mode", "BLACK").apply()
            }
        }
    }


    LaunchedEffect(Unit) {
        while(true) {
            isHardwareVolumeActive = audioEngine.isHardwareVolumeActive()
            delay(500)
        }
    }

        val initialDir = remember { 
        val savedPath = sharedPref.getString("last_directory", Environment.getExternalStorageDirectory().absolutePath)
        File(savedPath ?: Environment.getExternalStorageDirectory().absolutePath)
    }

    var currentDirectory by remember { mutableStateOf(initialDir) }
    
    LaunchedEffect(currentDirectory) {
        sharedPref.edit().putString("last_directory", currentDirectory.absolutePath).apply()
    }

    var librarySortMode by remember {
        mutableStateOf(
            runCatching {
                LibrarySortMode.valueOf(
                    sharedPref.getString("library_sort_mode", LibrarySortMode.DATE_DESC.name)
                        ?: LibrarySortMode.DATE_DESC.name
                )
            }.getOrDefault(LibrarySortMode.DATE_DESC)
        )
    }
    LaunchedEffect(librarySortMode) {
        sharedPref.edit().putString("library_sort_mode", librarySortMode.name).apply()
    }
    var libraryRefreshTrigger by remember { mutableIntStateOf(0) }
    
    val coroutineScope = rememberCoroutineScope()

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
    var prepareJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var playTrackRef by remember { mutableStateOf<((File, Boolean, Boolean) -> Unit)?>(null) }
    var currentVolume by remember { 
        mutableStateOf(sharedPref.getFloat("last_volume", 1.0f)) 
    }

    LaunchedEffect(currentVolume) {
        sharedPref.edit().putFloat("last_volume", currentVolume).apply()
        audioEngine.setSoftwareVolume(currentVolume)
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

    LaunchedEffect(viewState, isPlaying, keepAwake) {
        val activity = context as? android.app.Activity
        if (keepAwake && viewState == ViewState.TRACK && isPlaying) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val notificationManager = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    val setDndMode: (Boolean) -> Unit = { enabled ->
        if (notificationManager.isNotificationPolicyAccessGranted) {
            val filter = if (enabled) {
                NotificationManager.INTERRUPTION_FILTER_ALARMS
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            notificationManager.setInterruptionFilter(filter)
        }
    }

    var uacVersion by remember { mutableIntStateOf(0) }
    var claimedInterfaces by remember { mutableStateOf("") }
    
    var negotiatedSampleRate by remember { mutableIntStateOf(0) }
    var negotiatedBitDepth by remember { mutableIntStateOf(0) }
    var sourceSampleRate by remember { mutableIntStateOf(0) }
    var sourceBitDepth by remember { mutableIntStateOf(0) }
    var recentErrorCount by remember { mutableIntStateOf(0) }

    // Live I/O Monitor state
    var outputBitDepth by remember { mutableIntStateOf(0) }
    var outputSampleRate by remember { mutableIntStateOf(0) }
    var supportedBitDepths by remember { mutableStateOf("") }
    var supportedSampleRates by remember { mutableStateOf("") }
    var formatIncompatibleError by remember { mutableStateOf<String?>(null) }
    var refusedTrackHistory by remember { mutableStateOf<List<RefusedTrackEntry>>(emptyList()) }

    DisposableEffect(usbAudioController) {
        com.yuka.musicplayer.audio.AudioPlayerManager.onDacReady = { dacName ->
            coroutineScope.launch(Dispatchers.Main) {
                val wasConnected = isDacConnected
                isDacConnected = true
                if (!wasConnected) {
                    triggerWarmupProcess(dacName)
                }
            }
        }
        com.yuka.musicplayer.audio.AudioPlayerManager.onDacDetached = {
            coroutineScope.launch(Dispatchers.Main) {
                setDndMode(false)
                isPlaying = false
                isDacConnected = false
                showWarmupHud = false
                isWarmingUpState = false
                Toast.makeText(context, "USB DAC disconnected. Playback paused.", Toast.LENGTH_SHORT).show()
            }
        }
        usbAudioController.onDeviceAttached = {
            coroutineScope.launch(Dispatchers.Main) {
                Toast.makeText(context, "USB DAC detected. Initializing...", Toast.LENGTH_SHORT).show()
                usbAudioController.scanAndRequestPermission()
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
        audioEngine.onFormatIncompatibleCallback = { filename, bitDepth, sampleRate, reason ->
            coroutineScope.launch(Dispatchers.Main) {
                formatIncompatibleError = "$filename: $reason"
            }
        }
        onDispose {
            setDndMode(false)
            com.yuka.musicplayer.audio.AudioPlayerManager.onDacDetached = null
            audioEngine.onUsbStallFaultCallback = null
            audioEngine.onDeviceForceDisconnectedCallback = null
            audioEngine.onFormatIncompatibleCallback = null
        }
    }

    LaunchedEffect(Unit) {
        if (!audioEngine.isDacConnected()) {
            usbAudioController.scanAndRequestPermission()
        }
    }

    val usbManager = remember { context.getSystemService(android.content.Context.USB_SERVICE) as android.hardware.usb.UsbManager }
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    
    val focusChangeListener = remember {
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    if (isPlaying) {
                        audioEngine.pauseAudio()
                        isPlaying = false
                        pausedByTransientLoss = true
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    if (isPlaying) {
                        audioEngine.pauseAudio()
                        isPlaying = false
                    }
                    pausedByTransientLoss = false
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (pausedByTransientLoss) {
                        pausedByTransientLoss = false
                        val resumed = audioEngine.resumeAudio()
                        if (resumed) {
                            isPlaying = true
                        } else {
                            currentTrack?.file?.let { file ->
                                playTrackRef?.invoke(file, false, true)
                            }
                        }
                    }
                }
            }
        }
    }

    val audioFocusRequest = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
        } else {
            null
        }
    }

    val hwSampleRate = remember { 44100 } // Hardcoded fallback

    val filesInDir = remember(currentDirectory, isStorageGranted, librarySortMode, libraryRefreshTrigger) {
        val files = currentDirectory.listFiles()?.toList() ?: emptyList()
        val filtered = files.filter {
            it.isDirectory ||
            it.extension.equals("flac", ignoreCase = true) ||
            it.extension.equals("wav", ignoreCase = true) ||
            it.extension.equals("wave", ignoreCase = true)
        }
        sortLibraryFiles(filtered, librarySortMode)
    }

    fun getActiveTrackList(source: PlaybackSource = com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource): List<File> {
        return if (source == PlaybackSource.LIBRARY) {
            val list = if (playbackLibraryFiles.isNotEmpty()) {
                playbackLibraryFiles
            } else {
                val currentPlayingDir = currentTrack?.file?.parentFile
                val targetDir = currentPlayingDir ?: currentDirectory
                val files = targetDir.listFiles()?.filter {
                    !it.isDirectory && (
                        it.extension.equals("flac", ignoreCase = true) ||
                        it.extension.equals("wav", ignoreCase = true) ||
                        it.extension.equals("wave", ignoreCase = true)
                    )
                } ?: emptyList()
                val sorted = sortLibraryFiles(files, librarySortMode)
                playbackLibraryFiles = sorted
                sorted
            }
            list.filter { it.exists() }
        } else {
            val files = playlistPaths.mapNotNull { path ->
                val f = File(path)
                if (f.exists()) f else null
            }
            files
        }
    }

    val activeList = remember(currentPlaybackSource, playbackLibraryFiles, filesInDir, playlistPaths) {
        getActiveTrackList(currentPlaybackSource)
    }

    fun ensureShuffleDeck(activeList: List<File>, currentFile: File?) {
        if (activeList.isEmpty()) {
            shuffledList = emptyList()
            currentShuffleIndex = -1
            return
        }
        val activePaths = activeList.map { it.absolutePath }.toSet()
        val deckPaths = shuffledList.map { it.absolutePath }.toSet()

        if (shuffledList.isNotEmpty() && deckPaths == activePaths) {
            if (currentFile != null) {
                val idx = shuffledList.indexOfFirst { it.absolutePath == currentFile.absolutePath }
                if (idx != -1) {
                    currentShuffleIndex = idx
                    return
                }
            } else {
                return
            }
        }

        if (currentFile != null && activeList.any { it.absolutePath == currentFile.absolutePath }) {
            val remaining = activeList.filter { it.absolutePath != currentFile.absolutePath }.shuffled()
            shuffledList = listOf(currentFile) + remaining
            currentShuffleIndex = 0
        } else {
            shuffledList = activeList.shuffled()
            currentShuffleIndex = 0
        }
    }

    fun getNextTrackFile(isAutoAdvance: Boolean, consumeQueue: Boolean): File? {
        if (priorityQueue.isNotEmpty()) {
            val nextQueueFile = priorityQueue.first()
            if (consumeQueue) {
                priorityQueue = priorityQueue.drop(1)
                com.yuka.musicplayer.audio.AudioPlayerManager.priorityQueue = priorityQueue
            }
            return nextQueueFile
        }

        if (isAutoAdvance && repeatMode == RepeatMode.ONE && currentTrack?.file != null) {
            return currentTrack?.file
        }

        val currentActiveList = getActiveTrackList(com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource)
        if (currentActiveList.isEmpty()) return null

        if (isShuffleEnabled) {
            if (currentActiveList.size == 1) {
                return if (repeatMode != RepeatMode.OFF || !isAutoAdvance) currentActiveList.first() else null
            }

            val activePaths = currentActiveList.map { it.absolutePath }.toSet()
            val deckPaths = shuffledList.map { it.absolutePath }.toSet()
            if (shuffledList.isEmpty() || deckPaths != activePaths) {
                ensureShuffleDeck(currentActiveList, currentTrack?.file)
            }

            val nextIndex = currentShuffleIndex + 1
            if (nextIndex < shuffledList.size) {
                val nextFile = shuffledList[nextIndex]
                if (consumeQueue) {
                    currentShuffleIndex = nextIndex
                }
                return nextFile
            } else if (repeatMode == RepeatMode.ALL || (!isAutoAdvance && repeatMode == RepeatMode.OFF)) {
                if (consumeQueue) {
                    val currentPlaying = currentTrack?.file
                    val candidates = if (currentActiveList.size > 1 && currentPlaying != null) {
                        val withoutCurrent = currentActiveList.filter { it.absolutePath != currentPlaying.absolutePath }
                        val reshuffled = withoutCurrent.shuffled()
                        reshuffled + listOf(currentPlaying)
                    } else {
                        currentActiveList.shuffled()
                    }
                    shuffledList = candidates
                    currentShuffleIndex = 0
                    return candidates.firstOrNull()
                } else {
                    return shuffledList.firstOrNull()
                }
            } else {
                return null
            }
        }

        val currentTrackFile = currentTrack?.file
        val currentIndex = currentActiveList.indexOfFirst {
            it.absolutePath == currentTrackFile?.absolutePath || it.canonicalPath == currentTrackFile?.canonicalPath
        }
        return if (currentIndex != -1 && currentIndex + 1 < currentActiveList.size) {
            currentActiveList[currentIndex + 1]
        } else if (repeatMode == RepeatMode.ALL && currentActiveList.isNotEmpty()) {
            currentActiveList[0]
        } else {
            null
        }
    }

    fun peekNextTrackFile(): File? = getNextTrackFile(isAutoAdvance = true, consumeQueue = false)

    fun syncPreparedNextTrack() {
        prepareJob?.cancel()
        if (!isPlaying || currentTrack == null) {
            audioEngine.clearNextTrack()
            return
        }
        val nextFile = peekNextTrackFile()
        if (nextFile != null) {
            prepareJob = coroutineScope.launch(Dispatchers.IO) {
                audioEngine.prepareNextTrack(nextFile.absolutePath)
            }
        } else {
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

    fun playTrack(file: File, isAutoAdvance: Boolean, recordHistory: Boolean = true, source: PlaybackSource? = null) {
        val targetSource = source ?: com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource
        val sourceChanged = targetSource != com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource
        com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource = targetSource
        currentPlaybackSourceStr = targetSource.name
        if (sourceChanged) {
            shuffledList = emptyList()
            currentShuffleIndex = -1
            audioEngine.clearNextTrack()
        }

        if (targetSource == PlaybackSource.LIBRARY && (playbackLibraryFiles.isEmpty() || playbackLibraryFiles.none { it.absolutePath == file.absolutePath })) {
            playbackLibraryFiles = file.parentFile?.listFiles()?.filter {
                !it.isDirectory && (
                    it.extension.equals("flac", ignoreCase = true) ||
                    it.extension.equals("wav", ignoreCase = true) ||
                    it.extension.equals("wave", ignoreCase = true)
                )
            }?.let { sortLibraryFiles(it, librarySortMode) } ?: emptyList()
        }

        if (recordHistory && currentTrack?.file != null && currentTrack?.file?.absolutePath != file.absolutePath) {
            playbackHistory.add(currentTrack!!.file)
            if (playbackHistory.size > 50) {
                playbackHistory.removeAt(0)
            }
        }

        val currentActiveList = getActiveTrackList(targetSource)
        if (isShuffleEnabled) {
            ensureShuffleDeck(currentActiveList, file)
        }

        if (notificationManager.isNotificationPolicyAccessGranted) {
            setDndMode(true)
        }

        if (!isAutoAdvance) {
            viewState = ViewState.TRACK
        }
        
        // Tampilkan skeleton agar layar Now Playing tidak kosong selagi loading
        val initialTrack = TrackInfo(
            file = file,
            title = file.nameWithoutExtension,
            artist = "Loading...",
            album = "",
            year = "",
            durationSeconds = 0.0,
            coverArt = null,
            dominantColor = Color(0xFF00FF00)
        )
        currentTrack = initialTrack
        com.yuka.musicplayer.audio.AudioPlayerManager.currentTrack = initialTrack
        com.yuka.musicplayer.audio.AudioPlayerManager.notifyStateChanged()
        isPlaying = true
        playbackPosition = 0.0

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
                    // Success
                    withContext(Dispatchers.Main) {
                        formatIncompatibleError = null
                        
                        if (isDacConnected) {
                            negotiatedSampleRate = audioEngine.getSampleRate()
                            negotiatedBitDepth = audioEngine.getNegotiatedBitDepth()
                            sourceSampleRate = audioEngine.getSourceSampleRate()
                            sourceBitDepth = audioEngine.getSourceBitDepth()
                            outputBitDepth = audioEngine.getOutputBitDepth()
                            outputSampleRate = audioEngine.getOutputSampleRate()
                        }

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            audioManager.requestAudioFocus(audioFocusRequest!!)
                        } else {
                            @Suppress("DEPRECATION")
                            audioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                        }
                    }
                }
                result == -2 -> {
                    // Format incompatible — refuse
                    withContext(Dispatchers.Main) {
                        isPlaying = false
                        Toast.makeText(context, formatIncompatibleError ?: "Format not supported by DAC", Toast.LENGTH_SHORT).show()
                        if (isAutoAdvance) playNext(true)
                    }
                    return@launch
                }
                result == -1 -> {
                    // -1: File open/decode error (Permission denied or unsupported format)
                    withContext(Dispatchers.Main) {
                        isPlaying = false
                        val lastUsbDiag = audioEngine.getLastUsbDiagnostic()
                        val isPermissionIssue = lastUsbDiag.contains("Permission denied", ignoreCase = true) || !checkStoragePermission(context)
                        val errorMsg = if (isPermissionIssue) {
                            "Izin ditolak! Aktifkan 'Akses semua file' untuk tsrossa di Pengaturan Android."
                        } else {
                            "Gagal membuka file audio. Pastikan format FLAC atau WAV yang valid."
                        }
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                else -> {
                    // -3 (negotiation failed after retry), -4 (device wedged), or unknown
                    withContext(Dispatchers.Main) {
                        isPlaying = false
                        val msg = when (result) {
                            -3 -> "USB negotiation failed"
                            -4 -> "USB DAC wedged. Cabut dan pasang kembali DAC."
                            else -> "Playback failed (error code $result)"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
            }
        }
        
        // 2. Extract metadata asynchronously
        coroutineScope.launch(Dispatchers.IO) {
            val metadata = extractMetadata(file)
            withContext(Dispatchers.Main) {
                currentTrack = metadata
                com.yuka.musicplayer.audio.AudioPlayerManager.currentTrack = metadata
                com.yuka.musicplayer.audio.AudioPlayerManager.notifyStateChanged()
                syncPreparedNextTrack()
            }
        }
    }
    
    playTrackRef = { f, auto, rec -> playTrack(f, auto, rec, source = com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource) }

    fun playPrev() {
        val currentSource = com.yuka.musicplayer.audio.AudioPlayerManager.currentPlaybackSource
        val currentActiveList = getActiveTrackList(currentSource)
        if (currentActiveList.isEmpty()) return

        // 1. Check history stack first (only pop tracks belonging to CURRENT active list to prevent cross-source leak)
        val currentTrackFile = currentTrack?.file
        while (playbackHistory.isNotEmpty()) {
            val prevFile = playbackHistory.removeAt(playbackHistory.size - 1)
            if (currentActiveList.any { it.absolutePath == prevFile.absolutePath || it.canonicalPath == prevFile.canonicalPath }) {
                if (isShuffleEnabled && shuffledList.isNotEmpty()) {
                    val idx = shuffledList.indexOfFirst { it.absolutePath == prevFile.absolutePath || it.canonicalPath == prevFile.canonicalPath }
                    if (idx != -1) {
                        currentShuffleIndex = idx
                    }
                }
                playTrack(prevFile, isAutoAdvance = false, recordHistory = false, source = currentSource)
                return
            }
        }

        // 2. Shuffle mode fallback if history is empty
        if (isShuffleEnabled && shuffledList.isNotEmpty()) {
            if (currentShuffleIndex > 0) {
                currentShuffleIndex--
                playTrack(shuffledList[currentShuffleIndex], isAutoAdvance = false, recordHistory = false, source = currentSource)
            } else if (repeatMode == RepeatMode.ALL && shuffledList.isNotEmpty()) {
                currentShuffleIndex = shuffledList.lastIndex
                playTrack(shuffledList[currentShuffleIndex], isAutoAdvance = false, recordHistory = false, source = currentSource)
            } else {
                currentTrackFile?.let { playTrack(it, isAutoAdvance = false, recordHistory = false, source = currentSource) }
            }
            return
        }

        // 3. Normal sequential fallback
        val currentIndex = currentActiveList.indexOfFirst {
            it.absolutePath == currentTrackFile?.absolutePath || it.canonicalPath == currentTrackFile?.canonicalPath
        }
        if (currentIndex > 0) {
            playTrack(currentActiveList[currentIndex - 1], false, recordHistory = false, source = currentSource)
        } else if (repeatMode == RepeatMode.ALL && currentActiveList.isNotEmpty()) {
            playTrack(currentActiveList.last(), false, recordHistory = false, source = currentSource)
        } else {
            currentTrackFile?.let { playTrack(it, false, recordHistory = false, source = currentSource) }
        }
    }

    val toggleShuffle = {
        val newState = !isShuffleEnabled
        isShuffleEnabled = newState
        audioEngine.clearNextTrack()
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
        audioEngine.clearNextTrack()
        syncPreparedNextTrack()
    }

    val cycleLibrarySortMode = {
        val nextMode = librarySortMode.next()
        librarySortMode = nextMode
        if (currentPlaybackSourceStr == PlaybackSource.LIBRARY.name && playbackLibraryFiles.isNotEmpty()) {
            playbackLibraryFiles = sortLibraryFiles(playbackLibraryFiles, nextMode)
            syncPreparedNextTrack()
        }
        val msg = when (nextMode) {
            LibrarySortMode.DATE_DESC -> "Sorted: Date (Newest first)"
            LibrarySortMode.DATE_ASC -> "Sorted: Date (Oldest first)"
            LibrarySortMode.NAME_ASC -> "Sorted: Name (A to Z)"
            LibrarySortMode.NAME_DESC -> "Sorted: Name (Z to A)"
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    val refreshLibrary = {
        libraryRefreshTrigger++
        val currentScannedFiles = currentDirectory.listFiles()?.filter {
            it.isDirectory ||
            it.extension.equals("flac", ignoreCase = true) ||
            it.extension.equals("wav", ignoreCase = true) ||
            it.extension.equals("wave", ignoreCase = true)
        } ?: emptyList()
        if (currentPlaybackSourceStr == PlaybackSource.LIBRARY.name &&
            currentDirectory.absolutePath == currentTrack?.file?.parentFile?.absolutePath) {
            val audioFiles = sortLibraryFiles(currentScannedFiles.filter { !it.isDirectory }, librarySortMode)
            playbackLibraryFiles = audioFiles
            syncPreparedNextTrack()
        }
        Toast.makeText(context, "Library refreshed (${currentScannedFiles.size} items)", Toast.LENGTH_SHORT).show()
    }

    val currentPlayNext by rememberUpdatedState(::playNext)
    val currentActiveList by rememberUpdatedState(activeList)
    
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
        com.yuka.musicplayer.audio.AudioPlayerManager.onTogglePlay = {
            coroutineScope.launch(Dispatchers.Main) {
                if (isPlaying) {
                    audioEngine.pauseAudio()
                    isPlaying = false
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
        audioEngine.onTrackFinishedCallback = { 
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
                    val idx = shuffledList.indexOfFirst { matchesPath(it) }
                    if (idx != -1) {
                        currentShuffleIndex = idx
                    }
                }
                
                if (priorityQueue.isNotEmpty() && matchesPath(priorityQueue.first())) {
                    priorityQueue = priorityQueue.drop(1)
                    com.yuka.musicplayer.audio.AudioPlayerManager.priorityQueue = priorityQueue
                }

                // INSTANT SKELETON UPDATE: Cegah delay UI saat gapless & fix tombol next mengulang
                val initialTrack = TrackInfo(
                    file = nextFile,
                    title = nextFile.nameWithoutExtension,
                    artist = "Loading...",
                    album = "",
                    year = "",
                    durationSeconds = 0.0,
                    coverArt = null,
                    dominantColor = Color(0xFF00FF00)
                )
                currentTrack = initialTrack
                com.yuka.musicplayer.audio.AudioPlayerManager.currentTrack = initialTrack
                com.yuka.musicplayer.audio.AudioPlayerManager.notifyStateChanged()
                
                withContext(Dispatchers.IO) {
                    val nextMetadata = extractMetadata(nextFile)
                    
                    withContext(Dispatchers.Main) {
                        currentTrack = nextMetadata
                        com.yuka.musicplayer.audio.AudioPlayerManager.currentTrack = nextMetadata
                        com.yuka.musicplayer.audio.AudioPlayerManager.notifyStateChanged()

                        playbackPosition = 0.0
                        
                        if (isDacConnected) {
                            negotiatedSampleRate = audioEngine.getSampleRate()
                            negotiatedBitDepth = audioEngine.getNegotiatedBitDepth()
                            sourceSampleRate = audioEngine.getSourceSampleRate()
                            sourceBitDepth = audioEngine.getSourceBitDepth()
                            recentErrorCount = audioEngine.getRecentErrorCount()
                            outputBitDepth = audioEngine.getOutputBitDepth()
                            outputSampleRate = audioEngine.getOutputSampleRate()
                        }
                        syncPreparedNextTrack()
                    }
                }
            }
        }
        onDispose {
            com.yuka.musicplayer.audio.AudioPlayerManager.onPlayNext = null
            com.yuka.musicplayer.audio.AudioPlayerManager.onPlayPrev = null
            com.yuka.musicplayer.audio.AudioPlayerManager.onTogglePlay = null
            audioEngine.onTrackFinishedCallback = null
            audioEngine.onTrackTransitionCallback = null
        }
    }

    val telemetry = com.yuka.musicplayer.ui.diagnostics.rememberDacTelemetry(audioEngine, isDacConnected)



    val lifecycleOwner = context as LifecycleOwner
    LaunchedEffect(currentTrack, isPlaying) {
        while (isPlaying && currentTrack != null) {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                playbackPosition = audioEngine.getPosition()
            }
            delay(500)
        }
    }

        val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale * fontScale
        ),
        LocalAccentColor provides currentAccentColor
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bgMode == "BLUR" && wallpaperBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = wallpaperBitmap!!,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(24.dp)
                )
                Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f)))
            } else if (bgMode == "SIGNATURE") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    SignatureDeepNavy,
                                    SignatureSurfaceNavy.copy(alpha = 0.85f),
                                    SignatureDeepNavy
                                )
                            )
                        )
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black))
            }
            
            Column(modifier = Modifier.fillMaxSize()) {
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
            onWarmupClick = {
                val dacName = try {
                    val info = org.json.JSONObject(audioEngine.getDacInfo())
                    info.optString("productName", "USB DAC")
                } catch (e: Exception) {
                    "USB DAC"
                }
                triggerWarmupProcess(dacName)
            },
            isWarmingUp = isWarmingUpState,
            onToggleLockTask = toggleLockTask,
            isTaskLocked = isTaskLocked
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

        // 4. Upcoming Tracks calculation for Queue Panel
        val upcomingTracks = remember(priorityQueue, currentTrack, activeList, isShuffleEnabled, shuffledList, currentShuffleIndex, repeatMode) {
            val currentActiveList = activeList

            if (isShuffleEnabled && shuffledList.isNotEmpty()) {
                val startIndex = if (currentTrack?.file != null) {
                    val idx = shuffledList.indexOfFirst { it.absolutePath == currentTrack?.file?.absolutePath }
                    if (idx != -1) idx else currentShuffleIndex
                } else {
                    currentShuffleIndex
                }
                val afterCurrent = if (startIndex in shuffledList.indices && startIndex + 1 < shuffledList.size) {
                    shuffledList.subList(startIndex + 1, shuffledList.size)
                } else emptyList()

                if (repeatMode == RepeatMode.ALL && startIndex > 0) {
                    afterCurrent + shuffledList.subList(0, startIndex)
                } else {
                    afterCurrent
                }
            } else if (currentActiveList.isNotEmpty()) {
                val currentIndex = currentActiveList.indexOfFirst { it.absolutePath == currentTrack?.file?.absolutePath }
                if (currentIndex != -1) {
                    val afterCurrent = if (currentIndex + 1 < currentActiveList.size) {
                        currentActiveList.subList(currentIndex + 1, currentActiveList.size)
                    } else emptyList()

                    if (repeatMode == RepeatMode.ALL && currentIndex > 0) {
                        afterCurrent + currentActiveList.subList(0, currentIndex)
                    } else {
                        afterCurrent
                    }
                } else {
                    currentActiveList
                }
            } else {
                emptyList()
            }
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
                    sharedPref.edit().putBoolean("completed_permission_onboarding", true).apply()
                    showPermissionDialog = false
                }
            },
            isNotificationGranted = isNotificationGranted,
            isStorageGranted = isStorageGranted,
            isDndGranted = isDndGranted,
            onRequestNotification = requestNotificationPermission,
            onRequestStorage = requestStoragePermission,
            onRequestDnd = requestDndPermission,
            onCompletePermissions = {
                sharedPref.edit().putBoolean("completed_permission_onboarding", true).apply()
                showPermissionDialog = false
            },
            updateCheckState = updateCheckState,
            downloadState = downloadState,
            onTriggerCheckUpdate = { triggerCheckUpdate() },
            onTriggerDownloadAndInstall = { rel -> triggerDownloadAndInstall(rel) },
            showQueuePanel = showQueuePanel,
            onDismissQueuePanel = { showQueuePanel = false },
            currentTrack = currentTrack,
            priorityQueue = priorityQueue,
            upcomingTracks = upcomingTracks,
            onRemoveFromQueue = { idx ->
                if (idx in priorityQueue.indices) {
                    priorityQueue = priorityQueue.filterIndexed { i, _ -> i != idx }
                    com.yuka.musicplayer.audio.AudioPlayerManager.priorityQueue = priorityQueue
                    syncPreparedNextTrack()
                }
            },
            onClearQueue = {
                priorityQueue = emptyList()
                com.yuka.musicplayer.audio.AudioPlayerManager.priorityQueue = emptyList()
                syncPreparedNextTrack()
            },
            onSelectQueueTrack = { file ->
                showQueuePanel = false
                if (priorityQueue.any { it.absolutePath == file.absolutePath }) {
                    priorityQueue = priorityQueue.filter { it.absolutePath != file.absolutePath }
                    com.yuka.musicplayer.audio.AudioPlayerManager.priorityQueue = priorityQueue
                }
                val targetSource = if (playlistPaths.any { it == file.absolutePath }) {
                    PlaybackSource.PLAYLIST
                } else {
                    PlaybackSource.LIBRARY
                }
                playTrack(file, false, source = targetSource)
            },
            trackActionTarget = trackActionTarget,
            onDismissTrackOptions = { trackActionTarget = null },
            playlistSet = playlistSet,
            onPlayTrackAction = { target, targetSource ->
                trackActionTarget = null
                if (targetSource == PlaybackSource.LIBRARY) {
                    playbackLibraryFiles = target.parentFile?.listFiles()?.filter {
                        !it.isDirectory && (
                            it.extension.equals("flac", ignoreCase = true) ||
                            it.extension.equals("wav", ignoreCase = true) ||
                            it.extension.equals("wave", ignoreCase = true)
                        )
                    }?.let { sortLibraryFiles(it, librarySortMode) } ?: emptyList()
                }
                playTrack(target, false, source = targetSource)
            },
            onPlayNextInQueue = { target ->
                priorityQueue = listOf(target) + priorityQueue
                com.yuka.musicplayer.audio.AudioPlayerManager.priorityQueue = priorityQueue
                syncPreparedNextTrack()
                Toast.makeText(context, "Added as next track", Toast.LENGTH_SHORT).show()
            },
            onAddToQueue = { target ->
                priorityQueue = priorityQueue + target
                com.yuka.musicplayer.audio.AudioPlayerManager.priorityQueue = priorityQueue
                syncPreparedNextTrack()
                Toast.makeText(context, "Added to queue (#${priorityQueue.size})", Toast.LENGTH_SHORT).show()
            },
            onAddToPlaylist = { pathToAdd ->
                coroutineScope.launch(Dispatchers.IO) {
                    val added = appendPlaylist(context, pathToAdd, playlistSet)
                    if (added) {
                        val newPaths = loadPlaylist(context)
                        withContext(Dispatchers.Main) {
                            playlistPaths = newPaths
                            Toast.makeText(context, "Added to playlist", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onRemoveFromPlaylist = { pathToRemove ->
                coroutineScope.launch(Dispatchers.IO) {
                    val newPaths = removePlaylist(context, pathToRemove, playlistPaths)
                    withContext(Dispatchers.Main) {
                        playlistPaths = newPaths
                        if (currentPlaybackSourceStr == PlaybackSource.PLAYLIST.name) {
                            if (isShuffleEnabled) {
                                shuffledList = shuffledList.filter { it.absolutePath != pathToRemove }
                            }
                            syncPreparedNextTrack()
                        }
                        Toast.makeText(context, "Removed from playlist", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        val sharedOnTogglePlay: () -> Unit = {
            if (isPlaying) {
                audioEngine.pauseAudio()
                setDndMode(false)
                isPlaying = false
                pausedByTransientLoss = false
            } else {
                val resumed = audioEngine.resumeAudio()
                if (resumed) {
                    isPlaying = true
                    if (notificationManager.isNotificationPolicyAccessGranted) {
                        setDndMode(true)
                    }
                } else {
                    currentTrack?.file?.let { file ->
                        playTrack(file, false, source = currentPlaybackSource)
                    }
                }
            }
        }

        AppScreenContent(
            viewState = viewState,
            context = context,
            coroutineScope = coroutineScope,
            audioEngine = audioEngine,
            audioManager = audioManager,
            audioFocusRequest = audioFocusRequest,
            focusChangeListener = focusChangeListener,
            currentDirectory = currentDirectory,
            onNavigateDirectory = { currentDirectory = it },
            filesInDir = filesInDir,
            currentTrack = currentTrack,
            playlistSet = playlistSet,
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            librarySortMode = librarySortMode,
            onCycleSort = cycleLibrarySortMode,
            onRefreshLibrary = refreshLibrary,
            onTrackActionTarget = { file -> trackActionTarget = file },
            onPlayLibraryFile = { file ->
                if (file.name == "..") {
                    currentDirectory.parentFile?.let { currentDirectory = it }
                } else if (file.isDirectory) {
                    currentDirectory = file
                } else {
                    currentPlaybackSourceStr = PlaybackSource.LIBRARY.name
                    playbackLibraryFiles = file.parentFile?.listFiles()?.filter {
                        !it.isDirectory && (
                            it.extension.equals("flac", ignoreCase = true) ||
                            it.extension.equals("wav", ignoreCase = true) ||
                            it.extension.equals("wave", ignoreCase = true)
                        )
                    }?.let { sortLibraryFiles(it, librarySortMode) } ?: emptyList()
                    playTrack(file, false, source = PlaybackSource.LIBRARY)
                }
            },
            playlistPaths = playlistPaths,
            onUpdatePlaylistPaths = { playlistPaths = it },
            isShuffleEnabled = isShuffleEnabled,
            onToggleShuffle = toggleShuffle,
            repeatMode = repeatMode,
            onCycleRepeat = cycleRepeat,
            priorityQueueSize = priorityQueue.size,
            onOpenQueue = { showQueuePanel = true },
            onPlayPlaylistFile = { file ->
                currentPlaybackSourceStr = PlaybackSource.PLAYLIST.name
                playTrack(file, false, source = PlaybackSource.PLAYLIST)
            },
            onPlaylistFileRemoved = { path ->
                coroutineScope.launch(Dispatchers.IO) {
                    val newPaths = removePlaylist(context, path, playlistPaths)
                    withContext(Dispatchers.Main) {
                        playlistPaths = newPaths
                        if (currentPlaybackSourceStr == PlaybackSource.PLAYLIST.name) {
                            if (isShuffleEnabled) {
                                shuffledList = shuffledList.filter { it.absolutePath != path }
                            }
                            syncPreparedNextTrack()
                        }
                    }
                }
            },
            onClearPlaylistAction = {
                coroutineScope.launch(Dispatchers.IO) {
                    clearPlaylist(context)
                    withContext(Dispatchers.Main) {
                        playlistPaths = emptyList()
                        if (currentPlaybackSourceStr == PlaybackSource.PLAYLIST.name) {
                            shuffledList = emptyList()
                            currentShuffleIndex = -1
                            audioEngine.clearNextTrack()
                        }
                    }
                }
            },
            playbackPosition = playbackPosition,
            onSeekTo = { targetSec ->
                playbackPosition = targetSec
                audioEngine.seekTo(targetSec)
            },
            isPlaying = isPlaying,
            onTogglePlay = sharedOnTogglePlay,
            currentVolume = currentVolume,
            onVolumeChange = { vol ->
                currentVolume = vol
                audioEngine.setSoftwareVolume(vol)
            },
            onPlayNext = { playNext(false) },
            onPlayPrev = { playPrev() },
            onStopPlayback = {
                if (isPlaying) {
                    audioEngine.pauseAudio()
                    isPlaying = false
                    pausedByTransientLoss = false
                    setDndMode(false)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.abandonAudioFocus(focusChangeListener)
                }
            },
            sharedPref = sharedPref,
            bgMode = bgMode,
            onBgModeChange = { bgMode = it },
            fontScale = fontScale,
            onFontScaleChange = { fontScale = it },
            hapticEnabled = hapticEnabled,
            onHapticChange = { hapticEnabled = it },
            keepAwake = keepAwake,
            onKeepAwakeChange = { keepAwake = it },
            defaultScreenStr = defaultScreenStr,
            onDefaultScreenChange = { defaultScreenStr = it },
            accentMode = accentMode,
            onAccentModeChange = { accentMode = it },
            accentFixedColorStr = accentFixedColorStr,
            onAccentFixedColorChange = { accentFixedColorStr = it },
            isNotificationGranted = isNotificationGranted,
            isStorageGranted = isStorageGranted,
            isDndGranted = isDndGranted,
            onRequestNotification = requestNotificationPermission,
            onRequestStorage = requestStoragePermission,
            onRequestDnd = requestDndPermission,
            onOpenPermissionDialog = { showPermissionDialog = true },
            onOpenHelp = { showHelpPanel = true },
            updateCheckState = updateCheckState,
            onCheckForUpdate = { triggerCheckUpdate() },
            onOpenUpdateDialog = { showUpdatePanel = true }
        )
        
        BottomNavigationBar(
            currentView = viewState,
            onNavClick = { viewState = it }
        )
    }
        } // End Box wrapper
    } // End CompositionLocalProvider
} // End KewApp

