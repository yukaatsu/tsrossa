package com.yuka.musicplayer.ui.theme

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tracks the number of open modals/sheets across the app.
 * When > 0 and LocalSurfaceStyle is "BLUR_OPACITY", the main screen content automatically
 * applies a GPU-accelerated backdrop blur.
 */
val LocalModalBlurCount = compositionLocalOf<MutableIntState> { mutableIntStateOf(0) }

/**
 * Surface style local: "BLUR_OPACITY" (translucent glass + blur) or "SOLID" (opaque surfaces).
 */
val LocalSurfaceStyle = compositionLocalOf { "BLUR_OPACITY" }

/**
 * Automatically registers and unregisters a modal bottom sheet with the global backdrop blur counter.
 */
@Composable
fun RegisterModalBlur(visible: Boolean) {
    val modalBlurCount = LocalModalBlurCount.current
    DisposableEffect(visible) {
        if (visible) {
            modalBlurCount.intValue++
        }
        onDispose {
            if (visible && modalBlurCount.intValue > 0) {
                modalBlurCount.intValue--
            }
        }
    }
}

/**
 * Isolates modal backdrop blur state reads and animates GPU RenderEffect on the RenderThread
 * via graphicsLayer so the root composable tree never recomposes during blur transitions.
 */
@Composable
fun ModalBlurContainer(
    modifier: Modifier = Modifier,
    extraBlurActive: Boolean = false,
    maxBlurRadius: Dp = 3.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isBlurred = LocalModalBlurCount.current.intValue > 0 || extraBlurActive
    val maxBlurPx = with(LocalDensity.current) { maxBlurRadius.toPx() }
    val animatedBlurPx by animateFloatAsState(
        targetValue = if (isBlurred) maxBlurPx else 0f,
        animationSpec = tween(
            durationMillis = if (isBlurred) 180 else 150,
            easing = FastOutSlowInEasing
        ),
        label = "smoothModalBackdropBlurPx"
    )

    Box(
        modifier = modifier.graphicsLayer {
            val radiusPx = animatedBlurPx
            renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radiusPx > 0.5f) {
                BlurEffect(
                    radiusX = radiusPx,
                    radiusY = radiusPx,
                    edgeTreatment = TileMode.Clamp
                )
            } else {
                null
            }
        },
        content = content
    )
}

/**
 * Applies animated background blur to the root content whenever any modal is presented.
 */
@Composable
fun Modifier.modalBackdropBlur(): Modifier {
    val modalCount = LocalModalBlurCount.current.intValue
    val surfaceStyle = LocalSurfaceStyle.current
    val targetBlur = if (modalCount > 0 && surfaceStyle == "BLUR_OPACITY") 3.dp else 0.dp
    val animatedBlur by animateDpAsState(
        targetValue = targetBlur,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "ModalBackdropBlur"
    )
    return if (animatedBlur > 0.5.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.blur(animatedBlur)
    } else {
        this
    }
}

/**
 * Enhanced glassmorphic container with specular highlights, rounded shape, and optional backdrop blur.
 */
fun Modifier.glassMorphicContainer(
    backgroundColor: Color,
    borderColor: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 0.dp,
    enableHighlight: Boolean = true,
    isDarkTheme: Boolean = true
): Modifier = this
    .clip(shape)
    .background(backgroundColor, shape)
    .then(
        if (enableHighlight) {
            Modifier.drawWithContent {
                drawContent()
                // Top-to-bottom subtle glass specular sheen
                val highlightColor = if (isDarkTheme) {
                    Color.White.copy(alpha = 0.08f)
                } else {
                    Color.White.copy(alpha = 0.22f)
                }
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to highlightColor,
                        0.35f to Color.Transparent
                    )
                )
            }
        } else {
            Modifier
        }
    )
    .then(
        if (borderColor != Color.Unspecified && borderWidth > 0.dp) {
            Modifier.border(borderWidth, borderColor, shape)
        } else {
            Modifier
        }
    )
