#!/usr/bin/env bash
# RealScale-Pro — fetch the pinned native dependencies.
#
#   tools/fetch_ncnn.sh            # host (Linux) ncnn source checkout
#   tools/fetch_ncnn.sh linux      # same
#   tools/fetch_ncnn.sh android    # official Android prebuilts (per ABI)
#   tools/fetch_ncnn.sh stb        # header-only stb (rscli image I/O)
#   tools/fetch_ncnn.sh all        # host source + android prebuilts + stb
#
# Layout (override the root with RS_THIRD_PARTY_DIR):
#   <third_party>/ncnn            pinned ncnn source (host builds)
#   <third_party>/ncnn-android    prebuilt ncnn per ABI (Android builds)
#   <third_party>/stb             stb_image / stb_image_write headers
#
# Default third_party root: ../third_party next to the repository, which keeps
# multi-gigabyte build inputs and model weights out of the checkout.
#
# Re-running a step is a no-op when the pinned version is already on disk.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
THIRD_PARTY="${RS_THIRD_PARTY_DIR:-$ROOT/../third_party}"
PLATFORM="${1:-linux}"

NCNN_REPO="https://github.com/Tencent/ncnn.git"
NCNN_COMMIT="09981a0796cc0dd629ed5ba0f8d55653cc1406a5"
STB_REPO="https://github.com/nothings/stb.git"
STB_COMMIT="${STB_COMMIT:-}"
STB_BRANCH="${STB_BRANCH:-master}"

fetch_host_ncnn() {
  local dest="$THIRD_PARTY/ncnn"
  if [ -d "$dest" ] && [ -n "$(git -C "$dest" rev-parse HEAD 2>/dev/null)" ]; then
    local current
    current="$(git -C "$dest" rev-parse HEAD)"
    if [ "$current" = "$NCNN_COMMIT" ]; then
      echo ">> host ncnn already at pinned commit ${NCNN_COMMIT:0:12}"
      return 0
    fi
  fi
  mkdir -p "$THIRD_PARTY"
  if [ ! -d "$dest/.git" ]; then
    rm -rf "$dest"
    # Blob-less shallow fetch: only the pinned tree is downloaded.
    git init -q "$dest"
    git -C "$dest" remote add origin "$NCNN_REPO"
  fi
  git -C "$dest" fetch -q --depth 1 --filter=blob:none origin "$NCNN_COMMIT" || \
    git -C "$dest" fetch -q --depth 1 origin "$NCNN_COMMIT"
  git -C "$dest" checkout -q FETCH_HEAD
  echo ">> host ncnn ready at $dest ($(git -C "$dest" rev-parse --short HEAD))"
}

fetch_stb() {
  local dest="$THIRD_PARTY/stb"
  if [ -f "$dest/stb_image.h" ] && [ -f "$dest/stb_image_write.h" ]; then
    echo ">> stb already present at $dest"
    return 0
  fi
  mkdir -p "$THIRD_PARTY"
  rm -rf "$dest.tmp"
  git clone -q --depth 1 --branch "$STB_BRANCH" "$STB_REPO" "$dest.tmp"
  mkdir -p "$dest"
  cp "$dest.tmp/stb_image.h" "$dest.tmp/stb_image_write.h" "$dest/"
  if [ -n "$STB_COMMIT" ]; then
    local got
    got="$(git -C "$dest.tmp" rev-parse --short HEAD)"
    echo ">> stb fetched at $got (requested $STB_COMMIT)"
  fi
  rm -rf "$dest.tmp"
  echo ">> stb ready at $dest"
}

fetch_android_ncnn() {
  local script="$ROOT/tools/fetch_ncnn_android.sh"
  if [ ! -x "$script" ]; then
    chmod +x "$script"
  fi
  RS_THIRD_PARTY_DIR="$THIRD_PARTY" "$script"
}

case "$PLATFORM" in
  linux|host) fetch_host_ncnn ;;
  stb) fetch_stb ;;
  android) fetch_android_ncnn ;;
  all)
    fetch_host_ncnn
    fetch_android_ncnn
    fetch_stb
    ;;
  *)
    echo "usage: $0 [linux|host|android|stb|all]" >&2
    exit 2
    ;;
esac
