# AI Models & Custom Conversion Guide

RealScale-Pro ships **zero bundled model weights** inside its 20.5 MB APK. Instead, models download on demand into app-private storage (`filesDir/models/<manifestId>/`), verified against exact file byte counts and 64-character SHA-256 digests pinned in [`app/src/main/assets/model_manifest.json`](../RealSR-NCNN-Android-GUI/app/src/main/assets/model_manifest.json).

- **Primary HTTPS mirror:** <https://huggingface.co/tumuyan2/realsr-models> (supports HTTP `206 Partial Content` resume)
- **Git LFS source mirror:** <https://github.com/tumuyan/realsr-models>
- **Manifest integrity check:** `.github/workflows/model-facts.yml` validates all 61 files across all 14 catalog models.

---

## 1. Complete 14-Model Catalog

### Starter Models (Installed During Setup — 7.9 MB Total)

| Model | Manifest ID | Engine | Size | Native Scale | Min Tier | License | Best For |
| --- | --- | --- | ---: | :---: | --- | --- | --- |
| **Real-ESRGAN General v3** | `models-RealeSR-general-v3` | `realsr-ncnn` | 2.4 MB | 4× | Light | BSD-3-Clause | Everyday phone photos, portraits, fast general 2×–4× upscaling |
| **Waifu2x Anime** | `models-upconv_7_anime_style_art_rgb` | `waifu2x-ncnn` | 5.5 MB | 2× + denoise | Light | MIT | Anime, manga, comics, digital illustrations, icons |

### Photo & Restoration Models

| Model | Manifest ID | Engine | Size | Native Scale | Min Tier | License | Best For |
| --- | --- | --- | ---: | :---: | --- | --- | --- |
| **Waifu2x Photo** | `models-upconv_7_photo` | `waifu2x-ncnn` | 5.5 MB | 2× + denoise | Light | MIT | Faithful 2× enlargement keeping natural film grain and skin tones |
| **SRMD** | `models-srmd` | `srmd-ncnn` *(GPU)* | 18.4 MB | 2×, 3×, 4× + denoise | Light | MIT | Photos and flatbed scans needing native 3× scaling or adjustable denoise |
| **RealSR DF2K** | `models-DF2K` | `realsr-ncnn` | 33.5 MB | 4× | Light | MIT | Classic RealSR photo texture synthesis on clean inputs |
| **RealSR DF2K JPEG** | `models-DF2K_JPEG` | `realsr-ncnn` | 33.5 MB | 4× | Light | MIT | Old camera photos and messaging-app images with JPEG blocking |
| **Remacri x4** | `models-ESRGAN-Remacri` | `realsr-ncnn` | 33.6 MB | 4× | Standard | CC-BY-NC-SA-4.0 | Landscapes, architecture, foliage, skin, and fabric in Super Resolution Mode |
| **UltraMix Balanced x4** | `models-ESRGAN-UltraMix_Balanced` | `realsr-ncnn` | 33.6 MB | 4× | Standard | CC-BY-NC-SA-4.0 | Balanced detail and natural color fidelity without oversharpening |
| **UltraSharp x4 (fp16)** | `models-ESRGAN-UltraSharp-fp16` | `realsr-ncnn` | 33.6 MB | 4× | Standard | CC-BY-NC-SA-4.0 | Crisp edges on screenshots, product photos, signs, and mixed media |
| **Nomos8kSC x4** | `models-ESRGAN-Nomos8kSC` | `realsr-ncnn` | 33.5 MB | 4× | Powerful | CC-BY-NC-SA-4.0 | High-density photographic detail on flagship phones (12 GB+ RAM) |

### Anime, Manga & Illustration Models

| Model | Manifest ID | Engine | Size | Native Scale | Min Tier | License | Best For |
| --- | --- | --- | ---: | :---: | --- | --- | --- |
| **Waifu2x CUnet** | `models-cunet` | `waifu2x-ncnn` | 13.9 MB | 2× + denoise | Light | MIT | Stronger artifact and noise cleanup on older anime frames at 2× |
| **AnimeSharp Lite x4** | `models-ESRGAN-AnimeSharpLite` | `realsr-ncnn` | 20.1 MB | 4× | Light | CC-BY-NC-SA-4.0 | Fast 4× anime and line-art sharpening on mid-range hardware |
| **AnimeSharp x4** | `models-ESRGAN-AnimeSharp` | `realsr-ncnn` | 33.5 MB | 4× | Standard | CC-BY-NC-SA-4.0 | Flagship 4× anime, cel art, visual novels, and wallpaper upscaling |
| **RealESRGAN Anime 6B (MNN)** | `models-MNN-RealESRGAN_x4plus_anime_6B` | `mnnsr-ncnn` | 17.9 MB | 4× | Light | BSD-3-Clause | 6-RRDB-block anime network executed via Alibaba MNN (OpenCL/Vulkan/CPU) |

