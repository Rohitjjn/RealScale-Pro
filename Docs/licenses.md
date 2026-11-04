# RealScale-Pro — Reused Components and Licenses

Verified 3 November 2026. Versions are exact pins. Only permissive licenses are
allowed. Repository dates are the latest upstream activity observed during the
Phase 0 review. Model files are data, never downloaded executable code.

## Native engine and bundled model data

| Component | Exact version / commit | License | Upstream | Used for | Verified status |
| --- | --- | --- | --- | --- | --- |
| ncnn | `6125c9f47cd14b589de0521350668cf9d3d37e3c` | BSD-3-Clause (with permissive third-party notices) | https://github.com/Tencent/ncnn | CPU/Vulkan neural-network runtime | Source license read; active 2026-11-03 |
| Real-ESRGAN ncnn Vulkan | `37026f49824c5cf84062e7c6a5dd71445dcf610f` | MIT | https://github.com/xinntao/Real-ESRGAN-ncnn-vulkan | Primary upstream integration reference | License read; last code update 2024-05-10 |
| Real-CUGAN ncnn Vulkan | upstream snapshot reviewed 2026-11-03 | MIT | https://github.com/nihui/realcugan-ncnn-vulkan | Reviewed candidate; reserved for later model support | License read; last code update 2023-03-12 |
| waifu2x ncnn Vulkan | upstream snapshot reviewed 2026-11-03 | MIT | https://github.com/nihui/waifu2x-ncnn-vulkan | Bundled compact photo fallback model and CPU spike | License read; active 2026-04-13 |
| `scale2.0x_model` weights | from `models-upconv_7_photo` in waifu2x ncnn Vulkan | MIT repository distribution | https://github.com/nihui/waifu2x-ncnn-vulkan | Offline photo fallback | SHA-256 recorded in `assets/data/starter_manifest.json` |
| `anime_scale2.0x_model` weights | from `models-upconv_7_anime_style_art_rgb` in waifu2x ncnn Vulkan | MIT repository distribution | https://github.com/nihui/waifu2x-ncnn-vulkan | Offline anime fallback | SHA-256 recorded in `assets/data/starter_manifest.json` |
| NextE model audit and mirror | active main snapshot reviewed 2026-11-03 | MIT application; upstream model notices retained | https://github.com/erosTeam/NextE and https://github.com/erosTeam/NextE-Models | Exact Real-ESRGAN x2plus and anime-video x2 download files | x2plus source pair totals 67,106,328 bytes; anime pair totals 1,250,541 bytes; every SHA-256 is pinned in `model_catalog.json` |

The two bundled fallbacks are 2.2 MB together. Official Real-ESRGAN compact files
remain the preferred on-demand models. They are not bundled until their exact
weight terms and a reliable GitHub Release asset can be verified on Android and
Windows.

## Phase 2 model audit — current shipping gates

Audit started 3 November 2026. A catalog entry may be shown for transparency,
but it is enabled for download only after its exact files, checksums, weight terms
and native compatibility are verified. “Blocked” means it must not ship.

