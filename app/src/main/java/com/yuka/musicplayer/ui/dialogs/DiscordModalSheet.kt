package com.yuka.musicplayer.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.*
import com.yuka.musicplayer.ui.theme.*
import com.yuka.musicplayer.util.ColorContrastHelper

const val DISCORD_INVITE_URL = "https://discord.gg/VJZgdF6Fq8"

@Composable
fun DiscordModalPanel(
    context: Context,
    onClose: () -> Unit
) {
    val accent = LocalAccentColor.current
    val onAccent = if (ColorContrastHelper.calculateLuminance(accent) > 0.45f) Color.Black else Color.White

    val openDiscordUrl = {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_INVITE_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Intent fallback
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        // Modal Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphDiscord(color = accent, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(
                        text = "tsrossa Community",
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = m3OnSurface()
                    )
                    Text(
                        text = "Server Discord & Bug Report",
                        fontFamily = ModernSansFont,
                        fontSize = 12.sp,
                        color = m3OnSurfaceVariant()
                    )
                }
            }

            ModalCloseButton(onClick = onClose)
        }

        // Scrollable Body
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Bug Report & Technical Support Callout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(m3SurfaceContainerHigh().copy(alpha = 0.7f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GlyphSparkle(color = accent, modifier = Modifier.size(15.dp))
                        Text(
                            text = "Pusat Laporan Bug & Masalah USB DAC",
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = accent
                        )
                    }
                    Text(
                        text = "Menemukan bug, audio glitch, lag, kendala deteksi USB DAC, atau format audio tidak kompatibel? Laporkan langsung di server Discord tsrossa. Kamu bisa melampirkan System Logs agar dapat dianalisis dan diperbaiki segera oleh developer.",
                        fontFamily = ModernSansFont,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = m3OnSurface()
                    )
                }
            }

            // 2. Community & Audiophile Discussion Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Text(
                            text = "Sharing Preset PEQ & Diskusi Komunitas",
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = accent
                        )
                    }
                    Text(
                        text = "Bergabunglah dengan sesama penggemar bit-perfect audio! Bagikan racikan profil 10-Band Parametric EQ favoritmu, diskusikan kecocokan IEM / headphone / DAC, request fitur terbaru, dan dapatkan pengumuman rilis lebih awal.",
                        fontFamily = ModernSansFont,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = m3OnSurface()
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        // Action Buttons (Sticky Footer)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Primary Discord Button
            TactileBox(
                onClick = openDiscordUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GlyphDiscord(color = onAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gabung Server Discord (Lapor Bug & Diskusi)",
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = onAccent
                    )
                }
            }

            // Secondary Dismiss Button
            TactileBox(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(m3SurfaceContainerHigh().copy(alpha = 0.6f))
            ) {
                Text(
                    text = "Lanjut ke Aplikasi",
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = m3OnSurfaceVariant()
                )
            }
        }
    }
}
