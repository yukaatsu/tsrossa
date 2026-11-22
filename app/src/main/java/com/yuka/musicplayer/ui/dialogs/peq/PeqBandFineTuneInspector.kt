package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.audio.PeqBand
import com.yuka.musicplayer.audio.PeqFilterType
import com.yuka.musicplayer.ui.components.GlyphRefresh
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerLow
import com.yuka.musicplayer.util.ColorContrastHelper
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/**
 * UAPP-inspired compact parametric band editor:
 * - Clean header with Band #, Frequency, Acoustic Zone & 1-line description
 * - Full-width 3-Way Filter Type Segmented Bar (height = 42.dp) with Phosphor curve icons
 * - 3 precision rows with 40.dp x 40.dp Phosphor steppers:
 *   1) Gain stepper row (±0.1 dB per tap + Reset 0 dB)
 *   2) Frequency row (logarithmic slider 20 Hz..20 kHz + smart steppers)
 *   3) Q-Factor row (slider 0.3..5.0 + ±0.05 steppers)
 */
@Composable
internal fun PeqBandFineTuneInspector(
    band: PeqBand,
    accentColor: Color,
    isEnabled: Boolean,
    onBandChange: (PeqBand) -> Unit
) {
    val onSurfaceColor = m3OnSurface()
    val disabledGrey = m3OnSurfaceVariant().copy(alpha = 0.42f)
    val effectiveAccent = if (isEnabled) accentColor else disabledGrey
    val onAccentColor = ColorContrastHelper.getOnAccentColor(accentColor)

    val filterTypesOrdered = remember {
        listOf(PeqFilterType.LOW_SHELF, PeqFilterType.PEAKING, PeqFilterType.HIGH_SHELF)
    }

    val logMinFreq = remember { log10(20.0) }
    val logMaxFreq = remember { log10(20000.0) }
    val freqLogFraction = remember(band.freqHz) {
        ((log10(band.freqHz.toDouble().coerceIn(20.0, 20000.0)) - logMinFreq) / (logMaxFreq - logMinFreq))
            .toFloat()
            .coerceIn(0f, 1f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (isEnabled) 1.0f else 0.50f }
            .clip(RoundedCornerShape(16.dp))
            .background(m3SurfaceContainerLow())
            .border(0.8.dp, effectiveAccent.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header: Selected Band Identity
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "Band ${band.index + 1} • ${formatFullFreq(band.freqHz)} • ${band.zoneName}",
                color = if (isEnabled) onSurfaceColor else disabledGrey,
                fontSize = 13.5.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (band.acousticDescription.isNotBlank()) {
                Text(
                    text = band.acousticDescription,
                    color = if (isEnabled) m3OnSurfaceVariant() else disabledGrey.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = ModernSansFont,
                    lineHeight = 15.sp
                )
            }
        }

        // Full-Width 3-Way Filter Type Segmented Selector (height = 42.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(m3SurfaceContainerHigh())
                .border(0.8.dp, onSurfaceColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            filterTypesOrdered.forEach { type ->
                val isTypeActive = band.filterType == type
                val segmentBg = when {
                    isTypeActive && isEnabled -> accentColor
                    isTypeActive && !isEnabled -> onSurfaceColor.copy(alpha = 0.16f)
                    else -> Color.Transparent
                }
                val tint = when {
                    isTypeActive && isEnabled -> onAccentColor
                    isTypeActive && !isEnabled -> onSurfaceColor
                    isEnabled -> onSurfaceColor.copy(alpha = 0.80f)
                    else -> disabledGrey
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(segmentBg)
                        .then(
                            if (isTypeActive && !isEnabled) {
                                Modifier.border(0.8.dp, onSurfaceColor.copy(alpha = 0.30f), RoundedCornerShape(9.dp))
                            } else {
                                Modifier
                            }
                        )
                        .clickable(enabled = isEnabled) {
                            onBandChange(band.copy(filterType = type))
                        }
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PeqFilterTypeIcon(
                        filterType = type,
                        color = tint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = type.fullName,
                        color = tint,
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = if (isTypeActive) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 1. Gain Precision Row (±0.1 dB per tap — vertical fader handles fast drag)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PeqStepperButton(
                isPlus = false,
                enabled = isEnabled,
                accentColor = effectiveAccent,
                onClick = {
                    val next = (round((band.gainDb - 0.1f) * 10f) / 10f).coerceIn(-12.0f, 12.0f)
                    onBandChange(band.copy(gainDb = next))
                }
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(m3SurfaceContainerHigh().copy(alpha = if (isEnabled) 0.55f else 0.30f))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format(Locale.US, "Gain: %+.1f dB", band.gainDb),
                    color = if (isEnabled) onSurfaceColor else disabledGrey,
                    fontSize = 12.5.sp,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.Bold
                )

                val isNonZero = abs(band.gainDb) > 0.05f
                if (isNonZero && isEnabled) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(effectiveAccent.copy(alpha = 0.16f))
                            .clickable { onBandChange(band.copy(gainDb = 0.0f)) }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        GlyphRefresh(color = effectiveAccent, modifier = Modifier.size(12.dp))
                        Text(
                            text = "0 dB",
                            color = effectiveAccent,
                            fontSize = 11.sp,
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Text(
                        text = "Flat",
                        color = if (isEnabled) m3OnSurfaceVariant() else disabledGrey,
                        fontSize = 11.sp,
                        fontFamily = ModernSansFont
                    )
                }
            }

            PeqStepperButton(
                isPlus = true,
                enabled = isEnabled,
                accentColor = effectiveAccent,
                onClick = {
                    val next = (round((band.gainDb + 0.1f) * 10f) / 10f).coerceIn(-12.0f, 12.0f)
                    onBandChange(band.copy(gainDb = next))
                }
            )
        }

        // 2. Frequency Row (Logarithmic Slider + 40.dp Steppers)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Frequency",
                    color = if (isEnabled) m3OnSurfaceVariant() else disabledGrey,
                    fontSize = 11.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatFullFreq(band.freqHz),
                    color = if (isEnabled) onSurfaceColor else disabledGrey,
                    fontSize = 12.sp,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PeqStepperButton(
                    isPlus = false,
                    enabled = isEnabled,
                    accentColor = effectiveAccent,
                    onClick = {
                        val step = smartFreqStep(band.freqHz, steppingUp = false)
                        onBandChange(band.copy(freqHz = (band.freqHz - step).coerceAtLeast(20f)))
                    }
                )

                Slider(
                    value = freqLogFraction,
                    enabled = isEnabled,
                    onValueChange = { frac ->
                        if (isEnabled) {
                            val rawFreq = 10.0.pow(logMinFreq + frac.toDouble() * (logMaxFreq - logMinFreq)).toFloat()
                            val snapped = snapFrequencyNice(rawFreq).coerceIn(20f, 20000f)
                            onBandChange(band.copy(freqHz = snapped))
                        }
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = effectiveAccent,
                        activeTrackColor = effectiveAccent,
                        inactiveTrackColor = m3SurfaceContainerHigh(),
                        disabledThumbColor = disabledGrey,
                        disabledActiveTrackColor = disabledGrey.copy(alpha = 0.6f),
                        disabledInactiveTrackColor = m3SurfaceContainerHigh().copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                )

                PeqStepperButton(
                    isPlus = true,
                    enabled = isEnabled,
                    accentColor = effectiveAccent,
                    onClick = {
                        val step = smartFreqStep(band.freqHz, steppingUp = true)
                        onBandChange(band.copy(freqHz = (band.freqHz + step).coerceAtMost(20000f)))
                    }
                )
            }
        }

        // 3. Q-Factor Row (Bandwidth Control + 40.dp Steppers)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val qDesc = when {
                band.q <= 0.85f -> "Wide / Musical"
                band.q <= 1.65f -> "1-Octave Standard"
                else -> "Narrow / Surgical"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Q-Factor (Bandwidth)",
                    color = if (isEnabled) m3OnSurfaceVariant() else disabledGrey,
                    fontSize = 11.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = String.format(Locale.US, "Q: %.2f • %s", band.q, qDesc),
                    color = if (isEnabled) onSurfaceColor else disabledGrey,
                    fontSize = 11.5.sp,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PeqStepperButton(
                    isPlus = false,
                    enabled = isEnabled,
                    accentColor = effectiveAccent,
                    onClick = {
                        val next = (round((band.q - 0.05f) * 20f) / 20f).coerceIn(0.3f, 5.0f)
                        onBandChange(band.copy(q = next))
                    }
                )

                Slider(
                    value = band.q,
                    enabled = isEnabled,
                    onValueChange = { newQ ->
                        if (isEnabled) {
                            val stepped = (round(newQ * 20f) / 20f).coerceIn(0.3f, 5.0f)
                            onBandChange(band.copy(q = stepped))
                        }
                    },
                    valueRange = 0.3f..5.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = effectiveAccent,
                        activeTrackColor = effectiveAccent,
                        inactiveTrackColor = m3SurfaceContainerHigh(),
                        disabledThumbColor = disabledGrey,
                        disabledActiveTrackColor = disabledGrey.copy(alpha = 0.6f),
                        disabledInactiveTrackColor = m3SurfaceContainerHigh().copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                )

                PeqStepperButton(
                    isPlus = true,
                    enabled = isEnabled,
                    accentColor = effectiveAccent,
                    onClick = {
                        val next = (round((band.q + 0.05f) * 20f) / 20f).coerceIn(0.3f, 5.0f)
                        onBandChange(band.copy(q = next))
                    }
                )
            }
        }
    }
}
