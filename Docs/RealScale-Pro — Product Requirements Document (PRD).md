# RealScale-Pro — Product Requirements Document (PRD)

Version 2.0 (images only, video locked) · 3 November 2026 · Status: Draft, ready to hand to an AI coding agent · Owner: Product owner (non-coder)

## 1. Product summary and scope

RealScale-Pro is an offline, on-device AI image upscaler that runs on any phone or PC, from a 2 GB RAM budget device to a high-end gaming PC. This version covers **image upscaling only**. Video is visible in the UI as a locked card and must not be implemented.

**Product name:** RealScale-Pro

**One-line promise:** pick a photo, tap Upscale, get a sharper, bigger image in minutes, with no account, no internet after setup and no crashes.

### 1.1 Audience and what each group needs

| Group | Share | What they want | Design consequence |
| --- | --- | --- | --- |
| Social media users | 70% | Fast, good-looking results they can share in seconds | Recents carousel, one-tap Upscale, instant share, a premium look worth showing off |
| Normal users | 20% | Fix old or blurry family photos without learning anything | Default settings do everything automatically |
| Professionals and advanced users | 10% | Full control, original quality, best models | Super Resolution Mode with Manual and Advanced settings |

### 1.2 Platforms

- One Flutter codebase for Android, iOS, Windows, macOS and Linux.
- The flows in this PRD are **mobile-first**. Desktop reuses the same screens in wider layouts (Section 11).
- Build order: Android and Windows first, then iOS, macOS and Linux (Section 13).

### 1.3 In scope for this version

- First-launch experience: splash, guide, permission screens, device check and model download.
- Home screen with recent photos, import options, mode selector and app options.
- Two modes: **Upscale Mode** (default) and **Super Resolution Mode**.
- Three setting levels in each mode: **Default**, **Manual**, **Advanced**.
- Face enhancement toggle, scale factors 2x, 3x, 4x and 5x, automatic image-type detection.
- Animated progress with ETA, in-app result viewer, share, re-upscale, new image.
- Automatic saving to a dedicated RealScale-Pro folder.
- A model catalog containing every supported upscaling model, with on-demand download.

### 1.4 Out of scope (locked)

- **Video upscaling.** Show a locked "Video Upscale, coming soon" card only. Write no video code and add no video libraries. Keep the architecture open so video can be added later (Section 12).
- Accounts, cloud processing, batch folders, generative or diffusion upscalers, photo-editing tools.

### 1.5 Product principles

1. **Zero-config by default.** A first-time user never has to choose anything.
2. **Never crash.** The app adapts to the device and recovers from low memory on its own.
3. **Premium feel.** Smooth, minimal, polished, with motion and haptics that feel native.
4. **Private.** Every image stays on the device.
5. **Honest.** Real progress, real ETA, clear warnings before slow or risky actions.

## 2. Build approach and technology decisions

Reuse proven open-source projects wherever they exist. Write from scratch only the UI, the glue code and the Smart Engine rules. The UI is built by AI agents; the engine is assembled from existing, tested upscaling code.

### 2.1 Reuse-first policy (binding rules for AI agents)

1. Before writing any module, the agent searches GitHub and package registries for a maintained solution and reports 2-3 candidates with license, last update and fit. The owner approves the choice.
2. Prefer, in this order: Flutter built-in widgets, maintained pub.dev packages, existing open-source upscaling code, then custom code.
3. Accept permissive licenses only (MIT, BSD, Apache-2.0, MPL-2.0). GPL or AGPL code needs explicit owner approval, because it can force the app's own source code to be published.
4. Pin versions. A package with no release in 18 months needs approval.
5. Log every reused component (name, version, license, URL, where used) in a licenses file and show it on the in-app Open-source licenses screen.
6. Custom code is allowed only for: the screens and design system, the Device Profiler and Pipeline Planner rules, and thin adapters between parts.

### 2.2 Components to reuse (starting candidates, verified in Phase 0)

| Need | Candidate to reuse | Notes |
| --- | --- | --- |
| Upscaling engine for CNN models | ncnn (BSD-3) with Real-ESRGAN-ncnn-vulkan, realcugan-ncnn-vulkan, waifu2x-ncnn-vulkan (MIT) | Already include model loading, tiling and scale handling. Wrap as a native library and call it through Dart FFI |
| Android build reference | Existing open-source Android ncnn upscaler apps (for example RealSR-NCNN-Android) | Use as a guide for the native build; check the license before copying code |
| Transformer models (SwinIR, HAT, DAT) | ONNX Runtime (MIT) | Added in Phase 2 behind the same engine interface, high-end devices only |
| Model weights | Official Real-ESRGAN, Real-CUGAN, SwinIR, HAT, DAT, GFPGAN releases; OpenModelDB catalog | OpenModelDB lists community models with licenses. Many are non-commercial, so review each one |
| Anime vs photo detection | An existing small anime-vs-real image classifier | Verify license. Fallback: a simple statistics heuristic (color and edge histograms) |
| Face detection | A small open face detector (YuNet or RetinaFace family) | Verify license |
| Compression | flutter\_image\_compress | Native encoders, quality parameter |
| Gallery, recents, thumbnails | photo\_manager | Handles limited-access states |
| Pickers (files, camera) | file\_picker, image\_picker |  |
| Permissions | permission\_handler |  |
| Share | share\_plus |  |
| Model downloads | background\_downloader (or dio) | Resume, background, progress |
| Notifications | flutter\_local\_notifications | Progress notification on Android |
| Device info | device\_info\_plus plus a small native channel for RAM and GPU |  |
| Zoom viewer, compare slider | photo\_view or InteractiveViewer; an existing before/after slider package |  |
| Onboarding, splash | smooth\_page\_indicator, flutter\_native\_splash |  |
| Material 3, dynamic color, motion | Built-in Material 3, dynamic\_color, flutter\_animate, lottie |  |
| State and navigation | riverpod, go\_router |  |
| Settings and history storage | shared\_preferences; drift (SQLite) for history |  |

Package names are starting candidates, not final: the agent confirms each is maintained and correctly licensed before use.

**Reference only, do not copy code:** Upscayl (AGPL-3.0) and chaiNNer (GPL-3.0). Study their UX and model lists, but their licenses would force the same terms onto RealScale-Pro.

### 2.3 Technology decisions

| Layer | Decision | Why |
| --- | --- | --- |
| UI | Flutter (Dart), Material 3 | One codebase, smooth animation |
| Primary engine | **ncnn** (Vulkan GPU, CPU fallback) | Light, works on most GPUs without CUDA, existing upscalers already built on it |
| Second engine | **ONNX Runtime**, Phase 2 | For transformer models that ncnn handles poorly |
| Engine interface | One `UpscaleEngine` interface with two implementations | The UI and planner never know which engine runs |
| Native layer | Reused C/C++ upscaler code plus a thin FFI wrapper | Avoids writing inference code from scratch |
| Threading | Inference on native threads, results to Dart through streams | The UI never freezes |
| Minimum OS | Android 8 (API 26), iOS 15, Windows 10, macOS 12, Ubuntu LTS |  |

