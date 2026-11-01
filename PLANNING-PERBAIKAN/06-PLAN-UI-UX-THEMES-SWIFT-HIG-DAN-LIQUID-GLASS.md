# Plan 06: UI/UX Themes Refactoring ke Apple HIG, Swift Idioms & "Liquid Glass" Design System

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merombak arsitektur tema dan antarmuka pengguna TSRossa macOS dari hasil translasi mentah Android Material You (`DynamicMaterialTheme`) menjadi sistem desain **Apple Human Interface Guidelines (HIG) "Liquid Glass"** (macOS Tahoe / visionOS / SwiftUI modern) yang mendukung refraksi optik real-time, **Anisotropic Specular Rim**, **Continuous Squircle (`.continuous`)** dengan geometri konsentris, adaptasi otomatis **Light Mode / Dark Mode / Reduce Transparency**, pemecahan *God View* `MainAppView.swift` (3.579 baris) menjadi komponen modular yang cepat, serta isolasi re-render `PlaybackScrubberView`.

**Architecture:**
1. Membangun `macos/LiquidGlassComponents.swift` yang membungkus `NSVisualEffectView` (`MacVisualEffectView`) dan `LiquidGlassModifier` (`.liquidGlass(...)`) dengan 5 lapisan optik: *Backdrop Blur Material*, *Chromatic Artwork Tint*, *Surface Specular Sheen*, *Anisotropic Rim Stroke* (`0.5pt–1.0pt` gradien diagonal), dan *Dual Ambient/Key Shadow*.
2. Mengubah `Theme.swift` agar menghormati `@Environment(\.colorScheme)` (`Light` & `Dark`), `@Environment(\.accessibilityReduceTransparency)`, `@Environment(\.accessibilityReduceMotion)`, dan `@Environment(\.controlActiveState)`.
3. Mengganti layout kaku `VStack` di `MainAppView.swift` menjadi `NavigationSplitView` + full-bleed canvas dengan `.safeAreaInset(edge: .top)` dan `.safeAreaInset(edge: .bottom)` sehingga daftar lagu dan sampul album mengalir di **bawah** *Floating Top Pill Bar* dan *Floating Bottom Transport Capsule*, menciptakan efek pembiasan **Liquid Glass** sejati.
4. Memecah `MainAppView.swift` menjadi 8 file modular di `macos/Views/` dan mengisolasi pembacaan `engine.currentTime` ke dalam `PlaybackScrubberView.swift` agar jendela utama tidak lagi di-rerender 4 kali per detik.

**Tech Stack:** Swift 5.9+, `SwiftUI` (`Material`, `NavigationSplitView`, `.safeAreaInset`, `TimelineView`, `.clipShape(.rect(cornerRadius:style: .continuous))`, `.foregroundStyle`, `.tint`, `.sheet`, `.presentationBackground`), `AppKit` (`NSVisualEffectView`).

---

## File Structure

- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/LiquidGlassComponents.swift` — Komponen inti **Liquid Glass**:
  - `enum LiquidGlassRole`: `.navigationSidebar`, `.floatingPillBar`, `.transportCapsule`, `.bentoCard`, `.modalSheet`, `.interactiveChip`
  - `struct ConcentricSquircle`: Kalkulator jari-jari konsentris ($R_{\text{inner}} = \max(6, R_{\text{outer}} - \text{padding})$) dengan `RoundedRectangle(cornerRadius: r, style: .continuous)`
  - `struct MacVisualEffectView: NSViewRepresentable`: Wrapper `NSVisualEffectView` untuk `.sidebar`, `.headerView`, `.hudWindow`, `.underWindowBackground`
  - `struct LiquidGlassModifier: ViewModifier`: Implementasi 5 lapisan optik Liquid Glass + fallback otomatis saat `accessibilityReduceTransparency` aktif
  - `struct LiquidGlassButtonStyle: ButtonStyle`: Efek *spring physics* (`.spring(response: 0.28, dampingFraction: 0.78)`) dan *specular glow* saat hover/press
  - `struct AmbientArtworkMeshBackground: View`: Kanvas bias cahaya dinamis dari palet sampul album di belakang kaca
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/Theme.swift:1-405` — Mengganti `DynamicMaterialTheme` flat dengan `LiquidGlassTheme` adaptif (`colorScheme: ColorScheme`), mengganti Stringly-typed mode dengan enum strongly-typed (`AppThemeMode`, `BackgroundCanvasMode`, `AccentColorMode`, `MainWorkspaceTab`, `QueuePanelTab`), dan mengganti seluruh `.cornerRadius()` dengan `.continuousSquircle(...)`.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/Views/PlaybackScrubberView.swift` — Subview terisolasi yang mengamati `engine.currentTime` & `engine.totalDuration` secara lokal agar 3.500+ baris UI lainnya tidak ikut di-rerender setiap 250 ms.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/Views/LiquidTransportBarView.swift` — Kapsul kontrol pemutaran mengambang di bagian bawah (`.safeAreaInset(edge: .bottom)`) dengan artwork konsentris, kontrol Repeat 3-state (`repeat`, `repeat.1`), Shuffle, DAC badge, dan slider volume Studio Taper.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/Views/SidebarView.swift` — Sidebar navigasi `.liquidGlass(role: .navigationSidebar)` dengan daftar tab, playlist yang di-pin, dan menu konteks native (Rename, Pin, Clear, Delete).
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/Views/LibraryView.swift` — Tampilan perpustakaan lagu dengan filter chip `.liquidGlass(role: .interactiveChip)`, pemilih 12 mode Sortir (`SortField` + `SortOrder`), dan baris lagu yang mengalir di bawah header kaca.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/Views/NowPlayingSplitView.swift` — Tampilan hero sampul album beresolusi tinggi dengan *specular rim*, lencana sinyal *Bit-Perfect / MQA / Sample Rate*, dan integrasi panel samping.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/Views/QueueLyricsView.swift` — Panel antrean (*Priority Queue* & *Up Next*) serta penampil lirik tersemat/LRC dengan kaca transparan.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/Views/SettingsBentoView.swift` — Bento Grid pengaturan DAC, DSP, Library, Tema **Liquid Glass** (`System Auto` / `Light` / `Dark`, `Liquid Glass Refraction` / `Ambient Mesh` / `Pure Glass`), dan Updater.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/Views/TrackDetailsModalSheet.swift` — Native macOS `.sheet` dengan `.presentationBackground(.ultraThinMaterial)` untuk inspeksi properti teknis file audio dan dialog pembuatan/ubah nama playlist.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/MainAppView.swift` — Merampingkan dari 3.579 baris menjadi `< 300` baris yang menyatukan `NavigationSplitView`, `AmbientArtworkMeshBackground`, `.safeAreaInset(edge: .top)`, `.safeAreaInset(edge: .bottom)`, dan `.sheet` native.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/tests/swift/ColorAndThemeTests.swift` — Menambahkan unit test untuk matematika `ConcentricSquircle`, enum tema, dan resolusi warna Light vs Dark Mode di `LiquidGlassTheme`.

---

### Task 1: Tulis Unit Test Geometri Konsentris & Tema Adaptif, lalu Bangun `LiquidGlassComponents.swift`

**Files:**
- Modify: `tests/swift/ColorAndThemeTests.swift`
- Create: `macos/LiquidGlassComponents.swift`

- [ ] **Step 1: Tambahkan unit test untuk `ConcentricSquircle` dan adaptasi Light/Dark `LiquidGlassTheme` di `tests/swift/ColorAndThemeTests.swift`**

```swift
    TestSuite.run("ConcentricSquircle enforces R_inner = max(6, R_outer - padding)") {
        // Container luar r=24 dengan padding=8 -> inner harus r=16
        try TestSuite.assertEqual(ConcentricSquircle.innerRadius(outerRadius: 24, padding: 8), 16)
        // Container luar r=14 dengan padding=12 -> inner dikunci minimal 6pt agar tetap squircle halus
        try TestSuite.assertEqual(ConcentricSquircle.innerRadius(outerRadius: 14, padding: 12), 6)
    }

    TestSuite.run("LiquidGlassTheme adapts surface luminance and rim opacity between Light and Dark mode") {
        let accent = NSColor(srgbRed: 0.2, green: 0.6, blue: 1.0, alpha: 1.0)
        let darkTheme = LiquidGlassTheme(accentNSColor: accent, isDark: true, reduceTransparency: false)
        let lightTheme = LiquidGlassTheme(accentNSColor: accent, isDark: false, reduceTransparency: false)

        try TestSuite.assertTrue(darkTheme.isDark)
        try TestSuite.assertFalse(lightTheme.isDark)
        try TestSuite.assertTrue(darkTheme.specularTopOpacity != lightTheme.specularTopOpacity)
    }
```

