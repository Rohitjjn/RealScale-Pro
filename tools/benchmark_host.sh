#!/usr/bin/env bash
# RealScale-Pro — host benchmark matrix (Prompt 3 §7).
#
# Generates deterministic benchmark inputs from the official Real-ESRGAN
# test photo (bicubic resizes; the photo is only used as realistic image
# statistics — outputs are NOT used for quality claims) and runs the
# REAL inference pipeline via rscli for:
#
#   sizes   : 128x128 (small) · 512x512 (medium) · 800x600 (large)
#   models  : compact (realesr-animevideov3-x4) · quality (x4plus-anime)
#             · flagship (realesrgan-x4plus)
#   tiling  : single-tile (--tile 1024) vs multi-tile (--tile 128)
#   threads : 2 (host CPU count)
#
# Every number in the printed table comes from an rscli run that just
# executed (timings, tiles, attempts, peak RSS are read from rscli's own
# honest output). Results append to $OUT; already-recorded runs are
# skipped, so the script is resumable.
#
# Usage: tools/benchmark_host.sh [rscli-path] [models-dir] [out-file]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RSCLI="${1:-$ROOT/../third_party/rs-host-build/rscli}"
MODELS="${2:-$ROOT/../models}"
OUT="${3:-/tmp/realscale-bench.txt}"

mkdir -p "$(dirname "$OUT")"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# --- deterministic inputs from the official test photo -----------------
python3 - "$MODELS/input.jpg" "$WORK" << 'PYEOF'
import sys
from PIL import Image
src_path, work = sys.argv[1], sys.argv[2]
img = Image.open(src_path).convert("RGB")
for name, size in [("small_128", (128, 128)),
                   ("medium_512", (512, 512)),
                   ("large_800x600", (800, 600))]:
    img.resize(size, Image.BICUBIC).save(f"{work}/{name}.png")
print("inputs ready")
PYEOF

run_one() { # id model scale in tile
  local id="$1" model="$2" scale="$3" input="$4" tile="$5"
  if rg -q "^$id\b" "$OUT" 2>/dev/null; then
    echo ">> skip $id (already recorded)"
    return
  fi
  echo ">> run $id"
  local line
  line=$("$RSCLI" --param "$MODELS/$model.param" --bin "$MODELS/$model.bin" \
    --in "$input" --out "$WORK/out.png" --scale "$scale" --tile "$tile" \
    --threads 2 2>&1 | tr '\n' ' ')
  echo "$id | $line" >> "$OUT"
}

for entry in \
    "realesr-animevideov3-x4:4:compact" \
    "realesrgan-x4plus-anime:4:quality" \
    "realesrgan-x4plus:4:flagship"; do
  model="${entry%%:*}"; rest="${entry#*:}"; scale="${rest%%:*}"; role="${rest##*:}"
  for size in small_128 medium_512 large_800x600; do
    run_one "$role-$model-$size-single" "$model" "$scale" "$WORK/$size.png" 1024
    run_one "$role-$model-$size-multi" "$model" "$scale" "$WORK/$size.png" 128
  done
done

echo "=== results in $OUT ==="
cat "$OUT"
