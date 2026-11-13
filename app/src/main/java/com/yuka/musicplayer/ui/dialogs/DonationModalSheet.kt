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
import androidx.compose.runtime.*
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
import com.yuka.musicplayer.util.DonationSupporter
import com.yuka.musicplayer.util.SupporterRepository

@Composable
fun DonationModalPanel(
    context: Context,
    onClose: () -> Unit
) {
    val accent = LocalAccentColor.current
    val onAccent = if (ColorContrastHelper.calculateLuminance(accent) > 0.45f) Color.Black else Color.White
    var supporters by remember { mutableStateOf(SupporterRepository.DEFAULT_SUPPORTERS) }

    LaunchedEffect(Unit) {
        supporters = SupporterRepository.getSupporters()
    }

    val openDonationUrl = {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SupporterRepository.DONATION_URL))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                    GlyphBagiBagi(color = accent, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(
                        text = "Support tsrossa",
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = m3OnSurface()
                    )
                    Text(
                        text = "Independent Bit-Perfect Audio Engine",
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
            // 1. Mission & Developer Appreciation Card
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
                            text = "Terima Kasih Dari Developer",
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = accent
                        )
                    }
                    Text(
                        text = SupporterRepository.DEVELOPER_THANK_YOU_MESSAGE,
                        fontFamily = ModernSansFont,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = m3OnSurface()
                    )
                }
            }

            // 2. Priority Feature Request Callout Banner
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
                            text = "Priority Feature Request via Donasi",
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = accent
                        )
                    }
                    Text(
                        text = SupporterRepository.PRIORITY_FEATURE_REQUEST_NOTE,
                        fontFamily = ModernSansFont,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = m3OnSurface()
                    )
                }
            }

            // 3. Supporter Wall (Daftar Donatur & Pesan Dukungan)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlyphChatTeardropText(color = accent, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Pesan Donatur & Supporter Wall",
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = m3OnSurface()
                    )
                }

                supporters.forEach { supporter ->
                    DonationSupporterCard(supporter = supporter, accentColor = accent)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        // Action Buttons (Sticky Footer)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Primary Donation Button
            TactileBox(
                onClick = openDonationUrl,
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
                    GlyphBagiBagi(color = onAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Donasi & Request Fitur (Bagi-Bagi)",
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

@Composable
private fun DonationSupporterCard(
    supporter: DonationSupporter,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(m3SurfaceContainer().copy(alpha = 0.75f))
            .border(0.6.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Supporter identity row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Avatar bubble
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (supporter.isFeatureRequest) accentColor.copy(alpha = 0.2f)
                                else Color.White.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = supporter.name.firstOrNull()?.uppercase() ?: "S",
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (supporter.isFeatureRequest) accentColor else m3OnSurface()
                        )
                    }

                    Text(
                        text = supporter.name,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = m3OnSurface()
                    )
                }

                // Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (supporter.isFeatureRequest) accentColor.copy(alpha = 0.18f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = supporter.badge,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        color = if (supporter.isFeatureRequest) accentColor else m3OnSurfaceVariant()
                    )
                }
            }

            // Supporter message
            if (supporter.message.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "\"${supporter.message}\"",
                        fontFamily = ModernSansFont,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = m3OnSurface().copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}
