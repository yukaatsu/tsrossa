package com.yuka.musicplayer.ui.views.settings

import android.content.SharedPreferences
import androidx.compose.foundation.background
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
import com.yuka.musicplayer.ui.components.GlyphAdd
import com.yuka.musicplayer.ui.components.GlyphMinus
import com.yuka.musicplayer.ui.components.TactileBox
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalWhite

@Composable
fun TypographyHapticsSection(
    prefs: SharedPreferences,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    hapticEnabled: Boolean,
    onHapticChange: (Boolean) -> Unit
) {
    val accent = LocalAccentColor.current

    SettingsCard(title = "Typography & Haptics") {
        Text(
            text = "Global font scale",
            color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
            fontFamily = ModernSansFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TactileBox(
                onClick = {
                    val newScale = (fontScale - 0.1f).coerceAtLeast(0.8f)
                    onFontScaleChange(newScale)
                    prefs.edit().putFloat("font_scale", newScale).apply()
                },
                pressedScale = 0.88f,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f))
            ) {
                GlyphMinus(color = accent, modifier = Modifier.size(18.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(com.yuka.musicplayer.ui.theme.m3SubtleFill(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${String.format("%.1f", fontScale)}x Scale",
                    color = com.yuka.musicplayer.ui.theme.m3OnSurface(),
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }

            TactileBox(
                onClick = {
                    val newScale = (fontScale + 0.1f).coerceAtMost(1.5f)
                    onFontScaleChange(newScale)
                    prefs.edit().putFloat("font_scale", newScale).apply()
                },
                pressedScale = 0.88f,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f))
            ) {
                GlyphAdd(color = accent, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        SettingsToggleRow(
            title = "Haptic Tactile Feedback",
            subtitle = "Vibration ticks on button & transport interaction",
            isChecked = hapticEnabled,
            onCheckedChange = { enabled ->
                onHapticChange(enabled)
                prefs.edit().putBoolean("haptic_enabled", enabled).apply()
            }
        )
    }
}
