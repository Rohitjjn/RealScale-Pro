# RealScale-Pro — Design Fidelity Ledger

Source of truth: `docs/ui-reference/` (the TSX/Tailwind prototype, git-ignored, never in the build).
Each row: TSX file -> Compose file -> status. Status legend: OK = matches design by line-by-line token check · DEV = deliberate deviation (reason) · PENDING · REMOVED (no real backing feature).

Viewport targets: phone width 360–412dp · light + dark · 1.3x font scale checked on main routes (M5).

## Tokens & type

| Design (index.css / app.css) | Compose | Status |
| --- | --- | --- |
| `:root` light tokens `--c-*` | `ui/theme/Color.kt` `RealScaleColors.Light*` | OK |
| `[data-theme="dark"]` tokens | `ui/theme/Color.kt` `RealScaleColors.Dark*` | OK |
| Radius scale 4/6/8/12/16/20 | `ui/theme/Shape.kt` | OK |
| Shadow `0 1px 3px rgba(20,20,19,.08)` (only shadow) | `ui/theme/Shape.kt` `ShadowSoft` | OK |
| Easing `cubic-bezier(0.22,1,0.36,1)` | `ui/theme/Motion.kt` `SpringEasing` | OK |
| Type: Cormorant Garamond 500/600 (display, -0.02em, lh 1.08, lining) | `ui/theme/Type.kt` + `font/cormorant_garamond_*.ttf` | OK |
| Type: Inter Variable (body) | `ui/theme/Type.kt` + `font/inter_*.ttf` (static instances of the variable face) | OK |
| Type: JetBrains Mono Variable (mono) | `ui/theme/Type.kt` + `font/jetbrains_mono_*.ttf` | OK |
| Devanagari fallback | `font/noto_sans_devanagari_*.ttf` registered in `Type.kt` fallbacks | OK |
| `eyebrow` utility (12/500/0.125em/uppercase) | `RealScaleType.Eyebrow` | OK |
| `tnum` utility (tabular lining) | `RealScaleType.Tnum` via `fontFeatureSettings "tnum","lnum"` | OK |
| Keyframes push-in/pop-in/fade/rise-in/sheet-in/out/pop/tile-pulse/shimmer/sweep | `ui/theme/Motion.kt` + per-component animations | OK |
| `prefers-reduced-motion` | honoured globally in `Motion.kt` (durations -> 0.01ms) + haptics-independent | OK |

## Components (ui.tsx / shared.tsx / media.tsx)

| Design component | Compose equivalent | Status |
| --- | --- | --- |
| Mark (brand SVG) | `ui/components/Mark.kt` (brand logo Image, never redrawn; draw anim -> fade-in sequence) | OK |
| Button (primary/secondary/tonal/ghost/dark/danger, lg/md/sm, loading states n/a) | `ui/components/Buttons.kt` | OK |
| IconButton (outline/plain/dark) | `ui/components/Buttons.kt` | OK |
| Screen (push/pop/fade enter) | `ui/RealScaleApp.kt` transitions | OK |
| TopBar (scroll-collapse border, close variant) | `ui/components/scaffold/TopBar.kt` | OK |
| BottomBar | `ui/components/scaffold/BottomBar.kt` | OK |
| Sheet (portal, drag-to-dismiss, focus, scrim) | `ui/components/Sheets.kt` (ModalBottomSheet replacement = custom for exact look) | OK |
| ConfirmSheet | `ui/components/Sheets.kt` | OK |
| Switch | `ui/components/Controls.kt` | OK |
| Segmented | `ui/components/Controls.kt` | OK |
| Chip | `ui/components/Controls.kt` | OK |
| Range (slider + value) | `ui/components/Controls.kt` | OK |
| Stepper | `ui/components/Controls.kt` | OK |
| Field | `ui/components/Controls.kt` | OK |
| Badge | `ui/components/Display.kt` | OK |
| Dot | `ui/components/Display.kt` | OK |
| RadioMark | `ui/components/Display.kt` | OK |
| Group / Row / SwitchRow | `ui/components/Display.kt` | OK |
| ProgressBar | `ui/components/Display.kt` | OK |
| Ring | `ui/components/Display.kt` | OK |
| Toast | `ui/components/Toast.kt` | OK |
| BeforeAfter (sweep animation, hold-to-original) | `ui/components/BeforeAfter.kt` | OK |
| TileFill (tile-pulse, tiles from engine progress) | `ui/components/TileFill.kt` | OK |
| ShareSheet | `ui/components/ModelAndPhotoSheets.kt` (system share; Download/Copy fallback dropped — N/A on Android) | DEV |
| PhotoDetailsSheet | `ui/components/ModelAndPhotoSheets.kt` | OK |
| PhotoAccessCard | `ui/components/ModelAndPhotoSheets.kt` | OK |
| ModelRow | `ui/components/ModelRow.kt` | OK |
| ModelPickerSheet | `ui/components/ModelPickerSheet.kt` | OK |

