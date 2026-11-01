import Foundation
import AVFoundation

public struct LyricLine: Identifiable, Equatable {
    public let id: Int
    public let timestampSeconds: Double // -1.0 indicates unsynced line
    public let text: String
    
    public var isSynced: Bool {
        return timestampSeconds >= 0.0
    }
}

public class LyricsManager {
    public static let shared = LyricsManager()
    
    private init() {}
    
    /// Attempts to load lyrics for a track from local companion files (.lrc, .txt) or embedded metadata.
    public func fetchLyrics(for track: TrackMetadata) -> [LyricLine] {
        let audioUrl = URL(fileURLWithPath: track.path)
        let directoryUrl = audioUrl.deletingLastPathComponent()
        let baseFilename = audioUrl.deletingPathExtension().lastPathComponent
        
        // 1. Check for exact matching .lrc file in the same directory
        let lrcUrl = directoryUrl.appendingPathComponent("\(baseFilename).lrc")
        if FileManager.default.fileExists(atPath: lrcUrl.path),
           let content = try? String(contentsOf: lrcUrl, encoding: .utf8) {
            let parsed = parseLrcContent(content)
            if !parsed.isEmpty { return parsed }
        }
        
        // Also check with common case-insensitive variants or fallback encodings
        if FileManager.default.fileExists(atPath: lrcUrl.path),
           let content = try? String(contentsOf: lrcUrl, encoding: .ascii) {
            let parsed = parseLrcContent(content)
            if !parsed.isEmpty { return parsed }
        }
        
        // 2. Check for exact matching .txt file in the same directory
        let txtUrl = directoryUrl.appendingPathComponent("\(baseFilename).txt")
        if FileManager.default.fileExists(atPath: txtUrl.path),
           let content = try? String(contentsOf: txtUrl, encoding: .utf8) {
            let parsed = parseLrcContent(content)
            if !parsed.isEmpty { return parsed }
        }
        
        // 3. Fallback: Parse embedded lyrics tag via AVFoundation
        let asset = AVURLAsset(url: audioUrl)
        for item in asset.metadata {
            if let key = item.commonKey, key.rawValue.lowercased().contains("lyrics") {
                if let val = item.stringValue, !val.isEmpty {
                    let parsed = parseLrcContent(val)
                    if !parsed.isEmpty { return parsed }
                }
            }
            if let idKey = item.identifier?.rawValue.lowercased(), idKey.contains("uslt") || idKey.contains("lyrics") {
                if let val = item.stringValue, !val.isEmpty {
                    let parsed = parseLrcContent(val)
                    if !parsed.isEmpty { return parsed }
                }
            }
        }
        
        return []
    }
    
    /// Parses LRC formatted text with timestamps [mm:ss.xx] or plain unsynced lines.
    public func parseLrcContent(_ content: String) -> [LyricLine] {
        var lines: [LyricLine] = []
        let rawLines = content.components(separatedBy: .newlines)
        
        // Regex pattern matching [mm:ss.xx] or [mm:ss] or [mm:ss.xxx]
        let pattern = "\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?\\]"
        guard let regex = try? NSRegularExpression(pattern: pattern, options: []) else {
            return fallbackPlainLines(rawLines)
        }
        
        var idCounter = 0
        var foundAnySynced = false
        
        for raw in rawLines {
            let trimmed = raw.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty { continue }
            
            // Skip ID tags such as [ti:...], [ar:...], [al:...], [by:...]
            if trimmed.hasPrefix("[ti:") || trimmed.hasPrefix("[ar:") ||
               trimmed.hasPrefix("[al:") || trimmed.hasPrefix("[by:") ||
               trimmed.hasPrefix("[offset:") || trimmed.hasPrefix("[re:") ||
               trimmed.hasPrefix("[ve:") {
                continue
            }
            
            let nsString = trimmed as NSString
            let matches = regex.matches(in: trimmed, options: [], range: NSRange(location: 0, length: nsString.length))
            
            if !matches.isEmpty {
                foundAnySynced = true
                // Extract lyric text after the last timestamp match
                let lastMatch = matches.last!
                let textStartIndex = lastMatch.range.location + lastMatch.range.length
                let lyricText = nsString.substring(from: textStartIndex).trimmingCharacters(in: .whitespaces)
                
                for match in matches {
                    let minStr = nsString.substring(with: match.range(at: 1))
                    let secStr = nsString.substring(with: match.range(at: 2))
                    var msVal: Double = 0.0
                    
                    if match.numberOfRanges > 3 && match.range(at: 3).location != NSNotFound {
                        let msStr = nsString.substring(with: match.range(at: 3))
                        if msStr.count == 1 {
                            msVal = (Double(msStr) ?? 0.0) / 10.0
                        } else if msStr.count == 2 {
                            msVal = (Double(msStr) ?? 0.0) / 100.0
                        } else {
                            msVal = (Double(msStr) ?? 0.0) / 1000.0
                        }
                    }
                    
                    let minutes = Double(minStr) ?? 0.0
                    let seconds = Double(secStr) ?? 0.0
                    let totalSeconds = (minutes * 60.0) + seconds + msVal
                    
                    lines.append(LyricLine(
                        id: idCounter,
                        timestampSeconds: totalSeconds,
                        text: lyricText.isEmpty ? "..." : lyricText
                    ))
                    idCounter += 1
                }
            } else {
                // Non-timestamped line in an unsynced file
                if !foundAnySynced {
                    lines.append(LyricLine(
                        id: idCounter,
                        timestampSeconds: -1.0,
                        text: trimmed
                    ))
                    idCounter += 1
                }
            }
        }
        
        // Sort chronologically by timestamp
        if foundAnySynced {
            lines.sort { $0.timestampSeconds < $1.timestampSeconds }
        }
        
        return lines
    }
    
    private func fallbackPlainLines(_ rawLines: [String]) -> [LyricLine] {
        var lines: [LyricLine] = []
        var idCounter = 0
        for raw in rawLines {
            let trimmed = raw.trimmingCharacters(in: .whitespaces)
            if !trimmed.isEmpty {
                lines.append(LyricLine(id: idCounter, timestampSeconds: -1.0, text: trimmed))
                idCounter += 1
            }
        }
        return lines
    }
}
