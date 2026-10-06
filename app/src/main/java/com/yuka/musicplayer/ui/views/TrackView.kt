package com.yuka.musicplayer.ui.views

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.RepeatMode
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.TerminalWhite
import kotlin.math.roundToInt

@Composable
fun PrecisionScrubber(
    playbackPosition: Double,
    durationSeconds: Double,
    isPlaying: Boolean,
    dynamicColor: Color,
    onSeekTo: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }

    val currentFraction = if (durationSeconds > 0) {
        (playbackPosition / durationSeconds).toFloat().coerceIn(0f, 1f)
    } else 0f

    val displayProgress = if (isDragging) dragProgress else currentFraction
    val targetSeconds = displayProgress.toDouble() * durationSeconds

    // Format timestamps
    val curPosSec = if (isDragging) targetSeconds else playbackPosition
    val curMin = (curPosSec / 60).toInt()
    val curSec = (curPosSec % 60).toInt()
    val posStr = String.format("%02d:%02d", curMin, curSec)

    val remainingSec = (durationSeconds - curPosSec).coerceAtLeast(0.0)
    val remMin = (remainingSec / 60).toInt()
    val remSec = (remainingSec % 60).toInt()
    val durStr = String.format("-%02d:%02d", remMin, remSec)

    val animatedThumbRadius by animateFloatAsState(
        targetValue = if (isDragging) 6.5f else 4.5f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "thumbRadius"
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(0.88f)
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val density = LocalDensity.current
        val trackStart = with(density) { 6.dp.toPx() }
        val trackEnd = totalWidthPx - trackStart
        val trackWidth = (trackEnd - trackStart).coerceAtLeast(1f)

        val thumbXPx = trackStart + displayProgress * trackWidth

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Interactive Scrubber Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .pointerInput(durationSeconds) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isDragging = true
                            dragProgress = ((down.position.x - trackStart) / trackWidth).coerceIn(0f, 1f)

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                change.consume()
                                dragProgress = ((change.position.x - trackStart) / trackWidth).coerceIn(0f, 1f)
                            }

                            val finalSeekSec = dragProgress.toDouble() * durationSeconds
                            isDragging = false
                            onSeekTo(finalSeekSec)
                        }
                    }
            ) {
                val cy = size.height / 2f
                val trackStroke = 3.dp.toPx()

                // Background track rail (Subtle dark navy groove)
                drawLine(
                    color = Color(0xFF141A28),
                    start = Offset(trackStart, cy),
                    end = Offset(trackEnd, cy),
                    strokeWidth = trackStroke,
                    cap = StrokeCap.Round
                )

                // Active Played rail
                if (thumbXPx > trackStart) {
                    // Outer subtle glow
                    drawLine(
                        color = dynamicColor.copy(alpha = 0.35f),
                        start = Offset(trackStart, cy),
                        end = Offset(thumbXPx, cy),
                        strokeWidth = trackStroke + 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Core line
                    drawLine(
                        color = dynamicColor,
                        start = Offset(trackStart, cy),
                        end = Offset(thumbXPx, cy),
                        strokeWidth = trackStroke,
                        cap = StrokeCap.Round
                    )
                }

                // Thumb outer aura glow
                val thumbR = with(density) { animatedThumbRadius.dp.toPx() }
                drawCircle(
                    color = dynamicColor.copy(alpha = if (isDragging) 0.40f else 0.20f),
                    radius = thumbR + 4.dp.toPx(),
                    center = Offset(thumbXPx, cy)
                )

                // Thumb core solid circle
                drawCircle(
                    color = dynamicColor,
                    radius = thumbR,
                    center = Offset(thumbXPx, cy)
                )

                // Center tactile dot
                drawCircle(
                    color = Color(0xFF070A12),
                    radius = thumbR * 0.45f,
                    center = Offset(thumbXPx, cy)
                )
            }

            // Timestamps Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = posStr,
                    color = if (isDragging) dynamicColor else TerminalWhite.copy(alpha = 0.85f),
                    fontFamily = TerminalFont,
                    fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 10.5.sp
                )
                Text(
                    text = durStr,
                    color = TerminalGray.copy(alpha = 0.75f),
                    fontFamily = TerminalFont,
                    fontSize = 10.5.sp
                )
            }
        }
    }
}

