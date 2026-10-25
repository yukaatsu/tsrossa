package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.yuka.musicplayer.ui.components.GlyphClose
import com.yuka.musicplayer.ui.components.ModalCloseButton
import com.yuka.musicplayer.ui.components.TactileBox
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant

@Composable
fun HelpSectionHeader(title: String) {
    Text(
        text = title,
        color = LocalAccentColor.current,
        fontFamily = ModernSansFont,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.05.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun HelpItem(title: String, desc: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(com.yuka.musicplayer.ui.theme.m3SurfaceContainer())
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .padding(bottom = 2.dp)
    ) {
        Text(
            text = title,
            color = m3OnSurface(),
            fontFamily = ModernSansFont,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = desc,
            color = m3OnSurfaceVariant(),
            fontFamily = ModernSansFont,
            fontSize = 12.sp,
            lineHeight = 16.5.sp
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun HelpPanel(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "User Guide & Manual",
                    color = m3OnSurface(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ModernSansFont
                )
                Text(
                    text = "Bit-perfect UAC2 playback and control tips",
                    color = m3OnSurfaceVariant(),
                    fontSize = 12.sp,
                    fontFamily = ModernSansFont
                )
            }

            ModalCloseButton(onClick = onClose)
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            item {
                HelpSectionHeader("01. USB DAC & BIT-PERFECT AUDIO")
                HelpItem("Bit-Perfect UAC2 Streaming", "Audio is streamed directly to your external USB DAC via exclusive asynchronous libusb isochronous transfers, bypassing the Android mixer and resampler for 100% bit-perfect signal purity.")
                HelpItem("Volume Dot Indicator", "Amber Dot: 32-bit hardware volume control active in DAC chip.\nRed Dot: 64-bit dithered digital software attenuation.")
                HelpItem("Do Not Disturb (DND) Integration", "Automatically silences system notifications during audio playback to prevent audio interruptions and sample rate renegotiations.")
            }

            item {
                HelpSectionHeader("02. TRANSPORT & PLAYBACK CONTROLS")
                HelpItem("Playback Navigation", "Previous / Replay [ |<< ] | Play / Pause [ ▶ / ❚❚ ] | Next [ >>| ] with smooth tactile feedback.")
                HelpItem("Precision Volume Stepper", "[-] and [+] buttons adjust volume in exact 5% increments with optional volume locking.")
                HelpItem("Gapless Playback Engine", "The C++ engine preloads upcoming track frames into memory before the active track concludes, ensuring zero silence gaps between tracks.")
            }

            item {
                HelpSectionHeader("03. SHUFFLE, REPEAT & QUEUE")
                HelpItem("Shuffle (Non-destructive)", "Reorders the playback queue without altering your original files or playlist structure.")
                HelpItem("Repeat Modes", "• Off: Stops after the final track.\n• All: Repeats the entire list.\n• One: Repeats the current track continuously.")
                HelpItem("Priority Playback Queue", "Tracks queued via 'Play Next' or 'Add to Queue' take precedence before normal playback resumes.")
            }

            item {
                HelpSectionHeader("04. GESTURES & FAST ACTIONS")
                HelpItem("Instant Play", "Tap any audio file in Library or Playlist to play immediately.")
                HelpItem("Quick Action Sheet", "Tap the 3-dots icon or long-press any track to play next, add to queue, or manage playlist membership.")
                HelpItem("Root Folder & Instant Filter", "Use the search bar to filter your library instantly, or tap the folder icon to switch root music directories.")
            }

            item {
                HelpSectionHeader("05. THEMES & SYSTEM ACCESS")
                HelpItem("Material You Dynamic Accents", "Extracts primary and secondary tonal colors dynamically from your active album artwork.")
                HelpItem("Keep Screen Awake", "Keeps the screen active while on the Now Playing screen during music playback.")
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
