#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$DIR/build"
APP_PATH="$BUILD_DIR/tsrossa.app"
DMG_NAME="tsrossa.dmg"
DMG_PATH="$DIR/$DMG_NAME"
STAGE_DIR="$BUILD_DIR/dmg_stage"

if [ ! -d "$APP_PATH" ]; then
    echo "==> $APP_PATH belum ada. Membangun Universal Binary terlebih dahulu..."
    "$DIR/build_mac.sh" --universal
fi

echo "==> [1/4] Menyiapkan Staging Area DMG..."
rm -rf "$STAGE_DIR" "$DMG_PATH" "$BUILD_DIR/$DMG_NAME"
mkdir -p "$STAGE_DIR"

# Salin aplikasi TSRossa ke staging area
cp -R "$APP_PATH" "$STAGE_DIR/TSRossa.app"

# Buat symlink ke folder /Applications agar user mudah drag-and-drop
ln -s /Applications "$STAGE_DIR/Applications"

echo "==> [2/4] Membuat Disk Image (DMG): $DMG_PATH..."
hdiutil create -srcfolder "$STAGE_DIR" -volname "TSRossa" -fs HFS+ \
    -format UDZO -imagekey zlib-level=9 -ov "$DMG_PATH"

# Salin juga ke build/tsrossa.dmg
cp "$DMG_PATH" "$BUILD_DIR/$DMG_NAME"

echo "==> [3/4] Ad-hoc Signing file DMG..."
codesign --force --sign - "$DMG_PATH" 2>/dev/null || true

echo "==> [4/4] Verifikasi Mounting DMG..."
MOUNT_INFO=$(hdiutil attach -nobrowse -readonly "$DMG_PATH")
MOUNT_POINT=$(echo "$MOUNT_INFO" | grep "/Volumes/" | awk '{print $NF}')
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
echo "=========================================================="
echo "  SUKSES! DMG siap didistribusikan:"
echo "  Path: $DMG_PATH ($DMG_SIZE)"
echo "  Arch: Universal Binary (arm64 + x86_64)"
echo "=========================================================="
