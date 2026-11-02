import SwiftUI

public struct CreatePlaylistModalSheet: View {
    @ObservedObject var vm = AppViewModel.shared
    public var theme: LiquidGlassTheme

    public init(theme: LiquidGlassTheme) {
        self.theme = theme
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("NEW PLAYLIST")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundStyle(theme.primary)
                    Text("Create Playlist or Smart Filter")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(.primary)
                }
                Spacer()
                Button(action: { vm.showNewPlaylistModal = false }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(PlainButtonStyle())
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("PLAYLIST NAME")
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundStyle(.secondary)

                TextField("e.g., Favorites, Late Night, Polyphia...", text: $vm.newPlaylistName)
                    .textFieldStyle(PlainTextFieldStyle())
                    .padding(10)
                    .background(Color.white.opacity(0.06))
                    .continuousSquircle(8)
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("SMART FILTER KEYWORD (OPTIONAL)")
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundStyle(.secondary)

                TextField("e.g. artist or album keyword to auto-match...", text: $vm.newPlaylistKeyword)
                    .textFieldStyle(PlainTextFieldStyle())
                    .padding(10)
                    .background(Color.white.opacity(0.06))
                    .continuousSquircle(8)
            }

            HStack {
                Button("Cancel") {
                    vm.showNewPlaylistModal = false
                }
                .buttonStyle(PlainButtonStyle())
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(Color.white.opacity(0.08))
                .continuousSquircle(8)

                Spacer()

                Button("Create Playlist") {
                    vm.createPlaylistFromModal()
                }
                .buttonStyle(PlainButtonStyle())
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(theme.primary)
                .foregroundStyle(theme.onPrimary)
                .continuousSquircle(8)
            }
            .padding(.top, 8)
        }
        .padding(24)
        .frame(width: 440)
        .background(theme.surface.opacity(0.92))
        .continuousSquircle(20)
    }
}
