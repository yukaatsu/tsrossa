#!/bin/bash
set -euo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
APP_BUNDLE="$DIR/build/tsrossa.app"
DEST="/Applications/TSRossa.app"

if [ ! -d "$APP_BUNDLE" ]; then
    echo "==> Bundle tsrossa.app belum ditemukan, menjalankan build terlebih dahulu..."
    "$DIR/build_mac.sh"
fi

echo "==> Memasang TSRossa ke $DEST..."
rm -rf "$DEST"
cp -R "$APP_BUNDLE" "$DEST"

# Hapus quarantine attribute bila ada
xattr -dr com.apple.quarantine "$DEST" 2>/dev/null || true

echo "==> Berhasil! TSRossa Audio Player telah terpasang di /Applications."
echo "==> Anda dapat membukanya melalui Spotlight (Cmd + Space ketik 'TSRossa') atau Launchpad."
