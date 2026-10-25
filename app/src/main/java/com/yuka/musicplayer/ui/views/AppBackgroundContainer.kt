package com.yuka.musicplayer.ui.views

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.LocalIsDarkTheme
import com.yuka.musicplayer.ui.theme.M3LightSurface
import com.yuka.musicplayer.ui.theme.M3LightSurfaceContainerLowest
import com.yuka.musicplayer.ui.theme.M3Surface
import com.yuka.musicplayer.ui.theme.M3SurfaceContainerLowest

@Composable
fun AppBackgroundContainer(
    bgMode: String,
    wallpaperBitmap: ImageBitmap? = null,
    coverArtBitmap: ImageBitmap? = null,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val accent = LocalAccentColor.current
    val isDark = LocalIsDarkTheme.current

    val baseSurface by animateColorAsState(
        targetValue = if (isDark) M3Surface else M3LightSurface,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "bgBaseSurface"
    )
    val baseLowest by animateColorAsState(
        targetValue = if (isDark) M3SurfaceContainerLowest else M3LightSurfaceContainerLowest,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "bgBaseLowest"
    )
    val oledOrLightColor by animateColorAsState(
        targetValue = if (isDark) Color.Black else M3LightSurface,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "bgOledOrLight"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Crossfade(
            targetState = Pair(bgMode, coverArtBitmap),
            animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
            label = "appBgCanvasCrossfade"
        ) { (mode, artworkBitmap) ->
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    (mode == "ARTWORK" || mode == "BLUR") && artworkBitmap != null -> {
                        // 1. Subtle, lightweight Pre-Blurred Album Artwork Layer (0 GPU shader overhead, 60fps)
                        Image(
                            bitmap = artworkBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.tint(
                                color = baseSurface.copy(alpha = 0.30f),
                                blendMode = BlendMode.SrcAtop
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = 1.08f
                                    scaleY = 1.08f
                                }
                        )

                        // 2. Dominant Tonal M3 Scrim Overlay (~0.85 opacity) for crisp text readability
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithCache {
                                    val tonalOverlayGradient = Brush.verticalGradient(
                                        colors = listOf(
                                            baseLowest.copy(alpha = 0.86f),
                                            baseSurface.copy(alpha = 0.84f),
                                            baseLowest.copy(alpha = 0.88f)
                                        )
                                    )
                                    val tonalAccentGlow = Brush.radialGradient(
                                        colors = listOf(
                                            accent.copy(alpha = if (isDark) 0.09f else 0.11f),
                                            Color.Transparent
                                        ),
                                        center = Offset(size.width * 0.5f, size.height * 0.25f),
                                        radius = size.width * 0.95f
                                    )
                                    onDrawBehind {
                                        drawRect(tonalOverlayGradient)
                                        drawRect(tonalAccentGlow)
                                    }
                                }
                        )
                    }
                    mode == "SIGNATURE" || mode == "ARTWORK" || mode == "BLUR" -> {
                        // Tonal M3 background (also used as fallback when Album Artwork is selected but no track cover art exists)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithCache {
                                    val radialGlow = Brush.radialGradient(
                                        colors = listOf(accent.copy(alpha = if (isDark) 0.07f else 0.09f), Color.Transparent),
                                        center = Offset(size.width * 0.5f, size.height * 0.22f),
                                        radius = size.width * 0.95f
                                    )
                                    val vertGradient = Brush.verticalGradient(
                                        colors = listOf(baseLowest, baseSurface, baseLowest)
                                    )
                                    onDrawBehind {
                                        drawRect(vertGradient)
                                        drawRect(radialGlow)
                                    }
                                }
                        )
                    }
                    else -> {
                        // OLED / Pure Light surface
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithCache {
                                    val radialGlow = Brush.radialGradient(
                                        colors = listOf(accent.copy(alpha = if (isDark) 0.05f else 0.07f), Color.Transparent),
                                        center = Offset(size.width * 0.5f, size.height * 0.22f),
                                        radius = size.width * 0.90f
                                    )
                                    onDrawBehind {
                                        drawRect(oledOrLightColor)
                                        drawRect(radialGlow)
                                    }
                                }
                        )
                    }
                }
            }
        }

        content()
    }
}
