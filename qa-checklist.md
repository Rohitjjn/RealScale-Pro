# RealScale-Pro — QA Checklist

Version 1.0 · 3 November 2026 · Implements prd.md Sections 3.6, 4.7, 5.7, 7.4, 13.2.
The Builder agent runs the relevant sections after every phase and records
pass/fail per item in `reports/phase-N.md`. "Not run" must say why.

## 1. Device matrix

| Class | Profile | Emulation note for agents |
| --- | --- | --- |
| Android Light | 2 GB RAM, budget CPU | Android emulator with 2 GB RAM / low-memory AVD; declare exact AVD in report |
| Android Light+ | 4 GB RAM | AVD 4 GB |
| Android Powerful | 8 GB+, flagship-class | Physical device if available, else declare honestly |
| Windows Light | 4 GB, integrated GPU | Dev machine with capped memory if possible; declare specs |
| Windows Powerful | 16 GB, dedicated GPU | Declare specs |
| Phase 4 | iPhone, Mac, Linux | Real hardware required; owner provides |

## 2. Test image set (prd §13.2)

Old low-res photo · noisy night photo · portrait with several faces · anime
illustration · 24 MP camera photo · screenshot with text · PNG with transparency ·
5 MB JPEG (must be quality-90 re-encoded) · 1.5 MB JPEG (must be untouched).

## 3. Phase 1 checklist

### Onboarding (prd §3)
- [ ] Cold start: no white flash; splash ≤1.5 s; Home interactive <1 s after splash
- [ ] Guide: 4 slides, indicator, Skip works, "Get started" lands on Home
- [ ] Guide replayable from Settings
- [ ] Permission primers shown before any system dialog; "Not now" respected
- [ ] Deny photos → Home shows "Allow photo access" card; Gallery/Files tiles still work via system picker
- [ ] "Selected photos only" handled gracefully (Android 14/iOS)
- [ ] Device check completes in 2–3 s and shows friendly tier label
- [ ] Setup: per-model rows with size + status; overall progress with %, MB, speed, ETA
- [ ] Pause/Resume works; **kill app mid-download → resumes without restarting from zero**
- [ ] Mobile data: ask first, Wi-Fi-only default
- [ ] Corrupted download → checksum failure → auto re-download
- [ ] New user reaches Home <3 min on normal Wi-Fi; every-permission-denied still yields a working app

### Home (prd §4)
- [ ] Collapsing header; recents carousel: 30 newest, smooth 60 fps scrolling
- [ ] Each recent card: thumbnail + Upscale button + resolution badge
- [ ] Thumbnail → full-screen viewer with shared-element feel; swipe paging, zoom, swipe-down close
- [ ] Viewer bottom bar: Upscale loads config with that photo; Details sheet; Share; mode chip
- [ ] Import tiles: Gallery / Files / Camera (asks permission on first tap) / More sheet — all reach config
- [ ] Mode selector: Upscale default, persisted; SR shows one-time warning sheet with device-fit chip
- [ ] App options list complete; **Video row shows lock + "Coming soon" sheet; zero video code exists**
- [ ] Empty/loading/permission-denied states all designed and reachable

### Upscale flow — Default level (prd §5, §7)
- [ ] One tap from recent photo to config; with Default selected, **one tap to start**
- [ ] Detection card shows "Detected: Photo/Anime · 2x · Auto" (low confidence shows "Not sure… Change?")
- [ ] 5 MB JPEG → processed via quality-90 temp copy, deleted after job; 1.5 MB JPEG untouched
- [ ] Transparency never compressed; EXIF orientation respected; CMYK/16-bit → 8-bit sRGB
- [ ] Processing: real tile fill, large %, tile counter, ETA within 15% of final, "Estimating..." early
- [ ] Stages labeled; haptics at 25/50/75%; cancel ≤2 s with temp cleanup; keep-awake; background mode (Android notification)
- [ ] Slow-device/battery note appears under throttling
- [ ] Failure path: friendly message + "Try safer settings" retry
- [ ] Result screen: zoom, before/after slider, hold-to-compare, info row, Share, Upscale again (settings kept), New image
- [ ] Auto-save: RealScale-Pro folder per platform; naming rule; formats per prd §7.3; metadata kept; visible in system gallery without extra steps
- [ ] Original file never modified; free-space check message on low storage
- [ ] My Upscales grid: open, share, delete; multi-select

### Quality bars
- [ ] `flutter analyze` zero errors/warnings; `dart format` clean
- [ ] Light + dark themes on every screen; 360x640 small-phone layout; large-text 200% pass
- [ ] All strings localized (no hard-coded text)
- [ ] No crash under low-memory profile; recovery chain engages (tile halving) instead of dying
- [ ] UI stays responsive during processing (60 fps target)

## 4. Phase 2 additions (run with Phase 1 items)
- [ ] Manual: every catalog model listed with correct installed state; inline download → auto-select
- [ ] Heavy models flagged with confirmation in Upscale Mode
- [ ] Scale chips 2x–5x with live output size + time line; 5x notes extra resize step
- [ ] Face toggle: detector hint, download-with-progress on first enable, pipeline = upscale → restore faces → blend
- [ ] Model Manager: packs, statuses, storage bar, filters, delete-fallback-to-auto
- [ ] Download interruption resume verified for model downloads

## 5. Phase 3 additions
- [ ] Advanced: every option changes behavior (spot-check each); tile presets never exceed device max
- [ ] Super Resolution: original never compressed; feasibility green/amber/red correct on test images
- [ ] Preview crop <30 s on mid-range; streamed output; JPEG/WebP side limits respected
- [ ] ONNX engine behind same interface; Ultra models gated to T3; "Coming soon" fallback if integration fails

## 6. Sign-off format (copy into each phase report)

```
## Test results vs qa-checklist.md
| Section | Items | Passed | Failed | Not run (reason) |
| Onboarding | 13 | 13 | 0 | 0 |
| Home | 9 | 8 | 1 | 0 — see issue #N |
...
Device(s) used: <exact models/AVDs>
```

Failures must link to a repo issue with file references, or be explicitly
escalated to the owner in the report's open-questions section.
