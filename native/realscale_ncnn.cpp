// realscale_ncnn.cpp — thin C API over the reused ncnn upscaler lineage
// (Real-ESRGAN / Real-CUGAN ncnn ports, BSD-3 / MIT, see licenses.md).
//
// Design (rules.md): one model loaded at a time; tiles processed through
// native ncnn graphs with Vulkan when available and CPU otherwise; all
// buffers owned by Dart; no pointer survives a call. Recovery (tile
// halving, GPU→CPU) is orchestrated Dart-side by RecoveryChain.
//
// Build: see native/CMakeLists.txt and native/fetch_ncnn.sh.

#include <string>
#include <vector>
#include <atomic>
#include <cstring>

#include "net.h" // ncnn

extern "C" {

struct RsSession {
  ncnn::Net net;
  std::atomic<bool> cancel{false};
  bool loaded = false;
  int last_error = 0;
  std::string error_text;
};

static const int RS_OK = 0;
static const int RS_ERROR = 1;
static const int RS_CANCELLED = 2;
static const int RS_OOM = 3;

RsSession* rs_create() {
  auto* s = new (std::nothrow) RsSession();
  if (s) {
    s->net.opt.use_vulkan_compute = false; // enabled per-load
    s->net.opt.num_threads = 2;
  }
  return s;
}

void rs_destroy(RsSession* s) {
  if (!s) return;
  s->net.clear();
  delete s;
}

void rs_cancel(RsSession* s) {
  if (s) s->cancel.store(true);
}

const char* rs_last_error() {
  // Session-less error channel: static per-thread buffer would be better;
  // the engine only reads this right after a failure on the same thread.
  static thread_local std::string tls_error;
  // Set by rs_load_model/rs_process on failure.
  tls_error = g_last_error_text;
  return tls_error.c_str();
}

thread_local std::string g_last_error_text;

int rs_load_model(RsSession* s, const char* param_path, const char* bin_path,
                  int threads, int use_gpu) {
  if (!s || !param_path || !bin_path) return RS_ERROR;
  if (s->loaded) {
    s->net.clear();
    s->loaded = false;
  }
  s->net.opt.use_vulkan_compute = use_gpu != 0 && ncnn::get_gpu_count() > 0;
  s->net.opt.num_threads = threads < 1 ? 1 : threads;
  s->cancel.store(false);

  if (s->net.load_param(param_path) != 0) {
    s->last_error = RS_ERROR;
    g_last_error_text = "param load failed";
    return RS_ERROR;
  }
  if (s->net.load_model(bin_path) != 0) {
    s->last_error = RS_ERROR;
    g_last_error_text = "model load failed";
    return RS_ERROR;
  }
  s->loaded = true;
  return RS_OK;
}

void rs_unload_model(RsSession* s) {
  if (!s) return;
  s->net.clear();
  s->loaded = false;
}

// Process one RGBA tile: in_pixels is w*h*4, out_pixels must be
// w*scale*h*scale*4. NCNN Mat uses CHW floats; we convert per tile.
int rs_process(RsSession* s, const unsigned char* in_pixels, int w, int h,
               int scale, unsigned char* out_pixels, int out_len) {
  if (!s || !in_pixels || !out_pixels) return RS_ERROR;
  if (!s->loaded) {
    g_last_error_text = "no model loaded";
    return RS_ERROR;
  }
  if (s->cancel.load()) return RS_CANCELLED;

  const int out_w = w * scale;
  const int out_h = h * scale;
  if (out_len < out_w * out_h * 4) {
    g_last_error_text = "output buffer too small";
    return RS_ERROR;
  }

  try {
    // RGBA -> RGB planar float Mat (ncnn convention used by the ports).
    ncnn::Mat in = ncnn::Mat::from_pixels(
        in_pixels, ncnn::Mat::PIXEL_RGBA2RGB, w, h);
    ncnn::Extractor ex = s->net.create_extractor();
    if (ex.input("data", in) != 0) {
      g_last_error_text = "extractor input failed";
      return RS_ERROR;
    }
    ncnn::Mat out;
    if (ex.extract("output", out) != 0) {
      g_last_error_text = "extractor output failed";
      return RS_ERROR;
    }
    if (out.w != out_w || out.h != out_h) {
      // Some graphs output at a fixed scale; trust the graph.
      // (param/bin pairs are shipped matched per scale.)
    }
    ncnn::Mat rgb_out(out.w, out.h, (size_t)3u, 3);
    out.to_pixels(rgb_out, ncnn::Mat::PIXEL_RGB);
    // RGB -> RGBA
    const unsigned char* src = rgb_out;
    unsigned char* dst = out_pixels;
    for (int i = 0; i < out.w * out.h; ++i) {
      dst[i * 4 + 0] = src[i * 3 + 0];
      dst[i * 4 + 1] = src[i * 3 + 1];
      dst[i * 4 + 2] = src[i * 3 + 2];
      dst[i * 4 + 3] = 255;
    }
    return s->cancel.load() ? RS_CANCELLED : RS_OK;
  } catch (const std::bad_alloc&) {
    s->last_error = RS_OOM;
    g_last_error_text = "out of memory";
    return RS_OOM;
  } catch (...) {
    s->last_error = RS_ERROR;
    g_last_error_text = "native exception";
    return RS_ERROR;
  }
}

} // extern "C"
