import SwiftUI
import AppKit

public struct TelemetryModalView: View {
    @ObservedObject var engine = AudioEngine.shared
    @ObservedObject var vm = AppViewModel.shared
    @ObservedObject var logger = AppLogger.shared
    public var theme: LiquidGlassTheme?

    public init(theme: LiquidGlassTheme? = nil) {
        self.theme = theme
    }

    private var snap: HardwareTelemetrySnapshot {
        engine.telemetrySnapshot
    }

    private var filteredLogs: [LogEntry] {
        logger.entries.filter { entry in
            if let cat = vm.telemetrySelectedCategory, entry.category != cat {
                return false
            }
            if !vm.telemetrySearchQuery.isEmpty {
                return entry.message.localizedCaseInsensitiveContains(vm.telemetrySearchQuery) ||
                       entry.category.rawValue.localizedCaseInsensitiveContains(vm.telemetrySearchQuery)
            }
            return true
        }
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header (Native macOS Dialog Header - No iOS drag handle)
            HStack(alignment: .center) {
                HStack(spacing: 10) {
                    Circle()
                        .fill(snap.hogModeStatus == "EXCLUSIVE_HOG" ? Color.green : Color.orange)
                        .frame(width: 10, height: 10)

                    VStack(alignment: .leading, spacing: 2) {
                        Text("CoreAudio Hardware & Telemetry Audit")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(TsrossaColors.textPrimary)
                        Text(snap.hogModeStatus == "EXCLUSIVE_HOG" ? "Exclusive Hog Mode Locked (Bit-Perfect)" : "CoreAudio Shared HAL Mixer")
                            .font(.system(size: 11, design: .monospaced))
                            .foregroundColor(TsrossaColors.textSecondary)
                    }
                }

                Spacer()

                Button(action: { vm.showTelemetry = false }) {
                    ZStack {
                        Circle()
                            .fill(Color.white.opacity(0.12))
                            .frame(width: 28, height: 28)
                        Image(systemName: "xmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(TsrossaColors.textPrimary)
                    }
                }
                .buttonStyle(PlainButtonStyle())
                .frame(width: 28, height: 28)
                .contentShape(Rectangle())
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 16)

            // Quick Diagnostic Metric Tiles (Real C++ CoreAudio HAL Metrics)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                metricTile(
                    title: "Signal Path",
                    value: snap.streamIsInteger ? "32-Bit Int Direct" : "64-Bit DSP -> Float",
                    detail: snap.streamIsInteger ? "Bit-Perfect memcpy" : (snap.ditherEnabled ? "TPDF Dither ON" : "No Dither"),
                    isPositive: snap.streamIsInteger
                )
                metricTile(
                    title: "Hardware Rate",
                    value: snap.actualHardwareSampleRate > 0 ? "\(Int(snap.actualHardwareSampleRate)) Hz" : "Standby",
                    detail: snap.sampleRate > 0 ? "Stream: \(snap.sampleRate) Hz" : "No active stream",
                    isPositive: snap.actualHardwareSampleRate > 0 && UInt32(snap.actualHardwareSampleRate) == snap.sampleRate
                )
                metricTile(
                    title: "RAM Buffer",
                    value: String(format: "%.1f%%", snap.ringBufferFillPercent),
                    detail: snap.isMemoryLocked ? "mlock() Active" : "Dynamic Virtual RAM",
                    isPositive: snap.isMemoryLocked
                )
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 16)

