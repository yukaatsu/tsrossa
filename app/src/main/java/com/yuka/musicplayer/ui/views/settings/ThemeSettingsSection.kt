package com.yuka.musicplayer.ui.views.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.TactileBox
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray

@Composable
fun ThemeSettingsSection(
    bgMode: String,
    onBgModeChange: (String) -> Unit,
    accentMode: String,
    onAccentModeChange: (String) -> Unit,
    accentFixedColorStr: String,
    onAccentFixedColorChange: (String) -> Unit,
    themeMode: String = "DARK",
    onThemeModeChange: (String) -> Unit = {},
    surfaceStyle: String = "BLUR_OPACITY",
    onSurfaceStyleChange: (String) -> Unit = {}
) {
    SettingsCard(title = "Theme & Display") {
        Text(
            text = "Theme mode",
            color = com.yuka.musicplayer.ui.theme.m3OnSurface().copy(alpha = 0.70f),
            fontFamily = ModernSansFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        val themeModes = listOf(
            "DARK" to "Dark Mode",
            "LIGHT" to "Light Mode"
        )
        SettingsSegmentedControl(
            items = themeModes,
            selectedItem = themeMode,
            onItemSelected = onThemeModeChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Background canvas",
            color = com.yuka.musicplayer.ui.theme.m3OnSurface().copy(alpha = 0.70f),
            fontFamily = ModernSansFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        val bgModes = listOf(
            "BLACK" to "OLED",
            "SIGNATURE" to "Tonal M3",
            "ARTWORK" to "Album Artwork"
        )
        val currentBgKey = if (bgMode == "BLUR" || bgMode == "ARTWORK") "ARTWORK" else bgMode
        SettingsSegmentedControl(
            items = bgModes,
            selectedItem = currentBgKey,
            onItemSelected = onBgModeChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Accent color mode",
            color = com.yuka.musicplayer.ui.theme.m3OnSurface().copy(alpha = 0.70f),
            fontFamily = ModernSansFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        val accentModes = listOf(
            "WALLPAPER" to "Wallpaper",
            "DYNAMIC" to "Album Art",
            "FIXED" to "Fixed"
        )
        SettingsSegmentedControl(
            items = accentModes,
            selectedItem = accentMode,
            onItemSelected = onAccentModeChange
        )

        if (accentMode == "FIXED") {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "PALETTE SELECTION",
                color = com.yuka.musicplayer.ui.theme.m3OnSurface().copy(alpha = 0.70f),
                fontFamily = ModernSansFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val fixedColors = listOf(
                "#FFFFFF" to "White",
                "#00FF00" to "Green",
                "#00E5FF" to "Cyan",
                "#FFD700" to "Amber",
                "#FF4081" to "Pink",
                "#B388FF" to "Purple"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                fixedColors.forEach { (hex, _) ->
                    val color = Color(android.graphics.Color.parseColor(hex))
                    val isSelected = accentFixedColorStr.equals(hex, ignoreCase = true)
                    TactileBox(
                        onClick = { onAccentFixedColorChange(hex) },
                        pressedScale = 0.88f,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) color.copy(alpha = 0.35f) else com.yuka.musicplayer.ui.theme.m3SubtleFill(alpha = 0.08f)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 18.dp else 14.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}
