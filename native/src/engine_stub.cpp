// RealScale-Pro — interface-only engine stub.
//
// Compiled only when the ncnn backend is unavailable (RS_HAS_NCNN
// undefined). The ABI layer still builds and reports honest
// RS_ERR_UNSUPPORTED failures so Dart-side wiring, planning and UI can
// be developed and tested on machines without the inference backend.
//
// Copyright 2026 RealScale-Pro contributors. MIT License.
#include "engine.h"
#include "error.h"

#include <memory>

namespace rs {

std::unique_ptr<Engine> create_ncnn_engine(const rs_engine_options& opts) {
    (void)opts;
    set_error("this build of the native engine was compiled WITHOUT the "
              "ncnn backend; rebuild with RS_BUILD_NCNN=ON");
    return nullptr;
}

std::string engine_backend_name(const Engine* e) {
    (void)e;
    return "none";
}

} // namespace rs
