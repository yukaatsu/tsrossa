import Foundation

public func runUpdateVersionTests() {
    print("==> Running UpdateVersionTests...")

    TestSuite.run("Stable release is newer than lower stable release") {
        try TestSuite.assertTrue(VersionComparator.isNewerVersion(latestTag: "v5.5.0", currentVersion: "5.4.0"))
        try TestSuite.assertFalse(VersionComparator.isNewerVersion(latestTag: "v5.4.0", currentVersion: "5.4.0"))
        try TestSuite.assertFalse(VersionComparator.isNewerVersion(latestTag: "v5.3.9", currentVersion: "5.4.0"))
    }

    TestSuite.run("Stable release is newer than pre-release of same numeric version") {
        try TestSuite.assertTrue(VersionComparator.isNewerVersion(latestTag: "v5.4.0", currentVersion: "5.4.0-rc1"))
        try TestSuite.assertTrue(VersionComparator.isNewerVersion(latestTag: "5.4.0", currentVersion: "5.4.0-beta2"))
        try TestSuite.assertFalse(VersionComparator.isNewerVersion(latestTag: "5.4.0-rc1", currentVersion: "5.4.0"))
    }

    TestSuite.run("Pre-release ordering alpha < beta < rc and natural number beta2 < beta10") {
        try TestSuite.assertTrue(VersionComparator.isNewerVersion(latestTag: "5.4.0-beta1", currentVersion: "5.4.0-alpha9"))
        try TestSuite.assertTrue(VersionComparator.isNewerVersion(latestTag: "5.4.0-rc1", currentVersion: "5.4.0-beta10"))
        try TestSuite.assertTrue(VersionComparator.isNewerVersion(latestTag: "5.4.0-beta10", currentVersion: "5.4.0-beta2"))
        try TestSuite.assertFalse(VersionComparator.isNewerVersion(latestTag: "5.4.0-beta2", currentVersion: "5.4.0-beta10"))
    }

    TestSuite.run("macOS Asset Selector rejects Android/generic zips and accepts only macOS assets") {
        let androidAssets = [
            GitHubAssetInfo(name: "tsrossa-v5.5-android-universal.apk", browserDownloadUrl: "https://example.com/a.apk"),
            GitHubAssetInfo(name: "tsrossa-v5.5-android-debug-symbols.zip", browserDownloadUrl: "https://example.com/b.zip")
        ]
        try TestSuite.assertEqual(VersionComparator.selectMacOSAsset(from: androidAssets), nil)

        let mixedAssets = androidAssets + [
            GitHubAssetInfo(name: "TSRossa-macOS-v5.5-universal.zip", browserDownloadUrl: "https://example.com/mac.zip")
        ]
        let selected = VersionComparator.selectMacOSAsset(from: mixedAssets)
        try TestSuite.assertEqual(selected?.name, "TSRossa-macOS-v5.5-universal.zip")
    }
}
