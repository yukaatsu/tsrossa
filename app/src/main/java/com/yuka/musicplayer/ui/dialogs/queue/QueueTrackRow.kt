package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import com.yuka.musicplayer.ui.components.GlyphClose
import com.yuka.musicplayer.ui.components.GlyphMusicNote
import com.yuka.musicplayer.ui.components.TactileBox
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SurfaceContainer
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.util.TrackAlbumThumbnail
import java.io.File

@Composable
internal fun QueueTrackRow(
    file: File,
    title: String,
    artist: String,
    accentColor: Color,
    isPriority: Boolean,
    onSelectTrack: (File) -> Unit,
    onRemoveFromQueue: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isPriority) 3.dp else 2.5.dp)
            .clip(RoundedCornerShape(if (isPriority) 14.dp else 12.dp))
            .background(if (isPriority) m3SurfaceContainer() else m3SurfaceContainer().copy(alpha = 0.65f))
            .clickable { onSelectTrack(file) }
            .padding(horizontal = 10.dp, vertical = if (isPriority) 8.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val thumbSize = if (isPriority) 44.dp else 40.dp
        val cornerRad = if (isPriority) 10.dp else 8.dp

        TrackAlbumThumbnail(
            filePath = file.absolutePath,
            isCurrentTrack = false,
            currentTrackArt = null,
            modifier = Modifier.size(thumbSize),
            cornerRadius = cornerRad
        ) {
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .clip(RoundedCornerShape(cornerRad))
                    .background(if (isPriority) accentColor.copy(alpha = 0.16f) else m3SurfaceContainerHigh()),
                contentAlignment = Alignment.Center
            ) {
                GlyphMusicNote(
                    color = if (isPriority) accentColor else m3OnSurfaceVariant().copy(alpha = 0.60f),
                    modifier = Modifier.size(if (isPriority) 20.dp else 18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(if (isPriority) 12.dp else 10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = m3OnSurface(),
                fontFamily = ModernSansFont,
                fontWeight = if (isPriority) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = if (isPriority) 13.5.sp else 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = artist,
                color = m3OnSurfaceVariant(),
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Normal,
                fontSize = if (isPriority) 12.sp else 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (onRemoveFromQueue != null) {
            Spacer(modifier = Modifier.width(8.dp))
            TactileBox(
                onClick = onRemoveFromQueue,
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
}
