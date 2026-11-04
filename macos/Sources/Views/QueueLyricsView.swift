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
        VStack(spacing: 10) {
            // Segmented Header
            headerSwitcher

            // Content Area
            ZStack {
                if vm.queueTab == .upNext {
                    queueList
                        .transition(.asymmetric(
                            insertion: .move(edge: .leading).combined(with: .opacity),
                            removal: .move(edge: .trailing).combined(with: .opacity)
                        ))
                } else {
                    lyricsView
                        .transition(.asymmetric(
                            insertion: .move(edge: .trailing).combined(with: .opacity),
                            removal: .move(edge: .leading).combined(with: .opacity)
                        ))
                }
            }
            .animation(.spring(response: 0.34, dampingFraction: 0.86), value: vm.queueTab)
            .clipped()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(vm.queueTab == .lyrics ? Color.white.opacity(0.015) : Color.white.opacity(0.035))
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .strokeBorder(
                            LinearGradient(
                                colors: [
                                    Color.white.opacity(vm.queueTab == .lyrics ? 0.08 : 0.14),
                                    Color.white.opacity(0.02)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1
                        )
                )
        }
        .continuousSquircle(20)
        .background(
            GeometryReader { geo in
                Color.clear
                    .onAppear {
                        NowPlayingDrawerState.shared.queuePanelFrameInWindow = geo.frame(in: .named("MainWindowCoordSpace"))
                    }
                    .onChange(of: geo.frame(in: .named("MainWindowCoordSpace"))) { newFrame in
                        NowPlayingDrawerState.shared.queuePanelFrameInWindow = newFrame
                    }
            }
        )
    }

    // MARK: - Header Switcher
    private var headerSwitcher: some View {
        HStack(spacing: 8) {
            HStack(spacing: 4) {
                tabButton(title: "Up Next", icon: "list.bullet", tab: .upNext)
                tabButton(title: "Lyrics", icon: "quote.bubble.fill", tab: .lyrics)
            }
            .padding(3)
            .background(Color.white.opacity(0.06))
            .continuousSquircle(9)

            Spacer()

            if vm.queueTab == .upNext && !vm.upNextQueue.isEmpty {
                Button(action: {
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
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
                .buttonStyle(TsrossaScaleButtonStyle())
            } else if vm.queueTab == .lyrics {
                if let curr = engine.currentTrack {
                    Button(action: {
                        vm.loadLyrics(for: curr)
                    }) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                            .frame(width: 26, height: 26)
                            .background(Color.white.opacity(0.06))
                            .clipShape(Circle())
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())
                    .help("Reload Lyrics")
                }
            }
        }
    }

    private func tabButton(title: String, icon: String, tab: QueuePanelTab) -> some View {
        let isSelected = vm.queueTab == tab
        return Button(action: {
            withAnimation(.spring(response: 0.32, dampingFraction: 0.82)) {
                vm.queueTab = tab
            }
        }) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 10.5, weight: .semibold))
                Text(title)
                    .font(.system(size: 11.5, weight: isSelected ? .bold : .medium))
            }
            .foregroundStyle(isSelected ? theme.onPrimary : .secondary)
            .padding(.horizontal, 11)
            .padding(.vertical, 5)
            .background {
                if isSelected {
                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                        .fill(theme.primary)
                        .matchedGeometryEffect(id: "queueTabPill", in: queueTabNamespace)
                        .shadow(color: theme.primary.opacity(0.35), radius: 6, x: 0, y: 1)
                }
            }
            .continuousSquircle(7)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }

    // MARK: - Queue List
    private var queueList: some View {
        ScrollView {
            LazyVStack(spacing: 8) {
                // Now Playing Strip
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

    // MARK: - Now Playing Strip
    private var nowPlayingCard: some View {
        Group {
            if let curr = engine.currentTrack {
                HStack(spacing: 10) {
                    // Artwork Thumbnail
                    Group {
                        if let art = curr.artwork {
                            Image(nsImage: art)
                                .resizable()
                                .scaledToFill()
                        } else {
                            ZStack {
                                Color.white.opacity(0.08)
                                Image(systemName: "music.note")
                                    .font(.system(size: 12))
                                    .foregroundStyle(theme.primary)
                            }
                        }
                    }
                    .frame(width: 32, height: 32)
                    .continuousSquircle(7)

                    VStack(alignment: .leading, spacing: 1) {
                        Text(curr.title)
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                        Text(curr.artist)
                            .font(.system(size: 10.5))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }

                    Spacer()

                    if engine.isPlaying && !engine.isPaused {
                        HStack(spacing: 2) {
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
                        .padding(.trailing, 4)
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color.white.opacity(0.04))
                .continuousSquircle(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .strokeBorder(theme.primary.opacity(0.20), lineWidth: 1)
                )
            } else {
                HStack(spacing: 10) {
                    Image(systemName: "play.circle.fill")
                        .font(.system(size: 20))
                        .foregroundStyle(theme.primary)
                    VStack(alignment: .leading, spacing: 1) {
                        Text("No Track Playing")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(.primary)
                        Text("Select a song to start playback")
                            .font(.system(size: 10))
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color.white.opacity(0.03))
                .continuousSquircle(8)
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
                if let art = track.listThumbnail ?? track.artwork {
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
            .buttonStyle(TsrossaScaleButtonStyle())
            .help("Play Now")

            // Remove button
            Button(action: {
                if index < vm.upNextQueue.count {
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                        _ = vm.upNextQueue.remove(at: index)
                    }
                }
            }) {
                Image(systemName: "xmark")
                    .font(.system(size: 10))
                    .foregroundStyle(.secondary)
                    .frame(width: 22, height: 22)
            }
            .buttonStyle(TsrossaScaleButtonStyle())
            .help("Remove from Queue")
        }
        .padding(6)
        .background(Color.white.opacity(0.04))
        .continuousSquircle(8)
    }

    private func upcomingTrackRow(track: TrackMetadata) -> some View {
        return Button(action: {
            engine.play(track: track)
        }) {
            HStack(spacing: 8) {
                // Mini artwork
                Group {
                    if let art = track.listThumbnail ?? track.artwork {
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

    // MARK: - Beautiful Lyrics View (Apple Music Sing / Spicetify Style)
    private var lyricsView: some View {
        ScrollViewReader { proxy in
            ScrollView(showsIndicators: false) {
                if vm.isLoadingLyrics {
                    VStack(spacing: 12) {
                        ProgressView()
                            .scaleEffect(0.9)
                        Text("Loading lyrics...")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 80)
                } else if vm.currentLyrics.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "quote.bubble")
                            .font(.system(size: 32, weight: .light))
                            .foregroundStyle(theme.primary.opacity(0.6))
                        Text("No Lyrics Available")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(.primary)
                        Text("Embedded FLAC/ID3 tags or companion .lrc file not found for this track.")
                            .font(.system(size: 11.5))
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 80)
                } else {
                    LazyVStack(alignment: .leading, spacing: 18) {
                        // Vertical Stage Top Spacer
                        Spacer()
                            .frame(height: 100)

                        // Intro musical dots if playing before first lyric line
                        if let first = vm.currentLyrics.first,
                           first.isSynced,
                           engine.isPlaying && !engine.isPaused,
                           engine.progress.currentTime < first.timestampSeconds,
                           vm.currentLyricIndex < 0 {
                            HStack(spacing: 6) {
                                Circle().fill(theme.primary.opacity(0.8)).frame(width: 6, height: 6)
                                Circle().fill(theme.primary.opacity(0.5)).frame(width: 6, height: 6)
                                Circle().fill(theme.primary.opacity(0.3)).frame(width: 6, height: 6)
                            }
                            .padding(.leading, 10)
                            .padding(.bottom, 6)
                            .transition(.opacity)
                        }

                        ForEach(Array(vm.currentLyrics.enumerated()), id: \.element.id) { idx, line in
                            beautifulLyricLine(line: line, index: idx, activeIndex: vm.currentLyricIndex)
                                .id(idx)
                        }

                        // Vertical Stage Bottom Spacer
                        Spacer()
                            .frame(height: 120)
                    }
                    .padding(.horizontal, 10)
                    .mask(
                        LinearGradient(
                            stops: [
                                .init(color: .clear, location: 0.0),
                                .init(color: .black, location: 0.08),
                                .init(color: .black, location: 0.90),
                                .init(color: .clear, location: 1.0)
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .onChange(of: vm.currentLyricIndex) { newIdx in
                        if newIdx >= 0 {
                            withAnimation(.spring(response: 0.44, dampingFraction: 0.82)) {
                                proxy.scrollTo(newIdx, anchor: .center)
                            }
                        }
                    }
                    .onAppear {
                        if vm.currentLyricIndex >= 0 {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                                withAnimation(.spring(response: 0.44, dampingFraction: 0.82)) {
                                    proxy.scrollTo(vm.currentLyricIndex, anchor: .center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private func beautifulLyricLine(line: LyricLine, index: Int, activeIndex: Int) -> some View {
        let isActive = (activeIndex == index)
        let isPast = (activeIndex >= 0 && index < activeIndex)
        let distance = activeIndex >= 0 ? abs(index - activeIndex) : 0
        let isHovered = (vm.hoveredLyricIndex == index)

        // Dynamic scale hierarchy: active 1.0, gracefully tapering down for synced lines; uniform 0.96 for unsynced
        let targetScale: CGFloat = {
            if !line.isSynced { return isHovered ? 1.0 : 0.96 }
            if isActive { return 1.0 }
            if isHovered { return 0.96 }
            switch distance {
            case 1: return 0.93
            case 2: return 0.89
            default: return 0.86
            }
        }()

        // Opacity cascade: glowing active 1.0, tapering with distance, crisp on hover; uniform 0.88 for unsynced
        let targetOpacity: Double = {
            if !line.isSynced { return isHovered ? 1.0 : 0.88 }
            if isActive { return 1.0 }
            if isHovered { return 0.92 }
            guard activeIndex >= 0 else {
                return 0.85 // Neutral state when idle before first timestamp
            }
            switch distance {
            case 1: return isPast ? 0.42 : 0.56
            case 2: return isPast ? 0.26 : 0.36
            case 3: return isPast ? 0.16 : 0.22
            default: return max(0.10, 0.18 - Double(distance - 3) * 0.025)
            }
        }()

        // Optical Gaussian blur cascade (always 0 for unsynced lines)
        let targetBlur: CGFloat = {
            if !line.isSynced || isActive || isHovered || activeIndex < 0 { return 0 }
            switch distance {
            case 1: return 0.5
            case 2: return 1.3
            case 3: return 2.2
            default: return min(3.6, 2.2 + CGFloat(distance - 3) * 0.45)
            }
        }()

        return Button(action: {
            if line.isSynced && line.timestampSeconds >= 0 {
                AudioEngine.shared.seek(to: line.timestampSeconds)
            }
        }) {
            Text(line.text)
                .font(.system(size: 22, weight: .bold, design: .rounded))
                .foregroundStyle(isActive ? Color.white : (line.isSynced ? Color.white : Color.primary))
                .opacity(targetOpacity)
                .scaleEffect(targetScale, anchor: .leading)
                .blur(radius: targetBlur)
                // Dual Vocal Glow Shadow for the active sung line
                .shadow(
                    color: isActive ? theme.primary.opacity(0.60) : .clear,
                    radius: isActive ? 16 : 0,
                    x: 0,
                    y: 0
                )
                .shadow(
                    color: isActive ? Color.white.opacity(0.28) : .clear,
                    radius: isActive ? 6 : 0,
                    x: 0,
                    y: 0
                )
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(isHovered && !isActive ? Color.white.opacity(0.06) : Color.clear)
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
        .onHover { isHover in
            withAnimation(.easeOut(duration: 0.15)) {
                vm.hoveredLyricIndex = isHover ? index : nil
            }
        }
        .animation(.spring(response: 0.40, dampingFraction: 0.82), value: isActive)
        .animation(.easeOut(duration: 0.15), value: isHovered)
    }

    private func formatDuration(_ seconds: Double) -> String {
        guard seconds.isFinite && seconds > 0 else { return "0:00" }
        let total = Int(seconds.rounded(.down))
        let m = total / 60
        let s = total % 60
        return String(format: "%d:%02d", m, s)
    }
}
