import Foundation
import AVFoundation

public class MetadataScanner {
    /// Ultra-fast pass (<5ms): enumerates FLAC/WAV files and reads the 42-byte FLAC STREAMINFO
    /// or 44-byte WAV fmt header directly for instant, accurate bit-depth, sample-rate (kHz), and duration.
    /// Explicitly skips Apple Music's `.musiclibrary` package to avoid unnecessary TCC database probes.
    public static func fastScanDirectory(url: URL) -> [TrackMetadata] {
        var tracks: [TrackMetadata] = []
        let fileManager = FileManager.default
        let keys: [URLResourceKey] = [.isRegularFileKey, .nameKey, .isPackageKey]
        
        guard let enumerator = fileManager.enumerator(
            at: url,
            includingPropertiesForKeys: keys,
            options: [.skipsHiddenFiles, .skipsPackageDescendants]
        ) else {
            return tracks
        }
        
        for case let fileURL as URL in enumerator {
            let path = fileURL.path
            if path.contains(".musiclibrary") || path.contains("Automatically Add to Music") {
                enumerator.skipDescendants()
                continue
            }
            
            let ext = fileURL.pathExtension.lowercased()
            if ext == "flac" || ext == "wav" {
                let filename = fileURL.deletingPathExtension().lastPathComponent
                var title = filename
                var artist = "Unknown Artist"
                let album = fileURL.deletingLastPathComponent().lastPathComponent
                
                // Heuristic parsing from "Title - Artist"
                if filename.contains(" - ") {
                    let parts = filename.components(separatedBy: " - ")
                    if parts.count >= 2 {
                        title = parts[0].trimmingCharacters(in: .whitespaces)
                        artist = parts[1].trimmingCharacters(in: .whitespaces)
                    }
                }
                
                let header = parseAudioHeader(fileURL: fileURL, ext: ext)
                
                let track = TrackMetadata(
                    path: path,
                    title: title,
                    artist: artist,
                    album: album,
                    sampleRate: header.sampleRate,
                    bitDepth: header.bitDepth,
                    channels: header.channels,
                    durationSeconds: header.durationSeconds
                )
                tracks.append(track)
            }
        }
        
        return tracks.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
    }
    
