import Foundation

public func runSortAndFuzzySearchTests() {
    print("==> Running SortAndFuzzySearchTests...")

    TestSuite.run("FuzzySearchHelper matches direct substring, multi-token out-of-order, and subsequence") {
        // Direct & multi-token out-of-order
        try TestSuite.assertTrue(FuzzySearchHelper.matches(query: "hotel eagles", fields: ["Hotel California", "Eagles", "Hell Freezes Over"]) > 0)
        // Subsequence match ("htlcal" -> "Hotel California")
        try TestSuite.assertTrue(FuzzySearchHelper.matches(query: "htlcal", fields: ["Hotel California", "Eagles"]) > 0)
        // Non-matching query
        try TestSuite.assertEqual(FuzzySearchHelper.matches(query: "xyz999", fields: ["Hotel California", "Eagles"]), 0)
    }

    TestSuite.run("TrackSorter correctly sorts all 7 SortFields in both Ascending and Descending orders") {
        let t1 = AudioTrack.makeTestTrack(title: "Alpha", artist: "Zed", album: "B_Album", duration: 120, dateAdded: 1000, dateModified: 5000, fileSize: 10_000)
        let t2 = AudioTrack.makeTestTrack(title: "Beta", artist: "Amy", album: "A_Album", duration: 300, dateAdded: 2000, dateModified: 3000, fileSize: 50_000)
        let list = [t1, t2]

        try TestSuite.assertEqual(TrackSorter.sort(list, by: .title, order: .ascending).first?.title, "Alpha")
        try TestSuite.assertEqual(TrackSorter.sort(list, by: .title, order: .descending).first?.title, "Beta")
        try TestSuite.assertEqual(TrackSorter.sort(list, by: .artist, order: .ascending).first?.artist, "Amy")
        try TestSuite.assertEqual(TrackSorter.sort(list, by: .album, order: .ascending).first?.album, "A_Album")
        try TestSuite.assertEqual(TrackSorter.sort(list, by: .duration, order: .descending).first?.durationSeconds, 300)
        try TestSuite.assertEqual(TrackSorter.sort(list, by: .dateAdded, order: .descending).first?.dateAdded, 2000)
        try TestSuite.assertEqual(TrackSorter.sort(list, by: .dateModified, order: .descending).first?.dateModified, 5000)
        try TestSuite.assertEqual(TrackSorter.sort(list, by: .fileSize, order: .descending).first?.fileSize, 50_000)
    }

    TestSuite.run("QueueHelper RepeatMode.one repeats current track, RepeatMode.off stops at end, RepeatMode.all wraps") {
        let tracks = [
            AudioTrack.makeTestTrack(title: "Track 1", artist: "A", album: "B", duration: 100, dateAdded: 1, dateModified: 1, fileSize: 1),
            AudioTrack.makeTestTrack(title: "Track 2", artist: "A", album: "B", duration: 100, dateAdded: 2, dateModified: 2, fileSize: 1)
        ]
        var q = PlaybackQueueState(tracks: tracks, currentIndex: 1, repeatMode: .one, isShuffle: false)
        // Repeat One saat auto-advance harus mengembalikan lagu yang sama (index 1)
        let nextOne = QueueHelper.resolveNextTrack(state: &q, userInitiated: false)
        try TestSuite.assertEqual(nextOne?.title, "Track 2")

        // Repeat Off pada lagu terakhir harus mengembalikan nil (berhenti di akhir)
        q.repeatMode = .off
        let nextOff = QueueHelper.resolveNextTrack(state: &q, userInitiated: false)
        try TestSuite.assertEqual(nextOff, nil)

        // Repeat All pada lagu terakhir harus membungkus kembali ke Track 1 (index 0)
        q.repeatMode = .all
        let nextAll = QueueHelper.resolveNextTrack(state: &q, userInitiated: false)
        try TestSuite.assertEqual(nextAll?.title, "Track 1")
    }

    TestSuite.run("QueueHelper ShuffleDeck visits every track exactly once before reshuffling") {
        let tracks = (1...6).map { i in
            AudioTrack.makeTestTrack(title: "Song \(i)", artist: "A", album: "B", duration: 100, dateAdded: Double(i), dateModified: 1, fileSize: 1)
        }
        var q = PlaybackQueueState(tracks: tracks, currentIndex: 0, repeatMode: .all, isShuffle: true)
        var visited: Set<String> = [tracks[0].title]
        for _ in 1..<6 {
            if let next = QueueHelper.resolveNextTrack(state: &q, userInitiated: true) {
                visited.insert(next.title)
            }
        }
        try TestSuite.assertEqual(visited.count, 6, "All 6 tracks must be visited without repetition in one deck cycle")
    }

    TestSuite.run("QueueHelper resolvePreviousTrack restarts track if currentTime > 3.0 or pops from history") {
        let tracks = [
            AudioTrack.makeTestTrack(title: "Song 1", artist: "A", album: "B"),
            AudioTrack.makeTestTrack(title: "Song 2", artist: "A", album: "B")
        ]
        var q = PlaybackQueueState(tracks: tracks, currentIndex: 1, repeatMode: .off, isShuffle: false)
        // If currentTime > 3.0s, restartOnly must be true
        let resOver3 = QueueHelper.resolvePreviousTrack(state: &q, currentTime: 4.5)
        try TestSuite.assertTrue(resOver3.restartOnly)
        try TestSuite.assertEqual(resOver3.track, nil)

        // If currentTime <= 3.0s, steps back to previous track
        let resUnder3 = QueueHelper.resolvePreviousTrack(state: &q, currentTime: 1.2)
        try TestSuite.assertEqual(resUnder3.restartOnly, false)
        try TestSuite.assertEqual(resUnder3.track?.title, "Song 1")
    }
}
