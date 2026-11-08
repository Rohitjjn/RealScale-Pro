# PERFORMANCE-REPORT

**Environment:** Debian 13 host, 2 × x86_64 CPUs, 3.9 GB RAM, no GPU.
ncnn pinned commit `09981a0796cc…` (CPU build, Release, fp32 storage).
Date: 2026-11-06. Every number below was measured by a command that ran
in this environment (`tools/benchmark_host.sh`, `tools/bench_encode.dart`;
raw outputs retained in the session log).

> These are HOST figures for engine characterization. Android ARM64
> devices will differ (usually slower per-core, but modern big-core
> phones are competitive with this 2-core host). No on-device
> measurements were possible in this environment — see
> KNOWN-LIMITATIONS.md.

## 1. Inference benchmarks (real end-to-end runs)

Inputs are bicubic resizes of the official 220×220 Real-ESRGAN test
photo (deterministic; used for realistic image statistics, not quality
claims). Threads = 2. `rscli` reads, infers and encodes PNG.

### Compact — realesr-animevideov3-x4 (1.25 MB, default model)

| Input | Tiling | Tiles | Output | Time |
| --- | --- | --- | --- | --- |
| 128×128 | single (1024px) | 1 | 512×512 | 0.1 s |
| 128×128 | 128px | 1 | 512×512 | 0.1 s |
| 512×512 | single (1024px) | 1 | 2048×2048 | 1.8 s |
| 512×512 | 128px | 25 | 2048×2048 | 2.1 s |
| 800×600 | single (1024px) | 1 | 3200×2400 | 3.4 s |
| 800×600 | 128px | 35 | 3200×2400 | 3.1 s |

### Quality — realesrgan-x4plus-anime (8.94 MB, RRDBNet 6B)

| Input | Tiling | Tiles | Output | Time |
| --- | --- | --- | --- | --- |
| 128×128 | single (1024px) | 1 | 512×512 | 1.2 s |
| 128×128 | 128px | 1 | 512×512 | 1.2 s |
| 512×512 | 128px | 25 | 2048×2048 | 26.0 s |
| 800×600 | 128px | 42 | 3200×2400 | 42.5 s |

### Flagship — realesrgan-x4plus (33.42 MB, RRDBNet 23B)

| Input | Tiling | Tiles | Output | Time |
| --- | --- | --- | --- | --- |
| 128×128 | single (1024px) | 1 | 512×512 | 3.3 s |
| 128×128 | 128px | 1 | 512×512 | 3.3 s |
| 512×512 | 128px | 25 | 2048×2048 | 79.9 s |
| 800×600 | 128px | 42 | 3200×2400 | 135.2 s |

### Honest findings

- **Single-tile RRDB inference is impractical at ≥ 512² inputs on
  low-core CPUs:** a single 1024px-tile run of `x4plus-anime` on a 512²
  input exceeded 10 minutes on this host and was terminated. This is a
  measured justification for the planner's tiled-by-default policy
  (96–128px tiles): tiling is not a memory compromise, it is also the
  only practical execution shape for RRDB-class models.
- The compact model is 25–40× faster than the flagship — the tier-based
  default (compact on low/balanced devices) is validated by data.
- No recovery events occurred in any benchmark run (all attempt=1) —
  the memory-recovery path is exercised separately by failure injection
  tests (QA-REPORT.md).

## 2. Output encoding (Dart, the app's real encode stage)

`package:image` pure-Dart encoder (the same code JobManager runs in a
`compute()` isolate), gradient RGBA input:

| Buffer | PNG | JPEG q92 |
| --- | --- | --- |
| 1024×1024 (1.0 MP) | 83 ms | 75 ms |
| 2048×2048 (4.2 MP) | 209 ms | 210 ms |
| 4096×4096 (16.8 MP) | 763 ms | 792 ms |

**Conclusion:** encoding is 100–200× cheaper than flagship inference on
this host (e.g. 0.6 s vs 135 s for an 800×600 → 3200×2400 job). A native
libjpeg/libpng encode path (roadmap item) would improve total wall time
by well under 1% for RRDB models — correctly deprioritized by
measurement, not by assumption. For compact-model jobs on fast devices
encode can be a visible fraction (~15–20% of a 3 s job), so it remains a
future optimization candidate only there.

## 3. Memory behavior

- Peak-memory estimate formula (native `rs_estimate_peak_memory`, mirrored
  in Dart `PipelinePlanner`) drives tile-size selection before any
  allocation; equality across the two implementations is asserted by an
  integration test across 4 configurations.
- Inference runs inside a dedicated worker isolate; the UI isolate only
  polls a shared control block (progress/cancel), so the UI stays
  responsive and no full-resolution copies cross isolates except the
  final encoded bytes.
- OOM-class failures trigger bounded tile-halving recovery (floor 32 px,
  ≤ 3 retries) — verified by failure injection (10/10 with 2 forced
  failures).
- Output accumulation uses a float RGBA accumulator capped by the 1 Gpx
  output safety limit; the planner additionally refuses configurations
  whose estimate exceeds the device budget.

## 4. What was NOT measured here

- Android on-device timings, peak RSS and thermal behavior (no device).
- Vulkan GPU execution (no GPU on the host; the APK ships a
  Vulkan-capable ncnn build with GPU opt-in and CPU fallback).
- Battery impact.
