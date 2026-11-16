# Changelog

All notable changes to **RealScale-Pro** are documented in this file.

## [2.3.0] - 2026-11-14

Round 5: the three reports that survived 2.2.2 were reproduced and fixed at their root, and **AI
Upscale Mode** was added — bring your own model API and upscale with a cloud image model.

### Fixed
- **A finished upscale lands on the Result screen (for real this time).** The 2.2.2 fix guarded the Result screen, but the bounce to Home happened one step earlier: the Processing screen called `store.home()` whenever the job was gone or the photo could not be found in the library list — both transient states while the engine finishes — and the engine's `Completed` handler bailed out completely when the job had already been cleared, so nothing navigated at all. The Processing screen no longer sends anyone Home (it follows the result id, and keeps rendering when only the photo row is missing), the completion handler rewrites the stack even with no job, and the results flow now moves a still-visible Processing screen on as soon as its row exists.
- **Paste from clipboard works.** `MediaLibrary.photoFromUri()` called `ContentResolver.query()` unguarded; a `file://` uri — which is exactly what the clipboard path produces after copying the clip into the app cache — has no MediaStore row, so the call threw `IllegalArgumentException` and the whole paste died silently. The query is now guarded and the mime type is derived from the file name. The clipboard reader itself was also rewritten: it reads every item of the clip (not just the first), accepts a uri, a share-intent `EXTRA_STREAM`, a `content://` / `file://` / absolute path in the text, a browser's HTML `<img src>` (downloaded when it is a remote URL), and it says *why* nothing happened instead of failing quietly.
- **The configuration screen no longer collapses for tall photos.** The preview used `fillMaxWidth().heightIn(max = 320.dp).aspectRatio(ratio)`; for a 9:16 photo the width-first measurement produced a 640 dp height, the height cap then forced the *width* down to ~180 dp and the entire screen's content shrank into a narrow column. The card is now full width with a clamped height and the photo is letterboxed inside it, so nothing moves for any aspect ratio.
- **Selected-image details are their own block.** Resolution, file size, type and aspect ratio sit in a grouped card under the preview instead of one crowded line.

### Added — AI Upscale Mode
- **Third mode on Home**, under *Upscale Mode* and *Super Resolution Mode*, with its own card: the photo is sent to a cloud model the user pays for. Picking a local mode leaves AI mode; picking AI mode sends a newly picked image to the AI configuration screen.
- **API setup and dashboard** (`AI Upscaling`): add keys for **Google Gemini**, **OpenAI**, **Stability AI**, **Replicate** or a **custom OpenAI-compatible endpoint**; several keys per provider, one name per key, masked display, enable/disable, monthly image limits, per-key usage, reset usage, delete with confirmation, links to each provider's key page, and an overview of keys / active keys / images used this month. Keys live in the app's private prefs and are only sent as the request header of the provider that was chosen.
- **AI icon on the image preview**, immediately left of the fullscreen icon: no key yet → the API setup screen; a key → the **AI Image Upscale configuration** screen.
- **AI configuration screen** mirroring the on-device one — *Default / Manual / Advanced*:
  - **Default**: a model is chosen automatically (seeded by the photo, so it is stable on screen) from the saved keys, and the photo is uploaded with the built-in reconstruction prompt.
  - **Manual**: model, scale (per model), which key, and the prompt.
  - **Advanced**: everything in Manual plus output format (PNG / JPG / WebP), face enhancement and faithfulness (model-dependent) and the upload size limit.
- **AI processing screen** with its own animation — a single beam sweeping the whole frame instead of the engine's tile grid — plus the provider stage, elapsed seconds, an estimate, cancel, retry on failure and "continue in background".
- **AI results are ordinary results**: the same Result screen (before/after slider, share, full screen), saved to the gallery or the chosen SAF folder, listed in *My Upscales* with the model name and an `AI` badge, and charged against the key's monthly limit only after the provider returned an image.
- Requests are built locally with `HttpURLConnection`: OpenAI `/images/edits` (multipart), Gemini `generateContent` (inline image part), Stability `stable-image/upscale/*`, Replicate `models/{owner}/{name}/predictions` with polling. Uploads are capped and re-encoded, answers are resized to the requested scale (or saved byte-for-byte when too large to decode), and provider errors are translated into one actionable sentence.
- Version **2.3.0** (`60`).

