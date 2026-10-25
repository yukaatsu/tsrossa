package com.yuka.musicplayer.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.R

val LocalAccentColor = compositionLocalOf { Color(0xFF00FF00) }
val LocalBgMode = compositionLocalOf { "SIGNATURE" }
val LocalIsDarkTheme = compositionLocalOf { true }
val TerminalFont = FontFamily(Font(R.font.fantasquesans_regular))
val ModernSansFont = FontFamily.SansSerif

fun Color.blendWithWhite(ratio: Float): Color {
    return Color(
        red = this.red + (1f - this.red) * ratio,
        green = this.green + (1f - this.green) * ratio,
        blue = this.blue + (1f - this.blue) * ratio,
        alpha = this.alpha
    )
}

// ==========================================
// MATERIAL 3 (MATERIAL YOU) TONAL ELEVATIONS
// ==========================================

val TrueBlack = Color(0xFF000000)
val TerminalGreen = Color(0xFF00FF00)
val TerminalWhite = Color(0xFFF5F5F5)
val TerminalGray = Color(0xFF8E8E93)

// Base M3 Dark Surface Scale
val M3Surface = Color(0xFF0E0D13)
val M3SurfaceContainerLowest = Color(0xFF09080C)
val M3SurfaceContainerLow = Color(0xFF14121A)
val M3SurfaceContainer = Color(0xFF1B1822)
val M3SurfaceContainerHigh = Color(0xFF24212D)
val M3SurfaceContainerHighest = Color(0xFF2F2B3B)

// Base M3 Light Surface Scale
val M3LightSurface = Color(0xFFF8F8FC)
val M3LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val M3LightSurfaceContainerLow = Color(0xFFF1F1F6)
val M3LightSurfaceContainer = Color(0xFFE9E9F0)
val M3LightSurfaceContainerHigh = Color(0xFFE1E1E9)
val M3LightSurfaceContainerHighest = Color(0xFFD8D8E2)

// Preserved aliases for backwards compatibility with existing views
val SignatureDeepNavy = Color(0xFF0E0D13)
val SignatureSurfaceNavy = Color(0xFF1B1822)
val SurfaceContainerLow = M3SurfaceContainerLow
val SurfaceContainerHigh = M3SurfaceContainerHigh
val TrueBlackContainerLow = Color(0xFF0A0A0C)
val TrueBlackContainerHigh = Color(0xFF141418)

// Hairline border tokens (soft tonal borders for backwards-compatible callers)
val CardBorderSubtle = Color(0x00FFFFFF) // 100% borderless in M3 by default
val BadgeBorderSubtle = Color(0x14FFFFFF)

// Refined Palette Tokens
val SakuraPink = Color(0xFFFF85A1)
val VividViolet = Color(0xFFA259FF)
val SubtleMint = Color(0xFF64FFDA)
val PastelPurple = Color(0xFFB388FF)
val AudiophileAmber = Color(0xFFFFB300)
val AudiophileCyan = Color(0xFF00E5FF)

/**
 * Tonal container color resolver:
 * Calculates a smooth Material 3 tonal elevation with Glassmorphism (0.85f alpha default)
 * by blending [LocalAccentColor] into the active base surface at a specified tone ratio (8% - 18%).
 */
fun resolveM3TonalSurface(
    baseSurface: Color,
    accent: Color,
    tintRatio: Float = 0.10f,
    alpha: Float = 0.85f
): Color {
    return Color(
        red = (baseSurface.red * (1f - tintRatio) + accent.red * tintRatio).coerceIn(0f, 1f),
        green = (baseSurface.green * (1f - tintRatio) + accent.green * tintRatio).coerceIn(0f, 1f),
        blue = (baseSurface.blue * (1f - tintRatio) + accent.blue * tintRatio).coerceIn(0f, 1f),
        alpha = alpha
    )
}

/**
 * Glassmorphic button color resolver for tactile UI buttons with 0.85f alpha
 * and subtle accent color tint.
 */
fun resolveButtonGlassColor(
    accent: Color,
    isDark: Boolean,
    isSelected: Boolean = false
): Color {
    val base = if (isDark) Color(0xFF1C1C22) else Color(0xFFE8E8EE)
    val tintRatio = if (isSelected) 0.32f else 0.10f
    return resolveM3TonalSurface(base, accent, tintRatio = tintRatio, alpha = 0.85f)
}

/**
 * Convenience helper to get tonal container for a given bgMode.
 */
