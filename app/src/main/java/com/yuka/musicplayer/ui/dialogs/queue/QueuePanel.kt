package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.ScannedMusicItem
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.ui.components.CyberBottomSheet
import com.yuka.musicplayer.ui.components.GlyphClose
import com.yuka.musicplayer.ui.components.GlyphMusicNote
import com.yuka.musicplayer.ui.components.GlyphNowPlaying
import com.yuka.musicplayer.ui.components.GlyphQueue
import com.yuka.musicplayer.ui.components.GlyphSearch
import com.yuka.musicplayer.ui.components.ModalCloseButton
import com.yuka.musicplayer.ui.components.TactileBox
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SurfaceContainer
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.util.FuzzySearchHelper
import com.yuka.musicplayer.util.TrackAlbumThumbnail
import com.yuka.musicplayer.util.TrackTitleParser
import java.io.File

@Composable
fun QueuePanel(
    currentTrack: TrackInfo?,
    priorityQueue: List<File>,
    upcomingTracks: List<File>,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onSelectTrack: (File) -> Unit,
    onClose: () -> Unit,
    scannedItemsMap: Map<String, ScannedMusicItem> = emptyMap()
) {
    val accent = LocalAccentColor.current
    var queueSearchQuery by remember { mutableStateOf("") }

    fun getTrackMetadata(file: File): Pair<String, String> {
        val scanned = scannedItemsMap[file.absolutePath]
        if (scanned != null) {
            val t = scanned.title.trim().ifEmpty { file.nameWithoutExtension }
            val a = scanned.artist.trim().ifEmpty { "Unknown Artist" }
            return t to a
        }
        val (parsedTitle, parsedArtist) = TrackTitleParser.parse(file.nameWithoutExtension.ifEmpty { file.name })
        val finalTitle = parsedTitle.ifEmpty { file.nameWithoutExtension }
        val finalArtist = parsedArtist.ifEmpty { "Unknown Artist" }
        return finalTitle to finalArtist
    }

    val filteredPriorityQueue = remember(priorityQueue, queueSearchQuery, scannedItemsMap) {
        if (queueSearchQuery.isBlank()) {
            priorityQueue.mapIndexed { idx, file -> idx to file }
        } else {
            priorityQueue.mapIndexedNotNull { idx, file ->
                val (title, artist) = getTrackMetadata(file)
                if (FuzzySearchHelper.matchesAny(queueSearchQuery, title, artist, file.name, file.nameWithoutExtension)) {
                    idx to file
                } else {
                    null
                }
            }
        }
    }

    val filteredUpcomingTracks = remember(upcomingTracks, queueSearchQuery, scannedItemsMap) {
        if (queueSearchQuery.isBlank()) {
            upcomingTracks.mapIndexed { idx, file -> idx to file }
        } else {
            upcomingTracks.mapIndexedNotNull { idx, file ->
                val (title, artist) = getTrackMetadata(file)
                if (FuzzySearchHelper.matchesAny(queueSearchQuery, title, artist, file.name, file.nameWithoutExtension)) {
                    idx to file
                } else {
                    null
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlyphQueue(color = accent, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Play Queue",
                        color = m3OnSurface(),
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (priorityQueue.isNotEmpty()) {
                        TactileBox(
                            onClick = onClearQueue,
                            pressedScale = 0.92f,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFFF5252).copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Clear Queue",
                                color = Color(0xFFFF5252),
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    ModalCloseButton(onClick = onClose)
                }
            }

            // M3 Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(m3SurfaceContainerHigh())
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GlyphSearch(
                        color = m3OnSurfaceVariant().copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (queueSearchQuery.isEmpty()) {
                            Text(
                                text = "Search queue...",
                                color = m3OnSurfaceVariant().copy(alpha = 0.5f),
                                fontFamily = ModernSansFont,
                                fontSize = 13.sp
                            )
                        }
                        BasicTextField(
                            value = queueSearchQuery,
                            onValueChange = { queueSearchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = m3OnSurface(),
                                fontFamily = ModernSansFont,
                                fontSize = 13.sp
                            ),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (queueSearchQuery.isNotEmpty()) {
                        TactileBox(
                            onClick = { queueSearchQuery = "" },
                            pressedScale = 0.85f,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        ) {
                            GlyphClose(
                                color = m3OnSurfaceVariant(),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Now Playing Card
            if (currentTrack != null) {
                Text(
                    text = "Now Playing",
                    color = accent,
                    fontFamily = ModernSansFont,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val cover = currentTrack.coverArt
                    if (cover != null) {
                        Image(
                            bitmap = cover.asImageBitmap(),
                            contentDescription = "Now Playing Cover",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accent.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphMusicNote(color = accent, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack.title,
                            color = m3OnSurface(),
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentTrack.artist,
                            color = m3OnSurfaceVariant(),
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    GlyphNowPlaying(color = accent, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Priority Queue Section
                if (priorityQueue.isNotEmpty()) {
                    val pCountLabel = if (queueSearchQuery.isNotBlank() && filteredPriorityQueue.size != priorityQueue.size) {
                        "Up Next (Queue) (${filteredPriorityQueue.size}/${priorityQueue.size})"
                    } else {
                        "Up Next (Queue) (${priorityQueue.size})"
                    }
                    item {
                        Text(
                            text = pCountLabel,
                            color = accent,
                            fontFamily = ModernSansFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (filteredPriorityQueue.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(m3SurfaceContainer())
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matches in priority queue",
                                    color = m3OnSurfaceVariant().copy(alpha = 0.60f),
                                    fontFamily = ModernSansFont,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    } else {
                        items(
                            items = filteredPriorityQueue,
                            key = { (idx, file) -> "priority_${idx}_${file.absolutePath}" }
                        ) { (idx, file) ->
                            val (title, artist) = getTrackMetadata(file)
                            QueueTrackRow(
                                file = file,
                                title = title,
                                artist = artist,
                                accentColor = accent,
                                isPriority = true,
                                onSelectTrack = onSelectTrack,
                                onRemoveFromQueue = { onRemoveFromQueue(idx) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }

                // Upcoming in Playlist / Folder
                if (upcomingTracks.isNotEmpty()) {
                    val upCountLabel = if (queueSearchQuery.isNotBlank() && filteredUpcomingTracks.size != upcomingTracks.size) {
                        "Upcoming in Playlist / Folder (${filteredUpcomingTracks.size}/${upcomingTracks.size})"
                    } else {
                        "Upcoming in Playlist / Folder (${upcomingTracks.size})"
                    }
                    item {
                        Text(
                            text = upCountLabel,
                            color = m3OnSurface(),
                            fontFamily = ModernSansFont,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (filteredUpcomingTracks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(m3SurfaceContainer())
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matches in upcoming tracks",
                                    color = m3OnSurfaceVariant().copy(alpha = 0.60f),
                                    fontFamily = ModernSansFont,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        items(
                            items = filteredUpcomingTracks,
                            key = { (idx, file) -> "upcoming_${idx}_${file.absolutePath}" }
                        ) { (idx, file) ->
                            val (title, artist) = getTrackMetadata(file)
                            QueueTrackRow(
                                file = file,
                                title = title,
                                artist = artist,
                                accentColor = accent,
                                isPriority = false,
                                onSelectTrack = onSelectTrack,
                                onRemoveFromQueue = null
                            )
                        }
                    }
                }
            }
    }
}