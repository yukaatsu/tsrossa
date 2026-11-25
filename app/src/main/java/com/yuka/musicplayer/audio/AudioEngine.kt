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

        /**
         * Converts a normalized UI slider value [0.0f..1.0f] into a perceptual attenuation in dB.
         * Uses the calibrated Audiophile Studio Taper curve: dB(v) = -48.0 * (1 - v) * sqrt(1 - v)
         * - 100% (1.00) ->   0.00 dB (Bit-Perfect Unity)
         * -  75% (0.75) ->  -6.00 dB
         * -  50% (0.50) -> -16.97 dB (Powerful punch & full dynamics)
         * -  10% (0.10) -> -40.98 dB
         * -   5% (0.05) -> -44.45 dB (Safe quiet floor)
         * -   0% (0.00) -> -Infinity (Mute)
         */
        @JvmStatic
        fun sliderToPerceptualDb(slider: Float): Float {
            if (slider <= 0.0f) return Float.NEGATIVE_INFINITY
            if (slider >= 1.0f) return 0.0f
            val complement = 1.0f - slider
            return -48.0f * complement * kotlin.math.sqrt(complement)
        }

        @JvmStatic
        fun computePerceptualDb(slider: Double): Double {
            if (slider <= 0.0001) return -120.0
            if (slider >= 0.9999) return 0.0
            val inv = 1.0 - slider
            return -48.0 * inv * Math.sqrt(inv)
        }

        @JvmStatic
        fun sliderToLinearGain(slider: Float): Float {
            if (slider <= 0.0f) return 0.0f
            if (slider >= 1.0f) return 1.0f
            val db = sliderToPerceptualDb(slider)
            return Math.pow(10.0, (db / 20.0)).toFloat()
        }

        data class UacVolumeRange(val minDb: Short, val maxDb: Short, val resDb: Short)

        @JvmStatic
        fun sanitizeUacVolumeRange(minDb: Short, maxDb: Short, resDb: Short): UacVolumeRange {
            val minInt = minDb.toInt()
            val maxInt = maxDb.toInt()
            val isValid = (minInt < maxInt) && ((maxInt - minInt) >= (6 * 256))
            return if (isValid) {
                val step = if (resDb <= 0) 256.toShort() else resDb
                UacVolumeRange(minDb, maxDb, step)
            } else {
                // Safe standard fallback: -64 dB to 0 dB, step 1 dB
                UacVolumeRange((-16384).toShort(), 0.toShort(), 256.toShort())
            }
        }

        @JvmStatic
        fun quantizeHardwareVolumeTarget(
            slider: Float,
            minDb: Short,
            maxDb: Short,
            resDb: Short,
            preferHardwareVolume: Boolean
        ): Short {
            if (!preferHardwareVolume) {
                return maxDb
            }
            if (slider <= 0.0f) {
                return minDb
            }
            val desiredDb = sliderToPerceptualDb(slider)
            var rawTarget = maxDb.toInt() + Math.round(desiredDb * 256.0f).toInt()
            val step = if (resDb > 0) resDb.toInt() else 1
            if (step > 1) {
                rawTarget = ((rawTarget - maxDb.toInt()) / step) * step + maxDb.toInt()
            }
            if (rawTarget < minDb.toInt()) rawTarget = minDb.toInt()
            if (rawTarget > maxDb.toInt()) rawTarget = maxDb.toInt()
            return rawTarget.toShort()
        }

        /**
         * Calculates residual software attenuation to complement hardware attenuation.
         * When hardware volume is active, hardware attenuates by [appliedHardwareDb].
         * The residual dB is (desiredDb - appliedHardwareDb).
         * If residual dB < -0.05 dB, returns linear gain 10^(residualDb / 20.0).
         * Otherwise returns 1.0f (exact bit-perfect unity).
         */
        @JvmStatic
        fun computeResidualGain(slider: Float, appliedHardwareDb: Float): Float {
            if (slider <= 0.0f) return 0.0f
            if (slider >= 1.0f) return 1.0f
            val desiredDb = sliderToPerceptualDb(slider)
            val residualDb = desiredDb - appliedHardwareDb
            return if (residualDb < -0.05f) {
                Math.pow(10.0, (residualDb / 20.0)).toFloat()
            } else {
                1.0f
            }
        }

        /**
         * Pure-math mirror of C++ `decode_uac_feedback_q16()` for JVM unit verification.
         * Decodes UAC1 (10.14) / UAC2 (16.16) explicit feedback payloads with ALSA quirk
         * normalization and strict ±1.5% sanity bounds. Returns null if invalid/out-of-range.
         */
        @JvmStatic
        fun decodeUacFeedbackQ16(
            payload: ByteArray,
            uacVersion: Int,
            sampleRate: Int,
            usbFramesPerSec: Int
        ): Long? {
            if (payload.size < 3 || sampleRate <= 0 || usbFramesPerSec <= 0) return null
            val nomQ16 = (sampleRate.toLong() shl 16) / usbFramesPerSec.toLong()
            if (nomQ16 <= 0L) return null
            val minQ16 = (nomQ16 * 985L) / 1000L
            val maxQ16 = (nomQ16 * 1015L) / 1000L

            val b0 = payload[0].toLong() and 0xFFL
            val b1 = payload[1].toLong() and 0xFFL
            val b2 = payload[2].toLong() and 0xFFL
            val raw: Long = if (payload.size >= 4 && uacVersion == 2) {
                val b3 = payload[3].toLong() and 0xFFL
                b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
            } else {
                (b0 or (b1 shl 8) or (b2 shl 16)) shl 2
            }

            val candidates = longArrayOf(raw, raw shl 2, raw shl 3, raw ushr 3)
            for (c in candidates) {
                if (c in minQ16..maxQ16) return c
            }
            return null
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
    external fun isHardwareVolumeSupported(): Boolean
    external fun setHardwareVolumeEnabled(enabled: Boolean)
    external fun isHardwareVolumeEnabled(): Boolean
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

    // Professional 10-Band Parametric EQ (Direct USB C++ Engine)
    external fun setPeqEnabled(enabled: Boolean)
    external fun isPeqEnabled(): Boolean
    external fun setPeqPreampDb(preampDb: Float)
    external fun setPeqBand(bandIndex: Int, freqHz: Float, gainDb: Float, q: Float, filterType: Int, enabled: Boolean)
    external fun setPeqAllBands(freqsHz: FloatArray, gainsDb: FloatArray, qs: FloatArray, filterTypes: IntArray)
    external fun setPeqFullConfig(enabled: Boolean, preampDb: Float, freqsHz: FloatArray, gainsDb: FloatArray, qs: FloatArray, filterTypes: IntArray)
    external fun resetPeqState()

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
