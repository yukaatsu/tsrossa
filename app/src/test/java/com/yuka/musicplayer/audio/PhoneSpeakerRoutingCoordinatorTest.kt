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
}
