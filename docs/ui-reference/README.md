# UI reference: the React/Tailwind prototype

This folder is the ground truth for the Compose port. Nothing here ships in the APK.

| Path | What it is |
| --- | --- |
| `prototype/` | The prototype source from `claude-style-tsx-ui-design (2).zip`, unmodified. Its build output and the generated stub assets (see `.gitignore` inside) are not committed. |
| `screens/light`, `screens/dark` | 79 captured states per theme: `NN-name.png` + `NN-name.layout.json`. See `screens/INDEX.md`. |
| `TOKENS.md`, `tokens.json` | Design tokens extracted from the prototype CSS plus the type scale observed in the captures. |
| `../FIDELITY.md` | The running map TSX → Compose file → reference PNG → diff %. |

## Rebuilding the captures

```bash
cd tools/ui-capture
npm ci
node gen-stub-assets.mjs                 # logo.jpg + photos the zip does not ship (ImageMagick)
(cd ../../docs/ui-reference/prototype && npm ci && npm run build)
LD_LIBRARY_PATH=/path/to/chromium-libs node capture.mjs   # both themes, 79 states
node extract-tokens.mjs && node index-md.mjs
```

`tools/ui-capture/capture.mjs` runs headless Chromium (`@sparticuz/chromium` + `puppeteer-core`),
serves `prototype/dist` read-only and drives the real UI. On this sandbox the bundled Chromium
needs the AL2023 libraries that ship inside the same npm package:
`node -e "…brotliDecompressSync(al2023.tar.br)…" && tar -xf /tmp/al2023.tar -C /tmp/al`, then
`LD_LIBRARY_PATH=/tmp/al/lib`.
