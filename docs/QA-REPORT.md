# QA-REPORT — v1.0.0 final verification

**Date:** 2026-11-06 · **Environment:** Debian 13, 2 × x86_64,
3.9 GB RAM · Flutter 3.47.9 / Dart 3.13.7 · ncnn pinned `09981a07…`
· Android SDK 36 / NDK 28.2.13676358 / JDK 17.0.21 (Temurin).

All results below come from commands executed in this session. Mocked
tests and real-inference tests are explicitly separated; nothing mocked
is ever counted as AI verification.

## 1. Static analysis

| Check | Result |
| --- | --- |
| `flutter analyze` | **No issues found** |

## 2. Flutter test suite — 59/59 PASS

| Suite | Tests | Covers |
| --- | --- | --- |
| `device_profile_test.dart` | ✓ | tier classification, thread policy, unknowns stay null |
| `image_metadata_test.dart` | ✓ | format sniffing, EXIF orientation all 8, oriented dims |
| `model_registry_test.dart` | ✓ | registry parse, recorded checksums match the shipped files |
| `pipeline_planner_test.dart` | ✓ | tile/format policy, RAM estimates == native formula, safer alternatives, no silent downsampling |
| `storage_test.dart` | ✓ | atomic writes, duplicate-safe names, free-space guard, non-destructive behavior |
| `job_lifecycle_test.dart` | ✓ | full phase order, honest result facts, **input byte-identical after run**, failure + cancellation paths |
| `native_ffi_integration_test.dart` | ✓ (**REAL inference**) | loads the production `.so` + checked-in compact model: ABI/version, device info, Dart↔native estimate equality ×4, real JPEG → decode → tiled FFI inference → 4× output ≠ input, per-tile native progress observed, tiled-vs-single seam bound, cancellation from Dart, wrong-scale rejection |
| `widget_test.dart` | ✓ | app boots to Home with Paper-Crafted theme |
| `prompt3_features_test.dart` (new) | 13 | streamed download + SHA-256 verify + atomic install; **HTTP Range resume**; Range-ignored restart; checksum-mismatch → corrupted, never installed; HTTP error surfacing; gallery export + content-URI share; honest share-unavailable; channel-failure → StorageException with file intact; StatFs-backed free space; temp cleanup; GPU setting default-off + persistence; JobResult honesty defaults |

## 3. Native test suites (host, real code)

| Suite | Result | Covers |
| --- | --- | --- |
| `test_tile_layout` | **9/9** | exact coverage invariants, no slivers, odd dims 1×1…1000×511, context clamping, degenerate clamps |
| `test_feather_blend` | **8/8** | constant-field exactness, gradient seam ≤ 2/255, corners/edges/alpha, smoothstep bounds |
| `test_engine` × 5 models | **10/10 each** | ABI/lifecycle/invalid-args, bad-path loads, full tiled pipeline on odd-size gradient, tiled-vs-single consistency, cancellation, wrong-scale rejection |
| `test_engine` + `RS_TEST_FAIL_TILES=2` | **10/10** | OOM-recovery injection: tiles halved, image completed, progress reported attempt ≥ 2; deterministic errors stay out of the retry loop |

Re-run after every Prompt 3 native change (Vulkan path, CMake wiring) —
no regressions.

## 4. Real inference verification (not mocked)

- 5 official models × 10 engine tests each — all pass on this host.
- `rscli` end-to-end on the official 220×220 photo (Prompt 2 record) and
  the full benchmark matrix (Prompt 3, PERFORMANCE-REPORT.md) — outputs
  dimensionally exact, PSNR vs bicubic 10–16 dB (genuine model output,
  not a resize).
- Dart FFI integration tests run the **production** `.so` end-to-end
  from JPEG bytes to a verified 4× result.

## 5. Builds

| Target | Result |
| --- | --- |
| Android release APK (arm64) | **✓ built** — `RealScale-Pro-v1.0.0-arm64-release.apk`, 52.6 MB, SHA-256 `891227a6…31aed9d` |
| APK content verification | ✓ `unzip -l` (3 ABI engine libs, Dart AOT, resources), `aapt2 dump badging` (id/version/sdk/permissions as designed), `llvm-nm` (C ABI symbols exported), `strings` (ncnn + `ncnn-vulkan` markers in arm64 engine), `apksigner verify` (debug cert, as documented) |
| Web release | **✓ built** (41 MB) after QA caught and fixed a real regression (web stub missing the new `compute` parameter — the exact class of break only a web build catches) |
| Host native (prod + test-hook) | ✓ rebuilt and green |

## 6. Integration-level checks

- Original-image preservation: asserted byte-identical after a full job
  (job lifecycle test) — plus storage tests assert the app never writes
  to the input path.
- Cancellation: native between-tile poll → `RS_ERR_CANCELLED`, no output
  image, job reported cancelled (never as success); Dart-side control
  block path also tested.
- Progress honesty: all displayed progress originates from native tile
  callbacks / byte counters / phase transitions; ETA only with ≥ 2
  measured tiles and stable timings.
- Model integrity: SHA-256 verify on install (happy + tamper + resume
  paths tested); corrupted models are never usable.

## 7. Security & privacy review

| Item | Status |
| --- | --- |
| Manifest permissions | `INTERNET` (model downloads only) + `CAMERA` — verified via `aapt2 dump badging`; no location/contacts/storage permissions |
| Image data locality | all processing on-device; no upload code path exists |
| Analytics/tracking | none in dependencies (inventory audited) or code |
| Network endpoints | one: the official Real-ESRGAN GitHub release (models), consent-gated |
| Model integrity | SHA-256 on every install; recorded-from-official-release values in registry |
| Temp files | cleaned at startup (`cleanTempFiles`, tested) and after downloads (tested) |
| Signing | debug key only, documented; no fabricated credentials |
| Crash/restore | atomic writes (tmp+rename) everywhere; interrupted jobs fail honestly (never partial success) |
| Log hygiene | error strings carry statuses/paths/sizes, never pixel data |

## 8. Verification-level matrix (the honest summary)

| Claim | Verified how |
| --- | --- |
| AI inference works (5 models) | Linux host, native + Dart FFI tests + CLI |
| Tiling/seams/recovery/cancellation | Linux host, automated tests incl. failure injection |
| Flutter app logic + UI | Linux host, 59 tests + analyzer |
| APK builds, contains engine, signs | This environment, artifact inspected programmatically |
| App runs on a real Android device | **NOT verified — no device in this environment** |
| Vulkan GPU execution | **NOT verified — no GPU here** (detection + opt-in + CPU fallback implemented; see KNOWN-LIMITATIONS) |
| Web build | compiles; engine honestly unavailable |
