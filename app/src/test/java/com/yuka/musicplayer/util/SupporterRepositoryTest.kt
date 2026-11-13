package com.yuka.musicplayer.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SupporterRepositoryTest {

    @Test
    fun testDefaultSupportersListIsNotEmpty() {
        val supporters = SupporterRepository.DEFAULT_SUPPORTERS
        assertEquals(1, supporters.size)
        val s = supporters.first()
        assertEquals("Seseorang", s.name)
        assertTrue(s.message.contains("fitur EQ"))
        assertTrue(s.message.contains("Berapa bit itu lagunya"))
        for (supporter in supporters) {
            assertTrue("Supporter name must not be blank", supporter.name.isNotBlank())
            assertTrue("Supporter message must not be blank", supporter.message.isNotBlank())
            assertTrue("Supporter badge must not be blank", supporter.badge.isNotBlank())
            assertFalse(supporter.name.contains("Ahmad F."))
            assertFalse(supporter.name.contains("Budi S."))
        }
    }

    @Test
    fun testPriorityFeatureRequestNoteContainsInformation() {
        val note = SupporterRepository.PRIORITY_FEATURE_REQUEST_NOTE
        assertTrue("Note should mention request fitur", note.contains("request fitur", ignoreCase = true))
        assertTrue("Note should mention Bagi-Bagi", note.contains("Bagi-Bagi", ignoreCase = true))
    }

    @Test
    fun testDeveloperThankYouMessageIsPresent() {
        val thankYou = SupporterRepository.DEVELOPER_THANK_YOU_MESSAGE
        assertTrue("Thank you message must acknowledge supporters", thankYou.contains("Terima kasih", ignoreCase = true))
        assertTrue("Thank you message must mention tsrossa", thankYou.contains("tsrossa", ignoreCase = true))
    }

    @Test
    fun testDonationUrlPointsToBagiBagiYukaatsu() {
        assertEquals("https://bagibagi.co/Yukaatsu", SupporterRepository.DONATION_URL)
    }

    @Test
    fun testGetSupportersFallbackReturnsDefaultListOffline() = runBlocking {
        val supporters = SupporterRepository.getSupporters()
        assertTrue("getSupporters should return list even when offline", supporters.isNotEmpty())
    }
}