**Phase 0 must prove** that the Starter Pack models run correctly on a 2-3 GB Android phone (CPU and Vulkan) and on a Windows laptop with 4 GB RAM and integrated graphics. If ncnn fails this test, switch the primary engine to ONNX Runtime before any UI work.

**Honest risk:** two engines add complexity. If SwinIR, HAT and DAT cannot be integrated cleanly, they stay in the catalog as "Coming soon" and Phase 2 ships without them.

**Build note:** iOS and macOS builds need a Mac and an Apple Developer account, so they come after Android and Windows.

## 3. First launch experience

A new user goes through five steps and lands on Home with the Starter Pack models installed. Every later launch goes splash, then Home, in under 2 seconds.

| Step | Screen | Purpose |
| --- | --- | --- |
| 1 | Splash | Brand moment, no white flash |
| 2 | Guide (4 slides) | Explain the app in 15 seconds |
| 3 | Permission screens | Ask for access with a clear reason |
| 4 | Device check | Detect the device and pick its performance profile |
| 5 | Setup and model download | Install the Starter Pack with visible progress |

### 3.1 Splash screen

- Native splash that matches the app theme (light or dark), then a short animated logo reveal, at most 1.5 seconds.
- No loading spinner. Initialization runs behind the animation.

### 3.2 Guide screens

- Four swipeable slides with a page indicator, a Skip button and a final "Get started" button.
- Slide 1: "Make any photo sharper" (animated before and after). Slide 2: "Works on every device" (the app adapts automatically). Slide 3: "Two modes" (Upscale Mode and Super Resolution Mode in one line each). Slide 4: "Private and offline" (photos never leave your device).
- The guide can be replayed later from Settings, Help.

### 3.3 Permission screens

Each permission has its own pre-permission screen: an icon, one sentence on why it is needed, one sentence on what the app will not do, an "Allow" button that triggers the system dialog, and a "Not now" button. Never show raw system dialogs without this explanation.

| Permission | When | Needed for | If denied |
| --- | --- | --- | --- |
| Photos and media access | First launch | The Recent photos carousel and the in-app gallery | Home shows an "Allow photo access" card instead of Recents. The Gallery and Files tiles still work through system pickers, so the app stays fully usable |
| Notifications | First launch (optional) | Progress and completion alerts when the app is in the background | Progress is shown only inside the app |
| Saving results | No separate screen | Android 10+ saves through MediaStore into Pictures/RealScale-Pro, which needs no permission. Android 9 and below ask for storage write access when first saving. iOS asks for add-photos permission on first save | Offer "Save to app folder" as a fallback |
| Camera | Only when the user taps the Camera tile | Taking a photo to upscale | A short message with an "Open settings" button |

Rules: ask only for what the current step needs, remember a "Not now" choice, re-ask in context later (for example when the user taps the missing Recents card), and offer "Open settings" when a permission is permanently denied. On Android 14 and iOS, handle "selected photos only" access gracefully.

### 3.4 Device check

- A 2-3 second animated screen: "Checking your device".
- Reads RAM, CPU cores, GPU and Vulkan support, free storage, OS version, and runs a micro-benchmark with one small tile.
- Shows a friendly result such as "Your device is ready. Performance: Standard" using three labels (Light, Standard, Powerful), never raw tier names.
- The profile is stored and refreshed after app updates or on demand from Settings.

### 3.5 Setup and Starter Pack download

- Title "Setting up RealScale-Pro" with a list of the Starter Pack models. Each row shows the model name, size and a status (queued, downloading with percentage, verifying, done).
- One overall progress bar with percentage, megabytes done and total, current speed and time remaining. Pause and Resume buttons.
- **Starter Pack** = everything Upscale Mode needs: the compact photo and anime models, the anime-vs-photo classifier and the face detector. Target total size: 50 MB or less. Face restoration models are large and are not part of it.
- On mobile data, ask first: "Download 45 MB using mobile data?" with Wi-Fi-only as the default.
- Handle: no internet (Retry button, preserved partial files), low storage (clear message with required space), corrupted file (automatic re-download after checksum failure).
- Downloads continue if the app goes to the background.
- On success: a completion animation, a success haptic and a "Start upscaling" button that opens Home.
- Recommendation to confirm (Section 13): ship the two tiny compact models inside the app package so the very first upscale works even offline, and download the rest.

### 3.6 Acceptance criteria

- A new user can reach Home in under 3 minutes on normal Wi-Fi.
- Denying every permission still leaves a working app.
- Killing the app during download resumes the download on next launch without restarting from zero.

## 4. Home screen

Home is one scrollable screen with five zones, top to bottom: header, recent photos, choose another image, mode selector, and app options.

```
+--------------------------------------+
|  RealScale-Pro                  [gear]|   1. Header
|                                      |
|  Recent photos              See all >|   2. Recents carousel
|  +------+ +------+ +------+ +---    |      (scroll left to right)
|  | img  | | img  | | img  | | im     |
|  +------+ +------+ +------+ +---    |
|  [Upscale][Upscale][Upscale][Up       |
|                                      |
|  Choose another image                |   3. Import tiles
|  [Gallery] [Files] [Camera] [More]   |
|                                      |
|  Mode                                |   4. Mode selector
|  (o) Upscale Mode          (default) |
|  ( ) Super Resolution Mode      PRO  |
|                                      |
|  My Upscales            >            |   5. App options
|  Model Manager          >            |
|  Video Upscale     [lock] Coming soon|
|  Settings / Guide / Help / About  >  |
+--------------------------------------+
```

### 4.1 Header

- The app name "RealScale-Pro" in a premium wordmark at the top, with a Settings icon on the right.
- Large title that collapses into a compact bar when the user scrolls.

### 4.2 Recent photos carousel

- A horizontal strip, scrolling left to right, showing the 30 newest photos on the device, newest first.
- Each card has a rounded thumbnail and, directly below it, an **Upscale** button. A small badge on the thumbnail shows the resolution (for example 1200x800).
- Tapping the **Upscale** button opens the configuration screen for the currently selected mode with that photo loaded (Section 5 or 6).
- Tapping the **thumbnail** opens the full-screen viewer (4.3).
- "See all" opens the in-app gallery with albums.
- States: loading (shimmer placeholders), empty (friendly message), permission missing (a card with an "Allow photo access" button), limited access (selected photos plus a "Manage" button).
- Thumbnails are small cached versions, loaded lazily, so scrolling stays smooth at 60 fps.
- The thumbnail expands into the full-screen viewer with a shared-element animation.

