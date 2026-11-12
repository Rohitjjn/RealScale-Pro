# RealScale Pro

**Private, offline-first AI photo upscaler for Android — rebuilt in Jetpack Compose with an 18.5 MB APK and an on-demand Model Hub.**

RealScale Pro modernizes [`tumuyan/RealSR-NCNN-Android`](https://github.com/tumuyan/RealSR-NCNN-Android) (`v1.13.2`) into a calm, warm editorial Android app anyone can use in three taps — while keeping every native NCNN / MNN / Vulkan / OpenCL upscaling engine 100% intact under the hood.

---

## Highlights

- **96% smaller install (`~475 MB` → `18.54 MiB` release APK)**
  Zero AI model weights are bundled inside the APK. All 19 unmodified native CLI executables and shared libraries (`realsr-ncnn`, `realcugan-ncnn`, `waifu2x-ncnn`, `srmd-ncnn`, `mnnsr-ncnn`, `resize-ncnn`, `Anime4k`, `magick`, plus NCNN/MNN/OpenMP `.so` runtimes) are solid-packed into `binaries.tar.xz` (`16.63 MiB`) and unpacked once on first launch.
- **3-Tap Photo Enhancement**
  1. **Choose a photo** (single, multi-photo batch up to 20 images, or `Share` directly from any Gallery app).
  2. **Pick a style** — **Instant** (`avir-lancir` sinc Lanczos, works immediately offline with zero downloads), **Photo** (`RealESRGAN General v3`, `2.3 MB`), **Anime & Art** (`Waifu2x UpConv-7`, `5.3 MB`), or **Maximum** (`DF2K`, `32.0 MB`).
  3. **Tap Upscale** — runs inside a foreground `ProcessingService` with live percentage, ETA, and safe background execution.
- **Interactive Before / After Split Comparison**
  Synchronized pinch-to-zoom (`1×`–`8×`), pan, and draggable vertical split handle on the Result screen, with one-tap **Save** (`Pictures/RealScale-Pro` or custom SAF folder) and **Share**.
- **Resumable On-Demand Model Hub**
  14 curated real models hosted on [`huggingface.co/tumuyan2/realsr-models`](https://huggingface.co/tumuyan2/realsr-models), downloaded via `WorkManager` with HTTP `Range` resume support (`206 Partial Content`), atomic `.part` staging, and strict **SHA-256 verification** before activation. Once downloaded, models run 100% offline.
- **Warm Editorial Design System**
  Built in Jetpack Compose + Material 3 following Claude's warm editorial palette (`#FAF9F5` parchment canvas, `#EFE9DE` surface cards, `#141413` ink, `#CC785C` terracotta primary CTAs, `#181715` warm obsidian dark mode, weight-400 serif display headers, humanist sans body, and `1px` hairline borders without drop-shadow clutter).
- **Full Power-User Compatibility**
  GPU (Vulkan / OpenCL) vs CPU toggle, tile size presets (`Auto`, `64`, `128`, `256`, `512`), lossless PNG / WebP / JPG output, SAF folder batch upscaling, thread count (`l:p:s`), and raw CLI command execution with live monospace console output.

---

## APK Size Comparison

| Build | Upstream `1.13.2` | RealScale Pro `2.0.0` |
| --- | --- | --- |
| **Release APK (`arm64-v8a`)** | `~475 MB` | **`18.54 MiB`** (`19,436,217` bytes) |
| **Debug APK** | `~480 MB` | **`29.87 MiB`** (`31,325,047` bytes) |
| **Bundled AI Model Weights** | `~440 MB` (`models-*`) | **`0 bytes`** (downloaded on demand) |
| **Bundled Native CLI Engines** | All 8 CLI tools | **All 8 CLI tools** (100% unmodified) |

---

## Architecture

RealScale Pro lives in `RealSR-NCNN-Android-GUI/` (`RealSR-NCNN-Android-CLI/` remains untouched upstream C++/JNI reference source):

```text
com.tumuyan.ncnn.realsr
├── MainActivity.kt                  # Single-activity Compose host + ACTION_SEND / SEND_MULTIPLE intake
├── core/
│   ├── database/                    # Room 2.8 (RecentCreationEntity, DownloadedModelEntity, DAOs)
│   ├── engine/                      # StateFlow UpscaleEngine facade over ProcessingService + binaries.tar.xz extractor
│   └── model/                       # Bundled 14-model manifest, HTTP Range ResumableModelInstaller, WorkManager worker
├── ui/
│   ├── theme/                       # Warm editorial colors, serif/sans/mono type scale, 1px hairline shapes, vector icons
│   ├── components/                  # EditorialCard, TerracottaPrimaryButton, synchronized zoom/pan BeforeAfterSplitView
│   ├── screens/                     # StudioScreen, ProcessingScreen, ResultScreen, ModelHubScreen, SettingsScreen
│   ├── MediaExportHelper.kt         # MediaStore Pictures/RealScale-Pro, SAF DocumentFile export, FileProvider share
│   └── RealScaleViewModel.kt        # Unidirectional StateFlow<RealScaleUiState> ViewModel
└── [Upstream Java Engine]           # Unmodified ProcessingService, ImageProcessor, CommandBuilder, CommandListManager,
                                     # AssetsCopyer, ProgressLogHelper, GifUntils, PreprocessToPng, UriUntils, ShellUtils
```

### Key Technical Notes
- **Android SDK Levels**: `compileSdk = 37`, `minSdk = 24` (Android 7.0+), `targetSdk = 28` (intentionally preserved so native CLI binaries extracted into `<cacheDir>/realsr` retain `execve()` execution privileges under Android 10+ SELinux policies).
- **Toolchain**: Gradle `9.8.1`, Android Gradle Plugin `9.4.1`, Kotlin `2.4.21`, KSP `2.3.12`, Compose BOM `2026.09.00`, Room `2.8.5`, WorkManager `2.12.0`, Coil `3.6.3`, `org.tukaani:xz:1.12`.

---

## Building & Testing Locally

Prerequisites: **JDK 21** and Android SDK (`ANDROID_HOME` set).

```bash
cd RealSR-NCNN-Android-GUI

# Run unit + Robolectric tests (command parity, Room DAOs, SHA-256 resumable installer, ViewModel)
./gradlew testDebugUnitTest

# Assemble slim debug & R8-minified release APKs
./gradlew assembleDebug assembleRelease

# Run Compose instrumentation smoke tests on a connected device or emulator
./gradlew connectedDebugAndroidTest
```

> **CI Note**: `.github/workflows/build-apk.yml` automatically fetches upstream `1.13.2` native binaries, strips all `models-*` directories, packs the 19 native binaries/libraries into `assets/realsr/binaries.tar.xz`, enforces `release APK <= 30 MB` and zero bundled `.bin`/`.param`/`.mnn` model files, runs unit tests, and executes `RealScaleSmokeInstrumentedTest` on an Android emulator. Pushing a `v*` tag automatically publishes a GitHub Release with `RealScale-Pro-<tag>-arm64.apk` and its `.sha256` checksum.

---

## Credits & License

RealScale Pro is released under the **MIT License** (see [`LICENSE`](LICENSE)) and builds directly upon the open-source work of:

- **[tumuyan/RealSR-NCNN-Android](https://github.com/tumuyan/RealSR-NCNN-Android)** & **[tumuyan2/realsr-models](https://huggingface.co/tumuyan2/realsr-models)** — Android CLI integration, `ProcessingService` / `ImageProcessor` runtime, and converted NCNN/MNN model repository by **tumuyan**.
- **[nihui](https://github.com/nihui)** — [`ncnn`](https://github.com/Tencent/ncnn), [`realsr-ncnn-vulkan`](https://github.com/nihui/realsr-ncnn-vulkan), [`waifu2x-ncnn-vulkan`](https://github.com/nihui/waifu2x-ncnn-vulkan), [`srmd-ncnn-vulkan`](https://github.com/nihui/srmd-ncnn-vulkan), and [`realcugan-ncnn-vulkan`](https://github.com/nihui/realcugan-ncnn-vulkan).
- **[xinntao/Real-ESRGAN](https://github.com/xinntao/Real-ESRGAN)** — Practical blind super-resolution models.
- **[TianZerL/Anime4KCPP](https://github.com/TianZerL/Anime4KCPP)** — Fast anime upscaling CLI (`Anime4k`).
- **[Alibaba MNN](https://github.com/alibaba/MNN)** & **[ImageMagick](https://imagemagick.org/)** — Mobile neural network inference (`mnnsr-ncnn`) and image format/GIF processing (`magick`).
