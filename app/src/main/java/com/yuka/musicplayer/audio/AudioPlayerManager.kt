package com.yuka.musicplayer.audio

import android.content.Context
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
    var currentPlaybackSource: PlaybackSource = PlaybackSource.LIBRARY
    var playlistPaths: List<String> = emptyList()
    var priorityQueue: List<File> = emptyList()

    fun initialize(context: Context) {
        if (isInitialized) return
        
        audioEngine = AudioEngine()
        usbAudioController = UsbAudioController(context)
        isInitialized = true
    }

    fun release() {
        if (!isInitialized) return
        usbAudioController.release()
        audioEngine.pauseAudio()
        currentTrack = null
        isPlaying = false
        priorityQueue = emptyList()
        isInitialized = false
    }
}
