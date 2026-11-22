package com.yuka.musicplayer.util

import com.yuka.musicplayer.utils.AppLogger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLoggerNoiseFilterTest {

    @Test
    fun testFrameworkNoiseTagsAreFiltered() {
        assertTrue(AppLogger.isNoisyFrameworkLog("InsetsController", "hide(ime(), fromIme=false)"))
        assertTrue(AppLogger.isNoisyFrameworkLog("ImeTracker", "onRequestHide at ORIGIN_CLIENT_HIDE_SOFT_INPUT"))
        assertTrue(AppLogger.isNoisyFrameworkLog("Choreographer", "Skipped 2 frames!"))
        assertTrue(AppLogger.isNoisyFrameworkLog("WindowOnBackDispatcher", "sendCancelIfRunning: dispatching cancel"))
        assertTrue(AppLogger.isNoisyFrameworkLog("PlayCore", "UID: [1024] ..."))
        assertTrue(AppLogger.isNoisyFrameworkLog("ProfileInstaller", "Installing profile for ..."))
        assertTrue(AppLogger.isNoisyFrameworkLog("VRI[MainActivity]", "relayout: viewVisibility=0"))
    }

    @Test
    fun testFrameworkNoiseMessagesAreFiltered() {
        assertTrue(AppLogger.isNoisyFrameworkLog("SomeTag", "Transient error: PHASE_CLIENT_ALREADY_HIDDEN"))
        assertTrue(AppLogger.isNoisyFrameworkLog("SomeTag", "Handling ViewPostIme pointer 0"))
        assertTrue(AppLogger.isNoisyFrameworkLog("SomeTag", "call setFrameRateCategory: category=0"))
        assertTrue(AppLogger.isNoisyFrameworkLog("SomeTag", "received onFocusEvent for window"))
        assertTrue(AppLogger.isNoisyFrameworkLog("com.yuka.musicplayer", "Accessing hidden method Landroid/media/AudioSystem;->setForceUse(II)I (blocked, reflection, denied)"))
        assertTrue(AppLogger.isNoisyFrameworkLog("AudioSystem", "setForceUse() error -1"))
    }

    @Test
    fun testRelevantLogsAreNotFiltered() {
        assertFalse(AppLogger.isNoisyFrameworkLog("USB", "Device attached: VID=2972, PID=0126"))
        assertFalse(AppLogger.isNoisyFrameworkLog("TsrossaAudio", "play_audio: Altsetting 1 activated"))
        assertFalse(AppLogger.isNoisyFrameworkLog("AudioEngine", "Starting stream at 44100Hz 16-bit"))
        assertFalse(AppLogger.isNoisyFrameworkLog("PLAYBACK", "Playing track: test.flac"))
        assertFalse(AppLogger.isNoisyFrameworkLog("LibraryView", "LazyColumn scrolled to index 15"))
    }
}
