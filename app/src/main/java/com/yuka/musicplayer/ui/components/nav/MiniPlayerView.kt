package com.yuka.musicplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.LocalIsDarkTheme
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.glassMorphic
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import kotlinx.coroutines.launch

/**
 * Authentic Material 3 Glassmorphic Floating Mini-Player.
 * - Tap opens Now Playing directly.
 * - Swipe up follows finger in real-time smoothly to expand Now Playing.
 * - Swipe down follows finger in real-time to stop playback and dismiss Mini Player.
 * - Glassmorphic surface (opacity = 0.85f + accent tint).
 */
@Composable
fun MiniPlayerView(
    track: TrackInfo,
    isPlaying: Boolean,
    playbackPosition: Double = 0.0,
    durationSeconds: Double = 0.0,
    positionProvider: () -> Double = { playbackPosition },
    @Suppress("UNUSED_PARAMETER") bgMode: String = "SIGNATURE",
    onTogglePlay: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    onStopAndDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dynamicColor = LocalAccentColor.current
    val isDark = LocalIsDarkTheme.current
    val containerColor = com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh().copy(alpha = 1f)

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedDragOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "miniPlayerDragY"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .graphicsLayer {
                translationY = animatedDragOffsetY
                if (dragOffsetY > 0f) {
                    alpha = (1f - (dragOffsetY / 150f)).coerceIn(0f, 1f)
                }
            }
            .glassMorphic(
                backgroundColor = containerColor,
                borderColor = Color.Unspecified,
                shape = RoundedCornerShape(26.dp),
                borderWidth = 0.dp
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragOffsetY = 0f },
                    onDragEnd = {
                        if (dragOffsetY < -32f) {
                            onNavigateToNowPlaying()
                        } else if (dragOffsetY > 45f) {
                            onStopAndDismiss()
                        }
                        dragOffsetY = 0f
                    },
                    onDragCancel = { dragOffsetY = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY += dragAmount
                    }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onNavigateToNowPlaying()
            }
    ) {
        // Ultra-subtle top drag handle pill
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
                .size(width = 24.dp, height = 2.dp)
                .background(dynamicColor.copy(alpha = 0.35f), RoundedCornerShape(50))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val imageMod = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f))

            val coverBitmap = remember(track.file.absolutePath, track.coverArt) {
                com.yuka.musicplayer.util.AlbumArtThumbnailCache.getImageBitmap(track.file.absolutePath)
                    ?: track.coverArt?.asImageBitmap()
            }
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap,
                    contentDescription = "Cover",
                    modifier = imageMod,
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = imageMod,
                    contentAlignment = Alignment.Center
                ) {
                    GlyphMusicNote(color = dynamicColor, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = m3OnSurface(),
                    fontSize = 13.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    color = m3OnSurfaceVariant(),
                    fontSize = 11.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            val playPauseScale = remember { Animatable(1f) }
            val playPauseCornerRadius by animateDpAsState(
                targetValue = if (isPlaying) 12.dp else 19.dp,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
                label = "miniPlayerShape"
            )
            val miniPlayerScope = rememberCoroutineScope()

            TactileBox(
                onClick = {
                    miniPlayerScope.launch {
                        playPauseScale.animateTo(0.78f, tween(75))
                        playPauseScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 450f))
                    }
                    onTogglePlay()
                },
                pressedScale = 0.88f,
                modifier = Modifier
                    .size(38.dp)
                    .graphicsLayer {
                        scaleX = playPauseScale.value
                        scaleY = playPauseScale.value
                    }
                    .clip(RoundedCornerShape(playPauseCornerRadius))
                    .background(dynamicColor)
            ) {
                val iconColor = if (!isDark || com.yuka.musicplayer.util.ColorContrastHelper.calculateLuminance(dynamicColor) <= 0.45f) {
                    Color.White
                } else {
                    Color(0xFF0E0D13)
                }
                AnimatedContent(
                    targetState = isPlaying,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.75f, animationSpec = tween(180)))
                            .togetherWith(fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.75f, animationSpec = tween(140)))
                    },
                    label = "miniPlayerPlayPauseIcon"
                ) { playing ->
                    if (playing) {
                        GlyphPause(color = iconColor, modifier = Modifier.size(16.dp))
                    } else {
                        Box(modifier = Modifier.padding(start = 2.dp)) {
                            GlyphPlay(color = iconColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // 2dp Material 3 Progress Indicator hairline along the bottom
        MiniPlayerProgressBar(
            positionProvider = positionProvider,
            durationSeconds = durationSeconds,
            dynamicColor = dynamicColor,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

@Composable
private fun MiniPlayerProgressBar(
    positionProvider: () -> Double,
    durationSeconds: Double,
    dynamicColor: Color,
    modifier: Modifier = Modifier
) {
    val currentPosition = positionProvider()
    val progress = if (durationSeconds > 0.0) (currentPosition / durationSeconds).toFloat().coerceIn(0f, 1f) else 0f
    if (progress > 0f) {
        Box(
            modifier = modifier
                .fillMaxWidth(progress)
                .height(2.dp)
                .background(dynamicColor)
        )
    }
}
