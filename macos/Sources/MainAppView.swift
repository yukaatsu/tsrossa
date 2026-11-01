import SwiftUI
import AppKit

public struct MainAppView: View {
    @ObservedObject private var engine = AudioEngine.shared
    @ObservedObject private var vm = AppViewModel.shared
    @ObservedObject private var updater = UpdateManager.shared
    
    private var filteredTracks: [TrackMetadata] {
        vm.filteredTracks
    }
    
    public init() {}
    
    public var body: some View {
        ZStack {
            // ==========================================================
            // 0. MATERIAL YOU (MONET) DYNAMIC DESKTOP AMBIENT BACKGROUND
            // ==========================================================
            backgroundLayer
            
            // ==========================================================
            // MAIN WIDESCREEN DESKTOP ARCHITECTURE / THEATER HUD
            // ==========================================================
            if vm.isTheaterMode {
                desktopTheaterView
                    .transition(.opacity)
            } else {
                VStack(spacing: 0) {
                    // UPPER WORKSPACE: LEFT SIDEBAR + MAIN WIDESCREEN CONTENT
                    HStack(spacing: 0) {
                        // ZONE 1: NATIVE MACOS SIDEBAR (236px fixed)
                        desktopSidebar
                            .frame(width: 236)
                        
                        // Vertical Divider
                        Rectangle()
                            .fill(vm.currentTheme.outline.opacity(0.18))
                            .frame(width: 1)
                        
                        // ZONE 2: MAIN WIDESCREEN CONTENT AREA
                        VStack(spacing: 0) {
                            desktopTopBar
                                .padding(.horizontal, 28)
                                .padding(.top, 18)
                                .padding(.bottom, 14)
                            
                            Divider()
                                .background(vm.currentTheme.outline.opacity(0.14))
                            
                            ZStack {
                                switch vm.activeTab {
                                case .library:
                                    desktopLibraryView
                                case .nowPlaying:
                                    desktopNowPlayingSplitView
                                case .playlist:
                                    desktopQueueView
                                case .settings:
                                    desktopSettingsBentoView
                                }
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    
                    // Top border of transport bar
                    Rectangle()
                        .fill(vm.currentTheme.outline.opacity(0.22))
                        .frame(height: 1)
                    
                    // ZONE 3: PERSISTENT BOTTOM TRANSPORT BAR (Full Width, 90px height)
                    desktopBottomTransportBar
                        .frame(height: 90)
                }
            }
            
            // ==========================================================
            // NEW PLAYLIST CREATION MODAL (Cmd + N)
            // ==========================================================
            if vm.showNewPlaylistModal {
                Color.black.opacity(0.72)
                    .ignoresSafeArea()
                    .onTapGesture { vm.showNewPlaylistModal = false }
                
                newPlaylistModalView
                    .transition(.scale.combined(with: .opacity))
            }
            
            // ==========================================================
            // SAVE CURRENT QUEUE AS PLAYLIST MODAL
            // ==========================================================
            if vm.showSaveQueueModal {
                Color.black.opacity(0.72)
                    .ignoresSafeArea()
                    .onTapGesture { vm.showSaveQueueModal = false }
                
                saveQueueModalView
                    .transition(.scale.combined(with: .opacity))
            }
            
            // ==========================================================
            // HARDWARE DIAGNOSTICS & TELEMETRY MODAL (Cmd + D)
            // ==========================================================
            if vm.showTelemetry {
                Color.black.opacity(0.72)
                    .ignoresSafeArea()
                    .onTapGesture { vm.showTelemetry = false }
                
                TelemetryModalView()
                    .transition(.scale.combined(with: .opacity))
            }
            
            // ==========================================================
            // TOAST NOTIFICATION OVERLAY (Queue & Play Next feedback)
            // ==========================================================
            if !vm.toastMessage.isEmpty {
                VStack {
                    HStack(spacing: 8) {
                        Image(systemName: "music.note")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(vm.currentTheme.primary)
                        Text(vm.toastMessage)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(vm.currentTheme.textPrimary)
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 10)
                    .background(vm.currentTheme.surfaceContainerHigh)
                    .cornerRadius(24)
                    .overlay(
                        RoundedRectangle(cornerRadius: 24)
                            .stroke(vm.currentTheme.primary.opacity(0.5), lineWidth: 1)
                    )
                    .shadow(color: Color.black.opacity(0.45), radius: 12, x: 0, y: 6)
                    .padding(.top, 24)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    
                    Spacer()
                }
                .allowsHitTesting(false)
            }
        }
        .frame(minWidth: 980, minHeight: 680)
        .onAppear {
            TsrossaBrandLogo.applyMacDockIcon()
            vm.loadInitialMusicDirectory()
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                updater.checkForUpdates(userInitiated: false)
            }
        }
    }
    
    // =========================================================================
    // AMBIENT BACKGROUND (Strictly Clipped so Layout Geometry Never Expands!)
    // =========================================================================
    @ViewBuilder
    private var backgroundLayer: some View {
        if vm.bgCanvasMode == "BLACK" {
            Color.black.ignoresSafeArea()
        } else if vm.bgCanvasMode == "AURA", let artwork = vm.currentArtwork {
            Color.clear
                .background(vm.currentTheme.surfaceContainerLowest)
                .overlay(
                    Image(nsImage: artwork)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .blur(radius: 85)
                        .opacity(0.32)
                        .saturation(1.35)
                )
                .clipped()
                .ignoresSafeArea()
                .animation(.easeInOut(duration: 0.5), value: vm.currentTheme)
        } else {
            ZStack {
                LinearGradient(
                    colors: [
                        vm.currentTheme.surfaceContainerLowest,
                        vm.currentTheme.surface
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
                
                RadialGradient(
                    colors: [vm.currentTheme.primary.opacity(0.16), Color.clear],
                    center: .topLeading,
                    startRadius: 40,
                    endRadius: 680
                )
                .ignoresSafeArea()
                
                RadialGradient(
                    colors: [vm.currentTheme.tertiary.opacity(0.10), Color.clear],
                    center: .bottomTrailing,
                    startRadius: 60,
                    endRadius: 600
                )
                .ignoresSafeArea()
            }
            .animation(.easeInOut(duration: 0.5), value: vm.currentTheme)
        }
    }
    
    // =========================================================================
    // ZONE 1: LEFT NAVIGATION SIDEBAR (Apple Music / Spotify / Roon Style)
    // =========================================================================
    private var desktopSidebar: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Clearance for macOS Red/Yellow/Green Traffic Light Window Controls
            Spacer()
                .frame(height: 46)
            
            // Brand Header with Authentic Android tsrossa Crimson Rose Logo
            HStack(spacing: 11) {
                TsrossaBrandIcon(size: 38, cornerRadius: 10)
                    .shadow(color: Color(red: 1.0, green: 0.18, blue: 0.33).opacity(0.35), radius: 8, x: 0, y: 3)
                
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text("tsrossa")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(vm.currentTheme.textPrimary)
                        
                        Circle()
                            .fill(TsrossaColors.bitPerfectGold)
                            .frame(width: 6, height: 6)
                    }
                    Text("BIT-PERFECT MAC DAC")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .foregroundColor(vm.currentTheme.primary)
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 18)
            
            // Navigation Section Label
            Text("WORKSPACE")
                .font(.system(size: 10, weight: .bold, design: .monospaced))
                .foregroundColor(vm.currentTheme.textMuted)
                .padding(.horizontal, 22)
                .padding(.bottom, 6)
            
            // Sidebar Navigation Items
            VStack(spacing: 3) {
                sidebarNavButton(
                    tab: .nowPlaying,
                    icon: "play.square.stack.fill",
                    title: "Now Playing",
                    shortcut: "⌘1"
                )
                sidebarNavButton(
                    tab: .library,
                    icon: "square.grid.2x2.fill",
                    title: "Music Library",
                    shortcut: "⌘2"
                )
                sidebarNavButton(
                    tab: .playlist,
                    icon: "music.note.list",
                    title: "Play Queue",
                    shortcut: "⌘3"
                )
                sidebarNavButton(
                    tab: .settings,
                    icon: "slider.horizontal.3",
                    title: "DAC & Settings",
                    shortcut: "⌘4"
                )
            }
            .padding(.horizontal, 12)
            .padding(.bottom, 14)
            
            // ==========================================================
            // PLAYLISTS SECTION (Custom & Smart Filter Playlists like Polyphia)
            // ==========================================================
            HStack {
                Text("PLAYLISTS")
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundColor(vm.currentTheme.textMuted)
                
                Spacer()
                
                Button(action: { vm.openCreatePlaylistModal() }) {
                    HStack(spacing: 3) {
                        Image(systemName: "plus")
                            .font(.system(size: 9, weight: .bold))
                        Text("New")
                            .font(.system(size: 10, weight: .bold))
                    }
                    .foregroundColor(vm.currentTheme.primary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(vm.currentTheme.primaryContainer.opacity(0.55))
                    .cornerRadius(8)
                    .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())
                .help("Create New Playlist / Smart Filter (⌘N)")
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 6)
            
            ScrollView {
                VStack(spacing: 3) {
                    // "All Tracks" reset item
                    sidebarPlaylistRow(
                        title: "All Master Tracks",
                        icon: "opticaldisc.fill",
                        count: vm.tracks.count,
                        isSelected: vm.activeTab == .library && vm.selectedPlaylistId == nil,
                        onSelect: {
                            vm.selectedPlaylistId = nil
                            vm.activeTab = .library
                        },
                        onDelete: nil
                    )
                    
                    // "Liked Songs" pinned item (YouTube Music style)
                    sidebarPlaylistRow(
                        title: "Liked Songs",
                        icon: "heart.fill",
                        count: vm.likedTracksCount,
                        isSelected: vm.activeTab == .library && vm.selectedPlaylistId == "LIKED_SONGS",
                        onSelect: {
                            vm.selectedPlaylistId = "LIKED_SONGS"
                            vm.activeTab = .library
                        },
                        onDelete: nil
                    )
                    
                    ForEach(vm.playlists) { pl in
                        let matchCount = vm.tracks.filter { pl.matches(track: $0) }.count
                        sidebarPlaylistRow(
                            title: pl.name,
                            icon: pl.filterKeyword.isEmpty ? "music.note.list" : "sparkles",
                            count: matchCount,
                            isSelected: vm.activeTab == .library && vm.selectedPlaylistId == pl.id,
                            onSelect: {
                                vm.selectedPlaylistId = pl.id
                                vm.activeTab = .library
                            },
                            onDelete: {
                                vm.deletePlaylist(id: pl.id)
                            }
                        )
                    }
                }
                .padding(.horizontal, 12)
            }
            .frame(maxHeight: 165)
            
            Spacer(minLength: 6)
            
            // Quick Palette Switcher in Sidebar
            VStack(alignment: .leading, spacing: 8) {
                Text("MATERIAL YOU PALETTE")
                    .font(.system(size: 9, weight: .bold, design: .monospaced))
                    .foregroundColor(vm.currentTheme.textMuted)
                
                HStack(spacing: 5) {
                    sidebarPaletteDot(mode: "DYNAMIC", title: "Auto", color: vm.currentTheme.primary)
                    sidebarPaletteDot(mode: "ROSE", title: "Rose", color: Color(red: 1.0, green: 0.18, blue: 0.33))
                    sidebarPaletteDot(mode: "MATCHA", title: "Matcha", color: Color(red: 0.62, green: 0.82, blue: 0.38))
                    sidebarPaletteDot(mode: "GOLD", title: "Gold", color: Color(red: 1.0, green: 0.84, blue: 0.0))
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 12)
            
            // Hardware Signal Path Mini-Card at Bottom of Sidebar (Roon / Audirvana Reference)
            Button(action: {
                engine.updateTelemetry()
                vm.showTelemetry = true
            }) {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Circle()
                            .fill(TsrossaColors.terminalGreen)
                            .frame(width: 7, height: 7)
                        Text(vm.isExclusiveHogMode ? "COREAUDIO HOG LOCK" : "COREAUDIO SHARED")
                            .font(.system(size: 9, weight: .bold, design: .monospaced))
                            .foregroundColor(TsrossaColors.terminalGreen)
                        Spacer()
                        Image(systemName: "waveform.path.ecg")
                            .font(.system(size: 11))
                            .foregroundColor(vm.currentTheme.primary)
                    }
                    
                    Text(activeDeviceName)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(vm.currentTheme.textPrimary)
                        .lineLimit(1)
                    
                    HStack(spacing: 6) {
                        Text("\(String(format: "%.1f kHz", Double(engine.currentTrack?.sampleRate ?? 44100) / 1000.0))")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(TsrossaColors.bitPerfectGold)
                        Text("•")
                            .foregroundColor(vm.currentTheme.textMuted)
                        Text("\(engine.currentTrack?.bitDepth ?? 16)-BIT")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(vm.currentTheme.primary)
                        Text("•")
                            .foregroundColor(vm.currentTheme.textMuted)
                        Text("mlock")
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundColor(vm.currentTheme.textSecondary)
                    }
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(vm.currentTheme.surfaceContainerHigh.opacity(0.75))
                .cornerRadius(14)
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(vm.currentTheme.outline.opacity(0.25), lineWidth: 1)
                )
                .contentShape(Rectangle())
            }
            .buttonStyle(TsrossaScaleButtonStyle())
            .padding(.horizontal, 14)
            .padding(.bottom, 16)
        }
        .background(vm.currentTheme.surfaceContainerLowest.opacity(0.65))
    }
    
    private func sidebarPlaylistRow(
        title: String,
        icon: String,
        count: Int,
        isSelected: Bool,
        onSelect: @escaping () -> Void,
        onDelete: (() -> Void)?
    ) -> some View {
        HStack(spacing: 8) {
            Button(action: onSelect) {
                HStack(spacing: 9) {
                    Image(systemName: icon)
                        .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                        .foregroundColor(isSelected ? vm.currentTheme.primary : vm.currentTheme.textSecondary)
                        .frame(width: 18)
                    
                    Text(title)
                        .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                        .foregroundColor(isSelected ? vm.currentTheme.textPrimary : vm.currentTheme.textSecondary)
                        .lineLimit(1)
                    
                    Spacer()
                    
                    Text("\(count)")
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundColor(isSelected ? vm.currentTheme.primary : vm.currentTheme.textMuted)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(PlainButtonStyle())
            
            if let onDelete = onDelete {
                Button(action: onDelete) {
                    Image(systemName: "trash")
                        .font(.system(size: 9))
                        .foregroundColor(vm.currentTheme.textMuted.opacity(0.65))
                        .frame(width: 16, height: 16)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())
                .help("Delete Playlist")
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 7)
        .background(isSelected ? vm.currentTheme.primaryContainer.opacity(0.75) : Color.clear)
        .cornerRadius(10)
    }
    
    private func sidebarNavButton(tab: AppTab, icon: String, title: String, shortcut: String) -> some View {
        let isSelected = vm.activeTab == tab
        let showUpdateBadge = (tab == .settings && updater.updateAvailable)
        return Button(action: { vm.activeTab = tab }) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 15, weight: isSelected ? .bold : .medium))
                    .foregroundColor(isSelected ? vm.currentTheme.onPrimaryContainer : vm.currentTheme.textSecondary)
                    .frame(width: 22)
                
                Text(title)
                    .font(.system(size: 13, weight: isSelected ? .bold : .medium))
                    .foregroundColor(isSelected ? vm.currentTheme.onPrimaryContainer : vm.currentTheme.textSecondary)
                
                if showUpdateBadge {
                    Circle()
                        .fill(TsrossaColors.bitPerfectGold)
                        .frame(width: 6, height: 6)
                }
                
                Spacer()
                
                Text(shortcut)
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundColor(isSelected ? vm.currentTheme.primary : vm.currentTheme.textMuted.opacity(0.7))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
            .frame(maxWidth: .infinity)
            .background(isSelected ? vm.currentTheme.primaryContainer.opacity(0.85) : Color.clear)
            .cornerRadius(12)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private func sidebarPaletteDot(mode: String, title: String, color: Color) -> some View {
        let isSelected = vm.accentColorMode == mode
        return Button(action: { vm.setAccentColorMode(mode) }) {
            HStack(spacing: 4) {
                Circle()
                    .fill(color)
                    .frame(width: 7, height: 7)
                Text(title)
                    .font(.system(size: 9, weight: isSelected ? .bold : .medium))
                    .foregroundColor(isSelected ? vm.currentTheme.textPrimary : vm.currentTheme.textSecondary)
            }
            .padding(.horizontal, 7)
            .padding(.vertical, 5)
            .background(isSelected ? vm.currentTheme.primaryContainer.opacity(0.75) : Color.white.opacity(0.05))
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isSelected ? vm.currentTheme.primary.opacity(0.5) : Color.clear, lineWidth: 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    // =========================================================================
    // TOP UTILITY BAR (Typeahead Search with Enter-to-Play, New Playlist, Status)
    // =========================================================================
    private var desktopTopBar: some View {
        HStack(spacing: 12) {
            // Search Box (Spotify Typeahead + Enter to Play Top Hit)
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 13))
                    .foregroundColor(vm.currentTheme.textSecondary)
                
                TextField("Search tracks, artists... (Press ⏎ to play)", text: $vm.searchText)
                    .textFieldStyle(PlainTextFieldStyle())
                    .font(.system(size: 13))
                    .foregroundColor(vm.currentTheme.textPrimary)
                    .onSubmit {
                        vm.playSelectedSearchResult()
                    }
                    .onChange(of: vm.searchText) { newValue in
                        if !newValue.isEmpty && vm.activeTab != .library {
                            vm.activeTab = .library
                        }
                    }
                
                if !vm.searchText.isEmpty {
                    Button(action: { vm.playSelectedSearchResult() }) {
                        Text("⏎ Play")
                            .font(.system(size: 9, weight: .bold, design: .monospaced))
                            .foregroundColor(vm.currentTheme.onPrimary)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 3)
                            .background(vm.currentTheme.primary)
                            .cornerRadius(6)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(PlainButtonStyle())
                    .help("Press Return/Enter to immediately play the highlighted result")
                    
                    Button(action: { vm.searchText = "" }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 12))
                            .foregroundColor(vm.currentTheme.textSecondary)
                            .contentShape(Circle())
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .frame(width: 330)
            .background(vm.currentTheme.surfaceContainerHigh.opacity(0.85))
            .cornerRadius(20)
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(!vm.searchText.isEmpty ? vm.currentTheme.primary.opacity(0.6) : vm.currentTheme.outline.opacity(0.22), lineWidth: 1)
            )
            
            // Sort Button
            Button(action: { vm.toggleSort() }) {
                HStack(spacing: 5) {
                    Image(systemName: "arrow.up.arrow.down")
                        .font(.system(size: 11))
                    Text(vm.sortMode)
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                }
                .foregroundColor(vm.currentTheme.textPrimary)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(vm.currentTheme.surfaceContainerHigh.opacity(0.65))
                .cornerRadius(16)
                .contentShape(Rectangle())
            }
            .buttonStyle(TsrossaScaleButtonStyle())
            
            // + New Playlist Quick Button
            Button(action: { vm.openCreatePlaylistModal() }) {
                HStack(spacing: 5) {
                    Image(systemName: "plus.rectangle.on.folder.fill")
                        .font(.system(size: 11))
                    Text("New Playlist")
                        .font(.system(size: 11, weight: .bold))
                }
                .foregroundColor(vm.currentTheme.onPrimaryContainer)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(vm.currentTheme.primaryContainer.opacity(0.65))
                .cornerRadius(16)
                .contentShape(Rectangle())
            }
            .buttonStyle(TsrossaScaleButtonStyle())
            
            Spacer()
            
            // Hardware Status Pill
            HStack(spacing: 8) {
                Image(systemName: "cpu")
                    .font(.system(size: 12))
                    .foregroundColor(vm.currentTheme.primary)
                Text(formatBadgeText)
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundColor(vm.currentTheme.textPrimary)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(vm.currentTheme.primaryContainer.opacity(0.45))
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(vm.currentTheme.primary.opacity(0.30), lineWidth: 1)
            )
            
            // Quick Action Circle Buttons
            HStack(spacing: 8) {
                topActionCircleButton(icon: "folder.badge.plus", helpText: "Choose Music Folder") {
                    vm.openFolderPicker()
                }
                topActionCircleButton(icon: "arrow.clockwise", helpText: "Rescan Music Library (⌘R)") {
                    vm.rescanCurrentFolder()
                }
                topActionCircleButton(icon: "waveform.path.ecg", helpText: "Signal Path & Hardware Diagnostics (⌘D)") {
                    engine.updateTelemetry()
                    vm.showTelemetry = true
                }
            }
        }
    }
    
    private func topActionCircleButton(icon: String, helpText: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .fill(vm.currentTheme.surfaceContainerHigh)
                    .frame(width: 34, height: 34)
                Image(systemName: icon)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(vm.currentTheme.textPrimary)
            }
            .frame(width: 34, height: 34)
            .contentShape(Circle())
        }
        .buttonStyle(TsrossaScaleButtonStyle())
        .help(helpText)
    }
    
