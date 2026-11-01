import Foundation
import AppKit

public enum LogCategory: String, Codable, CaseIterable {
    case audio = "AUDIO"
    case dsp = "DSP"
    case hardware = "DAC/HAL"
    case library = "LIBRARY"
    case system = "SYSTEM"
}

public enum LogLevel: String, Codable, CaseIterable {
    case info = "INFO"
    case notice = "NOTICE"
    case warning = "WARN"
    case error = "ERROR"
}

public struct LogEntry: Identifiable, Codable, Equatable {
    public let id: UUID
    public let timestamp: Date
    public let category: LogCategory
    public let level: LogLevel
    public let message: String

    private static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss.SSS"
        return f
    }()

    public var formattedTimestamp: String {
        Self.formatter.string(from: timestamp)
    }
}

public final class AppLogger: ObservableObject {
    public static let shared = AppLogger()
    private let maxEntries = 500
    private let lock = NSLock()

    @Published public private(set) var entries: [LogEntry] = []

    private init() {
        log(.system, .info, "TSRossa macOS Telemetry & Diagnostic Logger initialized")
    }

    public func log(_ category: LogCategory, _ level: LogLevel = .info, _ message: String) {
        let entry = LogEntry(id: UUID(), timestamp: Date(), category: category, level: level, message: message)
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.lock.lock()
            self.entries.append(entry)
            if self.entries.count > self.maxEntries {
                self.entries.removeFirst(self.entries.count - self.maxEntries)
            }
            self.lock.unlock()
        }
    }

    public func clear() {
        lock.lock()
        entries.removeAll()
        lock.unlock()
    }

    public func exportText(telemetryHeader: String) -> String {
        lock.lock()
        let currentEntries = entries
        lock.unlock()

        var lines = [
            "=== TSROSSA MACOS DIAGNOSTIC REPORT ===",
            "Generated: \(ISO8601DateFormatter().string(from: Date()))",
            telemetryHeader,
            "=== EVENT LOG (\(currentEntries.count) entries) ==="
        ]
        for e in currentEntries {
            lines.append("[\(e.formattedTimestamp)] [\(e.level.rawValue)] [\(e.category.rawValue)] \(e.message)")
        }
        return lines.joined(separator: "\n")
    }
}
