# Laporan Audit Analisa UI: Penyempurnaan Developer Card & Information Architecture (`test-bug.md`)

**Tanggal Audit:** 31 Oktober 2026  
**Disusun Oleh:** Senior Android UI/UX & Systems Engineer  
**Ditujukan Untuk:** Junior Android Engineer (Panduan Mentoring Teknis Mendalam)  
**Target Berkas Masalah:** [`test-bug.md`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/test-bug.md)  
**Berkas Sumber Utama:** [`app/src/main/java/com/yuka/musicplayer/ui/views/settings/UpdateHelpSections.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/views/settings/UpdateHelpSections.kt)

---

## 1. Kata Pengantar dari Senior untuk Junior

Halo Junior! Mari kita telaah catatan terbaru pada berkas [`test-bug.md`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/test-bug.md):

```markdown
#BUG
1.hilangkan icon tanda panah keatas di developer card dan hilangkan link donasi yang berada di samping link github.
```

Dalam rekayasa antarmuka pengguna (*UI/UX Engineering*), masukan seperti ini sangat berharga karena berkaitan langsung dengan **Information Architecture (IA)**, **Visual Hierarchy**, dan **Cognitive Load**.

Ketika pengguna meminta:
1. **Menghilangkan tautan/tombol donasi yang berada di samping tautan GitHub di Developer Card**, dan
2. **Menghilangkan ikon/simbol tanda panah ke atas (`↗`) pada Developer Card**,

Ada dua pelajaran fundamental yang perlu kamu pahami sebagai seorang engineer:

### Prinsip 1: Separation of Concerns dalam Desain UI
Kartu **Developer Card (`DeveloperInfoSection`)** memiliki tujuan utama (*Primary Purpose*) untuk memperkenalkan kreator/pembuat aplikasi, filosofi rancang bangun sistem (*bit-perfect USB audio engine*), dan menyediakan akses ke repositori kode sumber (*open-source repository* di GitHub).  
Sementara itu, tepat di bawahnya sudah tersedia kartu khusus **`Donation & Support` (`DonationSupportSection`)**.  
Ketika kita menaruh tombol donasi Bagi-Bagi ke dalam Developer Card berdampingan dengan tombol GitHub, terjadi **duplikasi fungsional (*UI Redundancy*)** yang membingungkan pengguna: *"Mengapa tombol donasi ada di dalam kartu profil developer dan ada lagi di kartu donasi tepat di bawahnya?"*. Selain itu, membagi kartu menjadi dua kolom sempit (`weight(1f)`) membuat kedua tombol terasa sesak di layar ponsel.

### Prinsip 2: Visual Affordance & Clutter Reduction
Simbol panah diagonal ke atas (`↗` atau *North-East Arrow*) secara historis digunakan di web untuk menandai tautan keluar (*external hyperlink*). Namun, dalam aplikasi mobile modern berbasis Material Design 3 / iOS HIG, ketika sebuah tombol sudah dilengkapi dengan ikon resmi identitas jenama (yaitu **`GlyphGitHub` / Octocat**), keberadaan teks tambahan `↗` justru menjadi **derau visual (*visual noise/clutter*)**. Ikon GitHub yang elegan bersama teks tebal `"GitHub Repository"` sudah memberikan *affordance* yang sangat kuat dan intuitif bahwa tombol tersebut akan membuka repositori proyek.

Mari kita bedah kode saat ini secara rinci.

---

## 2. Bedah Forensik Kode Saat Ini (*Codebase Forensic Audit*)

### 2.1. Lokasi Kode Bermasalah
Berkas: [`app/src/main/java/com/yuka/musicplayer/ui/views/settings/UpdateHelpSections.kt`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/views/settings/UpdateHelpSections.kt)  
Fungsi: [`DeveloperInfoSection()`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/views/settings/UpdateHelpSections.kt#L142-L285) (baris 202–282)

```kotlin
// KODE SAAT INI DI UpdateHelpSections.kt (Baris 202-282):

// Dual Action Buttons: GitHub Repository & Bagi-Bagi Donation
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp)
) {
    // GitHub Repository Button
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
            .weight(1f) // <-- MEMBAGI DUA KOLOM
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.16f))
            .padding(vertical = 10.dp, horizontal = 8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphGitHub(color = accent, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "GitHub ↗", // <-- TERDAPAT SIMBOL TANDA PANAH KE ATAS (↗)
                color = accent,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }

    // Bagi-Bagi Donation Button <-- MENIMBULKAN REDUNDANSI & CLUTTER DI DEVELOPER CARD
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
            .weight(1f) // <-- MEMBAGI DUA KOLOM
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.16f))
            .padding(vertical = 10.dp, horizontal = 8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphBagiBagi(color = accent, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Bagi-Bagi ↗", // <-- TERDAPAT SIMBOL TANDA PANAH KE ATAS (↗)
                color = accent,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
```

---

## 3. Analisis Dampak Antarmuka Pengguna (*UI Defect Analysis*)

### 3.1. Masalah 1: Simbol Panah Ke Atas (`↗`) Mengganggu Kerapian Tipografi
* **Gejala:** Teks tombol bertuliskan `"GitHub ↗"` dan `"Bagi-Bagi ↗"`.
* **Akar Masalah:** Karakter unicode `↗` (U+2197: North East Arrow) memiliki *baseline* dan *bounding box* font yang bervariasi antar perangkat Android (tergantung sistem font OEM seperti Samsung One UI, Google Roboto, dll.). Pada beberapa layar, tanda panah ini tampak terlalu tinggi atau miring tidak proporsional dibandingkan tinggi huruf kapital modern sans.
* **Solusi Ideal:** Hilangkan karakter `↗` secara menyeluruh pada Developer Card. Teks cukup ditulis `"GitHub Repository"`. Ikon vektor resmi [`GlyphGitHub`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/components/AudioGlyphs.kt#L520-L530) (Octocat) berukuran `18.dp` sudah bertindak sebagai *visual anchor* yang sempurna dan berkelas.

### 3.2. Masalah 2: Penempatan Tombol Donasi di Samping Tombol GitHub Menghancurkan Proporsi Card
* **Gejala:** Developer card memuat dua tombol sempit yang dijejerkan secara horizontal dengan `Modifier.weight(1f)`.
* **Akar Masalah:** Developer card seharusnya fokus pada identitas pengembang dan tautan proyek open-source. Tautan donasi yang disematkan berdampingan menciptakan tumpang tindih fungsi dengan kartu `DonationSupportSection` yang berada tepat di bawahnya.
* **Solusi Ideal:**
  1. Hapus total tombol Bagi-Bagi dari dalam Developer Card.
  2. Kembalikan tombol GitHub menjadi **Full-Width Action Button (`fillMaxWidth()`)** yang elegan, berjarak sentuh lega (*ergonomic tactile target* sesuai standar HIG minimum 44dp), dengan ikon `GlyphGitHub` di kiri dan label bersih `"GitHub Repository"`.
  3. Biarkan tautan donasi Bagi-Bagi berada secara eksklusif di dalam kartu **Donation & Support (`DonationSupportSection`)** di bawahnya, lengkap dengan ikon resminya [`GlyphBagiBagi`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/components/AudioGlyphs.kt#L532-L545), dan juga tanpa simbol panah `↗` agar desainnya selaras (*consistent*).

---

## 4. Matriks Ringkasan Temuan Audit (*Findings Summary Matrix*)

| ID | Komponen UI | Kondisi Saat Ini | Kondisi yang Diharapkan User | Aksi Perbaikan |
| :--- | :--- | :--- | :--- | :--- |
| **UI-01** | Developer Card (`DeveloperInfoSection`) | Memuat tombol donasi Bagi-Bagi di samping tombol GitHub (`weight(1f)`). | Tombol donasi di samping tombol GitHub **dihilangkan**. | Hapus tombol donasi dari Developer Card; jadikan tombol GitHub kembali satu tombol lebar penuh (`fillMaxWidth()`). |
| **UI-02** | Developer Card (`DeveloperInfoSection`) | Label tombol GitHub menggunakan teks `"GitHub ↗"` dengan panah diagonal. | Ikon tanda panah ke atas (`↗`) **dihilangkan**. | Ubah label menjadi `"GitHub Repository"` murni tanpa tanda panah `↗`, didampingi ikon vektor `GlyphGitHub`. |
| **UI-03** | Donation Card (`DonationSupportSection`) | Label tombol menggunakan `"Bagi-Bagi (bagibagi.co) ↗"`. | Simbol panah diselaraskan dengan estetika minimalis. | Hapus tanda panah `↗` pada kartu donasi sehingga bersih menjadi `"Bagi-Bagi (bagibagi.co)"` dengan `GlyphBagiBagi`. |

---

## 5. Kesimpulan Audit

Permintaan pada [`test-bug.md`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/test-bug.md) sangat jelas dan terarah. Perubahan ini akan:
1. Mengembalikan hierarki visual Developer Card menjadi bersih, profesional, dan fokus.
2. Menghilangkan redundansi tombol donasi antara Developer Card dan Donation Section.
3. Menghilangkan derau visual dari simbol panah `↗` yang tidak serasi.

Silakan membaca berkas implementasi dan rencana eksekusi terperinci di [`AUDIT-PLANNING/planning-developer-card.md`](file:///Users/yuka/Documents/Projects/tsrossa-ujicoba/AUDIT-PLANNING/planning-developer-card.md).
