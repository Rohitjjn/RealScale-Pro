# RealScale-Pro — Model Catalog & Manifest

Version 1.0 · 3 November 2026 · Implements prd.md Section 9.
The agent must verify every size, scale and license in Phase 0 before shipping
any model. Sizes below are approximate.

## 1. Catalog

| Model | Best for | Native scales | Engine | ~Size | Speed | Min tier | Pack | License status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Real-ESRGAN general compact (x4v3, denoise variant) | Everyday photos | 4x | ncnn | <10 MB | Very fast | T0 | Starter | BSD-3 code, verify weights |
| Real-ESRGAN anime-video compact (animevideov3) | Anime, fast | 2x,3x,4x | ncnn | <10 MB | Very fast | T0 | Starter | BSD-3 code, verify weights |
| Real-CUGAN (conservative/denoise/pro) | Anime | 2x,3x,4x | ncnn | 5–40 MB | Fast | T1 | Starter (lite), Pro (full) | MIT, verify weights |
| waifu2x (cunet, upconv) | Anime, line art | 2x | ncnn | <20 MB | Fast | T0 | Pro | MIT, verify weights |
| RealSR | Photos w/ compression artifacts | 4x | ncnn | 30–70 MB | Medium | T2 | Pro | Verify |
| Real-ESRGAN x4plus | High-quality photos | 4x | ncnn | ~65 MB | Medium | T1 (slow) | Pro | BSD-3 code, verify weights |
| Real-ESRGAN x2plus | High-quality photos 2x | 2x | ncnn | ~65 MB | Medium | T1 | Pro | BSD-3 code, verify weights |
| Real-ESRGAN x4plus anime 6B | Anime and art | 4x | ncnn | ~17 MB | Medium | T1 | Pro | BSD-3 code, verify weights |
| SwinIR (real-world) | Photos, ultra | 4x | ONNX Runtime | 50–150 MB | Slow | T3 | Ultra | Apache-2.0 reported, verify |
| HAT | Photos, ultra | 4x | ONNX Runtime | 100–400 MB | Very slow | T3 | Ultra | Verify |
| DAT | Photos, ultra | 2x,3x,4x | ONNX Runtime | 100–400 MB | Very slow | T3 | Ultra | Verify |
| GFPGAN v1.4 | Face restoration | face crops | ncnn or ONNX | 300+ MB (smaller FP16) | Medium | T1 | Face | Apache-2.0 reported, verify |
| CodeFormer | Face restoration | face crops | ONNX Runtime | ~375 MB | Medium | T2 | Face (optional) | **Non-commercial — BLOCKED until licensed** |
| OpenModelDB community | Specialized looks | varies | ncnn | varies | varies | varies | Community | Only commercial-friendly, each reviewed |
| Anime-vs-photo classifier, face detector | Internal helpers | n/a | ncnn or ONNX | few MB each | Instant | T0 | Starter | Verify |

**Excluded:** Topaz (proprietary). "Universal Fast V2 / Omni" — source and license
unverified, excluded until verified.

## 2. Packs

| Pack | Contents | Downloads when |
| --- | --- | --- |
| Starter (≤50 MB) | Compact photo + anime models, Real-CUGAN lite, classifier, face detector | First launch (prd §3.5) |
| Pro | x4plus, x2plus, anime 6B, Real-CUGAN full, waifu2x, RealSR | User picks such a model / opens Super Resolution |
| Face | GFPGAN (+ CodeFormer only if licensed) | First time face toggle is switched on |
| Ultra | SwinIR, HAT, DAT | On request, capable devices only |
| Community | Reviewed OpenModelDB models | On request |

Individual one-by-one downloads happen inside the model list.

## 3. Manifest format (remote JSON, prd §9.3)

Full schema with example: see architecture.md §3.3. Requirements:
`id, version, name, engine, pack, minTier, nativeScales, modes, bestFor, speed,
sizeBytes, url(s), sha256, license, notes`. New models can be added without an
app update. HTTPS only; checksum verified after every download; corrupt file →
automatic re-download. Pause/resume/queue/background; Wi-Fi-only default;
free-space checked first; offer update when manifest version is newer.

## 4. Automatic selection table (Default level, prd §8.3)

| Mode | Type | T0–T1 | T2 | T3 |
| --- | --- | --- | --- | --- |
| Upscale | Photo | Real-ESRGAN general compact | same | same |
| Upscale | Anime | Real-ESRGAN anime-video compact | Real-CUGAN | Real-CUGAN |
| Super Res | Photo | x4plus (slow warning on T1) | x4plus | x4plus (Ultra manual-only) |
| Super Res | Anime | x4plus anime 6B | Real-CUGAN | Real-CUGAN full |
| Either + faces | — | GFPGAN v1.4 | GFPGAN v1.4 | GFPGAN v1.4 |

This table is data (assets/data/), consumed by PipelinePlanner — never hard-coded.

## 5. Scale handling (prd §8.4)

Smallest native scale ≥ target, then Lanczos resample. Photo models: native 4x
(some 2x) — 2x: native-2x if installed else 4x→50%; 3x: 4x→75%; 4x: native;
5x: 4x→125% + light sharpening ("Uses an extra resize step"). Anime models
(Real-CUGAN, anime-video): native 2x/3x/4x.
