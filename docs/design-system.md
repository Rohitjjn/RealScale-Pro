# Design system — Paper-Crafted language

Implemented in `lib/core/theme.dart` + shared components
(`PaperCard`, `PaperChip`, `PaperButton`).

## Foundations

**Light (paper)**

| Token | Value |
| --- | --- |
| Background | `#F7F3EC` warm ivory |
| Surface | `#FCFAF5` / alt `#EFE9DE` |
| Border | `#DCD3C3` hairline |
| Ink | `#2B2723` primary / `#6B6257` secondary |
| Accent | `#B4552D` terracotta (restrained use) |
| Success / Warning / Error | `#3E6B4F` / `#9A6B2F` / `#9C3D2E` |

**Dark (graphite)**

| Token | Value |
| --- | --- |
| Background | `#211F1D` |
| Surface | `#2B2825` / alt `#35312D` |
| Border | `#45403A` |
| Ink | `#EDE7DD` / `#A69C8E` |
| Accent | `#D07A50` |

## Typography (editorial)

- Display/headlines: serif-weight 600, 30/22 px, tight leading (1.15).
- Titles 16.5 px w600; body 14.5 px/1.5; captions 12.5 px with +0.2
  tracking; monospaced numerics for measurements (tile counts, MB, %).
- Font families map to system defaults in this build (`SansTools`,
  `SerifTools`, `MonoTools` aliases); bundling the chosen typefaces is a
  Prompt 3 polish item (kept as aliases so it is a one-line swap).

## Components

- **PaperCard** — surface + hairline border + 14 px radius; selected
  state uses the accent border (mode cards).
- **PaperChip** — pill labels for statuses (`Low resource`, `Installed`,
  `37%`).
- **PaperButton** — filled (accent) / outlined; 12 px radius; no
  elevation (paper, not plastic).

## Motion

- Intentional and small: 200 ms ease-out page transitions, draggable
  before/after divider.
- `MediaQuery.disableAnimations` respected (onboarding disables
  PageView physics and shortens transitions to zero).
- No decorative animations on processing surfaces — the screen shows
  real progress instead.

## Content principles in UI

- Never claim what did not happen: progress is native-driven; ETA only
  when measured and stable; "recovered with smaller tiles" is shown when
  it happens; engine unavailability on web is stated, not hidden.
- Quality and dimensions are always separate facts in the plan dialog
  and results screen.
