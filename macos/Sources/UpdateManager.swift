import Foundation
import AppKit

public struct GitHubAssetInfo: Equatable, Codable {
    public let name: String
    public let browserDownloadUrl: String

    enum CodingKeys: String, CodingKey {
        case name
        case browserDownloadUrl = "browser_download_url"
    }

    public init(name: String, browserDownloadUrl: String) {
        self.name = name
        self.browserDownloadUrl = browserDownloadUrl
    }
}

public enum VersionComparator {
    /// Membandingkan versi SemVer termasuk suffix pre-release (alpha < beta < rc < stable)
    /// serta perbandingan angka natural (beta10 > beta2), selaras dengan AppUpdateManager.kt di Android.
    public static func isNewerVersion(latestTag: String, currentVersion: String) -> Bool {
        let cleanLatest = stripPrefix(latestTag)
        let cleanCurrent = stripPrefix(currentVersion)
        if cleanLatest.isEmpty || cleanCurrent.isEmpty || cleanLatest == cleanCurrent {
            return false
        }

        let (latestCore, latestSuffix) = splitCoreAndSuffix(cleanLatest)
        let (currentCore, currentSuffix) = splitCoreAndSuffix(cleanCurrent)

        let maxParts = max(latestCore.count, currentCore.count)
        for i in 0..<maxParts {
            let lVal = i < latestCore.count ? latestCore[i] : 0
            let cVal = i < currentCore.count ? currentCore[i] : 0
            if lVal != cVal {
                return lVal > cVal
            }
        }

        // Jika angka inti sama: rilis stabil (tanpa suffix) lebih baru daripada pre-release
        if latestSuffix.isEmpty && !currentSuffix.isEmpty { return true }
        if !latestSuffix.isEmpty && currentSuffix.isEmpty { return false }
        if latestSuffix.isEmpty && currentSuffix.isEmpty { return false }

        return comparePreReleaseSuffix(latestSuffix, currentSuffix) > 0
    }

    public static func selectMacOSAsset(from assets: [GitHubAssetInfo]) -> GitHubAssetInfo? {
        let macKeywords = ["macos", "mac", "darwin", "osx"]
        let bannedKeywords = ["android", "windows", "win64", "linux", "debug-symbols", ".apk"]

        return assets.first { asset in
            let lower = asset.name.lowercased()
            let hasSupportedExt = lower.hasSuffix(".zip") || lower.hasSuffix(".dmg")
            let hasMacKeyword = macKeywords.contains { lower.contains($0) }
            let hasBannedKeyword = bannedKeywords.contains { lower.contains($0) }
            return hasSupportedExt && hasMacKeyword && !hasBannedKeyword
        }
    }

    private static func stripPrefix(_ raw: String) -> String {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("v") || s.hasPrefix("V") {
            s.removeFirst()
        }
        if let plusIdx = s.firstIndex(of: "+") {
            s = String(s[..<plusIdx])
        }
        return s
    }

    private static func splitCoreAndSuffix(_ version: String) -> ([Int], String) {
        let parts = version.split(separator: "-", maxSplits: 1, omittingEmptySubsequences: false)
        let coreStr = String(parts[0])
        let suffixStr = parts.count > 1 ? String(parts[1]).lowercased() : ""
        let coreNums = coreStr.split(separator: ".").map { part -> Int in
            let digits = part.prefix { $0.isNumber }
            return Int(digits) ?? 0
        }
        return (coreNums, suffixStr)
    }

    private static func comparePreReleaseSuffix(_ a: String, _ b: String) -> Int {
        if a == b { return 0 }
        let rankA = preReleaseStageRank(a)
        let rankB = preReleaseStageRank(b)
        if rankA != rankB {
            return rankA > rankB ? 1 : -1
        }
        return a.compare(b, options: [.numeric, .caseInsensitive]).rawValue
    }

    private static func preReleaseStageRank(_ suffix: String) -> Int {
        if suffix.hasPrefix("rc") { return 3 }
        if suffix.hasPrefix("beta") || suffix.hasPrefix("b") { return 2 }
        if suffix.hasPrefix("alpha") || suffix.hasPrefix("a") { return 1 }
        return 0
    }
}

public class UpdateManager: NSObject, ObservableObject, URLSessionDownloadDelegate {
    public static let shared = UpdateManager()

    public static let githubOwner = "alens"
    public static let githubRepo = "tsrossa"
    public static let expectedBundleIdentifier = "com.yuka.tsrossa.macos"

    public var repoOwner: String { Self.githubOwner }
    public var repoName: String { Self.githubRepo }

