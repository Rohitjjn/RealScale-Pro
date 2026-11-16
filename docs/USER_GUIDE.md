# RealScale-Pro User Guide

**Applies to:** RealScale-Pro `v2.3.1` (`com.realscale.pro`) · Android 7.0+ (`arm64-v8a`)

RealScale-Pro enlarges and cleans up photos, scans, screenshots, anime, illustrations, and animated GIFs locally on your Android phone using Vulkan/OpenCL GPU or CPU neural inference. Every step runs offline once your chosen model is installed.

---

## Table of Contents

1. [Installation & Updates](#1-installation--updates)
2. [First-Run Onboarding, Device Check & Starter Pack](#2-first-run-onboarding-device-check--starter-pack)
3. [Importing Images (5 Ways)](#3-importing-images-5-ways)
4. [Upscale Mode vs. Super Resolution Mode](#4-upscale-mode-vs-super-resolution-mode)
5. [Default, Manual & Advanced Settings](#5-default-manual--advanced-settings)
6. [Testing Settings with 512×512 Preview Crop](#6-testing-settings-with-512512-preview-crop)
7. [Processing, Background Queue & Thermal Protection](#7-processing-background-queue--thermal-protection)
8. [Comparing, Zooming, Saving & Sharing Results](#8-comparing-zooming-saving--sharing-results)
9. [Managing AI Models](#9-managing-ai-models)
10. [Home-Screen Widgets](#10-home-screen-widgets)
11. [Batch Upscaling & Animated GIFs](#11-batch-upscaling--animated-gifs)
12. [Troubleshooting](#12-troubleshooting)

---

## 1. Installation & Updates

1. Download the latest `RealScale-Pro-vX.Y.Z-arm64.apk` from [**GitHub Releases**](https://github.com/Rohitjjn/RealScale-Pro/releases/latest).
2. *(Optional)* Verify the file against the matching `.sha256` file: `sha256sum -c RealScale-Pro-v2.3.1-arm64.apk.sha256`.
3. Open the APK on your Android phone.
   - If prompted on Android 14 or 15 with *"App was built for an older version of Android"*, tap **More details → Install anyway** (see [`SECURITY.md`](../SECURITY.md#4-why-targetsdk--28-is-retained) for why `targetSdk = 28` is used for native NCNN executables).
4. **Automatic updates:** Add `https://github.com/Rohitjjn/RealScale-Pro` to [Obtainium](https://github.com/ImranR98/Obtainium). All official releases from `v2.2.1` onward share the same signing certificate (`SHA-256: 45:C3:D0:AA:...`) so updates install in place and retain all downloaded models.

---

## 2. First-Run Onboarding, Device Check & Starter Pack

On first launch, RealScale-Pro walks through four quick steps (you can replay the guide anytime from **Settings → Help and feedback → Replay the guide**):

1. **Interactive Welcome Guide:** Drag the live Before/After slider and review offline privacy guarantees.
2. **Optional Permissions:** Grant notification permission on Android 13+ if you want background progress and completion alerts. Broad photo library permission is optional because the system photo picker works without it.
3. **Device Check (`DeviceProfile`):** Measures RAM, CPU core count, free storage, and GPU capability to assign your phone to one of three tiers:
   - **Light** (4–6 GB RAM) — defaults to safe `128–256 px` tiles and compact models.
   - **Standard** (6–10 GB RAM) — defaults to `256–384 px` tiles and balanced models.
   - **Powerful** (12 GB+ RAM) — unlocks heavy models like *Nomos8kSC x4* and larger tile budgets.
4. **Starter Pack (7.9 MB total):** Downloads two compact, fast models over HTTPS with pause/resume and SHA-256 verification:
   - **Real-ESRGAN General v3** (`2.4 MB`) for everyday photos.
   - **Waifu2x Anime** (`5.5 MB`) for anime, comics, and illustrations.

---

## 3. Importing Images (5 Ways)

- **Gallery** (Home tile): Opens the native Android system photo picker across all albums and cloud galleries without requiring broad storage permissions.
- **Files** (Home tile): Opens the system document browser (`ACTION_OPEN_DOCUMENT`) so you can pick lossless PNGs, WebPs, TIFF/BMPs, HEIF/AVIFs, or GIFs from any folder, SD card, USB drive, or cloud drive.
- **Camera** (Home tile): Captures a new photo directly into the configuration screen.
- **System Share & Open-with Quick Sheet:** Inside Google Photos, Samsung Gallery, Fossify Gallery, or any file manager, tap **Share → RealScale-Pro** (supports single or multiple photos) or **Open with → RealScale-Pro** to launch the translucent **Quick Upscale Sheet** directly over your current app.
- **Home-Screen Enhance Widget:** Tap the widget (or its **2×**, **3×**, or **5×** chip) to pick a photo and start upscaling immediately.

---

## 4. Upscale Mode vs. Super Resolution Mode

Choose your mode on the Home screen (or set a default in **Settings → General → Default mode on launch**):

| Mode | Best For | Behavior |
| --- | --- | --- |
| **Upscale Mode** | Everyday photos, social shares, quick enlargements, older phones | Prioritizes fast models (*Real-ESRGAN General v3*, *Waifu2x*), gently optimizes oversized inputs (`> 2 MB` at 90% in Auto policy, never touching PNGs), and caps memory conservatively. |
| **Super Resolution Mode** | Archival prints, old family scans, detailed landscapes, wallpaper art | Preserves the uncompressed source image, raises the output megapixel ceiling, and requires a full-capacity model (*Remacri x4*, *UltraSharp x4*, *AnimeSharp x4*, or *RealSR DF2K*). If the heavy model isn't installed yet, the primary button downloads it first instead of silently running a light fallback. |

---

## 5. Default, Manual & Advanced Settings

On the configuration screen, use the segmented bar under your photo preview to pick how much control you want:

### Default
Automatically detects **Photo** vs. **Anime**, chooses the best installed model for your device tier and mode, sets a safe tile size, and displays exact output resolution and estimated duration on the **Upscale** button.

### Manual
- **Image type:** `Auto`, `Photo`, or `Anime`
- **Model:** Browse, switch, or download any of the 14 catalog models
- **Scale:** Exact `2×`, `3×`, `4×`, or `5×` (neural pass + precision Lanczos pass when the target differs from the network's native factor)
- **Output format:** `Default` (matches input), `JPEG`, `PNG`, or `WebP`

### Advanced
- **Tile preset:** `Safe (128 px, ~0.4 GB)`, `Balanced (256 px, ~0.9 GB)`, `Fast (384 px, ~1.6 GB)`, `Maximum` (12 GB+ devices), or `Custom` pixel size. Smaller tiles avoid GPU crashes; larger tiles finish faster and reduce seam overhead.
- **Compute:** `Auto`, `GPU` (Vulkan / OpenCL), or `CPU` (`-g -1` on ncnn, `-b 0` on MNN; note that SRMD runs on GPU only).
- **Threads:** Automatic (`0`) or custom thread count.
- **Denoise:** Strength control for noise-aware models (`Waifu2x` and `SRMD`).
- **Enhanced quality (TTA):** Enables 8-way Test-Time Augmentation (`-x`) on `realsr-ncnn`, `waifu2x-ncnn`, `srmd-ncnn`, and `realcugan-ncnn`. Produces cleaner micro-details at roughly 4–8× longer runtime.
- **Input compression:** `Auto` (re-encodes inputs > 2 MB at 90%, never turning PNG into JPEG and never above 24 MP), `Off`, or `Custom`.
- **Output quality:** JPEG / WebP quality slider (`default 95%`).
- **Keep photo metadata:** Copies EXIF tags (capture date, camera, lens, exposure, GPS) from the source photo onto the saved output.

---

## 6. Testing Settings with 512×512 Preview Crop

Tap the **Preview Crop** button in the top-right corner of the configuration screen, then tap any region of interest (eyes, foliage, text, hair lines). RealScale-Pro runs your exact model and parameters on that `512×512` area alone and renders **Original** and **Preview** side by side in a few seconds.

---

## 7. Processing, Background Queue & Thermal Protection

- **Live Tile Reveal:** Your photo begins desaturated and fills in with color tile by tile as the engine finishes, paired with percentage progress, rolling ETA, and elapsed time.
- **Continue in Background:** Tap *Continue in background* to keep browsing or leave the app. A floating card inside RealScale-Pro and a silent foreground notification (with a **Cancel** action) bring you back in one tap.
- **Thermal & Battery Guard:** When enabled in Settings, RealScale-Pro monitors platform thermal status and low-battery broadcasts, automatically dialing back tile size and thread count to keep your phone cool.

---

## 8. Comparing, Zooming, Saving & Sharing Results

- Completed jobs automatically save to **`Pictures/RealScale-Pro`** (or your custom folder configured in **Settings → Output → Save folder**) and register in Android's MediaStore so they appear immediately in your gallery.
- Drag the **Before / After** handle horizontally to inspect sharpness and denoising.
- Tap **Full screen** to pinch-zoom or double-tap into native 1:1 resolution (powered by `BitmapRegionDecoder` so even 50 MP outputs stay razor-sharp without out-of-memory errors).
- Browse, multi-select, share, or delete previous creations from **My Upscales**.

---

## 9. Managing AI Models

Tap the **Layers** icon on the top bar of Home (or **Settings → Models → Manage models**):

- Inspect total model storage alongside free device space.
- Filter across **All**, **Photo**, and **Anime**.
- Tap a model's download button to stream from Hugging Face with live progress; tap during transfer to pause/cancel (resumes from the partial `.part` file via HTTP `Range`).
- Tap any model card to inspect native scales, engine, speed, license, and hardware fit, or delete installed optional models to reclaim space.

---

## 10. Home-Screen Widgets

Long-press your Android home screen → **Widgets → RealScale-Pro**:

1. **Enhance Widget:** One-tap photo upscale launcher with **2×**, **3×**, and **5×** quick-scale chips (resize vertically to reveal chips on compact launchers).
2. **Recent Creations Widget:** Displays your latest upscaled photos with instant tap-to-open access.

---

## 11. Batch Upscaling & Animated GIFs

- **Multiple images:** Select multiple images via Android's **Share** menu to queue them sequentially (one active GPU task at a time to prevent Vulkan memory exhaustion). If one URI in a batch is unreadable, the rest of the batch continues safely.
- **Animated GIFs:** Importing a multi-frame `.gif` splits frames with bundled ImageMagick, upscales every frame through the selected neural model, and reassembles the animated GIF at its original average frame delay.

---

## 12. Troubleshooting

- **Output looks solid black or crashes midway:** Lower **Tiles** to `Safe (128 px)` in Advanced config. If your phone's Vulkan driver still misbehaves on a specific model, switch **Compute** to `CPU` or try the MNN engine (*RealESRGAN Anime 6B*).
- **Result looks overly smooth or "painted":** Lower the **Denoise** slider, switch from *Real-ESRGAN General v3* to *UltraMix Balanced* or *Waifu2x Photo*, or use `2×` instead of `4×`.
- **Upscaling takes several minutes:** Check whether **Enhanced quality (TTA)** is enabled (TTA multiplies runtime by ~4–8×), whether **Compute** is set to `CPU`, or whether **Thermal Guard** is actively cooling your device.
- **Reclaiming storage:** Open **Settings → Storage → Clear temporary cache** to remove crop previews and cached imports older than three days, and remove unused 33 MB models in **Model Manager**.
