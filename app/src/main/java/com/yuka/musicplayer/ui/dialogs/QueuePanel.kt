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
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                GlyphQueue(color = accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Play Queue & Up Next (${priorityQueue.size})",
                    color = m3OnSurface(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ModernSansFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (priorityQueue.isNotEmpty()) {
                    TactileBox(
                        onClick = onClearQueue,
                        pressedScale = 0.88f,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFF5252).copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Clear",
                            color = Color(0xFFFF5252),
                            fontSize = 11.sp,
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                ModalCloseButton(onClick = onClose)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Fuzzy Search Bar (Item 9.2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(m3SurfaceContainer())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphSearch(color = m3OnSurfaceVariant(), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = queueSearchQuery,
                onValueChange = { queueSearchQuery = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = m3OnSurface(),
                    fontSize = 13.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(accent),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (queueSearchQuery.isEmpty()) {
                            Text(
                                text = "Search in queue...",
                                color = m3OnSurfaceVariant().copy(alpha = 0.65f),
                                fontSize = 13.5.sp,
                                fontFamily = ModernSansFont
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.weight(1f)
            )
            if (queueSearchQuery.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                TactileBox(
                    onClick = { queueSearchQuery = "" },
                    pressedScale = 0.85f,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(m3SurfaceContainerHigh())
                ) {
                    GlyphClose(
                        color = m3OnSurfaceVariant(),
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.Center)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Now Playing Section (only if not filtering or matching current track)
            val showCurrentTrack = currentTrack != null && (
                queueSearchQuery.isBlank() || FuzzySearchHelper.matchesAny(
                    queueSearchQuery,
                    currentTrack.title,
                    currentTrack.artist,
                    currentTrack.album,
                    currentTrack.file.name
                )
            )
            if (showCurrentTrack && currentTrack != null) {
                item {
                    Text(
                        text = "Now Playing",
                        color = accent,
                        fontFamily = ModernSansFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(m3SurfaceContainer())
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentTrack.coverArt != null) {
                            Image(
                                bitmap = currentTrack.coverArt.asImageBitmap(),
                                contentDescription = "Artwork",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accent.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                GlyphNowPlaying(color = accent, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTrack.title,
                                color = m3OnSurface(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = ModernSansFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentTrack.artist,
                                color = accent,
                                fontSize = 12.sp,
                                fontFamily = ModernSansFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            // Priority Queue Section
            val pqCountLabel = if (queueSearchQuery.isNotBlank() && filteredPriorityQueue.size != priorityQueue.size) {
                "Priority Queue (${filteredPriorityQueue.size}/${priorityQueue.size})"
            } else {
                "Priority Queue (${priorityQueue.size})"
            }
            item {
                Text(
                    text = pqCountLabel,
                    color = m3OnSurface(),
                    fontFamily = ModernSansFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (priorityQueue.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(m3SurfaceContainer())
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Priority queue is empty.\nLong-press any track to 'Play Next' or 'Add to Queue'.",
                            color = m3OnSurfaceVariant().copy(alpha = 0.70f),
                            fontFamily = ModernSansFont,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            } else if (filteredPriorityQueue.isEmpty()) {
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(m3SurfaceContainer())
                            .clickable { onSelectTrack(file) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackAlbumThumbnail(
                            filePath = file.absolutePath,
                            isCurrentTrack = false,
                            currentTrackArt = null,
                            modifier = Modifier.size(44.dp),
                            cornerRadius = 10.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                GlyphMusicNote(color = accent, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = m3OnSurface(),
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = artist,
                                color = m3OnSurfaceVariant(),
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TactileBox(
                            onClick = { onRemoveFromQueue(idx) },
                            pressedScale = 0.85f,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        ) {
                            GlyphClose(
                                color = Color(0xFFFF5252).copy(alpha = 0.85f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // Upcoming in Playlist / Folder (Items 9.1, 9.3)
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
                    // Item 9.3: Show ALL tracks (no .take(12) limit!)
                    items(
                        items = filteredUpcomingTracks,
                        key = { (idx, file) -> "upcoming_${idx}_${file.absolutePath}" }
                    ) { (idx, file) ->
                        val (title, artist) = getTrackMetadata(file)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.5.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(m3SurfaceContainer().copy(alpha = 0.65f))
                                .clickable { onSelectTrack(file) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackAlbumThumbnail(
                                filePath = file.absolutePath,
                                isCurrentTrack = false,
                                currentTrackArt = null,
                                modifier = Modifier.size(40.dp),
                                cornerRadius = 8.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(m3SurfaceContainerHigh()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    GlyphMusicNote(
                                        color = m3OnSurfaceVariant().copy(alpha = 0.60f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    color = m3OnSurface(),
                                    fontFamily = ModernSansFont,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = artist,
                                    color = m3OnSurfaceVariant(),
                                    fontFamily = ModernSansFont,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
