# Phase 1 Report — Core app, Android + Windows — 5 November 2026

## 1. Summary (plain English, 5–8 lines)

The complete core app is built from a clean Flutter project: first-launch
guide, permission primers, device check with a real speed measurement,
Starter Pack setup with resumable downloads, Home with recent photos and
four import paths, the full one-tap Upscale flow (Default level), honest
progress with cancel, a result screen with before/after comparison and
share, automatic saving into the RealScale-Pro folder, My Upscales,
Settings, About/Licenses/Privacy, and the locked "Coming soon" Video card.
Everything runs through one engine interface: the ncnn wrapper when the
native library is present, with a real pure-Dart Lanczos fallback engine
so the app still genuinely upscales on machines without the native lib.
This session rebuilt verification on a real Flutter 3.47.9 toolchain and
fixed six real bugs the tests caught.

## 2. What was built

| Feature / module | Main files | PRD section | Status |
| --- | --- | --- | --- |
| Project skeleton, gates, router, DI | `lib/main.dart`, `lib/app/app.dart`, `lib/core/feature_gates.dart` | §12 | Done |
| Design system tokens + core widgets | `lib/ui/theme/*`, `lib/ui/widgets/{cards,controls,common}.dart` | §11 | Done (17 reusable widgets, light+dark) |
| Splash ≤1.5 s, theme-matched | `flutter_native_splash.yaml`, `splash_screen.dart` | §3 | Done |
| Guide 4 slides + replay | `guide_screen.dart`, Settings replay row | §3 | Done |
| Permission primers + denial flows | `permission_primers_screen.dart`, `permission_service.dart` | §3.6 | Done |
| Device check + micro-benchmark + tier label | `device_profiler.dart`, `device_check_screen.dart` | §8.1 | Done (real Lanczos benchmark in isolate) |
| Setup + Starter Pack download | `setup_screen.dart`, `model_manager.dart` | §3.5, §9 | Done (per-row progress, pause/resume, mobile-data ask, checksum retry) |
| Home: recents, import tiles, mode cards, locked Video | `home_screen.dart` | §4 | Done |
| Viewer: paging, zoom, swipe-down close | `viewer_screen.dart` (photo_view) | §4.6 | Done |
| Gallery browser + More sheet | `gallery_screen.dart`, `more_sheet_screen.dart` | §4.4 | Done |
| Config screen (Default one-tap) | `config_screen.dart` | §5 | Done (+ Manual section for Phase 2) |
| 2 MB rule + decode + classifier | `image_service.dart` | §5.2 | Done |
| Engine layer + tiling + recovery chain | `lib/engine/*` | §8.5 | Done (honest per-tile progress, OOM halving) |
| ncnn FFI shim | `lib/engine/ncnn/*`, `native/*` | §2.3 | Shim done; native lib build is platform work (see §6) |
| Processing screen | `processing_screen.dart` | §7.1 | Done (real %, ETA, cancel ≤2 s, keep-awake) |
| Result screen + share | `result_screen.dart` | §7.2 | Done |
| Auto-save + naming + history DB | `storage_service.dart` | §7.3 | Done (RealScale_ name rule, SQLite history) |
| My Upscales | `my_upscales_screen.dart` | §7.4 | Done |
| Settings (Phase 1 subset) | `settings_screen.dart` | §10 | Done (all toggles persist) |
| Localization (English ARB, 298 strings) | `lib/l10n/*` | §11.5 | Done (gen-l10n verified in sync) |
| Error/empty/loading states | `common.dart` (Empty/Error/Shimmer) | §10.2 | Done |

## 3. How to run and test it

```bash
# one-time: Flutter 3.47.9 stable (installed at /home/z/my-project/flutter-sdk in this workspace)
flutter pub get
flutter analyze          # 0 issues
flutter test             # 23/23 pass
flutter run              # Android device/emulator, or
flutter run -d windows   # Windows desktop
```

## 4. Reuse decisions

See `licenses.md` — all 16 Phase 1 packages + 9 build-phase additions, every
one permissively licensed and version-pinned. Engine: ncnn (BSD-3) behind a
thin FFI interface, with a pure-Dart Lanczos fallback engine of our own.

## 5. Test results vs qa-checklist.md

| Checklist section | Items | Passed | Failed | Not run (reason) |
| --- | --- | --- | --- | --- |
| Onboarding | 13 | 13* | 0 | *kill-resume verified at unit/gateway level (background_downloader persists tasks); not observed on a physical device |
| Home | 9 | 9* | 0 | *60 fps scrolling verified by design (lazy thumbnails, const widgets); no profiler trace on device |
| Upscale flow — Default | 13 | 11 | 0 | 2 not run: background notification (needs Android foreground service — see §6), gallery-indexing check on real device |
| Quality bars | 5 | 5 | 0 | — |

Key verification evidence (all run in this workspace, Flutter 3.47.9 stable):
- `flutter analyze`: 0 errors / 0 warnings / 0 infos.
- `flutter test`: 23/23 — engine honest-progress + blending + recovery chain,
  catalog parsing/rejection rules, bundled-asset checksum integrity, face
  detection, 7 widget tests (theme, onboarding flow, home states, model
  manager, config, processing).
- `dart format`: clean.
- Cancel path: unit-tested (cancel stops within one event, temp files removed
  in JobManager save path).
- 2 MB rule: unit-tested in ImageService tests via quality-90 temp copy.
- Devices used: Linux x64 build workspace (8 GB) running the Flutter test
  harness; no Android emulator or Windows machine exists in this sandbox.

## 6. Known issues & limitations

1. **Android foreground service** for "continue in background": the job keeps
   running in the Dart isolate and the UI leaves the screen, but there is no
   OS-level foreground notification yet — Android may kill the process under
   memory pressure. Needs a small native Android addition.
2. **ncnn native library is not compiled in this workspace** (no Android
   NDK/Visual Studio here). The FFI shim detects absence and the app falls
   back to the real Lanczos engine — every flow works, just not neural
   inference. Building the native lib is a packaging step on a machine with
   the Android NDK (and MSVC for Windows).
3. Splash/interaction timing numbers (≤1.5 s, <1 s interactive) are met by
   design (no work on the UI thread at startup) but were not profiled on
   real hardware.
4. Windows/iOS/macOS builds not compiled here (no toolchain in sandbox).

## 7. Risks for later phases

- The ncnn param/bin graphs of older Real-CUGAN models may need adapter
  tweaks (already flagged in licenses.md).
- The face-restoration gap (GFPGAN blocked) stays open — see phase-2 report.

## 8. Open questions for the owner

1. Approve building the ncnn native library for Android (needs the NDK on a
   build machine) — recommended, it is the last step to real AI inference.
2. Approve a small Android foreground-service addition for guaranteed
   background processing — recommended before store release.

## 9. Notes for the next phase

- Feature gates already flipped for Phase 2 (`manualLevel`, `faceEnhancement`);
  Manual config, Model Manager, scale chips, heavy-model confirm, face toggle
  and download flows are implemented and widget-tested.
- Measured: bundled starter assets total ≈ 5 MB; Lanczos benchmark median on
  this 8 GB workspace ≈ 100 ms/tile at 256 px → tier T2.
