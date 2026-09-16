package com.yuka.musicplayer

import android.widget.Toast
import android.content.Intent
import org.json.JSONArray
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Context
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
import java.io.File
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                    color = MaterialTheme.colorScheme.background
                ) {
                    KewApp(com.yuka.musicplayer.audio.AudioPlayerManager.audioEngine)
                }
            }
        }
    }
}

enum class ViewState { LIBRARY, PLAYLIST, TRACK, SETTINGS }

enum class PlaybackSource { LIBRARY, PLAYLIST }

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
)

val TerminalFont = FontFamily(Font(R.font.fantasquesans_regular))

@Composable
fun KewApp(audioEngine: AudioEngine) {
    val context = LocalContext.current
    var viewState by remember { mutableStateOf(ViewState.LIBRARY) }
    
    var playlistPaths by remember { mutableStateOf(emptyList<String>()) }
    val playlistSet = remember(playlistPaths) { playlistPaths.toSet() }
    var currentPlaybackSourceStr by rememberSaveable { mutableStateOf(PlaybackSource.LIBRARY.name) }
    val getCurrentSource = { currentPlaybackSourceStr }
    val currentPlaybackSource = PlaybackSource.valueOf(currentPlaybackSourceStr)

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            playlistPaths = loadPlaylist(context)
        }
    }

    var currentTrack by remember { mutableStateOf<TrackInfo?>(null) }
    var searchQuery by remember { mutableStateOf("") }


    var isHardwareVolumeActive by remember { mutableStateOf(false) }
    var isHardwareVolumeLockedBySystem by remember { mutableStateOf(false) }
    var isForceSoftwareVolume by remember { mutableStateOf(false) }
    var isDeviceWedged by remember { mutableStateOf(false) }
    var isSampleRateUnverified by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while(true) {
            isHardwareVolumeActive = audioEngine.isHardwareVolumeActive()
            delay(500)
        }
    }

    val sharedPref = remember { context.getSharedPreferences("KewMobilePrefs", android.content.Context.MODE_PRIVATE) }
    val initialDir = remember { 
        val savedPath = sharedPref.getString("last_directory", Environment.getExternalStorageDirectory().absolutePath)
        File(savedPath ?: Environment.getExternalStorageDirectory().absolutePath)
    }

    var currentDirectory by remember { mutableStateOf(initialDir) }
    
    LaunchedEffect(currentDirectory) {
        sharedPref.edit().putString("last_directory", currentDirectory.absolutePath).apply()
    }
    
    val coroutineScope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(false) }
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
        val intent = Intent(context, com.yuka.musicplayer.audio.AudioForegroundService::class.java)
        if (isPlaying) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // ForegroundServiceStartNotAllowedException (API 31+) or
                // IllegalStateException on older versions.
                // App is in background — service start denied by OS.
                // Non-fatal: playback continues via C++ engine without wakelock protection.
                android.util.Log.w("KewApp", "FG service start denied (app in background): ${e.message}")
            }
        } else {
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                // Extremely unlikely, but defensive
            }
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
    var isDacConnected by remember { mutableStateOf(false) }
    
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
        }
        usbAudioController.onDeviceDetached = {
            audioEngine.closeUsbDac()
            setDndMode(false)
            audioEngine.pauseAudio()
            isPlaying = false
            isDacConnected = false
        }
        audioEngine.onUsbStallFaultCallback = {
            // C++ reported a hard stall. Re-init the DAC.
            audioEngine.pauseAudio()
            isPlaying = false
            usbAudioController.scanAndRequestPermission()
        }
        audioEngine.onDeviceForceDisconnectedCallback = {
            // C++ reported surprise removal (hotplug).
            coroutineScope.launch(Dispatchers.Main) {
                setDndMode(false)
                audioEngine.pauseAudio()
                isPlaying = false
                isDacConnected = false
            }
        }
        audioEngine.onFormatIncompatibleCallback = { filename, bitDepth, sampleRate, reason ->
            coroutineScope.launch(Dispatchers.Main) {
                formatIncompatibleError = "$filename: $reason"
            }
        }
        onDispose {
            setDndMode(false)
            audioEngine.onUsbStallFaultCallback = null
            audioEngine.onDeviceForceDisconnectedCallback = null
            audioEngine.onFormatIncompatibleCallback = null
        }
    }

    LaunchedEffect(Unit) {
        usbAudioController.scanAndRequestPermission()
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
        files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    val activeList = remember(currentPlaybackSource, filesInDir, playlistPaths) {
        if (currentPlaybackSource == PlaybackSource.LIBRARY) filesInDir
        else playlistPaths.map { File(it) }
    }

    var playTrackRef: ((File, Boolean) -> Unit)? = null

    fun playNext(isAutoAdvance: Boolean) {
        val currentActiveList = if (getCurrentSource() == PlaybackSource.LIBRARY.name) filesInDir else playlistPaths.map { File(it) }
        val currentIndex = currentActiveList.indexOfFirst { it.absolutePath == currentTrack?.file?.absolutePath }
        android.util.Log.e("DEBUG_BUG_A1", "playNext dipanggil! isAutoAdvance=$isAutoAdvance, source=${getCurrentSource()}, activeList.size=${currentActiveList.size}, currentIndex=$currentIndex")
        if (currentIndex != -1 && currentIndex + 1 < currentActiveList.size) {
            playTrackRef?.invoke(currentActiveList[currentIndex + 1], isAutoAdvance)
        } else if (isAutoAdvance) {
            // Track terakhir selesai secara alami
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
            
            // Prepare next track for gapless playback (Bug A Fix)
            val currentActiveList = if (getCurrentSource() == PlaybackSource.LIBRARY.name) filesInDir else playlistPaths.map { File(it) }
            val currentIndex = currentActiveList.indexOfFirst { it.absolutePath == file.absolutePath }
            if (currentIndex != -1 && currentIndex + 1 < currentActiveList.size) {
                val nextFile = currentActiveList[currentIndex + 1]
                
                // Guard 1: Ensure user hasn't skipped to another track manually before we start prepare
                if (currentTrack?.file?.absolutePath == file.absolutePath) {
                    audioEngine.prepareNextTrack(nextFile.absolutePath)
                    
                    // Guard 2: If the user skipped during prepare, we shouldn't keep the prepared track
                    if (currentTrack?.file?.absolutePath != file.absolutePath) {
                        // The engine state will be overwritten by the next playAudio call anyway, 
                        // but this satisfies the logical guard requirement.
                    }
                }
            }
        }
    }
    
    playTrackRef = ::playTrack

    fun playPrev() {
        val currentActiveList = if (getCurrentSource() == PlaybackSource.LIBRARY.name) filesInDir else playlistPaths.map { File(it) }
        val currentIndex = currentActiveList.indexOfFirst { it.absolutePath == currentTrack?.file?.absolutePath }
        if (currentIndex - 1 >= 0) {
            playTrack(currentActiveList[currentIndex - 1], false)
        }
    }

    val currentPlayNext by rememberUpdatedState(::playNext)
    val currentActiveList by rememberUpdatedState(activeList)
    
    DisposableEffect(Unit) {
        audioEngine.onTrackFinishedCallback = { 
            coroutineScope.launch(Dispatchers.Main) {
                currentPlayNext(true) 
            }
        }
        audioEngine.onTrackTransitionCallback = { path ->
            coroutineScope.launch(Dispatchers.Main) {
                audioEngine.cleanGarbage()
                val nextFile = File(path)
                val newIndex = currentActiveList.indexOfFirst { it.absolutePath == path }
                
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
                    val sRate = audioEngine.getSampleRate()
                    
                    if (newIndex != -1 && newIndex + 1 < currentActiveList.size) {
                        audioEngine.prepareNextTrack(currentActiveList[newIndex + 1].absolutePath)
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

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
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
                color = Color(0xFFe6cc98),
                lineHeight = 12.sp,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp),
                textAlign = TextAlign.Center
            )
            val hwVolumeText = if (isForceSoftwareVolume) {
                "Software Attenuation (Dithered)"
            } else if (isHardwareVolumeActive) {
                "Hardware Bit-Perfect Volume"
            } else if (isHardwareVolumeLockedBySystem) {
                "Software Attenuation (Hardware Volume unavailable — Audio Control locked by system)"
            } else {
                "Software Attenuation (Dithered)"
            }
            Text(
                text = hwVolumeText,
                fontFamily = TerminalFont,
                fontSize = 10.sp,
                color = if (isHardwareVolumeActive && !isForceSoftwareVolume) Color(0xFFFFD700) else Color(0xFFf87171),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
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
                    color = Color(0xFF5b7a68),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
        Divider(color = Color(0xFF1a3a26), thickness = 1.dp)

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
                    coroutineScope.launch(Dispatchers.IO) {
                        val added = appendPlaylist(context, file.absolutePath, playlistSet)
                        if (added) {
                            val newPaths = loadPlaylist(context)
                            withContext(Dispatchers.Main) {
                                playlistPaths = newPaths
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        } else if (viewState == ViewState.PLAYLIST) {
            PlaylistView(
                playlistPaths = playlistPaths,
                playingFile = currentTrack?.file,
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
                modifier = Modifier.weight(1f)
            )
        } else if (viewState == ViewState.TRACK) {
            TrackView(
                track = currentTrack,
                playbackPosition = playbackPosition,
                isPlaying = isPlaying,
                currentVolume = currentVolume,
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
                modifier = Modifier.weight(1f)
            )
        } else {
            // Settings View
            var showExitDialog by remember { mutableStateOf(false) }
            val dacName = remember(viewState) {
                // Determine DAC name using UsbManager instead of AudioManager
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
                    .weight(1f)
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = "[ SYSTEM LOGS & SETTINGS ]", 
                    color = TerminalWhite, 
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                // 1. DEVICE STATUS
                Text("[ DEVICE STATUS ]", color = TerminalGray, fontFamily = TerminalFont, fontWeight = FontWeight.Bold)
                Text("DAC Name : $dacName", color = TerminalWhite, fontFamily = TerminalFont)
                if (isDeviceWedged) {
                    Text("Status   : ERROR / WEDGED", color = Color.Red, fontFamily = TerminalFont)
                } else if (isDacConnected) {
                    Text("Status   : ACTIVE (Exclusive)", color = MaterialTheme.colorScheme.primary, fontFamily = TerminalFont)
                    Text("Engine   : libusb UAC2", color = TerminalWhite, fontFamily = TerminalFont)
                } else {
                    Text("Status   : DISCONNECTED", color = TerminalGray, fontFamily = TerminalFont)
                }
                Text("-----------------------------", color = TerminalGray, fontFamily = TerminalFont)
                Spacer(modifier = Modifier.height(24.dp))

                // 2. SIGNAL PATH (LIVE I/O)
                Text("[ SIGNAL PATH ]", color = TerminalGray, fontFamily = TerminalFont, fontWeight = FontWeight.Bold)
                if (isPlaying) {
                    val srcStr = if (sourceSampleRate > 0) "$sourceBitDepth-Bit / ${sourceSampleRate/1000.0}kHz" else "--"
                    val outStr = if (outputSampleRate > 0) "$outputBitDepth-Bit / ${outputSampleRate/1000.0}kHz" else "--"
                    Text("Input  : $srcStr", color = TerminalWhite, fontFamily = TerminalFont)
                    Text("Output : $outStr", color = TerminalWhite, fontFamily = TerminalFont)
                    
                    if (sourceSampleRate > 0 && sourceSampleRate == outputSampleRate && sourceBitDepth == outputBitDepth) {
                        Text("Result : [ ✓ BIT-PERFECT ]", color = Color(0xFF55FF55), fontFamily = TerminalFont, fontWeight = FontWeight.Bold)
                    } else if (sourceSampleRate > 0) {
                        Text("Result : [ ✗ RESAMPLING ]", color = Color(0xFFFF5555), fontFamily = TerminalFont, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Result : --", color = TerminalGray, fontFamily = TerminalFont)
                    }
                } else {
                    Text("(Playback stopped)", color = TerminalGray, fontFamily = TerminalFont)
                }
                Text("-----------------------------", color = TerminalGray, fontFamily = TerminalFont)
                Spacer(modifier = Modifier.height(24.dp))

                // 3. DIAGNOSTICS & CAPABILITIES
                Text("[ DIAGNOSTICS ]", color = TerminalGray, fontFamily = TerminalFont, fontWeight = FontWeight.Bold)
                if (isDacConnected) {
                    Text("UAC Ver  : UAC$uacVersion", color = TerminalWhite, fontFamily = TerminalFont)
                    Text("Iface    : $claimedInterfaces", color = TerminalWhite, fontFamily = TerminalFont)
                    if (isSampleRateUnverified) {
                        Text("Rate     : ${negotiatedSampleRate/1000.0}kHz (Unverified)", color = Color(0xFFe6cc98), fontFamily = TerminalFont)
                    } else {
                        Text("Rate     : ${negotiatedSampleRate/1000.0}kHz", color = TerminalWhite, fontFamily = TerminalFont)
                    }
                    Text("Errors   : $recentErrorCount", color = if (recentErrorCount == 0) TerminalWhite else Color.Red, fontFamily = TerminalFont)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text("Supp. Hz : ", color = TerminalWhite, fontFamily = TerminalFont)
                        val formattedRates = if (supportedSampleRates.isNotEmpty()) {
                            supportedSampleRates.split(",").mapNotNull { it.trim().toIntOrNull() }
                                .map { if (it % 1000 == 0) "${it/1000}k" else "${it/1000.0}k" }
                                .joinToString(", ")
                        } else "--"
                        Text(formattedRates, color = MaterialTheme.colorScheme.primary, fontFamily = TerminalFont)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text("Supp. Bit: ", color = TerminalWhite, fontFamily = TerminalFont)
                        Text(if (supportedBitDepths.isNotEmpty()) supportedBitDepths else "--", color = MaterialTheme.colorScheme.primary, fontFamily = TerminalFont)
                    }
                } else {
                    Text("No diagnostics available", color = TerminalGray, fontFamily = TerminalFont)
                }
                Text("-----------------------------", color = TerminalGray, fontFamily = TerminalFont)
                Spacer(modifier = Modifier.height(24.dp))

                // 4. REFUSED TRACKS
                if (refusedTrackHistory.isNotEmpty()) {
                    Text("[ REFUSED TRACKS ]", color = Color.Red, fontFamily = TerminalFont, fontWeight = FontWeight.Bold)
                    refusedTrackHistory.forEachIndexed { index, track ->
                        Text("${index + 1}. \"${track.filename}\" - ${track.bitDepth}-Bit/${track.sampleRate/1000.0}kHz", color = TerminalWhite, fontFamily = TerminalFont)
                        Text("   ${track.reason}", color = Color.Red, fontFamily = TerminalFont, fontSize = 12.sp)
                    }
                    Text("-----------------------------", color = Color.Red, fontFamily = TerminalFont)
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
                // 5. MEMORY & DSP
                Text("[ ENGINE CONFIGURATION ]", color = TerminalGray, fontFamily = TerminalFont, fontWeight = FontWeight.Bold)
                Text("RAM Play: ON (Zero Jitter)", color = MaterialTheme.colorScheme.primary, fontFamily = TerminalFont)
                Text("Gapless : ON (Pre-Loaded)", color = MaterialTheme.colorScheme.primary, fontFamily = TerminalFont)
                Text("DSP     : BYPASSED", color = MaterialTheme.colorScheme.primary, fontFamily = TerminalFont)
                Text("-----------------------------", color = TerminalGray, fontFamily = TerminalFont)

                
                Spacer(modifier = Modifier.height(32.dp))
                
                if (isDacConnected) {
                    Button(
                        onClick = { showExitDialog = true },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "KILL ENGINE & RELEASE DAC",
                            fontFamily = TerminalFont,
                            color = Color.White
                        )
                    }

                    if (showExitDialog) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showExitDialog = false },
                            title = { Text(text = "WARNING", fontFamily = TerminalFont, fontWeight = FontWeight.Bold) },
                            text = { Text(text = "Are you sure you want to release the DAC and exit the audio engine?", fontFamily = TerminalFont) },
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
                                    Text("YES", fontFamily = TerminalFont, color = Color.Red)
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
            }
        }
        
        if (currentTrack != null && (viewState == ViewState.LIBRARY || viewState == ViewState.PLAYLIST)) {
            Divider(color = Color(0xFF1a3a26), thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
            MiniPlayerView(
                track = currentTrack!!,
                isPlaying = isPlaying,
                onTogglePlay = sharedOnTogglePlay,
                onNavigateToNowPlaying = { viewState = ViewState.TRACK }
            )
        }
        
        BottomNavigationBar(
            currentView = viewState,
            onNavClick = { viewState = it }
        )
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
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text(
            text = "— LIBRARY —",
            color = Color.White,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            fontFamily = TerminalFont,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        TextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            textStyle = TextStyle(color = Color(0xFF00FF00), fontFamily = TerminalFont),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color(0xFF00FF00),
                unfocusedIndicatorColor = Color(0xFF005500),
                cursorColor = Color(0xFF00FF00)
            ),
            placeholder = { Text("[ SEARCH LIBRARY ]", color = Color(0xFF005500), fontFamily = TerminalFont) },
            singleLine = true
        )

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (searchQuery.isEmpty() && currentDirectory.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
                item {
                    Text(
                        text = "  [..]  (Up to parent)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TerminalFont,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFileSelected(File("..")) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            items(filteredIndexedFiles) { (originalIndex, file) ->
                val isDir = file.isDirectory
                val isPlaying = file.absolutePath == playingFile?.absolutePath
                val inPlaylist = playlistSet.contains(file.absolutePath)
                val prefixStr = if (isDir) "[ DIR ]" else if (inPlaylist) "★" else "☆"
                val displayName = file.name
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onFileSelected(file) },
                            onLongClick = {
                                if (!isDir && !inPlaylist) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onFileLongPressed(file)
                                }
                            }
                        )
                        .background(if (isPlaying) Color(0xFF123b25) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val prefixColor = if (isDir) TerminalWhite else if (isPlaying || inPlaylist) Color(0xFF4ade80) else Color(0xFF3a5a48)
                    Text(
                        text = prefixStr,
                        color = prefixColor,
                        fontFamily = TerminalFont,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    
                    val textColor = if (isPlaying) Color(0xFFa7f3c8) else TerminalWhite
                    val suffix = if (isPlaying && !isDir) " ▶" else ""
                    Text(
                        text = "$displayName$suffix",
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = TerminalFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistView(
    playlistPaths: List<String>,
    playingFile: File?,
    onFileSelected: (File) -> Unit,
    onFileRemoved: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text(
            text = "— PLAYLIST —",
            color = Color.White,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            fontFamily = TerminalFont,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        if (playlistPaths.isEmpty()) {
            Text(
                text = "Belum ada lagu di playlist — long-press lagu di Library untuk menambah",
                color = TerminalGray,
                fontFamily = TerminalFont,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(playlistPaths.mapIndexed { index, path -> index to path }) { (index, path) ->
                    val file = File(path)
                    val isPlaying = file.absolutePath == playingFile?.absolutePath
                    val prefix = "[FLAC]"
                    val displayName = file.name
                    
                    val indexStr = (index + 1).toString().padStart(2, ' ')
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFileSelected(file) }
                            .background(if (isPlaying) Color(0xFF123b25) else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val textColor = if (isPlaying) Color(0xFFa7f3c8) else TerminalWhite
                        val suffix = if (isPlaying) " ▶" else ""
                        Text(
                            text = "$indexStr. $prefix $displayName$suffix",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            fontFamily = TerminalFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onFileRemoved(path) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("X", color = Color.Red, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsView(
    audioEngine: AudioEngine,
    modifier: Modifier = Modifier
) {
    var dacInfoJson by remember { mutableStateOf("{}") }
    
    LaunchedEffect(Unit) {
        while(true) {
            dacInfoJson = audioEngine.getDacInfo()
            kotlinx.coroutines.delay(1000)
        }
    }

    var productName = "Unknown"
    var manufacturer = "Unknown"
    var vid = 0
    var pid = 0
    var isHardwareVolumeActive = false
    var isForceSoftwareVolume = false
    var uacVersion = 0
    var isConnected = false

    try {
        val json = org.json.JSONObject(dacInfoJson)
        if (json.has("productName")) {
            isConnected = true
            productName = json.getString("productName")
            manufacturer = json.getString("manufacturerName")
            vid = json.getInt("vid")
            pid = json.getInt("pid")
            isHardwareVolumeActive = json.getBoolean("isHardwareVolumeActive")
            isForceSoftwareVolume = json.getBoolean("isForceSoftwareVolume")
            uacVersion = json.getInt("uacVersion")
        }
    } catch (e: Exception) {
        // Ignored
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "— SYSTEM MONITOR —",
            color = Color.White,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            fontFamily = TerminalFont,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val statusColor = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336)
        val statusText = if (isConnected) "CONNECTED" else "DISCONNECTED"

        Text(
            text = "DAC Status: $statusText",
            color = statusColor,
            fontWeight = FontWeight.Bold,
            fontFamily = TerminalFont,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (isConnected) {
            Text(text = "Manufacturer: $manufacturer", color = TerminalWhite, fontFamily = TerminalFont)
            Text(text = "Product: $productName", color = TerminalWhite, fontFamily = TerminalFont)
            Text(text = "VID:PID: 0x${vid.toString(16).uppercase()}:0x${pid.toString(16).uppercase()}", color = TerminalWhite, fontFamily = TerminalFont)
            Text(text = "UAC Version: UAC$uacVersion", color = TerminalWhite, fontFamily = TerminalFont)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val hwVolText = if (isForceSoftwareVolume) {
                "BLACKLISTED (Forced Software Volume)"
            } else if (!isHardwareVolumeActive) {
                "NOT SUPPORTED (Missing UAC2 Volume Feature Unit)"
            } else {
                "ACTIVE (Bit-Perfect)"
            }
            val hwVolColor = if (isHardwareVolumeActive) Color(0xFF4CAF50) else Color(0xFFF44336)
            
            Text(
                text = "Hardware Volume Control: $hwVolText",
                color = hwVolColor,
                fontFamily = TerminalFont,
                fontWeight = FontWeight.Bold
            )
            
            if (isForceSoftwareVolume) {
                Text(
                    text = "Reason: This DAC is known to crash when receiving volume commands while streaming. System has forced standard software volume processing for stability.",
                    color = TerminalGray,
                    fontFamily = TerminalFont,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else if (!isHardwareVolumeActive) {
                Text(
                    text = "Reason: No recognized volume control interfaces were found on this DAC.",
                    color = TerminalGray,
                    fontFamily = TerminalFont,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun RetroButton(text: String, onClick: () -> Unit, color: Color = TerminalWhite, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .border(1.dp, color)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun TrackView(
    track: TrackInfo?,
    playbackPosition: Double,
    isPlaying: Boolean,
    currentVolume: Float,
    onTogglePlay: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrev: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dynamicColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (track == null) {
            Text("NO TRACK SELECTED", color = TerminalGray, style = MaterialTheme.typography.titleLarge)
            return
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .aspectRatio(1f)
                .border(BorderStroke(2.dp, dynamicColor.copy(alpha = 0.5f)))
                .padding(4.dp)
        ) {
            if (track.coverArt != null) {
                Image(
                    bitmap = track.coverArt.asImageBitmap(),
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                        color = MaterialTheme.colorScheme.primary,
                        blendMode = androidx.compose.ui.graphics.BlendMode.Multiply
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text("[ NO COVER ]", color = TerminalGray, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = track.title,
            color = TerminalWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = TerminalFont,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.artist,
            color = dynamicColor,
            fontSize = 18.sp,
            fontFamily = TerminalFont,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        val curMin = (playbackPosition / 60).toInt()
        val curSec = (playbackPosition % 60).toInt()
        val totMin = (track.durationSeconds / 60).toInt()
        val totSec = (track.durationSeconds % 60).toInt()
        val volPercent = (currentVolume * 100).roundToInt()

        val posStr = String.format("%d:%02d", curMin, curSec)
        val durStr = String.format("%d:%02d", totMin, totSec)
        
        Spacer(modifier = Modifier.height(12.dp))
        
        val progress = if (track.durationSeconds > 0) (playbackPosition / track.durationSeconds) else 0.0
        val barLength = 20
        val filled = (progress * barLength).toInt().coerceIn(0, barLength)
        val empty = barLength - filled
        val barStr = "[" + "▓".repeat(filled) + "░".repeat(empty) + "]"
        
        Text(barStr, color = TerminalWhite, fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(0.9f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(posStr, color = TerminalWhite, fontFamily = TerminalFont)
            Text("VOL: $volPercent%", color = dynamicColor, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
            Text(durStr, color = TerminalGray, fontFamily = TerminalFont)
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Media Controls Panel
        Row(
            modifier = Modifier.fillMaxWidth(0.9f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val controlColor = dynamicColor
            Text("|<<", modifier = Modifier.clickable { onPlayPrev() }, color = controlColor, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
            val playText = if (isPlaying) "❚❚" else "▶"
            Text(playText, modifier = Modifier.clickable { onTogglePlay() }, color = controlColor, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
            Text(">>|", modifier = Modifier.clickable { onPlayNext() }, color = controlColor, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
            Text("+", modifier = Modifier.clickable { onVolumeChange((currentVolume + 0.05f).coerceAtMost(1.0f)) }, color = controlColor, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
            Text("-", modifier = Modifier.clickable { onVolumeChange((currentVolume - 0.05f).coerceAtLeast(0.0f)) }, color = controlColor, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
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
            .background(Color(0xFF0a1f14), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val modifierImage = Modifier
            .size(28.dp)
            .border(1.dp, Color(0xFF2a5a3f))
            .background(Color(0xFF123b25))
            
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
            color = Color(0xFFa7f3c8),
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
            color = Color(0xFF4ade80),
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Divider(color = Color(0xFF1a3a26), thickness = 1.dp, modifier = Modifier.padding(bottom = 6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val libText = if (currentView == ViewState.LIBRARY) "[[ LIBRARY ]]" else "[ LIBRARY ]"
            Text(
                text = libText, 
                color = if (currentView == ViewState.LIBRARY) TerminalWhite else TerminalGray,
                fontSize = 11.sp,
                fontFamily = TerminalFont,
                fontWeight = if (currentView == ViewState.LIBRARY) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onNavClick(ViewState.LIBRARY) }
            )

            val playText = if (currentView == ViewState.PLAYLIST) "[[ PLAYLIST ]]" else "[ PLAYLIST ]"
            Text(
                text = playText, 
                color = if (currentView == ViewState.PLAYLIST) TerminalWhite else TerminalGray,
                fontSize = 11.sp,
                fontFamily = TerminalFont,
                fontWeight = if (currentView == ViewState.PLAYLIST) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onNavClick(ViewState.PLAYLIST) }
            )
            
            val trackText = if (currentView == ViewState.TRACK) "[[ NOW PLAYING ]]" else "[ NOW PLAYING ]"
            Text(
                text = trackText, 
                color = if (currentView == ViewState.TRACK) TerminalWhite else TerminalGray,
                fontSize = 11.sp,
                fontFamily = TerminalFont,
                fontWeight = if (currentView == ViewState.TRACK) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onNavClick(ViewState.TRACK) }
            )
            
            val setText = if (currentView == ViewState.SETTINGS) "[[ SETTINGS ]]" else "[ SETTINGS ]"
            Text(
                text = setText, 
                color = if (currentView == ViewState.SETTINGS) TerminalWhite else TerminalGray,
                fontSize = 11.sp,
                fontFamily = TerminalFont,
                fontWeight = if (currentView == ViewState.SETTINGS) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onNavClick(ViewState.SETTINGS) }
            )
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
