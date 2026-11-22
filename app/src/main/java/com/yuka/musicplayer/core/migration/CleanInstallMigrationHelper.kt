package com.yuka.musicplayer.core.migration

import android.content.Context
import android.os.Build
import com.yuka.musicplayer.BuildConfig
import com.yuka.musicplayer.core.prefs.AppPreferences
import com.yuka.musicplayer.util.AlbumArtThumbnailCache
import com.yuka.musicplayer.util.MusicLibraryDatabaseHelper
import java.io.File

/**
 * Ensures a 100% clean-slate ("clear install") state whenever the application is updated
 * from a previous version OR when a user/tester installs an updated debug.apk build.
 *
 * Detection uses a deterministic Triple-Fingerprint:
 * 1. [CLEAN_INSTALL_SCHEMA_VERSION] (post-refactoring architecture schema)
 * 2. [BuildConfig.VERSION_CODE] & [BuildConfig.VERSION_NAME]
 * 3. [android.content.pm.PackageInfo.lastUpdateTime] (updated by Android OS on every APK install/update,
 *    including `adb install -r app-debug.apk` or Package Installer updates with identical versionCode,
 *    while remaining constant across normal app restarts).
 */
object CleanInstallMigrationHelper {

    const val CLEAN_INSTALL_SCHEMA_VERSION = 1
    const val MARKER_FILE_NAME = ".tsrossa_clean_install_marker_v1"

    data class InstallFingerprint(
        val schemaVersion: Int,
        val versionCode: Int,
        val versionName: String,
        val lastUpdateTime: Long
    ) {
        fun serialize(): String = "$schemaVersion|$versionCode|$versionName|$lastUpdateTime"

        companion object {
            fun parse(raw: String?): InstallFingerprint? {
                if (raw.isNullOrBlank()) return null
                val parts = raw.trim().split("|")
                if (parts.size != 4) return null
                val schema = parts[0].toIntOrNull() ?: return null
                val code = parts[1].toIntOrNull() ?: return null
                val name = parts[2]
                val updateTime = parts[3].toLongOrNull() ?: return null
                return InstallFingerprint(
                    schemaVersion = schema,
                    versionCode = code,
                    versionName = name,
                    lastUpdateTime = updateTime
                )
            }
        }
    }

    /**
     * Pure evaluation function (unit-testable): returns true whenever [saved] is missing
     * or differs from [current] (indicating an upgrade from an older version or a freshly installed debug.apk update).
     */
    fun shouldPerformCleanInstallWipe(
        saved: InstallFingerprint?,
        current: InstallFingerprint
    ): Boolean {
        if (saved == null) return true
        if (saved.schemaVersion != current.schemaVersion) return true
        if (saved.versionCode != current.versionCode) return true
        if (saved.versionName != current.versionName) return true
        if (saved.lastUpdateTime != current.lastUpdateTime) return true
        return false
    }

    /**
     * Checks the current package install/update fingerprint and, if an update or first-time migration
     * is detected, wipes all leftover caches, SQLite databases, SharedPreferences, and legacy files
     * before writing the new fingerprint marker.
     *
     * @return true if a clean install wipe was executed on this launch.
     */
    fun ensureCleanInstallOnUpdate(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        val currentFingerprint = resolveCurrentFingerprint(appContext)
        val markerFile = getMarkerFile(appContext)
        val savedFingerprint = try {
            if (markerFile.exists()) InstallFingerprint.parse(markerFile.readText()) else null
        } catch (_: Throwable) {
            null
        }

        if (!shouldPerformCleanInstallWipe(savedFingerprint, currentFingerprint)) {
            return false
        }

        performFullLegacyWipe(appContext, markerFile)

        try {
            markerFile.parentFile?.mkdirs()
            markerFile.writeText(currentFingerprint.serialize())
        } catch (_: Throwable) {}

        return true
    }

    private fun resolveCurrentFingerprint(context: Context): InstallFingerprint {
        val lastUpdate = try {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            pkgInfo.lastUpdateTime
        } catch (_: Throwable) {
            0L
        }
        return InstallFingerprint(
            schemaVersion = CLEAN_INSTALL_SCHEMA_VERSION,
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME,
            lastUpdateTime = lastUpdate
        )
    }

    private fun getMarkerFile(context: Context): File {
        val baseDir = context.noBackupFilesDir ?: context.filesDir
        return File(baseDir, MARKER_FILE_NAME)
    }

    private fun performFullLegacyWipe(context: Context, markerFile: File) {
        // 1. Close & delete SQLite databases (music_library_cache.db and any legacy dbs)
        try {
            MusicLibraryDatabaseHelper.resetAndDeleteDatabase(context)
            context.databaseList()?.forEach { dbName ->
                runCatching { context.deleteDatabase(dbName) }
            }
        } catch (_: Throwable) {}

        // 2. Clear & delete all SharedPreferences (active KewMobilePrefs, legacy music_player_prefs, and any other XMLs)
        try {
            val knownPrefs = mutableSetOf(
                AppPreferences.PREFS_NAME,
                AppPreferences.LEGACY_PREFS_NAME,
                "${context.packageName}_preferences"
            )
            val dataDir = context.applicationInfo?.dataDir?.let { File(it) } ?: context.filesDir?.parentFile
            val sharedPrefsDir = dataDir?.let { File(it, "shared_prefs") }
            sharedPrefsDir?.listFiles()?.forEach { file ->
                if (file.name.endsWith(".xml")) {
                    knownPrefs.add(file.name.removeSuffix(".xml"))
                }
            }
            for (prefName in knownPrefs) {
                runCatching {
                    context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                        .edit()
                        .clear()
                        .commit()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    runCatching { context.deleteSharedPreferences(prefName) }
                }
            }
            sharedPrefsDir?.listFiles()?.forEach { file ->
                runCatching { file.delete() }
            }
        } catch (_: Throwable) {}

        // 3. Evict RAM caches & wipe internal/external cache directories (album_art_cache, updates, etc.)
        try {
            AlbumArtThumbnailCache.clearCache()
        } catch (_: Throwable) {}
        deleteDirectoryContents(context.cacheDir)
        context.externalCacheDir?.let { deleteDirectoryContents(it) }

        // 4. Wipe leftover files in filesDir & noBackupFilesDir (legacy playlists, old temp files)
        deleteDirectoryContents(context.filesDir, excludeFile = markerFile)
        context.noBackupFilesDir?.let { deleteDirectoryContents(it, excludeFile = markerFile) }
    }

    internal fun deleteDirectoryContents(dir: File?, excludeFile: File? = null) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { child ->
            if (excludeFile != null && child.absolutePath == excludeFile.absolutePath) {
                return@forEach
            }
            try {
                if (child.isDirectory) {
                    deleteDirectoryContents(child, excludeFile)
                }
                child.delete()
            } catch (_: Throwable) {}
        }
    }
}
