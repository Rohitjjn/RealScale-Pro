// RealScale Pro — Native C ABI smoke test for CI verification.
// Exercises rs_create, rs_load_model, rs_process, rs_cancel, rs_unload_model,
// and rs_destroy against all four bundled starter models.

#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

struct RsSession;

extern "C" {
RsSession* rs_create();
void rs_destroy(RsSession* s);
int rs_load_model(RsSession* s, const char* param_path, const char* bin_path,
                  int threads, int use_gpu);
void rs_unload_model(RsSession* s);
int rs_process(RsSession* s, const uint8_t* in_rgba, int w, int h, int scale,
               uint8_t* out_rgba, int out_len);
void rs_cancel(RsSession* s);
const char* rs_last_error();
}

namespace {

struct StarterCase {
  const char* name;
  const char* param_rel;
  const char* bin_rel;
  int scale;
  int w;
  int h;
  bool translucent;
};

int run_case(RsSession* s, const std::string& repo_root,
             const StarterCase& tc) {
  const std::string param = repo_root + "/" + tc.param_rel;
  const std::string bin = repo_root + "/" + tc.bin_rel;

  int rc = rs_load_model(s, param.c_str(), bin.c_str(), 2, 0);
  if (rc != 0) {
    std::fprintf(stderr, "[FAIL] %s load_model rc=%d err=%s\n", tc.name, rc,
                 rs_last_error());
    return 1;
  }

  std::vector<uint8_t> in_rgba(static_cast<size_t>(tc.w) * tc.h * 4);
  for (int y = 0; y < tc.h; ++y) {
    for (int x = 0; x < tc.w; ++x) {
      const size_t idx = (static_cast<size_t>(y) * tc.w + x) * 4;
      in_rgba[idx + 0] = static_cast<uint8_t>((x * 255) / std::max(1, tc.w - 1));
      in_rgba[idx + 1] = static_cast<uint8_t>((y * 255) / std::max(1, tc.h - 1));
      in_rgba[idx + 2] = static_cast<uint8_t>(96 + ((x + y) % 96));
      in_rgba[idx + 3] = tc.translucent ? static_cast<uint8_t>(40 + (x * 180) / tc.w) : 255;
    }
  }

  const int out_w = tc.w * tc.scale;
  const int out_h = tc.h * tc.scale;
  std::vector<uint8_t> out_rgba(static_cast<size_t>(out_w) * out_h * 4, 0);

  rc = rs_process(s, in_rgba.data(), tc.w, tc.h, tc.scale, out_rgba.data(),
                  static_cast<int>(out_rgba.size()));
  if (rc != 0) {
    std::fprintf(stderr, "[FAIL] %s rs_process rc=%d err=%s\n", tc.name, rc,
                 rs_last_error());
    return 1;
  }

  // Verify non-blank, non-constant output with sane variance and alpha.
  double sum = 0.0;
  uint8_t min_v = 255;
  uint8_t max_v = 0;
  bool saw_non_opaque_alpha = false;
  const size_t pixels = static_cast<size_t>(out_w) * out_h;
  for (size_t i = 0; i < pixels; ++i) {
    const uint8_t r = out_rgba[i * 4 + 0];
    const uint8_t g = out_rgba[i * 4 + 1];
    const uint8_t b = out_rgba[i * 4 + 2];
    const uint8_t a = out_rgba[i * 4 + 3];
    const uint8_t lum = static_cast<uint8_t>((r + g + b) / 3);
    sum += lum;
    if (lum < min_v) min_v = lum;
    if (lum > max_v) max_v = lum;
    if (a < 255) saw_non_opaque_alpha = true;
  }
  const double mean = sum / static_cast<double>(pixels);
  if (max_v - min_v < 20 || mean < 15.0 || mean > 245.0) {
    std::fprintf(stderr,
                 "[FAIL] %s degenerate output min=%u max=%u mean=%.2f\n",
                 tc.name, min_v, max_v, mean);
    return 1;
  }
  if (tc.translucent && !saw_non_opaque_alpha) {
    std::fprintf(stderr, "[FAIL] %s lost alpha transparency\n", tc.name);
    return 1;
  }
  if (!tc.translucent && saw_non_opaque_alpha) {
    std::fprintf(stderr, "[FAIL] %s corrupted opaque alpha\n", tc.name);
    return 1;
  }

  std::printf("[PASS] %-28s %dx%d -> %dx%d (min=%u max=%u mean=%.1f)\n",
              tc.name, tc.w, tc.h, out_w, out_h, min_v, max_v, mean);
  return 0;
}

}  // namespace

int main(int argc, char** argv) {
  const std::string repo_root = (argc > 1) ? argv[1] : ".";
  RsSession* s = rs_create();
  if (!s) {
    std::fprintf(stderr, "rs_create returned null\n");
    return 1;
  }

  const StarterCase cases[] = {
      {"waifu2x-upconv-photo-2x",
       "assets/models_starter/waifu2x-upconv-photo-2x.param",
       "assets/models_starter/waifu2x-upconv-photo-2x.bin", 2, 33, 29, false},
      {"waifu2x-upconv-anime-2x",
       "assets/models_starter/waifu2x-upconv-anime-2x.param",
       "assets/models_starter/waifu2x-upconv-anime-2x.bin", 2, 32, 32, true},
      {"realcugan-se-2x-denoise1x",
       "assets/models_starter/up2x-denoise1x.param",
       "assets/models_starter/up2x-denoise1x.bin", 2, 41, 37, false},
      {"realesr-animevideov3-x4",
       "assets/models_starter/realesr-animevideov3-x4.param",
       "assets/models_starter/realesr-animevideov3-x4.bin", 4, 27, 25, true},
  };

  for (const auto& tc : cases) {
    if (run_case(s, repo_root, tc) != 0) {
      rs_destroy(s);
      return 1;
    }
  }

  // Verify cancel flag returns 2 (rsCancelled) cleanly.
  rs_cancel(s);
  uint8_t dummy_in[16 * 16 * 4] = {};
  uint8_t dummy_out[64 * 64 * 4] = {};
  int cancel_rc = rs_process(s, dummy_in, 16, 16, 4, dummy_out,
                             static_cast<int>(sizeof(dummy_out)));
  if (cancel_rc != 2) {
    std::fprintf(stderr, "[FAIL] expected cancel rc=2, got %d\n", cancel_rc);
    rs_destroy(s);
    return 1;
  }

  rs_unload_model(s);
  rs_destroy(s);
  std::printf("All native ncnn starter model smoke tests passed!\n");
  return 0;
}
