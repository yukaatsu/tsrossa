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
    cp "$DIR/macos/Resources/ic_launcher.png" "$RESOURCES_DIR/AppIcon.png"
    cp "$DIR/macos/Resources/ic_launcher.png" "$RESOURCES_DIR/ic_launcher.png"
    
    # Generate AppIcon.icns
    ICONSET_DIR="$BUILD_DIR/AppIcon.iconset"
    mkdir -p "$ICONSET_DIR"
    sips -z 16 16     "$DIR/macos/Resources/ic_launcher.png" --out "$ICONSET_DIR/icon_16x16.png" >/dev/null 2>&1 || true
    sips -z 32 32     "$DIR/macos/Resources/ic_launcher.png" --out "$ICONSET_DIR/icon_16x16@2x.png" >/dev/null 2>&1 || true
    sips -z 32 32     "$DIR/macos/Resources/ic_launcher.png" --out "$ICONSET_DIR/icon_32x32.png" >/dev/null 2>&1 || true
    sips -z 64 64     "$DIR/macos/Resources/ic_launcher.png" --out "$ICONSET_DIR/icon_32x32@2x.png" >/dev/null 2>&1 || true
    sips -z 128 128   "$DIR/macos/Resources/ic_launcher.png" --out "$ICONSET_DIR/icon_128x128.png" >/dev/null 2>&1 || true
    sips -z 256 256   "$DIR/macos/Resources/ic_launcher.png" --out "$ICONSET_DIR/icon_128x128@2x.png" >/dev/null 2>&1 || true
    sips -z 256 256   "$DIR/macos/Resources/ic_launcher.png" --out "$ICONSET_DIR/icon_256x256.png" >/dev/null 2>&1 || true
    sips -z 512 512   "$DIR/macos/Resources/ic_launcher.png" --out "$ICONSET_DIR/icon_256x256@2x.png" >/dev/null 2>&1 || true
    sips -z 512 512   "$DIR/macos/Resources/ic_launcher.png" --out "$ICONSET_DIR/icon_512x512.png" >/dev/null 2>&1 || true
    sips -z 1024 1024 "$DIR/macos/Resources/ic_launcher.png" --out "$ICONSET_DIR/icon_512x512@2x.png" >/dev/null 2>&1 || true
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