            // Action Buttons: Copy, Export, Clear
            HStack(spacing: 10) {
                Button(action: copyTelemetryReport) {
                    HStack(spacing: 6) {
                        Image(systemName: vm.copiedTelemetry ? "checkmark" : "doc.on.doc")
                        Text(vm.copiedTelemetry ? "Report Copied" : "Copy Report")
                    }
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(TsrossaColors.textPrimary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 36)
                    .background(Color.white.opacity(0.12))
                    .continuousSquircle(18)
                    .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())

                Button(action: exportLogFile) {
                    HStack(spacing: 6) {
                        Image(systemName: "square.and.arrow.down")
                        Text("Export .log")
                    }
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(TsrossaColors.textPrimary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 36)
                    .background(Color.white.opacity(0.12))
                    .continuousSquircle(18)
                    .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())

                Button(action: { logger.clear() }) {
                    HStack(spacing: 6) {
                        Image(systemName: "trash")
                        Text("Clear")
                    }
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(TsrossaColors.textSecondary)
                    .frame(width: 80, height: 36)
                    .background(Color.white.opacity(0.08))
                    .continuousSquircle(18)
                    .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 12)

            // Category Filter Pills & Search
            HStack(spacing: 8) {
                categoryPill(title: "All", category: nil)
                ForEach(LogCategory.allCases, id: \.self) { cat in
                    categoryPill(title: cat.rawValue.capitalized, category: cat)
                }
                Spacer()
                HStack(spacing: 4) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 11))
                        .foregroundColor(Color.white.opacity(0.4))
                    TextField("Filter logs...", text: $vm.telemetrySearchQuery)
                        .textFieldStyle(PlainTextFieldStyle())
                        .font(.system(size: 11))
                        .foregroundColor(.white)
                        .frame(width: 100)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.white.opacity(0.08))
                .continuousSquircle(8)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 10)

            Divider()
                .background(Color.white.opacity(0.08))

            // Diagnostic Log View (Immutable Timestamps from AppLogger)
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 4) {
                        if filteredLogs.isEmpty {
                            Text("No diagnostic events recorded.")
                                .font(.system(size: 11, design: .monospaced))
                                .foregroundColor(Color.white.opacity(0.4))
                                .padding(16)
                        } else {
                            ForEach(filteredLogs) { entry in
                                logRow(entry)
                                    .id(entry.id)
                            }
                        }
                    }
                    .padding(14)
                }
                .background(Color.black.opacity(0.35))
            }
        }
        .frame(minWidth: 380, idealWidth: 620, maxWidth: 660, minHeight: 340, idealHeight: 540, maxHeight: 600)
        .background(theme?.surface.opacity(0.94) ?? TsrossaColors.deepNavy)
        .background(.ultraThinMaterial)
        .continuousSquircle(24)
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(Color.white.opacity(0.12), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.7), radius: 30, x: 0, y: 15)
        .padding(24)
    }

    private func metricTile(title: String, value: String, detail: String, isPositive: Bool) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(.system(size: 10, weight: .bold, design: .monospaced))
                .foregroundColor(TsrossaColors.textSecondary)
            Text(value)
                .font(.system(size: 12, weight: .bold, design: .monospaced))
                .foregroundColor(isPositive ? Color.green : TsrossaColors.textPrimary)
                .lineLimit(1)
            Text(detail)
                .font(.system(size: 10, design: .monospaced))
                .foregroundColor(Color.white.opacity(0.55))
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(Color.white.opacity(0.06))
        .continuousSquircle(12)
    }

    private func categoryPill(title: String, category: LogCategory?) -> some View {
        Button(action: { vm.telemetrySelectedCategory = category }) {
            Text(title)
                .font(.system(size: 10, weight: .bold, design: .monospaced))
                .foregroundColor(vm.telemetrySelectedCategory == category ? Color.white : Color.white.opacity(0.5))
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(vm.telemetrySelectedCategory == category ? (theme?.primary.opacity(0.4) ?? Color.white.opacity(0.2)) : Color.clear)
                .continuousSquircle(6)
                .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }

    private func logRow(_ entry: LogEntry) -> some View {
        HStack(alignment: .top, spacing: 6) {
            Text("[\(entry.formattedTimestamp)]")
                .font(.system(size: 10, design: .monospaced))
                .foregroundColor(Color.white.opacity(0.4))

            Text("[\(entry.category.rawValue)]")
                .font(.system(size: 10, weight: .bold, design: .monospaced))
                .foregroundColor(categoryColor(entry.category))

            Text(entry.message)
                .font(.system(size: 10, design: .monospaced))
                .foregroundColor(levelColor(entry.level))
                .textSelection(.enabled)
        }
    }

    private func categoryColor(_ cat: LogCategory) -> Color {
        switch cat {
        case .audio: return Color.cyan
        case .dsp: return Color.purple
        case .hardware: return Color.orange
        case .library: return Color.green
        case .system: return Color.gray
        }
    }

    private func levelColor(_ lvl: LogLevel) -> Color {
        switch lvl {
        case .info: return Color(white: 0.85)
        case .notice: return Color.cyan
        case .warning: return Color.yellow
        case .error: return Color.red
        }
    }

    private func copyTelemetryReport() {
        let header = "Telemetry State: \(engine.telemetryJson)"
        let text = logger.exportText(telemetryHeader: header)
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)
        vm.copiedTelemetry = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            self.vm.copiedTelemetry = false
        }
    }

    private func exportLogFile() {
        let header = "Telemetry State: \(engine.telemetryJson)"
        let text = logger.exportText(telemetryHeader: header)

        let panel = NSSavePanel()
        panel.title = "Export Diagnostic Log"
        panel.nameFieldStringValue = "tsrossa_diagnostics_\(Int(Date().timeIntervalSince1970)).log"
        panel.canCreateDirectories = true

        if panel.runModal() == .OK, let url = panel.url {
            try? text.write(to: url, atomically: true, encoding: .utf8)
            AppLogger.shared.log(.system, .info, "Diagnostic log exported to \(url.lastPathComponent)")
        }
    }
}
