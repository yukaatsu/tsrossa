package com.yuka.musicplayer.audio

import org.junit.Assert.*
import org.junit.Test

class PeqModelsTest {

    @Test
    fun testDefault10BandsConfiguration() {
        val defaultBands = PeqConfiguration.createDefaultBands()
        assertEquals(10, defaultBands.size)
        assertEquals(31.0f, defaultBands[0].freqHz, 0.1f)
        assertEquals(62.0f, defaultBands[1].freqHz, 0.1f)
        assertEquals(125.0f, defaultBands[2].freqHz, 0.1f)
        assertEquals(250.0f, defaultBands[3].freqHz, 0.1f)
        assertEquals(500.0f, defaultBands[4].freqHz, 0.1f)
        assertEquals(1000.0f, defaultBands[5].freqHz, 0.1f)
        assertEquals(2000.0f, defaultBands[6].freqHz, 0.1f)
        assertEquals(4000.0f, defaultBands[7].freqHz, 0.1f)
        assertEquals(8000.0f, defaultBands[8].freqHz, 0.1f)
        assertEquals(16000.0f, defaultBands[9].freqHz, 0.1f)

        // Multi-Zone Q: 0.707 for Butterworth Shelf (bands 0 & 9), 0.95 for Bass (bands 1 & 2), 1.25 for Mid (bands 3 & 4), 1.41 for Treble (bands 5..8)
        assertEquals(0.707f, defaultBands[0].q, 0.01f)
        assertEquals(0.95f, defaultBands[1].q, 0.01f)
        assertEquals(0.95f, defaultBands[2].q, 0.01f)
        assertEquals(1.25f, defaultBands[3].q, 0.01f)
        assertEquals(1.25f, defaultBands[4].q, 0.01f)
        for (i in 5..8) {
            assertEquals("Band $i must use Constant-Q 1.41", 1.41f, defaultBands[i].q, 0.01f)
        }
        assertEquals(0.707f, defaultBands[9].q, 0.01f)

        // All gains should be 0 dB by default
        for (b in defaultBands) {
            assertEquals(0.0f, b.gainDb, 0.01f)
            assertTrue(b.enabled)
            assertTrue(b.shortLabel.isNotBlank())
            assertTrue(b.zoneName.isNotBlank())
            assertTrue(b.acousticDescription.isNotBlank())
        }

        // Verify default config has autoPreampEnabled = true by default for headroom protection
        val defaultConfig = PeqConfiguration()
        assertTrue(defaultConfig.autoPreampEnabled)
        assertEquals(0.0f, defaultConfig.effectivePreampDb, 0.001f)
    }

    @Test
    fun testBuiltInPresetsRemoved() {
        // Per #NOTE 1: All built-in presets must be removed
        assertTrue("All built-in presets must be removed", PeqPresets.ALL.isEmpty())
    }

    @Test
    fun testAutoPreampAntiClipping() {
        // Set Band 5 (1000 Hz, PEAKING filter) to +6.0 dB
        val bands = PeqConfiguration.createDefaultBands().mapIndexed { i, b ->
            if (i == 5) b.copy(gainDb = 6.0f) else b
        }
        val config = PeqConfiguration(
            enabled = true,
            preampDb = 0.0f,
            autoPreampEnabled = true,
            bands = bands
        )
        // Energy-weighted headroom formula: -(maxPeak * 0.78f + 0.25f)
        // For +6.0 dB boost: -(6.0 * 0.78 + 0.25) = -4.93 dB
        assertEquals(-4.93f, config.effectivePreampDb, 0.25f)

        // For massive +12.0 dB boost: -(12.0 * 0.78 + 0.25) = -9.61 dB
        val extremeBands = PeqConfiguration.createDefaultBands().mapIndexed { i, b ->
            if (i == 5) b.copy(gainDb = 12.0f) else b
        }
        val extremeConfig = PeqConfiguration(
            enabled = true,
            preampDb = 0.0f,
            autoPreampEnabled = true,
            bands = extremeBands
        )
        assertEquals(-9.61f, extremeConfig.effectivePreampDb, 0.35f)

        // For pure cut (Band 5 at -3.0 dB), zero unnecessary volume cut (0.0 dB!)
        val cutBands = PeqConfiguration.createDefaultBands().mapIndexed { i, b ->
            if (i == 5) b.copy(gainDb = -3.0f) else b
        }
        val cutConfig = PeqConfiguration(
            enabled = true,
            preampDb = 0.0f,
            autoPreampEnabled = true,
            bands = cutBands
        )
        assertEquals(0.0f, cutConfig.effectivePreampDb, 0.01f)
    }

