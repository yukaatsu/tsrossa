package com.yuka.musicplayer.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungAudioEffectHelperTest {

    @Test
    fun testSamsungDeviceDetection() {
        assertTrue(SamsungAudioEffectHelper.isSamsungDevice("samsung"))
        assertTrue(SamsungAudioEffectHelper.isSamsungDevice("SAMSUNG"))
        assertTrue(SamsungAudioEffectHelper.isSamsungDevice("Samsung Electronics"))
        assertFalse(SamsungAudioEffectHelper.isSamsungDevice("Google"))
        assertFalse(SamsungAudioEffectHelper.isSamsungDevice("Xiaomi"))
        assertFalse(SamsungAudioEffectHelper.isSamsungDevice("Sony"))
    }

    @Test
    fun testIntentActions() {
        val action = SamsungAudioEffectHelper.SOUND_ALIVE_ACTION
        assertEquals("com.sec.android.app.soundalive.SOUNDALIVE_MAIN", action)
        val fallbackAction = SamsungAudioEffectHelper.FALLBACK_SOUND_ACTION
        assertEquals("android.settings.SOUND_SETTINGS", fallbackAction)
    }

    private fun assertEquals(expected: String, actual: String?) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
