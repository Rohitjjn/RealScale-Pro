#!/usr/bin/env bash
# RealScale-Pro — host (Linux x64) native engine build for tests.
#
# Builds <third_party>/rs-host-build/librealscale_native.so against the pinned
# ncnn checkout, and copies it to build/native/librealscale_native.so, which is
# where test/native_ffi_integration_test.dart looks for it. Also builds the
# native test binaries (test_engine, test_tile_layout, test_feather_blend) and
# the rscli harness.
#
# Usage: tools/build_native.sh [build-dir]
#
# Environment:
#   RS_THIRD_PARTY_DIR  root of ncnn/stb/build dirs (default: ../third_party)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
THIRD_PARTY="${RS_THIRD_PARTY_DIR:-$ROOT/../third_party}"
BUILD_DIR="${1:-$THIRD_PARTY/rs-host-build}"
NCNN_DIR="$THIRD_PARTY/ncnn"
NCNN_BUILD="$THIRD_PARTY/ncnn-build"
NCNN_LIB="$NCNN_BUILD/src/libncnn.a"
CMAKE="$(command -v cmake || echo "$HOME/.venv/bin/cmake")"
JOBS="$(nproc 2>/dev/null || echo 2)"

if [ ! -d "$NCNN_DIR" ]; then
  echo ">> pinned ncnn source missing — fetching it"
  "$ROOT/tools/fetch_ncnn.sh" linux
fi

if [ ! -f "$NCNN_LIB" ]; then
  echo ">> ncnn static library not found at $NCNN_LIB — building it (CPU-only, no Vulkan)"
  "$CMAKE" -G Ninja -DCMAKE_BUILD_TYPE=Release \
        -DNCNN_VULKAN=OFF -DNCNN_BUILD_TOOLS=OFF -DNCNN_BUILD_EXAMPLES=OFF \
        -DNCNN_BUILD_BENCHMARK=OFF -DNCNN_PYTHON_TOOLS=OFF \
        -DNCNN_SHARED_LIB=OFF -S "$NCNN_DIR" -B "$NCNN_BUILD"
  "$CMAKE" --build "$NCNN_BUILD" -j"$JOBS"
fi

echo ">> configuring realscale_native ($BUILD_DIR)"
"$CMAKE" -G Ninja -DCMAKE_BUILD_TYPE=Release \
      -DRS_BUILD_TESTS=ON -DRS_BUILD_CLI=ON \
      -DRS_NCNN_LIB="$NCNN_LIB" \
      -DRS_NCNN_INCLUDE="$NCNN_DIR/src;$NCNN_BUILD/src" \
      -S "$ROOT/native" -B "$BUILD_DIR"

echo ">> building (test fail hooks ON — test builds only, never in the APK)"
"$CMAKE" --build "$BUILD_DIR" -j"$JOBS"

mkdir -p "$ROOT/build/native"
cp "$BUILD_DIR/librealscale_native.so" "$ROOT/build/native/librealscale_native.so"
echo ">> done:"
echo "   $BUILD_DIR/librealscale_native.so   (test binaries + rscli)"
echo "   $ROOT/build/native/librealscale_native.so   (used by flutter test)"
