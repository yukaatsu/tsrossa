package com.yuka.musicplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.runtime.compositionLocalOf

import androidx.compose.ui.text.font.Font
import com.yuka.musicplayer.R

val LocalAccentColor = compositionLocalOf { Color(0xFF00FF00) }
val TerminalFont = FontFamily(Font(R.font.fantasquesans_regular))

fun Color.blendWithWhite(ratio: Float): Color {
    return Color(
        red = this.red + (1f - this.red) * ratio,
        green = this.green + (1f - this.green) * ratio,
        blue = this.blue + (1f - this.blue) * ratio,
        alpha = this.alpha
    )
}


val TrueBlack = Color(0xFF000000)
val TerminalGreen = Color(0xFF00FF00)
val TerminalWhite = Color(0xFFF5F5F5)
val TerminalGray = Color(0xFF888888)

// Tsrossa Signature Neon Sakura Palette
val SignatureDeepNavy = Color(0xFF070A12)
val SignatureSurfaceNavy = Color(0xFF101626)
val SakuraPink = Color(0xFFFF85A1)
val VividViolet = Color(0xFFA259FF)
val SubtleMint = Color(0xFF64FFDA)
val PastelPurple = Color(0xFFB388FF)

private val DarkColorScheme = darkColorScheme(
    primary = TerminalGreen,
    secondary = TerminalWhite,
    tertiary = TerminalGray,
    background = TrueBlack,
    surface = TrueBlack,
    onPrimary = TrueBlack,
    onSecondary = TrueBlack,
    onTertiary = TrueBlack,
    onBackground = TerminalWhite,
    onSurface = TerminalWhite,
)

val TerminalTypography = androidx.compose.material3.Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun KewMobileTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme // Force dark theme for terminal look

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TerminalTypography,
        content = content
    )
}
