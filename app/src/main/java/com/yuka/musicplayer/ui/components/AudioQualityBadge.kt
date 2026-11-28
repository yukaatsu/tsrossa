package com.yuka.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.diagnostics.AudioQualityBadgeModel
import com.yuka.musicplayer.ui.diagnostics.SignalPathStatus
import com.yuka.musicplayer.ui.theme.AudiophileCyan
import com.yuka.musicplayer.ui.theme.LocalIsDarkTheme
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGreen
import com.yuka.musicplayer.ui.theme.m3GlassBorder
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh

@Composable
internal fun resolveSignalPathAccentColor(status: SignalPathStatus): Color {
    val isDark = LocalIsDarkTheme.current
    val muted = m3OnSurfaceVariant()
    return when (status) {
        SignalPathStatus.BIT_PERFECT -> if (isDark) TerminalGreen else Color(0xFF008F39)
        SignalPathStatus.HYBRID_UAC_TRIM -> if (isDark) Color(0xFFFFD700) else Color(0xFFB27B00)
        SignalPathStatus.UNIVERSAL_DSP -> if (isDark) AudiophileCyan else Color(0xFF007A8C)
        SignalPathStatus.WARNING -> if (isDark) Color(0xFFFF9100) else Color(0xFFC65100)
        SignalPathStatus.NO_DAC -> muted
    }
}

@Composable
fun AudioQualityBadge(
    badgeModel: AudioQualityBadgeModel,
    signalPathStatus: SignalPathStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = resolveSignalPathAccentColor(signalPathStatus)
    val surfaceHigh = m3SurfaceContainerHigh()
    val isMuted = signalPathStatus == SignalPathStatus.NO_DAC
    val pillBackground = if (isMuted) {
        surfaceHigh
    } else {
        statusColor.copy(alpha = 0.14f).compositeOver(surfaceHigh)
    }
    val pillBorder = if (isMuted) {
        m3GlassBorder()
    } else {
        statusColor.copy(alpha = 0.35f)
    }
    val onSurface = m3OnSurface()
    val onSurfaceVariant = m3OnSurfaceVariant()

    TactileBox(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(pillBackground)
            .border(1.dp, pillBorder, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Text(
                text = badgeModel.primaryLabel,
                color = onSurface,
                fontFamily = ModernSansFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (badgeModel.showMqaMicroTag) {
                Text(
                    text = "• MQA",
                    color = onSurfaceVariant,
                    fontFamily = TerminalFont,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            GlyphChevronRight(
                color = onSurfaceVariant.copy(alpha = 0.78f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}
