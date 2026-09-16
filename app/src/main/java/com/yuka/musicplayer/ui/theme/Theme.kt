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

val TrueBlack = Color(0xFF000000)
val TerminalGreen = Color(0xFF00FF00)
val TerminalWhite = Color(0xFFF5F5F5)
val TerminalGray = Color(0xFF888888)

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
