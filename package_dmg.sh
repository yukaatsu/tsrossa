#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$DIR/build"
APP_PATH="$BUILD_DIR/tsrossa.app"

if [ ! -d "$APP_PATH" ]; then
    echo "==> $APP_PATH belum ada. Membangun Universal Binary terlebih dahulu..."
    "$DIR/build_mac.sh" --universal
fi

# Dapatkan versi dari Info.plist
APP_VERSION=$(/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" "$APP_PATH/Contents/Info.plist" 2>/dev/null || echo "1.0.0-BETA")
RELEASE_DMG_NAME="tsrossa-v${APP_VERSION}-macos.dmg"
LEGACY_DMG_NAME="tsrossa.dmg"
DMG_PATH="$DIR/$RELEASE_DMG_NAME"
STAGE_DIR="$BUILD_DIR/dmg_stage"

echo "=========================================================="
echo "  TSRossa DMG Packaging - v${APP_VERSION}"
echo "=========================================================="

echo "==> [1/5] Menyiapkan Staging Area DMG..."
rm -rf "$STAGE_DIR" "$DMG_PATH" "$DIR/$LEGACY_DMG_NAME" "$BUILD_DIR/$RELEASE_DMG_NAME" "$BUILD_DIR/$LEGACY_DMG_NAME"
mkdir -p "$STAGE_DIR"

# Salin aplikasi TSRossa ke staging area
cp -R "$APP_PATH" "$STAGE_DIR/TSRossa.app"

# Buat symlink ke folder /Applications agar user mudah drag-and-drop
ln -s /Applications "$STAGE_DIR/Applications"

echo "==> [2/5] Membuat Disk Image (DMG): $DMG_PATH..."
hdiutil create -srcfolder "$STAGE_DIR" -volname "TSRossa" -fs HFS+ \
    -format UDZO -imagekey zlib-level=9 -ov "$DMG_PATH"

# Buat salinan tsrossa.dmg untuk kemudahan alias dan kompatibilitas
cp "$DMG_PATH" "$DIR/$LEGACY_DMG_NAME"
cp "$DMG_PATH" "$BUILD_DIR/$RELEASE_DMG_NAME"
cp "$DMG_PATH" "$BUILD_DIR/$LEGACY_DMG_NAME"

echo "==> [3/5] Ad-hoc Signing file DMG..."
codesign --force --sign - "$DMG_PATH" 2>/dev/null || true
codesign --force --sign - "$DIR/$LEGACY_DMG_NAME" 2>/dev/null || true

echo "==> [4/5] Verifikasi Mounting DMG..."
MOUNT_INFO=$(hdiutil attach -nobrowse -readonly "$DMG_PATH")
MOUNT_POINT=$(echo "$MOUNT_INFO" | grep "/Volumes/" | sed -n 's/.*\(\/Volumes\/.*\)/\1/p')
echo "    -> Berhasil di-mount di: $MOUNT_POINT"
if [ -d "$MOUNT_POINT/TSRossa.app" ] && [ -L "$MOUNT_POINT/Applications" ]; then
    echo "    -> Verifikasi isi DMG valid (TSRossa.app & Applications link ditemukan)."
else
    echo "    -> Peringatan: Isi DMG tidak sesuai ekspektasi!"
fi
hdiutil detach "$MOUNT_POINT" -quiet
echo "    -> Berhasil di-unmount."

# Bersihkan staging directory
rm -rf "$STAGE_DIR"

DMG_SIZE=$(du -h "$DMG_PATH" | cut -f1)
SHA256=$(shasum -a 256 "$DMG_PATH" | cut -d ' ' -f 1)

echo "=========================================================="
echo "  SUKSES! DMG siap didistribusikan ke GitHub Releases:"
echo "  File (Primary): $DMG_PATH ($DMG_SIZE)"
echo "  File (Alias)  : $DIR/$LEGACY_DMG_NAME"
echo "  Versi         : v${APP_VERSION}"
echo "  Arch          : Universal Binary (arm64 + x86_64)"
echo "  SHA256        : $SHA256"
echo "=========================================================="
