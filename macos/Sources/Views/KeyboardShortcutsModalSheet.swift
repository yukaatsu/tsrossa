import SwiftUI

public struct KeyboardShortcutsModalSheet: View {
    @ObservedObject var vm = AppViewModel.shared
    public var theme: LiquidGlassTheme

    public init(theme: LiquidGlassTheme) {
        self.theme = theme
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header
            HStack {
                HStack(spacing: 8) {
                    Image(systemName: "keyboard.fill")
                        .font(.system(size: 16))
                        .foregroundStyle(theme.primary)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Keyboard Shortcuts")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.primary)
                        Text("macOS productivity & playback controls")
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
                Button(action: { vm.showKeyboardShortcutsModal = false }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(PlainButtonStyle())
                .frame(width: 28, height: 28)
                .contentShape(Rectangle())
            }
            .padding(20)

            Divider()

            ScrollView {
                VStack(spacing: 18) {
                    shortcutSection(title: "Playback & Audio") {
                        shortcutRow(keys: ["Space"], description: "Play / Pause toggle")
                        shortcutRow(keys: ["⌘", "→"], description: "Next track")
                        shortcutRow(keys: ["⌘", "←"], description: "Previous track")
                        shortcutRow(keys: ["⌘", "↑"], description: "Volume increase")
                        shortcutRow(keys: ["⌘", "↓"], description: "Volume decrease")
                        shortcutRow(keys: ["⌘", "S"], description: "Toggle shuffle")
                        shortcutRow(keys: ["⌘", "T"], description: "Cycle repeat mode")
                        shortcutRow(keys: ["⌘", "L"], description: "Toggle favorite / like")
                    }

                    shortcutSection(title: "Navigation & Tabs") {
                        shortcutRow(keys: ["⌘", "1"], description: "Now Playing view")
                        shortcutRow(keys: ["⌘", "2"], description: "Library view")
                        shortcutRow(keys: ["⌘", "3"], description: "Playlists view")
                        shortcutRow(keys: ["⌘", "4"], description: "Settings view")
                        shortcutRow(keys: ["⌥", "←"], description: "Previous workspace tab")
                        shortcutRow(keys: ["⌥", "→"], description: "Next workspace tab")
                    }

                    shortcutSection(title: "Search & Overlays") {
                        shortcutRow(keys: ["⌘", "F"], description: "Focus search bar")
                        shortcutRow(keys: ["⌘", "O"], description: "Open music folder")
                        shortcutRow(keys: ["⌘", "N"], description: "New playlist dialog")
                        shortcutRow(keys: ["⌘", "R"], description: "Rescan library files")
                        shortcutRow(keys: ["⌘", "I"], description: "Track technical info")
                        shortcutRow(keys: ["⌘", "U"], description: "CoreAudio telemetry audit")
                        shortcutRow(keys: ["⌘", "/"], description: "Show keyboard shortcuts")
                        shortcutRow(keys: ["Esc"], description: "Dismiss active modal / blur")
                    }
                }
                .padding(20)
            }

            Divider()

            HStack {
                Spacer()
                Button("Done") {
                    vm.showKeyboardShortcutsModal = false
                }
                .buttonStyle(PlainButtonStyle())
                .padding(.horizontal, 18)
                .padding(.vertical, 7)
                .background(theme.primary)
                .foregroundStyle(theme.onPrimary)
                .continuousSquircle(8)
                .contentShape(Rectangle())
            }
            .padding(14)
        }
        .frame(minWidth: 380, idealWidth: 480, maxWidth: 540, minHeight: 340, idealHeight: 480, maxHeight: 560)
        .background(theme.surface.opacity(0.94))
        .background(.ultraThinMaterial)
        .continuousSquircle(20)
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(Color.white.opacity(0.12), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.5), radius: 24, x: 0, y: 12)
        .padding(24)
    }

    private func shortcutSection<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 11, weight: .bold, design: .monospaced))
                .foregroundStyle(theme.primary)
                .padding(.leading, 4)

            VStack(spacing: 4) {
                content()
            }
            .padding(8)
            .background(Color.white.opacity(0.04))
            .continuousSquircle(10)
        }
    }

    private func shortcutRow(keys: [String], description: String) -> some View {
        HStack {
            Text(description)
                .font(.system(size: 12))
                .foregroundStyle(.primary)

            Spacer()

            HStack(spacing: 4) {
                ForEach(keys, id: \.self) { key in
                    keyCap(key)
                }
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
    }

    private func keyCap(_ key: String) -> some View {
        Text(key)
            .font(.system(size: 11, weight: .semibold, design: .monospaced))
            .foregroundStyle(.primary)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(Color.white.opacity(0.10))
            .continuousSquircle(6)
            .overlay(
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .stroke(Color.white.opacity(0.15), lineWidth: 0.8)
            )
    }
}