- [ ] **Step 2: Buat `macos/LiquidGlassComponents.swift` dengan `MacVisualEffectView`, `ConcentricSquircle`, `LiquidGlassModifier`, dan `AmbientArtworkMeshBackground`**

```swift
import SwiftUI
import AppKit

public enum LiquidGlassRole {
    case navigationSidebar
    case floatingPillBar
    case transportCapsule
    case bentoCard
    case modalSheet
    case interactiveChip
}

public enum ConcentricSquircle {
    public static func innerRadius(outerRadius: CGFloat, padding: CGFloat, minimum: CGFloat = 6) -> CGFloat {
        max(minimum, outerRadius - padding)
    }
}

public struct MacVisualEffectView: NSViewRepresentable {
    public var material: NSVisualEffectView.Material
    public var blendingMode: NSVisualEffectView.BlendingMode
    public var state: NSVisualEffectView.State

    public init(
        material: NSVisualEffectView.Material = .hudWindow,
        blendingMode: NSVisualEffectView.BlendingMode = .withinWindow,
        state: NSVisualEffectView.State = .followsWindowActiveState
    ) {
        self.material = material
        self.blendingMode = blendingMode
        self.state = state
    }

    public func makeNSView(context: Context) -> NSVisualEffectView {
        let view = NSVisualEffectView()
        view.material = material
        view.blendingMode = blendingMode
        view.state = state
        view.isEmphasized = true
        return view
    }

    public func updateNSView(_ nsView: NSVisualEffectView, context: Context) {
        nsView.material = material
        nsView.blendingMode = blendingMode
        nsView.state = state
    }
}

public struct LiquidGlassModifier: ViewModifier {
    public var role: LiquidGlassRole
    public var cornerRadius: CGFloat
    public var tint: Color
    public var isInteractive: Bool
    public var isHovered: Bool

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.controlActiveState) private var controlActiveState

    public func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        let isDark = (colorScheme == .dark)
        let activeFactor: Double = (controlActiveState == .inactive) ? 0.65 : 1.0
        let hoverBoost: Double = (isInteractive && isHovered) ? 0.08 : 0.0

        content
            .background {
                if reduceTransparency {
                    shape.fill(Color(nsColor: .windowBackgroundColor))
                } else {
                    ZStack {
                        // Layer 1: Native SwiftUI/AppKit Optical Blur Material
                        shape.fill(swiftUIMaterial)

                        // Layer 2: Chromatic Artwork Refraction Tint
                        shape.fill(
                            tint.opacity((isDark ? 0.11 : 0.08) * activeFactor + hoverBoost)
                        )

                        // Layer 3: Top Surface Specular Sheen
                        shape.fill(
                            LinearGradient(
                                colors: [
                                    Color.white.opacity((isDark ? 0.16 : 0.42) * activeFactor + hoverBoost),
                                    Color.white.opacity(isDark ? 0.02 : 0.10),
                                    Color.clear
                                ],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                    }
                }
            }
            .clipShape(shape)
            .overlay {
                // Layer 4: Anisotropic Specular Rim Highlight (Top-Leading bright -> Bottom-Trailing tinted)
                shape.strokeBorder(
                    LinearGradient(
                        stops: [
                            .init(color: Color.white.opacity((isDark ? 0.44 : 0.72) * activeFactor + hoverBoost), location: 0.0),
                            .init(color: Color.white.opacity(isDark ? 0.10 : 0.22), location: 0.45),
                            .init(color: tint.opacity((isDark ? 0.28 : 0.20) * activeFactor), location: 1.0)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: role == .transportCapsule || role == .floatingPillBar ? 1.0 : 0.75
                )
            }
            .shadow(
                color: Color.black.opacity(shadowOpacity(isDark: isDark)),
                radius: shadowRadius,
                x: 0,
                y: shadowYOffset
            )
    }

    private var swiftUIMaterial: Material {
        switch role {
        case .navigationSidebar: return .ultraThinMaterial
        case .floatingPillBar: return .thinMaterial
        case .transportCapsule: return .ultraThinMaterial
        case .bentoCard: return .regularMaterial
        case .modalSheet: return .thickMaterial
        case .interactiveChip: return .ultraThinMaterial
        }
    }

    private var shadowRadius: CGFloat {
        switch role {
        case .transportCapsule, .modalSheet: return 24
        case .floatingPillBar: return 16
        case .bentoCard: return 12
        case .navigationSidebar, .interactiveChip: return 6
        }
    }

    private var shadowYOffset: CGFloat {
        switch role {
        case .transportCapsule, .modalSheet: return 10
        case .floatingPillBar, .bentoCard: return 6
        case .navigationSidebar, .interactiveChip: return 2
        }
    }

    private func shadowOpacity(isDark: Bool) -> Double {
        switch role {
        case .transportCapsule, .modalSheet: return isDark ? 0.42 : 0.16
        case .floatingPillBar, .bentoCard: return isDark ? 0.28 : 0.10
        case .navigationSidebar, .interactiveChip: return isDark ? 0.16 : 0.06
        }
    }
}

public extension View {
    func liquidGlass(
        role: LiquidGlassRole = .bentoCard,
        cornerRadius: CGFloat = 18,
        tint: Color = .accentColor,
        isInteractive: Bool = false,
        isHovered: Bool = false
    ) -> some View {
        modifier(
            LiquidGlassModifier(
                role: role,
                cornerRadius: cornerRadius,
                tint: tint,
                isInteractive: isInteractive,
                isHovered: isHovered
            )
        )
    }

    func continuousSquircle(_ radius: CGFloat) -> some View {
        clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
    }
}
```