| Candidate | Source state and license | Maintenance / delivery | Phase 2 decision |
| --- | --- | --- | --- |
| waifu2x upconv7 photo + anime | MIT repository distribution; exact bundled checksums already recorded | Upstream active 2026-04-13; files already pass the shared ncnn adapter | **Enabled** as the installed Starter fallbacks |
| Real-ESRGAN x2plus and anime-video x2 | Original project is BSD-3-Clause; official ncnn anime pair is MIT-distributed | Exact source files are mirrored by the active MIT NextE project in `erosTeam/NextE-Models`; x2plus weights were audited against the official checkpoint and anime files against the official ncnn release | **Approved Starter downloads** with exact sizes/checksums; native integration tests still gate making them the runtime default |
| Remaining Real-ESRGAN ncnn models | Port repository is MIT; its release advertises x4plus, x4plus-anime and realesrnet models | Latest release 0.2.0 (2022-04-24), last push 2024-05-10 | **Held** until exact release files, checksums and adapter tests pass |
| Real-CUGAN ncnn models | MIT; model files are distributed in the source repository | Latest release 20220728; last push 2023-03-12; the Phase 1 graph was incompatible with the current thin adapter | **Owner-approved for strict audit** on 3 November 2026; held until a compatible audited adapter passes |
| RealSR ncnn DF2K models | MIT; two 33,424,520-byte model binaries plus parameter files are distributed in the source repository | Latest release 20220728; last push 2023-03-12 | **Owner-approved for strict audit** on 3 November 2026; held until checksums and adapter tests pass |
| Official GFPGAN v1.4 | Project states Apache-2.0 except bundled third-party components; its license includes an NVIDIA non-commercial use limitation | Last push 2024-07-26; official ncnn delivery is absent | **Restricted exception approved by owner** on 3 November 2026 because face enhancement is required; use official files only and keep the non-commercial warning visible |
| Qengineering GFPGAN ncnn port | Port code is BSD-3-Clause | Last push 2023-01-04; required weights come from an external Google Drive archive with no license or checksums in the repository | **Blocked** because the model-weight license cannot be verified |
| Other GFPGAN ncnn forks | One active candidate is GPL-3.0; two candidates have no repository license | Activity varies | **Blocked** by GPL or missing license |
| CodeFormer | Non-commercial model terms | Not applicable | **Blocked** unless the owner deliberately changes the commercial-license rule |

The face requirement was escalated under `rules.md` §10 because no maintained
ncnn face-restoration candidate has a complete permissive source-and-weights
chain. After being shown the restriction, the owner required GFPGAN and approved
using the official source. This is a documented exception, not a finding that the
model is commercially unrestricted. Unlicensed third-party mirrors and GPL forks
remain blocked; official files still require checksums and runtime tests before
they can be enabled.

## Flutter packages selected for Phase 1

| Package | Pinned version | License | Upstream | Where used / reason |
| --- | --- | --- | --- | --- |
| flutter_riverpod | 3.4.3 | MIT | https://github.com/rrousselGit/riverpod | Typed app state; required by rules.md |
| go_router | 18.1.2 | BSD-3-Clause | https://github.com/flutter/packages/tree/main/packages/go_router | Navigation; required by rules.md |
| photo_manager | 3.12.0 | Apache-2.0 | https://github.com/fluttercandies/flutter_photo_manager | Recents and limited photo access |
| file_picker | 13.3.2 | MIT | https://github.com/vicajilau/flutter_file_picker | Android and desktop file picker |
| ffi | 2.2.0 | BSD-3-Clause | https://github.com/dart-lang/native/tree/main/pkgs/ffi | Safe Dart bindings to the thin ncnn C interface |
| image_picker | 1.2.4 | BSD-3-Clause | https://github.com/flutter/packages/tree/main/packages/image_picker | Camera and system gallery fallback |
| permission_handler | 13.0.2 | MIT | https://github.com/Baseflow/flutter-permission-handler | Permission requests after primers |
| background_downloader | 9.6.5 | BSD-3-Clause | https://github.com/781flyingdutchman/background_downloader | Resumable model downloads |
| crypto | 3.0.8 | BSD-3-Clause | https://github.com/dart-lang/core/tree/main/pkgs/crypto | SHA-256 model verification |
| share_plus | 13.3.1 | BSD-3-Clause | https://github.com/fluttercommunity/plus_plugins | System share sheet |
| device_info_plus | 13.3.0 | BSD-3-Clause | https://github.com/fluttercommunity/plus_plugins | OS/device profile |
| package_info_plus | 10.2.2 | BSD-3-Clause | https://github.com/fluttercommunity/plus_plugins | About screen version |
| shared_preferences | 2.5.6 | BSD-3-Clause | https://github.com/flutter/packages/tree/main/packages/shared_preferences | Onboarding and settings persistence |
| path_provider | 2.1.6 | BSD-3-Clause | https://github.com/flutter/packages/tree/main/packages/path_provider | Models, temporary copies and fallback output folders |
| image | 4.5.4 | MIT | https://github.com/brendan-duncan/image | Decode, orientation, compression and preview helpers |
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
