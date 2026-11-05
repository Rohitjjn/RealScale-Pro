# Phase 4 Report — Expansion and launch — 5 November 2026

## 1. Summary (plain English, 5–8 lines)

Phase 4 expands the app to more people and prepares it for launch with
everything this sandbox can honestly verify. **Hindi localization ships**:
a complete `app_hi.arb` (all 346 user-facing strings, placeholders
validated against the English template), generated localizations, a
system/English/हिन्दी language picker in Settings (persisted), and
`MaterialApp.router` locale wiring — a widget test renders the translated
Home. An **accessibility pass** verifies the config screen stays usable at
200 % text scale (all three levels, sticky CTA reachable, no exceptions).
**Store assets** are generated from the brand palette: feature graphic
(1024×500), hi-res icons (1024 px flat + rounded), and a 1080×1920
listing frame with a Hindi CTA. Platform build targets that this Linux
sandbox cannot compile (iOS/macOS store builds, Windows packaging) remain
honest gaps — the fixes and configurations from earlier phases carry
over, and nothing in Phase 4 changed platform code.

## 2. What was built

| Feature / module | Main files | Status |
| --- | --- | --- |
| Hindi ARB (346 strings) | `lib/l10n/app_hi.arb`, `scripts/hi_strings.json`, `scripts/gen_hi_arb.py` | Done — placeholder-safe generation with template validation |
| Generated Hindi localizations | `lib/l10n/app_localizations_hi.dart` (regenerated) | Done |
| Language picker + persistence | `settings_service.dart` (`language`: system/en/hi), `settings_screen.dart` (`_pickLanguage`) | Done |
| Locale wiring | `lib/app/app.dart` (`supportedLocales` + `locale` switch) | Done |
| Accessibility verification | `widget_screens_test.dart` (200 % text-scale test) | Done |
| Hindi render test | `widget_screens_test.dart` (Hindi Home test) | Done |
| Store assets | `store/feature_graphic_1024x500.png`, `store/app_icon_1024.png`, `store/app_icon_rounded_1024.png`, `store/screenshot_frame_1080x1920.png`, `scripts/gen_store_assets.py` | Done |
| Regression | full suite re-run | Done |

## 3. How to run and test it

```bash
flutter pub get
flutter analyze          # 0 issues
flutter test             # 65/65 pass
flutter run              # Settings → Language → हिन्दी → whole app flips live
```

## 4. Reuse decisions

| Component | Chosen option | License | Alternatives | Why |
| --- | --- | --- | --- | --- |
| Hindi translation | Hand-written ARB with scripted validation | project code | Machine-translation services (out: network + quality), intl proxy files | Fully offline, reviewable diff, generator re-runs when English changes |
| Store art | Own PIL generator from brand tokens | project code | Hand-made art in external tools (not reproducible) | Scripted, deterministic, regenerable from the design tokens |

## 5. Test results vs qa-checklist.md (Phase 4)

| Checklist section | Items | Passed | Failed | Not run (reason) |
| --- | --- | --- | --- | --- |
| Phase 4 (this phase) | new coverage | 2 | 0 | store review, device-level a11y pass pending |
| Phase 1–3 regression re-run | 63 | 63 | 0 | — |

Evidence:
- `flutter analyze`: **0 issues**; `flutter test`: **65/65**; formatted.
- Hindi: 346/346 strings present, placeholder usage verified against the
  English template by `gen_hi_arb.py`; `Locale('hi')` Home widget test
  passes (अपस्केल / सुपर रेज़ोल्यूशन / सेटिंग्स all render).
- Accessibility: 200 % text-scale config test passes with all three level
  segments and the sticky Upscale button present; no exceptions.
- Store assets exist and are non-trivial (9–37 KB PNGs each).

## 6. Known issues & limitations (honest hardware gaps)

1. **iOS/macOS builds are not compilable in this Linux sandbox** — no
   Xcode toolchain. Flutter's `ios/` and `macos/` scaffolding can be
   generated on a Mac with `flutter create --platforms=ios,macos .` in
   one command; no project code is platform-gated beyond the existing
   `tryLoad()` guards (Phase 3) that safely refuse native engines there.
2. **Windows packaging** remains blocked by the sandbox disk ceiling
   documented in `reports/phase-2.md` §6 (unchanged); the Gradle/CMake
   fixes already applied apply to it directly.
3. Device-level accessibility (TalkBack/VoiceOver sweeps) and store
   review need real hardware — the automated 200 % scale check covers
   layout robustness only.
4. Hindi is complete for all shipped UI strings; new strings added later
   must be mirrored in both ARBs (`gen_hi_arb.py` fails loudly if a key
   is missing — intentional).

## 7. Risks for later phases

- Later-phase work is post-launch (video unlock decision, batch,
  before/after collage, share-to-app) — none of it affects shipped code.
- If English strings change, run `flutter gen-l10n` and
  `python3 scripts/gen_hi_arb.py` (after updating `hi_strings.json`) —
  the placeholder validation catches drift.

## 8. Open questions for the owner

1. Sign-in assets for Play Store / App Store listing copy (short + full
   descriptions) are not written yet — owner tone preference needed.
2. Beta channel choice (Play internal testing vs TestFlight first).

## 9. Notes

- The app is feature-complete for phases 0–4 scope: offline upscaling
  (Default / Manual / Advanced), Super Resolution mode with feasibility
  gate + preview crop, model manager with verified checksums, face
  pipeline (model-blocked by license decision), Hindi + English, light/
  dark Material 3 themes, full settings, privacy statement.
- Git tags: `phase-3`, `phase-4`.
