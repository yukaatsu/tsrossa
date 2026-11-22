package com.yuka.musicplayer.core.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CleanInstallMigrationHelperTest {

    @Test
    fun shouldPerformCleanInstallWipe_triggersOnFirstMigrationFromLegacyVersion() {
        val current = CleanInstallMigrationHelper.InstallFingerprint(
            schemaVersion = 1,
            versionCode = 17,
            versionName = "1.8.1-BETA",
            lastUpdateTime = 1700000000000L
        )
        assertTrue(CleanInstallMigrationHelper.shouldPerformCleanInstallWipe(saved = null, current = current))
    }

    @Test
    fun shouldPerformCleanInstallWipe_triggersWhenReleaseVersionCodeOrNameChanges() {
        val saved = CleanInstallMigrationHelper.InstallFingerprint(
            schemaVersion = 1,
            versionCode = 16,
            versionName = "1.8.0-BETA",
            lastUpdateTime = 1700000000000L
        )
        val current = CleanInstallMigrationHelper.InstallFingerprint(
            schemaVersion = 1,
            versionCode = 17,
            versionName = "1.8.1-BETA",
            lastUpdateTime = 1700000000000L
        )
        assertTrue(CleanInstallMigrationHelper.shouldPerformCleanInstallWipe(saved, current))
    }

    @Test
    fun shouldPerformCleanInstallWipe_triggersOnDebugApkUpdateEvenWhenVersionCodeUnchanged() {
        val saved = CleanInstallMigrationHelper.InstallFingerprint(
            schemaVersion = 1,
            versionCode = 17,
            versionName = "1.8.1-BETA",
            lastUpdateTime = 1700000000000L
        )
        val updatedDebugApk = CleanInstallMigrationHelper.InstallFingerprint(
            schemaVersion = 1,
            versionCode = 17,
            versionName = "1.8.1-BETA",
            lastUpdateTime = 1700000050000L
        )
        assertTrue(CleanInstallMigrationHelper.shouldPerformCleanInstallWipe(saved, updatedDebugApk))
    }

    @Test
    fun shouldPerformCleanInstallWipe_skipsWipeOnNormalAppReopenWithoutUpdate() {
        val saved = CleanInstallMigrationHelper.InstallFingerprint(
            schemaVersion = 1,
            versionCode = 17,
            versionName = "1.8.1-BETA",
            lastUpdateTime = 1700000050000L
        )
        val current = CleanInstallMigrationHelper.InstallFingerprint(
            schemaVersion = 1,
            versionCode = 17,
            versionName = "1.8.1-BETA",
            lastUpdateTime = 1700000050000L
        )
        assertFalse(CleanInstallMigrationHelper.shouldPerformCleanInstallWipe(saved, current))
    }

    @Test
    fun installFingerprint_serializesAndParsesCleanly() {
        val fp = CleanInstallMigrationHelper.InstallFingerprint(
            schemaVersion = 1,
            versionCode = 17,
            versionName = "1.8.1-BETA",
            lastUpdateTime = 1763809200000L
        )
        val serialized = fp.serialize()
        assertEquals("1|17|1.8.1-BETA|1763809200000", serialized)
        assertEquals(fp, CleanInstallMigrationHelper.InstallFingerprint.parse(serialized))
        assertNull(CleanInstallMigrationHelper.InstallFingerprint.parse("invalid"))
        assertNull(CleanInstallMigrationHelper.InstallFingerprint.parse(""))
    }

    @Test
    fun deleteDirectoryContents_removesNestedLegacyFilesWhilePreservingMarker() {
        val tempRoot = Files.createTempDirectory("tsrossa_clean_install_test").toFile()
        try {
            val nestedCache = File(tempRoot, "album_art_cache").apply { mkdirs() }
            val oldThumb = File(nestedCache, "abc_thumb_96.jpg").apply { writeText("old_thumb") }
            val oldColor = File(nestedCache, "abc_color_v4.txt").apply { writeText("#FF00FF00") }
            val marker = File(tempRoot, CleanInstallMigrationHelper.MARKER_FILE_NAME).apply {
                writeText("1|17|1.8.1-BETA|123456")
            }

            CleanInstallMigrationHelper.deleteDirectoryContents(tempRoot, excludeFile = marker)

            assertFalse(oldThumb.exists())
            assertFalse(oldColor.exists())
            assertFalse(nestedCache.exists())
            assertTrue(marker.exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
