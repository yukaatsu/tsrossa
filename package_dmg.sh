#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$DIR/build"
APP_PATH="$BUILD_DIR/tsrossa.app"
DMG_PATH="$BUILD_DIR/tsrossa-1.6.0.dmg"
STAGE_DIR="$BUILD_DIR/dmg_stage"

if [ ! -d "$APP_PATH" ]; then
    echo "Error: $APP_PATH does not exist. Run build first."
    exit 1
fi

echo "==> Preparing DMG staging area..."
rm -rf "$STAGE_DIR" "$DMG_PATH"
mkdir -p "$STAGE_DIR"
cp -R "$APP_PATH" "$STAGE_DIR/"
ln -s /Applications "$STAGE_DIR/Applications"

echo "==> Creating disk image: $DMG_PATH..."
if command -v diskutil >/dev/null 2>&1; then
    diskutil image create from --volumeName "tsrossa" --format UDZO "$STAGE_DIR" "$DMG_PATH" || \
    hdiutil create -srcfolder "$STAGE_DIR" -volname "tsrossa" -format UDZO "$DMG_PATH"
else
    hdiutil create -srcfolder "$STAGE_DIR" -volname "tsrossa" -format UDZO "$DMG_PATH"
fi

rm -rf "$STAGE_DIR"
echo "==> Successfully created $DMG_PATH!"
