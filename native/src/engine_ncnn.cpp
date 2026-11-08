// RealScale-Pro — ncnn engine implementation: REAL tiled inference.
//
// Pipeline per run:
//   1. Validate input/model/job.
//   2. Compute tile layout (tile size, overlap, context; model-aware
//      defaults when the caller passes 0).
//   3. For every tile: extract the context region from the input RGBA
//      buffer, convert to an ncnn::Mat (RGB float), run the model,
//      convert the output back to RGBA and blend it into the output
//      accumulator with normalized feather weights.
//   4. Normalize the accumulator and hand the final image to the caller.
//
// Recovery: when inference or allocation fails mid-run (std::bad_alloc,
// ncnn error, or an intentionally injected test failure), the tile size
// is halved (>= 32 px floor) and the whole blending restarts on a fresh
// accumulator, up to job.max_retries times. Progress reports carry the
// attempt number so the UI can surface honest recovery information.
// A partially blended image is NEVER returned as success.
//
// Copyright 2026 RealScale-Pro contributors. MIT License.
#include "engine.h"

#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <new>
#include <string>
#include <sys/stat.h>
#include <vector>

#include "error.h"
#include "feather_blend.h"
#include "tile_layout.h"

#include "cpu.h"
#include "net.h"
#if defined(NCNN_VULKAN) && NCNN_VULKAN
#include "gpu.h"
#define RS_HAS_VULKAN 1
#else
#define RS_HAS_VULKAN 0
#endif

namespace rs {

namespace {

constexpr const char* kInputBlob = "data";
constexpr const char* kOutputBlob = "output";

// Test hook: fail the first `n` tiles of the first attempt.
// Enabled only in test builds (RS_TEST_HOOKS). Production binaries
// read the env var but it is inert because the hook is compiled out.
int test_fail_first_tiles() {
#ifdef RS_TEST_HOOKS
    const char* v = std::getenv("RS_TEST_FAIL_TILES");
    if (v != nullptr) {
        return std::atoi(v);
    }
#endif
    return 0;
}

struct ModelProfile {
    int32_t default_tile = 128;
    int32_t default_overlap = 12;
    int32_t default_context = 4;
};

// Model-aware defaults. SRVGGNetCompact (realesr-animevideov3,
// realesr-general-x4v3 family) has a small receptive field; RRDBNet
// (realesrgan-x4plus) a large one, so it gets a wider overlap.
ModelProfile profile_for_bin_size(int64_t bin_bytes) {
    ModelProfile p;
    if (bin_bytes < 4 * 1024 * 1024) {
        // Compact SRVGG family.
        p.default_tile = 128;
        p.default_overlap = 10;
        p.default_context = 2;
    } else {
        // RRDBNet family.
        p.default_tile = 128;
        p.default_overlap = 16;
        p.default_context = 8;
    }
    return p;
}

class NcnnEngine : public Engine {
public:
    explicit NcnnEngine(const rs_engine_options& opts)
        : opts_(opts), threads_(resolve_threads(opts.thread_count)) {
        net_.opt.num_threads = threads_;
        net_.opt.use_fp16_packed = resolve_fp16(opts.use_fp16, /*packed=*/true);
        net_.opt.use_fp16_storage = resolve_fp16(opts.use_fp16, /*packed=*/false);
        net_.opt.use_fp16_arithmetic = false; // not portable across SoCs
        net_.opt.use_winograd_convolution = true;
        net_.opt.use_sgemm_convolution = true;
        net_.opt.use_int8_inference = false;  // SR quality must not degrade
        net_.opt.lightmode = true;            // lower peak memory
#if RS_HAS_VULKAN
        // GPU is opt-in only (RS_COMPUTE_GPU); AUTO keeps the verified
        // CPU path. When requested but no Vulkan device exists, we fall
        // back to CPU here and report it honestly via rs_device_info.
        if (opts_.compute == RS_COMPUTE_GPU && gpu_available()) {
            net_.opt.use_vulkan_compute = true;
            use_gpu_ = true;
        }
#endif
    }

