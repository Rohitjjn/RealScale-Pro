# Changelog

## 2026-11-06 — Prompt 2: native AI engine, real image processing,
device adaptation & end-to-end integration

> Workspace note: the Prompt 1 session's files were not present when
> Prompt 2 started (no checkpoint ZIP was attached). The Prompt 1
> foundation was rebuilt inside this prompt and everything below was
> implemented and verified in the same pass. Flutter 3.47.9 / Dart
> 3.13.7 — same toolchain version as the Prompt 1 report, re-downloaded
> and re-verified.

### Native engine (all new, verified)

- `realscale.h` — stable C ABI v2 (opaque handles, POD structs,
  progress struct with attempt/tile reporting, cancel flag contract).
- ncnn engine: real tiled inference with model-aware defaults, context
  margins, per-tile extraction without input copies, output scale
  validation, 1 Gpx safety cap.
- Tile layout: even anchor distribution, full-size edge tiles, per-side
  overlap records, single-tile degradation, degenerate-arg clamping.
- Feather blending: float accumulation, smoothstep ramps, weight
  normalization; measured seam deviation ≤ 7/255 vs single-tile runs.
- Recovery: OOM-only bounded tile halving (floor 32 px), fresh
  accumulators per attempt, attempt/tile-size surfaced in progress;
  deterministic errors fail fast (bug found by test and fixed).
- Error reporting: thread-local messages for every failure path.
- Test hooks (`RS_TEST_HOOKS` + `RS_TEST_FAIL_TILES`) — test builds only.
- `rscli` host harness (stb): real end-to-end runs, fixtures generated.
- Builds: host production `.so`, host test-hook build, Android gradle
  wiring (externalNativeBuild, 3 ABIs), interface-only stub fallback.

### Flutter app

- FFI bindings mirroring the ABI; conditional export with an honest web
  stub; isolate execution model with a shared native control block for
  progress + cancellation across the blocked FFI boundary.
- Domain layer: ImageMetadata, DeviceProfile/tiers, PipelinePlan,
  ModelInfo, JobState/Progress/Result.
- Services: ImageService (inspection, header parsing, EXIF orientation
  all 8, decode, encode, white-flatten JPEG safety), DeviceProfiler
  (+ Android MethodChannel reading real MemoryInfo/ABI/Vulkan probe),
  StorageService (atomic, duplicate-safe, non-destructive),
  ModelManager (registry, streamed consent downloads, SHA-256
  verification, deletion), SettingsService.
- Processing: PipelinePlanner (memory-adaptive tiles, format policy,
  honest warnings, explicit-only downsample alternatives) and JobManager
  (full lifecycle, real progress/ETA gating, cancellation, recovery
  surfacing, isolate encoding).
- UI (Paper-Crafted): splash, onboarding + consent, home (status strip,
  mode cards, plan dialog), processing (real progress/tiles/ETA/
  recovery/cancel), results (before/after slider + measured facts),
  models (download/verify/delete), settings, about/licenses.
- Android embedding: gradle native build, permissions, MainActivity
  device-info channel; web target compiles.

### Verification summary

- `flutter analyze`: clean. `flutter test`: **46/46** (includes 5 REAL
  inference integration tests).
- Native: 17/17 geometry+blending; 10/10 engine tests × 5 models;
  10/10 with recovery injection.
- rscli real-photo runs: 220² → 880² in 12.2 s (flagship) / 0.4 s
  (compact); outputs programmatically verified.
- Web release build: ✓.
- Android APK: blocked by sandbox disk budget (exact arithmetic in
  BUILD.md); wiring complete.

## 2026-11-05 — (Prompt 1, as reported by the prior session)

Workspace existed with foundation + 98 tests + native C++ tests per the
Prompt 1 report; artifacts were not present at Prompt 2 start and were
rebuilt (see note above).

## 2026-11-06 — Prompt 3 (final): Android release build, Vulkan path,
storage UX, benchmarks, full QA & release engineering

### Android — the headline item

- **Release APK built and verified in this environment** (Prompt 2's
  blocker was disk; Prompt 3 freed ~1.9 GB of regenerable caches and
  completed the build): `RealScale-Pro-v1.0.0-arm64-release.apk`,
  52.6 MB, SHA-256 `891227a6…31aed9d`, debug-signed (documented; no
  invented credentials).
- Toolchain staged from scratch: Temurin JDK 17.0.21, cmdline-tools,
  platform 36 / build-tools 36.0.0 / cmake 3.22.1, NDK 28.2.13676358,
  official ncnn `20250503-android-vulkan` prebuilt (arm64).
