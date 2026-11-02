import SwiftUI

public struct SettingsBentoView: View {
    @ObservedObject var vm = AppViewModel.shared
    @ObservedObject var engine = AudioEngine.shared
    @ObservedObject var updater = UpdateManager.shared
    public var theme: LiquidGlassTheme

    public init(theme: LiquidGlassTheme) {
        self.theme = theme
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                // Header
                VStack(alignment: .leading, spacing: 2) {
                    Text("Settings & Audio Configuration")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundStyle(.primary)
                    Text("Bit-Perfect CoreAudio, DSP pipeline & Liquid Glass appearance")
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 8)

                LazyVGrid(columns: [GridItem(.adaptive(minimum: 310), spacing: 16)], spacing: 16) {
                    // CARD 1: CoreAudio Output Device & Hog Mode
                    bentoCard(title: "CoreAudio Endpoint & Clock", icon: "hifispeaker.fill") {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Active Output Device")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundStyle(.secondary)

                            ForEach(engine.devices) { dev in
                                Button(action: { engine.selectDevice(id: dev.id) }) {
                                    HStack(spacing: 8) {
                                        Image(systemName: dev.id == engine.selectedDeviceId ? "checkmark.circle.fill" : "circle")
                                            .font(.system(size: 13))
                                            .foregroundStyle(dev.id == engine.selectedDeviceId ? theme.primary : .secondary)

                                        VStack(alignment: .leading, spacing: 1) {
                                            Text(dev.name)
                                                .font(.system(size: 12, weight: dev.id == engine.selectedDeviceId ? .semibold : .regular))
                                                .foregroundStyle(.primary)
                                            Text(dev.isUsb ? "USB External DAC" : "Built-in CoreAudio Endpoint")
                                                .font(.system(size: 10))
                                                .foregroundStyle(.secondary)
                                        }
                                        Spacer()
                                    }
                                    .padding(8)
                                    .background(dev.id == engine.selectedDeviceId ? theme.primary.opacity(0.12) : Color.white.opacity(0.03))
                                    .continuousSquircle(8)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(PlainButtonStyle())
                            }

                            Divider().padding(.vertical, 4)

                            Toggle("Exclusive Hog Mode (Bit-Perfect)", isOn: $vm.isExclusiveHogMode)
                                .font(.system(size: 12))
                                .tint(theme.primary)

                            Toggle("Automatic Sample Rate Clock Switch", isOn: $vm.isAutoClockSwitch)
                                .font(.system(size: 12))
                                .tint(theme.primary)
                        }
                    }

                    // CARD 2: Engine Performance & Jitter Defense
                    bentoCard(title: "Memory & Jitter Defense", icon: "memorychip") {
                        VStack(alignment: .leading, spacing: 10) {
                            Toggle("POSIX mlock() RAM Playback", isOn: $vm.isRamPlayback)
                                .font(.system(size: 12))
                                .tint(theme.primary)

                            Text("Pins decoded PCM stream to physical memory to prevent paging jitter.")
                                .font(.system(size: 10))
                                .foregroundStyle(.secondary)

                            Divider().padding(.vertical, 4)

                            Text("Ring Buffer Profile")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundStyle(.secondary)

                            Picker("", selection: $vm.bufferProfile) {
                                Text("64ms Low Latency").tag("64ms Ultra Low")
                                Text("160ms Balanced").tag("160ms Balanced")
                                Text("192ms Studio Safe").tag("192ms Jitter Safe")
                            }
                            .pickerStyle(RadioGroupPickerStyle())
                            .font(.system(size: 11))

                            Toggle("Keep Screen Awake During Playback", isOn: $vm.keepScreenAwake)
                                .font(.system(size: 12))
                                .tint(theme.primary)
                                .padding(.top, 4)
                        }
                    }

                    // CARD 3: Liquid Glass Appearance
                    bentoCard(title: "Liquid Glass Appearance", icon: "sparkles") {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Theme Mode")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundStyle(.secondary)

                            Picker("", selection: $vm.themeMode) {
                                Text("Dark Glass").tag("DARK")
                                Text("Light Glass").tag("LIGHT")
                            }
                            .pickerStyle(SegmentedPickerStyle())

                            Divider().padding(.vertical, 2)

                            Text("Background Canvas")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundStyle(.secondary)

                            Picker("", selection: $vm.bgCanvasMode) {
                                Text("Refraction").tag("DYNAMIC")
                                Text("Mesh Aura").tag("AURA")
                                Text("Minimal").tag("BLACK")
                            }
                            .pickerStyle(SegmentedPickerStyle())

                            Divider().padding(.vertical, 2)

                            Text("Accent Palette")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundStyle(.secondary)

                            HStack(spacing: 12) {
                                ForEach(ThemePaletteOption.allOptions) { opt in
                                    Button(action: {
                                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                            vm.accentColorMode = opt.id
                                        }
                                    }) {
                                        VStack(spacing: 4) {
                                            ZStack {
                                                Circle()
                                                    .fill(opt.color)
                                                    .frame(width: 24, height: 24)

                                                if vm.accentColorMode == opt.id {
                                                    Circle()
                                                        .strokeBorder(Color.white, lineWidth: 2)
                                                        .frame(width: 30, height: 30)
                                                }
                                            }
                                            .frame(width: 32, height: 32)

                                            Text(opt.name.components(separatedBy: " ").first ?? opt.name)
                                                .font(.system(size: 9.5, weight: vm.accentColorMode == opt.id ? .bold : .regular))
                                                .foregroundStyle(vm.accentColorMode == opt.id ? .primary : .secondary)
                                        }
                                        .contentShape(Rectangle())
                                    }
                                    .buttonStyle(PlainButtonStyle())
                                }
                            }
                            .padding(.top, 2)
                        }
                    }

