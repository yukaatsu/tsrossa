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
            // Omnibar Search Field (Smoothly expands when focused)
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
                    Button(action: {
                        withAnimation(.spring(response: 0.24, dampingFraction: 0.80)) {
                            vm.searchText = ""
                        }
                    }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                            .frame(width: 20, height: 20)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(PlainButtonStyle())
                    .transition(.scale(scale: 0.7).combined(with: .opacity))
                } else if !isSearchFocused {
                    Text("⌘F")
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundStyle(.tertiary)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 2)
                        .background(Color.white.opacity(0.06))
                        .continuousSquircle(4)
                        .transition(.opacity)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(Color.white.opacity(isSearchFocused ? 0.09 : 0.06))
            .continuousSquircle(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(isSearchFocused ? theme.primary.opacity(0.40) : Color.clear, lineWidth: 1)
            )
            .frame(minWidth: 140, idealWidth: isSearchFocused ? 260 : 200, maxWidth: isSearchFocused ? 280 : 230)
            .animation(.spring(response: 0.30, dampingFraction: 0.80), value: isSearchFocused)
            .animation(.spring(response: 0.24, dampingFraction: 0.80), value: vm.searchText.isEmpty)
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
                    .background(vm.hoveredToolbarBtn == "FOLDER" ? Color.white.opacity(0.10) : Color.white.opacity(0.06))
                    .continuousSquircle(10)
                    .contentShape(Rectangle())
                }
                .buttonStyle(TsrossaScaleButtonStyle())
                .onHover { isH in withAnimation(.easeOut(duration: 0.12)) { vm.hoveredToolbarBtn = isH ? "FOLDER" : nil } }
                .help("Open Music Directory (⌘O)")

                Button(action: {
                    withAnimation(.spring(response: 0.30, dampingFraction: 0.82)) {
                        vm.rescanCurrentFolder()
                    }
                }) {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                        .frame(width: 28, height: 28)
                        .background(vm.hoveredToolbarBtn == "RESCAN" ? Color.white.opacity(0.10) : Color.white.opacity(0.06))
                        .continuousSquircle(10)
                        .contentShape(Rectangle())
                }
                .buttonStyle(TsrossaScaleButtonStyle())
                .onHover { isH in withAnimation(.easeOut(duration: 0.12)) { vm.hoveredToolbarBtn = isH ? "RESCAN" : nil } }
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
                        .background(vm.hoveredToolbarBtn == "INFO" ? Color.white.opacity(0.10) : Color.white.opacity(0.06))
                        .continuousSquircle(10)
                        .contentShape(Rectangle())
                }
                .buttonStyle(TsrossaScaleButtonStyle())
                .onHover { isH in withAnimation(.easeOut(duration: 0.12)) { vm.hoveredToolbarBtn = isH ? "INFO" : nil } }
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
                        .background(vm.hoveredToolbarBtn == "HELP" ? Color.white.opacity(0.10) : Color.white.opacity(0.06))
                        .continuousSquircle(10)
                        .contentShape(Rectangle())
                }
                .buttonStyle(TsrossaScaleButtonStyle())
                .onHover { isH in withAnimation(.easeOut(duration: 0.12)) { vm.hoveredToolbarBtn = isH ? "HELP" : nil } }
                .help("Keyboard Shortcuts (⌘/)")
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .liquidGlass(role: .floatingPillBar, cornerRadius: 18, tint: theme.primary)
    }
}
