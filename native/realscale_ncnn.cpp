// RealScale Pro — minimal C ABI wrapper around Tencent ncnn.
// License: MIT (this shim); ncnn itself is BSD-3-Clause.
//
// Exposes the exact symbols bound by lib/engine/ncnn/ncnn_ffi.dart:
//   RsSession* rs_create();
//   void       rs_destroy(RsSession*);
//   int        rs_load_model(RsSession*, const char* param, const char* bin,
//                            int threads, int use_gpu);
//   void       rs_unload_model(RsSession*);
//   int        rs_process(RsSession*, const uint8_t* in_rgba, int w, int h,
//                         int scale, uint8_t* out_rgba, int out_len);
//   void       rs_cancel(RsSession*);
//   const char* rs_last_error();
//
// Return codes (matching NcnnLibrary in ncnn_ffi.dart):
//   0 = rsOk, 1 = rsError, 2 = rsCancelled, 3 = rsOutOfMemory

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <exception>
#include <fstream>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#include "net.h"
#if defined(NCNN_VULKAN) || RS_ENABLE_VULKAN
#include "gpu.h"
#endif

#if defined(_WIN32)
#define RS_API extern "C" __declspec(dllexport)
#else
#define RS_API extern "C" __attribute__((visibility("default"))) __attribute__((used))
#endif

