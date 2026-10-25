package com.yuka.musicplayer.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun testBeta10IsNewerThanBeta2() {
        assertTrue(AppUpdateManager.isNewerVersion("v1.5.0-beta10", "1.5.0-beta2"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.5.0-beta2", "1.5.0-beta10"))
    }

    @Test
    fun testStableIsNewerThanPreRelease() {
        assertTrue(AppUpdateManager.isNewerVersion("1.5.0", "1.5.0-rc1"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.5.0", "1.5.0-beta10"))
        assertFalse(AppUpdateManager.isNewerVersion("1.5.0-rc1", "1.5.0"))
    }

    @Test
    fun testRcIsNewerThanBetaAndAlpha() {
        assertTrue(AppUpdateManager.isNewerVersion("1.5.0-rc1", "1.5.0-beta10"))
        assertTrue(AppUpdateManager.isNewerVersion("1.5.0-beta1", "1.5.0-alpha5"))
        assertFalse(AppUpdateManager.isNewerVersion("1.5.0-beta5", "1.5.0-rc1"))
    }

    @Test
    fun testMajorAndMinorVersionChanges() {
        assertTrue(AppUpdateManager.isNewerVersion("v2.0.0", "1.9.9"))
        assertTrue(AppUpdateManager.isNewerVersion("1.3.0", "1.2.9"))
        assertFalse(AppUpdateManager.isNewerVersion("1.2.0", "1.2.1"))
    }

    @Test
    fun testIdenticalVersions() {
        assertFalse(AppUpdateManager.isNewerVersion("1.5.0", "1.5.0"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.5.0-beta1", "1.5.0-beta1"))
    }
}
