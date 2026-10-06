package com.yuka.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalWhite

@Composable
fun RetroButton(
    text: String,
    onClick: () -> Unit,
    color: Color = TerminalWhite,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val isAccent = isSelected || color == LocalAccentColor.current
    Box(
        modifier = modifier
            .border(1.dp, if (isAccent) LocalAccentColor.current else color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .background(
                if (isAccent) LocalAccentColor.current.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isAccent) LocalAccentColor.current else color,
            fontFamily = TerminalFont,
            fontWeight = if (isAccent) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun RetroActionItem(
    icon: String,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val accent = if (isDestructive) Color(0xFFFF5252) else LocalAccentColor.current
    TactileBox(
        onClick = onClick,
        pressedScale = 0.96f,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(accent.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .border(0.8.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                color = if (isDestructive) accent else TerminalWhite,
                fontSize = 11.sp,
                fontFamily = TerminalFont,
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
    size: androidx.compose.ui.unit.Dp = 26.dp,
    isActive: Boolean = false,
    activeColor: Color = LocalAccentColor.current,
    inactiveColor: Color = LocalAccentColor.current
) {
    val borderColor = if (isActive) activeColor else inactiveColor.copy(alpha = 0.45f)
    val bgColor = if (isActive) activeColor.copy(alpha = 0.22f) else Color.Transparent
    val textColor = if (isActive) activeColor else inactiveColor.copy(alpha = 0.8f)

    Box(
        modifier = modifier
            .size(size)
            .border(1.dp, borderColor, CircleShape)
            .background(bgColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            color = textColor,
            fontSize = (size.value * 0.45f).sp,
            fontFamily = TerminalFont,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