---

### Task 2: Refactor `Theme.swift` ke Strongly-Typed Enums & Sistem Warna Adaptif Light/Dark Mode

**Files:**
- Modify: `macos/Theme.swift:1-405`
- Modify: `macos/AppViewModel.swift`

- [ ] **Step 1: Tambahkan Strongly-Typed Enums untuk Tema, Kanvas, Aksen, dan Tab di `macos/Theme.swift`**

```swift
public enum AppThemeMode: String, Codable, CaseIterable, Identifiable {
    case system = "System Auto"
    case dark = "Dark Glass"
    case light = "Light Glass"
    public var id: String { rawValue }

    public var preferredColorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .dark: return .dark
        case .light: return .light
        }
    }
}

public enum BackgroundCanvasMode: String, Codable, CaseIterable, Identifiable {
    case liquidRefraction = "Liquid Glass Refraction"
    case ambientMesh = "Ambient Artwork Mesh"
    case pureGlass = "Pure Minimal Glass"
    public var id: String { rawValue }
}

public enum AccentColorMode: String, Codable, CaseIterable, Identifiable {
    case dynamicArtwork = "Dynamic Artwork"
    case systemBlue = "Apple Blue"
    case studioAmber = "Studio Amber"
    case emeraldHiRes = "Hi-Res Emerald"
    case amethyst = "Royal Amethyst"
    public var id: String { rawValue }
}

public enum MainWorkspaceTab: String, Codable, CaseIterable, Identifiable {
    case nowPlaying = "Now Playing"
    case library = "Library"
    case playlists = "Playlists"
    case settings = "Settings"
    public var id: String { rawValue }
}

public enum QueuePanelTab: String, Codable, CaseIterable, Identifiable {
    case upNext = "Up Next"
    case lyrics = "Lyrics"
    public var id: String { rawValue }
}
```

- [ ] **Step 2: Perbarui `LiquidGlassTheme` (serta alias kompatibilitas `DynamicMaterialTheme`) di `macos/Theme.swift` agar mendukung Light Mode, Dark Mode, dan WCAG Contrast**

Ganti warna flat Android (`#0F0D13`, `#1D1B20`) dengan token semantik adaptif yang menyesuaikan `isDark` dan `reduceTransparency`, serta menggunakan `ColorContrastHelper.ensureContrast` untuk memastikan rasio kontras teks dan aksen selalu memenuhi standar WCAG AA (`>= 4.5:1`) baik di Light Mode maupun Dark Mode.

