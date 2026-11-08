# QA checklist — Prompt 2 executed state

## Automated (all green, 2026-11-06)

- [x] `flutter analyze` — No issues found (0 errors / 0 warnings / 0 infos)
- [x] `flutter test` — **46/46 pass**, including:
  - [x] Pipeline planner: format policy, memory adaptation, mode honesty
        (SR never proposes downsample; upscale alternative is explicit),
        quality clamping, model choice, estimate monotonicity
  - [x] Image metadata: format sniffing, header dims, EXIF parse all 8
        orientations + transform round-trip, alpha detection, HEIC
        rejection
  - [x] Device profile: tier classification, budget math, thread caps
  - [x] Model registry: schema, checksum format, recorded-hash
        consistency with the workspace SHA256SUMS.txt
  - [x] Storage: duplicate naming "(n)", atomic writes, no tmp
        leftovers, extension normalization
  - [x] Job lifecycle (scripted fake engine): full phase order, honest
        result facts, original preserved byte-for-byte, failure and
        cancellation states
  - [x] Widget: app shell boots to Home, status strip + mode cards
  - [x] **REAL FFI integration** (production .so + real model): ABI
        info, Dart↔native estimate equality, real end-to-end
        (JPEG→decode→tiled inference→4× result, native progress,
        output≠input), seam bound, cancellation, wrong-scale error
- [x] Native `test_tile_layout` 9/9, `test_feather_blend` 8/8
- [x] Native `test_engine` 10/10 for **five** models (incl. recovery
      injection run 10/10)
- [x] rscli end-to-end on the real 220×220 photo (flagship + compact),
      outputs verified programmatically (dims + PSNR-vs-bicubic sanity)

## Manual checks performed

- [x] Web release build succeeds; engine-unavailable messaging is honest
- [x] Original image untouched after full pipeline (test asserts bytes)
- [x] Checkpoint ZIP regenerated (not the final release ZIP)

## Known gaps (honest, carried to Prompt 3)

- [ ] Android APK not built in this sandbox (disk arithmetic — BUILD.md)
- [ ] On-device ARM performance/RAM matrix unverified
- [ ] Vulkan build path unexercised (compiled CPU-only here)
- [ ] Android free-space reporting uses `df` (host) — StatFs channel pending
- [ ] Downloads are not resumable (restart on failure)
- [ ] Share sheet / save-to-gallery not yet implemented
- [ ] Custom typefaces not yet bundled (system fonts via aliases)
- [ ] EXIF metadata (beyond orientation) not copied to outputs

## Prompt 3 final pass — 2026-11-06

- [x] flutter analyze: No issues found
- [x] flutter test: 59/59 (46 prior + 13 new feature tests)
- [x] Native: 9/9 + 8/8 + 10/10 × 5 models + 10/10 recovery injection
- [x] Web release build: ✓ (regression caught & fixed)
- [x] Android release APK: built, contents/symbols/signature verified
- [x] Benchmark matrix recorded (PERFORMANCE-REPORT.md)
- [x] Security & privacy review recorded (QA-REPORT.md §7)
- [x] Docs: README + 4 new docs + 11 updated; final ZIP verified
- [ ] On-device verification (requires hardware — KNOWN-LIMITATIONS §1)
- [ ] Vulkan GPU execution validation (requires a GPU device)
