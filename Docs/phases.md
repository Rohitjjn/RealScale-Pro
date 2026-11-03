# RealScale-Pro — Phases & Task Plans

Version 1.0 · 3 November 2026 · Implements prd.md Section 13.1.
Rules: phases ship in order, each ends with a report (agents.md template) saved
to `reports/phase-N.md` and a git tag. The next phase may not start until the
owner approves the previous report. Android and Windows are built first; iOS,
macOS and Linux come in Phase 4.

---

## Phase 0 — Feasibility spike (target: a few days of agent work)

Goal: prove the engine path before any UI exists.

| # | Task | Test / evidence |
| --- | --- | --- |
| 0.1 | Survey reuse candidates for the ncnn wrapper (RealSR-NCNN-Android lineage, realcugan/waifu2x/Real-ESRGAN ncnn-vulkan ports). Record license, last commit, fit in `licenses.md` + report | Table of 2–3 candidates with a recommendation |
| 0.2 | Build a minimal native harness: load Real-ESRGAN compact x4 model through ncnn on CPU and Vulkan; run one 256-px tile; run one real photo end-to-end | Output image is correct (no seams, correct scale); logs attached to report |
| 0.3 | Measure on the two reference profiles (or the closest available dev machines): 2-3 GB Android-class device and 4 GB Windows laptop, CPU and GPU | Record ms/tile, peak RSS; fill the DeviceProfile benchmark fields |
| 0.4 | Verify every Phase 1 pub.dev package from models.md/licenses.md: maintained, license OK, version pinned | licenses.md complete, no unverifiable package remains |
| 0.5 | Decision: confirm ncnn as primary engine, or switch to ONNX Runtime per prd §2.3. Record it | Written decision in `reports/phase-0.md` |

**Exit check:** correct output at acceptable speed on both reference profiles; the
engine decision recorded. Deliverable: `reports/phase-0.md` with measurements.
If the harness work surfaces a blocker (no Vulkan, build failure), this is an
escalation per rules.md §10 — stop and report to the owner.

---

## Phase 1 — Core app, Android + Windows (the big build)

Goal: a new user can install, set up, upscale a photo with Default settings and
share it — no crash on a 2 GB-class device. Scope prd §3, §4, §5 (Default level
only), §7, plus the locked Video card.

