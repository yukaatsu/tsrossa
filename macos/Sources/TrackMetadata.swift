import Foundation
import AppKit

public typealias AudioTrack = TrackMetadata

public struct TrackMetadata: Identifiable, Hashable, Codable {
    public let id: String
    public var path: String
    public var title: String
    public var artist: String
    public var album: String
    public var albumArtist: String
    public var genre: String
    public var composer: String
    public var year: Int
    public var trackNumber: Int
    public var sampleRate: UInt32
    public var bitDepth: UInt32
    public var channels: UInt32
    public var durationSeconds: Double
    public var fileSize: Int64
    public var dateModified: Double
    public var dateAdded: Double
    public var isMqa: Bool
    public var lyrics: String?
    public var artworkThumbPath: String?
    public var dominantColorHex: String?

    public init(
        path: String,
        title: String,
        artist: String,
        album: String,
        albumArtist: String = "",
        genre: String = "",
        composer: String = "",
        year: Int = 0,
        trackNumber: Int = 0,
        sampleRate: UInt32 = 44100,
        bitDepth: UInt32 = 16,
        channels: UInt32 = 2,
        durationSeconds: Double = 0.0,
        fileSize: Int64 = 0,
        dateModified: Double = 0.0,
        dateAdded: Double = 0.0,
        isMqa: Bool = false,
        lyrics: String? = nil,
        artworkThumbPath: String? = nil,
        dominantColorHex: String? = nil
    ) {
        self.id = path
        self.path = path
        self.title = title
        self.artist = artist
        self.album = album
        self.albumArtist = albumArtist.isEmpty ? artist : albumArtist
        self.genre = genre
        self.composer = composer
        self.year = year
        self.trackNumber = trackNumber
        self.sampleRate = sampleRate
        self.bitDepth = bitDepth
        self.channels = channels
        self.durationSeconds = durationSeconds
        self.fileSize = fileSize
        self.dateModified = dateModified
        self.dateAdded = dateAdded
        self.isMqa = isMqa
        self.lyrics = lyrics
        self.artworkThumbPath = artworkThumbPath
        self.dominantColorHex = dominantColorHex
    }

    public var artwork: NSImage? {
        AlbumArtCache.shared.thumbnailImage(for: path, knownThumbPath: artworkThumbPath)
    }

    public var listThumbnail: NSImage? {
        AlbumArtCache.shared.fastMemoryThumbnail(for: path, knownThumbPath: artworkThumbPath)
    }

    public var blurredArtwork: NSImage? {
        AlbumArtCache.shared.cachedBlurredCanvasImage(for: path, knownThumbPath: artworkThumbPath)
    }

    public static func makeTestTrack(
        title: String,
        artist: String,
        album: String,
        duration: Double = 120.0,
        dateAdded: Double = 0.0,
        dateModified: Double = 0.0,
        fileSize: Int64 = 0
    ) -> TrackMetadata {
        TrackMetadata(
            path: "/test/\(artist)_\(title).flac",
            title: title,
            artist: artist,
            album: album,
            durationSeconds: duration,
            fileSize: fileSize,
            dateModified: dateModified,
            dateAdded: dateAdded
        )
    }
}