    rs_status load_model(const std::string& param_path,
                         const std::string& bin_path,
                         rs_model_info* out_info) override {
        std::lock_guard<std::mutex> lock(mutex_);
        param_path_ = param_path;
        bin_path_ = bin_path;
        rs_status st = load_model_locked(param_path, bin_path);
        if (st != RS_OK && use_gpu_) {
            // Vulkan model load failed (e.g. unsupported layer on GPU,
            // device lost). Fall back to CPU and retry once — never
            // leave the engine in a half-loaded state.
            use_gpu_ = false;
            net_.clear();
            net_.opt.use_vulkan_compute = false;
            st = load_model_locked(param_path, bin_path);
        }
        if (st != RS_OK) {
            return st;
        }
        struct stat st_buf {};
        int64_t param_bytes = 0, bin_bytes = 0;
        if (::stat(param_path.c_str(), &st_buf) == 0) {
            param_bytes = static_cast<int64_t>(st_buf.st_size);
        }
        if (::stat(bin_path.c_str(), &st_buf) == 0) {
            bin_bytes = static_cast<int64_t>(st_buf.st_size);
        }
        profile_ = profile_for_bin_size(bin_bytes);
        loaded_ = true;
        if (out_info != nullptr) {
            out_info->scale_hint = 0; // validated at run time
            out_info->input_tile_hint = profile_.default_tile;
            out_info->param_bytes = param_bytes;
            out_info->bin_bytes = bin_bytes;
            out_info->loaded = 1;
        }
        return RS_OK;
    }

    rs_status model_info(rs_model_info* out_info) const override {
        if (out_info == nullptr) {
            return RS_ERR_INVALID_ARG;
        }
        if (!loaded_) {
            out_info->loaded = 0;
            return RS_ERR_MODEL_INVALID;
        }
        out_info->scale_hint = 0;
        out_info->input_tile_hint = profile_.default_tile;
        out_info->loaded = 1;
        return RS_OK;
    }

    rs_status device_info(rs_device_info* out_info) const override {
        if (out_info == nullptr) {
            return RS_ERR_INVALID_ARG;
        }
        out_info->gpu_available = gpu_available();
        out_info->using_gpu = use_gpu_ ? 1 : 0;
        out_info->thread_count = threads_;
        out_info->fp16_storage = net_.opt.use_fp16_storage ? 1 : 0;
        return RS_OK;
    }

    rs_compute compute_backend() const override {
        return use_gpu_ ? RS_COMPUTE_GPU : RS_COMPUTE_CPU;
    }

    rs_status run(const RunContext& ctx, rs_image** out) override;

private:
    static int resolve_threads(int requested) {
        if (requested > 0) {
            return requested;
        }
        int n = ncnn::get_cpu_count();
        int phy = ncnn::get_physical_cpu_count();
        return phy > 0 ? phy : (n > 0 ? n : 2);
    }

    // Reloads the stored model pair into the (possibly reconfigured)
    // net. Caller holds mutex_.
    rs_status load_model_locked(const std::string& param_path,
                                const std::string& bin_path) {
        if (net_.load_param(param_path.c_str()) != 0) {
            set_error("failed to load ncnn param file: %s", param_path.c_str());
            return RS_ERR_MODEL_LOAD;
        }
        if (net_.load_model(bin_path.c_str()) != 0) {
            net_.clear();
            set_error("failed to load ncnn bin file: %s", bin_path.c_str());
            return RS_ERR_MODEL_LOAD;
        }
        return RS_OK;
    }

    // Switches a GPU engine to CPU mid-run (bounded: once per run) and
    // restarts the attempt loop. Returns false when the CPU reload also
    // fails. Caller holds mutex_.
    bool fallback_to_cpu() {
        use_gpu_ = false;
        net_.clear();
        net_.opt.use_vulkan_compute = false;
        net_.opt.num_threads = threads_;
        net_.opt.use_fp16_packed = resolve_fp16(opts_.use_fp16, true);
        net_.opt.use_fp16_storage = resolve_fp16(opts_.use_fp16, false);
        return load_model_locked(param_path_, bin_path_) == RS_OK;
    }

#if RS_HAS_VULKAN
    static int gpu_available() { return ncnn::get_gpu_count() > 0 ? 1 : 0; }
#else
    static int gpu_available() { return 0; }
#endif

