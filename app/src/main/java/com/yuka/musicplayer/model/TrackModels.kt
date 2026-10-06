package com.yuka.musicplayer.model

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import java.io.File

enum class ViewState { LIBRARY, PLAYLIST, TRACK, SETTINGS }

enum class PlaybackSource { LIBRARY, PLAYLIST }

enum class RepeatMode { OFF, ALL, ONE }

enum class LibrarySortMode(val label: String, val shortLabel: String) {
    DATE_DESC("DATE (NEWEST)", "DATE ↓"),
    DATE_ASC("DATE (OLDEST)", "DATE ↑"),
    NAME_ASC("NAME (A-Z)", "A-Z"),
    NAME_DESC("NAME (Z-A)", "Z-A");

    fun next(): LibrarySortMode = when (this) {
        DATE_DESC -> DATE_ASC
        DATE_ASC -> NAME_ASC
        NAME_ASC -> NAME_DESC
        NAME_DESC -> DATE_DESC
    }
}

fun sortLibraryFiles(files: List<File>, mode: LibrarySortMode): List<File> {
    return when (mode) {
        LibrarySortMode.DATE_DESC -> files.sortedWith(compareBy<File>({ !it.isDirectory }).thenByDescending { it.lastModified() })
        LibrarySortMode.DATE_ASC -> files.sortedWith(compareBy<File>({ !it.isDirectory }).thenBy { it.lastModified() })
        LibrarySortMode.NAME_ASC -> files.sortedWith(compareBy<File>({ !it.isDirectory }).thenBy { it.name.lowercase() })
        LibrarySortMode.NAME_DESC -> files.sortedWith(compareBy<File>({ !it.isDirectory }).thenByDescending { it.name.lowercase() })
    }
}

fun loadPlaylist(context: Context): List<String> {
    val file = File(context.filesDir, "playlist.txt")
    if (!file.exists()) return emptyList()
    return try {
        file.readLines().filter { it.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }
}

fun appendPlaylist(context: Context, path: String, currentSet: Set<String>): Boolean {
    if (currentSet.contains(path)) return false
    return try {
        val file = File(context.filesDir, "playlist.txt")
        file.appendText("$path\n")
        true
    } catch (e: Exception) {
        false
    }
}

fun removePlaylist(context: Context, path: String, currentPaths: List<String>): List<String> {
    val newPaths = currentPaths.filter { it != path }
    return try {
        val file = File(context.filesDir, "playlist.txt")
        file.writeText(newPaths.joinToString("\n") + if (newPaths.isNotEmpty()) "\n" else "")
        newPaths
    } catch (e: Exception) {
        currentPaths
    }
}

fun clearPlaylist(context: Context): Boolean {
    return try {
        val file = File(context.filesDir, "playlist.txt")
        file.writeText("")
        true
    } catch (e: Exception) {
        false
    }
}

data class AltSettingInfo(
    val interfaceNum: Int,
    val altSetting: Int,
    val subframeSize: Int
)

data class RefusedTrackEntry(
    val filename: String,
    val bitDepth: Int,
    val sampleRate: Int,
    val reason: String
)

data class TrackInfo(
    val file: File,
    val title: String,
    val artist: String,
    val album: String,
    val year: String,
    val durationSeconds: Double,
    val coverArt: Bitmap?,
    val dominantColor: Color
) {
    val codec: String
        get() = when (file.extension.lowercase()) {
            "flac" -> "FLAC"
            "wav", "wave" -> "WAV"
            else -> file.extension.uppercase().ifEmpty { "AUDIO" }
        }
}
