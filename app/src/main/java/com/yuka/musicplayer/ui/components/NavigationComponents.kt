package com.yuka.musicplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.model.ViewState
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.LocalIsDarkTheme
import com.yuka.musicplayer.ui.theme.M3SurfaceContainer
import com.yuka.musicplayer.ui.theme.M3SurfaceContainerHigh
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.TerminalWhite
import com.yuka.musicplayer.ui.theme.glassMorphic
import com.yuka.musicplayer.ui.theme.m3GlassBorder
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.resolveButtonGlassColor

/**
 * Clean Material 3 Top Status & Utility Bar.
 * - Tap brand ("tsrossa") navigates to Settings -> About.
 * - Center responsive Glass Pill indicator displaying DAC product name (with walking marquee) or DISCONNECTED.
 *   - Long-press on DAC pill triggers Warm Up DAC confirmation modal.
 * - Right: Action cluster with Settings (Gear), Diagnostics, and Update (Warmup bolt moved to Pill long-press).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppHeader(
    viewState: ViewState,
    isHardwareVolumeActive: Boolean,
    isForceSoftwareVolume: Boolean,
    isHardwareVolumeLockedBySystem: Boolean,
    onToggleLogs: () -> Unit,
    onToggleUpdate: () -> Unit,
    hasUpdateAvailable: Boolean = false,
    onAboutClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    connectedDacName: String? = null,
    isDacConnected: Boolean = false,
    onDacPillLongClick: () -> Unit = {},
    onToggleLockTask: () -> Unit = {},
    isTaskLocked: Boolean = false
) {
    val accent = LocalAccentColor.current
    val isDark = LocalIsDarkTheme.current
    val statusColor = when {
        isHardwareVolumeLockedBySystem -> Color(0xFFFF9100)
        isForceSoftwareVolume -> Color(0xFFFF5252)
        isHardwareVolumeActive -> Color(0xFFFFD700)
        else -> accent
    }

    val dacDisplayText = if (isDacConnected && !connectedDacName.isNullOrBlank()) {
        connectedDacName
    } else {
        "DISCONNECTED"
    }
    val dacActiveColor = if (isDacConnected) {
        if (isHardwareVolumeActive) Color(0xFFFFD700) else accent
    } else {
        TerminalGray
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Title ("tsrossa" -> About) + Centered Responsive DAC Glass Pill
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TactileBox(
                    onClick = onAboutClick,
                    pressedScale = 0.94f
                ) {
                    Text(
                        text = "tsrossa",
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        letterSpacing = (-0.3).sp,
                        color = m3OnSurface()
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Centered Responsive Glass DAC Pill with Marquee & Long-Press Warm Up
                TactileBox(
                    onClick = onToggleLogs,
                    onLongClick = onDacPillLongClick,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .height(30.dp)
                        .glassMorphic(
                            backgroundColor = com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh().copy(alpha = 0.85f),
                            borderColor = m3GlassBorder(),
                            shape = RoundedCornerShape(50),
                            borderWidth = 0.8.dp
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.5.dp)
                                .background(dacActiveColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = dacDisplayText,
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = if (isDacConnected) m3OnSurface() else TerminalGray,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: M3 Glassmorphic Action Cluster (Settings far right, Update left of it)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // UPDATES (always visible directly to the left of Settings)
                val updateTransition = rememberInfiniteTransition(label = "updateIconAnim")
                val pulseScale by updateTransition.animateFloat(
                    initialValue = 0.94f,
                    targetValue = 1.12f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "updatePulseScale"
                )
                val arrowBobOffsetY by updateTransition.animateFloat(
                    initialValue = -2.2f,
                    targetValue = 2.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "updateBobOffset"
                )
                val glowAlpha by updateTransition.animateFloat(
                    initialValue = 0.16f,
                    targetValue = 0.38f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "updateGlowAlpha"
                )

                TactileBox(
                    onClick = onToggleUpdate,
                    pressedScale = 0.90f,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .then(
                            if (hasUpdateAvailable) {
                                Modifier
                                    .border(1.dp, Color(0xFF00E676).copy(alpha = glowAlpha * 1.5f), CircleShape)
                                    .background(Color(0xFF00E676).copy(alpha = glowAlpha))
                            } else {
                                Modifier.background(accent.copy(alpha = 0.12f))
                            }
                        )
                ) {
                    Box(
                        modifier = Modifier.graphicsLayer {
                            if (hasUpdateAvailable) {
                                translationY = arrowBobOffsetY
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphUpdate(
                            color = if (hasUpdateAvailable) Color(0xFF00E676) else accent,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // SETTINGS (positioned at the far right)
                TactileBox(
                    onClick = onSettingsClick,
                    pressedScale = 0.90f,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f))
                ) {
                    GlyphSettings(
                        color = accent,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

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
    bgMode: String = "SIGNATURE",
    onTogglePlay: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    onStopAndDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dynamicColor = LocalAccentColor.current
    val isDark = LocalIsDarkTheme.current
    val progress = if (durationSeconds > 0.0) (playbackPosition / durationSeconds).toFloat().coerceIn(0f, 1f) else 0f
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
                        if (dragOffsetY < -45f) {
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

            if (track.coverArt != null) {
                Image(
                    bitmap = track.coverArt.asImageBitmap(),
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
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress)
                    .height(2.dp)
                    .background(dynamicColor)
            )
        }
    }
}

/**
 * Authentic Material 3 Glassmorphic NavigationBar:
 * - Contains Library and Playlist (Settings moved to Top App Banner).
 * - Full glassmorphism (opacity = 0.85f + accent tint).
 * - Long-press hold reveals floating tooltip with menu name.
 */
