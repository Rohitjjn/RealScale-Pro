#!/usr/bin/env bash
# RealScale-Pro — fetch the pinned ncnn source for host builds.
#
# The pinned commit matches the version verified during development
# (see docs/native-engine-design.md). Re-running this script is a
# no-op when the pinned checkout already exists.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/../third_party/ncnn"
PINNED_COMMIT="09981a0796cc0dd629ed5ba0f8d55653cc1406a5"

if [ -d "$DEST" ] && [ -n "$(git -C "$DEST" rev-parse HEAD 2>/dev/null)" ]; then
  CURRENT="$(git -C "$DEST" rev-parse HEAD)"
  if [ "$CURRENT" = "$PINNED_COMMIT" ]; then
    echo "ncnn already at pinned commit $PINNED_COMMIT"
    exit 0
  fi
  echo ">> warning: existing checkout at $CURRENT, expected $PINNED_COMMIT"
fi

mkdir -p "$ROOT/../third_party"
if [ ! -d "$DEST" ]; then
  git clone https://github.com/Tencent/ncnn.git "$DEST"
fi
git -C "$DEST" fetch --depth 1 origin "$PINNED_COMMIT" || \
  git -C "$DEST" fetch --unshallow origin
git -C "$DEST" checkout "$PINNED_COMMIT"
echo ">> ncnn ready at $DEST ($(git -C "$DEST" rev-parse --short HEAD))"
