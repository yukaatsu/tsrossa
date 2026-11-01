import Foundation
import AppKit
import Combine

public class UpdateManager: NSObject, ObservableObject, URLSessionDownloadDelegate {
    public static let shared = UpdateManager()
    
    public let repoOwner = "RazerZS-oss"
    public let repoName = "tsrossa"
    
    @Published public var isChecking: Bool = false
    @Published public var hasChecked: Bool = false
    @Published public var updateAvailable: Bool = false
    @Published public var latestVersion: String = ""
    @Published public var releaseTitle: String = ""
    @Published public var releaseNotes: String = ""
    @Published public var downloadUrl: URL? = nil
    @Published public var isDownloading: Bool = false
    @Published public var downloadProgress: Double = 0.0
    @Published public var statusMessage: String = ""
    @Published public var isExtracting: Bool = false
    
    private var downloadSession: URLSession?
    private var downloadTask: URLSessionDownloadTask?
    
    public var currentVersion: String {
        let ver = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.6.0"
        return ver.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    
    public override init() {
        super.init()
    }
    
    public func checkForUpdates(userInitiated: Bool = true) {
        guard !isChecking && !isDownloading else { return }
        
        DispatchQueue.main.async {
            self.isChecking = true
            self.statusMessage = "Checking for updates..."
        }
        
        let urlString = "https://api.github.com/repos/\(repoOwner)/\(repoName)/releases/latest"
        guard let url = URL(string: urlString) else {
            DispatchQueue.main.async {
                self.isChecking = false
                self.statusMessage = "Invalid update URL."
            }
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/vnd.github.v3+json", forHTTPHeaderField: "Accept")
        request.setValue("tsrossa-macos-updater", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 15.0
        
        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            guard let self = self else { return }
            
            DispatchQueue.main.async {
                self.isChecking = false
                self.hasChecked = true
                
                if let error = error {
                    self.statusMessage = "Check failed: \(error.localizedDescription)"
                    return
                }
                
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                    self.statusMessage = "Unable to parse release information."
                    return
                }
                
                // GitHub error or rate limit
                if let message = json["message"] as? String, json["tag_name"] == nil {
                    if message == "Not Found" {
                        self.statusMessage = "No releases published yet on GitHub (v\(self.currentVersion) is active)."
                    } else {
                        self.statusMessage = "GitHub: \(message)"
                    }
                    return
                }
                
                guard let tagName = json["tag_name"] as? String else {
                    self.statusMessage = "No release tag found."
                    return
                }
                
                let rawTag = tagName.trimmingCharacters(in: CharacterSet(charactersIn: "vV \t\n\r"))
                self.latestVersion = rawTag
                self.releaseTitle = (json["name"] as? String) ?? "tsrossa v\(rawTag)"
                self.releaseNotes = (json["body"] as? String) ?? ""
                
                // Locate macos zip asset
                var foundAssetUrl: URL? = nil
                if let assets = json["assets"] as? [[String: Any]] {
                    for asset in assets {
                        if let name = asset["name"] as? String,
                           let downloadStr = asset["browser_download_url"] as? String {
                            let lower = name.lowercased()
                            if lower.hasSuffix(".zip") && (lower.contains("macos") || lower.contains("mac") || lower.contains("tsrossa")) {
                                foundAssetUrl = URL(string: downloadStr)
                                break
                            }
                        }
                    }
                    // Fallback to any zip asset
                    if foundAssetUrl == nil {
                        for asset in assets {
                            if let name = asset["name"] as? String,
                               let downloadStr = asset["browser_download_url"] as? String,
                               name.lowercased().hasSuffix(".zip") {
                                foundAssetUrl = URL(string: downloadStr)
                                break
                            }
                        }
                    }
                }
                self.downloadUrl = foundAssetUrl
                
                let isNewer = self.compareVersions(v1: rawTag, v2: self.currentVersion) > 0
                self.updateAvailable = isNewer
                
                if isNewer {
                    self.statusMessage = "New version v\(rawTag) is available!"
                } else {
                    self.statusMessage = "You're on the latest version (v\(self.currentVersion))."
                }
            }
        }.resume()
    }
    
    private func compareVersions(v1: String, v2: String) -> Int {
        let parts1 = v1.split(separator: ".").compactMap { Int($0) }
        let parts2 = v2.split(separator: ".").compactMap { Int($0) }
        
        let maxCount = max(parts1.count, parts2.count)
        for i in 0..<maxCount {
            let p1 = i < parts1.count ? parts1[i] : 0
            let p2 = i < parts2.count ? parts2[i] : 0
            if p1 > p2 { return 1 }
            if p1 < p2 { return -1 }
        }
        return 0
    }
    
    public func startDownloadAndInstall() {
        guard let url = downloadUrl else {
            statusMessage = "No download package found in release."
            return
        }
        guard !isDownloading else { return }
        
        DispatchQueue.main.async {
            self.isDownloading = true
            self.downloadProgress = 0.0
            self.statusMessage = "Downloading update..."
        }
        
        let config = URLSessionConfiguration.default
        downloadSession = URLSession(configuration: config, delegate: self, delegateQueue: nil)
        downloadTask = downloadSession?.downloadTask(with: url)
        downloadTask?.resume()
    }
    
    // MARK: - URLSessionDownloadDelegate
    
    public func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        if totalBytesExpectedToWrite > 0 {
            let progress = Double(totalBytesWritten) / Double(totalBytesExpectedToWrite)
            DispatchQueue.main.async {
                self.downloadProgress = min(1.0, max(0.0, progress))
                let percent = Int(progress * 100.0)
                self.statusMessage = "Downloading: \(percent)%..."
            }
        }
    }
    
