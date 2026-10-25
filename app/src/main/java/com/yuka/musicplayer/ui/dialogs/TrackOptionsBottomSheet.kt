package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.PlaybackSource
import com.yuka.musicplayer.model.ViewState
import com.yuka.musicplayer.ui.components.CyberBottomSheet
import com.yuka.musicplayer.ui.components.GlyphCheck
import com.yuka.musicplayer.ui.components.GlyphChevronRight
import com.yuka.musicplayer.ui.components.GlyphDelete
import com.yuka.musicplayer.ui.components.GlyphInfo
import com.yuka.musicplayer.ui.components.GlyphMusicNote
import com.yuka.musicplayer.ui.components.GlyphNext
import com.yuka.musicplayer.ui.components.GlyphPlay
import com.yuka.musicplayer.ui.components.GlyphPlaylists
import com.yuka.musicplayer.ui.components.GlyphQueue
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerLow
import com.yuka.musicplayer.util.TrackAlbumThumbnail
import com.yuka.musicplayer.util.TrackTitleParser
import java.io.File

/**
 * Bottom Sheet menu for individual audio track actions.
 * Styled identically to SettingsView category cards (SettingsMenuCard) with full verticalScroll
 * nested-scroll gesture support and persistent target caching during exit animations.
 */
@Composable
fun TrackOptionsBottomSheet(
    target: File?,
    onDismissRequest: () -> Unit,
    playlistSet: Set<String>,
    viewState: ViewState,
    onPlayTrack: (File, PlaybackSource) -> Unit,
    onPlayNextInQueue: (File) -> Unit,
    onAddToQueue: (File) -> Unit,
    onOpenAddToPlaylist: (File) -> Unit,
    onShowDetails: (File) -> Unit = {},
    onDeleteTrack: (File) -> Unit = {},
    onSelectMultiple: ((File) -> Unit)? = null
) {
    // Remember the last non-null target so the sheet content stays rendered during the fly-out animation
    var rememberedTarget by remember { mutableStateOf<File?>(null) }
    if (target != null) {
        rememberedTarget = target
    }
    val activeTarget = target ?: rememberedTarget
    val inPlaylist = if (activeTarget != null) playlistSet.contains(activeTarget.absolutePath) else false

    CyberBottomSheet(
        visible = target != null,
        onDismissRequest = onDismissRequest,
        maxHeightFraction = 0.82f,
        maxWidth = 440.dp,
        wrapHeight = true
    ) {
        if (activeTarget != null) {
            val accent = LocalAccentColor.current
            val displayName = activeTarget.nameWithoutExtension.ifEmpty { activeTarget.name }
            val (parsedTitle, parsedArtist) = remember(displayName) {
                TrackTitleParser.parse(displayName)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Track Header Card (Settings-style surface container)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(m3SurfaceContainerLow())
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackAlbumThumbnail(
                        filePath = activeTarget.absolutePath,
                        isCurrentTrack = false,
                        currentTrackArt = null,
                        modifier = Modifier.size(48.dp),
                        cornerRadius = 14.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(m3SurfaceContainerHigh()),
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphMusicNote(
                                color = accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = parsedTitle,
                            color = m3OnSurface(),
                            fontSize = 15.sp,
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (parsedArtist.isNotEmpty()) {
                                "$parsedArtist • ${activeTarget.extension.uppercase()}"
                            } else {
                                activeTarget.extension.uppercase()
                            },
                            color = accent,
                            fontSize = 12.sp,
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 1. Play Now
                TrackSettingStyleOptionCard(
                    title = "Play Now",
                    subtitle = "Start playing this track immediately",
                    icon = { GlyphPlay(color = accent, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        val targetSource = when (viewState) {
                            ViewState.PLAYLIST -> PlaybackSource.PLAYLIST
                            ViewState.LIBRARY -> PlaybackSource.LIBRARY
                            else -> if (inPlaylist) PlaybackSource.PLAYLIST else PlaybackSource.LIBRARY
                        }
                        onDismissRequest()
                        onPlayTrack(activeTarget, targetSource)
                    }
                )

                // 2. Play Next
                TrackSettingStyleOptionCard(
                    title = "Play Next",
                    subtitle = "Insert at the top of your active playback queue",
                    icon = { GlyphNext(color = accent, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        onDismissRequest()
                        onPlayNextInQueue(activeTarget)
                    }
                )

                // 3. Add to Queue
                TrackSettingStyleOptionCard(
                    title = "Add to Queue",
                    subtitle = "Append to the end of your priority queue",
                    icon = { GlyphQueue(color = accent, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        onDismissRequest()
                        onAddToQueue(activeTarget)
                    }
                )

                // 4. Add to / Manage in Playlist
                TrackSettingStyleOptionCard(
                    title = if (inPlaylist) "Manage in Playlist" else "Add to Playlist",
                    subtitle = "Save or organize this track inside your playlists",
                    icon = { GlyphPlaylists(color = accent, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        onDismissRequest()
                        onOpenAddToPlaylist(activeTarget)
                    }
                )

                // 5. Select Multiple Tracks (Library mode)
                if (viewState == ViewState.LIBRARY && onSelectMultiple != null) {
                    TrackSettingStyleOptionCard(
                        title = "Select Multiple Tracks",
                        subtitle = "Select several songs for batch playlist or file actions",
                        icon = { GlyphCheck(color = accent, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            onDismissRequest()
                            onSelectMultiple(activeTarget)
                        }
                    )
                }

                // 6. Track Details
                TrackSettingStyleOptionCard(
                    title = "Track Details",
                    subtitle = "View audio format, sample rate, bitrate, and file path",
                    icon = { GlyphInfo(color = accent, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        onDismissRequest()
                        onShowDetails(activeTarget)
                    }
                )

                // 7. Delete Track (Destructive)
                val dangerColor = Color(0xFFFF5252)
                TrackSettingStyleOptionCard(
                    title = "Delete Track",
                    subtitle = "Permanently remove this audio file from device storage",
                    accentOverride = dangerColor,
                    icon = { GlyphDelete(color = dangerColor, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        onDismissRequest()
                        onDeleteTrack(activeTarget)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TrackSettingStyleOptionCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    accentOverride: Color? = null,
    onClick: () -> Unit
) {
    val accent = accentOverride ?: LocalAccentColor.current
    val containerBg = m3SurfaceContainerLow()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(containerBg)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (accentOverride != null) accentOverride else m3OnSurface(),
                fontSize = 15.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = m3OnSurfaceVariant(),
                fontSize = 12.sp,
                fontFamily = ModernSansFont,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        GlyphChevronRight(
            color = if (accentOverride != null) accentOverride.copy(alpha = 0.7f) else m3OnSurfaceVariant(),
            modifier = Modifier.size(18.dp)
        )
    }
}
