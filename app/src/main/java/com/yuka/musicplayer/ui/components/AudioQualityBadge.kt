package com.yuka.musicplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.diagnostics.AudioQualityBadgeModel
import com.yuka.musicplayer.ui.diagnostics.AudioQualityTier
import com.yuka.musicplayer.ui.diagnostics.SignalPathStatus
import com.yuka.musicplayer.ui.diagnostics.buildAudioQualityAccessibilityLabel
import com.yuka.musicplayer.ui.theme.AudiophileCyan
import com.yuka.musicplayer.ui.theme.LocalIsDarkTheme
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGreen
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant

/**
 * Shared semantic accent color resolver for Signal Path states.
 * Used extensively by SignalPathModalSheet — DO NOT remove or move packages.
 */
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

/**
 * Apple Music-style borderless, pill-free audio quality indicator for the Now Playing screen.
 * Displays a dedicated vector logo per quality tier (Hi-Res Lossless / Lossless / Codec)
 * paired with understated adaptive monochrome typography; tapping opens the Signal Path Inspector.
 */
@Composable
fun AudioQualityBadge(
    badgeModel: AudioQualityBadgeModel,
    signalPathStatus: SignalPathStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onSurface = m3OnSurface()
    val onSurfaceVariant = m3OnSurfaceVariant()
    val warningColor = resolveSignalPathAccentColor(SignalPathStatus.WARNING)

    val logoColor: Color
    val labelColor: Color
    when (signalPathStatus) {
        SignalPathStatus.WARNING -> {
            logoColor = warningColor
            labelColor = warningColor
        }
        SignalPathStatus.NO_DAC -> {
            logoColor = onSurfaceVariant.copy(alpha = 0.72f)
            labelColor = onSurfaceVariant.copy(alpha = 0.72f)
        }
        SignalPathStatus.BIT_PERFECT,
        SignalPathStatus.HYBRID_UAC_TRIM,
        SignalPathStatus.UNIVERSAL_DSP -> {
            logoColor = onSurface.copy(alpha = 0.84f)
            labelColor = onSurface.copy(alpha = 0.80f)
        }
    }

    val accessibilityText = buildAudioQualityAccessibilityLabel(badgeModel)

    TactileBox(
        onClick = onClick,
        pressedScale = 0.94f,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityText
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            when (badgeModel.tier) {
                AudioQualityTier.HI_RES -> GlyphHiResLosslessBadge(
                    color = logoColor,
                    modifier = Modifier.size(15.dp)
                )
                AudioQualityTier.LOSSLESS -> GlyphLosslessBadge(
                    color = logoColor,
                    modifier = Modifier.size(15.dp)
                )
                AudioQualityTier.CODEC_FALLBACK -> GlyphAudioCodecBadge(
                    color = logoColor,
                    modifier = Modifier.size(14.dp)
                )
            }

            Text(
                text = badgeModel.primaryLabel,
                color = labelColor,
                fontFamily = ModernSansFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (badgeModel.showMqaMicroTag) {
                Text(
                    text = "• MQA",
                    color = labelColor.copy(alpha = 0.78f),
                    fontFamily = TerminalFont,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
