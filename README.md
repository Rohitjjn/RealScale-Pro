# RealScale-Pro

On-device image super-resolution for Android, rebuilt with a warm-minimalist
Jetpack Compose UI and an on-demand Model Hub — the base APK ships **no AI
models**, only the lightweight NCNN engine binaries.

## Architecture (MVVM + Clean Architecture)

```
app/src/main/java/dev/realscale/pro/
├── core/engine/     NcnnEngine (assets→cache extraction, chmod, `sh` CLI shell),
│                    NcnnCommandBuilder (pure, unit-tested), LanczosResampler (offline fallback)
├── core/database/   Room DB: creations ("Recent Creations") + downloaded model metadata
├── data/models/     ModelCatalog + ModelRepository (HF download, Range resume,
│                    .param magic + SHA-256 verification, filesDir/models storage)
└── ui/              theme (docs/DESIGN.md tokens), components (Before/After canvas,
                     calm progress), screens (Home Studio, Model Hub), StateFlow ViewModel
```

- Heavy inference runs on `Dispatchers.IO`; the upstream C++/NCNN binaries are
  treated as a stable, unmodified execution shell (same `./realsr-ncnn -i … -o … -m <dir> -s N` CLI).
- Models download on demand from `huggingface.co/tumuyan2/realsr-models` into
  `filesDir/models/` and their absolute path is passed to the engine at runtime.
- Classical Lanczos-3 works offline immediately — no binary or model download needed.
- API 26 → 35.

## Upstream

Full upstream source (`tumuyan/RealSR-NCNN-Android`, CLI + legacy GUI) is kept,
unmodified, under `upstream/` for reference. Core binaries
(`realsr-ncnn`, `realcugan-ncnn`, `waifu2x-ncnn`, `srmd-ncnn`, `resize-ncnn`,
`Anime4k`, `libncnn.so`, `libomp.so`, `libc++_shared.so`) are fetched in CI from
the upstream `assets.zip` release and are gitignored — never `models-*`.

## Build

CI (`.github/workflows/build-apk.yml`) runs unit tests, Lint,
`assembleDebug` + `assembleRelease`, verifies no bundled models / APK size,
and uploads the APK artifacts.

Locally: `./gradlew testDebugUnitTest lint assembleDebug` (JDK 17 + Android SDK).
