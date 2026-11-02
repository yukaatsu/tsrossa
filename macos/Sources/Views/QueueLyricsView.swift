import SwiftUI

public struct QueueLyricsView: View {
    @ObservedObject var vm = AppViewModel.shared
    @ObservedObject var engine = AudioEngine.shared
    public var theme: LiquidGlassTheme

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
                    Button("Clear Queue") {
                        vm.upNextQueue.removeAll()
                    }
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
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
        return Button(action: { vm.queueTab = tab }) {
            Text(title)
                .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                .foregroundStyle(isSelected ? theme.onPrimary : .secondary)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isSelected ? theme.primary : Color.white.opacity(0.06))
                .continuousSquircle(8)
        }
        .buttonStyle(PlainButtonStyle())
    }

    private var queueList: some View {
        ScrollView {
            LazyVStack(spacing: 4) {
                // Priority Queue items
                if !vm.upNextQueue.isEmpty {
                    HStack {
                        Text("MANUAL QUEUE")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundStyle(.secondary)
                        Spacer()
                    }
                    .padding(.top, 4)

                    ForEach(Array(vm.upNextQueue.enumerated()), id: \.offset) { idx, track in
                        HStack(spacing: 8) {
                            Image(systemName: "line.3.horizontal")
                                .font(.system(size: 11))
                                .foregroundStyle(.tertiary)

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

                            Button(action: { vm.upNextQueue.remove(at: idx) }) {
                                Image(systemName: "xmark")
                                    .font(.system(size: 10))
                                    .foregroundStyle(.secondary)
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                        .padding(8)
                        .background(Color.white.opacity(0.04))
                        .continuousSquircle(8)
                    }
                }

                // Library upcoming items
                HStack {
                    Text("PLAYING FROM LIBRARY")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundStyle(.secondary)
                    Spacer()
                }
                .padding(.top, 8)

                let list = vm.filteredTracks
                if let curr = engine.currentTrack, let currIdx = list.firstIndex(where: { $0.path == curr.path }) {
                    let upcoming = list.dropFirst(currIdx + 1).prefix(20)
                    ForEach(Array(upcoming), id: \.id) { track in
                        Button(action: { engine.play(track: track) }) {
                            HStack(spacing: 8) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(track.title)
                                        .font(.system(size: 12))
                                        .foregroundStyle(.primary)
                                        .lineLimit(1)
                                    Text(track.artist)
                                        .font(.system(size: 10))
                                        .foregroundStyle(.secondary)
                                        .lineLimit(1)
                                }
                                Spacer()
                                Text("\(track.sampleRate / 1000)k")
                                    .font(.system(size: 9, design: .monospaced))
                                    .foregroundStyle(.tertiary)
                            }
                            .padding(8)
                            .background(Color.white.opacity(0.02))
                            .continuousSquircle(8)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
            }
        }
    }

    private var lyricsView: some View {
        ScrollViewReader { proxy in
            ScrollView {
                if vm.currentLyrics.isEmpty {
                    VStack(spacing: 8) {
                        Image(systemName: "quote.bubble")
                            .font(.system(size: 24))
                            .foregroundStyle(.secondary)
                        Text("No lyrics available")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(.primary)
                        Text("Embedded FLAC/ID3 tags or .lrc file not found.")
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 40)
                } else {
                    LazyVStack(spacing: 12) {
                        ForEach(Array(vm.currentLyrics.enumerated()), id: \.offset) { idx, line in
                            let isCurrent = idx == vm.currentLyricIndex
                            Text(line.text)
                                .font(.system(size: isCurrent ? 16 : 13, weight: isCurrent ? .bold : .regular))
                                .foregroundStyle(isCurrent ? theme.primary : .secondary)
                                .multilineTextAlignment(.center)
                                .frame(maxWidth: .infinity)
                                .id(idx)
                                .animation(.spring(response: 0.3, dampingFraction: 0.8), value: isCurrent)
                        }
                    }
                    .padding(.vertical, 16)
                    .onChange(of: vm.currentLyricIndex) { newIdx in
                        withAnimation {
                            proxy.scrollTo(newIdx, anchor: .center)
                        }
                    }
                }
            }
        }
    }
}
