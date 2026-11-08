// RealScale-Pro — ABI, engine lifecycle and REAL inference tests.
//
// The inference tests require a model; the model paths are passed via
// environment variables so CI / local runs can choose which models to
// verify. Tests whose model is not provided are SKIPPED (reported, not
// silently passed).
//
//   RS_TEST_MODEL_PARAM / RS_TEST_MODEL_BIN  — ncnn model pair
//   RS_TEST_MODEL_SCALE                      — expected scale (2..4)
//
// Copyright 2026 RealScale-Pro contributors. MIT License.
#include "rs_test.h"
#include "realscale/realscale.h"

#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

namespace {

struct ProgressLog {
    std::atomic<int> calls{0};
    std::atomic<int> last_stage{0};
    std::atomic<int> last_tiles_done{0};
    std::atomic<int> last_tiles_total{0};
    std::atomic<int> max_attempt{1};
    std::atomic<int> last_tile_size{0};
    std::atomic<double> max_fraction{0.0};
};

void progress_cb(void* user, const rs_progress* p) {
    auto* log = static_cast<ProgressLog*>(user);
    log->calls.fetch_add(1);
    log->last_stage.store(p->stage);
    log->last_tiles_done.store(p->tiles_done);
    log->last_tiles_total.store(p->tiles_total);
    int a = p->attempt;
    int cur = log->max_attempt.load();
    while (a > cur && !log->max_attempt.compare_exchange_weak(cur, a)) {
    }
    log->last_tile_size.store(p->tile_size);
    double f = p->fraction;
    double curf = log->max_fraction.load();
    while (f > curf && !log->max_fraction.compare_exchange_weak(curf, f)) {
    }
}

std::vector<uint8_t> make_gradient_rgba(int32_t w, int32_t h) {
    std::vector<uint8_t> px(static_cast<size_t>(w) * h * 4);
    for (int32_t y = 0; y < h; ++y) {
        for (int32_t x = 0; x < w; ++x) {
            uint8_t* p = px.data() + (static_cast<size_t>(y) * w + x) * 4;
            p[0] = static_cast<uint8_t>(255 * x / std::max(w - 1, 1));
            p[1] = static_cast<uint8_t>(255 * y / std::max(h - 1, 1));
            p[2] = static_cast<uint8_t>((x + y) % 256);
            p[3] = 255;
        }
    }
    return px;
}

bool env_model(std::string& param, std::string& bin, int& scale) {
    const char* p = std::getenv("RS_TEST_MODEL_PARAM");
    const char* b = std::getenv("RS_TEST_MODEL_BIN");
    const char* s = std::getenv("RS_TEST_MODEL_SCALE");
    if (p == nullptr || b == nullptr) {
        return false;
    }
    param = p;
    bin = b;
    scale = s != nullptr ? std::atoi(s) : 4;
    return true;
}

} // namespace

RS_TEST(abi_version_matches) {
    RS_ASSERT_EQ(rs_abi_version(), RS_ABI_VERSION);
    return true;
}

RS_TEST(abi_create_rejects_null_and_bad_version) {
    RS_ASSERT(rs_engine_create(nullptr) == nullptr);
    rs_engine_options opts{};
    opts.abi_version = 1; // wrong
    RS_ASSERT(rs_engine_create(&opts) == nullptr);
    RS_ASSERT(std::strlen(rs_last_error()) > 0);
    return true;
}

RS_TEST(abi_engine_lifecycle) {
    rs_engine_options opts{};
    opts.abi_version = RS_ABI_VERSION;
    opts.compute = RS_COMPUTE_CPU;
    opts.thread_count = 1;
    opts.use_fp16 = 0;
    rs_engine* e = rs_engine_create(&opts);
    RS_ASSERT(e != nullptr);
    rs_device_info di{};
    RS_ASSERT_EQ(rs_engine_device_info(e, &di), RS_OK);
    RS_ASSERT_EQ(di.using_gpu, 0);
    RS_ASSERT_EQ(di.thread_count, 1);
    // No model loaded yet: model_info reports not-loaded; run fails.
    rs_model_info mi{};
    RS_ASSERT_EQ(rs_engine_model_info(e, &mi), RS_ERR_MODEL_INVALID);
    RS_ASSERT_EQ(mi.loaded, 0);
    const std::vector<uint8_t> img = make_gradient_rgba(16, 16);
    rs_image in{};
    in.pixels = const_cast<uint8_t*>(img.data());
    in.width = 16;
    in.height = 16;
    in.stride = 64;
    in.channels = 4;
    rs_job_desc job{};
    job.scale = 4;
    rs_image* out = nullptr;
    RS_ASSERT_EQ(rs_run(e, &in, &job, &out, nullptr, nullptr, nullptr),
                 RS_ERR_MODEL_INVALID);
    RS_ASSERT(out == nullptr);
    // Invalid arguments.
    RS_ASSERT_EQ(rs_run(nullptr, &in, &job, &out, nullptr, nullptr, nullptr),
                 RS_ERR_INVALID_ARG);
    job.scale = 9;
    RS_ASSERT_EQ(rs_run(e, &in, &job, &out, nullptr, nullptr, nullptr),
                 RS_ERR_INVALID_ARG);
    job.scale = 4;
    in.channels = 3;
    RS_ASSERT_EQ(rs_run(e, &in, &job, &out, nullptr, nullptr, nullptr),
                 RS_ERR_INVALID_ARG);
    in.channels = 4;
    rs_engine_destroy(e);
    return true;
}