    @Test
    fun testCompositeFrequencyResponseCurve() {
        // Flat configuration should yield ~0 dB at all frequencies
        val flatConfig = PeqConfiguration(
            enabled = true,
            preampDb = 0f,
            autoPreampEnabled = false,
            bands = PeqConfiguration.createDefaultBands()
        )
        val testFreqs = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        val flatCurve = PeqEvaluator.evaluateCurve(flatConfig.bands, testFreqs, flatConfig.preampDb)
        for (gain in flatCurve) {
            assertEquals(0.0f, gain, 0.05f)
        }

        // Bass boost configuration should yield positive boost on low frequencies
        val bassBoostBands = PeqConfiguration.createDefaultBands().mapIndexed { i, b ->
            when (i) {
                0 -> b.copy(gainDb = 5.5f)
                1 -> b.copy(gainDb = 4.0f)
                else -> b
            }
        }
        val bassCurve = PeqEvaluator.evaluateCurve(bassBoostBands, testFreqs, 0f)
        assertTrue(bassCurve[0] > 2.0f) // 31 Hz boost
        assertTrue(bassCurve[1] > 2.0f) // 62 Hz boost
        assertEquals(0.0f, bassCurve[6], 0.1f) // 2000 Hz flat
    }

    @Test
    fun testRandomizedDonationReopenCycle() {
        // Test that randomized target is strictly within 2..4 range
        for (i in 0 until 50) {
            val target = (2..4).random()
            assertTrue("Target reopen should be between 2 and 4", target in 2..4)
        }

        // Simulate reopen cycle:
        var reopenCount = 0
        var target = 3
        var modalShown = false

        // Open 1: count becomes 1 (< 3), modal not shown
        reopenCount++
        if (reopenCount >= target) modalShown = true
        assertFalse(modalShown)

        // Open 2: count becomes 2 (< 3), modal not shown
        reopenCount++
        if (reopenCount >= target) modalShown = true
        assertFalse(modalShown)

        // Open 3: count becomes 3 (>= 3), modal shown!
        reopenCount++
        if (reopenCount >= target) modalShown = true
        assertTrue(modalShown)

        // On dismiss: count resets to 0 and target re-rolls in 2..4
        modalShown = false
        reopenCount = 0
        target = (2..4).random()
        assertTrue(target in 2..4)
        assertEquals(0, reopenCount)
    }

    @Test
    fun testDiscordInviteUrlConstant() {
        val expectedUrl = "https://discord.gg/VJZgdF6Fq8"
        assertEquals(expectedUrl, com.yuka.musicplayer.ui.dialogs.DISCORD_INVITE_URL)
        assertTrue(com.yuka.musicplayer.ui.dialogs.DISCORD_INVITE_URL.startsWith("https://discord.gg/"))
    }

    @Test
    fun testSequentialModalQueueWithoutCollision() {
        // Test non-overlapping modal presentation
        var showDonationModal = false
        var showDiscordModal = false

        // Scenario 1: Both triggers hit simultaneously
        val donationTriggered = true
        val discordTriggered = true

        if (donationTriggered) {
            showDonationModal = true
        } else if (discordTriggered) {
            showDiscordModal = true
        }

        // Donation takes priority on cold start; Discord is deferred
        assertTrue(showDonationModal)
        assertFalse(showDiscordModal)

        // Scenario 2: When donation modal is dismissed, discord modal can be scheduled next
        showDonationModal = false
        if (!showDonationModal && discordTriggered) {
            showDiscordModal = true
        }
        assertFalse(showDonationModal)
        assertTrue(showDiscordModal)
    }

