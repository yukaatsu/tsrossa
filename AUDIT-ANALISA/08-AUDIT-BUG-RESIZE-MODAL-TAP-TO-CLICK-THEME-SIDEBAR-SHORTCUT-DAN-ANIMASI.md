# Audit & Analisa Aspek 8: Bedah Mendalam Bug Tabrakan Modal & Menu Saat Dikecilkan, Inkonsistensi Gestur Tap-to-Click, Teks `'BIT-PERFECT'` Bertumpuk, Sidebar Hitam Tidak Mengikuti Theme, Shortcut Keyboard, Eliminasi "AI Slop", & Sistem Animasi Kontekstual (`bug-note.md`)

**Sumber Laporan Bug & Catatan Penyempurnaan:** [`bug-note.md`](file:///Users/yuka/Documents/Projects/tsrossa-macos/bug-note.md)

### Daftar Masalah yang Diaudit:
- **#BUG:**
  1. *"ketika aplikasi di kecilkan tampilan modal modal menu menjadi bertabtakan."*
  2. *"gestur tap to click belum konsisten"*
  3. *"beberapa tulisan masih bertumpuk contoh tulisan di bar nowplaying 'bit-perfect' masih bertumpuk"*
  4. *"backgaund di bar kiri yang menampung menu playlist dll belum mengikuti tampilan sesuai theme, masih hitam saja"*
- **#NOTE:**
  1. *"shortcat keyboard belum ada"*
  2. *"hilangkan ui/ux yang mengandung ai slop"*
  3. *"tambahkan animasi di semua aspek saat membuka, berganti menu dll. dengan smooth dan sesuai dengan apa yang di buka dan berganti ke menu apa."*

**Target File yang Diaudit:**
- [`MainAppView.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/MainAppView.swift)
- [`LiquidGlassComponents.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/LiquidGlassComponents.swift)
- [`Theme.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Theme.swift)
- [`AppViewModel.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift)
- [`main.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/main.swift)
- [`TelemetryModalView.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift)
- [`SidebarView.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SidebarView.swift)
- [`LiquidTransportBarView.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/LiquidTransportBarView.swift)
- [`FloatingTopToolbarView.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/FloatingTopToolbarView.swift)
- [`NowPlayingSplitView.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/NowPlayingSplitView.swift)
- [`QueueLyricsView.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/QueueLyricsView.swift)
- [`LibraryView.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/LibraryView.swift)
- [`SettingsBentoView.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SettingsBentoView.swift)
- [`CreatePlaylistModalSheet.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/CreatePlaylistModalSheet.swift)
- [`TrackDetailsModalSheet.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/TrackDetailsModalSheet.swift)

---

## 🎓 Pengantar Mentoring: Dari Senior Engineer untuk Junior Engineer

> **Pesan Senior Engineer:**
> Setelah kita menyelesaikan fondasi *App Shell* di Plan 07, pengujian nyata dari pengguna di [`bug-note.md`](file:///Users/yuka/Documents/Projects/tsrossa-macos/bug-note.md) mengungkap **lapisan kedua dari rekayasa aplikasi desktop macOS kelas produksi (Apple Design Award Standard)**.
>
> Seorang Junior Engineer sering menganggap "asal sudah kompilasi dan tampil, berarti selesai". Sebaliknya, seorang **Senior macOS UI/UX Engineer** selalu menguji 5 aspek krusial ini:
> 1. **Matematika Ruang Sempit (*Constrained-Space Layout Math*)**: Apa yang terjadi pada setiap *card*, *segmented picker*, *modal sheet*, dan *badge* ketika jendela diperkecil ke ukuran kompak? Apakah komponen memiliki ukuran mati (*hardcoded width/height*) yang saling bertabrakan?
> 2. **Fisika Hit-Testing Trackpad macOS (*Tap-to-Click Hit-Testing*)**: Mengapa ketukan ringan satu jari di trackpad (*Tap-to-Click*) kadang tidak merespons pada tombol atau baris menu? (Spoiler: ada dua jebakan klasik SwiftUI macOS — `Color.clear` tanpa `.contentShape(Rectangle())` dan konflik `.onTapGesture(count: 2)` yang menahan single-tap selama 250ms!).
> 3. **Propagasi Tema & *ColorScheme* pada Material Kaca (*Chromatic Glass Propagation*)**: Mengapa mengganti tema ke *Light Glass* atau mengganti palet warna (*Rose, Matcha, Gold, Cyan*) tidak mengubah warna background bar kiri (`SidebarView`) yang tetap hitam legam?
> 4. **Anti-"AI Slop" & Human Interface Guidelines (HIG)**: Bagaimana membedakan UI buatan AI generik (penuh huruf kapital monospaced, istilah hiperbolis, *drag handle* ala layar sentuh HP di aplikasi desktop macOS, warna pelangi tidak senada) dengan desain asli macOS Sequoia / Apple Music yang tenang, dewasa, dan presisi?
> 5. **Koreografi Animasi Kontekstual (*Spatial & Contextual Choreography*)**: Di macOS modern, elemen UI tidak boleh "muncul mendadak" (*teleport*) dalam 1 frame. Setiap aksi harus memiliki hubungan ruang (*spatial continuity*) menggunakan pegas fisika (*spring physics*) dan `@Namespace matchedGeometryEffect`.
>
> Mari kita bedah ketujuh poin di `bug-note.md` secara matematis dan baris demi baris!

---

## 1. Matriks Ringkasan Eksekutif (4 Bug & 3 Catatan Penyempurnaan)

| Kode | Masalah di `bug-note.md` | Akar Masalah Teknis (*Root Cause*) | Lokasi Kode | Tingkat Keparahan |
| :--- | :--- | :--- | :--- | :--- |
| **BUG #1** | Ketika aplikasi dikecilkan, tampilan modal-modal & menu menjadi bertabrakan | **Ukuran Mati (*Hardcoded Frame*) Tanpa Geometri Adaptif**: (1) `SettingsBentoView` memaksa 2 kolom `LazyVGrid` tetap berdampingan; di dalamnya `SegmentedPickerStyle` dengan 3–5 opsi panjang (`"Liquid Glass Refraction"`, `DYNAMIC/ROSE/MATCHA/GOLD/CYAN`) meluap keluar kartu dan menabrak kartu sebelahnya. (2) `NowPlayingSplitView` mengunci artwork `320×320` dan membagi 2 kolom kaku sehingga pada jendela kecil teks & panel `QueueLyricsView` bertabrakan. (3) Modal `TelemetryModalView` (`620×560`), `TrackDetailsModalSheet` (`480×440`), dan `CreatePlaylistModalSheet` (`440`) memakai ukuran statis tanpa batas `GeometryReader`. | [`SettingsBentoView.swift:L27,L110-L143`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SettingsBentoView.swift#L27), [`NowPlayingSplitView.swift:L13-L105`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/NowPlayingSplitView.swift#L13-L105), [`TelemetryModalView.swift:L190`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift#L190) | **KRITIS** |
| **BUG #2** | Gestur *tap-to-click* belum konsisten | **3 Biang Keladi Hit-Testing SwiftUI macOS**: (1) `.onTapGesture(count: 2)` pada kontainer induk `FloatingTopToolbarView` memaksa SwiftUI menunggu 250ms untuk memastikan bukan *double-tap*, sehingga *single tap-to-click* pada tombol & kolom Search terasa macet/tidak konsisten. (2) Tombol `PlainButtonStyle()` di `SidebarView`, `SettingsBentoView`, dan `QueueLyricsView` menggunakan `.background(Color.clear)` **tanpa `.contentShape(Rectangle())`** di dalam label, sehingga ketukan di area kosong sebelah teks tidak terdeteksi sama sekali! (3) Tombol ikon di `LiquidTransportBarView` hanya berukuran `13×13pt` tanpa frame hit-target minimum `32×32pt`. | [`FloatingTopToolbarView.swift:L72-L75`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/FloatingTopToolbarView.swift#L72-L75), [`SidebarView.swift:L63-L84,L121-L189`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SidebarView.swift#L121-L189), [`LiquidTransportBarView.swift:L66-L120`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/LiquidTransportBarView.swift#L66-L120) | **KRITIS** |
| **BUG #3** | Beberapa tulisan masih bertumpuk, contoh tulisan di bar Now Playing `'BIT-PERFECT'` masih bertumpuk | **Defisit Lebar Kolom Kanan Transport Bar (`56pt < 112pt`) & Absennya `.fixedSize()`**: Di `LiquidTransportBarView`, kolom kanan dikunci `.frame(width: 210)`. Di dalamnya ada slider volume (`142pt`) + spasi (`12pt`), sehingga hanya tersisa **`56pt`** untuk kotak badge `'BIT-PERFECT'` + `'192 kHz / 24-bit'` yang membutuhkan **`112pt`**! Tanpa `.lineLimit(1)` dan `.fixedSize(horizontal: true, vertical: false)`, teks terlipat menjadi 3 baris vertikal dan saling bertumpuk! | [`LiquidTransportBarView.swift:L128-L179`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/LiquidTransportBarView.swift#L128-L179), [`NowPlayingSplitView.swift:L66-L84,L109-L117`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/NowPlayingSplitView.swift#L66-L84) | **KRITIS** |
| **BUG #4** | Background di bar kiri (`SidebarView`) yang menampung menu playlist dll belum mengikuti tampilan sesuai theme, masih hitam saja | **Hardcoded Near-Black Surface (`l = 0.065`) + Tidak Memakai Material Kaca Berwarna + Absennya `.preferredColorScheme` + Mismatch Tag Picker**: (1) `SidebarView` hanya memakai `.background(theme.surface.opacity(0.70))` di mana `theme.surface` di `Theme.swift` diset pada lightness `0.065` (`#0F0F12`, hitam pekat) dengan saturasi dikecilkan ke `0.08`. (2) `MainAppView` tidak memasang `.preferredColorScheme(isEffectiveDark ? .dark : .light)`, sehingga SwiftUI `Material` selalu merender kaca hitam. (3) Default `vm.themeMode = "DARK"` dan `vm.bgCanvasMode = "DYNAMIC"` tidak cocok dengan tag enum di `SettingsBentoView`. | [`SidebarView.swift:L122`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SidebarView.swift#L122), [`Theme.swift:L166,L183`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Theme.swift#L166), [`MainAppView.swift:L11-L43,L135`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/MainAppView.swift#L11-L43), [`SettingsBentoView.swift:L110-L143`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SettingsBentoView.swift#L110-L143) | **TINGGI** |
| **NOTE #1** | Shortcut keyboard belum ada | **Shortcut Tersembunyi & Konflik `⌘F`**: (1) Tidak ada petunjuk shortcut (`⌘1`–`⌘4`, `⌘N`, `Space`, dll.) di Sidebar, tombol UI, maupun panel bantuan shortcut (`⌘/`). (2) Menekan `⌘F` di `AppViewModel.swift:L1023` justru memanggil `isTheaterMode.toggle()` (fitur mati), padahal placeholder Search Bar meminta `⌘F` untuk fokus pencarian! (3) Menu bar `.commands` di `main.swift` belum memiliki `⌘F` (Find), `⌘N` (New Playlist), `⌘O` (Open Folder), `⌘I` (Track Info), `⌘L` (Like), dan `⌘/` (Keyboard Shortcuts). | [`AppViewModel.swift:L1021-L1088`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L1021-L1088), [`main.swift:L29-L137`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/main.swift#L29-L137), [`FloatingTopToolbarView.swift:L19`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/FloatingTopToolbarView.swift#L19) | **TINGGI** |
| **NOTE #2** | Hilangkan UI/UX yang mengandung AI Slop | **Gejala Visual "AI Slop"**: (1) *Drag handle* kapsul iOS (`38×4pt`) di atas modal desktop `TelemetryModalView`. (2) Banjir teks `ALL-CAPS MONOSPACED` di setiap header & form (`COREAUDIO HARDWARE`, `MEMORY & JITTER DEFENSE`, `SMART FILTER KEYWORD (OPTIONAL)`). (3) Raw string `DYNAMIC / ROSE / MATCHA / GOLD / CYAN` pada picker, bukan swatch warna visual yang elegan. (4) Jargon promosi berlebihan & badge warna-warni mencolok yang merusak estetika Apple HIG. | [`TelemetryModalView.swift:L30-L35,L191`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift#L30-L35), [`SettingsBentoView.swift:L18-L24,L72-L81,L136-L143`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SettingsBentoView.swift#L18-L24), [`CreatePlaylistModalSheet.swift:L15-L53`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/CreatePlaylistModalSheet.swift#L15-L53) | **TINGGI** |
| **NOTE #3** | Tambahkan animasi di semua aspek saat membuka, berganti menu dll. dengan smooth dan sesuai dengan apa yang dibuka dan berganti ke menu apa | **Mutasi State Tanpa `withAnimation` & Tanpa Transisi Kontekstual**: Perpindahan `vm.activeTab` di `MainAppView` dan `SidebarView` terjadi secara instan (1 frame) tanpa animasi arah (*directional spring transition*), tanpa indikator aktif yang meluncur (`matchedGeometryEffect`), tanpa animasi masuk saat aplikasi pertama dibuka, dan tanpa animasi skala sampul album saat lagu diputar/dijeda. | [`MainAppView.swift:L83-L157`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/MainAppView.swift#L83-L157), [`SidebarView.swift:L123-L147`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SidebarView.swift#L123-L147), [`NowPlayingSplitView.swift:L19-L45`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/NowPlayingSplitView.swift#L19-L45) | **TINGGI** |

---

## 2. Bedah Mendalam #BUG 1: Mengapa Tampilan Modal & Menu Bertabrakan Saat Aplikasi Dikecilkan?

### 2.1. Pelajaran Senior: Anatomi Ruang Kerja Saat Jendela Dikecilkan
Mari kita hitung ruang aktual yang tersedia ketika pengguna mengecilkan jendela aplikasi ke ukuran kompak (misalnya **`900 × 600 pt`** atau **`920 × 620 pt`**):
- **Lebar Total Jendela:** `920 pt`
- **Lebar Sidebar Kiri + Divider:** `220–236 pt` + `1 pt`
- **Lebar Area Kerja Kanan (*Workspace Width*):** `920 - 237 = 683 pt` (dikurangi padding kiri-kanan `40 pt` = **`643 pt`**).
- **Tinggi Total Jendela:** `620 pt`
- **Tinggi Top Bar (`~58 pt`) + Bottom Transport Bar (`~78 pt`):** `136 pt`
- **Tinggi Area Kerja Tengah (*Workspace Height*):** `620 - 136 =` **`484 pt`**.

Sekarang lihat apa yang terjadi pada **4 tempat** di kode kita ketika ruang kerja menyempit menjadi `643 × 484 pt`:

#### A. Tabrakan Kartu & Segmented Picker di `SettingsBentoView.swift`
Lihat [`SettingsBentoView.swift:L27`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SettingsBentoView.swift#L27) dan [`L110-L143`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SettingsBentoView.swift#L110-L143):
```swift
LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 14) {
    ...
    // CARD 3: Liquid Glass Appearance
    Picker("", selection: $vm.bgCanvasMode) {
        ForEach(BackgroundCanvasMode.allCases) { mode in
            Text(mode.rawValue).tag(mode.rawValue)
            // "Liquid Glass Refraction" | "Ambient Artwork Mesh" | "Pure Minimal Glass"
        }
    }
    .pickerStyle(SegmentedPickerStyle())
```
- Karena `columns` dipaksa selalu **2 kolom** (`[GridItem(.flexible()), GridItem(.flexible())]`), pada lebar `643 pt`, setiap kartu Bento hanya mendapat lebar `(643 - 14) / 2 = 314 pt` (dikurangi padding dalam kartu `32 pt` = **`282 pt`**).
- Di dalam lebar `282 pt` tersebut, `SegmentedPickerStyle()` dipaksa merender 3 teks panjang: `"Liquid Glass Refraction"`, `"Ambient Artwork Mesh"`, dan `"Pure Minimal Glass"` yang membutuhkan lebar minimum **`430 pt`**!
- Akibatnya, `NSSegmentedControl` **menembus batas kartu Bento ke kanan dan bertabrakan langsung dengan Kartu #4 di sebelahnya!**

#### B. Tabrakan Kolom Kiri (Artwork) dan Kolom Kanan (Queue/Lyrics) di `NowPlayingSplitView.swift`
Lihat [`NowPlayingSplitView.swift:L13-L105`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/NowPlayingSplitView.swift#L13-L105):
- `HStack(spacing: 28)` membagi ruang antara Hero Artwork + Info Lagu + 4 Badge di kiri dan `QueueLyricsView` di kanan.
- Secara vertikal, pada tinggi ruang kerja `484 pt`, gambar sampul berukuran `320 pt` + spasi `20 pt` + 3 baris teks judul/artis/album (`68 pt`) + spasi `20 pt` + baris badge (`30 pt`) + padding bawah (`16 pt`) membutuhkan **`474 pt`**. Begitu jendela dikecilkan sedikit saja atau judul lagu panjang, teks dan badge langsung saling menabrak/terpotong!
- Secara horizontal, karena tidak menggunakan `GeometryReader` untuk menghitung proporsi (misal `42%` kiri : `58%` kanan dengan skala sampul dinamis `clamp(160, min(geo.size.width * 0.34, geo.size.height - 150), 310)`), kedua panel saling berebut ruang.

#### C. Tabrakan Modal (`TelemetryModalView`, `TrackDetailsModalSheet`, `CreatePlaylistModalSheet`)
Lihat ukuran ketiga modal kita saat ini:
1. [`TelemetryModalView.swift:L190`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift#L190): `.frame(width: 620, height: 560)` — Di dalamnya (baris 142), 6 pill kategori log (`ALL`, `AUDIO`, `DSP`, `HARDWARE`, `LIBRARY`, `SYSTEM`) + kotak pencarian `Filter logs...` dijejalkan dalam satu `HStack` tanpa pembungkus responsif! Saat jendela dikecilkan, tinggi `560 pt` nyaris menabrak tepi atas/bawah jendela dan elemen filter di dalamnya berdesakan.
2. [`TrackDetailsModalSheet.swift:L92`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/TrackDetailsModalSheet.swift#L92): `.frame(width: 480, height: 440)` statis.
3. [`CreatePlaylistModalSheet.swift:L80`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/CreatePlaylistModalSheet.swift#L80): `.frame(width: 440)` statis.

#### D. Tabrakan Filter Chips & Kolom Album di `LibraryView.swift`
Lihat [`LibraryView.swift:L69-L105`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/LibraryView.swift#L69-L105):
- Baris filter memiliki 6 tombol chip + tombol Reset dalam satu `HStack` kaku (bukan `ScrollView(.horizontal, showsIndicators: false)`). Saat jendela dikecilkan, teks di dalam chip terlipat vertikal!
- Pada `trackRow` (baris 180), kolom nama album tetap dipaksa tampil meskipun lebar jendela sempit, sehingga menabrak judul lagu.

---

## 3. Bedah Mendalam #BUG 2: Mengapa Gestur *Tap-to-Click* Belum Konsisten?

> **Pelajaran Senior tentang Trackpad macOS (*Tap-to-Click* vs *Physical Click*):**
> Di macOS, ketika pengguna mengaktifkan fitur **Tap to Click** di *System Settings > Trackpad*, ketukan jari menghasilkan sekuens `leftMouseDown` diikuti `leftMouseUp` secara instan (selang waktu `< 15ms`).
> Ada **3 kesalahan kode** di aplikasi kita yang membuat ketukan ringan ini sering gagal atau terlambat merespons:

### 3.1. Kesalahan #1: `.onTapGesture(count: 2)` pada Kontainer Induk Menahan Single-Tap Selama 250ms!
Lihat [`FloatingTopToolbarView.swift:L71-L75`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/FloatingTopToolbarView.swift#L71-L75):
```swift
.liquidGlass(role: .floatingPillBar, cornerRadius: 18, tint: theme.primary)
.contentShape(Rectangle())
.onTapGesture(count: 2) {
    WindowZoomManager.shared.toggleFitToScreen()
}
```
**Mengapa ini merusak *Tap-to-Click*?**
- Ketika kita memasang `.onTapGesture(count: 2)` di seluruh kontainer `FloatingTopToolbarView`, *Gesture Recognizer* milik SwiftUI memasang *failure requirement* pada semua ketukan di dalam toolbar tersebut.
- Saat pengguna melakukan **1x tap** pada tombol `Music Folder`, tombol `Rescan`, tombol hapus pencarian (`xmark`), atau kolom `Search`, SwiftUI menahan event tersebut untuk menunggu apakah akan ada ketukan kedua!
- Padahal kita **sudah** memiliki `NSEvent.addLocalMonitorForEvents(matching: [.leftMouseDown])` di [`WindowZoomManager`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/LiquidGlassComponents.swift#L298-L327) yang memeriksa `event.clickCount == 2` di level AppKit **tanpa** menunda `clickCount == 1`!
- Jadi memasang `.onTapGesture(count: 2)` tambahan di level SwiftUI justru menciptakan konflik gestur!

### 3.2. Kesalahan #2: `Button` dengan `PlainButtonStyle()` + `Color.clear` Tanpa `.contentShape(Rectangle())`
Lihat [`SidebarView.swift:L121-L147`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SidebarView.swift#L121-L147):
```swift
private func sidebarNavItem(tab: AppTab, title: String, icon: String) -> some View {
    let isSelected = vm.activeTab == tab
    return Button(action: { ... }) {
        HStack(spacing: 10) {
            Image(systemName: icon)
            Text(title)
            Spacer() // <-- AREA KOSONG INI TIDAK BISA DI-TAP!
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(isSelected ? theme.primary.opacity(0.18) : Color.clear)
        .continuousSquircle(10)
    }
    .buttonStyle(PlainButtonStyle())
}
```
**Pelajaran Senior yang Wajib Diingat Seumur Hidup:**
Di SwiftUI macOS, `Color.clear` adalah **transparan terhadap hit-testing (tembus klik)**!
Ketika sebuah `Button` menggunakan `.buttonStyle(PlainButtonStyle())` dan background-nya `Color.clear` (saat tab belum dipilih), SwiftUI **hanya menganggap piksel huruf pada `Text(title)` dan garis ikon pada `Image` sebagai area yang bisa diklik**!
- Jika pengguna mengetuk (*tap-to-click*) di bagian tengah/kanan baris menu (`Spacer()`), atau di sela-sela huruf, **ketukan tersebut jatuh menembus ke background dan tombol sama sekali tidak merespons!**
- Hal yang persis sama terjadi pada:
  1. Tombol `Liked Songs` di [`SidebarView.swift:L63-L84`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SidebarView.swift#L63-L84)
  2. Setiap baris playlist `playlistRow` di [`SidebarView.swift:L149-L189`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SidebarView.swift#L149-L189)
  3. Tombol pilihan DAC di [`SettingsBentoView.swift:L36-L56`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SettingsBentoView.swift#L36-L56)
  4. Baris antrean lagu di [`QueueLyricsView.swift:L112-L134`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/QueueLyricsView.swift#L112-L134)
  5. Tombol kategori log di [`TelemetryModalView.swift:L220-L231`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift#L220-L231)

**Solusi Wajib:** Setiap label di dalam `Button` **harus** diakhiri dengan `.contentShape(Rectangle())` sebelum penutup label `Button`, sehingga seluruh kotak persegi panjang (termasuk `Spacer()` dan `padding`) menjadi area aktif 100%!

### 3.3. Kesalahan #3: Hit-Target Tombol Ikon Transport Bar Terlalu Kecil (`13×13 pt`)
Lihat [`LiquidTransportBarView.swift:L66-L118`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/LiquidTransportBarView.swift#L66-L118):
```swift
Button(action: { vm.isShuffle.toggle() }) {
    Image(systemName: "shuffle")
        .font(.system(size: 13, weight: .semibold))
        .foregroundStyle(vm.isShuffle ? theme.primary : .secondary)
}
.buttonStyle(TsrossaScaleButtonStyle())
```
- Ukuran ikon `shuffle`, `backward.fill`, `forward.fill`, dan `repeat` hanya `13–14 pt` tanpa `.frame(width: 32, height: 32)` dan tanpa `.contentShape(Rectangle())`!
- Menurut Apple Human Interface Guidelines (HIG), ukuran target ketukan minimum pada macOS adalah **`28×28 pt` hingga `32×32 pt`**. Tanpa frame dan `.contentShape(Rectangle())`, ketukan yang meleset 1 milimeter saja dari garis tipis ikon panah tidak akan terbaca!

---

## 4. Bedah Mendalam #BUG 3: Mengapa Tulisan `'BIT-PERFECT'` di Bar Now Playing Masih Bertumpuk?

Mari kita lihat bukti matematis di [`LiquidTransportBarView.swift:L128-L179`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/LiquidTransportBarView.swift#L128-L179):

```swift
// Right: Hardware Status Badge & Studio Volume Slider
HStack(spacing: 12) {
    if let track = engine.currentTrack {
        VStack(alignment: .trailing, spacing: 2) {
            HStack(spacing: 4) {
                Circle()
                    .fill(...)
                    .frame(width: 6, height: 6)
                Text(engine.telemetrySnapshot.streamIsInteger ? "BIT-PERFECT" : "DSP ACTIVE")
                    .font(.system(size: 9, weight: .bold, design: .monospaced))
                    .foregroundStyle(.primary)
            }
            Text("\(track.sampleRate / 1000) kHz / \(track.bitDepth)-bit")
                .font(.system(size: 10, design: .monospaced))
                .monospacedDigit()
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.white.opacity(0.06))
        .continuousSquircle(8)
    }

    // Volume Slider
    HStack(spacing: 6) {
        Image(systemName: ...) // ~14pt
        Slider(...)
            .frame(width: 80)  // 80pt
        Text("\(vm.volumeDb) dB")
            .frame(width: 36, alignment: .trailing) // 36pt
    }
}
.frame(width: 210, alignment: .trailing) // <-- BIANG KELADI MATEMATIKA!
```

### 📐 Perhitungan Matematika Ruang Kolom Kanan Transport Bar:
1. Total lebar kolom kanan dikunci sebesar: **`210 pt`**
2. Blok Slider Volume di sebelah kanan memakan ruang:
   - Ikon Speaker: `14 pt`
   - Spasi `HStack`: `6 pt`
   - `Slider`: `80 pt`
   - Spasi `HStack`: `6 pt`
   - Teks `"-60 dB"`: `36 pt`
   - **Total Slider Volume = `14 + 6 + 80 + 6 + 36 = 142 pt`**
3. Spasi `HStack(spacing: 12)` antara Badge dan Slider Volume: **`12 pt`**
4. **Sisa ruang untuk kotak Badge `'BIT-PERFECT'`:**
   $$\text{Sisa Lebar} = 210 - 142 - 12 = \mathbf{56\text{ pt}}$$
5. Dikurangi `.padding(.horizontal, 8)` (`16 pt`), ruang bersih untuk teks di dalam badge hanya **`40 pt`**!
6. Padahal:
   - Titik hijau (`6 pt`) + spasi (`4 pt`) + teks `"BIT-PERFECT"` (11 karakter monospaced 9pt bold) membutuhkan **`82 pt`**.
   - Teks `"192 kHz / 24-bit"` (16 karakter monospaced 10pt) membutuhkan **`102 pt`**.
7. Karena teks membutuhkan `102 pt` tetapi dipaksa masuk ke celah `40 pt` **tanpa `.lineLimit(1)` dan tanpa `.fixedSize(horizontal: true, vertical: false)`**, SwiftUI melipat teks tersebut menjadi 3 baris vertikal:
   ```text
   ● BIT-
     PERF
     ECT
   44 kHz
   / 16-
   bit
   ```
   Sehingga di dalam bar Now Playing yang tingginya terbatas, baris-baris teks tersebut saling berhimpitan/bertumpuk!

Selain itu, di [`NowPlayingSplitView.swift:L66-L84`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/NowPlayingSplitView.swift#L66-L84), fungsi `badgeView(text:color:)` juga tidak memiliki `.lineLimit(1)` dan `.fixedSize(horizontal: true, vertical: false)`, sehingga ketika jendela diperkecil, badge `"BIT-PERFECT"` di bawah sampul album ikut terlipat menjadi 2 baris!

---

## 5. Bedah Mendalam #BUG 4: Mengapa Background di Bar Kiri (`SidebarView`) Masih Hitam Saja dan Tidak Mengikuti Theme?

Ada **4 penyebab berantai** mengapa `SidebarView` selalu tampak hitam legam dan mengabaikan perubahan Theme / Accent Palette:

### 5.1. Penyebab #1: `SidebarView` Memakai Flat `theme.surface` (`#0F0F12` Hitam Pekat), Bukan Material Liquid Glass Berwarna
Lihat [`SidebarView.swift:L122`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SidebarView.swift#L122):
```swift
.background(theme.surface.opacity(0.70))
```
Dan lihat bagaimana `theme.surface` dihitung di [`Theme.swift:L166,L183`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Theme.swift#L166):
```swift
let neutralSat = min(0.12, sat * 0.22)
self.surface = hslToColor(h: hue, s: neutralSat, l: 0.065)
```
- Lightness `l = 0.065` (6.5%) dengan saturasi `0.08–0.12` adalah warna **`RGB(15, 15, 18)` — praktis hitam pekat!**
- Berbeda dengan `FloatingTopToolbarView` dan `LiquidTransportBarView` yang menggunakan `.liquidGlass(role: ..., tint: theme.primary)`, `SidebarView` **sama sekali tidak menggunakan `LiquidGlassModifier` atau gradasi warna `theme.primary` / `theme.primaryContainer`**!
- Akibatnya, meskipun pengguna memilih tema warna **Matcha (Hijau)**, **Rose (Merah Muda)**, **Gold (Emas)**, **Cyan (Biru Muda)**, atau **Dynamic Artwork**, bar kiri tetap berwarna hitam mati!

### 5.2. Penyebab #2: `MainAppView` Tidak Memasang `.preferredColorScheme(...)`
Lihat [`MainAppView.swift:L11-L18,L131-L135`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/MainAppView.swift#L11-L18):
- Walaupun `isEffectiveDark` bernilai `false` saat pengguna memilih **Light Glass**, `MainAppView` **tidak pernah memanggil `.preferredColorScheme(isEffectiveDark ? .dark : .light)`**!
- Akibatnya, `@Environment(\.colorScheme)` di dalam `LiquidGlassModifier` dan semua warna semantik SwiftUI (`.primary`, `.secondary`, `.ultraThinMaterial`) tetap terkunci pada Dark Mode bawaan macOS!

### 5.3. Penyebab #3: Elemen di Dalam `SidebarView` Memakai Hardcoded `Color.white.opacity(0.06)`
Di [`SidebarView.swift`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SidebarView.swift), permukaan tombol dan divider menggunakan nilai statis `Color.white.opacity(0.06)` yang tidak merespons warna tema maupun mode terang/gelap.

### 5.4. Penyebab #4: Mismatch Nilai Default `AppViewModel` vs Tag Picker di `SettingsBentoView`
Lihat [`AppViewModel.swift:L248-L258`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L248-L258):
- `themeMode` memiliki default `"DARK"`, sedangkan Picker di `SettingsBentoView` memakai `AppThemeMode.allCases` yang tag-nya `"System Auto"`, `"Dark Glass"`, `"Light Glass"`.
- `bgCanvasMode` memiliki default `"DYNAMIC"`, sedangkan Picker di `SettingsBentoView` memakai `BackgroundCanvasMode.allCases` yang tag-nya `"Liquid Glass Refraction"`, `"Ambient Artwork Mesh"`, `"Pure Minimal Glass"`.
- Ketidakcocokan string ini membuat kontrol pengaturan tema terlihat tidak sinkron saat pertama kali dibuka.

---

## 6. Bedah Mendalam #NOTE 1: Mengapa Shortcut Keyboard Belum Ada / Tidak Terlihat & Konflik?

1. **Absennya Visibilitas Shortcut di Seluruh UI (*Zero Discoverability*)**:
   - Di aplikasi macOS kelas profesional, pengguna mempelajari shortcut langsung dari antarmuka: badge `⌘1`–`⌘4` di samping menu Sidebar, tooltip `.help("Play / Pause (Space)")` pada setiap tombol kontrol, dan **Modal Daftar Shortcut Keyboard (`⌘/` atau tombol `?`)**. Saat ini di `SidebarView`, tidak ada badge shortcut sama sekali pada menu navigasi utama maupun tombol tambah playlist (`⌘N`).
2. **Bug Fatal `⌘F`: Bukannya Fokus ke Kolom Search, Malah Memicu `isTheaterMode`!**
   - Di [`FloatingTopToolbarView.swift:L19`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/FloatingTopToolbarView.swift#L19), teks placeholder menulis: `"Search ... (⌘F)"`.
   - Namun di [`AppViewModel.swift:L1023`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/AppViewModel.swift#L1023):
     ```swift
     case 3: // Cmd + F -> Toggle Theater Mode
         self.isTheaterMode.toggle()
         return nil
     ```
     Saat pengguna menekan `⌘F` untuk mencari lagu, kursor **tidak pernah masuk ke kotak pencarian**!
3. **Shortcut yang Belum Terdaftar di Menu Bar `.commands` (`main.swift`)**:
   - `⌘F` (Focus Search Field)
   - `⌘N` (New Playlist)
   - `⌘O` (Open Music Folder)
   - `⌘R` (Rescan Music Folder)
   - `⌘I` (Audio Track Details / Info)
   - `⌘L` (Toggle Like Song)
   - `⌥←` / `⌥→` (Seek Backward / Forward 5s)
   - `⌘/` (Keyboard Shortcuts Guide)

---

## 7. Bedah Mendalam #NOTE 2: Identifikasi & Pembersihan "AI Slop" pada UI/UX

> **Pelajaran Senior: Apa itu "AI Slop" dalam Desain UI/UX?**
> *AI Slop* adalah pola desain klise yang sering dihasilkan oleh LLM ketika diminta membuat aplikasi "modern/audiophile", yang justru membuat aplikasi terlihat murahan, berisik, dan tidak autentik di macOS.

Berikut adalah daftar **AI Slop** yang ditemukan di codebase kita dan harus dibersihkan:

| Lokasi File | Bentuk "AI Slop" Saat Ini | Standar Apple HIG / macOS Sequoia yang Benar |
| :--- | :--- | :--- |
| [`TelemetryModalView.swift:L30-L35`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/TelemetryModalView.swift#L30-L35) | **Fake Mobile Drag Handle (`Capsule 38×4pt`)** di bagian atas modal desktop macOS + warna hardcoded `TsrossaColors.deepNavy`. | Hapus drag handle ala layar sentuh iOS! Gunakan header sheet macOS yang bersih dengan material `.liquidGlass(role: .modalSheet, tint: theme.primary)`. |
| [`SidebarView.swift:L20-L22,L96-L115`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SidebarView.swift#L20-L22) | Subjudul monospaced `"Hi-Res Studio Player"` & tombol besar `"Audio Diagnostics ⌘U"` dengan ikon EKG yang memakan ruang bawah sidebar. | Gunakan subjudul bersih atau indikator status ringkas, dan rapikan footer sidebar dengan tombol kompak **Inspector / Shortcuts (`⌘/`)** & **Diagnostics (`⌘U`)**. |
| [`SettingsBentoView.swift:L18-L144`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/SettingsBentoView.swift#L18-L144) | Header & judul kartu serba `ALL-CAPS MONOSPACED` (`COREAUDIO HARDWARE`, `MEMORY & JITTER DEFENSE`, `LIQUID GLASS DESIGN`), ikon `sparkles` klise AI, serta pemilih warna teks mentah `DYNAMIC \| ROSE \| MATCHA \| GOLD \| CYAN`. | Gunakan tipografi standar Apple (`13pt semibold` dengan ikon SF Symbols kontekstual), dan ganti teks mentah `ROSE/MATCHA/GOLD` menjadi **Interactive Color Swatch Circles** (lingkaran warna asli dengan cincin aktif) yang elegan! |
| [`CreatePlaylistModalSheet.swift:L15-L53`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/CreatePlaylistModalSheet.swift#L15-L53) | Duplikasi judul (`NEW PLAYLIST` + `Create Playlist or Smart Filter`) dan label form `ALL-CAPS MONOSPACED` (`PLAYLIST NAME`, `SMART FILTER KEYWORD (OPTIONAL)`). | Gunakan label form *sentence-case* standar macOS (`12pt medium`) yang bersih, tenang, dan mendukung tombol `Return` untuk submit langsung. |
| [`FloatingTopToolbarView.swift:L19`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/Views/FloatingTopToolbarView.swift#L19) | Placeholder terlalu panjang & cerewet: `"Search titles, artists, albums, or genres (⌘F)..."`. | Gunakan placeholder ringkas `"Search tracks, artists, or albums"` dengan badge `⌘F` visual di sisi kanan kolom input. |

---

## 8. Bedah Mendalam #NOTE 3: Sistem Animasi Halus & Kontekstual di Semua Aspek

Saat ini, hampir seluruh transisi di aplikasi terjadi secara kaku (*instant snap*):
1. **Saat Membuka Aplikasi (*App Launch Entrance*)**:
   - Belum ada animasi masuk bertahap (*staggered spring entrance*) untuk Sidebar, Top Toolbar, Konten Utama, dan Bottom Transport Bar.
2. **Saat Berganti Menu Utama (`Now Playing` ↔ `Library` ↔ `Playlists` ↔ `Settings`)**:
   - Di [`MainAppView.swift:L139-L150`](file:///Users/yuka/Documents/Projects/tsrossa-macos/macos/Sources/MainAppView.swift#L139-L150), `switch vm.activeTab` tidak memiliki transisi kontekstual.
   - Seharusnya:
     - Setiap tab memiliki indeks urutan (`nowPlaying = 0`, `library = 1`, `playlist = 2`, `settings = 3`).
     - Ketika berpindah ke `Now Playing`, tampilkan transisi **Zoom-In & Lift** (`.scale(scale: 0.96).combined(with: .opacity)`) di mana Hero Artwork mekar dengan pegas halus.
     - Ketika berpindah antar menu daftar/pengaturan (`Library`, `Playlists`, `Settings`), gunakan transisi arah vertikal/horizontal yang selaras dengan arah perpindahan menu di Sidebar, disertai indikator pil aktif Sidebar yang **meluncur mulus menggunakan `@Namespace` dan `.matchedGeometryEffect(id: "sidebarActivePill", in: sidebarNamespace)`**!
3. **Saat Membuka & Menutup Modal (`New Playlist`, `Track Details`, `Audio Diagnostics`, `Keyboard Shortcuts`)**:
   - Harus dibungkus dengan `.animation(.spring(response: 0.32, dampingFraction: 0.84), value: ...)` serta efek backdrop blur/dimming halus dan transisi `.scale(scale: 0.94).combined(with: .opacity)`.
4. **Saat Lagu Diputar vs Dijeda (*Playback State Micro-Animation*)**:
   - Seperti pada Apple Music macOS, sampul album di `NowPlayingSplitView` harus membesar ke skala `1.0` dengan bayangan dalam saat **Playing**, dan mengecil halus ke skala `0.94` dengan bayangan meredup saat **Paused** menggunakan `.animation(.spring(response: 0.45, dampingFraction: 0.75), value: engine.isPlaying && !engine.isPaused)`.
5. **Saat Berganti Tab `Up Next` ↔ `Lyrics` & Perubahan Tema Warna**:
   - Indikator tab di `QueueLyricsView` harus meluncur dengan `matchedGeometryEffect`, dan perubahan warna tema (`theme.primary`) harus bertransisi mulus dengan `.animation(.easeInOut(duration: 0.35), value: vm.accentColorMode)`.

---

## 9. Kesimpulan Audit & Langkah Selanjutnya

Seluruh 4 bug dan 3 catatan penyempurnaan di [`bug-note.md`](file:///Users/yuka/Documents/Projects/tsrossa-macos/bug-note.md) telah teridentifikasi akar masalahnya hingga ke baris kode dan rumus matematikanya. Selanjutnya, langkah perbaikan dirumuskan secara sistematis dan terstruktur di dalam dokumen rencana implementasi:
👉 **[`PLANNING-PERBAIKAN/08-PLAN-PERBAIKAN-MODAL-TAP-THEME-SIDEBAR-SHORTCUT-ANTI-SLOP-DAN-ANIMASI.md`](file:///Users/yuka/Documents/Projects/tsrossa-macos/PLANNING-PERBAIKAN/08-PLAN-PERBAIKAN-MODAL-TAP-THEME-SIDEBAR-SHORTCUT-ANTI-SLOP-DAN-ANIMASI.md)**