fun getM3ContainerColor(
    bgMode: String,
    accent: Color,
    level: Int = 1 // 0=lowest, 1=container, 2=high, 3=highest
): Color {
    val base = when (bgMode) {
        "BLACK" -> when (level) {
            0 -> Color(0xFF050505)
            1 -> Color(0xFF0E0E12)
            2 -> Color(0xFF16161C)
            else -> Color(0xFF202028)
        }
        else -> when (level) {
            0 -> M3SurfaceContainerLowest
            1 -> M3SurfaceContainer
            2 -> M3SurfaceContainerHigh
            else -> M3SurfaceContainerHighest
        }
    }
    val tint = when (level) {
        0 -> 0.05f
        1 -> 0.09f
        2 -> 0.14f
        else -> 0.18f
    }
    return resolveM3TonalSurface(base, accent, tint, alpha = 0.85f)
}

data class AnimatedM3Palette(
    val surfaceBase: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color
)

val LocalAnimatedM3Palette = compositionLocalOf<AnimatedM3Palette?> { null }

fun computeRawM3SurfaceBase(bgMode: String, accent: Color, isDark: Boolean, style: String): Color {
    val alpha = if (style == "SOLID") 1.0f else 0.65f
    return if (isDark) {
        if (bgMode == "BLACK") {
            resolveM3TonalSurface(Color(0xFF070709), accent, 0.05f, alpha)
        } else {
            resolveM3TonalSurface(Color(0xFF0A0A0C), accent, 0.07f, alpha)
        }
    } else {
        resolveM3TonalSurface(M3LightSurface, accent, 0.06f, alpha)
    }
}

fun computeRawM3SurfaceContainerLow(bgMode: String, accent: Color, isDark: Boolean, style: String): Color {
    val alpha = if (style == "SOLID") 1.0f else 0.68f
    return if (isDark) {
        if (bgMode == "BLACK") {
            resolveM3TonalSurface(Color(0xFF08080A), accent, 0.06f, alpha)
        } else {
            resolveM3TonalSurface(Color(0xFF111114), accent, 0.10f, alpha)
        }
    } else {
        resolveM3TonalSurface(M3LightSurfaceContainerLow, accent, 0.08f, alpha)
    }
}

fun computeRawM3SurfaceContainer(bgMode: String, accent: Color, isDark: Boolean, style: String): Color {
    val alpha = if (style == "SOLID") 1.0f else 0.72f
    return if (isDark) {
        if (bgMode == "BLACK") {
            resolveM3TonalSurface(Color(0xFF0E0E11), accent, 0.09f, alpha)
        } else {
            resolveM3TonalSurface(Color(0xFF16161A), accent, 0.14f, alpha)
        }
    } else {
        resolveM3TonalSurface(M3LightSurfaceContainer, accent, 0.11f, alpha)
    }
}

fun computeRawM3SurfaceContainerHigh(bgMode: String, accent: Color, isDark: Boolean, style: String): Color {
    val alpha = if (style == "SOLID") 1.0f else 0.76f
    return if (isDark) {
        if (bgMode == "BLACK") {
            resolveM3TonalSurface(Color(0xFF141418), accent, 0.13f, alpha)
        } else {
            resolveM3TonalSurface(Color(0xFF1C1C21), accent, 0.18f, alpha)
        }
    } else {
        resolveM3TonalSurface(M3LightSurfaceContainerHigh, accent, 0.14f, alpha)
    }
}

// Central dynamic M3 surface resolvers reading smoothly animated root tokens
@Composable
@ReadOnlyComposable
fun m3SurfaceBase(): Color {
    LocalAnimatedM3Palette.current?.let { return it.surfaceBase }
    return computeRawM3SurfaceBase(LocalBgMode.current, LocalAccentColor.current, LocalIsDarkTheme.current, LocalSurfaceStyle.current)
}

@Composable
@ReadOnlyComposable
fun m3SurfaceContainerLow(): Color {
    LocalAnimatedM3Palette.current?.let { return it.surfaceContainerLow }
    return computeRawM3SurfaceContainerLow(LocalBgMode.current, LocalAccentColor.current, LocalIsDarkTheme.current, LocalSurfaceStyle.current)
}

@Composable
@ReadOnlyComposable
fun m3SurfaceContainer(): Color {
    LocalAnimatedM3Palette.current?.let { return it.surfaceContainer }
    return computeRawM3SurfaceContainer(LocalBgMode.current, LocalAccentColor.current, LocalIsDarkTheme.current, LocalSurfaceStyle.current)
}

@Composable
@ReadOnlyComposable
fun m3SurfaceContainerHigh(): Color {
    LocalAnimatedM3Palette.current?.let { return it.surfaceContainerHigh }
    return computeRawM3SurfaceContainerHigh(LocalBgMode.current, LocalAccentColor.current, LocalIsDarkTheme.current, LocalSurfaceStyle.current)
}

@Composable
@ReadOnlyComposable
fun m3OnSurface(): Color {
    LocalAnimatedM3Palette.current?.let { return it.onSurface }
    return if (LocalIsDarkTheme.current) Color(0xFFF5F5F5) else Color(0xFF141419)
}

