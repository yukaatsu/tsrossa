import SwiftUI

public struct QueueLyricsView: View {
    @ObservedObject var vm = AppViewModel.shared
    @ObservedObject var engine = AudioEngine.shared
    public var theme: LiquidGlassTheme
    @Namespace private var queueTabNamespace

    public init(theme: LiquidGlassTheme) {
        self.theme = theme
    }

    public var body: some View {
        VStack(spacing: 12) {
            // Segmented Header
            HStack(spacing: 8) {
                tabButton(title: "Up Next", tab: .upNext)
                tabButton(title: "Lyrics", tab: .lyrics)
                Spacer()

                if vm.queueTab == .upNext && !vm.upNextQueue.isEmpty {
                    Button(action: {
                        withAnimation {
                            vm.upNextQueue.removeAll()
                        }
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "trash")
                                .font(.system(size: 10))
                            Text("Clear Queue")
                                .font(.system(size: 11, weight: .medium))
                        }
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.white.opacity(0.06))
                        .continuousSquircle(6)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            .padding(.top, 4)

            // Content Area
            if vm.queueTab == .upNext {
                queueList
            } else {
                lyricsView
            }
        }
        .padding(16)
        .liquidGlass(role: .bentoCard, cornerRadius: 20, tint: theme.primary)
    }

    private func tabButton(title: String, tab: QueuePanelTab) -> some View {
        let isSelected = vm.queueTab == tab
        return Button(action: {
            withAnimation(.spring(response: 0.32, dampingFraction: 0.8)) {
                vm.queueTab = tab
            }
        }) {
            ZStack {
                if isSelected {
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(theme.primary)
                        .matchedGeometryEffect(id: "queueTabPill", in: queueTabNamespace)
                }
                Text(title)
                    .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                    .foregroundStyle(isSelected ? theme.onPrimary : .secondary)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
            }
            .background(isSelected ? Color.clear : Color.white.opacity(0.06))
            .continuousSquircle(8)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }

    // MARK: - Queue List
    private var queueList: some View {
        ScrollView {
            LazyVStack(spacing: 8) {
                // Now Playing Hero Card
                nowPlayingCard

                // Manual / Priority Queue items
                if !vm.upNextQueue.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("MANUAL QUEUE")
                                .font(.system(size: 10, weight: .bold, design: .monospaced))
                                .foregroundStyle(theme.primary)
                            Text("(\(vm.upNextQueue.count))")
                                .font(.system(size: 10, design: .monospaced))
                                .foregroundStyle(.secondary)
                            Spacer()
                        }
                        .padding(.top, 4)
                        .padding(.horizontal, 2)

                        ForEach(Array(vm.upNextQueue.enumerated()), id: \.offset) { idx, track in
                            manualQueueRow(track: track, index: idx)
                        }
                    }
                }

                // Library upcoming items (Guaranteed NEVER empty)
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("UP NEXT FROM LIBRARY")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundStyle(.secondary)
                        Spacer()
                    }
                    .padding(.top, 8)
                    .padding(.horizontal, 2)

                    let upcoming = upcomingLibraryTracks
                    if upcoming.isEmpty {
                        HStack {
                            Spacer()
                            Text("No tracks available in library")
                                .font(.system(size: 12))
                                .foregroundStyle(.secondary)
                                .padding(.vertical, 16)
                            Spacer()
                        }
                    } else {
                        ForEach(upcoming, id: \.id) { track in
                            upcomingTrackRow(track: track)
                        }
                    }
                }
            }
            .padding(.trailing, 2)
        }
    }

    // MARK: - Now Playing Card
    private var nowPlayingCard: some View {
        Group {
            if let curr = engine.currentTrack {
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("NOW PLAYING")
                            .font(.system(size: 9.5, weight: .bold, design: .monospaced))
                            .foregroundStyle(theme.primary)
                        Spacer()
                        if engine.isPlaying && !engine.isPaused {
                            HStack(spacing: 2.5) {
                                RoundedRectangle(cornerRadius: 1)
                                    .fill(theme.primary)
                                    .frame(width: 2.5, height: 10)
                                RoundedRectangle(cornerRadius: 1)
                                    .fill(theme.primary)
                                    .frame(width: 2.5, height: 14)
                                RoundedRectangle(cornerRadius: 1)
                                    .fill(theme.primary)
                                    .frame(width: 2.5, height: 8)
                            }
                        }
                    }

                    HStack(spacing: 10) {
                        // Artwork Thumbnail
                        Group {
                            if let art = curr.artwork {
                                Image(nsImage: art)
                                    .resizable()
                                    .scaledToFit()
                            } else {
                                ZStack {
                                    Color.white.opacity(0.08)
                                    Image(systemName: "music.note")
                                        .font(.system(size: 14))
                                        .foregroundStyle(theme.primary)
                                }
                            }
                        }
                        .frame(width: 38, height: 38)
                        .continuousSquircle(8)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(curr.title)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(.primary)
                                .lineLimit(1)
                            Text(curr.artist)
                                .font(.system(size: 11))
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }

                        Spacer()

                        Button(action: {
                            engine.togglePlayPause()
                        }) {
                            Image(systemName: engine.isPlaying && !engine.isPaused ? "pause.fill" : "play.fill")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(theme.onPrimary)
                                .frame(width: 30, height: 30)
                                .background(theme.primary)
                                .clipShape(Circle())
                        }
                        .buttonStyle(PlainButtonStyle())
                        .help("Play / Pause")
                    }
                }
                .padding(10)
                .background(Color.white.opacity(0.05))
                .continuousSquircle(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .strokeBorder(theme.primary.opacity(0.25), lineWidth: 1)
                )
            } else {
                HStack(spacing: 10) {
                    Image(systemName: "play.circle.fill")
                        .font(.system(size: 24))
                        .foregroundStyle(theme.primary)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("No Track Playing")
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(.primary)
                        Text("Choose a track below or press Play to begin")
                            .font(.system(size: 10.5))
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button("Play First") {
                        vm.playSelectedOrHighlightedTrack()
                    }
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(theme.onPrimary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(theme.primary)
                    .continuousSquircle(8)
                    .buttonStyle(PlainButtonStyle())
                }
                .padding(10)
                .background(Color.white.opacity(0.03))
                .continuousSquircle(10)
            }
        }
    }

    // MARK: - Upcoming Library Tracks Computation
    private var upcomingLibraryTracks: [TrackMetadata] {
        let list = vm.filteredTracks
        guard !list.isEmpty else { return [] }

        if let curr = engine.currentTrack, let currIdx = list.firstIndex(where: { $0.path == curr.path }) {
            let remaining = Array(list.dropFirst(currIdx + 1))
            if remaining.count < 25 {
                let wrapCount = min(list.count - remaining.count, 25 - remaining.count)
                let wrapped = wrapCount > 0 ? Array(list.prefix(wrapCount)) : []
                return remaining + wrapped
            } else {
                return Array(remaining.prefix(35))
            }
        } else {
            return Array(list.prefix(35))
        }
    }

    // MARK: - Rows
    private func manualQueueRow(track: TrackMetadata, index: Int) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "line.3.horizontal")
                .font(.system(size: 10))
                .foregroundStyle(.tertiary)

            // Artwork
            Group {
                if let art = track.artwork {
                    Image(nsImage: art)
                        .resizable()
                        .scaledToFit()
                } else {
                    ZStack {
                        Color.white.opacity(0.06)
                        Image(systemName: "music.note")
                            .font(.system(size: 10))
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .frame(width: 30, height: 30)
            .continuousSquircle(6)

            VStack(alignment: .leading, spacing: 2) {
                Text(track.title)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                Text(track.artist)
                    .font(.system(size: 10))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }

            Spacer()

            // Play now button
            Button(action: {
                let trackToPlay = track
                if index < vm.upNextQueue.count {
                    vm.upNextQueue.remove(at: index)
                }
                engine.play(track: trackToPlay)
            }) {
                Image(systemName: "play.fill")
                    .font(.system(size: 10))
                    .foregroundStyle(theme.primary)
                    .frame(width: 24, height: 24)
                    .background(Color.white.opacity(0.06))
                    .clipShape(Circle())
            }
            .buttonStyle(PlainButtonStyle())
            .help("Play Now")

            // Remove button
            Button(action: {
                if index < vm.upNextQueue.count {
                    withAnimation {
                        _ = vm.upNextQueue.remove(at: index)
                    }
                }
            }) {
                Image(systemName: "xmark")
                    .font(.system(size: 10))
                    .foregroundStyle(.secondary)
                    .frame(width: 22, height: 22)
            }
            .buttonStyle(PlainButtonStyle())
            .help("Remove from Queue")
        }
        .padding(6)
        .background(Color.white.opacity(0.04))
        .continuousSquircle(8)
    }

    private func upcomingTrackRow(track: TrackMetadata) -> some View {
        Button(action: {
            engine.play(track: track)
        }) {
            HStack(spacing: 8) {
                // Mini artwork
                Group {
                    if let art = track.artwork {
                        Image(nsImage: art)
                            .resizable()
                            .scaledToFit()
                    } else {
                        ZStack {
                            Color.white.opacity(0.06)
                            Image(systemName: "music.note")
                                .font(.system(size: 10))
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .frame(width: 30, height: 30)
                .continuousSquircle(6)

                VStack(alignment: .leading, spacing: 2) {
                    Text(track.title)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    Text(track.artist)
                        .font(.system(size: 10))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Spacer()

                // Sample rate badge
                Text("\(track.sampleRate / 1000)k")
                    .font(.system(size: 9.5, weight: .semibold, design: .monospaced))
                    .foregroundStyle(.tertiary)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(Color.white.opacity(0.04))
                    .continuousSquircle(4)

                // Duration
                Text(formatDuration(track.durationSeconds))
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundStyle(.secondary)
                    .frame(width: 36, alignment: .trailing)

                Image(systemName: "play.circle")
                    .font(.system(size: 12))
                    .foregroundStyle(theme.primary.opacity(0.75))
            }
            .padding(6)
            .background(Color.white.opacity(0.025))
            .continuousSquircle(8)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }

    // MARK: - Lyrics View (Apple Music Style Click-to-Seek)
    private var lyricsView: some View {
        ScrollViewReader { proxy in
            ScrollView {
                if vm.isLoadingLyrics {
                    VStack(spacing: 12) {
                        ProgressView()
                            .scaleEffect(0.9)
                        Text("Loading lyrics...")
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 50)
                } else if vm.currentLyrics.isEmpty {
                    VStack(spacing: 10) {
                        Image(systemName: "quote.bubble")
                            .font(.system(size: 28))
                            .foregroundStyle(.secondary)
                        Text("No Lyrics Available")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(.primary)
                        Text("Embedded FLAC/ID3 tags or .lrc file not found for this track.")
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 50)
                } else {
                    LazyVStack(spacing: 8) {
                        ForEach(Array(vm.currentLyrics.enumerated()), id: \.offset) { idx, line in
                            let isCurrent = idx == vm.currentLyricIndex
                            lyricLineRow(line: line, index: idx, isCurrent: isCurrent)
                        }
                    }
                    .padding(.vertical, 16)
                    .onChange(of: vm.currentLyricIndex) { newIdx in
                        if newIdx >= 0 {
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                                proxy.scrollTo(newIdx, anchor: .center)
                            }
                        }
                    }
                }
            }
        }
    }

    private func lyricLineRow(line: LyricLine, index: Int, isCurrent: Bool) -> some View {
        Button(action: {
            if line.isSynced && line.timestampSeconds >= 0 {
                engine.seek(to: line.timestampSeconds)
            }
        }) {
            HStack(alignment: .center, spacing: 8) {
                if isCurrent {
                    Capsule()
                        .fill(theme.primary)
                        .frame(width: 3.5, height: 18)
                }

                Text(line.text)
                    .font(.system(size: isCurrent ? 16.5 : 13.5, weight: isCurrent ? .bold : .medium))
                    .foregroundStyle(isCurrent ? theme.primary : (line.isSynced ? Color.white.opacity(0.82) : Color.secondary))
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)

                Spacer()

                if line.isSynced && line.timestampSeconds > 0 {
                    Text(formatDuration(line.timestampSeconds))
                        .font(.system(size: 9.5, design: .monospaced))
                        .foregroundStyle(isCurrent ? theme.primary.opacity(0.85) : Color.white.opacity(0.28))
                }
            }
            .padding(.vertical, isCurrent ? 8 : 4)
            .padding(.horizontal, 10)
            .background(isCurrent ? theme.primary.opacity(0.12) : Color.clear)
            .continuousSquircle(8)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
        .id(index)
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: isCurrent)
    }

    private func formatDuration(_ seconds: Double) -> String {
        guard seconds.isFinite && seconds > 0 else { return "0:00" }
        let total = Int(seconds.rounded(.down))
        let m = total / 60
        let s = total % 60
        return String(format: "%d:%02d", m, s)
    }
}
