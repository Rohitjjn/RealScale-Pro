# Licenses

## Application

- **RealScale-Pro application code** — MIT License.
  Copyright 2026 RealScale-Pro contributors.

## Third-party components shipped or downloaded

| Component | License | Copy/notice obligation | How we comply |
| --- | --- | --- | --- |
| ncnn (Tencent) | BSD 3-Clause | retain copyright notice + license text + disclaimer | NOTICE section below; full text at https://github.com/Tencent/ncnn/blob/master/LICENSE.txt |
| Real-ESRGAN models + code (xinntao) | BSD 3-Clause | same | models are user-downloaded, not bundled; license surfaced in-app (About) and in the registry metadata |
| stb (nothings) | Public Domain / MIT | none required; attribution kept | host test harness only; never distributed |
| Flutter / Dart SDK | BSD 3-Clause | notices in binary | Flutter's standard NOTICE mechanism |
| flutter_riverpod, go_router, ffi, archive | MIT | retain notice | standard Flutter license aggregation in Prompt 3 release |
| path_provider, image, http, crypto, shared_preferences, flutter_lints, path_provider_platform_interface | BSD-3 | retain notice | standard aggregation |
| image_picker | Apache-2.0 / BSD-3 (upstream files) | retain notice | standard aggregation |

## Model weights licensing note

Real-ESRGAN weights and code are BSD-3-Clause per the repository
LICENSE. Redistribution is permitted; we additionally (a) do NOT bundle
weights in the APK, (b) download only from the official GitHub release,
(c) SHA-256-verify, (d) show the license in the UI before download.

Explicitly NOT integrated due to licensing: CodeFormer (non-commercial
weights), Real-CUGAN (weights terms differ from code license), waifu2x
community conversions (per-model terms vary). See
`docs/model-compatibility-matrix.md`.

## NOTICE (excerpt)

This product includes software developed by Tencent (ncnn), by xinntao
(Real-ESRGAN), and the Flutter/Dart open-source community, along with
pub.dev packages listed in `docs/dependency-inventory.md`. Third-party
BSD-3/MIT notices are reproduced in the aggregated license view
(Prompt 3 release step).
