#!/usr/bin/env bash
# RealScale-Pro — host (Linux x64) native engine build for tests.
#
# Builds build/native/librealscale_native.so against the pinned ncnn
# checkout, used by `flutter test` integration tests and by the rscli
# verification harness.
#
# Usage: tools/build_native.sh [build-dir]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="${1:-$ROOT/build/native}"
NCNN_DIR="$ROOT/../third_party/ncnn"
NCNN_BUILD="$ROOT/../third_party/ncnn-build"
NCNN_LIB="$NCNN_BUILD/src/libncnn.a"
CMAKE="$(command -v cmake || echo "$HOME/.venv/bin/cmake")"

if [ ! -f "$NCNN_LIB" ]; then
  echo ">> ncnn static library not found at $NCNN_LIB"
  echo ">> building ncnn first (CPU-only)…"
  mkdir -p "$NCNN_BUILD"
  cmake -G Ninja -DCMAKE_BUILD_TYPE=Release \
        -DNCNN_VULKAN=OFF -DNCNN_BUILD_TOOLS=OFF -DNCNN_BUILD_EXAMPLES=OFF \
        -DNCNN_BUILD_BENCHMARK=OFF -DNCNN_PYTHON_TOOLS=OFF \
        -DNCNN_SHARED_LIB=OFF -S "$NCNN_DIR" -B "$NCNN_BUILD"
  cmake --build "$NCNN_BUILD" -j"$(nproc)"
fi

echo ">> configuring realscale_native ($BUILD_DIR)"
cmake -G Ninja -DCMAKE_BUILD_TYPE=Release \
      -DRS_BUILD_TESTS=ON -DRS_BUILD_CLI=ON \
      -DRS_NCNN_LIB="$NCNN_LIB" \
      -DRS_NCNN_INCLUDE="$NCNN_DIR/src;$NCNN_BUILD/src" \
      -S "$ROOT/native" -B "$BUILD_DIR"

echo ">> building"
cmake --build "$BUILD_DIR" -j"$(nproc)"

echo ">> done: $BUILD_DIR/librealscale_native.so"
