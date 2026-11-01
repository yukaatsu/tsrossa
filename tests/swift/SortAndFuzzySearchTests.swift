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
}
