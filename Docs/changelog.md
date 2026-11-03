# RealScale-Pro — Changelog

Running log of every change, written by the agents (rules.md §7). Newest first.
Every entry: date, phase, what changed, why (one or two lines, plain English).

---

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
