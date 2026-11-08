# Dependency inventory

Every runtime and build dependency with verified source, license and
purpose. Policy: official sources only; nothing executes third-party
scripts; no dependency where the standard library suffices.

## Native (C/C++)

| Dependency | Version / pin | Source | License | Purpose |
| --- | --- | --- | --- | --- |
| ncnn | commit `09981a0796cc0dd629ed5ba0f8d55653cc1406a5` (2026-11-05, post-1.0) | https://github.com/Tencent/ncnn (shallow clone, `tools/fetch_ncnn.sh`) | BSD 3-Clause | inference backend (static link, CPU build here) |
| stb_image / stb_image_write | v2.30 / v2.10 | https://github.com/nothings/stb (`tools` fetched `master` headers) | Public domain / MIT dual | **host test harness & rscli only** — never in the app / Android build |

OpenMP: linked on the host build only when present (ncnn compiled with
it); Android ncnn prebuilts use pthreads.

## Flutter (pub) — all from pub.dev, resolved 2026-11-06

| Package | Resolved | License | Purpose |
| --- | --- | --- | --- |
| flutter_riverpod | 2.6.1 | MIT | state management |
| go_router | 14.8.1 | BSD-3 | navigation |
| path_provider | 2.1.5 | BSD-3 | documents/support dirs |
| path_provider_platform_interface | 2.1.2 | BSD-3 | (dev) test fakes |
| image | 4.12.0 | BSD-3 | PNG/JPEG encode + test-time decode |
| http | 1.4.0 | BSD-3 | model download (streamed) |
| crypto | 3.0.6 | BSD-3 | SHA-256 verification |
| shared_preferences | 2.5.3 | BSD-3 | settings |
| image_picker | 1.1.2 | Apache-2.0 / BSD-3 | gallery/camera import |
| archive | 4.0.7 | MIT | model zip extraction |
| ffi | 2.1.0 | MIT | calloc/Utf8 extensions for dart:ffi |
| flutter_lints | 6.0.0 | BSD-3 | (dev) lint set |

Deliberate exclusions (YAGNI / cost):
- `permission_handler` — the system photo picker needs no runtime
  permission on Android 13+ and the camera flow is surfaced by
  image_picker's own permission request; re-add only if a real gap
  appears on target devices.
- `dio`, `share_plus`, `device_info_plus` — not needed for the current
  scope (http suffices; share planned for Prompt 3 with a fresh cost
  check; device info uses a 60-line MethodChannel).

## Toolchain (workspace, not shipped)

| Tool | Version | Source |
| --- | --- | --- |
| Flutter SDK | 3.47.9 stable (Dart 3.13.7) | storage.googleapis.com official tarball |
| CMake / Ninja | 4.4.4 / 1.13 | pip (PyPI) into the user venv |
| g++ | Debian 13 system | system package |
| Model weights | Real-ESRGAN v0.2.5.0 release | github.com/xinntao/Real-ESRGAN (see models.md) |

## Prompt 3 additions

No new pub.dev or runtime dependencies were added (YAGNI held: gallery
save, share and StatFs are ~150 lines of Kotlin in the existing host
activity rather than three plugin packages). New project-local tooling
(revision-controlled, no runtime role):

| Tool | Purpose |
| --- | --- |
| tools/generate_icon.py | launcher/splash asset generation (PIL, deterministic geometry — no third-party art, nothing to license) |
| tools/benchmark_host.sh | host inference benchmark matrix |
| tools/bench_encode.dart | Dart encode-stage benchmark |

Build-time external components (documented in BUILD.md, not bundled in
the source ZIP): Temurin JDK 17, Android SDK 36 / build-tools 36.0.0 /
cmake 3.22.1 / NDK 28.2.13676358, Gradle 9.3.1 (via wrapper), ncnn
android prebuilt 20250503 (vulkan), aliyun maven mirror (local-only
workaround, never committed).
