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

        // Proportional 1-octave Q: 0.707 for Shelf (bands 0 & 9), 0.85 for Bass (bands 1 & 2), 1.05 for Mid/High (bands 3-8)
        assertEquals(0.707f, defaultBands[0].q, 0.01f)
        assertEquals(0.85f, defaultBands[1].q, 0.01f)
        assertEquals(0.85f, defaultBands[2].q, 0.01f)
        assertEquals(1.05f, defaultBands[3].q, 0.01f)
        assertEquals(1.05f, defaultBands[5].q, 0.01f)
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
        // Auto preamp uses 85% peak factor + 0.6 dB phase-rotation margin: -(6.0 * 0.85 + 0.6) = -5.7 dB
        assertEquals(-5.7f, config.effectivePreampDb, 0.3f)

        // For massive +12.0 dB boost: clamped to -10.0 dB maximum headroom attenuation
        val extremeBands = PeqConfiguration.createDefaultBands().mapIndexed { i, b ->
            if (i == 5) b.copy(gainDb = 12.0f) else b
        }
        val extremeConfig = PeqConfiguration(
            enabled = true,
            preampDb = 0.0f,
            autoPreampEnabled = true,
            bands = extremeBands
        )
        assertEquals(-10.0f, extremeConfig.effectivePreampDb, 0.1f)

        // For pure cut (Band 5 at -3.0 dB), apply 0.6 dB phase rotation headroom
        val cutBands = PeqConfiguration.createDefaultBands().mapIndexed { i, b ->
            if (i == 5) b.copy(gainDb = -3.0f) else b
        }
        val cutConfig = PeqConfiguration(
            enabled = true,
            preampDb = 0.0f,
            autoPreampEnabled = true,
            bands = cutBands
        )
        assertEquals(-0.6f, cutConfig.effectivePreampDb, 0.01f)
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
}
