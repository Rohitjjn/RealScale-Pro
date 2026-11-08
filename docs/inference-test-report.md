# Inference test report

**Environment:** Debian 13, 2 × x86_64 CPUs, 3.9 GB RAM. ncnn pinned
commit `09981a0796cc0dd629ed5ba0f8d55653cc1406a5` (2026-11-05), built
CPU-only (Release). Flutter 3.47.9 / Dart 3.13.7. Date: 2026-11-06.
**All results below are from commands actually executed in this
environment; raw commands are included so they can be re-run.**

## 1. Native unit + ABI tests (no model needed)

```
build/native-tests/test_tile_layout    → 9/9  PASS
build/native-tests/test_feather_blend  → 8/8  PASS
```

Covers: exact coverage invariants (tile cores tile the image exactly),
no sliver tiles, odd dimensions (1×1 … 1000×511), context clamping,
degenerate argument clamping, constant-field exact reproduction,
gradient seam bound (≤ 2/255), corner/edge/alpha preservation,
smoothstep bounds.

## 2. Native engine tests — REAL inference (model required)

Command shape:

```
RS_TEST_MODEL_PARAM=models/<m>.param RS_TEST_MODEL_BIN=models/<m>.bin \
RS_TEST_MODEL_SCALE=<s> build/native-tests/test_engine
```

Results per model (identical command, only env differs):

| Model | Result | Tiled-vs-single seam deviation |
| --- | --- | --- |
| realesr-animevideov3-x4 | 10/10 PASS | max 6/255, mean 0.209 |
| realesr-animevideov3-x3 | 10/10 PASS | — (same suite) |
| realesr-animevideov3-x2 | 10/10 PASS | — |
| realesrgan-x4plus | 10/10 PASS | max 7/255, mean 0.994 |
| realesrgan-x4plus-anime | 10/10 PASS | — |

With recovery injection (`RS_TEST_FAIL_TILES=2`, test-hook build):
**10/10 PASS** — the engine halved tiles, completed the image, and the
progress stream reported attempt ≥ 2. The wrong-scale-variant test
simultaneously confirmed that deterministic errors do NOT enter the
retry loop (this test caught a real classification bug during
development, which was fixed).

Tests included: ABI version/lifecycle/invalid-args, bad-path model
loads, full tiled pipeline on a 100×76 odd-size gradient (4× output,
progress reaching `RS_STAGE_DONE`, output ≠ input), tiled-vs-single
consistency, cancellation mid-run (`RS_ERR_CANCELLED`, no output),
recovery, wrong-scale rejection with a descriptive message.

## 3. End-to-end CLI on a real photograph

```
rscli --param realesrgan-x4plus.param --bin realesrgan-x4plus.bin \
      --in input.jpg --out out.png --scale 4 --tile 128 --threads 2
# input.jpg = official 220×220 test photo
```

- realesrgan-x4plus: 220×220 → **880×880** in **12.2 s**, 4 tiles, real
  per-tile progress on the console.
- realesr-animevideov3-x4: same → **0.4 s**, 9 tiles.

Output verification (Python/PIL): dimensions exact; PSNR vs bicubic
×4 upscale 15.72 dB (x4plus) / 10.19 dB (animev3) — i.e., the outputs
are genuinely different from a naive resize (a copy or resize would be
> 30 dB identical); sample pixels plausible. Fixtures saved:
`test/fixtures/input_x4plus.png`, `input_animev3.png`.

## 4. Flutter-side integration (dart:ffi through the full stack)

`flutter test` → **46/46 PASS** (`flutter analyze`: clean).

The FFI integration group (skipped visibly when the `.so`/model is
absent) verified with the REAL production `.so` and the checked-in
compact model:

- ABI/thread/fp16/GPU info via Dart.
- Dart ↔ native peak-memory estimate equality across 4 configurations.
- **Real end-to-end**: real JPEG → dart:ui decode (EXIF path) → FFI
  tiled inference (9 tiles @96 px) → result dims 4×, per-tile native
  progress observed, output ≠ input.
- Tiled-vs-single seam bound via Dart: ≤ 12/255 max, ≤ 2.0 mean.
- Cancellation from Dart (control block) → cancellation exception.
- Wrong-scale request → descriptive error containing "scale".

## 5. Honest limitations

- All inference verification ran on **host x86_64 CPU**, not on Android
  ARM hardware (APK blocked by sandbox disk budget — `BUILD.md`).
- Vulkan/GPU path is compiled out on this host; GPU availability
  reporting returns 0 (honest for this build).
- The 12.2 s flagship number is a 2-CPU x86_64 host figure, NOT an
  Android on-device benchmark.
- ETA accuracy on-device depends on per-tile timing stability; the UI
  only shows an ETA when ≥ 2 measured tiles and stable timings exist.

## Prompt 3 re-verification (2026-11-06, after the Vulkan/CMake changes)

All suites re-run against the modified engine (CPU build unchanged in
behavior):

```
third_party/rs-host-build/test_tile_layout    → 9/9  PASS
third_party/rs-host-build/test_feather_blend  → 8/8  PASS
test_engine × 5 models                        → 10/10 PASS each
test_engine + RS_TEST_FAIL_TILES=2            → 10/10 PASS (recovery)
flutter test (incl. production .so FFI runs)  → 59/59 PASS
flutter analyze                               → No issues found
flutter build web --release                   → ✓ (caught + fixed a web-stub regression)
flutter build apk --release (arm64)           → ✓ 52.6 MB, verified artifact
```

Benchmark matrix for the same engine: docs/PERFORMANCE-REPORT.md
(including the measured impracticality of single-tile RRDB at ≥ 512²
inputs on 2-core hosts, which validates the tiled-by-default planner).
