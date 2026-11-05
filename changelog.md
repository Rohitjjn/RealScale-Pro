# RealScale-Pro — Changelog

Running log of every change, written by the agents (rules.md §7). Newest first.
Every entry: date, phase, what changed, why (one or two lines, plain English).

---

## 2026-11-05 — Phase 2 — Full model catalog verified against official releases
- Downloaded the official Real-ESRGAN v0.2.5.0 and Real-CUGAN 20220728 zips and
  re-computed every checksum. One Real-CUGAN row had wrong entries and a corrupt
  checksum; corrected with the real values. All catalog rows are now real.

## 2026-11-05 — Phase 2 — Bundled waifu2x fallbacks + supply-chain test
- Bundled the two MIT waifu2x upconv7 2x models (photo + anime) with recorded
  checksums and a new test that verifies every bundled file against the catalog.

## 2026-11-05 — Phase 1 — Verification on a real Flutter toolchain (3.47.9)
- Flutter SDK installed in the build workspace. `flutter analyze` 0 issues,
  `flutter test` 23/23 passed (engine, catalog, assets, widgets), `dart format`
  clean. Fixed: catalog sha256 validation crashed on every real checksum
  (256-bit value parsed as 64-bit int), tier names silently degraded to T1,
  cancel used the photo path instead of the job id, dynamic-color plugin could
  throw an unhandled error, storage bar showed a fake 40%, processing tile
  grid overflowed on wide windows.

## 2026-11-05 — Phase 2 — Widget tests for screens and Phase 2 features
- 7 widget tests cover theme tokens, the splash→guide→primers flow, home
  states (ready/denied), Model Manager rows and filters, the config screen
  (detection card, Manual section, face hint) and honest processing widgets.

## 2026-11-05 — Phase 2 — Face detection helper (task 2.7)
- Added a typed, isolate-based, fully offline face-detection hint
  (conservative skin-region heuristic). Removed `google_mlkit_face_detection`
  (proprietary ML Kit terms) — recorded in licenses.md.

## 2026-11-03 — Phase 2 — Model and face reuse audit
- Audited the planned CNN and face sources before copying code. Existing waifu2x
  fallbacks remain safe; older CNN candidates need owner approval and exact-file
  tests, while every current GFPGAN ncnn route is blocked by non-commercial,
  GPL, missing or unverifiable model terms.

## 2026-11-03 — Phase 2 — Kickoff and task plan
- Expanded Phase 2 into twelve small, testable tasks informed by the Phase 1
  report. Hardware gaps remain open while model choice, downloads, faces and
  complete Settings are built in order.

## 2026-11-03 — Phase 1 — Cloud verification for owner hand-off
- Added pinned GitHub Actions checks for formatting, analysis, tests, prohibited
  package policy, a real ncnn model run, and Android/Windows debug builds. Successful
  builds are retained for 14 days so Phase 1 can be checked without owning a PC.

## 2026-11-03 — Phase 1 — Verification and native compatibility fixes
- Verified the shared ncnn library on one-tile and nine-tile inputs, replaced the
  incompatible anime fallback with a matching permissive model, added Android
  hardware profiling, and registered native notices in the in-app license page.

## 2026-11-03 — Phase 1 — Tests and target hand-off
- Added portable unit tests and `tool/verify_phase1.sh`. JavaScript/data checks
  pass here; Flutter analysis and Android/Windows builds remain explicitly
  pending because this workspace cannot download the Flutter SDK or run targets.

## 2026-11-03 — Phase 1 — Interactive HTML preview
- Added a responsive browser preview for owner review. It supports image import,
  real tile-based preview progress, before/after comparison, saving and history;
  it is a UI preview, not the release AI engine.

## 2026-11-03 — Phase 1 — Results, saving and settings
- Added result comparison and sharing, automatic RealScale-Pro folder saving,
  local My Upscales history, deletion, themes, haptics, guide replay, privacy and
  open-source license screens.

## 2026-11-03 — Phase 1 — Default upscale flow
- Added image inspection, the 2 MB quality-90 temporary-copy rule, data-driven
  planning, a thin ncnn interface, tiled processing with completed-tile progress,
  moving ETA, cancellation, a safe CPU fallback and friendly failures.

