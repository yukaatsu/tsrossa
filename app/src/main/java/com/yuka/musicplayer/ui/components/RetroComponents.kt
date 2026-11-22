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


