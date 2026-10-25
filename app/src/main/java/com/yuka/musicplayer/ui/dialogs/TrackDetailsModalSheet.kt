package com.yuka.musicplayer.ui.dialogs

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.CyberBottomSheet
import com.yuka.musicplayer.ui.components.GlyphMusicNote
import com.yuka.musicplayer.ui.components.ModalCloseButton
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.util.TrackAlbumThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class DetailedTrackMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val year: String,
    val trackNumber: String,
    val composer: String,
    val durationFormatted: String,
    val format: String,
    val sampleRate: String,
    val bitrate: String,
    val fileSizeFormatted: String,
    val filePath: String
)

@Composable
fun TrackDetailsModalSheet(
    target: File?,
    onDismissRequest: () -> Unit
) {
    var rememberedTarget by remember { mutableStateOf<File?>(null) }
    if (target != null) {
        rememberedTarget = target
    }
    val activeTarget = rememberedTarget ?: return

    val accent = LocalAccentColor.current
    var metadata by remember(activeTarget) {
        mutableStateOf(
            DetailedTrackMetadata(
                title = activeTarget.nameWithoutExtension.ifEmpty { activeTarget.name },
                artist = "Loading...",
                album = "Loading...",
                albumArtist = "",
                genre = "",
                year = "",
                trackNumber = "",
                composer = "",
                durationFormatted = "--:--",
                format = activeTarget.extension.uppercase().ifEmpty { "AUDIO" },
                sampleRate = "--",
                bitrate = "--",
                fileSizeFormatted = formatFileSize(activeTarget.length()),
                filePath = activeTarget.absolutePath
            )
        )
    }

    LaunchedEffect(activeTarget) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(activeTarget.absolutePath)
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: activeTarget.nameWithoutExtension.ifEmpty { activeTarget.name }
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
                val albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: ""
                val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
                val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: ""
                val trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER) ?: ""
                val composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER) ?: ""
                
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                val bitrateStr = if (bitrateBps != null && bitrateBps > 0) "${bitrateBps / 1000} kbps" else "--"

                val sampleRateMeta = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.let { "$it Hz" }
                    } else null
                } catch (_: Exception) { null } ?: "--"

                metadata = DetailedTrackMetadata(
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtist = albumArtist,
                    genre = genre,
                    year = year,
                    trackNumber = trackNumber,
                    composer = composer,
                    durationFormatted = formatDuration(durationMs),
                    format = activeTarget.extension.uppercase().ifEmpty { "AUDIO" },
                    sampleRate = sampleRateMeta,
                    bitrate = bitrateStr,
                    fileSizeFormatted = formatFileSize(activeTarget.length()),
                    filePath = activeTarget.absolutePath
                )
            } catch (_: Exception) {
                // Fallback to basic details
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        }
    }

    CyberBottomSheet(
        visible = target != null,
        onDismissRequest = onDismissRequest,
        wrapHeight = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackAlbumThumbnail(
                    filePath = activeTarget.absolutePath,
                    isCurrentTrack = false,
                    currentTrackArt = null,
                    modifier = Modifier.size(54.dp),
                    cornerRadius = 14.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(com.yuka.musicplayer.ui.theme.m3SubtleFill(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphMusicNote(color = accent, modifier = Modifier.size(24.dp))
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Track Details",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModernSansFont,
                        letterSpacing = 0.06.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = metadata.title,
                        color = m3OnSurface(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = ModernSansFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${metadata.artist} • ${metadata.album}",
                        color = m3OnSurfaceVariant(),
                        fontSize = 12.sp,
                        fontFamily = ModernSansFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = com.yuka.musicplayer.ui.theme.m3SubtleFill(alpha = 0.08f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Metadata items
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetadataDetailRow("Title", metadata.title)
                MetadataDetailRow("Artist", metadata.artist)
                MetadataDetailRow("Album", metadata.album)
                if (metadata.albumArtist.isNotEmpty()) {
                    MetadataDetailRow("Album Artist", metadata.albumArtist)
                }
                if (metadata.genre.isNotEmpty()) {
                    MetadataDetailRow("Genre", metadata.genre)
                }
                if (metadata.year.isNotEmpty()) {
                    MetadataDetailRow("Year", metadata.year)
                }
                if (metadata.trackNumber.isNotEmpty()) {
                    MetadataDetailRow("Track No.", metadata.trackNumber)
                }
                if (metadata.composer.isNotEmpty()) {
                    MetadataDetailRow("Composer", metadata.composer)
                }
                MetadataDetailRow("Duration", metadata.durationFormatted)
                MetadataDetailRow("Format / Codec", metadata.format)
                if (metadata.sampleRate != "--") {
                    MetadataDetailRow("Sample Rate", metadata.sampleRate)
                }
                if (metadata.bitrate != "--") {
                    MetadataDetailRow("Bitrate", metadata.bitrate)
                }
                MetadataDetailRow("File Size", metadata.fileSizeFormatted)
                MetadataDetailRow("File Path", metadata.filePath, isMono = true)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MetadataDetailRow(
    label: String,
    value: String,
    isMono: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = m3OnSurfaceVariant(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = ModernSansFont
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value.ifEmpty { "-" },
            color = m3OnSurface(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = if (isMono) TerminalFont else ModernSansFont,
            lineHeight = 18.sp
        )
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.2f MB", mb)
    } else {
        String.format(Locale.US, "%.1f KB", kb)
    }
}