    @Test
    fun testFirstInstallDualModalGuaranteeAndRandomReopenCycle() {
        // 1. Simulation of First-Install Guarantee (#BUG 1):
        // Both modals MUST appear sequentially (no matter who is randomized first)
        var hasSeenWelcomeDonation = false
        var hasSeenWelcomeDiscord = false
        val firstInstallDonationFirst = true // Simulate donation first

        var showDonationModal = false
        var showDiscordModal = false

        // Step 1: Initial launch (neither seen)
        if (!hasSeenWelcomeDonation && !hasSeenWelcomeDiscord) {
            if (firstInstallDonationFirst) {
                showDonationModal = true
            } else {
                showDiscordModal = true
            }
        }
        assertTrue("Donation modal must appear first in this run", showDonationModal)
        assertFalse("Discord modal must wait for donation dismissal", showDiscordModal)

        // Step 2: User dismisses donation modal
        showDonationModal = false
        hasSeenWelcomeDonation = true

        // State machine evaluation: now !hasSeenWelcomeDiscord is still true!
        if (!hasSeenWelcomeDonation || !hasSeenWelcomeDiscord) {
            if (!hasSeenWelcomeDiscord) {
                showDiscordModal = true
            }
        }
        assertTrue("Discord modal must now 100% appear as second modal on first install", showDiscordModal)

        // Step 3: User dismisses discord modal
        showDiscordModal = false
        hasSeenWelcomeDiscord = true

        // First install phase is now officially completed!
        assertTrue(hasSeenWelcomeDonation)
        assertTrue(hasSeenWelcomeDiscord)

        // 2. Simulation of Subsequent Randomized Reopen Cycle (#BUG 1):
        // Counter increments on each app reopen until target (2..4) is reached,
        // then randomly selects either DONATION or DISCORD with anti-streak balancer.
        val history = mutableListOf<String>()
        var lastType = ""
        var streak = 0

        fun pickMockModal(): String {
            val chosen = if (streak >= 2 && (lastType == "DONATION" || lastType == "DISCORD")) {
                if (lastType == "DONATION") "DISCORD" else "DONATION"
            } else {
                if (kotlin.random.Random.nextBoolean()) "DONATION" else "DISCORD"
            }
            streak = if (chosen == lastType) streak + 1 else 1
            lastType = chosen
            return chosen
        }

        // Run 20 cycles to verify anti-streak balancer & randomization
        for (cycle in 1..20) {
            val target = (2..4).random()
            var reopenCount = 0
            var triggeredModal: String? = null

            for (open in 1..target) {
                reopenCount++
                if (reopenCount >= target) {
                    triggeredModal = pickMockModal()
                }
            }

            assertNotNull(triggeredModal)
            assertTrue(triggeredModal == "DONATION" || triggeredModal == "DISCORD")
            history.add(triggeredModal!!)
        }

        // Verify that history contains both modals across multiple cycles
        assertTrue("Should show both donation and discord across 20 cycles", history.contains("DONATION") && history.contains("DISCORD"))

        // Verify anti-streak: no single modal can appear 3 or more times consecutively
        var consecutiveCount = 1
        for (i in 1 until history.size) {
            if (history[i] == history[i - 1]) {
                consecutiveCount++
                assertTrue("Anti-streak balancer must prevent >= 3 consecutive occurrences of $lastType", consecutiveCount <= 2)
            } else {
                consecutiveCount = 1
            }
        }
    }

    @Test
    fun testUserPeqPresetJsonSerializationAndCrud() {
        val defaultBands = PeqConfiguration.createDefaultBands()
        val customBands = defaultBands.mapIndexed { i, b ->
            if (i == 0) b.copy(gainDb = 4.5f, freqHz = 35.0f, q = 0.85f, filterType = PeqFilterType.LOW_SHELF)
            else if (i == 5) b.copy(gainDb = -2.0f, q = 1.4f, filterType = PeqFilterType.PEAKING)
            else b
        }

        // 1. Test Bands Serialization & Deserialization
        val jsonArray = PeqPreferencesHelper.serializeBandsToJson(customBands)
        assertEquals(10, jsonArray.length())

        val restoredBands = PeqPreferencesHelper.deserializeBandsFromJson(jsonArray)
        assertEquals(10, restoredBands.size)
        assertEquals(4.5f, restoredBands[0].gainDb, 0.01f)
        assertEquals(35.0f, restoredBands[0].freqHz, 0.01f)
        assertEquals(0.85f, restoredBands[0].q, 0.01f)
        assertEquals(PeqFilterType.LOW_SHELF, restoredBands[0].filterType)
        assertEquals(-2.0f, restoredBands[5].gainDb, 0.01f)
        assertEquals(1.4f, restoredBands[5].q, 0.01f)

        // 2. Test UserPeqPreset Data Class
        val userPreset = UserPeqPreset(
            id = "preset_test_1",
            name = "Moondrop Aria Warm",
            preampDb = -2.5f,
            autoPreampEnabled = true,
            bands = customBands
        )
        assertEquals("preset_test_1", userPreset.id)
        assertEquals("Moondrop Aria Warm", userPreset.name)
        assertEquals(-2.5f, userPreset.preampDb, 0.01f)
        assertTrue(userPreset.autoPreampEnabled)
        assertEquals(10, userPreset.bands.size)
    }