### 4.3 Full-screen viewer

- Opens on the tapped photo. Swipe left or right to move through the recents. Pinch or double-tap to zoom. Tap once to hide or show the controls. Swipe down to close.
- Top bar: back button and photo info (resolution, file size, date).
- Bottom bar: a primary **Upscale** button, a Details button (information sheet), a Share button, and a chip showing the current mode that can be tapped to switch.
- **Upscale** opens the configuration screen with this photo.

### 4.4 Choose another image

A tidy group of four large tiles with an icon, a title and a one-line subtitle.

| Tile | Subtitle | Behavior |
| --- | --- | --- |
| Gallery | Albums and all photos | In-app gallery browser. If photo access is denied, opens the system photo picker |
| Files | Browse folders | System file picker for JPEG, PNG, WebP, HEIC and BMP |
| Camera | Take a photo | Asks camera permission on first tap |
| More | Clipboard, cloud, other apps | Bottom sheet: paste from clipboard, import through the system picker (cloud drives, iCloud), and (later) receive images shared from other apps |

On desktop, add a drag-and-drop zone next to these tiles.

### 4.5 Mode selector

- Two large selectable cards: **Upscale Mode** (selected by default: "Fast and simple. Everyday photos. Works on any device") and **Super Resolution Mode** (a "Pro" badge: "Maximum quality. Original file, no compression. For powerful devices").
- The selection is remembered between launches and decides which configuration screen opens.
- The first time the user picks Super Resolution Mode, show a bottom sheet: "Recommended for high-end devices and advanced users." It shows how well the device fits (for example "Your device: Powerful, great fit" or "Your device: Light, may be slow") with "Use Super Resolution" and "Stay in Upscale Mode" buttons.
- A small chip near the recents title shows the active mode.
- Selection gives a light haptic click.

### 4.6 App options (bottom of Home)

A grouped list of rows with icons: **My Upscales** (the results gallery), **Model Manager**, **Video Upscale** (locked, see Section 10), **Settings**, **Guide** (replays the intro), **Help and feedback**, **About and open-source licenses**, **Privacy**, **Rate and share the app**.

### 4.7 Acceptance criteria

- Home is interactive within 1 second after the splash.
- From a recent photo, the configuration screen is exactly one tap away.
- The screen is fully usable with photo permission denied.

## 5. Upscale Mode: configuration screen

The user reaches this screen from the Upscale button on a recent photo, from the full-screen viewer, or after picking an image through Gallery, Files, Camera or More. One screen holds everything, top to bottom: image preview, setting level, face toggle, Upscale button.

```
+--------------------------------------+
|  <  Upscale Mode                [swap]|
|  +--------------------------------+  |
|  |                                |  |   Image preview
|  |         selected image         |  |   1200x800 . 2.4 MB
|  +--------------------------------+  |
|  [ Default ] [ Manual ] [ Advanced ] |   Setting level
|  Detected: Photo . 2x . Auto         |   (Default selected)
|  (options expand here for            |
|   Manual and Advanced)               |
|                                      |
|  Enhance faces               [ off ] |   Face toggle
|                                      |
|  [        Upscale   ~3 min        ]  |   Sticky primary button
+--------------------------------------+
```

### 5.1 Image preview

- The selected image sits at the top in a rounded frame, with its resolution and file size. Tap to open a zoomable full view. A swap icon lets the user pick a different image without leaving the screen.
- The original file is never modified.

### 5.2 Input compression rule (Upscale Mode only)

| Source image | What happens |
| --- | --- |
| 2 MB or smaller | Not compressed. Used exactly as selected |
| Larger than 2 MB | Re-encoded at **90% quality** into a temporary copy before upscaling |
| Any image with transparency | Never compressed, so the alpha channel is kept |

- The temporary copy is deleted after the job. The original stays untouched.
- Quality-90 compression reduces file size and decoding cost but not pixel count. Memory use depends on pixel dimensions, so the Smart Engine's tiling and input pixel limit still apply (Section 8).
- Advanced settings can change this rule: Auto (default), Off, or Custom quality.

### 5.3 Setting level: Default, Manual, Advanced

A three-way segmented control directly under the preview. **Default** is selected at the start. Each level contains everything from the one before it.

#### A. Default (nothing to choose)

The app decides everything:

- **Image type:** a small built-in classifier detects whether the image is Anime or a general Photo within half a second of loading it.
- **Scale:** 2x.
- **Model:** chosen automatically for the detected type and the device's performance profile (Section 8.3).
- **Tile size, compute device and compression:** chosen automatically for the device.
- A summary card shows the result, for example "Detected: Photo, 2x, Auto settings", with a "Change" link that switches to Manual. If detection confidence is low, the card says "Not sure, using Photo. Change?".

#### B. Manual

Tapping Manual expands these options below the control:

1. **Image type:** Auto, Photo, Anime (chips). The detected type is marked.
2. **Model:** a list of **every model in the catalog**, installed or not.
   - Each row shows the name, a one-line description, download size, badges (Fast, Balanced, Best quality) and a device-fit indicator.
   - Installed models show a check mark. Models that are not installed show a **Download** button with the size. Tapping it downloads the model inside the list with a progress ring, and the model becomes selectable when it finishes.
   - Heavy models (SwinIR, HAT, DAT) carry the note "Heavy, Super Resolution Mode recommended" and ask for confirmation in Upscale Mode.
   - The model the app would choose automatically carries a "Recommended" badge.
3. **Scale:** 2x, 3x, 4x, 5x chips. A live line shows the output size (for example "4800 x 3200, 15.4 MP") and estimated time. 5x shows "Uses an extra resize step".

#### C. Advanced

Everything from Manual, plus:

| Option | Behavior |
| --- | --- |
| Tile size | **Device-based presets** shown as chips with the tile size and memory use: Safe, Balanced, Fast, Maximum (no tiling). Presets the device cannot handle are disabled with a reason. The best one is marked "Recommended for your device". A **Custom** option takes a number (multiples of 32, 64-1024) and an overlap value (Auto, 16, 32, 64) |
| Compute device | Auto, GPU or CPU. Lists the detected GPU by name. GPU is disabled with a reason if the device lacks support. CPU thread count: Auto or a number |
| Image compression | Auto (the 2 MB rule), Off, or Custom with a quality slider (60-100) |
| Output format | Same as source, JPEG, PNG, WebP, with a quality slider for lossy formats |
| Denoise strength | Slider, shown only for models that support it |
| Enhanced quality (TTA) | Off by default. Warns that it can be up to 8 times slower |
| Numeric precision | Auto, FP16, FP32. Hidden when the device supports only one |
| Keep photo metadata | On by default |
| Reset | "Reset to defaults" button |