- [ ] **Step 3: Jalankan `./run_tests.sh` untuk memverifikasi `ColorAndThemeTests` lulus**

```bash
./run_tests.sh
```

Expected: Seluruh unit test di `ColorAndThemeTests` lulus.

---

### Task 3: Buat `PlaybackScrubberView.swift` & `LiquidTransportBarView.swift` (Isolasi Re-render 4 Hz & Kapsul Transport Liquid Glass)

**Files:**
- Create: `macos/Views/PlaybackScrubberView.swift`
- Create: `macos/Views/LiquidTransportBarView.swift`

- [ ] **Step 1: Buat `macos/Views/PlaybackScrubberView.swift` untuk mengisolasi observasi `engine.currentTime`**

Dengan memisahkan `PlaybackScrubberView` ke `View` mandiri yang memegang `@ObservedObject var engine: AudioEngine`, perubahan `engine.currentTime` setiap 250 ms **hanya** me-rerender komponen scrubber berukuran tinggi `28pt` ini, bukan seluruh daftar lagu atau window utama:

```swift
import SwiftUI

struct PlaybackScrubberView: View {
    @ObservedObject var engine: AudioEngine
    var accentColor: Color
    @State private var dragValue: Double? = nil
    @State private var isHoveringBar: Bool = false

    var body: some View {
        let duration = max(engine.totalDuration, 0.001)
        let displayedTime = dragValue ?? engine.currentTime
        let progress = min(max(displayedTime / duration, 0.0), 1.0)

        HStack(spacing: 10) {
            Text(Self.formatTime(displayedTime))
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .monospacedDigit()
                .foregroundStyle(.secondary)
                .frame(width: 44, alignment: .trailing)

            GeometryReader { geo in
                let width = max(geo.size.width, 1)
                ZStack(alignment: .leading) {
                    Capsule(style: .continuous)
                        .fill(Color.primary.opacity(0.14))
                        .frame(height: isHoveringBar || dragValue != nil ? 6 : 4)

                    Capsule(style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [accentColor, accentColor.opacity(0.82)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: width * CGFloat(progress), height: isHoveringBar || dragValue != nil ? 6 : 4)

                    if isHoveringBar || dragValue != nil {
                        Circle()
                            .fill(Color.white)
                            .frame(width: 12, height: 12)
                            .shadow(color: Color.black.opacity(0.3), radius: 3, x: 0, y: 1)
                            .offset(x: max(0, min(width - 12, width * CGFloat(progress) - 6)))
                    }
                }
                .frame(maxHeight: .infinity)
                .contentShape(Rectangle())
                .onHover { hovering in
                    withAnimation(.spring(response: 0.22, dampingFraction: 0.8)) {
                        isHoveringBar = hovering
                    }
                }
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            engine.isSeeking = true
                            let fraction = min(max(0.0, Double(value.location.x / width)), 1.0)
                            dragValue = fraction * engine.totalDuration
                        }
                        .onEnded { value in
                            let fraction = min(max(0.0, Double(value.location.x / width)), 1.0)
                            let targetTime = fraction * engine.totalDuration
                            dragValue = nil
                            engine.isSeeking = false
                            engine.seek(to: targetTime)
                        }
                )
            }
            .frame(height: 18)

            Text(Self.formatTime(engine.totalDuration))
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .monospacedDigit()
                .foregroundStyle(.secondary)
                .frame(width: 44, alignment: .leading)
        }
    }

    static func formatTime(_ seconds: Double) -> String {
        guard seconds.isFinite && seconds > 0 else { return "0:00" }
        let totalSec = Int(seconds.rounded(.down))
        let mins = totalSec / 60
        let secs = totalSec % 60
        return String(format: "%d:%02d", mins, secs)
    }
}
```

- [ ] **Step 2: Buat `macos/Views/LiquidTransportBarView.swift` sebagai kapsul mengambang konsentris (`.liquidGlass(role: .transportCapsule, cornerRadius: 24)`)**

