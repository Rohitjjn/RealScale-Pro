# RealScale-Pro — Reused Components and Licenses

Verified 3 November 2026. Versions are exact pins. Only permissive licenses are
allowed. Repository dates are the latest upstream activity observed during the
Phase 0 review. Model files are data, never downloaded executable code.

## Native engine and bundled model data

| Component | Exact version / commit | License | Upstream | Used for | Verified status |
| --- | --- | --- | --- | --- | --- |
| ncnn | `6125c9f47cd14b589de0521350668cf9d3d37e3c` | BSD-3-Clause (with permissive third-party notices) | https://github.com/Tencent/ncnn | CPU/Vulkan neural-network runtime | Source license read; active 2026-11-03 |
| Real-ESRGAN ncnn Vulkan | `37026f49824c5cf84062e7c6a5dd71445dcf610f` | MIT | https://github.com/xinntao/Real-ESRGAN-ncnn-vulkan | Primary upstream integration reference | License read; last code update 2024-05-10 |
| Real-CUGAN ncnn Vulkan | upstream snapshot reviewed 2026-11-03 | MIT | https://github.com/nihui/realcugan-ncnn-vulkan | Bundled compact anime fallback model | License read; last code update 2023-03-12 |
| waifu2x ncnn Vulkan | upstream snapshot reviewed 2026-11-03 | MIT | https://github.com/nihui/waifu2x-ncnn-vulkan | Bundled compact photo fallback model and CPU spike | License read; active 2026-04-13 |
| `scale2.0x_model` weights | from `models-upconv_7_photo` in waifu2x ncnn Vulkan | MIT repository distribution | https://github.com/nihui/waifu2x-ncnn-vulkan | Offline photo fallback | SHA-256 recorded in `assets/data/starter_manifest.json` |
| `up2x-no-denoise` weights | from `models-nose` in Real-CUGAN ncnn Vulkan | MIT repository distribution | https://github.com/nihui/realcugan-ncnn-vulkan | Offline anime fallback | SHA-256 recorded in `assets/data/starter_manifest.json` |

The two bundled fallbacks are 3.7 MB together. Official Real-ESRGAN compact files
remain the preferred on-demand models. They are not bundled until their exact
weight terms and a reliable GitHub Release asset can be verified on Android and
Windows.

## Flutter packages selected for Phase 1

| Package | Pinned version | License | Upstream | Where used / reason |
| --- | --- | --- | --- | --- |
| flutter_riverpod | 3.4.3 | MIT | https://github.com/rrousselGit/riverpod | Typed app state; required by rules.md |
| go_router | 18.1.2 | BSD-3-Clause | https://github.com/flutter/packages/tree/main/packages/go_router | Navigation; required by rules.md |
| photo_manager | 3.12.0 | Apache-2.0 | https://github.com/fluttercandies/flutter_photo_manager | Recents and limited photo access |
| file_picker | 13.3.2 | MIT | https://github.com/vicajilau/flutter_file_picker | Android and desktop file picker |
| image_picker | 1.2.4 | BSD-3-Clause | https://github.com/flutter/packages/tree/main/packages/image_picker | Camera and system gallery fallback |
| permission_handler | 13.0.2 | MIT | https://github.com/Baseflow/flutter-permission-handler | Permission requests after primers |
| background_downloader | 9.6.5 | BSD-3-Clause | https://github.com/781flyingdutchman/background_downloader | Resumable model downloads |
| share_plus | 13.3.1 | BSD-3-Clause | https://github.com/fluttercommunity/plus_plugins | System share sheet |
| device_info_plus | 13.3.0 | BSD-3-Clause | https://github.com/fluttercommunity/plus_plugins | OS/device profile |
| package_info_plus | 10.2.2 | BSD-3-Clause | https://github.com/fluttercommunity/plus_plugins | About screen version |
| shared_preferences | 2.5.6 | BSD-3-Clause | https://github.com/flutter/packages/tree/main/packages/shared_preferences | Onboarding and settings persistence |
| path_provider | 2.1.6 | BSD-3-Clause | https://github.com/flutter/packages/tree/main/packages/path_provider | Models, temporary copies and fallback output folders |
| image | 4.13.0 | MIT | https://github.com/brendan-duncan/image | Decode, orientation, compression and preview helpers |
| flutter_native_splash | 2.4.8 | MIT | https://github.com/jonbhanson/flutter_native_splash | Theme-matched native splash generation |

All listed repositories had activity within 18 months on the verification date.
Flutter and Dart are distributed under BSD-3-Clause. Material widgets,
`InteractiveViewer`, `PageView`, localization, animation and license display use
Flutter itself rather than adding packages.

## Candidates reviewed but not copied

| Candidate | License | Last activity | Decision |
| --- | --- | --- | --- |
| RealSR-NCNN-Android | MIT | active 2026-11-03 | Useful Android build reference, but not copied; direct upstream keeps desktop/mobile parity and a smaller audit surface |
| Upscayl | AGPL-3.0 | not applicable | Blocked; reference-only under project rules |
| chaiNNer | GPL-3.0 | not applicable | Blocked; reference-only under project rules |
| CodeFormer | non-commercial model terms | not applicable | Blocked unless a commercial license is obtained |

## Required notices in the app

The About → Open-source licenses screen uses Flutter's license registry and a
project credits list. Binary distributions must retain the ncnn, model and
package notices above. This register is a compliance record, not legal advice.
