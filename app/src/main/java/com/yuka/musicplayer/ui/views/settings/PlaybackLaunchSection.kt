package com.yuka.musicplayer.ui.views.settings

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.GlyphBolt
import com.yuka.musicplayer.ui.components.GlyphWarning
import com.yuka.musicplayer.ui.components.TactileBox
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray

@Composable
fun PlaybackLaunchSection(
    prefs: SharedPreferences,
    keepAwake: Boolean,
    onKeepAwakeChange: (Boolean) -> Unit,
    defaultScreen: String,
    onDefaultScreenChange: (String) -> Unit,
    usbBufferProfile: Int = 1,
    onUsbBufferProfileChange: (Int) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isSamsung = remember { com.yuka.musicplayer.util.SamsungAudioEffectHelper.isSamsungDevice() }

    SettingsCard(title = "Playback & Audio Tuning") {
        SettingsToggleRow(
            title = "Keep screen awake",
            subtitle = "Prevent display timeout when audio is actively playing",
            isChecked = keepAwake,
            onCheckedChange = { awake ->
                onKeepAwakeChange(awake)
                prefs.edit().putBoolean("keep_awake", awake).apply()
            }
        )

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Default screen on launch",
            color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
            fontFamily = ModernSansFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        val screens = listOf(
            "LIBRARY" to "Library",
            "PLAYLIST" to "Playlist",
            "SETTINGS" to "Settings"
        )
        val currentScreen = if (defaultScreen == "TRACK") "LIBRARY" else defaultScreen
        SettingsSegmentedControl(
            items = screens,
            selectedItem = currentScreen,
            onItemSelected = { screen ->
                onDefaultScreenChange(screen)
                prefs.edit().putString("default_screen", screen).apply()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "USB BUFFER DEPTH (JITTER PROFILE)",
            color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
            fontFamily = ModernSansFont,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        val bufferProfiles = listOf(
            0 to "64ms Ultra",
            1 to "192ms Jitter",
            2 to "500ms Stable"
        )
        SettingsSegmentedControl(
            items = bufferProfiles,
            selectedItem = usbBufferProfile,
            onItemSelected = { profile ->
                onUsbBufferProfileChange(profile)
                prefs.edit().putInt("usb_buffer_profile", profile).apply()
                com.yuka.musicplayer.audio.AudioEngine.applyBufferProfile(profile)
            }
        )

        if (isSamsung) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFFB300).copy(alpha = 0.12f))
                    .padding(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        GlyphWarning(color = Color(0xFFFFB300), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Samsung Dolby Atmos Bypass",
                            color = Color(0xFFFFB300),
                            fontFamily = ModernSansFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Samsung One UI forces Dolby Atmos DSP on USB. For pure bit-perfect imaging & wide soundstage, turn Dolby Atmos and Equalizer OFF.",
                        color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                        fontFamily = ModernSansFont,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                TactileBox(
                    onClick = { com.yuka.musicplayer.util.SamsungAudioEffectHelper.openSoundQualitySettings(context) },
                    pressedScale = 0.90f,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFFFB300).copy(alpha = 0.25f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "SETUP",
                        color = Color(0xFFFFB300),
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(com.yuka.musicplayer.ui.theme.m3SubtleFill(alpha = 0.06f))
                .padding(12.dp)
        ) {
            Column {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    GlyphBolt(color = Color(0xFFFF9100), modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "USB OTG Power Advisory",
                        color = Color(0xFFFF9100),
                        fontFamily = ModernSansFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Phone USB-C ports deliver max ~500mA. Planar or high-impedance headphones can cause dynamic compression on intense bass transients. For best authority, consider a power injection OTG cable.",
                    color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                    fontFamily = ModernSansFont,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
