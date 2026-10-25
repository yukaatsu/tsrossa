package com.yuka.musicplayer.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import java.io.File

enum class ViewState { LIBRARY, PLAYLIST, TRACK, SETTINGS }

enum class PlaybackSource { LIBRARY, PLAYLIST }

enum class RepeatMode { OFF, ALL, ONE }

enum class SortField(val displayName: String) {
    TITLE("Title"),
    ALBUM("Album"),
    ARTIST("Artist"),
    ALBUM_ARTIST("Album Artist"),
    GENRE("Genre"),
    TRACK_NUMBER("Track Number"),
    DURATION("Duration"),
    YEAR("Year"),
    COMPOSER("Composer"),
    SIZE("File Size"),
    DATE_ADDED("Date Added"),
    DATE_MODIFIED("Date Modified")
}

enum class SortOrder(val displayName: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

fun sortScannedItemsByField(
    items: List<ScannedMusicItem>,
    field: SortField,
    order: SortOrder = SortOrder.ASCENDING
): List<ScannedMusicItem> {
    val baseComparator: Comparator<ScannedMusicItem> = when (field) {
        SortField.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        SortField.ALBUM -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.album.ifEmpty { "\uFFFF" } }
        SortField.ARTIST -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist.ifEmpty { "\uFFFF" } }
        SortField.ALBUM_ARTIST -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.albumArtist.ifEmpty { it.artist.ifEmpty { "\uFFFF" } } }
        SortField.GENRE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.genre.ifEmpty { "\uFFFF" } }
        SortField.TRACK_NUMBER -> compareBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
        SortField.DURATION -> compareBy { it.durationMs }
        SortField.YEAR -> compareBy { if (it.year > 0) it.year else Int.MAX_VALUE }
        SortField.COMPOSER -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.composer.ifEmpty { "\uFFFF" } }
        SortField.SIZE -> compareBy { it.fileSize }
        SortField.DATE_ADDED -> compareBy { it.dateAdded }
        SortField.DATE_MODIFIED -> compareBy { it.dateModified }
    }
    val tieBreaker = baseComparator.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    return if (order == SortOrder.ASCENDING) {
        items.sortedWith(tieBreaker)
    } else {
        items.sortedWith(tieBreaker.reversed())
    }
}

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

fun sortScannedMusicItems(items: List<ScannedMusicItem>, mode: LibrarySortMode): List<ScannedMusicItem> {
    return when (mode) {
        LibrarySortMode.DATE_DESC -> items.sortedWith(compareByDescending<ScannedMusicItem> { it.dateModified }.thenBy { it.file.name.lowercase() })
        LibrarySortMode.DATE_ASC -> items.sortedWith(compareBy<ScannedMusicItem> { it.dateModified }.thenBy { it.file.name.lowercase() })
        LibrarySortMode.NAME_ASC -> items.sortedBy { it.file.name.lowercase() }
        LibrarySortMode.NAME_DESC -> items.sortedByDescending { it.file.name.lowercase() }
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
    val dominantColor: Color,
    val isMqa: Boolean = false,
    val blurredCoverArt: Bitmap? = null,
    val isMqaStudio: Boolean = false,
    val mqaOriginalSampleRate: Int = 0,
    val albumArtist: String = "",
    val genre: String = "",
    val trackNumber: String = "",
    val composer: String = "",
    val sampleRateHz: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 2,
    val fileSizeBytes: Long = 0L,
    val explicitCodec: String? = null,
    val explicitBitrateKbps: Int = 0
) {
    val codec: String
        get() = explicitCodec ?: when (file.extension.lowercase()) {
            "flac" -> "FLAC"
            "wav", "wave" -> "WAV"
            else -> file.extension.uppercase().ifEmpty { "AUDIO" }
        }

    val bitrateKbps: Int
        get() {
            if (explicitBitrateKbps > 0) return explicitBitrateKbps
            val size = if (fileSizeBytes > 0L) fileSizeBytes else try { file.length() } catch (_: Exception) { 0L }
            return if (durationSeconds > 0 && size > 0) {
                ((size * 8.0) / (durationSeconds * 1000.0)).toInt()
            } else {
                0
            }
        }
}