> **Commercial vs. Non-Commercial Use:** All `MIT` and `BSD-3-Clause` models permit commercial use. Models under `CC-BY-NC-SA-4.0` are restricted by their respective model authors to personal, research, and non-commercial use.

---

## 2. How Exact 2×, 3×, 4×, and 5× Scaling Works

Most super-resolution networks only contain weights for a single scale factor (`4×` for ESRGAN/RealSR, `2×` for Waifu2x; `2×/3×/4×` for SRMD). When you select a target scale such as `2×`, `3×`, or `5×`:

1. `ModelCatalog.modelScaleFor` picks the native weight scale supported by the selected network.
2. The neural engine writes the intermediate upscaled output.
3. `ProcessingServiceEngine` inspects the output dimensions and, if they differ from `input × targetScale`, executes a single `resize-ncnn -m avir-lancir` pass to produce exact target pixel dimensions.

---

## 3. Bundled Native CLI Engines

All eight arm64 executables are packed inside `assets/realsr/binaries.tar.xz` and unpacked once into `cacheDir/realsr/`:

| Binary | Role | Key Flags |
| --- | --- | --- |
| `realsr-ncnn` | Runs RealSR & Real-ESRGAN/ESRGAN NCNN models | `-i -o -s -m -t -j -g -x -f` |
| `waifu2x-ncnn` | Runs Waifu2x upconv7 & CUnet NCNN models | `-i -o -s -n -m -t -j -g -x -f` |
| `srmd-ncnn` | Runs SRMD 2×/3×/4× denoising super-resolution (Vulkan GPU) | `-i -o -s -n -m -t -j -x -f` |
| `realcugan-ncnn` | Runs Real-CUGAN anime NCNN models | `-i -o -s -n -m -t -j -g -x -f` |
| `mnnsr-ncnn` | Runs `.mnn` super-resolution models via Alibaba MNN (CPU/OpenCL/Vulkan) | `-i -o -s -m -b -t -j -f` |
| `Anime4k` | Runs built-in Anime4KCPP CNN/shader upscaling | `-i -o -z -A -w -H` |
| `resize-ncnn` | High-precision `avir-lancir`, `bicubic`, `bilinear`, `nearest`, `de-nearest`, `perfectpixel` | `-i -o -m -s` |
| `magick` | Format conversion (HEIC/WebP/JPEG/BMP) and animated GIF coalesce/reassembly | `-filter -resize -coalesce -delay` |

---

## 4. Converting Custom PyTorch / ONNX Models

### Converting `.pth` (ESRGAN) to NCNN (`.param` + `.bin`) with chaiNNer

This repository includes a ready-to-use [chaiNNer](https://chainner.app/) workflow file ([`chainner-pth2ncnn.chn`](../chainner-pth2ncnn.chn), previewed in [`chainner-pth2ncnn.png`](../chainner-pth2ncnn.png)):

1. Download a compatible ESRGAN / Real-ESRGAN `.pth` model from [OpenModelDB](https://openmodeldb.info/).
2. Open `chainner-pth2ncnn.chn` in chaiNNer, load your `.pth` file, and export to NCNN format (`x4.param` and `x4.bin`).
3. Verify the parameter input/output blob names match `realsr-ncnn` conventions (`data` and `output`).

### Converting `.onnx` to MNN (`.mnn`)

1. Install the official MNN converter in Python:
   ```bash
   pip install MNN
   ```
2. Convert an ONNX super-resolution model to FP16 MNN:
   ```bash
   MNNConvert -f ONNX --modelFile model.onnx --MNNModel model-x4.mnn --bizCode biz --fp16 --detectSparseSpeedUp
   ```
3. Include the scale tag (`-x2`, `-x3`, or `-x4`) in the `.mnn` filename so `mnnsr-ncnn` and `CommandListManager` detect the native scale factor automatically. You can also convert online using upstream's Hugging Face Space: <https://huggingface.co/spaces/tumuyan2/model2mnn>.