The app remembers the last level and the Manual and Advanced choices. Default is always one tap away.

### 5.4 Face enhancement toggle

- A clearly labeled "Enhance faces" switch directly below the level control, **off** by default.
- After the image loads, the face detector checks it. If faces are found, show a hint "Faces found. Turn on face enhancement?". If none are found, the helper text says "No faces detected" and the switch stays usable.
- Face models are a separate download. If they are not installed, switching on shows the download size and a progress bar, and the switch turns on when the download finishes.
- Pipeline: upscale the whole image first, then restore each detected face and blend it back with a soft mask.
- Advanced lets the user choose the face model (GFPGAN by default; see licensing in Section 12).

### 5.5 Upscale button

- A sticky full-width primary button labeled **Upscale**, with a second line showing the output size and estimated time (for example "2400 x 1600, about 3 min").
- Disabled with a clear reason while the image or a model is still loading.
- A medium haptic tap on press, then a transition to the Processing screen (Section 7).

### 5.6 Edge cases

- Unsupported, corrupted or tiny images (under 64 px on a side): a friendly message and a way to pick another image.
- Animated images (GIF): not supported, the first frame is offered instead.
- Respect EXIF orientation. Convert CMYK and 16-bit images to 8-bit sRGB.
- Very large output: show the feasibility warning from Section 8.6 before starting.

### 5.7 Acceptance criteria

- With Default selected, upscaling takes exactly one tap after the image is chosen.
- The anime-vs-photo detector is correct on at least 90% of a mixed test set of 100 images.
- The Manual model list shows every catalog model with the correct installed state, and downloading from the list selects the model afterward.
- Advanced tile presets never offer a size that crashes the device.
- A 5 MB JPEG is recompressed at quality 90 and a 1.5 MB JPEG is untouched.

## 6. Super Resolution Mode

Super Resolution Mode uses the same screen layout and the same Default, Manual and Advanced structure as Upscale Mode (Section 5). It never compresses the image and uses higher-quality models and more options.

### 6.1 What differs from Upscale Mode

| Aspect | Upscale Mode | Super Resolution Mode |
| --- | --- | --- |
| Audience | Everyone, social media users, low-end devices | High-end devices, professionals, advanced users |
| Input compression | 2 MB rule: files over 2 MB re-encoded at quality 90 | **None, ever.** The original file is used as is |
| Input size | Limited by the device's pixel budget (Section 8) | Original size of any image, up to what the device can safely process |
| Default model | Fast, compact models | Highest-quality model the device handles comfortably |
| Model list | Every catalog model, heavy ones flagged | Every catalog model, sorted by quality, Ultra models first on capable devices |
| Default output format | Same as source | PNG (lossless) |
| Warnings | Rarely needed | Recommendation banner and a feasibility check before every run |
| Extra tools | None | Preview crop, sharpening, resize method, color profile |

### 6.2 Entry and recommendation

- The configuration screen shows a banner at the top: **"Recommended for high-end devices and advanced users."** with a device-fit chip (Great fit, OK, May be slow).
- The first time the user chooses the mode on Home, the bottom sheet from Section 4.5 appears.
- Low-end devices may still use the mode. The feasibility check (6.5) protects them from crashes.

### 6.3 Setting levels in this mode

- **Default:** detects the image type, uses 2x, picks the best model the device handles comfortably, the largest safe tile size, GPU when available, no compression, PNG output. The user chooses nothing.
- **Manual:** image type, the full model list and scale (2x to 5x), exactly like Upscale Mode, without the "heavy model" confirmation.
- **Advanced:** every Advanced option from Section 5.3 plus the extra tools below.

### 6.4 Extra Advanced options

| Option | Behavior |
| --- | --- |
| Preview crop | Runs the full pipeline on one 512 x 512 area chosen by the user and shows it next to the original, so models and settings can be compared in seconds before the full run |
| Sharpen after upscale | Slider from 0 to 100, default 0 |
| Resize method for 3x and 5x | Lanczos (default) or Bicubic |
| Color profile | Keep the original ICC profile (default) or convert to sRGB |
| Output format | PNG (default), JPEG, WebP lossless or lossy, with quality slider |
| Face model quality | Higher-quality face restoration settings and model choice |
| Output size cap | Optional maximum output resolution |

### 6.5 Large images and the feasibility check

Original files of any size are allowed, including a 50 MB photo from a professional camera. Before starting, the app calculates the projected output size, memory use and time, and shows a plain-language result.

- Green: go ahead.
- Amber: "This will take about 25 minutes on your device." with options to continue, lower the scale or choose a smaller tile preset.
- Red: "This is too large for your device." with the largest scale that fits, and a "Continue anyway" option only on Advanced.
- Output is written in streamed strips, so the whole result never has to sit in memory.
- Respect format limits: JPEG tops out at 65,535 px per side and WebP at 16,383 px. If exceeded, suggest PNG.

### 6.6 Acceptance criteria

- A 24 MP photo upscales 2x on a high-end device without crashing and without any pre-compression.
- Every Advanced option visibly changes the result or the behavior.
- The Preview crop of a chosen model finishes in under 30 seconds on a mid-range device.
- The original file is never modified.

## 7. Processing, result and saving

This flow is identical in both modes. The user always knows how far along the job is and how long it will take.

### 7.1 Processing screen

- **Fill animation:** the selected image sits in the center under a soft grid. As each tile finishes, that area fills in from blurred to sharp using the real upscaled result, so the user watches the image being enhanced and sees how much is done. This must reflect real progress, never a fake animation. Fallback if too costly on weak devices: a progress fill over the blurred image.
- A large percentage (for example "42%") with a tile counter ("Tile 54 of 128").
- **ETA:** "About 2 min 10 s left", updated every second from a moving average of recent tile times. The first few seconds show "Estimating..." using the benchmark value. Elapsed time is shown in small text.
- **Stage label:** Preparing image, Enhancing details, Enhancing faces (only when the toggle is on), Finishing and saving.
- If the device slows down or the battery is low, show a calm note: "Slowing down to keep your phone cool."
- **Controls:** Cancel (with a confirmation that progress will be lost) and "Continue in background". On Android the job runs as a foreground service with a progress notification. On iOS the screen asks the user to keep the app open. The screen stays awake while processing (switchable in Settings).
- **Haptics:** a soft tick at 25%, 50% and 75%, and a success pattern on completion.
- A rotating tip line below the animation (for example "4x works best on photos under 1000 px").
- **Failure handling:** the Smart Engine retries automatically (Section 8.5). If the job still fails, show a friendly message with a "Try safer settings" button that reruns with smaller tiles or CPU.

### 7.2 Result screen

