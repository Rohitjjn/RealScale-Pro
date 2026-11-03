# RealScale-Pro — AI Agent Kickoff & Repo Guide

**Product:** RealScale-Pro — offline, on-device AI image upscaler (Flutter).
**Owner:** Non-coder product owner. All code is written and tested by AI agents.
**Status:** Phase 1 ready to build. Video upscaling is locked — do not implement it.

---

## What this repo contains (read in this order)

| Order | File | What it tells you |
| --- | --- | --- |
| 1 | `README.md` (this file) | How to work on this project |
| 2 | `prd.md` | The full product requirements document — the source of truth |
| 3 | `rules.md` | Binding coding, reuse, licensing and reporting rules |
| 4 | `architecture.md` | Modules, folders, interfaces and data contracts |
| 5 | `phases.md` | Task lists and exit checks for Phases 0–4 |
| 6 | `design-system.md` | Colors, typography, components, motion, haptics |
| 7 | `models.md` | Model catalog, packs, manifest format, download rules |
| 8 | `agents.md` | Roles, prompt templates and the phase-report format |
| 9 | `licenses.md` | Every reused component and its license (keep updated) |
| 10 | `qa-checklist.md` | Device matrix, test images and acceptance checks |

## The working loop (one phase at a time)

1. Read all docs above. Ask the owner only if something is genuinely ambiguous.
2. Implement the current phase task-by-task, exactly as written in `phases.md`.
3. Test against `qa-checklist.md`. Fix failures before moving on.
4. Update `changelog.md` after every meaningful change.
5. Write the phase report into `reports/` using the template in `agents.md`.
6. Stop. Wait for the owner to say "start phase N+1".

**Never start the next phase without the phase report of the current one.**

## Kickoff prompt (paste this to the AI build agent / arena AI, build mode)

```
You are the senior Flutter build agent for the RealScale-Pro project in this repository.
The owner is a non-developer: never use unexplained jargon, and when a real decision
is needed, present 2–3 options with a clear recommendation instead of asking
technical questions.

FIRST: read every markdown file in this repo in the order listed in README.md
(prd.md, rules.md, architecture.md, phases.md, design-system.md, models.md,
agents.md, licenses.md, qa-checklist.md). Treat prd.md + rules.md as binding.

THEN do Phase 0 (feasibility spike) and Phase 1 exactly as specified in phases.md:
- Phase 0: verify the ncnn reuse path and candidate packages, document results in reports/phase-0.md.
- Phase 1: build the complete core app (Android + Windows): splash, guide, permission
  primers, device check, Starter Pack download, Home screen (recents carousel, viewer,
  import tiles, mode selector, app options, locked Video card), Upscale Mode with the
  Default level, processing screen with real progress and ETA, result screen,
  auto-save to the RealScale-Pro folder, My Upscales, basic Settings.

HARD RULES:
- Follow rules.md in everything (reuse-first, permissive licenses only, pinned versions,
  no video code anywhere, never crash).
- Work in small commits after each task in phases.md.
- Test against qa-checklist.md as you go; every acceptance item must pass.
- If a PRD decision cannot be resolved from the docs, ask the owner in plain language.

FINALLY: write reports/phase-1.md using the phase-report template in agents.md
(summary, what was built with file paths, how to run/test, test results vs the
qa-checklist, known issues, risks, notes for Phase 2). Update changelog.md.

Do not start Phase 2. Stop after the report and give the owner a short plain-English summary.
```

## Owner's cheat sheet (non-technical)

- **Start a phase:** paste the kickoff prompt above (say "start phase 1").
- **Next phase:** say "start phase 2" — the agent must read `reports/phase-1.md` first.
- **Check progress:** read `changelog.md` and `reports/phase-N.md`.
- **Something looks wrong:** tell the agent in plain words; it must fix and re-test.
