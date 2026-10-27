package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.audio.AudioEngine
import com.yuka.musicplayer.ui.components.CyberBottomSheet
import com.yuka.musicplayer.ui.components.GlyphVolume
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SubtleFill
import kotlin.math.roundToInt

/**
 * Minimalist, zero-latency Free-Swipe Volume Modal Sheet:
 * - Pure horizontal touch & swipe bar (no preset buttons or extra clutter).
 * - Uses a single `awaitEachGesture` loop + local `sliderFraction` state + GPU Canvas draw
 *   so touch-down, hold, and horizontal dragging respond in 0ms at 120fps.
 */
@Composable
fun VolumeModalSheet(
    visible: Boolean,
    currentVolume: Float,
    isHardwareVolumeActive: Boolean,
    isHardwareVolumeSupported: Boolean = false,
    isHardwareVolumeEnabled: Boolean = false,
    onToggleHardwareVolume: ((Boolean) -> Unit)? = null,
    onVolumeChange: (Float) -> Unit,
    onDismissRequest: () -> Unit
) {
    val accent = LocalAccentColor.current
    val haptic = LocalHapticFeedback.current
    val currentOnVolumeChange by rememberUpdatedState(onVolumeChange)

    // Local high-frequency state for 0ms finger tracking without waiting for MainActivity recomposition
    var isUserDragging by remember { mutableStateOf(false) }
    var localVolume by remember { mutableFloatStateOf(currentVolume.coerceIn(0f, 1f)) }
    var lastSentPercent by remember { mutableIntStateOf((currentVolume * 100).roundToInt()) }

    LaunchedEffect(currentVolume, isUserDragging) {
        if (!isUserDragging) {
            localVolume = currentVolume.coerceIn(0f, 1f)
            lastSentPercent = (localVolume * 100).roundToInt()
        }
    }

    CyberBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        wrapHeight = true
    ) {
        val displayVolume = if (isUserDragging) localVolume else currentVolume.coerceIn(0f, 1f)
        val volPercent = (displayVolume * 100).roundToInt()
        val dbText = remember(volPercent) {
            when {
                volPercent >= 100 -> "0.0 dB (Bit-Perfect)"
                volPercent <= 0 -> "-∞ dB (Muted)"
                else -> {
                    val db = AudioEngine.sliderToPerceptualDb(volPercent / 100f)
                    String.format(java.util.Locale.US, "%.1f dB", db)
                }
            }
        }

        val onSurface = m3OnSurface()
        val onSurfaceVariant = m3OnSurfaceVariant()
        val trackBgColor = m3SubtleFill(alpha = 0.12f)
        val trackFillColor = accent.copy(alpha = 0.36f)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: Volume Mode & Live Readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphVolume(color = accent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Master Output Gain",
                            color = onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ModernSansFont
                        )
                        Text(
                            text = "Swipe horizontally to adjust volume",
                            color = onSurfaceVariant,
                            fontSize = 11.sp,
                            fontFamily = ModernSansFont
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$volPercent%",
                        color = accent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TerminalFont
                    )
                    Text(
                        text = dbText,
                        color = onSurfaceVariant,
                        fontSize = 10.5.sp,
                        fontFamily = TerminalFont
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dedicated Toggle Button: UNIVERSAL 64-BIT DSP <-> HYBRID HARDWARE UAC
            val isUacModeSelected = isHardwareVolumeEnabled
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(onSurface.copy(alpha = 0.06f))
                    .border(
                        width = 1.dp,
                        color = onSurface.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Option 1: UNIVERSAL 64-BIT DSP
                val dspSelected = !isUacModeSelected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (dspSelected) accent.copy(alpha = 0.18f)
                            else Color.Transparent
                        )
                        .border(
                            width = 1.dp,
                            color = if (dspSelected) accent.copy(alpha = 0.55f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable(enabled = onToggleHardwareVolume != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isUacModeSelected) {
                                onToggleHardwareVolume?.invoke(false)
                            } else {
                                onToggleHardwareVolume?.invoke(true)
                            }
                        }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "UNIVERSAL 64-BIT DSP",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (dspSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            letterSpacing = 0.6.sp,
                            fontSize = 10.sp
                        ),
                        color = if (dspSelected) accent else onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Option 2: HYBRID HARDWARE UAC
                val uacSelected = isUacModeSelected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (uacSelected) accent.copy(alpha = 0.18f)
                            else Color.Transparent
                        )
                        .border(
                            width = 1.dp,
                            color = if (uacSelected) accent.copy(alpha = 0.55f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable(enabled = onToggleHardwareVolume != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!isUacModeSelected) {
                                onToggleHardwareVolume?.invoke(true)
                            } else {
                                onToggleHardwareVolume?.invoke(false)
                            }
                        }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "HYBRID HARDWARE UAC",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (uacSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            letterSpacing = 0.6.sp,
                            fontSize = 10.sp
                        ),
                        color = if (uacSelected) accent else onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status row beneath segmented toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dotColor = when {
                    isHardwareVolumeActive -> Color(0xFFFFD700)
                    isHardwareVolumeSupported && isHardwareVolumeEnabled -> accent
                    else -> onSurfaceVariant.copy(alpha = 0.5f)
                }
                val statusText = when {
                    isHardwareVolumeActive -> "Hardware DAC Volume Control Active"
                    isHardwareVolumeSupported && isHardwareVolumeEnabled -> "Hardware UAC Enabled (Standby)"
                    isHardwareVolumeSupported -> "DAC Supports Hardware Volume (DSP Mode Active)"
                    else -> "USB/Internal DAC: Universal 64-Bit DSP Gain"
                }
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    color = if (isHardwareVolumeActive) Color(0xFFFFD700) else onSurfaceVariant.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    fontFamily = TerminalFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Pure Free-Swipe Volume Pill (Single awaitEachGesture + GPU Canvas = 0ms latency)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            isUserDragging = true

                            fun updateFromX(xPx: Float) {
                                val widthPx = size.width.toFloat().coerceAtLeast(1f)
                                val raw = (xPx / widthPx).coerceIn(0f, 1f)
                                localVolume = raw
                                val pct = (raw * 100).roundToInt().coerceIn(0, 100)
                                if (pct != lastSentPercent) {
                                    lastSentPercent = pct
                                    if (pct % 5 == 0 || pct == 0 || pct == 100) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    currentOnVolumeChange(pct / 100f)
                                }
                            }

                            updateFromX(down.position.x)

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                change.consume()
                                updateFromX(change.position.x)
                            }

                            val finalPct = (localVolume * 100).roundToInt().coerceIn(0, 100)
                            currentOnVolumeChange(finalPct / 100f)
                            isUserDragging = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    // Background track
                    drawRoundRect(
                        color = trackBgColor,
                        size = size,
                        cornerRadius = cornerRadius
                    )
                    // Active fill
                    val fillW = (size.width * displayVolume.coerceIn(0f, 1f)).coerceAtLeast(0f)
                    if (fillW > 0f) {
                        drawRoundRect(
                            color = trackFillColor,
                            size = Size(fillW, size.height),
                            cornerRadius = cornerRadius
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "0%",
                        color = m3OnSurfaceVariant(),
                        fontSize = 12.sp,
                        fontFamily = TerminalFont,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$volPercent%",
                        color = m3OnSurface(),
                        fontSize = 14.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "100%",
                        color = accent,
                        fontSize = 12.sp,
                        fontFamily = TerminalFont,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