Bangun `LiquidTransportBarView` dengan:
- Sampul album mini di sisi kiri yang menggunakan jari-jari konsentris `ConcentricSquircle.innerRadius(outerRadius: 24, padding: 10)` (`14pt` `.continuous`).
- Tombol Shuffle, Previous, Play/Pause (dengan animasi `.spring(response: 0.28, dampingFraction: 0.78)`), Next, dan **Repeat 3-State** (`off`, `all`, `one` dengan ikon SF Symbols `"repeat"` / `"repeat.1"`).
- `PlaybackScrubberView` di bagian tengah.
- Indikator status *Bit-Perfect / Sample Rate* dan kontrol volume Studio Taper di sisi kanan.

---

### Task 4: Pecah `MainAppView.swift` menjadi Komponen Modular di `macos/Views/` dengan Standar Swift HIG Modern

**Files:**
- Create: `macos/Views/SidebarView.swift`
- Create: `macos/Views/LibraryView.swift`
- Create: `macos/Views/NowPlayingSplitView.swift`
- Create: `macos/Views/QueueLyricsView.swift`
- Create: `macos/Views/SettingsBentoView.swift`
- Create: `macos/Views/TrackDetailsModalSheet.swift`
- Modify: `macos/MainAppView.swift`

- [ ] **Step 1: Buat `SidebarView.swift`, `LibraryView.swift`, `NowPlayingSplitView.swift`, `QueueLyricsView.swift`, `SettingsBentoView.swift`, dan `TrackDetailsModalSheet.swift` di `macos/Views/`**

