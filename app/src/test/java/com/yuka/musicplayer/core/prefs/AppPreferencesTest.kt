package com.yuka.musicplayer.core.prefs

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesTest {

    private class FakeSharedPreferences(
        val values: MutableMap<String, Any?> = mutableMapOf()
    ) : SharedPreferences {

        override fun getAll(): MutableMap<String, *> = HashMap(values)
        override fun getString(key: String?, defValue: String?): String? = (values[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (values[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (values[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (values[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (values[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (values[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val temp = HashMap(values)

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) temp[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) temp.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                temp.clear()
                return this
            }
            override fun commit(): Boolean {
                values.clear()
                values.putAll(temp)
                return true
            }
            override fun apply() {
                commit()
            }
        }
    }

    @Test
    fun migrate_copiesLegacyToTargetWhenTargetIsEmpty() {
        val legacy = FakeSharedPreferences(mutableMapOf(
            "music_root_directory" to "/storage/emulated/0/Music",
            "first_run_completed" to true,
            "sample_rate_pref" to 96000
        ))
        val target = FakeSharedPreferences(mutableMapOf())

        val result = AppPreferences.migrate(legacy, target)
        assertTrue(result)
        assertEquals("/storage/emulated/0/Music", target.getString("music_root_directory", null))
        assertTrue(target.getBoolean("first_run_completed", false))
        assertEquals(96000, target.getInt("sample_rate_pref", 0))
        assertTrue(target.getBoolean(AppPreferences.KEY_PREFS_UNIFIED, false))
    }

    @Test
    fun migrate_preservesTargetValuesOverLegacyValues() {
        // Target has newer user settings in KewMobilePrefs; legacy has older settings
        val legacy = FakeSharedPreferences(mutableMapOf(
            "music_root_directory" to "/storage/emulated/0/OldMusic",
            "legacy_only_key" to "from_legacy"
        ))
        val target = FakeSharedPreferences(mutableMapOf(
            "music_root_directory" to "/storage/emulated/0/NewFlacCollection"
        ))

        val result = AppPreferences.migrate(legacy, target)
        assertTrue(result)
        // Target must NOT be overwritten by legacy!
        assertEquals("/storage/emulated/0/NewFlacCollection", target.getString("music_root_directory", null))
        assertEquals("from_legacy", target.getString("legacy_only_key", null))
        assertTrue(target.getBoolean(AppPreferences.KEY_PREFS_UNIFIED, false))
    }

    @Test
    fun migrate_isIdempotentWhenAlreadyUnified() {
        val legacy = FakeSharedPreferences(mutableMapOf(
            "music_root_directory" to "/storage/emulated/0/OldMusic"
        ))
        val target = FakeSharedPreferences(mutableMapOf(
            AppPreferences.KEY_PREFS_UNIFIED to true,
            "music_root_directory" to "/storage/emulated/0/CurrentMusic"
        ))

        val result = AppPreferences.migrate(legacy, target)
        assertFalse("Migration should return false when already unified", result)
        assertEquals("/storage/emulated/0/CurrentMusic", target.getString("music_root_directory", null))
    }
}