    /// Reads the first 128 bytes of a FLAC or WAV file to extract hardware sample rate, bit depth, channels, and duration.
    private static func parseAudioHeader(fileURL: URL, ext: String) -> (sampleRate: UInt32, bitDepth: UInt32, channels: UInt32, durationSeconds: Double) {
        guard let handle = try? FileHandle(forReadingFrom: fileURL) else {
            return (44100, 16, 2, 0.0)
        }
        defer { try? handle.close() }
        
        guard let data = try? handle.read(upToCount: 128), data.count >= 42 else {
            return (44100, 16, 2, 0.0)
        }
        let bytes = [UInt8](data)
        
        if ext == "flac" && bytes[0] == 0x66 && bytes[1] == 0x4C && bytes[2] == 0x61 && bytes[3] == 0x43 {
            // FLAC STREAMINFO starts at byte 8 (34 bytes payload, bytes[18..25] hold rate/ch/bits/frames)
            let sampleRate = (UInt32(bytes[18]) << 12) | (UInt32(bytes[19]) << 4) | (UInt32(bytes[20]) >> 4)
            let channels = UInt32(((bytes[20] >> 1) & 0x07) + 1)
            let bitDepth = UInt32((((bytes[20] & 0x01) << 4) | (bytes[21] >> 4)) + 1)
            let totalSamples = (UInt64(bytes[21] & 0x0F) << 32)
                | (UInt64(bytes[22]) << 24)
                | (UInt64(bytes[23]) << 16)
                | (UInt64(bytes[24]) << 8)
                | UInt64(bytes[25])
            let duration = sampleRate > 0 ? Double(totalSamples) / Double(sampleRate) : 0.0
            if sampleRate > 0 && bitDepth > 0 {
                return (sampleRate, bitDepth, channels, duration)
            }
        } else if ext == "wav" && bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46 {
            // RIFF WAVE header: scan chunks starting at offset 12
            var offset = 12
            var sampleRate: UInt32 = 44100
            var bitDepth: UInt32 = 16
            var channels: UInt32 = 2
            var byteRate: UInt32 = 0
            var dataBytes: UInt32 = 0
            
            while offset + 8 <= bytes.count {
                let c0 = bytes[offset], c1 = bytes[offset + 1], c2 = bytes[offset + 2], c3 = bytes[offset + 3]
                let chunkSize = UInt32(bytes[offset + 4])
                    | (UInt32(bytes[offset + 5]) << 8)
                    | (UInt32(bytes[offset + 6]) << 16)
                    | (UInt32(bytes[offset + 7]) << 24)
                let payload = offset + 8
                if c0 == 0x66 && c1 == 0x6D && c2 == 0x74 && c3 == 0x20 && payload + 16 <= bytes.count { // "fmt "
                    channels = UInt32(bytes[payload + 2]) | (UInt32(bytes[payload + 3]) << 8)
                    sampleRate = UInt32(bytes[payload + 4])
                        | (UInt32(bytes[payload + 5]) << 8)
                        | (UInt32(bytes[payload + 6]) << 16)
                        | (UInt32(bytes[payload + 7]) << 24)
                    byteRate = UInt32(bytes[payload + 8])
                        | (UInt32(bytes[payload + 9]) << 8)
                        | (UInt32(bytes[payload + 10]) << 16)
                        | (UInt32(bytes[payload + 11]) << 24)
                    bitDepth = UInt32(bytes[payload + 14]) | (UInt32(bytes[payload + 15]) << 8)
                } else if c0 == 0x64 && c1 == 0x61 && c2 == 0x74 && c3 == 0x61 { // "data"
                    dataBytes = chunkSize
                    break
                }
                offset = payload + Int(chunkSize) + (Int(chunkSize) % 2)
            }
            let duration = byteRate > 0 ? Double(dataBytes) / Double(byteRate) : 0.0
            if sampleRate > 0 && bitDepth > 0 {
                return (sampleRate, bitDepth, max(1, channels), duration)
            }
        }
        
        return (44100, 16, 2, 0.0)
    }
    
    /// Background pass: enriches tracks with embedded FLAC/ID3 tags off the main thread.
    public static func enrichMetadataInBackground(tracks: [TrackMetadata], completion: @escaping ([TrackMetadata]) -> Void) {
        DispatchQueue.global(qos: .utility).async {
            var enriched: [TrackMetadata] = []
            enriched.reserveCapacity(tracks.count)
            
            for item in tracks {
                let fileURL = URL(fileURLWithPath: item.path)
                var title = item.title
                var artist = item.artist
                var album = item.album
                var duration = item.durationSeconds
                
                let asset = AVURLAsset(url: fileURL)
                if duration <= 0 {
                    let dur = CMTimeGetSeconds(asset.duration)
                    if !dur.isNaN && dur > 0 {
                        duration = dur
                    }
                }
                
                for meta in asset.metadata {
                    guard let key = meta.commonKey else { continue }
                    if key == .commonKeyTitle, let val = meta.stringValue, !val.isEmpty {
                        title = val
                    } else if key == .commonKeyArtist, let val = meta.stringValue, !val.isEmpty {
                        artist = val
                    } else if key == .commonKeyAlbumName, let val = meta.stringValue, !val.isEmpty {
                        album = val
                    }
                }
                
                enriched.append(
                    TrackMetadata(
                        path: item.path,
                        title: title,
                        artist: artist,
                        album: album,
                        sampleRate: item.sampleRate,
                        bitDepth: item.bitDepth,
                        channels: item.channels,
                        durationSeconds: duration
                    )
                )
            }
            
            DispatchQueue.main.async {
                completion(enriched)
            }
        }
    }
}