| # | Task | Build notes | Test before moving on |
| --- | --- | --- | --- |
| 1.1 | Repo skeleton per architecture.md; app flavors/gates; `flutter analyze` clean in CI | Feature gates from architecture §6 | Empty app builds on Android emulator + Windows |
| 1.2 | Design system: theme tokens, colors, type, radius, motion constants, core widgets (Section 11.7 list, shared parts) | design-system.md is the spec | Widget gallery screen renders light+dark (temporary dev screen is OK) |
| 1.3 | Splash + native splash, ≤1.5 s, theme-matched | flutter_native_splash | Cold start shows no white flash |
| 1.4 | Guide: 4 swipeable slides, indicator, Skip, "Get started"; replayable from Settings | smooth_page_indicator | Completing guide lands on Home; replay works |
| 1.5 | Permission primers (photos/media, notifications) with Allow / Not now, denial flows, "selected photos" handling | PermissionService; photo denied → home card + system picker fallback | Deny-all flow still yields a usable app (prd §3.6) |
| 1.6 | Device check: animated 2-3 s screen; DeviceProfiler detection + micro-benchmark; friendly tier label | Native channel for RAM/GPU where plugins fall short | Profile stored; label shows Light/Standard/Powerful |
| 1.7 | Setup + Starter Pack download: model list rows, per-row + overall progress, speed, ETA, pause/resume, resume-after-kill, checksum verify, mobile-data ask, Wi-Fi-only default | background_downloader; manifest from models.md §3.3 | Kill app mid-download → resumes, never restarts from zero |
| 1.8 | Home screen: collapsing header, recents carousel (30 newest, lazy thumbnails, 60 fps), Upscale-per-card, See all, permission-missing card | photo_manager; shimmer placeholders; cached thumbnails | prd §4.7: interactive <1 s after splash; one tap to config |
| 1.9 | Full-screen viewer: swipe paging, zoom, tap-to-hide controls, swipe-down close, top info bar, bottom actions (Upscale, Details, Share, mode chip) | photo_view/InteractiveViewer | Opens from thumbnail with shared-element feel; Upscale loads config with this photo |
| 1.10 | Import tiles: Gallery (in-app browser; system picker fallback), Files, Camera (permission on first tap), More (clipboard, system picker sheet) | file_picker, image_picker | Each path lands on config screen with the chosen image |
| 1.11 | Mode selector: two cards, Upscale default, Pro badge, selection persisted, first-time SR warning sheet with device-fit chip | PRD §4.5 | Switching modes updates the chip; sheet appears once |
| 1.12 | App options list + locked Video card (lock icon, "Coming soon" bottom sheet, zero video code) | PRD §10.3 | No video import anywhere; tapping shows the sheet |
| 1.13 | Config screen (Upscale Mode, Default level): preview + swap, 3-way level control (only Default active), "Detected: Photo/Anime · 2x · Auto" summary card with Change→Manual link (link may show "coming in Phase 2" state), face toggle OFF with detector hint text only (toggle disabled with note until Phase 2 — or hidden per gate), sticky Upscale button with output size + ETA estimate | JobSpec built via PipelinePlanner; TilingEngine + NcnnEngine from Phase 0; EXIF orientation handled; 2 MB rule in ImageService | 5 MB JPEG → temp quality-90 copy used and deleted; 1.5 MB untouched; exact one-tap run from Default |
| 1.14 | Processing screen: real tile-fill animation, large %, tile counter, ETA (moving average; "Estimating..." first seconds), stage label, cancel with confirm, continue-in-background (Android foreground service + notification), keep-awake, haptics at 25/50/75%, rotating tips, failure → "Try safer settings" retry path | JobManager; honest progress only | Percent/ETA within 15% on test devices; cancel ≤2 s and temp files gone |
| 1.15 | Result screen: viewer with pinch zoom, before/after slider + hold-to-compare, info row (resolution, size, scale, model, time, "Saved to…" chip), Share (system sheet), Upscale again (same image+settings), New image, More menu | share_plus | Saved file appears in system gallery with correct naming (prd §7.3) |
| 1.16 | Auto-save + My Upscales: platform RealScale-Pro folder, naming rule, format rules, metadata kept, free-space check; grid with open/detail/delete | MediaStore on Android 10+; legacy storage ask on ≤9; Windows Pictures\RealScale-Pro | Original never modified; save failure shows clear message |
| 1.17 | Settings (Phase 1 subset): theme (system/light/dark), dynamic color, haptics on/off, keep screen awake, language placeholder (English), guide replay, About + open-source licenses screen (from licenses.md), privacy statement | SettingsService + shared_preferences | All toggles persist across restarts |
| 1.18 | Localization pass: every string in .arb; no hard-coded text | — | `flutter gen-l10n` output used everywhere |
| 1.19 | Error/empty states everywhere: loading shimmer, empty, error, retry — designed, no raw errors | PRD §10.2 | Every screen checked in qa-checklist |
| 1.20 | Full QA run: qa-checklist.md Phase 1 sections on at least: Android low-RAM emulator profile + Windows build; fix all failures or escalate | Record per-item pass/fail in report | Exit checks below |

**Phase 1 exit checks (must all pass; evidence in `reports/phase-1.md`):**
- New user reaches Home in <3 min on normal Wi-Fi; later launches <2 s to Home.
- Denying every permission still leaves a fully usable app.
- Kill during Starter Pack download resumes without restarting from zero.
- From a recent photo, the config screen is exactly one tap; with Default selected,
  upscaling is exactly one tap after the image is chosen.
- Percentage/ETA finish within 15% of estimate; cancel ≤2 s, temp files removed.
- Finished result visible in the system gallery; "Upscale again" keeps settings.
- `flutter analyze` clean; no video code; licenses.md complete.
- QA matrix row "Android Light" and "Windows Light" fully passed (emulator-class acceptable where noted in qa-checklist).

---

## Phase 2 — Choice and faces

Goal: let people choose an audited CNN model and scale, manage model storage and
downloads, restore faces after upscaling, and control the complete Settings set.
The Phase 1 hardware gaps remain open and must stay visible in the Phase 2 report.

