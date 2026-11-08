# Requirements traceability — Prompt 2 brief → implementation

Format: requirement (condensed from the Prompt 2 instructions) → where
it lives → how it is verified. "Host" = verified on the Linux build in
this sandbox; "wired" = implemented but not executable here.

| # | Requirement | Implementation | Verification |
| --- | --- | --- | --- |
| 1.1 | Locate/audit the existing project; read AGENT_RULES/ROADMAP/CHANGELOG | Workspace found empty (reset); documented; foundation rebuilt | Report + CHANGELOG note |
| 1.2 | Recheck environment (disk/RAM/SDKs/NDK/CMake) | measured: 2 CPU, 3.9 GB RAM, 5.0 GB free end-state, no Android SDK/NDK | Report §Environment |
| 1.3 | Don't re-download valid SDKs; protect caches | Flutter 3.47.9 re-fetched (was absent); nothing deleted | BUILD.md artifacts list |
| 2 | Real inference via existing engine abstractions (UpscaleEngine/NcnnEngine/TilingEngine/RecoveryChain/ModelManager) | Implemented as: C ABI + `engine_ncnn.cpp` + `tile_layout`/`feather_blend` + recovery loop + `ModelManager`; OnnxEngine = documented extension point | 10/10 × 5 models + 46/46 |
| 2.1 | Stable C ABI; no C++ across FFI; init/load/run/progress/cancel/errors/cleanup | `realscale.h` v2 | ABI tests + wrong-scale/cancel tests |
| 2.2 | No model claimed supported until inference succeeds | matrix lists only tested models | `model-compatibility-matrix.md` |
| 3 | Model integration: lightweight first, official sources, licensing | compact SRVGG family primary; x4v3-general conversion deferred (documented) | models.md + matrix |
| 3.1 | Model mgmt: registry, version, size, license, source, progress, verification, install status, deletion | `ModelManager` + registry.json (SHA-256 recorded from official release) | registry tests |
| 3.2 | No bundled weights; consent; offline after download | download flow + onboarding consent + app-support storage | models screen + tests |
| 4A | Image inspection: dims, ratio, format, alpha, size, EXIF, unsupported handling | `ImageService.inspect` + EXIF parser | metadata tests |
| 4A.2 | EXIF orientation before inference; preserve aspect/rotation | orientation applied in decode; oriented dims used in planning | EXIF round-trip tests |
| 4B | Device profiling: RAM, version, ABI, cores, storage, Vulkan; no invented specs | MethodChannel + honest fallbacks | device profile tests |
| 4B.2 | Tiers guide tile/model/concurrency/fallback | `PipelinePlanner` + tier table | planner tests |
| 4C | Planner: dims, scale, model, memory, tier, tiles, overlap, output format, compression; recommend safer config | `PipelinePlanner.plan` + saferAlternative | planner tests |
| 4D | Upscale Mode: preserve original, temp working copy, JPEG 90–96, downsampling explicit+separate, lossless when needed, report actual dims/size, never claim compression improved resolution | JobManager + planner + results facts | lifecycle tests |
| 4E | SR Mode: no lossy processing, no silent downsampling, tiling, suitable format, honest alternative when unsafe | planner SR branch + PNG default + refusal message | planner tests (SR) |
| 5 | TilingEngine: tile size, model-aware overlap, context, edges, odd dims, padding, seam-free, normalized weights, output consistency, memory-aware reduction, error recovery | native tiling + blending + recovery | 17 native tests + seam measurements |
| 5.2 | Recovery: record failure, release resources, reduce tile, retry, stop safely; no infinite loops; never return partial as success | engine recovery loop | injection test 10/10 |
| 5.3 | Seam tests: boundaries, corners, edges, overlaps, odd, tiny, large, full-vs-tiled | native + Dart seam suites | max 7/255 |
| 6 | End-to-end integration: import (gallery/camera/file/permissions/unsupported/large), processing screen (real stages/tile progress/elapsed/ETA/cancel/errors), results (compare, dims, sizes, duration, model, save), storage (predictable folder, duplicates, insufficient storage, never overwrite original) | full flow implemented | lifecycle + widget + integration tests; storage tests |
| 7 | Preserve Paper-Crafted design; polish processing/results; real previews; respect reduced motion | design system + screens | widget test + code review |
| 8 | Ponytail: recheck upstream; apply YAGNI etc.; do not integrate at runtime | verified v5.3.3 (agent discipline skill, MIT); principles applied; not integrated | AGENT_RULES §8 |
| 9 | Tests: planner, device, metadata, registry, lifecycle, storage, recovery, widget; native ABI/tiles/blending/boundaries/lifecycle/engine; integration incl. original-unchanged; never label mocked as real | all present; real vs mocked distinctly named | 46/46 + 30 native runs |
| 10 | Build strategy: measure disk, minimal toolchain, arm64 first, keep web green, document exact blocker if APK impossible | arithmetic in BUILD.md; web ✓; APK blocked+documented | BUILD.md |
| 11 | Docs: maintain 8 + add 5 new | all 18 written (13 maintained + 5 new) + this report | docs/ |
| 12 | Completion criteria 1–11 | see PROMPT2_REPORT.md matrix | report |
| 13 | Final report + checkpoint ZIP (not release ZIP) | PROMPT2_REPORT.md + download/ checkpoint | deliverables |

