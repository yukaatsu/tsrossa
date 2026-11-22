package com.yuka.musicplayer.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.Playlist
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
import com.yuka.musicplayer.ui.theme.resolveM3TonalSurface
import com.yuka.musicplayer.util.TrackAlbumThumbnail

@Composable
internal fun PlaylistOverviewSection(
    filteredPlaylists: List<Playlist>,
    playlistSearchQuery: String,
    onPlaylistSearchQueryChange: (String) -> Unit,
    activePlayingPlaylistId: String?,
    currentTrack: TrackInfo?,
    onCreatePlaylistClick: () -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onTogglePinPlaylist: (Playlist) -> Unit,
    onRenamePlaylistClick: (Playlist) -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val accent = LocalAccentColor.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Search Bar with "+ New" button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(m3SubtleFill(alpha = 0.08f))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphSearch(color = accent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (playlistSearchQuery.isEmpty()) {
                    Text(
                        text = "Search playlists...",
                        color = m3OnSurfaceVariant().copy(alpha = 0.65f),
                        fontSize = 14.sp,
                        fontFamily = ModernSansFont
                    )
                }
                BasicTextField(
                    value = playlistSearchQuery,
                    onValueChange = onPlaylistSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = m3OnSurface(),
                        fontSize = 14.sp,
                        fontFamily = ModernSansFont
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(accent)
                )
            }

            if (playlistSearchQuery.isNotEmpty()) {
                TactileBox(
                    onClick = { onPlaylistSearchQueryChange("") },
                    pressedScale = 0.85f,
                    modifier = Modifier.size(30.dp).clip(CircleShape)
                ) {
                    GlyphClose(color = m3OnSurfaceVariant(), modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // "+ New" Playlist Button
            TactileBox(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCreatePlaylistClick()
                },
                pressedScale = 0.92f,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .glassMorphic(
                        backgroundColor = accent.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphAdd(color = accent, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "New",
                        color = accent,
                        fontSize = 12.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Playlist Count
        Text(
            text = "${filteredPlaylists.size} Playlists",
            color = m3OnSurfaceVariant(),
            fontSize = 12.sp,
            fontFamily = ModernSansFont,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Cards List
        if (filteredPlaylists.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlyphPlaylists(color = m3OnSurfaceVariant().copy(alpha = 0.40f), modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (playlistSearchQuery.isNotEmpty()) "No matching playlists" else "No playlists created yet",
                        color = m3OnSurfaceVariant(),
                        fontSize = 15.sp,
                        fontFamily = ModernSansFont
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 168.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = filteredPlaylists,
                    key = { it.id },
                    contentType = { "playlist_card_row" }
                ) { playlist ->
                    val isPlayingThis = activePlayingPlaylistId == playlist.id
                    val firstTrackPath = playlist.trackPaths.firstOrNull() ?: ""

                    val baseCardSurface = m3SurfaceContainerHigh().copy(alpha = 0.85f)
                    val playlistCardBgColor = if (isPlayingThis) {
                        resolveM3TonalSurface(
                            baseSurface = baseCardSurface,
                            accent = accent,
                            tintRatio = 0.28f,
                            alpha = 0.85f
                        )
                    } else {
                        baseCardSurface
                    }

                    // Glassmorphic Playlist Card (0.85 opacity)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassMorphic(
                                backgroundColor = playlistCardBgColor,
                                borderColor = if (isPlayingThis) accent.copy(alpha = 0.35f) else m3GlassBorder(),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectPlaylist(playlist.id)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thumbnail / Artwork
                        TrackAlbumThumbnail(
                            filePath = firstTrackPath,
                            isCurrentTrack = isPlayingThis,
                            currentTrackArt = if (isPlayingThis) currentTrack?.coverArt else null,
                            modifier = Modifier.size(52.dp),
                            cornerRadius = 14.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (isPlayingThis) accent.copy(alpha = 0.25f) else m3SubtleFill(alpha = 0.08f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                GlyphPlaylists(color = accent, modifier = Modifier.size(24.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Playlist Info Column
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = playlist.name,
                                    color = if (isPlayingThis) accent else m3OnSurface(),
                                    fontSize = 15.sp,
                                    fontFamily = ModernSansFont,
                                    fontWeight = FontWeight.SemiBold,
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
                                if (playlist.isPinned) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    GlyphPin(
                                        color = accent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                if (isPlayingThis) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(accent.copy(alpha = 0.25f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "PLAYING",
                                            color = accent,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = ModernSansFont
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "${playlist.trackPaths.size} tracks",
                                color = m3OnSurfaceVariant(),
                                fontSize = 12.sp,
                                fontFamily = ModernSansFont
                            )
                        }

                        // Quick Actions: Pin, Rename & Delete
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TactileBox(
                                onClick = { onTogglePinPlaylist(playlist) },
                                pressedScale = 0.85f,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (playlist.isPinned) accent.copy(alpha = 0.18f) else Color.Transparent)
                            ) {
                                GlyphPin(
                                    color = if (playlist.isPinned) accent else m3OnSurfaceVariant(),
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            if (!playlist.isFolderPlaylist) {
                                TactileBox(
                                    onClick = { onRenamePlaylistClick(playlist) },
                                    pressedScale = 0.88f,
                                    modifier = Modifier.size(32.dp).clip(CircleShape)
                                ) {
                                    GlyphClipboard(color = m3OnSurfaceVariant(), modifier = Modifier.size(16.dp))
                                }

                                TactileBox(
                                    onClick = { onDeletePlaylistClick(playlist) },
                                    pressedScale = 0.88f,
                                    modifier = Modifier.size(32.dp).clip(CircleShape)
                                ) {
                                    GlyphDelete(color = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
