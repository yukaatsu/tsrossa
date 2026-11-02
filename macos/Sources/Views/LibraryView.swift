import SwiftUI

public struct LibraryView: View {
    @ObservedObject var vm = AppViewModel.shared
    @ObservedObject var engine = AudioEngine.shared
    public var theme: LiquidGlassTheme
    public var playlistsOnlyMode: Bool = false

    public init(theme: LiquidGlassTheme, playlistsOnlyMode: Bool = false) {
        self.theme = theme
        self.playlistsOnlyMode = playlistsOnlyMode
    }

    public var body: some View {
        let tracks = vm.filteredTracks
        let totalDuration = tracks.reduce(0.0) { $0 + $1.durationSeconds }
        let durationText = formatTotalDuration(totalDuration)

        GeometryReader { geo in
            let availableWidth = geo.size.width

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    // Top Header: Title, Track count, and Sort Options
                    HStack(alignment: .firstTextBaseline) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(headerTitle)
                                .font(.system(size: 22, weight: .bold))
                                .foregroundStyle(.primary)

                            Text("\(tracks.count) tracks • \(durationText)")
                                .font(.system(size: 11, design: .monospaced))
                                .foregroundStyle(.secondary)
                        }

                        Spacer()

                        // Sort Order Menu
                        Menu {
                            ForEach(SortField.allCases) { field in
                                Button(action: {
                                    if vm.sortField == field {
                                        vm.sortOrder = (vm.sortOrder == .ascending) ? .descending : .ascending
                                    } else {
                                        vm.sortField = field
                                        vm.sortOrder = .ascending
                                    }
                                }) {
                                    HStack {
                                        Text(field.rawValue)
                                        if vm.sortField == field {
                                            Image(systemName: vm.sortOrder == .ascending ? "chevron.up" : "chevron.down")
                                        }
                                    }
                                }
                            }
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "arrow.up.arrow.down")
                                    .font(.system(size: 11))
                                Text("Sort: \(vm.sortField.rawValue)")
                                    .font(.system(size: 11, weight: .medium))
                                Image(systemName: vm.sortOrder == .ascending ? "chevron.up" : "chevron.down")
                                    .font(.system(size: 9))
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.white.opacity(0.08))
                            .continuousSquircle(8)
                        }
                        .menuStyle(BorderlessButtonMenuStyle())
                    }
                    .padding(.top, 8)

                    // Audiophile Filter Pills Row (Horizontally Scrollable)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            filterChip(title: "ALL", isSelected: vm.selectedFormatFilter == "ALL") {
                                vm.selectedFormatFilter = "ALL"
                            }
                            filterChip(title: "FLAC", isSelected: vm.selectedFormatFilter == "FLAC") {
                                vm.selectedFormatFilter = "FLAC"
                            }
                            filterChip(title: "WAV", isSelected: vm.selectedFormatFilter == "WAV") {
                                vm.selectedFormatFilter = "WAV"
                            }

                            Divider()
                                .frame(height: 16)

                            filterChip(title: "24-bit Hi-Res", isSelected: vm.selectedBitDepthFilter == 24) {
                                vm.selectedBitDepthFilter = (vm.selectedBitDepthFilter == 24) ? 0 : 24
                            }
                            filterChip(title: "192 kHz", isSelected: vm.selectedSampleRateFilter == 192000) {
                                vm.selectedSampleRateFilter = (vm.selectedSampleRateFilter == 192000) ? 0 : 192000
                            }
                            filterChip(title: "96 kHz", isSelected: vm.selectedSampleRateFilter == 96000) {
                                vm.selectedSampleRateFilter = (vm.selectedSampleRateFilter == 96000) ? 0 : 96000
                            }

                            if vm.hasActiveFilters {
                                Button("Reset Filters") {
                                    vm.selectedFormatFilter = "ALL"
                                    vm.selectedBitDepthFilter = 0
                                    vm.selectedSampleRateFilter = 0
                                    vm.searchText = ""
                                }
                                .font(.system(size: 11))
                                .foregroundStyle(theme.primary)
                                .padding(.leading, 4)
                                .contentShape(Rectangle())
                            }
                        }
                        .padding(.vertical, 2)
                    }

                    // Tracks List
                    LazyVStack(spacing: 2) {
                        if tracks.isEmpty {
                            VStack(spacing: 8) {
                                Image(systemName: "music.note")
                                    .font(.system(size: 28))
                                    .foregroundStyle(.secondary)
                                Text("No tracks found")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundStyle(.primary)
                                Text("Try adjusting your filters or choose another music folder.")
                                    .font(.system(size: 12))
                                    .foregroundStyle(.secondary)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 40)
                        } else {
                            ForEach(Array(tracks.enumerated()), id: \.element.id) { index, track in
                                trackRow(track: track, index: index + 1, width: availableWidth)
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 16)
            }
        }
    }

    private var headerTitle: String {
        if let id = vm.selectedPlaylistId {
            if id == "LIKED_SONGS" {
                return "Liked Songs"
            } else if let pl = vm.playlists.first(where: { $0.id == id }) {
                return pl.name
            }
        }
        return "All Tracks"
    }

    private func formatTotalDuration(_ total: Double) -> String {
        let hours = Int(total) / 3600
        let mins = (Int(total) % 3600) / 60
        if hours > 0 {
            return "\(hours) hr \(mins) min"
        }
        return "\(mins) min"
    }

    private func filterChip(title: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 11, weight: isSelected ? .bold : .medium))
                .foregroundStyle(isSelected ? theme.onPrimary : .primary)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(isSelected ? theme.primary : Color.white.opacity(0.08))
                .continuousSquircle(8)
                .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }

    private func trackRow(track: TrackMetadata, index: Int, width: CGFloat) -> some View {
        let isCurrent = engine.currentTrack?.path == track.path
        let isLiked = vm.isLiked(track: track)
        return HStack(spacing: 12) {
            trackIndexView(isCurrent: isCurrent, index: index)
            trackArtworkView(track: track)
            trackInfoView(track: track, isCurrent: isCurrent)
            Spacer()
            if width > 600 {
                Text(track.album)
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .frame(maxWidth: min(180, width * 0.22), alignment: .leading)
            }
            trackBadges(track: track)
            Button(action: { vm.toggleLike(track: track) }) {
                Image(systemName: isLiked ? "heart.fill" : "heart")
                    .font(.system(size: 12))
                    .foregroundStyle(isLiked ? Color.pink : Color.white.opacity(0.3))
                    .frame(width: 24, height: 24)
                    .contentShape(Rectangle())
            }
            .buttonStyle(PlainButtonStyle())

            Text(formatDuration(track.durationSeconds))
                .font(.system(size: 11, design: .monospaced))
                .monospacedDigit()
                .foregroundStyle(.secondary)
                .frame(width: 44, alignment: .trailing)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .background(isCurrent ? theme.primary.opacity(0.12) : Color.white.opacity(0.02))
        .continuousSquircle(10)
        .contentShape(Rectangle())
        .onTapGesture {
            engine.play(track: track)
        }
        .contextMenu {
            Button("Play Next") {
                vm.upNextQueue.insert(track, at: 0)
            }
            Button("Add to Queue") {
                vm.upNextQueue.append(track)
            }
            Divider()
            Button(isLiked ? "Remove from Liked" : "Add to Liked Songs") {
                vm.toggleLike(track: track)
            }
        }
    }

    private func trackIndexView(isCurrent: Bool, index: Int) -> some View {
        ZStack {
            if isCurrent && engine.isPlaying && !engine.isPaused {
                Image(systemName: "speaker.wave.2.fill")
                    .font(.system(size: 11))
                    .foregroundStyle(theme.primary)
            } else {
                Text("\(index)")
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(.secondary)
            }
        }
        .frame(width: 24, alignment: .trailing)
    }

    private func trackArtworkView(track: TrackMetadata) -> some View {
        Group {
            if let img = track.artwork {
                Image(nsImage: img)
                    .resizable()
                    .scaledToFill()
            } else {
                ZStack {
                    Color.white.opacity(0.06)
                    Image(systemName: "music.note")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
            }
        }
        .frame(width: 34, height: 34)
        .continuousSquircle(6)
    }

    private func trackInfoView(track: TrackMetadata, isCurrent: Bool) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(track.title)
                .font(.system(size: 13, weight: isCurrent ? .bold : .regular))
                .foregroundStyle(isCurrent ? theme.primary : .primary)
                .lineLimit(1)

            Text(track.artist)
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(minWidth: 140, alignment: .leading)
    }

    private func trackBadges(track: TrackMetadata) -> some View {
        HStack(spacing: 4) {
            Text("\(track.sampleRate / 1000)k")
                .font(.system(size: 9, weight: .bold, design: .monospaced))
                .padding(.horizontal, 4)
                .padding(.vertical, 2)
                .background(Color.white.opacity(0.06))
                .continuousSquircle(4)

            Text("\(track.bitDepth)b")
                .font(.system(size: 9, weight: .bold, design: .monospaced))
                .padding(.horizontal, 4)
                .padding(.vertical, 2)
                .background(Color.white.opacity(0.06))
                .continuousSquircle(4)
        }
    }

    private func formatDuration(_ seconds: Double) -> String {
        guard seconds.isFinite && seconds > 0 else { return "0:00" }
        let totalSec = Int(seconds.rounded(.down))
        let mins = totalSec / 60
        let secs = totalSec % 60
        return String(format: "%d:%02d", mins, secs)
    }
}
