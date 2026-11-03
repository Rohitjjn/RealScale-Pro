# RealScale-Pro — Rules for AI Agents

Version 1.0 · 3 November 2026 · Binding for every agent that touches this repo.
Read this file fully before writing any code. When this file and any other file
conflict, this file wins; when this file and `prd.md` conflict, `prd.md` wins.

## 1. Context: who you work for

The owner is a **non-coder**. They cannot read code, run terminal commands they
don't understand, or judge technical trade-offs. Consequences:

- Explain decisions in plain English, with a recommendation, not a lecture.
- If a real decision is needed, present 2–3 options and recommend one.
- Never ask the owner to do anything you can do yourself (install tools, run builds).
- Produce runnable results and test them yourself before reporting success.

## 2. Golden rules (never violate)

1. **Reuse first, write last.** Before writing any module, search GitHub / pub.dev
   for a maintained solution. Report 2–3 candidates (license, last release, fit)
   to the owner in plain language and pick the best fitting one. Log every reused
   component in `licenses.md` (name, version, license, URL, where used).
2. **Permissive licenses only.** MIT, BSD, Apache-2.0, MPL-2.0 are allowed.
   GPL / AGPL / CC-BY-NC or any non-commercial code is **blocked** unless the owner
   explicitly approves it in writing (chat message is fine).
3. **Pin every dependency.** No floating versions. A package with no release in
   18 months needs owner approval.
4. **No video code. Ever.** No video picker, library, permission or processing
   code. The Video Upscale card is UI-only: a lock icon, "Coming soon", and a
   bottom sheet with a Close button.
5. **Never crash.** Handle out-of-memory, disk-full, corrupt files, denied
   permissions and driver failures gracefully. Follow the recovery chain in
   prd.md Section 8.5. No raw error screens, no raw stack traces for the user.
6. **Honest progress only.** Percentages, ETA and the tile-fill animation must
   reflect real work. Fake progress animations are forbidden.
7. **Private by default.** No accounts, no cloud processing, no image upload.
   Network is used only for model downloads and manifest checks.
8. **Originals are never modified.** All work happens on copies; temp files are
   deleted after every job, on cancel, and at app start.

## 3. Technology rules

- One Flutter codebase (Dart, null-safe) for Android, iOS, Windows, macOS, Linux.
- Material 3 design system; state management: Riverpod; navigation: go_router.
- All AI inference runs in the native layer via FFI, on background threads.
  The UI thread must never block. UI targets 60 fps.
- All decision rules (model choice, tile size, limits) live in the **Pipeline
  Planner** service, driven by data files — never hard-coded in widgets.
- The engine communicates with the UI through typed result objects and streams
  (progress events). No engine code may import Flutter UI libraries.

## 4. Code quality rules

- Dart: `flutter analyze` must pass with **zero errors and zero warnings** before
  any task is marked done.
- Format all code (`dart format`). No dead code, no commented-out blocks, no TODO
  left silently — TODOs must be listed in the phase report with an owner decision.
- One feature per commit. Commit messages: `type(scope): summary`, types:
  feat, fix, chore, docs, test, refactor.
- Comments explain *why*, not *what*. Public classes and services get doc comments.
- Localize every user-visible string from day one (English file in `lib/l10n/`;
  Hindi is added in Phase 4). No hard-coded strings in widgets.

## 5. Git rules

- Work in small commits, one logical change each, after every task in `phases.md`.
- Never commit secrets, keys, or generated build output. `.gitignore` must cover
  build/, .dart_tool/, downloaded models, and local config.
- Never rewrite history or force-push. Breaking changes get a changelog entry.
- Every phase ends with a tag: `phase-1`, `phase-2`, ...

## 6. Testing rules

- Each task in `phases.md` lists its test. Run it before moving to the next task.
- After each phase, run the full `qa-checklist.md`. Every item must be checked
  pass/fail; failures are fixed or explicitly escalated to the owner.
- The agent documents how it tested (emulator model, device profile, steps) in
  the phase report. "It compiled" is not a test.

## 7. Documentation rules (mandatory, every phase)

- After every meaningful change: add an entry to `changelog.md` (date, phase,
  what changed, why).
- At the end of every phase: write `reports/phase-N.md` using the template in
  `agents.md`. Phase 2+ must begin by reading the previous phase's report.
- Keep `licenses.md` current the moment a new dependency is added.

## 8. Definition of done (per task)

1. Code written and formatted; `flutter analyze` clean.
2. Task's test from `phases.md` performed and passed.
3. Commit created with a proper message.
4. `changelog.md` updated.

## 9. Definition of done (per phase)

1. Every task in the phase is done per Section 8.
2. Full `qa-checklist.md` run for the phase's scope; failures fixed.
3. `reports/phase-N.md` written and complete.
4. Plain-English summary delivered to the owner: what works, how to try it,
   what is still missing, what is recommended next.

## 10. Escalation rules

Stop and ask the owner (in plain language, with options) when:

- No maintained, correctly licensed reuse candidate exists for a module.
- A PRD requirement appears impossible on the target hardware after real attempts.
- A license cannot be verified for a model or package that matters.
- Any change would modify a decision written in `prd.md` (Section 13.4 open
  decisions are the exception: the agent may recommend, owner decides).

Everything else: make the reasonable choice, document it in the changelog and
the phase report, and keep going.
