# Model research, licensing and management

## Research summary (Prompt 2)

Primary target from the brief: **Real-ESRGAN x4v3 compact**
(`realesr-general-x4v3`, SRVGGNetCompact). Verified findings:

- The official release (v0.2.5.0, 2022-04-24) publishes the **PyTorch
  weights** (`realesr-general-x4v3.pth`, 4.66 MB) but **no ncnn-format
  files** for the general x4v3 variant.
- The official ncnn-format models in that release are:
  `realesrgan-x4plus` (33.4 MB), `realesrgan-x4plus-anime` (8.9 MB),
  `realesr-animevideov3-x2/x3/x4` (1.25 MB each — same SRVGGNetCompact
  architecture family as x4v3, the "v3" compact generation).
- Converting the general x4v3 `.pth` → ONNX → ncnn would require a
  local PyTorch toolchain (~2 GB) and the ncnn converter tools; viable
  follow-up for Prompt 3, deferred rather than half-done (see
  `model-compatibility-matrix.md`).

**Decision:** integrate the official ncnn-format models now — verified
from the authoritative source — and treat the general-x4v3 conversion as
a tracked follow-up. One genuinely working pipeline beats three
unverified conversions.

## Registry (assets/models/registry.json)

All from the official release package
`realesrgan-ncnn-vulkan-20220424-ubuntu.zip`
(v0.2.5.0), all verified by REAL inference tests in this environment:

| Model | Scale | Weights | Min RAM | Role |
| --- | --- | --- | --- | --- |
| realesr-animevideov3-x4 | 4× | 1.25 MB | ~500 MB | compact default (low/balanced tiers) |
| realesrgan-x4plus-anime | 4× | 8.9 MB | ~1 GB | illustration quality step-up |
| realesrgan-x4plus | 4× | 33.4 MB | ~1.5 GB | flagship photographic (high tier) |

SHA-256 values are recorded in the registry from the official package at
integration time (upstream publishes no separate checksum file). Every
install re-verifies against these recorded values — this protects
against corruption and tampered mirrors, and is honestly labeled
`recorded-from-official-release` rather than claimed as
upstream-published.

## Licensing

- Real-ESRGAN (code and released models): **BSD 3-Clause** —
  https://github.com/xinntao/Real-ESRGAN/blob/master/LICENSE
- ncnn: **BSD 3-Clause** (Tencent).
- Redistribution-compatible: yes, with license text attribution —
  weights may be offered for download (we additionally choose consent
  gating and no APK bundling).
- **Not integrated — restricted licenses:** CodeFormer (weights
  non-commercial), Real-CUGAN (weights have separate terms), waifu2x
  (MIT code but model redistribution terms vary by model version),
  SwinIR (research-heavy, too large for low-resource targets). None are
  wired in; none are claimed as supported. Revisit only with a verified
  license review.

## Download & storage flow

1. User opens Models screen or starts a job needing a missing model.
2. Consent dialog (once, remembered) states size + source + verification.
3. `ModelManager.download`: streamed GET from the official GitHub
   release → byte-counted progress → zip extract (both files) → SHA-256
   compare → write to app-support/models.
4. Failure paths: HTTP error, missing file in package, checksum
   mismatch — each reported; a failed install never leaves usable
   corrupted weights.
5. Deletion removes both files and frees the status.

## Follow-ups for Prompt 3

- Convert `realesr-general-x4v3` to ncnn (torch CPU → ONNX →
  onnx2ncnn/ncnnoptimize) and add fp16 optimized variants of all
  models; verify with the same test matrix.
- Resume support for interrupted downloads (range requests).
- Optional per-model "preferred tile" override informed by on-device
  benchmarks.

## Prompt 3 update

- Host benchmark data for all three registry models (small/medium/large
  inputs, single- vs multi-tile): see `docs/PERFORMANCE-REPORT.md`. The
  tier-based defaults (compact for low/balanced) are validated by
  measurement (compact is 25–40× faster than the flagship here).
- Downloads are now **streamed and resumable** (HTTP Range; package
  never fully in RAM; atomic install after verification) — tested in
  `test/prompt3_features_test.dart`.
- `realesr-general-x4v3`: the official `.pth` (4.89 MB,
  SHA-256 `8dc7edb9…ce96292`) was obtained and inspected; it contains
  quantization-aware keys, so conversion requires the official dequant
  preprocessing + a local PyTorch toolchain that exceeded the sandbox
  disk budget. Deferred with full rationale and a conversion recipe in
  `docs/model-compatibility-matrix.md` — not silently dropped, and no
  unverified third-party conversion was used.
