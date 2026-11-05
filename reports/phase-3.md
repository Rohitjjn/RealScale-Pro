# Phase 3 Report — Pro — 5 November 2026

## 1. Summary (plain English, 5–8 lines)

Phase 3 unlocks the Pro tier. **Advanced level** joins Default and Manual
with real pipeline overrides (tile size, tile overlap, CPU threads,
compute device, output format, JPEG quality, output-size cap) that all
reach the planner and persist across restarts. **Super Resolution Mode**
is now a live pipeline, not a locked card: it runs compression-off
(never creates the quality-90 temp copy), always saves lossless PNG with
metadata, and gates every run behind a new **green/amber/red feasibility
check** (peak-memory + megapixel estimate vs free RAM and the device
cap). A **Preview crop** lets people upscale only a chosen region first —
the engine runs on the crop, nothing is saved to the gallery, and the
result appears in a before/after sheet. The **ONNX Runtime engine**
adapter ships behind the engine interface with a real FFI binding
(OrtApi v20, ordinals verified against the official header); the Ultra
rows (SwinIR, HAT, DAT) stay honestly `Coming soon` until audited
weights exist, which phases.md explicitly allows. All three feature
gates are flipped; Ultra stays data-off.

## 2. What was built

| Feature / module | Main files | Status |
| --- | --- | --- |
| Feature gates flipped | `lib/core/feature_gates.dart` | Done (`advancedLevel`, `superResolutionMode`, `onnxEngine` = true) |
| Feasibility checker | `lib/services/feasibility/feasibility_checker.dart` | Done (pure function; green/amber/red; typed reasons; preview-aware) |
| SR mode pipeline | `lib/state/providers.dart` (`ConfigState.mode`, `ensureInputRule`, `JobNotifier.run`) | Done (SR auto-table model resolution, mode support check, PNG lossless out, red-run last-line block) |
| Advanced options + persistence | `lib/services/settings_service/settings_service.dart`, `providers.dart` (`AdvancedOptions`) | Done (7 keys persisted, loaded into config) |
| Advanced UI section | `lib/ui/screens/config/advanced_section.dart` | Done (6 chip rows + quality slider; Auto defaults) |
| Planner overrides | `pipeline_planner.dart`, `engine_types.dart` (`overlapExplicit`) | Done (tile/overlap/threads/compute/cap reach the plan; threads clamped to cores) |
| Preview crop | `lib/ui/screens/config/crop_picker_screen.dart`, `sr_section.dart` (`PreviewResultSheet`), `providers.dart` (`PreviewNotifier`) | Done (drag/resize picker, engine-direct crop run, temp hygiene, before/after) |
| SR sections | `lib/ui/screens/config/sr_section.dart` | Done (lossless explainer, feasibility banner, preview card) |
| Config 3-way level control | `lib/ui/screens/config/config_screen.dart` | Done (Default/Manual/Advanced; SR title + PRO chip; amber confirm; red blocked sheet with one-tap switch to Upscale) |
| Home SR flow | `lib/ui/screens/home/home_screen.dart` | Done (first-time honest sheet with device-fit chip → picker → config in SR mode; flag persisted) |
| ONNX Runtime FFI | `lib/engine/onnx/onnx_ffi.dart` | Done (OrtGetApiBase → OrtApi v20 indexed ordinals; env/session/tensor/run/status; UTF-16 Windows paths; telemetry disabled; typed status errors) |
| OnnxEngine | `lib/engine/onnx/onnx_engine.dart` | Done (TilingEngine subclass; RGBA↔NCHW float32; 8-px padding; per-model session reuse; tier gating) |
| Registry wiring | `lib/main.dart` | Done (`OnnxEngine.tryCreate()` inserted before the Lanczos fallback) |
| Localization | `lib/l10n/app_en.arb` (+45 keys), regenerated | Done |
| Catalog SR modes | `assets/data/model_catalog.json` | Done (x4plus-anime + realcugan-pro now declare `superResolution`) |
| Tests | 4 new test files + 3 widget tests | 25 new tests (63/63 total) |

## 3. How to run and test it

```bash
flutter pub get
flutter analyze          # 0 issues
flutter test             # 63/63 pass
flutter run              # Home → "Super Resolution" card → pick a photo
                         #   → lossless banner + device check + preview crop
                         # Config → level "Advanced" → tile/overlap/threads/
                         #   compute/format/cap overrides
```

## 4. Reuse decisions