namespace {

constexpr int kRsOk = 0;
constexpr int kRsError = 1;
constexpr int kRsCancelled = 2;
constexpr int kRsOutOfMemory = 3;

enum class ModelFamily {
  kRealEsrgan,
  kWaifu2xUpconv,
  kWaifu2xCunet,
  kRealCugan,
};

struct ModelMeta {
  std::string input_blob = "data";
  std::string output_blob = "output";
  ModelFamily family = ModelFamily::kRealEsrgan;
};

thread_local std::string g_last_error_text;

void set_err(const std::string& msg) { g_last_error_text = msg; }

std::string to_lower_ascii(std::string s) {
  for (char& ch : s) {
    if (ch >= 'A' && ch <= 'Z') {
      ch = static_cast<char>(ch - 'A' + 'a');
    }
  }
  return s;
}

bool parse_param_metadata(const char* param_path, ModelMeta& meta) {
  std::ifstream ifs(param_path);
  if (!ifs.is_open()) {
    set_err(std::string("Failed to open param file: ") + param_path);
    return false;
  }

  std::string line;
  int header_lines = 0;
  bool found_input = false;
  std::string last_top_blob;

  while (std::getline(ifs, line)) {
    if (!line.empty() && line.back() == '\r') {
      line.pop_back();
    }
    if (line.empty() || line[0] == '#') {
      continue;
    }
    if (header_lines < 2) {
      ++header_lines;
      continue;
    }

    std::istringstream iss(line);
    std::string layer_type;
    std::string layer_name;
    int bottom_count = 0;
    int top_count = 0;
    if (!(iss >> layer_type >> layer_name >> bottom_count >> top_count)) {
      continue;
    }
    if (bottom_count < 0 || top_count < 0) {
      continue;
    }

    std::string tok;
    for (int i = 0; i < bottom_count; ++i) {
      if (!(iss >> tok)) break;
    }
    std::string first_top;
    for (int i = 0; i < top_count; ++i) {
      if (!(iss >> tok)) break;
      if (i == 0) {
        first_top = tok;
      }
    }

    if (layer_type == "Input" && !first_top.empty() && !found_input) {
      meta.input_blob = first_top;
      found_input = true;
    }
    if (!first_top.empty() && layer_type != "Split") {
      last_top_blob = first_top;
    }
  }

  if (!last_top_blob.empty()) {
    meta.output_blob = last_top_blob;
  }

  const std::string lower_path = to_lower_ascii(param_path ? param_path : "");
  if (lower_path.find("cunet") != std::string::npos) {
    meta.family = ModelFamily::kWaifu2xCunet;
  } else if (meta.input_blob == "Input1" ||
             lower_path.find("waifu2x") != std::string::npos) {
    meta.family = ModelFamily::kWaifu2xUpconv;
  } else if (meta.input_blob == "in0" ||
             lower_path.find("realcugan") != std::string::npos ||
             lower_path.find("up2x-") != std::string::npos ||
             lower_path.find("up3x-") != std::string::npos ||
             lower_path.find("up4x-") != std::string::npos) {
    meta.family = ModelFamily::kRealCugan;
  } else {
    meta.family = ModelFamily::kRealEsrgan;
  }
  return true;
}

inline uint8_t clamp_u8(float v) {
  const int iv = static_cast<int>(std::floor(v + 0.5f));
  return static_cast<uint8_t>(iv < 0 ? 0 : (iv > 255 ? 255 : iv));
}

void upscale_alpha_bilinear(const uint8_t* in_rgba, int w, int h, int scale,
                            uint8_t* out_rgba) {
  const int out_w = w * scale;
  const int out_h = h * scale;

  bool has_transparency = false;
  const size_t total_in = static_cast<size_t>(w) * static_cast<size_t>(h);
  for (size_t i = 0; i < total_in; ++i) {
    if (in_rgba[i * 4 + 3] != 255) {
      has_transparency = true;
      break;
    }
  }

  if (!has_transparency) {
    const size_t total_out =
        static_cast<size_t>(out_w) * static_cast<size_t>(out_h);
    for (size_t i = 0; i < total_out; ++i) {
      out_rgba[i * 4 + 3] = 255;
    }
    return;
  }

  for (int oy = 0; oy < out_h; ++oy) {
    const float fy = (oy + 0.5f) / static_cast<float>(scale) - 0.5f;
    const int y0 = std::clamp(static_cast<int>(std::floor(fy)), 0, h - 1);
    const int y1 = std::min(y0 + 1, h - 1);
    const float wy = fy - std::floor(fy);

    for (int ox = 0; ox < out_w; ++ox) {
      const float fx = (ox + 0.5f) / static_cast<float>(scale) - 0.5f;
      const int x0 = std::clamp(static_cast<int>(std::floor(fx)), 0, w - 1);
      const int x1 = std::min(x0 + 1, w - 1);
      const float wx = fx - std::floor(fx);

      const float a00 = in_rgba[(y0 * w + x0) * 4 + 3];
      const float a01 = in_rgba[(y0 * w + x1) * 4 + 3];
      const float a10 = in_rgba[(y1 * w + x0) * 4 + 3];
      const float a11 = in_rgba[(y1 * w + x1) * 4 + 3];

      const float top = a00 * (1.0f - wx) + a01 * wx;
      const float bot = a10 * (1.0f - wx) + a11 * wx;
      const float val = top * (1.0f - wy) + bot * wy;
      out_rgba[(oy * out_w + ox) * 4 + 3] = clamp_u8(val);
    }
  }
}

void compute_padding(ModelFamily family, int w, int h, int scale, int& prepad,
                     int& pad_top, int& pad_bottom, int& pad_left,
                     int& pad_right, int& border_type) {
  prepad = 0;
  int align = 4;
  border_type = ncnn::BORDER_REPLICATE;

  switch (family) {
    case ModelFamily::kWaifu2xUpconv:
      prepad = 7;
      align = (scale == 1) ? 4 : 2;
      border_type = ncnn::BORDER_REPLICATE;
      break;
    case ModelFamily::kWaifu2xCunet:
      prepad = 18;
      align = 4;
      border_type = ncnn::BORDER_REPLICATE;
      break;
    case ModelFamily::kRealCugan:
      if (scale == 2) {
        prepad = 18;
        align = 2;
      } else if (scale == 3) {
        prepad = 14;
        align = 4;
      } else if (scale == 4) {
        prepad = 19;
        align = 2;
      } else {
        prepad = 18;
        align = 4;
      }
      // BORDER_REFLECT_101 (2) matches upstream realcugan when dimensions
      // strictly exceed prepadding; fall back to BORDER_REPLICATE on tiny tiles.
      border_type = (w > prepad && h > prepad) ? 2 : ncnn::BORDER_REPLICATE;
      break;
    case ModelFamily::kRealEsrgan:
    default:
      prepad = 0;
      align = 4;
      border_type = ncnn::BORDER_REPLICATE;
      break;
  }

  const int align_w = ((w + align - 1) / align) * align - w;
  const int align_h = ((h + align - 1) / align) * align - h;

  pad_top = prepad;
  pad_left = prepad;
  pad_bottom = prepad + align_h;
  pad_right = prepad + align_w;
}

bool configure_and_load(ncnn::Net& net, const char* param_path,
                        const char* bin_path, int threads, bool want_gpu,
                        bool& active_gpu) {
  net.clear();
  net.opt = ncnn::Option();
  net.opt.num_threads = std::max(1, threads);
  net.opt.use_fp16_packed = false;
  net.opt.use_fp16_storage = false;
  net.opt.use_fp16_arithmetic = false;
  net.opt.use_int8_storage = false;
  active_gpu = false;

#if defined(NCNN_VULKAN) || RS_ENABLE_VULKAN
  if (want_gpu) {
    const int gpu_count = ncnn::get_gpu_count();
    if (gpu_count > 0) {
      net.opt.use_vulkan_compute = true;
      net.opt.use_fp16_packed = true;
      net.opt.use_fp16_storage = true;
      active_gpu = true;
    }
  }
#else
  (void)want_gpu;
#endif

  int r1 = net.load_param(param_path);
  if (r1 != 0) {
    set_err("load_param failed (" + std::to_string(r1) + "): " + param_path);
    return false;
  }
  int r2 = net.load_model(bin_path);
  if (r2 != 0) {
    set_err("load_model failed (" + std::to_string(r2) + "): " + bin_path);
    net.clear();
    return false;
  }
  return true;
}

}  // namespace