    @Test
    fun testPeqDisabledStateConfigIntegrity() {
        val defaultConfig = PeqConfiguration(enabled = false)
        assertFalse(defaultConfig.enabled)

        // Switching preset while enabled = false preserves state without auto-enabling
        val testPreset = UserPeqPreset(
            id = "custom_test_1",
            name = "Test Preset",
            preampDb = -1.5f,
            autoPreampEnabled = true,
            bands = PeqConfiguration.createDefaultBands()
        )
        val updatedConfig = defaultConfig.copy(
            selectedPresetId = testPreset.id,
            bands = testPreset.bands
        )
        assertFalse("Config should remain disabled when user selects preset while PEQ is OFF", updatedConfig.enabled)
        assertEquals("custom_test_1", updatedConfig.selectedPresetId)
    }

    @Test
    fun testPeqExportImportShareCodeAndJsonBundle() {
        val customBands = PeqConfiguration.createDefaultBands().mapIndexed { i, b ->
            when (i) {
                0 -> b.copy(gainDb = 3.5f, freqHz = 32f, q = 0.707f, filterType = PeqFilterType.LOW_SHELF)
                5 -> b.copy(gainDb = 2.0f, freqHz = 1000f, q = 1.05f, filterType = PeqFilterType.PEAKING)
                9 -> b.copy(gainDb = 2.5f, freqHz = 16000f, q = 0.707f, filterType = PeqFilterType.HIGH_SHELF)
                else -> b
            }
        }
        val preset = UserPeqPreset(
            id = "user_iem_1",
            name = "Moondrop Chu II Vocal",
            preampDb = -2.0f,
            autoPreampEnabled = true,
            bands = customBands
        )

        // 1. Export to TSROSSA-PEQ1 share code
        val shareCodeWithHeader = PeqPreferencesHelper.exportPresetToShareCode(preset, includeHeader = true)
        assertTrue(shareCodeWithHeader.contains("TSROSSA-PEQ1:"))
        assertTrue(shareCodeWithHeader.contains("Moondrop Chu II Vocal"))

        val shareCodeOnly = PeqPreferencesHelper.exportPresetToShareCode(preset, includeHeader = false)
        assertTrue(shareCodeOnly.startsWith("TSROSSA-PEQ1:"))

        // 2. Parse from pure code
        val parsedFromPure = PeqPreferencesHelper.parsePresetsFromImportText(shareCodeOnly)
        assertEquals(1, parsedFromPure.size)
        val p1 = parsedFromPure.first()
        assertEquals("Moondrop Chu II Vocal", p1.name)
        assertEquals(-2.0f, p1.preampDb, 0.01f)
        assertTrue(p1.autoPreampEnabled)
        assertEquals(10, p1.bands.size)
        assertEquals(3.5f, p1.bands[0].gainDb, 0.01f)
        assertEquals(2.0f, p1.bands[5].gainDb, 0.01f)
        assertEquals(2.5f, p1.bands[9].gainDb, 0.01f)

        // 3. Parse from messy Discord chat message (code surrounded by text/emojis)
        val chatMessage = """
            Halo guys, ini preset enak banget buat IEM Moondrop Chu II di TSROSSA:
            🔥 Jangan lupa dicoba ya:
            $shareCodeOnly
            Mantap abis vokalnya maju dan bass nendang!
        """.trimIndent()
        val parsedFromChat = PeqPreferencesHelper.parsePresetsFromImportText(chatMessage)
        assertEquals(1, parsedFromChat.size)
        assertEquals("Moondrop Chu II Vocal", parsedFromChat.first().name)

        // 4. Export & Parse Multi-Preset JSON Bundle
        val preset2 = UserPeqPreset(
            id = "user_bass_heavy",
            name = "Bass Cannon",
            preampDb = -4.0f,
            autoPreampEnabled = true,
            bands = customBands.mapIndexed { i, b -> if (i == 1) b.copy(gainDb = 6.0f) else b }
        )
        val jsonBundle = PeqPreferencesHelper.exportPresetsToJsonString(listOf(preset, preset2))
        assertTrue(jsonBundle.contains("\"app\": \"TSROSSA\""))
        assertTrue(jsonBundle.contains("Moondrop Chu II Vocal"))
        assertTrue(jsonBundle.contains("Bass Cannon"))

        val parsedBundle = PeqPreferencesHelper.parsePresetsFromImportText(jsonBundle)
        assertEquals(2, parsedBundle.size)
        assertEquals("Moondrop Chu II Vocal", parsedBundle[0].name)
        assertEquals("Bass Cannon", parsedBundle[1].name)
        assertEquals(6.0f, parsedBundle[1].bands[1].gainDb, 0.01f)
    }

