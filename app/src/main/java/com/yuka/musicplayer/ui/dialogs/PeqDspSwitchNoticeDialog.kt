package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.yuka.musicplayer.ui.components.CyberBottomSheet
import com.yuka.musicplayer.ui.components.GlyphEqualizer
import com.yuka.musicplayer.ui.components.TactileBox
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.util.ColorContrastHelper

@Composable
fun PeqDspSwitchNoticeDialog(
    visible: Boolean,
    title: String = "Switched to Universal 64-Bit DSP",
    subtitle: String = "DSP & Volume Architecture Sync",
    message: String = "Because Parametric EQ (PEQ) is enabled, volume control has automatically switched from Hybrid Hardware UAC to Universal 64-Bit DSP. This ensures 10-band biquad filtering runs at full 64-bit floating-point precision.",
    confirmText: String = "Yes",
    cancelText: String = "No",
    onConfirm: (() -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    val accent = LocalAccentColor.current
    val onAccent = ColorContrastHelper.getOnAccentColor(accent)
    val onSurfaceColor = m3OnSurface()

    CyberBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        wrapHeight = true,
        maxWidth = 420.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphEqualizer(color = accent, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = onSurfaceColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModernSansFont
                    )
                    Text(
                        text = subtitle,
                        color = accent,
                        fontSize = 11.sp,
                        fontFamily = TerminalFont,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(m3SurfaceContainerHigh().copy(alpha = 0.7f))
                    .border(0.8.dp, onSurfaceColor.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = message,
                    color = onSurfaceColor,
                    fontSize = 12.5.sp,
                    fontFamily = ModernSansFont,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (onConfirm != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TactileBox(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(m3SurfaceContainerHigh())
                            .border(1.dp, onSurfaceColor.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = cancelText,
                                color = onSurfaceColor,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            )
                        }
                    }

                    TactileBox(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = confirmText,
                                color = onAccent,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            } else {
                TactileBox(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Got It",
                            color = onAccent,
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
