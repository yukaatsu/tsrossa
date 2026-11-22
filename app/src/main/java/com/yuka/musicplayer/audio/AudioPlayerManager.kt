package com.yuka.musicplayer.audio

import android.content.Context
import android.util.Log
import com.yuka.musicplayer.model.PlaybackSource
import com.yuka.musicplayer.model.TrackInfo
import java.io.File

object AudioPlayerManager {
    lateinit var audioEngine: AudioEngine
    lateinit var usbAudioController: UsbAudioController
    private var appContext: Context? = null
    
    var isInitialized = false
        private set

    var currentTrack: TrackInfo? = null
    var isPlaying: Boolean = false
    var isDacConnected: Boolean = false
    var currentPlaybackSource: PlaybackSource = PlaybackSource.LIBRARY
    var activePlayingPlaylistId: String? = null
    var playlistPaths: List<String> = emptyList()
    var priorityQueue: List<File> = emptyList()
    var hasEvaluatedStartupModalSession: Boolean = false

    // Callbacks registered by UI / Playback coordinator (MainActivity)
    var onPlayNext: (() -> Unit)? = null
    var onPlayPrev: (() -> Unit)? = null
    var onTogglePlay: (() -> Unit)? = null
    var onSeekTo: ((Double) -> Unit)? = null
    var onDacDetached: (() -> Unit)? = null
    var onDacAttached: (() -> Unit)? = null
    var onDacReady: ((String) -> Unit)? = null
    var onNotificationUpdateRequired: (() -> Unit)? = null

    fun initialize(context: Context) {
        if (isInitialized) return
        
        appContext = context.applicationContext
        audioEngine = AudioEngine()
        usbAudioController = UsbAudioController(context)

        val prefs = com.yuka.musicplayer.core.prefs.AppPreferences.get(context)
        val usbProfile = prefs.getInt("usb_buffer_profile", 1)
        AudioEngine.applyBufferProfile(usbProfile)
        val peqConfig = PeqPreferencesHelper.load(prefs)
        PeqPreferencesHelper.applyToEngine(audioEngine, peqConfig)
        
        // Connect USB controller lifecycle directly to AudioPlayerManager
        usbAudioController.onDeviceReady = { fd ->
            UsbAudioController.log("AudioPlayerManager onDeviceReady: calling audioEngine.initUsbDac(FD=$fd)")
            isDacConnected = audioEngine.initUsbDac(fd)
            UsbAudioController.log("AudioPlayerManager onDeviceReady: initUsbDac returned $isDacConnected")
            if (!isDacConnected) {
                Log.e("AudioPlayerManager", "initUsbDac failed for FD $fd! Closing USB connection to avoid wedged state.")
                usbAudioController.closeDevice()
            } else {
                val currentPrefs = com.yuka.musicplayer.core.prefs.AppPreferences.get(context)
                AudioEngine.applyBufferProfile(currentPrefs.getInt("usb_buffer_profile", 1))
                PeqPreferencesHelper.applyToEngine(audioEngine, PeqPreferencesHelper.load(currentPrefs))
                // Activate phone speaker redirection immediately for both Standby/PAUSE and PLAY (#NOTE 1)
                PhoneSpeakerRoutingCoordinator.onDacConnected(context.applicationContext)
                val dacName = try {
                    val infoJson = org.json.JSONObject(audioEngine.getDacInfo())
                    infoJson.optString("productName", "USB DAC")
                } catch (e: Exception) {
                    "USB DAC"
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onDacReady?.invoke(dacName)
                }
            }
            Log.i("AudioPlayerManager", "USB DAC initialized. Connected: $isDacConnected")
            notifyStateChanged()
        }
        
        usbAudioController.onDeviceDetached = {
            Log.w("AudioPlayerManager", "USB DAC physically detached. Handling safe teardown.")
            handleDacDetached()
        }
        
        isInitialized = true
    }

    fun requestPlayNext() {
        onPlayNext?.invoke()
    }

    fun requestPlayPrev() {
        onPlayPrev?.invoke()
    }

    fun requestTogglePlay() {
        if (onTogglePlay != null) {
            onTogglePlay?.invoke()
        } else if (isInitialized) {
            if (isPlaying) {
                audioEngine.pauseAudio()
                isPlaying = false
            } else if (isDacConnected) {
                val resumed = audioEngine.resumeAudio()
                isPlaying = resumed
            } else {
                isPlaying = false
            }
            notifyStateChanged()
        }
    }

    fun requestSeekTo(seconds: Double) {
        if (isInitialized) {
            audioEngine.seekTo(seconds)
        }
        onSeekTo?.invoke(seconds)
    }

    fun handleDacDetached() {
        PhoneSpeakerRoutingCoordinator.onDacDisconnected(appContext)
        audioEngine.closeUsbDac()
        audioEngine.pauseAudio()
        isPlaying = false
        isDacConnected = false
        notifyStateChanged()
        onDacDetached?.invoke()
    }

    fun handleDacAttached() {
        onDacAttached?.invoke()
    }

    fun notifyStateChanged() {
        onNotificationUpdateRequired?.invoke()
    }

    fun release() {
        if (!isInitialized) return
        PhoneSpeakerRoutingCoordinator.onDacDisconnected(appContext)
        try {
            audioEngine.pauseAudio()
            audioEngine.closeUsbDac()
        } catch (e: Exception) {
            Log.w("AudioPlayerManager", "Error closing audio engine during release: ${e.message}")
        }
        usbAudioController.release()
        currentTrack = null
        isPlaying = false
        isDacConnected = false
        priorityQueue = emptyList()
        isInitialized = false
    }
}
