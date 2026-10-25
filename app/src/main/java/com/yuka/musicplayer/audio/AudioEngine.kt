package com.yuka.musicplayer.audio

import android.hardware.usb.UsbDeviceConnection

class AudioEngine {
    
    companion object {
        init {
            try {
                System.loadLibrary("kewmobile")
            } catch (e: UnsatisfiedLinkError) {
                // Ignore in JVM unit test environments where .so is not present
            }
        }

        @JvmStatic
        external fun setUsbBufferProfile(transfers: Int, packets: Int)

        @JvmStatic
        fun applyBufferProfile(profile: Int): Pair<Int, Int> {
            val (transfers, packets) = when (profile) {
                0 -> 16 to 32     // 64ms Low Latency (512 microframes)
                2 -> 24 to 64     // 192ms Maximum Stability (1536 microframes, strictly <= 2048 FRINDEX)
                else -> 20 to 64  // 160ms Balanced Robust (1280 microframes, Default)
            }
            setUsbBufferProfile(transfers, packets)
            return transfers to packets
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
    external fun seekTo(positionSeconds: Double): Boolean
    external fun isFinished(): Boolean
    external fun isPlaying(): Boolean
    external fun isDacConnected(): Boolean
    external fun getSampleRate(): Int
    
    // Gapless Playback JNI
    external fun prepareNextTrack(filePath: String): Boolean
    external fun clearNextTrack(): Boolean
    external fun cleanGarbage()
    external fun closeUsbDac()

    // Phase 3: Diagnostic JNI Getters
    external fun getNegotiatedBitDepth(): Int
    external fun getUacVersion(): Int
    external fun getClaimedInterfaces(): String
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
    external fun getLastUsbDiagnostic(): String
    external fun triggerWarmup(durationMs: Int): Boolean
    external fun cancelWarmup(): Boolean
    external fun isWarmingUp(): Boolean
    external fun getTimingTelemetry(): String
    external fun isBitPerfectIntegerPassActive(): Boolean

    // C++ callback when track playback completes
    var onTrackFinishedCallback: (() -> Unit)? = null
    // C++ callback when track transitions gaplessly
    var onTrackTransitionCallback: ((String) -> Unit)? = null
    // C++ callback if USB transfer stalls (Pipe Error)
    var onUsbStallFaultCallback: (() -> Unit)? = null
    // C++ callback if device is unexpectedly detached (Surprise Removal)
    var onDeviceForceDisconnectedCallback: (() -> Unit)? = null
    // C++ callback if audio format is unsupported by DAC
    var onFormatIncompatibleCallback: ((String, Int, Int, String) -> Unit)? = null

    // Called by JNI (C++)
    fun onTrackFinished() {
        onTrackFinishedCallback?.invoke()
    }
    
    // Called by JNI (C++)
    fun onTrackTransition(nextFilePath: String) {
        onTrackTransitionCallback?.invoke(nextFilePath)
    }

    // Called by JNI (C++)
    fun onUsbStallFault() {
        onUsbStallFaultCallback?.invoke()
    }

    // Called by JNI (C++)
    fun onDeviceForceDisconnected() {
        onDeviceForceDisconnectedCallback?.invoke()
    }

    // Called by JNI (C++)
    fun onFormatIncompatible(filename: String, bitDepth: Int, sampleRate: Int, reason: String) {
        onFormatIncompatibleCallback?.invoke(filename, bitDepth, sampleRate, reason)
    }

    /**
     * Initializes the USB DAC through libusb using the provided file descriptor.
     * This bypasses Android's AudioFlinger.
     */
    external fun initUsbDac(fd: Int): Boolean
}
