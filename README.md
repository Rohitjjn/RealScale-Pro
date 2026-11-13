# RealScale-Pro

**Private, offline-first AI photo upscaler for Android — rebuilt in Jetpack Compose with a ~20.2 MB APK, instant Open-with Quick Sheet, and an on-demand SHA-256 verified Model Hub.**

RealScale-Pro (`v2.1.0`, `com.realscale.pro`) modernizes [`tumuyan/RealSR-NCNN-Android`](https://github.com/tumuyan/RealSR-NCNN-Android) (`v1.13.2`) into a calm, warm editorial Android experience anyone can use in three taps — while keeping every native NCNN / MNN / Vulkan / OpenCL upscaling engine 100% intact under the hood.

---

## What's New in v2.1.0

- **Brand Identity & Adaptive Icon Suite**
  - Official RealScale-Pro eagle medallion across adaptive (`ic_launcher_foreground` + `#BD4707` terracotta field), Android 13+ `<monochrome>` themed icon, legacy square/round mipmaps, Android 12+ `SplashScreen`, status-bar silhouette (`ic_stat_realscale`), empty states, and About screen.
  - Application ID finalized as `com.realscale.pro` (`com.realscale.pro.debug` for debug builds).
- **System-Wide `"Open with"` & `"Share to"` Quick Upscale Sheet**
  - Registers both `ACTION_VIEW` (`Open with` / `Edit with` from Gallery & Files) and `ACTION_SEND` / `ACTION_SEND_MULTIPLE` (`Share`).
  - Opens a translucent `QuickUpscaleActivity` bottom sheet directly over the source app with live resolution preview, `2×`/`4×` toggle, preset chips, `"Enhance"`, and `"Open in RealScale-Pro"`. Configurable in Settings (`Quick sheet` vs `Full app`).
- **Sequential Engine Queue & Rich Notifications**
  - Sequential job queue (`1` active GPU/Vulkan job + queued batch items `"2 of 5"` with per-item cancel) preventing simultaneous Vulkan OOM crashes.
  - Silent ongoing foreground progress notification with live percentage, ETA, and **Cancel** action, plus a `BigPictureStyle` completion notification with **Share** and **Open** deep-link actions.
- **Honest Stages, Pre-Flight Guardrails & OOM-Safe Tile Split Viewer**
  - Human-readable stages (**Preparing** → **Enhancing** → **Saving**) with rolling-average ETA when determinate percentages arrive and elapsed timer otherwise — zero raw CLI stderr leaked to users.
  - Pre-flight megapixel, output dimension (`16,384 px` ceiling), and storage checks with one-tap remedies (`Switch to 2×`, `Safe tile size 128`, `Use CPU`).
  - Dual-layer `BeforeAfterSplitView` combining a downsampled base preview with viewport-cropped `BitmapRegionDecoder` tiles at zoom `> 1.5×`, tap-and-hold original peek, and double-tap `100%` toggle.
- **Fast `"Preview"` Crop Comparison Sheet**
  - Extracts the sharpest `224×224` high-detail patch via 3×3 gradient-energy scoring and upscales just that patch in ~1–2 seconds inside a `ModalBottomSheet` before committing to a full-image upscale.
- **Studio, Dedicated Library & Scalable Model Hub**
  - **Studio**: Single editorial drop-zone card, content-aware `"Suggested for this photo"` heuristic (`Photo` vs `Anime & Art`), stacked batch strip, and sticky CTA bar with live output resolution and estimated duration.
  - **Library**: Dedicated 3-tab destination (`Studio` · `Library` · `Models`) with search, sort, multi-select **Share / Compare / Delete**, and full metadata.
  - **Model Hub**: Storage summary header, 2-model **Starter Pack**, `Installed` vs `Available` sections, debounced search, sort (`Recommended`, `Fastest`, `Smallest`, `Best quality`, `Name`), counted filter chips, Wi-Fi-only toggle, and global download banner.
- **Home-Screen Widgets, Tablet Layout, Hindi Localization & Baseline Profile**
  - Jetpack Glance `1.2.0` **Small** (`Enhance a photo`) and **Medium** (`Recent creations`) home-screen widgets.
  - `WindowWidthSizeClass` responsive navigation rail and two-pane Studio layout on tablets, foldables, and landscape (`>= 600dp`).
  - Complete English + **Hindi (`हिन्दी`, `values-hi`)** UI localizations with Devanagari-safe `includeFontPadding` and expanded line heights.
  - Bundled ART **Baseline Profile** (`baseline-prof.txt` + `androidx.profileinstaller:1.4.1`) for fast cold start and jank-free scrolling.

---

## APK Size Comparison

| Build | Upstream `1.13.2` | RealScale-Pro `2.1.0` |
| --- | --- | --- |
| **Release APK (`arm64-v8a`)** | `~475 MB` | **`20.21 MiB`** (`21,195,286` bytes) |
| **Debug APK** | `~480 MB` | **`32.85 MiB`** (`34,440,564` bytes) |
| **Bundled AI Model Weights** | `~440 MB` (`models-*`) | **`0 bytes`** (downloaded on demand, SHA-256 verified) |
| **Bundled Native CLI Engines** | All 8 CLI tools | **All 8 CLI tools** (100% unmodified) |

---

## Architecture

RealScale-Pro lives in `RealSR-NCNN-Android-GUI/` (`RealSR-NCNN-Android-CLI/` remains untouched upstream C++/JNI reference source):

```text
com.tumuyan.ncnn.realsr (applicationId: com.realscale.pro)
├── MainActivity.kt                  # Single-activity Compose host + SplashScreen + share/widget/deep-link routing
├── QuickUpscaleActivity.kt          # Translucent "Open with" / "Share" quick upscale bottom-sheet activity
├── widget/
│   └── RealScaleWidgets.kt          # Jetpack Glance 1.2.0 Small ("Enhance") & Medium ("Recent creations") widgets
├── core/
│   ├── database/                    # Room 2.8 (RecentCreationEntity, DownloadedModelEntity, DAOs)
│   ├── engine/                      # StateFlow UpscaleEngine, EngineJobQueue, PreflightAnalyzer, EngineProgressParser,
│   │                                # ImageContentClassifier, CropPreviewEngine, PresetCommandResolver, binaries.tar.xz extractor
│   └── model/                       # 14-model HuggingFace manifest, ModelHubQueryEngine, HTTP Range ResumableModelInstaller
├── ui/
│   ├── theme/                       # Claude Warm Editorial palette (#FAF9F5 / #181715 / #CC785C), Devanagari-safe typography
│   ├── components/                  # AdaptiveLayout (SideRail/TwoPane), OomSafeImageDecoders, BeforeAfterSplitView, CropPreviewSheet
│   ├── screens/                     # StudioScreen, LibraryScreen, ProcessingScreen, ResultScreen, ModelHubScreen,
│   │                                # SettingsScreen, QuickUpscaleSheet
│   ├── MediaExportHelper.kt         # MediaStore Pictures/RealScale-Pro/<name>_x2/_x4, format conversion, FileProvider share
│   └── RealScaleViewModel.kt        # SavedStateHandle + StateFlow<RealScaleUiState> ViewModel
└── [Upstream Java Engine]           # ProcessingService (3 notification channels + Cancel/Share/Open actions), ImageProcessor,
                                     # CommandBuilder, CommandListManager, AssetsCopyer, ProgressLogHelper, GifUntils, ShellUtils
```

### Key Technical Notes
- **Android SDK Levels**: `compileSdk = 37`, `minSdk = 24` (Android 7.0+), `targetSdk = 28` (preserved so native CLI binaries extracted into `<cacheDir>/realsr` retain `execve()` execution privileges under Android 10+ SELinux policies).
- **Toolchain**: Gradle `9.8.1`, Android Gradle Plugin `9.4.1`, Kotlin `2.4.21`, KSP `2.3.12`, Compose BOM `2026.09.00`, Glance `1.2.0`, ProfileInstaller `1.4.1`, Room `2.8.5`, WorkManager `2.12.0`, Coil `3.6.3`, `org.tukaani:xz:1.12`.

---

## Building & Testing Locally

Prerequisites: **JDK 21** and Android SDK (`ANDROID_HOME` set).

```bash
cd RealSR-NCNN-Android-GUI

# Run unit + Robolectric tests (CLI parity, queue, pre-flight, progress parser, classifier, Hub query, CropPreview, Hindi strings, ViewModel)
./gradlew testDebugUnitTest

# Assemble slim debug & R8-minified release APKs (with ART Baseline Profile)
./gradlew assembleDebug assembleRelease

# Run Compose instrumentation smoke & visual verification tests on a connected device or emulator
./gradlew connectedDebugAndroidTest
```

---

## Credits & License

RealScale-Pro is released under the **MIT License** (see [`LICENSE`](LICENSE)) and builds directly upon the open-source work of:

- **[tumuyan/RealSR-NCNN-Android](https://github.com/tumuyan/RealSR-NCNN-Android)** & **[tumuyan2/realsr-models](https://huggingface.co/tumuyan2/realsr-models)** — Android CLI integration, `ProcessingService` / `ImageProcessor` runtime, and converted NCNN/MNN model repository by **tumuyan**.
- **[nihui](https://github.com/nihui)** — [`ncnn`](https://github.com/Tencent/ncnn), [`realsr-ncnn-vulkan`](https://github.com/nihui/realsr-ncnn-vulkan), [`waifu2x-ncnn-vulkan`](https://github.com/nihui/waifu2x-ncnn-vulkan), [`srmd-ncnn-vulkan`](https://github.com/nihui/srmd-ncnn-vulkan), and [`realcugan-ncnn-vulkan`](https://github.com/nihui/realcugan-ncnn-vulkan).
- **[xinntao/Real-ESRGAN](https://github.com/xinntao/Real-ESRGAN)** — Practical blind super-resolution models.
- **[TianZerL/Anime4KCPP](https://github.com/TianZerL/Anime4KCPP)** — Fast anime upscaling CLI (`Anime4k`).
- **[Alibaba MNN](https://github.com/alibaba/MNN)** & **[ImageMagick](https://imagemagick.org/)** — Mobile neural network inference (`mnnsr-ncnn`) and image format/GIF processing (`magick`).
