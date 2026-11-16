# UI Reference: Design Prototype & Reference Screens

This directory contains the design-system prototype, extracted tokens, and 79 captured screen states (in both Light and Dark themes) used to build and verify RealScale-Pro's Jetpack Compose UI. Nothing in this directory is bundled into the Android APK.

| Path | Description |
| --- | --- |
| `prototype/` | React + Tailwind CSS design specification source (build outputs and stub assets are git-ignored). |
| `screens/light/`, `screens/dark/` | 79 captured states per theme (`800 × 1720` PNGs + computed `.layout.json` specs). Indexed in [`screens/INDEX.md`](screens/INDEX.md). |
| `TOKENS.md`, `tokens.json` | Extracted color roles, radii, spacing, and typography scale mirrored in `design/Tokens.kt` and `design/Type.kt`. |
| [`../FIDELITY.md`](../FIDELITY.md) | Screen-by-screen mapping between prototype files, Compose screens, intentional product evolutions, and automated test coverage. |

## Regenerating Captures

```bash
cd tools/ui-capture
npm ci
node gen-stub-assets.mjs
(cd ../../docs/ui-reference/prototype && npm ci && npm run build)
node capture.mjs
node extract-tokens.mjs && node index-md.mjs
```
