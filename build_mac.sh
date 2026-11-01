#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$DIR/.." && pwd)"
BUILD_DIR="$DIR/build"

echo "==> Building native CoreAudio & FLAC/WAV engine (Apple Silicon arm64)..."
mkdir -p "$BUILD_DIR"
clang++ -dynamiclib -std=c++17 -O2 \
    -install_name @rpath/libtsrossa-mac.dylib \
    -I "$ROOT_DIR/app/src/main/cpp" \
    -I "$DIR/src/nativeMain/cpp" \
    -framework CoreAudio -framework AudioToolbox -framework CoreFoundation \
    "$DIR/src/nativeMain/cpp/dr_impl.c" \
    "$DIR/src/nativeMain/cpp/CoreAudioSink.cpp" \
    "$DIR/src/nativeMain/cpp/TsrossaAudioBridge.cpp" \
    -o "$BUILD_DIR/libtsrossa-mac.dylib"

echo "==> Compiling tsrossa macOS application..."
mkdir -p "$BUILD_DIR/ModuleCache"
swiftc -parse-as-library \
    -module-cache-path "$BUILD_DIR/ModuleCache" \
    -import-objc-header "$DIR/src/nativeMain/cpp/TsrossaAudioBridge.h" \
    -L "$BUILD_DIR" -ltsrossa-mac \
    -Xlinker -rpath -Xlinker @executable_path/../Frameworks \
    -Xlinker -rpath -Xlinker @loader_path \
    "$DIR/macos/Sources"/*.swift \
    -o "$BUILD_DIR/tsrossa"

echo "==> Bundling tsrossa.app..."
rm -rf "$BUILD_DIR/tsrossa.app"
mkdir -p "$BUILD_DIR/tsrossa.app/Contents/MacOS"
mkdir -p "$BUILD_DIR/tsrossa.app/Contents/Frameworks"
mkdir -p "$BUILD_DIR/tsrossa.app/Contents/Resources"

cp "$BUILD_DIR/tsrossa" "$BUILD_DIR/tsrossa.app/Contents/MacOS/"
cp "$BUILD_DIR/libtsrossa-mac.dylib" "$BUILD_DIR/tsrossa.app/Contents/Frameworks/"
cp "$DIR/macos/Info.plist" "$BUILD_DIR/tsrossa.app/Contents/"

# Copy authentic Android logo assets & compile native macOS AppIcon.icns
ANDROID_ICON="$ROOT_DIR/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
ANDROID_ICON_ROUND="$ROOT_DIR/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png"
ANDROID_ICON_FG="$ROOT_DIR/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png"

if [ -f "$ANDROID_ICON" ]; then
    cp "$ANDROID_ICON" "$BUILD_DIR/tsrossa.app/Contents/Resources/ic_launcher.png"
    cp "$ANDROID_ICON_ROUND" "$BUILD_DIR/tsrossa.app/Contents/Resources/ic_launcher_round.png" 2>/dev/null || true
    cp "$ANDROID_ICON_FG" "$BUILD_DIR/tsrossa.app/Contents/Resources/ic_launcher_foreground.png" 2>/dev/null || true

    ICONSET_DIR="$BUILD_DIR/AppIcon.iconset"
    rm -rf "$ICONSET_DIR"
    mkdir -p "$ICONSET_DIR"
    sips -z 16 16     "$ANDROID_ICON" --out "$ICONSET_DIR/icon_16x16.png" >/dev/null 2>&1
    sips -z 32 32     "$ANDROID_ICON" --out "$ICONSET_DIR/icon_16x16@2x.png" >/dev/null 2>&1
    sips -z 32 32     "$ANDROID_ICON" --out "$ICONSET_DIR/icon_32x32.png" >/dev/null 2>&1
    sips -z 64 64     "$ANDROID_ICON" --out "$ICONSET_DIR/icon_32x32@2x.png" >/dev/null 2>&1
    sips -z 128 128   "$ANDROID_ICON" --out "$ICONSET_DIR/icon_128x128.png" >/dev/null 2>&1
    sips -z 256 256   "$ANDROID_ICON" --out "$ICONSET_DIR/icon_128x128@2x.png" >/dev/null 2>&1
    sips -z 256 256   "$ANDROID_ICON" --out "$ICONSET_DIR/icon_256x256.png" >/dev/null 2>&1
    sips -z 512 512   "$ANDROID_ICON" --out "$ICONSET_DIR/icon_256x256@2x.png" >/dev/null 2>&1
    sips -z 512 512   "$ANDROID_ICON" --out "$ICONSET_DIR/icon_512x512.png" >/dev/null 2>&1
    sips -z 1024 1024 "$ANDROID_ICON" --out "$ICONSET_DIR/icon_512x512@2x.png" >/dev/null 2>&1
    iconutil -c icns "$ICONSET_DIR" -o "$BUILD_DIR/tsrossa.app/Contents/Resources/AppIcon.icns" || true
    rm -rf "$ICONSET_DIR"
fi

codesign --force --deep -s - "$BUILD_DIR/tsrossa.app"

VERSION=$(/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" "$DIR/macos/Info.plist" 2>/dev/null || echo "1.6.0")

echo "==> Packaging tsrossa-${VERSION}-macos.zip..."
(cd "$BUILD_DIR" && rm -f "tsrossa-${VERSION}-macos.zip" "tsrossa-macos.zip" && zip -r -y "tsrossa-${VERSION}-macos.zip" tsrossa.app && ln -s "tsrossa-${VERSION}-macos.zip" tsrossa-macos.zip)

if [ "$1" == "--install" ] || [ "$INSTALL_APP" == "1" ]; then
    echo "==> Installing to /Applications/tsrossa.app..."
    rm -rf /Applications/tsrossa.app
    cp -R "$BUILD_DIR/tsrossa.app" /Applications/tsrossa.app
    xattr -cr /Applications/tsrossa.app 2>/dev/null || true
    echo "    Installed successfully to /Applications/tsrossa.app"
fi

echo "==> Build complete! Output located at:"
echo "    App: $BUILD_DIR/tsrossa.app"
echo "    Zip: $BUILD_DIR/tsrossa-${VERSION}-macos.zip"
