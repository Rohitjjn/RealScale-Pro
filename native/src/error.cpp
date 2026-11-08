// RealScale-Pro — thread-local error reporting implementation.
//
// Copyright 2026 RealScale-Pro contributors. MIT License.
#include "error.h"

#include <cstdarg>
#include <cstdio>
#include <cstring>

namespace rs {

namespace {
thread_local char t_error[512] = {0};
}

void set_error(const char* fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(t_error, sizeof(t_error), fmt, ap);
    va_end(ap);
}

const char* last_error_buffer() {
    return t_error;
}

void clear_error() {
    t_error[0] = '\0';
}

} // namespace rs
