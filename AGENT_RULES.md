# AGENT_RULES.md — Persistent engineering rules

These rules bind every prompt/session working on RealScale-Pro. They were
(re)established in Prompt 2 after the workspace reset; they carry forward
to Prompt 3 unchanged.

## 1. Honesty is the highest-priority rule

- Never fabricate: builds, tests, progress percentages, model availability,
  checksums, device capabilities, or processing results.
- A claim must be backed by a command that ran and produced the stated
  output in THIS environment. If it did not run here, it is marked
  "not verified in this environment".
- A model is "supported" only after a real inference test succeeded and
  produced a verifiable output image.
- Progress UI is driven by real processing events (native tile callbacks,
  byte counters, phase transitions) — never timers or fake increments.
- ETA appears only when measured per-tile timings make it meaningful.
- Partial results are never reported as success. Cancelled is cancelled,
  failed is failed, with the real reason.
- Quality (compression) and dimensions (pixels) are different parameters;
  never confuse them in code, UI text, or docs.

## 2. Workspace continuity

- The project lives at `/home/z/my-project/realscale-pro/` (source) with
  toolchain/shared assets at `/home/z/my-project/tools/` (Flutter SDK),
  `/home/z/my-project/third_party/` (ncnn source+build, stb) and
  `/home/z/my-project/models/` (verified model store + SHA256SUMS.txt).
- Background processes are killed when a tool call returns: run long builds
  in foreground chunks (they are incremental/resumable).
- At the end of every prompt: regenerate the checkpoint ZIP in
  `/home/z/my-project/download/` (NOT the final release ZIP until Prompt 3
  sign-off).
- If the workspace was reset again: say so plainly in the report, rebuild
  from this documentation set, and never claim prior-session artifacts
  exist without checking the filesystem first.

## 3. Dependency policy

- Only official upstream sources (Tencent/ncnn, xinntao/Real-ESRGAN,
  pub.dev, nothings/stb). Record source URL, license, version, purpose for
  every dependency in `docs/dependency-inventory.md`.
- Never execute untrusted scripts from third parties.
- Do not add a dependency when the standard library suffices (YAGNI,
  see §8).
- Removing a dependency requires checking storage/build cost before
  re-adding.

## 4. Model policy

- Weights are never bundled into the APK. Download on explicit consent,
  verify SHA-256 against values recorded from the official release,
  store app-privately, allow deletion.
- Checksums are recorded-at-download (upstream publishes no checksum
  files); this is stated openly, and verification still protects against
  corruption/tampering of stored copies.
- License must permit redistribution (BSD-3 currently). Anything more
  restrictive (e.g. CodeFormer weights) requires explicit clearance and is
  NOT integrated by default.

## 5. Native code rules

- The C ABI (`native/include/realscale/realscale.h`) is the ONLY contract
  across the FFI boundary. No C++ types cross it. ABI version is checked
  at engine creation.
- Caller owns input buffers; native owns outputs (freed via `rs_image_free`).
- Test hooks (`RS_TEST_HOOKS`) exist only in test builds; production
  Android builds never define them.
- Tile layout/blending invariants are enforced by tests: full coverage,
  no slivers, exact constant-field reproduction, bounded seam deviation.
- Recovery: only OOM-class failures retry (tile halving, floor 32px,
  bounded attempts). Deterministic errors fail fast with the real cause.

## 6. Testing rules

- `flutter analyze` must be clean before finishing.
- Every test that needs the native library must skip VISIBLY (with the
  reason) when it is absent — never silently pass.
- Real-inference tests and mocked tests are named distinctly; a mocked
  test never claims to verify AI.
- Keep the production `.so` (`build/native/`) and the test-hook build
  (`build/native-tests/`) separate.

## 7. Environment constraints (updated end of Prompt 3, 2026-11-06)

- Debian 13, 2 CPUs, 3.9 GB RAM, ~10 GB rootfs (managed down to ~0.9 GB
  free with the full Android toolchain present), no sudo, Java 21 JRE
  system-wide, pip-installed CMake/Ninja in `/home/z/.venv/bin`.
- Android toolchain now staged in-workspace (see BUILD.md): Temurin JDK
  17 at `tools/jdk17`, SDK at `tools/android-sdk` (platform 36,
  build-tools 36.0.0, cmake 3.22.1, NDK 28.2.13676358), Gradle caches
  in `~/.gradle`. The release APK **was built** with these.
- Network: github.com/pub.dev/storage.googleapis.com reachable (~6 MB/s);
  `repo.maven.apache.org` rate-limits (HTTP 429) under Gradle's parallel
  requests — local mirror workarounds documented in BUILD.md, never
  committed.
- **Build-dir reaping:** processes spawned by a tool call can be killed
  when the call returns, and Gradle removes outputs of interrupted
  builds. Consequences: (a) never rely on `build/` surviving between
  calls — keep host test binaries in `third_party/rs-host-build`;
  (b) copy release artifacts into `download/` in the SAME command that
  produces them.

## 8. Ponytail-informed development discipline

[Ponytail](https://github.com/DietrichGebert/ponytail) (verified
2026-11-06: v5.3.3, MIT, an AI-agent development-discipline skill —
"makes your agent think like the laziest senior dev in the room"; it is
NOT an image-processing library and has no runtime role here). We apply
its principles, not its code:

- YAGNI — no speculative features or dependencies.
- Minimal abstraction — one engine backend (ncnn) behind one small
  interface; OnnxEngine remains a documented extension point, not dead
  scaffolding.
- Focused, small, verifiable steps — every claim above is tied to a test
  or a command.
- No runtime integration of Ponytail (it is not an image-processing
  library and integrating it would serve no purpose).

## 9. Process per prompt

1. Audit the workspace; read this file, CHANGELOG, ROADMAP and the
   previous report first.
2. Re-check environment facts (disk/RAM/SDKs) — never assume.
3. Plan with a TODO list; update it as work proceeds.
4. Implement; run analyze + tests; fix until green.
5. Update every affected doc; append to CHANGELOG.
6. Regenerate the checkpoint ZIP; write the completion report with the
   honest status matrix.
