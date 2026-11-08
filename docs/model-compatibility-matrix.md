# Model compatibility matrix

Honest separation of **tested** (real inference executed and verified in
this environment, Prompt 2) vs **investigated** (researched only).

## Tested — REAL inference verified (host Linux, ncnn CPU, x86_64)

Verified by: native suite `test_engine` (full pipeline, tiled-vs-single
seam quality, cancellation, wrong-scale rejection, recovery injection)
+ Dart FFI integration tests + rscli end-to-end on a real photo.
Logs: `docs/inference-test-report.md`.

| Model | Scale | Format | Tiles verified | Seam max dev | Notes |
| --- | --- | --- | --- | --- | --- |
| realesr-animevideov3-x4 | 4× | ncnn | 64/96/32 px | 6/255 (mean 0.209) | also exercised through the full Dart stack (decode→infer→encode) |
| realesr-animevideov3-x3 | 3× | ncnn | 64 px | suite pass | compact |
| realesr-animevideov3-x2 | 2× | ncnn | 64 px | suite pass | compact |
| realesrgan-x4plus | 4× | ncnn | 64/128 px | 7/255 (mean 0.994) | flagship; 12.2 s for 220² → 880² (2 CPU threads) |
| realesrgan-x4plus-anime | 4× | ncnn | 64 px | suite pass | illustration |

## Investigated — NOT integrated

| Model | Status | Blocker / reason |
| --- | --- | --- |
| realesr-general-x4v3 (the x4v3 compact) | **investigated deeply (Prompt 3); conversion deferred with cause** | official release ships only PyTorch weights (`realesr-general-x4v3.pth`, 4.89 MB, SHA-256 `8dc7edb9ac80ccdc30c3a5dca6616509367f05fbc184ad95b731f05bece96292`, downloaded and inspected from the official release). Archive inspection (without torch) shows **quantization-aware keys** (`body.*.weightq` / `biasq` + scales), so a naive torchscript/ONNX export would produce wrong weights — the official dequant preprocessing is required first. The full path (torch CPU ≈ 1 GB + pnnx/ncnnoptimize + validation matrix) exceeded the sandbox disk budget after the Android toolchain consumed the headroom. No third-party converted model was used (provenance policy). |
| realesr-general-wdn-x4v3 | researched | same as above (denoise variant) |
| Real-CUGAN | rejected for now | model weights carry redistribution terms distinct from code license; requires license clearance before any integration |
| CodeFormer | rejected | weights are non-commercial; commercial use unclear → forbidden by project policy until cleared |
| waifu2x (cunet/upconv variants) | researched | per-model redistribution terms vary; ncnn conversions are community-maintained rather than first-party; lower priority than Real-ESRGAN family |
| SwinIR / Swin2SR | researched | transformer weights (60–260 MB) exceed low-resource targets; CPU latency impractical on mid-tier Android |
| RealESRGAN_x2plus | researched | 2× general model exists in PyTorch form; official ncnn pair not present in the release package; revisit with conversion tooling |

## Android on-device status

**Release APK built and programmatically verified** in Prompt 3 (see
BUILD.md / QA-REPORT.md): arm64-v8a engine linked against the official
Vulkan-capable ncnn prebuilt, plus armeabi-v7a / x86_64 engine builds
from the same pinned source. **Not yet run on physical hardware** —
no device exists in this environment; on-device performance, RAM and
thermal behavior remain the top unverified item (KNOWN-LIMITATIONS.md
§1).

### Conversion recipe when a capable machine is available

1. `pip install torch --index-url https://download.pytorch.org/whl/cpu`
2. Load `realesr-general-x4v3.pth`; apply the official dequant
   (`weight = weightq` handling in Real-ESRGAN's `load_net`) to obtain
   float SRVGGNetCompact weights.
3. `torch.jit.trace` the network, then `pnnx` (ncnn's converter) →
   `.param`/`.bin`; optionally `ncnnoptimize` for fp16.
4. Validate parameter names/shapes against the animevideov3 v3-family
   models, then run THIS project's test matrix (10 engine tests + seam
   + PSNR-vs-reference) before registering. Record both SHA-256 values
   in `assets/models/registry.json` with `recorded-from-official-release`
   provenance notes.