- Verified programmatically: aapt2 badging, zip contents (3 ABI engine
  libs), exported C ABI symbols, ncnn + ncnn-vulkan markers, apksigner.
- Maven-Central 429 rate limiting worked around with local-only mirror
  configuration (documented in BUILD.md; the repo ships clean repos).

### Native engine

- Vulkan compute path implemented behind the existing `rs_compute`
  option: GPU is opt-in (experimental, default OFF), with GPU model-load
  fallback and a bounded mid-run GPU→CPU fallback that restarts the pass
  on the verified CPU backend. Detection reports honestly via
  `rs_device_info`. CPU build unchanged and fully re-verified.
- Android CMake wiring: per-ABI prebuilt derivation
  (`RS_NCNN_ANDROID_DIR`), raw-archive linking (the imported CMake
  target propagates -fno-exceptions, incompatible with our recovery
  engine), glslang/SPIRV family, static OpenMP, log/jnigraphics/dl.
- Host test binaries relocated to `third_party/rs-host-build` (gradle
  reaps `build/` between runs — a lesson from this session).

### Flutter app

- Storage UX: MediaStore gallery export to `Pictures/RealScale-Pro`
  (API 29+) with app-external + media-scan fallback (API <29), system
  share sheet via content URI, StatFs-backed free space, `.tmp` cleanup
  at startup, `StorageException` surfacing with the file always intact.
- Model downloads: streamed to disk (the ~50 MB package is never fully
  in RAM) and **resumable via HTTP Range** (206 → append; 200 → clean
  restart); atomic installs (tmp+rename); partial files cleaned.
- Results screen: backend fact row, saved-to-gallery line with honest
  failure notes, Share button.
- Settings: experimental GPU toggle (only enabled when the device
  reports Vulkan), storage free-space card.
- Processing screen: PopScope navigation guard (mid-run back navigation
  no longer risks a duplicate job).
- Branding: generated launcher icon set (legacy + adaptive) and splash
  (light/dark) from the Paper-Crafted palette — `tools/generate_icon.py`.

### Testing & QA

- New `prompt3_features_test.dart`: 13 tests (download
  streaming/resume/restart/tamper, gallery export/share/failure, StatFs
  free space, temp cleanup, GPU setting, honesty defaults).
- Full suite: **59/59** (46 prior + 13 new), analyzer clean.
- Native suites re-run after every change: 9/9, 8/8, 10/10 × 5 models,
  10/10 with recovery injection.
- Web build re-verified — **caught a real regression** (web stub missing
  `compute`), fixed, rebuilt green.
- Security & privacy review recorded in QA-REPORT.md.

### Benchmarks (see PERFORMANCE-REPORT.md)

- Full host matrix: 3 models × 3 sizes × single/multi-tile. Key
  findings: compact ≈ 25–40× faster than flagship; single-tile RRDB at
  ≥ 512² inputs measured impractical (>10 min) — data-validated tiling
  policy; Dart encode ≤ 1% of RRDB job time (native encode correctly
  deprioritized).

### Model research

- `realesr-general-x4v3.pth` obtained from the official release
  (4.89 MB, SHA-256 `8dc7edb9…ce96292`) and inspected without torch:
  the checkpoint contains quantization-aware keys (`weightq`/`biasq`),
  so conversion requires the official dequant preprocessing plus a
  local PyTorch toolchain (~1 GB) — blocked by the disk budget and
  documented as deferred with the full rationale (not silently
  dropped). The registry keeps the three verified ncnn models.

### Documentation

- New: INSTALLATION.md, PERFORMANCE-REPORT.md, QA-REPORT.md,
  KNOWN-LIMITATIONS.md, PROMPT3_REPORT.md.
- Updated: README (final), BUILD (executed Android path), architecture
  (GPU + storage), models + compatibility matrix (x4v3 investigation),
  inference-test-report, DEVICE-COMPATIBILITY (renamed), LICENSES
  (renamed), AGENT_RULES (moved to root), dependency-inventory,
  QA-CHECKLIST, IMPLEMENTATION-ROADMAP, requirements-traceability.
- Final source ZIP: `RealScale-Pro-Final-Source-v1.0.zip` with recorded
  file count, size and SHA-256 (see PROMPT3_REPORT.md).

### Honest remaining gaps

See KNOWN-LIMITATIONS.md — headline: no physical-device or emulator
verification, Vulkan execution untested (no GPU here), benchmarks are
host figures, general-x4v3 conversion deferred with technical rationale.
