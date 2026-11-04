import Foundation
import AppKit
import CommonCrypto
import CoreImage

public final class AlbumArtCache {
    public static let shared = AlbumArtCache()

    private let memoryCache = NSCache<NSString, NSImage>()
    private let thumbnailMemoryCache = NSCache<NSString, NSImage>()
    private let blurredMemoryCache = NSCache<NSString, NSImage>()
    private let fileManager = FileManager.default
    private let cacheDirectory: URL
    private var missingPaths = Set<String>()
    private var inFlightPaths = Set<String>()
    private let lock = NSLock()
    private lazy var ciContext = CIContext(options: [.useSoftwareRenderer: false])

    private init() {
        memoryCache.countLimit = 350
        thumbnailMemoryCache.countLimit = 3000
        blurredMemoryCache.countLimit = 150
        if let cachesURL = fileManager.urls(for: .cachesDirectory, in: .userDomainMask).first {
            cacheDirectory = cachesURL.appendingPathComponent("com.yuka.tsrossa.macos/art_thumbs", isDirectory: true)
        } else {
            cacheDirectory = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("art_thumbs", isDirectory: true)
        }
        try? fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
    }

    public func sha256Hex(_ string: String) -> String {
        guard let data = string.data(using: .utf8) else { return UUID().uuidString }
        var digest = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        data.withUnsafeBytes {
            _ = CC_SHA256($0.baseAddress, CC_LONG(data.count), &digest)
        }
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    public func thumbnailImage(for trackPath: String, knownThumbPath: String? = nil) -> NSImage? {
        let key = NSString(string: trackPath)
        if let cached = memoryCache.object(forKey: key) {
            return cached
        }

        lock.lock()
        let isKnownMissing = missingPaths.contains(trackPath)
        lock.unlock()

        if isKnownMissing && (knownThumbPath == nil || knownThumbPath?.isEmpty == true) {
            return nil
        }

        // 1. Try known thumbnail path if provided
        if let path = knownThumbPath, !path.isEmpty {
            if fileManager.fileExists(atPath: path), let img = NSImage(contentsOfFile: path) {
                memoryCache.setObject(img, forKey: key)
                lock.lock()
                missingPaths.remove(trackPath)
                lock.unlock()
                return img
            }
        }

        // 2. Try SHA256 hashed filename in disk cache
        let hash = sha256Hex(trackPath)
        let diskURL = cacheDirectory.appendingPathComponent("\(hash).jpg")
        if fileManager.fileExists(atPath: diskURL.path), let img = NSImage(contentsOf: diskURL) {
            memoryCache.setObject(img, forKey: key)
            lock.lock()
            missingPaths.remove(trackPath)
            lock.unlock()
            return img
        }

        // 3. Negative cache: mark as missing to skip subsequent disk probes
        lock.lock()
        missingPaths.insert(trackPath)
        lock.unlock()
        return nil
    }

    /// O(1) in-memory non-blocking thumbnail accessor for 120Hz smooth scrolling.
    /// Never hits the disk synchronously on the Main Thread.
    public func fastMemoryThumbnail(for trackPath: String, knownThumbPath: String? = nil) -> NSImage? {
        let key = NSString(string: trackPath)
        if let thumb = thumbnailMemoryCache.object(forKey: key) {
            return thumb
        }
        if let full = memoryCache.object(forKey: key) {
            return full
        }

        lock.lock()
        let isMissing = missingPaths.contains(trackPath)
        let inFlight = inFlightPaths.contains(trackPath)
        if !isMissing && !inFlight {
            inFlightPaths.insert(trackPath)
            lock.unlock()
            DispatchQueue.global(qos: .utility).async { [weak self] in
                guard let self = self else { return }
                _ = self.thumbnailImage(for: trackPath, knownThumbPath: knownThumbPath)
                self.lock.lock()
                self.inFlightPaths.remove(trackPath)
                self.lock.unlock()
            }
        } else {
            lock.unlock()
        }
        return nil
    }

    /// Strictly non-blocking O(1) in-memory lookup for SwiftUI render loop.
    /// If not in blurredMemoryCache, returns cached thumbnail while triggering background blur generation.
    public func cachedBlurredCanvasImage(for trackPath: String, knownThumbPath: String? = nil) -> NSImage? {
        let key = NSString(string: trackPath)
        if let cached = blurredMemoryCache.object(forKey: key) {
            return cached
        }
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            _ = self?.blurredCanvasImage(for: trackPath, knownThumbPath: knownThumbPath)
        }
        return fastMemoryThumbnail(for: trackPath, knownThumbPath: knownThumbPath)
    }

