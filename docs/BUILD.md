# BUILD.md

## Environment facts (verified 2026-11-06 in this sandbox)

- Debian 13, 2 × x86_64, 3.9 GB RAM, no swap, no sudo, ~10 GB rootfs.
- Flutter 3.47.9 stable / Dart 3.13.7 at `/home/z/my-project/tools/flutter`.
- JDK 17.0.21 (Temurin) at `/home/z/my-project/tools/jdk17` (the system
  Java is a 21 JRE without javac).
- Android SDK at `/home/z/my-project/tools/android-sdk`:
  `platforms;android-36`, `build-tools;36.0.0`, `cmake;3.22.1`,
  `ndk;28.2.13676358` (the versions Flutter 3.47.9 pins).
- CMake 4.4.4 + Ninja via `/home/z/.venv/bin` (host builds).

## Host verification build (what was actually run)

```bash
export PATH="/home/z/my-project/tools/flutter/bin:/home/z/.venv/bin:$PATH"
cd /home/z/my-project/realscale-pro

# 1. ncnn (once; pinned commit 09981a0…; see tools/fetch_ncnn.sh)
cmake -G Ninja -DCMAKE_BUILD_TYPE=Release -DNCNN_VULKAN=OFF \
      -DNCNN_BUILD_TOOLS=OFF -DNCNN_BUILD_EXAMPLES=OFF -DNCNN_BUILD_BENCHMARK=OFF \
      -DNCNN_PYTHON_TOOLS=OFF -DNCNN_SHARED_LIB=OFF \
      -S ../third_party/ncnn -B ../third_party/ncnn-build
cmake --build ../third_party/ncnn-build -j2          # ~9 min on 2 cores

# 2. Native engine: production .so + tests + CLI (persistent location
#    outside build/ so gradle cannot reap it between runs)
cmake -G Ninja -DCMAKE_BUILD_TYPE=Release -DRS_BUILD_TESTS=ON -DRS_BUILD_CLI=ON \
      -DRS_NCNN_LIB=../third_party/ncnn-build/src/libncnn.a \
      -DRS_NCNN_INCLUDE="../third_party/ncnn/src;../third_party/ncnn-build/src" \
      -S native -B ../third_party/rs-host-build && cmake --build ../third_party/rs-host-build -j2

# 3. Production .so for flutter test (same place the FFI tests look)
tools/build_native.sh                                  # -> build/native/

# 4. Native tests (per model; x3/x2 variants use their scale)
RS_TEST_MODEL_PARAM=../models/realesr-animevideov3-x4.param \
RS_TEST_MODEL_BIN=../models/realesr-animevideov3-x4.bin RS_TEST_MODEL_SCALE=4 \
../third_party/rs-host-build/test_engine

# 5. Flutter
flutter analyze                                        # No issues found
flutter test                                           # 59/59 PASS
flutter build web --release                            # ✓ (41 MB)
```

## Android build — EXECUTED AND VERIFIED HERE

Contrary to the Prompt 2 blocker (disk), Prompt 3 re-measured, freed
~1.9 GB of regenerable caches (playwright/puppeteer/gradle dist docs /
non-arm64 Flutter engine artifacts) and completed the build:

```bash
export JAVA_HOME=/home/z/my-project/tools/jdk17
export ANDROID_HOME=/home/z/my-project/tools/android-sdk
export PATH="/home/z/my-project/tools/flutter/bin:/home/z/.venv/bin:$PATH"

tools/fetch_ncnn_android.sh    # official ncnn 20250503 android-vulkan prebuilt
                               # (arm64-v8a; NCNN_ANDROID_ARCHS adds more ABIs)

flutter build apk --release --target-platform android-arm64
# ✓ Built build/app/outputs/flutter-apk/app-release.apk (52.6MB)
```

Result: `RealScale-Pro-v1.0.0-arm64-release.apk`
(SHA-256 `891227a67cd166552f9005fdb3ae9e25f6821a67bec11f1bddfb6fc1351aed9d`),
debug-signed for testability, verified via `aapt2 dump badging`,
`unzip -l`, `llvm-nm` and `apksigner verify` (see QA-REPORT.md).

What is inside: `lib/arm64-v8a/librealscale_native.so` linked against
the official Vulkan-capable ncnn prebuilt (+ glslang/SPIRV family,
static OpenMP), plus `armeabi-v7a` and `x86_64` engine builds compiled
from the same pinned ncnn source by the CMake fallback when no prebuilt
exists for those ABIs. Dart AOT (`libapp.so`) ships arm64-only.

### Toolchain arithmetic for small disks (measured)

| Component | Peak disk used |
| --- | --- |
| JDK 17 (Temurin tarball) | 0.31 GB (zip deleted) |
| cmdline-tools + platform 36 + build-tools 36 + cmake 3.22.1 | ~0.4 GB |
| NDK 28.2.13676358 | 2.2 GB |
| ncnn-android prebuilt (arm64) | 0.02 GB |
| Gradle dist + AGP/dependency caches | ~1.9 GB |
| Build intermediates + APK | ~0.6 GB |

Practical levers used here when space ran out (all regenerable):
browser/pip caches, gradle wrapper zip, `docs/`+`src/` inside the
extracted Gradle `-all` distribution, non-target-ABI Flutter engine
artifacts (`flutter precache` restores them), the web SDK cache, and the
host ncnn build dir during the Android build.

### Rate-limited networks (workarounds actually used)

`repo.maven.apache.org` intermittently returned HTTP 429 under Gradle's
parallel requests. Workarounds (local-only, none shipped in the repo):
a `~/.gradle/init.gradle.kts` putting the aliyun mirror first for plugin
resolution, and temporarily mirroring `mavenCentral()` in the root
`build.gradle.kts` (reverted after the build). The Flutter engine maven
repository (`storage.googleapis.com/download.flutter.io`) resolved the
`io.flutter:*` artifacts directly. On a normal network none of this is
needed.

## Web build (functional, verified)

```bash
flutter build web --release     # ✓ 41 MB
```

Compiles and runs; the native engine is honestly reported unavailable
(conditional export → stub) — no fake inference on web. QA on this build
caught a real regression in Prompt 3 (web stub missing the new
`compute` parameter), which was fixed and re-verified.

## Build artifacts (do not delete blindly)

- `build/native/librealscale_native.so` — used by `flutter test`.
- `../third_party/rs-host-build/*` — native test binaries + rscli
  (rebuilt by the command in §2).
- `../third_party/ncnn-build/src/libncnn.a` — pinned ncnn host build.
- `../third_party/ncnn-android/<abi>/` — prebuilt Android ncnn.
- `android/app/.cxx/` — gradle's native build state (excluded from the
  source ZIP).
