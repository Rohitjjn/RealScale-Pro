# PRD — RealScale Pro v1 (Prompt 2 state)

> Rebuilt after the workspace reset. This PRD restates the product
> contract from the Prompt 1/2 briefs and tracks the ACTUAL Prompt 2
> implementation state of each requirement.

## Product

Offline-first AI image enhancement for Android. Two modes, one promise:
**honest processing** — real on-device inference, no fake progress, no
silent quality reduction, original images never modified.

### Modes

- **Upscale Mode** — practical: auto model/strategy selection, JPEG
  output quality 90–96 (quality ≠ dimensions), explicit-only
  downsampling proposals, original preserved.
- **Super Resolution Mode** — quality-first: no lossy steps in the
  processing chain, no silent downsampling, tiling for large images,
  honest refusal when the device cannot safely process.

## Requirement status (Prompt 2)

| Requirement | Status |
| --- | --- |
| Native engine via FFI (C ABI) | ✅ implemented + verified |
| Real AI inference (ncnn) | ✅ verified on host incl. real photos |
| Tiled inference, seam-free | ✅ verified (max 7/255 deviation) |
| Memory recovery (tile reduction) | ✅ verified via failure injection |
| Cancellation | ✅ verified (native + Dart) |
| EXIF orientation (all 8) | ✅ unit tested |
| Device tiers & adaptation | ✅ implemented; Android RAM via channel |
| Pipeline planner w/ honest warnings | ✅ implemented + unit tested |
| Model registry + consent download + SHA-256 | ✅ implemented |
| Storage (atomic, non-destructive) | ✅ implemented + tested |
| Processing screen (real progress/ETA/recovery) | ✅ implemented |
| Results (before/after + measured facts) | ✅ implemented |
| Models / Settings / About screens | ✅ implemented |
| Onboarding + consent | ✅ implemented |
| Web build | ✅ compiles; engine honestly unavailable |
| Android APK | ⛔ blocked by sandbox disk (see BUILD.md) — wiring complete |
| On-device Android verification | ⏳ Prompt 3 |
| Share sheet, EXIF passthrough, gallery save | ⏳ Prompt 3 candidates |

## Non-goals (v1)

- Cloud processing, accounts, telemetry (privacy by design).
- Video super resolution.
- Batch queues beyond a single active job.
- Restricted-license models (CodeFormer etc.) without license clearance.
