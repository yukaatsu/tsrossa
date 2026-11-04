import Foundation

public struct ExtractedAudioMetadata: Equatable {
    public var title: String?
    public var artist: String?
    public var album: String?
    public var albumArtist: String?
    public var genre: String?
    public var composer: String?
    public var year: Int?
    public var trackNumber: Int?
    public var sampleRate: UInt32 = 0
    public var bitDepth: UInt32 = 0
    public var channels: UInt32 = 0
    public var totalSamples: UInt64 = 0
    public var durationSeconds: Double = 0.0
    public var isMqa: Bool = false
    public var lyrics: String?
    public var rawArtworkData: Data?

    public init() {}
}

public enum TrackTitleParser {
    /// Memisahkan nama file menjadi Artist dan Title.
    /// Berbeda dari pemotongan biasa, pemotongan dilakukan pada kemunculan TERAKHIR " - "
    /// (lastIndexOf) sehingga nama band/lagu dengan tanda hubung (seperti AC-DC) tidak terpotong salah.
    public static func parse(_ rawName: String) -> (artist: String, title: String) {
        var base = rawName.trimmingCharacters(in: .whitespacesAndNewlines)
        if let dotRange = base.range(of: "^[0-9]{1,3}\\.\\s+", options: .regularExpression) {
            base = String(base[dotRange.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
        }

        if let range = base.range(of: " - ", options: .backwards) {
            let left = String(base[..<range.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
            let right = String(base[range.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
            if !left.isEmpty && !right.isEmpty {
                return (artist: left, title: right)
            }
        }

        return (artist: "Unknown Artist", title: rawName.trimmingCharacters(in: .whitespacesAndNewlines))
    }
}

public final class AudioTagReader {
    public static func readMetadata(from url: URL, extractArtworkData: Bool = false) -> ExtractedAudioMetadata {
        var meta = ExtractedAudioMetadata()
        guard let handle = try? FileHandle(forReadingFrom: url) else {
            return meta
        }
        defer { try? handle.close() }

        guard let headerData = try? handle.read(upToCount: 16), headerData.count >= 4 else {
            return meta
        }
        let magic = [UInt8](headerData)

        // 1. FLAC: Magic 'fLaC' (0x66, 0x4C, 0x61, 0x43)
        if magic[0] == 0x66 && magic[1] == 0x4C && magic[2] == 0x61 && magic[3] == 0x43 {
            parseFlacStream(handle: handle, meta: &meta, extractArtwork: extractArtworkData)
            return meta
        }

        // 2. WAV: Magic 'RIFF....WAVE'
        if magic[0] == 0x52 && magic[1] == 0x49 && magic[2] == 0x46 && magic[3] == 0x46 {
            try? handle.seek(toOffset: 0)
            parseWavStream(handle: handle, meta: &meta, extractArtwork: extractArtworkData)
            return meta
        }

        // 3. ID3v2 header di awal file
        if magic[0] == 0x49 && magic[1] == 0x44 && magic[2] == 0x33 {
            try? handle.seek(toOffset: 0)
            if let id3Data = try? handle.read(upToCount: 256 * 1024) {
                parseID3v2(data: id3Data, meta: &meta, extractArtwork: extractArtworkData)
            }
        }

        return meta
    }

    private static func parseFlacStream(handle: FileHandle, meta: inout ExtractedAudioMetadata, extractArtwork: Bool) {
        try? handle.seek(toOffset: 4)
        var isLast = false

        while !isLast {
            guard let blockHeaderData = try? handle.read(upToCount: 4), blockHeaderData.count == 4 else { break }
            let hBytes = [UInt8](blockHeaderData)
            isLast = (hBytes[0] & 0x80) != 0
            let blockType = hBytes[0] & 0x7F
            let length = (Int(hBytes[1]) << 16) | (Int(hBytes[2]) << 8) | Int(hBytes[3])
            guard length >= 0 else { break }

            if blockType == 0 && length >= 34 { // STREAMINFO
                if let streamData = try? handle.read(upToCount: 34), streamData.count == 34 {
                    let b = [UInt8](streamData)
                    let sr = (UInt32(b[10]) << 12) | (UInt32(b[11]) << 4) | (UInt32(b[12]) >> 4)
                    let ch = UInt32(((b[12] >> 1) & 0x07) + 1)
                    let bits = UInt32((((b[12] & 0x01) << 4) | (b[13] >> 4)) + 1)
                    let total = (UInt64(b[13] & 0x0F) << 32)
                        | (UInt64(b[14]) << 24)
                        | (UInt64(b[15]) << 16)
                        | (UInt64(b[16]) << 8)
                        | UInt64(b[17])

                    meta.sampleRate = sr
                    meta.channels = ch
                    meta.bitDepth = bits
                    meta.totalSamples = total
                    meta.durationSeconds = sr > 0 ? Double(total) / Double(sr) : 0.0

                    if length > 34 {
                        try? handle.seek(toOffset: (try? handle.offset()) ?? 0 + UInt64(length - 34))
                    }
                }
            } else if blockType == 4 { // VORBIS_COMMENT
                if let commentData = try? handle.read(upToCount: length) {
                    parseVorbisComment(data: commentData, meta: &meta)
                }
            } else if blockType == 6 && extractArtwork { // PICTURE
                if let picData = try? handle.read(upToCount: length) {
                    parseFlacPicture(data: picData, meta: &meta)
                }
            } else {
                // Lewati block
                if let cur = try? handle.offset() {
                    try? handle.seek(toOffset: cur + UInt64(length))
                }
            }
        }
    }

    public static func parseVorbisComment(data: Data, meta: inout ExtractedAudioMetadata) {
        guard data.count >= 8 else { return }
        var offset = 0

        // Little-endian vendor length
        let vendorLen = Int(data[offset]) | (Int(data[offset + 1]) << 8) | (Int(data[offset + 2]) << 16) | (Int(data[offset + 3]) << 24)
        offset += 4 + vendorLen
        guard offset + 4 <= data.count else { return }

        let listLen = Int(data[offset]) | (Int(data[offset + 1]) << 8) | (Int(data[offset + 2]) << 16) | (Int(data[offset + 3]) << 24)
        offset += 4

        for _ in 0..<listLen {
            guard offset + 4 <= data.count else { break }
            let strLen = Int(data[offset]) | (Int(data[offset + 1]) << 8) | (Int(data[offset + 2]) << 16) | (Int(data[offset + 3]) << 24)
            offset += 4
            guard strLen >= 0 && offset + strLen <= data.count else { break }

            let commentBytes = data.subdata(in: offset..<(offset + strLen))
            offset += strLen
            if let commentStr = String(data: commentBytes, encoding: .utf8) {
                applyVorbisTag(commentStr, meta: &meta)
            }
        }
    }

    public static func containsLrcTimestamp(_ text: String) -> Bool {
        return text.range(of: "\\[\\d{1,2}:\\d{2}", options: .regularExpression) != nil
    }

    private static func applyVorbisTag(_ tag: String, meta: inout ExtractedAudioMetadata) {
        let parts = tag.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
        guard parts.count == 2 else { return }
        let key = parts[0].trimmingCharacters(in: .whitespaces).uppercased()
        let val = String(parts[1]).trimmingCharacters(in: .whitespaces)

        switch key {
        case "TITLE":
            if meta.title == nil || meta.title?.isEmpty == true { meta.title = val }
        case "ARTIST":
            if meta.artist == nil || meta.artist?.isEmpty == true { meta.artist = val }
        case "ALBUM":
            if meta.album == nil || meta.album?.isEmpty == true { meta.album = val }
        case "ALBUMARTIST", "ALBUM ARTIST", "ALBUM_ARTIST":
            meta.albumArtist = val
        case "GENRE":
            meta.genre = val
        case "COMPOSER":
            meta.composer = val
        case "DATE", "YEAR":
            if let y = Int(val.prefix(4)) { meta.year = y }
        case "TRACKNUMBER", "TRACK":
            let clean = val.components(separatedBy: "/").first ?? val
            if let t = Int(clean.trimmingCharacters(in: .whitespaces)) { meta.trackNumber = t }
        case "LYRICS", "SYNCEDLYRICS", "UNSYNCEDLYRICS":
            guard !val.isEmpty else { break }
            if let existing = meta.lyrics, !existing.isEmpty {
                let existingIsSynced = containsLrcTimestamp(existing)
                let newIsSynced = containsLrcTimestamp(val)
                if newIsSynced && !existingIsSynced {
                    meta.lyrics = val
                } else if !existingIsSynced && key != "UNSYNCEDLYRICS" {
                    meta.lyrics = val
                }
            } else {
                meta.lyrics = val
            }
        case "MQAENCODER", "ORIGINALSAMPLERATE":
            meta.isMqa = true
        default:
            if key.contains("MQA") || val.uppercased().contains("MQA") {
                meta.isMqa = true
            }
        }
    }

    private static func parseFlacPicture(data: Data, meta: inout ExtractedAudioMetadata) {
        guard data.count > 32 else { return }
        var offset = 4 // skip type
        guard offset + 4 <= data.count else { return }

        // MIME length (Big-endian)
        let mimeLen = (Int(data[offset]) << 24) | (Int(data[offset + 1]) << 16) | (Int(data[offset + 2]) << 8) | Int(data[offset + 3])
        offset += 4 + mimeLen
        guard offset + 4 <= data.count else { return }

        // Description length
        let descLen = (Int(data[offset]) << 24) | (Int(data[offset + 1]) << 16) | (Int(data[offset + 2]) << 8) | Int(data[offset + 3])
        offset += 4 + descLen + 16 // skip width, height, depth, colors
        guard offset + 4 <= data.count else { return }

        let dataLen = (Int(data[offset]) << 24) | (Int(data[offset + 1]) << 16) | (Int(data[offset + 2]) << 8) | Int(data[offset + 3])
        offset += 4
        if dataLen > 0 && offset + dataLen <= data.count {
            meta.rawArtworkData = data.subdata(in: offset..<(offset + dataLen))
        }
    }

    private static func parseWavStream(handle: FileHandle, meta: inout ExtractedAudioMetadata, extractArtwork: Bool) {
        guard let headerData = try? handle.read(upToCount: 2048) else { return }
        let bytes = [UInt8](headerData)
        guard bytes.count >= 12 else { return }

        var offset = 12
        while offset + 8 <= bytes.count {
            let chunkId = String(bytes: bytes[offset..<(offset + 4)], encoding: .ascii) ?? ""
            let chunkSize = Int(bytes[offset + 4]) | (Int(bytes[offset + 5]) << 8) | (Int(bytes[offset + 6]) << 16) | (Int(bytes[offset + 7]) << 24)
            offset += 8
            guard chunkSize >= 0 else { break }

            if chunkId == "fmt " && offset + 16 <= bytes.count {
                let channels = UInt32(bytes[offset + 2]) | (UInt32(bytes[offset + 3]) << 8)
                let sampleRate = UInt32(bytes[offset + 4]) | (UInt32(bytes[offset + 5]) << 8) | (UInt32(bytes[offset + 6]) << 16) | (UInt32(bytes[offset + 7]) << 24)
                let bitDepth = UInt32(bytes[offset + 14]) | (UInt32(bytes[offset + 15]) << 8)
                meta.channels = channels
                meta.sampleRate = sampleRate
                meta.bitDepth = bitDepth
            } else if chunkId == "data" && meta.sampleRate > 0 && meta.channels > 0 {
                let bytesPerFrame = (meta.bitDepth / 8) * meta.channels
                if bytesPerFrame > 0 {
                    let totalFrames = UInt64(chunkSize) / UInt64(bytesPerFrame)
                    meta.totalSamples = totalFrames
                    meta.durationSeconds = Double(totalFrames) / Double(meta.sampleRate)
                }
            } else if chunkId == "id3 " || chunkId == "ID3 " {
                let id3Data = headerData.subdata(in: offset..<min(offset + chunkSize, headerData.count))
                parseID3v2(data: id3Data, meta: &meta, extractArtwork: extractArtwork)
            }
            offset += chunkSize + (chunkSize % 2) // WAV word alignment
        }
    }

    public static func parseID3v2(data: Data, meta: inout ExtractedAudioMetadata, extractArtwork: Bool) {
        guard data.count >= 10 else { return }
        let bytes = [UInt8](data)
        guard bytes[0] == 0x49 && bytes[1] == 0x44 && bytes[2] == 0x33 else { return }

        let majorVer = bytes[3]
        let totalSize = (Int(bytes[6] & 0x7F) << 21) | (Int(bytes[7] & 0x7F) << 14) | (Int(bytes[8] & 0x7F) << 7) | Int(bytes[9] & 0x7F)
        let endOffset = min(10 + totalSize, data.count)
        var offset = 10

        if majorVer == 2 { // ID3v2.2 (3-char frame IDs, 3-byte size)
            while offset + 6 <= endOffset {
                guard let frameId = String(bytes: bytes[offset..<(offset + 3)], encoding: .ascii), !frameId.hasPrefix("\0") else { break }
                let frameSize = (Int(bytes[offset + 3]) << 16) | (Int(bytes[offset + 4]) << 8) | Int(bytes[offset + 5])
                offset += 6
                guard frameSize > 0 && offset + frameSize <= endOffset else { break }
                let frameData = data.subdata(in: offset..<(offset + frameSize))
                offset += frameSize
                applyID3Frame(id: frameId, data: frameData, meta: &meta, extractArtwork: extractArtwork)
            }
        } else if majorVer == 3 || majorVer == 4 { // ID3v2.3 / v2.4 (4-char frame IDs, 4-byte size)
            while offset + 10 <= endOffset {
                guard let frameId = String(bytes: bytes[offset..<(offset + 4)], encoding: .ascii), !frameId.hasPrefix("\0") else { break }
                var frameSize = 0
                if majorVer == 4 { // synchsafe in v2.4
                    frameSize = (Int(bytes[offset + 4] & 0x7F) << 21) | (Int(bytes[offset + 5] & 0x7F) << 14) | (Int(bytes[offset + 6] & 0x7F) << 7) | Int(bytes[offset + 7] & 0x7F)
                } else {
                    frameSize = (Int(bytes[offset + 4]) << 24) | (Int(bytes[offset + 5]) << 16) | (Int(bytes[offset + 6]) << 8) | Int(bytes[offset + 7])
                }
                offset += 10 // skip header + flags
                guard frameSize > 0 && offset + frameSize <= endOffset else { break }
                let frameData = data.subdata(in: offset..<(offset + frameSize))
                offset += frameSize
                applyID3Frame(id: frameId, data: frameData, meta: &meta, extractArtwork: extractArtwork)
            }
        }
    }

    private static func applyID3Frame(id: String, data: Data, meta: inout ExtractedAudioMetadata, extractArtwork: Bool) {
        guard !data.isEmpty else { return }

        if id == "TIT2" || id == "TT2" {
            meta.title = decodeID3String(data: data)
        } else if id == "TPE1" || id == "TP1" {
            meta.artist = decodeID3String(data: data)
        } else if id == "TALB" || id == "TAL" {
            meta.album = decodeID3String(data: data)
        } else if id == "TPE2" {
            meta.albumArtist = decodeID3String(data: data)
        } else if id == "TCON" {
            meta.genre = decodeID3String(data: data)
        } else if id == "TCOM" {
            meta.composer = decodeID3String(data: data)
        } else if id == "TRCK" || id == "TRK" {
            if let str = decodeID3String(data: data) {
                let clean = str.components(separatedBy: "/").first ?? str
                meta.trackNumber = Int(clean.trimmingCharacters(in: .whitespaces))
            }
        } else if id == "TYER" || id == "TDRC" || id == "TYE" {
            if let str = decodeID3String(data: data), let y = Int(str.prefix(4)) {
                meta.year = y
            }
        } else if id == "USLT" || id == "ULT" || id == "SYLT" {
            if let str = decodeID3String(data: data.dropFirst(4)), !str.isEmpty {
                if let existing = meta.lyrics, !existing.isEmpty {
                    let existingIsSynced = containsLrcTimestamp(existing)
                    let newIsSynced = containsLrcTimestamp(str)
                    if newIsSynced || !existingIsSynced {
                        meta.lyrics = str
                    }
                } else {
                    meta.lyrics = str
                }
            }
        } else if (id == "APIC" || id == "PIC") && extractArtwork {
            // APIC payload
            let b = [UInt8](data)
            var idx = 1 // skip encoding
            while idx < b.count && b[idx] != 0 { idx += 1 } // skip MIME
            idx += 2 // skip null terminator + picture type
            while idx < b.count && b[idx] != 0 { idx += 1 } // skip description
            idx += 1
            if idx < b.count {
                meta.rawArtworkData = data.subdata(in: idx..<data.count)
            }
        }
    }

    private static func decodeID3String(data: Data) -> String? {
        guard !data.isEmpty else { return nil }
        let encoding = data[0]
        let payload = data.dropFirst()

        switch encoding {
        case 0: return String(data: payload, encoding: .isoLatin1)?.trimmingCharacters(in: CharacterSet(["\0", " ", "\t"]))
        case 1: return String(data: payload, encoding: .utf16)?.trimmingCharacters(in: CharacterSet(["\0", " ", "\t"]))
        case 2: return String(data: payload, encoding: .utf16BigEndian)?.trimmingCharacters(in: CharacterSet(["\0", " ", "\t"]))
        case 3: return String(data: payload, encoding: .utf8)?.trimmingCharacters(in: CharacterSet(["\0", " ", "\t"]))
        default: return String(data: payload, encoding: .utf8)?.trimmingCharacters(in: CharacterSet(["\0", " ", "\t"]))
        }
    }
}
