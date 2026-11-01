import Foundation

public enum SortField: String, Codable, CaseIterable, Identifiable {
    case title = "Title"
    case artist = "Artist"
    case album = "Album"
    case duration = "Duration"
    case dateAdded = "Date Added"
    case dateModified = "Date Modified"
    case fileSize = "File Size"

    public var id: String { rawValue }
}

public enum SortOrder: String, Codable, CaseIterable, Identifiable {
    case ascending = "Ascending"
    case descending = "Descending"

    public var id: String { rawValue }
}

public enum TrackSorter {
    public static func sort(_ list: [TrackMetadata], by field: SortField, order: SortOrder) -> [TrackMetadata] {
        let isAsc = (order == .ascending)
        return list.sorted { a, b in
            let comp: ComparisonResult
            switch field {
            case .title:
                comp = a.title.localizedCaseInsensitiveCompare(b.title)
            case .artist:
                comp = a.artist.localizedCaseInsensitiveCompare(b.artist)
            case .album:
                comp = a.album.localizedCaseInsensitiveCompare(b.album)
            case .duration:
                if a.durationSeconds == b.durationSeconds { comp = .orderedSame }
                else { comp = a.durationSeconds < b.durationSeconds ? .orderedAscending : .orderedDescending }
            case .dateAdded:
                if a.dateAdded == b.dateAdded { comp = .orderedSame }
                else { comp = a.dateAdded < b.dateAdded ? .orderedAscending : .orderedDescending }
            case .dateModified:
                if a.dateModified == b.dateModified { comp = .orderedSame }
                else { comp = a.dateModified < b.dateModified ? .orderedAscending : .orderedDescending }
            case .fileSize:
                if a.fileSize == b.fileSize { comp = .orderedSame }
                else { comp = a.fileSize < b.fileSize ? .orderedAscending : .orderedDescending }
            }

            if comp == .orderedSame {
                return a.title.localizedCaseInsensitiveCompare(b.title) == .orderedAscending
            }
            return isAsc ? (comp == .orderedAscending) : (comp == .orderedDescending)
        }
    }
}

public enum FuzzySearchHelper {
    public static func matches(query: String, fields: [String]) -> Int {
        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if cleanQuery.isEmpty { return 100 }

        let cleanFields = fields.map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
        let combined = cleanFields.joined(separator: " ")

        // 1. Direct substring match (highest score 100 or 80)
        for f in cleanFields {
            if f == cleanQuery { return 100 }
            if f.hasPrefix(cleanQuery) { return 90 }
            if f.contains(cleanQuery) { return 80 }
        }
        if combined.contains(cleanQuery) { return 75 }

        // 2. Multi-token out-of-order matching
        let tokens = cleanQuery.split(separator: " ").map { String($0) }.filter { !$0.isEmpty }
        if tokens.count > 1 {
            var allTokensMatched = true
            for token in tokens {
                if !combined.contains(token) {
                    allTokensMatched = false
                    break
                }
            }
            if allTokensMatched { return 65 }
        }

        // 3. Subsequence matching (for queries >= 3 chars, e.g. "htlcal" -> "Hotel California")
        if cleanQuery.count >= 3 {
            for f in cleanFields {
                if isSubsequence(pattern: cleanQuery, target: f) {
                    return 35
                }
            }
        }

        return 0
    }

    private static func isSubsequence(pattern: String, target: String) -> Bool {
        if pattern.isEmpty { return true }
        if target.count < pattern.count { return false }

        var pIdx = pattern.startIndex
        var tIdx = target.startIndex

        while pIdx < pattern.endIndex && tIdx < target.endIndex {
            if pattern[pIdx] == target[tIdx] {
                pIdx = pattern.index(after: pIdx)
            }
            tIdx = target.index(after: tIdx)
        }
        return pIdx == pattern.endIndex
    }

    public static func filterAndRank(tracks: [TrackMetadata], query: String) -> [TrackMetadata] {
        let clean = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.isEmpty { return tracks }
        var scored: [(TrackMetadata, Int)] = []
        for t in tracks {
            let score = matches(query: clean, fields: [t.title, t.artist, t.album, t.genre])
            if score > 0 {
                scored.append((t, score))
            }
        }
        return scored.sorted { a, b in
            if a.1 != b.1 { return a.1 > b.1 }
            return a.0.title.localizedCaseInsensitiveCompare(b.0.title) == .orderedAscending
        }.map { $0.0 }
    }
}