@Composable
@ReadOnlyComposable
fun m3OnSurfaceVariant(): Color {
    LocalAnimatedM3Palette.current?.let { return it.onSurfaceVariant }
    return if (LocalIsDarkTheme.current) Color(0xFFCAC4D0) else Color(0xFF5A5A65)
}

@Composable
@ReadOnlyComposable
fun m3SubtleFill(alpha: Float = 0.08f): Color {
    return if (LocalIsDarkTheme.current) {
        Color.White.copy(alpha = alpha)
    } else {
        Color(0xFF14141F).copy(alpha = (alpha * 0.9f).coerceIn(0.04f, 0.25f))
    }
}

@Composable
@ReadOnlyComposable
fun m3GlassBorder(): Color {
    val accent = LocalAccentColor.current
    return if (LocalIsDarkTheme.current) {
        accent.copy(alpha = 0.15f)
    } else {
        accent.copy(alpha = 0.20f)
    }
}

@Composable
@ReadOnlyComposable
fun m3ActivePillContainer(): Color {
    val accent = LocalAccentColor.current
    return accent.copy(alpha = 0.28f)
}

fun Modifier.glassMorphic(
    backgroundColor: Color,
    borderColor: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 0.dp
): Modifier = this
    .clip(shape)
    .background(backgroundColor, shape)
    .then(
        if (borderColor != Color.Unspecified && borderWidth > 0.dp) {
            Modifier.border(borderWidth, borderColor, shape)
        } else {
            Modifier
        }
    )

private val DarkColorScheme = darkColorScheme(
    primary = TerminalGreen,
    secondary = TerminalWhite,
    tertiary = TerminalGray,
    background = TrueBlack,
    surface = M3Surface,
    surfaceVariant = M3SurfaceContainerHigh,
    onPrimary = TrueBlack,
    onSecondary = TrueBlack,
    onTertiary = TrueBlack,
    onBackground = TerminalWhite,
    onSurface = TerminalWhite,
    onSurfaceVariant = Color(0xFFCAC4D0)
)

private val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF008542),
    secondary = Color(0xFF141419),
    tertiary = Color(0xFF5A5A65),
    background = Color(0xFFF7F7FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8E8EE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF141419),
    onSurface = Color(0xFF141419),
    onSurfaceVariant = Color(0xFF5A5A65)
)

val ModernTypography = androidx.compose.material3.Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.01).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = TerminalFont,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun KewMobileTheme(
    isDarkTheme: Boolean = LocalIsDarkTheme.current,
    content: @Composable () -> Unit
) {
    val bgMode = LocalBgMode.current
    val accent = LocalAccentColor.current
    val style = LocalSurfaceStyle.current
    val animSpec = androidx.compose.animation.core.tween<Color>(
        durationMillis = 500,
        easing = androidx.compose.animation.core.FastOutSlowInEasing
    )

    val surfaceBase by androidx.compose.animation.animateColorAsState(
        targetValue = computeRawM3SurfaceBase(bgMode, accent, isDarkTheme, style),
        animationSpec = animSpec,
        label = "m3SurfaceBaseAnim"
    )
    val surfaceContainerLow by androidx.compose.animation.animateColorAsState(
        targetValue = computeRawM3SurfaceContainerLow(bgMode, accent, isDarkTheme, style),
        animationSpec = animSpec,
        label = "m3SurfaceLowAnim"
    )
    val surfaceContainer by androidx.compose.animation.animateColorAsState(
        targetValue = computeRawM3SurfaceContainer(bgMode, accent, isDarkTheme, style),
        animationSpec = animSpec,
        label = "m3SurfaceContainerAnim"
    )
    val surfaceContainerHigh by androidx.compose.animation.animateColorAsState(
        targetValue = computeRawM3SurfaceContainerHigh(bgMode, accent, isDarkTheme, style),
        animationSpec = animSpec,
        label = "m3SurfaceHighAnim"
    )
    val onSurface by androidx.compose.animation.animateColorAsState(
        targetValue = if (isDarkTheme) Color(0xFFF5F5F5) else Color(0xFF141419),
        animationSpec = animSpec,
        label = "m3OnSurfaceAnim"
    )
    val onSurfaceVariant by androidx.compose.animation.animateColorAsState(
        targetValue = if (isDarkTheme) Color(0xFFCAC4D0) else Color(0xFF5A5A65),
        animationSpec = animSpec,
        label = "m3OnSurfaceVariantAnim"
    )

    val animatedPalette = androidx.compose.runtime.remember(
        surfaceBase, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, onSurface, onSurfaceVariant
    ) {
        AnimatedM3Palette(
            surfaceBase = surfaceBase,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAnimatedM3Palette provides animatedPalette
    ) {
        MaterialTheme(
            colorScheme = if (isDarkTheme) DarkColorScheme else LightColorScheme,
            typography = ModernTypography,
            content = content
        )
    }
}
