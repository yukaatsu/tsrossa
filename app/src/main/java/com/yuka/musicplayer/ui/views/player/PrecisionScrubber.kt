package com.yuka.musicplayer.ui.views

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import kotlinx.coroutines.isActive

/**
 * Material 3 Precision Scrubber with smooth 60/120fps local time interpolation.
 * Clean, borderless Material You slider track with responsive dragging thumb.
 */
@Composable
fun PrecisionScrubber(
    playbackPosition: Double,
    durationSeconds: Double,
    isPlaying: Boolean,
    dynamicColor: Color,
    onSeekTo: (Double) -> Unit,
    trackKey: Any? = null,
    centerBadgeSlot: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentOnSeekTo by rememberUpdatedState(onSeekTo)
    val currentDurationSeconds by rememberUpdatedState(durationSeconds)
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    // Guard against snap-back: holds user's target seek position until JNI confirms playback arrived near it
    var pendingSeekTargetSec by remember { mutableStateOf<Double?>(null) }
    var pendingSeekTimeoutNanos by remember { mutableLongStateOf(0L) }

    // Real-time anchor tracking from hardware / JNI updates
    var anchorPosSec by remember { mutableDoubleStateOf(playbackPosition) }
    var anchorNanos by remember { mutableLongStateOf(System.nanoTime()) }

    // Single unified 60/120fps continuous progress float dedicated ONLY for draw phase
    val smoothProgress = remember { Animatable(0f) }
    var displayPosSec by remember { mutableIntStateOf(playbackPosition.toInt()) }
    val durSecInt = remember(durationSeconds) { durationSeconds.toInt() }

    // Reset scrubber anchor and timer immediately when switching tracks (Next / Prev / Track select)
    LaunchedEffect(trackKey) {
        isDragging = false
        pendingSeekTargetSec = null
        anchorPosSec = 0.0
        anchorNanos = System.nanoTime()
        displayPosSec = 0
        smoothProgress.snapTo(0f)
    }

    // Re-anchor on play/pause state transitions so pausing freezes at the exact playback position
    LaunchedEffect(isPlaying) {
        val now = System.nanoTime()
        if (!isDragging && pendingSeekTargetSec == null) {
            anchorPosSec = playbackPosition
            anchorNanos = now
            displayPosSec = playbackPosition.toInt()
        }
    }

    // Synchronize anchor whenever playbackPosition updates from JNI or when seek finishes
    LaunchedEffect(playbackPosition) {
        val now = System.nanoTime()
        val target = pendingSeekTargetSec
        if (target != null) {
            // Clear pending seek guard if engine is within 1.5s of target, or if timeout expired
            val diff = kotlin.math.abs(playbackPosition - target)
            val timedOut = now > pendingSeekTimeoutNanos
            if (diff < 1.5 || timedOut) {
                pendingSeekTargetSec = null
                anchorPosSec = playbackPosition
                anchorNanos = now
            }
        } else if (!isDragging) {
            anchorPosSec = playbackPosition
            anchorNanos = now
        }
    }

    // Continuous glide loop synchronized with display refresh rate, paused when app is not resumed
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(isPlaying, durationSeconds, trackKey) {
        if (durationSeconds <= 0.0) {
            displayPosSec = 0
            smoothProgress.snapTo(0f)
            return@LaunchedEffect
        }
        while (isActive) {
            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                kotlinx.coroutines.delay(250L)
                continue
            }
            var targetFrac = 0f
            withFrameNanos { frameNanos ->
                if (!isDragging) {
                    val guardTarget = pendingSeekTargetSec
                    val currentPosSec = if (guardTarget != null) {
                        guardTarget
                    } else if (isPlaying) {
                        // Cap local frame extrapolation to 0.5s between JNI polls so the timer never
                        // phantom-advances if hardware streaming is warming up or stalled
                        val elapsedSec = (((frameNanos - anchorNanos).coerceAtLeast(0L)) / 1_000_000_000.0)
                            .coerceIn(0.0, 0.5)
                        (anchorPosSec + elapsedSec).coerceIn(0.0, durationSeconds)
                    } else {
                        anchorPosSec.coerceIn(0.0, durationSeconds)
                    }

                    targetFrac = (currentPosSec / durationSeconds).toFloat().coerceIn(0f, 1f)

                    val secInt = currentPosSec.toInt()
                    if (secInt != displayPosSec) {
                        displayPosSec = secInt
                    }
                }
            }
            if (!isDragging) {
                smoothProgress.snapTo(targetFrac)
            }
        }
    }

    val curSecInt = if (isDragging) {
        (dragProgress * durationSeconds).toInt()
    } else if (pendingSeekTargetSec != null) {
        pendingSeekTargetSec!!.toInt()
    } else {
        displayPosSec
    }

    val posStr = remember(curSecInt) {
        val curMin = curSecInt / 60
        val curSec = curSecInt % 60
        String.format(java.util.Locale.US, "%02d:%02d", curMin, curSec)
    }

    val durStr = remember(durSecInt) {
        val dMin = durSecInt / 60
        val dSec = durSecInt % 60
        String.format(java.util.Locale.US, "%02d:%02d", dMin, dSec)
    }

    val animatedThumbRadius by animateFloatAsState(
        targetValue = if (isDragging) 7f else 5.5f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "thumbRadius"
    )

    val isDark = com.yuka.musicplayer.ui.theme.LocalIsDarkTheme.current
    val railInactiveColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.12f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .systemGestureExclusion()
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val density = LocalDensity.current
        val trackStart = with(density) { 6.dp.toPx() }
        val trackEnd = totalWidthPx - trackStart
        val trackWidth = (trackEnd - trackStart).coerceAtLeast(1f)

        Column(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(trackStart, trackWidth) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (currentDurationSeconds <= 0.0) return@awaitEachGesture
                            down.consume()
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

                            val dur = currentDurationSeconds
                            if (dur <= 0.0) {
                                isDragging = false
                                return@awaitEachGesture
                            }
                            val rawSeekSec = dragProgress.toDouble() * dur
                            val safeMaxSeekSec = if (dur > 0.5) (dur - 0.25) else dur
                            val finalSeekSec = rawSeekSec.coerceIn(0.0, safeMaxSeekSec)
                            // Engage anti snap-back guard: keep thumb at seek target until JNI catches up
                            pendingSeekTargetSec = finalSeekSec
                            pendingSeekTimeoutNanos = System.nanoTime() + 800_000_000L // 800ms safety timeout
                            anchorPosSec = finalSeekSec
                            anchorNanos = System.nanoTime()
                            displayPosSec = finalSeekSec.toInt()
                            isDragging = false
                            currentOnSeekTo(finalSeekSec)
                        }
                    }
            ) {
                val cy = size.height / 2f
                val trackStroke = 4.dp.toPx()
                val currentProgress = if (isDragging) {
                    dragProgress
                } else if (pendingSeekTargetSec != null && durationSeconds > 0.0) {
                    (pendingSeekTargetSec!! / durationSeconds).toFloat().coerceIn(0f, 1f)
                } else {
                    smoothProgress.value
                }
                val thumbXPx = trackStart + currentProgress * trackWidth
                val thumbR = with(density) { animatedThumbRadius.dp.toPx() }

                // M3 Inactive track rail
                drawLine(
                    color = railInactiveColor,
                    start = Offset(trackStart, cy),
                    end = Offset(trackEnd, cy),
                    strokeWidth = trackStroke,
                    cap = StrokeCap.Round
                )

                // M3 Active progress rail
                if (currentProgress > 0f) {
                    drawLine(
                        color = dynamicColor,
                        start = Offset(trackStart, cy),
                        end = Offset(thumbXPx, cy),
                        strokeWidth = trackStroke,
                        cap = StrokeCap.Round
                    )
                }

                // M3 Circular Thumb
                drawCircle(
                    color = dynamicColor,
                    radius = thumbR,
                    center = Offset(thumbXPx, cy)
                )
            }

            // Timestamps & Centered Apple Music-style Audio Quality Indicator Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = posStr,
                    color = if (isDragging) dynamicColor else m3OnSurfaceVariant(),
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.defaultMinSize(minWidth = 42.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    centerBadgeSlot?.invoke()
                }

                Text(
                    text = durStr,
                    color = m3OnSurfaceVariant(),
                    fontFamily = TerminalFont,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.defaultMinSize(minWidth = 42.dp)
                )
            }
        }
    }
}
