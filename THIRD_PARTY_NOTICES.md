# Third-Party Notices & Attributions

RealScale-Pro's application source code is licensed under the [MIT License](LICENSE). This project builds upon and interacts with the following open-source libraries, native inference engines, fonts, icon sets, and neural network model weights.

---

## 1. Upstream Android Project & Native Engines

### RealSR-NCNN-Android
- **Author:** tumuyan
- **Repository:** <https://github.com/tumuyan/RealSR-NCNN-Android>
- **License:** MIT License (`Copyright (c) 2022 tumuyan`)
- **Usage:** Native Android CLI toolchain (`RealSR-NCNN-Android-CLI/`), shell execution runtime (`ProcessingService`, `ImageProcessor`, `CommandBuilder`, `CommandListManager`, `AssetsCopyer`, `ProgressLogHelper`, `GifUntils`, `ShellUtils`), and mirrored model collection (<https://huggingface.co/tumuyan2/realsr-models>).

### realsr-ncnn-vulkan, waifu2x-ncnn-vulkan, srmd-ncnn-vulkan, realcugan-ncnn-vulkan
- **Author:** nihui
- **Repositories:**
  - <https://github.com/nihui/realsr-ncnn-vulkan>
  - <https://github.com/nihui/waifu2x-ncnn-vulkan>
  - <https://github.com/nihui/srmd-ncnn-vulkan>
  - <https://github.com/nihui/realcugan-ncnn-vulkan>
- **License:** MIT License

```text
The MIT License (MIT)

Copyright (c) 2019 nihui

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### Tencent ncnn
- **Repository:** <https://github.com/Tencent/ncnn>
- **License:** BSD-3-Clause
- **Usage:** Primary Vulkan and ARM NEON mobile neural network inference framework.

### Alibaba MNN
- **Repository:** <https://github.com/alibaba/MNN>
- **License:** Apache-2.0
- **Usage:** Mobile neural network inference engine for `.mnn` models (`mnnsr-ncnn`).

### Real-ESRGAN
- **Authors:** Xintao Wang, Liangbin Xie, Chao Dong, Ying Shan
- **Repository:** <https://github.com/xinntao/Real-ESRGAN>
- **License:** BSD-3-Clause

### waifu2x & SRMD
- **Repositories:** <https://github.com/nagadomi/waifu2x>, <https://github.com/cszn/SRMD>
- **License:** MIT License

### Anime4KCPP & Anime4K
- **Repositories:** <https://github.com/TianZerL/Anime4KCPP>, <https://github.com/bloc97/Anime4K>
- **License:** MIT License

### ImageMagick
- **Repository:** <https://github.com/ImageMagick/ImageMagick>
- **License:** ImageMagick License (Apache-2.0 compatible)
- **Usage:** Image format conversion and animated GIF frame extraction/reassembly (`magick`).

### OpenCV & libwebp
- **Repositories:** <https://github.com/opencv/opencv> (Apache-2.0), <https://chromium.googlesource.com/webm/libwebp> (BSD-3-Clause)
- **Usage:** Image decoding, encoding, and matrix utilities in native CLI tools.

---

## 2. Android, Kotlin & JVM Libraries

| Library | License |
| --- | --- |
| AndroidX Jetpack (Compose, Activity, Lifecycle, Navigation, Room, Glance, Core, SplashScreen, ProfileInstaller, DocumentFile) | Apache-2.0 |
| Kotlin Standard Library, Coroutines & Kotlinx Serialization (`org.jetbrains.kotlinx`) | Apache-2.0 |
| Coil 3 (`io.coil-kt.coil3:coil-compose`) | Apache-2.0 |
| XZ for Java (`org.tukaani:xz`) | 0BSD / Public Domain |
| JUnit 4 (`junit:junit`, test only) | EPL-1.0 |
| Robolectric (`org.robolectric:robolectric`, test only) | MIT |

---

## 3. Fonts & Icons

| Asset | Source | License |
| --- | --- | --- |
| Cormorant Garamond | Catharsis Fonts / Google Fonts | SIL Open Font License 1.1 (OFL-1.1) |
| Inter | Rasmus Andersson / rsms.me | SIL Open Font License 1.1 (OFL-1.1) |
| JetBrains Mono | JetBrains s.r.o. | SIL Open Font License 1.1 (OFL-1.1) |
| Noto Sans Devanagari | Google Fonts | SIL Open Font License 1.1 (OFL-1.1) |
| Lucide Icons (`LucideIcons.kt`) | <https://github.com/lucide-icons/lucide> | ISC License |

---

## 4. On-Demand AI Model Weights

RealScale-Pro bundles **zero** neural network weights inside its APK. Models are downloaded on demand by the user from <https://huggingface.co/tumuyan2/realsr-models> (mirrored from <https://github.com/tumuyan/realsr-models>). Each model remains governed by its original author's license:

| Model Name | Manifest ID | License | Commercial Use |
| --- | --- | --- | --- |
| Real-ESRGAN General v3 | `models-RealeSR-general-v3` | BSD-3-Clause | Permitted |
| Waifu2x Anime | `models-upconv_7_anime_style_art_rgb` | MIT | Permitted |
| Waifu2x Photo | `models-upconv_7_photo` | MIT | Permitted |
| Waifu2x CUnet | `models-cunet` | MIT | Permitted |
| SRMD | `models-srmd` | MIT | Permitted |
| RealSR DF2K | `models-DF2K` | MIT | Permitted |
| RealSR DF2K JPEG | `models-DF2K_JPEG` | MIT | Permitted |
| RealESRGAN Anime 6B (MNN) | `models-MNN-RealESRGAN_x4plus_anime_6B` | BSD-3-Clause | Permitted |
| Remacri x4 (Foolhardy) | `models-ESRGAN-Remacri` | CC-BY-NC-SA-4.0 | Non-commercial only |
| UltraMix Balanced x4 | `models-ESRGAN-UltraMix_Balanced` | CC-BY-NC-SA-4.0 | Non-commercial only |
| UltraSharp x4 fp16 (Kim2091) | `models-ESRGAN-UltraSharp-fp16` | CC-BY-NC-SA-4.0 | Non-commercial only |
| Nomos8kSC x4 (Phhofm) | `models-ESRGAN-Nomos8kSC` | CC-BY-NC-SA-4.0 | Non-commercial only |
| AnimeSharp Lite x4 (Kim2091) | `models-ESRGAN-AnimeSharpLite` | CC-BY-NC-SA-4.0 | Non-commercial only |
| AnimeSharp x4 (Kim2091) | `models-ESRGAN-AnimeSharp` | CC-BY-NC-SA-4.0 | Non-commercial only |
