package com.yuka.musicplayer.ui.views

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.ScannedMusicItem
import com.yuka.musicplayer.ui.components.*
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TrueBlack
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SubtleFill
import com.yuka.musicplayer.util.TrackAlbumThumbnail
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryTrackRow(
    item: ScannedMusicItem,
    isPlaying: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    inPlaylist: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val accent = LocalAccentColor.current
    val isSupported = item.isSupported
    val itemAlpha = if (isSupported) 1f else 0.42f
    val displayTitle = item.displayTitle.ifEmpty { item.title }
    val secondaryMetadata = item.secondaryMetadata.ifEmpty { item.formatLabel }

    val trackRowShape = remember { RoundedCornerShape(14.dp) }
    val onSurfaceColor = m3OnSurface()
    val onSurfaceVariantColor = m3OnSurfaceVariant()
    val subtleFill08 = m3SubtleFill(alpha = 0.08f)
    val subtleFill15 = m3SubtleFill(alpha = 0.15f)
    val playingRowBg = remember(accent) { accent.copy(alpha = 0.12f) }
    val selectedRowBg = remember(accent) { accent.copy(alpha = 0.20f) }
    val nowPlayingThumbBg = remember(accent) { accent.copy(alpha = 0.22f) }
    val secondaryPlayingColor = remember(accent) { accent.copy(alpha = 0.85f) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(itemAlpha)
            .clip(trackRowShape)
            .background(
                when {
                    isSelected -> selectedRowBg
                    isPlaying -> playingRowBg
                    else -> Color.Transparent
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (isSupported) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                }
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection checkbox indicator when in selection mode
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) accent else subtleFill15
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    GlyphCheck(color = TrueBlack, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        // Artwork Squircle (46dp, RoundedCornerShape(12.dp))
        TrackAlbumThumbnail(
            filePath = item.filePath,
            isCurrentTrack = isPlaying,
            currentTrackArt = null,
            modifier = Modifier.size(46.dp),
            cornerRadius = 12.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isPlaying) nowPlayingThumbBg else subtleFill08
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    GlyphNowPlaying(color = accent, modifier = Modifier.size(20.dp))
                } else {
                    GlyphMusicNote(
                        color = if (isSupported) onSurfaceVariantColor else Color(0xFFFF5252),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Metadata Column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayTitle,
                color = when {
                    isPlaying -> accent
                    isSupported -> onSurfaceColor
                    else -> onSurfaceVariantColor
                },
                fontFamily = ModernSansFont,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = secondaryMetadata,
                color = if (isPlaying) secondaryPlayingColor else onSurfaceVariantColor,
                fontSize = 12.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Trailing star for playlist items & 3-dot options
        if (inPlaylist && isSupported) {
            GlyphPin(color = accent, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }

        if (!isSelectionMode) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (isSupported) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onMenuClick()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                GlyphMoreVert(color = onSurfaceVariantColor, modifier = Modifier.size(18.dp))
            }
        }
    }
}
