package com.yuka.musicplayer.util

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Helper to detect Samsung One UI audio effect layers (Dolby Atmos, SoundAlive, Adapt Sound)
 * and provide intents to direct the user to disable spatialization / DSP processing for bit-perfect UAC playback.
 */
object SamsungAudioEffectHelper {

    const val SOUND_ALIVE_ACTION = "com.sec.android.app.soundalive.SOUNDALIVE_MAIN"
    const val FALLBACK_SOUND_ACTION = "android.settings.SOUND_SETTINGS"

    fun isSamsungDevice(manufacturer: String = Build.MANUFACTURER): Boolean {
        return manufacturer.contains("samsung", ignoreCase = true)
    }

    /**
     * Resolves the best intent to open Samsung's "Sound quality and effects" menu.
     */
    fun createSoundQualitySettingsIntent(): Intent {
        val intent = Intent(SOUND_ALIVE_ACTION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    /**
     * Fallback intent to standard sound settings if Samsung-specific action is unavailable.
     */
    fun createFallbackSoundSettingsIntent(): Intent {
        val intent = Intent(FALLBACK_SOUND_ACTION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    fun openSoundQualitySettings(context: Context): Boolean {
        return try {
            val intent = createSoundQualitySettingsIntent()
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                val fallback = createFallbackSoundSettingsIntent()
                context.startActivity(fallback)
                true
            }
        } catch (_: Exception) {
            try {
                context.startActivity(createFallbackSoundSettingsIntent())
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