- The upscaled image is shown inside the app, with pinch-to-zoom and pan.
- A **before and after slider** compares the original and the result. Pressing and holding shows the original.
- An information row: new resolution (for example 2400 x 1600), file size, scale, model used, time taken, and a chip "Saved to Pictures/RealScale-Pro".

| Action | Behavior |
| --- | --- |
| **Share** (primary) | Opens the system share sheet with the full-resolution file |
| **Upscale again** | Returns to the configuration screen with the same image and the same settings, so the user can change them and run again |
| **New image** | Returns to Home to pick another image |
| More menu | Open in gallery, Details, Delete result |
| Later (P2) | Share a before and after collage, made for social media |

### 7.3 Saving

- The result is saved automatically when the job finishes.
- **Location:** a folder named **RealScale-Pro**. Android: Pictures/RealScale-Pro through MediaStore, so it appears in gallery apps. iOS: a Photos album named RealScale-Pro. Desktop: Pictures/RealScale-Pro, changeable in Settings. Fallback: the app's own documents folder.
- **File name:** `<original name>_RealScale_<scale>x_<date-time>.<ext>`.
- **Format:** Upscale Mode keeps the source format (JPEG at quality 95, PNG stays PNG, HEIC, WebP and BMP become JPEG). Super Resolution Mode defaults to PNG. Advanced can override both.
- Photo metadata is kept by default.
- Originals are never overwritten. The app checks free storage before saving and shows a clear message when space is short.
- **My Upscales** (from Home) shows all saved results in a grid. Tapping one opens the result screen, with multi-select for share and delete.

### 7.4 Acceptance criteria

- The percentage and ETA move smoothly and finish within 15% of the estimate on test devices.
- Cancel stops the job within 2 seconds and removes all temporary files.
- A finished result is visible in the system gallery without any extra step.
- "Upscale again" keeps the previous image and settings.

## 8. Smart Engine: automatic hardware handling

The Smart Engine is the invisible brain behind the **Default** level. It reads the device, then chooses model, tile size, compute device and limits so the user never has to. Manual and Advanced users can override its choices, but it still guards against crashes.

### 8.1 Device detection and benchmark

- Detect total and free RAM, CPU cores, GPU type and Vulkan support, VRAM where available, free storage, OS version, battery level and thermal state.
- Run a 2-3 second micro-benchmark (one small tile through the compact model). Use the result to refine the tile size and to calibrate the time estimates.
- Store the profile and refresh it after app updates or on demand from Settings.

### 8.2 Device tiers (initial values, tuned during QA)

| Tier | UI label | Typical device | Default tile | Max tile | Overlap | Compute order | Upscale Mode input limit | Upscale Mode output cap |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T0 | Light | 2 GB RAM or less, old CPU, no Vulkan | 128 | 256 | 16 px | CPU | 1 MP | 16 MP |
| T1 | Light | 3-4 GB RAM, integrated GPU | 256 (128 on phones if the probe is unstable) | 384 | 16 px | GPU if stable, else CPU | 2 MP | 32 MP |
| T2 | Standard | 6-8 GB RAM, mid-range GPU | 384 | 512 | 24 px | GPU | 4 MP | 64 MP |
| T3 | Powerful | 8 GB+ RAM, dedicated GPU with 6 GB+ VRAM | 512 | 1024 or no tiling | 32 px | GPU (Vulkan, DirectML or CUDA where supported) | 8 MP | 128 MP |

MP means megapixels (width times height divided by one million). The tile presets shown in Advanced (Safe, Balanced, Fast, Maximum) are built from the tier's values and never exceed the Max tile column.

### 8.3 Automatic model selection (Default level)

| Mode | Image type | T0-T1 | T2 | T3 |
| --- | --- | --- | --- | --- |
| Upscale Mode | Photo | Real-ESRGAN general compact | Real-ESRGAN general compact | Real-ESRGAN general compact |
| Upscale Mode | Anime | Real-ESRGAN anime-video compact | Real-CUGAN | Real-CUGAN |
| Super Resolution | Photo | Real-ESRGAN x4plus (slow warning on T1) | Real-ESRGAN x4plus | Real-ESRGAN x4plus (Ultra models only by manual choice) |
| Super Resolution | Anime | Real-ESRGAN x4plus anime 6B | Real-CUGAN | Real-CUGAN (full weights) |
| Either | Faces (toggle on) | GFPGAN v1.4 | GFPGAN v1.4 | GFPGAN v1.4 |

All of this lives in one configurable **Pipeline Planner** module driven by data files, never scattered through the UI code. Choices may change after Phase 0 quality tests.

### 8.4 Scale handling (2x, 3x, 4x, 5x)

Rule: use the **smallest native scale that is at least the target**, then resample with Lanczos.

| Target | Photo models (native 4x, some 2x) | Anime models (Real-CUGAN and anime-video: native 2x, 3x, 4x) |
| --- | --- | --- |
| 2x | Native 2x model if installed, else 4x then Lanczos down to 50% | Native 2x |
| 3x | 4x then Lanczos down to 75% | Native 3x |
| 4x | Native 4x | Native 4x |
| 5x | 4x then Lanczos up by 125%, with light sharpening | Same as photo |

5x is larger than any native scale, so the UI shows "Uses an extra resize step".

### 8.5 Tiling and crash-proofing

- Split the image into tiles with overlap and blend the overlapping edges so no seams appear.
- Write finished rows to disk in strips. Never hold a full-size result in memory.
- Keep only one model loaded at a time. Unload it as soon as the job ends.
- **Automatic recovery chain** when memory runs short: halve the tile size (up to 3 times, minimum 64), then switch from GPU to CPU, then reduce CPU threads, then show a friendly message. Never show a raw error or crash.
- If the GPU driver fails (device lost), switch to CPU for this job and remember the GPU as unstable.
- On phones, slow down automatically when the device is hot or the battery is under 15%, and tell the user.
- Delete temporary files after every job, on cancel and at app start.

### 8.6 Feasibility check before starting

Compute the projected output size, memory and time.

- **Default level in Upscale Mode:** if the image exceeds the tier's input limit, the app resizes the input to fit and shows "Large photo optimized for your device". If the output would exceed the cap, it lowers the scale automatically and says so.
- **Manual and Advanced in Upscale Mode:** show a warning and let the user choose between optimizing the size and continuing at full size.
- **Super Resolution Mode:** green, amber or red result as described in Section 6.5. It never resizes the input without asking.
- Respect format limits (JPEG 65,535 px per side, WebP 16,383 px per side).

## 9. Model catalog and downloads

Every supported model lives in one catalog. It feeds the model list in Manual and Advanced settings, the Model Manager and the automatic selection in Section 8.3. Models that are not installed appear in the lists with a Download button.

### 9.1 Catalog

