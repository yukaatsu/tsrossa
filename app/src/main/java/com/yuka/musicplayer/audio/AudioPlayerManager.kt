package com.yuka.musicplayer.audio

import android.content.Context

object AudioPlayerManager {
    lateinit var audioEngine: AudioEngine
    lateinit var usbAudioController: UsbAudioController
    
    var isInitialized = false
        private set

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
        isInitialized = false
    }
}
