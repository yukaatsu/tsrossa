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
            // Layer 1: Deterministic App Shell (HStack + VStack)
            HStack(spacing: 0) {
                SidebarView(theme: theme)
                    .frame(width: 236)
                    .frame(maxHeight: .infinity)

                Rectangle()
                    .fill(Color.white.opacity(isEffectiveDark ? 0.08 : 0.14))
                    .frame(width: 1)
                    .frame(maxHeight: .infinity)

                VStack(spacing: 0) {
                    FloatingTopToolbarView(theme: theme)
                        .padding(.horizontal, 18)
                        .padding(.top, 12)
                        .padding(.bottom, 8)

                    ZStack {
                        workspaceContent(theme: theme)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()

                    LiquidTransportBarView(theme: theme)
                        .padding(.horizontal, 18)
                        .padding(.top, 6)
                        .padding(.bottom, 12)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            // Layer 1.5: Expandable Now Playing Stage Drawer
            if vm.isNowPlayingExpanded || vm.nowPlayingDragOffset < 0 {
                Color.black.opacity(0.55)
                    .ignoresSafeArea()
                    .onTapGesture {
                        withAnimation(.spring(response: 0.38, dampingFraction: 0.85)) {
                            vm.isNowPlayingExpanded = false
                        }
                    }

                NowPlayingSplitView(theme: theme)
                    .offset(y: max(0, vm.nowPlayingDragOffset))
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .zIndex(15)
            }

            // Layer 2: Modal Sheets & Overlays
            if vm.showNewPlaylistModal {
                Color.black.opacity(0.65)
                    .ignoresSafeArea()
                    .onTapGesture { vm.showNewPlaylistModal = false }

                CreatePlaylistModalSheet(theme: theme)
                    .transition(.scale(scale: 0.95).combined(with: .opacity))
            }

            if vm.showTrackDetailsModal {
                Color.black.opacity(0.65)
                    .ignoresSafeArea()
                    .onTapGesture { vm.showTrackDetailsModal = false }

                TrackDetailsModalSheet(theme: theme)
                    .transition(.scale(scale: 0.95).combined(with: .opacity))
            }

            if vm.showTelemetry {
                Color.black.opacity(0.65)
                    .ignoresSafeArea()
                    .onTapGesture { vm.showTelemetry = false }

                TelemetryModalView(theme: theme)
                    .transition(.scale(scale: 0.95).combined(with: .opacity))
            }

            if vm.showKeyboardShortcutsModal {
                Color.black.opacity(0.65)
                    .ignoresSafeArea()
                    .onTapGesture { vm.showKeyboardShortcutsModal = false }

                KeyboardShortcutsModalSheet(theme: theme)
                    .transition(.scale(scale: 0.95).combined(with: .opacity))
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
        .background {
            AmbientArtworkMeshBackground(
                artwork: engine.currentTrack?.artwork,
                accentColor: theme.primary,
                canvasMode: currentCanvasMode,
                isDark: isEffectiveDark
            )
        }
        .frame(minWidth: 920, minHeight: 620)
        .preferredColorScheme(isEffectiveDark ? .dark : .light)
        .opacity(vm.hasAppeared ? 1 : 0)
        .scaleEffect(vm.hasAppeared ? 1 : 0.985)
        .tint(theme.primary)
        .onAppear {
            vm.loadInitialMusicDirectory()
            withAnimation(.easeOut(duration: 0.35)) {
                vm.hasAppeared = true
            }
        }
    }

    @ViewBuilder
    private func workspaceContent(theme: LiquidGlassTheme) -> some View {
        ZStack {
            switch vm.activeTab {
            case .library:
                LibraryView(theme: theme)
                    .id(AppTab.library)
                    .transition(contextualTabTransition)
            case .playlist:
                LibraryView(theme: theme, playlistsOnlyMode: true)
                    .id(AppTab.playlist)
                    .transition(contextualTabTransition)
            case .settings:
                SettingsBentoView(theme: theme)
                    .id(AppTab.settings)
                    .transition(contextualTabTransition)
            }
        }
        .animation(.spring(response: 0.36, dampingFraction: 0.82), value: vm.activeTab)
    }

    private var contextualTabTransition: AnyTransition {
        let edge: Edge = vm.tabTransitionDirection >= 0 ? .bottom : .top
        return .asymmetric(
            insertion: .move(edge: edge).combined(with: .opacity),
            removal: .move(edge: edge == .bottom ? .top : .bottom).combined(with: .opacity)
        )
    }
}

fileprivate extension Color {
    func toNSColor() -> NSColor {
        NSColor(self)
    }
}