    static int resolve_fp16(int requested, bool packed) {
        if (requested == 1) {
            return 1;
        }
        if (requested == 0) {
            return 0;
        }
        // AUTO: enable fp16 storage on aarch64 (all modern Android
        // devices), keep it off elsewhere for portability.
#if defined(__aarch64__) || defined(_M_ARM64)
        (void)packed;
        return 1;
#else
        (void)packed;
        return 0;
#endif
    }

    void emit_progress(const RunContext& ctx, int32_t stage,
                       int32_t done, int32_t total, int32_t attempt,
                       int32_t tile_size, double fraction) {
        if (ctx.progress == nullptr) {
            return;
        }
        rs_progress p{};
        p.stage = stage;
        p.tiles_done = done;
        p.tiles_total = total;
        p.attempt = attempt;
        p.tile_size = tile_size;
        p.fraction = std::min(1.0, std::max(0.0, fraction));
        ctx.progress(ctx.progress_user, &p);
    }

    rs_engine_options opts_{};
    int threads_ = 2;
    ncnn::Net net_;
    ModelProfile profile_{};
    bool loaded_ = false;
    bool use_gpu_ = false;      // actually running on Vulkan
    bool gpu_fallback_used_ = false;
    std::string param_path_;
    std::string bin_path_;
    std::mutex mutex_;
};

// ------------------------------------------------------------------ //
// Inference of a single context region. Returns false on failure and
// sets the thread-local error message.
// ------------------------------------------------------------------ //
bool infer_region(ncnn::Net& net, int threads,
                  const uint8_t* rgba, int32_t region_w, int32_t region_h,
                  int32_t row_offset, int32_t stride_bytes,
                  int32_t scale,
                  std::vector<uint8_t>& out_rgba) {
    const uint8_t* base = rgba + static_cast<size_t>(row_offset);
    ncnn::Mat in = ncnn::Mat::from_pixels(
        base, ncnn::Mat::PIXEL_RGBA2RGB, region_w, region_h, stride_bytes);
    if (in.empty()) {
        set_error("ncnn input conversion failed for %dx%d region",
                  region_w, region_h);
        return false;
    }
    ncnn::Mat out;
    {
        ncnn::Extractor ex = net.create_extractor();
        if (ex.input(kInputBlob, in) != 0) {
            set_error("ncnn extractor input failed (blob '%s')", kInputBlob);
            return false;
        }
        if (ex.extract(kOutputBlob, out) != 0) {
            set_error("ncnn extractor failed (blob '%s') — inference error",
                      kOutputBlob);
            return false;
        }
    }
    if (out.empty()) {
        set_error("ncnn returned an empty output");
        return false;
    }
    if (out.w != region_w * scale || out.h != region_h * scale) {
        // Non-integer or unexpected scale — refuse to guess.
        if (out.w % region_w != 0 || out.h % region_h != 0) {
            set_error("model output %dx%d does not match an integer scale of "
                      "input %dx%d (requested scale %d)",
                      out.w, out.h, region_w, region_h, scale);
            return false;
        }
        const int actual = out.w / region_w;
        set_error("model upscale factor is %dx but job requested %dx; "
                  "select the matching model variant", actual, scale);
        return false;
    }
    const size_t need = static_cast<size_t>(out.w) * static_cast<size_t>(out.h) * 4;
    if (out_rgba.size() < need) {
        out_rgba.resize(need);
    }
    out.to_pixels(out_rgba.data(), ncnn::Mat::PIXEL_RGB2RGBA,
                  static_cast<int>(out.w) * 4);
    return true;
}

rs_status NcnnEngine::run(const RunContext& ctx, rs_image** out) {
    clear_error();
    if (out == nullptr || ctx.input == nullptr || ctx.input->pixels == nullptr) {
        set_error("invalid arguments to rs_run");
        return RS_ERR_INVALID_ARG;
    }
    if (ctx.input->channels != 4) {
        set_error("input image must be RGBA (channels=4), got %d",
                  ctx.input->channels);
        return RS_ERR_INVALID_ARG;
    }
    if (ctx.input->width <= 0 || ctx.input->height <= 0) {
        set_error("input image dimensions must be positive");
        return RS_ERR_INVALID_ARG;
    }
    if (ctx.job.scale < 2 || ctx.job.scale > 4) {
        set_error("scale must be between 2 and 4, got %d", ctx.job.scale);
        return RS_ERR_INVALID_ARG;
    }
    if (!loaded_) {
        set_error("no model loaded");
        return RS_ERR_MODEL_INVALID;
    }
    std::lock_guard<std::mutex> lock(mutex_);

    const int32_t in_w = ctx.input->width;
    const int32_t in_h = ctx.input->height;
    const int32_t out_w = in_w * ctx.job.scale;
    const int32_t out_h = in_h * ctx.job.scale;
    const int32_t stride = ctx.input->stride > 0 ? ctx.input->stride : in_w * 4;

    int32_t tile = ctx.job.tile_size > 0 ? ctx.job.tile_size
                                         : profile_.default_tile;
    int32_t overlap = ctx.job.tile_overlap > 0 ? ctx.job.tile_overlap
                                               : profile_.default_overlap;
    int32_t context = ctx.job.context_margin >= 0 ? ctx.job.context_margin
                                                  : profile_.default_context;
    const int32_t max_retries =
        ctx.job.max_retries > 0 ? ctx.job.max_retries : 3;

    // Cap the accumulation buffer for pathological inputs.
    const int64_t max_pixels = static_cast<int64_t>(1) << 30; // 1 Gpx guard
    if (static_cast<int64_t>(out_w) * out_h > max_pixels) {
        set_error("output %dx%d exceeds the engine safety limit", out_w, out_h);
        return RS_ERR_DEVICE_LIMIT;
    }

    emit_progress(ctx, RS_STAGE_PREPROCESS, 0, 0, 1, tile, 0.01);

    FeatherBlender blender;
    int32_t attempt = 0;
    const int fail_first = test_fail_first_tiles();

    while (true) {
        ++attempt;
        if (tile < 32 && attempt > 1) {
            set_error("tile size reduced below the safe minimum (%d px) "
                      "after %d attempts", tile, attempt - 1);
            return RS_ERR_OUT_OF_MEMORY;
        }
        TileLayout layout =
            compute_tile_layout(in_w, in_h, tile, overlap, context);
        blender.reset(out_w, out_h);
        emit_progress(ctx, RS_STAGE_INFERENCE, 0,
                      static_cast<int32_t>(layout.tiles.size()),
                      attempt, tile, 0.02);

        bool failed = false;
        bool restart_after_gpu_fallback = false;
        rs_status fail_status = RS_ERR_INFERENCE;
        int32_t done = 0;
        std::vector<uint8_t> tile_out;

        for (const Tile& t : layout.tiles) {
            if (ctx.cancel_flag != nullptr && *ctx.cancel_flag != 0) {
                set_error("cancelled by user");
                return RS_ERR_CANCELLED;
            }
            bool ok = false;
            try {
                const bool inject_failure =
                    attempt == 1 && fail_first > 0 && done < fail_first;
                if (inject_failure) {
                    throw std::bad_alloc();
                }
                ok = infer_region(net_, threads_, ctx.input->pixels,
                                  t.cw, t.ch, t.cy * stride + t.cx * 4,
                                  stride, ctx.job.scale, tile_out);
            } catch (const std::bad_alloc&) {
                set_error("out of memory while inferring a %dx%d tile "
                          "(attempt %d)", t.cw, t.ch, attempt);
                ok = false;
                fail_status = RS_ERR_OUT_OF_MEMORY;
            } catch (const std::exception& e) {
                set_error("inference exception: %s", e.what());
                ok = false;
            }
            if (!ok) {
                if (use_gpu_ && !gpu_fallback_used_) {
                    // A Vulkan error mid-run (device lost, unsupported
                    // layer path, driver fault): switch to the verified
                    // CPU backend once and restart the pass. Bounded by
                    // gpu_fallback_used_ — no infinite restarts.
                    if (fallback_to_cpu()) {
                        gpu_fallback_used_ = true;
                        emit_progress(ctx, RS_STAGE_INFERENCE, 0, 0,
                                      attempt + 1, tile, 0.02);
                        restart_after_gpu_fallback = true;
                        break;
                    }
                    fail_status = RS_ERR_ENGINE_INIT;
                }
                failed = true;
                break;
            }
            blender.accumulate(t, tile_out.data(),
                               t.cw * ctx.job.scale * 4, ctx.job.scale);
            ++done;
            const double frac =
                0.02 + 0.93 * (static_cast<double>(done) /
                                static_cast<double>(layout.tiles.size()));
            emit_progress(ctx, RS_STAGE_INFERENCE, done,
                          static_cast<int32_t>(layout.tiles.size()),
                          attempt, tile, frac);
        }

        if (restart_after_gpu_fallback) {
            continue; // fresh accumulator + CPU backend at loop top
        }

        if (!failed) {
            emit_progress(ctx, RS_STAGE_POSTPROCESS, done,
                          static_cast<int32_t>(layout.tiles.size()),
                          attempt, tile, 0.96);
            auto* image = static_cast<rs_image*>(std::malloc(sizeof(rs_image)));
            if (image == nullptr) {
                set_error("out of memory allocating the result header");
                return RS_ERR_OUT_OF_MEMORY;
            }
            const size_t buf_size =
                static_cast<size_t>(out_w) * static_cast<size_t>(out_h) * 4;
            image->pixels = static_cast<uint8_t*>(std::malloc(buf_size));
            if (image->pixels == nullptr) {
                std::free(image);
                set_error("out of memory allocating %zu bytes for the result",
                          buf_size);
                return RS_ERR_OUT_OF_MEMORY;
            }
            image->width = out_w;
            image->height = out_h;
            image->stride = out_w * 4;
            image->channels = 4;
            if (!blender.normalize_to(image->pixels, image->stride)) {
                rs_image_free(image);
                set_error("internal error: tile layout left coverage holes");
                return RS_ERR_INFERENCE;
            }
            emit_progress(ctx, RS_STAGE_DONE, done,
                          static_cast<int32_t>(layout.tiles.size()),
                          attempt, tile, 1.0);
            *out = image;
            return RS_OK;
        }

        if (fail_status != RS_ERR_OUT_OF_MEMORY) {
            // Non-memory inference errors are deterministic (e.g. a
            // wrong scale variant or a corrupt model): retrying with
            // smaller tiles cannot help, so fail fast with the real
            // cause instead of masking it as an OOM loop.
            return fail_status;
        }
        if (attempt > max_retries) {
            set_error("processing failed after %d recovery attempts "
                      "(last error: %s)", attempt - 1, last_error_buffer());
            return RS_ERR_OUT_OF_MEMORY;
        }
        // Recovery: halve the tile edge, keep overlap proportional.
        tile = std::max<int32_t>(32, tile / 2);
        overlap = std::max<int32_t>(8, overlap / 2);
        context = std::max<int32_t>(2, context / 2);
        emit_progress(ctx, RS_STAGE_INFERENCE, 0, 0, attempt + 1, tile, 0.02);
    }
}

} // namespace

// ------------------------------------------------------------------ //
// Factory used by the ABI layer.
// ------------------------------------------------------------------ //
std::unique_ptr<Engine> create_ncnn_engine(const rs_engine_options& opts) {
    return std::make_unique<NcnnEngine>(opts);
}

std::string engine_backend_name(const Engine* e) {
    if (e == nullptr) {
        return "null";
    }
    return e->compute_backend() == RS_COMPUTE_CPU ? "ncnn-cpu" : "ncnn-vulkan";
}

} // namespace rs
