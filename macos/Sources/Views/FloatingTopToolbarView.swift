import SwiftUI

public struct FloatingTopToolbarView: View {
    @ObservedObject var vm = AppViewModel.shared
    public var theme: LiquidGlassTheme

    public init(theme: LiquidGlassTheme) {
        self.theme = theme
    }

    public var body: some View {
        HStack(spacing: 12) {
            // Omnibar Search Field
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)

                TextField("Search titles, artists, albums, or genres (⌘F)...", text: $vm.searchText)
                    .textFieldStyle(PlainTextFieldStyle())
                    .font(.system(size: 13))
                    .foregroundStyle(.primary)

                if !vm.searchText.isEmpty {
                    Button(action: { vm.searchText = "" }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(Color.white.opacity(0.06))
            .continuousSquircle(12)

            Spacer()

            // Quick Actions: Change Folder & Rescan
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
                }
                .buttonStyle(PlainButtonStyle())

                Button(action: { vm.rescanCurrentFolder() }) {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                        .padding(7)
                        .background(Color.white.opacity(0.06))
                        .continuousSquircle(10)
                }
                .buttonStyle(PlainButtonStyle())
                .help("Rescan current music folder (⌘R)")
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .liquidGlass(role: .floatingPillBar, cornerRadius: 18, tint: theme.primary)
        .contentShape(Rectangle())
        .onTapGesture(count: 2) {
            WindowZoomManager.shared.toggleFitToScreen()
        }
    }
}
