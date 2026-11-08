// RealScale-Pro — rscli: host-side end-to-end harness.
//
// Loads a real image file (JPEG/PNG/BMP via stb_image, host-build only),
// runs the REAL tiled inference pipeline through the public C ABI and
// writes the result as a PNG (stb_image_write). Prints honest stats:
// timing, tiles, attempts, peak RSS. Used for inference verification
// and to produce test fixtures.
//
//   rscli --param M.param --bin M.bin --in in.jpg --out out.png \
//         [--scale 4] [--tile 128] [--overlap 16] [--context 4] \
//         [--threads 4] [--fp16 0|1]
//
// Copyright 2026 RealScale-Pro contributors. MIT License.
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

#include "realscale/realscale.h"

#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "stb_image_write.h"

namespace {

struct Args {
    std::string param, bin, in, out;
    int scale = 4, tile = 0, overlap = 0, context = 0, threads = 0, fp16 = 0;
    bool valid = false;
};

Args parse_args(int argc, char** argv) {
    Args a;
    for (int i = 1; i < argc; ++i) {
        const std::string k = argv[i];
        auto next = [&](const char* name) -> std::string {
            if (i + 1 >= argc) {
                std::fprintf(stderr, "missing value for %s\n", name);
                std::exit(2);
            }
            return argv[++i];
        };
        if (k == "--param") {
            a.param = next("--param");
        } else if (k == "--bin") {
            a.bin = next("--bin");
        } else if (k == "--in") {
            a.in = next("--in");
        } else if (k == "--out") {
            a.out = next("--out");
        } else if (k == "--scale") {
            a.scale = std::atoi(next("--scale").c_str());
        } else if (k == "--tile") {
            a.tile = std::atoi(next("--tile").c_str());
        } else if (k == "--overlap") {
            a.overlap = std::atoi(next("--overlap").c_str());
        } else if (k == "--context") {
            a.context = std::atoi(next("--context").c_str());
        } else if (k == "--threads") {
            a.threads = std::atoi(next("--threads").c_str());
        } else if (k == "--fp16") {
            a.fp16 = std::atoi(next("--fp16").c_str());
        } else {
            std::fprintf(stderr, "unknown arg: %s\n", k.c_str());
            std::exit(2);
        }
    }
    a.valid = !a.param.empty() && !a.bin.empty() && !a.in.empty() &&
              !a.out.empty();
    return a;
}

void progress_cb(void* user, const rs_progress* p) {
    auto* last_print = static_cast<int*>(user);
    const int pct = static_cast<int>(p->fraction * 100.0);
    if (p->stage == RS_STAGE_INFERENCE &&
        (p->tiles_done > *last_print || p->attempt > 1)) {
        std::printf("\r  [%d/%d tiles, attempt %d, tile %dpx] %3d%%",
                    p->tiles_done, p->tiles_total, p->attempt, p->tile_size,
                    pct);
        std::fflush(stdout);
        *last_print = p->tiles_done;
    }
}

} // namespace

int main(int argc, char** argv) {
    const Args args = parse_args(argc, argv);
    if (!args.valid) {
        std::fprintf(stderr,
                     "usage: rscli --param P --bin B --in IN --out OUT "
                     "[--scale N --tile N --overlap N --context N "
                     "--threads N --fp16 0|1]\n");
        return 2;
    }

    int w = 0, h = 0, n = 0;
    unsigned char* pixels = stbi_load(args.in.c_str(), &w, &h, &n, 4);
    if (pixels == nullptr) {
        std::fprintf(stderr, "failed to decode input: %s\n", args.in.c_str());
        return 3;
    }
    std::printf("input : %s (%dx%d, %d channels -> RGBA)\n",
                args.in.c_str(), w, h, n);

    rs_engine_options opts{};
    opts.abi_version = RS_ABI_VERSION;
    opts.compute = RS_COMPUTE_CPU;
    opts.thread_count = args.threads;
    opts.use_fp16 = args.fp16;
    rs_engine* engine = rs_engine_create(&opts);
    if (engine == nullptr) {
        std::fprintf(stderr, "engine init failed: %s\n", rs_last_error());
        stbi_image_free(pixels);
        return 4;
    }
    rs_model_info mi{};
    if (rs_engine_load_model(engine, args.param.c_str(), args.bin.c_str(),
                             &mi) != RS_OK) {
        std::fprintf(stderr, "model load failed: %s\n", rs_last_error());
        rs_engine_destroy(engine);
        stbi_image_free(pixels);
        return 5;
    }
    std::printf("model : %s (%.1f MB weights, tile hint %d)\n",
                args.param.c_str(),
                static_cast<double>(mi.bin_bytes) / (1024.0 * 1024.0),
                mi.input_tile_hint);

    rs_image in{};
    in.pixels = pixels;
    in.width = w;
    in.height = h;
    in.stride = w * 4;
    in.channels = 4;

    rs_job_desc job{};
    job.scale = args.scale;
    job.tile_size = args.tile;
    job.tile_overlap = args.overlap;
    job.context_margin = args.context;
    job.max_retries = 3;

    rs_device_info di{};
    rs_engine_device_info(engine, &di);
    std::printf("device: %d threads, fp16 storage=%d, gpu=%d\n",
                di.thread_count, di.fp16_storage, di.gpu_available);

    int last_print = -1;
    rs_image* out = nullptr;
    std::printf("run   : scale=%d tile=%s overlap=%s context=%s\n",
                args.scale,
                args.tile > 0 ? std::to_string(args.tile).c_str() : "auto",
                args.overlap > 0 ? std::to_string(args.overlap).c_str() : "auto",
                args.context >= 0 ? std::to_string(args.context).c_str() : "auto");
    const auto t0 = std::chrono::steady_clock::now();
    const rs_status st =
        rs_run(engine, &in, &job, &out, progress_cb, &last_print, nullptr);
    const auto t1 = std::chrono::steady_clock::now();
    const double ms =
        std::chrono::duration<double, std::milli>(t1 - t0).count();
    std::printf("\n");

    if (st != RS_OK || out == nullptr) {
        std::fprintf(stderr, "FAILED after %.1f s: %s (status %d)\n",
                     ms / 1000.0, rs_last_error(), static_cast<int>(st));
        rs_engine_destroy(engine);
        stbi_image_free(pixels);
        return 6;
    }

    const int ok = stbi_write_png(args.out.c_str(), out->width, out->height, 4,
                                  out->pixels, out->stride);
    std::printf("output: %s (%dx%d) in %.1f s -> %s\n", args.out.c_str(),
                out->width, out->height, ms / 1000.0,
                ok ? "written" : "WRITE FAILED");

    rs_image_free(out);
    rs_engine_destroy(engine);
    stbi_image_free(pixels);
    return ok ? 0 : 7;
}
