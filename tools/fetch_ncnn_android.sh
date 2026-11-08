#!/usr/bin/env bash
# RealScale-Pro — fetch prebuilt ncnn for Android (arm64-v8a primary).
#
# Downloads the official Tencent ncnn Android prebuilt package (Vulkan
# build, so GPU detection/inference is available at runtime with CPU
# fallback) and extracts the static libraries needed to link
# librealscale_native.so in the Gradle externalNativeBuild step.
#
# Pinned release: ncnn-20250503-android-vulkan (see
# docs/native-engine-design.md). Verify a newer release on
# https://github.com/Tencent/ncnn/releases before changing NCNN_ANDROID_VERSION;
# release assets are named ncnn-<date>-android-vulkan.zip and contain one
# subdirectory per ABI.
#
# Environment overrides:
#   NCNN_ANDROID_VERSION  release tag (default 20250503)
#   NCNN_ANDROID_ARCHS    space-separated ABI list (default: arm64-v8a)
#                         e.g. "arm64-v8a armeabi-v7a x86_64" for wider APKs
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/../third_party/ncnn-android"
BASE_URL="https://github.com/Tencent/ncnn/releases/download"
VERSION="${NCNN_ANDROID_VERSION:-20250503}"
ARCHS="${NCNN_ANDROID_ARCHS:-arm64-v8a}"
ZIP="ncnn-${VERSION}-android-vulkan"
PKG_INNER="ncnn-${VERSION}-android-vulkan"

mkdir -p "$DEST"
for ARCH in $ARCHS; do
  if [ -d "$DEST/$ARCH/lib" ]; then
    echo ">> $ARCH already present"
    continue
  fi
  if [ ! -f "$DEST/$ZIP.zip" ]; then
    echo ">> downloading $ZIP.zip (contains all ABIs)"
    curl -fSL --retry 3 -o "$DEST/$ZIP.zip" "$BASE_URL/$VERSION/$ZIP.zip"
  fi
  # Extract only the requested ABI subdirectory and normalize the layout
  # to $DEST/<abi>/{include,lib} (a CMake install tree for find_package).
  unzip -o -q "$DEST/$ZIP.zip" "$PKG_INNER/$ARCH/*" -d "$DEST/_pkg"
  mv "$DEST/_pkg/$PKG_INNER/$ARCH" "$DEST/$ARCH"
  rm -rf "$DEST/_pkg"
  echo ">> extracted $DEST/$ARCH"
done
rm -f "$DEST/$ZIP.zip"
echo ">> done. Gradle expects ncnn at: $DEST/<abi> (CMake install tree)"
