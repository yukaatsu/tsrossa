#!/bin/bash
set -euo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BUILD_DIR="$DIR/build"
APP_NAME="tsrossa"
APP_BUNDLE="$BUILD_DIR/$APP_NAME.app"
CONTENTS_DIR="$APP_BUNDLE/Contents"
MACOS_DIR="$CONTENTS_DIR/MacOS"
FRAMEWORKS_DIR="$CONTENTS_DIR/Frameworks"
RESOURCES_DIR="$CONTENTS_DIR/Resources"

ARCH_FLAGS=""
SWIFT_TARGET_FLAGS=()
if [[ "${1:-}" == "--universal" ]]; then
    echo "==> Mode Universal Binary (arm64 + x86_64) aktif"
    ARCH_FLAGS="-arch arm64 -arch x86_64"
    SWIFT_TARGET_FLAGS=(-target arm64-apple-macos13.0 -target x86_64-apple-macos13.0)
else
    HOST_ARCH="$(uname -m)"
    ARCH_FLAGS="-arch $HOST_ARCH"
    SWIFT_TARGET_FLAGS=(-target "${HOST_ARCH}-apple-macos13.0")
fi

echo "==> [1/5] Membersihkan direktori build..."
rm -rf "$BUILD_DIR"
mkdir -p "$MACOS_DIR" "$FRAMEWORKS_DIR" "$RESOURCES_DIR" "$BUILD_DIR/ModuleCache"

echo "==> [2/5] Mengompilasi C/C++ Native Audio Engine..."
clang -c -O3 -ffast-math -mmacosx-version-min=13.0 $ARCH_FLAGS \
    -I"$DIR/src/nativeMain/cpp" \
    "$DIR/src/nativeMain/cpp/dr_impl.c" \
    -o "$BUILD_DIR/dr_impl.o"

clang++ -c -std=c++17 -O3 -ffast-math -mmacosx-version-min=13.0 $ARCH_FLAGS \
    -I"$DIR/src/nativeMain/cpp" \
    "$DIR/src/nativeMain/cpp/CoreAudioSink.cpp" \
    -o "$BUILD_DIR/CoreAudioSink.o"

clang++ -c -std=c++17 -O3 -ffast-math -mmacosx-version-min=13.0 $ARCH_FLAGS \
    -I"$DIR/src/nativeMain/cpp" \
    "$DIR/src/nativeMain/cpp/TsrossaAudioBridge.cpp" \
    -o "$BUILD_DIR/TsrossaAudioBridge.o"

libtool -static -o "$BUILD_DIR/libtsrossa_audio.a" \
    "$BUILD_DIR/dr_impl.o" \
    "$BUILD_DIR/CoreAudioSink.o" \
    "$BUILD_DIR/TsrossaAudioBridge.o"

echo "==> [3/5] Mengompilasi Swift UI & Application Layer..."
SWIFT_FILES=()
while IFS= read -r -d '' file; do
    SWIFT_FILES+=("$file")
done < <(find "$DIR/macos" -name "*.swift" -print0 | sort -z)

swiftc -parse-as-library -O "${SWIFT_TARGET_FLAGS[@]}" \
    -module-cache-path "$BUILD_DIR/ModuleCache" \
    -import-objc-header "$DIR/src/nativeMain/cpp/TsrossaAudioBridge.h" \
    "${SWIFT_FILES[@]}" \
    "$BUILD_DIR/libtsrossa_audio.a" \
    -lc++ -lsqlite3 \
    -framework Cocoa \
    -framework SwiftUI \
    -framework CoreAudio \
    -framework AudioToolbox \
    -framework AudioUnit \
    -framework Accelerate \
    -framework UniformTypeIdentifiers \
    -framework MediaPlayer \
    -framework IOKit \
    -o "$MACOS_DIR/$APP_NAME"

echo "==> [4/5] Menyalin Resources & Membuat Info.plist..."
if [ -f "$DIR/macos/Resources/ic_launcher.png" ]; then
    # Generate macOS HIG Squircle PNG
    swift - "$DIR/macos/Resources/ic_launcher.png" "$BUILD_DIR/mac_squircle_1024.png" <<'EOF'
import AppKit

let args = CommandLine.arguments
guard args.count >= 3, let raw = NSImage(contentsOfFile: args[1]) else { exit(1) }
let canvasSize: CGFloat = 1024
let targetSize = NSSize(width: canvasSize, height: canvasSize)
let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: 1024, pixelsHigh: 1024, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
let ctx = NSGraphicsContext.current!.cgContext
ctx.setAllowsAntialiasing(true)
ctx.setShouldAntialias(true)
ctx.interpolationQuality = .high

let margin = canvasSize * 0.09765625
let bodySize = canvasSize - (margin * 2.0)
let bodyRect = NSRect(x: margin, y: margin, width: bodySize, height: bodySize)
let cornerRadius = bodySize * 0.225

