import SwiftUI

public struct FloatingTopToolbarView: View {
    @ObservedObject var vm = AppViewModel.shared
    public var theme: LiquidGlassTheme
    @FocusState private var isSearchFocused: Bool

    public init(theme: LiquidGlassTheme) {
        self.theme = theme
    }

    public var body: some View {
        HStack(spacing: 12) {
            // Omnibar Search Field
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 13))
                    .foregroundStyle(isSearchFocused ? theme.primary : .secondary)

                TextField("Search tracks, artists, or albums...", text: $vm.searchText)
                    .focused($isSearchFocused)
                    .textFieldStyle(PlainTextFieldStyle())
                    .font(.system(size: 13))
                    .foregroundStyle(.primary)

                if !vm.searchText.isEmpty {
                    Button(action: { vm.searchText = "" }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                            .frame(width: 20, height: 20)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(PlainButtonStyle())
                } else if !isSearchFocused {
                    Text("⌘F")
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundStyle(.tertiary)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 2)
                        .background(Color.white.opacity(0.06))
                        .continuousSquircle(4)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(Color.white.opacity(0.06))
            .continuousSquircle(12)
            .onChange(of: vm.searchFocusTrigger) { _ in
                isSearchFocused = true
            }

            Spacer()

            // Quick Actions: Change Folder, Rescan, Track Info, Shortcuts Help
            HStack(spacing: 6) {
                Button(action: { vm.openFolderPicker() }) {
                    HStack(spacing: 5) {
                        Image(systemName: "folder")
                            .font(.system(size: 12))
                        Text("Music Folder")
                            .font(.system(size: 12, weight: .medium))
                    }
                    .foregroundStyle(.primary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(Color.white.opacity(0.06))
                    .continuousSquircle(10)
                    .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())
                .help("Open Music Directory (⌘O)")

                Button(action: { vm.rescanCurrentFolder() }) {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                        .frame(width: 28, height: 28)
                        .background(Color.white.opacity(0.06))
                        .continuousSquircle(10)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())
                .help("Rescan current music folder (⌘R)")

                Button(action: {
                    withAnimation(.spring(response: 0.32, dampingFraction: 0.82)) {
                        vm.showTrackDetailsModal.toggle()
                    }
                }) {
                    Image(systemName: "info.circle")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                        .frame(width: 28, height: 28)
                        .background(Color.white.opacity(0.06))
                        .continuousSquircle(10)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())
                .help("Audio Track Info (⌘I)")

                Button(action: {
                    withAnimation(.spring(response: 0.32, dampingFraction: 0.82)) {
                        vm.showKeyboardShortcutsModal.toggle()
                    }
                }) {
                    Image(systemName: "questionmark.circle")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                        .frame(width: 28, height: 28)
                        .background(Color.white.opacity(0.06))
                        .continuousSquircle(10)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())
                .help("Keyboard Shortcuts (⌘/)")
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .liquidGlass(role: .floatingPillBar, cornerRadius: 18, tint: theme.primary)
    }
}
