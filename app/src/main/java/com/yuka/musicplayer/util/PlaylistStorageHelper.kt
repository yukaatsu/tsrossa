package com.yuka.musicplayer.util

import android.content.Context
import android.content.SharedPreferences
import com.yuka.musicplayer.model.Playlist
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PlaylistStorageHelper {

    private const val PLAYLISTS_FILE = "playlists_v2.json"
    private const val PLAYLISTS_TMP_FILE = "playlists_v2.json.tmp"
    private const val LEGACY_FILE = "playlist.txt"
    private const val PREF_MIGRATED = "playlists_v2_migrated"

    @Volatile
    private var cachedBaseDirPath: String? = null
    @Volatile
    private var cachedPlaylists: List<Playlist>? = null

    fun invalidateCache() {
        synchronized(this) {
            cachedBaseDirPath = null
            cachedPlaylists = null
        }
    }

    @Synchronized
    fun loadAllPlaylists(baseDir: File): List<Playlist> {
        val currentPath = baseDir.absolutePath
        if (cachedBaseDirPath == currentPath && cachedPlaylists != null) {
            return cachedPlaylists!!
        }

        val file = File(baseDir, PLAYLISTS_FILE)
        if (!file.exists()) {
            cachedBaseDirPath = currentPath
            cachedPlaylists = emptyList()
            return emptyList()
        }

        return try {
            val content = file.readText()
            if (content.isBlank()) {
                cachedBaseDirPath = currentPath
                cachedPlaylists = emptyList()
                return emptyList()
            }

            val jsonArray = JSONArray(content)
            val list = mutableListOf<Playlist>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val isPinned = obj.optBoolean("isPinned", false)
                val tracksJson = obj.optJSONArray("trackPaths") ?: JSONArray()
                val tracks = mutableListOf<String>()
                for (j in 0 until tracksJson.length()) {
                    tracks.add(tracksJson.getString(j))
                }
                list.add(Playlist(id = id, name = name, trackPaths = tracks, createdAt = createdAt, isPinned = isPinned))
            }
            val sorted = list.sortedWith(compareByDescending<Playlist> { it.isPinned }.thenBy { it.createdAt })
            cachedBaseDirPath = currentPath
            cachedPlaylists = sorted
            sorted
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun loadAllPlaylists(context: Context): List<Playlist> = loadAllPlaylists(context.filesDir)

    @Synchronized
    fun saveAllPlaylists(baseDir: File, playlists: List<Playlist>): Boolean {
        return try {
            val targetFile = File(baseDir, PLAYLISTS_FILE)
            val tempFile = File(baseDir, PLAYLISTS_TMP_FILE)
            val jsonArray = JSONArray()
            for (p in playlists) {
                val obj = JSONObject()
                obj.put("id", p.id)
                obj.put("name", p.name)
                obj.put("createdAt", p.createdAt)
                obj.put("isPinned", p.isPinned)
                val tracksJson = JSONArray()
                p.trackPaths.forEach { tracksJson.put(it) }
                obj.put("trackPaths", tracksJson)
                jsonArray.put(obj)
            }

            // 1. Write to temp file first
            tempFile.writeText(jsonArray.toString(2))

            // 2. Atomically rename temp file over target file
            val success = if (targetFile.exists()) {
                targetFile.delete() && tempFile.renameTo(targetFile)
            } else {
                tempFile.renameTo(targetFile)
            }

            if (success) {
                cachedBaseDirPath = baseDir.absolutePath
                cachedPlaylists = playlists
                true
            } else {
                // Fallback direct write if atomic rename is not supported by filesystem
                targetFile.writeText(jsonArray.toString(2))
                tempFile.delete()
                cachedBaseDirPath = baseDir.absolutePath
                cachedPlaylists = playlists
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun saveAllPlaylists(context: Context, playlists: List<Playlist>): Boolean = saveAllPlaylists(context.filesDir, playlists)

    fun getPlaylist(baseDir: File, id: String): Playlist? {
        return loadAllPlaylists(baseDir).firstOrNull { it.id == id }
    }

    fun getPlaylist(context: Context, id: String): Playlist? = getPlaylist(context.filesDir, id)

    @Synchronized
    fun createPlaylist(baseDir: File, name: String): Pair<Playlist, List<Playlist>> {
        val current = loadAllPlaylists(baseDir).toMutableList()
        val trimmed = name.trim().ifEmpty { "Playlist ${current.size + 1}" }
        val newPlaylist = Playlist(name = trimmed)
        current.add(newPlaylist)
        saveAllPlaylists(baseDir, current)
        return Pair(newPlaylist, current)
    }

    fun createPlaylist(context: Context, name: String): Pair<Playlist, List<Playlist>> = createPlaylist(context.filesDir, name)

    @Synchronized
    fun renamePlaylist(baseDir: File, id: String, newName: String): List<Playlist>? {
        val current = loadAllPlaylists(baseDir).toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index == -1) return null
        val trimmed = newName.trim().ifEmpty { current[index].name }
        val updated = current[index].copy(name = trimmed)
        current[index] = updated
        val saved = saveAllPlaylists(baseDir, current)
        return if (saved) current else null
    }

    fun renamePlaylist(context: Context, id: String, newName: String): List<Playlist>? = renamePlaylist(context.filesDir, id, newName)

    @Synchronized
    fun deletePlaylist(baseDir: File, id: String): List<Playlist>? {
        val current = loadAllPlaylists(baseDir).toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) {
            val saved = saveAllPlaylists(baseDir, current)
            return if (saved) current else null
        }
        return null
    }

    fun deletePlaylist(context: Context, id: String): List<Playlist>? = deletePlaylist(context.filesDir, id)

    @Synchronized
    fun addTrackToPlaylist(baseDir: File, playlistId: String, trackPath: String): List<Playlist>? {
        val current = loadAllPlaylists(baseDir).toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index == -1) return null
        val playlist = current[index]
        if (playlist.trackPaths.contains(trackPath)) return current

        val updatedTracks = playlist.trackPaths + trackPath
        current[index] = playlist.copy(trackPaths = updatedTracks)
        val saved = saveAllPlaylists(baseDir, current)
        return if (saved) current else null
    }

    fun addTrackToPlaylist(context: Context, playlistId: String, trackPath: String): List<Playlist>? = addTrackToPlaylist(context.filesDir, playlistId, trackPath)

    @Synchronized
    fun removeTrackFromPlaylist(baseDir: File, playlistId: String, trackPath: String): List<Playlist>? {
        val current = loadAllPlaylists(baseDir).toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index == -1) return null
        val playlist = current[index]
        val updatedTracks = playlist.trackPaths.filter { it != trackPath }
        current[index] = playlist.copy(trackPaths = updatedTracks)
        val saved = saveAllPlaylists(baseDir, current)
        return if (saved) current else null
    }

    fun removeTrackFromPlaylist(context: Context, playlistId: String, trackPath: String): List<Playlist>? = removeTrackFromPlaylist(context.filesDir, playlistId, trackPath)

    @Synchronized
    fun clearPlaylistTracks(baseDir: File, playlistId: String): List<Playlist>? {
        val current = loadAllPlaylists(baseDir).toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index == -1) return null
        current[index] = current[index].copy(trackPaths = emptyList())
        val saved = saveAllPlaylists(baseDir, current)
        return if (saved) current else null
    }

    fun clearPlaylistTracks(context: Context, playlistId: String): List<Playlist>? = clearPlaylistTracks(context.filesDir, playlistId)

    @Synchronized
    fun togglePinPlaylist(baseDir: File, playlistId: String): List<Playlist>? {
        val current = loadAllPlaylists(baseDir).toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index == -1) return null
        val target = current[index]
        current[index] = target.copy(isPinned = !target.isPinned)
        val sorted = current.sortedWith(compareByDescending<Playlist> { it.isPinned }.thenBy { it.createdAt })
        saveAllPlaylists(baseDir, sorted)
        return sorted
    }

    fun togglePinPlaylist(context: Context, playlistId: String): List<Playlist>? =
        togglePinPlaylist(context.filesDir, playlistId)

    @Synchronized
    fun performOneTimeLegacyReset(baseDir: File, sharedPref: SharedPreferences) {
        val migrated = sharedPref.getBoolean(PREF_MIGRATED, false)
        if (!migrated) {
            try {
                val legacy = File(baseDir, LEGACY_FILE)
                if (legacy.exists()) {
                    legacy.delete()
                }
            } catch (_: Exception) {}
            sharedPref.edit().putBoolean(PREF_MIGRATED, true).apply()
        }
    }

    fun performOneTimeLegacyReset(context: Context, sharedPref: SharedPreferences) =
        performOneTimeLegacyReset(context.filesDir, sharedPref)
}
