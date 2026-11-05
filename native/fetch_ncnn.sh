#!/usr/bin/env bash
# Fetches a prebuilt ncnn release for the target platform and prints the
# NCNN_DIR to use with native/CMakeLists.txt. Run once per build machine.
#
#   bash native/fetch_ncnn.sh <android|windows|linux> [ncnn-version]
#
# The ncnn library is BSD-3-Clause (see licenses.md).
set -euo pipefail

PLATFORM="${1:-android}"
VERSION="${2:-20240410}"
OUT="$(pwd)/native/third_party"
mkdir -p "$OUT"

case "$PLATFORM" in
  android)
    ASSET="ncnn-${VERSION}-android-shared.zip"
    ;;
  windows)
    ASSET="ncnn-${VERSION}-windows-vs2022.zip"
    ;;
  linux)
    ASSET="ncnn-${VERSION}-ubuntu-2204-shared.zip"
    ;;
  *) echo "unknown platform $PLATFORM"; exit 1 ;;
esac

URL="https://github.com/Tencent/ncnn/releases/download/${VERSION}/${ASSET}"
echo "Downloading $URL"
curl -L --retry 3 -o "$OUT/ncnn.zip" "$URL"
mkdir -p "$OUT/ncnn-$PLATFORM"
unzip -o -q "$OUT/ncnn.zip" -d "$OUT/ncnn-$PLATFORM"
rm -f "$OUT/ncnn.zip"
echo "NCNN_DIR=$OUT/ncnn-$PLATFORM/$ASSET%.zip"
ls "$OUT/ncnn-$PLATFORM"
echo "Done. Pass this dir as -DNCNN_DIR when configuring native/CMakeLists.txt."
