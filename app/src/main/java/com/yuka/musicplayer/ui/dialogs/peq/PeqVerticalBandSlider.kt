package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.audio.PeqBand
import com.yuka.musicplayer.ui.theme.LocalIsDarkTheme
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.util.ColorContrastHelper
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Poweramp-inspired spacious vertical EQ band fader:
 * - Wide 62.dp column & tall 164.dp touchable vertical track
 * - 0.1 dB precision on tap & drag + double-tap reset to 0.0 dB
 * - Light & Dark theme adaptive inactive rail, 0 dB center tick, and thumb contrast
 */
@Composable
internal fun PeqVerticalBandSlider(
    band: PeqBand,
    accentColor: Color,
    isEnabled: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onGainChange: (Float) -> Unit
) {
    val freqText = remember(band.freqHz) { formatShortFreq(band.freqHz) }
    val isDark = LocalIsDarkTheme.current
    val onSurfaceColor = m3OnSurface()
    val disabledGrey = m3OnSurfaceVariant().copy(alpha = 0.42f)
    val effectiveAccent = if (isEnabled) accentColor else disabledGrey

    // Adaptive contrast palette for both Dark Mode and Light Mode
    val inactiveRailColor = if (isDark) {
        Color.White.copy(alpha = if (isEnabled) 0.16f else 0.07f)
    } else {
        onSurfaceColor.copy(alpha = if (isEnabled) 0.24f else 0.10f)
    }
    val zeroTickColor = if (isDark) {
        Color.White.copy(alpha = if (isEnabled) 0.42f else 0.18f)
    } else {
        onSurfaceColor.copy(alpha = if (isEnabled) 0.52f else 0.22f)
    }

    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnGainChange by rememberUpdatedState(onGainChange)
    val currentGainDb by rememberUpdatedState(band.gainDb)
    val onAccentColor = ColorContrastHelper.getOnAccentColor(effectiveAccent)
    var sliderHeightPx by remember { mutableFloatStateOf(1f) }

    Column(
        modifier = Modifier
            .width(62.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) effectiveAccent.copy(alpha = if (isEnabled) 0.14f else 0.08f)
                else Color.Transparent
            )
            .border(
                width = if (isSelected) 0.9.dp else 0.dp,
                color = if (isSelected) effectiveAccent.copy(alpha = 0.35f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = isEnabled) { currentOnSelect() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Live Gain dB Readout (0.1 dB precision)
        val gainStr = String.format(Locale.US, "%+.1f", band.gainDb)
        Text(
            text = gainStr,
            color = when {
                !isEnabled -> disabledGrey
                isSelected -> accentColor
                abs(band.gainDb) > 0.05f -> onSurfaceColor
                else -> m3OnSurfaceVariant()
            },
            fontSize = 11.sp,
            fontFamily = TerminalFont,
            fontWeight = if (isSelected || abs(band.gainDb) > 0.05f) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )

        // 2. Tall 164.dp Vertical Fader Track (full column touch width, 0.1 dB step)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp)
                .onGloballyPositioned { coords ->
                    sliderHeightPx = coords.size.height.toFloat()
                }
                .pointerInput(band.index, isEnabled) {
                    if (!isEnabled) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = {
                            currentOnSelect()
                            currentOnGainChange(0.0f)
                        },
                        onTap = {
                            // Single tap ONLY selects this band for fine-tuning below (never jumps gain accidentally!)
                            currentOnSelect()
                        }
                    )
                }
                .pointerInput(band.index, isEnabled) {
                    if (!isEnabled) return@pointerInput
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pad = 10.dp.toPx()
                        val trackLen = (sliderHeightPx - 2f * pad).coerceAtLeast(1f)
                        val startGain = currentGainDb.coerceIn(-12f, 12f)
                        val startFrac = (startGain + 12f) / 24f
                        val currentThumbY = (sliderHeightPx - pad) - (startFrac * trackLen)
                        val thumbHitRadiusPx = 24.dp.toPx()

                        // If user touches outside the thumb knob area, let parent verticalScroll / horizontalScroll handle it!
                        if (abs(down.position.y - currentThumbY) > thumbHitRadiusPx) {
                            return@awaitEachGesture
                        }

                        var dragLocked = false
                        var accumulatedDx = 0f
                        var accumulatedDy = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val delta = change.position - change.previousPosition
                            if (!dragLocked) {
                                if (change.isConsumed) break
                                accumulatedDx += delta.x
                                accumulatedDy += delta.y
                                val distMoved = kotlin.math.hypot(accumulatedDx.toDouble(), accumulatedDy.toDouble()).toFloat()
                                if (distMoved > touchSlop) {
                                    if (abs(accumulatedDy) >= abs(accumulatedDx) * 1.1f) {
                                        dragLocked = true
                                        currentOnSelect()
                                        change.consume()
                                    } else {
                                        // User is scrolling the 10-band row horizontally -> yield to Row.horizontalScroll!
                                        break
                                    }
                                }
                            }

                            if (dragLocked) {
                                change.consume()
                                val totalDeltaY = change.position.y - down.position.y
                                val deltaGain = (-totalDeltaY / trackLen) * 24f
                                val newGain = (startGain + deltaGain).coerceIn(-12f, 12f)
                                val stepped = (round(newGain.toDouble() * 10.0) / 10.0).toFloat()
                                if (abs(stepped - currentGainDb) >= 0.05f) {
                                    currentOnGainChange(stepped)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val topY = 10.dp.toPx()
                val botY = size.height - 10.dp.toPx()
                val trackLen = botY - topY
                val midY = topY + trackLen / 2f

                // Inactive rail (adaptive for Dark & Light mode)
                drawLine(
                    color = inactiveRailColor,
                    start = Offset(cx, topY),
                    end = Offset(cx, botY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Zero dB center reference tick (adaptive for Dark & Light mode)
                drawLine(
                    color = zeroTickColor,
                    start = Offset(cx - 8.dp.toPx(), midY),
                    end = Offset(cx + 8.dp.toPx(), midY),
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Active gain fill from 0 dB to thumb
                val frac = ((band.gainDb.coerceIn(-12f, 12f) + 12f) / 24f)
                val thumbY = botY - (frac * trackLen)

                if (abs(thumbY - midY) > 1.dp.toPx()) {
                    drawLine(
                        color = effectiveAccent,
                        start = Offset(cx, midY),
                        end = Offset(cx, thumbY),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Soft halo ring for selected band
                if (isSelected && isEnabled) {
                    drawCircle(
                        color = effectiveAccent.copy(alpha = 0.24f),
                        radius = 13.5.dp.toPx(),
                        center = Offset(cx, thumbY)
                    )
                }

                // High-contrast thumb for both Light and Dark themes
                if (isSelected && isEnabled) {
                    drawCircle(
                        color = effectiveAccent,
                        radius = 8.5.dp.toPx(),
                        center = Offset(cx, thumbY)
                    )
                    drawCircle(
                        color = onAccentColor,
                        radius = 3.2.dp.toPx(),
                        center = Offset(cx, thumbY)
                    )
                } else {
                    drawCircle(
                        color = if (isEnabled) effectiveAccent else disabledGrey,
                        radius = 6.dp.toPx(),
                        center = Offset(cx, thumbY)
                    )
                }
            }
        }

        // 3. Frequency Label
        Text(
            text = freqText,
            color = when {
                !isEnabled -> disabledGrey
                isSelected -> accentColor
                else -> onSurfaceColor
            },
            fontSize = 12.sp,
            fontFamily = ModernSansFont,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )

        // 4. Phosphor Filter Curve Icon (15.dp)
        PeqFilterTypeIcon(
            filterType = band.filterType,
            color = if (isSelected) effectiveAccent else if (isEnabled) m3OnSurfaceVariant() else disabledGrey,
            modifier = Modifier.size(15.dp)
        )
    }
}
