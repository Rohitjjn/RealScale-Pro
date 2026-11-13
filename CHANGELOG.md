# Changelog

All notable changes to **RealScale-Pro** are documented in this file.

## [2.1.0] - 2026-11-12

### Added
- **Brand Identity & Splash (`P0-A`)**: Official RealScale-Pro eagle medallion adaptive icon (`#BD4707` terracotta background), Android 13+ `<monochrome>` themed icon, API 24–25 legacy square/round icons, monochrome `ic_stat_realscale` notification silhouette, AndroidX `SplashScreen` (`1.2.0`) with reduced-motion-aware greeting fade, and finalized `applicationId = "com.realscale.pro"`.
- **Open-With Quick Upscale Sheet & Sequential Queue (`P0-B`)**:
  - `ACTION_VIEW` (`Open with` / `Edit with`) and `ACTION_SEND` / `ACTION_SEND_MULTIPLE` (`Share`) routed to translucent `QuickUpscaleActivity` bottom sheet (configurable in Settings: `Quick sheet` vs `Full app`).
  - Sequential `EngineJobQueue` (`1` active native GPU/CPU job + queued items with per-item cancellation).
  - Three notification channels (`channel_progress`, `channel_service_alive`, `channel_result`) with live foreground progress + **Cancel** action and `BigPictureStyle` completion notification with **Share** and **Open** deep links.
  - Clean MediaStore export to `Pictures/RealScale-Pro/<original>_x2.<ext>` / `_x4.<ext>` with PNG/JPG/WebP conversion.
- **Engine Reliability, Pre-Flight Guardrails & Tile Split Viewer (`P0-C`)**:
  - Human-readable stages (`Preparing`, `Enhancing`, `Saving`) with rolling-average determinate ETA and elapsed-seconds fallback when native CLI tools (`resize-ncnn`) emit no percentages.
  - `PreflightAnalyzer` megapixel, `16,384 px` dimension ceiling, and storage guardrails with one-tap remedies (`Switch to 2×`, `Safe tile size 128`, `Use CPU`), plus one-tap error recovery actions.
  - `SavedStateHandle` + Room restoration across configuration changes and process death.
  - OOM-safe `BeforeAfterSplitView` combining downsampled base bitmaps with viewport-cropped `BitmapRegionDecoder` tiles at zoom `> 1.5×`.
- **Studio Polish & Smart Preset Suggestion (`P0-D`)**:
  - Unified editorial drop-zone card, fast `<30ms` `ImageContentClassifier` (`Suggested for this photo`: Photo vs Anime/Line-art), stacked batch strip (`Batch of N`), sticky bottom summary CTA, haptic feedback, and calm spring transitions respecting system `ANIMATOR_DURATION_SCALE`.
- **Scalable Model Hub (`P0-E`)**:
  - `ModelHubQueryEngine` with storage header, 2-model `"Recommended for you"` Starter Pack, `Installed` vs `Available` sections, debounced search, sort (`Recommended`, `Fastest`, `Smallest`, `Best quality`, `Name`), counted filter chips, Wi-Fi-only WorkManager constraints, and global download progress banner.
- **Dedicated Library, Result Polish, Grouped Settings & Glance Widgets (`P1`)**:
  - 3-tab primary navigation (`Studio` · `Library` · `Models`) + top-right Settings.
  - `LibraryScreen` with 2-column grid, search, sort, and multi-select **Share / Compare / Delete**.
  - Edge-to-edge `ResultScreen` with dual-contrast split handle, tap-and-hold original peek, double-tap `100%` zoom, details row, `"Open in gallery"` snackbar, and Delete confirmation.
  - Grouped `SettingsScreen` (`Defaults`, `Performance`, `Downloads & Storage` with live cache size & clear, `Notifications & Integrations`, `Appearance`, `About`, and `Advanced` raw CLI/logs).
  - Jetpack Glance `1.2.0` **Small** (`Enhance a photo`) and **Medium** (`Recent creations`) home-screen widgets.
- **Fast Crop Preview, Hindi Localization, Tablet Layout & Baseline Profile (`P2`)**:
  - `CropPreviewEngine` + `CropPreviewSheet` (`224×224` sharpest-detail patch upscaled in ~1–2s before full-photo processing).
  - Complete English + Hindi (`हिन्दी`, `res/values-hi/strings.xml`) UI strings with Devanagari-safe `PlatformTextStyle(includeFontPadding = true)` and expanded line heights.
  - `WindowWidthSizeClass` (`>= 600dp`) `EditorialSideRail` and two-pane side-by-side `StudioScreen` for tablets and foldables.
  - Bundled ART `baseline-prof.txt` + `androidx.profileinstaller:profileinstaller:1.4.1`.

### Changed
- Removed inherited upstream `.github/workflows/{android,huggingface,libmnn-linux,libs,mnnsr-linux}.yml` so `.github/workflows/build-apk.yml` is the sole authoritative CI/release pipeline.
- Release APK verified at **`20.21 MiB`** (`0` bundled AI models; all 19 unmodified native CLI binaries & `.so` libraries packed in `binaries.tar.xz`).

## [2.0.0] - 2026-11-11

### Added
- Initial Jetpack Compose Warm Editorial rebuild, decoupled `StateFlow` `UpscaleEngine`, Room database, `binaries.tar.xz` slim asset extractor (`18.54 MiB` release APK), and 14-model SHA-256 verified HuggingFace `ModelHub`.