    @Published public var isChecking: Bool = false
    @Published public var hasChecked: Bool = false
    @Published public var isDownloading: Bool = false
    @Published public var isExtracting: Bool = false
    @Published public var downloadProgress: Double = 0.0
    @Published public var updateAvailable: Bool = false
    @Published public var latestVersion: String = ""
    @Published public var releaseTitle: String = ""
    @Published public var releaseNotes: String = ""
    @Published public var downloadUrl: URL? = nil
    @Published public var assetFileName: String = ""
    @Published public var statusMessage: String = ""
    @Published public var errorMessage: String? = nil

    private var downloadSession: URLSession?
    private var downloadTask: URLSessionDownloadTask?

    public var currentVersion: String {
        let ver = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "5.4"
        return ver.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    public override init() {
        super.init()
    }

    public func checkForUpdates(userInitiated: Bool = true) {
        guard !isChecking && !isDownloading else { return }
        isChecking = true
        errorMessage = nil
        if userInitiated {
            statusMessage = "Memeriksa pembaruan di GitHub..."
        }

        let urlStr = "https://api.github.com/repos/\(Self.githubOwner)/\(Self.githubRepo)/releases/latest"
        guard let url = URL(string: urlStr) else {
            finishCheckWithError("URL repositori GitHub tidak valid.")
            return
        }

        var request = URLRequest(url: url)
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.setValue("TSRossa-macOS/\(currentVersion)", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 15

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.isChecking = false
                self.hasChecked = true

                if let error = error {
                    if userInitiated { self.errorMessage = "Gagal terhubung: \(error.localizedDescription)" }
                    self.statusMessage = userInitiated ? "Gagal: \(error.localizedDescription)" : ""
                    return
                }

                guard let httpResp = response as? HTTPURLResponse, httpResp.statusCode == 200, let data = data else {
                    if userInitiated { self.errorMessage = "Belum ada rilis publik macOS yang tersedia di GitHub." }
                    self.statusMessage = userInitiated ? "Belum ada rilis baru." : ""
                    return
                }

                self.parseReleaseData(data, manual: userInitiated)
            }
        }.resume()
    }

    private func parseReleaseData(_ data: Data, manual: Bool) {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let tagName = json["tag_name"] as? String else {
            if manual { errorMessage = "Respons JSON rilis tidak valid." }
            return
        }

        let body = json["body"] as? String ?? "Perbaikan performa dan peningkatan stabilitas audio."
        let title = json["name"] as? String ?? "TSRossa \(tagName)"
        var parsedAssets: [GitHubAssetInfo] = []
        if let rawAssets = json["assets"] as? [[String: Any]] {
            for item in rawAssets {
                if let name = item["name"] as? String,
                   let dl = item["browser_download_url"] as? String {
                    parsedAssets.append(GitHubAssetInfo(name: name, browserDownloadUrl: dl))
                }
            }
        }

        self.releaseTitle = title
        if VersionComparator.isNewerVersion(latestTag: tagName, currentVersion: currentVersion) {
            guard let macAsset = VersionComparator.selectMacOSAsset(from: parsedAssets),
                  let assetURL = URL(string: macAsset.browserDownloadUrl) else {
                updateAvailable = false
                if manual {
                    statusMessage = "Rilis \(tagName) ditemukan, namun paket khusus macOS belum tersedia."
                }
                return
            }

            latestVersion = tagName
            releaseNotes = body
            downloadUrl = assetURL
            assetFileName = macAsset.name
            updateAvailable = true
            statusMessage = "Pembaruan \(tagName) siap diunduh (\(macAsset.name))."
        } else {
            updateAvailable = false
            if manual {
                statusMessage = "Anda sudah menggunakan versi terbaru (v\(currentVersion))."
            }
        }
    }

    public func startDownloadAndInstall() {
        downloadAndInstallUpdate()
    }

    public func downloadAndInstallUpdate() {
        guard let url = downloadUrl, !isDownloading else { return }
        isDownloading = true
        isExtracting = false
        downloadProgress = 0.0
        errorMessage = nil
        statusMessage = "Mengunduh \(assetFileName.isEmpty ? "pembaruan" : assetFileName)..."

        let config = URLSessionConfiguration.default
        downloadSession = URLSession(configuration: config, delegate: self, delegateQueue: OperationQueue.main)
        downloadTask = downloadSession?.downloadTask(with: url)
        downloadTask?.resume()
    }