## Screens (route -> Compose file -> status)

| Route | Design | Compose | Status |
| --- | --- | --- | --- |
| splash (onboarding phase) | Onboarding.tsx `Splash` | `ui/screens/onboarding/Splash.kt` | OK |
| guide | Onboarding.tsx `Guide` | `ui/screens/onboarding/Guide.kt` | OK |
| perm-photos | Onboarding.tsx `Primer kind=photos` | `ui/screens/onboarding/Primer.kt` | OK |
| perm-notif | Onboarding.tsx `Primer kind=notif` | `ui/screens/onboarding/Primer.kt` | OK |
| device | Onboarding.tsx `DeviceCheck` | `ui/screens/onboarding/DeviceCheck.kt` | OK |
| setup | Onboarding.tsx `Setup` | `ui/screens/onboarding/Setup.kt` | OK |
| home | Home.tsx | `ui/screens/HomeScreen.kt` | OK |
| viewer | Viewer.tsx | `ui/screens/ViewerScreen.kt` | OK |
| config | Config.tsx | `ui/screens/ConfigScreen.kt` | OK |
| processing | Processing.tsx | `ui/screens/ProcessingScreen.kt` | OK |
| result | Result.tsx | `ui/screens/ResultScreen.kt` | OK |
| upscales | Library.tsx `Upscales` | `ui/screens/MyUpscalesScreen.kt` | OK |
| gallery | Library.tsx `Gallery` | `ui/screens/GalleryScreen.kt` | OK |
| models | Models.tsx | `ui/screens/ModelsScreen.kt` | OK |
| settings | Settings.tsx `SettingsScreen` | `ui/screens/SettingsScreen.kt` | OK |
| help | Settings.tsx `Help` | `ui/screens/HelpScreen.kt` | OK |
| about | Settings.tsx `About` | `ui/screens/AboutScreen.kt` | OK |

## Flow wiring (real data, no mocks)

| Concern | Implementation | Status |
| --- | --- | --- |
| Photos/Gallery/Viewer source | `core/photos/PhotoRepository.kt` (MediaStore + SAF imports) | OK |
| Albums tab | MediaStore buckets (`BUCKET_DISPLAY_NAME`) | OK |
| Permission per API level; denied -> system Photo Picker fallback | `ui/screens/onboarding/PermissionGate.kt` + Home | OK |
| Take a photo (ACTION_IMAGE_CAPTURE, FileProvider, no CAMERA perm) | Home camera tile | OK |
| Clipboard image | `core/photos/ClipboardImages.kt` + toast "No image found on the clipboard." | OK |
| Device check values | `core/engine/DeviceProfile.kt` (ActivityManager/Runtime/StatFs/Vulkan via PackageManager + GPU via EGL GL_RENDERER) | OK |
| Device tier | derived (Light <4GB · Standard 4–11 · Powerful >=12GB) | OK |
| Speed test row | one-time benchmark through engine when Starter present; else "Measured during setup" | OK |
| Planner (plan.ts port, pure + unit tests) | `core/engine/planner/PipelinePlanner.kt` | OK |
| ETA source | rolling per-model avg of real finished jobs (DataStore) + live progress rate; no constants | OK |
| Model catalog | real `assets/model_manifest.json` mapped into design's ModelRow (Starter=25MB real general+anime · Pro=heavier) | OK |
| 5x scale | 4x run + `resize-ncnn -s 1.25` post-step (CommandBuilder; golden test) | OK |
| 3x on x4-only models | 2x run + `resize-ncnn`/`magick` 1.5x post-step, or srmd native when installed | OK |
| 5x/3x post-step engine | `core/engine/PostProcessJob.kt` (resize-ncnn, CPU-safe) | OK |
| Preview crop 512x512 | real pipeline on crop via existing `CropPreviewEngine` | OK |
| Keep metadata | ExifInterface copy on JPEG/WebP outputs | OK |
| Wi-Fi only downloads | `ModelRepository` WorkManager UNMETERED constraint | OK |
| JobStrip | global bottom strip, tap to resume/open result | OK |
| Upscale Mode / Super Resolution Mode | kept; Super = original file untouched + PNG lossless | OK |
| Widgets | Glance restyled to v3 tokens | OK |
| Quick Upscale sheet | restyled translucent activity | OK |
| applicationId | `com.realscale.pro` (already applied in v2.1.0; re-verified) | OK |

