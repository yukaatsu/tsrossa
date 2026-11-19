package com.yuka.musicplayer.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SupporterRepositoryTest {

    @Test
    fun testDefaultSupportersListContainsAryaAndSeseorang() {
        val supporters = SupporterRepository.DEFAULT_SUPPORTERS
        assertEquals(2, supporters.size)

        val arya = supporters.firstOrNull { it.name == "Arya" }
        assertNotNull("Arya must be included in DEFAULT_SUPPORTERS", arya)
        assertEquals(
            "Nah kemarin kan udah di kabulkan fitur EQ nya, nah sekarang aku ada usulan fitur bit-perfect biar bisa jalan di aplikasi lain... Kayak UAPP, ini ku coba play Apple music dac nya ga beneran kerja (indikator masih idle)... Dah itu aja..",
            arya!!.message
        )
        assertTrue(arya.isFeatureRequest)

        val seseorang = supporters.firstOrNull { it.name == "Seseorang" }
        assertNotNull("Seseorang must remain in DEFAULT_SUPPORTERS", seseorang)
        assertTrue(seseorang!!.message.contains("fitur EQ"))
        assertTrue(seseorang.message.contains("Berapa bit itu lagunya"))

        for (supporter in supporters) {
            assertTrue("Supporter name must not be blank", supporter.name.isNotBlank())
            assertTrue("Supporter message must not be blank", supporter.message.isNotBlank())
            assertTrue("Supporter badge must not be blank", supporter.badge.isNotBlank())
            assertFalse(supporter.name.contains("Ahmad F."))
            assertFalse(supporter.name.contains("Budi S."))
        }
    }

    @Test
    fun testMergeSupportersPreservesLocalDefaultsAndDeduplicates() {
        val remoteOlderList = listOf(
            SupporterRepository.DEFAULT_SUPPORTERS.last(), // Duplicate of Seseorang
            DonationSupporter(name = "RemoteDonor", message = "Great USB DAC player!", badge = "Supporter")
        )
        val merged = SupporterRepository.mergeSupporters(remoteOlderList)
        assertEquals(3, merged.size)
        assertTrue(merged.any { it.name == "Arya" })
        assertTrue(merged.any { it.name == "Seseorang" })
        assertTrue(merged.any { it.name == "RemoteDonor" })
    }

    @Test
    fun testPriorityFeatureRequestNoteContainsInformation() {
        val note = SupporterRepository.PRIORITY_FEATURE_REQUEST_NOTE
        assertTrue("Note should mention feature request", note.contains("feature request", ignoreCase = true))
        assertTrue("Note should mention Bagi-Bagi", note.contains("Bagi-Bagi", ignoreCase = true))
    }

    @Test
    fun testDeveloperThankYouMessageIsPresent() {
        val thankYou = SupporterRepository.DEVELOPER_THANK_YOU_MESSAGE
        assertTrue("Thank you message must acknowledge supporters", thankYou.contains("supporters", ignoreCase = true))
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