struct RsSession {
  ncnn::Net net;
  std::mutex mu;
  std::atomic<bool> cancel{false};
  bool loaded = false;
  bool using_gpu = false;
  int threads = 2;
  std::string param_path;
  std::string bin_path;
  ModelMeta meta;
};

RS_API RsSession* rs_create() {
  try {
    return new RsSession();
  } catch (const std::exception& e) {
    set_err(std::string("rs_create exception: ") + e.what());
    return nullptr;
  } catch (...) {
    set_err("rs_create unknown exception");
    return nullptr;
  }
}

RS_API void rs_destroy(RsSession* s) {
  if (!s) return;
  try {
    std::lock_guard<std::mutex> lock(s->mu);
    s->net.clear();
    s->loaded = false;
  } catch (...) {
  }
  delete s;
}

RS_API int rs_load_model(RsSession* s, const char* param_path,
                         const char* bin_path, int threads, int use_gpu) {
  if (!s || !param_path || !bin_path) {
    set_err("null session or path");
    return kRsError;
  }
  try {
    std::lock_guard<std::mutex> lock(s->mu);
    s->loaded = false;
    s->cancel.store(false);

    ModelMeta meta;
    if (!parse_param_metadata(param_path, meta)) {
      return kRsError;
    }

    bool active_gpu = false;
    if (!configure_and_load(s->net, param_path, bin_path, threads,
                            use_gpu != 0, active_gpu)) {
      if (use_gpu != 0) {
        // Automatic fallback to CPU if mobile Vulkan driver rejected pipeline.
        if (!configure_and_load(s->net, param_path, bin_path, threads, false,
                                active_gpu)) {
          return kRsError;
        }
      } else {
        return kRsError;
      }
    }

    s->meta = meta;
    s->threads = std::max(1, threads);
    s->using_gpu = active_gpu;
    s->param_path = param_path;
    s->bin_path = bin_path;
    s->loaded = true;
    return kRsOk;
  } catch (const std::bad_alloc&) {
    set_err("out of memory in rs_load_model");
    return kRsOutOfMemory;
  } catch (const std::exception& e) {
    set_err(std::string("rs_load_model exception: ") + e.what());
    return kRsError;
  } catch (...) {
    set_err("rs_load_model unknown exception");
    return kRsError;
  }
}

RS_API void rs_unload_model(RsSession* s) {
  if (!s) return;
  try {
    std::lock_guard<std::mutex> lock(s->mu);
    s->net.clear();
    s->loaded = false;
  } catch (...) {
  }
}

RS_API void rs_cancel(RsSession* s) {
  if (!s) return;
  s->cancel.store(true);
}

