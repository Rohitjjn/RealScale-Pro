# RealScale-Pro — Changelog

Running log of every change, written by the agents (rules.md §7). Newest first.
Every entry: date, phase, what changed, why (one or two lines, plain English).

---

## 2026-11-03 — Phase 2 — Model-source decisions
- The owner approved carefully testing the older permissive CNN sources one by
  one and required official GFPGAN despite its disclosed non-commercial component.
  Nothing becomes downloadable until its exact files, checksums and runtime pass.

## 2026-11-03 — Phase 2 — Model and face reuse audit
- Audited the planned CNN and face sources before copying code. Existing waifu2x
  fallbacks remain safe; older CNN candidates require exact-file tests, while the
  official GFPGAN route carries a non-commercial restriction and other routes are
  blocked by GPL, missing or unverifiable model terms.

## 2026-11-03 — Phase 2 — Kickoff and task plan
- Expanded Phase 2 into twelve small, testable tasks informed by the Phase 1
  report. Hardware gaps remain open while model choice, downloads, faces and
  complete Settings are built in order.

## 2026-11-03 — Phase 1 — Cloud verification for owner hand-off
- Added pinned GitHub Actions checks for formatting, analysis, tests, prohibited
  package policy, a real ncnn model run, and Android/Windows debug builds. Successful
  builds are retained for 14 days so Phase 1 can be checked without owning a PC.

## 2026-11-03 — Phase 1 — Verification and native compatibility fixes
- Verified the shared ncnn library on one-tile and nine-tile inputs, replaced the
  incompatible anime fallback with a matching permissive model, added Android
  hardware profiling, and registered native notices in the in-app license page.

## 2026-11-03 — Phase 1 — Tests and target hand-off
- Added portable unit tests and `tool/verify_phase1.sh`. JavaScript/data checks
  pass here; Flutter analysis and Android/Windows builds remain explicitly
  pending because this workspace cannot download the Flutter SDK or run targets.

## 2026-11-03 — Phase 1 — Interactive HTML preview
- Added a responsive browser preview for owner review. It supports image import,
  real tile-based preview progress, before/after comparison, saving and history;
  it is a UI preview, not the release AI engine.

## 2026-11-03 — Phase 1 — Results, saving and settings
- Added result comparison and sharing, automatic RealScale-Pro folder saving,
  local My Upscales history, deletion, themes, haptics, guide replay, privacy and
  open-source license screens.

## 2026-11-03 — Phase 1 — Default upscale flow
- Added image inspection, the 2 MB quality-90 temporary-copy rule, data-driven
  planning, a thin ncnn interface, tiled processing with completed-tile progress,
  moving ETA, cancellation, a safe CPU fallback and friendly failures.

## 2026-11-03 — Phase 1 — Home and image selection
- Added recents, viewer and zoom, Gallery/Files/Camera/More imports, persisted
  mode guidance, app options and the locked Coming soon card with no processing
  or media libraries for that future feature.

## 2026-11-03 — Phase 1 — First launch and design system
- Added the owner-approved neutral/blue/coral/amber palette, light and dark
  themes, native splash styling, four-slide guide, permission explanations,
  device check and offline Starter setup.

## 2026-11-03 — Phase 1 — Android and Windows app foundation
- Added the pinned Flutter project, Android 8+ and Windows 10+ shells, Riverpod,
  go_router, English localization data, planner/model data and feature structure.

## 2026-11-03 — Phase 0 — Engine feasibility decision
- Confirmed the owner-selected direct-upstream ncnn path and documented the
  available CPU result plus the Android, Windows and Vulkan checks that remain
  pending because this workspace does not provide those targets.

## 2026-11-03 — Phase 0 — Real ncnn CPU proof
- Added a repeatable native harness and two small, permissively licensed offline
  fallback models. A real model produced a 128×128 result from a 64×64 input in
  39 ms of engine time on the available 4 GB Linux workspace.

## 2026-11-03 — Phase 0 — Reuse and license audit
- Recreated the missing `licenses.md`, compared upstream engine approaches, and
  pinned the selected native and Flutter dependencies to audited permissive
  versions. GPL, AGPL and non-commercial options remain blocked.

## 2026-11-03 — Project setup — Phase 0 prep
- Created the complete project document set from PRD v2.0:
  `README.md` (agent kickoff), `rules.md`, `agents.md`, `architecture.md`,
  `phases.md`, `design-system.md`, `models.md`, `licenses.md`, `qa-checklist.md`.
- Established the phase loop: each phase ends with a report in `reports/`
  using the agents.md template; next phase starts only after owner approval.
- Decision framework added for reuse-first work, permissive licenses only,
  video feature permanently locked (UI card only).

<!-- Agents: continue this file below. Format:
## YYYY-MM-DD — Phase N — short title
- what changed; why
-->
