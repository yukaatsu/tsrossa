package com.yuka.musicplayer.ui.diagnostics

import com.yuka.musicplayer.utils.AppLogEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportTest {

    @Test
    fun generateDiagnosticReport_includesAllLogcatEventsAndProcessLogcatByDefault() {
        val sampleAppLogs = listOf(
            AppLogEntry(1L, "17:37:15.100", "INFO", "USB", "Audio Device Selected: SNOWSKY Melody"),
            AppLogEntry(2L, "17:37:16.250", "INFO", "AUDIO", "UAC2 Clock locked at 96000 Hz"),
            AppLogEntry(3L, "17:38:01.400", "INFO", "PLAYBACK", "Playing track: test.flac"),
            AppLogEntry(4L, "17:38:05.500", "WARN", "STORAGE", "Scan completed: 142 files"),
            AppLogEntry(5L, "17:39:00.100", "INFO", "UI", "Opened SystemLogsPanel"),
            AppLogEntry(6L, "17:39:02.900", "ERR", "SYSTEM", "Sample system event")
        )
        val sampleProcessLogcat = listOf(
            "11-20 17:37:15.120 I/KewAudioEngine: initUsbDac SUCCESS",
            "11-20 17:37:16.260 I/KewAudioEngine: playAudio SUCCESS: isPlaying=true"
        )

        val report = generateDiagnosticReport(
            dacName = "SNOWSKY Melody",
            isDacConnected = true,
            isDeviceWedged = false,
            uacVersion = 2,
            claimedInterfaces = "1, 2",
            isPlaying = true,
            sourceBitDepth = 24,
            sourceSampleRate = 96000,
            outputBitDepth = 24,
            outputSampleRate = 96000,
            negotiatedSampleRate = 96000,
            isSampleRateUnverified = false,
            recentErrorCount = 0,
            supportedSampleRates = "44100,48000,96000",
            supportedBitDepths = "16 / 24 / 32",
            refusedTrackHistory = emptyList(),
            appLogs = sampleAppLogs,
            logcatLines = sampleProcessLogcat
        )

        // Verify [LOGCAT] section and ALL application log categories are included by default (#NEW FITUR 1)
        assertTrue(report.contains("[LOGCAT]"))
        assertTrue(report.contains("--- Application Event Logs (ALL: 6 events) ---"))
        assertTrue(report.contains("[17:37:15.100] [INFO/USB] Audio Device Selected: SNOWSKY Melody"))
        assertTrue(report.contains("[17:37:16.250] [INFO/AUDIO] UAC2 Clock locked at 96000 Hz"))
        assertTrue(report.contains("[17:38:01.400] [INFO/PLAYBACK] Playing track: test.flac"))
        assertTrue(report.contains("[17:38:05.500] [WARN/STORAGE] Scan completed: 142 files"))
        assertTrue(report.contains("[17:39:00.100] [INFO/UI] Opened SystemLogsPanel"))
        assertTrue(report.contains("[17:39:02.900] [ERR/SYSTEM] Sample system event"))

        // Verify process logcat output is included
        assertTrue(report.contains("--- Process Logcat Output (2 lines) ---"))
        assertTrue(report.contains("initUsbDac SUCCESS"))
        assertTrue(report.contains("playAudio SUCCESS: isPlaying=true"))
    }

    @Test
    fun generateDiagnosticReport_canStillExcludeLogcatWhenExplicitlyDisabled() {
        val report = generateDiagnosticReport(
            dacName = "SNOWSKY Melody",
            isDacConnected = true,
            isDeviceWedged = false,
            uacVersion = 2,
            claimedInterfaces = "1, 2",
            isPlaying = false,
            sourceBitDepth = 16,
            sourceSampleRate = 44100,
            outputBitDepth = 16,
            outputSampleRate = 44100,
            negotiatedSampleRate = 44100,
            isSampleRateUnverified = false,
            recentErrorCount = 0,
            supportedSampleRates = "44100,48000",
            supportedBitDepths = "16 / 24",
            refusedTrackHistory = emptyList(),
            includeLogcat = false,
            appLogs = listOf(AppLogEntry(1L, "12:00:00.000", "INFO", "USB", "Hidden entry"))
        )

        assertFalse(report.contains("[LOGCAT]"))
        assertFalse(report.contains("Hidden entry"))
    }

    @Test
    fun evaluateSampleRateTransmission_validatesStrictlyFromSampleRateAndHandlesUnknown() {
        // Bit-Perfect (matching sample rate, verified clock)
        org.junit.Assert.assertEquals(SampleRateTransmissionState.BIT_PERFECT, evaluateSampleRateTransmission(true, 44100, 44100, false))
        org.junit.Assert.assertEquals("[ ✓ BIT-PERFECT ]", resolveTransmissionUiLabel(44100, 44100, false, true))
        org.junit.Assert.assertEquals("BIT-PERFECT [PASS]", resolveTransmissionReportStatus(44100, 44100, false, true))

        org.junit.Assert.assertEquals(SampleRateTransmissionState.BIT_PERFECT, evaluateSampleRateTransmission(true, 96000, 96000, false))
        org.junit.Assert.assertEquals("[ ✓ BIT-PERFECT ]", resolveTransmissionUiLabel(96000, 96000, false, true))
        org.junit.Assert.assertEquals("BIT-PERFECT [PASS]", resolveTransmissionReportStatus(96000, 96000, false, true))

        // Resampled (different sample rate)
        org.junit.Assert.assertEquals(SampleRateTransmissionState.RESAMPLED, evaluateSampleRateTransmission(true, 44100, 48000, false))
        org.junit.Assert.assertEquals("[ ✗ RESAMPLED ]", resolveTransmissionUiLabel(44100, 48000, false, true))
        org.junit.Assert.assertEquals("RESAMPLED [FAIL]", resolveTransmissionReportStatus(44100, 48000, false, true))

        // Unknown: insufficient data (source <= 0 or output <= 0 while playing)
        org.junit.Assert.assertEquals(SampleRateTransmissionState.UNKNOWN_INSUFFICIENT_DATA, evaluateSampleRateTransmission(true, 0, 44100, false))
        org.junit.Assert.assertEquals("[ ? UNKNOWN ]", resolveTransmissionUiLabel(0, 44100, false, true))
        org.junit.Assert.assertEquals("UNKNOWN [INSUFFICIENT DATA]", resolveTransmissionReportStatus(0, 44100, false, true))

        org.junit.Assert.assertEquals(SampleRateTransmissionState.UNKNOWN_INSUFFICIENT_DATA, evaluateSampleRateTransmission(true, 96000, 0, false))
        org.junit.Assert.assertEquals("[ ? UNKNOWN ]", resolveTransmissionUiLabel(96000, 0, false, true))
        org.junit.Assert.assertEquals("UNKNOWN [INSUFFICIENT DATA]", resolveTransmissionReportStatus(96000, 0, false, true))

        // Unknown: unverified clock
        org.junit.Assert.assertEquals(SampleRateTransmissionState.UNKNOWN_UNVERIFIED_CLOCK, evaluateSampleRateTransmission(true, 96000, 96000, true))
        org.junit.Assert.assertEquals("[ ? UNKNOWN ]", resolveTransmissionUiLabel(96000, 96000, true, true))
        org.junit.Assert.assertEquals("UNKNOWN [UNVERIFIED CLOCK]", resolveTransmissionReportStatus(96000, 96000, true, true))

        // No stream / Standby
        org.junit.Assert.assertEquals(SampleRateTransmissionState.NO_STREAM, evaluateSampleRateTransmission(false, 44100, 44100, false))
        org.junit.Assert.assertEquals("[ ⋯ NO STREAM ]", resolveTransmissionUiLabel(44100, 44100, false, false))
        org.junit.Assert.assertEquals("STANDBY [NO STREAM]", resolveTransmissionReportStatus(44100, 44100, false, false))
    }

    @Test
    fun evaluateBitDepthPath_separatesBitDepthStatusFromSampleRate() {
        // Native exact
        org.junit.Assert.assertEquals(BitDepthPathState.NATIVE_EXACT, evaluateBitDepthPath(24, 24))
        org.junit.Assert.assertEquals("[ ✓ NATIVE 24-BIT ]", resolveBitDepthUiLabel(24, 24))
        org.junit.Assert.assertEquals("NATIVE 1:1 (24-BIT) [EXACT]", resolveBitDepthReportStatus(24, 24))

        // Lossless container (16->32, 24->32, 16->24)
        org.junit.Assert.assertEquals(BitDepthPathState.LOSSLESS_CONTAINER, evaluateBitDepthPath(16, 32))
        org.junit.Assert.assertEquals("[ ✓ 16→32-BIT CONTAINER ]", resolveBitDepthUiLabel(16, 32))
        org.junit.Assert.assertEquals("LOSSLESS CONTAINER (16-BIT -> 32-BIT) [PASS]", resolveBitDepthReportStatus(16, 32))

        org.junit.Assert.assertEquals(BitDepthPathState.LOSSLESS_CONTAINER, evaluateBitDepthPath(24, 32))
        org.junit.Assert.assertEquals("[ ✓ 24→32-BIT CONTAINER ]", resolveBitDepthUiLabel(24, 32))
        org.junit.Assert.assertEquals("LOSSLESS CONTAINER (24-BIT -> 32-BIT) [PASS]", resolveBitDepthReportStatus(24, 32))

        // Downconverted (24->16)
        org.junit.Assert.assertEquals(BitDepthPathState.DOWNCONVERTED, evaluateBitDepthPath(24, 16))
        org.junit.Assert.assertEquals("[ ! 24→16-BIT DOWNCONVERT ]", resolveBitDepthUiLabel(24, 16))
        org.junit.Assert.assertEquals("DOWNCONVERTED (24-BIT -> 16-BIT) [TRUNCATED]", resolveBitDepthReportStatus(24, 16))

        // Unknown
        org.junit.Assert.assertEquals(BitDepthPathState.UNKNOWN, evaluateBitDepthPath(0, 32))
        org.junit.Assert.assertEquals("[ ? UNKNOWN ]", resolveBitDepthUiLabel(0, 32))
        org.junit.Assert.assertEquals("UNKNOWN [INSUFFICIENT DATA]", resolveBitDepthReportStatus(0, 32))
    }

    @Test
    fun resolveConfiguredMusicRootPath_prioritizesKewMobilePrefsAndFallsBackToScanRoot() {
        // Prioritize KewMobilePrefs
        val path1 = resolveConfiguredMusicRootPath(
            kewPrefsRoot = "/storage/emulated/0/flac",
            legacyPrefsRoot = "/storage/emulated/0/Music",
            legacyTreePath = null,
            lastScanRootPath = "/storage/emulated/0/Download"
        )
        org.junit.Assert.assertEquals("/storage/emulated/0/flac", path1)

        // Fallback to legacyPrefsRoot
        val path2 = resolveConfiguredMusicRootPath(
            kewPrefsRoot = null,
            legacyPrefsRoot = "/storage/emulated/0/flac",
            legacyTreePath = null,
            lastScanRootPath = "/storage/emulated/0/Download"
        )
        org.junit.Assert.assertEquals("/storage/emulated/0/flac", path2)

        // Fallback to lastScanRootPath
        val path3 = resolveConfiguredMusicRootPath(
            kewPrefsRoot = "",
            legacyPrefsRoot = null,
            legacyTreePath = null,
            lastScanRootPath = "/storage/emulated/0/Music"
        )
        org.junit.Assert.assertEquals("/storage/emulated/0/Music", path3)

        // All empty -> fallback "(Not Configured)"
        val path4 = resolveConfiguredMusicRootPath(
            kewPrefsRoot = null,
            legacyPrefsRoot = null,
            legacyTreePath = null,
            lastScanRootPath = null
        )
        org.junit.Assert.assertEquals("(Not Configured)", path4)
    }

    @Test
    fun generateDiagnosticReport_includesSeparatedTransmissionAndBitDepthPath() {
        val report = generateDiagnosticReport(
            dacName = "SNOWSKY Melody",
            isDacConnected = true,
            isDeviceWedged = false,
            uacVersion = 2,
            claimedInterfaces = "1, 2",
            isPlaying = true,
            sourceBitDepth = 16,
            sourceSampleRate = 44100,
            outputBitDepth = 32,
            outputSampleRate = 44100,
            negotiatedSampleRate = 44100,
            isSampleRateUnverified = false,
            recentErrorCount = 0,
            supportedSampleRates = "44100,48000,96000",
            supportedBitDepths = "16 / 24 / 32",
            refusedTrackHistory = emptyList(),
            includeLogcat = false
        )

        // Verify that 16-Bit -> 32-Bit with matching 44.1 kHz is BIT-PERFECT [PASS], NOT RESAMPLED [FAIL]
        assertTrue(report.contains("Transmission: BIT-PERFECT [PASS]"))
        assertTrue(report.contains("Bit Depth Path: LOSSLESS CONTAINER (16-BIT -> 32-BIT) [PASS]"))
        assertFalse(report.contains("RESAMPLED [FAIL]"))
    }

    @Test
    fun resolveEffectiveOutputBitDepth_switchesInstantlyBetweenHybridHardwareUacAndUniversal64BitDsp() {
        // 1. Hybrid Hardware UAC active + PEQ OFF on 24-bit track (C++ slash-separated format) -> Native 1:1 (24-bit)
        val uacNativeSlash = resolveEffectiveOutputBitDepth(
            sourceBitDepth = 24,
            rawNegotiatedBitDepth = 32,
            supportedBitDepths = "16 / 24 / 32",
            isHardwareVolumeActive = true,
            isPeqEnabled = false
        )
        org.junit.Assert.assertEquals(24, uacNativeSlash)
        org.junit.Assert.assertEquals("[ ✓ NATIVE 24-BIT ]", resolveBitDepthUiLabel(24, uacNativeSlash))

        // 2. Hybrid Hardware UAC active + PEQ OFF on 24-bit track (comma-separated format) -> Native 1:1 (24-bit)
        val uacNativeComma = resolveEffectiveOutputBitDepth(
            sourceBitDepth = 24,
            rawNegotiatedBitDepth = 32,
            supportedBitDepths = "16-bit, 24-bit, 32-bit",
            isHardwareVolumeActive = true,
            isPeqEnabled = false
        )
        org.junit.Assert.assertEquals(24, uacNativeComma)

        // 3. Universal 64-Bit DSP active (isHardwareVolumeActive = false) -> 32-bit container
        val dspContainer = resolveEffectiveOutputBitDepth(
            sourceBitDepth = 24,
            rawNegotiatedBitDepth = 24,
            supportedBitDepths = "16 / 24 / 32",
            isHardwareVolumeActive = false,
            isPeqEnabled = false
        )
        org.junit.Assert.assertEquals(32, dspContainer)
        org.junit.Assert.assertEquals("[ ✓ 24→32-BIT CONTAINER ]", resolveBitDepthUiLabel(24, dspContainer))

        // 4. PEQ active overrides pure 1:1 native -> 32-bit container
        val peqContainer = resolveEffectiveOutputBitDepth(
            sourceBitDepth = 24,
            rawNegotiatedBitDepth = 24,
            supportedBitDepths = "16 / 24 / 32",
            isHardwareVolumeActive = true,
            isPeqEnabled = true
        )
        org.junit.Assert.assertEquals(32, peqContainer)

        // 5. DAC without 32-bit support falls back cleanly to max supported (24-bit)
        val max24Dac = resolveEffectiveOutputBitDepth(
            sourceBitDepth = 24,
            rawNegotiatedBitDepth = 24,
            supportedBitDepths = "16 / 24",
            isHardwareVolumeActive = false,
            isPeqEnabled = false
        )
        org.junit.Assert.assertEquals(24, max24Dac)
    }
}
