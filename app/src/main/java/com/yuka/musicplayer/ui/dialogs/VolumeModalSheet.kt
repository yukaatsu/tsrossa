package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.CyberBottomSheet
import com.yuka.musicplayer.ui.components.GlyphVolume
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SubtleFill
import kotlin.math.log10
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
                    val db = 20.0 * log10(volPercent / 100.0)
                    String.format(java.util.Locale.US, "%.1f dB", db)
                }
            }
        }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphVolume(color = accent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isHardwareVolumeActive) "Hardware DAC Volume" else "64-Bit DSP Volume",
                            color = m3OnSurface(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ModernSansFont
                        )
                        Text(
                            text = "Swipe horizontally to adjust volume",
                            color = m3OnSurfaceVariant(),
                            fontSize = 11.5.sp,
                            fontFamily = ModernSansFont
                        )
                    }
                }
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
                        color = m3OnSurfaceVariant(),
                        fontSize = 10.5.sp,
                        fontFamily = TerminalFont
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

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