RS_API int rs_process(RsSession* s, const uint8_t* in_rgba, int w, int h,
                      int scale, uint8_t* out_rgba, int out_len) {
  if (!s || !in_rgba || !out_rgba || w <= 0 || h <= 0 || scale <= 0) {
    set_err("invalid args to rs_process");
    return kRsError;
  }
  const int64_t expected_len =
      static_cast<int64_t>(w) * scale * static_cast<int64_t>(h) * scale * 4;
  if (out_len < expected_len) {
    set_err("output buffer too small");
    return kRsError;
  }
  try {
    std::lock_guard<std::mutex> lock(s->mu);
    if (!s->loaded) {
      set_err("model not loaded");
      return kRsError;
    }
    if (s->cancel.exchange(false)) {
      set_err("cancelled");
      return kRsCancelled;
    }

    // 1. Convert RGBA8 -> planar RGB float normalized to [0, 1].
    ncnn::Mat in_rgb =
        ncnn::Mat::from_pixels(in_rgba, ncnn::Mat::PIXEL_RGBA2RGB, w, h);
    const float norm_vals[3] = {1.0f / 255.0f, 1.0f / 255.0f, 1.0f / 255.0f};
    in_rgb.substract_mean_normalize(nullptr, norm_vals);

    // 2. Model-specific prepadding & alignment so deconv/crop layers line up.
    int prepad = 0;
    int pad_top = 0;
    int pad_bottom = 0;
    int pad_left = 0;
    int pad_right = 0;
    int border_type = ncnn::BORDER_REPLICATE;
    compute_padding(s->meta.family, w, h, scale, prepad, pad_top, pad_bottom,
                    pad_left, pad_right, border_type);

    ncnn::Mat in_padded;
    if (pad_top > 0 || pad_bottom > 0 || pad_left > 0 || pad_right > 0) {
      ncnn::Option border_opt = s->net.opt;
      border_opt.use_vulkan_compute = false;
      border_opt.use_fp16_packed = false;
      border_opt.use_fp16_storage = false;
      ncnn::copy_make_border(in_rgb, in_padded, pad_top, pad_bottom, pad_left,
                             pad_right, border_type, 0.0f, border_opt);
    } else {
      in_padded = in_rgb;
    }

    // 3. Run forward pass (with automatic CPU retry if Vulkan extract fails).
    auto run_forward = [&](ncnn::Mat& out_mat) -> int {
      ncnn::Extractor ex = s->net.create_extractor();
      int ri = ex.input(s->meta.input_blob.c_str(), in_padded);
      if (ri != 0) return ri;
      return ex.extract(s->meta.output_blob.c_str(), out_mat);
    };

    ncnn::Mat out;
    int r = run_forward(out);
    if (r != 0 && s->using_gpu) {
      bool active_gpu = false;
      if (configure_and_load(s->net, s->param_path.c_str(), s->bin_path.c_str(),
                             s->threads, false, active_gpu)) {
        s->using_gpu = false;
        r = run_forward(out);
      }
    }

    if (r != 0 || out.empty() || out.c < 3) {
      set_err("extract failed (" + std::to_string(r) + ") blob=" +
              s->meta.input_blob + "->" + s->meta.output_blob);
      return (r == -100) ? kRsOutOfMemory : kRsError;
    }

    if (s->cancel.exchange(false)) {
      set_err("cancelled");
      return kRsCancelled;
    }

    const int out_w = w * scale;
    const int out_h = h * scale;
    if (out.w < out_w || out.h < out_h) {
      set_err("unexpected output shape " + std::to_string(out.w) + "x" +
              std::to_string(out.h) + " (expected >= " +
              std::to_string(out_w) + "x" + std::to_string(out_h) + ")");
      return kRsError;
    }

    // 4. Denormalize RGB planar floats [0, 1] -> interleaved RGBA8.
    const bool add_cugan_4x_residual =
        (s->meta.family == ModelFamily::kRealCugan && scale == 4);

    const ncnn::Mat ch_r = out.channel(0);
    const ncnn::Mat ch_g = out.channel(1);
    const ncnn::Mat ch_b = out.channel(2);

    const ncnn::Mat in_r = in_padded.channel(0);
    const ncnn::Mat in_g = in_padded.channel(1);
    const ncnn::Mat in_b = in_padded.channel(2);

    for (int y = 0; y < out_h; ++y) {
      const float* pr = ch_r.row(y);
      const float* pg = ch_g.row(y);
      const float* pb = ch_b.row(y);
      uint8_t* dst = out_rgba + static_cast<size_t>(y) * out_w * 4;

      if (add_cugan_4x_residual) {
        const float* ir = in_r.row(prepad + y / 4) + prepad;
        const float* ig = in_g.row(prepad + y / 4) + prepad;
        const float* ib = in_b.row(prepad + y / 4) + prepad;
        for (int x = 0; x < out_w; ++x) {
          dst[x * 4 + 0] = clamp_u8((pr[x] + ir[x / 4]) * 255.0f);
          dst[x * 4 + 1] = clamp_u8((pg[x] + ig[x / 4]) * 255.0f);
          dst[x * 4 + 2] = clamp_u8((pb[x] + ib[x / 4]) * 255.0f);
        }
      } else {
        for (int x = 0; x < out_w; ++x) {
          dst[x * 4 + 0] = clamp_u8(pr[x] * 255.0f);
          dst[x * 4 + 1] = clamp_u8(pg[x] * 255.0f);
          dst[x * 4 + 2] = clamp_u8(pb[x] * 255.0f);
        }
      }
    }

    // 5. Preserve input alpha channel cleanly.
    upscale_alpha_bilinear(in_rgba, w, h, scale, out_rgba);
    return kRsOk;
  } catch (const std::bad_alloc&) {
    set_err("out of memory in rs_process");
    return kRsOutOfMemory;
  } catch (const std::exception& e) {
    set_err(std::string("rs_process exception: ") + e.what());
    return kRsError;
  } catch (...) {
    set_err("rs_process unknown exception");
    return kRsError;
  }
}

RS_API const char* rs_last_error() { return g_last_error_text.c_str(); }
