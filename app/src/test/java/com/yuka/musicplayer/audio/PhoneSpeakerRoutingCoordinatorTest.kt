package com.yuka.musicplayer.audio

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSpeakerRoutingCoordinatorTest {

    @Test
    fun isUsbDeviceType_identifiesAllUsbAudioTypes() {
        assertTrue(PhoneSpeakerRoutingCoordinator.isUsbDeviceType(PhoneSpeakerRoutingCoordinator.TYPE_USB_DEVICE))
        assertTrue(PhoneSpeakerRoutingCoordinator.isUsbDeviceType(PhoneSpeakerRoutingCoordinator.TYPE_USB_HEADSET))
        assertTrue(PhoneSpeakerRoutingCoordinator.isUsbDeviceType(PhoneSpeakerRoutingCoordinator.TYPE_USB_ACCESSORY))

        assertFalse(PhoneSpeakerRoutingCoordinator.isUsbDeviceType(PhoneSpeakerRoutingCoordinator.TYPE_BUILTIN_SPEAKER))
        assertFalse(PhoneSpeakerRoutingCoordinator.isUsbDeviceType(PhoneSpeakerRoutingCoordinator.TYPE_BUILTIN_EARPIECE))
        assertFalse(PhoneSpeakerRoutingCoordinator.isUsbDeviceType(PhoneSpeakerRoutingCoordinator.TYPE_BLUETOOTH_SCO))
        assertFalse(PhoneSpeakerRoutingCoordinator.isUsbDeviceType(PhoneSpeakerRoutingCoordinator.TYPE_WIRED_HEADSET))
    }

    @Test
    fun selectPhoneSpeakerDeviceType_prioritizesBuiltInSpeakerAndExcludesUsb() {
        // Only USB devices present -> returns null
        assertNull(
            PhoneSpeakerRoutingCoordinator.selectPhoneSpeakerDeviceType(
                listOf(
                    PhoneSpeakerRoutingCoordinator.TYPE_USB_DEVICE,
                    PhoneSpeakerRoutingCoordinator.TYPE_USB_HEADSET,
                    PhoneSpeakerRoutingCoordinator.TYPE_USB_ACCESSORY
                )
            )
        )

        // USB + Built-in Speaker + Earpiece + Bluetooth -> prioritizes Built-in Speaker ("speaker hp", #NOTE 1)
        assertEquals(
            PhoneSpeakerRoutingCoordinator.TYPE_BUILTIN_SPEAKER,
            PhoneSpeakerRoutingCoordinator.selectPhoneSpeakerDeviceType(
                listOf(
                    PhoneSpeakerRoutingCoordinator.TYPE_USB_HEADSET,
                    PhoneSpeakerRoutingCoordinator.TYPE_BUILTIN_EARPIECE,
                    PhoneSpeakerRoutingCoordinator.TYPE_BLUETOOTH_SCO,
                    PhoneSpeakerRoutingCoordinator.TYPE_BUILTIN_SPEAKER
                )
            )
        )

        // If Built-in Speaker is absent, falls back cleanly to non-USB communication device
        assertEquals(
            PhoneSpeakerRoutingCoordinator.TYPE_BUILTIN_EARPIECE,
            PhoneSpeakerRoutingCoordinator.selectPhoneSpeakerDeviceType(
                listOf(
                    PhoneSpeakerRoutingCoordinator.TYPE_USB_DEVICE,
                    PhoneSpeakerRoutingCoordinator.TYPE_BUILTIN_EARPIECE
                )
            )
        )
    }

    @Test
    fun shouldIgnoreAudioFocusLossWhenDacConnected_preservesCoexistence() {
        // When USB DAC is connected (PLAY or PAUSE/Standby), other apps on phone speaker never pause tsrossa
        assertTrue(
            PhoneSpeakerRoutingCoordinator.shouldIgnoreAudioFocusLossWhenDacConnected(
                isDacConnected = true,
                focusChange = AudioManager.AUDIOFOCUS_LOSS
            )
        )
        assertTrue(
            PhoneSpeakerRoutingCoordinator.shouldIgnoreAudioFocusLossWhenDacConnected(
                isDacConnected = true,
                focusChange = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
            )
        )
        assertTrue(
            PhoneSpeakerRoutingCoordinator.shouldIgnoreAudioFocusLossWhenDacConnected(
                isDacConnected = true,
                focusChange = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
            )
        )

        // When USB DAC is NOT connected, standard Android AudioFocus rules apply
        assertFalse(
            PhoneSpeakerRoutingCoordinator.shouldIgnoreAudioFocusLossWhenDacConnected(
                isDacConnected = false,
                focusChange = AudioManager.AUDIOFOCUS_LOSS
            )
        )
        assertFalse(
            PhoneSpeakerRoutingCoordinator.shouldIgnoreAudioFocusLossWhenDacConnected(
                isDacConnected = false,
                focusChange = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
            )
        )
        assertTrue(
            PhoneSpeakerRoutingCoordinator.shouldIgnoreAudioFocusLossWhenDacConnected(
                isDacConnected = false,
                focusChange = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
            )
        )
    }

    @Test
    fun shouldAttemptAudioSystemReflection_disabledOnAndroid10Plus() {
        // Android 8.0 (API 26) and Android 9 (API 28): legacy fallback allowed
        assertTrue(PhoneSpeakerRoutingCoordinator.shouldAttemptAudioSystemReflection(26))
        assertTrue(PhoneSpeakerRoutingCoordinator.shouldAttemptAudioSystemReflection(28))

        // Android 10 (API 29) through Android 16 (API 36): blocked hidden API -> MUST skip to prevent ART E/ log spam
        assertFalse(PhoneSpeakerRoutingCoordinator.shouldAttemptAudioSystemReflection(29))
        assertFalse(PhoneSpeakerRoutingCoordinator.shouldAttemptAudioSystemReflection(34))
        assertFalse(PhoneSpeakerRoutingCoordinator.shouldAttemptAudioSystemReflection(36))
    }

    @Test
    fun shouldThrottleRoutingEnforcement_preventsCallbackStormOnAndroid16() {
        // First call (lastEnforcedMs == 0) is never throttled
        assertFalse(PhoneSpeakerRoutingCoordinator.shouldThrottleRoutingEnforcement(nowMs = 1000L, lastEnforcedMs = 0L))

        // Rapid burst within 500ms window (e.g., Apple Music playback callbacks while tsrossa is idle) -> throttled
        assertTrue(PhoneSpeakerRoutingCoordinator.shouldThrottleRoutingEnforcement(nowMs = 1100L, lastEnforcedMs = 1000L))
        assertTrue(PhoneSpeakerRoutingCoordinator.shouldThrottleRoutingEnforcement(nowMs = 1499L, lastEnforcedMs = 1000L))

        // Once 500ms cooldown elapsed -> allowed again
        assertFalse(PhoneSpeakerRoutingCoordinator.shouldThrottleRoutingEnforcement(nowMs = 1500L, lastEnforcedMs = 1000L))
        assertFalse(PhoneSpeakerRoutingCoordinator.shouldThrottleRoutingEnforcement(nowMs = 2500L, lastEnforcedMs = 1000L))
    }

    @Test
    fun shouldUseLegacySpeakerphoneFallback_skipsRedundantToggleOnApi31ModeNormal() {
        // Android 12-16 (API 31-36) with setCommunicationDevice already applied in MODE_NORMAL -> skip deprecated toggle
        assertFalse(
            PhoneSpeakerRoutingCoordinator.shouldUseLegacySpeakerphoneFallback(
                sdkInt = 36,
                communicationDeviceApplied = true,
                audioMode = AudioManager.MODE_NORMAL
            )
        )
        // If call/VoIP mode is active -> allow speakerphone reinforcement
        assertTrue(
            PhoneSpeakerRoutingCoordinator.shouldUseLegacySpeakerphoneFallback(
                sdkInt = 36,
                communicationDeviceApplied = true,
                audioMode = AudioManager.MODE_IN_COMMUNICATION
            )
        )
        // If setCommunicationDevice failed or API < 31 -> allow legacy fallback
        assertTrue(
            PhoneSpeakerRoutingCoordinator.shouldUseLegacySpeakerphoneFallback(
                sdkInt = 36,
                communicationDeviceApplied = false,
                audioMode = AudioManager.MODE_NORMAL
            )
        )
        assertTrue(
            PhoneSpeakerRoutingCoordinator.shouldUseLegacySpeakerphoneFallback(
                sdkInt = 30,
                communicationDeviceApplied = true,
                audioMode = AudioManager.MODE_NORMAL
            )
        )
    }
}
