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
                    Text("New Playlist")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundStyle(theme.primary)
                    Text("Create Playlist or Smart Filter")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(.primary)
                }
                Spacer()
                Button(action: {
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) {
                        vm.showNewPlaylistModal = false
                    }
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(TsrossaScaleButtonStyle())
                .frame(width: 28, height: 28)
                .contentShape(Rectangle())
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("Playlist Name")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.secondary)

                TextField("e.g. Audiophile Reference, Late Night Jazz...", text: $vm.newPlaylistName)
                    .textFieldStyle(PlainTextFieldStyle())
                    .padding(10)
                    .background(Color.white.opacity(0.06))
                    .continuousSquircle(8)
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("Smart Filter Keyword (Optional)")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.secondary)

                TextField("e.g. artist or album keyword to auto-match...", text: $vm.newPlaylistKeyword)
                    .textFieldStyle(PlainTextFieldStyle())
                    .padding(10)
                    .background(Color.white.opacity(0.06))
                    .continuousSquircle(8)
            }

            HStack {
                Button("Cancel") {
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) {
                        vm.showNewPlaylistModal = false
                    }
                }
                .buttonStyle(TsrossaScaleButtonStyle())
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(Color.white.opacity(0.08))
                .continuousSquircle(8)
                .contentShape(Rectangle())

                Spacer()

                Button("Create Playlist") {
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) {
                        vm.createPlaylistFromModal()
                    }
                }
                .buttonStyle(TsrossaScaleButtonStyle())
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(theme.primary)
                .foregroundStyle(theme.onPrimary)
                .continuousSquircle(8)
                .contentShape(Rectangle())
            }
            .padding(.top, 8)
        }
        .padding(22)
        .frame(minWidth: 320, idealWidth: 420, maxWidth: 460)
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
}
