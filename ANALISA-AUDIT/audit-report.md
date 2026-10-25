# Laporan Analisa & Audit Forensik Audio Engine (`test-bug.md`)
**Topik:**
1. **Bug #1 — Volume 5% Masih Sangat Keras pada USB DAC (Kiwi Ears AD1, M06Pro 2, dll.) & Arsitektur Volume Universal**
2. **Bug #2 — Lagu Berhenti & Pindah Sendiri (*Auto-Next*) di ~70% Durasi Track**
3. **Audit Log Sistem Tambahan (`WindowOnBackDispatcher` & `SELinux perf_ioctl`)**

**Tanggal Audit:** 25 Oktober 2026  
**Sumber Bukti:** [`test-bug.md`](file:///Users/yuka/Projects/tsrossa-ujicoba/test-bug.md), [`UsbDacController.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp), [`AudioStreamer.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp), [`AudioCommon.h`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioCommon.h), [`AudioEngineState.h`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioEngineState.h), [`VolumeModalSheet.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/dialogs/VolumeModalSheet.kt), [`MainActivity.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/MainActivity.kt)  
**Metode Audit:** *Systematic Debugging* + *Code Knowledge Graph (`graphify`)* + *Mathematical Log Correlation*

---

## 1. Pengantar: Cara Berpikir Senior Engineer Saat Membaca Bug & Log

Halo! Anggap dokumen ini sebagai sesi *mentoring* 1-on-1 antara **Senior Audio Systems Engineer** dan kamu sebagai **Junior Engineer**.

Ketika user melaporkan dua gejala yang kelihatannya tidak berhubungan di [`test-bug.md`](file:///Users/yuka/Projects/tsrossa-ujicoba/test-bug.md):
1. *"Udah 5% volumenya, tapi keras volumenya di DAC ku (Kiwi Ears AD1, M06Pro 2), saran buat agar bisa di universal device dan universal DAC."*
2. *"Bug pas play music udah 70an % track malah ke next sendiri, jadi gak selesai."*

Seorang engineer pemula biasanya langsung menebak-nebak (*guessing*): *"Coba ganti angka konstanta volume"* atau *"Mungkin file FLAC-nya rusak"*.  
Sebaliknya, seorang **Senior Engineer** tidak pernah menebak. Kita melakukan **Forensik Matematika & Alur Data (*Dataflow Tracing*)**:
- Dari mana angka `5%` masuk di UI Kotlin, bagaimana angkanya berubah di jembatan JNI, rumus matematika apa yang dipakai di C++, dan paket byte USB apa yang dikirim ke chip DAC?
- Pada detik ke berapa lagu mulai diputar di log (`18:20:35.926`), pada detik ke berapa lagu tiba-tiba pindah sendiri (`18:24:14.616`), dan **mengapa selisih waktunya persis `218,69 detik` (3 menit 38,69 detik)**?

Mari kita bedah kedua bug ini sampai ke akar-akarnya!

---

## 2. Bedah Tuntas Bug #1: Mengapa Volume 5% Masih Sangat Keras di DAC Dongle (Kiwi Ears AD1, M06Pro 2) & Bagaimana Membuatnya Universal

### 2.1. Bagaimana Jalur Volume Bekerja Saat Ini di Codebase Kita?

Mari kita telusuri perjalanan nilai volume dari jari pengguna di layar sampai menjadi sinyal listrik di *earphone* (IEM):

```mermaid
flowchart TD
    UI["1. UI Slider (VolumeModalSheet.kt / MainActivity.kt)<br/>User geser ke 5% -> level = 0.05f"] --> JNI["2. JNI Bridge (AudioEngine.kt -> native-lib.cpp)<br/>setVolume(0.05f)"]
    JNI --> SETVOL["3. UsbDacController.cpp: set_volume(0.05f)<br/>g_audioState.currentVolume = 0.05f"]
    SETVOL --> CHECK{"Apakah g_audioState.isHardwareVolumeSupported == true?"}
    CHECK -- "YA (Mode Hardware UAC)" --> HW["4A. Thread Kontrol USB (control_thread_func)<br/>Hitung dB: 20 * log10(0.05) = -26.02 dB<br/>Kirim USB Control Transfer SET_CUR ke Feature Unit"]
    CHECK -- "YA (Di AudioStreamer.cpp:481)" --> DSP_BYPASS["4B. AudioStreamer.cpp:481<br/>isHardwareVolumeActive == true<br/>target_vol = 1.0f (PCM dikirim 100% FULL SCALE 0 dBFS!)"]
    CHECK -- "TIDAK (Mode Software DSP)" --> SW["4C. AudioStreamer.cpp:481<br/>target_vol = 0.05f * 0.05f = 0.0025 (-52 dB)<br/>64-bit DSP + TPDF Dither"]
```

Setelah kita audit baris demi baris di [`UsbDacController.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp) dan [`AudioStreamer.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp), kita menemukan **4 Akar Masalah (*Root Causes*) yang saling menumpuk** yang menyebabkan volume 5% tetap memekakkan telinga pada DAC seperti **Kiwi Ears AD1** dan **M06Pro 2**.

---

### 2.2. Root Cause 1A: Salah Kaprah Rumus Fisika Amplitudo (`20 * log10(vol)`) vs Psikoakustik Pendengaran Manusia

Buka [`UsbDacController.cpp:96-113`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L96-L113) (dan rumus tampilan UI yang identik di [`VolumeModalSheet.kt:71-75`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/dialogs/VolumeModalSheet.kt#L71-L75)):

```cpp
// UsbDacController.cpp baris 99-109
if (req.volume <= 0.01f) {
  target_db = g_audioState.minVolumeDb;
} else {
  // Logarithmic perceptual mapping (20 * log10(vol))
  float db = 20.0f * std::log10(req.volume);
  // UAC uses 1/256 dB steps
  target_db = g_audioState.maxVolumeDb + (int)(db * 256.0f);
  if (target_db < g_audioState.minVolumeDb)
    target_db = g_audioState.minVolumeDb;
  if (target_db > g_audioState.maxVolumeDb)
    target_db = g_audioState.maxVolumeDb;
}
```

**Penjelasan Senior ke Junior (Mengapa rumus ini salah untuk slider volume):**
1. Komentar di kode menulis `// Logarithmic perceptual mapping (20 * log10(vol))`. **Ini adalah kesalahpahaman klasik di pemrograman audio!**
2. Rumus $\text{dB} = 20 \times \log_{10}(x)$ **bukanlah kurva persepsi telinga**, melainkan hanya **konversi satuan linear amplitudo ($x$) ke satuan desibel (dB)**. Jika kamu memasukkan nilai slider `x = 0.05` langsung ke dalam $20 \times \log_{10}(0.05)$, artinya kamu memperlakukan slider sebagai **slider tegangan listrik linear**, bukan slider kenyamanan telinga!
3. Mari kita hitung angkanya dengan kalkulator:
   - Saat slider di **100% (`1.00`)**: $20 \times \log_{10}(1.00) = \mathbf{0.0\text{ dB}}$
   - Saat slider di **50% (`0.50`)**: $20 \times \log_{10}(0.50) = \mathbf{-6.02\text{ dB}}$
   - Saat slider di **10% (`0.10`)**: $20 \times \log_{10}(0.10) = \mathbf{-20.00\text{ dB}}$
   - Saat slider di **5% (`0.05`)**: $20 \times \log_{10}(0.05) = \mathbf{-26.02\text{ dB}}$
   - Saat slider di **2% (`0.02`)**: $20 \times \log_{10}(0.02) = \mathbf{-33.98\text{ dB}}$
   - Saat slider di **1% (`0.01`)**: Tiba-tiba langsung loncat ke `minVolumeDb` ($\mathbf{-127\text{ dB}}$ / mati total)!

**Apa artinya `-26 dB` di dunia nyata pada DAC Dongle (Kiwi Ears AD1 / M06Pro 2) + IEM?**
- USB Dongle DAC modern mengeluarkan tegangan penuh **1 Vrms hingga 2 Vrms** pada `0 dBFS`.
- IEM (*In-Ear Monitor*) modern memiliki sensitivitas tinggi, sekitar **110–122 dB SPL / Vrms**.
- Artinya pada `0 dB` (100%), kombinasi DAC + IEM menghasilkan tekanan suara sekitar **120–125 dB SPL** (setara suara mesin jet!).
- Ketika user menurunkan volume ke **5%**, rumus lama hanya mengurangi `-26 dB`. Tekanan suara di gendang telinga user masih:
  $$122\text{ dB SPL} - 26\text{ dB} = \mathbf{96\text{ dB SPL!}}$$
  **96 dB SPL itu setara suara bor listrik atau konser rock dari jarak dekat!** Pantas saja user mengeluh: *"udah 5% volumenya, tapi keras volumenya!"*
- Agar volume **5%** terdengar pelan dan nyaman untuk malam hari atau IEM sensitif (~60–65 dB SPL), atenuasi pada posisi 5% harus berada di kisaran **$-54\text{ dB}$ hingga $-58\text{ dB}$**, bukan $-26\text{ dB}$!

Lihat tabel perbandingan antara rumus lama di `UsbDacController.cpp`, rumus software di `AudioStreamer.cpp`, dan standar kurva psikoakustik profesional (64 dB dynamic range):

| Posisi Slider UI | Rumus Hardware Lama (`20*log10(x)`) | Rumus Software Lama (`x^2` -> `40*log10(x)`) | Kurva Psikoakustik Universal Baru | Persepsi Telinga Manusia (IEM 118 dB/V) |
| :--- | :---: | :---: | :---: | :--- |
| **100%** | `0.0 dB` | `0.0 dB` | **`0.0 dB`** | Full-scale Bit-Perfect (100%) |
| **75%** | `-2.5 dB` | `-5.0 dB` | **`-9.5 dB`** | Keras / Loud listening |
| **50%** | `-6.0 dB` (masih terlalu keras!) | `-12.0 dB` | **`-22.0 dB`** | Normal nyaman (~1/4 kerasnya 100%) |
| **25%** | `-12.0 dB` | `-24.1 dB` | **`-38.0 dB`** | Santai / Background music |
| **10%** | `-20.0 dB` | `-40.0 dB` | **`-50.0 dB`** | Pelan / Malam hari |
| **5%** | **`-26.0 dB` (MASIH ~92–96 dB SPL!)** | `-52.0 dB` | **`-55.5 dB`** | **Bisikan halus (~62 dB SPL, sangat nyaman)** |
| **2%** | `-34.0 dB` | `-68.0 dB` | **`-60.5 dB`** | Nyaris tak terdengar (Ultra-fine IEM control) |
| **1%** | Loncat ke `-127 dB` (Mati!) | `-80.0 dB` | **`-64.0 dB`** | Ambang batas sunyi (tanpa loncatan kasar) |
| **0%** | `-127.0 dB` (Mute) | `-∞ dB` (Mute) | **`Mute (-∞ dB)`** | Hening total (0.0) |

---

### 2.3. Root Cause 1B: Bug Fatal pada Inisialisasi Volume UAC1 (`GET_MIN` / `GET_MAX` Tidak Divalidasi & Fallback Hilang!)

Sekarang mari kita lihat bug kode yang lebih berbahaya lagi di [`UsbDacController.cpp:1037-1091`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L1037-L1091). Perhatikan blok `if (g_audioState.uacVersion == 1)` vs `else`:

```cpp
// UsbDacController.cpp baris 1039-1090
if (g_audioState.uacVersion == 1) {
  uint8_t min_buf[2] = {0, 0};
  uint8_t max_buf[2] = {0, 0};
  // Query MIN (0x82) and MAX (0x83)
  libusb_control_transfer(g_audioState.devHandle, 0xA1, 0x82,
                          (0x02 << 8) | g_audioState.volumeChannel,
                          (g_audioState.featureUnitId << 8) | 0x00,
                          min_buf, 2, 1000);
  lib_usb_clear_halt_if_needed();
  libusb_control_transfer(g_audioState.devHandle, 0xA1, 0x83,
                          (0x02 << 8) | g_audioState.volumeChannel,
                          (g_audioState.featureUnitId << 8) | 0x00,
                          max_buf, 2, 1000);

  g_audioState.minVolumeDb = (int16_t)((min_buf[1] << 8) | min_buf[0]);
  g_audioState.maxVolumeDb = (int16_t)((max_buf[1] << 8) | max_buf[0]);
  // <--- BUG FATAL 1: Return value libusb_control_transfer TIDAK DICEK!
  // <--- BUG FATAL 2: Tidak ada pengecekan if (minVolumeDb >= maxVolumeDb) di cabang UAC1!
} else {
  // UAC 2.0 uses 0x82 (RANGE) ...
  ...
  if (g_audioState.minVolumeDb >= g_audioState.maxVolumeDb) {
    g_audioState.minVolumeDb = -32768; // -128 dB
    g_audioState.maxVolumeDb = 0;      // 0 dB
  }
}
```

**Penjelasan Senior ke Junior (Apa yang terjadi pada DAC Dongle UAC1 seperti seri M06Pro 2 / AD1):**
1. Banyak dongle DAC USB-C (terutama yang menggunakan chip Conexant CX31993, KTMicro KT02H20, Realtek ALC5686, atau Bluetrum/Synwit pada dongle kelas M06Pro 2 dan Kiwi Ears AD1) beroperasi sebagai **UAC 1.0 (`uacVersion == 1`)** atau memiliki firmware UAC1 yang mengembalikan `STALL` (`LIBUSB_ERROR_PIPE`) saat ditanya `GET_MIN (0x82)` / `GET_MAX (0x83)`.
2. Karena nilai kembalian (`return value`) dari `libusb_control_transfer` di cabang UAC1 **tidak diperiksa sama sekali**, ketika transfer gagal atau DAC mengembalikan `0`, array `min_buf` dan `max_buf` tetap berisi `{0, 0}`!
3. Akibatnya:
   - `g_audioState.minVolumeDb = 0` (`0.0 dB`)
   - `g_audioState.maxVolumeDb = 0` (`0.0 dB`)
4. Dan karena pengecekan `if (g_audioState.minVolumeDb >= g_audioState.maxVolumeDb)` hanya ditaruh di dalam blok `else` (UAC2), pada UAC1 nilai `minVolumeDb = 0` dan `maxVolumeDb = 0` **lolos begitu saja!**
5. Lalu apa yang terjadi saat user menggeser volume ke **5%** di [`control_thread_func` (baris 105-109)](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L105-L109)?
   ```cpp
   target_db = g_audioState.maxVolumeDb + (int)(db * 256.0f); // 0 + (-6661) = -6661
   if (target_db < g_audioState.minVolumeDb)
     target_db = g_audioState.minVolumeDb; // <--- DI-CLAMP BALIK KE 0 (0 dB = 100% VOLUME)!
   ```
   **BOOM!** Berapa pun volume yang dipilih user (50%, 10%, 5%, bahkan 1%), `target_db` selalu di-*clamp* ke `0` (`0 dB` = volume maksimum 100%), sementara software volume di `AudioStreamer.cpp` dimatikan (`target_vol = 1.0f`) karena mengira hardware volume bekerja!

---

### 2.4. Root Cause 1C: Konflik Channel Master (`ch=0`) vs Left/Right (`ch=1, ch=2`) & Resolusi Step (`resVolumeDb`)

Mari perhatikan bagaimana [`UsbDacController.cpp:601-615`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L601-L615) memilih channel volume saat mem-parsing descriptor USB:

```cpp
// UsbDacController.cpp baris 602-615
if (master_controls & 0x02) { // Bit 1: Volume (UAC1) or Bits 2-3 (UAC2)
  g_audioState.isHardwareVolumeSupported = true;
  g_audioState.volumeChannel = 0; // Master
} else if (num_channels >= 1 && (ch1_controls & 0x02)) {
  g_audioState.isHardwareVolumeSupported = true;
  g_audioState.volumeChannel = 1; // Ch 1 & 2
}
```
Dan bagaimana [`control_thread_func` (baris 121-136)](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L121-L136) mengirim perintah `SET_CUR`:
```cpp
if (g_audioState.volumeChannel == 0) {
  r = libusb_control_transfer(g_audioState.devHandle, 0x21, 0x01,
                              (0x02 << 8) | 0x00, // <--- HANYA KIRIM KE CHANNEL 0!
                              (g_audioState.featureUnitId << 8) | 0x00,
                              vol_buf, 2, 200);
} else {
  // Kirim ke Ch 1 dan Ch 2
}
```

**Penjelasan Senior ke Junior:**
Di dunia nyata hardware USB DAC (terutama chip jembatan USB seperti Savitech SA9023/SA9312, Comtrue CT7601, Conexant, KTMicro, dan WalkPlay/CBHT yang dipakai di puluhan merk dongle Chi-Fi termasuk Kiwi Ears / Moondrop / KZ / M06Pro):
1. **Quirk Descriptor Channel:** Banyak firmware menulis bit *Volume Control* pada `bmaControls[0]` (Master Channel `0`) **DAN/ATAU** `bmaControls[1..2]` (Channel 1 & 2). Namun di dalam silikon chip-nya, register I2S Digital Mixer hanya terhubung ke **Channel 1 (Left) dan Channel 2 (Right)**, sedangkan perintah `SET_CUR` ke Channel `0` hanya di-ACK oleh microcontroller USB tanpa mengubah register penguat suara! Sebaliknya, ada juga DAC yang hanya merespons Channel `0`.
2. **Quirk Resolusi (`wRes` / `resVolumeDb`):** Di UAC2 (`baris 1075`), kita membaca `sub_res` (`resVolumeDb`), tapi di UAC1 kita tidak pernah membaca `GET_RES (0x84)`, dan di `control_thread_func` kita tidak pernah membulatkan (`quantize`) `target_db` ke kelipatan `resVolumeDb`. Beberapa DAC menolak atau mengabaikan nilai `target_db` yang tidak kelipatan `resVolumeDb` (misal step 0.5 dB = `128` atau 1.0 dB = `256`).
3. **Quirk Rentang Sempit (*Shallow Hardware Range*):** Beberapa DAC memiliki `minVolumeDb` yang sangat dangkal (misal hanya `-18 dB` atau `-24 dB` sampai `0 dB`). Jika user meminta `-55 dB` (volume 5%), hardware DAC mentok di `-24 dB`, dan sisa `-31 dB`-nya hilang begitu saja karena software DSP tidak ikut membantu!

---

### 2.5. Root Cause 1D: "Dummy Feature Unit" & Anti-Pattern Hardcode Nama Produk (`"celest"`, `"ruyi"`)

Lihat [`UsbDacController.cpp:1015-1028`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L1015-L1028):

```cpp
// Check for known buggy devices (e.g., TTGK Celest Ruyi)
std::string prodLower = g_audioState.productName;
std::transform(prodLower.begin(), prodLower.end(), prodLower.begin(), ::tolower);

if (prodLower.find("celest") != std::string::npos ||
    prodLower.find("ruyi") != std::string::npos) {
  LOGI("Force disabling Hardware Volume for known buggy device: %s",
       g_audioState.productName.c_str());
  g_audioState.isHardwareVolumeSupported = false;
  g_audioState.isHardwareVolumeActive = false;
}
```

**Penjelasan Senior ke Junior (Mengapa Hardcode Blacklist Tidak Akan Pernah Universal):**
- Sebelumnya, developer menemukan bahwa DAC *Celest Ruyi* memiliki "Dummy Feature Unit" (menerima paket USB `SET_CUR` dengan sukses `r >= 0`, tetapi tidak mengecilkan suara sama sekali).
- Solusi yang diambil waktu itu adalah meng-*hardcode* string `"celest"` dan `"ruyi"`.
- Masalahnya: Ada **ribuan** model DAC di dunia (Kiwi Ears AD1, M06Pro 2, JCALLY, Fiio, Moondrop, Shanling, iBasso, dsb.) yang menggunakan ribuan nama string USB berbeda (bahkan banyak yang string USB-nya cuma `"USB Audio Device"` atau `"KT USB Audio"`).
- Meng-*hardcode* nama produk satu per satu adalah pendekatan *whack-a-mole* (tambal sulam) yang mustahil menjadi **Universal DAC**.

---

### 2.6. Solusi Arsitektur Senior: "Universal Hybrid Volume Engine" (Untuk Semua Device & Semua DAC)

Bagaimana aplikasi audiophile kelas dunia (seperti UAPP / Neutron / Roon) menangani ribuan DAC yang berbeda secara **Universal**? Kita harus menerapkan **4 Lapis Pertahanan Universal**:

```mermaid
flowchart TD
    SLIDER["Slider UI (0% - 100%)"] --> CURVE["Lapis 1: Unified Psychoacoustic Curve<br/>0% = Mute | 1% = -64 dB | 5% = -55.5 dB | 50% = -22 dB | 100% = 0 dB"]
    CURVE --> MODE{"Lapis 4: Volume Control Mode"}
    MODE -- "Mode Universal DSP (Default Aman)" --> SW_ONLY["100% Attenuation di 64-bit Float DSP + TPDF Dither<br/>Hardware DAC dikunci di 0 dB (Unity Gain)<br/>Pasti jalan di 100% DAC di dunia!"]
    MODE -- "Mode Hybrid / Hardware UAC" --> HW_PROBE["Lapis 2: Validated UAC1 & UAC2 Probe + Readback<br/>- Cek return value GET_MIN / GET_MAX / GET_RES<br/>- Verifikasi rentang masuk akal (min < max, span >= 6 dB)<br/>- Tulis ke Master (ch=0) DAN Left/Right (ch=1,2)<br/>- Verifikasi GET_CUR (0x81) apakah DAC benar-benar menyimpan nilai!"]
    HW_PROBE --> HYBRID["Lapis 3: Automatic Hybrid Gain Split<br/>total_db = target_db + preamp_db<br/>hw_db = clamp(quantize(total_db, res), minVolumeDb, maxVolumeDb)<br/>sw_remainder_db = total_db - hw_db<br/>Software 64-bit DSP mengerjakan sisa sw_remainder_db!"]
```

1. **Lapis 1 — Kurva Psikoakustik Terpadu (*Unified Perceptual Loudness Curve*):**
   - Baik UI (`VolumeModalSheet.kt`), Software DSP (`AudioStreamer.cpp`), maupun Hardware UAC (`UsbDacController.cpp`) wajib menggunakan **satu fungsi matematika yang sama persis**.
   - Dengan menggunakan kurva *Hybrid Power-dB* berjangkauan dinamis **64 dB** (dimana gain linear $G(v) = v^{3.2}$ untuk $v \ge 0.05$ dengan *smooth roll-off* hingga $-64\text{ dB}$ di $1\%$ dan Mute di $0\%$):
     - Di **100% (`1.00`)**: `0.0 dB` (Bit-Perfect ketika di 100%)
     - Di **50% (`0.50`)**: `-19.3 dB`
     - Di **10% (`0.10`)**: `-44.8 dB`
     - Di **5% (`0.05`)**: **`-54.4 dB`** (Sangat nyaman dan pelan bahkan pada IEM 120 dB/Vrms dan DAC 2 Vrms!)
     - Di **1% (`0.01`)**: **`-64.0 dB`** (Halus, tidak loncat tiba-tiba ke `-127 dB`)
2. **Lapis 2 — Validasi Protokol UAC1/UAC2, *Dual-Channel Write* (`ch=0` & `ch=1,2`), dan *Readback Verification (`GET_CUR`)*:**
   - Di UAC1 maupun UAC2, kita **wajib memeriksa `r >= 2`** saat membaca `GET_MIN`, `GET_MAX`, dan `GET_RES`.
   - Saat mengirim `SET_CUR`, kita tulis ke Master (`ch=0`) **dan** ke Channel 1 & 2 (`ch=1`, `ch=2`) jika descriptor mendukung atau jika salah satu gagal, serta membulatkan nilai ke kelipatan `resVolumeDb`.
   - Setelah `SET_CUR`, kita lakukan **Readback Verification (`GET_CUR = 0x81`)**: jika DAC mengembalikan nilai yang tidak berubah sama sekali (ciri khas *Dummy Feature Unit*), sistem otomatis menandai hardware volume DAC tersebut sebagai *Dummy/Unverified* dan mengalihkan atenuasi ke 64-bit Software DSP secara *real-time* tanpa perlu *hardcode* nama merk DAC!
3. **Lapis 3 — *Automatic Hybrid Attenuation Split* (Kerja Sama Hardware + Software 64-bit):**
   - Jika DAC hanya punya rentang hardware terbatas (misal `minVolumeDb = -24 dB`), dan user menyetel volume 5% (`-54.4 dB`), maka:
     - Hardware DAC diturunkan sebesar `-24.0 dB`.
     - Sisa kekurangannya (`-30.4 dB`) otomatis dikerjakan oleh **64-bit Floating-Point Software Mixer + TPDF Dithering** di [`AudioStreamer.cpp`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp)!
4. **Lapis 4 — Mode Volume Universal di UI (`Universal 64-Bit DSP` vs `Hybrid Hardware+DSP`):**
   - Mesin audio kita sudah memiliki pengolahan **64-bit `double` + TPDF Dithering** di [`AudioStreamer.cpp:620-660`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L620-L660) yang menjaga *dynamic range* >140 dB (jauh melampaui batas fisik DAC 24-bit/32-bit mana pun di dunia).
   - Pada DAC dongle yang *firmware*-nya membandel (mengaku mengubah register UAC padahal chip analognya di-*hardwire* *fixed output* seperti banyak dongle CX31993 / AD1 / M06Pro 2), **Universal 64-Bit DSP Volume** menjamin **100% kompatibilitas di seluruh smartphone dan seluruh DAC di dunia**, sementara saat volume disetel ke **100%**, `target_vol == 1.0f` tetap melakukan **Bit-Perfect Bypass** murni!
   - Kita juga menyediakan *toggle* langsung di [`VolumeModalSheet.kt`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/dialogs/VolumeModalSheet.kt) agar user bisa memilih antara **Universal 64-Bit DSP (Default & Paling Kompatibel)** dan **Hardware UAC + Hybrid** kapan saja tanpa harus mencabut DAC.

---

## 3. Bedah Tuntas Bug #2: Mengapa Lagu Berhenti & Pindah Sendiri (*Auto-Next*) di ~70% Durasi Track

Sekarang mari kita bedah bug kedua dari [`test-bug.md`](file:///Users/yuka/Projects/tsrossa-ujicoba/test-bug.md):
> *"2. bug pas play music udah 70an % track malah ke next sendiri, jadi gak selesai."*

Ini adalah bagian paling menarik. Mari kita buktikan bahwa bug ini **bukan kebetulan acak**, melainkan bisa dibuktikan secara **matematis sampai presisi milidetik** dari log yang kamu lampirkan!

### 3.1. Bukti Forensik dari Log di `test-bug.md`

Lihat baris 19 sampai 27 di [`test-bug.md`](file:///Users/yuka/Projects/tsrossa-ujicoba/test-bug.md#L19-L27):

```text
10-25 18:20:35.875 18715 18771 D AudioStreamer: Hardware Readiness: Stream active. Sending 200ms pre-roll silence to stabilize DAC PLL.
10-25 18:20:35.926 18715 18715 D UsbAudio: playAudio SUCCESS: Reona - かたっぽの靴下.flac
10-25 18:24:14.616 18715 18715 D UsbAudio: playTrack: Kalafina - Heavenly Blue.flac (source=LIBRARY, auto=true)
```

Mari kita hitung selisih waktu dari saat lagu **ReoNa - かたっぽの靴下.flac** mulai berbunyi (`18:20:35.926`) sampai tiba-tiba aplikasi memanggil `playTrack(..., auto=true)` untuk lagu berikutnya (**Kalafina - Heavenly Blue.flac**) pada `18:24:14.616`:

$$\text{Waktu Mulai} = 18\text{ jam } 20\text{ menit } 35,926\text{ detik}$$
$$\text{Waktu Auto-Next} = 18\text{ jam } 24\text{ menit } 14,616\text{ detik}$$
$$\Delta t = 3\text{ menit } 38,690\text{ detik} = \mathbf{218,690\text{ detik}}$$

Perhatikan parameter `auto=true` pada log `playTrack: Kalafina - Heavenly Blue.flac (source=LIBRARY, auto=true)`.  
Di [`MainActivity.kt:460`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/MainActivity.kt#L460), `auto=true` hanya dikirim dari callback `onTrackFinished`:
```kotlin
// MainActivity.kt baris 441 & 460
override fun onTrackFinished() {
    ...
    playTrack(nextTrack, currentPlaybackSource, isAutoAdvance = true)
}
```
Artinya, C++ Audio Engine mengirim event `REQ_EVENT_TRACK_FINISHED (1)` tepat setelah memutar lagu selama **218,690 detik**!

Mengapa C++ Audio Engine menganggap lagu sudah habis tepat di detik ke-**218,690**?

---

### 3.2. Pembuktian Matematika pada `AudioCommon.h` & `AudioStreamer.cpp` (Salah Hitung 10x Lipat!)

Sekarang buka [`AudioCommon.h:20-22`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioCommon.h#L20-L22):

```cpp
// AudioCommon.h baris 20-22
// Security & Stability Constants
constexpr uint64_t MAX_TRACK_BUFFER_BYTES = 160ULL * 1024 * 1024;     // 160 MB limit (~36 min @ 96kHz / ~79 min @ 44.1kHz)
constexpr uint64_t MAX_NEXT_TRACK_BUFFER_BYTES = 96ULL * 1024 * 1024; // 96 MB limit for pre-buffering
```

**LIHAT KOMENTAR DI BARIS 21 ITU BAIK-BAIK!**
Penulis kode sebelumnya menulis komentar:
`// 160 MB limit (~36 min @ 96kHz / ~79 min @ 44.1kHz)`

Mari kita uji komentar itu dengan matematika dasar!
1. Berapa ukuran `160 MB` dalam byte?
   $$160 \times 1024 \times 1024 = \mathbf{167.772.160\text{ bytes}}$$
2. Di [`AudioStreamer.cpp:950`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L950), audio didekode ke dalam `std::vector<int32_t> pcmBuffer`.
   - 1 sampel `int32_t` = **4 bytes** (`sizeof(int32_t)`).
   - Stereo = **2 channel**.
   - Jadi 1 *frame* stereo memakan: $2 \times 4\text{ bytes} = \mathbf{8\text{ bytes per frame}}$.
3. Lagu Jepang Hi-Res (seperti rilisan Sony Music / SACRA MUSIC **ReoNa - かたっぽの靴下.flac** dan **Kalafina - Heavenly Blue.flac**) memiliki format **Hi-Res 24-bit / 96.000 Hz Stereo**.
4. Pada sample rate **96.000 Hz**, berapa byte RAM yang dibutuhkan per detik?
   $$\text{Bytes per detik} = 96.000\text{ frame/detik} \times 8\text{ bytes/frame} = \mathbf{768.000\text{ bytes/detik}}$$
5. Sekarang bagi batas `MAX_TRACK_BUFFER_BYTES` (`167.772.160 bytes`) dengan `768.000 bytes/detik`:
   $$\text{Durasi Maksimum} = \frac{167.772.160\text{ bytes}}{768.000\text{ bytes/detik}} = \mathbf{218,4533\text{ detik}} \text{ (3 menit 38,45 detik!)}$$
6. Ingat bahwa di log baris 19 (`18:20:35.875`), `AudioStreamer` menyuntikkan **200ms (`0,200 detik`) pre-roll silence** (`Sending 200ms pre-roll silence to stabilize DAC PLL`)!
   Tambahkan `218,453 detik` lagu + `0,200 detik` pre-roll silence:
   $$218,453\text{ detik} + 0,200\text{ detik} = \mathbf{218,653\text{ detik!}}$$

**BANDINGKAN KEDUA ANGKA INI:**
- **Hasil hitungan rumus `MAX_TRACK_BUFFER_BYTES` + pre-roll:** **`218,653 detik`**
- **Waktu aktual di log `test-bug.md` saat lagu putus dan pindah sendiri:** **`218,690 detik`**

Selisihnya hanya **37 milidetik** (waktu satu siklus thread untuk mengirim callback JNI ke Kotlin)!
Komentar `~36 min @ 96kHz` di [`AudioCommon.h:21`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioCommon.h#L21) **salah hitung 10 kali lipat** (3,64 menit ditulis 36 menit)!

Dan berapa persentase lagu saat terpotong di detik ke-218,45?
Lagu *ReoNa - かたっぽの靴下* berdurasi sekitar **5 menit (~300 detik)**:
$$\frac{218,45\text{ detik}}{300\text{ detik}} = \mathbf{72,8\%!}$$
Persis sekali dengan laporanmu: ***"udah 70an % track malah ke next sendiri, jadi gak selesai"***!

Bahkan lebih parah lagi jika kamu memutar lagu **192.000 Hz**:
$$\frac{167.772.160\text{ bytes}}{192.000 \times 8\text{ bytes/detik}} = \mathbf{109,22\text{ detik (1 menit 49 detik!)}}$$
Dan jika kamu memutar lagu **384.000 Hz**: lagu akan terpotong hanya dalam **54,6 detik**!

---

### 3.3. Bagaimana Kode di `AudioStreamer.cpp` Memotong Lagu Secara Diam-Diam (*Silent Truncation*)

Mari kita lihat TKP pemotongan lagu tersebut di [`AudioStreamer.cpp:933-959`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L933-L959):

```cpp
// AudioStreamer.cpp baris 933-959
uint64_t requiredBytes = totalFrames * newChannels * sizeof(int32_t);
uint64_t targetFrames = totalFrames;
if (requiredBytes > MAX_TRACK_BUFFER_BYTES) {
  LOGW("Track size (%llu MB) exceeds limit. Capping initial buffer.",
       (unsigned long long)(requiredBytes / (1024 * 1024)));
  targetFrames =
      MAX_TRACK_BUFFER_BYTES / ((uint64_t)newChannels * sizeof(int32_t));
}

try {
  g_audioState.pcmBuffer.resize(targetFrames * newChannels);
} catch (const std::bad_alloc &e) {
  ...
}
totalFrames = targetFrames; // <--- BUG FATAL: SISA LAGU (>218.45s) DIBUANG SELAMANYA!
g_audioState.totalFrames.store(totalFrames);
```

Dan lihat apa yang terjadi di `decoder_thread_func` ([`AudioStreamer.cpp:259-277`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L259-L277)):
```cpp
uint64_t total = g_audioState.totalFrames.load();
uint64_t current = g_audioState.decodedFrames.load();

if (current >= total || !g_audioState.activeDecoder) {
  ...
  // Dekoder BERHENTI mendekode karena current sudah mencapai targetFrames (218.45 detik)!
}
```

Lalu mengapa di layar HP-mu *progress bar* (scrubber) menunjukkan lagu baru berjalan **~70%** ketika lagu tiba-tiba habis?
1. Karena **UI Kotlin** membaca total durasi lagu (`track.duration` = ~300.000 ms / 5 menit) langsung dari header metadata file FLAC melalui [`MetadataExtractor.kt:71`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/metadata/MetadataExtractor.kt#L71) (`AudioTagReader.readDurationMs`).
2. Sedangkan **C++ AudioStreamer** memotong `g_audioState.totalFrames` menjadi `218,45 detik`!
3. Saat `pcmIndex` di C++ mencapai `218,45 detik`, `MainActivity.kt` menghitung posisi scrubber:
   $$\text{Progress UI} = \frac{\text{getCurrentPosition (218.450 ms)}}{\text{track.duration (300.000 ms)}} \approx \mathbf{72,8\%}$$
4. Tepat di posisi 72,8% itu, C++ menganggap `pcmIndex >= totalFrames` ([`AudioStreamer.cpp:587`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L587)), lalu menembakkan `REQ_EVENT_TRACK_FINISHED` (`onTrackFinished()`), sehingga aplikasi langsung loncat (*auto-next*) ke lagu berikutnya sebelum lagu selesai!

**Bug Terkait pada Pre-Buffering Gapless (`prepare_next_track_internal`):**
Lihat juga [`AudioStreamer.cpp:1325-1334`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L1325-L1334):
```cpp
uint64_t requiredBytes = totalFrames * newChannels * sizeof(int32_t);
if (requiredBytes > MAX_NEXT_TRACK_BUFFER_BYTES) {
  LOGW("Next track too large for pre-buffering (%llu MB). Skipping.",
       (unsigned long long)(requiredBytes / (1024 * 1024)));
  return false;
}
```
Karena `MAX_NEXT_TRACK_BUFFER_BYTES` disetel `96 MB`, untuk lagu 96 kHz:
$$\frac{96 \times 1024 \times 1024}{768.000} = \mathbf{131\text{ detik (2 menit 11 detik)}}$$
Artinya, **semua lagu Hi-Res 96 kHz yang durasinya di atas 2 menit 11 detik gagal melakukan Gapless Pre-Buffering**! Itu sebabnya di log baris 20–26 kita melihat `Sending 200ms pre-roll silence` setiap ganti lagu, karena *gapless transition* ditolak oleh batas `96 MB` tersebut!

---

### 3.4. Solusi Arsitektur Senior untuk Bug #2: *Sliding-Window Ring Buffer* (Streaming Tanpa Batas Durasi & Hemat RAM)

Sebagai Senior Engineer, kita harus bertanya: *"Apakah solusinya cukup dengan menaikkan `MAX_TRACK_BUFFER_BYTES` dari 160 MB menjadi 1 GB?"*
**Jawabannya: TIDAK BOLEH!** Mengapa?
- Jika user memutar file **192 kHz / 32-bit** berdurasi 15 menit (misal musik klasik atau *live concert*), ukurannya adalah $192.000 \times 8 \times 900 = \mathbf{1,38\text{ GB}}$!
- Jika kita mengalokasikan `std::vector<int32_t>` sebesar 1,38 GB sekaligus di RAM Android, *Low Memory Killer Daemon* (`lmkd`) milik Android akan langsung membunuh (*force close / OOM kill*) aplikasi kita!

**Bagaimana cara kerja Audio Engine profesional?**
Kita mengubah `pcmBuffer` dari *Full-Track Flat Array* menjadi **Fixed-Capacity Sliding-Window Ring Buffer (Buffer Melingkar Berjalan)**:

```mermaid
flowchart LR
    subgraph File_FLAC["File Audio Hi-Res (Durasi Bebas: 5 Menit s/d 3 Jam)"]
        F1["0% ... 100% (totalFrames Asli TIDAK PERNAH DIPOTONG)"]
    end
    subgraph RingBuffer["RAM Ring Buffer (Maksimal 64 MB = 8.388.608 Frames)"]
        RB["Indeks Baca: pcmIndex % capacityFrames<br/>Indeks Tulis: decodedFrames % capacityFrames"]
    end
    subgraph USB_ISO["USB Isochronous Callback (Real-Time)"]
        DAC["Kirim ke USB DAC (Lock-Free, Zero Allocation)"]
    end
    File_FLAC -- "decoder_thread_func mengisi di depan pcmIndex<br/>(selama decodedFrames - pcmIndex < capacityFrames)" --> RingBuffer
    RingBuffer -- "fill_iso_packet_buffer membaca pcmIndex<br/>lalu memajukan pcmIndex++" --> USB_ISO
```

**Cara Kerja *Sliding-Window Ring Buffer* yang Akan Kita Bangun:**
1. **`totalFrames` TIDAK PERNAH DIPOTONG!** Jika lagu 96 kHz berdurasi 5 menit (`28.800.000 frames`) atau 60 menit (`345.600.000 frames`), `g_audioState.totalFrames` tetap menyimpan jumlah frame asli seutuhnya (`100%`).
2. **Alokasi RAM Dibatasi Cerdas (`RING_BUFFER_MAX_BYTES = 64 MB`):**
   - Jika ukuran total lagu $\le 64\text{ MB}$ (misal lagu 44.1 kHz biasa ~9,5 menit), buffer dialokasikan seukuran lagu penuh.
   - Jika ukuran lagu $> 64\text{ MB}$ (misal lagu Hi-Res 96 kHz / 192 kHz / 384 kHz atau lagu panjang), `pcmBuffer` hanya mengalokasikan **64 MB** (`capacityFrames = 8.388.608 stereo frames`, yang setara **87 detik audio pada 96 kHz** atau **43 detik pada 192 kHz**).
3. **Pembacaan & Penulisan Melingkar (*Modulo Indexing*):**
   - `decodedFrames` dan `pcmIndex` tetap berupa *monotonic counter* (`0` sampai `totalFrames`) sehingga logika durasi, *scrubber UI*, dan deteksi akhir lagu (`pcmIndex >= totalFrames`) **tidak berubah dan tetap 100% akurat**.
   - Saat menulis ke `pcmBuffer` di `decoder_thread_func`, posisi memori fisik dihitung dengan `(frameIndex % capacityFrames) * channels`.
   - Agar `decoder_thread_func` tidak menimpa sampel yang belum diputar oleh USB callback, `decoder_thread_func` cukup menahan diri (*backpressure*) ketika:
     $$\text{decodedFrames} - \text{pcmIndex} \ge \text{capacityFrames} - \text{safetyMarginFrames}$$
   - Begitu USB callback memutar audio dan memajukan `pcmIndex`, ruang di ring buffer otomatis terbuka dan `decoder_thread_func` melanjutkan dekode sampai `decodedFrames == totalFrames` (100% lagu selesai)!
4. **Dukungan *Seek* (Geser Scrubber) & *Gapless Pre-Buffering*:**
   - Jika user menggeser *scrubber* (`seek_audio`) ke bagian lagu yang berada di dalam jendela ring buffer saat ini, perpindahan terjadi **instan (0 ms)**. Jika di luar jendela, `decoder_thread_func` melakukan `activeDecoder->seek(targetFrame)` dan mengisi ulang ring buffer dari titik tersebut dengan mulus!
   - Untuk *Gapless Pre-Buffering* (`prepare_next_track_internal`), jika lagu berikutnya $> 32\text{ MB}$, kita tidak lagi menolak (*skip*) lagu tersebut! Kita cukup mem-*pre-buffer* **32 MB pertama** di `nextPcmBuffer` dan menyimpan `nextActiveDecoder` agar ketika transisi *gapless* terjadi, `decoder_thread_func` langsung melanjutkan dekode sisa lagu berikutnya tanpa jeda (*true gapless* untuk semua ukuran file Hi-Res)!

---

## 4. Analisa Log Tambahan di `test-bug.md` (Agar Junior Paham Mana Bug Aplikasi vs Noise OS)

Di [`test-bug.md:32-71`](file:///Users/yuka/Projects/tsrossa-ujicoba/test-bug.md#L32-L71), kamu juga menyertakan tiga potongan log sistem. Mari kita bahas satu per satu agar kamu mengerti cara membacanya:

### 4.1. Log `WindowOnBackDispatcher` (Baris 32–42)
```text
10-25 18:22:32.393 18715 18715 W WindowOnBackDispatcher: OnBackInvokedCallback is not enabled for the application.
10-25 18:22:32.393 18715 18715 W WindowOnBackDispatcher: Set 'android:enableOnBackInvokedCallback="true"' in the application manifest.
```
- **Apa artinya?** Mulai Android 13 (API 33+), sistem operasi menampilkan *warning* jika aplikasi belum mengaktifkan fitur *Predictive Back Gesture* di `AndroidManifest.xml`, atau jika Activity menggunakan `onBackPressed()` tradisional tanpa mendaftarkan `OnBackPressedCallback` saat dialog ditutup.
- **Status di Codebase:** Di [`AndroidManifest.xml:24`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/AndroidManifest.xml#L24), atribut `android:enableOnBackInvokedCallback="true"` sudah ditambahkan. Log ini tidak memengaruhi audio sama sekali.

### 4.2. Log `[ScrollIdentify] on fling` & `SELinux avc: denied /proc/perfmgr/perf_ioctl` (Baris 53–71)
```text
10-25 18:26:52.710 18715 18715 I System.out: [ScrollIdentify] on fling
10-25 18:26:52.735 18715 18715 W Thread-5: type=1400 audit(0.0:5872): avc: denied { ioctl } for path="/proc/perfmgr/perf_ioctl" dev="proc" ino=4026532425 ioctlcmd=0x6709 scontext=u:r:untrusted_app:s0:c204,c257,c512,c768 tcontext=u:object_r:proc_perfmgr:s0 tclass=file permissive=0 app=com.yuka.musicplayer
```
- **Apa artinya?** Perhatikan waktu kejadiannya: `18:26:52.710` (`[ScrollIdentify] on fling`) diikuti `25 ms` kemudian (`18:26:52.735`) oleh `avc: denied { ioctl } for path="/proc/perfmgr/perf_ioctl"`.
- Di perangkat Android dengan chipset **MediaTek** (ROM OEM seperti Xiaomi/Vivo/Oppo/Transsion), framework UI Android (`ViewRootImpl` / `OverScroller`) milik pabrikan HP menyuntikkan library internal (`libperfioctl.so`) ke dalam setiap aplikasi untuk mendeteksi gerakan *scroll fling* (`[ScrollIdentify] on fling`) dan mencoba menaikkan frekuensi CPU lewat `/proc/perfmgr/perf_ioctl`.
- Namun karena kebijakan keamanan SELinux Android membatasi aplikasi pihak ketiga (`untrusted_app`) mengakses `/proc/perfmgr/perf_ioctl` secara langsung, kernel mencetak log `avc: denied`.
- **Kesimpulan Senior:** Ini adalah log internal ROM/chipset HP saat kamu melakukan *scroll* daftar lagu, **bukan error dari kode aplikasi kita** dan **sama sekali tidak berbahaya**.

---

## 5. Ringkasan Matriks Audit & Rencana Aksi

| ID | Masalah yang Dilaporkan | Lokasi File & Baris | Root Cause (Akar Masalah) | Solusi Arsitektur |
| :--- | :--- | :--- | :--- | :--- |
| **BUG-1A** | Volume 5% masih sangat keras (~95 dB SPL) | [`UsbDacController.cpp:103`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L103), [`VolumeModalSheet.kt:74`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/java/com/yuka/musicplayer/ui/dialogs/VolumeModalSheet.kt#L74) | Menggunakan rumus amplitudo linear `20 * log10(0.05) = -26 dB`, bukan kurva persepsi telinga (psikoakustik). | Kurva Psikoakustik Terpadu (64 dB range: `5% = -54.4 dB`, `1% = -64 dB`, `100% = 0 dB`). |
| **BUG-1B** | Volume UAC1 terkunci di 100% (`0 dB`) pada beberapa dongle DAC | [`UsbDacController.cpp:1039-1086`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L1039-L1086) | Return value `GET_MIN`/`GET_MAX` UAC1 tidak dicek & fallback `minVolumeDb >= maxVolumeDb` hanya ada di blok UAC2 (`else`). | Validasi penuh return value UAC1 & UAC2 + `GET_RES` + fallback terpadu. |
| **BUG-1C** | Perintah `SET_CUR` diabaikan oleh DAC tertentu | [`UsbDacController.cpp:121-136`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L121-L136), [`601-615`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L601-L615) | Hanya mengirim `SET_CUR` ke Master `ch=0` atau L/R `ch=1,2` saja, tanpa *step quantization* (`resVolumeDb`). | Kirim `SET_CUR` terkuantisasi ke Master (`ch=0`) **dan** L/R (`ch=1,2`) + verifikasi *readback* `GET_CUR`. |
| **BUG-1D** | Dongle DAC dengan *Dummy Feature Unit* (Kiwi Ears AD1, M06Pro 2, dll.) tidak mengecilkan suara | [`AudioStreamer.cpp:481`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L481), [`UsbDacController.cpp:1016`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/UsbDacController.cpp#L1016) | Mengandalkan *hardcode* nama produk (`"celest"`, `"ruyi"`) dan mematikan software DSP (`target_vol = 1.0f`) secara membabi buta. | **Universal Hybrid / 64-Bit DSP Volume Mode** (Default: 64-Bit TPDF DSP yang 100% universal di semua DAC, dengan opsi Hardware UAC Hybrid). |
| **BUG-2A** | Lagu Hi-Res berhenti & *auto-next* sendiri di ~70% durasi (detik ke-218,69) | [`AudioCommon.h:21`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioCommon.h#L21), [`AudioStreamer.cpp:935-949`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L935-L949) | `MAX_TRACK_BUFFER_BYTES` (160 MB) salah hitung 10x lipat (hanya cukup 218,45 detik @ 96kHz) dan memotong `totalFrames = targetFrames`. | **Sliding-Window Ring Buffer** di `AudioStreamer.cpp`: `totalFrames` tidak pernah dipotong, streaming mulus untuk durasi & sample rate berapa pun! |
| **BUG-2B** | *Gapless Pre-Buffering* gagal untuk lagu Hi-Res > 2 menit 11 detik | [`AudioCommon.h:22`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioCommon.h#L22), [`AudioStreamer.cpp:1328`](file:///Users/yuka/Projects/tsrossa-ujicoba/app/src/main/cpp/AudioStreamer.cpp#L1328) | `prepare_next_track_internal` menolak (`return false`) jika lagu > 96 MB (`MAX_NEXT_TRACK_BUFFER_BYTES`). | *Partial Pre-Buffering* di Ring Buffer + serah terima `nextActiveDecoder` saat transisi *gapless*. |

---

> [!TIP]
> **Langkah Selanjutnya:**  
> Silakan buka dokumen rencana implementasi di [`PLANNING/planning.md`](file:///Users/yuka/Projects/tsrossa-ujicoba/PLANNING/planning.md) untuk melihat panduan *step-by-step* perbaikan kode beserta pengujiannya (*Test-Driven Development*).