Sizes are approximate and depend on file format and precision (FP16 or FP32). Confirm every size, scale and license in Phase 0.

| Model | Best for | Native scales | Engine | Approx. size | Speed | Min. tier | Pack | License status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Real-ESRGAN general compact (x4v3, with denoise variant) | Everyday photos | 4x | ncnn | under 10 MB | Very fast | T0 | Starter | BSD-3 code, verify weights |
| Real-ESRGAN anime-video compact (animevideov3) | Anime and illustration, fast | 2x, 3x, 4x | ncnn | under 10 MB | Very fast | T0 | Starter | BSD-3 code, verify weights |
| Real-CUGAN (conservative, denoise variants, pro weights) | Anime | 2x, 3x, 4x | ncnn | 5-40 MB | Fast | T1 | Starter (lite), Pro (full) | MIT, verify weights |
| waifu2x (cunet, upconv) | Anime, line art | 2x | ncnn | under 20 MB | Fast | T0 | Pro | MIT, verify weights |
| RealSR | Photos with compression artifacts | 4x | ncnn | 30-70 MB | Medium | T2 | Pro | Verify |
| Real-ESRGAN x4plus | High-quality photos | 4x | ncnn | about 65 MB | Medium | T1 (slow) | Pro | BSD-3 code, verify weights |
| Real-ESRGAN x2plus | High-quality photos at 2x | 2x | ncnn | about 65 MB | Medium | T1 | Pro | BSD-3 code, verify weights |
| Real-ESRGAN x4plus anime 6B | Anime and art | 4x | ncnn | about 17 MB | Medium | T1 | Pro | BSD-3 code, verify weights |
| SwinIR (real-world) | Photos, ultra quality | 4x | ONNX Runtime | 50-150 MB | Slow | T3 | Ultra | Apache-2.0 reported, verify |
| HAT | Photos, ultra quality | 4x | ONNX Runtime | 100-400 MB | Very slow | T3 | Ultra | Verify |
| DAT | Photos, ultra quality | 2x, 3x, 4x | ONNX Runtime | 100-400 MB | Very slow | T3 | Ultra | Verify |
| GFPGAN v1.4 | Face restoration | Face crops | ncnn or ONNX | 300+ MB (smaller in FP16) | Medium | T1 | Face | Apache-2.0 reported, verify |
| CodeFormer | Face restoration | Face crops | ONNX Runtime | about 375 MB | Medium | T2 | Face (optional) | **Non-commercial license. Blocked until a license is obtained** |
| Community models from OpenModelDB | Specialized looks | Varies | ncnn | Varies | Varies | Varies | Community | Only models with a commercial-friendly license, each reviewed individually |
| Anime-vs-photo classifier, face detector | Internal helpers | n/a | ncnn or ONNX | a few MB each | Instant | T0 | Starter | Verify |

**Not included:** Topaz products (paid and proprietary). "Universal Fast V2 / Omni" appeared in earlier research notes, but its source and license are unverified, so it is excluded until verified.

### 9.2 Packs

| Pack | Contents | When it downloads |
| --- | --- | --- |
| Starter (50 MB or less) | Compact photo and anime models, Real-CUGAN lite, classifier, face detector | First launch (Section 3.5) |
| Pro | x4plus, x2plus, anime 6B, Real-CUGAN full, waifu2x, RealSR | When the user picks one of these models or opens Super Resolution Mode |
| Face | GFPGAN, plus CodeFormer if licensed | When the face toggle is first switched on |
| Ultra | SwinIR, HAT, DAT | On request, offered only on capable devices |
| Community | Reviewed OpenModelDB models | On request |

Individual models can also be downloaded one by one straight from the model list.

### 9.3 Download rules

- A remote **manifest file** (JSON) lists every model with its id, version, URL, size, SHA-256 checksum, license, minimum tier, pack, engine, native scales and supported modes. New models can be added without an app update. Hosting (GitHub Releases, Hugging Face or paid storage) is an open decision (Section 13).
- HTTPS only. Verify the checksum after every download and re-download on failure.
- Check free storage first. Pause, resume, queue and background downloads. Wi-Fi only by default.
- Everything works fully offline once a model is installed.
- Updating: when the manifest shows a newer model version, offer the update in Model Manager.

### 9.4 Model Manager screen

- Models grouped by pack, each with its status (installed, not installed, downloading, update available), size, speed and quality badges, and a license link.
- A storage bar showing space used by models and space left on the device.
- Filter by type (Photo, Anime, Face) and by mode. Download all models of a pack, delete a model, check for updates.
- Deleting a model that the current settings use falls back to the automatic choice.

## 10. Settings and other screens

The app must feel like a complete product from a professional company: a proper Settings screen, a help section, an about page and honest handling of every state.

### 10.1 Settings

| Group | Settings |
| --- | --- |
| General | Language (English first, Hindi later), theme (System, Light, Dark), dynamic color on Android, haptics on or off |
| Processing | Default mode on launch, compute preference (Auto, GPU, CPU), keep screen awake while processing, slow down on low battery or high heat, re-run the device check (shows the detected performance label) |
| Output | Save folder (changeable on desktop), default output format (Auto), keep photo metadata |
| Downloads and storage | Download on Wi-Fi only, link to Model Manager, space used by models and cache, clear cache |
| Privacy | Usage analytics (off by default, opt-in), crash reports (off by default, opt-in), a short statement that images never leave the device |
| Help | Replay the guide, FAQ, send feedback or report a problem (can attach the device profile, never images) |
| About | App version, **Open-source licenses** (mandatory), model credits (Real-ESRGAN, Real-CUGAN and the others), terms, privacy policy |

### 10.2 Other screens

- **My Upscales:** grid of saved results with multi-select for share and delete (Section 7.3).
- **Model Manager:** model list, downloads and storage (Section 9.4).
- **In-app gallery:** albums, "See all" from the recents strip, same grid style as My Upscales.
- **Help and FAQ:** short answers to common questions (why is it slow, what is a tile, why 5x looks different).
- **Error and empty states:** every screen has a designed empty, loading and error state with a clear next action. No raw error codes anywhere.

### 10.3 Locked video feature

- Show a **Video Upscale** row or card with a lock icon and the label "Coming soon", in the app options zone on Home.
- Tapping it opens a small bottom sheet: "Video upscaling is coming in a future update." with a Close button.
- **Do not implement anything for video:** no video picker, no video libraries, no video permissions and no processing code. The architecture should simply not make a later addition hard (Section 12).

## 11. Design system and UX quality bar

The app must look and feel premium, polished and minimal, and be as easy to use as a built-in Apple app. All the complexity lives in the engine. The user sees photos, a few big choices and one primary action per screen. The UI is built by AI agents, so this section is the design brief they follow.

### 11.1 Principles

