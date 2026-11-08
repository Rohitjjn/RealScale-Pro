# KNOWN-LIMITATIONS

Honest list of what is NOT done, NOT verified, or approximate in
RealScale-Pro v1.0.0. Anything not listed here is verified to the level
stated in QA-REPORT.md.

## Not verified in this environment

1. **No physical Android device testing.** The release APK was built and
   programmatically inspected (contents, ABI symbols, ncnn markers,
   signature), but never launched on real hardware. On-device
   verification (install, import, inference timing, thermal, the
   Realme Narzo N53 reference profile) requires a device.
2. **No Android emulator testing either** (no KVM / disk in the sandbox).
3. **Vulkan GPU execution untested.** The arm64 engine in the APK is
   linked against the official ncnn Vulkan build and implements GPU
   opt-in with automatic CPU fallback, but this host has no GPU, so:
   - GPU *detection* is compiled in and reports honestly
     (`rs_device_info.gpu_available`, powered by `ncnn::get_gpu_count`).
   - GPU *execution* is marked **experimental**, OFF by default, and has
     never been observed running. No Android GPU configuration has been
     tested.
4. **Benchmarks are host figures** (2-core x86_64 Linux). Android ARM64
   numbers, on-device peak RSS and battery/thermal impact are unmeasured.
5. **API < 29 gallery save** (app-external dir + media scan) is
   implemented but untested — the reference devices run API 29+.

## Known functional limitations

6. **`realesr-general-x4v3` (compact general model) is not shipped.**
   Upstream publishes only PyTorch weights, and the checkpoint contains
   quantization-aware keys (`weightq`/`biasq` — verified by inspecting
   the archive), so a conversion requires the official dequant
   preprocessing plus a local PyTorch toolchain (~1 GB), which exceeded
   the sandbox disk budget. The compact *anime* model (same SRVGGNet
   family) is the verified default instead. Full investigation:
   `model-compatibility-matrix.md`.
7. **Output encoding is pure Dart** (`package:image`). Measured cost is
   small relative to inference (≤ 1% for RRDB models; ~15–20% of a
   compact-model job on a fast device). A native encode path remains a
   future optimization, correctly deprioritized by measurement.
8. **EXIF metadata beyond orientation is not copied** to outputs (the
   output is written upright, so re-applying orientation would be wrong;
   other tags like GPS/camera info are intentionally dropped — privacy-
   conservative default).
9. **Downloads resume only when the server honors HTTP Range.** GitHub
   release redirects do; a server answering 200 to a Range request
   triggers a clean restart (tested).
10. **One heavy job at a time.** Parallel jobs are deliberately not
    supported on low-resource devices (bounded concurrency by design).
11. **Gallery export duplicates the file on Android** (app storage +
    Pictures/RealScale-Pro). This is intentional: the app-storage copy
    guarantees the Results screen and share work even if MediaStore
    fails, and failures are surfaced, never fatal.
12. **Model deletion does not remove gallery exports** (they are user
    files in the shared collection).
13. **Web platform**: no inference. The engine reports itself unavailable
    and the UI communicates this — no degraded fake mode.
14. **Job history is session-scoped**; completed results on disk are
    listable but not re-opened into the compare view after an app
    restart.

## Approximations

15. **Peak-RAM estimate** is a conservative formula (mirrored native ↔
    Dart, test-asserted equal), not a measurement; actual usage can be
    lower.
16. **Checksum provenance**: upstream publishes no checksum file; the
    registry SHA-256 values were recorded from the official release
    package at integration time (labeled as such) and protect against
    corruption/tampered mirrors.
17. **ETA** appears only after ≥ 2 measured tiles with stable timings —
    early in a job there is deliberately no estimate.
