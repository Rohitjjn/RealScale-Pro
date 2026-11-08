#!/usr/bin/env bash
# RealScale-Pro — fetch prebuilt ncnn for Android, one directory per ABI.
#
# Downloads the official Tencent ncnn Android prebuilt package (Vulkan build,
# so the GPU probe/detection works at runtime while inference stays on the
# verified CPU path unless GPU is explicitly requested) and normalises it to a
# CMake install tree per ABI:
#
#   <third_party>/ncnn-android/<abi>/include/ncnn/*.h
#   <third_party>/ncnn-android/<abi>/lib/libncnn.a (+ glslang/SPIRV archives)
#
# `android/app/build.gradle.kts` passes that directory to CMake as
# RS_NCNN_ANDROID_DIR. Re-running is a no-op for ABIs already extracted.
#
# Pinned release: ncnn-20250503-android-vulkan (see
# docs/native-engine-design.md). Verify a newer release on
# https://github.com/Tencent/ncnn/releases before changing the version;
# assets are named ncnn-<date>-android-vulkan.zip and contain one directory
# per ABI.
#
# Environment overrides:
#   NCNN_ANDROID_VERSION  release tag            (default 20250503)
#   NCNN_ANDROID_ARCHS    space separated ABIs   (default: all three Flutter ABIs)
#   RS_THIRD_PARTY_DIR    destination root       (default: ../third_party)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
THIRD_PARTY="${RS_THIRD_PARTY_DIR:-$ROOT/../third_party}"
DEST="$THIRD_PARTY/ncnn-android"
BASE_URL="https://github.com/Tencent/ncnn/releases/download"
VERSION="${NCNN_ANDROID_VERSION:-20250503}"
ARCHS="${NCNN_ANDROID_ARCHS:-arm64-v8a armeabi-v7a x86_64}"
PKG="ncnn-${VERSION}-android-vulkan"

missing=()
for ARCH in $ARCHS; do
  if [ -f "$DEST/$ARCH/lib/libncnn.a" ]; then
    echo ">> $ARCH already present"
  else
    missing+=("$ARCH")
  fi
done

if [ "${#missing[@]}" -eq 0 ]; then
  echo ">> ncnn android prebuilts complete at $DEST"
  exit 0
fi

mkdir -p "$DEST"
if [ ! -f "$DEST/$PKG.zip" ]; then
  echo ">> downloading $PKG.zip ($(echo "$ARCHS" | wc -w) ABI(s) requested)"
  curl -fSL --retry 5 --retry-delay 3 --retry-all-errors \
    -o "$DEST/$PKG.zip" "$BASE_URL/$VERSION/$PKG.zip"
fi

for ARCH in "${missing[@]}"; do
  echo ">> extracting $ARCH"
  rm -rf "$DEST/_pkg" "$DEST/$ARCH"
  unzip -o -q "$DEST/$PKG.zip" "$PKG/$ARCH/*" -d "$DEST/_pkg"
  mv "$DEST/_pkg/$PKG/$ARCH" "$DEST/$ARCH"
  rm -rf "$DEST/_pkg"
  if [ ! -f "$DEST/$ARCH/lib/libncnn.a" ]; then
    echo "!! $ARCH: libncnn.a missing after extraction" >&2
    exit 1
  fi
done

rm -f "$DEST/$PKG.zip"
echo ">> done. Gradle expects ncnn at: $DEST/<abi> (CMake install tree)"
ls -1 "$DEST"
