package com.yuka.musicplayer.ui.views

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.os.Build
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import com.yuka.musicplayer.util.LyricLine
import com.yuka.musicplayer.util.LyricWord
import com.yuka.musicplayer.util.VoiceType
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.yuka.musicplayer.ui.theme.glassMorphic
import com.yuka.musicplayer.util.LyricsHelper
import com.yuka.musicplayer.util.ParsedLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.yuka.musicplayer.ui.theme.LocalBgMode
import com.yuka.musicplayer.ui.theme.m3SurfaceBase
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
import com.yuka.musicplayer.ui.components.*
import com.yuka.musicplayer.ui.dialogs.VolumeModalSheet
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.m3ActivePillContainer
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SurfaceContainer
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerLow
import kotlin.math.roundToInt
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

            // Timestamps Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = posStr,
                    color = if (isDragging) dynamicColor else m3OnSurfaceVariant(),
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp
                )
                Text(
                    text = durStr,
                    color = m3OnSurfaceVariant(),
                    fontFamily = TerminalFont,
                    fontSize = 11.5.sp
                )
            }
        }
    }
}

/**
 * Radical Material 3 (Material You) Now Playing Screen.
 */
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
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onQueueDragFractionChanged: (Float) -> Unit = {},
    onCollapseDragFractionChanged: (Float) -> Unit = {},
    onSeekTo: (Double) -> Unit = {},
    isHardwareVolumeActive: Boolean = false,
    isHardwareVolumeSupported: Boolean = false,
    isHardwareVolumeEnabled: Boolean = false,
    onToggleHardwareVolume: ((Boolean) -> Unit)? = null,
    onCollapse: (() -> Unit)? = null,
    outputBitDepth: Int = 0,
    outputSampleRate: Int = 0,
    isDacConnected: Boolean = false,
    isPeqActive: Boolean = false,
    onOpenPeq: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dynamicColor = LocalAccentColor.current
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val currentBgMode = LocalBgMode.current
    val isDark = com.yuka.musicplayer.ui.theme.LocalIsDarkTheme.current
    val opaqueBackdropColor = if (isDark) Color(0xFF09080C) else Color(0xFFF8F8FC)

    var lastValidCoverBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val trackCoverBitmap = remember(track?.file?.absolutePath, track?.coverArt) {
        track?.coverArt?.asImageBitmap()
    }
    LaunchedEffect(trackCoverBitmap, track?.artist) {
        if (trackCoverBitmap != null) {
            lastValidCoverBitmap = trackCoverBitmap
        } else if (track?.artist != "Loading...") {
            lastValidCoverBitmap = null
        }
    }
    val effectiveCoverBitmap = trackCoverBitmap ?: lastValidCoverBitmap

    val haptic = LocalHapticFeedback.current
    var showLyrics by remember { mutableStateOf(false) }
    var parsedLyrics by remember(track?.file?.absolutePath) { mutableStateOf<ParsedLyrics?>(null) }
    var isLyricsLoading by remember(track?.file?.absolutePath) { mutableStateOf(false) }

    LaunchedEffect(track?.file?.absolutePath) {
        val file = track?.file
        if (file != null && file.exists()) {
            isLyricsLoading = true
            parsedLyrics = withContext(Dispatchers.IO) {
                LyricsHelper.findLyricsForTrack(file)
            }
            isLyricsLoading = false
        } else {
            parsedLyrics = null
            isLyricsLoading = false
        }
    }

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var totalVerticalDrag by remember { mutableFloatStateOf(0f) }
    var queueDragPx by remember { mutableFloatStateOf(0f) }
    var gestureDirection by remember { mutableIntStateOf(0) } // 0 = undecided, -1 = queue (up), +1 = collapse (down)
    var lastDragDelta by remember { mutableFloatStateOf(0f) }

    val animatedDragOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "nowPlayingDragY"
    )

    // Only notify MainActivity when the boolean drag state flips (prevents per-frame full-tree recomposition lag)
    val isDraggingDown = animatedDragOffsetY > 2f
    LaunchedEffect(isDraggingDown) {
        onCollapseDragFractionChanged(if (isDraggingDown) 1f else 0f)
    }

    DisposableEffect(Unit) {
        onDispose {
            onCollapseDragFractionChanged(0f)
        }
    }

    val topCornerRadius = if (animatedDragOffsetY > 1f) {
        ((animatedDragOffsetY / 36f).coerceIn(0.25f, 1f) * 28f).dp
    } else {
        0.dp
    }

    AppBackgroundContainer(
        bgMode = currentBgMode,
        coverArtBitmap = effectiveCoverBitmap,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = animatedDragOffsetY
                val dragFraction = (animatedDragOffsetY / screenHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                val scaleReduction = (dragFraction * 0.08f).coerceIn(0f, 0.08f)
                scaleX = 1f - scaleReduction
                scaleY = 1f - scaleReduction
                alpha = (1f - (dragFraction * 0.65f)).coerceIn(0.20f, 1f)
                shape = RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius)
                clip = animatedDragOffsetY > 1f
            }
            .background(opaqueBackdropColor)
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                // Consume all taps on empty space so they do not click-through to background views
                detectTapGestures(onTap = { /* consume tap */ })
            }
            .pointerInput(onCollapse, onOpenQueue) {
                detectVerticalDragGestures(
                    onDragStart = {
                        totalVerticalDrag = 0f
                        queueDragPx = 0f
                        dragOffsetY = 0f
                        gestureDirection = 0
                        lastDragDelta = 0f
                    },
                    onDragEnd = {
                        var didCollapse = false
                        when (gestureDirection) {
                            1 -> {
                                // Started by swiping DOWN: only collapse if still pulled down and not reversing up
                                if (dragOffsetY > 110f && lastDragDelta >= -1.5f && onCollapse != null) {
                                    didCollapse = true
                                    onQueueDragFractionChanged(0f)
                                    onCollapseDragFractionChanged(0f)
                                    onCollapse()
                                }
                            }
                            -1 -> {
                                // Started by swiping UP: only open queue if raised past 28% and not reversing down
                                val queueFraction = (queueDragPx / (screenHeightPx * 0.42f)).coerceIn(0f, 1f)
                                if (queueFraction > 0.28f && lastDragDelta <= 1.5f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onQueueDragFractionChanged(0f)
                                    onOpenQueue()
                                } else {
                                    onQueueDragFractionChanged(0f)
                                }
                            }
                            else -> {
                                onQueueDragFractionChanged(0f)
                                onCollapseDragFractionChanged(0f)
                            }
                        }
                        if (!didCollapse) {
                            dragOffsetY = 0f
                        }
                        totalVerticalDrag = 0f
                        queueDragPx = 0f
                        gestureDirection = 0
                        lastDragDelta = 0f
                    },
                    onDragCancel = {
                        dragOffsetY = 0f
                        totalVerticalDrag = 0f
                        queueDragPx = 0f
                        gestureDirection = 0
                        lastDragDelta = 0f
                        onQueueDragFractionChanged(0f)
                        onCollapseDragFractionChanged(0f)
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        totalVerticalDrag += dragAmount
                        if (kotlin.math.abs(dragAmount) > 0.5f) {
                            lastDragDelta = dragAmount
                        }
                        if (gestureDirection == 0 && kotlin.math.abs(totalVerticalDrag) > 10f) {
                            gestureDirection = if (totalVerticalDrag < 0f) -1 else 1
                        }
                        when (gestureDirection) {
                            1 -> {
                                // Locked in Collapse Mode: swipe down pulls sheet, swipe up pushes it back to 0f
                                onQueueDragFractionChanged(0f)
                                queueDragPx = 0f
                                dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                            }
                            -1 -> {
                                // Locked in Queue Mode: swipe up raises queue, swipe down lowers queue back to 0f
                                dragOffsetY = 0f
                                queueDragPx = (queueDragPx - dragAmount).coerceAtLeast(0f)
                                val fraction = (queueDragPx / (screenHeightPx * 0.42f)).coerceIn(0f, 1f)
                                onQueueDragFractionChanged(fraction)
                            }
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isLandscape) 16.dp else 24.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar with Subtle Pill Handle: Tap to Collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clickable { onCollapse?.invoke() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Subtle pill handle
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .background(
                            com.yuka.musicplayer.ui.theme.m3OnSurface().copy(alpha = if (dragOffsetY > 0f) 0.35f else 0.18f),
                            RoundedCornerShape(50)
                        )
                )
            }

        if (track == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 80.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(com.yuka.musicplayer.ui.theme.m3SubtleFill(0.08f))
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlyphMusicNote(color = dynamicColor.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No track selected",
                        color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                }
            }
            return@AppBackgroundContainer
        }

        val albumScale by animateFloatAsState(
            targetValue = if (isPlaying) 1.0f else 0.95f,
            animationSpec = spring(dampingRatio = 0.80f, stiffness = 400f),
            label = "albumArtScale"
        )

        if (isLandscape) {
            // Tablet / Landscape Responsive 2-Column Split Layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Album Art or Lyrics
                Box(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = showLyrics,
                        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                        label = "landscapeLyricsTransition"
                    ) { lyricsActive ->
                        if (lyricsActive) {
                            NowPlayingLyricsView(
                                lyrics = parsedLyrics,
                                isLoading = isLyricsLoading,
                                playbackPosition = playbackPosition,
                                isPlaying = isPlaying,
                                dynamicColor = dynamicColor,
                                onSeekTo = onSeekTo,
                                modifier = Modifier.fillMaxHeight(0.85f)
                            )
                        } else {
                            AlbumArtHero(
                                track = track,
                                albumScale = albumScale,
                                dynamicColor = dynamicColor,
                                modifier = Modifier.fillMaxHeight(0.85f)
                            )
                        }
                    }
                }

                // Right Column: Controls & Metadata
                Column(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                        .verticalScroll(scrollState)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TrackControlsSection(
                        track = track,
                        playbackPosition = playbackPosition,
                        isPlaying = isPlaying,
                        currentVolume = currentVolume,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        priorityQueueSize = priorityQueueSize,
                        isHardwareVolumeActive = isHardwareVolumeActive,
                        isHardwareVolumeSupported = isHardwareVolumeSupported,
                        isHardwareVolumeEnabled = isHardwareVolumeEnabled,
                        onToggleHardwareVolume = onToggleHardwareVolume,
                        dynamicColor = dynamicColor,
                        showLyrics = showLyrics,
                        onToggleLyrics = { showLyrics = !showLyrics },
                        onTogglePlay = onTogglePlay,
                        onVolumeChange = onVolumeChange,
                        onPlayNext = onPlayNext,
                        onPlayPrev = onPlayPrev,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        onOpenQueue = onOpenQueue,
                        onSeekTo = onSeekTo,
                        outputBitDepth = outputBitDepth,
                        outputSampleRate = outputSampleRate,
                        isDacConnected = isDacConnected,
                        isPeqActive = isPeqActive,
                        onOpenPeq = onOpenPeq
                    )
                }
            }
        } else {
            // Portrait Layout: Single clean column with balanced screen distribution (no nested scroll conflicts)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))

                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                    label = "portraitLyricsTransition"
                ) { lyricsActive ->
                    if (lyricsActive) {
                        NowPlayingLyricsView(
                            lyrics = parsedLyrics,
                            isLoading = isLyricsLoading,
                            playbackPosition = playbackPosition,
                            isPlaying = isPlaying,
                            dynamicColor = dynamicColor,
                            onSeekTo = onSeekTo,
                            modifier = Modifier.fillMaxWidth(0.92f)
                        )
                    } else {
                        AlbumArtHero(
                            track = track,
                            albumScale = albumScale,
                            dynamicColor = dynamicColor,
                            modifier = Modifier.fillMaxWidth(0.92f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                TrackControlsSection(
                    track = track,
                    playbackPosition = playbackPosition,
                    isPlaying = isPlaying,
                    currentVolume = currentVolume,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    priorityQueueSize = priorityQueueSize,
                    isHardwareVolumeActive = isHardwareVolumeActive,
                    isHardwareVolumeSupported = isHardwareVolumeSupported,
                    isHardwareVolumeEnabled = isHardwareVolumeEnabled,
                    onToggleHardwareVolume = onToggleHardwareVolume,
                    dynamicColor = dynamicColor,
                    showLyrics = showLyrics,
                    onToggleLyrics = { showLyrics = !showLyrics },
                    onTogglePlay = onTogglePlay,
                    onVolumeChange = onVolumeChange,
                    onPlayNext = onPlayNext,
                    onPlayPrev = onPlayPrev,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onOpenQueue = onOpenQueue,
                    onSeekTo = onSeekTo,
                    outputBitDepth = outputBitDepth,
                    outputSampleRate = outputSampleRate,
                    isDacConnected = isDacConnected,
                    isPeqActive = isPeqActive,
                    onOpenPeq = onOpenPeq,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
}
}


@Composable
private fun AlbumArtHero(
    track: TrackInfo,
    albumScale: Float,
    dynamicColor: Color,
    modifier: Modifier = Modifier
) {
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
            if (track.coverArt != null) {
                Image(
                    bitmap = track.coverArt.asImageBitmap(),
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

/**
 * 60 FPS smooth playback position interpolator using withFrameNanos.
 * Keeps word-by-word karaoke fills perfectly fluid without stressing background threads.
 */
@Composable
private fun rememberSmoothPlaybackPosition(
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
 * Apple Music style breathing dots (• • •) indicating instrumental intro or mid-song breaks.
 */
@Composable
private fun InterludeBreathingDots(
    dynamicColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dotsBreathing")
    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.65f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "dot1"
    )
    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.65f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(750, delayMillis = 200, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "dot2"
    )
    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.65f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(750, delayMillis = 400, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "dot3"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer { scaleX = dot1Scale; scaleY = dot1Scale }
                .background(dynamicColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer { scaleX = dot2Scale; scaleY = dot2Scale }
                .background(dynamicColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer { scaleX = dot3Scale; scaleY = dot3Scale }
                .background(dynamicColor, CircleShape)
        )
    }
}

/**
 * Word-by-word karaoke line with Apple Music style horizontal fill, micro-lift, and duet alignment.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordSyncedLyricLine(
    line: LyricLine,
    smoothPosition: Double,
    isActive: Boolean,
    dynamicColor: Color,
    onClick: () -> Unit
) {
    val alignment = when (line.voiceType) {
        VoiceType.SECONDARY -> Alignment.End
        else -> Alignment.Start
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 5.dp, horizontal = 8.dp),
        horizontalAlignment = alignment
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = when (line.voiceType) {
                VoiceType.SECONDARY -> Arrangement.End
                else -> Arrangement.Start
            },
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (word in line.words) {
                val duration = (word.endTimeSec - word.startTimeSec).coerceAtLeast(0.08)
                val wordProgress = ((smoothPosition - word.startTimeSec) / duration).toFloat().coerceIn(0f, 1f)
                val isCurrentlySinging = isActive && wordProgress > 0f && wordProgress < 1f
                val isPast = wordProgress >= 1f

                val liftY = if (isCurrentlySinging) -2f else 0f

                val textColor = when {
                    !isActive -> m3OnSurface().copy(alpha = 0.40f)
                    isPast -> Color.White
                    isCurrentlySinging -> Color.White
                    else -> Color.White.copy(alpha = 0.35f)
                }

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationY = liftY
                        }
                        .padding(end = 4.dp)
                ) {
                    if (isCurrentlySinging) {
                        Text(
                            text = word.text,
                            style = TextStyle(
                                brush = Brush.horizontalGradient(
                                    0.0f to Color.White,
                                    wordProgress to Color.White,
                                    (wordProgress + 0.06f).coerceAtMost(1f) to Color.White.copy(alpha = 0.35f),
                                    1.0f to Color.White.copy(alpha = 0.35f)
                                ),
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = ModernSansFont
                            )
                        )
                    } else {
                        Text(
                            text = word.text,
                            color = textColor,
                            fontSize = if (isActive) 21.sp else 18.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                            fontFamily = ModernSansFont
                        )
                    }
                }
            }
        }

        if (!line.backgroundText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "(${line.backgroundText})",
                color = if (isActive) dynamicColor.copy(alpha = 0.85f) else m3OnSurfaceVariant().copy(alpha = 0.40f),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ModernSansFont
            )
        }
    }
}

/**
 * Standard line-synced lyric line for songs with line-level timestamps.
 */
@Composable
private fun StandardLyricLine(
    line: LyricLine,
    isActive: Boolean,
    dynamicColor: Color,
    onClick: () -> Unit
) {
    val alignment = when (line.voiceType) {
        VoiceType.SECONDARY -> Alignment.End
        else -> Alignment.Start
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 5.dp, horizontal = 8.dp),
        horizontalAlignment = alignment
    ) {
        Text(
            text = line.text.ifEmpty { "♪" },
            color = if (isActive) Color.White else m3OnSurface().copy(alpha = 0.40f),
            fontSize = if (isActive) 21.sp else 18.sp,
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
            fontFamily = ModernSansFont,
            textAlign = when (line.voiceType) {
                VoiceType.SECONDARY -> TextAlign.End
                else -> TextAlign.Start
            }
        )

        if (!line.backgroundText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "(${line.backgroundText})",
                color = if (isActive) dynamicColor.copy(alpha = 0.85f) else m3OnSurfaceVariant().copy(alpha = 0.40f),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ModernSansFont
            )
        }
    }
}

/**
 * Apple Music style "Beauty Lyrics" view with 60 FPS interpolation, word-by-word karaoke sweep,
 * depth-of-field progressive blur, instrumental breathing dots, and vertical edge vignette mask.
 */
@Composable
private fun NowPlayingLyricsView(
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
    val cardBackground = if (currentBgMode == "BLUR") {
        if (isDark) Color.Black.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.70f)
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

                            val targetBlur = if (listState.isScrollInProgress) {
                                0.dp
                            } else {
                                when {
                                    isActive -> 0.dp
                                    distance == 1 -> 0.8.dp
                                    distance == 2 -> 2.0.dp
                                    else -> 3.5.dp
                                }
                            }

                            val safeBlur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) targetBlur else 0.dp

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
                                    .blur(safeBlur)
                            ) {
                                if (line.words.isNotEmpty()) {
                                    WordSyncedLyricLine(
                                        line = line,
                                        smoothPosition = smoothPosition,
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

@Composable
private fun TrackControlsSection(
    track: TrackInfo,
    playbackPosition: Double,
    isPlaying: Boolean,
    currentVolume: Float,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    priorityQueueSize: Int,
    isHardwareVolumeActive: Boolean,
    isHardwareVolumeSupported: Boolean = false,
    isHardwareVolumeEnabled: Boolean = false,
    onToggleHardwareVolume: ((Boolean) -> Unit)? = null,
    dynamicColor: Color,
    showLyrics: Boolean = false,
    onToggleLyrics: () -> Unit = {},
    onTogglePlay: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrev: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onSeekTo: (Double) -> Unit,
    outputBitDepth: Int = 0,
    outputSampleRate: Int = 0,
    isDacConnected: Boolean = false,
    isPeqActive: Boolean = false,
    onOpenPeq: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showVolumeModal by remember { mutableStateOf(false) }
    var showAudioSpecsModal by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val volumePulseAnim = remember { Animatable(0f) }
    val volumeScope = rememberCoroutineScope()
    val triggerVolumePulse: (Float) -> Unit = { direction ->
        volumeScope.launch {
            volumePulseAnim.snapTo(direction)
            volumePulseAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 520f)
            )
        }
    }
    val animatedVolumeFill by animateFloatAsState(
        targetValue = currentVolume.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 600f),
        label = "animatedVolumeFill"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {

        // 2. Left-Aligned Track Metadata (Bold Title with Inline Audio Info Button & Subtle Artist/Album)
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                Text(
                    text = track.title,
                    color = com.yuka.musicplayer.ui.theme.m3OnSurface(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ModernSansFont,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            delayMillis = 1500,
                            velocity = 28.dp,
                            spacing = androidx.compose.foundation.MarqueeSpacing(36.dp)
                        )
                )

                TactileBox(
                    onClick = { showAudioSpecsModal = true },
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(dynamicColor.copy(alpha = 0.16f))
                ) {
                    GlyphInfo(
                        color = dynamicColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val artistText = if (track.album.isNotEmpty() && track.album != "Unknown Album") {
                "${track.artist}  •  ${track.album}"
            } else {
                track.artist
            }

            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
            Text(
                text = artistText,
                color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                fontSize = 15.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        delayMillis = 1500,
                        velocity = 24.dp,
                        spacing = androidx.compose.foundation.MarqueeSpacing(36.dp)
                    )
            )
        }


        // 3. M3 Precision Scrubber & Timecodes (direct beneath title/artist)
        PrecisionScrubber(
            playbackPosition = playbackPosition,
            durationSeconds = track.durationSeconds,
            isPlaying = isPlaying,
            dynamicColor = dynamicColor,
            onSeekTo = onSeekTo,
            trackKey = track.file.absolutePath,
            modifier = Modifier.fillMaxWidth()
        )

        // 4. Centered Audiophile Telemetry String (Single-Line Dynamic Engine Readout with Tap-to-Cycle)
        var telemetryCycleIndex by remember { mutableIntStateOf(0) }
        val engineArchReadout = if (isHardwareVolumeActive) "32-BIT HARDWARE CHIP" else "64-BIT BIT-PERFECT DSP"
        val outKhzStr = if (outputSampleRate > 0) {
            if (outputSampleRate % 1000 == 0) "${outputSampleRate / 1000} kHz" else String.format(java.util.Locale.US, "%.1f kHz", outputSampleRate / 1000.0)
        } else null
        val inKhzStr = if (track.sampleRateHz > 0) {
            if (track.sampleRateHz % 1000 == 0) "${track.sampleRateHz / 1000} kHz" else String.format(java.util.Locale.US, "%.1f kHz", track.sampleRateHz / 1000.0)
        } else null

        val line1 = buildString {
            if (isDacConnected && outputBitDepth > 0 && outKhzStr != null) {
                val inBit = if (track.bitDepth > 0) "${track.bitDepth}-BIT / " else ""
                val inRate = inKhzStr ?: ""
                append("IN: $inBit$inRate  →  OUT: $outputBitDepth-BIT / $outKhzStr (DIRECT USB)")
            } else {
                append(track.codec.uppercase())
                if (track.bitDepth > 0) append("  •  ${track.bitDepth}-BIT")
                if (track.bitrateKbps > 0) append("  •  ${track.bitrateKbps} KBPS")
                append("  •  $engineArchReadout")
                if (track.isMqa) append("  •  MQA")
            }
        }
        val line2 = buildString {
            append(engineArchReadout)
            if (isPeqActive) {
                append("  •  PEQ: ON (10-BAND)")
            } else {
                append("  •  PEQ: OFF  •  DIRECT USB  •  BIT-PERFECT")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { telemetryCycleIndex++ }
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = if (telemetryCycleIndex % 2 == 0) line1 else line2,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "telemetryCycle"
            ) { textToDisplay ->
                Text(
                    text = textToDisplay,
                    color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                    fontFamily = TerminalFont,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.05.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 5. M3 Horizontal Transport Controls Row (Shuffle, Prev, Play/Pause Hero 68dp, Next, Repeat)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle Button
            TactileBox(
                onClick = onToggleShuffle,
                pressedScale = 0.88f,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isShuffleEnabled) dynamicColor.copy(alpha = 0.22f) else Color.Transparent)
            ) {
                GlyphShuffle(
                    color = if (isShuffleEnabled) dynamicColor else com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant().copy(alpha = 0.70f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Prev Button (permanently circular 48dp)
            TactileBox(
                onClick = onPlayPrev,
                pressedScale = 0.88f,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(m3SurfaceContainerHigh())
            ) {
                GlyphPrev(color = com.yuka.musicplayer.ui.theme.m3OnSurface(), modifier = Modifier.size(20.dp))
            }

            // Hero Play/Pause Button (permanently circular 68dp)
            TactileBox(
                onClick = onTogglePlay,
                pressedScale = 0.92f,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(dynamicColor)
            ) {
                val isDarkTheme = com.yuka.musicplayer.ui.theme.LocalIsDarkTheme.current
                val iconColor = if (!isDarkTheme || com.yuka.musicplayer.util.ColorContrastHelper.calculateLuminance(dynamicColor) <= 0.45f) {
                    Color.White
                } else {
                    Color(0xFF0E0D13)
                }
                if (isPlaying) {
                    GlyphPause(color = iconColor, modifier = Modifier.size(26.dp))
                } else {
                    Box(modifier = Modifier.padding(start = 3.dp)) {
                        GlyphPlay(color = iconColor, modifier = Modifier.size(26.dp))
                    }
                }
            }

            // Next Button (permanently circular 48dp)
            TactileBox(
                onClick = onPlayNext,
                pressedScale = 0.88f,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(m3SurfaceContainerHigh())
            ) {
                GlyphNext(color = com.yuka.musicplayer.ui.theme.m3OnSurface(), modifier = Modifier.size(20.dp))
            }

            // Repeat Button
            val isRepeatActive = repeatMode != RepeatMode.OFF
            TactileBox(
                onClick = onCycleRepeat,
                pressedScale = 0.88f,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isRepeatActive) dynamicColor.copy(alpha = 0.22f) else Color.Transparent)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (repeatMode == RepeatMode.ONE) {
                        GlyphRepeatOne(
                            color = dynamicColor,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        GlyphRepeat(
                            color = if (isRepeatActive) dynamicColor else com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant().copy(alpha = 0.70f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 6. Symmetrical Bottom Utility Action Row (Interactive Volume Fill Capsule & Queue Button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Interactive Volume Pill with dynamic fill bar
            val isBitPerfect = currentVolume >= 0.999f
            val volPercent = (currentVolume * 100).roundToInt()
            val pulseMag = kotlin.math.abs(volumePulseAnim.value)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .graphicsLayer {
                        scaleX = 1f + (0.028f * pulseMag)
                        scaleY = 1f + (0.045f * pulseMag)
                        translationX = volumePulseAnim.value * 4.dp.toPx()
                    }
                    .clip(RoundedCornerShape(25.dp))
                    .background(m3SurfaceContainer())
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showVolumeModal = true
                            }
                        )
                    }
            ) {
                // Background subtle fill tracking volume level
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedVolumeFill)
                        .fillMaxHeight()
                        .background(dynamicColor.copy(alpha = 0.16f + 0.12f * pulseMag))
                )

                // Foreground content with Steppers and Center Level
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TactileBox(
                        onClick = {
                            val newVol = ((volPercent - 1).coerceIn(0, 100)) / 100f
                            onVolumeChange(newVol)
                            triggerVolumePulse(-1f)
                        },
                        pressedScale = 0.85f,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(com.yuka.musicplayer.ui.theme.m3SubtleFill(0.08f))
                    ) {
                        GlyphMinus(color = com.yuka.musicplayer.ui.theme.m3OnSurface(), modifier = Modifier.size(16.dp))
                    }

                    TactileBox(
                        onClick = {
                            showVolumeModal = true
                        },
                        onLongClick = {
                            showVolumeModal = true
                        },
                        pressedScale = 0.92f,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (volPercent == 0 && !isBitPerfect) {
                                GlyphVolumeMute(color = com.yuka.musicplayer.ui.theme.m3OnSurface().copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                            } else {
                                GlyphVolume(color = if (isBitPerfect) dynamicColor else com.yuka.musicplayer.ui.theme.m3OnSurface(), modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBitPerfect) "0 dBFS" else "$volPercent%",
                                color = if (isBitPerfect) dynamicColor else com.yuka.musicplayer.ui.theme.m3OnSurface(),
                                fontFamily = TerminalFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    TactileBox(
                        onClick = {
                            val newVol = ((volPercent + 1).coerceIn(0, 100)) / 100f
                            onVolumeChange(newVol)
                            triggerVolumePulse(1f)
                        },
                        pressedScale = 0.85f,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(com.yuka.musicplayer.ui.theme.m3SubtleFill(0.08f))
                    ) {
                        GlyphAdd(color = com.yuka.musicplayer.ui.theme.m3OnSurface(), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Lyrics Toggle Button (50dp height aligned with Volume Pill & Queue Button)
            TactileBox(
                onClick = onToggleLyrics,
                pressedScale = 0.90f,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(
                        if (showLyrics) dynamicColor.copy(alpha = 0.25f)
                        else m3SurfaceContainer()
                    )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphLyrics(
                        color = if (showLyrics) dynamicColor else com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // PEQ Button (50dp height aligned with Volume Pill, Lyrics, and Queue)
            if (onOpenPeq != null) {
                TactileBox(
                    onClick = onOpenPeq,
                    pressedScale = 0.90f,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(
                            if (isPeqActive) dynamicColor.copy(alpha = 0.25f)
                            else m3SurfaceContainer()
                        )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphEqualizer(
                            color = if (isPeqActive) dynamicColor else com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Queue Button (50dp height aligned with Volume Pill)
            TactileBox(
                onClick = onOpenQueue,
                pressedScale = 0.90f,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(m3SurfaceContainer())
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphQueue(color = dynamicColor, modifier = Modifier.size(20.dp))
                    if (priorityQueueSize > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(dynamicColor)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "$priorityQueueSize",
                                color = Color(0xFF0E0D13),
                                fontSize = 9.sp,
                                fontFamily = TerminalFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    VolumeModalSheet(
        visible = showVolumeModal,
        currentVolume = currentVolume,
        isHardwareVolumeActive = isHardwareVolumeActive,
        isHardwareVolumeSupported = isHardwareVolumeSupported,
        isHardwareVolumeEnabled = isHardwareVolumeEnabled,
        onToggleHardwareVolume = onToggleHardwareVolume,
        onVolumeChange = onVolumeChange,
        onDismissRequest = { showVolumeModal = false }
    )

    com.yuka.musicplayer.ui.dialogs.AudioSpecsAndLogsModalSheet(
        visible = showAudioSpecsModal,
        track = track,
        isHardwareVolumeActive = isHardwareVolumeActive,
        onDismissRequest = { showAudioSpecsModal = false }
    )
}


