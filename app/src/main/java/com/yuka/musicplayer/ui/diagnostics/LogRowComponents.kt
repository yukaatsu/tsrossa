package com.yuka.musicplayer.ui.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.m3OnSurface

@Composable
fun LogSectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp)
    ) {
        Text(
            text = "— $title —",
            color = LocalAccentColor.current,
            fontFamily = TerminalFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(6.dp))
        Divider(
            modifier = Modifier.weight(1f),
            color = LocalAccentColor.current.copy(alpha = 0.25f),
            thickness = 1.dp
        )
    }
}

@Composable
fun LogItem(label: String, value: String, valueColor: Color = Color.Unspecified) {
    val actualValueColor = if (valueColor != Color.Unspecified) valueColor else m3OnSurface()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TerminalGray,
            fontFamily = TerminalFont,
            fontSize = 11.sp
        )
        Text(
            text = value,
            color = actualValueColor,
            fontFamily = TerminalFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LogMultilineItem(label: String, value: String, valueColor: Color = LocalAccentColor.current) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = TerminalGray,
            fontFamily = TerminalFont,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor,
            fontFamily = TerminalFont,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}
