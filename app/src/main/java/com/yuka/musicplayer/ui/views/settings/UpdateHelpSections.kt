package com.yuka.musicplayer.ui.views.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.BuildConfig
import com.yuka.musicplayer.R
import com.yuka.musicplayer.ui.components.TactileBox
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalWhite
import com.yuka.musicplayer.update.UpdateCheckState



@Composable
fun ApplicationUpdatesSection(
    updateCheckState: UpdateCheckState,
    onOpenUpdateDialog: () -> Unit
) {
    val accent = LocalAccentColor.current

    SettingsCard(title = "Application Updates") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(com.yuka.musicplayer.ui.theme.m3SubtleFill(alpha = 0.06f))
                .clickable { onOpenUpdateDialog() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = "Release v${BuildConfig.VERSION_NAME}",
                    color = com.yuka.musicplayer.ui.theme.m3OnSurface(),
                    fontFamily = ModernSansFont,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                val statusDesc = when (updateCheckState) {
                    is UpdateCheckState.Checking -> "Checking GitHub releases..."
                    is UpdateCheckState.UpToDate -> "Application is up to date"
                    is UpdateCheckState.UpdateAvailable -> "New version ${updateCheckState.release.tagName} available!"
                    is UpdateCheckState.Error -> "Check failed: ${updateCheckState.message}"
                    is UpdateCheckState.Idle -> "Tap to scan for updates"
                }
                Text(
                    text = statusDesc,
                    color = when (updateCheckState) {
                        is UpdateCheckState.UpdateAvailable -> Color(0xFF00E676)
                        is UpdateCheckState.Checking -> Color(0xFFFFD700)
                        is UpdateCheckState.Error -> Color.Red
                        else -> com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant()
                    },
                    fontFamily = ModernSansFont,
                    fontSize = 11.5.sp
                )
            }

            TactileBox(
                onClick = onOpenUpdateDialog,
                pressedScale = 0.90f,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (updateCheckState is UpdateCheckState.UpdateAvailable) Color(0xFF00E676).copy(alpha = 0.20f)
                        else accent.copy(alpha = 0.16f)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (updateCheckState is UpdateCheckState.UpdateAvailable) "VIEW" else "CHECK",
                    color = if (updateCheckState is UpdateCheckState.UpdateAvailable) Color(0xFF00E676) else accent,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "CHANGELOG & RELEASE NOTES",
            color = accent,
            fontSize = 11.sp,
            fontFamily = ModernSansFont,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        val changelogRelease = when (updateCheckState) {
            is UpdateCheckState.UpdateAvailable -> updateCheckState.release
            is UpdateCheckState.UpToDate -> updateCheckState.release
            else -> null
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh())
                .padding(14.dp)
        ) {
            Text(
                text = changelogRelease?.tagName?.let { "Release $it" } ?: "v${BuildConfig.VERSION_NAME} (Installed)",
                color = com.yuka.musicplayer.ui.theme.m3OnSurface(),
                fontFamily = ModernSansFont,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = changelogRelease?.changelog?.takeIf { it.isNotBlank() }
                    ?: "• Bit-Perfect 64-bit DSP & 32-bit Hardware USB DAC Engine\n• Per-DAC Smart Volume Memory & Safety Defaults\n• Pin Playlists, Queue Fuzzy Search & Artwork Thumbnails\n• Progressive Swipe-Up Queue & Animated Synced Lyrics\n• Full Material 3 Light & Dark Mode Contrast",
                color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                fontFamily = ModernSansFont,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
fun DeveloperInfoSection(
    onOpenLogs: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accent = LocalAccentColor.current

    SettingsCard(title = "Developer") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(com.yuka.musicplayer.ui.theme.m3SubtleFill(alpha = 0.06f))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.profile),
                    contentDescription = "Developer Profile Picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Developed by Yuka (@yukaatsu)",
                        color = com.yuka.musicplayer.ui.theme.m3OnSurface(),
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "tsrossa • Bit-perfect USB Audio Engine",
                        color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                        fontFamily = ModernSansFont,
                        fontSize = 11.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Crafted with Jetpack Compose & custom low-latency C++ / libusb kernel streaming engine for audiophile hardware DACs.",
                color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                fontFamily = ModernSansFont,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Full-width GitHub Repository Button (App Logs button removed per spec)
            TactileBox(
                onClick = {
                    try {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/yukaatsu/tsrossa")
                        ).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Could not open browser", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                pressedScale = 0.94f,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f))
                    .padding(vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GitHub Repository ↗",
                        color = accent,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DonationSupportSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accent = LocalAccentColor.current

    SettingsCard(title = "Donation & Support") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(com.yuka.musicplayer.ui.theme.m3SubtleFill(alpha = 0.06f))
                .padding(14.dp)
        ) {
            Text(
                text = "Support the continued development of tsrossa and its bit-perfect USB DAC audio engine.",
                color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                fontFamily = ModernSansFont,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            TactileBox(
                onClick = {
                    try {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://bagibagi.co/Yukaatsu")
                        ).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Could not open browser", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                pressedScale = 0.94f,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f))
                    .padding(vertical = 10.dp, horizontal = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bagi-Bagi (bagibagi.co) ↗",
                        color = accent,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
