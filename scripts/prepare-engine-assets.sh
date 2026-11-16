#!/usr/bin/env bash
# Fetches unmodified upstream RealSR-NCNN-Android 1.13.2 arm64 native engine binaries,
# strips all bundled AI model folders, and packs executables + shared libraries into
# RealSR-NCNN-Android-GUI/app/src/main/assets/realsr/binaries.tar.xz.
#
# Run this once before `./gradlew assembleDebug` or `./gradlew assembleRelease`.
# To compile the native CLI binaries from C++ source instead, see docs/BUILDING.md.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UPSTREAM_URL="${UPSTREAM_APK_1_13_2_URL:-https://github.com/tumuyan/RealSR-NCNN-Android/releases/download/1.13.2/RealSR-NCNN-Android-GUI-armv8a-1.13.2.apk}"
ASSET_DIR="$REPO_ROOT/RealSR-NCNN-Android-GUI/app/src/main/assets/realsr"
WORK_DIR="$(mktemp -d)"

cleanup() {
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

for cmd in curl unzip tar xz python3; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Error: required command '$cmd' is not installed." >&2
    exit 1
  fi
done

echo "Downloading upstream native engine archive (1.13.2)..."
curl -fL --retry 3 "$UPSTREAM_URL" -o "$WORK_DIR/upstream.apk"

echo "Extracting assets/realsr (excluding bundled model weights)..."
unzip -q "$WORK_DIR/upstream.apk" 'assets/realsr/*' -d "$WORK_DIR/extracted"

rm -rf "$ASSET_DIR"
mkdir -p "$ASSET_DIR"
cp -r "$WORK_DIR/extracted/assets/realsr/"* "$ASSET_DIR/"
rm -rf "$ASSET_DIR"/models*

echo "Packing native arm64 executables and shared libraries into binaries.tar.xz..."
ASSET_DIR="$ASSET_DIR" WORK_DIR="$WORK_DIR" python3 - << 'PY'
import os
import shutil
import subprocess

asset_dir = os.environ["ASSET_DIR"]
pack_dir = os.path.join(os.environ["WORK_DIR"], "solid-pack")
os.makedirs(pack_dir, exist_ok=True)

packed = []
for entry in sorted(os.listdir(asset_dir)):
    full = os.path.join(asset_dir, entry)
    if os.path.isfile(full) and not entry.endswith(".xml"):
        shutil.move(full, os.path.join(pack_dir, entry))
        packed.append(entry)

tar_path = os.path.join(asset_dir, "binaries.tar")
subprocess.run(["tar", "-cf", tar_path, "-C", pack_dir] + packed, check=True)
subprocess.run(["xz", "-f", "--arm64", "--lzma2=dict=16MiB,preset=9e", tar_path], check=True)

xz_mb = os.path.getsize(tar_path + ".xz") / (1024 * 1024)
print(f"Packed {len(packed)} native files into binaries.tar.xz ({xz_mb:.2f} MiB).")
PY

echo "Engine assets ready at: $ASSET_DIR"
