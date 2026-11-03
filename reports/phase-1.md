# Phase 1 Report — Core app, Android + Windows — 3 November 2026

## 1. Summary
A complete Flutter project structure and the main Android/Windows user journey
now exist, plus an interactive browser preview for owner review. The app includes
first launch, permissions explanations, device profiling, Home, image selection,
Default configuration, real ncnn CPU processing, completed-tile progress, results,
saving, My Upscales and basic Settings. The shared native library loaded both
bundled models and completed a nine-tile image on the available Linux machine.
GitHub-hosted verification now passes formatting, analysis, tests, source policy,
real ncnn CPU inference, Android arm64 compilation and Windows compilation. The
resulting test packages are retained with the workflow run for 14 days. These
remote builds do not replace real Android/Windows hardware checks. Several QA
gaps listed below remain; this report is not store-ready sign-off, and Phase 2
must not start yet.

## 2. What was built
| Feature / module | Main files | PRD section | Status |
| --- | --- | --- | --- |
| Android 8+ and Windows 10+ shells | `android/`, `windows/`, `pubspec.yaml` | §1.2, §2.3 | Android arm64 and Windows debug builds pass in CI; hardware not run |
| App state, routes and contracts | `lib/app/`, `lib/state/`, `lib/core/models.dart` | §12.1–12.3 | Built |
| Owner-approved design system | `lib/ui/theme/`, `lib/ui/widgets/` | §11 | Built; browser reviewed only |
| Splash and four-slide guide | `lib/ui/screens/onboarding/` | §3.1–3.2 | Built |
| Photo/notification explanations and denial path | `lib/services/permission_service/`, onboarding screens | §3.3 | Built; platform dialogs not run |
| Device check and Android hardware channel | `lib/services/device_profiler/`, `MainActivity.kt` | §3.4, §8.1 | Built; Windows uses conservative fallback |
| Starter setup and background download foundation | `SetupScreen`, `model_download_service.dart`, `starter_manifest.json` | §3.5, §9.3 | Bundled models work; remote rows not wired to setup UI |
| Home, recents and import choices | `lib/ui/screens/home/home_screen.dart` | §4 | Core paths built; album browser and clipboard import missing |
| Full-screen image viewer | `lib/ui/screens/home/viewer_screen.dart` | §4.3 | Built |
| Mode cards and locked future card | `home_screen.dart` | §4.5, §10.3 | Built; no future media processing code or package |
| Default configuration and 2 MB rule | `config_screen.dart`, `image_service.dart` | §5.1–5.5 | Built |
| Data-driven planner | `pipeline_planner.dart`, `planner_rules.json` | §8 | Built |
| ncnn C interface and Dart adapter | `native/realscale/`, `lib/engine/ncnn/` | §2.3, §8.5 | CPU path tested on Linux and compiled for both targets; target runtime/Vulkan pending |
| Honest progress, ETA, cancel and Android foreground service | `processing_screen.dart`, `ProcessingService.kt` | §7.1 | Built; target runtime and ETA accuracy pending |
| Result compare, share and repeat | `result_screens.dart` | §7.2 | Built |
| Save rules, Android gallery copy and history | `storage_service.dart`, `MainActivity.kt` | §7.3 | Built; target runtime pending |
| My Upscales open/share/delete selection | `result_screens.dart` | §7.3, §10.2 | Built |
| Basic Settings/About/licenses/privacy | `settings_screens.dart`, `native_notices.txt` | §10.1 | Built |
| English localization | `lib/l10n/app_en.arb`, `l10n.yaml` | §11.6 | Built and generated sources verified in CI |
| Responsive owner preview | `preview/` | owner request | Running; UI preview only, not AI product |
| Verification hand-off | `test/`, `tool/verify_phase1.sh` | §13.2 | Formatting, analyzer, unit/widget tests and target compilation pass in CI |

## 3. How to run and test it

### Browser UI preview
From the repository root:

```sh
python3 -m http.server 4173 --bind 0.0.0.0 --directory preview
```

Open port 4173. Pick a JPEG/PNG/WebP/BMP, press Upscale and inspect the real
canvas work progress, result comparison, save button and local result gallery.
This preview deliberately uses browser resizing; it is not represented as AI.

### Flutter app on a suitable Android/Windows machine
Install the pinned Flutter 3.47.9 toolchain, then run:

