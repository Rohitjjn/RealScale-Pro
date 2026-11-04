# Phase 2 Progress Report — Choice and faces — 4 November 2026

> **Work in progress — not Phase 2 sign-off.** This report exists so the owner can
> see exactly what is and is not implemented. It must not be used to create the
> `phase-2` tag.

## 1. Summary
Phase 2 has started but is not finished. The model-source audit and typed catalog
foundation exist, and two higher-quality 2× Starter downloads now have exact
GitHub addresses, sizes and checksums. The original small waifu2x models are still
the only models the native app can execute; they remain safe fallbacks while
Real-ESRGAN integration is built and tested. Model Manager, Manual controls,
face detection/restoration, complete Settings and Phase 2 QA are not implemented
yet. Current Flutter tests and Android/Windows compilation pass in GitHub Actions.

## 2. What has been built
| Task | Current result | Status |
| --- | --- | --- |
| 2.1 Model/license audit | Audited current CNN and GFPGAN sources; pinned exact Real-ESRGAN x2plus and anime-video x2 files; documented the owner-approved GFPGAN restriction | In progress — remaining models and executable GFPGAN files are not verified |
| 2.2 Typed catalog | Added validated definitions for 17 catalog entries with bundled, on-demand, held, blocked, restricted and future states | In progress — install/download runtime-state contracts still remain |
| 2.3 ModelManager service | Existing Phase 1 downloader only | Not started |
| 2.4 Model Manager screen | Phase 1 placeholder only | Not started |
| 2.5 Manual configuration | Manual control remains disabled | Not started |
| 2.6 Manual planner | Default 2× fallback planner only | Not started |
| 2.7 Face detection | No verified detector pipeline | Not started |
| 2.8 Face download/toggle | Disabled Phase 1 placeholder | Not started |
| 2.9 Native face restoration | No GFPGAN runtime pipeline | Not started |
| 2.10 Complete Settings | Phase 1 subset only | Not started |
| 2.11 Accessibility/localization/preview | No Phase 2 pass yet | Not started |
| 2.12 Full QA and release evidence | CI compilation is green; Phase 2 behavior QA has not run | Not started |

## 3. Current model position
| Role | Model | Download size | Runtime state |
| --- | --- | ---: | --- |
| Preferred photo 2× target | Real-ESRGAN x2plus | 67,106,328 bytes | Exact source pinned; native integration not complete |
| Preferred anime 2× target | Real-ESRGAN AnimeVideo v3 x2 | 1,250,541 bytes | Exact official-derived pair pinned; native integration not complete |
| Emergency photo fallback | Waifu2x compact photo 2× | 1,107,295 bytes | Bundled and executable |
| Emergency anime fallback | Waifu2x compact anime 2× | 1,107,295 bytes | Bundled and executable |
| Face restoration target | Official GFPGAN v1.4 | Approximately 349 MB | Required by owner; restricted license documented; files/runtime not enabled |

The preferred models are **not yet called by the app**, so this report does not
claim that image quality has changed. Switching the label before the engine runs
the model would be misleading.

## 4. Verification evidence
Pull request: https://github.com/Rohitjjn/RealScale-Pro/pull/2

Latest complete run at the time of this report:
https://github.com/Rohitjjn/RealScale-Pro/actions/runs/41763750099

- Flutter formatting, analyzer, unit/widget tests and policy checks: passed.
- Native ncnn fallback proof: passed.
- Android arm64 debug compilation and artifact: passed.
- Windows debug compilation and artifact: passed.
- The earlier Android failure was hosted-runner disk exhaustion, not an app
  compiler failure; runner cleanup was added and the next two Android runs passed.

## 5. Known gaps and risks
- Real-ESRGAN x2plus needs source-file conversion, native input/output handling,
  tiling and real output comparison before it can replace the fallback.
- Download resume, updates, storage accounting and delete-to-Auto are not wired.
- GFPGAN includes a disclosed non-commercial third-party restriction. The owner
  approved using the official source, but the restriction remains a distribution
  risk and must stay visible.
- Phase 1 real-device memory, driver, thermal and cancellation checks remain open.
- No Phase 2 checklist item should be marked passed merely because compilation is green.

## 6. Next implementation order
1. Finish install/download state contracts and the ModelManager service.
2. Download, transform and run both preferred Real-ESRGAN 2× models through ncnn.
3. Make the tested photo model the default and preserve automatic fallback.
4. Build Model Manager and Manual controls with 2×–5× planning.
5. Add the audited face detector, Face pack and GFPGAN pipeline.
6. Complete Settings, localization, preview and the full QA matrix.

A final Phase 2 report will replace this progress report only after those items
are implemented and honestly tested.
