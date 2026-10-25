package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.Playlist
import com.yuka.musicplayer.ui.components.CyberBottomSheet
import com.yuka.musicplayer.ui.components.GlyphAdd
import com.yuka.musicplayer.ui.components.GlyphClose
import com.yuka.musicplayer.ui.components.ModalCloseButton
import com.yuka.musicplayer.ui.components.GlyphPlaylists
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SurfaceContainer
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.TerminalWhite
import java.io.File

@Composable
fun SelectPlaylistDialog(
    visible: Boolean,
    targetTrack: File? = null,
    targetTracks: List<File> = emptyList(),
    playlists: List<Playlist>,
    onDismissRequest: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateNewPlaylist: () -> Unit
) {
    val effectiveTracks = if (targetTracks.isNotEmpty()) targetTracks else if (targetTrack != null) listOf(targetTrack) else emptyList()

    CyberBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        maxHeightFraction = 0.60f,
        maxWidth = 420.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add to playlist",
                        color = m3OnSurface(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModernSansFont
                    )
                    if (effectiveTracks.size > 1) {
                        Text(
                            text = "${effectiveTracks.size} tracks selected",
                            color = LocalAccentColor.current,
                            fontSize = 12.sp,
                            fontFamily = ModernSansFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (effectiveTracks.size == 1) {
                        val singleTrack = effectiveTracks.first()
                        Text(
                            text = singleTrack.nameWithoutExtension.ifEmpty { singleTrack.name },
                            color = m3OnSurfaceVariant(),
                            fontSize = 12.sp,
                            fontFamily = ModernSansFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                ModalCloseButton(onClick = onDismissRequest)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Create New Playlist Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(m3SurfaceContainerHigh())
                    .clickable {
                        onDismissRequest()
                        onCreateNewPlaylist()
                    }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(LocalAccentColor.current.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphAdd(color = LocalAccentColor.current, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "New playlist",
                    color = LocalAccentColor.current,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ModernSansFont
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No custom playlists yet",
                        color = m3OnSurfaceVariant().copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        fontFamily = ModernSansFont
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(playlists) { playlist ->
                        val containsTrack = targetTrack != null && playlist.trackPaths.contains(targetTrack.absolutePath)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (containsTrack) LocalAccentColor.current.copy(alpha = 0.12f) else m3SurfaceContainer())
                                .clickable {
                                    onSelectPlaylist(playlist)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(m3SurfaceContainerHigh()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    GlyphPlaylists(
                                        color = if (containsTrack) LocalAccentColor.current else TerminalGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = playlist.name,
                                        color = if (containsTrack) LocalAccentColor.current else m3OnSurface(),
                                        fontSize = 14.sp,
                                        fontFamily = ModernSansFont,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${playlist.trackPaths.size} tracks",
                                        color = m3OnSurfaceVariant(),
                                        fontSize = 11.sp,
                                        fontFamily = ModernSansFont
                                    )
                                }
                            }
                            if (containsTrack) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(LocalAccentColor.current.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Added",
                                        color = LocalAccentColor.current,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = ModernSansFont
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