    /// O(1) in-memory pre-blurred 64x64 artwork accessor for 120Hz smooth window resize & Fit-to-Screen.
    /// Completely eliminates GPU blur shader cost and Metal texture re-allocations.
    public func blurredCanvasImage(for trackPath: String, knownThumbPath: String? = nil) -> NSImage? {
        let key = NSString(string: trackPath)
        if let cached = blurredMemoryCache.object(forKey: key) {
            return cached
        }

        guard let sourceImage = thumbnailImage(for: trackPath, knownThumbPath: knownThumbPath) else {
            return nil
        }

        // 1. Draw source into small 64x64 bitmap rep
        guard let smallRep = createThumbnailRep(from: sourceImage, targetSize: 64),
              let cgSource = smallRep.cgImage else {
            return nil
        }

        let ciInput = CIImage(cgImage: cgSource)
        let clampFilter = CIFilter(name: "CIAffineClamp")
        clampFilter?.setValue(ciInput, forKey: kCIInputImageKey)
        clampFilter?.setValue(CGAffineTransform.identity, forKey: "inputTransform")
        let clamped = clampFilter?.outputImage ?? ciInput

        guard let blurFilter = CIFilter(name: "CIGaussianBlur") else { return nil }
        blurFilter.setValue(clamped, forKey: kCIInputImageKey)
        blurFilter.setValue(10.0, forKey: kCIInputRadiusKey)

        guard let blurredCI = blurFilter.outputImage,
              let blurredCG = ciContext.createCGImage(blurredCI, from: CGRect(x: 0, y: 0, width: 64, height: 64)) else {
            return nil
        }

        let finalImage = NSImage(cgImage: blurredCG, size: NSSize(width: 64, height: 64))
        blurredMemoryCache.setObject(finalImage, forKey: key)
        return finalImage
    }

    /// Background pre-warming of pre-decoded thumbnails and pre-blurred canvas into RAM when library is loaded.
    public func prewarmThumbnails(for trackPaths: [(path: String, thumbPath: String?)]) {
        DispatchQueue.global(qos: .utility).async { [weak self] in
            guard let self = self else { return }
            for item in trackPaths {
                let key = NSString(string: item.path)
                if self.thumbnailMemoryCache.object(forKey: key) == nil {
                    if let img = self.thumbnailImage(for: item.path, knownThumbPath: item.thumbPath) {
                        self.thumbnailMemoryCache.setObject(img, forKey: key)
                    }
                }
                if self.blurredMemoryCache.object(forKey: key) == nil {
                    _ = self.blurredCanvasImage(for: item.path, knownThumbPath: item.thumbPath)
                }
            }
        }
    }

    public func storeAndProcessArtwork(rawData: Data?, for trackURL: URL) -> (thumbnailPath: String?, dominantHex: String?) {
        var image: NSImage? = nil

        // 1. Coba decode rawData jika ada
        if let data = rawData, let img = NSImage(data: data) {
            image = img
        }

        // 2. Jika tidak ada tag gambar tersemat, cari file gambar di folder lokal
        if image == nil {
            let folder = trackURL.deletingLastPathComponent()
            let commonNames = ["cover.jpg", "cover.png", "folder.jpg", "front.jpg", "front.png", "artwork.jpg"]
            for name in commonNames {
                let imgURL = folder.appendingPathComponent(name)
                if fileManager.fileExists(atPath: imgURL.path), let img = NSImage(contentsOf: imgURL) {
                    image = img
                    break
                }
            }
        }

        guard let sourceImage = image else {
            return (nil, nil)
        }

        let accent = ColorContrastHelper.extractDominantAccent(from: sourceImage)
        let hash = sha256Hex(trackURL.path)
        let thumbURL = cacheDirectory.appendingPathComponent("\(hash).jpg")

        // Resize down to max 320x320 for disk caching to save storage and RAM
        if let rep = createThumbnailRep(from: sourceImage, targetSize: 320),
           let jpgData = rep.representation(using: .jpeg, properties: [.compressionFactor: 0.85]) {
            try? jpgData.write(to: thumbURL)
            if let savedThumb = NSImage(data: jpgData) {
                memoryCache.setObject(savedThumb, forKey: NSString(string: trackURL.path))
                lock.lock()
                missingPaths.remove(trackURL.path)
                lock.unlock()
            }
            return (thumbURL.path, accent.hexString)
        }

        return (nil, accent.hexString)
    }

    private func createThumbnailRep(from image: NSImage, targetSize: CGFloat) -> NSBitmapImageRep? {
        let size = image.size
        guard size.width > 0 && size.height > 0 else { return nil }

        let aspect = size.width / size.height
        var w = targetSize
        var h = targetSize
        if aspect > 1.0 {
            h = targetSize / aspect
        } else {
            w = targetSize * aspect
        }

        guard let rep = NSBitmapImageRep(
            bitmapDataPlanes: nil,
            pixelsWide: Int(w),
            pixelsHigh: Int(h),
            bitsPerSample: 8,
            samplesPerPixel: 4,
            hasAlpha: true,
            isPlanar: false,
            colorSpaceName: .deviceRGB,
            bytesPerRow: 0,
            bitsPerPixel: 0
        ) else { return nil }

        NSGraphicsContext.saveGraphicsState()
        let context = NSGraphicsContext(bitmapImageRep: rep)
        NSGraphicsContext.current = context
        image.draw(in: NSRect(x: 0, y: 0, width: w, height: h), from: .zero, operation: .copy, fraction: 1.0)
        NSGraphicsContext.restoreGraphicsState()

        return rep
    }
}