```sh
flutter pub get
flutter gen-l10n
dart format --output=none --set-exit-if-changed lib test
flutter analyze
flutter test
flutter run                 # Android device/emulator
flutter run -d windows      # Windows 10/11 machine
```

For the release hand-off checks, the pull request automatically runs
`.github/workflows/phase-1-ci.yml`. It checks formatting, analysis, tests, source
policy, a real ncnn model, Android arm64 and Windows. Run `41651460328` passed all
four jobs at commit `04adf4838e0d2518d442228768564a71b9c0fa8a`:
https://github.com/Rohitjjn/RealScale-Pro/actions/runs/41651460328

The run retains `RealScale-Pro-Android-debug` (80,986,232 bytes) and
`RealScale-Pro-Windows-debug` (59,964,627 bytes) until 17 November 2026, so the
owner can download both without a PC. The local equivalent is:

```sh
RUN_ANDROID_BUILD=1 tool/verify_phase1.sh
# Windows is built by the pull-request workflow.
```

The first native build fetches the exact pinned ncnn commit from GitHub. No
model executable code is downloaded; the two small model data files are bundled.

### Native proof available in this workspace
The shared C interface was built with CMake/Ninja and called through Python
`ctypes`. A 130×130 image using a 64 px tile completed 9/9 tiles, produced a
260×260 output (202,815-byte PPM), and took 162 ms with two CPU threads.
Both photo and anime fallback models returned success.

## 4. Reuse decisions
| Component | Chosen option | Version | License | Alternatives considered | Why this one |
| --- | --- | --- | --- | --- | --- |
| State | flutter_riverpod | 3.4.3 | MIT | Provider, Bloc | Required by project rules; typed and maintained |
| Routes | go_router | 18.1.2 | BSD-3-Clause | Navigator only | Required by rules; declarative deep links |
| Recents | photo_manager | 3.12.0 | Apache-2.0 | image_picker only | Handles recent/limited photo access |
| Files/camera | file_picker + image_picker | 13.3.2 / 1.2.4 | MIT / BSD-3 | Custom platform pickers | Maintained system pickers |
| Permissions | permission_handler | 13.0.2 | MIT | Custom channels | Consistent denial/settings states |
| Downloads | background_downloader | 9.6.5 | BSD-3 | Custom HTTP downloader | Resume, persistence, Wi-Fi restriction and background work |
| Image preparation | image | 4.5.4 | MIT | Native encoders only | Orientation, quality-90 copy, alpha and output encoding |
| Engine | ncnn upstream | pinned commit `6125c9f…` | BSD-3-Clause | ONNX Runtime, third-party app code | Owner approved; small shared Android/Windows core |
| Offline models | waifu2x photo/anime upconv7 2x | audited repository files | MIT | Real-ESRGAN release assets, Real-CUGAN | Small, same compatible graph, available from audited source |
| Sharing/settings/device | share_plus, shared_preferences, device_info_plus | exact pins in `Docs/licenses.md` | BSD-3 | Platform code | Maintained and permissive |
| Viewer/compare/guide dots | Flutter built-ins | Flutter SDK | BSD-3 | Extra viewer/onboarding packages | Fewer dependencies and adequate behavior |

## 5. Test results vs qa-checklist.md
| Checklist section | Items | Passed | Failed | Not run (reason) |
| --- | ---: | ---: | ---: | --- |
| Onboarding | 12 | 5 | 1 | 6 — timing, platform permissions and interrupted network download require Android |
| Home | 8 | 5 | 2 | 1 — 60 fps/platform recents requires Android; album browser and clipboard path are missing |
| Default upscale flow | 11 | 4 | 3 | 4 — ETA accuracy, gallery visibility, cancel timing and low-storage behavior require targets |
| Quality bars | 5 | 3 | 0 | 2 — low-memory recovery and frame rate require real target hardware |
| Native CPU proof | 4 | 4 | 0 | 0 — load, photo model, anime model and nine-tile progress passed on Linux |
| Portable source checks | 4 | 4 | 0 | 0 — JavaScript syntax, three JSON files and prohibited-package scan passed |

Native runtime device used: Arena Linux x86_64, 3.8 GiB RAM, two ncnn threads,
no swap and no Vulkan SDK/driver. GitHub-hosted Ubuntu, Android and Windows jobs
verified code quality and compilation. Android Light and Windows Light hardware
were **not available**.

