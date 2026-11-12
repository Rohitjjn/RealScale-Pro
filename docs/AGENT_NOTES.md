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