    // =========================================================================
    // VIEW 1: WIDESCREEN NOW PLAYING SPLIT VIEW (Apple Music / Tidal / Roon)
    // =========================================================================
    private var desktopNowPlayingSplitView: some View {
        HStack(spacing: 28) {
            // LEFT COLUMN: Large Album Art Showcase & Track Identity
            VStack(spacing: 20) {
                Spacer(minLength: 8)
                
                ZStack {
                    if let artwork = vm.currentArtwork {
                        Image(nsImage: artwork)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 310, height: 310)
                            .clipShape(RoundedRectangle(cornerRadius: 28))
                            .overlay(
                                RoundedRectangle(cornerRadius: 28)
                                    .stroke(Color.white.opacity(0.18), lineWidth: 1)
                            )
                            .shadow(color: vm.currentTheme.primary.opacity(0.38), radius: 32, x: 0, y: 16)
                    } else {
                        TsrossaBrandIcon(size: 310, cornerRadius: 28)
                            .shadow(color: vm.currentTheme.primary.opacity(0.32), radius: 28, x: 0, y: 14)
                    }
                }
                
                VStack(spacing: 6) {
                    Text(engine.currentTrack?.title ?? "No Track Selected")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(vm.currentTheme.textPrimary)
                        .lineLimit(1)
                    
                    Text(subtitleText)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(vm.currentTheme.textSecondary)
                        .lineLimit(1)
                }
                
                // Audiophile Master Quality Badge
                HStack(spacing: 8) {
                    Circle()
                        .fill(TsrossaColors.bitPerfectGold)
                        .frame(width: 7, height: 7)
                    Text(formatBadgeText)
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(vm.currentTheme.onPrimaryContainer)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(vm.currentTheme.primaryContainer.opacity(0.65))
                .cornerRadius(20)
                
                Spacer(minLength: 8)
            }
            .frame(maxWidth: .infinity)
            
            // RIGHT COLUMN: Live Signal Path Inspector + Up Next Queue (Roon / Apple Music Desktop)
            VStack(spacing: 16) {
                // 1. LIVE SIGNAL PATH CARD
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Image(systemName: "point.3.connected.trianglepath.dotted")
                            .foregroundColor(vm.currentTheme.primary)
                        Text("LOSSLESS SIGNAL PATH")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundColor(vm.currentTheme.primary)
                        Spacer()
                        Text("BIT-PERFECT")
                            .font(.system(size: 9, weight: .bold, design: .monospaced))
                            .foregroundColor(TsrossaColors.terminalGreen)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(TsrossaColors.terminalGreen.opacity(0.14))
                            .cornerRadius(6)
                    }
                    
                    signalPathStep(
                        icon: "doc.badge.gearshape",
                        title: "Source Decoder (\(engine.currentTrack?.path.hasSuffix(".flac") == true ? "dr_flac" : "dr_wav"))",
                        detail: "\(engine.currentTrack?.sampleRate ?? 44100) Hz / \(engine.currentTrack?.bitDepth ?? 16)-bit Float64 Zero-Copy",
                        badgeColor: vm.currentTheme.primary
                    )
                    
                    signalPathStep(
                        icon: "memorychip",
                        title: "Memory Pinning (POSIX mlock)",
                        detail: vm.isRamPlayback ? "Locked in physical RAM • 0 SSD page faults" : "Standard buffered streaming",
                        badgeColor: vm.isRamPlayback ? TsrossaColors.audiophileCyan : vm.currentTheme.textSecondary
                    )
                    
                    signalPathStep(
                        icon: "lock.shield",
                        title: "CoreAudio HAL Output (\(activeDeviceName))",
                        detail: vm.isExclusiveHogMode ? "Exclusive Hog Mode • System Mixer Bypassed" : "Shared CoreAudio Output",
                        badgeColor: TsrossaColors.bitPerfectGold
                    )
                }
                .padding(16)
                .background(vm.currentTheme.surfaceContainer.opacity(0.88))
                .cornerRadius(18)
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .stroke(vm.currentTheme.outline.opacity(0.22), lineWidth: 1)
                )
                