- **Photos are the hero.** Large imagery, calm chrome, generous whitespace.
- **One primary action per screen.** Everything else is secondary or hidden behind progressive disclosure (Default, then Manual, then Advanced).
- **Feels closer to a photo app than a tool.** Think Apple Photos or Google Photos, not a developer utility.
- **Fast and calm.** Motion that explains, never slows.
- **Social-first touches** for the 70% audience: instant share, a good-looking result screen, and a later before and after collage for sharing.

### 11.2 Visual language

- **Material 3** throughout, with light and dark themes and optional dynamic color on Android.
- A signature brand accent (a deep indigo to violet gradient with one warm highlight is the suggested direction). In Phase 1 the agent proposes 2-3 palettes and the owner approves one.
- One clean sans-serif typeface with a clear size scale. Rounded Material Symbols icons.
- Large corner radius (20-28 dp on cards, pill-shaped buttons), tonal surfaces instead of heavy shadows.
- The app icon and logo are designed once and approved by the owner before store submission.

### 11.3 Motion

- Shared-element transitions: recent thumbnail to full-screen viewer to configuration preview to result.
- Spring animations on the setting-level control, chips and cards. Skeleton loaders instead of spinners. The tile fill animation on the processing screen (Section 7.1).
- Typical duration 200-400 ms. Honor the system reduce-motion setting. Target 60 fps on mid-range phones.

### 11.4 Haptics

| Event | Haptic |
| --- | --- |
| Selecting a chip, scale value, level or mode | Light selection click |
| Switching a toggle | Light impact |
| Pressing Upscale | Medium impact |
| Progress at 25%, 50%, 75% | Soft tick |
| Job finished, model installed | Success pattern |
| Error or warning | Warning pattern |

Haptics follow the system setting and can be turned off in Settings.

### 11.5 Responsive layouts and desktop

- **Phones:** single column as drawn in Sections 4 and 5.
- **Tablets and desktop (Windows, macOS, Linux):** a navigation rail on the left, and a two-pane configuration screen (image preview on the left, settings on the right). The recents strip shows the latest files from the Pictures folder and recently opened files. A drag-and-drop zone sits beside the import tiles. Keyboard shortcuts: open image, Enter to upscale, Esc to cancel. Minimum window size 800 x 600. No permission screens on desktop.

### 11.6 Accessibility and localization

- Contrast level AA, screen-reader labels on every control, text scaling up to 200%, touch targets of at least 48 dp, no meaning carried by color alone.
- All text lives in language files (English first, Hindi next). Layouts must survive longer translations.

### 11.7 Reusable components for agents to build once

ModeCard, RecentPhotoCard (thumbnail plus Upscale button), ImportTile, LevelSegmentedControl (Default, Manual, Advanced), ScaleChips, ModelListRow (installed, downloading, not installed), TilePresetChips, FaceToggleRow, PrimaryActionBar, ProgressFillView, BeforeAfterSlider, PermissionPrimer, DownloadProgressRow, InfoChip and the bottom-sheet pattern.

### 11.8 UI acceptance checklist

Every screen is reviewed in light and dark themes, on a small phone (360 x 640), a large phone, a tablet and a desktop window, with large text enabled. No overflow, no clipped text, no frame drops, a designed state for loading, empty and error.

## 12. Architecture, non-functional requirements and licensing

Detailed file structure and agent instructions come in the next planning run. This section fixes the shape every later document must respect.

### 12.1 Layers and modules

1. **UI layer:** screens, reusable widgets and the theme (Sections 3-11).
2. **App state and navigation:** one state-management approach used everywhere.
3. **Services:**
   - Device Profiler: detects hardware and runs the benchmark.
   - Pipeline Planner: turns the user's choices and the device profile into an exact plan. **All decision rules live here**, driven by data files.
   - Model Manager: catalog, manifest, downloads, checksums, storage.
   - Image Service: decoding, 2 MB compression rule, anime-vs-photo detection, face detection.
   - Job Manager: queue, progress, ETA, cancel, background execution, temp-file cleanup.
   - Storage Service: saving to the RealScale-Pro folder, history, My Upscales.
   - Permission Service and Settings Service.
4. **Engine layer:** one `UpscaleEngine` interface, with an ncnn implementation first and an ONNX Runtime implementation in Phase 2. The Tiling Engine (split, process, blend, stream-write, recovery chain) sits here.
5. **Native layer:** reused C/C++ upscaler code behind a thin FFI wrapper.

Rules: the UI contains no AI logic, the engine contains no UI code, and each service has a clear interface so agents can build and test it on its own. A later video feature must be a new service that reuses the engine, without changing existing screens.

### 12.2 Central data contracts

- **JobSpec:** the single object passed from the UI to the engine. It holds the input image, mode, setting level, image type, scale, model, face toggle, tile size and overlap, compute device, compression choice, output format and options.
- **DeviceProfile:** tier, RAM, GPU, Vulkan support, benchmark speed.
- **Model manifest:** the remote JSON catalog (Section 9.3).

### 12.3 Non-functional requirements

| Area | Requirement |
| --- | --- |
| Stability | No known crash path on out-of-memory. The recovery chain in Section 8.5 always applies |
| Performance | UI stays at 60 fps while processing. Home is interactive within 1 second after the splash. Heavy work never runs on the UI thread |
| Install size | Base app under 60 MB per device architecture, excluding downloaded models (use app bundles and architecture splits) |
| Privacy | Fully on-device. No account. Network is used only for model downloads and manifest checks. Analytics and crash reports are opt-in |
| Offline | Fully offline once the needed models are installed |
| Battery and heat | Throttle on high temperature or low battery on phones |
| Storage hygiene | Temporary files removed after every job, on cancel and at app start |
| Security | HTTPS downloads with checksum verification. Models are data files. The app never downloads executable code |
| Accessibility | See Section 11.6 |
| Supported OS | Android 8+ (API 26), Windows 10+, then iOS 15+, macOS 12+, Ubuntu LTS |

### 12.4 Licensing checklist

This is a starting checklist, not legal advice. Verify every item, including the terms of each model's weights and training data, before release.

| Item | Status to verify |
| --- | --- |
| Real-ESRGAN, Real-CUGAN, waifu2x (code) | BSD-3 and MIT reported. Check the weights |
| SwinIR, HAT, DAT, GFPGAN | Apache-2.0 reported. Check each repository and its weights |
| **CodeFormer** | **Non-commercial license. Not shipped unless a commercial license is obtained.** GFPGAN is the default face model |
| OpenModelDB community models | Licenses vary and many are non-commercial. Review each one before adding |
| ncnn, ONNX Runtime, Flutter | BSD-3, MIT, BSD-3 reported |
| Upscayl, chaiNNer | AGPL-3.0 and GPL-3.0. Reference only, no code copied |
| In-app credits | An Open-source licenses screen and model credits are mandatory |

