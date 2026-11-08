# Native engine design

## The C ABI (`native/include/realscale/realscale.h`)

ABI version 2. The complete surface:

| Function | Purpose |
| --- | --- |
| `rs_abi_version` | returns 2; callers assert equality before use |
| `rs_engine_create(opts)` | opaque engine handle; validates ABI version |
| `rs_engine_destroy` | release |
| `rs_engine_load_model(p, b, out_info)` | load ncnn param/bin pair; fills sizes + tile hint |
| `rs_engine_model_info` / `rs_engine_device_info` | capability queries (threads, fp16, GPU) |
| `rs_estimate_peak_memory(w,h,scale,tile)` | planning helper, no allocation |
| `rs_run(engine, in, job, out, cb, user, cancel)` | synchronous tiled run; progress callback; cancel flag polled between tiles |
| `rs_image_free` | frees native-owned output |
| `rs_last_error` | thread-local message for the last failure on this thread |

Rules enforced by design:

- Only POD structs with fixed-width fields cross the boundary; no C++
  types, no STL.
- Inputs are caller-owned and read-only; outputs are native-allocated
  and released with `rs_image_free`.
- A failed `rs_run` NEVER returns a partial image: on error `*out`
  stays NULL.
- Cancellation returns `RS_ERR_CANCELLED`; deterministic failures return
  their real status, not a generic error.

## Execution model (Dart side)

`rs_run` blocks its thread, so it runs in a dedicated worker isolate.
The subtlety: while the worker is inside the FFI call it cannot receive
Dart messages, so progress and cancellation both bypass Dart messaging:

```
UI isolate                    worker isolate                 native
──────────                    ──────────────                 ──────
ControlBlock (calloc) ─────── address passed in job ────────┐
  [0] cancel flag ─────────── written by UI ────────────► polled between tiles
  [1..6] progress  ◄── 120ms poll (transport) ◄── C callback writes ◄─ per tile
result ◄────────── SendPort (TransferableTypedData) ◄──── on completion only
```

The static C trampoline (`_progressTrampoline`) only writes plain
Int32s — safe from any thread. The main isolate's `Timer.periodic`
reads the block: pure transport of native-written values.

## Engine internals (`engine_ncnn.cpp`)

- One `ncnn::Net` per engine; options: `lightmode`, winograd/sgemm on,
  int8 off (SR quality), fp16 storage auto (aarch64 on, else off),
  fp16 arithmetic off (portability).
- Model-aware defaults by weight size: SRVGG compact family
  (bin < 4 MB): tile 128 / overlap 10 / context 2; RRDB family:
  tile 128 / overlap 16 / context 8.
- Per tile: extract context region directly from the input RGBA (zero
  copy), `Mat::from_pixels(RGBA2RGB)`, extractor over blobs `data` →
  `output`, `to_pixels(RGB2RGBA)` into a reusable buffer, then
  `FeatherBlender::accumulate`.
- Output scale is validated on every tile: `out.w == region.w * scale`
  must hold exactly, else a descriptive error (wrong variant detection).
- Safety cap: outputs beyond 1 Gpx are refused (`RS_ERR_DEVICE_LIMIT`).
- Recovery: on `std::bad_alloc`/ncnn failure the tile edge halves
  (floor 32 px, overlap/context proportional), blending restarts on a
  clean accumulator, up to `max_retries` (default 3). Attempt and tile
  size are reported through progress. Deterministic errors return
  immediately.
- Test hooks: `RS_TEST_HOOKS` + `RS_TEST_FAIL_TILES=n` injects
  `bad_alloc` on the first n tiles of attempt 1 (test builds only;
  never defined for production Android).

## Tiling (`tile_layout.cpp`)

- Anchors distributed evenly; the last tile is always full-size and
  anchored at the image edge (no slivers).
- Core = the region a tile must reconstruct; context = core + margin,
  clamped at image borders.
- Per-side actual overlaps recorded per tile (`ov_l/r/t/b`) — they can
  differ from the request after even redistribution, and the blender
  uses exactly these.
- Images smaller than one tile degrade to a single tile.

## Blending (`feather_blend.cpp`)

- Float accumulation buffers (RGBA + weight), smoothstep ramps of width
  `overlap × scale` per side, weight-normalized division at the end.
- Invariant (tested): tile cores cover the image exactly; any overlap
  pattern reconstructs constant fields exactly; gradient deviation ≤ 2.
- Measured against single-tile runs with real models: max 7/255, mean
  ≤ 0.99 — see `inference-test-report.md`.

## Builds

- Host (Linux x64, tests + CLI): `tools/build_native.sh` — links the
  pinned prebuilt `libncnn.a`, OpenMP detected and linked when present.
- Production `.so` for `flutter test`: `build/native/` (no test hooks).
- Test-hook build: `build/native-tests/` (adds `RS_TEST_HOOKS`).
- Android: `android/app/build.gradle.kts` → `externalNativeBuild` →
  `native/CMakeLists.txt`; ncnn via prebuilt static libs
  (`tools/fetch_ncnn_android.sh`); ABIs arm64-v8a, armeabi-v7a, x86_64.
- No-ncnn fallback: `engine_stub.cpp` reports `RS_ERR_UNSUPPORTED` so
  interface-level work stays testable on machines without the backend.

## Vulkan / GPU path (Prompt 3)

- The Android arm64 engine links the official `ncnn-20250503-android-
  vulkan` prebuilt (raw archives, not the imported CMake target — that
  target propagates `-fno-exceptions`, incompatible with the
  exception-based recovery engine; the glslang/SPIRV family is linked
  explicitly, OpenMP statically).
- `RS_COMPUTE_GPU` enables `net.opt.use_vulkan_compute` at engine
  creation only when `ncnn::get_gpu_count() > 0`; otherwise the engine
  silently stays on CPU and `rs_device_info` reports the truth.
- Failure handling: (a) GPU model-load failure → clear + reload on CPU
  once; (b) mid-run GPU inference failure → `fallback_to_cpu()` clears
  the net, reloads on CPU and restarts the tiled pass (bounded once per
  run by `gpu_fallback_used_`).
- Honest status: CPU execution is fully verified on the host; GPU
  execution could not be tested here (no Vulkan device). The setting is
  OFF by default and labeled experimental in the UI.
