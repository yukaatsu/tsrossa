package com.yuka.musicplayer.audio

import android.content.Context
import android.util.Log
import com.yuka.musicplayer.PlaybackSource
import com.yuka.musicplayer.TrackInfo
import java.io.File

object AudioPlayerManager {
    lateinit var audioEngine: AudioEngine
    lateinit var usbAudioController: UsbAudioController
    
    var isInitialized = false
        private set

    var currentTrack: TrackInfo? = null
    var isPlaying: Boolean = false
    var isDacConnected: Boolean = false
    var currentPlaybackSource: PlaybackSource = PlaybackSource.LIBRARY
    var playlistPaths: List<String> = emptyList()
    var priorityQueue: List<File> = emptyList()

    // Callbacks registered by UI / Playback coordinator (MainActivity)
    var onPlayNext: (() -> Unit)? = null
    var onPlayPrev: (() -> Unit)? = null
    var onTogglePlay: (() -> Unit)? = null
    var onDacDetached: (() -> Unit)? = null
    var onDacAttached: (() -> Unit)? = null
    var onNotificationUpdateRequired: (() -> Unit)? = null

    fun initialize(context: Context) {
        if (isInitialized) return
        
        audioEngine = AudioEngine()
        usbAudioController = UsbAudioController(context)
        
        // Connect USB controller lifecycle directly to AudioPlayerManager
        usbAudioController.onDeviceReady = { fd ->
            UsbAudioController.log("AudioPlayerManager onDeviceReady: calling audioEngine.initUsbDac(FD=$fd)")
            isDacConnected = audioEngine.initUsbDac(fd)
            UsbAudioController.log("AudioPlayerManager onDeviceReady: initUsbDac returned $isDacConnected")
            if (!isDacConnected) {
                Log.e("AudioPlayerManager", "initUsbDac failed for FD $fd! Closing USB connection to avoid wedged state.")
                usbAudioController.closeDevice()
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
        onTogglePlay?.invoke()
    }

    fun handleDacDetached() {
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