| # | Task | Build notes | Test before moving on |
| --- | --- | --- | --- |
| 2.1 | Audit the Phase 2 CNN and face-model catalog, model weights, licenses, sizes and maintained upstreams; keep blocked or unverifiable models unavailable | Reuse first; permissive licenses only; record candidates and decisions in `licenses.md` and the report | Every enabled catalog row has a source, exact version/checksum and permissive license |
| 2.2 | Add typed model catalog, pack, install-state and download-state contracts plus a data-driven bundled catalog | One catalog feeds Manual, Model Manager and planner; malformed entries fail safely | Unit tests parse every row and reject invalid URLs, checksums, tiers and scales |
| 2.3 | Build the ModelManager service over the existing pinned `background_downloader`: install detection, queue, pause/resume, checksum retry, updates, delete, storage accounting and fallback-to-Auto | HTTPS only; Wi-Fi-only default; originals/images never enter network code | Service tests cover installed/not installed/downloading/update/error and delete fallback |
| 2.4 | Replace the placeholder Model Manager with grouped packs, status and size rows, storage bar, Photo/Anime/Face/mode filters, download-all, delete and update actions | Designed loading, empty and friendly error states; license links are catalog-driven | Widget tests cover filters and all row states; owner preview mirrors the screen |
| 2.5 | Implement Manual configuration: image-type chips, every available catalog model, inline download-and-select, recommended/device-fit/heavy badges, and 2x–5x scale chips | Advanced remains Phase 3; blocked/ONNX models may be visible only as clearly unavailable catalog entries | Widget/unit tests verify installed state, auto-select after download and heavy-model confirmation |
| 2.6 | Extend PipelinePlanner and JobSpec for persisted Manual choices, native-scale-to-target resize chains, live output dimensions/megapixels and honest time estimates | Rules remain in data files, never widgets; 5x identifies the extra resize step | Planner tests cover Photo/Anime, tiers T0–T3, scales 2x–5x and unavailable-model fallback |
| 2.7 | Add face detection as a local helper with a typed result and a responsive hint; keep detection off the UI thread | Reuse an audited permissive detector/model; no image upload | Face/no-face/error fixtures return friendly typed states without blocking widgets |
| 2.8 | Add the Face pack download flow and face toggle; first enable shows size/progress and turns on only after verified installation | GFPGAN is default if its weights pass task 2.1; CodeFormer remains blocked | Interrupted download resumes; checksum failure retries; toggle state matches installation |
| 2.9 | Implement the native face pipeline: upscale whole image, restore detected crops, soft-mask blend, emit real `faces` progress and preserve safe cancellation/cleanup | Same engine interface; recovery chain and CPU fallback; no raw native failures | Fixture with multiple faces verifies stage order, dimensions, blend boundaries and cancellation |
| 2.10 | Complete Settings: default mode, compute preference, battery/heat slowdown, rerun device check, output defaults/metadata, Wi-Fi-only, storage/cache, privacy opt-ins, FAQ/feedback and model credits | Privacy options default off; feedback may attach device profile but never an image | Every setting persists; clear-cache preserves installed models; device check can rerun |
| 2.11 | Localization, accessibility and responsive pass for every Phase 2 screen; update the HTML owner preview | English ARB only in this phase; 200% text and 360×640 remain usable | Formatter/analyzer clean; widget tests find no overflow or hard-coded user strings |
| 2.12 | Full Phase 1 + Phase 2 QA and target CI; write `reports/phase-2.md` and tag only if all exit checks genuinely pass | Record real hardware gaps as not run, never as passes | `qa-checklist.md` evidence, Android/Windows artifacts and an honest report |

**Phase 2 exit checks:**
- Every enabled, installed CNN catalog model loads and completes an image through
  the shared engine interface; unavailable or blocked models are clearly labeled.
- Manual lists every catalog entry with correct installed/download state, scale
  chips 2x–5x update output/time, and inline download selects the model afterward.
- Model Manager filters, pack actions, storage accounting, updates and deletion
  work; deleting the selected model falls back to Auto.
- Face detection and verified Face-pack download work; enhancement runs in the
  order upscale → restore faces → soft blend, with honest stage progress.
- Killing an active model download and reopening resumes without restarting.
- Phase 1 quality checks plus every Phase 2 checklist item pass or are explicitly
  escalated with evidence in `reports/phase-2.md`.

## Phase 3 — Pro (summary)

Scope: Advanced level, Super Resolution Mode (no compression, feasibility check
green/amber/red, Preview crop, extra options), ONNX Runtime engine with SwinIR,
HAT, DAT (or catalog "Coming soon" if integration is not clean).
Exit: 24 MP photo upscales on a high-end device without crash; report in `reports/phase-3.md`.

## Phase 4 — Expansion and launch (summary)

Scope: iOS/macOS/Linux builds, Hindi, accessibility pass, store assets, beta,
performance tuning. Exit: store-ready builds; `reports/phase-4.md`.

## Later (not in this version)

Video upscaling (unlock the card), batch, before/after collage, share-to-app.