@Composable
fun BottomNavigationBar(
    currentView: ViewState,
    bgMode: String = "SIGNATURE",
    onNavClick: (ViewState) -> Unit
) {
    val items = listOf(
        ViewState.LIBRARY to "Library",
        ViewState.PLAYLIST to "Playlist"
    )

    val accent = LocalAccentColor.current
    val isDark = LocalIsDarkTheme.current
    val navBarBg = com.yuka.musicplayer.ui.theme.m3SurfaceContainer().copy(alpha = 1f)

    var heldItem by remember { mutableStateOf<ViewState?>(null) }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .glassMorphic(
                backgroundColor = navBarBg,
                borderColor = Color.Unspecified,
                shape = RoundedCornerShape(26.dp),
                borderWidth = 0.dp
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (view, label) ->
                val isSelected = currentView == view

                val tabScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.06f else 0.94f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
                    label = "tabScale"
                )
                val iconBounceY by animateFloatAsState(
                    targetValue = if (isSelected) -2f else 0f,
                    animationSpec = spring(dampingRatio = 0.50f, stiffness = 500f),
                    label = "iconBounceY"
                )
                val pillWidthFraction by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.85f,
                    animationSpec = spring(dampingRatio = 0.60f, stiffness = 480f),
                    label = "pillWidthFraction"
                )
                val pillBgColor by animateColorAsState(
                    targetValue = if (isSelected) com.yuka.musicplayer.ui.theme.m3ActivePillContainer() else Color.Transparent,
                    animationSpec = tween(durationMillis = 220),
                    label = "pillBgColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Floating Tooltip Popup directly centered above the held tab item
                    if (heldItem == view) {
                        androidx.compose.ui.window.Popup(
                            alignment = Alignment.TopCenter,
                            offset = androidx.compose.ui.unit.IntOffset(0, with(LocalDensity.current) { (-44).dp.roundToPx() }),
                            onDismissRequest = { heldItem = null }
                        ) {
                            Box(
                                modifier = Modifier
                                    .glassMorphic(
                                        backgroundColor = accent,
                                        shape = RoundedCornerShape(50),
                                        borderWidth = 0.dp
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = ModernSansFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isDark) Color.Black else Color.White
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(view) {
                                detectTapGestures(
                                    onLongPress = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        heldItem = view
                                    },
                                    onTap = {
                                        heldItem = null
                                        onNavClick(view)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(60.dp * pillWidthFraction)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(pillBgColor)
                                    .graphicsLayer {
                                        scaleX = tabScale
                                        scaleY = tabScale
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val iconColor = if (isSelected) accent else m3OnSurfaceVariant()
                                Box(modifier = Modifier.graphicsLayer { translationY = iconBounceY }) {
                                    when (view) {
                                        ViewState.LIBRARY -> GlyphLibrary(color = iconColor, modifier = Modifier.size(20.dp))
                                        ViewState.PLAYLIST -> GlyphPlaylists(color = iconColor, modifier = Modifier.size(20.dp))
                                        else -> GlyphNowPlaying(color = iconColor, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = label,
                                color = if (isSelected) accent else m3OnSurfaceVariant(),
                                fontSize = 11.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
