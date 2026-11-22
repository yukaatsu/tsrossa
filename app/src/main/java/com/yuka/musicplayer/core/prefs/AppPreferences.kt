package com.yuka.musicplayer.core.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Unified SharedPreferences manager for tsrossa.
 * Solves the split between legacy "music_player_prefs" and "KewMobilePrefs"
 * with zero data loss by migrating legacy preferences and ensuring active user settings
 * take precedence.
 */
object AppPreferences {

    const val PREFS_NAME = "KewMobilePrefs"
    const val LEGACY_PREFS_NAME = "music_player_prefs"
    const val KEY_PREFS_UNIFIED = "prefs_unified_v1"

    const val KEY_MUSIC_ROOT_DIRECTORY = "music_root_directory"
    const val KEY_MUSIC_ROOT_TREE_URI = "music_root_tree_uri"
    const val KEY_MUSIC_ROOT_TREE_PATH = "music_root_tree_path"

    /**
     * Migrates legacy preferences into target preferences with correct precedence:
     * 1. Copies all legacy entries into target.
     * 2. Overlays target's existing entries on top so newer user settings are NEVER overwritten.
     * 3. Sets [KEY_PREFS_UNIFIED] to true.
     *
     * @return true if migration was performed, false if already migrated.
     */
    fun migrate(legacyPrefs: SharedPreferences, targetPrefs: SharedPreferences): Boolean {
        if (targetPrefs.getBoolean(KEY_PREFS_UNIFIED, false)) {
            return false
        }

        val legacyEntries = legacyPrefs.all
        val targetEntries = targetPrefs.all

        val editor = targetPrefs.edit()

        // Step 1: Copy legacy values
        for ((key, value) in legacyEntries) {
            putTypedValue(editor, key, value)
        }

        // Step 2: Overlay target values (newer active settings win)
        for ((key, value) in targetEntries) {
            putTypedValue(editor, key, value)
        }

        // Step 3: Mark migration as complete
        editor.putBoolean(KEY_PREFS_UNIFIED, true)
        editor.apply()
        return true
    }

    @Suppress("UNCHECKED_CAST")
    private fun putTypedValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> editor.putStringSet(key, value as? Set<String>)
        }
    }

    /**
     * Returns the unified [SharedPreferences] instance, performing migration if necessary.
     */
    fun get(context: Context): SharedPreferences {
        val targetPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!targetPrefs.getBoolean(KEY_PREFS_UNIFIED, false)) {
            val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            migrate(legacyPrefs, targetPrefs)
        }
        return targetPrefs
    }

    fun getMusicRootDirectory(context: Context): String? {
        return get(context).getString(KEY_MUSIC_ROOT_DIRECTORY, null)
    }

    fun setMusicRootDirectory(context: Context, path: String?) {
        get(context).edit().putString(KEY_MUSIC_ROOT_DIRECTORY, path).apply()
    }

    fun getMusicRootTreeUri(context: Context): String? {
        return get(context).getString(KEY_MUSIC_ROOT_TREE_URI, null)
    }

    fun setMusicRootTreeUri(context: Context, uri: String?) {
        get(context).edit().putString(KEY_MUSIC_ROOT_TREE_URI, uri).apply()
    }

    fun getMusicRootTreePath(context: Context): String? {
        return get(context).getString(KEY_MUSIC_ROOT_TREE_PATH, null)
    }

    fun setMusicRootTreePath(context: Context, path: String?) {
        get(context).edit().putString(KEY_MUSIC_ROOT_TREE_PATH, path).apply()
    }
}
