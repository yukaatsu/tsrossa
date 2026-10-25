package com.yuka.musicplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEngineEventTest {

    @Test
    fun `test onTrackFinished triggers callback correctly`() {
        var finishedCalled = false
        val engine = AudioEngine()
        engine.onTrackFinishedCallback = {
            finishedCalled = true
        }

        engine.onTrackFinished()
        assertTrue("onTrackFinished should invoke onTrackFinishedCallback", finishedCalled)
    }

    @Test
    fun `test onTrackTransition passes nextFilePath correctly`() {
        var transitionPath: String? = null
        val engine = AudioEngine()
        engine.onTrackTransitionCallback = { path ->
            transitionPath = path
        }

        val testPath = "/storage/emulated/0/Music/next_song.flac"
        engine.onTrackTransition(testPath)
        assertEquals(testPath, transitionPath)
    }

    @Test
    fun `test onUsbStallFault triggers callback correctly`() {
        var stallCalled = false
        val engine = AudioEngine()
        engine.onUsbStallFaultCallback = {
            stallCalled = true
        }

        engine.onUsbStallFault()
        assertTrue("onUsbStallFault should invoke onUsbStallFaultCallback", stallCalled)
    }

    @Test
    fun testOnTrackFinishedMultipleInvocations() {
        var finishedCount = 0
        val engine = AudioEngine()
        engine.onTrackFinishedCallback = { finishedCount++ }
        engine.onTrackFinished()
        engine.onTrackFinished()
        assertEquals(2, finishedCount)
    }
}

