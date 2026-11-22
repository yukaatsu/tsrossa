package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.yuka.musicplayer.audio.PeqFilterType
import com.yuka.musicplayer.ui.components.GlyphAdd
import com.yuka.musicplayer.ui.components.GlyphFilterHighShelf
import com.yuka.musicplayer.ui.components.GlyphFilterLowShelf
import com.yuka.musicplayer.ui.components.GlyphFilterPeaking
import com.yuka.musicplayer.ui.components.GlyphMinus
import com.yuka.musicplayer.ui.components.TactileBox
import com.yuka.musicplayer.ui.theme.LocalIsDarkTheme
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerLow
import com.yuka.musicplayer.util.ColorContrastHelper
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

internal fun formatShortFreq(freqHz: Float): String {
    return if (freqHz >= 1000f) {
        val k = freqHz / 1000f
        if (abs(k - round(k)) < 0.05f) "${round(k).toInt()}k"
        else String.format(Locale.US, "%.1fk", k)
    } else {
        "${freqHz.toInt()}"
    }
}

internal fun formatFullFreq(freqHz: Float): String {
    return if (freqHz >= 1000f) {
        val k = freqHz / 1000f
        if (abs(k - round(k)) < 0.01f) "${round(k).toInt()} kHz"
        else String.format(Locale.US, "%.2f kHz", k)
    } else {
        "${freqHz.toInt()} Hz"
    }
}

internal fun smartFreqStep(freqHz: Float, steppingUp: Boolean): Float {
    val ref = if (steppingUp) freqHz else (freqHz - 1f)
    return when {
        ref < 100f -> 5f
        ref < 500f -> 10f
        ref < 1000f -> 25f
        ref < 5000f -> 100f
        else -> 250f
    }
}

internal fun snapFrequencyNice(rawHz: Float): Float {
    return when {
        rawHz < 100f -> round(rawHz)
        rawHz < 1000f -> round(rawHz / 5f) * 5f
        rawHz < 5000f -> round(rawHz / 25f) * 25f
        else -> round(rawHz / 50f) * 50f
    }
}

/**
 * Ergonomic toolbar chip (minHeight = 40.dp) with Phosphor Icon + text label.
 */
@Composable
internal fun PeqToolbarActionChip(
    label: String,
    isActive: Boolean,
    enabled: Boolean,
    accentColor: Color,
    subtle: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val onSurfaceColor = m3OnSurface()
    val disabledGrey = m3OnSurfaceVariant().copy(alpha = 0.42f)
    val contentTint = when {
        isActive -> ColorContrastHelper.getOnAccentColor(accentColor)
        !enabled -> disabledGrey
        subtle -> m3OnSurfaceVariant()
        else -> accentColor
    }
    val bgColor = when {
        isActive -> accentColor
        subtle -> m3SurfaceContainerLow()
        else -> m3SurfaceContainerHigh()
    }
    val borderColor = when {
        isActive -> Color.Transparent
        subtle -> onSurfaceColor.copy(alpha = if (enabled) (if (isDark) 0.12f else 0.16f) else 0.06f)
        else -> accentColor.copy(alpha = if (enabled) 0.28f else 0.08f)
    }

    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(width = 0.8.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        icon(contentTint)
        Text(
            text = label,
            color = contentTint,
            fontSize = 12.sp,
            fontFamily = ModernSansFont,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * Phosphor curve icon helper matching PeqFilterType.
 */
@Composable
internal fun PeqFilterTypeIcon(
    filterType: PeqFilterType,
    color: Color,
    modifier: Modifier = Modifier.size(16.dp)
) {
    when (filterType) {
        PeqFilterType.LOW_SHELF -> GlyphFilterLowShelf(color = color, modifier = modifier)
        PeqFilterType.PEAKING -> GlyphFilterPeaking(color = color, modifier = modifier)
        PeqFilterType.HIGH_SHELF -> GlyphFilterHighShelf(color = color, modifier = modifier)
    }
}

/**
 * Ergonomic 40.dp x 40.dp Phosphor stepper button (Minus / Plus) for accurate thumb taps.
 */
@Composable
internal fun PeqStepperButton(
    isPlus: Boolean,
    enabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val onSurfaceColor = m3OnSurface()
    val disabledGrey = m3OnSurfaceVariant().copy(alpha = 0.42f)
    TactileBox(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(m3SurfaceContainerHigh().copy(alpha = if (enabled) 1.0f else 0.45f))
            .border(
                width = 0.8.dp,
                color = if (enabled) accentColor.copy(alpha = 0.24f) else onSurfaceColor.copy(alpha = if (isDark) 0.06f else 0.10f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        val iconColor = if (enabled) onSurfaceColor else disabledGrey
        if (isPlus) {
            GlyphAdd(color = iconColor, modifier = Modifier.size(18.dp))
        } else {
            GlyphMinus(color = iconColor, modifier = Modifier.size(18.dp))
        }
    }
}
