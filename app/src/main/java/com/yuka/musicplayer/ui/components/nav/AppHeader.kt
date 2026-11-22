package com.yuka.musicplayer.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.ViewState
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.LocalIsDarkTheme
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.glassMorphic
import com.yuka.musicplayer.ui.theme.m3GlassBorder
import com.yuka.musicplayer.ui.theme.m3OnSurface

/**
 * Clean Material 3 Top Status & Utility Bar.
 * - Tap brand ("tsrossa") navigates to Settings -> About.
 * - Center responsive Glass Pill indicator displaying DAC product name (with walking marquee) or DISCONNECTED.
 *   - Long-press on DAC pill triggers Warm Up DAC confirmation modal.
 * - Right: Action cluster with Settings (Gear), Diagnostics, and Update (Warmup bolt moved to Pill long-press).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppHeader(
    viewState: ViewState,
    isHardwareVolumeActive: Boolean,
    isForceSoftwareVolume: Boolean,
    isHardwareVolumeLockedBySystem: Boolean,
    onToggleLogs: () -> Unit,
    onToggleUpdate: () -> Unit,
    hasUpdateAvailable: Boolean = false,
    onDonationClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    connectedDacName: String? = null,
    isDacConnected: Boolean = false,
    onDacPillLongClick: () -> Unit = {},
    onToggleLockTask: () -> Unit = {},
    isTaskLocked: Boolean = false
) {
    val accent = LocalAccentColor.current
    val isDark = LocalIsDarkTheme.current
    val statusColor = when {
        isHardwareVolumeLockedBySystem -> Color(0xFFFF9100)
        isForceSoftwareVolume -> Color(0xFFFF5252)
        isHardwareVolumeActive -> Color(0xFFFFD700)
        else -> accent
    }

    val dacDisplayText = if (isDacConnected && !connectedDacName.isNullOrBlank()) {
        connectedDacName
    } else {
        "DISCONNECTED"
    }
    val dacActiveColor = if (isDacConnected) {
        if (isHardwareVolumeActive) Color(0xFFFFD700) else accent
    } else {
        TerminalGray
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Title ("tsrossa" -> About) + Centered Responsive DAC Glass Pill
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TactileBox(
                    onClick = onAboutClick,
                    pressedScale = 0.94f
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlyphTsrossaLogoSilhouette(
                            color = m3OnSurface(),
                            modifier = Modifier.size(21.dp)
                        )
                        Text(
                            text = "tsrossa",
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            letterSpacing = (-0.3).sp,
                            color = m3OnSurface()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Centered Responsive Glass DAC Pill with Marquee & Long-Press Warm Up
                TactileBox(
                    onClick = onToggleLogs,
                    onLongClick = onDacPillLongClick,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .height(30.dp)
                        .glassMorphic(
                            backgroundColor = com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh().copy(alpha = 0.85f),
                            borderColor = m3GlassBorder(),
                            shape = RoundedCornerShape(50),
                            borderWidth = 0.8.dp
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.5.dp)
                                .background(dacActiveColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = dacDisplayText,
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = if (isDacConnected) m3OnSurface() else TerminalGray,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: M3 Glassmorphic Action Cluster (Donation, Update, Settings)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // DONATION & SUPPORTER WALL
                TactileBox(
                    onClick = onDonationClick,
                    pressedScale = 0.90f,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f))
                ) {
                    GlyphBagiBagi(
                        color = accent,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // UPDATES (always visible directly to the left of Settings)
                if (hasUpdateAvailable) {
                    val updateTransition = rememberInfiniteTransition(label = "updateIconAnim")
                    val pulseScale by updateTransition.animateFloat(
                        initialValue = 0.94f,
                        targetValue = 1.12f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "updatePulseScale"
                    )
                    val arrowBobOffsetY by updateTransition.animateFloat(
                        initialValue = -2.2f,
                        targetValue = 2.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "updateBobOffset"
                    )
                    val glowAlpha by updateTransition.animateFloat(
                        initialValue = 0.16f,
                        targetValue = 0.38f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "updateGlowAlpha"
                    )

                    TactileBox(
                        onClick = onToggleUpdate,
                        pressedScale = 0.90f,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color(0xFF00E676).copy(alpha = glowAlpha * 1.5f), CircleShape)
                            .background(Color(0xFF00E676).copy(alpha = glowAlpha))
                    ) {
                        Box(
                            modifier = Modifier.graphicsLayer {
                                translationY = arrowBobOffsetY
                                scaleX = pulseScale
                                scaleY = pulseScale
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphUpdate(
                                color = Color(0xFF00E676),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                } else {
                    TactileBox(
                        onClick = onToggleUpdate,
                        pressedScale = 0.90f,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.12f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            GlyphUpdate(
                                color = accent,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                // SETTINGS (positioned at the far right)
                TactileBox(
                    onClick = onSettingsClick,
                    pressedScale = 0.90f,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f))
                ) {
                    GlyphSettings(
                        color = accent,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}