    public func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        if totalBytesExpectedToWrite > 0 {
            DispatchQueue.main.async {
                self.downloadProgress = Double(totalBytesWritten) / Double(totalBytesExpectedToWrite)
            }
        }
    }

    public func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        DispatchQueue.main.async {
            self.isDownloading = false
            self.isExtracting = true
            self.statusMessage = "Memverifikasi paket pembaruan..."
            self.verifyAndStageUpdate(from: location)
        }
    }

    public func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if let error = error {
            DispatchQueue.main.async {
                self.isDownloading = false
                self.isExtracting = false
                self.errorMessage = "Unduhan gagal: \(error.localizedDescription)"
                self.statusMessage = "Gagal mengunduh."
            }
        }
    }

    private func verifyAndStageUpdate(from downloadedFile: URL) {
        let fm = FileManager.default
        guard let cachesRoot = fm.urls(for: .cachesDirectory, in: .userDomainMask).first else {
            errorMessage = "Tidak dapat mengakses direktori Caches pengguna."
            isExtracting = false
            return
        }

        let stageDir = cachesRoot
            .appendingPathComponent("com.yuka.tsrossa.macos.updater", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)

        do {
            try fm.createDirectory(at: stageDir, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
            let archivePath = stageDir.appendingPathComponent(assetFileName.isEmpty ? "update.zip" : assetFileName)
            try fm.moveItem(at: downloadedFile, to: archivePath)

            if archivePath.pathExtension.lowercased() == "dmg" {
                isExtracting = false
                statusMessage = "Membuka installer DMG..."
                NSWorkspace.shared.open(archivePath)
                return
            }

            statusMessage = "Mengekstrak dan memverifikasi tanda tangan digital..."
            let dittoProc = Process()
            dittoProc.executableURL = URL(fileURLWithPath: "/usr/bin/ditto")
            dittoProc.arguments = ["-x", "-k", archivePath.path, stageDir.path]
            try dittoProc.run()
            dittoProc.waitUntilExit()

            guard dittoProc.terminationStatus == 0 else {
                throw NSError(domain: "UpdateManager", code: 1, userInfo: [NSLocalizedDescriptionKey: "Gagal mengekstrak arsip ZIP."])
            }

            let contents = try fm.contentsOfDirectory(at: stageDir, includingPropertiesForKeys: nil)
            guard let extractedApp = contents.first(where: { $0.pathExtension == "app" }) else {
                throw NSError(domain: "UpdateManager", code: 2, userInfo: [NSLocalizedDescriptionKey: "Tidak ada bundle .app di dalam arsip pembaruan."])
            }

            try Self.verifyExtractedAppBundle(at: extractedApp)
            try replaceRunningAppAndRelaunch(with: extractedApp, stageDir: stageDir)
        } catch {
            try? fm.removeItem(at: stageDir)
            isExtracting = false
            errorMessage = error.localizedDescription
            statusMessage = "Gagal: \(error.localizedDescription)"
        }
    }

    public static func verifyExtractedAppBundle(at appURL: URL) throws {
        guard let bundle = Bundle(url: appURL),
              let bundleId = bundle.bundleIdentifier,
              bundleId == expectedBundleIdentifier else {
            throw NSError(
                domain: "UpdateManager",
                code: 3,
                userInfo: [NSLocalizedDescriptionKey: "Bundle identifier pembaruan tidak cocok dengan \(expectedBundleIdentifier)."]
            )
        }

        let codesignProc = Process()
        codesignProc.executableURL = URL(fileURLWithPath: "/usr/bin/codesign")
        codesignProc.arguments = ["--verify", "--deep", "--strict", appURL.path]
        try codesignProc.run()
        codesignProc.waitUntilExit()

        guard codesignProc.terminationStatus == 0 else {
            throw NSError(
                domain: "UpdateManager",
                code: 4,
                userInfo: [NSLocalizedDescriptionKey: "Verifikasi tanda tangan digital (codesign) gagal untuk paket pembaruan."]
            )
        }
    }

    private func replaceRunningAppAndRelaunch(with newAppURL: URL, stageDir: URL) throws {
        let currentAppURL = Bundle.main.bundleURL
        guard currentAppURL.pathExtension == "app" else {
            NSWorkspace.shared.activateFileViewerSelecting([newAppURL])
            statusMessage = "Paket terverifikasi di \(newAppURL.path)"
            isExtracting = false
            return
        }

        let pid = ProcessInfo.processInfo.processIdentifier
        let scriptPath = stageDir.appendingPathComponent("apply_update.sh")
        let scriptContent = """
        #!/bin/bash
        while kill -0 \(pid) 2>/dev/null; do
            sleep 0.2
        done
        rm -rf "\(currentAppURL.path)"
        mv "\(newAppURL.path)" "\(currentAppURL.path)"
        xattr -cr "\(currentAppURL.path)" 2>/dev/null || true
        open "\(currentAppURL.path)"
        rm -rf "\(stageDir.path)"
        """
        try scriptContent.write(to: scriptPath, atomically: true, encoding: .utf8)
        try FileManager.default.setAttributes([.posixPermissions: 0o700], ofItemAtPath: scriptPath.path)

        let runner = Process()
        runner.executableURL = URL(fileURLWithPath: "/bin/bash")
        runner.arguments = [scriptPath.path]
        try runner.run()

        NSApplication.shared.terminate(nil)
    }

    private func finishCheckWithError(_ msg: String) {
        isChecking = false
        errorMessage = msg
        statusMessage = ""
    }
}
