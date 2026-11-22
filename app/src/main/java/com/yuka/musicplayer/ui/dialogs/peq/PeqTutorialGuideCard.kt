package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.yuka.musicplayer.ui.components.GlyphClose
import com.yuka.musicplayer.ui.components.GlyphQuestion
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerLow

/**
 * In-Sheet Interactive Guide Card (100% Phosphor Icons, Full English).
 */
@Composable
internal fun PeqTutorialGuideCard(
    accentColor: Color,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(m3SurfaceContainerLow())
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlyphQuestion(color = accentColor, modifier = Modifier.size(18.dp))
                Text(
                    text = "10-Band Parametric EQ Guide",
                    color = m3OnSurface(),
                    fontSize = 13.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GlyphClose(color = accentColor, modifier = Modifier.size(13.dp))
                Text(
                    text = "Close",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        PeqGuideSection(
            title = "1. Quick Start",
            accentColor = accentColor,
            body = "• Drag the 10 vertical faders up (+dB) or down (-dB) in 0.1 dB steps to shape your sound. Double-tap any fader to reset it to 0.0 dB.\n" +
                "• Select any band to adjust its filter type (Low Shelf / Peaking / High Shelf), center frequency, or Q-Factor in the editor below.\n" +
                "• Use Save to store or delete custom presets, or Export / Import to share preset codes with the community."
        )

        PeqGuideSection(
            title = "2. Parameter Glossary",
            accentColor = accentColor,
            body = "• Gain (dB): Boost (+) or cut (-) amount in 0.1 dB increments. Adjustments of ±1.5 to ±4.0 dB sound most natural.\n" +
                "• Q-Factor: Bandwidth of the filter curve. Q = 1.41 is the true 1-octave Constant-Q standard for clean instrument separation. Lower Q (<0.9) is wider; higher Q (>2.0) is narrower.\n" +
                "• Auto-Clip: Automatically maintains 100% linear headroom (zero clipping/IMD) whenever you boost frequencies."
        )

        PeqGuideSection(
            title = "3. 10-Band Acoustic Map (31 Hz – 16 kHz)",
            accentColor = accentColor,
            body = "• 31 Hz – 125 Hz (Bass): Sub-bass rumble, kick drum punch, and lower body warmth.\n" +
                "• 250 Hz – 1 kHz (Mids & Vocals): Instrument thickness (cut 250 Hz slightly to remove mud) and vocal intimacy (1 kHz).\n" +
                "• 2 kHz – 4 kHz (Clarity & Attack): Vocal articulation, string definition, and transient bite.\n" +
                "• 8 kHz – 16 kHz (Soundstage & Air): Cymbal sparkle, soundstage width, and fine micro-detail."
        )
    }
}

@Composable
internal fun PeqGuideSection(
    title: String,
    accentColor: Color,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(m3SurfaceContainerHigh().copy(alpha = 0.5f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            color = accentColor,
            fontSize = 11.5.sp,
            fontFamily = ModernSansFont,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = body,
            color = m3OnSurface(),
            fontSize = 10.5.sp,
            fontFamily = ModernSansFont,
            lineHeight = 15.sp
        )
    }
}