## 13. Roadmap, testing, risks and open decisions

### 13.1 Roadmap (images only)

Phases ship one at a time and each ends with a check on real devices. The next planning run turns this into a detailed phases file.

| Phase | Scope | Exit check |
| --- | --- | --- |
| **0. Feasibility spike** | Run the Starter Pack models through ncnn on one 2-3 GB Android phone and one 4 GB Windows laptop (CPU and GPU). Measure speed and memory. Confirm the engine decision and the reuse candidates from Section 2.2 | Correct output at acceptable speed on both devices |
| **1. Core app (Android and Windows)** | App shell and design system, splash, guide, permission screens, device check, Starter Pack download with progress, Home (recents carousel, full-screen viewer, import tiles, mode selector, app options, locked Video card), Upscale Mode with the **Default** level, processing screen with ETA, result screen, auto-save to the RealScale-Pro folder, My Upscales | A new user installs, sets up, upscales a photo and shares it, with no crash on a 2 GB phone |
| **2. Choice and faces** | **Manual** level with the full model list and inline downloads, Model Manager, all CNN models in the catalog, face enhancement, complete Settings | Every installed model works, face toggle works, downloads resume after interruption |
| **3. Pro** | **Advanced** level, **Super Resolution Mode** (no compression, feasibility check, Preview crop, extra options), ONNX Runtime engine with SwinIR, HAT and DAT | A 24 MP photo upscales on a high-end device without crashing |
| **4. Expansion and launch** | iOS, macOS, Linux, Hindi, accessibility pass, store listing assets, beta test, performance tuning | Store-ready builds |
| **Later (not in this version)** | Video upscaling (unlock the card), batch processing, before and after collage sharing, share-to-app |  |

### 13.2 QA test matrix

| Device class | Example | Must pass |
| --- | --- | --- |
| Android Light | 2 GB RAM, budget CPU | Full Default flow, cancel, out-of-memory recovery |
| Android Light+ | 4 GB RAM | Default and Manual flows, face toggle |
| Android Powerful | 8 GB+ RAM, flagship | Super Resolution Mode, Pro models |
| Windows Light | 4 GB RAM, integrated GPU | Default flow, DirectML or CPU fallback |
| Windows Powerful | 16 GB RAM, dedicated GPU | Super Resolution, Ultra models, 24 MP image |
| Phase 4 | iPhone, Mac, Linux | Native GPU paths |

Test images: an old low-resolution photo, a noisy night photo, a portrait with several faces, an anime illustration, a 24 MP camera photo, a screenshot with text, a PNG with transparency, a 5 MB JPEG and a 1.5 MB JPEG (for the 2 MB compression rule).

### 13.3 Risks

| Risk | Mitigation |
| --- | --- |
| Models too slow on 2 GB devices | Phase 0 benchmarks, compact models, honest ETA, input limits in the Default level |
| Native engine integration with Flutter is hard for a non-coder | Reuse existing ncnn upscalers, one small step at a time, Phase 0 proof first |
| Two engines add complexity | ONNX Runtime arrives only in Phase 3. Ultra models can slip without blocking release |
| Out-of-memory on huge images | Streamed output, feasibility check, recovery chain |
| Overheating or battery drain on phones | Throttling, thermal checks, clear messages |
| Wrong anime-vs-photo detection | Visible "Detected" card, one tap to Manual, fallback heuristic |
| License problems (CodeFormer, community models) | Licensing checklist in Section 12.4, GFPGAN default |
| Store review of large downloads | Models are data, downloaded on user action, Wi-Fi-only by default |
| Quality surprises at 3x and 5x | Smallest-native-scale-then-Lanczos rule, visual test set |
| AI agents drift from the plan | Small phases, written rules and agent instructions, test after every phase |

### 13.4 Open decisions for the owner

1. **Business model:** free, free with ads, one-time purchase or freemium? Should free results carry a watermark?
2. **Platform order:** is Android plus Windows first acceptable? iOS needs a Mac and a paid Apple Developer account.
3. **Model hosting:** GitHub Releases, Hugging Face or paid storage?
4. **Face model:** accept GFPGAN as the default and skip CodeFormer unless a license is obtained?
5. **Bundled fallback models:** ship the two tiny compact models inside the app so the first upscale works even before or without the download? Recommended: yes.
6. **Large photos in the Default level:** confirm that the Default level may resize an oversized input to fit the device, with a visible note, while the 2 MB and quality-90 rule applies on top for file size.
7. **Brand:** approve the color palette direction in Phase 1, supply or commission the logo and icon, and check that "RealScale-Pro" is free as an app name and domain.
8. **Languages at launch:** English only, or English and Hindi?
9. **Analytics:** none, or opt-in only?
10. **Final engine:** confirm ncnn as primary after the Phase 0 report.

### 13.5 Glossary

- **Upscale:** make an image larger and sharper with AI.
- **Model:** a trained file that knows how to enhance images (for example Real-ESRGAN).
- **Tile and tiling:** cutting a big image into small pieces, processing them one by one, then joining them, so memory use stays low.
- **Overlap:** the shared edge between neighboring tiles, blended so no seams show.
- **RAM and VRAM:** the device's working memory and the graphics card's memory.
- **CPU and GPU:** the main processor and the graphics processor. GPUs are usually much faster for AI.
- **Vulkan:** a graphics technology that lets ncnn use many kinds of GPUs.
- **ncnn and ONNX Runtime:** the two engines that run AI models on the device.
- **Out of memory (OOM):** the device runs out of working memory, which normally crashes an app.
- **MP (megapixel):** a measure of image size, one million pixels.
- **FP16 and FP32:** number precision. FP16 is faster and lighter, FP32 is more exact.
- **TTA (test-time augmentation):** running the image several ways and averaging, for slightly better quality at much slower speed.
- **Manifest:** the online list of models with their links, sizes and checksums.
- **FFI:** the bridge that lets Flutter code call the native upscaling code.

### 13.6 Planned project files (next run)

The next run creates these files from this PRD so any AI agent can build the app consistently.

| File | Purpose |
| --- | --- |
| rules.md | Coding, design, testing and reuse rules every agent must follow |
| agents.md | Instructions and prompts for AI agents, one per role and phase |
| architecture.md | Modules, folders, interfaces and data contracts from Section 12 |
| phases.md | Detailed tasks, order and exit checks for Phases 0-4 |
| changelog.md | A running log of every change made, by phase |
| design-system.md | Colors, typography, components and motion from Section 11 |
| models.md | The model catalog, packs, sizes and licenses from Section 9 |
| licenses.md | Reused components and their licenses (Section 2.1) |
| qa-checklist.md | Device matrix, test images and acceptance checks |