## [2.2.2] - 2026-11-14

Round 4: everything the previous build only *looked* like it did now actually does something.

### Fixed
- **A finished upscale lands on the Result screen.** Two separate emitters (the engine's `Completed` and the Room flow behind `results`) race by a frame; in that frame the Result screen found no row and offered "Back to Home", so the job ended on Home. The store now remembers `lastResultId`, the Result screen waits for its own row instead of declaring it deleted, and the Processing hand-over no longer falls back to Home while an id exists.
- **Notification permission is asked.** The notification slide of the guide recorded "asked" and moved on, so Android's POST_NOTIFICATIONS dialog never appeared and no "upscale finished" alert could ever be posted. The slide now launches the real system request.
- **`Choose An Image To Upscale`** on Home (was "Choose Another Image To Upscale"); the instrumented smoke test asserts the same string.
- **Ghost Anime4K rows are gone.** *Anime4K 2× / 4×* sat in the Model Manager at `0.0 MB`, counted as installed and impossible to delete or download. Both entries are removed from the catalog (Anime4K still runs through its preset); creations saved by an older build keep a readable label.
- **Delete asks first.** The model sheet deleted the weights and *then* opened "Delete model?"; it now only arms the confirmation and the deletion happens on confirm.
- **Haptics work.** The `VIBRATE` permission was missing, so every `vibrate()` call threw inside a `runCatching` and the switch was silent.
- **Keep screen awake works.** The pref reached `EnginePreferences` but no window ever set `keepScreenOn`; the Processing screen now holds the screen awake while a job runs and releases it on the way out.
- **Slow down when hot or low on battery works.** New `ThermalGuard` reads the platform thermal status and the battery broadcast; the engine runs the same model with a smaller tile and a smaller thread budget, and the Processing banner shows the real reason instead of a guess derived from the estimate.
- **Save folder works.** The row was a label; it now opens the system folder picker, persists the SAF grant and the engine writes results there (with a way back to `Pictures/RealScale-Pro`).
- **Keep photo metadata works.** EXIF (date, camera, lens, exposure, location) is copied from the picked photo onto the saved result.
- **Clear cache clears something.** It also drops crop previews and imported copies older than three days; the current session's imports stay.
- **Download on Wi-Fi only** was verified end-to-end (pref → `ModelRepository.hasUsableNetwork`), and *Dynamic color* was removed from Settings because no dynamic colour scheme exists in the design system.
- **Paste from clipboard works.** Only `ClipData.Item.uri` was read; the clipboard now also accepts a share intent, a raw bitmap (screenshots) and a `content://` / `file://` text, all through the normal intake.
- **Super Resolution never runs on a light model.** When its heavy model was not installed the planner fell back to the small model and the job ran anyway. The screen now shows the required model with its size and a **Download** button, and `startJob` refuses to start without it.
- **No file name on the configuration screen.** The picked image's name (and "Imported …" for shared files) is no longer shown under the preview — only the size facts.
- **Enhanced quality (TTA) works.** The switch is wired to the ncnn tools' `-x` flag (`realsr`, `waifu2x`, `srmd`, `realcugan`); the MNN engine has no TTA and says so.
- **Image compression and output quality work.** The engine re-encodes the input copy per the Auto / Off / Custom policy (never above 24 MP, never turning PNG into JPEG) and applies the JPEG/WebP quality slider to the saved file, with metadata copied afterwards.
- Version **2.2.2** (`59`).

## [2.2.1] - 2026-11-15

### Fixed
- **Gallery tile opens the device picker.** Home's *Gallery* now launches the system photo picker (albums, Google Photos, every gallery provider) instead of the app's own grid; *Files* keeps the full folder browser.
- **Model Manager downloads.** Every catalog row emits a state, and a model the user never touched reported `NOT_INSTALLED` — which the store mapped into `downloads`, so all cards showed a dead 0 % ring with "Tap to cancel" and there was no way to start a download from the screen. Only real transfer states (`QUEUED`, `DOWNLOADING`, `VERIFYING`, `PAUSED`, `FAILED`) are drawn now (`ModelInstallStatus.isActiveTransfer`), and a not-installed model gets a real **Download** button with its size inside it. "Too heavy" is a warning, no longer a wall.
- **No box around the live tile grid.** The monochrome-preview card had a dark surface and padding that read as a black border; the photo now sits directly on the screen background with a caption row underneath.
- **The job is reachable again.** "Continue in background" left the Processing screen with no way back. A floating card ("Upscaling in the background · tap to open") now follows the user on Home, Models, Settings and My Upscales, and a tap on the ongoing notification opens the Processing screen instead of Home.
- **Stuck "Saving to Gallery… 99 %" notification.** The save stage posted a second notification that nothing ever cleared, and every finished command left its own "Done" row behind. The save step no longer notifies separately, the per-command row is always removed, and `ProcessingService.finishSession()` clears the foreground notification and stops the service when the batch ends.
- **Full-screen viewer is sharp.** The upscale was decoded once at screen size and stretched by the GPU, so pinching in made it blurry. The viewer now paints a fast preview and, once the gesture settles, a native-resolution region of exactly the visible area decoded with `BitmapRegionDecoder` (`ZoomableImageFile`), with a loading indicator and pan limits.
- **App updates install over the old version.** Both variants were signed with the CI runner's auto-generated debug key, which changes on every run, so Android answered "package conflicts with an existing package". The repository now ships one stable PKCS#12 key (`keystore/realscale.p12`), both `debug` and `release` use it, and CI verifies both APKs carry the same certificate. *Existing installs need one uninstall because they carry the old key.*
- Version **2.2.1** (`58`).

## [2.2.0] - 2026-11-14

### Added
- **Home is the import screen**: "Choose Another Image To Upscale" with four tiles — **Gallery** (in-app browser), **Files** (full system document browser, any folder or cloud provider), **Camera**, and **More** (Paste from clipboard, Share from other apps). The old hero buttons and the recent-photos strip are gone.
- **Result is where a job ends**: a finished upscale navigates straight to the result, which now has a **Full screen** button opening the upscaled file in a pinch/double-tap zoomable viewer with share.
- **Starter models** section in the Model Manager, above the All / Photo / Anime lists, with the two models the app installs at startup (Real-ESRGAN General v3 for photos, waifu2x Anime for illustrations).
- **Model cards** rebuilt: name and description on the left, a Details chevron, and the action on the right — download icon with size, round 0–100 % progress, or a green check disc when installed.
- **Widget quick upscale**: the Enhance widget now carries 2× / 3× / 5× chips. Tapping one picks an image and starts the job immediately at that factor.

### Changed
- **Model catalog matches the shipped engine**: only the 14 models the bundled ncnn/MNN binaries actually support (`assets/model_manifest.json`), plus the two bundled Anime4K shader models. Real-CUGAN, SourceBook, MoeSR and the duplicate SRMD variant were dropped.
- **3× and 5× work.** The network runs at the factor its weights support (`ModelCatalog.modelScaleFor`) and the engine measures the real output, then makes one exact Lanczos pass to the size the plan promised.
- **Live tiles show the image**: while upscaling, the photo is drawn monochrome and each finished tile reveals the colour result, with a hairline grid and an amber ring on the active cell.
- **Tips are just text**: the "Tip" eyebrow above the upscaling tip is gone.
- **Settings**: the Privacy section (usage analytics, crash reports) is removed, the status bar and its icons follow the in-app theme immediately, and Send feedback opens the mail app prefilled to `userrohitai@gmail.com`.
- **Guide screen** uses a real, text-free photo pair, and the notification primer's bell glyph is centred in its tile.
- Version **2.2.0** (`57`).

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
