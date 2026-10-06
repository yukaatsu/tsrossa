package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.diagnostics.LogSectionHeader
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalWhite

@Composable
fun HelpItem(title: String, desc: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = "• $title",
            color = LocalAccentColor.current,
            fontFamily = TerminalFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = desc,
            color = TerminalWhite.copy(alpha = 0.85f),
            fontFamily = TerminalFont,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(start = 10.dp, top = 2.dp)
        )
    }
}

@Composable
fun HelpPanel(onClose: () -> Unit) {
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
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(1.dp, LocalAccentColor.current, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", color = LocalAccentColor.current, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TSROSSA USER MANUAL",
                    color = TerminalWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont,
                    letterSpacing = 0.5.sp
                )
            }
            Box(
                modifier = Modifier
                    .border(1.dp, Color.Red.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("✕ CLOSE", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LocalAccentColor.current.copy(alpha = 0.25f)))
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            item {
                LogSectionHeader("01. USB DAC & AUDIO BIT-PERFECT")
                HelpItem("Bit-Perfect UAC2 Streaming", "Audio dialirkan langsung ke hardware DAC eksternal melalui USB Isochronous libusb eksklusif, melewati audio mixer & resampler Android demi kemurnian sinyal 100%.")
                HelpItem("Indikator Titik Volume (Dot)", "🟡 Emas: 32-bit hardware volume control di chip DAC aktif.\n🔴 Merah: 64-bit dithered software attenuation.")
                HelpItem("Mode DND (Do Not Disturb)", "Mengheningkan notifikasi suara sistem secara otomatis saat musik diputar untuk mencegah gangguan audio pada stream bit-perfect.")
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                LogSectionHeader("02. KONTROL TRANSPORT & VOLUME")
                HelpItem("Navigasi Playback", "[ |<< ] Track Sebelumnya / Replay | [ ▶ / ❚❚ ] Play/Pause | [ >>| ] Track Berikutnya.")
                HelpItem("Kontrol Volume Presisi", "Tombol [-] dan [+] mengatur level volume secara presisi bertahap 5%.")
                HelpItem("Gapless Playback Engine", "Engine C++ otomatis me-load lagu berikutnya ke memori buffer sebelum lagu saat ini selesai untuk transisi 0-jeda tanpa jeda hening.")
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                LogSectionHeader("03. SHUFFLE, REPEAT & QUEUE")
                HelpItem("🔀 Shuffle (Acak)", "Mengacak urutan putar lagu secara non-destruktif tanpa merusak urutan asli file atau daftar putar.")
                HelpItem("🔁 Repeat (3 Mode)", "• OFF: Memutar hingga lagu terakhir dan berhenti.\n• ALL: Mengulang daftar playlist dari awal saat lagu terakhir usai.\n• 1: Mengulang lagu saat ini terus-menerus.")
                HelpItem("☰ Playback Queue (Antrean Prioritas)", "Lagu di antrean ini akan selalu diputar terlebih dahulu sebelum kembali ke urutan normal. Ketuk tombol [ ☰ QUEUE ] untuk melihat & mengelola antrean.")
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                LogSectionHeader("04. LIBRARY & PLAYLIST GESTURES")
                HelpItem("Pemutaran Lagu", "Ketuk sekali pada file audio untuk langsung memutarnya.")
                HelpItem("Menu Aksi Cepat (Long-Press)", "Tekan dan tahan (long-press) lagu apa pun di Library atau Playlist untuk membuka menu: Play Now, Play Next, Add to Queue, atau Add/Remove Playlist.")
                HelpItem("Navigasi Folder & Pencarian", "Ketuk 📁 [ .. ] untuk naik satu tingkat folder. Gunakan search bar > SEARCH FILES... untuk memfilter file seketika.")
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                LogSectionHeader("05. TEMA TAMPILAN & PENGATURAN")
                HelpItem("Warna Aksen Terminal", "• DYNAMIC: Mengekstrak warna aksen dari cover art album.\n• FIXED: Memilih warna tema terminal tetap (Matrix Green, Amber, Cyan, dll.).")
                HelpItem("Wallpaper Blur", "Memburamkan wallpaper latar HP Anda di belakang interface cyber HUD.")
                HelpItem("Keep Screen Awake", "Menjaga layar tetap menyala khusus saat berada di layar Now Playing selagi lagu berputar.")
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                LogSectionHeader("06. DIAGNOSTIK & SYSTEM LOGS")
                HelpItem("Panel Log [ ⚙ LOGS ]", "Ketuk tombol [ ⚙ LOGS ] di header atas kapan saja untuk memeriksa detail hardware DAC, sample rate yang ternegosiasi, status clock, dan riwayat file yang tidak kompatibel.")
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
