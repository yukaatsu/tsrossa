import SwiftUI

public struct SettingsBentoView: View {
    @ObservedObject var vm = AppViewModel.shared
    @ObservedObject var engine = AudioEngine.shared
    @ObservedObject var updater = UpdateManager.shared
    public var theme: LiquidGlassTheme
    public var isWide: Bool = true

    public init(theme: LiquidGlassTheme, isWide: Bool = true) {
        self.theme = theme
        self.isWide = isWide
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

                    if isWide {
                        VStack(spacing: 16) {
                            HStack(alignment: .top, spacing: 16) {
                                coreAudioCard
                                memoryJitterCard
                            }
                            .fixedSize(horizontal: false, vertical: true)

                            HStack(alignment: .top, spacing: 16) {
                                appearanceCard
                                libraryAppCard
                            }
                            .fixedSize(horizontal: false, vertical: true)
                        }
                    } else {
                        VStack(spacing: 16) {
                            coreAudioCard
                            memoryJitterCard
                            appearanceCard
                            libraryAppCard
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
        }

    // MARK: - Bento Cards

    private var coreAudioCard: some View {
        bentoCard(title: "CoreAudio Endpoint & Clock", icon: "hifispeaker.fill") {
            VStack(alignment: .leading, spacing: 10) {
                Text("Active Output Device")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.secondary)

                VStack(spacing: 4) {
                    ForEach(engine.devices) { dev in
                        Button(action: {
                            withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                                engine.selectDevice(id: dev.id)
                            }
                        }) {
                            HStack(spacing: 8) {
                                Image(systemName: dev.id == engine.selectedDeviceId ? "checkmark.circle.fill" : "circle")
                                    .font(.system(size: 13))
                                    .foregroundStyle(dev.id == engine.selectedDeviceId ? theme.primary : .secondary)

                                VStack(alignment: .leading, spacing: 1) {
                                    Text(dev.name)
                                        .font(.system(size: 12, weight: dev.id == engine.selectedDeviceId ? .semibold : .regular))
                                        .foregroundStyle(.primary)
                                        .lineLimit(1)
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
                        .buttonStyle(TsrossaScaleButtonStyle(scaleAmount: 0.98))
                    }
                }

                Divider().padding(.vertical, 2)

                settingSwitchRow(
                    title: "Exclusive Hog Mode",
                    subtitle: "Bypass OS mixer for bit-perfect transmission",
                    isOn: $vm.isExclusiveHogMode
                )

                settingSwitchRow(
                    title: "Automatic Clock Switch",
                    subtitle: "Match DAC hardware sample rate to track PCM",
                    isOn: $vm.isAutoClockSwitch
                )
            }
        }
    }

    private var memoryJitterCard: some View {
        bentoCard(title: "Memory & Jitter Defense", icon: "memorychip") {
            VStack(alignment: .leading, spacing: 10) {
                settingSwitchRow(
                    title: "POSIX mlock() RAM Lock",
                    subtitle: "Pins PCM stream in RAM to eliminate page faults",
                    isOn: $vm.isRamPlayback
                )

                Divider().padding(.vertical, 2)

                Text("Ring Buffer Latency Profile")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.secondary)

                Picker("", selection: $vm.bufferProfile) {
                    Text("64ms Ultra Low Latency").tag("64ms Ultra Low")
                    Text("160ms Balanced Performance").tag("160ms Balanced")
                    Text("192ms Studio Jitter Safe").tag("192ms Jitter Safe")
                }
                .pickerStyle(RadioGroupPickerStyle())
                .labelsHidden()
                .font(.system(size: 11))

                Divider().padding(.vertical, 2)

                settingSwitchRow(
                    title: "Keep Screen Awake",
                    subtitle: "Prevent sleep during audiophile playback",
                    isOn: $vm.keepScreenAwake
                )
            }
        }
    }

    private var appearanceCard: some View {
        bentoCard(title: "Liquid Glass Appearance", icon: "sparkles") {
            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Theme Mode")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(.secondary)

                    Picker("", selection: $vm.themeMode) {
                        Text("Dark Glass").tag("DARK")
                        Text("Light Glass").tag("LIGHT")
                    }
                    .pickerStyle(SegmentedPickerStyle())
                    .labelsHidden()
                }

                Divider().padding(.vertical, 2)

                VStack(alignment: .leading, spacing: 4) {
                    Text("Background Canvas")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(.secondary)

                    Picker("", selection: $vm.bgCanvasMode) {
                        Text("Refraction").tag("DYNAMIC")
                        Text("Mesh Aura").tag("AURA")
                        Text("Minimal").tag("BLACK")
                    }
                    .pickerStyle(SegmentedPickerStyle())
                    .labelsHidden()
                }

                Divider().padding(.vertical, 2)

                VStack(alignment: .leading, spacing: 4) {
                    Text("Accent Chromatic Palette")
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
                            .buttonStyle(TsrossaScaleButtonStyle(scaleAmount: 0.92))
                        }
                    }
                    .padding(.top, 2)
                }
            }
        }
    }

    private var libraryAppCard: some View {
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
                    .buttonStyle(TsrossaScaleButtonStyle())
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color.white.opacity(0.08))
                    .continuousSquircle(8)
                    .contentShape(Rectangle())

                    Button("Rescan Library") {
                        withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                            vm.rescanCurrentFolder()
                        }
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color.white.opacity(0.08))
                    .continuousSquircle(8)
                    .contentShape(Rectangle())
                }

                Divider().padding(.vertical, 2)

                settingSwitchRow(
                    title: "Auto-create Folder Playlist",
                    subtitle: "Generate smart playlists by directory",
                    isOn: $vm.makeFolderAsPlaylist
                )

                Divider().padding(.vertical, 2)

                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("TSRossa macOS v5.4")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(.primary)
                        Text(updater.isChecking ? "Checking for updates..." : "Native Apple Silicon audiophile player")
                            .font(.system(size: 10))
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button(action: {
                        withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                            updater.checkForUpdates()
                        }
                    }) {
                        Text("Check Updates")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(theme.primary)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(theme.primary.opacity(0.12))
                            .continuousSquircle(6)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(TsrossaScaleButtonStyle())
                }
            }
        }
    }

    private func settingSwitchRow(title: String, subtitle: String? = nil, isOn: Binding<Bool>) -> some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(.primary)
                if let sub = subtitle {
                    Text(sub)
                        .font(.system(size: 10))
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            Toggle("", isOn: isOn)
                .labelsHidden()
                .toggleStyle(.switch)
                .tint(theme.primary)
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

            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(18)
        .liquidGlass(role: .bentoCard, cornerRadius: 18, tint: theme.primary)
    }
}
