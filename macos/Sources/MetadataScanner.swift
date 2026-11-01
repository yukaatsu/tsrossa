import Foundation
import AppKit

public class MetadataScanner {
    /// Incremental SQLite-backed directory scanner:
    /// - Cold start: loads from SQLite in < 20ms
    /// - Compares fileSize and contentModificationDate; unchanged files bypass binary tag decoding entirely!
    /// - Only new or modified files are parsed with AudioTagReader.readMetadata(...) and upserted.
    /// - Deleted files are pruned from the database.
    public static func fastScanDirectory(url: URL) -> [TrackMetadata] {
        var tracks: [TrackMetadata] = []
        let fileManager = FileManager.default
        let keys: [URLResourceKey] = [.isRegularFileKey, .nameKey, .fileSizeKey, .contentModificationDateKey]

        guard let enumerator = fileManager.enumerator(
            at: url,
            includingPropertiesForKeys: keys,
            options: [.skipsHiddenFiles, .skipsPackageDescendants]
        ) else {
            return tracks
        }

        let cachedTracks = MusicLibraryDatabase.shared.loadAllCachedTracks()
        var scannedPaths = Set<String>()
        var tracksToUpsert: [TrackMetadata] = []

        for case let fileURL as URL in enumerator {
            let path = fileURL.path
            if path.contains(".musiclibrary") || path.contains("Automatically Add to Music") {
                enumerator.skipDescendants()
                continue
            }

            let ext = fileURL.pathExtension.lowercased()
            if ext == "flac" || ext == "wav" {
                scannedPaths.insert(path)

                let resourceValues = try? fileURL.resourceValues(forKeys: Set(keys))
                let fileSize = Int64(resourceValues?.fileSize ?? 0)
                let dateModified = resourceValues?.contentModificationDate?.timeIntervalSince1970 ?? 0.0

                // 1. Fast Cache Hit: Same size and modification date
                if let cached = cachedTracks[path], cached.fileSize == fileSize, abs(cached.dateModified - dateModified) < 1.0 {
                    tracks.append(cached)
                    continue
                }

                // 2. Cache Miss: Extract with AudioTagReader
                let meta = AudioTagReader.readMetadata(from: fileURL, extractArtworkData: true)
                let filename = fileURL.deletingPathExtension().lastPathComponent
                let (fallbackArtist, fallbackTitle) = TrackTitleParser.parse(filename)
                let folderAlbum = fileURL.deletingLastPathComponent().lastPathComponent

                let title = (meta.title != nil && !meta.title!.isEmpty) ? meta.title! : fallbackTitle
                let artist = (meta.artist != nil && !meta.artist!.isEmpty) ? meta.artist! : fallbackArtist
                let album = (meta.album != nil && !meta.album!.isEmpty) ? meta.album! : folderAlbum
                let albumArtist = meta.albumArtist ?? artist
                let genre = meta.genre ?? ""
                let composer = meta.composer ?? ""
                let year = meta.year ?? 0
                let trackNumber = meta.trackNumber ?? 0

                let (thumbPath, dominantHex) = AlbumArtCache.shared.storeAndProcessArtwork(rawData: meta.rawArtworkData, for: fileURL)

                let track = TrackMetadata(
                    path: path,
                    title: title,
                    artist: artist,
                    album: album,
                    albumArtist: albumArtist,
                    genre: genre,
                    composer: composer,
                    year: year,
                    trackNumber: trackNumber,
                    sampleRate: meta.sampleRate > 0 ? meta.sampleRate : 44100,
                    bitDepth: meta.bitDepth > 0 ? meta.bitDepth : 16,
                    channels: meta.channels > 0 ? meta.channels : 2,
                    durationSeconds: meta.durationSeconds,
                    fileSize: fileSize,
                    dateModified: dateModified,
                    dateAdded: Date().timeIntervalSince1970,
                    isMqa: meta.isMqa,
                    lyrics: meta.lyrics,
                    artworkThumbPath: thumbPath,
                    dominantColorHex: dominantHex
                )

                tracks.append(track)
                tracksToUpsert.append(track)
            }
        }

        // Upsert newly parsed tracks into SQLite cache
        if !tracksToUpsert.isEmpty {
            MusicLibraryDatabase.shared.upsertTracks(tracksToUpsert)
        }

        // Clean up deleted tracks
        let deletedPaths = cachedTracks.keys.filter { $0.hasPrefix(url.path) && !scannedPaths.contains($0) }
        if !deletedPaths.isEmpty {
            MusicLibraryDatabase.shared.removeDeletedPaths(deletedPaths)
        }

        return tracks.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
    }

    public static func enrichMetadataInBackground(tracks: [TrackMetadata], completion: @escaping ([TrackMetadata]) -> Void) {
        DispatchQueue.global(qos: .utility).async {
            var enriched: [TrackMetadata] = []
            var updatedToDb: [TrackMetadata] = []

            for track in tracks {
                var t = track
                if t.artworkThumbPath == nil || t.dominantColorHex == nil {
                    let fileURL = URL(fileURLWithPath: t.path)
                    let meta = AudioTagReader.readMetadata(from: fileURL, extractArtworkData: true)
                    let (thumb, hex) = AlbumArtCache.shared.storeAndProcessArtwork(rawData: meta.rawArtworkData, for: fileURL)
                    t.artworkThumbPath = thumb
                    t.dominantColorHex = hex
                    if t.lyrics == nil { t.lyrics = meta.lyrics }
                    updatedToDb.append(t)
                }
                enriched.append(t)
            }

            if !updatedToDb.isEmpty {
                MusicLibraryDatabase.shared.upsertTracks(updatedToDb)
            }

            DispatchQueue.main.async {
                completion(enriched)
            }
        }
    }
}
