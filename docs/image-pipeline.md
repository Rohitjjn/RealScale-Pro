# Image processing pipeline

End-to-end flow with the exact component responsible at each step.

## 1. Import

- Gallery/camera via `image_picker` (system picker; no storage
  permission needed on Android 13+, camera permission requested by the
  picker itself).
- Unsupported formats: magic-byte sniffing (`detectFormat`) — HEIC/HEIF
  rejected with an actionable message; unknown formats rejected with the
  actual leading bytes quoted.
- Safety limit: 12,000 px per edge (`JobManager.maxImageEdge`).

## 2. Inspection (`ImageService.inspect`)

Reads without full decode where possible:

| Fact | Source |
| --- | --- |
| Format | magic bytes (JPEG/PNG/WEBP/BMP/HEIC/HEIF) |
| Dimensions | JPEG SOF segment / PNG IHDR chunk (header-only) |
| Alpha | PNG color type; JPEG = false; others decoded |
| EXIF orientation | JPEG APP1 TIFF tag 0x0112 (pure-Dart parser) |
| File size | stat |
| Aspect ratio / MP | derived |

`needsOrientationFix` and `orientedDimensions` (swap for 5–8) are
precomputed so the plan is computed against the *display* orientation.

## 3. Device profiling (`DeviceProfiler` + Android MethodChannel)

Kotlin side (`MainActivity.kt`) reports `ActivityManager.MemoryInfo`
(total/available), Android version, primary ABI, and a real Vulkan probe
(`System.loadLibrary("vulkan")`). Nothing is invented: on non-Android or
when the channel is unavailable, RAM stays null and the tier falls back
to `balanced` with a conservative budget.

Tiers:

| Tier | Rule (total RAM unless noted) | Engine threads |
| --- | --- | --- |
| lowResource | < 3 GB | ≤ 2 |
| balanced | 3–6 GB | ≤ 4 |
| highPerformance | ≥ 6 GB and ≥ 6 cores, or GPU + ≥ 4 GB | ≤ 6 |

Inference budget = 70% of min(total, available) (tier defaults when
unknown).

## 4. Planning (`PipelinePlanner`)

Inputs: metadata, device profile, mode, model, JPEG quality setting.

Decisions, in order:

1. **Model** — compact (SRVGG) for low/balanced and default upscale;
   flagship RRDB for high-performance SR mode.
2. **Tile geometry** — family defaults modulated by tier (low: 96/64,
   balanced: 128/96, high: 160/128 px).
3. **Output format** — SR mode: PNG (lossless). Upscale: JPEG q∈[90,96]
   for opaque photographic sources; PNG when alpha or PNG source.
4. **Memory safety** — peak estimate (Dart mirror of the native formula,
   equality asserted by test) vs the device budget. If over: halve tiles
   until within budget (up to 32 px floor), recording a warning.
5. **Honest failure handling** —
   - SR mode with an unsafe plan: warning states the device cannot
     process this resolution safely; NO downsample proposal, ever.
   - Upscale mode with an unsafe plan: warning + optional **explicit**
     safer alternative (downsample target ≈ fitting edge), presented in
     the plan dialog and requiring user confirmation. Never automatic.

## 5. Model acquisition (`ModelManager`)

- Registry asset → metadata only.
- Download on consent: streamed with byte-count progress → zip extract →
  SHA-256 of both files compared against the recorded registry values →
  written app-privately.
- Corrupted → status reported, files not used; delete always available.

## 6. Decode (`ImageService.decodeToRgba`)

dart:ui codec (platform-native speed) → `rawRgba` bytes → EXIF
orientation transform (all 8 cases, pure Dart, unit-tested) so the
engine receives display-oriented pixels. Dimensions/aspect are preserved
exactly; no rotation surprises downstream.

## 7. Inference (native, see `native-engine-design.md`)

Tiled execution with context margins, seam-free normalized blending,
real progress events, cancel-between-tiles, bounded recovery.

## 8. Encode (isolate)

- PNG (lossless) or JPEG q∈[90,96].
- JPEG safety net: alpha composited over white (planner normally routes
  alpha sources to PNG).
- Runs via `compute` — off the UI thread.

## 9. Save (`StorageService`)

- `<app documents>/RealScale-Pro/`, name `<stem>_<mode>_<scale>x.<ext>`.
- Duplicates: " (n)" suffix — existing files are never overwritten.
- Atomic: `.tmp` + rename, with a free-space check.
- The original image is only ever read.

## 10. Results UI

Before/after slider (clipped stack + draggable divider), and only
measured facts: input/output dimensions, file sizes, duration, model,
format, recovery attempts if any. Large previews are cache-capped to
avoid memory duplication.
