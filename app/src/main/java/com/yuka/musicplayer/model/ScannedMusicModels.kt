package com.yuka.musicplayer.model

import androidx.compose.runtime.Immutable
import com.yuka.musicplayer.util.TrackTitleParser
import java.io.File
import java.util.Locale

@Immutable
data class ScannedMusicItem(
    val file: File,
    val isSupported: Boolean,
    val formatLabel: String,
    val breadcrumb: String = "",
    val title: String = file.nameWithoutExtension.ifEmpty { file.name },
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val trackNumber: Int = 0,
    val durationMs: Long = 0L,
    val year: Int = 0,
    val composer: String = "",
    val fileSize: Long = 0L,
    val sampleRateHz: Int = 0,
    val bitDepth: Int = 0,
    val bitrateKbps: Int = 0,
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val displayTitle: String = "",
    val displayArtist: String = "",
    val secondaryMetadata: String = ""
) {
    companion object {
        fun formatSizeShort(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val mb = bytes / (1024.0 * 1024.0)
            return if (mb >= 1.0) {
                String.format(Locale.US, "%.1f MB", mb)
            } else {
                String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            }
        }

        fun createWithPrecomputedDisplay(
            file: File,
            isSupported: Boolean,
            formatLabel: String,
            breadcrumb: String,
            title: String,
            artist: String,
            album: String,
            albumArtist: String,
            genre: String,
            trackNumber: Int,
            durationMs: Long,
            year: Int,
            composer: String,
            fileSize: Long,
            sampleRateHz: Int = 0,
            bitDepth: Int = 0,
            bitrateKbps: Int = 0,
            dateAdded: Long,
            dateModified: Long
        ): ScannedMusicItem {
            val rawName = file.nameWithoutExtension.ifEmpty { file.name }
            val (parsedTitle, parsedArtist) = if (artist.isNotBlank()) {
                Pair(title.ifBlank { rawName }, artist)
            } else {
                TrackTitleParser.parse(rawName)
            }
            val finalTitle = if (title.isNotBlank() && title != rawName) title else parsedTitle
            val finalArtist = artist.ifBlank { parsedArtist }
            val sizeLabel = formatSizeShort(fileSize)
            val secondary = buildString {
                if (finalArtist.isNotEmpty()) {
                    append(finalArtist)
                    append(" • ")
                }
                if (breadcrumb.isNotEmpty()) {
                    append(breadcrumb)
                    append(" • ")
                }
                append(formatLabel)
                if (fileSize > 0L) {
                    append(" • ")
                    append(sizeLabel)
                }
            }
            return ScannedMusicItem(
                file = file,
                isSupported = isSupported,
                formatLabel = formatLabel,
                breadcrumb = breadcrumb,
                title = finalTitle,
                artist = finalArtist,
                album = album,
                albumArtist = albumArtist,
                genre = genre,
                trackNumber = trackNumber,
                durationMs = durationMs,
                year = year,
                composer = composer,
                fileSize = fileSize,
                sampleRateHz = sampleRateHz,
                bitDepth = bitDepth,
                bitrateKbps = bitrateKbps,
                dateAdded = dateAdded,
                dateModified = dateModified,
                displayTitle = finalTitle,
                displayArtist = finalArtist,
                secondaryMetadata = secondary
            )
        }
    }
}
