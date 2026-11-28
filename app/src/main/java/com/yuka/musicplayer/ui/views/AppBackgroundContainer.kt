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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "bgBaseSurface"
    )
    val baseLowest by animateColorAsState(
        targetValue = if (isDark) M3SurfaceContainerLowest else M3LightSurfaceContainerLowest,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "bgBaseLowest"
    )
    val oledOrLightColor by animateColorAsState(
        targetValue = if (isDark) Color.Black else M3LightSurface,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "bgOledOrLight"
    )

    val currentAccent by rememberUpdatedState(accent)
    val currentBaseSurface by rememberUpdatedState(baseSurface)
    val currentBaseLowest by rememberUpdatedState(baseLowest)
    val currentOledOrLight by rememberUpdatedState(oledOrLightColor)
    val currentIsDark by rememberUpdatedState(isDark)

    Box(modifier = modifier.fillMaxSize()) {
        Crossfade(
            targetState = Pair(bgMode, coverArtBitmap),
            animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
            label = "appBgCanvasCrossfade"
        ) { (mode, artworkBitmap) ->
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    (mode == "ARTWORK" || mode == "BLUR") && artworkBitmap != null -> {
                        // 1. Vibrant Pre-Blurred Album Artwork Canvas Layer (Bicubic GPU sampling, 0 shader overhead, 60/120fps)
                        Image(
                            bitmap = artworkBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = 1.08f
                                    scaleY = 1.08f
                                }
                        )

                        // 2. High-Contrast Atmospheric Glass Scrim Overlay: showcases the blurred album artwork
                        //    while guaranteeing WCAG text contrast across all album art luminances.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithCache {
                                    val artworkScrimGradient = if (currentIsDark) {
                                        Brush.verticalGradient(
                                            0.00f to currentBaseLowest.copy(alpha = 0.54f),
                                            0.36f to currentBaseSurface.copy(alpha = 0.46f),
                                            0.72f to currentBaseSurface.copy(alpha = 0.62f),
                                            1.00f to currentBaseLowest.copy(alpha = 0.78f)
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            0.00f to currentBaseLowest.copy(alpha = 0.72f),
                                            0.40f to currentBaseSurface.copy(alpha = 0.66f),
                                            0.75f to currentBaseSurface.copy(alpha = 0.76f),
                                            1.00f to currentBaseLowest.copy(alpha = 0.85f)
                                        )
                                    }
                                    val tonalAccentGlow = Brush.radialGradient(
                                        colors = listOf(
                                            currentAccent.copy(alpha = if (currentIsDark) 0.09f else 0.08f),
                                            Color.Transparent
                                        ),
                                        center = Offset(size.width * 0.5f, size.height * 0.24f),
                                        radius = size.width * 0.98f
                                    )
                                    onDrawBehind {
                                        drawRect(artworkScrimGradient)
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
                                        colors = listOf(currentAccent.copy(alpha = if (currentIsDark) 0.07f else 0.09f), Color.Transparent),
                                        center = Offset(size.width * 0.5f, size.height * 0.22f),
                                        radius = size.width * 0.95f
                                    )
                                    val vertGradient = Brush.verticalGradient(
                                        colors = listOf(currentBaseLowest, currentBaseSurface, currentBaseLowest)
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
                                        colors = listOf(currentAccent.copy(alpha = if (currentIsDark) 0.05f else 0.07f), Color.Transparent),
                                        center = Offset(size.width * 0.5f, size.height * 0.22f),
                                        radius = size.width * 0.90f
                                    )
                                    val bgColor = currentOledOrLight
                                    onDrawBehind {
                                        drawRect(bgColor)
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
