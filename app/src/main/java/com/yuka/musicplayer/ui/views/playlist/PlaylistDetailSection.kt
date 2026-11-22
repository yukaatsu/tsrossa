package com.yuka.musicplayer.ui.views

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.Playlist
import com.yuka.musicplayer.model.RepeatMode
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.ui.components.*
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.glassMorphic
import com.yuka.musicplayer.ui.theme.m3GlassBorder
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SubtleFill
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.util.FuzzySearchHelper
import com.yuka.musicplayer.util.TrackAlbumThumbnail
import com.yuka.musicplayer.util.TrackTitleParser
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlaylistDetailSection(
    playlist: Playlist,
    isCurrentPlaying: Boolean,
    currentTrack: TrackInfo?,
    repeatMode: RepeatMode,
    priorityQueueSize: Int,
    trackSearchQuery: String,
    onTrackSearchQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onClearPlaylist: (String) -> Unit,
    onRenamePlaylistClick: (Playlist) -> Unit,
    onPlayPlaylistSequential: (Playlist) -> Unit,
    onPlayPlaylistShuffled: (Playlist) -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onFileSelected: (File, String) -> Unit,
    onFileRemoved: (String, String) -> Unit,
    onTrackLongPressed: (File) -> Unit,
    scannedItemsMap: Map<String, com.yuka.musicplayer.model.ScannedMusicItem> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val accent = LocalAccentColor.current
    val currentTracks = playlist.trackPaths

    val filteredTracks = remember(currentTracks, trackSearchQuery, scannedItemsMap) {
        if (trackSearchQuery.isEmpty()) {
            currentTracks
        } else {
            currentTracks.filter { path ->
                val cached = scannedItemsMap[path]
                if (cached != null) {
                    FuzzySearchHelper.matchesAny(trackSearchQuery, cached.displayTitle, cached.displayArtist, cached.album, path)
                } else {
                    val name = path.substringAfterLast('/').substringBeforeLast('.')
                    FuzzySearchHelper.matchesAny(trackSearchQuery, name, path)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Detail Header with Back Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TactileBox(
                onClick = onBackClick,
                pressedScale = 0.90f,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .glassMorphic(
                        backgroundColor = m3SubtleFill(alpha = 0.08f),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphArrowBack(color = accent, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Playlists",
                        color = m3OnSurface(),
                        fontSize = 12.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = playlist.name,
                        color = m3OnSurface(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModernSansFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (playlist.isFolderPlaylist) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accent.copy(alpha = 0.18f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlyphFolder(color = accent, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "FOLDER",
                                    color = accent,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = ModernSansFont
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "${currentTracks.size} tracks",
                    color = m3OnSurfaceVariant(),
                    fontSize = 11.5.sp,
                    fontFamily = ModernSansFont
                )
            }

            // Rename & Clear Quick Options
            if (!playlist.isFolderPlaylist) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (currentTracks.isNotEmpty()) {
                        TactileBox(
                            onClick = { onClearPlaylist(playlist.id) },
                            pressedScale = 0.88f,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(accent.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlyphDelete(color = accent, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("CLEAR", color = accent, fontSize = 9.sp, fontFamily = ModernSansFont, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    TactileBox(
                        onClick = { onRenamePlaylistClick(playlist) },
                        pressedScale = 0.88f,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphEdit(color = accent, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("RENAME", color = accent, fontSize = 9.sp, fontFamily = ModernSansFont, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2 HERO BUTTONS: PLAY (SEQUENTIAL) & SHUFFLE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Play Button (Sequential from start)
            TactileBox(
                onClick = {
                    if (currentTracks.isNotEmpty()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlayPlaylistSequential(playlist)
                    }
                },
                pressedScale = 0.94f,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .glassMorphic(
                        backgroundColor = accent.copy(alpha = 0.22f),
                        borderColor = accent.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphPlay(color = accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Play",
                        color = accent,
                        fontSize = 14.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 2. Shuffle Button (Random)
            TactileBox(
                onClick = {
                    if (currentTracks.isNotEmpty()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlayPlaylistShuffled(playlist)
                    }
                },
                pressedScale = 0.94f,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .glassMorphic(
                        backgroundColor = m3SurfaceContainerHigh(),
                        borderColor = m3GlassBorder(),
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphShuffle(color = m3OnSurface(), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Shuffle",
                        color = m3OnSurface(),
                        fontSize = 14.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Transport Quick Strip (Repeat, Queue)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isRepActive = repeatMode != RepeatMode.OFF
            TactileBox(
                onClick = onCycleRepeat,
                pressedScale = 0.92f,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .glassMorphic(
                        backgroundColor = if (isRepActive) accent.copy(alpha = 0.22f) else m3SubtleFill(alpha = 0.08f),
                        shape = RoundedCornerShape(50)
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (repeatMode == RepeatMode.ONE) {
                        GlyphRepeatOne(color = if (isRepActive) accent else m3OnSurfaceVariant(), modifier = Modifier.size(14.dp))
                    } else {
                        GlyphRepeat(color = if (isRepActive) accent else m3OnSurfaceVariant(), modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    val repText = when (repeatMode) {
                        RepeatMode.OFF -> "Repeat"
                        RepeatMode.ALL -> "Repeat All"
                        RepeatMode.ONE -> "Repeat One"
                    }
                    Text(
                        text = repText,
                        color = if (isRepActive) accent else m3OnSurfaceVariant(),
                        fontSize = 11.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            TactileBox(
                onClick = onOpenQueue,
                pressedScale = 0.92f,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .glassMorphic(
                        backgroundColor = if (priorityQueueSize > 0) accent.copy(alpha = 0.22f) else m3SubtleFill(alpha = 0.08f),
                        shape = RoundedCornerShape(50)
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphQueue(color = if (priorityQueueSize > 0) accent else m3OnSurfaceVariant(), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Queue ($priorityQueueSize)",
                        color = if (priorityQueueSize > 0) accent else m3OnSurfaceVariant(),
                        fontSize = 11.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar inside Playlist
        if (currentTracks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(m3SubtleFill(alpha = 0.06f))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlyphSearch(color = accent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(10.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (trackSearchQuery.isEmpty()) {
                        Text(
                            text = "Search tracks in playlist...",
                            color = m3OnSurfaceVariant().copy(alpha = 0.60f),
                            fontSize = 13.sp,
                            fontFamily = ModernSansFont
                        )
                    }
                    BasicTextField(
                        value = trackSearchQuery,
                        onValueChange = onTrackSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            color = m3OnSurface(),
                            fontSize = 13.sp,
                            fontFamily = ModernSansFont
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(accent)
                    )
                }

                if (trackSearchQuery.isNotEmpty()) {
                    TactileBox(
                        onClick = { onTrackSearchQueryChange("") },
                        pressedScale = 0.85f,
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                    ) {
                        GlyphClose(color = m3OnSurfaceVariant(), modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Track list inside playlist
        if (currentTracks.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlyphMusicNote(color = m3OnSurfaceVariant().copy(alpha = 0.40f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "This playlist is empty",
                        color = m3OnSurface(),
                        fontSize = 14.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select tracks in Library then tap 'Add to playlist'",
                        color = m3OnSurfaceVariant(),
                        fontSize = 12.sp,
                        fontFamily = ModernSansFont
                    )
                }
            }
        } else if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matching tracks",
                    color = m3OnSurfaceVariant(),
                    fontSize = 13.sp,
                    fontFamily = ModernSansFont
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 168.dp)
            ) {
                items(
                    items = filteredTracks,
                    key = { it },
                    contentType = { "playlist_track_row" }
                ) { path ->
                    val cachedItem = scannedItemsMap[path]
                    val isPlaying = path == currentTrack?.file?.absolutePath && isCurrentPlaying
                    val parsedTitle = cachedItem?.displayTitle?.ifEmpty { cachedItem.title }
                        ?: remember(path) {
                            val name = path.substringAfterLast('/').substringBeforeLast('.')
                            TrackTitleParser.parse(name).first
                        }
                    val secondary = cachedItem?.secondaryMetadata?.ifEmpty { cachedItem.formatLabel }
                        ?: remember(path) {
                            val fileName = path.substringAfterLast('/')
                            val name = fileName.substringBeforeLast('.')
                            val ext = fileName.substringAfterLast('.', "").uppercase()
                            val artist = TrackTitleParser.parse(name).second
                            if (artist.isNotEmpty()) "$artist • $ext" else ext
                        }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isPlaying) accent.copy(alpha = 0.12f) else Color.Transparent)
                            .combinedClickable(
                                onClick = { onFileSelected(cachedItem?.file ?: File(path), playlist.id) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTrackLongPressed(cachedItem?.file ?: File(path))
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackAlbumThumbnail(
                            filePath = path,
                            isCurrentTrack = isPlaying,
                            currentTrackArt = if (isPlaying) currentTrack?.coverArt else null,
                            modifier = Modifier.size(46.dp),
                            cornerRadius = 12.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (isPlaying) accent.copy(alpha = 0.22f) else m3SubtleFill(alpha = 0.08f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isPlaying) {
                                    GlyphNowPlaying(color = accent, modifier = Modifier.size(20.dp))
                                } else {
                                    GlyphMusicNote(color = m3OnSurfaceVariant(), modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = parsedTitle,
                                color = if (isPlaying) accent else m3OnSurface(),
                                fontFamily = ModernSansFont,
                                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = secondary,
                                color = if (isPlaying) accent.copy(alpha = 0.80f) else m3OnSurfaceVariant(),
                                fontSize = 12.sp,
                                fontFamily = ModernSansFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!playlist.isFolderPlaylist) {
                            // Quick remove from playlist button
                            TactileBox(
                                onClick = { onFileRemoved(path, playlist.id) },
                                pressedScale = 0.85f,
                                modifier = Modifier.size(32.dp).clip(CircleShape)
                            ) {
                                GlyphClose(color = m3OnSurfaceVariant(), modifier = Modifier.size(14.dp))
                            }
                        }

                        // 3-dot options
                        TactileBox(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTrackLongPressed(cachedItem?.file ?: File(path))
                            },
                            pressedScale = 0.85f,
                            modifier = Modifier.size(36.dp).clip(CircleShape)
                        ) {
                            GlyphMoreVert(color = m3OnSurfaceVariant(), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
