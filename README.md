# RealScale-Pro

Offline-first AI image enhancement for Android. Flutter frontend, native
C/C++ inference engine (ncnn) through a stable C ABI and dart:ffi.

> **Status: v1.0.0 — Prompt 3 of 3 complete (final).**
> Real tiled AI inference is implemented and verified end-to-end on the
> host build; a release APK was built and programmatically verified in
> this environment. See `docs/CHANGELOG.md`, `docs/QA-REPORT.md`,
> `docs/PERFORMANCE-REPORT.md` and `docs/KNOWN-LIMITATIONS.md`.

## What this is

- **Upscale Mode** — practical enhancement: controlled output-size
  optimization, JPEG quality 90–96, explicit (never silent) downsampling
  proposals, original preserved.
- **Super Resolution Mode** — quality-first: lossless processing chain,
  tiling with seam-free feathered blending, never a silent downsample.
- **Offline by design** — images never leave the device. The only network
  use is the consented, checksum-verified, resumable model download.
- **Honest engineering** — no fabricated progress, no fake inference, no
  silent quality reduction. Unverifiable states are reported as such.

## Supported platforms

| Platform | State |
| --- | --- |
| Android arm64-v8a | **Release APK built and verified** (52.6 MB, ncnn engine with Vulkan-capable build, CPU execution verified on host) |
| Android armeabi-v7a / x86_64 | Engine built from the same source in the APK (CPU); not device-tested here |
| Web | Compiles and runs; the engine is honestly reported unavailable (no fake inference) |
| Linux host | Full verification environment for the engine and tests |

## Supported models (all verified by real inference tests)

| Model | Role | Size | Scale |
| --- | --- | --- | --- |
| `realesr-animevideov3-x4` | compact default — low/balanced devices | 1.25 MB | 4× |
| `realesrgan-x4plus-anime` | illustration quality step-up | 8.94 MB | 4× |
| `realesrgan-x4plus` | flagship photographic | 33.42 MB | 4× |

All weights are official Real-ESRGAN release artifacts (BSD-3-Clause),
downloaded on explicit consent with SHA-256 verification against values
recorded from the official package — never bundled into the APK.
`realesr-general-x4v3` (the compact *general* model) is NOT included:
upstream publishes only PyTorch weights with quantization-aware keys, and
the conversion could not be completed honestly in this environment — see
`docs/model-compatibility-matrix.md` for the full investigation.

## Install

See `docs/INSTALLATION.md` for: installing the built APK on a device,
first-run model download, and the release signing procedure (the shipped
APK is signed with the debug key for testability; no credentials were
invented).

## Build

See `docs/BUILD.md` for the exact, verified commands: host verification
build (ncnn + native engine + `flutter test`), the Android release APK
(arm64 first), and the web build. The Android toolchain arithmetic for
small disks is documented there too.

## Run the tests

```bash
flutter analyze                    # No issues found
flutter test                       # 59/59, includes REAL FFI inference tests
# native suites (host):
third_party/rs-host-build/test_tile_layout        # 9/9
third_party/rs-host-build/test_feather_blend      # 8/8
RS_TEST_MODEL_PARAM=… RS_TEST_MODEL_BIN=… RS_TEST_MODEL_SCALE=4 \
  third_party/rs-host-build/test_engine           # 10/10 per model × 5
```

Full results and what each suite actually covers: `docs/QA-REPORT.md`.

## Offline capabilities

Everything except the one-time model download works with no network:
image import (gallery/camera), inspection, planning, inference, encoding,
saving, sharing. No analytics, no tracking, no telemetry — the manifest
requests only `INTERNET` (model downloads) and `CAMERA` (capture).

## Known limitations

Honestly listed in `docs/KNOWN-LIMITATIONS.md` — including: no physical
Android device was available in this environment (verification levels are
labeled per claim), the Vulkan GPU path is compiled in but its execution
was not testable here (CPU is the default with automatic fallback), and
the general-x4v3 conversion is deferred with its technical rationale.

## Repository layout

```
lib/            Flutter app (core/, domain/, services/, processing/, engine/, features/)
native/         C/C++ engine: stable C ABI, tiling, blending, ncnn engine, tests, rscli
android/        Android embedding (gradle externalNativeBuild -> native/)
assets/models/  registry.json (metadata + recorded SHA-256; no weights bundled)
test/           Dart tests incl. REAL FFI integration tests
tools/          build_native.sh, fetch_ncnn*.sh, benchmark_host.sh, bench_encode.dart,
                generate_icon.py
docs/           all project documentation (index below)
```

## Documentation index

- `AGENT_RULES.md` — binding engineering rules (honesty first)
- `docs/PRD.md` — product requirements
- `docs/architecture.md` — system architecture
- `docs/native-engine-design.md` — C ABI, engine internals, GPU path
- `docs/image-pipeline.md` — the full processing pipeline
- `docs/DEVICE-COMPATIBILITY.md` — device tiers, adaptation, save strategy
- `docs/models.md` + `docs/model-compatibility-matrix.md` — model registry
- `docs/inference-test-report.md` — REAL inference verification record
- `docs/PERFORMANCE-REPORT.md` — measured benchmarks
- `docs/QA-REPORT.md` + `docs/QA-CHECKLIST.md` — test results
- `docs/INSTALLATION.md` — install, models, signing
- `docs/BUILD.md` — verified build instructions
- `docs/dependency-inventory.md`, `docs/LICENSES.md`
- `docs/design-system.md` — Paper-Crafted design language
- `docs/KNOWN-LIMITATIONS.md` — what is NOT verified or NOT done
- `docs/IMPLEMENTATION-ROADMAP.md`, `docs/CHANGELOG.md`
- `docs/requirements-traceability.md`
- `docs/PROMPT2_REPORT.md`, `docs/PROMPT3_REPORT.md` — session reports
