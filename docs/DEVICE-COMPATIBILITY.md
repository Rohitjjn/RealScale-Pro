# Device compatibility

## Capability detection — what is read, and what is never invented

| Signal | Source | Fallback when unavailable |
| --- | --- | --- |
| Total / available RAM | Android `ActivityManager.MemoryInfo` via MethodChannel (`realscale/device`) | null → tier `balanced`, budget 2.8 GB × 0.7 |
| CPU cores | `Platform.numberOfProcessors` (always available) | — |
| CPU ABI | `Build.SUPPORTED_ABIS[0]` | `Platform.version` string |
| Android version | `Build.VERSION` | null |
| Vulkan | real probe: `System.loadLibrary("vulkan")` in Kotlin | false |
| Free storage | Android: `StatFs` on the Pictures volume via the device channel (Prompt 3, done); other platforms `df`; −1 = unknown |

No GPU model names, no invented RAM figures, no fabricated benchmark
numbers. The Home screen displays exactly what was readable.

## Tiers and their effects

| Tier | Classification | Inference budget (70% rule) | Engine threads | Default tiles (compact/RRDB) |
| --- | --- | --- | --- | --- |
| Low resource | RAM < 3 GB | min(RAM) × 0.7 | ≤ 2 | 96 / 64 px |
| Balanced | 3–6 GB (or unknown) | min(RAM) × 0.7 or 1.96 GB default | ≤ 4 | 128 / 96 px |
| High performance | ≥ 6 GB & ≥ 6 cores, or GPU & ≥ 4 GB | min(RAM) × 0.7 | ≤ 6 | 160 / 128 px |

Unknown RAM degrades to `balanced` — documented, conservative, and
surfaced in the UI as "RAM not readable".

## How the pipeline adapts

1. **Tile size** — tier default; halved under memory pressure until the
   peak estimate fits the budget (floor 32 px).
2. **Model choice** — compact SRVGG for low/balanced; flagship RRDB only
   for high-performance devices in SR mode.
3. **Concurrency** — the engine processes tiles sequentially by design
   (blending requires ordered accumulation; memory predictability beats
   speed on low-end devices); ncnn internal thread count adapts by tier.
4. **Fallback behavior** — OOM-class failures trigger bounded tile
   reduction (native, verified by failure injection). GPU is opt-in
   (Prompt 3): the release APK ships a Vulkan-capable ncnn build; when
   the user enables the experimental GPU setting AND the device exposes
   Vulkan, inference runs on GPU with a bounded mid-run GPU→CPU
   fallback. The CPU path — the verified default — is always compiled
   in and never depends on Vulkan.
5. **Refusal** — when no safe configuration exists: SR mode refuses with
   an explanation; Upscale mode offers an explicit downsample target
   that the user must accept.

## Where results live (Prompt 3)

| Platform | Primary store | Gallery-visible copy |
| --- | --- | --- |
| Android 29+ | app documents `RealScale-Pro/` | `Pictures/RealScale-Pro` via MediaStore (no permission) |
| Android < 29 | app documents `RealScale-Pro/` | app-external Pictures dir + media scan (no permission requested) |
| Other | app documents `RealScale-Pro/` | — |

The original image is never modified; a failed gallery export is
surfaced honestly and never removes the app-storage copy.

## Verified vs not

Verified in this environment: tier classification logic (unit tests),
budget math, tile adaptation, planner agreement with native estimates,
sequential-tile inference, recovery, gallery export + share + StatFs
channels (mock-channel tests), download resume. NOT yet verified:
on-device Android RAM/thermal behavior (no physical device), Vulkan
GPU execution (no GPU on the host — detection is compiled in, execution
is opt-in and labeled experimental).