| Component | Chosen option | Version | License | Alternatives considered | Why this one |
| --- | --- | --- | --- | --- | --- |
| ONNX Runtime binding | Own thin FFI (indexed OrtApi ordinals) | API v20 (runtime 1.20.x) | MIT (runtime), project code | Full struct bindings (onnxruntime pub packages are GPL-adjacent or stale), platform channels | Ordinal indexing avoids pinning a 285-field struct; only 30 functions bound; runtime stays swappable |
| Crop picker | Own `CropPickerScreen` | — | project code | image_cropper (plugin, platform dialogs), custom painter libs | Matches design system, no new dependency, returns pixel-space rect directly |
| Feasibility estimate | Own pure-Dart checker | — | project code | Actual allocation probe (risky), ML模型 memory profiler | Deterministic, unit-testable, honest — estimates only, never fake measurements |

## 5. Test results vs qa-checklist.md (Phase 3)

| Checklist section | Items | Passed | Failed | Not run (reason) |
| --- | --- | --- | --- | --- |
| Phase 3 (this phase) | new coverage | 25 | 0 | on-device run pending (no device in sandbox) |
| Phase 1+2 regression re-run | 38 | 38 | 0 | — |

Evidence:
- `flutter analyze`: **0 issues**. `flutter test`: **63/63** (feasibility 8,
  ONNX 8, SR pipeline 5, advanced planner 5, 24 MP 4, widget screens +3 …).
- 24 MP exit check (software path): planner accepts 6000×4000 on T3
  (input limit 34 MP), output clamps to exactly 96 MP, tile budget finite,
  ETA formula consistent (±5%), and a real 512-px tile resamples to 2048-px
  through the shared engine stack without crashing.
- SR lossless: `ensureInputRule` provably skips the 2 MB temp copy in SR
  mode; output kind pinned to PNG; catalog rows declare `superResolution`.
- Feasibility: green fits / amber warns / red blocks covered by tests,
  including "preview of a blocked photo is not memory-blocked".
- ONNX: probe returns a typed result when the runtime is absent (this
  sandbox); planner rejects ONNX without Vulkan and plans cleanly with it;
  bundled Ultra rows refuse to run while `comingSoon` (supply-chain guard
  the moment audited weights land, enabling them is a data-only change).
- Widget tests: SR config shows the lossless banner, green check, and
  preview card; Advanced config renders every override row and inherits
  scale/model sections; the Home SR card opens the honest first-time sheet
  and Continue persists `srWarningSeen`.

## 6. Known issues & limitations

1. **On-device run of the 24 MP exit check remains open** — this sandbox
   has no Android/Windows device (unchanged hardware gap from Phases 1–2).
   The software path is proven at the planner/budget/tile level; fps,
   thermal and real end-to-end timing still need a capable device.
2. **Ultra pack stays `Coming soon` by decision** (phases.md escape
   hatch): no audited, permissively licensed SwinIR/HAT/DAT ONNX weights
   with verifiable checksums exist in this workspace, and the ONNX Runtime
   shared library cannot be bundled without a real device build. The
   engine adapter, catalog rows and planner path are ready; enabling =
   verifying weights + flipping `unavailableReason` (data-only change).
3. The ONNX FFI binding is compiled and analyzed but **not exercised
   against a live runtime** here (no `libonnxruntime.so` on this sandbox);
   a dev-machine override path (`tryLoad(overridePath:)`) exists exactly
   for that verification session.
4. Preview runs use the auto-selected SR model only (no Manual model
   choice inside the preview sheet) — the full run honors Manual/Advanced
   selections; noted as a deliberate simplification.

## 7. Risks for later phases

- Phase 4 (iOS/macOS/Linux) needs ONNX + ncnn static-link decisions per
  platform; `tryLoad` already refuses unsupported platforms gracefully.
- The bundled-catalog checksum test still guards the supply chain; the
  two re-purposed SR-mode rows changed `modes` only (no binary changes).
- Advanced overrides are per-job, not per-profile; if the owner wants
  "presets", they layer cleanly on `AdvancedOptions`.

## 8. Open questions for the owner

1. Approve an **adapter-compatibility session** for the ONNX engine on a
   real device (onnxruntime + verified Ultra weights) before Ultra goes
   live — same pattern as the Real-CUGAN adapter question from Phase 2.
2. Confirm the feasibility thresholds (amber at 35 %, red at 60 % of free
   RAM; 15 min "very slow" warning on T0–T1) — they are conservative and
   test-pinned; field data may justify loosening them.

## 9. Notes for the next phase

- All Phase 3 gates are on; Phase 4 scope (iOS/macOS/Linux, Hindi,
  accessibility, store assets, beta, tuning) can start from `reports/`
  + git tag `phase-3`.
- Known Phase-1/2 hardware gaps remain visible and unchanged; nothing in
  this phase narrowed or widened them.
