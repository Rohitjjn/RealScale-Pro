# Implementation roadmap

## Prompt 1 (foundation) — rebuilt during Prompt 2

The Prompt 1 workspace was lost between sessions (no checkpoint
attached). Its scope was rebuilt as part of Prompt 2: project scaffold,
design system, app shell + navigation, core screens, domain models,
service interfaces, native skeleton. Rebuilt-and-verified rather than
assumed.

## Prompt 2 (this) — native engine + real processing ✅

Delivered and verified (see inference-test-report.md):
- Stable C ABI + dart:ffi bindings with isolate execution model
- Real ncnn engine: tiled inference, feathered blending, recovery,
  cancellation, progress
- 5 official Real-ESRGAN models really running (host)
- Image inspection incl. EXIF orientation (all 8)
- Device profiling + adaptive planning with honest warnings
- Model management (registry, consent download, SHA-256)
- Full job lifecycle + storage + results UI (before/after)
- 46 Flutter tests + 30 native test runs, analyzer clean, web build green
- Documentation set complete (13 maintained + 5 new docs)

## Prompt 3 — final engineering, optimization, QA, release

Priority order:

1. **Android build + on-device verification** (top priority)
   - Build the APK on a capable machine (commands in BUILD.md)
   - On-device matrix: arm64 flagship + a low-RAM device; measure
     per-tile times, peak RSS, thermal, battery for all registry models
   - Fix anything the matrix uncovers (tile defaults, thread counts)
2. **realesr-general-x4v3 compact conversion** (torch→ONNX→ncnn) +
   fp16 (`ncnnoptimize`) variants; extend the model matrix tests
3. **Vulkan build path** (`NCNN_VULKAN=ON`) with GPU→CPU fallback
   verified end-to-end
4. **Performance**: consider native libjpeg-turbo/libpng encode path
   (large JPEG outputs are the slowest pure-Dart stage); incremental
   progress during encode
5. **Storage UX**: MediaStore save-to-gallery + share sheet (cost-check
   deps first), StatFs free-space channel, resumable downloads
6. **Polish**: bundle the chosen typefaces, onboarding refinement,
   EXIF passthrough, error-message review pass
7. **Release**: license aggregation, icon/splash assets, versioning,
   signed release build, final QA checklist, release ZIP

## Deferred / optional

- Batch processing UI (engine already sequential per job)
- ONNX Runtime backend (extension point documented; add only with a
  concrete model need)
- iOS/desktop targets

## Prompt 3 (final) — delivered 2026-11-06

1. ✅ Android release APK built + programmatically verified (arm64
   Vulkan-capable engine + v7a/x86_64 CPU engines; the Prompt 2 disk
   blocker was re-measured and worked around by freeing regenerable
   caches — BUILD.md documents both the arithmetic and the levers).
2. ⛔→documented realesr-general-x4v3 conversion: blocked by disk after
   deep investigation (quantization-aware checkpoint + torch toolchain);
   recipe + provenance recorded in model-compatibility-matrix.md.
3. ✅ Vulkan path wired end-to-end (opt-in, CPU fallback, honest
   reporting); execution itself untestable on this host (no GPU) and
   labeled experimental.
4. ✅ Encode performance measured (≤ 1% of RRDB job time) — native
   encode deprioritized by data, not assumption.
5. ✅ Storage UX: MediaStore save, share sheet, StatFs, resumable
   downloads, temp cleanup — all tested (mock-channel + logic tests).
6. ✅ Polish: navigation guard, launcher icon + adaptive icon + splash,
   backend/gallery facts in Results, honest storage info.
7. ✅ Release: unsigned-credential policy (debug-signed APK +
   documented signing procedure), final QA (59/59 + native suites +
   web), documentation set completed, final source ZIP with SHA-256.

Remaining (requires hardware): on-device matrix, Vulkan execution
validation, Play-store signing/distribution. Everything else from the
original three-prompt scope is done and verified to the levels stated
in QA-REPORT.md.
