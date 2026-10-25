package com.yuka.musicplayer.ui.components

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.LocalIsDarkTheme
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalWhite
import com.yuka.musicplayer.ui.theme.glassMorphic
import com.yuka.musicplayer.ui.theme.m3GlassBorder
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.resolveButtonGlassColor

@Composable
fun RetroButton(
    text: String,
    onClick: () -> Unit,
    color: Color = TerminalWhite,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val accent = LocalAccentColor.current
    val isDark = LocalIsDarkTheme.current
    val isAccent = isSelected || color == accent
    val buttonBg = resolveButtonGlassColor(accent = accent, isDark = isDark, isSelected = isAccent)
    val borderColor = if (isAccent) accent.copy(alpha = 0.35f) else m3GlassBorder()
    val textColor = if (isAccent) {
        accent
    } else {
        if (color == TerminalWhite) m3OnSurface() else color
    }

    TactileBox(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 40.dp)
            .glassMorphic(
                backgroundColor = buttonBg,
                borderColor = borderColor,
                shape = RoundedCornerShape(50),
                borderWidth = 0.8.dp
            )
    ) {
        Text(
            text = text,
            color = textColor,
            fontFamily = ModernSansFont,
            fontWeight = if (isAccent) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Material 3 Action Item for Dialogs & Bottom Sheets:
 * Borderless tonal row with tactile press physics and modern sans typography.
 */
@Composable
fun RetroActionItem(
    icon: String = "",
    label: String,
    isDestructive: Boolean = false,
    iconContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val accent = if (isDestructive) Color(0xFFFF5252) else LocalAccentColor.current
    val isDark = LocalIsDarkTheme.current
    val itemBg = resolveButtonGlassColor(accent = accent, isDark = isDark, isSelected = false)
    val borderColor = m3GlassBorder()

    TactileBox(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .glassMorphic(
                backgroundColor = itemBg,
                borderColor = borderColor,
                shape = RoundedCornerShape(14.dp),
                borderWidth = 0.8.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                if (iconContent != null) {
                    iconContent()
                } else {
                    Text(
                        text = icon,
                        color = accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModernSansFont
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = label,
                color = if (isDestructive) accent else m3OnSurface(),
                fontSize = 13.5.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun RetroCircularButton(
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 28.dp,
    isActive: Boolean = false,
    activeColor: Color = LocalAccentColor.current
) {
    val isDark = LocalIsDarkTheme.current
    val bgColor = resolveButtonGlassColor(accent = activeColor, isDark = isDark, isSelected = isActive)
    val textColor = if (isActive) activeColor else m3OnSurface().copy(alpha = 0.85f)
    val borderColor = if (isActive) activeColor.copy(alpha = 0.35f) else m3GlassBorder()

    TactileBox(
        onClick = onClick,
        pressedScale = 0.88f,
        modifier = modifier
            .size(size)
            .glassMorphic(
                backgroundColor = bgColor,
                borderColor = borderColor,
                shape = CircleShape,
                borderWidth = 0.8.dp
            )
    ) {
        Text(
            text = icon,
            color = textColor,
            fontSize = (size.value * 0.45f).sp,
            fontFamily = ModernSansFont,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
