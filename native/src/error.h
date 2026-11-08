// RealScale-Pro — thread-local error reporting shared by the native
// modules and the C ABI.
//
// Copyright 2026 RealScale-Pro contributors. MIT License.
#ifndef RS_ERROR_H
#define RS_ERROR_H

namespace rs {

// Sets the thread-local error message (printf-style). Truncates safely.
void set_error(const char* fmt, ...) __attribute__((format(printf, 1, 2)));

// Returns the thread-local error buffer. Never NULL. Valid until the
// next native call on the same thread.
const char* last_error_buffer();

// Clears the thread-local error message.
void clear_error();

} // namespace rs

#endif // RS_ERROR_H
