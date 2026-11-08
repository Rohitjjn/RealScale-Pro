# Architecture

> Rebuilt in Prompt 2 after the workspace reset (the Prompt 1 workspace
> was lost between sessions; no checkpoint ZIP was attached). The
> architecture follows the Prompt 1 plan: same layering, same component
> names, now actually implemented and verified.

## System overview

```
┌────────────────────────────────────────────────────────────────────┐
│ Flutter (Dart)                                                     │
│                                                                    │
│  features/          UI screens (Paper-Crafted design system)       │
│    splash, onboarding, home, processing, results, models,          │
│    settings, about                                                 │
│  core/              theme, router (go_router), Riverpod providers  │
│  processing/        PipelinePlanner, JobManager (lifecycle)        │
│  services/          ImageService, DeviceProfiler (+ MethodChannel  │
│                     on Android), StorageService, ModelManager,     │
│                     SettingsService                                │
│  domain/            pure models (ImageMetadata, DeviceProfile,     │
│                     PipelinePlan, JobState/Result, ModelInfo)      │
│  engine/native/     dart:ffi bindings + isolate execution model    │
└──────────────────────────────┬─────────────────────────────────────┘
                               │ stable C ABI (realscale.h), ABI v2
┌──────────────────────────────▼─────────────────────────────────────┐
│ Native (C/C++), librealscale_native.so                            │
│                                                                    │
│  abi.cpp            C ABI implementation, validation, estimates    │
│  engine_ncnn.cpp    REAL inference: ncnn Net, per-tile extract/    │
│                     infer/blend, recovery, cancellation, progress  │
│  tile_layout.cpp    grid geometry (even distribution, no slivers,  │
│                     context margins, per-side overlaps)            │
│  feather_blend.cpp  normalized smoothstep accumulation blending    │
│  error.cpp          thread-local error messages                    │
│  engine_stub.cpp    interface-only build fallback (no ncnn)        │
└──────────────────────────────┬─────────────────────────────────────┘
                               │ static link
                      ┌────────▼────────┐
                      │ ncnn (Tencent)  │ BSD-3, pinned commit
                      │ CPU (SSE/AVX)   │ 09981a0796cc…
                      └─────────────────┘
```

## Key decisions

| Decision | Choice | Why |
| --- | --- | --- |
| Inference backend | ncnn (static, CPU) | Mobile-first, no runtime deps, BSD-3, mature ARM NEON path; Vulkan variant is a compile-time flag for Prompt 3 |
| FFI boundary | stable C ABI, opaque handles | C++ classes never cross; ABI version checked at create(); survives engine swaps |
| Execution model | 1 worker isolate per job + shared native control block | `rs_run` is blocking; the control block carries progress (written by the C callback) and the cancel flag (written by UI) across the blocked boundary; results leave via SendPort only |
| Progress transport | native callback → control block → main-isolate poll (120 ms) | every displayed value originates from a real native event; the poll is transport, not fabrication |
| Tiling | input-space tiles, even stride distribution, per-side overlap records, context margins, normalized feather blending | seam-free (measured ≤ 7/255 vs single-tile), handles odd sizes/borders |
| Recovery | OOM-class failures only; halve tile (floor 32 px), ≤ 3 retries; restart blending from scratch | deterministic errors fail fast with the real cause; partial images never return as success |
| Model storage | app-support dir, SHA-256 verified, never bundled in APK | consent, integrity, offline-after-download, deletable |
| Est. peak RAM | single formula mirrored in C (`rs_estimate_peak_memory`) and Dart (`PipelinePlanner`) | kept in sync by an integration test that asserts equality |
| Web | conditional export; engine reported unavailable | honest UX instead of fake inference |

## Data flow (one job)

1. **Import** — image_picker → File. `ImageService.inspect` sniffs the
   format, parses JPEG/PNG headers, reads EXIF orientation (pure Dart,
   tag 0x0112).
2. **Plan** — `PipelinePlanner.plan(image, device, mode, model)` →
   model, scale, tile geometry, output format (PNG for SR/alpha; JPEG
   90–96 otherwise), peak-RAM estimate, warnings, safer alternative
   (upscale mode only, explicit).
3. **Acquire model** — `ModelManager` downloads from the official
   Real-ESRGAN release (consent gated), extracts, SHA-256 verifies.
4. **Decode** — dart:ui codec → RGBA; EXIF orientation applied in Dart.
5. **Infer** — `JobManager` → `NativeEngine.run` spawns the worker
   isolate → `rs_run` tiles run through ncnn → blended output RGBA.
   Progress/ETA/recovery surface from real events; cancel writes the
   shared flag.
6. **Encode** — isolate (`compute`) via the pure-Dart image package:
   PNG, or JPEG q∈[90,96] with white-flatten for stray alpha.
7. **Save** — atomic (tmp+rename) into `<docs>/RealScale-Pro/`,
   duplicate names get " (n)", original untouched.

## Extension points (documented, not speculative scaffolding)

- `OnnxEngine` — a second `rs::Engine` implementation behind the same
  ABI; the factory in `abi.cpp` is the only integration point.
- Vulkan — build ncnn + engine with `NCNN_VULKAN=ON`; the engine already
  probes and reports GPU availability; compute preference plumbed via
  `rs_engine_options.compute`.
- Model registry — adding a model is a registry.json entry (plus its
  recorded SHA-256); no pipeline changes.

## Prompt 3 additions (v1.0.0)

- **Compute backend selection**: `rs_engine_options.compute` (0 AUTO =
  CPU, 1 CPU, 2 GPU) now flows from Settings → JobManager →
  `NativeJobRequest` → the worker isolate's engine. GPU is opt-in
  (experimental), requires a Vulkan-capable build + device, and falls
  back to CPU at model load or mid-run (bounded, restarts the tiled
  pass, surfaced via progress `attempt`).
- **Android storage integration**: a second MethodChannel
  (`realscale/media`) provides MediaStore gallery export
  (Pictures/RealScale-Pro on API 29+, app-external + media scan below),
  content-URI sharing and StatFs free-space reporting. JobManager
  exports after the app-storage save; failures are surfaced, never
  fatal.
- **Resumable model downloads**: ModelManager streams the package to a
  `.part` file (never fully in RAM) and resumes via HTTP Range; installs
  are atomic (tmp + rename) after SHA-256 verification.
- **Navigation guard**: PopScope on the processing screen prevents
  mid-run back navigation (job duplication hazard).
- **Branding**: generated launcher icon (adaptive + legacy) and
  light/dark splash from the Paper-Crafted palette
  (`tools/generate_icon.py`).