RS_TEST(abi_estimate_peak_memory) {
    const int64_t small = rs_estimate_peak_memory(64, 64, 4, 64);
    const int64_t large = rs_estimate_peak_memory(1024, 1024, 4, 64);
    RS_ASSERT(small > 0 && large > small);
    RS_ASSERT_EQ(rs_estimate_peak_memory(0, 10, 4, 64), -1);
    RS_ASSERT_EQ(rs_estimate_peak_memory(10, 10, 1, 64), -1);
    // Smaller tiles must reduce the estimate.
    RS_ASSERT(rs_estimate_peak_memory(512, 512, 4, 32) <
              rs_estimate_peak_memory(512, 512, 4, 128));
    return true;
}

RS_TEST(abi_load_model_bad_paths) {
    rs_engine_options opts{};
    opts.abi_version = RS_ABI_VERSION;
    rs_engine* e = rs_engine_create(&opts);
    RS_ASSERT(e != nullptr);
    RS_ASSERT_EQ(rs_engine_load_model(e, "/nonexistent.param", "/nonexistent.bin", nullptr),
                 RS_ERR_MODEL_LOAD);
    RS_ASSERT(std::strlen(rs_last_error()) > 0);
    rs_engine_destroy(e);
    return true;
}

// ------------------------------------------------------------------ //
// REAL inference tests (require a model via env vars).
// ------------------------------------------------------------------ //

RS_TEST(engine_real_inference_full_pipeline) {
    std::string param, bin;
    int scale = 4;
    if (!env_model(param, bin, scale)) {
        std::printf("  SKIP (no RS_TEST_MODEL_PARAM/RS_TEST_MODEL_BIN)\n");
        return true;
    }
    rs_engine_options opts{};
    opts.abi_version = RS_ABI_VERSION;
    opts.compute = RS_COMPUTE_CPU;
    opts.thread_count = 2;
    opts.use_fp16 = 0;
    rs_engine* e = rs_engine_create(&opts);
    RS_ASSERT(e != nullptr);
    rs_model_info mi{};
    RS_ASSERT_EQ(rs_engine_load_model(e, param.c_str(), bin.c_str(), &mi),
                 RS_OK);
    RS_ASSERT_EQ(mi.loaded, 1);
    RS_ASSERT(mi.bin_bytes > 0 && mi.param_bytes > 0);

    // 100x76 (odd size on purpose), tiled 64 -> multiple tiles.
    const int32_t W = 100, H = 76;
    const std::vector<uint8_t> img = make_gradient_rgba(W, H);
    rs_image in{};
    in.pixels = const_cast<uint8_t*>(img.data());
    in.width = W;
    in.height = H;
    in.stride = W * 4;
    in.channels = 4;
    rs_job_desc job{};
    job.scale = scale;
    job.tile_size = 64;
    job.tile_overlap = 8;
    job.context_margin = 2;
    job.max_retries = 3;
    ProgressLog log;
    rs_image* out = nullptr;
    const rs_status st = rs_run(e, &in, &job, &out, progress_cb, &log, nullptr);
    RS_ASSERT_EQ(st, RS_OK);
    RS_ASSERT(out != nullptr);
    RS_ASSERT_EQ(out->width, W * scale);
    RS_ASSERT_EQ(out->height, H * scale);
    RS_ASSERT_EQ(out->channels, 4);
    // Progress reached completion through real events.
    RS_ASSERT(log.last_stage.load() == RS_STAGE_DONE);
    RS_ASSERT(log.last_tiles_total.load() > 1); // actually tiled
    RS_ASSERT(log.max_fraction.load() >= 0.999);
    // Real inference must CHANGE the pixels (upscaled, not copied).
    int64_t diff = 0;
    for (int32_t y = 0; y < H * scale; y += 13) {
        for (int32_t x = 0; x < W * scale; x += 7) {
            const uint8_t* o = out->pixels +
                               (static_cast<size_t>(y) * out->stride + x * 4);
            const uint8_t* i = img.data() +
                               (static_cast<size_t>(y / scale) * in.stride +
                                (x / scale) * 4);
            diff += std::abs(static_cast<int>(o[0]) - i[0]) +
                    std::abs(static_cast<int>(o[1]) - i[1]) +
                    std::abs(static_cast<int>(o[2]) - i[2]);
        }
    }
    RS_ASSERT(diff > 0); // output is not a pixel copy of the input
    rs_image_free(out);
    rs_engine_destroy(e);
    return true;
}