## Removed (nothing real backing them — the design's prototype-only surface)

| Design element | Reason |
| --- | --- |
| Video Upscale row + sheet | No video pipeline exists |
| Enhance faces / face pack / GFPGAN / CodeFormer / faceQuality control | No face model in real catalog/manifest; not shippable |
| ONNX "Ultra" pack (SwinIR/HAT/DAT) + Pack descriptions | No ONNX models shipped; ncnn/MNN only |
| Community pack + helper models (classifier, face detector) | No catalog entries |
| TTA switch, FP16/FP32 precision control, tile overlap control | ncnn/realsr/waifu2x/realcugan CLIs expose no such flags |
| `max` tile preset "Maximum (no tiling)" | Not a valid CLI invocation for big images; crash risk; disabled in design anyway |
| Input "compression" control (re-encode at 90%) | Changes pixels the engine then reads; no honest benefit; removed |
| Output format quality slider (`formatQ`) | CLIs don't expose quality; ImageMagick post-encode would be double lossy re-encode |
| Color profile (Keep/sRGB) | No engine flag; would be a fake control |
| Image compression custom quality | Removed with the field above |
| "Rate and share the app" (5 stars) | No store listing; becomes "Share the app" (ACTION_SEND with repo link) |
| Usage analytics / Crash reports toggles | Nothing collected; become read-only info row |
| Help "Send feedback" with fake send | Becomes FAQ + link to repo Issues page |
| Privacy sheet toggles | Replaced by the read-only info row |
| About "Design preview / simulated" paragraph | No longer a simulated build |
| Licences: Flutter / ONNX Runtime / GFPGAN / SwinIR rows | Not dependencies anymore |
| FAQ "Enhance faces" tip & SwinIR-ish copy | Face pipeline removed |
| Guide/slides mention of faces art | No faces art in slides |
| Language picker "Hindi (coming soon)" disabled row | Only English ships |
| ShareSheet "Save image"/Download fallback | Android uses system share sheet |
| Desktop phone frame, rounded caps, desk wrapper | Browser-only presentation |
| SAMPLE_PHOTOS / fake DEVICE numbers / simulated jobs & downloads | Prototype data; all replaced by real sources |
| "Slow down when hot" + setting (throttling) | Engine has no pause/resume; hiding rather than faking |
| "Rate and share" star rating sheet | No Play Store page to rate |

## Verification log

| Check | Result |
| --- | --- |
| Repo visibility | PUBLIC (`gh repo view --json isPrivate` = false, 2026-11-13) |
| CI workflow | `.github/workflows/build-apk.yml` (ubuntu-24.04 only) — unit tests, lint, assembleDebug+Release, APK gate 30 MiB, emulator smoke |
| Local build | NOT possible (no Android SDK/dl.google.com in sandbox) — CI is authority |
| Reference prototype render | NOT built (no headless browser; only npm registry reachable) — compared line-by-line against TSX values instead |
| Screenshot tests | see M5 entry in AGENT_NOTES |
