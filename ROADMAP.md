# RealScale-Pro Roadmap

This roadmap tracks what has shipped and where **RealScale-Pro** is heading next. Community feedback and pull requests directly shape priorities — open a [Feature Request](https://github.com/Rohitjjn/RealScale-Pro/issues/new?template=feature_request.yml) or [Model Request](https://github.com/Rohitjjn/RealScale-Pro/issues/new?template=model_request.yml) to weigh in.

---

## Shipped Milestones

- **v2.0.0** — Full Kotlin + Jetpack Compose architecture on top of native NCNN/MNN engines, solid `binaries.tar.xz` compression (cutting APK size from ~475 MB to ~20 MB), Room database, and HTTP `Range` resumable SHA-256 verified model installer.
- **v2.1.0** — RealScale-Pro adaptive/monochrome icon suite (`com.realscale.pro`), translucent system-wide `Open with` & `Share` Quick Upscale Sheet, sequential GPU job queue, 3-channel notifications, OOM-safe `BitmapRegionDecoder` viewer, Jetpack Glance widgets, and ART Baseline Profile.
- **v2.2.0 – v2.2.2** — Warm Editorial design-system UI rewrite, hardware `DeviceProfile` check, deterministic `PipelinePlanner`, exact `2× / 3× / 4× / 5×` scaling, `512×512` Preview Crop, live monochrome-to-color tile grid, 14-model verified catalog, `ThermalGuard` throttling, TTA (`-x`), custom SAF save folder, EXIF metadata preservation, widget `2× / 3× / 5×` quick chips, and stable APK update signing.
- **v2.3.1 (Current)** — Strict 100% offline/on-device architecture (zero cloud mode, zero telemetry), rock-solid Result screen navigation, tall `9:16` configuration layout fix, resilient multi-file import intake, full open-source documentation suite, Fastlane store metadata, and public product site.

---

## Near-Term (v2.4.x) — Distribution & Polish

- [ ] **FOSS App Directory Distribution:** Submit to **IzzyOnDroid**, **Obtainium Hub**, **OpenAPK**, and **AlternativeTo** using the bundled Fastlane metadata.
- [ ] **F-Droid Fully Reproducible Source Build:** Build all native C++ engines in `RealSR-NCNN-Android-CLI/` directly from pinned source tarballs during CI instead of extracting prebuilt upstream binaries.
- [ ] **Custom Local Model Import UI:** First-class picker in Model Manager to import user-converted `.param`/`.bin` (NCNN) and `.mnn` models from device storage with automatic scale detection.
- [ ] **Expanded UI Localization:** Complete in-app language switcher wiring for Hindi (`हिन्दी`), Simplified Chinese (`简体中文`), Spanish, Japanese, Portuguese, and German.
- [ ] **Batch Queue Screen:** Rich multi-photo batch configuration with per-image preview, progress status, and export summary.

---

## Mid-Term (v2.5 – v3.0) — Engine & Platform Evolution

- [ ] **`jniLibs` Packaging & Modern `targetSdk`:** Package native CLI binaries as `lib*.so` under `jniLibs/arm64-v8a/` (or direct JNI bindings) so `targetSdk` can advance to API 35+ without Android 14/15 sideload warnings while preserving zero-overhead Vulkan execution.
- [ ] **Expanded Model Packs:** Optional verified Real-CUGAN Pro/SE anime pack, face-restoration helper pass, and document/text-scan restoration models.
- [ ] **Automated Visual Regression CI:** Pair Compose emulator renders against `docs/ui-reference/screens/` across Light and Dark themes on every PR.
- [ ] **Large-Screen & Foldable Refinements:** Dedicated side-by-side Configure + Live Compare workspace for Android tablets, foldables, and ChromeOS.

---

## Explicit Non-Goals

To keep RealScale-Pro trustworthy, fast, and focused, the following will **not** be added:

- Mandatory cloud accounts, paid credit tiers, watermarks, or advertising SDKs
- Silent telemetry, usage analytics, or third-party tracking libraries
- Bundling hundreds of megabytes of unused AI weights back into the base APK
