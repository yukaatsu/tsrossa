package com.yuka.musicplayer.ui.views

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.RepeatMode
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.ui.components.*
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.LocalBgMode
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.util.LyricsHelper
import com.yuka.musicplayer.util.ParsedLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Radical Material 3 (Material You) Now Playing Screen.
 */
@Composable
fun TrackView(
    track: TrackInfo?,
    playbackPosition: Double = 0.0,
    positionProvider: () -> Double = { playbackPosition },
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
    isCompressorActive: Boolean = false,
    isBitPerfectDirectPass: Boolean = false,
    isSampleRateUnverified: Boolean = false,
    isDeviceWedged: Boolean = false,
    isHardwareVolumeLockedBySystem: Boolean = false,
    onOpenSignalPath: () -> Unit = {},
    onOpenVolumeModal: (() -> Unit)? = null,
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
    val opaqueBackdropColor = com.yuka.musicplayer.ui.theme.m3SurfaceBase().copy(alpha = 1f)

    var lastValidCoverBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val activeTrackPath = track?.file?.absolutePath ?: ""
    var asyncTrackBackdrop by remember(activeTrackPath) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val trackCoverBitmap = remember(activeTrackPath, track?.blurredCoverArt, asyncTrackBackdrop) {
        com.yuka.musicplayer.util.AlbumArtThumbnailCache.getBlurredBackdropImageBitmap(activeTrackPath)
            ?: track?.blurredCoverArt?.asImageBitmap()
            ?: asyncTrackBackdrop
    }
    LaunchedEffect(activeTrackPath, trackCoverBitmap) {
        if (trackCoverBitmap != null) {
            lastValidCoverBitmap = trackCoverBitmap
        } else if (activeTrackPath.isNotEmpty()) {
            val prepared = com.yuka.musicplayer.util.AlbumArtThumbnailCache.prepareTrackVisuals(activeTrackPath)
            if (prepared != null) {
                asyncTrackBackdrop = prepared
                lastValidCoverBitmap = prepared
            } else if (com.yuka.musicplayer.util.AlbumArtThumbnailCache.isKnownMissingArt(activeTrackPath)) {
                lastValidCoverBitmap = null
            }
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
            if (!showLyrics) {
                // Defer disk lyrics parsing by 280ms so slide-up entrance animation completes without disk I/O interference
                delay(280L)
            }
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
                        val dragThreshold = with(density) { 14.dp.toPx() }
                        if (gestureDirection == 0 && kotlin.math.abs(totalVerticalDrag) > dragThreshold) {
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
            TactileBox(
                onClick = { onCollapse?.invoke() },
                pressedScale = 0.92f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
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
                        positionProvider = positionProvider,
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
                        isCompressorActive = isCompressorActive,
                        isBitPerfectDirectPass = isBitPerfectDirectPass,
                        isSampleRateUnverified = isSampleRateUnverified,
                        isDeviceWedged = isDeviceWedged,
                        isHardwareVolumeLockedBySystem = isHardwareVolumeLockedBySystem,
                        onOpenSignalPath = onOpenSignalPath,
                        onOpenVolumeModal = onOpenVolumeModal,
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
                            playbackPosition = positionProvider(),
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
                    positionProvider = positionProvider,
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
                    isCompressorActive = isCompressorActive,
                    isBitPerfectDirectPass = isBitPerfectDirectPass,
                    isSampleRateUnverified = isSampleRateUnverified,
                    isDeviceWedged = isDeviceWedged,
                    isHardwareVolumeLockedBySystem = isHardwareVolumeLockedBySystem,
                    onOpenSignalPath = onOpenSignalPath,
                    onOpenVolumeModal = onOpenVolumeModal,
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
