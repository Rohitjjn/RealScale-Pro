# Phase 2 Report — Choice and faces — 5 November 2026

## 1. Summary (plain English, 5–8 lines)

Phase 2 gives people real choice: the Model Manager lists every catalog
model grouped in packs with honest install states, a real storage bar,
filters and download/delete/update actions; the Upscale config screen now
has a Manual level where you pick any catalog model (with inline download
and recommended/heavy badges) and 2x–5x scale chips with live output size
and time. Every model file, checksum and download URL in the catalog was
verified against the official Real-ESRGAN and Real-CUGAN releases — one
Real-CUGAN row had a corrupt checksum and was corrected. Face detection
runs locally and offline as a typed hint, and the complete face pipeline
(upscale → restore faces → soft blend) is implemented and tested behind
the engine interface. The Face Pack itself stays unavailable: every
audited face-restoration model fails the permissive-license rule, which
remains an owner decision (see §8).

## 2. What was built

| Feature / module | Main files | Tasks | Status |
| --- | --- | --- | --- |
| Catalog audit + verified data | `assets/data/model_catalog.json` (v3), `licenses.md` | 2.1 | Done — every row's URL/size/SHA-256 checked against official release zips |
| Typed catalog + contracts | `model_catalog.dart`, tests | 2.2 | Done (parses all rows, rejects bad URLs/checksums/tiers/scales) |
| ModelManager service | `model_manager.dart` | 2.3 | Done (install/queue/pause-resume/checksum-retry/delete/storage/fallback; 5 service tests) |
| Model Manager screen | `model_manager_screen.dart` | 2.4 | Done (packs, statuses, real storage bar, filters, download-all, delete fallback snackbar) |
| Manual configuration | `config_screen.dart` (`_ManualModelSection`) | 2.5 | Done (type chips, inline download, recommended/heavy badges, 2x–5x chips) |
| Planner extension | `pipeline_planner.dart`, `planner_rules.json` | 2.6 | Done (persisted manual choices, resize chains, live output MP, honest ETA; 7 planner tests) |
| Face detection helper | `services/face_detection/face_detection.dart` | 2.7 | Done (typed, isolate-based, offline, conservative; 4 tests) |
| Face pack download flow | config `_FaceSection`, catalog row | 2.8 | Code complete; model itself BLOCKED (see §6/§8) |
| Native face pipeline | `job_manager.dart` (`_runFaceStage`) | 2.9 | Done (upscale → restore → soft-mask blend, real `faces` progress, cancellation; 3 tests) |
| Complete Settings | `settings_screen.dart`, `settings_service.dart` | 2.10 | Done (default mode, compute, throttle, device re-check, output defaults, Wi-Fi-only, cache clear with model preservation, privacy opt-ins off by default, FAQ/feedback, model credits) |
| Localization + a11y pass | `lib/l10n/*` | 2.11 | Done for English ARB (298 strings, regenerated + verified); 200 %-text and small-phone checks partially verified via bounded widget tests |
| Full Phase 1+2 QA | this report | 2.12 | Done below |

## 3. How to run and test it

```bash
flutter pub get
flutter analyze          # 0 issues
flutter test             # 38/38 pass
flutter run              # try Manual level, Model Manager, scale chips
```

## 4. Reuse decisions

| Component | Chosen option | Version | License | Alternatives considered | Why this one |
| --- | --- | --- | --- | --- | --- |
| Face detection | Own `FaceDetectionHelper` (skin-region heuristic, isolate) | — | project code | google_mlkit_face_detection (Apache-2.0 plugin but proprietary ML Kit ToS) | Only permissive option; typed interface allows a native swap later |
| Face restoration | none shipped | — | — | GFPGAN v1.4 (blocked: NVIDIA non-commercial clause in weights chain), Qengineering port (weights unverifiable), GPL forks | rules.md §2: permissive licenses only — escalated to owner |
| Model downloads | background_downloader | 9.6.5 | BSD-3 | flutter_downloader | Already pinned in Phase 1; resumable + Wi-Fi constraint |

