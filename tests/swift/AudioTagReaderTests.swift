import Foundation

public struct SyntheticFlacBuilder {
    public static func buildSampleFlac(
        sampleRate: UInt32,
        channels: UInt32,
        bitsPerSample: UInt32,
        totalSamples: UInt64,
        comments: [String]
    ) -> Data {
        var data = Data()
        // 1. Magic 'fLaC'
        data.append(contentsOf: [0x66, 0x4C, 0x61, 0x43])

        // 2. Block 0: STREAMINFO (isLast = false, length = 34)
        data.append(contentsOf: [0x00, 0x00, 0x00, 0x22])
        var streamInfo = [UInt8](repeating: 0, count: 34)
        // sampleRate (20 bits), channels-1 (3 bits), bits-1 (5 bits), totalSamples (36 bits)
        let chBits = (channels - 1) & 0x07
        let bpBits = (bitsPerSample - 1) & 0x1F
        streamInfo[10] = UInt8((sampleRate >> 12) & 0xFF)
        streamInfo[11] = UInt8((sampleRate >> 4) & 0xFF)
        streamInfo[12] = UInt8(((sampleRate & 0x0F) << 4) | (chBits << 1) | ((bpBits >> 4) & 0x01))
        streamInfo[13] = UInt8((bpBits & 0x0F) << 4) | UInt8((totalSamples >> 32) & 0x0F)
        streamInfo[14] = UInt8((totalSamples >> 24) & 0xFF)
        streamInfo[15] = UInt8((totalSamples >> 16) & 0xFF)
        streamInfo[16] = UInt8((totalSamples >> 8) & 0xFF)
        streamInfo[17] = UInt8(totalSamples & 0xFF)
        data.append(contentsOf: streamInfo)

        // 3. Block 4: VORBIS_COMMENT (isLast = true: 0x84)
        var commentPayload = Data()
        let vendor = "TSRossa Test Suite"
        var vendorLen = UInt32(vendor.utf8.count)
        commentPayload.append(Data(bytes: &vendorLen, count: 4))
        commentPayload.append(contentsOf: vendor.utf8)

        var count = UInt32(comments.count)
        commentPayload.append(Data(bytes: &count, count: 4))
        for c in comments {
            var cLen = UInt32(c.utf8.count)
            commentPayload.append(Data(bytes: &cLen, count: 4))
            commentPayload.append(contentsOf: c.utf8)
        }

        let blockLen = commentPayload.count
        data.append(0x84) // isLast=true, type=4
        data.append(UInt8((blockLen >> 16) & 0xFF))
        data.append(UInt8((blockLen >> 8) & 0xFF))
        data.append(UInt8(blockLen & 0xFF))
        data.append(commentPayload)

        return data
    }
}

