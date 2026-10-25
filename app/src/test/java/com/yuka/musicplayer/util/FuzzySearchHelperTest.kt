package com.yuka.musicplayer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzySearchHelperTest {

    @Test
    fun testExactAndSubstringMatch() {
        assertTrue(FuzzySearchHelper.matches("Bohemian Rhapsody", "bohemian"))
        assertTrue(FuzzySearchHelper.matches("Bohemian Rhapsody", "RHAPSODY"))
        assertTrue(FuzzySearchHelper.matches("Hotel California", "calif"))
    }

    @Test
    fun testSubsequenceFuzzyMatch() {
        // "bhm" matches "Bohemian"
        assertTrue(FuzzySearchHelper.matches("Bohemian Rhapsody", "bhm"))
        // "clfrn" matches "California"
        assertTrue(FuzzySearchHelper.matches("Hotel California", "clfrn"))
    }

    @Test
    fun testEmptyQueryAlwaysMatches() {
        assertTrue(FuzzySearchHelper.matches("Anything", ""))
        assertTrue(FuzzySearchHelper.matches("Anything", "   "))
    }

    @Test
    fun testUnrelatedQueryDoesNotMatch() {
        assertFalse(FuzzySearchHelper.matches("Bohemian Rhapsody", "metallica"))
        assertFalse(FuzzySearchHelper.matches("Stairway to Heaven", "xyz123"))
    }

    @Test
    fun testMultiFieldFuzzyMatch() {
        val fields = listOf("Stairway to Heaven", "Led Zeppelin", "Led Zeppelin IV", "Rock")
        assertTrue(FuzzySearchHelper.matchesAny(fields, "zep"))
        assertTrue(FuzzySearchHelper.matchesAny(fields, "heaven"))
        assertTrue(FuzzySearchHelper.matchesAny(fields, "stairway"))
        assertFalse(FuzzySearchHelper.matchesAny(fields, "queen"))
    }
}