## 2026-11-03 — Phase 1 — Home and image selection
- Added recents, viewer and zoom, Gallery/Files/Camera/More imports, persisted
  mode guidance, app options and the locked Coming soon card with no processing
  or media libraries for that future feature.

## 2026-11-03 — Phase 1 — First launch and design system
- Added the owner-approved neutral/blue/coral/amber palette, light and dark
  themes, native splash styling, four-slide guide, permission explanations,
  device check and offline Starter setup.

## 2026-11-03 — Phase 1 — Android and Windows app foundation
- Added the pinned Flutter project, Android 8+ and Windows 10+ shells, Riverpod,
  go_router, English localization data, planner/model data and feature structure.

## 2026-11-03 — Phase 0 — Engine feasibility decision
- Confirmed the owner-selected direct-upstream ncnn path and documented the
  available CPU result plus the Android, Windows and Vulkan checks that remain
  pending because this workspace does not provide those targets.

## 2026-11-03 — Phase 0 — Real ncnn CPU proof
- Added a repeatable native harness and two small, permissively licensed offline
  fallback models. A real model produced a 128×128 result from a 64×64 input in
  39 ms of engine time on the available 4 GB Linux workspace.

## 2026-11-03 — Phase 0 — Reuse and license audit
- Recreated the missing `licenses.md`, compared upstream engine approaches, and
  pinned the selected native and Flutter dependencies to audited permissive
  versions. GPL, AGPL and non-commercial options remain blocked.

## 2026-11-03 — Project setup — Phase 0 prep
- Created the complete project document set from PRD v2.0:
  `README.md` (agent kickoff), `rules.md`, `agents.md`, `architecture.md`,
  `phases.md`, `design-system.md`, `models.md`, `licenses.md`, `qa-checklist.md`.
- Established the phase loop: each phase ends with a report in `reports/`
  using the agents.md template; next phase starts only after owner approval.
- Decision framework added for reuse-first work, permissive licenses only,
  video feature permanently locked (UI card only).

## 2026-11-05 — Phase 3 — Pro: Advanced, Super Resolution, ONNX
- Unlocked `advancedLevel`, `superResolutionMode` and `onnxEngine` gates.
- Advanced level: tile size / overlap / threads / compute / output format /
  quality / output-cap overrides (persisted, planner-honored, threads
  clamped to cores).
- Super Resolution Mode is live: compression-off input (no 2 MB temp copy),
  lossless PNG output with metadata, mode-aware model selection from the SR
  auto table, and a green/amber/red feasibility check (peak-memory +
  megapixel budget) gating every run.
- Preview crop: full-screen drag/resize picker, engine-direct crop run with
  no gallery writes, before/after sheet with one-tap "Upscale full image".
- ONNX Runtime engine adapter behind the shared TilingEngine: thin FFI over
  OrtApi v20 (ordinals verified against the official header), RGBA↔NCHW
  float32 conversion, per-model session reuse, typed probe. Ultra rows
  (SwinIR/HAT/DAT) remain honestly `Coming soon` until audited weights ship.
- Home: the Super Resolution card now starts the real flow (first-time
  honest sheet with device-fit chip → picker → SR config).
- Catalog: x4plus-anime and realcugan-pro rows declare `superResolution`.
- 25 new tests (63/63), analyzer clean, formatted.

## 2026-11-05 — Phase 4 — Expansion and launch
- Hindi localization shipped: complete app_hi.arb (346 strings, validated
  placeholders), generated localizations, system/English/हिन्दी picker in
  Settings (persisted), MaterialApp locale wiring.
- Accessibility pass: 200 % text-scale widget test proves the config
  screen stays usable (all levels + sticky CTA).
- Store assets generated from brand tokens: feature graphic 1024×500,
  1024 px app icons (flat + rounded), 1080×1920 listing frame with Hindi
  CTA (store/, regenerable via scripts/gen_store_assets.py).
- iOS/macOS/Windows packaging remain honest sandbox gaps (documented in
  reports/phase-4.md §6); 65/65 tests, analyzer clean.

<!-- Agents: continue this file below. Format:
## YYYY-MM-DD — Phase N — short title
- what changed; why
-->
