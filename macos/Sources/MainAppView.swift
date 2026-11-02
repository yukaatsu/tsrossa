import SwiftUI

public struct MainAppView: View {
    @ObservedObject var vm = AppViewModel.shared
    @ObservedObject var engine = AudioEngine.shared
    @Environment(\.colorScheme) private var systemColorScheme
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    public init() {}

    private var isEffectiveDark: Bool {
        if vm.themeMode == "LIGHT" || vm.themeMode == "Light Glass" {
            return false
        } else if vm.themeMode == "DARK" || vm.themeMode == "Dark Glass" || vm.themeMode == "OLED" {
            return true
        }
        return systemColorScheme == .dark
    }

    private var currentCanvasMode: BackgroundCanvasMode {
        switch vm.bgCanvasMode {
        case "AURA", "Ambient Artwork Mesh":
            return .ambientMesh
        case "BLACK", "Pure Minimal Glass":
            return .pureGlass
        default:
            return .liquidRefraction
        }
    }

    private var activeTheme: LiquidGlassTheme {
        if vm.accentColorMode == "ROSE" {
            return LiquidGlassTheme(accentNSColor: NSColor(srgbRed: 1.0, green: 0.18, blue: 0.33, alpha: 1.0), isDark: isEffectiveDark, reduceTransparency: reduceTransparency)
        } else if vm.accentColorMode == "MATCHA" {
            return LiquidGlassTheme(accentNSColor: NSColor(srgbRed: 0.62, green: 0.82, blue: 0.38, alpha: 1.0), isDark: isEffectiveDark, reduceTransparency: reduceTransparency)
        } else if vm.accentColorMode == "GOLD" {
            return LiquidGlassTheme(accentNSColor: NSColor(srgbRed: 1.0, green: 0.84, blue: 0.0, alpha: 1.0), isDark: isEffectiveDark, reduceTransparency: reduceTransparency)
        } else if vm.accentColorMode == "CYAN" {
            return LiquidGlassTheme(accentNSColor: NSColor(srgbRed: 0.0, green: 0.90, blue: 1.0, alpha: 1.0), isDark: isEffectiveDark, reduceTransparency: reduceTransparency)
        }
        // Dynamic artwork seed
        return LiquidGlassTheme(accentNSColor: vm.currentTheme.seedColor.toNSColor(), isDark: isEffectiveDark, reduceTransparency: reduceTransparency)
    }

    public var body: some View {
        let theme = activeTheme

        ZStack {
            // Layer 0: Ambient Artwork Mesh Background (behind entire window)
            AmbientArtworkMeshBackground(
                artwork: engine.currentTrack?.artwork,
                accentColor: theme.primary,
                canvasMode: currentCanvasMode,
                isDark: isEffectiveDark
            )
            .ignoresSafeArea()

            // Layer 1: NavigationSplitView Workspace
            NavigationSplitView {
                SidebarView(theme: theme)
                    .navigationSplitViewColumnWidth(min: 210, ideal: 230, max: 270)
            } detail: {
                workspaceContent(theme: theme)
                    .safeAreaInset(edge: .top, spacing: 0) {
                        FloatingTopToolbarView(theme: theme)
                            .padding(.horizontal, 18)
                            .padding(.top, 10)
                            .padding(.bottom, 6)
                    }
                    .safeAreaInset(edge: .bottom, spacing: 0) {
                        LiquidTransportBarView(theme: theme)
                            .padding(.horizontal, 18)
                            .padding(.bottom, 12)
                            .padding(.top, 6)
                    }
            }

            // Layer 2: Modal Sheets & Overlays
            if vm.showNewPlaylistModal {
                Color.black.opacity(0.65)
                    .ignoresSafeArea()
                    .onTapGesture { vm.showNewPlaylistModal = false }

                CreatePlaylistModalSheet(theme: theme)
                    .transition(.scale.combined(with: .opacity))
            }

            if vm.showTrackDetailsModal {
                Color.black.opacity(0.65)
                    .ignoresSafeArea()
                    .onTapGesture { vm.showTrackDetailsModal = false }

                TrackDetailsModalSheet(theme: theme)
                    .continuousSquircle(24)
                    .transition(.scale.combined(with: .opacity))
            }

            if vm.showTelemetry {
                Color.black.opacity(0.65)
                    .ignoresSafeArea()
                    .onTapGesture { vm.showTelemetry = false }

                TelemetryModalView()
                    .transition(.scale.combined(with: .opacity))
            }

            // Layer 3: Toast Notification Pill
            if !vm.toastMessage.isEmpty {
                VStack {
                    Spacer()
                    HStack(spacing: 8) {
                        Image(systemName: "info.circle.fill")
                            .font(.system(size: 13))
                            .foregroundStyle(theme.primary)
                        Text(vm.toastMessage)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(.primary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .liquidGlass(role: .floatingPillBar, cornerRadius: 20, tint: theme.primary)
                    .padding(.bottom, 95)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .frame(minWidth: 920, minHeight: 620)
        .tint(theme.primary)
        .onAppear {
            vm.loadInitialMusicDirectory()
        }
    }

    @ViewBuilder
    private func workspaceContent(theme: LiquidGlassTheme) -> some View {
        switch vm.activeTab {
        case .nowPlaying:
            NowPlayingSplitView(theme: theme)
        case .library:
            LibraryView(theme: theme)
        case .playlist:
            LibraryView(theme: theme, playlistsOnlyMode: true)
        case .settings:
            SettingsBentoView(theme: theme)
        }
    }
}

fileprivate extension Color {
    func toNSColor() -> NSColor {
        NSColor(self)
    }
}
