## Summary

<!-- Briefly describe what this PR changes and why. Link any related issue (e.g., Closes #42). -->

## Type of Change

- [ ] Bug fix (non-breaking fix for an existing defect)
- [ ] Feature / UX improvement
- [ ] Model catalog or manifest update (`ModelCatalog.kt` / `model_manifest.json`)
- [ ] Localization / Fastlane metadata
- [ ] Documentation, CI, or build tooling

## Screenshots / Before & After (for UI changes)

<!-- Attach Light and Dark theme screenshots if any visible screen or widget changed. -->

## Verification Checklist

- [ ] `./gradlew testDebugUnitTest` passes locally
- [ ] `./gradlew assembleDebug assembleRelease` succeeds and release APK stays `<= 30 MB` with zero bundled model weights
- [ ] Preserves 100% offline image processing (no cloud uploads, telemetry, or unnecessary permissions)
- [ ] Uses `design/` tokens (`LocalRsColors`, `RsType`, `RsMotion`) and reactive `store.rememberState()`
- [ ] Updated documentation / `THIRD_PARTY_NOTICES.md` if behavior, dependencies, or models changed
