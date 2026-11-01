import Foundation
import SQLite3

public final class MusicLibraryDatabase {
    public static let shared = MusicLibraryDatabase()

    private var db: OpaquePointer?
    private let dbQueue = DispatchQueue(label: "com.yuka.tsrossa.sqlite", qos: .userInitiated)

    private init() {
        openDatabase()
        createTableIfNeeded()
    }

    deinit {
        if let d = db {
            sqlite3_close(d)
        }
    }

    private func openDatabase() {
        let fm = FileManager.default
        let appSupport: URL
        if let supportURL = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
            appSupport = supportURL.appendingPathComponent("tsrossa", isDirectory: true)
        } else {
            appSupport = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("tsrossa", isDirectory: true)
        }
        try? fm.createDirectory(at: appSupport, withIntermediateDirectories: true)

        let dbPath = appSupport.appendingPathComponent("music_library_cache.db").path
        if sqlite3_open(dbPath, &db) != SQLITE_OK {
            db = nil
        } else {
            sqlite3_exec(db, "PRAGMA journal_mode=WAL;", nil, nil, nil)
            sqlite3_exec(db, "PRAGMA synchronous=NORMAL;", nil, nil, nil)
        }
    }

    private func createTableIfNeeded() {
        guard let db = db else { return }
        let sql = """
        CREATE TABLE IF NOT EXISTS tracks (
            path TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            artist TEXT NOT NULL,
            album TEXT NOT NULL,
            album_artist TEXT NOT NULL,
            genre TEXT NOT NULL,
            composer TEXT NOT NULL,
            year INTEGER NOT NULL,
            track_number INTEGER NOT NULL,
            duration REAL NOT NULL,
            sample_rate INTEGER NOT NULL,
            bit_depth INTEGER NOT NULL,
            channels INTEGER NOT NULL,
            file_size INTEGER NOT NULL,
            date_modified REAL NOT NULL,
            date_added REAL NOT NULL,
            is_mqa INTEGER NOT NULL,
            lyrics TEXT,
            artwork_thumb_path TEXT,
            dominant_color_hex TEXT
        );
        CREATE INDEX IF NOT EXISTS idx_tracks_album ON tracks(album);
        CREATE INDEX IF NOT EXISTS idx_tracks_artist ON tracks(artist);
        """
        sqlite3_exec(db, sql, nil, nil, nil)
    }

    public func loadAllCachedTracks() -> [String: TrackMetadata] {
        return dbQueue.sync {
            var result: [String: TrackMetadata] = [:]
            guard let db = db else { return result }

            let query = "SELECT path, title, artist, album, album_artist, genre, composer, year, track_number, duration, sample_rate, bit_depth, channels, file_size, date_modified, date_added, is_mqa, lyrics, artwork_thumb_path, dominant_color_hex FROM tracks"
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, query, -1, &stmt, nil) == SQLITE_OK {
                while sqlite3_step(stmt) == SQLITE_ROW {
                    let path = String(cString: sqlite3_column_text(stmt, 0))
                    let title = String(cString: sqlite3_column_text(stmt, 1))
                    let artist = String(cString: sqlite3_column_text(stmt, 2))
                    let album = String(cString: sqlite3_column_text(stmt, 3))
                    let albumArtist = String(cString: sqlite3_column_text(stmt, 4))
                    let genre = String(cString: sqlite3_column_text(stmt, 5))
                    let composer = String(cString: sqlite3_column_text(stmt, 6))
                    let year = Int(sqlite3_column_int(stmt, 7))
                    let trackNumber = Int(sqlite3_column_int(stmt, 8))
                    let duration = sqlite3_column_double(stmt, 9)
                    let sampleRate = UInt32(sqlite3_column_int(stmt, 10))
                    let bitDepth = UInt32(sqlite3_column_int(stmt, 11))
                    let channels = UInt32(sqlite3_column_int(stmt, 12))
                    let fileSize = sqlite3_column_int64(stmt, 13)
                    let dateModified = sqlite3_column_double(stmt, 14)
                    let dateAdded = sqlite3_column_double(stmt, 15)
                    let isMqa = (sqlite3_column_int(stmt, 16) != 0)

                    var lyrics: String? = nil
                    if let lText = sqlite3_column_text(stmt, 17) {
                        lyrics = String(cString: lText)
                    }

                    var thumb: String? = nil
                    if let tText = sqlite3_column_text(stmt, 18) {
                        thumb = String(cString: tText)
                    }

                    var color: String? = nil
                    if let cText = sqlite3_column_text(stmt, 19) {
                        color = String(cString: cText)
                    }

                    let t = TrackMetadata(
                        path: path,
                        title: title,
                        artist: artist,
                        album: album,
                        albumArtist: albumArtist,
                        genre: genre,
                        composer: composer,
                        year: year,
                        trackNumber: trackNumber,
                        sampleRate: sampleRate,
                        bitDepth: bitDepth,
                        channels: channels,
                        durationSeconds: duration,
                        fileSize: fileSize,
                        dateModified: dateModified,
                        dateAdded: dateAdded,
                        isMqa: isMqa,
                        lyrics: lyrics,
                        artworkThumbPath: thumb,
                        dominantColorHex: color
                    )
                    result[path] = t
                }
                sqlite3_finalize(stmt)
            }
            return result
        }
    }

    public func upsertTracks(_ tracks: [TrackMetadata]) {
        guard !tracks.isEmpty, let db = db else { return }
        dbQueue.async {
            sqlite3_exec(db, "BEGIN IMMEDIATE;", nil, nil, nil)
            let sql = """
            INSERT INTO tracks (path, title, artist, album, album_artist, genre, composer, year, track_number, duration, sample_rate, bit_depth, channels, file_size, date_modified, date_added, is_mqa, lyrics, artwork_thumb_path, dominant_color_hex)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(path) DO UPDATE SET
                title=excluded.title,
                artist=excluded.artist,
                album=excluded.album,
                album_artist=excluded.album_artist,
                genre=excluded.genre,
                composer=excluded.composer,
                year=excluded.year,
                track_number=excluded.track_number,
                duration=excluded.duration,
                sample_rate=excluded.sample_rate,
                bit_depth=excluded.bit_depth,
                channels=excluded.channels,
                file_size=excluded.file_size,
                date_modified=excluded.date_modified,
                is_mqa=excluded.is_mqa,
                lyrics=excluded.lyrics,
                artwork_thumb_path=excluded.artwork_thumb_path,
                dominant_color_hex=excluded.dominant_color_hex;
            """
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                for t in tracks {
                    sqlite3_bind_text(stmt, 1, (t.path as NSString).utf8String, -1, nil)
                    sqlite3_bind_text(stmt, 2, (t.title as NSString).utf8String, -1, nil)
                    sqlite3_bind_text(stmt, 3, (t.artist as NSString).utf8String, -1, nil)
                    sqlite3_bind_text(stmt, 4, (t.album as NSString).utf8String, -1, nil)
                    sqlite3_bind_text(stmt, 5, (t.albumArtist as NSString).utf8String, -1, nil)
                    sqlite3_bind_text(stmt, 6, (t.genre as NSString).utf8String, -1, nil)
                    sqlite3_bind_text(stmt, 7, (t.composer as NSString).utf8String, -1, nil)
                    sqlite3_bind_int(stmt, 8, Int32(t.year))
                    sqlite3_bind_int(stmt, 9, Int32(t.trackNumber))
                    sqlite3_bind_double(stmt, 10, t.durationSeconds)
                    sqlite3_bind_int(stmt, 11, Int32(t.sampleRate))
                    sqlite3_bind_int(stmt, 12, Int32(t.bitDepth))
                    sqlite3_bind_int(stmt, 13, Int32(t.channels))
                    sqlite3_bind_int64(stmt, 14, t.fileSize)
                    sqlite3_bind_double(stmt, 15, t.dateModified)
                    sqlite3_bind_double(stmt, 16, t.dateAdded)
                    sqlite3_bind_int(stmt, 17, t.isMqa ? 1 : 0)

                    if let l = t.lyrics {
                        sqlite3_bind_text(stmt, 18, (l as NSString).utf8String, -1, nil)
                    } else {
                        sqlite3_bind_null(stmt, 18)
                    }

                    if let a = t.artworkThumbPath {
                        sqlite3_bind_text(stmt, 19, (a as NSString).utf8String, -1, nil)
                    } else {
                        sqlite3_bind_null(stmt, 19)
                    }

                    if let c = t.dominantColorHex {
                        sqlite3_bind_text(stmt, 20, (c as NSString).utf8String, -1, nil)
                    } else {
                        sqlite3_bind_null(stmt, 20)
                    }

                    sqlite3_step(stmt)
                    sqlite3_reset(stmt)
                }
                sqlite3_finalize(stmt)
            }
            sqlite3_exec(db, "COMMIT;", nil, nil, nil)
        }
    }

    public func removeDeletedPaths(_ paths: [String]) {
        guard !paths.isEmpty, let db = db else { return }
        dbQueue.async {
            sqlite3_exec(db, "BEGIN IMMEDIATE;", nil, nil, nil)
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, "DELETE FROM tracks WHERE path = ?;", -1, &stmt, nil) == SQLITE_OK {
                for p in paths {
                    sqlite3_bind_text(stmt, 1, (p as NSString).utf8String, -1, nil)
                    sqlite3_step(stmt)
                    sqlite3_reset(stmt)
                }
                sqlite3_finalize(stmt)
            }
            sqlite3_exec(db, "COMMIT;", nil, nil, nil)
        }
    }
}
