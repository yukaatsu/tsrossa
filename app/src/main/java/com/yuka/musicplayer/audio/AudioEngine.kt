package com.yuka.musicplayer.audio

import android.hardware.usb.UsbDeviceConnection

class AudioEngine {
    
    companion object {
        init {
            System.loadLibrary("kewmobile")
        }
    }


    /**
     * Memulai pemutaran audio secara Bit-Perfect di lapisan C++.
     * @param filePath Alur absolut ke file FLAC
     * @return 0 = success, -1 = file error, -2 = format incompatible, -3 = negotiation failure
     */
    external fun playAudio(filePath: String): Int
    external fun pauseAudio()
    external fun resumeAudio(): Boolean
    external fun stopAudio()
    external fun setSoftwareVolume(volume: Float)
    external fun isHardwareVolumeActive(): Boolean
    external fun getPosition(): Double
    external fun isFinished(): Boolean
    external fun isPlaying(): Boolean
    external fun isDacConnected(): Boolean
    external fun getSampleRate(): Int
    
    // Gapless Playback JNI
    external fun prepareNextTrack(filePath: String): Boolean
    external fun cleanGarbage()
    external fun closeUsbDac()

    // Phase 3: Diagnostic JNI Getters
    external fun getNegotiatedBitDepth(): Int
    external fun getUacVersion(): Int
    external fun getClaimedInterfaces(): String
    external fun getSourceSampleRate(): Int
    external fun getSourceBitDepth(): Int
    external fun getRecentErrorCount(): Int
    
    // UI State JNI Getters
    external fun isDeviceWedged(): Boolean
    external fun isSampleRateUnverified(): Boolean
    external fun isHardwareVolumeLockedBySystem(): Boolean
    external fun isForceSoftwareVolume(): Boolean

    // DAC Capabilities & Live I/O Monitor JNI Getters
    external fun getSupportedBitDepths(): String
    external fun getSupportedSampleRates(): String
    external fun getDacInfo(): String
    external fun getRefusedTrackHistory(): String
    external fun getOutputBitDepth(): Int
    external fun getOutputSampleRate(): Int

    // Callback dari C++ saat lagu habis
    var onTrackFinishedCallback: (() -> Unit)? = null
    // Callback dari C++ saat lagu berganti secara gapless
    var onTrackTransitionCallback: ((String) -> Unit)? = null
    // Callback dari C++ jika transfer USB terputus (Error Pipe)
    var onUsbStallFaultCallback: (() -> Unit)? = null
    // Callback dari C++ jika device dicabut paksa (Surprise Removal)
    var onDeviceForceDisconnectedCallback: (() -> Unit)? = null
    // Callback dari C++ jika format tidak didukung oleh DAC
    var onFormatIncompatibleCallback: ((String, Int, Int, String) -> Unit)? = null

    // Metode ini dipanggil oleh JNI (C++)
    fun onTrackFinished() {
        onTrackFinishedCallback?.invoke()
    }
    
    // Metode ini dipanggil oleh JNI (C++)
    fun onTrackTransition(nextFilePath: String) {
        onTrackTransitionCallback?.invoke(nextFilePath)
    }

    // Metode ini dipanggil oleh JNI (C++)
    fun onUsbStallFault() {
        onUsbStallFaultCallback?.invoke()
    }

    // Metode ini dipanggil oleh JNI (C++)
    fun onDeviceForceDisconnected() {
        onDeviceForceDisconnectedCallback?.invoke()
    }

    // Metode ini dipanggil oleh JNI (C++)
    fun onFormatIncompatible(filename: String, bitDepth: Int, sampleRate: Int, reason: String) {
        onFormatIncompatibleCallback?.invoke(filename, bitDepth, sampleRate, reason)
    }

    /**
     * Initializes the USB DAC through libusb using the provided file descriptor.
     * This bypasses Android's AudioFlinger.
     */
    external fun initUsbDac(fd: Int): Boolean
    
    fun connectToDac(connection: UsbDeviceConnection): Boolean {
        val fd = connection.fileDescriptor
        if (fd < 0) return false
        return initUsbDac(fd)
    }
}
