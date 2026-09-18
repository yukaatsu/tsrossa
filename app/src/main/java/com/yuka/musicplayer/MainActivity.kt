package com.yuka.musicplayer

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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

import android.media.AudioFocusRequest
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
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
    }
}

enum class ViewState { LIBRARY, PLAYLIST, TRACK, SETTINGS }

enum class PlaybackSource { LIBRARY, PLAYLIST }

enum class RepeatMode { OFF, ALL, ONE }

fun loadPlaylist(context: Context): List<String> {
    val file = File(context.filesDir, "playlist.txt")
    if (!file.exists()) return emptyList()
    return try {
        file.readLines().filter { it.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }
}

fun appendPlaylist(context: Context, path: String, currentSet: Set<String>): Boolean {
    if (currentSet.contains(path)) return false
    return try {
        val file = File(context.filesDir, "playlist.txt")
        file.appendText("$path\n")
        true
    } catch (e: Exception) {
        false
    }
}

fun removePlaylist(context: Context, path: String, currentPaths: List<String>): List<String> {
    val newPaths = currentPaths.filter { it != path }
    return try {
        val file = File(context.filesDir, "playlist.txt")
        file.writeText(newPaths.joinToString("\n") + if (newPaths.isNotEmpty()) "\n" else "")
        newPaths
    } catch (e: Exception) {
        currentPaths
    }
}

fun clearPlaylist(context: Context): Boolean {
    return try {
        val file = File(context.filesDir, "playlist.txt")
        file.writeText("")
        true
    } catch (e: Exception) {
        false
    }
}

data class AltSettingInfo(
    val interfaceNum: Int,
    val altSetting: Int,
    val subframeSize: Int
)

data class RefusedTrackEntry(
    val filename: String,
    val bitDepth: Int,
    val sampleRate: Int,
    val reason: String
)

data class TrackInfo(
    val file: File,
    val title: String,
    val artist: String,
    val album: String,
    val year: String,
    val durationSeconds: Double,
    val coverArt: Bitmap?,
    val dominantColor: Color
) {
    val codec: String
        get() = when (file.extension.lowercase()) {
            "flac" -> "FLAC"
            "wav", "wave" -> "WAV"
            else -> file.extension.uppercase().ifEmpty { "AUDIO" }
        }
}

val TerminalFont = FontFamily(Font(R.font.fantasquesans_regular))

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
    var trackActionTarget by remember { mutableStateOf<File?>(null) }

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
    var isHardwareVolumeLockedBySystem by remember { mutableStateOf(false) }
    var isForceSoftwareVolume by remember { mutableStateOf(false) }
    var isDeviceWedged by remember { mutableStateOf(false) }
    var isSampleRateUnverified by remember { mutableStateOf(false) }

    var wallpaperBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    
    androidx.compose.runtime.LaunchedEffect(bgMode) {
        if (bgMode == "BLUR") {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val wallpaperManager = android.app.WallpaperManager.getInstance(context)
                    val drawable = wallpaperManager.drawable
                    if (drawable != null) {
                        var bmp = if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
                            drawable.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                        } else {
                            val b = android.graphics.Bitmap.createBitmap(
                                if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1080,
                                if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1920,
                                android.graphics.Bitmap.Config.ARGB_8888
                            )
                            val canvas = android.graphics.Canvas(b)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            b
                        }
                        
                        val scale = 0.25f
                        val scaledBmp = android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
                        
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                            try {
                                val rs = android.renderscript.RenderScript.create(context)
                                val input = android.renderscript.Allocation.createFromBitmap(rs, scaledBmp)
                                val output = android.renderscript.Allocation.createTyped(rs, input.type)
                                val script = android.renderscript.ScriptIntrinsicBlur.create(rs, android.renderscript.Element.U8_4(rs))
                                script.setRadius(25f)
                                script.setInput(input)
                                script.forEach(output)
                                output.copyTo(scaledBmp)
                                rs.destroy()
                            } catch(e: Exception) {}
                        }
                        
                        val imageBitmap = scaledBmp.asImageBitmap()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            wallpaperBitmap = imageBitmap
                        }
                    }
                } catch (e: SecurityException) {
                    bgMode = "BLACK"
                    sharedPref.edit().putString("bg_mode", "BLACK").apply()
                } catch (e: Exception) {
                    bgMode = "BLACK"
                    sharedPref.edit().putString("bg_mode", "BLACK").apply()
                }
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
    
    val coroutineScope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(com.yuka.musicplayer.audio.AudioPlayerManager.isPlaying || audioEngine.isPlaying()) }
    var pausedByTransientLoss by remember { mutableStateOf(false) }
    var playbackPosition by remember { mutableStateOf(0.0) }
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

    val usbAudioController = com.yuka.musicplayer.audio.AudioPlayerManager.usbAudioController
    var isDacConnected by remember { mutableStateOf(audioEngine.isDacConnected()) }
    
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
        usbAudioController.onDeviceReady = { fd ->
            isDacConnected = audioEngine.initUsbDac(fd)
            com.yuka.musicplayer.audio.AudioPlayerManager.isDacConnected = isDacConnected
            com.yuka.musicplayer.audio.AudioPlayerManager.notifyStateChanged()
        }
        usbAudioController.onDeviceDetached = {
            com.yuka.musicplayer.audio.AudioPlayerManager.handleDacDetached()
        }
        usbAudioController.onDeviceAttached = {
            coroutineScope.launch(Dispatchers.Main) {
                Toast.makeText(context, "USB DAC detected. Initializing...", Toast.LENGTH_SHORT).show()
                usbAudioController.scanAndRequestPermission()
            }
        }
        com.yuka.musicplayer.audio.AudioPlayerManager.onDacDetached = {
            coroutineScope.launch(Dispatchers.Main) {
                setDndMode(false)
                isPlaying = false
                isDacConnected = false
                Toast.makeText(context, "USB DAC disconnected. Playback paused.", Toast.LENGTH_SHORT).show()
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
                            currentTrack?.file?.absolutePath?.let { path ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    var result = audioEngine.playAudio(path)
                                    if (result == -3) {
                                        delay(200)
                                        result = audioEngine.playAudio(path)
                                    }
                                    withContext(Dispatchers.Main) {
                                        if (result == 0) {
                                            isPlaying = true
                                        } else {
                                            isPlaying = false
                                            val msg = when (result) {
                                                -2 -> "Format no longer supported by DAC"
                                                -3 -> "USB negotiation failed on resume"
                                                else -> "Playback resume failed"
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
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

    val filesInDir = remember(currentDirectory) {
        val files = currentDirectory.listFiles()?.toList() ?: emptyList()
        files.filter {
            it.isDirectory ||
            it.extension.equals("flac", ignoreCase = true) ||
            it.extension.equals("wav", ignoreCase = true) ||
            it.extension.equals("wave", ignoreCase = true)
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    val activeList = remember(currentPlaybackSource, filesInDir, playlistPaths) {
        if (currentPlaybackSource == PlaybackSource.LIBRARY) filesInDir
        else playlistPaths.map { File(it) }
    }

    var playTrackRef: ((File, Boolean) -> Unit)? = null

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

        val currentActiveList = if (getCurrentSource() == PlaybackSource.LIBRARY.name) {
            filesInDir.filter { !it.isDirectory }
        } else {
            playlistPaths.map { File(it) }
        }
        if (currentActiveList.isEmpty()) return null

        if (isShuffleEnabled) {
            if (currentActiveList.size == 1) return currentActiveList.first()
            val candidatePool = currentActiveList.filter { it.absolutePath != currentTrack?.file?.absolutePath }
            return if (candidatePool.isNotEmpty()) candidatePool.random() else currentActiveList.random()
        }

        val currentIndex = currentActiveList.indexOfFirst { it.absolutePath == currentTrack?.file?.absolutePath }
        return if (currentIndex != -1 && currentIndex + 1 < currentActiveList.size) {
            currentActiveList[currentIndex + 1]
        } else if (repeatMode == RepeatMode.ALL && currentActiveList.isNotEmpty()) {
            currentActiveList[0]
        } else {
            null
        }
    }

    fun peekNextTrackFile(): File? = getNextTrackFile(isAutoAdvance = true, consumeQueue = false)

    fun playNext(isAutoAdvance: Boolean) {
        val nextFile = getNextTrackFile(isAutoAdvance, consumeQueue = true)
        if (nextFile != null) {
            playTrackRef?.invoke(nextFile, isAutoAdvance)
        } else if (isAutoAdvance) {
            audioEngine.stopAudio()
            isPlaying = false
        }
    }

    fun playTrack(file: File, isAutoAdvance: Boolean) {
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            context.startActivity(intent)
        } else {
            setDndMode(true)
        }

        if (!isAutoAdvance) {
            viewState = ViewState.TRACK
        }
        
        // Tampilkan skeleton agar layar Now Playing tidak kosong selagi loading
        currentTrack = TrackInfo(
            file = file,
            title = file.nameWithoutExtension,
            artist = "Loading...",
            album = "",
            year = "",
            durationSeconds = 0.0,
            coverArt = null,
            dominantColor = Color(0xFF00FF00)
        )
        isPlaying = true
        playbackPosition = 0.0

        // 1. Play audio INSTANTLY (without waiting for metadata extraction)
        coroutineScope.launch(Dispatchers.IO) {
            var result = audioEngine.playAudio(file.absolutePath)
            
            // Retry once on transient negotiation failure
            if (result == -3) {
                delay(200)
                result = audioEngine.playAudio(file.absolutePath)
            }
            
            when {
                result == 0 -> {
                    // Success
                    val sRate = audioEngine.getSampleRate()
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
                else -> {
                    // -1 (file error) or -3 (negotiation failed after retry)
                    withContext(Dispatchers.Main) {
                        isPlaying = false
                        if (result == -3) {
                            Toast.makeText(context, "USB negotiation failed", Toast.LENGTH_SHORT).show()
                        }
                        playNext(isAutoAdvance)
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
            }
            
            // Prepare next track for gapless playback
            val nextFile = peekNextTrackFile()
            if (nextFile != null && currentTrack?.file?.absolutePath == file.absolutePath) {
                audioEngine.prepareNextTrack(nextFile.absolutePath)
            }
        }
    }
    
    playTrackRef = ::playTrack

    fun playPrev() {
        val currentActiveList = if (getCurrentSource() == PlaybackSource.LIBRARY.name) {
            filesInDir.filter { !it.isDirectory }
        } else {
            playlistPaths.map { File(it) }
        }
        if (currentActiveList.isEmpty()) return
        val currentIndex = currentActiveList.indexOfFirst { it.absolutePath == currentTrack?.file?.absolutePath }
        if (currentIndex > 0) {
            playTrack(currentActiveList[currentIndex - 1], false)
        } else if (repeatMode == RepeatMode.ALL && currentActiveList.isNotEmpty()) {
            playTrack(currentActiveList.last(), false)
        }
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
                    currentTrack?.file?.let { file ->
                        playTrack(file, false)
                    } ?: run {
                        val active = if (getCurrentSource() == PlaybackSource.LIBRARY.name) {
                            filesInDir.filter { !it.isDirectory }
                        } else {
                            playlistPaths.map { File(it) }
                        }
                        if (active.isNotEmpty()) {
                            playTrack(active.first(), false)
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
                
                if (priorityQueue.isNotEmpty() && priorityQueue.first().absolutePath == path) {
                    priorityQueue = priorityQueue.drop(1)
                    com.yuka.musicplayer.audio.AudioPlayerManager.priorityQueue = priorityQueue
                }

                // INSTANT SKELETON UPDATE: Cegah delay UI saat gapless & fix tombol next mengulang
                currentTrack = TrackInfo(
                    file = nextFile,
                    title = nextFile.nameWithoutExtension,
                    artist = "Loading...",
                    album = "",
                    year = "",
                    durationSeconds = 0.0,
                    coverArt = null,
                    dominantColor = Color(0xFF00FF00)
                )
                
                withContext(Dispatchers.IO) {
                    val nextMetadata = extractMetadata(nextFile)
                    
                    val nextForGapless = peekNextTrackFile()
                    if (nextForGapless != null) {
                        audioEngine.prepareNextTrack(nextForGapless.absolutePath)
                    }
                    
                    withContext(Dispatchers.Main) {
                        currentTrack = nextMetadata

                        playbackPosition = 0.0
                        
                        if (isDacConnected) {
                            negotiatedSampleRate = audioEngine.getSampleRate()
                            negotiatedBitDepth = audioEngine.getNegotiatedBitDepth()
                            sourceSampleRate = audioEngine.getSourceSampleRate()
                            sourceBitDepth = audioEngine.getSourceBitDepth()
                            recentErrorCount = audioEngine.getRecentErrorCount()
                            uacVersion = audioEngine.getUacVersion()
                            claimedInterfaces = audioEngine.getClaimedInterfaces()
                        }
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
                            list.add(RefusedTrackEntry(
                                obj.getString("file"),
                                obj.getInt("bits"),
                                obj.getInt("rate"),
                                obj.getString("reason")
                            ))
                        }
                        refusedTrackHistory = list
                    } else {
                        refusedTrackHistory = emptyList()
                    }
                } catch (e: Exception) {
                    // Ignore JSON parsing errors
                }
                
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

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED) {
                    audioEngine.pauseAudio()
                    isPlaying = false
                    isDacConnected = false
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

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
            } else {
                Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black))
            }
            
            Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            viewState = viewState,
            isHardwareVolumeActive = isHardwareVolumeActive,
            isForceSoftwareVolume = isForceSoftwareVolume,
            isHardwareVolumeLockedBySystem = isHardwareVolumeLockedBySystem,
            onToggleLogs = { showSystemLogs = !showSystemLogs },
            onToggleHelp = { showHelpPanel = !showHelpPanel }
        )
        
        if (showSystemLogs) {
            androidx.compose.ui.window.Popup(
                alignment = Alignment.Center,
                onDismissRequest = { showSystemLogs = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { showSystemLogs = false }
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                            .fillMaxWidth(0.94f)
                            .fillMaxHeight(0.85f)
                            .background(androidx.compose.ui.graphics.Color(0xFF0C0C0C), shape = RoundedCornerShape(8.dp))
                            .border(1.dp, LocalAccentColor.current.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {}
                    ) {
                        SystemLogsPanel(
                            audioEngine = audioEngine,
                            viewState = viewState,
                            context = context,
                            usbManager = usbManager,
                            isDeviceWedged = isDeviceWedged,
                            isDacConnected = isDacConnected,
                            isPlaying = isPlaying,
                            sourceSampleRate = sourceSampleRate,
                            sourceBitDepth = sourceBitDepth,
                            outputSampleRate = outputSampleRate,
                            outputBitDepth = outputBitDepth,
                            uacVersion = uacVersion,
                            claimedInterfaces = claimedInterfaces,
                            isSampleRateUnverified = isSampleRateUnverified,
                            negotiatedSampleRate = negotiatedSampleRate,
                            recentErrorCount = recentErrorCount,
                            supportedSampleRates = supportedSampleRates,
                            supportedBitDepths = supportedBitDepths,
                            refusedTrackHistory = refusedTrackHistory,
                            onClose = { showSystemLogs = false }
                        )
                    }
                }
            }
        }

        if (showHelpPanel) {
            androidx.compose.ui.window.Popup(
                alignment = Alignment.Center,
                onDismissRequest = { showHelpPanel = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { showHelpPanel = false }
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                            .fillMaxWidth(0.94f)
                            .fillMaxHeight(0.85f)
                            .background(androidx.compose.ui.graphics.Color(0xFF0C0C0C), shape = RoundedCornerShape(8.dp))
                            .border(1.dp, LocalAccentColor.current.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {}
                    ) {
                        HelpPanel(onClose = { showHelpPanel = false })
                    }
                }
            }
        }

        if (showQueuePanel) {
            val upcomingTracks = remember(priorityQueue, currentTrack, activeList) {
                val currentActiveList = if (getCurrentSource() == PlaybackSource.LIBRARY.name) filesInDir.filter { !it.isDirectory } else playlistPaths.map { File(it) }
                val currentIndex = currentActiveList.indexOfFirst { it.absolutePath == currentTrack?.file?.absolutePath }
                if (currentIndex != -1 && currentIndex + 1 < currentActiveList.size) {
                    currentActiveList.subList(currentIndex + 1, currentActiveList.size)
                } else {
                    emptyList()
                }
            }
            androidx.compose.ui.window.Popup(
                alignment = Alignment.Center,
                onDismissRequest = { showQueuePanel = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { showQueuePanel = false }
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                            .fillMaxWidth(0.94f)
                            .fillMaxHeight(0.85f)
                            .background(androidx.compose.ui.graphics.Color(0xFF0C0C0C), shape = RoundedCornerShape(8.dp))
                            .border(1.dp, LocalAccentColor.current.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {}
                    ) {
                        QueuePanel(
                            currentTrack = currentTrack,
                            priorityQueue = priorityQueue,
                            upcomingTracks = upcomingTracks,
                            onRemoveFromQueue = { idx ->
                                if (idx in priorityQueue.indices) {
                                    priorityQueue = priorityQueue.filterIndexed { i, _ -> i != idx }
                                    com.yuka.musicplayer.audio.AudioPlayerManager.priorityQueue = priorityQueue
                                }
                            },
                            onClearQueue = {
                                priorityQueue = emptyList()
                                com.yuka.musicplayer.audio.AudioPlayerManager.priorityQueue = emptyList()
                            },
                            onSelectTrack = { file ->
                                showQueuePanel = false
                                playTrack(file, false)
                            },
                            onClose = { showQueuePanel = false }
                        )
                    }
                }
            }
        }

        if (trackActionTarget != null) {
            val target = trackActionTarget!!
            val inPlaylist = playlistSet.contains(target.absolutePath)
            androidx.compose.ui.window.Popup(
                alignment = Alignment.Center,
                onDismissRequest = { trackActionTarget = null },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { trackActionTarget = null }
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp)
                            .fillMaxWidth(0.9f)
                            .background(androidx.compose.ui.graphics.Color(0xFF0C0C0C), shape = RoundedCornerShape(8.dp))
                            .border(1.dp, LocalAccentColor.current.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {}
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TRACK OPTIONS",
                                    color = LocalAccentColor.current,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = TerminalFont
                                )
                                Text(
                                    "✕",
                                    color = TerminalGray,
                                    fontSize = 12.sp,
                                    fontFamily = TerminalFont,
                                    modifier = Modifier.clickable { trackActionTarget = null }.padding(4.dp)
                                )
                            }
                            Text(
                                text = target.nameWithoutExtension.ifEmpty { target.name },
                                color = TerminalWhite,
                                fontSize = 11.sp,
                                fontFamily = TerminalFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                            )
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LocalAccentColor.current.copy(alpha = 0.25f)))
                            Spacer(modifier = Modifier.height(10.dp))

                            RetroActionItem(
                                icon = "▶",
                                label = "PLAY NOW",
                                onClick = {
                                    trackActionTarget = null
                                    playTrack(target, false)
                                }
                            )
                            RetroActionItem(
                                icon = "⏩",
                                label = "PLAY NEXT (TOP OF QUEUE)",
                                onClick = {
                                    priorityQueue = listOf(target) + priorityQueue
                                    com.yuka.musicplayer.audio.AudioPlayerManager.priorityQueue = priorityQueue
                                    trackActionTarget = null
                                    Toast.makeText(context, "Added as next track", Toast.LENGTH_SHORT).show()
                                }
                            )
                            RetroActionItem(
                                icon = "☰",
                                label = "ADD TO QUEUE",
                                onClick = {
                                    priorityQueue = priorityQueue + target
                                    com.yuka.musicplayer.audio.AudioPlayerManager.priorityQueue = priorityQueue
                                    trackActionTarget = null
                                    Toast.makeText(context, "Added to queue (#${priorityQueue.size})", Toast.LENGTH_SHORT).show()
                                }
                            )
                            if (inPlaylist) {
                                RetroActionItem(
                                    icon = "✕",
                                    label = "REMOVE FROM PLAYLIST",
                                    isDestructive = true,
                                    onClick = {
                                        val pathToRemove = target.absolutePath
                                        trackActionTarget = null
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val newPaths = removePlaylist(context, pathToRemove, playlistPaths)
                                            withContext(Dispatchers.Main) {
                                                playlistPaths = newPaths
                                                Toast.makeText(context, "Removed from playlist", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            } else {
                                RetroActionItem(
                                    icon = "★",
                                    label = "ADD TO PLAYLIST",
                                    onClick = {
                                        val pathToAdd = target.absolutePath
                                        trackActionTarget = null
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
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

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
                    } else {
                        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        context.startActivity(intent)
                    }
                } else {
                    currentTrack?.file?.absolutePath?.let { path ->
                        coroutineScope.launch(Dispatchers.IO) {
                            var result = audioEngine.playAudio(path)
                            if (result == -3) {
                                delay(200)
                                result = audioEngine.playAudio(path)
                            }
                            withContext(Dispatchers.Main) {
                                if (result == 0) {
                                    isPlaying = true
                                    if (notificationManager.isNotificationPolicyAccessGranted) {
                                        setDndMode(true)
                                    }
                                } else {
                                    isPlaying = false
                                    val msg = when (result) {
                                        -2 -> "Format not supported by DAC"
                                        -3 -> "USB negotiation failed"
                                        else -> "Playback failed"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        }

        if (viewState == ViewState.LIBRARY) {
            LibraryView(
                currentDirectory = currentDirectory,
                filesInDir = filesInDir,
                playingFile = currentTrack?.file,
                playlistSet = playlistSet,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                onFileSelected = { file -> 
                    if (file.name == "..") {
                        currentDirectory.parentFile?.let { currentDirectory = it }
                    } else if (file.isDirectory) {
                        currentDirectory = file 
                    } else {
                        currentPlaybackSourceStr = PlaybackSource.LIBRARY.name
                        playTrack(file, false)
                    }
                },
                onFileLongPressed = { file ->
                    if (!file.isDirectory) {
                        trackActionTarget = file
                    }
                },
                modifier = Modifier.weight(1f)
            )
        } else if (viewState == ViewState.PLAYLIST) {
            PlaylistView(
                playlistPaths = playlistPaths,
                playingFile = currentTrack?.file,
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                priorityQueueSize = priorityQueue.size,
                onToggleShuffle = { isShuffleEnabled = !isShuffleEnabled },
                onCycleRepeat = {
                    repeatMode = when (repeatMode) {
                        RepeatMode.OFF -> RepeatMode.ALL
                        RepeatMode.ALL -> RepeatMode.ONE
                        RepeatMode.ONE -> RepeatMode.OFF
                    }
                },
                onOpenQueue = { showQueuePanel = true },
                onFileSelected = { file ->
                    currentPlaybackSourceStr = PlaybackSource.PLAYLIST.name
                    playTrack(file, false)
                },
                onFileRemoved = { path ->
                    coroutineScope.launch(Dispatchers.IO) {
                        val newPaths = removePlaylist(context, path, playlistPaths)
                        withContext(Dispatchers.Main) {
                            playlistPaths = newPaths
                        }
                    }
                },
                onClearPlaylist = {
                    coroutineScope.launch(Dispatchers.IO) {
                        clearPlaylist(context)
                        withContext(Dispatchers.Main) {
                            playlistPaths = emptyList()
                        }
                    }
                },
                onTrackLongPressed = { file ->
                    trackActionTarget = file
                },
                modifier = Modifier.weight(1f)
            )
        } else if (viewState == ViewState.TRACK) {
            TrackView(
                track = currentTrack,
                playbackPosition = playbackPosition,
                isPlaying = isPlaying,
                currentVolume = currentVolume,
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                priorityQueueSize = priorityQueue.size,
                onTogglePlay = sharedOnTogglePlay,
                onVolumeChange = { vol -> 
                    currentVolume = vol
                    audioEngine.setSoftwareVolume(vol)
                },
                onPlayNext = { playNext(false) },
                onPlayPrev = { playPrev() },
                onStop = {
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
                onToggleShuffle = { isShuffleEnabled = !isShuffleEnabled },
                onCycleRepeat = {
                    repeatMode = when (repeatMode) {
                        RepeatMode.OFF -> RepeatMode.ALL
                        RepeatMode.ALL -> RepeatMode.ONE
                        RepeatMode.ONE -> RepeatMode.OFF
                    }
                },
                onOpenQueue = { showQueuePanel = true },
                modifier = Modifier.weight(1f)
            )
        } else {
            // Settings View
            SettingsView(
                prefs = sharedPref,
                bgMode = bgMode,
                onBgModeChange = { bgMode = it },
                fontScale = fontScale,
                onFontScaleChange = { fontScale = it },
                hapticEnabled = hapticEnabled,
                onHapticChange = { hapticEnabled = it },
                keepAwake = keepAwake,
                onKeepAwakeChange = { keepAwake = it },
                defaultScreen = defaultScreenStr,
                onDefaultScreenChange = { defaultScreenStr = it },
                accentMode = accentMode,
                onAccentModeChange = { accentMode = it },
                accentFixedColorStr = accentFixedColorStr,
                onAccentFixedColorChange = { accentFixedColorStr = it },
                modifier = Modifier.weight(1f)
            )
        }
        
        BottomNavigationBar(
            currentView = viewState,
            onNavClick = { viewState = it }
        )
    }
        } // End Box wrapper
    } // End CompositionLocalProvider
} // End KewApp


@Composable
fun AppHeader(
    viewState: ViewState,
    isHardwareVolumeActive: Boolean,
    isForceSoftwareVolume: Boolean,
    isHardwareVolumeLockedBySystem: Boolean,
    onToggleLogs: () -> Unit,
    onToggleHelp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val tsrossaAscii = """
                 _                                
                | |_ ___ _ __ ___  ___ ___  __ _  
                | __/ __| '__/ _ \/ __/ __|/ _` | 
                | |_\__ \ | | (_) \__ \__ \ (_| | 
                 \__|___/_|  \___/|___/___/\__,_| 
            """.trimIndent()
            
            Text(
                text = tsrossaAscii,
                fontFamily = TerminalFont,
                fontWeight = FontWeight.Normal,
                color = LocalAccentColor.current,
                lineHeight = 12.sp,
                fontSize = 11.sp,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 14.dp),
                textAlign = TextAlign.Center
            )
            
            Row(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .border(1.dp, LocalAccentColor.current.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .background(LocalAccentColor.current.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                        .clickable { onToggleHelp() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("?", color = LocalAccentColor.current, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("HELP", color = TerminalWhite, fontSize = 10.sp, fontFamily = TerminalFont, fontWeight = FontWeight.Bold)
                    }
                }

                Box(
                    modifier = Modifier
                        .border(1.dp, LocalAccentColor.current.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .background(LocalAccentColor.current.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                        .clickable { onToggleLogs() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚙", color = LocalAccentColor.current, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("LOGS", color = TerminalWhite, fontSize = 10.sp, fontFamily = TerminalFont, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        val hwVolumeText = if (isForceSoftwareVolume) {
            "Software Attenuation (Dithered)"
        } else if (isHardwareVolumeActive) {
            "Hardware Bit-Perfect Volume"
        } else if (isHardwareVolumeLockedBySystem) {
            "Software Attenuation (Hardware Volume unavailable — Audio Control locked by system)"
        } else {
            "Software Attenuation (Dithered)"
        }
        val isBitPerfect = isHardwareVolumeActive && !isForceSoftwareVolume
        val statusColor = if (isBitPerfect) androidx.compose.ui.graphics.Color(0xFFFFD700) else androidx.compose.ui.graphics.Color(0xFFf87171)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = hwVolumeText,
                fontFamily = TerminalFont,
                fontSize = 10.sp,
                color = statusColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        val hintText = when (viewState) {
            ViewState.LIBRARY -> "Tap:Play · Long-press:Add to playlist · ★=in playlist"
            ViewState.PLAYLIST -> "Tap:Play · Order:added time"
            else -> ""
        }
        if (hintText.isNotEmpty()) {
            Text(
                text = hintText,
                fontFamily = TerminalFont,
                fontSize = 9.sp,
                color = LocalAccentColor.current.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
    Divider(color = LocalAccentColor.current.copy(alpha = 0.25f), thickness = 1.dp)
}

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
    sb.appendLine("App Version: 1.2.0-beta1")
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
        val bitPerfect = (sourceSampleRate > 0 && sourceSampleRate == outputSampleRate && sourceBitDepth == outputBitDepth)
        sb.appendLine("Transmission: ${if (bitPerfect) "BIT-PERFECT [PASS]" else "RESAMPLED [FAIL]"}")
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
            .padding(16.dp)
    ) {
        // --- Header Bar ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    letterSpacing = 0.5.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // COPY LOG
                Box(
                    modifier = Modifier
                        .border(1.dp, LocalAccentColor.current.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
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
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "📋 COPY",
                        color = LocalAccentColor.current,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TerminalFont
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // SHARE LOG
                Box(
                    modifier = Modifier
                        .border(1.dp, LocalAccentColor.current.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
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
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "↗ SHARE",
                        color = LocalAccentColor.current,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TerminalFont
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .border(1.dp, androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
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
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        Divider(color = LocalAccentColor.current.copy(alpha = 0.2f), thickness = 1.dp)
        
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


@Composable
fun HelpItem(title: String, desc: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = "• $title",
            color = LocalAccentColor.current,
            fontFamily = TerminalFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = desc,
            color = TerminalWhite.copy(alpha = 0.85f),
            fontFamily = TerminalFont,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(start = 10.dp, top = 2.dp)
        )
    }
}

@Composable
fun HelpPanel(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(1.dp, LocalAccentColor.current, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", color = LocalAccentColor.current, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TSROSSA USER MANUAL",
                    color = TerminalWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont,
                    letterSpacing = 0.5.sp
                )
            }
            Box(
                modifier = Modifier
                    .border(1.dp, androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .background(androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("✕ CLOSE", color = androidx.compose.ui.graphics.Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LocalAccentColor.current.copy(alpha = 0.25f)))
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            item {
                LogSectionHeader("01. USB DAC & AUDIO BIT-PERFECT")
                HelpItem("Bit-Perfect UAC2 Streaming", "Audio dialirkan langsung ke hardware DAC eksternal melalui USB Isochronous libusb eksklusif, melewati audio mixer & resampler Android demi kemurnian sinyal 100%.")
                HelpItem("Indikator Titik Volume (Dot)", "🟡 Emas: 32-bit hardware volume control di chip DAC aktif.\n🔴 Merah: 64-bit dithered software attenuation.")
                HelpItem("Mode DND (Do Not Disturb)", "Mengheningkan notifikasi suara sistem secara otomatis saat musik diputar untuk mencegah gangguan audio pada stream bit-perfect.")
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                LogSectionHeader("02. KONTROL TRANSPORT & VOLUME")
                HelpItem("Navigasi Playback", "[ |<< ] Track Sebelumnya / Replay | [ ▶ / ❚❚ ] Play/Pause | [ >>| ] Track Berikutnya.")
                HelpItem("Kontrol Volume Presisi", "Tombol [-] dan [+] mengatur level volume secara presisi bertahap 5%.")
                HelpItem("Gapless Playback Engine", "Engine C++ otomatis me-load lagu berikutnya ke memori buffer sebelum lagu saat ini selesai untuk transisi 0-jeda tanpa jeda hening.")
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                LogSectionHeader("03. SHUFFLE, REPEAT & QUEUE")
                HelpItem("🔀 Shuffle (Acak)", "Mengacak urutan putar lagu secara non-destruktif tanpa merusak urutan asli file atau daftar putar.")
                HelpItem("🔁 Repeat (3 Mode)", "• OFF: Memutar hingga lagu terakhir dan berhenti.\n• ALL: Mengulang daftar playlist dari awal saat lagu terakhir usai.\n• 1: Mengulang lagu saat ini terus-menerus.")
                HelpItem("☰ Playback Queue (Antrean Prioritas)", "Lagu di antrean ini akan selalu diputar terlebih dahulu sebelum kembali ke urutan normal. Ketuk tombol [ ☰ QUEUE ] untuk melihat & mengelola antrean.")
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                LogSectionHeader("04. LIBRARY & PLAYLIST GESTURES")
                HelpItem("Pemutaran Lagu", "Ketuk sekali pada file audio untuk langsung memutarnya.")
                HelpItem("Menu Aksi Cepat (Long-Press)", "Tekan dan tahan (long-press) lagu apa pun di Library atau Playlist untuk membuka menu: Play Now, Play Next, Add to Queue, atau Add/Remove Playlist.")
                HelpItem("Navigasi Folder & Pencarian", "Ketuk 📁 [ .. ] untuk naik satu tingkat folder. Gunakan search bar > SEARCH FILES... untuk memfilter file seketika.")
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                LogSectionHeader("05. TEMA TAMPILAN & PENGATURAN")
                HelpItem("Warna Aksen Terminal", "• DYNAMIC: Mengekstrak warna aksen dari cover art album.\n• FIXED: Memilih warna tema terminal tetap (Matrix Green, Amber, Cyan, dll.).")
                HelpItem("Wallpaper Blur", "Memburamkan wallpaper latar HP Anda di belakang interface cyber HUD.")
                HelpItem("Keep Screen Awake", "Menjaga layar tetap menyala khusus saat berada di layar Now Playing selagi lagu berputar.")
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                LogSectionHeader("06. DIAGNOSTIK & SYSTEM LOGS")
                HelpItem("Panel Log [ ⚙ LOGS ]", "Ketuk tombol [ ⚙ LOGS ] di header atas kapan saja untuk memeriksa detail hardware DAC, sample rate yang ternegosiasi, status clock, dan riwayat file yang tidak kompatibel.")
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun QueuePanel(
    currentTrack: TrackInfo?,
    priorityQueue: List<File>,
    upcomingTracks: List<File>,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onSelectTrack: (File) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("☰", color = LocalAccentColor.current, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PLAY QUEUE & UP NEXT (${priorityQueue.size})",
                    color = TerminalWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont,
                    letterSpacing = 0.5.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (priorityQueue.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .clickable { onClearQueue() }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text("CLEAR", color = androidx.compose.ui.graphics.Color.Red, fontSize = 9.sp, fontFamily = TerminalFont, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Box(
                    modifier = Modifier
                        .border(1.dp, androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .background(androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .clickable { onClose() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("✕", color = androidx.compose.ui.graphics.Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LocalAccentColor.current.copy(alpha = 0.25f)))
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            item {
                LogSectionHeader("NOW PLAYING")
                if (currentTrack != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LocalAccentColor.current.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .background(LocalAccentColor.current.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("▶", color = LocalAccentColor.current, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(currentTrack.title, color = TerminalWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(currentTrack.artist, color = LocalAccentColor.current, fontSize = 10.sp, fontFamily = TerminalFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else {
                    Text("No track actively playing", color = TerminalGray, fontFamily = TerminalFont, fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            item {
                LogSectionHeader("UP NEXT (PRIORITY QUEUE) [${priorityQueue.size}]")
            }

            if (priorityQueue.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, TerminalGray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[ PRIORITY QUEUE EMPTY ]\nLong-press any track to 'Play Next' or 'Add to Queue'",
                            color = TerminalGray,
                            fontFamily = TerminalFont,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            } else {
                items(priorityQueue.mapIndexed { idx, f -> idx to f }) { (idx, file) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .border(1.dp, LocalAccentColor.current.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .background(LocalAccentColor.current.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                            .clickable { onSelectTrack(file) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format("#%02d", idx + 1),
                            color = LocalAccentColor.current,
                            fontFamily = TerminalFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = file.nameWithoutExtension.ifEmpty { file.name },
                            color = TerminalWhite,
                            fontFamily = TerminalFont,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clickable { onRemoveFromQueue(idx) }
                                .padding(4.dp)
                        ) {
                            Text("✕", color = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(14.dp)) }
            }

            if (upcomingTracks.isNotEmpty()) {
                item {
                    LogSectionHeader("UPCOMING IN PLAYLIST / FOLDER")
                }
                items(upcomingTracks.take(8).mapIndexed { idx, f -> idx to f }) { (idx, file) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { onSelectTrack(file) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "•",
                            color = TerminalGray,
                            fontFamily = TerminalFont,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = file.nameWithoutExtension.ifEmpty { file.name },
                            color = TerminalGray.copy(alpha = 0.85f),
                            fontFamily = TerminalFont,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RetroActionItem(
    icon: String,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val accent = if (isDestructive) androidx.compose.ui.graphics.Color.Red else LocalAccentColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = if (isDestructive) accent else TerminalWhite, fontSize = 11.sp, fontFamily = TerminalFont, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsView(
    prefs: android.content.SharedPreferences,
    bgMode: String,
    onBgModeChange: (String) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    hapticEnabled: Boolean,
    onHapticChange: (Boolean) -> Unit,
    keepAwake: Boolean,
    onKeepAwakeChange: (Boolean) -> Unit,
    defaultScreen: String,
    onDefaultScreenChange: (String) -> Unit,
    accentMode: String,
    onAccentModeChange: (String) -> Unit,
    accentFixedColorStr: String,
    onAccentFixedColorChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "— SYSTEM & UI PREFERENCES —",
            color = TerminalWhite,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            fontFamily = TerminalFont,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // --- Category 1: THEME & DISPLAY ---
        LogSectionHeader("THEME & DISPLAY")
        
        Text("BACKGROUND STYLE", color = TerminalGray, fontFamily = TerminalFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RetroButton("SOLID BLACK", {
                onBgModeChange("BLACK")
                prefs.edit().putString("bg_mode", "BLACK").apply()
            }, isSelected = bgMode == "BLACK", modifier = Modifier.weight(1f))
            
            RetroButton("BLUR WALLPAPER", {
                onBgModeChange("BLUR")
                prefs.edit().putString("bg_mode", "BLUR").apply()
            }, isSelected = bgMode == "BLUR", modifier = Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text("ACCENT COLOR MODE", color = TerminalGray, fontFamily = TerminalFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RetroButton("DYNAMIC (ALBUM)", {
                onAccentModeChange("DYNAMIC")
                prefs.edit().putString("accent_mode", "DYNAMIC").apply()
            }, isSelected = accentMode == "DYNAMIC", modifier = Modifier.weight(1f))
            
            RetroButton("FIXED PALETTE", {
                onAccentModeChange("FIXED")
                prefs.edit().putString("accent_mode", "FIXED").apply()
            }, isSelected = accentMode == "FIXED", modifier = Modifier.weight(1f))
        }
        
        if (accentMode == "FIXED") {
            val colors = listOf("#00FF00", "#00FFFF", "#FF00FF", "#FFFF00", "#FF5555", "#5555FF")
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                colors.forEach { hex ->
                    val colorObj = try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)) } catch(e:Exception) { androidx.compose.ui.graphics.Color.White }
                    val isCurrent = accentFixedColorStr == hex
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .border(
                                2.dp,
                                if (isCurrent) Color.White else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .background(colorObj, RoundedCornerShape(4.dp))
                            .clickable {
                                onAccentFixedColorChange(hex)
                                prefs.edit().putString("accent_fixed_color", hex).apply()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCurrent) {
                            Text("✓", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        // --- Category 2: TYPOGRAPHY & TOUCH ---
        LogSectionHeader("TYPOGRAPHY & TOUCH")
        
        Text("GLOBAL FONT SCALE", color = TerminalGray, fontFamily = TerminalFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RetroButton("-", { 
                val newScale = (fontScale - 0.1f).coerceAtLeast(0.8f)
                onFontScaleChange(newScale)
                prefs.edit().putFloat("font_scale", newScale).apply()
            }, modifier = Modifier.width(50.dp))
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, LocalAccentColor.current.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${String.format("%.1f", fontScale)}x",
                    color = LocalAccentColor.current,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            
            RetroButton("+", { 
                val newScale = (fontScale + 0.1f).coerceAtMost(1.5f)
                onFontScaleChange(newScale)
                prefs.edit().putFloat("font_scale", newScale).apply()
            }, modifier = Modifier.width(50.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("HAPTIC FEEDBACK (BUTTON TOUCH)", color = TerminalGray, fontFamily = TerminalFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            RetroButton(
                if (hapticEnabled) "[✓] HAPTIC ENABLED" else "[ ] HAPTIC DISABLED",
                {
                    onHapticChange(!hapticEnabled)
                    prefs.edit().putBoolean("haptic_enabled", !hapticEnabled).apply()
                },
                isSelected = hapticEnabled,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        // --- Category 3: PLAYBACK & LAUNCH ---
        LogSectionHeader("PLAYBACK & LAUNCH")
        
        Text("KEEP SCREEN AWAKE", color = TerminalGray, fontFamily = TerminalFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            RetroButton(
                if (keepAwake) "[✓] ACTIVE (NOW PLAYING)" else "[ ] DISABLED",
                {
                    onKeepAwakeChange(!keepAwake)
                    prefs.edit().putBoolean("keep_awake", !keepAwake).apply()
                },
                isSelected = keepAwake,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("DEFAULT SCREEN ON LAUNCH", color = TerminalGray, fontFamily = TerminalFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val screens = listOf("LIBRARY", "PLAYLIST", "TRACK")
            screens.forEach { screen ->
                val label = if (screen == "TRACK") "NOW PLAYING" else screen
                RetroButton(
                    label,
                    {
                        onDefaultScreenChange(screen)
                        prefs.edit().putString("default_screen", screen).apply()
                    },
                    isSelected = defaultScreen == screen,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryView(
    currentDirectory: File,
    filesInDir: List<File>,
    playingFile: File?,
    playlistSet: Set<String>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onFileSelected: (File) -> Unit,
    onFileLongPressed: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val filteredIndexedFiles = remember(filesInDir, searchQuery) {
        val indexed = filesInDir.mapIndexed { index, file -> index to file }
        if (searchQuery.isEmpty()) indexed
        else indexed.filter { it.second.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "— FILE LIBRARY —",
            color = Color.White,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            fontFamily = TerminalFont,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        
        // Styled Search Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, LocalAccentColor.current.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .background(LocalAccentColor.current.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(">", color = LocalAccentColor.current, fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(6.dp))
            TextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = LocalAccentColor.current, fontFamily = TerminalFont, fontSize = 11.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = LocalAccentColor.current
                ),
                placeholder = { Text("SEARCH FILES...", color = TerminalGray.copy(alpha = 0.6f), fontFamily = TerminalFont, fontSize = 11.sp) },
                singleLine = true
            )
            if (searchQuery.isNotEmpty()) {
                Text(
                    "✕",
                    color = TerminalGray,
                    fontSize = 12.sp,
                    fontFamily = TerminalFont,
                    modifier = Modifier.clickable { onSearchChange("") }.padding(4.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (searchQuery.isEmpty() && currentDirectory.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .border(1.dp, LocalAccentColor.current.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .background(LocalAccentColor.current.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                            .clickable { onFileSelected(File("..")) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📁 [ .. ]", color = LocalAccentColor.current, fontWeight = FontWeight.Bold, fontFamily = TerminalFont, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Up to parent folder", color = TerminalGray, fontFamily = TerminalFont, fontSize = 11.sp)
                    }
                }
            }

            items(filteredIndexedFiles) { (originalIndex, file) ->
                val isDir = file.isDirectory
                val isPlaying = file.absolutePath == playingFile?.absolutePath
                val inPlaylist = playlistSet.contains(file.absolutePath)
                val ext = if (isDir) "DIR" else file.extension.uppercase().ifEmpty { "FILE" }
                val displayName = file.name
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.5.dp)
                        .border(
                            1.dp,
                            if (isPlaying) LocalAccentColor.current.copy(alpha = 0.5f) else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .background(
                            if (isPlaying) LocalAccentColor.current.copy(alpha = 0.12f) else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .combinedClickable(
                            onClick = { onFileSelected(file) },
                            onLongClick = {
                                if (!isDir) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onFileLongPressed(file)
                                }
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .border(
                                1.dp,
                                (if (isDir) TerminalWhite else if (isPlaying) LocalAccentColor.current else TerminalGray).copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = ext,
                            color = if (isDir) TerminalWhite else if (isPlaying) LocalAccentColor.current else TerminalGray,
                            fontSize = 9.sp,
                            fontFamily = TerminalFont
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = displayName,
                        color = if (isPlaying) LocalAccentColor.current.blendWithWhite(0.7f) else TerminalWhite,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = TerminalFont,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (inPlaylist && !isDir) {
                        Text("★", color = LocalAccentColor.current, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    
                    if (isPlaying && !isDir) {
                        Text("▶", color = LocalAccentColor.current, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistView(
    playlistPaths: List<String>,
    playingFile: File?,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    priorityQueueSize: Int,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onFileSelected: (File) -> Unit,
    onFileRemoved: (String) -> Unit,
    onClearPlaylist: () -> Unit = {},
    onTrackLongPressed: (File) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "— PLAYLIST (${playlistPaths.size}) —",
                color = Color.White,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                fontFamily = TerminalFont,
                fontWeight = FontWeight.Bold
            )
            if (playlistPaths.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .border(1.dp, androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .clickable { onClearPlaylist() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "CLEAR ALL",
                        color = androidx.compose.ui.graphics.Color.Red,
                        fontSize = 9.sp,
                        fontFamily = TerminalFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Playlist Quick Controls Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(
                        1.dp,
                        if (isShuffleEnabled) LocalAccentColor.current else LocalAccentColor.current.copy(alpha = 0.25f),
                        RoundedCornerShape(4.dp)
                    )
                    .background(
                        if (isShuffleEnabled) LocalAccentColor.current.copy(alpha = 0.15f) else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onToggleShuffle() }
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isShuffleEnabled) "🔀 SHUF" else "🔀 OFF",
                    color = if (isShuffleEnabled) LocalAccentColor.current else TerminalGray,
                    fontSize = 10.sp,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(
                        1.dp,
                        if (repeatMode != RepeatMode.OFF) LocalAccentColor.current else LocalAccentColor.current.copy(alpha = 0.25f),
                        RoundedCornerShape(4.dp)
                    )
                    .background(
                        if (repeatMode != RepeatMode.OFF) LocalAccentColor.current.copy(alpha = 0.15f) else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onCycleRepeat() }
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                val rText = when (repeatMode) {
                    RepeatMode.OFF -> "🔁 OFF"
                    RepeatMode.ALL -> "🔁 ALL"
                    RepeatMode.ONE -> "🔂 ONE"
                }
                Text(
                    text = rText,
                    color = if (repeatMode != RepeatMode.OFF) LocalAccentColor.current else TerminalGray,
                    fontSize = 10.sp,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .border(
                        1.dp,
                        if (priorityQueueSize > 0) LocalAccentColor.current else LocalAccentColor.current.copy(alpha = 0.25f),
                        RoundedCornerShape(4.dp)
                    )
                    .background(
                        if (priorityQueueSize > 0) LocalAccentColor.current.copy(alpha = 0.15f) else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onOpenQueue() }
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "☰ QUEUE ($priorityQueueSize)",
                    color = if (priorityQueueSize > 0) LocalAccentColor.current else TerminalWhite,
                    fontSize = 10.sp,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        if (playlistPaths.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp)
                    .border(1.dp, TerminalGray.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("[ PLAYLIST EMPTY ]", color = TerminalGray, fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Long-press any audio track in Library\nto add it to your playback queue.",
                        color = TerminalGray.copy(alpha = 0.7f),
                        fontFamily = TerminalFont,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(playlistPaths.mapIndexed { index, path -> index to path }) { (index, path) ->
                    val file = File(path)
                    val isPlaying = file.absolutePath == playingFile?.absolutePath
                    val ext = file.extension.uppercase().ifEmpty { "AUDIO" }
                    val displayName = file.nameWithoutExtension.ifEmpty { file.name }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.5.dp)
                            .border(
                                1.dp,
                                if (isPlaying) LocalAccentColor.current.copy(alpha = 0.5f) else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .background(
                                if (isPlaying) LocalAccentColor.current.copy(alpha = 0.12f) else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .combinedClickable(
                                onClick = { onFileSelected(file) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTrackLongPressed(file)
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format("%02d.", index + 1),
                            color = if (isPlaying) LocalAccentColor.current else TerminalGray,
                            fontFamily = TerminalFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .border(1.dp, (if (isPlaying) LocalAccentColor.current else TerminalGray).copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = ext,
                                color = if (isPlaying) LocalAccentColor.current else TerminalGray,
                                fontSize = 9.sp,
                                fontFamily = TerminalFont
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isPlaying) LocalAccentColor.current.blendWithWhite(0.7f) else TerminalWhite,
                            fontFamily = TerminalFont,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isPlaying) {
                            Text(
                                "▶",
                                color = LocalAccentColor.current,
                                fontSize = 10.sp,
                                fontFamily = TerminalFont,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clickable { onFileRemoved(path) }
                                .padding(4.dp)
                        ) {
                            Text("✕", color = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontFamily = TerminalFont, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RetroButton(
    text: String,
    onClick: () -> Unit,
    color: Color = TerminalWhite,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val isAccent = isSelected || color == LocalAccentColor.current
    Box(
        modifier = modifier
            .border(1.dp, if (isAccent) LocalAccentColor.current else color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .background(
                if (isAccent) LocalAccentColor.current.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isAccent) LocalAccentColor.current else color,
            fontFamily = TerminalFont,
            fontWeight = if (isAccent) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TrackView(
    track: TrackInfo?,
    playbackPosition: Double,
    isPlaying: Boolean,
    currentVolume: Float,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    priorityQueueSize: Int,
    onTogglePlay: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrev: () -> Unit,
    onStop: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dynamicColor = LocalAccentColor.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (track == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .border(1.dp, TerminalGray.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("NO TRACK SELECTED", color = TerminalGray, style = MaterialTheme.typography.titleMedium, fontFamily = TerminalFont)
            }
            return
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .aspectRatio(1f)
                .border(1.5.dp, dynamicColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            if (track.coverArt != null) {
                Image(
                    bitmap = track.coverArt.asImageBitmap(),
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF141414), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("[ NO COVER ART ]", color = TerminalGray, style = MaterialTheme.typography.titleMedium, fontFamily = TerminalFont)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = track.title,
            color = TerminalWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = TerminalFont,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .border(1.dp, dynamicColor.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                    .background(dynamicColor.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = track.codec,
                    color = dynamicColor,
                    fontSize = 10.sp,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = track.artist,
                color = dynamicColor,
                fontSize = 13.sp,
                fontFamily = TerminalFont,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        val curMin = (playbackPosition / 60).toInt()
        val curSec = (playbackPosition % 60).toInt()
        val totMin = (track.durationSeconds / 60).toInt()
        val totSec = (track.durationSeconds % 60).toInt()
        val posStr = String.format("%d:%02d", curMin, curSec)
        val durStr = String.format("%d:%02d", totMin, totSec)
        val progress = if (track.durationSeconds > 0) (playbackPosition / track.durationSeconds).coerceIn(0.0, 1.0) else 0.0

        val barLength = 20
        val filled = (progress * barLength).toInt().coerceIn(0, barLength)
        val empty = barLength - filled
        val barStr = "▓".repeat(filled) + "░".repeat(empty)
        
        Row(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(posStr, color = TerminalWhite, fontFamily = TerminalFont, fontSize = 11.sp)
            Text("[$barStr]", color = dynamicColor, fontFamily = TerminalFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(durStr, color = TerminalGray, fontFamily = TerminalFont, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dedicated Volume Control Row
        val volPercent = (currentVolume * 100).roundToInt()
        Row(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .border(1.dp, dynamicColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .clickable { onVolumeChange((currentVolume - 0.05f).coerceAtLeast(0.0f)) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("-", color = TerminalWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .border(1.dp, dynamicColor.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "VOL: $volPercent%",
                    color = dynamicColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .border(1.dp, dynamicColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .clickable { onVolumeChange((currentVolume + 0.05f).coerceAtMost(1.0f)) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("+", color = TerminalWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Transport Media Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .border(1.dp, dynamicColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .clickable { onPlayPrev() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("|<<", color = dynamicColor, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
            }
            
            Box(
                modifier = Modifier
                    .border(1.5.dp, dynamicColor, RoundedCornerShape(8.dp))
                    .background(dynamicColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .clickable { onTogglePlay() }
                    .padding(horizontal = 28.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isPlaying) "❚❚" else "▶",
                    color = dynamicColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont
                )
            }
            
            Box(
                modifier = Modifier
                    .border(1.dp, dynamicColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .clickable { onPlayNext() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(">>|", color = dynamicColor, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Secondary Playback Modes Row: Shuffle, Queue, Repeat
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(
                        1.dp,
                        if (isShuffleEnabled) dynamicColor else dynamicColor.copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp)
                    )
                    .background(
                        if (isShuffleEnabled) dynamicColor.copy(alpha = 0.15f) else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onToggleShuffle() }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isShuffleEnabled) "🔀 SHUF: ON" else "🔀 SHUF: OFF",
                    color = if (isShuffleEnabled) dynamicColor else TerminalGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont
                )
            }
            
            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .border(
                        1.dp,
                        if (priorityQueueSize > 0) dynamicColor else dynamicColor.copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp)
                    )
                    .background(
                        if (priorityQueueSize > 0) dynamicColor.copy(alpha = 0.15f) else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onOpenQueue() }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "☰ QUEUE ($priorityQueueSize)",
                    color = if (priorityQueueSize > 0) dynamicColor else TerminalWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(
                        1.dp,
                        if (repeatMode != RepeatMode.OFF) dynamicColor else dynamicColor.copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp)
                    )
                    .background(
                        if (repeatMode != RepeatMode.OFF) dynamicColor.copy(alpha = 0.15f) else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onCycleRepeat() }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                val repText = when (repeatMode) {
                    RepeatMode.OFF -> "🔁 REP: OFF"
                    RepeatMode.ALL -> "🔁 REP: ALL"
                    RepeatMode.ONE -> "🔂 REP: 1"
                }
                Text(
                    text = repText,
                    color = if (repeatMode != RepeatMode.OFF) dynamicColor else TerminalGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont
                )
            }
        }
    }
}

@Composable
fun MiniPlayerView(
    track: TrackInfo,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onNavigateToNowPlaying() }
            .background(LocalAccentColor.current.copy(alpha = 0.15f), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val modifierImage = Modifier
            .size(28.dp)
            .border(1.dp, LocalAccentColor.current.copy(alpha = 0.4f))
            .background(LocalAccentColor.current.copy(alpha = 0.2f))
            
        if (track.coverArt != null) {
            Image(
                bitmap = track.coverArt.asImageBitmap(),
                contentDescription = "Cover",
                modifier = modifierImage,
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = modifierImage)
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = "${track.title} — ${track.artist}",
            color = LocalAccentColor.current.blendWithWhite(0.7f),
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 10.sp,
            fontFamily = TerminalFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = if (isPlaying) "❚❚" else "▶",
            color = LocalAccentColor.current.blendWithWhite(0.4f),
            fontFamily = TerminalFont,
            fontSize = 13.sp,
            modifier = Modifier
                .clickable { onTogglePlay() }
                .padding(8.dp)
        )
    }
}

@Composable
fun BottomNavigationBar(currentView: ViewState, onNavClick: (ViewState) -> Unit) {
    val items = listOf(
        ViewState.LIBRARY to "LIBRARY",
        ViewState.PLAYLIST to "PLAYLIST",
        ViewState.TRACK to "NOW PLAYING",
        ViewState.SETTINGS to "SETTINGS"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Divider(color = LocalAccentColor.current.copy(alpha = 0.25f), thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (view, label) ->
                val isSelected = currentView == view
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .border(
                            1.dp,
                            if (isSelected) LocalAccentColor.current else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .background(
                            if (isSelected) LocalAccentColor.current.copy(alpha = 0.15f) else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onNavClick(view) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSelected) "[$label]" else label,
                        color = if (isSelected) LocalAccentColor.current else TerminalGray,
                        fontSize = 10.sp,
                        fontFamily = TerminalFont,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// Ekstrak metadata & Palette Warna
fun extractMetadata(file: File): TrackInfo {
    val retriever = MediaMetadataRetriever()
    var title = file.nameWithoutExtension
    var artist = "Unknown Artist"
    var album = "Unknown Album"
    var year = "----"
    var durationSec = 0.0
    var bitmap: Bitmap? = null
    var dominantColor = Color(0xFF00FF00) // Default Terminal Green

    try {
        retriever.setDataSource(file.absolutePath)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { title = it }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { artist = it }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { album = it }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.let { year = it }
        
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { ms ->
            durationSec = ms / 1000.0
        }

        val artBytes = retriever.embeddedPicture
        if (artBytes != null) {
            bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
            if (bitmap != null) {
                val palette = Palette.from(bitmap).generate()
                val colorInt = palette.getVibrantColor(palette.getDominantColor(0xFF00FF00.toInt()))
                dominantColor = Color(colorInt)
            }
        }
    } catch (e: Exception) {
        // Abaikan jika error membaca metadata
    } finally {
        retriever.release()
    }

    return TrackInfo(file, title, artist, album, year, durationSec, bitmap, dominantColor)
}
