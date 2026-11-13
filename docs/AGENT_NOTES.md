# RealScale-Pro Agent Notes

Running architectural notes, verified upstream facts, and decision log.

---

## 1. Upstream Baseline (`tumuyan/RealSR-NCNN-Android` tag `1.13.2`)

- **Upstream tag**: `1.13.2` (published `2026-05-30`, commit `0eb1676`).
- **Working branch note**: Arena binds this session to branch `arena/a8f7a90d-realscale-pro` (branched from `main` `2f21a0cd`), and prohibits creating, renaming, or pushing to other branches. We imported `1.13.2` cleanly onto `arena/a8f7a90d-realscale-pro` (`git diff 1.13.2` = empty on baseline commit `17d1c92`), leaving `main` untouched (no force-push).
- **Ponytail discipline**: Found `DietrichGebert/ponytail` on GitHub (`AGENTS.md` read and adopted): smallest complete change, zero speculative abstractions, reuse upstream engine classes directly, ship every non-trivial module with tests, and state verified vs unverified explicitly.
- **Design source**: Official Claude `DESIGN.md` fetched from `VoltAgent/awesome-design-md` (`design-md/claude/DESIGN.md`, 589 lines) and cross-checked with `VoltAgent/awesome-claude-design`.

---

## 2. SDK & Build Configuration in Upstream `1.13.2`