                    // CARD 4: Music Library & Updates
                    bentoCard(title: "Library & Application", icon: "folder.badge.gearshape") {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Music Directory")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundStyle(.secondary)

                            Text(vm.currentFolder)
                                .font(.system(size: 10, design: .monospaced))
                                .foregroundStyle(.primary)
                                .lineLimit(1)
                                .padding(8)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.white.opacity(0.04))
                                .continuousSquircle(6)

                            HStack(spacing: 8) {
                                Button("Change Folder...") {
                                    vm.openFolderPicker()
                                }
                                .buttonStyle(PlainButtonStyle())
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(Color.white.opacity(0.08))
                                .continuousSquircle(8)
                                .contentShape(Rectangle())

                                Button("Rescan Library") {
                                    vm.rescanCurrentFolder()
                                }
                                .buttonStyle(PlainButtonStyle())
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(Color.white.opacity(0.08))
                                .continuousSquircle(8)
                                .contentShape(Rectangle())
                            }

                            Toggle("Auto-create Folder as Playlist", isOn: $vm.makeFolderAsPlaylist)
                                .font(.system(size: 12))
                                .tint(theme.primary)

                            Divider().padding(.vertical, 4)

                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("TSRossa macOS v1.0.0")
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundStyle(.primary)
                                    Text(updater.isChecking ? "Checking for updates..." : "Native Apple Silicon build")
                                        .font(.system(size: 10))
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                Button(action: { updater.checkForUpdates() }) {
                                    Text("Check Updates")
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundStyle(theme.primary)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 5)
                                        .background(theme.primary.opacity(0.12))
                                        .continuousSquircle(6)
                                        .contentShape(Rectangle())
                                }
                                .buttonStyle(PlainButtonStyle())
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 16)
        }
    }

    private func bentoCard<Content: View>(title: String, icon: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(theme.primary)
                Text(title)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.primary)
                Spacer()
            }

            content()
        }
        .padding(16)
        .liquidGlass(role: .bentoCard, cornerRadius: 18, tint: theme.primary)
    }
}