RS_TEST(engine_real_inference_tiled_matches_single_tile) {
    std::string param, bin;
    int scale = 4;
    if (!env_model(param, bin, scale)) {
        std::printf("  SKIP (no RS_TEST_MODEL_PARAM/RS_TEST_MODEL_BIN)\n");
        return true;
    }
    rs_engine_options opts{};
    opts.abi_version = RS_ABI_VERSION;
    opts.thread_count = 2;
    opts.use_fp16 = 0;
    rs_engine* e = rs_engine_create(&opts);
    RS_ASSERT_EQ(rs_engine_load_model(e, param.c_str(), bin.c_str(), nullptr),
                 RS_OK);
    const int32_t W = 60, H = 52;
    const std::vector<uint8_t> img = make_gradient_rgba(W, H);
    rs_image in{};
    in.pixels = const_cast<uint8_t*>(img.data());
    in.width = W;
    in.height = H;
    in.stride = W * 4;
    in.channels = 4;

    rs_image* single = nullptr;
    rs_job_desc j1{};
    j1.scale = scale;
    j1.tile_size = 128; // whole image in one tile
    RS_ASSERT_EQ(rs_run(e, &in, &j1, &single, nullptr, nullptr, nullptr), RS_OK);

    rs_image* tiled = nullptr;
    rs_job_desc j2{};
    j2.scale = scale;
    j2.tile_size = 32; // forces a 2x2+ grid
    j2.tile_overlap = 8;
    j2.context_margin = 4;
    RS_ASSERT_EQ(rs_run(e, &in, &j2, &tiled, nullptr, nullptr, nullptr), RS_OK);

    RS_ASSERT_EQ(single->width, tiled->width);
    RS_ASSERT_EQ(single->height, tiled->height);
    // The tiled result must be close to the single-tile result — the
    // whole point of seam-free blending. Bound: 12/255 per channel.
    int max_dev = 0;
    double mean_dev = 0;
    int64_t count = 0;
    for (int32_t y = 0; y < tiled->height; ++y) {
        for (int32_t x = 0; x < tiled->width; ++x) {
            const uint8_t* a = single->pixels +
                               (static_cast<size_t>(y) * single->stride + x * 4);
            const uint8_t* b = tiled->pixels +
                               (static_cast<size_t>(y) * tiled->stride + x * 4);
            for (int c = 0; c < 3; ++c) {
                const int d = std::abs(static_cast<int>(a[c]) - b[c]);
                if (d > max_dev) {
                    max_dev = d;
                }
                mean_dev += d;
                ++count;
            }
        }
    }
    mean_dev /= static_cast<double>(count);
    std::printf("  tiled-vs-single: max=%d mean=%.3f\n", max_dev, mean_dev);
    RS_ASSERT(max_dev <= 12);
    RS_ASSERT(mean_dev <= 2.0);
    rs_image_free(single);
    rs_image_free(tiled);
    rs_engine_destroy(e);
    return true;
}