Pastikan keenam file view modular tersebut mematuhi seluruh aturan Swift & Apple HIG dari [`06-AUDIT-UI-UX-THEMES-SWIFT-HIG-DAN-LIQUID-GLASS.md`](file:///Users/yuka/Documents/Projects/tsrossa-macos/AUDIT-ANALISA/06-AUDIT-UI-UX-THEMES-SWIFT-HIG-DAN-LIQUID-GLASS.md):
1. **0 pemanggilan `.cornerRadius()` terdeprekasi:** Seluruh elemen menggunakan `.continuousSquircle(r)` atau `.liquidGlass(role:cornerRadius:tint:)`.
2. **Hierarki `.foregroundStyle(.primary / .secondary / .tertiary)`:** Menggantikan `.foregroundColor(...)` ber-opasitas manual agar teks beradaptasi sempurna di atas permukaan kaca pada Light Mode dan Dark Mode.
3. **Penggunaan `.tint(...)`:** Menggantikan `.accentColor(...)` yang terdeprekasi.
4. **Tipografi Numerik Presisi:** Seluruh angka sample rate (`192.0 kHz`), bit depth (`24-bit`), nomor trek, dan durasi wajib menyertakan `.monospacedDigit()`.
5. **Native `.sheet` dengan `.presentationBackground(.ultraThinMaterial)`:** `TrackDetailsModalSheet.swift` dan `TelemetryModalView.swift` disajikan sebagai sheet macOS native (yang otomatis mendukung tombol `Esc`), bukan lagi `ZStack` manual.

- [ ] **Step 2: Rampingkan `macos/MainAppView.swift` untuk menggunakan `NavigationSplitView` + `.safeAreaInset(edge: .top)` + `.safeAreaInset(edge: .bottom)`**

Susun `MainAppView.swift` agar konten utama mengalir di **bawah** *Floating Top Pill Bar* dan *Floating Bottom Transport Capsule*, sehingga sampul album dan baris lagu membiaskan warna secara real-time saat di-scroll:

```swift
import SwiftUI

struct MainAppView: View {
    @EnvironmentObject var appState: AppViewModel
    @EnvironmentObject var engine: AudioEngine
    @Environment(\.colorScheme) private var systemColorScheme
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    var body: some View {
        let effectiveDark = (appState.themeMode == .dark) || (appState.themeMode == .system && systemColorScheme == .dark)
        let theme = appState.buildLiquidGlassTheme(isDark: effectiveDark, reduceTransparency: reduceTransparency)

        ZStack {
            // Lapisan Dasar: Kanvas Refraksi Dinamis dari Sampul Lagu Aktif
            AmbientArtworkMeshBackground(
                artwork: engine.currentTrack?.artwork,
                accentColor: theme.primary,
                canvasMode: appState.bgCanvasMode,
                isDark: effectiveDark
            )
            .ignoresSafeArea()

            // Area Kerja Utama dengan Scroll-Under Liquid Glass Insets
            NavigationSplitView {
                SidebarView(theme: theme)
                    .navigationSplitViewColumnWidth(min: 200, ideal: 230, max: 280)
            } detail: {
                workspaceDetailContent(theme: theme)
                    .safeAreaInset(edge: .top, spacing: 0) {
                        FloatingTopToolbarView(theme: theme)
                            .padding(.horizontal, 18)
                            .padding(.top, 10)
                            .padding(.bottom, 6)
                    }
                    .safeAreaInset(edge: .bottom, spacing: 0) {
                        LiquidTransportBarView(engine: engine, theme: theme)
                            .padding(.horizontal, 18)
                            .padding(.bottom, 12)
                            .padding(.top, 6)
                    }
            }
        }
        .preferredColorScheme(appState.themeMode.preferredColorScheme)
        .tint(theme.primary)
        .sheet(isPresented: $appState.showTelemetryModal) {
            TelemetryModalView(theme: theme)
                .EnvironmentObject(engine)
                .presentationBackground(.ultraThinMaterial)
        }
        .sheet(isPresented: $appState.showTrackDetailsModal) {
            TrackDetailsModalSheet(theme: theme)
                .environmentObject(appState)
                .environmentObject(engine)
                .presentationBackground(.ultraThinMaterial)
        }
    }

    @ViewBuilder
    private func workspaceDetailContent(theme: LiquidGlassTheme) -> some View {
        switch appState.selectedWorkspaceTab {
        case .nowPlaying:
            NowPlayingSplitView(theme: theme)
        case .library:
            LibraryView(theme: theme)
        case .playlists:
            LibraryView(theme: theme, playlistsOnlyMode: true)
        case .settings:
            SettingsBentoView(theme: theme)
        }
    }
}
```

- [ ] **Step 3: Verifikasi bahwa tidak ada lagi `.cornerRadius(` atau `.accentColor(` terdeprekasi di `macos/`**

```bash
grep -rn "\.cornerRadius(" macos/
grep -rn "\.accentColor(" macos/
```

Expected: Kedua perintah mengembalikan kosong.

- [ ] **Step 4: Jalankan `./run_tests.sh` dan `./build_mac.sh` untuk memverifikasi seluruh test dan build aplikasi lulus**

```bash
./run_tests.sh && ./build_mac.sh
```

Expected: Seluruh unit test C++ dan Swift lulus, dan `build/TSRossa.app` berhasil dikompilasi serta ditandatangani (`codesign`).

- [ ] **Step 5: Verifikasi bahwa direktori referensi Android (`/Users/yuka/Documents/Projects/tsrossa-ujicoba`) tetap 100% bersih & tidak tersentuh**

```bash
git -C /Users/yuka/Documents/Projects/tsrossa-ujicoba status --short
```

Expected: Output kosong (0 perubahan di direktori referensi).

- [ ] **Step 6: Commit hasil Plan 06**

```bash
git add macos/ tests/
git commit -m "feat(ui): transform UI/UX to Apple HIG Liquid Glass, continuous squircles, and modular SwiftUI views"
```

---

## Review Focus

1. **Refraksi Optik *Scroll-Under* Sejati (`.safeAreaInset`):** Dengan memindahkan Top Bar dan Bottom Transport Bar ke dalam `.safeAreaInset(edge: .top / .bottom)` di atas kanvas `ScrollView` utama, elemen daftar lagu dan warna sampul album benar-benar bergerak di **belakang** lapisan `.ultraThinMaterial`, menghasilkan pembiasan cahaya khas **Apple Liquid Glass**.
2. **Eliminasi Re-Render Global 4 Hz:** Pemisahan `PlaybackScrubberView` memastikan pembaruan `engine.currentTime` (4 kali per detik) hanya menggambar ulang bilah progres kecil tanpa memicu evaluasi ulang pada `LibraryView`, `SidebarView`, atau `NowPlayingSplitView`.
3. **Dukungan Penuh Ekosistem Aksesibilitas & Mode Tampilan macOS:** Seluruh permukaan kaca merespons perubahan `System Auto / Light / Dark Mode`, meredupkan pantulan saat window tidak aktif (`controlActiveState == .inactive`), dan otomatis beralih ke permukaan solid ber-kontras tinggi ketika pengguna mengaktifkan `Reduce Transparency` di pengaturan macOS.
