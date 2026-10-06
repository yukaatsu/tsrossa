package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.TerminalWhite

@Composable
fun PermissionStatusRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    grantedText: String,
    deniedText: String,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isGranted) Color(0xFF00E676).copy(alpha = 0.35f) else Color(0xFFFF9100).copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .background(Color(0xFF141414), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = TerminalWhite, fontFamily = TerminalFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TerminalGray, fontFamily = TerminalFont, fontSize = 9.sp)
            Text(
                text = if (isGranted) grantedText else deniedText,
                color = if (isGranted) Color(0xFF00E676) else Color(0xFFFF9100),
                fontFamily = TerminalFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .border(1.dp, LocalAccentColor.current.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .clickable { onActionClick() }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text("MANAGE", color = LocalAccentColor.current, fontFamily = TerminalFont, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PermissionCard(
    icon: String,
    title: String,
    badge: String,
    isGranted: Boolean,
    isEssential: Boolean,
    desc: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    val borderColor = if (isGranted) Color(0xFF00E676).copy(alpha = 0.4f)
    else if (isEssential) Color(0xFFFF9100).copy(alpha = 0.5f)
    else TerminalGray.copy(alpha = 0.3f)

    val badgeColor = if (isGranted) Color(0xFF00E676)
    else if (isEssential) Color(0xFFFF9100)
    else TerminalGray

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .background(Color(0xFF121212), RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(icon, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, color = TerminalWhite, fontFamily = TerminalFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "[ $badge ]",
                color = badgeColor,
                fontFamily = TerminalFont,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(desc, color = TerminalWhite.copy(alpha = 0.75f), fontFamily = TerminalFont, fontSize = 10.sp, lineHeight = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))

        if (!isGranted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, badgeColor, RoundedCornerShape(4.dp))
                    .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                    .clickable { onAction() }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(actionLabel, color = badgeColor, fontFamily = TerminalFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓", color = Color(0xFF00E676), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Text(actionLabel, color = TerminalGray, fontFamily = TerminalFont, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun PermissionSetupDialog(
    isNotificationGranted: Boolean,
    isStorageGranted: Boolean,
    isDndGranted: Boolean,
    onRequestNotification: () -> Unit,
    onRequestStorage: () -> Unit,
    onRequestDnd: () -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚡", color = LocalAccentColor.current, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SYSTEM ACCESS SETUP",
                    color = TerminalWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont,
                    letterSpacing = 0.5.sp
                )
            }
            if (isStorageGranted) {
                Box(
                    modifier = Modifier
                        .border(1.dp, LocalAccentColor.current.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .clickable { onComplete() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("✕", color = LocalAccentColor.current, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LocalAccentColor.current.copy(alpha = 0.3f)))
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "tsrossa membutuhkan beberapa izin sistem Android agar engine bit-perfect C++ dan kontrol pemutar musik dapat berfungsi optimal:",
            color = TerminalWhite.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontFamily = TerminalFont,
            lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                PermissionCard(
                    icon = "📁",
                    title = "AKSES PENYIMPANAN MUSIK",
                    badge = if (isStorageGranted) "DIIZINKAN ✅" else "WAJIB DIIZINKAN ⚠️",
                    isGranted = isStorageGranted,
                    isEssential = true,
                    desc = "Dibutuhkan agar engine C++ (dr_flac & dr_wav) dapat membaca file FLAC & WAV secara langsung dari memori internal atau MicroSD.",
                    actionLabel = if (isStorageGranted) "SUDAH DIIZINKAN" else "BERI IZIN PENYIMPANAN",
                    onAction = onRequestStorage
                )
            }

            item {
                PermissionCard(
                    icon = "🔔",
                    title = "IZIN NOTIFIKASI & LOCKSCREEN",
                    badge = if (isNotificationGranted) "DIIZINKAN ✅" else "DIBUTUHKAN ⚠️",
                    isGranted = isNotificationGranted,
                    isEssential = true,
                    desc = "Dibutuhkan pada Android 13+ untuk menampilkan kontrol pemutar musik (MediaStyle) di bar notifikasi & lockscreen, serta status DAC Bit-Perfect.",
                    actionLabel = if (isNotificationGranted) "SUDAH DIIZINKAN" else "IZINKAN NOTIFIKASI",
                    onAction = onRequestNotification
                )
            }

            item {
                PermissionCard(
                    icon = "🔕",
                    title = "MODE JANGAN GANGGU (DND)",
                    badge = if (isDndGranted) "DIIZINKAN ✅" else "OPSIONAL ℹ️",
                    isGranted = isDndGranted,
                    isEssential = false,
                    desc = "Mengheningkan nada dering/notifikasi otomatis saat lagu berputar agar tidak menginterupsi stream bit-perfect dan melindungi telinga saat memakai IEM.",
                    actionLabel = if (isDndGranted) "SUDAH AKTIF" else "ATUR DND (OPSIONAL)",
                    onAction = onRequestDnd
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, LocalAccentColor.current.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .background(Color(0xFF101010), RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔌", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("KONEKSI USB DAC (UAC1/UAC2)", color = TerminalWhite, fontFamily = TerminalFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("[ OTOMATIS ]", color = LocalAccentColor.current, fontFamily = TerminalFont, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Saat USB DAC dicolokkan ke port USB ponsel, Android akan menampilkan prompt izin. Centang 'Selalu izinkan' dan tekan OK untuk akses direct hardware.",
                        color = TerminalGray,
                        fontFamily = TerminalFont,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!isStorageGranted) {
            Text(
                text = "⚠️ Mohon berikan Izin Penyimpanan agar tsrossa dapat membaca file musik Anda.",
                color = Color(0xFFFF9100),
                fontFamily = TerminalFont,
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Button(
            onClick = onComplete,
            enabled = isStorageGranted,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = LocalAccentColor.current,
                disabledContainerColor = Color(0xFF222222)
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Text(
                text = if (isStorageGranted) "LANJUTKAN KE PEMUTAR MUSIK ▶" else "BERIKAN IZIN DI ATAS UNTUK MELANJUTKAN",
                fontFamily = TerminalFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isStorageGranted) Color.Black else TerminalGray
            )
        }
    }
}