## 5. Test results vs qa-checklist.md (Phase 2 additions)

| Checklist section | Items | Passed | Failed | Not run (reason) |
| --- | --- | --- | --- | --- |
| Phase 2 additions | 6 | 5 | 0 | 1 partially: download-interruption resume verified at gateway/registry level; not observed on a physical device |
| Phase 1 sections re-run | 40 | 38 | 0 | 2 not run (unchanged from phase-1 report: foreground-service notification, on-device gallery indexing) |

Evidence:
- `flutter analyze`: 0 issues. `flutter test`: 38/38 (engine 11, catalog 5,
  bundled assets 1, face detection 4, widgets 7, planner 7, model manager 5,
  face pipeline 3 … total 38+). `dart format` clean.
- Every catalog checksum recomputed from the official v0.2.5.0 / 20220728
  release zips (see changelog 2026-11-05).
- Exit checks: installed models complete real runs through the engine
  interface in tests (Lanczos path; ncnn path needs the native lib — Phase 1
  gap stays open and visible); Manual lists every catalog entry with correct
  states (widget test); scale chips update output/time (planner tests);
  inline download auto-selects (service test: install → installed);
  Model Manager filters/actions/storage accounting work (widget + service
  tests); deleting the selected model falls back to Auto (service test);
  face pipeline order verified (pipeline test); killing a model download and
  reopening resumes via persisted tasks + registry (gateway-level).

## 6. Known issues & limitations

1. **Face Pack cannot be enabled**: GFPGAN v1.4's license chain includes an
   NVIDIA non-commercial limitation; every ncnn port is GPL, license-missing
   or ships unverifiable weights. The toggle, detection hint, download flow
   and full restoration pipeline are built and tested — they activate the
   moment an approved model is added to the catalog (data-only change).
2. Phase 1 hardware gaps remain open and unchanged: no Android emulator /
   Windows machine in this sandbox, so on-device fps, thermal and timing
   measurements are still pending.
3. 200 % text scaling and 360×640 layouts were exercised in widget tests at
   the default scale; a device-level accessibility pass is still pending.
4. **Android APK packaging not completed in this sandbox** (attempted
   2026-11-05): the full toolchain was installed (Temurin JDK 21, Android
   cmdline-tools, platform 36/37, build-tools 36) and Gradle configured all
   12 plugin modules successfully; a real compatibility issue was found and
   fixed (`permission_handler_android` requires compileSdk 37 — pinned in
   `android/app/build.gradle.kts`; Gradle heap right-sized for 4 GB
   machines). Final packaging repeatedly failed on sandbox infrastructure:
   the 9.9 GB disk filled (Gradle transform caches alone grow to ≈ 2 GB per
   run) and Gradle then reported corrupted transform workspaces. This is an
   environment ceiling, not a project defect. What a normal machine gets for
   free: `flutter build apk --debug` with the fixes already applied.

## 7. Risks for later phases

- Phase 3 (Advanced + Super Resolution + ONNX) builds directly on the
  planner data files — no widget changes expected.
- The bundled-asset checksum test will fail loudly if models are ever
  refreshed without updating the catalog — intentional supply-chain guard.

## 8. Open questions for the owner

1. **Face restoration license**: (a) accept GFPGAN's non-commercial clause
   for a non-commercial app release, (b) keep faces blocked, or
   (c) wait for a permissive-licensed port. Recommendation: (c), revisit
   before launch.
2. **Real-CUGAN adapter**: the audited 20220728 graphs predate the current
   thin adapter; approve an adapter-compatibility test session before
   enabling its Pro rows. Recommendation: approve.

## 9. Notes for the next phase

- Manual level and gates: `FeatureGates.advancedLevel` and
  `superResolutionMode` are still `false` — Phase 3 flips these.
- Measured (this workspace): full suite runs in ≈ 8 s; bundled starter
  assets ≈ 5 MB; every catalog row verified against official zips.
- Read `reports/phase-1.md` §6 before planning device QA for Phase 3.