                // 2. UP NEXT IN QUEUE CARD
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Image(systemName: "music.note.list")
                            .foregroundColor(vm.currentTheme.primary)
                        Text(vm.upNextQueue.isEmpty ? "UP NEXT IN LIBRARY (\(vm.tracks.count))" : "UP NEXT IN QUEUE (\(vm.upNextQueue.count))")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundColor(vm.currentTheme.primary)
                        Spacer()
                        if !vm.upNextQueue.isEmpty {
                            Button(action: { vm.clearQueue() }) {
                                Text("Clear")
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundColor(vm.currentTheme.error)
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                        Button(action: { vm.activeTab = .playlist }) {
                            Text("Manage Queue →")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(vm.currentTheme.textSecondary)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                    
                    ScrollView {
                        LazyVStack(spacing: 6) {
                            if !vm.upNextQueue.isEmpty {
                                ForEach(Array(vm.upNextQueue.enumerated()), id: \.element.id) { idx, track in
                                    queueRowWithRemove(index: idx + 1, track: track)
                                }
                                
                                Divider()
                                    .background(vm.currentTheme.outline.opacity(0.15))
                                    .padding(.vertical, 4)
                                
                                HStack {
                                    Text("UPCOMING CONTINUATION")
                                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                                        .foregroundColor(vm.currentTheme.textMuted)
                                    Spacer()
                                }
                                .padding(.horizontal, 4)
                            }
                            
                            ForEach(Array(vm.tracks.prefix(35).enumerated()), id: \.offset) { idx, track in
                                compactQueueRow(index: idx + 1, track: track)
                            }
                        }
                    }
                }
                .padding(16)
                .frame(maxHeight: .infinity)
                .background(vm.currentTheme.surfaceContainer.opacity(0.88))
                .cornerRadius(18)
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .stroke(vm.currentTheme.outline.opacity(0.22), lineWidth: 1)
                )
            }
            .frame(width: 400)
        }
        .padding(.horizontal, 28)
        .padding(.vertical, 20)
    }
    
    private func signalPathStep(icon: String, title: String, detail: String, badgeColor: Color) -> some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(badgeColor.opacity(0.15))
                    .frame(width: 30, height: 30)
                Image(systemName: icon)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(badgeColor)
            }
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(vm.currentTheme.textPrimary)
                    .lineLimit(1)
                Text(detail)
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundColor(vm.currentTheme.textSecondary)
                    .lineLimit(1)
            }
            Spacer()
        }
    }
    
    private func queueRowWithRemove(index: Int, track: TrackMetadata) -> some View {
        Button(action: {
            if let idx = vm.upNextQueue.firstIndex(where: { $0.path == track.path }) {
                vm.removeFromQueue(at: idx)
            }
            engine.play(track: track)
        }) {
            HStack(spacing: 8) {
                Text("NEXT #\(index)")
                    .font(.system(size: 8, weight: .bold, design: .monospaced))
                    .foregroundColor(vm.currentTheme.onPrimary)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(vm.currentTheme.primary)
                    .cornerRadius(4)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(track.title)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(vm.currentTheme.primary)
                        .lineLimit(1)
                    Text(track.artist)
                        .font(.system(size: 10))
                        .foregroundColor(vm.currentTheme.textSecondary)
                        .lineLimit(1)
                }
                
                Spacer()
                
                Button(action: {
                    if let idx = vm.upNextQueue.firstIndex(where: { $0.path == track.path }) {
                        vm.removeFromQueue(at: idx)
                    }
                }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(vm.currentTheme.textMuted)
                        .padding(4)
                }
                .buttonStyle(PlainButtonStyle())
                .help("Remove from Queue")
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(vm.currentTheme.primaryContainer.opacity(0.32))
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(vm.currentTheme.primary.opacity(0.35), lineWidth: 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private func compactQueueRow(index: Int, track: TrackMetadata) -> some View {
        let isCurrent = engine.currentTrack?.path == track.path
        return Button(action: { engine.play(track: track) }) {
            HStack(spacing: 10) {
                if isCurrent && engine.isPlaying {
                    Image(systemName: "waveform")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(vm.currentTheme.primary)
                        .frame(width: 22)
                } else {
                    Text("\(index)")
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundColor(isCurrent ? vm.currentTheme.primary : vm.currentTheme.textMuted)
                        .frame(width: 22)
                }
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(track.title)
                        .font(.system(size: 12, weight: isCurrent ? .bold : .medium))
                        .foregroundColor(isCurrent ? vm.currentTheme.primary : vm.currentTheme.textPrimary)
                        .lineLimit(1)
                    Text(track.artist)
                        .font(.system(size: 10))
                        .foregroundColor(vm.currentTheme.textSecondary)
                        .lineLimit(1)
                }
                
                Spacer()
                
                Text(vm.formatTime(track.durationSeconds))
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundColor(vm.currentTheme.textMuted)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(isCurrent ? vm.currentTheme.primaryContainer.opacity(0.45) : Color.white.opacity(0.03))
            .cornerRadius(8)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    // =========================================================================
    // VIEW 2: WIDESCREEN MUSIC LIBRARY (Spotify / Apple Music Table + Hero + Filters)
    // =========================================================================
    private var desktopLibraryView: some View {
        VStack(spacing: 0) {
            // Featured Hero Header Banner (Adapts to Selected Playlist like "Polyphia")
            HStack(spacing: 20) {
                ZStack {
                    if let art = vm.currentArtwork {
                        Image(nsImage: art)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 84, height: 84)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                            .shadow(color: vm.currentTheme.primary.opacity(0.35), radius: 12, x: 0, y: 6)
                    } else {
                        TsrossaBrandIcon(size: 84, cornerRadius: 16)
                            .shadow(color: vm.currentTheme.primary.opacity(0.28), radius: 12, x: 0, y: 6)
                    }
                }
                
                VStack(alignment: .leading, spacing: 5) {
                    HStack(spacing: 8) {
                        Text(vm.activePlaylist != nil ? "SMART PLAYLIST / COLLECTION" : "LOSSLESS MASTER COLLECTION")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(vm.currentTheme.primary)
                        
                        if let pl = vm.activePlaylist, !pl.filterKeyword.isEmpty {
                            Text("FILTER: \(pl.filterKeyword.uppercased())")
                                .font(.system(size: 9, weight: .bold, design: .monospaced))
                                .foregroundColor(vm.currentTheme.onPrimaryContainer)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 2)
                                .background(vm.currentTheme.primaryContainer)
                                .cornerRadius(6)
                        }
                    }
                    
                    Text(vm.activePlaylist?.name ?? "Local Audiophile Library")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(vm.currentTheme.textPrimary)
                    
                    Text("\(filteredTracks.count) of \(vm.tracks.count) tracks  •  \(vm.currentFolder)  •  Zero-Copy Direct Memory Engine")
                        .font(.system(size: 11))
                        .foregroundColor(vm.currentTheme.textSecondary)
                        .lineLimit(1)
                }
                
                Spacer()
                
                // Hero Play, Shuffle & New Playlist Actions
                HStack(spacing: 10) {
                    Button(action: {
                        if let first = filteredTracks.first {
                            engine.play(track: first)
                        }
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "play.fill")
                            Text(vm.activePlaylist != nil ? "Play \(vm.activePlaylist?.name ?? "Playlist")" : "Play Filtered")
                        }
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(vm.currentTheme.onPrimary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 9)
                        .background(vm.currentTheme.primary)
                        .cornerRadius(20)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())
                    
                    Button(action: {
                        vm.isShuffle = true
                        vm.playNextTrack()
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "shuffle")
                            Text("Shuffle")
                        }
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(vm.currentTheme.textPrimary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .background(vm.currentTheme.surfaceContainerHigh)
                        .cornerRadius(20)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())
                }
            }
            .padding(.horizontal, 28)
            .padding(.vertical, 14)
            .background(vm.currentTheme.surfaceContainerLow.opacity(0.55))
            
            // ==========================================================
            // AUDIOPHILE HARDWARE & PLAYLIST FILTER BAR
            // (Playlists + Format [FLAC/WAV] + Bit Depth [24/16/8-bit] + Sample Rate [kHz])
            // ==========================================================
            desktopAudiophileFilterBar
            
            // Widescreen Multi-Column Table Header
            HStack(spacing: 14) {
                Text("#")
                    .frame(width: 34, alignment: .center)
                Text("TITLE")
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text("ARTIST")
                    .frame(width: 165, alignment: .leading)
                Text("ALBUM")
                    .frame(width: 150, alignment: .leading)
                Text("ENCODING")
                    .frame(width: 110, alignment: .center)
                Image(systemName: "clock")
                    .frame(width: 54, alignment: .trailing)
                Text("LIST")
                    .frame(width: 34, alignment: .center)
            }
            .font(.system(size: 10, weight: .bold, design: .monospaced))
            .foregroundColor(vm.currentTheme.textMuted)
            .padding(.horizontal, 28)
            .padding(.vertical, 9)
            .background(vm.currentTheme.surfaceContainerLowest.opacity(0.5))
            
            Divider()
                .background(vm.currentTheme.outline.opacity(0.15))
            
            // Multi-Column Track Rows (or Empty Filter State)
            if filteredTracks.isEmpty {
                VStack(spacing: 12) {
                    Spacer()
                    Image(systemName: "waveform.badge.magnifyingglass")
                        .font(.system(size: 36))
                        .foregroundColor(vm.currentTheme.textMuted)
                    Text("No tracks match the current filter")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(vm.currentTheme.textPrimary)
                    Text("Try clearing your format, bit-depth, kHz, or playlist filters.")
                        .font(.system(size: 12))
                        .foregroundColor(vm.currentTheme.textSecondary)
                    Button(action: { vm.resetAllFilters() }) {
                        Text("Reset All Filters")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(vm.currentTheme.onPrimary)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(vm.currentTheme.primary)
                            .cornerRadius(16)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(PlainButtonStyle())
                    Spacer()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 4) {
                        ForEach(Array(filteredTracks.enumerated()), id: \.element.id) { idx, track in
                            desktopTrackTableRow(index: idx + 1, track: track)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                }
            }
        }
    }
    
    // =========================================================================
    // AUDIOPHILE FILTER STRIP (Playlists, Format, Bit-Depth, Sample Rate kHz)
    // =========================================================================
    private var desktopAudiophileFilterBar: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Row 1: Playlist & Artist Filter Pills + "+ New Playlist"
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 7) {
                    Text("PLAYLIST:")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .foregroundColor(vm.currentTheme.textMuted)
                    
                    filterChipButton(
                        title: "All (\(vm.tracks.count))",
                        isSelected: vm.selectedPlaylistId == nil
                    ) {
                        vm.selectedPlaylistId = nil
                    }
                    
                    ForEach(vm.playlists) { pl in
                        let count = vm.tracks.filter { pl.matches(track: $0) }.count
                        filterChipButton(
                            title: "\(pl.name) (\(count))",
                            icon: pl.filterKeyword.isEmpty ? "music.note.list" : "sparkles",
                            isSelected: vm.selectedPlaylistId == pl.id
                        ) {
                            vm.selectedPlaylistId = (vm.selectedPlaylistId == pl.id) ? nil : pl.id
                        }
                    }
                    
                    Button(action: { vm.openCreatePlaylistModal() }) {
                        HStack(spacing: 4) {
                            Image(systemName: "plus.circle.fill")
                                .font(.system(size: 10))
                            Text("New Playlist")
                                .font(.system(size: 10, weight: .bold))
                        }
                        .foregroundColor(vm.currentTheme.primary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(vm.currentTheme.primaryContainer.opacity(0.45))
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(vm.currentTheme.primary.opacity(0.4), lineWidth: 1)
                        )
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PlainButtonStyle())
                }
                .padding(.horizontal, 28)
            }
            
            // Row 2: Format (FLAC / WAV) + Bit Depth (24-bit / 16-bit / 8-bit) + Sample Rate (kHz)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    // FORMAT GROUP
                    Text("FORMAT:")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .foregroundColor(vm.currentTheme.textMuted)
                    
                    ForEach(["ALL", "FLAC", "WAV"], id: \.self) { fmt in
                        filterChipButton(
                            title: fmt,
                            isSelected: vm.selectedFormatFilter == fmt
                        ) {
                            vm.selectedFormatFilter = (vm.selectedFormatFilter == fmt && fmt != "ALL") ? "ALL" : fmt
                        }
                    }
                    
                    Divider()
                        .frame(height: 14)
                        .padding(.horizontal, 4)
                    
                    // BIT DEPTH GROUP (24-bit, 16-bit, 8-bit, 32-bit)
                    Text("BIT DEPTH:")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .foregroundColor(vm.currentTheme.textMuted)
                    
                    ForEach([UInt32(24), UInt32(16), UInt32(8), UInt32(32)], id: \.self) { bits in
                        filterChipButton(
                            title: "\(bits)-BIT",
                            isSelected: vm.selectedBitDepthFilter == bits
                        ) {
                            vm.selectedBitDepthFilter = (vm.selectedBitDepthFilter == bits) ? 0 : bits
                        }
                    }
                    
                    Divider()
                        .frame(height: 14)
                        .padding(.horizontal, 4)
                    
                    // SAMPLE RATE (kHz) GROUP
                    Text("CLOCK:")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .foregroundColor(vm.currentTheme.textMuted)
                    
                    ForEach(
                        [
                            (UInt32(44100), "44.1 kHz"),
                            (UInt32(48000), "48 kHz"),
                            (UInt32(88200), "88.2 kHz"),
                            (UInt32(96000), "96 kHz"),
                            (UInt32(192000), "192 kHz")
                        ],
                        id: \.0
                    ) { rate, label in
                        filterChipButton(
                            title: label,
                            isSelected: vm.selectedSampleRateFilter == rate
                        ) {
                            vm.selectedSampleRateFilter = (vm.selectedSampleRateFilter == rate) ? 0 : rate
                        }
                    }
                    
                    if vm.hasActiveFilters {
                        Button(action: { vm.resetAllFilters() }) {
                            HStack(spacing: 4) {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.system(size: 10))
                                Text("Reset")
                                    .font(.system(size: 10, weight: .bold))
                            }
                            .foregroundColor(Color(red: 1.0, green: 0.42, blue: 0.42))
                            .padding(.horizontal, 9)
                            .padding(.vertical, 5)
                            .background(Color(red: 1.0, green: 0.25, blue: 0.25).opacity(0.16))
                            .cornerRadius(12)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.horizontal, 28)
            }
        }
        .padding(.vertical, 10)
        .background(vm.currentTheme.surfaceContainer.opacity(0.55))
    }
    
    private func filterChipButton(
        title: String,
        icon: String? = nil,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if let icon = icon {
                    Image(systemName: icon)
                        .font(.system(size: 9, weight: .bold))
                }
                Text(title)
                    .font(.system(size: 10, weight: isSelected ? .bold : .semibold, design: .monospaced))
            }
            .foregroundColor(isSelected ? vm.currentTheme.onPrimary : vm.currentTheme.textSecondary)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(isSelected ? vm.currentTheme.primary : Color.white.opacity(0.06))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? vm.currentTheme.primary : vm.currentTheme.outline.opacity(0.18), lineWidth: 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private func desktopTrackTableRow(index: Int, track: TrackMetadata) -> some View {
        let isCurrent = engine.currentTrack?.path == track.path
        let isSearchTarget = !vm.searchText.isEmpty && (index - 1) == vm.searchSelectedIndex
        let isFlac = track.path.lowercased().hasSuffix(".flac")
        
        return HStack(spacing: 14) {
            Button(action: { engine.play(track: track) }) {
                HStack(spacing: 14) {
                    // Column 1: # or Playing Indicator
                    ZStack {
                        if isCurrent {
                            Image(systemName: engine.isPlaying && !engine.isPaused ? "waveform" : "speaker.wave.2.fill")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(vm.currentTheme.primary)
                        } else if isSearchTarget {
                            Image(systemName: "return")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(vm.currentTheme.primary)
                        } else {
                            Text("\(index)")
                                .font(.system(size: 12, design: .monospaced))
                                .foregroundColor(vm.currentTheme.textMuted)
                        }
                    }
                    .frame(width: 34, alignment: .center)
                    
                    // Column 2: Mini Thumbnail + Title + Spotify-style Top Hit Badge
                    HStack(spacing: 12) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 8)
                                .fill(isCurrent ? vm.currentTheme.primaryContainer : Color.white.opacity(0.07))
                                .frame(width: 38, height: 38)
                            
                            if isCurrent, let art = vm.currentArtwork {
                                Image(nsImage: art)
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .frame(width: 38, height: 38)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            } else {
                                Image(systemName: "music.note")
                                    .font(.system(size: 14))
                                    .foregroundColor(isCurrent ? vm.currentTheme.primary : vm.currentTheme.textSecondary)
                            }
                        }
                        
                        Text(track.title)
                            .font(.system(size: 13, weight: (isCurrent || isSearchTarget) ? .bold : .semibold))
                            .foregroundColor(isCurrent ? vm.currentTheme.primary : vm.currentTheme.textPrimary)
                            .lineLimit(1)
                        
                        if vm.isTrackLiked(track.path) {
                            Image(systemName: "heart.fill")
                                .font(.system(size: 10))
                                .foregroundColor(vm.currentTheme.primary)
                        }
                        
                        if isSearchTarget {
                            Text("⏎ ENTER TO PLAY")
                                .font(.system(size: 9, weight: .bold, design: .monospaced))
                                .foregroundColor(vm.currentTheme.onPrimary)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 2)
                                .background(vm.currentTheme.primary)
                                .cornerRadius(5)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    
                    // Column 3: Artist
                    Text(track.artist)
                        .font(.system(size: 12))
                        .foregroundColor(vm.currentTheme.textSecondary)
                        .lineLimit(1)
                        .frame(width: 165, alignment: .leading)
                    
                    // Column 4: Album
                    Text(track.album)
                        .font(.system(size: 12))
                        .foregroundColor(vm.currentTheme.textMuted)
                        .lineLimit(1)
                        .frame(width: 150, alignment: .leading)
                    
                    // Column 5: Format Badge
                    Text("\(isFlac ? "FLAC" : "WAV") \(track.bitDepth)/\(String(format: "%.1fk", Double(track.sampleRate)/1000.0))")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundColor(isCurrent ? vm.currentTheme.onPrimaryContainer : vm.currentTheme.primary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(isCurrent ? vm.currentTheme.primaryContainer : vm.currentTheme.primaryContainer.opacity(0.35))
                        .cornerRadius(6)
                        .frame(width: 110, alignment: .center)
                    
                    // Column 6: Duration
                    Text(vm.formatTime(track.durationSeconds))
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundColor(vm.currentTheme.textSecondary)
                        .frame(width: 54, alignment: .trailing)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(PlainButtonStyle())
            .contextMenu {
                Button(action: {
                    engine.play(track: track)
                }) {
                    Label("Play Now", systemImage: "play.fill")
                }
                
                Button(action: {
                    vm.addToPlayNext(track: track)
                }) {
                    Label("Play Next (Putar Berikutnya)", systemImage: "text.line.first.and.arrowtriangle.forward")
                }
                
                Button(action: {
                    vm.addToQueueEnd(track: track)
                }) {
                    Label("Add to Queue (Tambahkan ke Antrean)", systemImage: "text.badge.plus")
                }
                
                Divider()
                
                let isLiked = vm.isTrackLiked(track.path)
                Button(action: {
                    vm.toggleLike(track: track)
                }) {
                    Label(isLiked ? "Unlike Song" : "Like Song", systemImage: isLiked ? "heart.slash" : "heart.fill")
                }
                
                Divider()
                
                Menu("Add / Remove from Playlist") {
                    ForEach(vm.playlists) { pl in
                        let inPlaylist = pl.matches(track: track)
                        Button(action: {
                            vm.toggleTrackInPlaylist(trackPath: track.path, playlistId: pl.id)
                        }) {
                            Text("\(inPlaylist ? "✓ " : "   ")\(pl.name)")
                        }
                    }
                }
                
                Divider()
                
                Button(action: {
                    NSWorkspace.shared.activateFileViewerSelecting([URL(fileURLWithPath: track.path)])
                }) {
                    Label("Show in Finder", systemImage: "folder")
                }
            }
            
            // Column 7: Add to Queue & Playlist Menu Button
            Menu {
                Button(action: {
                    vm.addToPlayNext(track: track)
                }) {
                    Label("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward")
                }
                
                Button(action: {
                    vm.addToQueueEnd(track: track)
                }) {
                    Label("Add to Queue", systemImage: "text.badge.plus")
                }
                
                Divider()
                
                let isRowLiked = vm.isTrackLiked(track.path)
                Button(action: {
                    vm.toggleLike(track: track)
                }) {
                    Label(isRowLiked ? "Unlike Song" : "Like Song", systemImage: isRowLiked ? "heart.slash" : "heart.fill")
                }
                
                Divider()
                
                Text("Add / Remove from Playlist")
                ForEach(vm.playlists) { pl in
                    let inPlaylist = pl.matches(track: track)
                    Button(action: {
                        vm.toggleTrackInPlaylist(trackPath: track.path, playlistId: pl.id)
                    }) {
                        Text("\(inPlaylist ? "✓ " : "   ")\(pl.name)")
                    }
                }
                Divider()
                Button(action: {
                    vm.openCreatePlaylistModal()
                    vm.newPlaylistSelectedPaths.insert(track.path)
                    if vm.newPlaylistName.isEmpty && track.artist != "Unknown Artist" {
                        vm.newPlaylistName = track.artist
                    }
                }) {
                    Text("+ New Playlist with '\(track.title)'...")
                }
            } label: {
                Image(systemName: "ellipsis.circle")
                    .font(.system(size: 13))
                    .foregroundColor(vm.currentTheme.textSecondary)
                    .frame(width: 28, height: 28)
                    .contentShape(Rectangle())
            }
            .menuStyle(BorderlessButtonMenuStyle())
            .frame(width: 34, alignment: .center)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(
            isCurrent
                ? vm.currentTheme.primaryContainer.opacity(0.35)
                : (isSearchTarget ? vm.currentTheme.primaryContainer.opacity(0.22) : Color.white.opacity(0.02))
        )
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(isSearchTarget ? vm.currentTheme.primary.opacity(0.65) : Color.clear, lineWidth: 1)
        )
    }
    
    // =========================================================================
    // NEW PLAYLIST / SMART FILTER CREATION MODAL (Cmd + N)
    // =========================================================================
    private var newPlaylistModalView: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("CREATE PLAYLIST OR SMART FILTER")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundColor(vm.currentTheme.primary)
                    Text("New Playlist Module")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(vm.currentTheme.textPrimary)
                }
                Spacer()
                Button(action: { vm.showNewPlaylistModal = false }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 18))
                        .foregroundColor(vm.currentTheme.textSecondary)
                }
                .buttonStyle(PlainButtonStyle())
            }
            
            // 1. Playlist Name Input
            VStack(alignment: .leading, spacing: 6) {
                Text("PLAYLIST NAME")
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundColor(vm.currentTheme.textSecondary)
                
                TextField("e.g., Polyphia, Late Night 24-Bit, Favorites...", text: $vm.newPlaylistName)
                    .textFieldStyle(PlainTextFieldStyle())
                    .font(.system(size: 13))
                    .foregroundColor(vm.currentTheme.textPrimary)
                    .padding(10)
                    .background(Color.white.opacity(0.07))
                    .cornerRadius(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(vm.currentTheme.outline.opacity(0.25), lineWidth: 1)
                    )
            }
            
            // 2. Smart Auto-Filter Keyword (like "Polyphia")
            VStack(alignment: .leading, spacing: 6) {
                Text("SMART AUTO-FILTER KEYWORD / ARTIST (OPTIONAL)")
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundColor(vm.currentTheme.textSecondary)
                
                TextField("e.g., Polyphia (auto-includes all matching tracks)", text: $vm.newPlaylistKeyword)
                    .textFieldStyle(PlainTextFieldStyle())
                    .font(.system(size: 13))
                    .foregroundColor(vm.currentTheme.textPrimary)
                    .padding(10)
                    .background(Color.white.opacity(0.07))
                    .cornerRadius(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(vm.currentTheme.outline.opacity(0.25), lineWidth: 1)
                    )
                
                // 1-Click Quick-Pick Artist Chips from Library
                if !vm.distinctArtists.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            Text("Quick Fill Artist:")
                                .font(.system(size: 10, design: .monospaced))
                                .foregroundColor(vm.currentTheme.textMuted)
                            
                            ForEach(vm.distinctArtists.prefix(12), id: \.self) { artist in
                                Button(action: {
                                    vm.newPlaylistName = artist
                                    vm.newPlaylistKeyword = artist
                                }) {
                                    Text(artist)
                                        .font(.system(size: 10, weight: .semibold))
                                        .foregroundColor(vm.newPlaylistKeyword == artist ? vm.currentTheme.onPrimary : vm.currentTheme.textPrimary)
                                        .padding(.horizontal, 9)
                                        .padding(.vertical, 4)
                                        .background(vm.newPlaylistKeyword == artist ? vm.currentTheme.primary : Color.white.opacity(0.08))
                                        .cornerRadius(8)
                                        .contentShape(Rectangle())
                                }
                                .buttonStyle(PlainButtonStyle())
                            }
                        }
                        .padding(.top, 2)
                    }
                }
            }
            
            // 3. Select / Toggle Specific Tracks from Library
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("MANUAL TRACK SELECTION (\(vm.newPlaylistSelectedPaths.count) SELECTED)")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundColor(vm.currentTheme.textSecondary)
                    Spacer()
                    if !vm.newPlaylistSelectedPaths.isEmpty {
                        Button(action: { vm.newPlaylistSelectedPaths.removeAll() }) {
                            Text("Clear Selection")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundColor(vm.currentTheme.primary)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                
                ScrollView {
                    LazyVStack(spacing: 4) {
                        ForEach(vm.tracks) { t in
                            let isChecked = vm.newPlaylistSelectedPaths.contains(t.path) ||
                                (!vm.newPlaylistKeyword.trimmingCharacters(in: .whitespaces).isEmpty &&
                                 (t.artist.localizedCaseInsensitiveContains(vm.newPlaylistKeyword) ||
                                  t.title.localizedCaseInsensitiveContains(vm.newPlaylistKeyword)))
                            Button(action: {
                                if vm.newPlaylistSelectedPaths.contains(t.path) {
                                    vm.newPlaylistSelectedPaths.remove(t.path)
                                } else {
                                    vm.newPlaylistSelectedPaths.insert(t.path)
                                }
                            }) {
                                HStack(spacing: 10) {
                                    Image(systemName: isChecked ? "checkmark.square.fill" : "square")
                                        .foregroundColor(isChecked ? vm.currentTheme.primary : vm.currentTheme.textMuted)
                                    Text(t.title)
                                        .font(.system(size: 12, weight: .medium))
                                        .foregroundColor(vm.currentTheme.textPrimary)
                                        .lineLimit(1)
                                    Spacer()
                                    Text(t.artist)
                                        .font(.system(size: 11))
                                        .foregroundColor(vm.currentTheme.textSecondary)
                                        .lineLimit(1)
                                }
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(isChecked ? vm.currentTheme.primaryContainer.opacity(0.30) : Color.white.opacity(0.03))
                                .cornerRadius(8)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                }
                .frame(height: 170)
                .padding(6)
                .background(Color.black.opacity(0.22))
                .cornerRadius(10)
            }
            
            // Modal Action Buttons
            HStack {
                Button(action: { vm.showNewPlaylistModal = false }) {
                    Text("Cancel")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(vm.currentTheme.textSecondary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 9)
                        .background(Color.white.opacity(0.08))
                        .cornerRadius(10)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())
                
                Spacer()
                
                Button(action: { vm.createPlaylistFromModal() }) {
                    HStack(spacing: 6) {
                        Image(systemName: "checkmark.circle.fill")
                        Text("Create & Open Playlist")
                    }
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(vm.currentTheme.onPrimary)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 9)
                    .background(vm.currentTheme.primary)
                    .cornerRadius(10)
                    .contentShape(Rectangle())
                }
                .buttonStyle(TsrossaScaleButtonStyle())
            }
        }
        .padding(24)
        .frame(width: 540)
        .background(vm.currentTheme.surfaceContainerHigh)
        .cornerRadius(22)
        .overlay(
            RoundedRectangle(cornerRadius: 22)
                .stroke(vm.currentTheme.outline.opacity(0.35), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.55), radius: 30, x: 0, y: 16)
    }
    
    // =========================================================================
    // SAVE ACTIVE QUEUE AS PLAYLIST MODAL
    // =========================================================================
    private var saveQueueModalView: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("SAVE QUEUE AS PLAYLIST")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundColor(vm.currentTheme.primary)
                    Text("Convert Active Queue to Playlist")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(vm.currentTheme.textPrimary)
                }
                Spacer()
                Button(action: { vm.showSaveQueueModal = false }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 18))
                        .foregroundColor(vm.currentTheme.textSecondary)
                }
                .buttonStyle(PlainButtonStyle())
            }
            
            // 1. Playlist Name Input
            VStack(alignment: .leading, spacing: 6) {
                Text("PLAYLIST NAME")
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundColor(vm.currentTheme.textSecondary)
                
                TextField("e.g., My Up Next Mix, Evening Queue...", text: $vm.saveQueuePlaylistName)
                    .textFieldStyle(PlainTextFieldStyle())
                    .font(.system(size: 13))
                    .foregroundColor(vm.currentTheme.textPrimary)
                    .padding(10)
                    .background(Color.white.opacity(0.07))
                    .cornerRadius(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(vm.currentTheme.outline.opacity(0.25), lineWidth: 1)
                    )
            }
            
            // 2. Track List Summary
            VStack(alignment: .leading, spacing: 6) {
                let count = (engine.currentTrack != nil ? 1 : 0) + vm.upNextQueue.count
                Text("TRACKS TO INCLUDE (\(count) TRACKS)")
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundColor(vm.currentTheme.textSecondary)
                
                ScrollView {
                    LazyVStack(spacing: 4) {
                        if let current = engine.currentTrack {
                            HStack(spacing: 10) {
                                Image(systemName: "speaker.wave.2.fill")
                                    .font(.system(size: 11))
                                    .foregroundColor(vm.currentTheme.primary)
                                Text("Now Playing: \(current.title)")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(vm.currentTheme.textPrimary)
                                    .lineLimit(1)
                                Spacer()
                                Text(current.artist)
                                    .font(.system(size: 11))
                                    .foregroundColor(vm.currentTheme.textSecondary)
                                    .lineLimit(1)
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(vm.currentTheme.primaryContainer.opacity(0.25))
                            .cornerRadius(8)
                        }
                        
                        ForEach(Array(vm.upNextQueue.enumerated()), id: \.element.id) { idx, t in
                            HStack(spacing: 10) {
                                Text("#\(idx + 1)")
                                    .font(.system(size: 11, design: .monospaced))
                                    .foregroundColor(vm.currentTheme.primary)
                                    .frame(width: 24, alignment: .leading)
                                Text(t.title)
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundColor(vm.currentTheme.textPrimary)
                                    .lineLimit(1)
                                Spacer()
                                Text(t.artist)
                                    .font(.system(size: 11))
                                    .foregroundColor(vm.currentTheme.textSecondary)
                                    .lineLimit(1)
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.white.opacity(0.03))
                            .cornerRadius(8)
                        }
                    }
                }
                .frame(height: 160)
                .padding(6)
                .background(Color.black.opacity(0.22))
                .cornerRadius(10)
            }
            
            // Modal Action Buttons
            HStack {
                Button(action: { vm.showSaveQueueModal = false }) {
                    Text("Cancel")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(vm.currentTheme.textSecondary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 9)
                        .background(Color.white.opacity(0.08))
                        .cornerRadius(10)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())
                
                Spacer()
                
                Button(action: { vm.saveCurrentQueueAsPlaylist() }) {
                    HStack(spacing: 6) {
                        Image(systemName: "plus.rectangle.on.rectangle")
                        Text("Save as New Playlist")
                    }
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(vm.currentTheme.onPrimary)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 9)
                    .background(vm.currentTheme.primary)
                    .cornerRadius(10)
                    .contentShape(Rectangle())
                }
                .buttonStyle(TsrossaScaleButtonStyle())
            }
        }
        .padding(24)
        .frame(width: 520)
        .background(vm.currentTheme.surfaceContainerHigh)
        .cornerRadius(22)
        .overlay(
            RoundedRectangle(cornerRadius: 22)
                .stroke(vm.currentTheme.outline.opacity(0.35), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.55), radius: 30, x: 0, y: 16)
    }
    
    // =========================================================================
    // VIEW 3: WIDESCREEN PLAY QUEUE & LYRICS VIEW (YouTube Music Style Dual Mode)
    // =========================================================================
    private var desktopQueueView: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header Bar with YouTube Music Style Tab Switcher
            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(vm.queueTabMode == "LYRICS" ? "SYNCHRONIZED LYRICS" : "ACTIVE PLAYBACK QUEUE")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundColor(vm.currentTheme.primary)
                    Text(vm.queueTabMode == "LYRICS" ? "Karaoke & Song Lyrics" : "Play Queue & Up Next Flow")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(vm.currentTheme.textPrimary)
                }
                
                Spacer()
                
                // YouTube Music Style Dual Tab Pills: [ Up Next (N) ] [ Lyrics ]
                HStack(spacing: 6) {
                    Button(action: { vm.queueTabMode = "QUEUE" }) {
                        HStack(spacing: 5) {
                            Image(systemName: "music.note.list")
                            Text("Up Next (\(vm.upNextQueue.count))")
                        }
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(vm.queueTabMode == "QUEUE" ? vm.currentTheme.onPrimary : vm.currentTheme.textSecondary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(vm.queueTabMode == "QUEUE" ? vm.currentTheme.primary : vm.currentTheme.surfaceContainerHigh)
                        .cornerRadius(12)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PlainButtonStyle())
                    
                    Button(action: { vm.queueTabMode = "LYRICS" }) {
                        HStack(spacing: 5) {
                            Image(systemName: "quote.bubble.fill")
                            Text("Lyrics")
                            if vm.hasLyrics {
                                Circle()
                                    .fill(vm.queueTabMode == "LYRICS" ? vm.currentTheme.onPrimary : TsrossaColors.terminalGreen)
                                    .frame(width: 5, height: 5)
                            }
                        }
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(vm.queueTabMode == "LYRICS" ? vm.currentTheme.onPrimary : vm.currentTheme.textSecondary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(vm.queueTabMode == "LYRICS" ? vm.currentTheme.primary : vm.currentTheme.surfaceContainerHigh)
                        .cornerRadius(12)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PlainButtonStyle())
                }
                .padding(3)
                .background(vm.currentTheme.surfaceContainerLowest.opacity(0.6))
                .cornerRadius(14)
                
                Spacer()
                
                // Right Actions based on Active Tab
                if vm.queueTabMode == "QUEUE" {
                    HStack(spacing: 10) {
                        if !vm.upNextQueue.isEmpty {
                            Button(action: { vm.openSaveQueueModal() }) {
                                HStack(spacing: 6) {
                                    Image(systemName: "plus.rectangle.on.rectangle")
                                    Text("Save Queue as Playlist")
                                }
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(vm.currentTheme.onPrimary)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 7)
                                .background(vm.currentTheme.primary)
                                .cornerRadius(14)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(TsrossaScaleButtonStyle())
                            
                            Button(action: { vm.clearQueue() }) {
                                HStack(spacing: 6) {
                                    Image(systemName: "trash")
                                    Text("Clear Queue")
                                }
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(vm.currentTheme.error)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 7)
                                .background(vm.currentTheme.surfaceContainerHigh)
                                .cornerRadius(14)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                } else {
                    HStack(spacing: 10) {
                        if vm.hasLyrics {
                            Text(vm.currentLyrics.first?.isSynced == true ? "SYNCED LRC" : "PLAIN TEXT")
                                .font(.system(size: 9, weight: .bold, design: .monospaced))
                                .foregroundColor(TsrossaColors.terminalGreen)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(TsrossaColors.terminalGreen.opacity(0.15))
                                .cornerRadius(6)
                        }
                        
                        if let cur = engine.currentTrack {
                            Button(action: { vm.loadLyrics(for: cur) }) {
                                HStack(spacing: 5) {
                                    Image(systemName: "arrow.clockwise")
                                    Text("Reload Lyrics")
                                }
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(vm.currentTheme.textSecondary)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 7)
                                .background(vm.currentTheme.surfaceContainerHigh)
                                .cornerRadius(14)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                }
            }
            .padding(.horizontal, 28)
            .padding(.vertical, 16)
            
            Divider()
                .background(vm.currentTheme.outline.opacity(0.15))
            
            if vm.queueTabMode == "LYRICS" {
                desktopLyricsView
            } else {
                desktopQueueScrollContent
            }
        }
    }
    
    // =========================================================================
    // VIEW 3A: INTERACTIVE UP NEXT QUEUE CONTENT
    // =========================================================================
    private var desktopQueueScrollContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                // SECTION 1: NOW PLAYING CARD
                VStack(alignment: .leading, spacing: 10) {
                    Text("NOW PLAYING")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(vm.currentTheme.primary)
                    
                    if let current = engine.currentTrack {
                        HStack(spacing: 16) {
                            ZStack {
                                if let art = vm.currentArtwork {
                                    Image(nsImage: art)
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                        .frame(width: 52, height: 52)
                                        .clipShape(RoundedRectangle(cornerRadius: 10))
                                } else {
                                    RoundedRectangle(cornerRadius: 10)
                                        .fill(vm.currentTheme.primaryContainer.opacity(0.5))
                                        .frame(width: 52, height: 52)
                                        .overlay(
                                            Image(systemName: "waveform")
                                                .font(.system(size: 22))
                                                .foregroundColor(vm.currentTheme.primary)
                                        )
                                }
                            }
                            
                            VStack(alignment: .leading, spacing: 3) {
                                Text(current.title)
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(vm.currentTheme.textPrimary)
                                    .lineLimit(1)
                                HStack(spacing: 8) {
                                    Text(current.artist)
                                        .font(.system(size: 12))
                                        .foregroundColor(vm.currentTheme.textSecondary)
                                        .lineLimit(1)
                                    Text("•")
                                        .foregroundColor(vm.currentTheme.textMuted)
                                    Text(current.album)
                                        .font(.system(size: 12))
                                        .foregroundColor(vm.currentTheme.textMuted)
                                        .lineLimit(1)
                                }
                            }
                            
                            Spacer()
                            
                            // Format Badge & Duration
                            VStack(alignment: .trailing, spacing: 4) {
                                Text("\(current.path.hasSuffix(".flac") ? "FLAC" : "WAV") \(current.bitDepth)-bit / \(String(format: "%.1fkHz", Double(current.sampleRate)/1000.0))")
                                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                                    .foregroundColor(vm.currentTheme.onPrimaryContainer)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(vm.currentTheme.primaryContainer)
                                    .cornerRadius(6)
                                
                                Text("\(vm.formatTime(engine.currentTime)) / \(vm.formatTime(current.durationSeconds))")
                                    .font(.system(size: 11, design: .monospaced))
                                    .foregroundColor(vm.currentTheme.textSecondary)
                            }
                        }
                        .padding(14)
                        .background(vm.currentTheme.surfaceContainerHigh)
                        .cornerRadius(14)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(vm.currentTheme.primary.opacity(0.4), lineWidth: 1)
                        )
                    } else {
                        HStack(spacing: 12) {
                            Image(systemName: "pause.circle")
                                .font(.system(size: 20))
                                .foregroundColor(vm.currentTheme.textMuted)
                            Text("No track currently playing. Pick a song from your library or queue to start listening.")
                                .font(.system(size: 13))
                                .foregroundColor(vm.currentTheme.textSecondary)
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.white.opacity(0.03))
                        .cornerRadius(12)
                    }
                }
                
                // SECTION 2: DYNAMIC UP NEXT IN QUEUE
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("UP NEXT IN QUEUE (\(vm.upNextQueue.count))")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundColor(vm.currentTheme.primary)
                        
                        Spacer()
                        
                        if !vm.upNextQueue.isEmpty {
                            Text("These tracks play next in order before continuing normal playback")
                                .font(.system(size: 11))
                                .foregroundColor(vm.currentTheme.textMuted)
                        }
                    }
                    
                    if vm.upNextQueue.isEmpty {
                        VStack(spacing: 10) {
                            Image(systemName: "text.badge.plus")
                                .font(.system(size: 28))
                                .foregroundColor(vm.currentTheme.textMuted)
                            Text("Your Up Next Queue is Empty")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(vm.currentTheme.textPrimary)
                            Text("Use 'Play Next' or 'Add to Queue' from the track menu or right-click to queue tracks here dynamically like Google/Spotify.")
                                .font(.system(size: 12))
                                .foregroundColor(vm.currentTheme.textSecondary)
                                .multilineTextAlignment(.center)
                        }
                        .padding(24)
                        .frame(maxWidth: .infinity)
                        .background(Color.white.opacity(0.02))
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(vm.currentTheme.outline.opacity(0.18), lineWidth: 1)
                        )
                    } else {
                        LazyVStack(spacing: 6) {
                            ForEach(Array(vm.upNextQueue.enumerated()), id: \.element.id) { idx, track in
                                interactiveQueueItemRow(index: idx, track: track)
                            }
                        }
                    }
                }
                
                // SECTION 3: CONTINUATION FROM CURRENT CONTEXT
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("NEXT FROM: \(vm.selectedPlaylist?.name.uppercased() ?? "LIBRARY") (\(filteredTracks.count) TRACKS)")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundColor(vm.currentTheme.textSecondary)
                        Spacer()
                        Text("Will resume automatically when Up Next queue finishes")
                            .font(.system(size: 11))
                            .foregroundColor(vm.currentTheme.textMuted)
                    }
                    
                    LazyVStack(spacing: 4) {
                        ForEach(Array(filteredTracks.prefix(50).enumerated()), id: \.element.id) { idx, track in
                            desktopTrackTableRow(index: idx + 1, track: track)
                        }
                    }
                }
                
                // SECTION 4: AUTOPLAY INFINITE RADIO RECOMMENDATIONS (YouTube Music Style)
                let recommendations = vm.getAutoplayRecommendations()
                if !recommendations.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 8) {
                            Image(systemName: "sparkles")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(vm.isAutoplayRadio ? vm.currentTheme.primary : vm.currentTheme.textMuted)
                            
                            Text("AUTOPLAY INFINITE RADIO")
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                .foregroundColor(vm.currentTheme.textSecondary)
                            
                            Spacer()
                            
                            Button(action: {
                                vm.isAutoplayRadio.toggle()
                            }) {
                                HStack(spacing: 6) {
                                    Circle()
                                        .fill(vm.isAutoplayRadio ? TsrossaColors.terminalGreen : Color.gray.opacity(0.5))
                                        .frame(width: 7, height: 7)
                                    Text(vm.isAutoplayRadio ? "AUTOPLAY ACTIVE" : "AUTOPLAY OFF")
                                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                                        .foregroundColor(vm.isAutoplayRadio ? TsrossaColors.terminalGreen : vm.currentTheme.textMuted)
                                }
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(vm.currentTheme.surfaceContainerHigh)
                                .cornerRadius(12)
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                        
                        Text("When the current track or queue ends, tsrossa will seamlessly queue similar lossless music.")
                            .font(.system(size: 11))
                            .foregroundColor(vm.currentTheme.textMuted)
                        
                        LazyVStack(spacing: 4) {
                            ForEach(Array(recommendations.enumerated()), id: \.element.id) { idx, track in
                                desktopTrackTableRow(index: idx + 1, track: track)
                            }
                        }
                    }
                    .padding(.top, 14)
                }
            }
            .padding(.horizontal, 28)
            .padding(.vertical, 16)
        }
    }
    
    // =========================================================================
    // VIEW 3B: INTERACTIVE SYNCHRONIZED LYRICS (YouTube Music / Apple Music Style)
    // =========================================================================
    private var desktopLyricsView: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    if vm.isLoadingLyrics {
                        VStack(spacing: 12) {
                            ProgressView()
                                .scaleEffect(0.8)
                            Text("Loading lyrics...")
                                .font(.system(size: 12, design: .monospaced))
                                .foregroundColor(vm.currentTheme.textSecondary)
                        }
                        .frame(maxWidth: .infinity, minHeight: 320)
                    } else if vm.currentLyrics.isEmpty {
                        VStack(spacing: 14) {
                            Image(systemName: "quote.bubble")
                                .font(.system(size: 40))
                                .foregroundColor(vm.currentTheme.textMuted)
                            Text("No Lyrics Available")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(vm.currentTheme.textPrimary)
                            Text("Place a .lrc or .txt companion file in the same folder as your audio file:\n\(engine.currentTrack?.title ?? "song").lrc")
                                .font(.system(size: 12))
                                .foregroundColor(vm.currentTheme.textSecondary)
                                .multilineTextAlignment(.center)
                            
                            if let cur = engine.currentTrack {
                                Button(action: {
                                    NSWorkspace.shared.activateFileViewerSelecting([URL(fileURLWithPath: cur.path)])
                                }) {
                                    HStack(spacing: 6) {
                                        Image(systemName: "folder")
                                        Text("Open Track Folder in Finder")
                                    }
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(vm.currentTheme.onPrimary)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)
                                    .background(vm.currentTheme.primary)
                                    .cornerRadius(14)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(PlainButtonStyle())
                            }
                        }
                        .padding(40)
                        .frame(maxWidth: .infinity, minHeight: 320)
                    } else {
                        VStack(alignment: .leading, spacing: 18) {
                            ForEach(Array(vm.currentLyrics.enumerated()), id: \.element.id) { idx, line in
                                let isCurrent = (idx == vm.currentLyricIndex)
                                Button(action: {
                                    if line.isSynced {
                                        engine.seek(to: line.timestampSeconds)
                                    }
                                }) {
                                    HStack(alignment: .top, spacing: 14) {
                                        if line.isSynced {
                                            Text(vm.formatTime(line.timestampSeconds))
                                                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                                                .foregroundColor(isCurrent ? vm.currentTheme.primary : vm.currentTheme.textMuted.opacity(0.55))
                                                .frame(width: 44, alignment: .leading)
                                                .padding(.top, 2)
                                        }
                                        
                                        Text(line.text)
                                            .font(.system(size: isCurrent ? 22 : 16, weight: isCurrent ? .bold : .medium))
                                            .foregroundColor(isCurrent ? vm.currentTheme.textPrimary : vm.currentTheme.textMuted)
                                            .lineSpacing(4)
                                            .multilineTextAlignment(.leading)
                                        
                                        Spacer()
                                    }
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)
                                    .background(isCurrent ? vm.currentTheme.primaryContainer.opacity(0.35) : Color.clear)
                                    .cornerRadius(12)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(PlainButtonStyle())
                                .id(idx)
                            }
                        }
                        .padding(.horizontal, 36)
                        .padding(.vertical, 28)
                        .onChange(of: vm.currentLyricIndex) { newIndex in
                            if newIndex >= 0 {
                                withAnimation(.easeInOut(duration: 0.35)) {
                                    proxy.scrollTo(newIndex, anchor: .center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // =========================================================================
    // THEATER / FULLSCREEN IMMERSIVE MODE (Cmd + F, YouTube Music Style)
    // =========================================================================
    private var desktopTheaterView: some View {
        ZStack {
            // Ambient Aura Glow from Album Artwork / Deep Obsidian Background
            if let artwork = vm.currentArtwork {
                Image(nsImage: artwork)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .blur(radius: 80)
                    .opacity(0.18)
                    .clipped()
            }
            Color.black.opacity(0.85)
            
            VStack(spacing: 0) {
                // TOP HUD TOOLBAR
                HStack(spacing: 16) {
                    // Exit Theater Mode
                    Button(action: {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            vm.isTheaterMode = false
                        }
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "arrow.down.right.and.arrow.up.left")
                                .font(.system(size: 12, weight: .bold))
                            Text("Exit Theater (Esc)")
                                .font(.system(size: 11, weight: .semibold))
                        }
                        .foregroundColor(vm.currentTheme.textSecondary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(Color.white.opacity(0.08))
                        .cornerRadius(20)
                    }
                    .buttonStyle(PlainButtonStyle())
                    
                    Spacer()
                    
                    // Theater Mode Status Indicator
                    HStack(spacing: 8) {
                        Circle()
                            .fill(TsrossaColors.bitPerfectGold)
                            .frame(width: 7, height: 7)
                        Text("THEATER MODE")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(TsrossaColors.bitPerfectGold)
                        Text("•")
                            .foregroundColor(vm.currentTheme.textMuted)
                        Text("BIT-PERFECT DIRECT COREAUDIO")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(vm.currentTheme.textSecondary)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(Color.black.opacity(0.40))
                    .cornerRadius(18)
                    .overlay(
                        RoundedRectangle(cornerRadius: 18)
                            .stroke(Color.white.opacity(0.12), lineWidth: 1)
                    )
                    
                    Spacer()
                    
                    // Autoplay Infinite Radio Toggle
                    Button(action: {
                        vm.isAutoplayRadio.toggle()
                        vm.showToast(vm.isAutoplayRadio ? "Autoplay Infinite Radio Enabled" : "Autoplay Radio Disabled")
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "sparkles")
                                .font(.system(size: 12, weight: .semibold))
                            Text(vm.isAutoplayRadio ? "AUTOPLAY ON" : "AUTOPLAY OFF")
                                .font(.system(size: 10, weight: .bold, design: .monospaced))
                        }
                        .foregroundColor(vm.isAutoplayRadio ? vm.currentTheme.onPrimary : vm.currentTheme.textMuted)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(vm.isAutoplayRadio ? vm.currentTheme.primary : Color.white.opacity(0.08))
                        .cornerRadius(18)
                    }
                    .buttonStyle(PlainButtonStyle())
                    .help("Autoplay Infinite Radio (Plays matching lossless music continuously)")
                    
                    // Liked Song Toggle
                    if let cur = engine.currentTrack {
                        let isLiked = vm.isTrackLiked(cur.path)
                        Button(action: {
                            vm.toggleLike(track: cur)
                        }) {
                            Image(systemName: isLiked ? "heart.fill" : "heart")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(isLiked ? Color.red : vm.currentTheme.textSecondary)
                                .frame(width: 34, height: 34)
                                .background(Color.white.opacity(0.08))
                                .clipShape(Circle())
                        }
                        .buttonStyle(PlainButtonStyle())
                        .help(isLiked ? "Remove from Liked Songs" : "Save to Liked Songs")
                    }
                }
                .padding(.horizontal, 32)
                .padding(.top, 24)
                .padding(.bottom, 16)
                
                // MAIN WIDESCREEN CONTENT
                HStack(spacing: 36) {
                    // LEFT COLUMN: Huge Album Artwork & Audiophile Identifiers
                    VStack(spacing: 22) {
                        Spacer(minLength: 10)
                        
                        ZStack {
                            if let artwork = vm.currentArtwork {
                                Image(nsImage: artwork)
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .frame(width: 340, height: 340)
                                    .clipShape(RoundedRectangle(cornerRadius: 24))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 24)
                                            .stroke(Color.white.opacity(0.20), lineWidth: 1)
                                    )
                                    .shadow(color: vm.currentTheme.primary.opacity(0.45), radius: 36, x: 0, y: 18)
                            } else {
                                TsrossaBrandIcon(size: 340, cornerRadius: 24)
                                    .shadow(color: vm.currentTheme.primary.opacity(0.35), radius: 32, x: 0, y: 16)
                            }
                        }
                        
                        VStack(spacing: 8) {
                            Text(engine.currentTrack?.title ?? "No Track Selected")
                                .font(.system(size: 26, weight: .bold))
                                .foregroundColor(vm.currentTheme.textPrimary)
                                .lineLimit(1)
                                .multilineTextAlignment(.center)
                            
                            Text(subtitleText)
                                .font(.system(size: 15, weight: .medium))
                                .foregroundColor(vm.currentTheme.textSecondary)
                                .lineLimit(1)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: 400)
                        
                        // Lossless Format & Signal Badges
                        HStack(spacing: 10) {
                            HStack(spacing: 6) {
                                Circle()
                                    .fill(TsrossaColors.bitPerfectGold)
                                    .frame(width: 6, height: 6)
                                Text(formatBadgeText)
                                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                                    .foregroundColor(vm.currentTheme.onPrimaryContainer)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(vm.currentTheme.primaryContainer.opacity(0.75))
                            .cornerRadius(16)
                            
                            if vm.isRamPlayback {
                                Text("RAM PINNED")
                                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                                    .foregroundColor(TsrossaColors.audiophileCyan)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(TsrossaColors.audiophileCyan.opacity(0.18))
                                    .cornerRadius(16)
                            }
                            
                            if vm.isExclusiveHogMode {
                                Text("HOG MODE")
                                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                                    .foregroundColor(TsrossaColors.bitPerfectGold)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(TsrossaColors.bitPerfectGold.opacity(0.18))
                                    .cornerRadius(16)
                            }
                        }
                        
                        Spacer(minLength: 10)
                    }
                    .frame(maxWidth: .infinity)
                    
                    // RIGHT COLUMN: Synchronized Real-Time Lyrics or Up Next Queue
                    VStack(spacing: 14) {
                        // Switcher Header: Lyrics vs Queue
                        HStack(spacing: 8) {
                            Button(action: {
                                withAnimation(.easeInOut(duration: 0.2)) {
                                    vm.theaterTabMode = "LYRICS"
                                }
                            }) {
                                HStack(spacing: 6) {
                                    Image(systemName: "quote.bubble")
                                    Text("SYNCHRONIZED LYRICS")
                                }
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                .foregroundColor(vm.theaterTabMode == "LYRICS" ? vm.currentTheme.onPrimary : vm.currentTheme.textSecondary)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 7)
                                .background(vm.theaterTabMode == "LYRICS" ? vm.currentTheme.primary : Color.white.opacity(0.08))
                                .cornerRadius(16)
                            }
                            .buttonStyle(PlainButtonStyle())
                            
                            Button(action: {
                                withAnimation(.easeInOut(duration: 0.2)) {
                                    vm.theaterTabMode = "QUEUE"
                                }
                            }) {
                                HStack(spacing: 6) {
                                    Image(systemName: "music.note.list")
                                    Text("UP NEXT & RADIO")
                                }
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                .foregroundColor(vm.theaterTabMode == "QUEUE" ? vm.currentTheme.onPrimary : vm.currentTheme.textSecondary)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 7)
                                .background(vm.theaterTabMode == "QUEUE" ? vm.currentTheme.primary : Color.white.opacity(0.08))
                                .cornerRadius(16)
                            }
                            .buttonStyle(PlainButtonStyle())
                            
                            Spacer()
                        }
                        .padding(.horizontal, 8)
                        
                        // Content Pane
                        if vm.theaterTabMode == "LYRICS" {
                            theaterLyricsScrollView
                        } else {
                            theaterQueueScrollView
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
                .padding(.horizontal, 36)
                .frame(maxHeight: .infinity)
                
                // BOTTOM TRANSPORT BAR
                Rectangle()
                    .fill(Color.white.opacity(0.10))
                    .frame(height: 1)
                
                desktopBottomTransportBar
                    .frame(height: 90)
            }
        }
        .ignoresSafeArea()
    }
    
    private var theaterLyricsScrollView: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    if vm.isLoadingLyrics {
                        VStack(spacing: 12) {
                            ProgressView()
                                .scaleEffect(0.9)
                            Text("Loading synced lyrics...")
                                .font(.system(size: 13, design: .monospaced))
                                .foregroundColor(vm.currentTheme.textSecondary)
                        }
                        .frame(maxWidth: .infinity, minHeight: 340)
                    } else if vm.currentLyrics.isEmpty {
                        VStack(spacing: 16) {
                            Image(systemName: "quote.bubble")
                                .font(.system(size: 48))
                                .foregroundColor(vm.currentTheme.textMuted)
                            Text("No Lyrics Available")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(vm.currentTheme.textPrimary)
                            Text("Place a companion .lrc or .txt file next to your audio file to enable synchronized lyrics.")
                                .font(.system(size: 13))
                                .foregroundColor(vm.currentTheme.textSecondary)
                                .multilineTextAlignment(.center)
                            
                            Button(action: {
                                withAnimation(.easeInOut(duration: 0.2)) {
                                    vm.theaterTabMode = "QUEUE"
                                }
                            }) {
                                Text("View Up Next Queue →")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(vm.currentTheme.onPrimary)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)
                                    .background(vm.currentTheme.primary)
                                    .cornerRadius(14)
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                        .padding(40)
                        .frame(maxWidth: .infinity, minHeight: 340)
                    } else {
                        VStack(alignment: .leading, spacing: 22) {
                            ForEach(Array(vm.currentLyrics.enumerated()), id: \.element.id) { idx, line in
                                let isCurrent = (idx == vm.currentLyricIndex)
                                Button(action: {
                                    if line.isSynced {
                                        engine.seek(to: line.timestampSeconds)
                                    }
                                }) {
                                    HStack(alignment: .top, spacing: 16) {
                                        if line.isSynced {
                                            Text(vm.formatTime(line.timestampSeconds))
                                                .font(.system(size: 12, weight: .semibold, design: .monospaced))
                                                .foregroundColor(isCurrent ? vm.currentTheme.primary : vm.currentTheme.textMuted.opacity(0.40))
                                                .frame(width: 48, alignment: .leading)
                                                .padding(.top, 3)
                                        }
                                        
                                        Text(line.text)
                                            .font(.system(size: isCurrent ? 24 : 17, weight: isCurrent ? .bold : .medium))
                                            .foregroundColor(isCurrent ? vm.currentTheme.textPrimary : vm.currentTheme.textMuted.opacity(0.60))
                                            .lineSpacing(5)
                                            .multilineTextAlignment(.leading)
                                        
                                        Spacer()
                                    }
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 10)
                                    .background(isCurrent ? vm.currentTheme.primaryContainer.opacity(0.35) : Color.clear)
                                    .cornerRadius(12)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(PlainButtonStyle())
                                .id(idx)
                            }
                        }
                        .padding(.vertical, 30)
                        .padding(.horizontal, 12)
                    }
                }
            }
            .onChange(of: vm.currentLyricIndex) { newIndex in
                if newIndex >= 0 {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                        proxy.scrollTo(newIndex, anchor: .center)
                    }
                }
            }
        }
        .background(Color.white.opacity(0.03))
        .cornerRadius(18)
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
    }
    
    private var theaterQueueScrollView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if !vm.upNextQueue.isEmpty {
                    Text("UP NEXT QUEUE (\(vm.upNextQueue.count) TRACKS)")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(vm.currentTheme.primary)
                    
                    LazyVStack(spacing: 4) {
                        ForEach(Array(vm.upNextQueue.enumerated()), id: \.element.id) { idx, track in
                            desktopTrackTableRow(index: idx + 1, track: track)
                        }
                    }
                    
                    Divider()
                        .background(Color.white.opacity(0.12))
                        .padding(.vertical, 8)
                }
                
                let recs = vm.getAutoplayRecommendations()
                if !recs.isEmpty {
                    HStack {
                        Image(systemName: "sparkles")
                            .foregroundColor(vm.isAutoplayRadio ? vm.currentTheme.primary : vm.currentTheme.textMuted)
                        Text("AUTOPLAY INFINITE RADIO")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundColor(vm.currentTheme.textSecondary)
                        Spacer()
                        Text(vm.isAutoplayRadio ? "READY TO PLAY" : "AUTOPLAY OFF")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(vm.isAutoplayRadio ? TsrossaColors.terminalGreen : vm.currentTheme.textMuted)
                    }
                    
                    LazyVStack(spacing: 4) {
                        ForEach(Array(recs.enumerated()), id: \.element.id) { idx, track in
                            desktopTrackTableRow(index: idx + 1, track: track)
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(Color.white.opacity(0.03))
        .cornerRadius(18)
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
    }
    
    private func interactiveQueueItemRow(index: Int, track: TrackMetadata) -> some View {
        HStack(spacing: 12) {
            // Reorder controls (Up / Down)
            VStack(spacing: 2) {
                Button(action: {
                    if index > 0 {
                        vm.moveInQueue(from: index, to: index - 1)
                    }
                }) {
                    Image(systemName: "chevron.up")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(index > 0 ? vm.currentTheme.textSecondary : Color.clear)
                        .frame(width: 18, height: 12)
                }
                .buttonStyle(PlainButtonStyle())
                .disabled(index == 0)
                
                Button(action: {
                    if index < vm.upNextQueue.count - 1 {
                        vm.moveInQueue(from: index, to: index + 1)
                    }
                }) {
                    Image(systemName: "chevron.down")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(index < vm.upNextQueue.count - 1 ? vm.currentTheme.textSecondary : Color.clear)
                        .frame(width: 18, height: 12)
                }
                .buttonStyle(PlainButtonStyle())
                .disabled(index == vm.upNextQueue.count - 1)
            }
            .frame(width: 20)
            
            // Queue position number
            Text("#\(index + 1)")
                .font(.system(size: 12, weight: .bold, design: .monospaced))
                .foregroundColor(vm.currentTheme.primary)
                .frame(width: 30, alignment: .leading)
            
            // Play now button
            Button(action: {
                vm.removeFromQueue(at: index)
                engine.play(track: track)
            }) {
                Image(systemName: "play.circle.fill")
                    .font(.system(size: 16))
                    .foregroundColor(vm.currentTheme.primary)
            }
            .buttonStyle(PlainButtonStyle())
            
            // Track Info
            VStack(alignment: .leading, spacing: 2) {
                Text(track.title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(vm.currentTheme.textPrimary)
                    .lineLimit(1)
                
                HStack(spacing: 6) {
                    Text(track.artist)
                        .font(.system(size: 11))
                        .foregroundColor(vm.currentTheme.textSecondary)
                        .lineLimit(1)
                    Text("•")
                        .foregroundColor(vm.currentTheme.textMuted)
                    Text(track.album)
                        .font(.system(size: 11))
                        .foregroundColor(vm.currentTheme.textMuted)
                        .lineLimit(1)
                }
            }
            
            Spacer()
            
            // Format Badge
            Text("\(track.path.hasSuffix(".flac") ? "FLAC" : "WAV") \(track.bitDepth)-bit")
                .font(.system(size: 9, weight: .bold, design: .monospaced))
                .foregroundColor(vm.currentTheme.primary)
                .padding(.horizontal, 7)
                .padding(.vertical, 3)
                .background(vm.currentTheme.primaryContainer.opacity(0.35))
                .cornerRadius(5)
            
            // Duration
            Text(vm.formatTime(track.durationSeconds))
                .font(.system(size: 11, design: .monospaced))
                .foregroundColor(vm.currentTheme.textSecondary)
                .frame(width: 48, alignment: .trailing)
            
            // Remove from queue button (xmark)
            Button(action: { vm.removeFromQueue(at: index) }) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 14))
                    .foregroundColor(vm.currentTheme.textMuted)
                    .padding(4)
            }
            .buttonStyle(PlainButtonStyle())
            .help("Remove from Queue")
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(vm.currentTheme.surfaceContainerHigh.opacity(0.65))
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(vm.currentTheme.outline.opacity(0.18), lineWidth: 1)
        )
    }
    
    // =========================================================================
    // VIEW 4: WIDESCREEN 2-COLUMN BENTO SETTINGS (Desktop Audiophile Layout)
    // =========================================================================
    private var desktopSettingsBentoView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("COREAUDIO HAL & APPEARANCE")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(vm.currentTheme.primary)
                        Text("Audio Output & Desktop Preferences")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(vm.currentTheme.textPrimary)
                    }
                    Spacer()
                    Button(action: {
                        engine.updateTelemetry()
                        vm.showTelemetry = true
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "waveform.path.ecg")
                            Text("Live Hardware Audit (⌘D)")
                        }
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(vm.currentTheme.onPrimary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(vm.currentTheme.primary)
                        .cornerRadius(16)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())
                }
                
                // 2-Column Widescreen Bento Grid
                HStack(alignment: .top, spacing: 20) {
                    // LEFT BENTO COLUMN
                    VStack(spacing: 20) {
                        // 1. OUTPUT DEVICE SELECTION
                        settingsCard(title: "OUTPUT DEVICE & DAC HARDWARE") {
                            VStack(alignment: .leading, spacing: 12) {
                                ForEach(engine.devices) { dev in
                                    Button(action: { engine.selectDevice(id: dev.id) }) {
                                        HStack(spacing: 12) {
                                            Image(systemName: dev.isUsb ? "cable.connector" : "speaker.wave.2.fill")
                                                .font(.system(size: 16))
                                                .foregroundColor(vm.currentTheme.primary)
                                                .frame(width: 24)
                                            
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text(dev.name)
                                                    .font(.system(size: 13, weight: .semibold))
                                                    .foregroundColor(vm.currentTheme.textPrimary)
                                                Text(dev.isUsb ? "External USB DAC (UAC2 Direct Hardware Link)" : "Internal Apple Silicon CoreAudio Hardware")
                                                    .font(.system(size: 11))
                                                    .foregroundColor(vm.currentTheme.textSecondary)
                                            }
                                            
                                            Spacer()
                                            
                                            if dev.id == engine.selectedDeviceId {
                                                HStack(spacing: 6) {
                                                    Text(String(format: "%.1f kHz", dev.sampleRate / 1000.0))
                                                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                                                        .foregroundColor(vm.currentTheme.primary)
                                                    Image(systemName: "checkmark.circle.fill")
                                                        .foregroundColor(vm.currentTheme.primary)
                                                }
                                            }
                                        }
                                        .padding(12)
                                        .background(dev.id == engine.selectedDeviceId ? vm.currentTheme.primaryContainer.opacity(0.40) : Color.white.opacity(0.04))
                                        .cornerRadius(12)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12)
                                                .stroke(dev.id == engine.selectedDeviceId ? vm.currentTheme.primary.opacity(0.45) : Color.clear, lineWidth: 1)
                                        )
                                        .contentShape(Rectangle())
                                    }
                                    .buttonStyle(PlainButtonStyle())
                                }
                                
                                Divider().background(vm.currentTheme.outline.opacity(0.15))
                                
                                settingsToggleRow(
                                    title: "Bit-Perfect Exclusive Lock (Hog Mode)",
                                    subtitle: "Direct DAC ownership, bypassing macOS system audio mixer",
                                    isOn: $vm.isExclusiveHogMode
                                )
                                
                                Divider().background(vm.currentTheme.outline.opacity(0.15))
                                
                                settingsToggleRow(
                                    title: "Dynamic Hardware Clock Switching",
                                    subtitle: "Switches DAC sample rate (44.1k–192k) to match native file",
                                    isOn: $vm.isAutoClockSwitch
                                )
                                
                                Divider().background(vm.currentTheme.outline.opacity(0.15))
                                
                                settingsToggleRow(
                                    title: "Zero-Jitter RAM Playback (mlock)",
                                    subtitle: "Pins decoded PCM frames in physical RAM to prevent SSD latency",
                                    isOn: $vm.isRamPlayback
                                )
                            }
                        }
                        
                        // 2. MUSIC DIRECTORY & LIBRARY
                        settingsCard(title: "MUSIC DIRECTORY & STORAGE") {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Active Watched Folder:")
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundColor(vm.currentTheme.textSecondary)
                                
                                HStack {
                                    Image(systemName: "folder.fill")
                                        .foregroundColor(vm.currentTheme.primary)
                                    Text(vm.currentFolder)
                                        .font(.system(size: 12, design: .monospaced))
                                        .foregroundColor(vm.currentTheme.textPrimary)
                                        .lineLimit(1)
                                        .truncationMode(.middle)
                                }
                                .padding(10)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.white.opacity(0.05))
                                .cornerRadius(8)
                                
                                HStack(spacing: 10) {
                                    Button(action: { vm.openFolderPicker() }) {
                                        HStack(spacing: 6) {
                                            Image(systemName: "folder.badge.gearshape")
                                            Text("Change Folder")
                                        }
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundColor(vm.currentTheme.textPrimary)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 8)
                                        .background(vm.currentTheme.primaryContainer.opacity(0.45))
                                        .cornerRadius(8)
                                        .contentShape(Rectangle())
                                    }
                                    .buttonStyle(TsrossaScaleButtonStyle())
                                    
                                    Button(action: { vm.rescanCurrentFolder() }) {
                                        HStack(spacing: 6) {
                                            Image(systemName: "arrow.clockwise")
                                            Text("Rescan Library")
                                        }
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundColor(vm.currentTheme.textPrimary)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 8)
                                        .background(Color.white.opacity(0.08))
                                        .cornerRadius(8)
                                        .contentShape(Rectangle())
                                    }
                                    .buttonStyle(TsrossaScaleButtonStyle())
                                }
                            }
                        }
                    }
                    .frame(maxWidth: .infinity)
                    
                    // RIGHT BENTO COLUMN
                    VStack(spacing: 20) {
                        // 3. THEME & VISUAL APPEARANCE
                        settingsCard(title: "MATERIAL YOU & DESKTOP APPEARANCE") {
                            VStack(alignment: .leading, spacing: 14) {
                                Text("Accent Color Palette")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(vm.currentTheme.textPrimary)
                                
                                HStack(spacing: 6) {
                                    accentPill(title: "Auto (Monet)", mode: "DYNAMIC", color: vm.currentTheme.primary)
                                    accentPill(title: "Rose", mode: "ROSE", color: Color(red: 1.0, green: 0.18, blue: 0.33))
                                    accentPill(title: "Matcha", mode: "MATCHA", color: Color(red: 0.62, green: 0.82, blue: 0.38))
                                    accentPill(title: "Gold", mode: "GOLD", color: Color(red: 1.0, green: 0.84, blue: 0.0))
                                    accentPill(title: "Cyan", mode: "CYAN", color: Color(red: 0.0, green: 0.898, blue: 1.0))
                                }
                                
                                Divider().background(vm.currentTheme.outline.opacity(0.15))
                                
                                Text("Desktop Background Canvas")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(vm.currentTheme.textPrimary)
                                
                                HStack(spacing: 8) {
                                    bgPill(title: "Tonal M3", mode: "DYNAMIC")
                                    bgPill(title: "Artwork Aura", mode: "AURA")
                                    bgPill(title: "Pitch Black", mode: "BLACK")
                                }
                            }
                        }
                        
                        // 4. SIGNAL PATH & HARDWARE AUDIT
                        settingsCard(title: "SIGNAL PATH & HARDWARE AUDIT") {
                            VStack(alignment: .leading, spacing: 10) {
                                HStack {
                                    Text("Stream State:")
                                        .font(.system(size: 12, design: .monospaced))
                                        .foregroundColor(vm.currentTheme.textSecondary)
                                    Spacer()
                                    Text("ACTIVE (0 dBFS Bit-Perfect)")
                                        .font(.system(size: 12, weight: .bold, design: .monospaced))
                                        .foregroundColor(TsrossaColors.terminalGreen)
                                }
                                HStack {
                                    Text("DAC Clock:")
                                        .font(.system(size: 12, design: .monospaced))
                                        .foregroundColor(vm.currentTheme.textSecondary)
                                    Spacer()
                                    Text("\(engine.currentTrack?.sampleRate ?? 44100) Hz Crystal Lock")
                                        .font(.system(size: 12, weight: .bold, design: .monospaced))
                                        .foregroundColor(TsrossaColors.bitPerfectGold)
                                }
                                HStack {
                                    Text("Native Decoders:")
                                        .font(.system(size: 12, design: .monospaced))
                                        .foregroundColor(vm.currentTheme.textSecondary)
                                    Spacer()
                                    Text("dr_flac / dr_wav (C++17)")
                                        .font(.system(size: 12, weight: .bold, design: .monospaced))
                                        .foregroundColor(vm.currentTheme.primary)
                                }
                            }
                        }
                        
                        // 5. ABOUT TSROSSA & UPDATES
                        settingsCard(title: "ABOUT TSROSSA MACOS & SOFTWARE UPDATES") {
                            VStack(alignment: .leading, spacing: 14) {
                                HStack(spacing: 14) {
                                    TsrossaBrandIcon(size: 48, cornerRadius: 12)
                                    
                                    VStack(alignment: .leading, spacing: 6) {
                                        HStack {
                                            Text("tsrossa bit-perfect desktop player")
                                                .font(.system(size: 13, weight: .bold))
                                                .foregroundColor(vm.currentTheme.textPrimary)
                                            Spacer()
                                            Text("v\(updater.currentVersion) macOS")
                                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                                .foregroundColor(vm.currentTheme.primary)
                                        }
                                        Text("Native Apple Silicon CoreAudio HAL port with Material You dynamic theming and zero-DSP hardware playback.")
                                            .font(.system(size: 11))
                                            .foregroundColor(vm.currentTheme.textSecondary)
                                    }
                                }
                                
                                Divider().background(vm.currentTheme.outline.opacity(0.15))
                                
                                // Software Update Checker & Installer
                                VStack(alignment: .leading, spacing: 10) {
                                    HStack {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text("GitHub OTA Software Update")
                                                .font(.system(size: 12, weight: .semibold))
                                                .foregroundColor(vm.currentTheme.textPrimary)
                                            Text("github.com/\(updater.repoOwner)/\(updater.repoName)")
                                                .font(.system(size: 10, design: .monospaced))
                                                .foregroundColor(vm.currentTheme.textSecondary)
                                        }
                                        Spacer()
                                        
                                        if updater.isChecking {
                                            ProgressView()
                                                .scaleEffect(0.7)
                                        } else {
                                            Button(action: {
                                                updater.checkForUpdates()
                                            }) {
                                                HStack(spacing: 5) {
                                                    Image(systemName: "arrow.triangle.2.circlepath")
                                                        .font(.system(size: 11))
                                                    Text(updater.hasChecked ? "Check Again" : "Check for Updates")
                                                        .font(.system(size: 11, weight: .semibold))
                                                }
                                                .foregroundColor(vm.currentTheme.onPrimary)
                                                .padding(.horizontal, 12)
                                                .padding(.vertical, 6)
                                                .background(vm.currentTheme.primary)
                                                .cornerRadius(12)
                                                .contentShape(Rectangle())
                                            }
                                            .buttonStyle(TsrossaScaleButtonStyle())
                                            .disabled(updater.isDownloading || updater.isExtracting)
                                        }
                                    }
                                    
                                    if !updater.statusMessage.isEmpty {
                                        HStack(spacing: 6) {
                                            Circle()
                                                .fill(updater.updateAvailable ? TsrossaColors.bitPerfectGold : (updater.isDownloading ? vm.currentTheme.primary : TsrossaColors.terminalGreen))
                                                .frame(width: 6, height: 6)
                                            Text(updater.statusMessage)
                                                .font(.system(size: 11, design: .monospaced))
                                                .foregroundColor(updater.updateAvailable ? TsrossaColors.bitPerfectGold : vm.currentTheme.textSecondary)
                                        }
                                    }
                                    
                                    if updater.updateAvailable {
                                        VStack(alignment: .leading, spacing: 8) {
                                            if !updater.releaseNotes.isEmpty {
                                                Text("Release Notes:")
                                                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                                                    .foregroundColor(vm.currentTheme.textMuted)
                                                Text(updater.releaseNotes)
                                                    .font(.system(size: 11))
                                                    .foregroundColor(vm.currentTheme.textPrimary)
                                                    .lineLimit(4)
                                                    .padding(8)
                                                    .frame(maxWidth: .infinity, alignment: .leading)
                                                    .background(Color.white.opacity(0.04))
                                                    .cornerRadius(8)
                                            }
                                            
                                            if updater.isDownloading {
                                                VStack(alignment: .leading, spacing: 4) {
                                                    ProgressView(value: updater.downloadProgress, total: 1.0)
                                                        .accentColor(vm.currentTheme.primary)
                                                    HStack {
                                                        Text("\(Int(updater.downloadProgress * 100))%")
                                                            .font(.system(size: 10, design: .monospaced))
                                                            .foregroundColor(vm.currentTheme.textSecondary)
                                                        Spacer()
                                                        Text("Downloading package...")
                                                            .font(.system(size: 10, design: .monospaced))
                                                            .foregroundColor(vm.currentTheme.textSecondary)
                                                    }
                                                }
                                            } else if updater.isExtracting {
                                                HStack(spacing: 8) {
                                                    ProgressView().scaleEffect(0.7)
                                                    Text("Installing & relaunching...")
                                                        .font(.system(size: 11, weight: .semibold))
                                                        .foregroundColor(vm.currentTheme.primary)
                                                }
                                            } else {
                                                Button(action: {
                                                    updater.startDownloadAndInstall()
                                                }) {
                                                    HStack(spacing: 6) {
                                                        Image(systemName: "arrow.down.circle.fill")
                                                        Text("Update to v\(updater.latestVersion) & Restart")
                                                    }
                                                    .font(.system(size: 12, weight: .bold))
                                                    .foregroundColor(Color.black)
                                                    .padding(.horizontal, 16)
                                                    .padding(.vertical, 8)
                                                    .background(TsrossaColors.bitPerfectGold)
                                                    .cornerRadius(12)
                                                    .contentShape(Rectangle())
                                                }
                                                .buttonStyle(TsrossaScaleButtonStyle())
                                            }
                                        }
                                        .padding(10)
                                        .background(vm.currentTheme.primaryContainer.opacity(0.25))
                                        .cornerRadius(12)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12)
                                                .stroke(TsrossaColors.bitPerfectGold.opacity(0.35), lineWidth: 1)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(28)
        }
    }
    
    // =========================================================================
    // ZONE 3: PERSISTENT BOTTOM TRANSPORT BAR (Spotify / Apple Music macOS)
    // =========================================================================
    private var desktopBottomTransportBar: some View {
        HStack(spacing: 16) {
            // 1. LEFT SECTION: Mini Artwork + Track Title + Artist (270px)
            Button(action: { vm.activeTab = .nowPlaying }) {
                HStack(spacing: 12) {
                    ZStack {
                        if let art = vm.currentArtwork {
                            Image(nsImage: art)
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(width: 56, height: 56)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        } else {
                            TsrossaBrandIcon(size: 56, cornerRadius: 12)
                        }
                    }
                    
                    VStack(alignment: .leading, spacing: 3) {
                        Text(engine.currentTrack?.title ?? "No Track Selected")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(vm.currentTheme.textPrimary)
                            .lineLimit(1)
                        
                        Text(engine.currentTrack?.artist ?? "Select a track")
                            .font(.system(size: 11))
                            .foregroundColor(vm.currentTheme.textSecondary)
                            .lineLimit(1)
                        
                        HStack(spacing: 4) {
                            Circle()
                                .fill(TsrossaColors.bitPerfectGold)
                                .frame(width: 5, height: 5)
                            Text("\(String(format: "%.1f kHz", Double(engine.currentTrack?.sampleRate ?? 44100) / 1000.0)) • \(engine.currentTrack?.bitDepth ?? 16)-BIT")
                                .font(.system(size: 9, weight: .bold, design: .monospaced))
                                .foregroundColor(vm.currentTheme.primary)
                        }
                    }
                    
                    Spacer(minLength: 0)
                    
                    if let cur = engine.currentTrack {
                        let isCurLiked = vm.isTrackLiked(cur.path)
                        Button(action: { vm.toggleLike(track: cur) }) {
                            Image(systemName: isCurLiked ? "heart.fill" : "heart")
                                .font(.system(size: 15))
                                .foregroundColor(isCurLiked ? vm.currentTheme.primary : vm.currentTheme.textSecondary)
                                .frame(width: 30, height: 30)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(PlainButtonStyle())
                        .help(isCurLiked ? "Unlike Track (Cmd + L)" : "Like Track (Cmd + L)")
                    }
                }
                .frame(width: 275, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(PlainButtonStyle())
            
            // 2. CENTER SECTION: Transport Buttons + Wide Precision Scrubber
            VStack(spacing: 6) {
                HStack(spacing: 18) {
                    // Shuffle
                    Button(action: { vm.isShuffle.toggle() }) {
                        Image(systemName: "shuffle")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(vm.isShuffle ? vm.currentTheme.primary : vm.currentTheme.textMuted)
                            .frame(width: 30, height: 30)
                            .background(vm.isShuffle ? vm.currentTheme.primary.opacity(0.16) : Color.clear)
                            .clipShape(Circle())
                            .contentShape(Circle())
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())
                    
                    // Previous
                    Button(action: { vm.playPreviousTrack() }) {
                        Image(systemName: "backward.fill")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(vm.currentTheme.textPrimary)
                            .frame(width: 34, height: 34)
                            .background(Color.white.opacity(0.08))
                            .clipShape(Circle())
                            .contentShape(Circle())
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())
                    
                    // Center Play/Pause FAB
                    Button(action: { engine.togglePlayPause() }) {
                        ZStack {
                            Circle()
                                .fill(vm.currentTheme.primary)
                                .frame(width: 42, height: 42)
                                .shadow(color: vm.currentTheme.primary.opacity(0.40), radius: 8, x: 0, y: 3)
                            
                            Image(systemName: engine.isPlaying && !engine.isPaused ? "pause.fill" : "play.fill")
                                .font(.system(size: 17, weight: .bold))
                                .foregroundColor(vm.currentTheme.onPrimary)
                        }
                        .frame(width: 42, height: 42)
                        .contentShape(Circle())
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())
                    
                    // Next
                    Button(action: { vm.playNextTrack() }) {
                        Image(systemName: "forward.fill")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(vm.currentTheme.textPrimary)
                            .frame(width: 34, height: 34)
                            .background(Color.white.opacity(0.08))
                            .clipShape(Circle())
                            .contentShape(Circle())
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())
                    
                    // Repeat
                    Button(action: { vm.isRepeat.toggle() }) {
                        Image(systemName: "repeat")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(vm.isRepeat ? vm.currentTheme.primary : vm.currentTheme.textMuted)
                            .frame(width: 30, height: 30)
                            .background(vm.isRepeat ? vm.currentTheme.primary.opacity(0.16) : Color.clear)
                            .clipShape(Circle())
                            .contentShape(Circle())
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())
                }
                
                // Scrubber Timeline Row
                HStack(spacing: 10) {
                    Text(vm.formatTime(engine.currentTime))
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundColor(vm.currentTheme.textSecondary)
                        .frame(width: 42, alignment: .trailing)
                    
                    Slider(
                        value: Binding(
                            get: { engine.currentTime },
                            set: { newValue in engine.seek(to: newValue) }
                        ),
                        in: 0...max(1.0, engine.totalDuration)
                    )
                    .accentColor(vm.currentTheme.primary)
                    
                    Text(vm.formatTime(engine.totalDuration))
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundColor(vm.currentTheme.textSecondary)
                        .frame(width: 42, alignment: .leading)
                }
                .frame(maxWidth: 520)
            }
            .frame(maxWidth: .infinity)
            
            // 3. RIGHT SECTION: Hardware Attenuation Volume + Queue + Telemetry (260px)
            HStack(spacing: 10) {
                Spacer(minLength: 0)
                
                // Step Attenuator Pill (- / 0 dBFS / +)
                HStack(spacing: 8) {
                    Button(action: { vm.volumeDown() }) {
                        Image(systemName: "minus")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(vm.currentTheme.textPrimary)
                            .frame(width: 26, height: 26)
                            .background(Color.white.opacity(0.10))
                            .clipShape(Circle())
                            .contentShape(Circle())
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())
                    
                    HStack(spacing: 4) {
                        Image(systemName: "speaker.wave.2.fill")
                            .font(.system(size: 11))
                            .foregroundColor(vm.currentTheme.primary)
                        Text("\(vm.volumeDb) dBFS")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundColor(vm.currentTheme.primary)
                    }
                    .frame(width: 74)
                    
                    Button(action: { vm.volumeUp() }) {
                        Image(systemName: "plus")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(vm.currentTheme.textPrimary)
                            .frame(width: 26, height: 26)
                            .background(Color.white.opacity(0.10))
                            .clipShape(Circle())
                            .contentShape(Circle())
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 6)
                .background(vm.currentTheme.primaryContainer.opacity(0.45))
                .cornerRadius(20)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(vm.currentTheme.outline.opacity(0.25), lineWidth: 1)
                )
                
                // Lyrics Quick Switch (YouTube Music style)
                Button(action: {
                    if vm.activeTab != .playlist {
                        vm.activeTab = .playlist
                        vm.queueTabMode = "LYRICS"
                    } else if vm.queueTabMode == "LYRICS" {
                        vm.queueTabMode = "QUEUE"
                    } else {
                        vm.queueTabMode = "LYRICS"
                    }
                }) {
                    Image(systemName: "quote.bubble")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor((vm.activeTab == .playlist && vm.queueTabMode == "LYRICS") ? vm.currentTheme.onPrimary : vm.currentTheme.textPrimary)
                        .frame(width: 34, height: 34)
                        .background((vm.activeTab == .playlist && vm.queueTabMode == "LYRICS") ? vm.currentTheme.primary : vm.currentTheme.surfaceContainerHigh)
                        .clipShape(Circle())
                        .contentShape(Circle())
                }
                .buttonStyle(TsrossaScaleButtonStyle())
                .help("Toggle Synced Lyrics")
                
                // Queue Quick Switch
                Button(action: {
                    if vm.activeTab == .playlist && vm.queueTabMode == "QUEUE" {
                        vm.activeTab = .nowPlaying
                    } else {
                        vm.activeTab = .playlist
                        vm.queueTabMode = "QUEUE"
                    }
                }) {
                    Image(systemName: "music.note.list")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor((vm.activeTab == .playlist && vm.queueTabMode == "QUEUE") ? vm.currentTheme.onPrimary : vm.currentTheme.textPrimary)
                        .frame(width: 34, height: 34)
                        .background((vm.activeTab == .playlist && vm.queueTabMode == "QUEUE") ? vm.currentTheme.primary : vm.currentTheme.surfaceContainerHigh)
                        .clipShape(Circle())
                        .contentShape(Circle())
                }
                .buttonStyle(TsrossaScaleButtonStyle())
                .help("Toggle Play Queue (Cmd + 3)")
                
                // Theater Mode Quick Switch (YouTube Music style Cmd + F)
                Button(action: {
                    withAnimation(.easeInOut(duration: 0.25)) {
                        vm.isTheaterMode.toggle()
                    }
                }) {
                    Image(systemName: vm.isTheaterMode ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(vm.isTheaterMode ? vm.currentTheme.onPrimary : vm.currentTheme.textPrimary)
                        .frame(width: 34, height: 34)
                        .background(vm.isTheaterMode ? vm.currentTheme.primary : vm.currentTheme.surfaceContainerHigh)
                        .clipShape(Circle())
                        .contentShape(Circle())
                }
                .buttonStyle(TsrossaScaleButtonStyle())
                .help("Toggle Theater Fullscreen Mode (Cmd + F)")
            }
            .frame(width: 335, alignment: .trailing)
        }
        .padding(.horizontal, 24)
        .background(vm.currentTheme.surfaceContainer.opacity(0.95))
    }
    
    // =========================================================================
    // REUSABLE SETTINGS COMPONENTS
    // =========================================================================
    private func settingsCard<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title)
                .font(.system(size: 11, weight: .bold, design: .monospaced))
                .foregroundColor(vm.currentTheme.primary)
            
            content()
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(vm.currentTheme.surfaceContainer.opacity(0.90))
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(vm.currentTheme.outline.opacity(0.25), lineWidth: 1)
        )
    }
    
    private func settingsToggleRow(title: String, subtitle: String, isOn: Binding<Bool>) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(vm.currentTheme.textPrimary)
                Text(subtitle)
                    .font(.system(size: 11))
                    .foregroundColor(vm.currentTheme.textSecondary)
            }
            Spacer()
            Toggle("", isOn: isOn)
                .toggleStyle(SwitchToggleStyle(tint: vm.currentTheme.primary))
        }
    }
    
    private func accentPill(title: String, mode: String, color: Color) -> some View {
        let isSelected = vm.accentColorMode == mode
        return Button(action: { vm.setAccentColorMode(mode) }) {
            HStack(spacing: 5) {
                Circle().fill(color).frame(width: 8, height: 8)
                Text(title)
                    .font(.system(size: 11, weight: isSelected ? .bold : .medium))
            }
            .foregroundColor(isSelected ? vm.currentTheme.onPrimary : vm.currentTheme.textSecondary)
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(isSelected ? vm.currentTheme.primary : Color.white.opacity(0.06))
            .cornerRadius(8)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private func bgPill(title: String, mode: String) -> some View {
        let isSelected = vm.bgCanvasMode == mode
        return Button(action: {
            withAnimation(.easeInOut(duration: 0.35)) {
                vm.bgCanvasMode = mode
            }
        }) {
            Text(title)
                .font(.system(size: 11, weight: isSelected ? .bold : .medium))
                .foregroundColor(isSelected ? vm.currentTheme.onPrimary : vm.currentTheme.textSecondary)
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(isSelected ? vm.currentTheme.primary : Color.white.opacity(0.06))
                .cornerRadius(8)
                .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    // =========================================================================
    // HELPERS
    // =========================================================================
    private var activeDeviceName: String {
        if let dev = engine.devices.first(where: { $0.id == engine.selectedDeviceId }) {
            return dev.name
        }
        return engine.devices.first?.name ?? "Apple Silicon CoreAudio DAC"
    }
    
    private var subtitleText: String {
        if let track = engine.currentTrack {
            return "\(track.artist)  •  \(track.album)"
        }
        return "Select a track from your library"
    }
    
    private var formatBadgeText: String {
        if let track = engine.currentTrack {
            let fmt = track.path.lowercased().hasSuffix(".flac") ? "FLAC" : "WAV"
            return "\(fmt)  •  \(String(format: "%.1f", Double(track.sampleRate) / 1000.0)) kHz  •  \(track.bitDepth)-BIT HARDWARE"
        }
        return "FLAC  •  BIT-PERFECT DIRECT"
    }
}
