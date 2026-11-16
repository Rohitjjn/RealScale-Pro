# Contributing to RealScale-Pro

Thank you for your interest in contributing to **RealScale-Pro**. Whether you are reporting a Vulkan driver quirk on a specific phone, translating UI strings, tuning a super-resolution model entry, or improving Jetpack Compose screens, every contribution helps keep private on-device upscaling free for everyone.

---

## Ways to Contribute

1. **Report bugs and device compatibility notes** using the [Bug Report template](https://github.com/Rohitjjn/RealScale-Pro/issues/new?template=bug_report.yml). Include your phone model, chipset/GPU (e.g., Adreno 730, Mali-G610), RAM, Android version, chosen model, and scale factor.
2. **Request or package new NCNN / MNN models** using the [Model Request template](https://github.com/Rohitjjn/RealScale-Pro/issues/new?template=model_request.yml). Every model added to `ModelCatalog.kt` and `model_manifest.json` must load cleanly on the bundled `realsr-ncnn`, `waifu2x-ncnn`, `srmd-ncnn`, or `mnnsr-ncnn` engine and include a verified license and SHA-256 digest.
3. **Add or improve UI translations** under `RealSR-NCNN-Android-GUI/app/src/main/res/values-<locale>/strings.xml` and store listings under `fastlane/metadata/android/<locale>/`.
4. **Contribute Kotlin / Compose code or tests** in `RealSR-NCNN-Android-GUI/`.

---

## Development Setup

1. Install **JDK 21**, **Android SDK** (`ANDROID_HOME` configured), `curl`, `unzip`, `tar`, `xz`, and `python3`.
2. Fork and clone the repository:
   ```bash
   git clone https://github.com/<your-username>/RealScale-Pro.git
   cd RealScale-Pro
   ```
3. Prepare the slim native engine bundle (required before assembling an APK):
   ```bash
   ./scripts/prepare-engine-assets.sh
   ```
4. Run the unit and Robolectric test suite:
   ```bash
   cd RealSR-NCNN-Android-GUI
   ./gradlew testDebugUnitTest
   ```
5. Assemble debug and release APKs:
   ```bash
   ./gradlew assembleDebug assembleRelease
   ```

See [`docs/BUILDING.md`](docs/BUILDING.md) for custom keystore signing, emulator smoke tests, and native C++ CLI builds.

---

## Engineering Ground Rules

- **Keep it offline and private.** Never introduce cloud upload flows, analytics SDKs, crash-reporting trackers, ad libraries, or unnecessary Android permissions.
- **Keep the APK slim (`<= 30 MB`, target `~20 MB`).** Never commit neural network weights (`.bin`, `.param`, `.mnn`) into `app/src/main/assets/`. CI explicitly fails if any model weights are bundled in the APK.
- **Preserve `targetSdk = 28`** unless your PR also migrates all native CLI tools to `jniLibs/` execution and passes Android 11–15 device/emulator verification.
- **Follow the Warm Editorial design system (`design/`).** Read colors from `LocalRsColors.current` (`Tokens.kt`), typography from `RsType` (`Type.kt`), radii from `RsRadius`, and motion timings from `RsMotion` (respecting `rememberReduceMotion()`). Avoid raw `MaterialTheme.colorScheme` overrides on visible surfaces.
- **Observe store state reactively.** In Compose screens, always read `val state = store.rememberState()` — never raw `store.state.value`.
- **Keep planner changes tested.** Any update to `PipelinePlanner.kt`, `PresetCommandResolver.kt`, `EngineProgressParser.kt`, or `ModelCatalog.kt` must ship with updated unit tests in `app/src/test/`.
- **Do not break upstream C++ CLI source** in `RealSR-NCNN-Android-CLI/` unless specifically fixing a documented native engine issue.

---

## Adding a Model to the Catalog

1. Verify the model files exist on the HTTPS mirror (`huggingface.co/tumuyan2/realsr-models`) with HTTP `Range` support, and confirm the model's license (`MIT`, `BSD-3-Clause`, `Apache-2.0`, or clearly labeled `CC-BY-NC-SA-4.0`).
2. Compute exact byte sizes and lowercase 64-character SHA-256 checksums for every `.bin`, `.param`, or `.mnn` file and add the entry to `RealSR-NCNN-Android-GUI/app/src/main/assets/model_manifest.json`.
3. Add the matching `ModelEntry` (linked via `manifestId`) to `core/plan/ModelCatalog.kt`.
4. Document the model and its license in `docs/MODELS.md` and `THIRD_PARTY_NOTICES.md`.
5. Test downloading, pausing, resuming, running a 2× and 4× upscale, and deleting the model on an Android device or emulator.

---

## Pull Request Process

1. Create a focused feature or bugfix branch from `main` (one logical change per PR).
2. Run `./gradlew testDebugUnitTest assembleDebug assembleRelease` and ensure all checks pass.
3. Keep commit messages clear and concise (e.g., `fix(result): preserve EXIF orientation on WebP export`, `docs: add Spanish Fastlane listing`).
4. Open a Pull Request against `main` and fill out the PR checklist. Include before/after screenshots for any UI change.

---

## License & Code of Conduct

By contributing to RealScale-Pro, you agree that your contributions will be licensed under the project's [MIT License](LICENSE). All contributors and maintainers are expected to follow our [Code of Conduct](CODE_OF_CONDUCT.md).