public func runAudioTagReaderTests() {
    print("==> Running AudioTagReaderTests...")

    TestSuite.run("TrackTitleParser splits on LAST occurrence of ' - ' and strips track number prefix") {
        let parsed1 = TrackTitleParser.parse("01 - AC-DC - Back In Black")
        try TestSuite.assertEqual(parsed1.artist, "01 - AC-DC")
        try TestSuite.assertEqual(parsed1.title, "Back In Black")

        let parsed2 = TrackTitleParser.parse("04. Pink Floyd - Shine On You Crazy Diamond")
        try TestSuite.assertEqual(parsed2.artist, "Pink Floyd")
        try TestSuite.assertEqual(parsed2.title, "Shine On You Crazy Diamond")

        let parsed3 = TrackTitleParser.parse("UnformattedTrackName")
        try TestSuite.assertEqual(parsed3.artist, "Unknown Artist")
        try TestSuite.assertEqual(parsed3.title, "UnformattedTrackName")
    }

    TestSuite.run("AudioTagReader parses synthetic FLAC with STREAMINFO, VORBIS_COMMENT, MQA, and Lyrics") {
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent("test_\(UUID().uuidString).flac")
        defer { try? FileManager.default.removeItem(at: tempURL) }

        let flacData = SyntheticFlacBuilder.buildSampleFlac(
            sampleRate: 96000,
            channels: 2,
            bitsPerSample: 24,
            totalSamples: 96000 * 180,
            comments: [
                "TITLE=Hotel California (Live)",
                "ARTIST=Eagles",
                "ALBUM=Hell Freezes Over",
                "ALBUMARTIST=Eagles",
                "GENRE=Rock",
                "DATE=1994",
                "TRACKNUMBER=6/15",
                "COMPOSER=Don Felder",
                "LYRICS=[00:12.00]On a dark desert highway",
                "MQAENCODER=MQAEncode v1.1"
            ]
        )
        try flacData.write(to: tempURL)

        let meta = AudioTagReader.readMetadata(from: tempURL)
        try TestSuite.assertEqual(meta.title, "Hotel California (Live)")
        try TestSuite.assertEqual(meta.artist, "Eagles")
        try TestSuite.assertEqual(meta.album, "Hell Freezes Over")
        try TestSuite.assertEqual(meta.albumArtist, "Eagles")
        try TestSuite.assertEqual(meta.genre, "Rock")
        try TestSuite.assertEqual(meta.year, 1994)
        try TestSuite.assertEqual(meta.trackNumber, 6)
        try TestSuite.assertEqual(meta.composer, "Don Felder")
        try TestSuite.assertEqual(meta.sampleRate, 96000)
        try TestSuite.assertEqual(meta.bitDepth, 24)
        try TestSuite.assertTrue(meta.isMqa)
        try TestSuite.assertEqual(meta.lyrics, "[00:12.00]On a dark desert highway")
    }

    TestSuite.run("AudioTagReader prioritizes synced LYRICS over trailing UNSYNCEDLYRICS in Vorbis Comment") {
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent("test_lyrics_\(UUID().uuidString).flac")
        defer { try? FileManager.default.removeItem(at: tempURL) }

        let flacData = SyntheticFlacBuilder.buildSampleFlac(
            sampleRate: 44100,
            channels: 2,
            bitsPerSample: 16,
            totalSamples: 44100 * 60,
            comments: [
                "TITLE=Synced Priority Track",
                "ARTIST=Ado",
                "LYRICS=[00:01.20]First line\n[00:04.50]Second line",
                "UNSYNCEDLYRICS=First line\nSecond line"
            ]
        )
        try flacData.write(to: tempURL)

        let meta = AudioTagReader.readMetadata(from: tempURL)
        try TestSuite.assertEqual(meta.lyrics, "[00:01.20]First line\n[00:04.50]Second line")
        try TestSuite.assertTrue(AudioTagReader.containsLrcTimestamp(meta.lyrics ?? ""))
    }

    TestSuite.run("AudioTagReader.containsLrcTimestamp identifies LRC timestamp formats") {
        try TestSuite.assertTrue(AudioTagReader.containsLrcTimestamp("[00:12.34]Hello"))
        try TestSuite.assertTrue(AudioTagReader.containsLrcTimestamp("[01:23:45]Hello"))
        try TestSuite.assertTrue(AudioTagReader.containsLrcTimestamp("[2:05]Hello"))
        try TestSuite.assertFalse(AudioTagReader.containsLrcTimestamp("Plain unsynced lyrics line"))
    }

    TestSuite.run("LyricsManager.parseLrcContent parses both dot and colon timestamp formats") {
        let lrcWithDot = "[00:05.50]Dot millisecond format"
        let parsedDot = LyricsManager.shared.parseLrcContent(lrcWithDot)
        try TestSuite.assertEqual(parsedDot.count, 1)
        try TestSuite.assertTrue(parsedDot.first?.isSynced ?? false)
        try TestSuite.assertEqual(parsedDot.first?.text, "Dot millisecond format")
        try TestSuite.assertEqual(parsedDot.first?.timestampSeconds, 5.5)

        let lrcWithColon = "[01:10:25]Colon millisecond format"
        let parsedColon = LyricsManager.shared.parseLrcContent(lrcWithColon)
        try TestSuite.assertEqual(parsedColon.count, 1)
        try TestSuite.assertTrue(parsedColon.first?.isSynced ?? false)
        try TestSuite.assertEqual(parsedColon.first?.text, "Colon millisecond format")
        try TestSuite.assertEqual(parsedColon.first?.timestampSeconds, 70.25)
    }
}