// Drop shadow
ctx.saveGState()
let shadowOffset = CGSize(width: 0, height: -canvasSize * 0.018)
let shadowBlur = canvasSize * 0.045
ctx.setShadow(offset: shadowOffset, blur: shadowBlur, color: NSColor(calibratedWhite: 0.0, alpha: 0.38).cgColor)
let shadowPath = NSBezierPath(roundedRect: bodyRect, xRadius: cornerRadius, yRadius: cornerRadius)
NSColor.black.setFill()
shadowPath.fill()
ctx.restoreGState()

// Clip & draw
ctx.saveGState()
let clipPath = NSBezierPath(roundedRect: bodyRect, xRadius: cornerRadius, yRadius: cornerRadius)
clipPath.addClip()
raw.draw(in: bodyRect, from: NSRect(origin: .zero, size: raw.size), operation: .sourceOver, fraction: 1.0)
let strokePath = NSBezierPath(roundedRect: bodyRect.insetBy(dx: 1.5, dy: 1.5), xRadius: cornerRadius - 1.5, yRadius: cornerRadius - 1.5)
strokePath.lineWidth = 2.0
NSColor(calibratedWhite: 1.0, alpha: 0.22).setStroke()
strokePath.stroke()
ctx.restoreGState()

NSGraphicsContext.restoreGraphicsState()
if let pngData = rep.representation(using: .png, properties: [:]) {
    try? pngData.write(to: URL(fileURLWithPath: args[2]))
}
EOF

    BASE_ICON="$BUILD_DIR/mac_squircle_1024.png"
    if [ ! -f "$BASE_ICON" ]; then
        BASE_ICON="$DIR/macos/Resources/ic_launcher.png"
    fi
    cp "$BASE_ICON" "$RESOURCES_DIR/AppIcon.png"
    cp "$DIR/macos/Resources/ic_launcher.png" "$RESOURCES_DIR/ic_launcher.png"
    
    # Generate AppIcon.icns
    ICONSET_DIR="$BUILD_DIR/AppIcon.iconset"
    mkdir -p "$ICONSET_DIR"
    sips -z 16 16     "$BASE_ICON" --out "$ICONSET_DIR/icon_16x16.png" >/dev/null 2>&1 || true
    sips -z 32 32     "$BASE_ICON" --out "$ICONSET_DIR/icon_16x16@2x.png" >/dev/null 2>&1 || true
    sips -z 32 32     "$BASE_ICON" --out "$ICONSET_DIR/icon_32x32.png" >/dev/null 2>&1 || true
    sips -z 64 64     "$BASE_ICON" --out "$ICONSET_DIR/icon_32x32@2x.png" >/dev/null 2>&1 || true
    sips -z 128 128   "$BASE_ICON" --out "$ICONSET_DIR/icon_128x128.png" >/dev/null 2>&1 || true
    sips -z 256 256   "$BASE_ICON" --out "$ICONSET_DIR/icon_128x128@2x.png" >/dev/null 2>&1 || true
    sips -z 256 256   "$BASE_ICON" --out "$ICONSET_DIR/icon_256x256.png" >/dev/null 2>&1 || true
    sips -z 512 512   "$BASE_ICON" --out "$ICONSET_DIR/icon_256x256@2x.png" >/dev/null 2>&1 || true
    sips -z 512 512   "$BASE_ICON" --out "$ICONSET_DIR/icon_512x512.png" >/dev/null 2>&1 || true
    sips -z 1024 1024 "$BASE_ICON" --out "$ICONSET_DIR/icon_512x512@2x.png" >/dev/null 2>&1 || true
    iconutil -c icns "$ICONSET_DIR" -o "$RESOURCES_DIR/AppIcon.icns" 2>/dev/null || true
    rm -rf "$ICONSET_DIR"
fi
if [ -f "$DIR/macos/Resources/ic_launcher_foreground.png" ]; then
    cp "$DIR/macos/Resources/ic_launcher_foreground.png" "$RESOURCES_DIR/ic_launcher_foreground.png"
fi

if [ -f "$DIR/macos/Info.plist" ]; then
    cp "$DIR/macos/Info.plist" "$CONTENTS_DIR/Info.plist"
else
    cat > "$CONTENTS_DIR/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>$APP_NAME</string>
    <key>CFBundleIconFile</key>
    <string>AppIcon.icns</string>
    <key>CFBundleIdentifier</key>
    <string>com.yuka.tsrossa.macos</string>
    <key>CFBundleName</key>
    <string>TSRossa</string>
    <key>CFBundleDisplayName</key>
    <string>TSRossa Audio Player</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>5.4</string>
    <key>CFBundleVersion</key>
    <string>62</string>
    <key>LSMinimumSystemVersion</key>
    <string>13.0</string>
    <key>NSHighResolutionCapable</key>
    <true/>
</dict>
</plist>
EOF
fi

echo "==> [5/5] Ad-hoc Code Signing Bundle..."
codesign --force --deep --sign - "$APP_BUNDLE"

echo "==> Build selesai: $APP_BUNDLE"
