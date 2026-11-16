# RealScale-Pro Architecture

This document explains how **RealScale-Pro** (`v2.3.1`, application ID `com.realscale.pro`) is structured from Jetpack Compose UI down to native Vulkan/OpenCL execution.

---

## 1. High-Level Data Flow

```text
┌──────────────────────────────────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose + Warm Editorial Design Tokens)               │
│  Onboarding · Home · Viewer · Config/Advanced · Processing · Result ·    │
│  My Upscales · Model Manager · Settings/Help/About · QuickUpscaleSheet   │
└───────────────────────────────┬──────────────────────────────────────────┘
                                │ collectAsStateWithLifecycle (rememberState)
┌───────────────────────────────▼──────────────────────────────────────────┐
│  State & Planning Layer                                                  │
│  AppStore (@Stable) + StateFlow<AppUiState>                              │
│  ├── PipelinePlanner (deterministic scale, tile, memory, ETA, checks)    │
│  ├── ModelCatalog (14 verified models, tier fit, native scale mapping)   │
│  └── DeviceProfile + ThermalGuard (RAM/CPU/Vulkan tier & heat throttle)  │
└──────────────┬────────────────────────────────────┬──────────────────────┘
               │                                    │
┌──────────────▼─────────────────────┐ ┌────────────▼──────────────────────┐
│  Upscale Engine Layer              │ │  Model & Storage Layer            │
│  ProcessingServiceEngine           │ │  ModelRepository                  │
│  ├── EngineAssetExtractor (.tar.xz)│ │  ├── ResumableModelInstaller      │
│  ├── ImageContentClassifier (<30ms)│ │  │   (HTTP Range 206 + SHA-256)   │
│  ├── CropPreviewEngine (512×512)   │ │  ├── assets/model_manifest.json   │
│  ├── EngineJobQueue (1 active job) │ │  └── filesDir/models/<id>/        │
│  ├── PresetCommandResolver         │ │  RealScaleDatabase (Room 2.8)     │
│  ├── EngineProgressParser          │ │  ├── RecentCreationDao            │
│  └── OutputPostProcessor (EXIF/Q)  │ │  └── DownloadedModelDao           │
└──────────────┬─────────────────────┘ └───────────────────────────────────┘
               │ Bound foreground service + POSIX-escaped shell invocation
┌──────────────▼───────────────────────────────────────────────────────────┐
│  Native Runtime (cacheDir/realsr/ — arm64-v8a)                           │
│  ProcessingService + ImageProcessor + ShellUtils + GifUntils             │
│  realsr-ncnn · waifu2x-ncnn · srmd-ncnn · realcugan-ncnn · mnnsr-ncnn ·  │
│  Anime4k · resize-ncnn (avir-lancir) · magick                            │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Module Breakdown (`RealSR-NCNN-Android-GUI/app`)

### `design/` — Token-First Design System
- `Tokens.kt` defines 24 semantic color roles across `LightRsColors` (`#FAF9F5` warm cream canvas) and `DarkRsColors` (`#141311` obsidian canvas), plus `RsRadius` spacing/corner tokens.
- `Type.kt` provides `RsType.display` (*Cormorant Garamond*), `RsType.body` / `bodyTnum` (*Inter* + *Noto Sans Devanagari*), and `RsType.mono` (*JetBrains Mono*).
- `Motion.kt` centralizes transition timings and `rememberReduceMotion()` so users with system animations reduced receive instant, calm state changes.
- `Foundation.kt`, `Controls.kt`, `Sheet.kt`, and `icons/LucideIcons.kt` supply reusable buttons, grouped rows, segmented controls, sliders, modal/confirm sheets, and bottom toasts.

### `ui/` & `ui/state/` — Reactive Unidirectional Architecture
- `DesignApp.kt` implements navigation as an immutable route list (`AppUiState.routes`), unifying push/pop transitions, Android back handling, background job banner (`RunningJobCard`), and result stack rewrites.
- `AppStore.kt` (`@Stable`) owns navigation, per-mode `JobPrefs`, persisted `AppSettings`, active job progress, and model transfer states, throttling high-frequency download byte ticks to ~10 fps while delivering structural transitions on the tap frame.
- `MediaIntentsHost.kt` and `MediaLibrary.kt` manage system photo picker (`PickVisualMedia`), SAF document/folder pickers (`OpenDocument` / `OpenDocumentTree`), camera capture, share intents, and resilient URI intake.

### `core/plan/` — Deterministic Pipeline Planner
- `PipelinePlanner.kt` computes every number rendered on Config before execution: effective input optimization, neural model selection, tile size, peak memory estimate, output dimensions, rolling ETA, and green/amber/red pre-flight checks. Golden JSON vectors in `PipelinePlannerGoldenTest` pin behavior across edge cases.
- `ModelCatalog.kt` joins UI metadata (name, description, image kind, speed, minimum tier, license) directly to `model_manifest.json` via `manifestId`.

### `core/engine/` & Native Shell Bridge
- **Solid Asset Compression (`EngineAssetExtractor.kt`):** Upstream's native executables total ~105 MB raw (~37 MB individually ZIP-deflated) because OpenCV is linked into each binary. Compressing all arm64 executables and shared libraries together with `xz --arm64 --lzma2=dict=16MiB,preset=9e` shrinks them to ~15.6 MiB inside the APK and extracts byte-identically on first launch or version upgrade.
- **Exact Multi-Scale Pipeline (`PresetCommandResolver.kt` + `ProcessingServiceEngine.kt`):** Executes the selected model at a weight-supported scale factor (plus `-x` when TTA is enabled, `-t <tile>`, `-j <threads>`, and `-g -1` / `-b 0` on CPU), then chains `resize-ncnn -m avir-lancir` when `3×` or `5×` (or `2×` on a `4×`-only network) is requested.
- **Post-Processing (`OutputPostProcessor.kt`):** Re-encodes JPEG/WebP outputs at the requested quality level, writes to `Pictures/RealScale-Pro` via MediaStore or to the user's persisted SAF folder tree, and copies EXIF metadata afterwards so tags survive encoding.
- **Sequential Queue & Thermal Throttling:** `EngineJobQueue` serializes native tasks so two Vulkan allocations never collide; `ThermalGuard` tightens tile and thread limits dynamically when Android signals thermal pressure or low battery.

### `core/imaging/` — OOM-Safe High-Resolution Viewing
- `OomSafeImageDecoders.kt` pairs fast `inSampleSize`-downsampled full-image previews with viewport-scoped `BitmapRegionDecoder` tiles (`ZoomableImage.kt`), allowing smooth 1:1 inspection of 48–64 MP results within mobile memory limits.

### `core/model/` — Verified On-Demand Model Delivery
- `ResumableModelInstaller.kt` streams model files sequentially in-process into `.part` staging files, checks free disk space (with a 16 MiB safety margin), supports instant pause and HTTP `Range` (`206 Partial Content`) resume, verifies every file's SHA-256 digest against `model_manifest.json`, and atomically promotes verified models into `filesDir/models/<id>/`.

### `widget/` & Notifications
- `RealScaleWidgets.kt` delivers two `SizeMode.Exact` Jetpack Glance widgets (*Enhance* with `2× / 3× / 5×` quick-start chips and *Recent creations*), backed by XML initial/preview layouts.
- `ProcessingService.java` maintains three notification channels (see [`notification_logic.md`](../RealSR-NCNN-Android-GUI/docs/notification_logic.md)): silent progress (`channel_progress`), silent keep-alive (`channel_service_alive`), and high-priority completion (`channel_result`) with `Share` and `Open` deep links.
