package com.yuka.musicplayer.ui.views

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.GlyphLyrics
import com.yuka.musicplayer.ui.theme.LocalBgMode
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerLow
import com.yuka.musicplayer.util.LyricsHelper
import com.yuka.musicplayer.util.ParsedLyrics
import kotlinx.coroutines.isActive

/**
 * 60 FPS smooth playback position interpolator using withFrameNanos.
 * Keeps word-by-word karaoke fills perfectly fluid without stressing background threads.
 */
@Composable
internal fun rememberSmoothPlaybackPosition(
    externalPositionSec: Double,
    isPlaying: Boolean
): Double {
    var smoothPosition by remember { mutableDoubleStateOf(externalPositionSec) }
    val latestExternalPos by rememberUpdatedState(externalPositionSec)

    LaunchedEffect(externalPositionSec) {
        if (!isPlaying || kotlin.math.abs(externalPositionSec - smoothPosition) > 0.35) {
            smoothPosition = externalPositionSec
        }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (isActive && isPlaying) {
            val frameNanos = withFrameNanos { it }
            val deltaSec = ((frameNanos - lastFrameNanos) / 1_000_000_000.0).coerceIn(0.0, 0.08)
            lastFrameNanos = frameNanos
            val candidate = smoothPosition + deltaSec
            smoothPosition = if (kotlin.math.abs(candidate - latestExternalPos) > 0.45) {
                latestExternalPos
            } else {
                candidate
            }
        }
    }

    return if (isPlaying) smoothPosition else externalPositionSec
}

/**
 * Apple Music style "Beauty Lyrics" view with 60 FPS interpolation, word-by-word karaoke sweep,
 * depth-of-field progressive blur, instrumental breathing dots, and vertical edge vignette mask.
 */
@Composable
internal fun NowPlayingLyricsView(
    lyrics: ParsedLyrics?,
    isLoading: Boolean,
    playbackPosition: Double,
    isPlaying: Boolean,
    dynamicColor: Color,
    onSeekTo: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentBgMode = LocalBgMode.current
    val isDark = com.yuka.musicplayer.ui.theme.LocalIsDarkTheme.current
    val cardBackground = if (currentBgMode == "ARTWORK" || currentBgMode == "BLUR") {
        if (isDark) Color.Black.copy(alpha = 0.52f) else Color.White.copy(alpha = 0.65f)
    } else {
        m3SurfaceContainerLow()
    }

    val smoothPosition = rememberSmoothPlaybackPosition(playbackPosition, isPlaying)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(26.dp))
            .background(cardBackground)
            .border(0.8.dp, dynamicColor.copy(alpha = 0.22f), RoundedCornerShape(26.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp, bottom = 10.dp, start = 14.dp, end = 14.dp)
        ) {
            // Header Bar: "LYRICS" and SYNCED badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlyphLyrics(color = dynamicColor, modifier = Modifier.size(16.dp))
                    Text(
                        text = "LYRICS",
                        color = dynamicColor,
                        fontFamily = TerminalFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.08.sp
                    )
                }

                if (lyrics?.isSynced == true) {
                    val badgeLabel = if (lyrics.isWordSynced) "SYNCED • WORD" else "SYNCED"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(dynamicColor.copy(alpha = 0.18f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            color = dynamicColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TerminalFont
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Body Content with Top & Bottom Feather Vignette Mask
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.00f to Color.Transparent,
                                0.10f to Color.Black,
                                0.88f to Color.Black,
                                1.00f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Text(
                        text = "Loading lyrics...",
                        color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                        fontFamily = ModernSansFont,
                        fontSize = 14.sp
                    )
                } else if (lyrics == null || (lyrics.lines.isEmpty() && lyrics.plainText.isBlank())) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        GlyphLyrics(
                            color = dynamicColor.copy(alpha = 0.45f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No lyrics found",
                            color = com.yuka.musicplayer.ui.theme.m3OnSurface(),
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Supports embedded track lyrics or companion .lrc files in song folder",
                            color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                            fontFamily = TerminalFont,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                } else if (lyrics.isSynced) {
                    val activeIndex = LyricsHelper.findActiveLineIndex(lyrics.lines, smoothPosition)
                    val listState = rememberLazyListState()

                    LaunchedEffect(activeIndex) {
                        if (activeIndex >= 0 && !listState.isScrollInProgress) {
                            listState.animateScrollToItem(
                                index = (activeIndex - 1).coerceAtLeast(0)
                            )
                        }
                    }

                    // Check for breathing dots conditions
                    val showIntroDots = lyrics.lines.isNotEmpty() &&
                            smoothPosition < lyrics.lines.first().timeSec - 0.5 &&
                            lyrics.lines.first().timeSec >= 3.0

                    val showMidBreakDots = activeIndex >= 0 && activeIndex < lyrics.lines.size - 1 && run {
                        val activeLine = lyrics.lines[activeIndex]
                        val nextLine = lyrics.lines[activeIndex + 1]
                        smoothPosition > activeLine.endTimeSec + 0.8 && (nextLine.timeSec - activeLine.endTimeSec >= 5.0)
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 28.dp, bottom = 48.dp, start = 8.dp, end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        if (showIntroDots) {
                            item {
                                InterludeBreathingDots(dynamicColor = dynamicColor)
                            }
                        }

                        itemsIndexed(lyrics.lines) { index, line ->
                            val isActive = index == activeIndex
                            val distance = kotlin.math.abs(index - activeIndex)

                            val targetAlpha = if (listState.isScrollInProgress) {
                                0.80f
                            } else {
                                when {
                                    isActive -> 1f
                                    distance == 1 -> 0.55f
                                    distance == 2 -> 0.32f
                                    else -> 0.18f
                                }
                            }

                            val targetScale = if (isActive) 1.0f else 0.94f

                            val animAlpha by animateFloatAsState(
                                targetValue = targetAlpha,
                                animationSpec = tween(durationMillis = 280),
                                label = "lyricAlpha"
                            )
                            val animScale by animateFloatAsState(
                                targetValue = targetScale,
                                animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
                                label = "lyricScale"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = animScale
                                        scaleY = animScale
                                        alpha = animAlpha
                                    }
                            ) {
                                if (line.words.isNotEmpty()) {
                                    WordSyncedLyricLine(
                                        line = line,
                                        smoothPosition = if (isActive) smoothPosition else if (index < activeIndex) Double.MAX_VALUE else 0.0,
                                        isActive = isActive,
                                        dynamicColor = dynamicColor,
                                        onClick = { onSeekTo(line.timeSec) }
                                    )
                                } else {
                                    StandardLyricLine(
                                        line = line,
                                        isActive = isActive,
                                        dynamicColor = dynamicColor,
                                        onClick = { onSeekTo(line.timeSec) }
                                    )
                                }
                            }

                            if (showMidBreakDots && index == activeIndex) {
                                InterludeBreathingDots(dynamicColor = dynamicColor)
                            }
                        }
                    }
                } else {
                    // Plain unsynced text
                    val textScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(textScrollState)
                            .padding(vertical = 14.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = lyrics.plainText,
                            color = com.yuka.musicplayer.ui.theme.m3OnSurface().copy(alpha = 0.85f),
                            fontFamily = ModernSansFont,
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