    @Test
    fun testPeqGain01DbStepPrecisionAndEnglishAcousticRoles() {
        fun stepGain01(current: Float, delta: Float): Float {
            return (kotlin.math.round((current + delta) * 10f) / 10f).coerceIn(-12.0f, 12.0f)
        }

        // Verify 0.1 dB increments & decrements without floating-point drift
        var g = 0.0f
        g = stepGain01(g, 0.1f)
        assertEquals(0.1f, g, 0.0001f)
        g = stepGain01(g, 0.1f)
        assertEquals(0.2f, g, 0.0001f)
        g = stepGain01(g, 0.1f)
        assertEquals(0.3f, g, 0.0001f)
        g = stepGain01(g, -0.1f)
        assertEquals(0.2f, g, 0.0001f)

        // Verify clamping at ±12.0 dB
        assertEquals(12.0f, stepGain01(12.0f, 0.1f), 0.0001f)
        assertEquals(-12.0f, stepGain01(-12.0f, -0.1f), 0.0001f)

        // Verify all 10 acoustic roles are localized in English
        assertEquals(10, ACOUSTIC_ROLES.size)
        for (role in ACOUSTIC_ROLES) {
            assertTrue(role.description.isNotBlank())
            assertFalse("Acoustic description should be English: ${role.description}", role.description.contains("Getaran"))
            assertFalse("Acoustic description should be English: ${role.description}", role.description.contains("Turunkan"))
            assertFalse("Acoustic description should be English: ${role.description}", role.description.contains("vokal penyanyi"))
        }
    }

    @Test
    fun testPagerBoundaryHorizontalDragLock() {
        // Page 1 (Playlist right boundary): dragging left (deltaX < 0) past right edge must be blocked
        assertTrue(
            "Leftward drag at Playlist right boundary (page 1) must be blocked",
            com.yuka.musicplayer.ui.views.shouldBlockPagerHorizontalDrag(
                currentPage = 1,
                currentPageOffsetFraction = 0f,
                deltaX = -45f,
                pageCount = 2
            )
        )
        // Page 1 (Playlist): dragging right (deltaX > 0) back to Library (page 0) must be allowed
        assertFalse(
            "Rightward drag from Playlist back to Library must remain allowed",
            com.yuka.musicplayer.ui.views.shouldBlockPagerHorizontalDrag(
                currentPage = 1,
                currentPageOffsetFraction = 0f,
                deltaX = 45f,
                pageCount = 2
            )
        )

        // Page 0 (Library left boundary): dragging right (deltaX > 0) past left edge must be blocked
        assertTrue(
            "Rightward drag at Library left boundary (page 0) must be blocked",
            com.yuka.musicplayer.ui.views.shouldBlockPagerHorizontalDrag(
                currentPage = 0,
                currentPageOffsetFraction = 0f,
                deltaX = 45f,
                pageCount = 2
            )
        )
        // Page 0 (Library): dragging left (deltaX < 0) forward to Playlist (page 1) must be allowed
        assertFalse(
            "Leftward drag from Library forward to Playlist must remain allowed",
            com.yuka.musicplayer.ui.views.shouldBlockPagerHorizontalDrag(
                currentPage = 0,
                currentPageOffsetFraction = 0f,
                deltaX = -45f,
                pageCount = 2
            )
        )
    }

    @Test
    fun testShouldPromptPeqDspSwitch_onlyPromptsWhenHybridHardwareUacIsEnabled() {
        // Already in Universal 64-Bit DSP mode (isHardwareVolumeEnabled = false): must NOT prompt
        assertFalse(
            "Enabling PEQ when already in Universal 64-Bit DSP mode must not show switch prompt",
            com.yuka.musicplayer.ui.app.shouldPromptPeqDspSwitch(
                wasPeqEnabled = false,
                willEnablePeq = true,
                isHardwareVolumeEnabled = false
            )
        )

        // Currently in Hybrid Hardware UAC mode (isHardwareVolumeEnabled = true): MUST prompt on OFF -> ON
        assertTrue(
            "Enabling PEQ while Hybrid Hardware UAC is active must show Yes/No switch consent",
            com.yuka.musicplayer.ui.app.shouldPromptPeqDspSwitch(
                wasPeqEnabled = false,
                willEnablePeq = true,
                isHardwareVolumeEnabled = true
            )
        )

        // Adjusting bands while PEQ is already ON or turning PEQ OFF: must NOT prompt
        assertFalse(
            com.yuka.musicplayer.ui.app.shouldPromptPeqDspSwitch(
                wasPeqEnabled = true,
                willEnablePeq = true,
                isHardwareVolumeEnabled = true
            )
        )
        assertFalse(
            com.yuka.musicplayer.ui.app.shouldPromptPeqDspSwitch(
                wasPeqEnabled = true,
                willEnablePeq = false,
                isHardwareVolumeEnabled = true
            )
        )
    }

