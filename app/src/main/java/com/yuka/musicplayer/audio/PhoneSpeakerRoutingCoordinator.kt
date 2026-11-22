package com.yuka.musicplayer.audio

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Ensures that whenever the USB DAC is exclusively owned by tsrossa — both during PLAY and during
 * PAUSE / Standby (#NOTE 1) — any other app or Android system sound (notifications, incoming phone
 * calls, voice recording, video/music apps) is automatically redirected to the phone's internal
 * speaker & built-in microphone, while keeping 0% OS interference on tsrossa's direct libusb stream
 * (#NOTE 2 & #NOTE 3: 100% untouched commit a2f60f8 C++ audio core).
 */
object PhoneSpeakerRoutingCoordinator {
    private const val TAG = "PhoneSpeakerRouting"

    // Numeric constants mirrored from AudioDeviceInfo for JVM unit testability
    const val TYPE_BUILTIN_EARPIECE = 1
    const val TYPE_BUILTIN_SPEAKER = 2
    const val TYPE_WIRED_HEADSET = 3
    const val TYPE_WIRED_HEADPHONES = 4
    const val TYPE_BLUETOOTH_SCO = 7
    const val TYPE_BLUETOOTH_A2DP = 8
    const val TYPE_USB_DEVICE = 11
    const val TYPE_USB_ACCESSORY = 12
    const val TYPE_USB_HEADSET = 22
    const val TYPE_BLE_HEADSET = 26
    const val TYPE_BLE_SPEAKER = 27

    // Reflection constants from android.media.AudioSystem (Android 8-9 legacy fallback only)
    private const val FOR_COMMUNICATION = 0
    private const val FOR_MEDIA = 1
    private const val FOR_RECORD = 2
    private const val FOR_SYSTEM = 4
    private const val FORCE_NONE = 0
    private const val FORCE_SPEAKER = 1
    const val MIN_ENFORCEMENT_INTERVAL_MS = 500L

    @Volatile
    private var isRoutingActive = false

    @Volatile
    private var previousSpeakerphoneState = false

    @Volatile
    private var didOverrideSpeakerphone = false

    @Volatile
    private var didOverrideCommunicationDevice = false

    @Volatile
    private var lastEnforcementTimeMs = 0L

    @Volatile
    private var audioSystemReflectionUnavailable = false

    private var cachedSetForceUseMethod: java.lang.reflect.Method? = null

    private var registeredAudioManager: AudioManager? = null
    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null

    @JvmStatic
    fun isUsbDeviceType(deviceType: Int): Boolean {
        return deviceType == TYPE_USB_DEVICE ||
            deviceType == TYPE_USB_HEADSET ||
            deviceType == TYPE_USB_ACCESSORY
    }

    /**
     * Selects the phone speaker (or best available non-USB output fallback) so other apps,
     * notifications, and incoming calls route to the phone speaker instead of the locked USB DAC.
     */
    @JvmStatic
    fun selectPhoneSpeakerDeviceType(availableTypes: List<Int>): Int? {
        val nonUsb = availableTypes.filterNot { isUsbDeviceType(it) }
        if (nonUsb.isEmpty()) return null
        val priority = listOf(
            TYPE_BUILTIN_SPEAKER,
            TYPE_BLUETOOTH_SCO,
            TYPE_BLE_HEADSET,
            TYPE_BLUETOOTH_A2DP,
            TYPE_BLE_SPEAKER,
            TYPE_WIRED_HEADSET,
            TYPE_WIRED_HEADPHONES,
            TYPE_BUILTIN_EARPIECE
        )
        for (type in priority) {
            if (nonUsb.contains(type)) return type
        }
        return nonUsb.first()
    }

    /**
     * Coexistence rule (#NOTE 1):
     * When the USB DAC is connected to tsrossa (both during PLAY and PAUSE/Standby),
     * tsrossa outputs to the external USB DAC while other apps output to the phone speaker.
     * Therefore, AudioFocus changes on the phone speaker must never pause tsrossa.
     */
    @JvmStatic
    fun shouldIgnoreAudioFocusLossWhenDacConnected(isDacConnected: Boolean, focusChange: Int): Boolean {
        if (isDacConnected) {
            return true
        }
        return focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
    }

    /**
     * Android 10+ (API 29+, including Android 16 / API 36) permanently blocks AudioSystem.setForceUse
     * via Non-SDK hidden API enforcement and requires signature permission MODIFY_AUDIO_ROUTING.
     * Skipping reflection on API 29+ prevents ART/libart from logging E/ hidden-API errors into
     * tsrossa's process logcat whenever another app (e.g. Apple Music) triggers AudioPlaybackCallback.
     */
    @JvmStatic
    fun shouldAttemptAudioSystemReflection(sdkInt: Int): Boolean {
        return sdkInt < Build.VERSION_CODES.Q
    }

    /**
     * Cooldown debounce preventing rapid AudioPlaybackCallback / AudioRecordingCallback bursts
     * (e.g., when Apple Music loads & buffers while tsrossa is idle) from spamming Binder IPCs.
     */
    @JvmStatic
    fun shouldThrottleRoutingEnforcement(
        nowMs: Long,
        lastEnforcedMs: Long,
        minIntervalMs: Long = MIN_ENFORCEMENT_INTERVAL_MS
    ): Boolean {
        if (lastEnforcedMs <= 0L) return false
        val elapsed = nowMs - lastEnforcedMs
        return elapsed >= 0L && elapsed < minIntervalMs
    }

    /**
     * On Android 12+ (API 31+), setCommunicationDevice() supersedes deprecated isSpeakerphoneOn.
     * Avoid redundantly setting isSpeakerphoneOn=true in MODE_NORMAL when communicationDevice is active.
     */
    @JvmStatic
    fun shouldUseLegacySpeakerphoneFallback(
        sdkInt: Int,
        communicationDeviceApplied: Boolean,
        audioMode: Int
    ): Boolean {
        if (sdkInt < Build.VERSION_CODES.S) return true
        if (!communicationDeviceApplied) return true
        return audioMode != AudioManager.MODE_NORMAL
    }

    /**
     * Activated as soon as the USB DAC is initialized by tsrossa (onDeviceReady — covering
     * initial Standby/PAUSE as well as PLAY and subsequent PAUSE states).
     */
    @Synchronized
    fun onDacConnected(context: Context) {
        val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        registeredAudioManager = am
        isRoutingActive = true
        lastEnforcementTimeMs = 0L

        enforcePhoneSpeakerRouting(am, force = true)
        registerSystemActivityCallbacks(am)
        Log.i(TAG, "Phone speaker redirection activated for PLAY & PAUSE (Standby) while USB DAC is connected.")
    }

    /**
     * Deactivated only when the USB DAC is physically unplugged or the app is closed.
     */
    @Synchronized
    fun onDacDisconnected(context: Context? = null) {
        val am = registeredAudioManager
            ?: (context?.applicationContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
        isRoutingActive = false
        lastEnforcementTimeMs = 0L
        unregisterSystemActivityCallbacks(am)
        if (am != null) {
            restoreDefaultRouting(am)
        }
        registeredAudioManager = null
        Log.i(TAG, "USB DAC disconnected: restored default system audio routing.")
    }

    @Synchronized
    fun enforcePhoneSpeakerRouting(am: AudioManager?, force: Boolean = false) {
        if (am == null || !isRoutingActive) return
        val nowMs = System.currentTimeMillis()
        if (!force && shouldThrottleRoutingEnforcement(nowMs, lastEnforcementTimeMs)) {
            return
        }
        lastEnforcementTimeMs = nowMs
        try {
            var commDeviceReady = false
            // 1. Android 12+ (API 31+): Direct communication & capture routing to Built-in Speaker
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val commDevices = am.availableCommunicationDevices
                val targetType = selectPhoneSpeakerDeviceType(commDevices.map { it.type })
                val targetDevice = targetType?.let { t -> commDevices.firstOrNull { it.type == t } }
                if (targetDevice != null) {
                    if (am.communicationDevice?.id == targetDevice.id) {
                        commDeviceReady = true
                    } else if (am.setCommunicationDevice(targetDevice)) {
                        didOverrideCommunicationDevice = true
                        commDeviceReady = true
                    }
                }
            }

            // 2. Legacy speakerphone fallback (API < 31, or when setCommunicationDevice wasn't applied / call active)
            if (shouldUseLegacySpeakerphoneFallback(Build.VERSION.SDK_INT, commDeviceReady, am.mode)) {
                @Suppress("DEPRECATION")
                if (!didOverrideSpeakerphone) {
                    previousSpeakerphoneState = am.isSpeakerphoneOn
                    didOverrideSpeakerphone = true
                }
                @Suppress("DEPRECATION")
                if (!am.isSpeakerphoneOn) {
                    am.isSpeakerphoneOn = true
                }
            }

            // 3. Best-effort AudioSystem.setForceUse ONLY on Android 8-9 (API < 29) where hidden API isn't blocked
            applyAudioSystemForceSpeaker(enable = true)
        } catch (t: Throwable) {
            Log.w(TAG, "enforcePhoneSpeakerRouting warning: ${t.message}")
        }
    }

    private fun restoreDefaultRouting(am: AudioManager) {
        try {
            applyAudioSystemForceSpeaker(enable = false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && didOverrideCommunicationDevice) {
                am.clearCommunicationDevice()
                didOverrideCommunicationDevice = false
            }
            if (didOverrideSpeakerphone) {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = previousSpeakerphoneState
                didOverrideSpeakerphone = false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "restoreDefaultRouting warning: ${t.message}")
        }
    }

    private fun applyAudioSystemForceSpeaker(enable: Boolean) {
        if (!shouldAttemptAudioSystemReflection(Build.VERSION.SDK_INT) || audioSystemReflectionUnavailable) {
            return
        }
        try {
            val setForceUse = cachedSetForceUseMethod ?: run {
                val audioSystemClass = Class.forName("android.media.AudioSystem")
                audioSystemClass.getDeclaredMethod(
                    "setForceUse",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).apply {
                    isAccessible = true
                    cachedSetForceUseMethod = this
                }
            }
            val targetConfig = if (enable) FORCE_SPEAKER else FORCE_NONE
            setForceUse.invoke(null, FOR_COMMUNICATION, targetConfig)
            setForceUse.invoke(null, FOR_MEDIA, targetConfig)
            setForceUse.invoke(null, FOR_SYSTEM, targetConfig)
            setForceUse.invoke(null, FOR_RECORD, FORCE_NONE)
        } catch (_: Throwable) {
            // Permanently disable reflection after first failure so VM never logs repeated denials
            audioSystemReflectionUnavailable = true
            cachedSetForceUseMethod = null
        }
    }

    private fun registerSystemActivityCallbacks(am: AudioManager) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playbackCallback == null) {
                val pbCb = object : AudioManager.AudioPlaybackCallback() {
                    override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                        if (isRoutingActive && !configs.isNullOrEmpty()) {
                            // Another app or system notification started playing audio (throttled to avoid callback churn)
                            enforcePhoneSpeakerRouting(am, force = false)
                        }
                    }
                }
                playbackCallback = pbCb
                am.registerAudioPlaybackCallback(pbCb, mainHandler)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && recordingCallback == null) {
                val recCb = object : AudioManager.AudioRecordingCallback() {
                    override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
                        if (isRoutingActive && !configs.isNullOrEmpty()) {
                            // Another app (Call, WhatsApp, Voice Recorder) requested microphone input
                            enforcePhoneSpeakerRouting(am, force = false)
                        }
                    }
                }
                recordingCallback = recCb
                am.registerAudioRecordingCallback(recCb, mainHandler)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register audio activity callbacks: ${t.message}")
        }
    }

    private fun unregisterSystemActivityCallbacks(am: AudioManager?) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                playbackCallback?.let { am?.unregisterAudioPlaybackCallback(it) }
            }
            playbackCallback = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                recordingCallback?.let { am?.unregisterAudioRecordingCallback(it) }
            }
            recordingCallback = null
        } catch (_: Throwable) {}
    }
}
