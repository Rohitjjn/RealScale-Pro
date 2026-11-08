// RealScale-Pro — public C ABI implementation (see realscale.h).
//
// The ABI owns the engine instances and forwards to the internal
// rs::Engine interface. All handles are validated; all failures set
// the thread-local error buffer. No C++ type crosses the boundary.
//
// Copyright 2026 RealScale-Pro contributors. MIT License.
#include <cstdlib>
#include <cstring>
#include <memory>
#include <new>

#include "engine.h"
#include "error.h"
#include "realscale/realscale.h"

namespace {

rs::Engine* as_engine(rs_engine* handle) {
    return reinterpret_cast<rs::Engine*>(handle);
}

} // namespace

extern "C" {

int32_t rs_abi_version(void) {
    return RS_ABI_VERSION;
}

rs_engine* rs_engine_create(const rs_engine_options* options) {
    if (options == nullptr) {
        rs::set_error("rs_engine_create: options is NULL");
        return nullptr;
    }
    if (options->abi_version != RS_ABI_VERSION) {
        rs::set_error("rs_engine_create: abi_version mismatch (library %d, caller %d)",
                          RS_ABI_VERSION, options->abi_version);
        return nullptr;
    }
    try {
        // The only implemented backend today is ncnn (CPU or GPU when
        // built with Vulkan). Additional backends plug in here.
        std::unique_ptr<rs::Engine> engine =
            rs::create_ncnn_engine(*options);
        return reinterpret_cast<rs_engine*>(engine.release());
    } catch (const std::bad_alloc&) {
        rs::set_error("rs_engine_create: out of memory");
        return nullptr;
    } catch (const std::exception& e) {
        rs::set_error("rs_engine_create: %s", e.what());
        return nullptr;
    }
}

void rs_engine_destroy(rs_engine* engine) {
    delete as_engine(engine);
}

rs_status rs_engine_load_model(rs_engine* engine,
                               const char* param_path,
                               const char* bin_path,
                               rs_model_info* out_info) {
    if (engine == nullptr || param_path == nullptr || bin_path == nullptr) {
        rs::set_error("rs_engine_load_model: NULL argument");
        return RS_ERR_INVALID_ARG;
    }
    return as_engine(engine)->load_model(param_path, bin_path, out_info);
}

rs_status rs_engine_model_info(const rs_engine* engine, rs_model_info* out_info) {
    if (engine == nullptr) {
        rs::set_error("rs_engine_model_info: engine is NULL");
        return RS_ERR_INVALID_ARG;
    }
    return as_engine(const_cast<rs_engine*>(engine))->model_info(out_info);
}

rs_status rs_engine_device_info(const rs_engine* engine, rs_device_info* out_info) {
    if (engine == nullptr) {
        rs::set_error("rs_engine_device_info: engine is NULL");
        return RS_ERR_INVALID_ARG;
    }
    return as_engine(const_cast<rs_engine*>(engine))->device_info(out_info);
}

int64_t rs_estimate_peak_memory(int32_t in_w, int32_t in_h,
                                int32_t scale, int32_t tile_size) {
    if (in_w <= 0 || in_h <= 0 || scale < 2 || scale > 4) {
        return -1;
    }
    const int32_t tile = tile_size > 0 ? tile_size : 128;
    const int32_t out_tile = tile * scale;
    // Model activations dominate: input tile (RGB float per channel,
    // conservative 3x for ncnn workspace) + output tile + a couple of
    // intermediate tiles held during blending.
    const int64_t in_tile_bytes =
        static_cast<int64_t>(tile) * tile * 3 * 4 * 3;
    const int64_t out_tile_bytes =
        static_cast<int64_t>(out_tile) * out_tile * 3 * 4;
    // Full-output float accumulation + final RGBA + input RGBA copy.
    const int64_t acc_bytes =
        static_cast<int64_t>(in_w) * in_h * scale * scale * 4 * 4;
    const int64_t out_rgba =
        static_cast<int64_t>(in_w) * in_h * scale * scale * 4;
    const int64_t in_rgba = static_cast<int64_t>(in_w) * in_h * 4;
    const int64_t weights_headroom = 200LL * 1024 * 1024; // 200 MB model cap
    return in_tile_bytes * 2 + out_tile_bytes * 3 + acc_bytes + out_rgba +
           in_rgba + weights_headroom;
}

rs_status rs_run(rs_engine* engine,
                 const rs_image* input,
                 const rs_job_desc* job,
                 rs_image** out_output,
                 rs_progress_cb progress,
                 void* progress_user,
                 const volatile int32_t* cancel_flag) {
    if (engine == nullptr) {
        rs::set_error("rs_run: engine is NULL");
        return RS_ERR_INVALID_ARG;
    }
    rs::RunContext ctx{};
    ctx.input = input;
    ctx.progress = progress;
    ctx.progress_user = progress_user;
    ctx.cancel_flag = cancel_flag;
    if (job != nullptr) {
        ctx.job = *job;
    } else {
        ctx.job = rs_job_desc{};
        ctx.job.scale = 4;
        ctx.job.tile_size = 0;
        ctx.job.tile_overlap = 0;
        ctx.job.context_margin = 0;
        ctx.job.max_retries = 3;
    }
    return as_engine(engine)->run(ctx, out_output);
}

void rs_image_free(rs_image* image) {
    if (image == nullptr) {
        return;
    }
    std::free(image->pixels);
    std::free(image);
}

const char* rs_last_error(void) {
    return rs::last_error_buffer();
}

} // extern "C"
