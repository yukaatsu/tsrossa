package com.yuka.musicplayer.util

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.yuka.musicplayer.model.ScannedMusicItem
import java.io.File

/**
 * Persistent SQLite cache for scanned music files.
 * Provides instant (<20ms) cold-start library loading and supports incremental diff scanning.
 */
class MusicLibraryDatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "music_library_cache.db"
        private const val DB_VERSION = 2
        private const val TABLE_TRACKS = "tracks"

        @Volatile
        private var instance: MusicLibraryDatabaseHelper? = null

        fun getInstance(context: Context): MusicLibraryDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: MusicLibraryDatabaseHelper(context).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TRACKS (
                file_path TEXT PRIMARY KEY,
                root_path TEXT NOT NULL,
                is_supported INTEGER NOT NULL,
                format_label TEXT NOT NULL,
                breadcrumb TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT NOT NULL,
                album_artist TEXT NOT NULL,
                genre TEXT NOT NULL,
                track_number INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                year INTEGER NOT NULL,
                composer TEXT NOT NULL,
                file_size INTEGER NOT NULL,
                sample_rate INTEGER NOT NULL DEFAULT 0,
                bit_depth INTEGER NOT NULL DEFAULT 0,
                bitrate_kbps INTEGER NOT NULL DEFAULT 0,
                date_added INTEGER NOT NULL,
                date_modified INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tracks_root ON $TABLE_TRACKS(root_path)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TRACKS")
        onCreate(db)
    }

    /**
     * Loads all cached tracks for [rootDir] immediately from SQLite.
     */
    fun getCachedTracksForRoot(rootDir: File): List<ScannedMusicItem> {
        val rootPath = rootDir.absolutePath
        val result = ArrayList<ScannedMusicItem>(256)
        val db = readableDatabase
        db.query(
            TABLE_TRACKS,
            null,
            "root_path = ?",
            arrayOf(rootPath),
            null,
            null,
            null
        ).use { cursor ->
            val idxFilePath = cursor.getColumnIndexOrThrow("file_path")
            val idxSupported = cursor.getColumnIndexOrThrow("is_supported")
            val idxFormat = cursor.getColumnIndexOrThrow("format_label")
            val idxBreadcrumb = cursor.getColumnIndexOrThrow("breadcrumb")
            val idxTitle = cursor.getColumnIndexOrThrow("title")
            val idxArtist = cursor.getColumnIndexOrThrow("artist")
            val idxAlbum = cursor.getColumnIndexOrThrow("album")
            val idxAlbumArtist = cursor.getColumnIndexOrThrow("album_artist")
            val idxGenre = cursor.getColumnIndexOrThrow("genre")
            val idxTrackNum = cursor.getColumnIndexOrThrow("track_number")
            val idxDuration = cursor.getColumnIndexOrThrow("duration_ms")
            val idxYear = cursor.getColumnIndexOrThrow("year")
            val idxComposer = cursor.getColumnIndexOrThrow("composer")
            val idxFileSize = cursor.getColumnIndexOrThrow("file_size")
            val idxSampleRate = cursor.getColumnIndexOrThrow("sample_rate")
            val idxBitDepth = cursor.getColumnIndexOrThrow("bit_depth")
            val idxBitrate = cursor.getColumnIndexOrThrow("bitrate_kbps")
            val idxDateAdded = cursor.getColumnIndexOrThrow("date_added")
            val idxDateModified = cursor.getColumnIndexOrThrow("date_modified")

            while (cursor.moveToNext()) {
                val path = cursor.getString(idxFilePath)
                result.add(
                    ScannedMusicItem.createWithPrecomputedDisplay(
                        file = File(path),
                        isSupported = cursor.getInt(idxSupported) == 1,
                        formatLabel = cursor.getString(idxFormat),
                        breadcrumb = cursor.getString(idxBreadcrumb),
                        title = cursor.getString(idxTitle),
                        artist = cursor.getString(idxArtist),
                        album = cursor.getString(idxAlbum),
                        albumArtist = cursor.getString(idxAlbumArtist),
                        genre = cursor.getString(idxGenre),
                        trackNumber = cursor.getInt(idxTrackNum),
                        durationMs = cursor.getLong(idxDuration),
                        year = cursor.getInt(idxYear),
                        composer = cursor.getString(idxComposer),
                        fileSize = cursor.getLong(idxFileSize),
                        sampleRateHz = cursor.getInt(idxSampleRate),
                        bitDepth = cursor.getInt(idxBitDepth),
                        bitrateKbps = cursor.getInt(idxBitrate),
                        dateAdded = cursor.getLong(idxDateAdded),
                        dateModified = cursor.getLong(idxDateModified)
                    )
                )
            }
        }
        return result
    }

    /**
     * Replaces/synchronizes the cached track list for [rootDir] inside a single fast SQLite transaction.
     */
    fun saveTracksForRoot(
        rootDir: File,
        itemsToUpsert: List<ScannedMusicItem>,
        pathsToDelete: Collection<String>
    ) {
        if (itemsToUpsert.isEmpty() && pathsToDelete.isEmpty()) return
        val rootPath = rootDir.absolutePath
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (path in pathsToDelete) {
                db.delete(TABLE_TRACKS, "file_path = ?", arrayOf(path))
            }
            for (item in itemsToUpsert) {
                val cv = ContentValues().apply {
                    put("file_path", item.file.absolutePath)
                    put("root_path", rootPath)
                    put("is_supported", if (item.isSupported) 1 else 0)
                    put("format_label", item.formatLabel)
                    put("breadcrumb", item.breadcrumb)
                    put("title", item.title)
                    put("artist", item.artist)
                    put("album", item.album)
                    put("album_artist", item.albumArtist)
                    put("genre", item.genre)
                    put("track_number", item.trackNumber)
                    put("duration_ms", item.durationMs)
                    put("year", item.year)
                    put("composer", item.composer)
                    put("file_size", item.fileSize)
                    put("sample_rate", item.sampleRateHz)
                    put("bit_depth", item.bitDepth)
                    put("bitrate_kbps", item.bitrateKbps)
                    put("date_added", item.dateAdded)
                    put("date_modified", item.dateModified)
                }
                db.insertWithOnConflict(TABLE_TRACKS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Atomically replaces all cached tracks for [rootDir] with [freshItems].
     * Used for full manual library rescan to eliminate stale or ghost entries.
     */
    fun replaceAllTracksForRoot(rootDir: File, freshItems: List<ScannedMusicItem>) {
        val rootPath = rootDir.absolutePath
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_TRACKS, "root_path = ?", arrayOf(rootPath))
            for (item in freshItems) {
                val cv = ContentValues().apply {
                    put("file_path", item.file.absolutePath)
                    put("root_path", rootPath)
                    put("is_supported", if (item.isSupported) 1 else 0)
                    put("format_label", item.formatLabel)
                    put("breadcrumb", item.breadcrumb)
                    put("title", item.title)
                    put("artist", item.artist)
                    put("album", item.album)
                    put("album_artist", item.albumArtist)
                    put("genre", item.genre)
                    put("track_number", item.trackNumber)
                    put("duration_ms", item.durationMs)
                    put("year", item.year)
                    put("composer", item.composer)
                    put("file_size", item.fileSize)
                    put("sample_rate", item.sampleRateHz)
                    put("bit_depth", item.bitDepth)
                    put("bitrate_kbps", item.bitrateKbps)
                    put("date_added", item.dateAdded)
                    put("date_modified", item.dateModified)
                }
                db.insertWithOnConflict(TABLE_TRACKS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
