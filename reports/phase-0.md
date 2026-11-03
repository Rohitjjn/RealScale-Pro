# Phase 0 Report — Feasibility spike — 3 November 2026

## 1. Summary
The safest reuse path is the official ncnn engine with the official permissively
licensed model projects, not a third-party app. A real ncnn CPU model loaded and
produced a correctly sized 2x image on this 4 GB Linux workspace. The measured
64 px probe took 39 ms of engine time and 74 ms wall time using two CPU threads.
The workspace has no Vulkan driver, Android tools or Windows runner, so Android,
Windows and GPU measurements are honestly pending. The owner chose to continue
Phase 1 while those hardware checks remain pending. Two small permissively
licensed waifu2x fallback models are bundled; other models will be offered on demand
from GitHub Releases.

## 2. What was built
| Feature / module | Main files | PRD section | Status |
| --- | --- | --- | --- |
| Reuse and license survey | `Docs/licenses.md` | §2.1–2.3, §12.4 | Complete |
| Repeatable ncnn CPU harness | `native/spike/CMakeLists.txt`, `native/spike/main.cpp` | §2.3, §8 | Passed on available Linux CPU |
| Bundled fallback data | `assets/models_starter/` | §3.5, §9 | Added, 2.2 MB total |
| Android/Windows/Vulkan profile run | hardware-dependent | §2.3, §13.1 | Not run; hardware unavailable |

## 3. How to run and test it
Install CMake 3.20+, Ninja and a C++17 compiler, then run:

```sh
cmake -S native/spike -B build/spike -G Ninja \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_POLICY_VERSION_MINIMUM=3.5
cmake --build build/spike
build/spike/realscale_ncnn_spike \
  assets/models_starter/scale2.0x_model.param \
  assets/models_starter/scale2.0x_model.bin input.ppm output.ppm
```

The harness intentionally accepts simple PPM test images so engine proof does
not depend on another image library.

## 4. Reuse decisions
| Component | Chosen option | Version | License | Alternatives considered | Why this one |
| --- | --- | --- | --- | --- | --- |
| Engine | ncnn official upstream | commit `6125c9f…` | BSD-3-Clause | ONNX Runtime; third-party wrapper | Small, CPU/Vulkan, owner approved direct upstream |
| Native reference | Real-ESRGAN ncnn Vulkan | commit `37026f4…` | MIT | RealSR-NCNN-Android; custom inference | Official implementation and smallest audit surface |
| Offline photo fallback | waifu2x upconv7 photo | repository snapshot | MIT | Real-ESRGAN compact release asset | Small and available from audited source; exact Real-ESRGAN release asset was inaccessible here |
| Offline anime fallback | waifu2x anime upconv7 2x | repository snapshot | MIT | Real-CUGAN; Real-ESRGAN anime-video | Small, permissive and compatible with the shared adapter |
| Model downloads | GitHub Releases | n/a | model-specific | Owner server; undecided storage | Owner selected it; no private server required |

### Candidate findings

| Candidate | License | Last source update | Fit |
| --- | --- | --- | --- |
| ncnn + official Real-ESRGAN/Real-CUGAN projects | BSD-3 + MIT | ncnn 2026-11-03; model ports 2023–2024 | Best: shared Android/Windows core and auditable |
| RealSR-NCNN-Android | MIT | active 2026-11-03 | Good Android reference, poor Windows reuse |
| A Flutter-specific ncnn wrapper | no maintained suitable candidate found | n/a | Rejected: no trustworthy cross-platform package found |

## 5. Test results vs qa-checklist.md
| Checklist section | Items | Passed | Failed | Notes |
| --- | ---: | ---: | ---: | --- |
| Phase 0 model load/output | 1 | 1 | 0 | 64x64 RGB → 128x128 RGB; output SHA-256 `ac14a00f…`; values span 0–252 |
| Linux CPU measurement | 1 | 1 | 0 | ncnn inference 39 ms; wall 74 ms; 2 threads |
| Android Light CPU/Vulkan | 1 | 0 | 0 | Not run: no Android SDK/device in workspace |
| Windows Light CPU/Vulkan | 1 | 0 | 0 | Not run: Linux workspace cannot execute Windows build |
| Package license/maintenance | 1 | 1 | 0 | Exact pins recorded in `Docs/licenses.md` |

Device used: Arena Linux x86_64 workspace, 3.8 GiB RAM, 2 reported big CPU
threads, no swap, no Vulkan SDK/driver.

## 6. Known issues & limitations
- The mandatory Android and Windows measurements remain pending. This means the
  formal Phase 0 hardware exit check is not yet fully satisfied.
- Vulkan configuration failed because this workspace has no Vulkan headers or
  driver. This is an environment limitation, not proof that the target fails.
- Official Real-ESRGAN compact release assets redirect to a host unavailable in
  this workspace. The bundled audited fallbacks keep the first run offline.
- The spike checks one whole small image. Seam testing on multiple tiles belongs
  to the target-platform engine integration and remains pending.

## 7. Risks for later phases
The largest risk is native build validation on real Android and Windows devices.
GitHub Release bandwidth and model weight notices must be rechecked before a
public release. A GPU-driver failure path must be tested on hardware, even though
CPU fallback can be tested in portable code.

## 8. Open questions for the owner
Resolved during kickoff: use direct upstream ncnn, use the supplied Apple-like
palette, bundle two fallback models, host on GitHub Releases, and continue the
build while clearly marking hardware checks pending. No additional owner choice
is needed for Phase 1 implementation.

## 9. Notes for the next phase
Use the exact ncnn commit from the spike. Keep the native bridge thin and keep
all planning rules in Dart data files. Never make a network call for image data.
The target-device verification script must rerun CPU and Vulkan paths, a 256 px
tile, a multi-tile photo, memory measurements and cancellation before release.
