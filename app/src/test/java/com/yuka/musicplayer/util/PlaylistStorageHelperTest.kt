package com.yuka.musicplayer.util

import android.content.SharedPreferences
import com.yuka.musicplayer.model.Playlist
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlaylistStorageHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeSharedPreferences(private val initialMigrated: Boolean) : SharedPreferences {
        val storage = mutableMapOf<String, Any?>("playlists_v2_migrated" to initialMigrated)
        var applied = false

        override fun getAll(): MutableMap<String, *> = storage
        override fun getString(key: String?, defValue: String?): String? = storage[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String?, defValue: Int): Int = storage[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = storage[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = storage[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = storage[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = storage.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor { storage[key!!] = value; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor { storage[key!!] = value; return this }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor { storage[key!!] = value; return this }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { storage[key!!] = value; return this }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { storage[key!!] = value; return this }
            override fun remove(key: String?): SharedPreferences.Editor { storage.remove(key); return this }
            override fun clear(): SharedPreferences.Editor { storage.clear(); return this }
            override fun commit(): Boolean { applied = true; return true }
            override fun apply() { applied = true }
        }
    }

    @Test
    fun testCreateAndLoadMultiplePlaylists() {
        val filesDir = tempFolder.newFolder("files")

        val (p1, list1) = PlaylistStorageHelper.createPlaylist(filesDir, "Favorites")
        assertEquals(1, list1.size)
        val (p2, list2) = PlaylistStorageHelper.createPlaylist(filesDir, "Workout")
        assertEquals(2, list2.size)

        val all = PlaylistStorageHelper.loadAllPlaylists(filesDir)
        assertEquals(2, all.size)
        assertTrue(all.any { it.name == "Favorites" })
        assertTrue(all.any { it.name == "Workout" })
    }

    @Test
    fun testAddAndRemoveTracksWithinSpecificPlaylist() {
        val filesDir = tempFolder.newFolder("files")

        val (p1, _) = PlaylistStorageHelper.createPlaylist(filesDir, "Chill")
        val (p2, _) = PlaylistStorageHelper.createPlaylist(filesDir, "Focus")

        val afterAdd1 = PlaylistStorageHelper.addTrackToPlaylist(filesDir, p1.id, "/sdcard/chill1.flac")
        assertNotNull(afterAdd1)
        val afterAdd2 = PlaylistStorageHelper.addTrackToPlaylist(filesDir, p2.id, "/sdcard/focus1.flac")
        assertNotNull(afterAdd2)

        val updatedP1 = afterAdd2?.firstOrNull { it.id == p1.id }
        val updatedP2 = afterAdd2?.firstOrNull { it.id == p2.id }

        assertEquals(listOf("/sdcard/chill1.flac"), updatedP1?.trackPaths)
        assertEquals(listOf("/sdcard/focus1.flac"), updatedP2?.trackPaths)

        // Remove from p1 should not affect p2
        val afterRemove = PlaylistStorageHelper.removeTrackFromPlaylist(filesDir, p1.id, "/sdcard/chill1.flac")
        assertNotNull(afterRemove)
        assertEquals(emptyList<String>(), afterRemove?.firstOrNull { it.id == p1.id }?.trackPaths)
        assertEquals(listOf("/sdcard/focus1.flac"), afterRemove?.firstOrNull { it.id == p2.id }?.trackPaths)
    }

    @Test
    fun testRenameAndDeletePlaylist() {
        val filesDir = tempFolder.newFolder("files")

        val (p, _) = PlaylistStorageHelper.createPlaylist(filesDir, "Old Name")
        val renamedList = PlaylistStorageHelper.renamePlaylist(filesDir, p.id, "New Name")
        assertNotNull(renamedList)
        assertEquals("New Name", renamedList?.firstOrNull { it.id == p.id }?.name)

        val remainingList = PlaylistStorageHelper.deletePlaylist(filesDir, p.id)
        assertNotNull(remainingList)
        assertEquals(0, remainingList?.size)
        assertNull(PlaylistStorageHelper.getPlaylist(filesDir, p.id))
    }

    @Test
    fun testLegacyPlaylistResetWipesOldPlaylistTxt() {
        val filesDir = tempFolder.newFolder("files")
        val oldFile = File(filesDir, "playlist.txt").apply { writeText("/old/track.flac\n") }
        val fakePrefs = FakeSharedPreferences(initialMigrated = false)

        PlaylistStorageHelper.performOneTimeLegacyReset(filesDir, fakePrefs)

        assertFalse(oldFile.exists())
        assertTrue(fakePrefs.getBoolean("playlists_v2_migrated", false))
        assertTrue(fakePrefs.applied)
    }

    @Test
    fun testAtomicSaveAndCacheInvalidation() {
        val filesDir = tempFolder.newFolder("files")
        PlaylistStorageHelper.invalidateCache()

        val p = Playlist(name = "Atomic Test")
        val saved = PlaylistStorageHelper.saveAllPlaylists(filesDir, listOf(p))
        assertTrue(saved)

        val targetFile = File(filesDir, "playlists_v2.json")
        val tmpFile = File(filesDir, "playlists_v2.json.tmp")
        assertTrue(targetFile.exists())
        assertFalse(tmpFile.exists())

        // Cache verification
        val loaded = PlaylistStorageHelper.loadAllPlaylists(filesDir)
        assertEquals(1, loaded.size)
        assertEquals("Atomic Test", loaded[0].name)

        // Invalidate and reload
        PlaylistStorageHelper.invalidateCache()
        val reloaded = PlaylistStorageHelper.loadAllPlaylists(filesDir)
        assertEquals(1, reloaded.size)
        assertEquals("Atomic Test", reloaded[0].name)
    }
}

