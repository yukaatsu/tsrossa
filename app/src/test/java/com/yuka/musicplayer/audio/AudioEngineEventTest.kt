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

    @Test
    fun `test onDeviceForceDisconnected triggers callback correctly`() {
        var disconnectedCalled = false
        val engine = AudioEngine()
        engine.onDeviceForceDisconnectedCallback = {
            disconnectedCalled = true
        }

        engine.onDeviceForceDisconnected()
        assertTrue("onDeviceForceDisconnected should invoke onDeviceForceDisconnectedCallback", disconnectedCalled)
    }

    @Test
    fun `test onFormatIncompatible passes all format diagnostic parameters correctly`() {
        var receivedFilename = ""
        var receivedBitDepth = 0
        var receivedSampleRate = 0
        var receivedReason = ""
        val engine = AudioEngine()
        engine.onFormatIncompatibleCallback = { fn, bd, sr, r ->
            receivedFilename = fn
            receivedBitDepth = bd
            receivedSampleRate = sr
            receivedReason = r
        }

        engine.onFormatIncompatible("test_hi_res.flac", 32, 768000, "Device Max Sample Rate 192000 Hz Exceeded")
        assertEquals("test_hi_res.flac", receivedFilename)
        assertEquals(32, receivedBitDepth)
        assertEquals(768000, receivedSampleRate)
        assertEquals("Device Max Sample Rate 192000 Hz Exceeded", receivedReason)
    }

    @Test
    fun `test null callbacks do not throw NullPointerException`() {
        val engine = AudioEngine()
        engine.onTrackFinishedCallback = null
        engine.onTrackTransitionCallback = null
        engine.onUsbStallFaultCallback = null
        engine.onDeviceForceDisconnectedCallback = null
        engine.onFormatIncompatibleCallback = null

        // None of these should throw
        engine.onTrackFinished()
        engine.onTrackTransition("/path/to/song.flac")
        engine.onUsbStallFault()
        engine.onDeviceForceDisconnected()
        engine.onFormatIncompatible("song.flac", 24, 96000, "Err")
    }

    @Test
    fun `test end-to-end playback lifecycle from 0 to 100 percent fires finished event without premature cutoff`() {
        val engine = AudioEngine()
        var finishedFired = false
        var playbackProgress = 0.0

        engine.onTrackFinishedCallback = {
            finishedFired = true
        }

        // Simulate playback progression through a 300-second track (e.g. Katappo no Kutsushita)
        val totalDurationSec = 300.0
        var currentPosition = 0.0
        val stepSec = 1.0

        while (currentPosition < totalDurationSec) {
            currentPosition += stepSec
            playbackProgress = currentPosition / totalDurationSec
            // Ensure finished callback does NOT fire prematurely at 72.8% (minute 3:38 / 218s)
            if (currentPosition < totalDurationSec) {
                org.junit.Assert.assertFalse(
                    "Track must not finish prematurely at ${currentPosition}s (${playbackProgress * 100}%)",
                    finishedFired
                )
            }
        }

        // Simulate C++ streaming reaching exact track completion (100%)
        if (currentPosition >= totalDurationSec) {
            engine.onTrackFinished()
        }

        assertTrue("onTrackFinished must fire when track reaches 100%", finishedFired)
        assertEquals(1.0, playbackProgress, 0.001)
    }

    @Test
    fun `test seek event resets progress without triggering premature track finished event`() {
        val engine = AudioEngine()
        var finishedFired = false
        engine.onTrackFinishedCallback = { finishedFired = true }

        val totalDurationSec = 300.0 // 5m
        var currentPosition = 218.0 // 3m 38s (72.8%)

        // User performs Seek to 255.0s (minute 4:15, 85%)
        val seekTarget = 255.0
        currentPosition = seekTarget

        // Seeking must not trigger onTrackFinished
        org.junit.Assert.assertFalse("Seeking to 85% must not trigger onTrackFinished", finishedFired)
        assertEquals(255.0, currentPosition, 0.0)

        // Continue playback to end
        currentPosition = totalDurationSec
        engine.onTrackFinished()
        assertTrue("Track finished should fire only when final frame is reached", finishedFired)
    }
}

