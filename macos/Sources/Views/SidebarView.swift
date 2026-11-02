import SwiftUI

public struct SidebarView: View {
    @ObservedObject var vm = AppViewModel.shared
    public var theme: LiquidGlassTheme

    public init(theme: LiquidGlassTheme) {
        self.theme = theme
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // App Brand Logo & Name
            HStack(spacing: 12) {
                TsrossaBrandIcon(size: 32, cornerRadius: 8)
                VStack(alignment: .leading, spacing: 1) {
                    Text("TSRossa")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.primary)
                    Text("Hi-Res Studio Player")
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }
            .contentShape(Rectangle())
            .onTapGesture(count: 2) {
                WindowZoomManager.shared.toggleFitToScreen()
            }
            .padding(.horizontal, 16)
            .padding(.top, 38)
            .padding(.bottom, 18)

            // Primary Navigation Items
            VStack(spacing: 4) {
                sidebarNavItem(tab: .nowPlaying, title: "Now Playing", icon: "play.circle.fill")
                sidebarNavItem(tab: .library, title: "Music Library", icon: "music.note.list")
                sidebarNavItem(tab: .playlist, title: "Playlists", icon: "folder.fill")
                sidebarNavItem(tab: .settings, title: "Settings & DAC", icon: "slider.horizontal.3")
            }
            .padding(.horizontal, 10)

            Divider()
                .padding(.vertical, 14)
                .padding(.horizontal, 16)

            // Playlists Section Header
            HStack {
                Text("PLAYLISTS")
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundStyle(.secondary)
                Spacer()
                Button(action: { vm.openCreatePlaylistModal() }) {
                    Image(systemName: "plus")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(PlainButtonStyle())
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 8)

            // Playlists List
            ScrollView {
                VStack(spacing: 2) {
                    // Liked Songs Smart Playlist
                    Button(action: {
                        vm.selectedPlaylistId = "LIKED_SONGS"
                        vm.activeTab = .library
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "heart.fill")
                                .font(.system(size: 12))
                                .foregroundStyle(Color.pink)
                            Text("Liked Songs")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundStyle(.primary)
                            Spacer()
                            Text("\(vm.likedTrackPaths.count)")
                                .font(.system(size: 10, design: .monospaced))
                                .foregroundStyle(.secondary)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(vm.selectedPlaylistId == "LIKED_SONGS" ? theme.primary.opacity(0.18) : Color.clear)
                        .continuousSquircle(8)
                    }
                    .buttonStyle(PlainButtonStyle())

                    ForEach(vm.playlists) { pl in
                        playlistRow(pl)
                    }
                }
                .padding(.horizontal, 10)
            }

            Spacer()

            // Diagnostics Bar (Bottom Sidebar)
            Button(action: { vm.showTelemetry.toggle() }) {
                HStack(spacing: 8) {
                    Image(systemName: "waveform.path.ecg")
                        .font(.system(size: 12))
                        .foregroundStyle(theme.primary)
                    Text("Audio Diagnostics")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(.primary)
                    Spacer()
                    Text("⌘U")
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.white.opacity(0.06))
                .continuousSquircle(8)
            }
            .buttonStyle(PlainButtonStyle())
            .padding(14)
        }
        .frame(width: 236)
        .frame(maxHeight: .infinity)
        .background(theme.surface.opacity(0.70))
    }

    private func sidebarNavItem(tab: AppTab, title: String, icon: String) -> some View {
        let isSelected = vm.activeTab == tab
        return Button(action: {
            vm.activeTab = tab
            if tab == .library {
                vm.selectedPlaylistId = nil
            }
        }) {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.system(size: 14))
                    .foregroundStyle(isSelected ? theme.primary : .secondary)
                    .frame(width: 20)

                Text(title)
                    .font(.system(size: 13, weight: isSelected ? .semibold : .regular))
                    .foregroundStyle(isSelected ? .primary : .secondary)

                Spacer()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(isSelected ? theme.primary.opacity(0.18) : Color.clear)
            .continuousSquircle(10)
        }
        .buttonStyle(PlainButtonStyle())
    }

    private func playlistRow(_ pl: UserPlaylist) -> some View {
        let isSelected = vm.selectedPlaylistId == pl.id
        return Button(action: {
            vm.selectedPlaylistId = pl.id
            vm.activeTab = .library
        }) {
            HStack(spacing: 8) {
                if pl.isPinned {
                    Image(systemName: "pin.fill")
                        .font(.system(size: 10))
                        .foregroundStyle(theme.primary)
                } else if pl.isAutoFolder {
                    Image(systemName: "folder")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                } else {
                    Image(systemName: "music.note.list")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                }

                Text(pl.name)
                    .font(.system(size: 12))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Spacer()

                let count = pl.trackPaths.count
                if count > 0 {
                    Text("\(count)")
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(isSelected ? theme.primary.opacity(0.18) : Color.clear)
            .continuousSquircle(8)
        }
        .buttonStyle(PlainButtonStyle())
        .contextMenu {
            Button(pl.isPinned ? "Unpin Playlist" : "Pin to Top") {
                vm.togglePinPlaylist(id: pl.id)
            }
            Button("Clear Tracks") {
                vm.clearPlaylistTracks(id: pl.id)
            }
            Divider()
            Button("Delete Playlist", role: .destructive) {
                vm.deletePlaylist(id: pl.id)
            }
        }
    }
}
