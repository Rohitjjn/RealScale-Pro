# RealScale-Pro — Architecture

Version 1.0 · 3 November 2026 · Implements prd.md Section 12. Every module listed
here has a stable interface; agents build and test modules independently.

## 1. Layers

```
┌─────────────────────────────────────────────────────────┐
│ UI layer (Flutter widgets, screens, theme)               │  No AI logic here
├─────────────────────────────────────────────────────────┤
│ App state & navigation (Riverpod providers, go_router)     │
├─────────────────────────────────────────────────────────┤
│ Services (plain Dart, no UI imports)                      │
│  DeviceProfiler · PipelinePlanner · ModelManager ·      │
│  ImageService · JobManager · StorageService ·           │
│  PermissionService · SettingsService                    │
├─────────────────────────────────────────────────────────┤
│ Engine layer                                              │  No UI code here
│  UpscaleEngine (interface)                                │
│  ├─ NcnnEngine      (Phase 1)                             │
│  └─ OnnxEngine      (Phase 3)                             │
│  TilingEngine (split → process → blend → stream-write)      │
│  RecoveryChain (tile halving → GPU→CPU → threads → fail)  │
├─────────────────────────────────────────────────────────┤
│ Native layer (C/C++ reused upscaler code, thin FFI shim)  │
└─────────────────────────────────────────────────────────┘
```

Rules: dependencies point downward only. The UI never imports engine internals;
services never import Flutter widgets; the engine never knows screens exist.

## 2. Repository layout

```
realscale-pro/
├── prd.md  rules.md  agents.md  architecture.md  phases.md
├── design-system.md  models.md  licenses.md  qa-checklist.md
├── changelog.md
├── reports/                  # phase-N.md reports (mandatory)
├── android/  ios/  windows/  macos/  linux/   # Flutter platform shells
├── lib/
│   ├── main.dart
│   ├── app/                # app widget, router, theme wiring
│   ├── ui/
│   │   ├── theme/          # colors, type, radius, motion tokens (design-system.md)
│   │   ├── widgets/        # reusable components (Section 11.7 list)
│   │   ├── screens/
│   │   │   ├── onboarding/ # splash, guide, permission primers, device check, setup
│   │   │   ├── home/       # home, recents, viewer, gallery, import tiles
│   │   │   ├── config/     # upscale & super-resolution config (shared screen)
│   │   │   ├── processing/ # processing screen
│   │   │   ├── result/     # result viewer, my upscales
│   │   │   ├── models/     # model manager
│   │   │   └── settings/   # settings, help, about, licenses
│   ├── state/              # Riverpod providers, app state classes
│   ├── services/
│   │   ├── device_profiler/
│   │   ├── pipeline_planner/
│   │   ├── model_manager/
│   │   ├── image_service/
│   │   ├── job_manager/
│   │   ├── storage_service/
│   │   ├── permission_service/
│   │   └── settings_service/
│   ├── engine/
│   │   ├── upscale_engine.dart      # the interface + DTOs (JobSpec, ProgressEvent, EngineResult)
│   │   ├── tiling_engine.dart
│   │   ├── recovery_chain.dart
│   │   └── ncnn/                    # NcnnEngine, FFI bindings, progress stream adapter
│   ├── l10n/               # English strings (arb), Hindi in Phase 4
│   └── core/               # result types, errors, logging
├── native/                 # C/C++ reused code + FFI wrapper (ncnn, build scripts)
├── assets/
│   ├── models_starter/     # optional tiny bundled fallback models (PRD 13.4 #5)
│   ├── data/               # planner rules JSON, tiers, model catalog snapshot
│   └── animations/         # lottie files
└── test/                   # widget + unit tests mirroring lib/ structure
```

## 3. Data contracts (single source of truth)

### 3.1 JobSpec — the only object the UI ever sends to the engine

| Field | Type | Notes |
| --- | --- | --- |
| jobId | String | uuid |
| inputPath / inputBytes | String / Uint8List | original file path + decoded pixels |
| mode | enum {upscale, superResolution} | |
| settingLevel | enum {default, manual, advanced} | |
| imageType | enum {auto, photo, anime} | auto = let classifier decide |
| scale | int | 2, 3, 4, 5 |
| modelId | String | from models.md catalog |
| faceEnhancement | bool | + faceModelId |
| tileSize / overlap | int / enum | from planner or Advanced |
| computeDevice | enum {auto, gpu, cpu} + threadCount | |
| compression | enum {auto, off, custom} + quality | Upscale Mode 2 MB rule lives in ImageService, not here |
| outputFormat | enum {sameAsSource, jpeg, png, webp} + quality | |
| options | map | TTA, denoise, sharpen, resize method, color profile, metadata, output cap |

