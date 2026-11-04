#!/bin/bash
set -euo pipefail

echo "==> Memulai proses uninstall TSRossa Audio Player..."

# 0. Hentikan proses aplikasi jika sedang berjalan di memori
echo "  [x] Menghentikan proses TSRossa..."
killall tsrossa 2>/dev/null || true
pkill -f "TSRossa.app" 2>/dev/null || true
sleep 0.5

# 1. Hapus bundle aplikasi dari /Applications
if [ -d "/Applications/TSRossa.app" ]; then
    echo "  [x] Menghapus /Applications/TSRossa.app"
    rm -rf "/Applications/TSRossa.app"
fi

# 2. Hapus preferensi UserDefaults
echo "  [x] Menghapus preferensi UserDefaults com.yuka.tsrossa.macos"
defaults delete com.yuka.tsrossa.macos 2>/dev/null || true

# 3. Hapus database SQLite pustaka musik
APP_SUPPORT="$HOME/Library/Application Support/tsrossa"
if [ -d "$APP_SUPPORT" ]; then
    echo "  [x] Menghapus cache database $APP_SUPPORT"
    rm -rf "$APP_SUPPORT"
fi

# 4. Hapus thumbnail cache cover art
CACHES_DIR="$HOME/Library/Caches/com.yuka.tsrossa.macos"
if [ -d "$CACHES_DIR" ]; then
    echo "  [x] Menghapus thumbnail cache $CACHES_DIR"
    rm -rf "$CACHES_DIR"
fi

echo "==> Selesai! TSRossa telah berhasil di-uninstall secara bersih dari Mac Anda."