- **Project root**: `RealSR-NCNN-Android-GUI` (`:app` module). `RealSR-NCNN-Android-CLI` holds C++/JNI CLI sources and is **strictly off-limits** (never modified).
- **SDK versions** (`RealSR-NCNN-Android-GUI/app/build.gradle`):
  - `minSdk = 24` (Android 7.0+; runs on Android 8+ as required).
  - `targetSdk = 28` (**DO NOT INCREASE**: Android 10+ / API 29+ enforces W^X SELinux restrictions preventing `execve()` on binaries extracted to `getCacheDir()`/`getFilesDir()`; keeping `targetSdk = 28` preserves Android's legacy execution exemption for `cache/realsr/*`).
  - `compileSdk = 36`, Java 17, Gradle `8.13`, AGP `8.13.1`, `versionCode = 52`, `versionName = "1.13.2"`.
- **ABIs shipped**:
  - Upstream ships **arm64-v8a (`armv8a`)** prebuilt ELF executables and `.so` libraries inside `app/src/main/assets/realsr/` (not `jniLibs/`). Output APK naming in upstream is `RealSR-NCNN-Android-GUI-armv8a-${versionName}.apk`.
  - Manifest declares `<uses-native-library android:name="libOpenCL.so" android:required="true"/>` and `<uses-native-library android:name="libGLES_mali.so" android:required="true"/>` (note: check emulator compatibility in Step 7 if emulator lacks vendor OpenCL/Mali libs — inspect PackageManager behavior on emulator!).

---

## 3. Binary Extraction & Execution Pipeline

1. **Asset location in APK**: `RealSR-NCNN-Android-GUI/app/src/main/assets/realsr/`
   - Executables: `realsr-ncnn`, `realcugan-ncnn`, `waifu2x-ncnn`, `srmd-ncnn`, `resize-ncnn`, `mnnsr-ncnn`, `Anime4k`, `magick`
   - Shared libraries & configs: `libncnn.so`, `libomp.so`, `libc++_shared.so` (plus any MNN/OpenCV `.so` shipped in release assets), `colors.xml`, `delegates.xml`, `img/`
   - Bundled model directories in upstream (removed in Step 4): `models-*`
2. **Extraction (`AssetsCopyer.java`)**:
   - `AssetsCopyer.releaseAssets(context, "realsr", cacheDir, skipExistFile)` recursively extracts `assets/realsr` into `workingDir = context.getCacheDir().getAbsolutePath() + "/realsr"`.
   - `skipExistFile` is `true` when `SharedPreferences("config").getInt("version", 0) == BuildConfig.VERSION_CODE`, and `false` on first install / version upgrade / "Reset Cache".
   - On version upgrade or cache reset, `MainActivity` also runs `CMD_CP_LIB_OPENCL` to copy `/system/vendor/lib64/libOpenCL.so` and `libGLES_mali.so` (if present on device) into `workingDir`, followed by `chmod +x *`.
3. **Execution (`ImageProcessor.java`)**:
   - Single-thread `ExecutorService` spawns `new ProcessBuilder("sh")` with `.directory(new File(workingDir))` and `.redirectErrorStream(true)`.
   - Writes setup commands to shell stdin:
     ```sh
     cd <workingDir>; chmod +x *ncnn 2>/dev/null; export LD_LIBRARY_PATH=<workingDir>;
     <command>
     exit
     ```
   - Reads combined stdout/stderr line-by-line, filtering noisy driver lines (`unused DT entry`, `CPU Group:`, `(last_midr`, `Error tunning info`), forwarding each line to `ProcessCallback.onProgress(line)`, and waiting for `process.waitFor()` (`exitCode == 0` -> `success = true`).
   - Cancellation (`cancelCurrentTask()`): sets `taskCancelled = true`, cancels the `Future<?>`, and calls `currentProcess.destroy()`.

---

## 4. Roles of Core Upstream Classes

- **`CommandBuilder`**: Minimal fluent builder (`append(part)`, `append(key, value)`, `appendIf(...)`, `build()`, `buildArray()`) joining non-empty parts with spaces; used in `MainActivity.run20` to join the primary upscaling command with optional post-export shell commands (`;cp ...` or `;./magick ...`).
- **`CommandListManager`**: Central catalog of CLI commands and labels:
  - Holds `COMMAND_0` (38 preset commands across `realsr-ncnn`, `mnnsr-ncnn`, `realcugan-ncnn`, `Anime4k`).
  - Generates `resize-ncnn` (`Classical-<filter>-x<2|4|10>`) and `magick` (`Magick-<filter>-x<2|4|10>`) commands.
  - Scans `extraPath` for external model directories (`models-*`, `models-MNN/*.mnn`, `*.mnn`) and appends `extraCommand` lines.
  - Filters commands by `hiddenPrograms` (`Set<String>` of program types) and `supportsDirectoryMode(cmd)` (all tools except `magick`).
  - Supports custom display label overrides keyed by `commandFingerprint(cmd)` (command string without `-i <in> -o <out>`).
- **`ImageProcessor`**: Background shell process runner with live line callbacks and cancellation (`ProcessCallback`: `onProgress`, `onCompleted`, `onError`).
- **`ProcessingService`**: Bound + foreground `Service` wrapping `ImageProcessor`:
  - Starts foreground notification (`CHANNEL_ID_PROGRESS` or `CHANNEL_ID_ALIVE`) via `startForeground(1, notification)`.
  - Throttles progress notifications to 500 ms (`NOTIFICATION_UPDATE_INTERVAL_MS`) using `ProgressLogHelper.isProgressLine(line)` (`\s*\d([0-9.]*)%(\s.+)?`).
  - Handles `ACTION_STOP_TASK` intent and `cancelTask()` to cancel the running `ImageProcessor` task.
- **`ProgressLogHelper`**: Parses percentage progress lines (`isProgressLine`, `getProgressText`) vs persistent log lines, tracks elapsed wall time, and formats completion summaries.
- **`ShellUtils`**: Single-quote POSIX shell argument escaper (`escapeShellArgument(arg)` wraps in `'...'` and escapes internal `'` as `'\''`) added in `1.13.2` (`0eb1676`) to prevent path injection.

---

## 5. How Models & Parameters Are Passed to Each Tool

All commands execute inside `workingDir` (`<cacheDir>/realsr`) with `input.png` (file or directory) and `output.png` (file or escaped target directory):

| Tool | Executable | Model Flag & Format | Key Parameters |
|---|---|---|---|
| RealSR / Real-ESRGAN | `./realsr-ncnn` | `-m <modelDir>` (`x2.bin`/`x2.param`, `x3.*`, `x4.*`, etc.) | `-i <in> -o <out> -s <scale> [-t <tile>] [-j <l:p:s>] [-g -1] [-f <fmt>]` |
| Real-CUGAN | `./realcugan-ncnn` | `-m <modelDir>` (`models-se`, `models-pro`, `models-nose`) | `-i <in> -o <out> -s <scale> -n <-1|0|1|2|3> [-t <tile>] [-j <l:p:s>] [-g -1] [-f <fmt>]` |
| Waifu2x | `./waifu2x-ncnn` | `-m <modelDir>` (`models-cunet`, `models-upconv_7_*`) | `-i <in> -o <out> -s <scale> [-n <0|1|2|3>] [-t <tile>] [-j <l:p:s>] [-g -1] [-f <fmt>]` |
| SRMD | `./srmd-ncnn` | `-m <modelDir>` (`models-srmd`) | `-i <in> -o <out> -s <2|3|4> [-n <-1..10>] [-t <tile>] [-j <l:p:s>] [-f <fmt>]` (GPU only; upstream skips `-g -1` for `./srmd`) |
| MNN-SR | `./mnnsr-ncnn` | `-m <file.mnn>` | `-i <in> -o <out> -s <scale> [-b <backend>] [-d <decensor>] [-t <tile>] [-j <l:p:s>] [-f <fmt>]` (uses `-b 0` for CPU or `-b 3` OpenCL / `-b 7` Vulkan, not `-g -1`) |
| Classical Resize | `./resize-ncnn` | `-m <mode>` (`avir-lancir`, `avir`, `bicubic`, `bilinear`, `nearest`, `de-nearest`, `de-nearest2`, `perfectpixel`) — **no model files needed!** | `-i <in> -o <out> -m <mode> -s <scale>` |
| Anime4K | `./Anime4k` | Built-in CNN/shader kernels — **no external model folder needed!** | `-i <in> -o <out> -z <scale> -A [-w] [-H] [-a -e <n>] [-b -r <n>] [-E .<ext>]` |
| ImageMagick | `./magick` | Built-in filters (`Lanczos`, `Mitchell`, etc.) | `./magick input.png -filter <f> -resize <pct>% output.png` (single-file only; also used for GIF coalesce/reassembly and WebP/JPEG/HEIC format conversion) |

**Critical compatibility detail for Step 4**:
- Storing downloaded models under `new File(context.getFilesDir(), "models/<modelFolderName>")` (e.g., `<filesDir>/models/models-DF2K`, `<filesDir>/models/models-MNN/<file>.mnn`) allows passing `-m <absolutePath>` directly to each CLI binary AND allows `CommandListManager` to auto-discover downloaded models when given `<filesDir>/models` as an extra model directory.

---

## 6. Complete Upstream Feature Inventory (Must Stay Working)

1. **Single-image upscaling**: Gallery picker -> preprocess non-PNG/JPG/WebP/BMP (HEIF/AVIF via `BitmapFactory`, others via `magick`) to `cache/realsr/input.png` -> run tool -> preview `output.png` -> Save (PNG/WebP/GIF/HEIC/JPEG quality) & MediaScanner index -> Share via `FileProvider`.
2. **Multi-image batch processing**: Picker (`EXTRA_ALLOW_MULTIPLE`) or `ACTION_SEND_MULTIPLE` -> writes multiple images into directory `cache/realsr/input.png/*.png` -> swaps `output.png` for escaped `savePath` directory + optional `-f <dirFormat>` / `-E .<dirFormat>` -> MediaScanner scans outputs.
3. **Folder batch processing (`DirectoryProcessActivity`)**: SAF `ACTION_OPEN_DOCUMENT_TREE` input & output folders, auto-named output folder modes (`name3`), filters to `supportsDirectoryMode` commands, runs via `ProcessingService` with live log & Stop.
4. **Animated GIF support (`GifUntils` + `magick`)**: Detects multi-frame GIF, reads average frame delay via `./magick identify -format "%T "`, splits frames via `./magick tmp -coalesce -delay 0 input.png/%04d.png`, upscales frame directory into `output.png/`, reassembles via `./magick -delay <delay> output.png/* -loop 0 '<outputSavePath>.gif'`.
5. **Share-intent intake**: `android.intent.action.SEND` (`image/*`) and `android.intent.action.SEND_MULTIPLE` (`image/*`).
6. **GPU / CPU toggle & MNN backend**: `useCPU` appends `-g -1` for `realsr-ncnn`, `waifu2x-ncnn`, `realcugan-ncnn` (and `-b 0` vs `mnnBackend` for `mnnsr-ncnn`).
7. **Tile size & thread count**: `tileSize` (`0` = auto, or `>= 32`) appends `-t <tileSize>`; `threadCount` (`l:p:s`) appends `-j <threadCount>`.
8. **Power-user / Advanced features**:
   - Raw shell / command console (`help`, `in`, `out`, `show <file>`, arbitrary `./realsr-ncnn ...` commands).
   - Custom extra commands (`extraCommand` multiline list) and custom external model directory (`extraPath`).
   - Custom command label editor (`customLabels` JSON / TSV copy-paste).
   - Hiding specific programs (`hiddenPrograms` set for `realsr`, `srmd`, `waifu2x`, `realcugan`, `mnnsr`, `resize`, `magick`, `anime4k` on incompatible GPUs).
      - Cache reset (`CMD_RESET_CACHE` + re-extract assets) and Benchmark mode.

---

## 7. Step 1 & Step 2 Decisions & Verified Evidence

- **Step 1 Baseline CI**:
  - Unmodified upstream `1.13.2` built green in GitHub Actions (`https://github.com/Rohitjjn/RealScale-Pro/actions/runs/43220076302`).
  - Golden command parity fixtures (`golden_commands.txt` + `GoldenEngineCommandTest.java`) covering all 8 tools (`realsr-ncnn`, `realcugan-ncnn`, `waifu2x-ncnn`, `srmd-ncnn`, `mnnsr-ncnn`, `resize-ncnn`, `Anime4k`, `magick`), external model folders, runtime parameter flags, and `ShellUtils` escaping built & passed green (`https://github.com/Rohitjjn/RealScale-Pro/actions/runs/43221221177`).
  - Verified SHA-256 checksums and exact byte sizes computed by downloading 14 real model packs from `huggingface.co/tumuyan2/realsr-models` in CI (note: `github.com/tumuyan/realsr-models` stores `.bin` weights via Git LFS, so `raw.githubusercontent.com` returns 133-byte LFS pointers; `huggingface.co/tumuyan2/realsr-models/resolve/main/...` serves real binary weights with HTTP `Range` support).
- **Step 2 Modern Foundation**:
  - Migrated to Gradle `9.8.1` + Kotlin DSL + `gradle/libs.versions.toml` with verified current stable releases (zero alpha/beta/RC):
    - AGP `9.4.1` (built-in Kotlin), Kotlin `2.4.21`, KSP `2.3.12` (skipping `2.3.13` due to upstream issue #3286), `compileSdk = 37`, `minSdk = 24`, `targetSdk = 28` (preserved).
    - Compose BOM `2026.09.00` (Compose UI `1.12.1`, Material 3 `1.4.0` stable), Navigation Compose `2.10.2`, Room `2.8.5`, WorkManager `2.12.0`, Coil `3.6.3`, Kotlin Coroutines `1.11.0`, Kotlinx Serialization JSON `1.11.0`.
  - Removed `android.nonFinalResIds=false` (incompatible with AGP 9.x; verified upstream Java uses `if/else` rather than `switch` on `R.id.*`).
  - Step 2 verified green in GitHub Actions (`https://github.com/Rohitjjn/RealScale-Pro/actions/runs/43222437622`).

---

## 8. Steps 3–7 Architecture & Verified Measurements

- **Step 3 — Clean Kotlin Engine Facade & Room Persistence (`c1a1bb3`, green run `43226789180`)**:
  - Added `UpscaleEngine` / `ProcessingServiceEngine` exposing reactive `StateFlow<EngineState>`, `StateFlow<String>` console log, and `StateFlow<EnginePreferences>` while delegating shell execution and foreground notifications to unmodified `ProcessingService` + `ImageProcessor`.
  - Added `PresetCommandResolver` mapping friendly presets (`Instant`, `Photo`, `Anime & Art`, `Maximum`) and custom model specs to exact CLI invocations (`srmd-ncnn` never receives `-g -1`; `mnnsr-ncnn` uses `-b 0` for CPU / `-b 3` for OpenCL).
  - Added Room 2.8 `RealScaleDatabase` (`RecentCreationDao`, `DownloadedModelDao`) verified under Robolectric.
- **Step 4 — Slim APK (`binaries.tar.xz`) & On-Demand Resumable Model Hub (`a8f8c83`, green run `43236059282`)**:
  - Removed 100% of bundled `models-*` directories from `assets/realsr/`.
  - Solid-compressed all 19 unmodified arm64 CLI binaries and `.so` libraries into `assets/realsr/binaries.tar.xz` (`16.63 MiB`, unpacked once on first launch via `org.tukaani:xz:1.12` in `EngineAssetExtractor`).
  - Bundled `assets/model_manifest.json` with 14 real models (61 files) from `huggingface.co/tumuyan2/realsr-models` with verified byte counts and 64-char SHA-256 digests, paired with `ResumableModelInstaller` (HTTP `Range` `206` resume + `200` fallback + `.part` staging + SHA-256 check) and `ModelDownloadWorker` (`WorkManager`).
- **Step 5 — Claude Warm Editorial Compose UI & Legacy XML Removal (`d9afa76` run `43241730199` & `1635ff8` run `43244140294`)**:
  - Built 5 Compose screens (`StudioScreen`, `ProcessingScreen`, `ResultScreen` with synchronized zoom/pan `BeforeAfterSplitView`, `ModelHubScreen`, `SettingsScreen`) in Step 5A without touching engine classes.
  - Removed obsolete XML activities (`SettingActivity`, `DirectoryProcessActivity`, `LabelEditorActivity`), XML layouts, and `appcompat`/`material`/`subsampling-scale-image-view` dependencies in Step 5B (`1635ff8`), bringing final APK sizes to:
    - **`release` APK**: **`18.54 MiB` (`19,436,217 bytes`)**
    - **`debug` APK**: **`29.87 MiB` (`31,325,047 bytes`)**
    - **Bundled AI model files**: **`0`** (`unzip -l` verified in CI).
- **Step 6 & 7 — Emulator Smoke Suite, Release Automation & Docs**:
  - Added `RealScaleSmokeInstrumentedTest` (`app/src/androidTest/`) verifying `binaries.tar.xz` extraction of all 8 CLI tools (`realsr-ncnn`, `srmd-ncnn`, `realcugan-ncnn`, `waifu2x-ncnn`, `mnnsr-ncnn`, `resize-ncnn`, `magick`, `Anime4k`), Studio -> Model Hub -> Settings -> Dark/Light theme navigation, `singleTop` `ACTION_SEND` photo intake, on-demand model prompt, and Instant upscale / Result Before-After rendering on Android 11 ATD emulator in GitHub Actions (upgrading `cmdline-tools;latest` to `>=22.0` to avoid `actions/runner-images#14484` `target=android-0` bug).
- **Step 3 Decoupled Engine & Room Persistence**:
  - Added `core/database/` (`RealScaleDatabase`, `RecentCreationEntity`, `RecentCreationDao`, `DownloadedModelEntity`, `DownloadedModelDao`) with KSP + Room `2.8.5`.
  - Added `core/engine/` (`UpscaleEngine`, `ProcessingServiceEngine`, `PresetCommandResolver`, `EngineAssetExtractor`, `UpscalePreset`, `EngineModels`) wrapping upstream `CommandBuilder`, `CommandListManager`, `ImageProcessor`, `ProcessingService`, `ProgressLogHelper`, and `AssetsCopyer` behind pure Kotlin `StateFlow`s (zero UI callbacks, zero EventBus).
  - Measured upstream `1.13.2` non-model ELF binaries: `105.70 MB` raw / `36.96 MB` individually deflated (because OpenCV is statically linked into all 7 CLI binaries). Packing unmodified ELF binaries into `assets/realsr/binaries.tar.xz` (`--arm64 --lzma2=dict=16MiB`, unpacked transparently on first launch by `EngineAssetExtractor` after `AssetsCopyer.releaseAssets`) deduplicates OpenCV across binaries with 100% byte-identical SHA-256s after extraction (`15.63 MiB`, CI run `43226789180`).
- **Step 4 Slim APK & Model Downloader**:
  - Removed all `models-*` folders from `assets/realsr/`; bundled `assets/model_manifest.json` (version `1`) with 14 real models (61 files) from `huggingface.co/tumuyan2/realsr-models` (`mirrorInfo`: `github.com/tumuyan/realsr-models`) and verified 64-hex SHA-256 checksums + exact byte sizes.
  - Built `core/model/` (`ModelManifest`, `ResumableModelInstaller`, `ModelDownloadWorker`, `ModelRepository`) supporting HTTP `Range` (`206 Partial Content`) pause/resume/cancel, live progress & rolling byte/s speed, pre-download free-space check (`16 MiB` safety margin), pre-install SHA-256 verification, exponential backoff retries on transient HTTP/IO failures, atomic install to `context.filesDir/models/<id>/`, and optional HTTPS manifest refresh.

---

## 9. v2.1 Audit Findings (`notification_logic.md`, Engine Progress, UI & System Integration)

- **Branch note**: Arena binds this session to `arena/15dbc0d2-realscale-pro` (branched from `main` `908e09e6`). Per Arena platform constraints, all commits, pushes, and the final PR to `main` stay on `arena/15dbc0d2-realscale-pro` (replacing `feature/realscale-v2` without ever force-pushing `main`).
- **Upstream workflows cleanup**: Removing `libs.yml`, `libmnn-linux.yml`, `mnnsr-linux.yml`, `huggingface.yml`, and `android.yml` so `.github/workflows/build-apk.yml` is the sole active CI workflow on this repo.
- **Findings from `RealSR-NCNN-Android-GUI/docs/notification_logic.md` & `ProcessingService.java`**:
  - `notification_logic.md` specifies 3 channels (`channel_progress` LOW silent ongoing, `channel_service_alive` LOW silent ongoing, and `channel_result` HIGH with alert/auto-cancel), yet `ProcessingService.java` only created `channel_progress` and `channel_service_alive`, used `@mipmap/ic_launcher` instead of a monochrome white-on-transparent `smallIcon`, lacked a `Cancel` action button and `setProgress(...)` bar, and never posted a separate `BigPictureStyle` completion notification with `Share` / `Open` deep-link actions.
- **Findings on Progress Parsing (`ProgressLogHelper.java`, `ProcessingServiceEngine.kt`, C++ CLI sources)**:
  - `realsr-ncnn`, `waifu2x-ncnn`, and `mnnsr-ncnn` emit `%5.2f%%\t[%5.2fs /%5.2f ETA]\n` (or `\r`); `realcugan-ncnn` emits `%.2f%%\n`; `Anime4k` emits `%7.2f%%     elpsed: %8.2fs    remaining: %8.2fs\r`.
  - `resize-ncnn` in `avir-lancir` mode (`Instant` preset) emits **no** percentage lines during processing (`%.2f%%` only exists in `perfectpixel`/`de-nearest` loops).
  - In v2.0.0, `ProcessingServiceEngine` initialized `progressFraction = 0f` even when no determinate progress had arrived (`0% · Estimating time…` frozen on `Instant` or during shader compile) and `humanizeStatusLine` fell back to `trimmed.take(80)`, leaking raw C++ stderr onto `ProcessingScreen`.
  - Fix in P0-C: model explicit human stages (`Preparing`, `Enhancing`, `Saving`), keep `progressFraction: Float? = null` (indeterminate bar + live elapsed seconds) until determinate tile percentages arrive (rolling-average ETA), and confine raw logs strictly to `Settings > Advanced > Console` + `"Copy details"` on error.
- **Findings from `RealScaleViewModel.kt`, `RealScaleApp.kt`, & `PresetCommandResolver.kt`**:
  - `RealScaleViewModel` combines `selectionFlow`, `engine.state`/`preferences`/`consoleLog`, and Room `recentCreationDao` + `modelRepository.catalogModels`, but currently cancels `activeJob` instead of queueing additional jobs sequentially (`1` active GPU/Vulkan job + visible queue `"2 of 5"` with per-item cancel), lacks pre-flight megapixel/storage/dimension guardrails, lacks SavedStateHandle/disk persistence for in-flight/result state across process death, and does not persist last-used preset or `When opened from other apps` (`Quick sheet` vs `Full app`).
  - `PresetCommandResolver` deterministically builds `./resize-ncnn`, `./Anime4k`, `./mnnsr-ncnn`, `./realsr-ncnn`, `./realcugan-ncnn`, `./waifu2x-ncnn`, and `./srmd-ncnn` commands (correctly omitting `-g -1` for GPU-only `srmd-ncnn` and mapping `mnnsr-ncnn` to `-b 0` CPU / `-b 3` OpenCL).
- **Master Brand Asset (`/tmp/arena-coding-uploads/seedream-5.0-pro_a_Hey_ek_logo_banao_ha.jpeg`)**:
  - `2048×2048` sRGB master artwork: `#BD4707` warm burnt-terracotta field with centered circular eagle emblem (`bbox = 304..1746 × 297..1752`, `center = 1025, 1024.5`) in pure `#FFFFFF` white + `#090907` ink-black.
  - Adaptive foreground fits the untouched emblem inside the `66dp` safe zone (`60dp` diameter on `108dp` canvas) over `@color/ic_launcher_background` (`#BD4707`), paired with a crisp `<monochrome>` cutout layer for Android 13+ themed icons, legacy `48dp` square/round mipmaps for API 24–25, a `24dp` white-on-transparent `ic_stat_realscale` notification silhouette, and a circular medallion asset so both light (`#FAF9F5`) and dark (`#181715`) surfaces preserve full contrast on the black ring and wordmark.
- **Gated Execution Order**:
  - `P0-A` (Brand, adaptive/monochrome/legacy/notification icons, AndroidX SplashScreen `1.2.0`, `<600ms` reduced-motion-aware greeting fade, logo in About & empty states, `"RealScale-Pro"` label everywhere, remove upstream workflows) -> CI green ->
  - `P0-B` (`ACTION_VIEW` Open-with + translucent `QuickUpscaleActivity` bottom sheet + sequential engine queue + foreground ongoing progress/Cancel notification + `BigPictureStyle` completion notification with Share/Open deep link + Android 13+ `POST_NOTIFICATIONS` explanation + MediaStore `Pictures/RealScale-Pro/<name>_x2/_x4` + format conversion) -> CI green ->
  - `P0-C` (Human stages `Preparing`/`Enhancing`/`Saving`, determinate rolling-average ETA vs indeterminate elapsed timer, pre-flight megapixel/dimension/free-space checks with one-tap remedies, one-tap error recovery, SavedState/disk state restoration, OOM-safe `BitmapRegionDecoder` + downsampled `BeforeAfterSplitView`) -> CI green ->
  - `P0-D` (Studio single drop-zone card, cheap image heuristic `"Suggested for this photo"`, sticky bottom CTA with live resolution/time/size summary, `2×`/`4×` scale selector, Recent Creations shared-element transition + long-press menu + designed empty state, stacked batch strip `"Batch of N"`, haptics & calm spring motion) -> CI green ->
  - `P0-E` (Model Hub storage summary, `"Recommended for you"` 2 starter models, Installed vs Available sections, search + sort + counted filter chips, compact expandable cards with speed/best-for badges, global download banner, Wi-Fi-only toggle, full download state machine) -> CI green ->
  - `P1` (`LibraryScreen` grid + search/sort/multi-select Share/Delete/Compare + 3-tab bottom bar `Studio / Library / Models` + top-right Settings; Result edge-to-edge canvas fit, dual-contrast handle, tap-and-hold original, double-tap 100% zoom, details row, Save snackbar `"Open in gallery"`, Delete; grouped Settings with default preset, keep screen on, haptics, Wi-Fi-only, cache size/clear, Quick sheet behaviour, About; Jetpack Glance `1.2.0` Small `"Enhance a photo"` + Medium `"Recent creations"` widgets) -> CI green ->
  - `P2` (Fast `"Preview"` crop comparison sheet, `values-hi` Hindi + English strings with Devanagari-safe typography, `WindowSizeClass` two-pane tablet/foldable layout, `profileinstaller` `1.4.1` + `baseline-prof.txt`) -> CI green (`run 43383884613`, `release APK = 20.21 MiB`, `0` bundled AI models) ->
  - `Final Rename + Visual Screenshot Verification + Emulator ACTION_VIEW Smoke + PR` (`applicationId = "com.realscale.pro"`, `versionName = "2.1.0"`, `versionCode = 54`, polished `brand_logo_medallion.png` & `ic_stat_realscale.png` verified visually, Android 11 emulator pixel + screenshot capture uploaded to `realscale-visual-screenshots`, `README.md` + `CHANGELOG.md` updated, PR opened to `main`).







---

## 10. Design-v3 Rebuild (`arena/d17dcc03-realscale-pro`, branched from `3adb071`)

- **Repo visibility** (`gh repo view --json isPrivate`, `2026-11-13`): **PUBLIC** (`isPrivate=false`). CI is free and unlimited; `.github/workflows/build-apk.yml` remains the sole workflow (upstream `libs.yml`, `libmnn-linux.yml`, `mnnsr-linux.yml`, `huggingface.yml`, `android.yml` already removed). Ubuntu-24.04 runners only, `timeout-minutes` set, Gradle caching via `setup-gradle`, concurrency cancel-in-progress to be added, APK size gate 30 MiB kept.
- **Branch policy**: this Arena session is pinned to `arena/d17dcc03-realscale-pro`; all commits/pushes stay on it (never a new `feature/design-v3` branch, never force-push `main`). Final PR targets `main` from this branch.
- **Sandbox egress**: `dl.google.com`, `maven.google.com`, `repo.maven.apache.org`, `services.gradle.org`, `plugins.gradle.org` all unreachable (`curl` -> `000`); `registry.npmjs.org` and `pypi.org`/`files.pythonhosted.org` reachable (used to fetch OFL fonts + lucide-static icon geometry and Pillow). **No local Android SDK/JDK build possible** -> CI is the authority; every iteration pushes to the branch and reads real logs via `gh`.
- **Sources of truth**: `docs/ui-reference/` (unpacked `claude-style-tsx-ui-design (1).zip`, **git-ignored**, never seen by Gradle), master logo `RealSR-NCNN-Android-GUI/app/src/main/res/drawable-nodpi/brand_logo_medallion.png` (never redrawn).
- **Mock vs real**: design's `SAMPLE_PHOTOS` / fake device numbers / simulated jobs are NOT ported. Replaced by MediaStore photos, real `ActivityManager`/`StatFs`/Vulkan queries, real engine jobs via `ProcessingServiceEngine`, real model manifest (`assets/model_manifest.json`, 14 real ncnn/MNN models from `huggingface.co/tumuyan2/realsr-models`). Planner ETA comes from a rolling per-model average of real finished jobs (+ optional one-time benchmark), never from constants.
- **Stubbed design assets**: the zip ships only `assets/raw/{anime,vintage}.jpg`; `src/assets/photos/*.jpg` are missing (prototype images). For any future prototype render they must be generated as placeholders (see entry 11).
- **Licenses screen** must name only real dependencies: upstream tumuyan/RealSR-NCNN-Android (MIT), ncnn (BSD-3-Clause), MNN (Apache-2.0), Real-ESRGAN (BSD-3-Clause), Real-CUGAN (MIT), waifu2x (MIT), RealSR (PolyForm? -> verified below), Anime4KCPP, ImageMagick (Apache-2.0), resize-ncnn/Anime4k binaries, Lucide (ISC), Cormorant Garamond / Inter / JetBrains Mono / Noto Sans Devanagari (OFL). Flutter / ONNX Runtime / GFPGAN / SwinIR / HAT / DAT are **not** used and were removed from the list.
- **`applicationId` rename to `com.realscale.pro`**: already applied in v2.1.0 base (`3adb071`); re-verified by grep at the end (no stray `com.tumuyan.ncnn.realsr` in applicationId positions; namespace stays `com.tumuyan.ncnn.realsr` intentionally, upstream package kept).

## 11. Design-v3 Rebuild — M1/M2 Progress (`2026-11-13`)

- **Old UI deleted**: all `ui/screens/*.kt`, `ui/components/*.kt`, `RealScaleViewModel.kt`, `RealScaleApp.kt`, `ui/theme/*.kt` git-rm'd. `OomSafeImageDecoders` moved `ui/components` → `core/engine` (imports updated). UI-coupled tests deleted. Working tree currently = new theme + new components; nothing released yet.
- **New design system** (`ui/theme/`): `Color.kt` (Light/Dark tokens 1:1 + `LocalRealScaleColors`, `isDark` flag), `Type.kt` (Cormorant Medium/SemiBold display scale, Inter body, JetBrains Mono, Devanagari fallbacks, `.tnum()`), `Shape.kt` (radius 4/6/8/12/16/20dp + `softShadow`), `Motion.kt` (`SpringEasing = cubic-bezier(0.22,1,0.36,1)`, `MotionDuration` keyframe constants, `rememberReduceMotion()`, `motionDuration()`), `Theme.kt` (`RealScaleTheme(themeMode, dynamicColor)` + `AppTheme` enum; dynamic-colour path follows Material You surfaces while keeping text/hairline tokens).
- **New components** (`ui/components/`): `Mark.kt` (brand_logo_medallion only; never redraw), `Buttons.kt` (Primary/Secondary/Tonal/Ghost/Dark/Danger + IconButton), `Sheets.kt` (bottom sheet: scrim, drag-handle w/ 90dp dismiss + follow-finger drag, Back handler, spring-in 360/220 out, ConfirmSheet), `Controls.kt` (Switch 52×32, Segmented w/ sliding canvas pill, Chip, Range (pointer-input, custom track/thumb), Stepper, Field), `Display.kt` (Badge/Dot/RadioMark/Group/Row/SwitchRow/ProgressBar/Ring), `Scaffold.kt` (TopBar w/ status-bar inset + collapse-border hook, BottomBar, `rememberCollapseBorder`), `Toast.kt` (bottom-28 inverse pill), `BeforeAfter.kt` (sweep-in reveal, drag wipe, "hold to see original" via `showOriginal`, corner pills). Lucide = 52 `ic_lucide_*.xml` vector drawables (`lucide-static` 1.72.0; stroke geometry preserved; tint at call site).
- **Chart/graph-free**: no chart libraries added; all "graphs" in design are bars/rings.
- **Planet-scale lesson**: all Android/Maven hosts blocked locally ⇒ CI is the only compile check. Keep pushes frequent; `gh run list/view` after each milestone.
- **Fonts** installed (OFL): Cormorant Garamond Medium+SemiBold, Inter 400/500/600/700, JetBrains Mono 400/500/600, Noto Sans Devanagari 400/500.
- **Planner ported** (`core/engine/planner/PipelinePlanner.kt`): pure Kotlin, no Android deps; design's `plan.ts` rules 1:1 where natively supported — levels default/manual/advanced, tile presets Safe/Balanced/Fast(recommended)/Maximum(disabled msg verbatim)/custom + tile factor table, MP caps (4 MP input upscale-optimize; 64 MP upscale / 128 MP super red checks with "too large for your device." message + maxScale suggestion), WebP 16,383 px amber + "Too large for WebP.", ETA>600s amber TIME check, green otherwise, 5× = native 4× + 1.25 post-resize, 3× = SRMD native when installed/selected else largest-below native + resample (2× → 1.5×), tile grid from native (post-resize) output. Formatting helpers (`fmtEta/fmtLeft/fmtClock/fmtMP/fmtMB/resultFileName`) ported with unit coverage.
- **Dropped as not real** (no native engine support, = dead controls otherwise): `overlap` (ncnn has no flag), `tta` (no flag), `precision fp32` (no flag), `compression`/`compressionQ` (local runs are pixel-bound, CLI has no re-encode step), `formatQ` (CLI has no quality knob), `cap` (redundant with red checks), `sharpen`, `color`, `faces`/`faceQuality` (no on-device face pipeline; Face pack dropped), `resize lanczos/bicubic` selector (post-step uses platform high-quality filter; documented). Documented in `docs/FIDELITY.md`.
- **Pack mapping** (real catalog, no Face/Ultra/ONNX/Community): Starter `{upconv_7_photo (2×/4×), upconv_7_anime_style_art_rgb (2×/4×), RealeSR-general-v3 (4×)}` ≈ 14 MB, installed during setup; Pro = other 11 models (~300 MB), optional; recommendations: UPSCALE photo → upconv_7_photo, UPSCALE anime → upconv_7_anime; SUPER photo → DF2K, SUPER anime → MNN-RealESRGAN_x4plus_anime_6B (falls back to starter with an honest "install the Super model" CTA).
- **ETA never constants**: `core/engine/EtaTracker.kt` keeps rolling last-8 (outputMP, seconds) per modelId in DataStore (`eta_stats_v1`); secPerMP (Σsec/ΣoutMP) is injected into the planner; plans show "estimating…" until ≥2 samples. `ProcessingServiceEngine` records one sample per real finished job (after `RecentCreationDao.insert`).
- **Engine untouched behaviour (wrap only)**: added three additive observers — (1) optional input cap `UpscaleRequest.maxInputMP` → `InputOptimizer.capIfNeeded()` downscale before the CLI runs (Upscale Mode only; Super never caps), (2) post-resize `PostResizeProcessor.resizeInPlace()` after CLI success only when `request.scale != modelSpec.scale`, memory-guarded at 300 MB (else honest failure "Try 4× instead"), (3) `KeepMetadata.copyToOutput()` EXIF copy (new `androidx.exifinterface`) before the output is copied to the gallery, best-effort. New pref `EnginePreferences.keepMetadata=true` + persisted key. ETA record is wrapped in `runCatching` and never fails a job.
- **Unit tests added**: `PipelinePlannerTest` (23 tests: model resolution, pack wanted/fallback, input cap & super never-cap, red/amber/green checks, 5×/3× post-resize plan, tile grid on native scale, ETA null-until-samples + CPU factor, formatting helpers, determinism, mapper). `DeviceProfile`/`EtaTracker` not unit-tested (Android/DataStore dependent — CI emulator smoke covers presence only).
- **ETA & post-step truthfulness**: post-resize uses platform `createScaledBitmap(filter=true)` (bilinear-class), NOT lanczos and no sharpening pass — FAQ and About text were adjusted (see FIDELITY.md); 5× FAQ claim "light sharpening" removed.
