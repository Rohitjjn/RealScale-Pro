# Building RealScale-Pro from Source

This guide covers building the **RealScale-Pro** Android APK (`RealSR-NCNN-Android-GUI`), running unit and emulator tests, signing releases with your own keystore, and compiling the native C++ CLI tools (`RealSR-NCNN-Android-CLI`).

---

## 1. Prerequisites

| Tool | Required Version | Notes |
| --- | --- | --- |
| **JDK** | 21 (Temurin recommended) | Gradle `9.8.1` runs on JDK 21 and compiles Kotlin/Java targeting JVM 17 |
| **Android SDK** | `compileSdk = 37`, Build-Tools 35+ | Set `ANDROID_HOME` and `ANDROID_SDK_ROOT` |
| **CLI utilities** | `curl`, `unzip`, `tar`, `xz`, `python3` | Used by `scripts/prepare-engine-assets.sh` |
| **Android NDK + CMake** *(optional)* | NDK r25+ & CMake 3.22+ | Only required if compiling `RealSR-NCNN-Android-CLI` from C++ source |

---

## 2. Quick Build (Android APK)

```bash
# Clone repository
git clone https://github.com/Rohitjjn/RealScale-Pro.git
cd RealScale-Pro

# Fetch unmodified upstream arm64 CLI binaries, strip model weights, and build binaries.tar.xz
./scripts/prepare-engine-assets.sh

# Build & test inside the Android GUI project
cd RealSR-NCNN-Android-GUI
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

### Build Outputs

- **Debug APK (`com.realscale.pro.debug`):** `RealSR-NCNN-Android-GUI/app/build/outputs/apk/debug/app-debug.apk`
- **Release APK (`com.realscale.pro`, R8-minified & resource-shrunk):** `RealSR-NCNN-Android-GUI/app/build/outputs/apk/release/app-release.apk` (~20.5 MiB)

---

## 3. Running Tests

```bash
cd RealSR-NCNN-Android-GUI

# Unit & Robolectric suite (PipelinePlanner golden vectors, CLI command parity,
# queue ordering, preflight guardrails, progress parser, model status, Room DAOs)
./gradlew testDebugUnitTest

# Instrumentation suite (requires connected Android 7.0+ device or emulator)
./gradlew connectedDebugAndroidTest
```

### CI Verification Gates (`.github/workflows/build-apk.yml`)

Every push and pull request runs:
1. `testDebugUnitTest`, `assembleDebug`, `assembleRelease`, and `assembleDebugAndroidTest`.
2. **APK Size & Zero-Model Gate:** Fails if the release APK exceeds `30 MiB` or contains any `models-*`, `.bin`, `.param`, or `.mnn` files.
3. **Signing Certificate Continuity Gate:** Verifies both `debug` and `release` APKs carry the expected update certificate so sideload installs upgrade cleanly.
4. **Emulator Smoke Suite:** Boots an Android 11 ATD emulator and executes `RealScaleSmokeInstrumentedTest`, `DesignIconInstrumentedTest`, and `ModelDownloadInstrumentedTest`.

---

## 4. Signing with Your Own Keystore (Forks & Store Builds)

By default, Gradle signs builds with `RealSR-NCNN-Android-GUI/keystore/realscale.p12` so GitHub CI releases update smoothly in place. To sign a fork or store release with your own private key (without editing `build.gradle.kts`), export:

```bash
export REALSCALE_STORE_FILE="/secure/path/to/release.p12"
export REALSCALE_STORE_TYPE="PKCS12"          # or JKS
export REALSCALE_STORE_PASSWORD="your-store-password"
export REALSCALE_KEY_ALIAS="your-key-alias"
export REALSCALE_KEY_PASSWORD="your-key-password"

cd RealSR-NCNN-Android-GUI
./gradlew assembleRelease
```

All `*.jks`, `*.keystore`, and custom `*.p12` files are ignored by `.gitignore`.

---

## 5. Compiling Native C++ CLI Tools from Source (Optional)

The native C++ engines (`RealSR`, `Waifu2x`, `SRMD`, `RealCUGAN`, `MNN-SR`, `Anime4k`, `Resize`) live in [`RealSR-NCNN-Android-CLI/`](../RealSR-NCNN-Android-CLI/):

1. Populate third-party native SDKs (`ncnn`, `libwebp`, `OpenCV-android-sdk`, `MNN`) using [`3rdparty/setup.sh`](../3rdparty/setup.sh).
2. Configure Android NDK and CMake in your local environment (`ANDROID_HOME` / `ANDROID_NDK_HOME`).
3. Build the desired CLI modules inside `RealSR-NCNN-Android-CLI/` following [`RealSR-NCNN-Android-CLI/README.md`](../RealSR-NCNN-Android-CLI/README.md).
4. Place the resulting `arm64-v8a` ELF executables and shared libraries into `RealSR-NCNN-Android-GUI/app/src/main/assets/realsr/` and compress them with solid `--arm64 --lzma2=dict=16MiB,preset=9e` into `binaries.tar.xz` (matching `scripts/prepare-engine-assets.sh`).

---

## 6. Regenerating Design-System Tooling (Optional)

Helper Node.js tools live under `tools/` (none of them are required to build the APK):

- `tools/icons/` — Regenerates `LucideIcons.kt` vector glyphs (`npm ci && node gen_lucide_icons.mjs`).
- `tools/plan-vectors/` — Regenerates golden JSON test vectors for `PipelinePlannerGoldenTest`.
- `tools/ui-capture/` — Re-captures the 79 Light/Dark reference PNGs in `docs/ui-reference/screens/`.
- `tools/fonts/` — Subsets and prepares TTF font assets in `res/font/`.