    public func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        DispatchQueue.main.async {
            self.isDownloading = false
            self.isExtracting = true
            self.statusMessage = "Extracting & Installing update..."
        }
        
        let fileManager = FileManager.default
        let tempDir = URL(fileURLWithPath: "/tmp/tsrossa_update", isDirectory: true)
        let zipTarget = tempDir.appendingPathComponent("update.zip")
        let extractDir = tempDir.appendingPathComponent("extracted", isDirectory: true)
        let restartScript = tempDir.appendingPathComponent("restart.sh")
        
        do {
            try? fileManager.removeItem(at: tempDir)
            try fileManager.createDirectory(at: tempDir, withIntermediateDirectories: true, attributes: nil)
            try fileManager.createDirectory(at: extractDir, withIntermediateDirectories: true, attributes: nil)
            try fileManager.copyItem(at: location, to: zipTarget)
            
            // Extract using ditto for full macOS bundle & symlink fidelity
            let ditto = Process()
            ditto.executableURL = URL(fileURLWithPath: "/usr/bin/ditto")
            ditto.arguments = ["-xk", zipTarget.path, extractDir.path]
            try ditto.run()
            ditto.waitUntilExit()
            
            // Locate extracted tsrossa.app
            var extractedAppPath: String? = nil
            if let enumerator = fileManager.enumerator(at: extractDir, includingPropertiesForKeys: [.isDirectoryKey]) {
                for case let item as URL in enumerator {
                    if item.pathExtension == "app" {
                        extractedAppPath = item.path
                        break
                    }
                }
            }
            
            guard let newAppPath = extractedAppPath else {
                DispatchQueue.main.async {
                    self.isExtracting = false
                    self.statusMessage = "Error: tsrossa.app not found in update archive."
                }
                return
            }
            
            // Determine target application path
            let currentAppUrl = Bundle.main.bundleURL
            let targetPath: String
            if currentAppUrl.pathExtension == "app" {
                targetPath = currentAppUrl.path
            } else {
                targetPath = "/Applications/tsrossa.app"
            }
            
            // Prepare restart helper script
            let currentPid = ProcessInfo.processInfo.processIdentifier
            let scriptContent = """
            #!/bin/bash
            set -e
            TARGET="\(targetPath)"
            NEW_APP="\(newAppPath)"
            TARGET_PID="\(currentPid)"
            
            # Wait for running app to exit
            while kill -0 "$TARGET_PID" 2>/dev/null; do
                sleep 0.2
            done
            
            # Replace app bundle
            rm -rf "$TARGET"
            cp -R "$NEW_APP" "$TARGET"
            xattr -cr "$TARGET" 2>/dev/null || true
            
            # Reopen the updated app
            open "$TARGET"
            
            # Cleanup temporary folder
            rm -rf /tmp/tsrossa_update
            """
            
            try scriptContent.write(to: restartScript, atomically: true, encoding: .utf8)
            
            let chmod = Process()
            chmod.executableURL = URL(fileURLWithPath: "/bin/chmod")
            chmod.arguments = ["+x", restartScript.path]
            try chmod.run()
            chmod.waitUntilExit()
            
            DispatchQueue.main.async {
                self.statusMessage = "Update complete! Relaunching..."
                
                // Spawn script in background
                let launcher = Process()
                launcher.executableURL = URL(fileURLWithPath: "/bin/bash")
                launcher.arguments = [restartScript.path]
                try? launcher.run()
                
                // Exit current process so restart script can proceed
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    NSApplication.shared.terminate(nil)
                }
            }
        } catch {
            DispatchQueue.main.async {
                self.isExtracting = false
                self.statusMessage = "Installation failed: \(error.localizedDescription)"
            }
        }
    }
    
    public func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if let error = error {
            DispatchQueue.main.async {
                self.isDownloading = false
                self.isExtracting = false
                self.statusMessage = "Download failed: \(error.localizedDescription)"
            }
        }
    }
}