Specific failed checklist behavior:
1. Setup shows bundled/queued rows but does not yet bind remote transfer speed,
   MB and ETA to its UI, so resume-after-kill cannot be demonstrated end to end.
2. “See all” does not yet open an album browser; Gallery uses the safe system picker.
3. More does not yet paste an image from the clipboard.
4. Processing fill is tied to real completed-tile percentage, but displays the
   original preview rather than completed native output tile pixels.
5. Native output is assembled in a guarded memory buffer rather than streamed
   to disk in strips. Oversized jobs fail safely, but the full recovery chain is incomplete.
6. ncnn target code currently uses CPU. Vulkan integration and driver-to-CPU
   recovery require the Android/Windows hardware pass.

Commands actually run here:
- CMake/Ninja build of `native/spike` and `native/realscale`: passed.
- One-tile photo and anime model calls: passed.
- Nine-tile native call: passed, 9/9 real progress events.
- `node --check preview/app.js`: passed.
- JSON parse of localization/planner/manifest: passed.
- Prohibited future-media package/API scan: passed.
- GitHub Actions with Flutter 3.47.9: formatting, analyzer, unit/widget tests,
  tracked-source policy, Android arm64 and Windows debug builds all passed.
- `tool/verify_phase1.sh`: portable checks passed locally; its Flutter portion
  remains unavailable in this sandbox because the SDK download host is blocked.

## 6. Known issues & limitations
- Phase 1 does **not** meet its formal exit check until the checklist runs pass
  on Android Light and Windows Light hardware. No `phase-1` tag was created for that reason.
- Vulkan is not enabled in the app native library yet; current ncnn inference is CPU.
- The automatic fallback is a safe high-quality resize, not an AI model. It is
  used only if the native library cannot load, and must remain visibly documented.
- The setup downloader service supports resume, pause, Wi-Fi and checksums, but
  the setup progress rows are not connected to a published remote Starter manifest.
- The image classifier is a small color-statistics heuristic. Its required
  100-image/90% accuracy check was not run.
- Windows hardware values use conservative estimates until its native profiler
  channel is added.
- Desktop drag-and-drop and keyboard shortcuts are not implemented.
- Recents “See all”, in-app albums and clipboard import are not implemented.
- The native processor guards very large buffers but does not stream output strips
  or blend overlapping tiles yet. The observed synthetic seam edge difference was
  small, but the required visual image set was not run.
- Android foreground notification, MediaStore gallery insertion and legacy save
  permission are coded but untested on devices.
- The Windows package uses a generated blue “R” development icon; it is valid
  packaging artwork but is not an owner-approved final mark.
- The browser preview is intentionally separate from the release pipeline and
  must never be used as evidence that native AI processing passed.

## 7. Risks for later phases
Starting Phase 2 now would hide Phase 1 risk. CI compilation and Flutter quality
checks pass; the next step is low-memory cancellation and native runtime checks
on real target hardware. The highest product risks are output-buffer memory, Vulkan
driver variation, download hosting reliability and visual seams. Real-ESRGAN compact
weight terms and release delivery still need final verification before replacing
the audited fallback models.

## 8. Open questions for the owner
1. **Recommended: keep Phase 1 open for hardware verification.** Alternative A
   is to call this a CI-built UI/CPU preview milestone; alternative B is to mark
   it complete. Marking it complete is not recommended because compilation passed,
   but required Android/Windows runtime tests have not run.
2. **Starter download content:** recommended option is to publish only audited,
   permissive model data in the selected GitHub Release after target testing.
   The alternative is a placeholder remote manifest, which is not recommended.
3. **App icon:** recommended option is to approve/commission the final mark after
   the core flow passes. The current development icon is suitable only for test packages.

## 9. Notes for the next phase
The owner approved starting Phase 2 on 3 November 2026 after reviewing this
report. The unresolved Phase 1 hardware checks remain open and must not be
silently treated as passes. CI with Flutter 3.47.9 is green. Test the exact
Android 2 GB and Windows 4 GB profiles when hardware becomes available,
then connect setup rows to the release manifest, add streamed strip output and
overlap blending, enable Vulkan with CPU recovery, and complete albums/clipboard.
Measured Linux baseline: 64×64 → 128×128 at 39 ms in the Phase 0 harness; current
130×130 nine-tile native run at tile 64 took 162 ms with two threads.
