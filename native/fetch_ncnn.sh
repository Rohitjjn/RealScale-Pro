#!/usr/bin/env bash
# Fetches pinned prebuilt Tencent ncnn (BSD-3-Clause) binaries into
# native/third_party/ncnn-<platform>. Used by CI and local native builds.
set -euo pipefail

NCNN_VER="20240410"
PLATFORM="${1:-linux}" # linux | android | windows
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$SCRIPT_DIR/third_party"
DEST="$ROOT/ncnn-$PLATFORM"

if [ -f "$DEST/lib/cmake/ncnn/ncnnConfig.cmake" ] || \
   [ -f "$DEST/arm64-v8a/lib/cmake/ncnn/ncnnConfig.cmake" ]; then
  echo "ncnn ($PLATFORM) already present at $DEST"
  exit 0
fi

mkdir -p "$ROOT"

case "$PLATFORM" in
  linux)
    ASSET="ncnn-${NCNN_VER}-ubuntu-2204-shared.zip"
    ;;
  android)
    ASSET="ncnn-${NCNN_VER}-android-vulkan.zip"
    ;;
  windows)
    ASSET="ncnn-${NCNN_VER}-windows-vs2022-shared.zip"
    ;;
  *)
    echo "Unknown platform: $PLATFORM" >&2
    exit 2
    ;;
esac

URL="https://github.com/Tencent/ncnn/releases/download/${NCNN_VER}/${ASSET}"
ZIP="$ROOT/$ASSET"
EXTRACT_DIR="$ROOT/${ASSET%.zip}"

echo "Downloading $URL"
curl -fL --retry 3 --retry-delay 2 "$URL" -o "$ZIP"
rm -rf "$DEST" "$EXTRACT_DIR"
unzip -q "$ZIP" -d "$ROOT"
mv "$EXTRACT_DIR" "$DEST"
rm -f "$ZIP"
echo "Installed ncnn ($PLATFORM) -> $DEST"
