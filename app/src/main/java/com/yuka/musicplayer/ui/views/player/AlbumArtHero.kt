package com.yuka.musicplayer.ui.views

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.ui.components.GlyphMusicNote
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerLow
import com.yuka.musicplayer.util.AlbumArtThumbnailCache

@Composable
internal fun AlbumArtHero(
    track: TrackInfo,
    albumScale: Float,
    dynamicColor: Color,
    modifier: Modifier = Modifier
) {
    val trackPath = track.file.absolutePath
    val sharpCoverBitmap = remember(trackPath, track.coverArt) {
        AlbumArtThumbnailCache.getHeroCoverImageBitmap(trackPath)
            ?: track.coverArt?.asImageBitmap()
    }
    var lastValidHeroBitmap by remember { mutableStateOf<ImageBitmap?>(sharpCoverBitmap) }
    val isInitialPlaceholder = track.artist == "Loading..." && sharpCoverBitmap == null

    LaunchedEffect(trackPath, sharpCoverBitmap, track.artist) {
        if (sharpCoverBitmap != null) {
            lastValidHeroBitmap = sharpCoverBitmap
        } else if (track.artist != "Loading...") {
            lastValidHeroBitmap = null
        }
    }

    val effectiveBitmap = sharpCoverBitmap ?: if (isInitialPlaceholder) lastValidHeroBitmap else null

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = albumScale
                scaleY = albumScale
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(m3SurfaceContainerLow())
        ) {
            Crossfade(
                targetState = effectiveBitmap,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "albumArtHeroCrossfade"
            ) { activeBitmap ->
                if (activeBitmap != null) {
                    Image(
                        bitmap = activeBitmap,
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphMusicNote(
                            color = dynamicColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }
        }
    }
}