## Prompt 3 traceability (final)

| # | Requirement (Prompt 3) | Status | Evidence |
| --- | --- | --- | --- |
| 1 | Restore + verify actual project (do not trust reports) | done — full audit: analyzer/tests re-run, checkpoint cross-checked, every Prompt 2 claim re-verified before building on it | session log; QA-REPORT |
| 2 | Android device compatibility (runtime detection, memory-aware, storage checks, thread selection, model compat, Vulkan checks, safe tiles, load-failure recovery, conservative fallback ladder, no silent quality downgrade) | implemented; fallback ladder: verified CPU → smaller tiles → lower threads (tier policy) → CPU fallback (GPU opt-in) → smaller model (registry roles) → honest refusal (planner) | DEVICE-COMPATIBILITY.md; planner tests |
| 3 | Compact model investigation: official source, checkpoint structure, conversion path, or documented retention of verified models | investigated to the artifact level (quantization-aware keys found); conversion blocked by disk; recipe + hashes recorded | model-compatibility-matrix.md |
| 4 | Vulkan: investigate, keep CPU functional, document tested GPU configs | wired opt-in with bounded CPU fallback; CPU verified; no GPU configs tested (none available) — stated plainly | native-engine-design.md; KNOWN-LIMITATIONS |
| 5 | Complete processing experience (formats, EXIF, large/odd images, transparency, storage/MediaStore/share/duplicates/space/cleanup/recovery; never lose/overwrite originals) | implemented + tested (incl. byte-identical original after jobs) | QA-REPORT §6; prompt3 tests |
| 6 | Complete UX (home/import/modes/processing/results/model manager/settings; no decorative buttons) | refined on the Paper-Crafted system; every control wired | features/ screens |
| 7 | Performance review + benchmarks (small/medium/large, single/multi-tile; dims, model, backend, tiles, threads, time, memory, output size, recovery events) | measured matrix + encode stage + memory behavior; no real-time promises | PERFORMANCE-REPORT.md |
| 8 | Security/privacy (no upload, no analytics, explicit downloads, hashes, temp cleanup, local processing) | reviewed + recorded; manifest audited via aapt2 | QA-REPORT §7 |
| 9 | Comprehensive QA (native + flutter + integration; original unchanged; cancel ≠ success; regression; mocks separated) | all green; regression policy honored (web-stub bug fixed, not skipped) | QA-REPORT |
| 10 | Android build: inspect env, minimal toolchain, arm64 first, debug+release APKs, no fabricated builds, signing documented | release APK built+verified; debug APK skipped for disk (documented); signing procedure written | BUILD.md; INSTALLATION.md |
| 11 | Final source ZIP (complete, organized, verified, checksummed, no SDKs/caches/weights-by-policy) | built + verified (count/size/integrity/SHA-256) | PROMPT3_REPORT.md |
| 12 | Final documentation (12 required docs) | all present (4 new + 8 updated, plus the extended set) | docs/ |
| 13 | Final report with verification-level distinctions | PROMPT3_REPORT.md + session reply | report |