@Composable
fun TrackView(
    track: TrackInfo?,
    playbackPosition: Double,
    isPlaying: Boolean,
    currentVolume: Float,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    priorityQueueSize: Int,
    onTogglePlay: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrev: () -> Unit,
    onStop: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onSeekTo: (Double) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dynamicColor = LocalAccentColor.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        if (track == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(Color(0xFF0C101C), RoundedCornerShape(12.dp))
                    .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("NO TRACK SELECTED", color = TerminalGray, style = MaterialTheme.typography.titleMedium, fontFamily = TerminalFont)
            }
            return
        }

        val animatedGlowColor by animateColorAsState(
            targetValue = track.dominantColor.copy(alpha = 0.55f),
            animationSpec = tween(durationMillis = 600),
            label = "coverArtUnderglow"
        )

        // 1. Album Art Hero with Organic Glow Bloom
        Box(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            // Ambient organic bloom
            Box(
                modifier = Modifier
                    .fillMaxSize(1.18f)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(animatedGlowColor, Color.Transparent)
                        )
                    )
                    .blur(26.dp)
            )

            // Album cover container (Clean 12dp rounded corner, layered shadow)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0E1322), RoundedCornerShape(12.dp))
                    .border(0.8.dp, dynamicColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
            ) {
                if (track.coverArt != null) {
                    Image(
                        bitmap = track.coverArt.asImageBitmap(),
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0E1322)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "[ NO COVER ART ]",
                            color = TerminalGray.copy(alpha = 0.7f),
                            fontFamily = TerminalFont,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 2. Track Title & Artist
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        ) {
            Text(
                text = track.title,
                color = TerminalWhite,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TerminalFont,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = track.artist,
                color = dynamicColor.copy(alpha = 0.9f),
                fontSize = 12.5.sp,
                fontFamily = TerminalFont,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 3. Audiophile Spec Strip (Minimalist Industrial Badges)
        Row(
            modifier = Modifier
                .background(Color(0xFF0A0E1A), RoundedCornerShape(20.dp))
                .border(0.6.dp, dynamicColor.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.codec.uppercase(),
                color = dynamicColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TerminalFont
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(modifier = Modifier.size(3.dp).background(Color(0xFF445069), CircleShape))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "DIRECT USB",
                color = TerminalWhite.copy(alpha = 0.75f),
                fontSize = 9.sp,
                fontFamily = TerminalFont
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(modifier = Modifier.size(3.dp).background(Color(0xFF445069), CircleShape))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "BIT-PERFECT",
                color = dynamicColor.copy(alpha = 0.85f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TerminalFont
            )
        }

        // 4. Precision Scrubber
        PrecisionScrubber(
            playbackPosition = playbackPosition,
            durationSeconds = track.durationSeconds,
            isPlaying = isPlaying,
            dynamicColor = dynamicColor,
            onSeekTo = onSeekTo
        )

        // 5. Tactile Volume Stepper Row
        val volPercent = (currentVolume * 100).roundToInt()
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.yuka.musicplayer.ui.components.TactileBox(
                onClick = { onVolumeChange((currentVolume - 0.05f).coerceAtLeast(0.0f)) },
                pressedScale = 0.88f,
                modifier = Modifier
                    .size(width = 38.dp, height = 28.dp)
                    .background(Color(0xFF0F1524), RoundedCornerShape(6.dp))
                    .border(0.8.dp, dynamicColor.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            ) {
                Text("-", color = TerminalWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .height(28.dp)
                    .background(Color(0xFF0A0E1A), RoundedCornerShape(6.dp))
                    .border(0.6.dp, dynamicColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "VOL: $volPercent%",
                    color = dynamicColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            com.yuka.musicplayer.ui.components.TactileBox(
                onClick = { onVolumeChange((currentVolume + 0.05f).coerceAtMost(1.0f)) },
                pressedScale = 0.88f,
                modifier = Modifier
                    .size(width = 38.dp, height = 28.dp)
                    .background(Color(0xFF0F1524), RoundedCornerShape(6.dp))
                    .border(0.8.dp, dynamicColor.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            ) {
                Text("+", color = TerminalWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
            }
        }

        // 6. Hero Transport Controls
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.yuka.musicplayer.ui.components.SecondaryTransportButton(
                onClick = onPlayPrev,
                accentColor = dynamicColor,
                size = 50.dp
            ) {
                com.yuka.musicplayer.ui.components.GlyphPrev(color = dynamicColor, modifier = Modifier.size(18.dp))
            }

            com.yuka.musicplayer.ui.components.HeroPlayButton(
                isPlaying = isPlaying,
                accentColor = dynamicColor,
                onClick = onTogglePlay,
                size = 64.dp
            )

            com.yuka.musicplayer.ui.components.SecondaryTransportButton(
                onClick = onPlayNext,
                accentColor = dynamicColor,
                size = 50.dp
            ) {
                com.yuka.musicplayer.ui.components.GlyphNext(color = dynamicColor, modifier = Modifier.size(18.dp))
            }
        }

        // 7. Secondary Modes Row (Pill Chips with micro-LED dots)
        Row(
            modifier = Modifier.fillMaxWidth(0.90f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.yuka.musicplayer.ui.components.ModePillChip(
                label = if (isShuffleEnabled) "SHUFFLE" else "SHUF",
                isActive = isShuffleEnabled,
                accentColor = dynamicColor,
                onClick = onToggleShuffle,
                modifier = Modifier.weight(1f),
                icon = {
                    com.yuka.musicplayer.ui.components.GlyphShuffle(
                        color = if (isShuffleEnabled) dynamicColor else TerminalGray,
                        modifier = Modifier.size(14.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.width(6.dp))

            com.yuka.musicplayer.ui.components.ModePillChip(
                label = "QUEUE ($priorityQueueSize)",
                isActive = priorityQueueSize > 0,
                accentColor = dynamicColor,
                onClick = onOpenQueue,
                modifier = Modifier.weight(1.15f)
            )

            Spacer(modifier = Modifier.width(6.dp))

            val (repLabel, isRepActive) = when (repeatMode) {
                RepeatMode.OFF -> "REP: OFF" to false
                RepeatMode.ALL -> "REP: ALL" to true
                RepeatMode.ONE -> "REP: 1" to true
            }

            com.yuka.musicplayer.ui.components.ModePillChip(
                label = repLabel,
                isActive = isRepActive,
                accentColor = dynamicColor,
                onClick = onCycleRepeat,
                modifier = Modifier.weight(1f),
                icon = {
                    com.yuka.musicplayer.ui.components.GlyphRepeat(
                        color = if (isRepActive) dynamicColor else TerminalGray,
                        modifier = Modifier.size(14.dp)
                    )
                }
            )
        }
    }
}