RS_TEST(engine_real_inference_cancellation) {
    std::string param, bin;
    int scale = 4;
    if (!env_model(param, bin, scale)) {
        std::printf("  SKIP (no RS_TEST_MODEL_PARAM/RS_TEST_MODEL_BIN)\n");
        return true;
    }
    rs_engine_options opts{};
    opts.abi_version = RS_ABI_VERSION;
    opts.thread_count = 1;
    opts.use_fp16 = 0;
    rs_engine* e = rs_engine_create(&opts);
    RS_ASSERT_EQ(rs_engine_load_model(e, param.c_str(), bin.c_str(), nullptr),
                 RS_OK);
    const int32_t W = 200, H = 200; // enough tiles to cancel mid-run
    const std::vector<uint8_t> img = make_gradient_rgba(W, H);
    rs_image in{};
    in.pixels = const_cast<uint8_t*>(img.data());
    in.width = W;
    in.height = H;
    in.stride = W * 4;
    in.channels = 4;
    rs_job_desc job{};
    job.scale = scale;
    job.tile_size = 48;
    job.tile_overlap = 8;
    volatile int32_t cancel = 0;
    struct CancelAfter {
        ProgressLog* log;
        volatile int32_t* flag;
    } ctx{new ProgressLog(), &cancel};
    // Cancel as soon as the first tile completes.
    auto cancel_cb = +[](void* user, const rs_progress* p) {
        auto* c = static_cast<CancelAfter*>(user);
        if (p->stage == RS_STAGE_INFERENCE && p->tiles_done >= 1) {
            *c->flag = 1;
        }
        progress_cb(c->log, p);
    };
    rs_image* out = nullptr;
    const rs_status st = rs_run(e, &in, &job, &out, cancel_cb, &ctx, &cancel);
    delete ctx.log;
    RS_ASSERT_EQ(st, RS_ERR_CANCELLED);
    RS_ASSERT(out == nullptr);
    rs_engine_destroy(e);
    return true;
}

RS_TEST(engine_recovery_after_memory_failure) {
    std::string param, bin;
    int scale = 4;
    if (!env_model(param, bin, scale)) {
        std::printf("  SKIP (no RS_TEST_MODEL_PARAM/RS_TEST_MODEL_BIN)\n");
        return true;
    }
    // RS_TEST_FAIL_TILES injects bad_alloc on the first attempt (test
    // builds only). The engine must recover with smaller tiles and
    // still produce a complete, correct image.
    std::string fail = std::getenv("RS_TEST_FAIL_TILES") != nullptr
                           ? std::getenv("RS_TEST_FAIL_TILES")
                           : "";
    if (fail.empty()) {
        std::printf("  SKIP (set RS_TEST_FAIL_TILES=2 to exercise recovery)\n");
        return true;
    }
    rs_engine_options opts{};
    opts.abi_version = RS_ABI_VERSION;
    opts.thread_count = 1;
    rs_engine* e = rs_engine_create(&opts);
    RS_ASSERT_EQ(rs_engine_load_model(e, param.c_str(), bin.c_str(), nullptr),
                 RS_OK);
    const int32_t W = 100, H = 80;
    const std::vector<uint8_t> img = make_gradient_rgba(W, H);
    rs_image in{};
    in.pixels = const_cast<uint8_t*>(img.data());
    in.width = W;
    in.height = H;
    in.stride = W * 4;
    in.channels = 4;
    rs_job_desc job{};
    job.scale = scale;
    job.tile_size = 64;
    job.max_retries = 3;
    ProgressLog log;
    rs_image* out = nullptr;
    RS_ASSERT_EQ(rs_run(e, &in, &job, &out, progress_cb, &log, nullptr),
                 RS_OK);
    RS_ASSERT(out != nullptr);
    RS_ASSERT_EQ(out->width, W * scale);
    RS_ASSERT(log.max_attempt.load() >= 2); // recovery really happened
    RS_ASSERT(log.last_stage.load() == RS_STAGE_DONE);
    rs_image_free(out);
    rs_engine_destroy(e);
    return true;
}

RS_TEST(engine_wrong_scale_variant_rejected) {
    std::string param, bin;
    int scale = 4;
    if (!env_model(param, bin, scale)) {
        std::printf("  SKIP (no RS_TEST_MODEL_PARAM/RS_TEST_MODEL_BIN)\n");
        return true;
    }
    rs_engine_options opts{};
    opts.abi_version = RS_ABI_VERSION;
    rs_engine* e = rs_engine_create(&opts);
    RS_ASSERT_EQ(rs_engine_load_model(e, param.c_str(), bin.c_str(), nullptr),
                 RS_OK);
    const std::vector<uint8_t> img = make_gradient_rgba(24, 24);
    rs_image in{};
    in.pixels = const_cast<uint8_t*>(img.data());
    in.width = 24;
    in.height = 24;
    in.stride = 24 * 4;
    in.channels = 4;
    rs_job_desc job{};
    job.scale = scale == 4 ? 2 : 4; // deliberately mismatched
    rs_image* out = nullptr;
    const rs_status st = rs_run(e, &in, &job, &out, nullptr, nullptr, nullptr);
    RS_ASSERT(st == RS_ERR_INFERENCE || st == RS_ERR_MODEL_INVALID);
    RS_ASSERT(out == nullptr);
    RS_ASSERT(std::strstr(rs_last_error(), "scale") != nullptr);
    rs_engine_destroy(e);
    return true;
}

int main() {
    return rs::run_all_tests() ? 0 : 1;
}
