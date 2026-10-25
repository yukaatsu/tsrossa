package com.yuka.musicplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.RegisterModalBlur
import com.yuka.musicplayer.ui.theme.glassMorphic
import com.yuka.musicplayer.ui.theme.m3SurfaceBase
import kotlinx.coroutines.launch

/**
 * Empty no-op per UX specification: modals have clean edge-to-edge aesthetics without redundant '✕' close buttons.
 */
@Composable
fun ModalCloseButton(
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Intentionally empty per notes.md specification
}

/**
 * Authentic Material 3 + Glassmorphic Modal Bottom Sheet Container:
 * - 100% Full-width edge-to-edge coverage (fillMaxWidth).
 * - Borderless smooth glassmorphic surface (borderWidth = 0.dp).
 * - Automatic global backdrop blur registration (RegisterModalBlur).
 * - Lightweight graphicsLayer GPU animations (zero remeasure per frame, smooth on low-end phones).
 * - Soft keyboard (IME) responsive via imePadding.
 * - Nested scroll connection: pull-down when scrolled to top boundary drags the sheet down and dismisses.
 */
@Composable
fun CyberBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = 1.0f,
    maxWidth: Dp = Dp.Unspecified,
    wrapHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var isMounted by remember { mutableStateOf(visible) }
    var isDismissing by remember { mutableStateOf(false) }
    // ALWAYS initialize to 0f so even sheets that mount with visible=true smoothly fly in!
    val progress = remember { Animatable(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(visible) {
        if (visible) {
            isDismissing = false
            isMounted = true
            dragOffsetY = 0f
            if (progress.value < 0.01f) {
                progress.snapTo(0f)
            }
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 380f
                )
            )
        } else {
            isDismissing = true
            if (progress.value > 0.01f) {
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
                )
            }
            isMounted = false
            dragOffsetY = 0f
        }
    }

    if (isMounted) {
        // Unregister blur the exact instant dismissal starts so backdrop unblurs smoothly in parallel
        RegisterModalBlur(visible = visible && !isDismissing)

        val sheetSurface = m3SurfaceBase().copy(alpha = 1f)
        val accent = LocalAccentColor.current
        var isFullscreen by remember(wrapHeight) { mutableStateOf(!wrapHeight) }

        val animatedDragOffset by animateFloatAsState(
            targetValue = dragOffsetY,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
            label = "sheetDragOffset"
        )

        val triggerDismiss = {
            if (!isDismissing) {
                isDismissing = true
                coroutineScope.launch {
                    progress.animateTo(0f, tween(durationMillis = 160, easing = FastOutSlowInEasing))
                    onDismissRequest()
                }
            }
        }

        val density = androidx.compose.ui.platform.LocalDensity.current
        val minDismissDragPx = with(density) { 135.dp.toPx() }
        val minFlingDismissDragPx = with(density) { 90.dp.toPx() }
        var contentScrolledInCurrentGesture by remember { mutableStateOf(false) }
        var lastContentScrollTimeMs by remember { mutableLongStateOf(0L) }

        // NestedScrollConnection: stops cleanly at top boundary ("mentok atas dulu") on rapid scrolls/flings
        val nestedScrollConnection = remember(minDismissDragPx, minFlingDismissDragPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.Drag && dragOffsetY > 0f && available.y < 0f) {
                        val delta = available.y.coerceAtLeast(-dragOffsetY)
                        dragOffsetY += delta
                        return Offset(0f, delta)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    val now = android.os.SystemClock.uptimeMillis()
                    if (kotlin.math.abs(consumed.y) > 0.5f) {
                        // Inner list was scrolling (via drag or fling) -> lock sheet at top boundary!
                        contentScrolledInCurrentGesture = true
                        lastContentScrollTimeMs = now
                    }
                    if (source == NestedScrollSource.Drag && available.y > 0f) {
                        val cooledDownSinceTopHit = (now - lastContentScrollTimeMs) > 220L
                        if (!contentScrolledInCurrentGesture && cooledDownSinceTopHit) {
                            dragOffsetY = (dragOffsetY + available.y * 0.65f).coerceAtLeast(0f)
                            return Offset(0f, available.y)
                        } else {
                            // Stop cleanly at the top boundary ("mentok atas dulu")
                            dragOffsetY = 0f
                            return Offset(0f, available.y)
                        }
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    val now = android.os.SystemClock.uptimeMillis()
                    val cooledDownSinceTopHit = (now - lastContentScrollTimeMs) > 220L
                    if (!contentScrolledInCurrentGesture && cooledDownSinceTopHit &&
                        (dragOffsetY > minDismissDragPx || (dragOffsetY > minFlingDismissDragPx && available.y > 2400f))
                    ) {
                        triggerDismiss()
                        return available
                    } else if (dragOffsetY > 0f) {
                        dragOffsetY = 0f
                        return available
                    }
                    return Velocity.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (kotlin.math.abs(consumed.y) > 1f) {
                        lastContentScrollTimeMs = android.os.SystemClock.uptimeMillis()
                    }
                    contentScrolledInCurrentGesture = false
                    return Velocity.Zero
                }
            }
        }

        Popup(
            alignment = Alignment.BottomCenter,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true, clippingEnabled = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.40f * progress.value.coerceIn(0f, 1f)))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { triggerDismiss() },
                contentAlignment = Alignment.BottomCenter
            ) {
                val boxMod = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .imePadding()
                    .then(
                        if (wrapHeight && !isFullscreen) {
                            Modifier.wrapContentHeight(Alignment.Bottom)
                        } else {
                            Modifier.fillMaxHeight(if (isFullscreen) 0.90f else maxHeightFraction.coerceAtMost(0.90f))
                        }
                    )
                    .graphicsLayer {
                        val p = progress.value.coerceIn(0f, 1.05f)
                        val sheetHeightPx = size.height.takeIf { it > 0f } ?: 1200f
                        val slideOffset = (1f - p) * sheetHeightPx
                        translationY = (slideOffset + animatedDragOffset).coerceAtLeast(0f)
                        val scale = 0.94f + (0.06f * p.coerceIn(0f, 1f))
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1.0f)
                        alpha = (p * 1.25f).coerceIn(0f, 1f)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {} // consume touch inside sheet
                    .glassMorphic(
                        backgroundColor = sheetSurface,
                        borderColor = Color.Unspecified,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        borderWidth = 0.dp
                    )
                    .nestedScroll(nestedScrollConnection)

                Box(modifier = boxMod) {
                    Column(
                        modifier = modifier
                            .fillMaxWidth()
                            .then(if (wrapHeight && !isFullscreen) Modifier.wrapContentHeight() else Modifier.fillMaxHeight())
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        // Interactive Drag Handle Pill with real-time finger tracking
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .fillMaxWidth()
                                .height(32.dp)
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragStart = { },
                                        onDragEnd = {
                                            if (dragOffsetY > 110f) {
                                                triggerDismiss()
                                            } else if (dragOffsetY < -40f && !isFullscreen) {
                                                isFullscreen = true
                                                dragOffsetY = 0f
                                            } else {
                                                dragOffsetY = 0f
                                            }
                                        },
                                        onDragCancel = { dragOffsetY = 0f },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                                        }
                                    )
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    isFullscreen = !isFullscreen
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 38.dp, height = 4.5.dp)
                                    .background(accent.copy(alpha = 0.45f), RoundedCornerShape(50))
                            )
                        }

                        content()
                    }
                }
            }
        }
    }
}

