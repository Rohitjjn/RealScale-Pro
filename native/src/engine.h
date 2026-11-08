// RealScale-Pro — internal engine interface.
//
// The public C ABI (realscale.h) is implemented on top of this thin
// internal interface so that additional backends (e.g. an ONNX Runtime
// engine) can be added without touching the ABI. Exactly one backend is
// implemented today: the ncnn engine (engine_ncnn.cpp). OnnxEngine is a
// documented extension point, NOT an implemented component — see
// docs/native-engine-design.md.
//
// Copyright 2026 RealScale-Pro contributors. MIT License.
#ifndef RS_ENGINE_H
#define RS_ENGINE_H

#include <cstdint>
#include <memory>
#include <string>

#include "realscale/realscale.h"

namespace rs {

struct RunContext {
    const rs_image* input = nullptr;
    rs_job_desc job{};
    rs_progress_cb progress = nullptr;
    void* progress_user = nullptr;
    const volatile int32_t* cancel_flag = nullptr;
};

class Engine {
public:
    virtual ~Engine() = default;

    virtual rs_status load_model(const std::string& param_path,
                                 const std::string& bin_path,
                                 rs_model_info* out_info) = 0;
    virtual rs_status model_info(rs_model_info* out_info) const = 0;
    virtual rs_status device_info(rs_device_info* out_info) const = 0;

    // Runs tiled inference. On success, `out` is a native-allocated
    // RGBA image of size (input->width * scale) x (input->height * scale).
    virtual rs_status run(const RunContext& ctx, rs_image** out) = 0;

    virtual rs_compute compute_backend() const = 0;
};

// Factory used by the ABI layer. Implemented by engine_ncnn.cpp when
// the ncnn backend is available, otherwise by engine_stub.cpp.
std::unique_ptr<Engine> create_ncnn_engine(const rs_engine_options& opts);

// Platform/backend string for logs, e.g. "ncnn-cpu".
std::string engine_backend_name(const Engine* e);

} // namespace rs

#endif // RS_ENGINE_H
