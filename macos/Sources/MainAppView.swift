import SwiftUI

public struct MainAppView: View {
    @ObservedObject var vm = AppViewModel.shared
    @ObservedObject var engine = AudioEngine.shared
    @ObservedObject private var drawer = NowPlayingDrawerState.shared
    @ObservedObject private var zoomManager = WindowZoomManager.shared
    @ObservedObject private var canvasState = AmbientCanvasState.shared
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
        // Continuous interpolated artwork seed from AmbientCanvasState
        return LiquidGlassTheme(accentNSColor: canvasState.interpolatedAccentNSColor, isDark: isEffectiveDark, reduceTransparency: reduceTransparency)
    }

    public var body: some View {
        let theme = activeTheme

        GeometryReader { geo in
            let windowW = max(880.0, geo.size.width)
            let windowH = max(600.0, geo.size.height)
            let progress = min(1.0, max(0.0, drawer.revealProgress))
            let drawerY = windowH * (1.0 - progress)

            let sidebarW: CGFloat = 212.0
            let rightColumnW = max(400.0, windowW - sidebarW - 1.0)

            ZStack(alignment: .topLeading) {
                // Layer 1: Deterministic App Shell (HStack + VStack)
                HStack(spacing: 0) {
                    SidebarView(theme: theme)
                        .frame(width: sidebarW, height: windowH)

                    Rectangle()
                        .fill(Color.white.opacity(isEffectiveDark ? 0.08 : 0.14))
                        .frame(width: 1, height: windowH)

                    VStack(spacing: 0) {
                        FloatingTopToolbarView(theme: theme)
                            .padding(.horizontal, 14)
                            .padding(.top, 12)
                            .padding(.bottom, 8)

                        ZStack {
                            workspaceContent(theme: theme, rightColumnW: rightColumnW)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .clipped()

                        LiquidTransportBarView(theme: theme)
                            .padding(.horizontal, 14)
                            .padding(.top, 6)
                            .padding(.bottom, 12)
                            .opacity(1.0 - Double(progress))
                            .scaleEffect(1.0 - (progress * 0.03))
                            .offset(y: progress * 24)
                            .allowsHitTesting(progress < 0.5)
                    }
                    .frame(width: rightColumnW, height: windowH)
                    .clipped()
                }
                .frame(width: windowW, height: windowH, alignment: .topLeading)

                // Layer 1.5: Geometry-Driven Now Playing Stage Drawer (Unmounted when closed to accelerate resize)
                if progress > 0.001 {
                    Color.black
                        .opacity(0.60 * Double(progress))
                        .ignoresSafeArea()
                        .allowsHitTesting(progress > 0.05)
                        .onTapGesture {
                            vm.closeNowPlayingStage()
                        }

                    NowPlayingSplitView(theme: theme)
                        .frame(width: windowW, height: windowH)
                        .offset(y: drawerY)
                        .allowsHitTesting(progress > 0.5)
                        .zIndex(15)
                }

                // Layer 2: Modal Sheets & Overlays
                if vm.showNewPlaylistModal {
                    Color.black.opacity(0.65)
                        .ignoresSafeArea()
                        .onTapGesture {
                            withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) {
                                vm.showNewPlaylistModal = false
                            }
                        }

                    CreatePlaylistModalSheet(theme: theme)
                        .transition(.scale(scale: 0.95).combined(with: .opacity))
                }

                if vm.showTrackDetailsModal {
                    Color.black.opacity(0.65)
                        .ignoresSafeArea()
                        .onTapGesture {
                            withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) {
                                vm.showTrackDetailsModal = false
                            }
                        }

                    TrackDetailsModalSheet(theme: theme)
                        .transition(.scale(scale: 0.95).combined(with: .opacity))
                }

                if vm.showTelemetry {
                    Color.black.opacity(0.65)
                        .ignoresSafeArea()
                        .onTapGesture {
                            withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) {
                                vm.showTelemetry = false
                            }
                        }

                    TelemetryModalView(theme: theme)
                        .transition(.scale(scale: 0.95).combined(with: .opacity))
                }

                if vm.showKeyboardShortcutsModal {
                    Color.black.opacity(0.65)
                        .ignoresSafeArea()
                        .onTapGesture {
                            withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) {
                                vm.showKeyboardShortcutsModal = false
                            }
                        }

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
            .animation(.spring(response: 0.30, dampingFraction: 0.84), value: vm.showNewPlaylistModal)
            .animation(.spring(response: 0.30, dampingFraction: 0.84), value: vm.showTrackDetailsModal)
            .animation(.spring(response: 0.30, dampingFraction: 0.84), value: vm.showTelemetry)
            .animation(.spring(response: 0.30, dampingFraction: 0.84), value: vm.showKeyboardShortcutsModal)
            .animation(.spring(response: 0.34, dampingFraction: 0.82), value: vm.toastMessage.isEmpty)
            .coordinateSpace(name: "MainWindowCoordSpace")
            .frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading)
        }
        .background {
            AmbientArtworkMeshBackground(
                artwork: engine.currentTrack?.blurredArtwork ?? engine.currentTrack?.artwork,
                accentColor: theme.primary,
                secondaryColor: theme.secondary,
                surfaceColor: theme.surface,
                canvasMode: currentCanvasMode,
                isDark: isEffectiveDark
            )
        }
        .frame(minWidth: 880, minHeight: 600)
        .preferredColorScheme(isEffectiveDark ? .dark : .light)
        .transaction { transaction in
            if zoomManager.isZoomAnimating {
                transaction.disablesAnimations = true
            }
        }
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
    private func workspaceContent(theme: LiquidGlassTheme, rightColumnW: CGFloat) -> some View {
        ZStack {
            switch vm.activeTab {
            case .library:
                LibraryView(theme: theme, showAlbumColumn: rightColumnW > 600)
                    .id(AppTab.library)
                    .transition(contextualTabTransition)
            case .playlist:
                LibraryView(theme: theme, playlistsOnlyMode: true, showAlbumColumn: rightColumnW > 600)
                    .id(AppTab.playlist)
                    .transition(contextualTabTransition)
            case .settings:
                SettingsBentoView(theme: theme, isWide: rightColumnW >= 680)
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
