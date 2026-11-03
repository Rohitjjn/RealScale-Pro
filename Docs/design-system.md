# RealScale-Pro — Design System

Version 1.0 · 3 November 2026 · Implements prd.md Section 11. This file is the
design brief the Builder agent implements as theme tokens and widgets. Deviations
need owner approval (record in changelog).

## 1. Principles

Photos are the hero · one primary action per screen · feels like Apple Photos,
not a developer tool · motion explains, never slows · social-first polish.

## 2. Color

**Primary palette (recommended, Phase 1 proposal — owner approves):** deep indigo
to violet gradient with one warm highlight.

| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| `primary` | #4F46E5 (indigo-600) | #818CF8 (indigo-300) | buttons, active states |
| `gradientStart` | #4F46E5 | #6366F1 | brand gradient start |
| `gradientEnd` | #7C3AED (violet-600) | #A78BFA | brand gradient end |
| `accentWarm` | #F59E0B (amber-500) | #FBBF24 | highlight chips, ETA, warm moments |
| `surface` | #FFFFFF | #121318 | cards |
| `surfaceVariant` | #F3F4F8 | #1D1F27 | recessed areas, sheets |
| `background` | #FAFAFC | #0E0F13 | screen background |
| `onSurface` | #181A20 | #E7E8EE | primary text |
| `onSurfaceVariant` | #5B5F6B | #A6AAB8 | secondary text |
| `outline` | #E2E4EB | #2C2F3A | hairlines, borders |
| `success` | #16A34A | #4ADE80 | feasibility green |
| `warning` | #D97706 | #FBBF24 | feasibility amber |
| `error` | #DC2626 | #F87171 | feasibility red, errors |

- Optional dynamic color on Android (Material You) overrides `primary`/`secondary`.
- Status colors never carry meaning alone: pair with icon or label (accessibility).
- Gradients: brand header wordmark, primary button background, progress highlights.
  Everything else is tonal surfaces — no heavy shadows (use 0-2 dp elevation).

## 3. Typography

- One clean sans family: **Inter** (bundled; premium, open OFL license). Fallback: system sans.
- Scale (phones): display 32/40 · headline 24/32 · title 18/26 · body 15/22 ·
  label 13/18 · numeric mono for ETA/percent (tabular figures).
- Weights: 400 body, 500 emphasis, 600 titles, 700 the wordmark only.
- Supports text scaling to 200% without truncation (test in qa-checklist).

## 4. Shape & layout

- Corner radius: cards 20 dp, sheets 28 dp, buttons pill (full), chips pill, inputs 16 dp.
- Spacing scale: 4, 8, 12, 16, 24, 32 dp. Generous whitespace; content max width 560 dp on wide screens.
- Touch targets ≥48 dp. Minimum desktop window 800x600; nav rail + two-pane config layout.
- Icons: rounded Material Symbols, weight 400-500, 24 dp.

## 5. Components (build once, reuse everywhere — prd §11.7)

ModeCard · RecentPhotoCard (thumbnail + Upscale button + resolution badge) ·
ImportTile · LevelSegmentedControl (Default/Manual/Advanced) · ScaleChips ·
ModelListRow (installed/downloading/not-installed) · TilePresetChips ·
FaceToggleRow · PrimaryActionBar · ProgressFillView · BeforeAfterSlider ·
PermissionPrimer · DownloadProgressRow · InfoChip · AppBottomSheet.
Each has: light+dark variants, loading/empty/error states, semantics labels.

## 6. Motion

- Durations: micro (selection) 150-200 ms, screen transitions 300-400 ms.
- Easing: emphasized-decelerate for entrances, standard for exits; springs (bouncy,
  low stiffness) on segmented control, chips, cards.
- Shared-element: recents thumbnail → viewer → config preview → result image.
- Skeleton shimmer loaders (no spinners); tile fill animation on processing.
- Respect system reduce-motion: replace transitions with fades; keep the tile fill
  but as an instant-fill-per-tile.
- Target 60 fps on mid-range phones; nothing animates while the engine thread is busy.

## 7. Haptics (prd §11.4)

| Event | Pattern |
| --- | --- |
| Chip/level/mode selection | light selection click |
| Toggle switch | light impact |
| Upscale press | medium impact |
| 25% / 50% / 75% | soft tick |
| Job finished, model installed | success pattern |
| Error/warning | warning pattern |

Follow system haptics setting; off-switch in Settings.

## 8. Screen specs (quick reference)

- **Home:** collapsing large title "RealScale-Pro" + settings icon; recents strip
  with per-card Upscale button and resolution badge; 2x2 import tiles; two mode
  cards (Upscale selected: violet gradient outline; Super Resolution: PRO pill
  badge); grouped app options; Video row shows lock icon + "Coming soon".
- **Config:** image preview rounded 20 dp with swap icon + meta line; sticky bottom
  primary button (gradient, white label, second line "2400 x 1600 · about 3 min").
- **Processing:** image center, soft grid overlay, tiles fill blurred→sharp; giant
  percent + tile counter; ETA line; stage label; Cancel + Continue in background.
- **Result:** full-bleed image, top info chip row, before/after slider pill,
  primary Share button + Upscale again + New image.
- Empty/loading/error: friendly illustration line, one sentence, one clear action.