### 3.2 DeviceProfile

`{tier: T0..T3, uiLabel: Light|Standard|Powerful, totalRamMb, freeRamMb, cpuCores,
gpuName, vulkanSupported, vramMb?, benchmarkMsPerTile, freeStorageMb, osVersion,
batteryLevel, thermalState, measuredAt}` — produced by DeviceProfiler (prd §8.1),
stored locally, refreshed after app updates or on demand.

### 3.3 ModelManifest (remote JSON, prd §9.3)

```json
{
  "version": 3,
  "models": [{
    "id": "realesrgan-x4v3", "version": "1.2.0",
    "name": "Real-ESRGAN general compact", "engine": "ncnn",
    "pack": "starter", "minTier": "T0",
    "nativeScales": [4], "modes": ["upscale", "superResolution"],
    "bestFor": ["photo"], "speed": "veryFast", "sizeBytes": 9800000,
    "url": "https://.../model.param", "urlBin": "https://.../model.bin",
    "sha256": "...", "license": "BSD-3-Clause", "notes": ""
  }]
}
```

### 3.4 ProgressEvent (engine → UI stream)

`{jobId, stage: preparing|upscaling|faces|saving, percent: 0..1, tileIndex,
tileTotal, etaSeconds?, message?}` — percentages come from completed tiles only
(prd §7.1: honest progress). The UI maps tile completion to the fill animation.

## 4. Service responsibilities

- **DeviceProfiler** — detection + micro-benchmark (prd §8.1), produces DeviceProfile.
- **PipelinePlanner** — pure logic. Input: JobSpec (user choices) + DeviceProfile +
  catalog data → full plan (model, native scale + resample chain, tile size/overlap,
  compute device, input pixel limit, output cap, ETA baseline). All prd §8 tables are
  data files in `assets/data/`, loaded by this service. Never scattered in widgets.
- **ModelManager** — manifest fetch, download queue (pause/resume/background),
  checksum verify, install/delete, storage accounting (prd §9).
- **ImageService** — decode, EXIF orientation, CMYK/16-bit conversion, the 2 MB /
  quality-90 rule (prd §5.2), transparency protection, anime-vs-photo classifier,
  face detection (prd §5.4).
- **JobManager** — one active job, queue, cancel within 2 s, temp-file hygiene,
  background execution (Android foreground service; iOS keep-open guidance),
  ETA moving average, haptic checkpoints.
- **StorageService** — save to RealScale-Pro folder per platform (prd §7.3),
  history DB (drift/SQLite), My Upscales source, free-space checks.
- **PermissionService / SettingsService** — primers, denial flows, all settings.

## 5. Engine interface (Dart)

```dart
abstract class UpscaleEngine {
  String get engineId;                       // "ncnn" | "onnx"
  bool supports(ModelDef m, DeviceProfile d);
  Stream<ProgressEvent> run(JobSpec job);    // single stream: progress + final EngineResult (last event)
  Future<void> cancel(String jobId);
  Future<void> dispose();
}
```

TilingEngine implements prd §8.5: overlap blending, row streaming to disk, one
model loaded at a time, and the automatic recovery chain (halve tile → GPU→CPU →
fewer threads → friendly message). `NcnnEngine` wraps the reused native code in
`native/` behind FFI; it adapts native callbacks into `Stream<ProgressEvent>`.

## 6. Build-time feature gates

| Gate | Phase | Effect when off |
| --- | --- | --- |
| `faceEnhancement` | Phase 2 | toggle hidden, face models absent |
| `manualAndAdvanced` | Phase 3 | config screen shows Default only; level control hidden |
| `superResolutionMode` | Phase 3 | mode card visible but config opens with SR disabled state; PRD says card exists from Phase 1 with warning sheet — implement the card + warning, gate the actual SR pipeline |
| `onnxEngine` / ultra models | Phase 3 | catalog rows show "Coming soon" |
| `video` | never | locked card + bottom sheet only, zero video code |

## 7. Extensibility notes (do not implement now)

- Video (prd §12.1): a future `VideoService` should accept a JobSpec-like spec and
  reuse TilingEngine/engines; no existing screen or interface may need changes.
- ONNX engine (Phase 3) implements the same `UpscaleEngine`; UI and planner stay
  untouched. Ultra models appear in the catalog gated by `onnxEngine` and tier T3.