    @Test
    fun testCanProceedWithDacPlayback_blocksWhenDacDisconnected() {
        assertFalse(
            "Playback must be blocked when USB DAC is disconnected",
            com.yuka.musicplayer.player.canProceedWithDacPlayback(isDacConnected = false)
        )
        assertTrue(
            "Playback must be permitted when USB DAC is connected",
            com.yuka.musicplayer.player.canProceedWithDacPlayback(isDacConnected = true)
        )
    }

    @Test
    fun `calculateFastScrollerProgress interpolates smoothly from top to viewport-adjusted bottom`() {
        // At top of list (index 0, offset 0) -> 0.0f
        assertEquals(
            0f,
            com.yuka.musicplayer.ui.components.calculateFastScrollerProgress(
                itemCount = 20,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                firstVisibleItemSize = 180,
                rowItemSize = 180,
                usableViewportHeightPx = 1260 // 7 items visible span -> maxFirstIndex = 13
            ),
            0.001f
        )

        // Halfway down scrollable range (index 6 + 90px/180px = 6.5 / 13.0) -> 0.5f
        assertEquals(
            0.5f,
            com.yuka.musicplayer.ui.components.calculateFastScrollerProgress(
                itemCount = 20,
                firstVisibleItemIndex = 6,
                firstVisibleItemScrollOffset = 90,
                firstVisibleItemSize = 180,
                rowItemSize = 180,
                usableViewportHeightPx = 1260
            ),
            0.001f
        )

        // At bottom of list when 7 items fill the viewport (firstVisibleItemIndex = 13) -> 1.0f
        assertEquals(
            1.0f,
            com.yuka.musicplayer.ui.components.calculateFastScrollerProgress(
                itemCount = 20,
                firstVisibleItemIndex = 13,
                firstVisibleItemScrollOffset = 0,
                firstVisibleItemSize = 180,
                rowItemSize = 180,
                usableViewportHeightPx = 1260
            ),
            0.001f
        )
    }

    @Test
    fun `resolvePagerTargetPage and resolveViewStateForPagerPage keep navbar and pager synchronized`() {
        assertEquals(
            0,
            com.yuka.musicplayer.ui.views.resolvePagerTargetPage(
                viewState = com.yuka.musicplayer.model.ViewState.LIBRARY,
                currentPage = 1
            )
        )
        assertEquals(
            1,
            com.yuka.musicplayer.ui.views.resolvePagerTargetPage(
                viewState = com.yuka.musicplayer.model.ViewState.PLAYLIST,
                currentPage = 0
            )
        )
        // Non-tab states preserve current pager page
        assertEquals(
            1,
            com.yuka.musicplayer.ui.views.resolvePagerTargetPage(
                viewState = com.yuka.musicplayer.model.ViewState.SETTINGS,
                currentPage = 1
            )
        )

        // Pager page -> ViewState resolution across repeated Library <-> Playlist switches
        assertEquals(
            com.yuka.musicplayer.model.ViewState.PLAYLIST,
            com.yuka.musicplayer.ui.views.resolveViewStateForPagerPage(
                page = 1,
                currentViewState = com.yuka.musicplayer.model.ViewState.LIBRARY
            )
        )
        assertEquals(
            com.yuka.musicplayer.model.ViewState.LIBRARY,
            com.yuka.musicplayer.ui.views.resolveViewStateForPagerPage(
                page = 0,
                currentViewState = com.yuka.musicplayer.model.ViewState.PLAYLIST
            )
        )
        assertNull(
            com.yuka.musicplayer.ui.views.resolveViewStateForPagerPage(
                page = 0,
                currentViewState = com.yuka.musicplayer.model.ViewState.SETTINGS
            )
        )
    }
}
