// RealScale-Pro — feather blending implementation.
//
// Copyright 2026 RealScale-Pro contributors. MIT License.
#include "feather_blend.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace rs {

float smoothstep_ramp(float t, float ramp) {
    if (ramp <= 0.0f) {
        return 1.0f;
    }
    if (t <= 0.0f) {
        return 0.0f;
    }
    if (t >= ramp) {
        return 1.0f;
    }
    const float x = t / ramp;
    return x * x * (3.0f - 2.0f * x);
}

void FeatherBlender::reset(int32_t out_w, int32_t out_h) {
    out_w_ = std::max(out_w, 0);
    out_h_ = std::max(out_h, 0);
    const size_t n = static_cast<size_t>(out_w_) * static_cast<size_t>(out_h_);
    acc_.assign(n * 4, 0.0f);
    wsum_.assign(n, 0.0f);
}

void FeatherBlender::accumulate(const Tile& tile,
                                const uint8_t* tile_output,
                                int32_t tile_output_stride,
                                int32_t scale) {
    if (scale <= 0 || tile_output == nullptr) {
        return;
    }
    // Source offset of the core within the tile's context-region output.
    const int32_t src_ox = core_offset(tile.x, tile.cx, scale);
    const int32_t src_oy = core_offset(tile.y, tile.cy, scale);
    // Destination offset of the core within the full output image.
    const int32_t dst_ox = tile.x * scale;
    const int32_t dst_oy = tile.y * scale;
    const int32_t core_w = tile.w * scale;
    const int32_t core_h = tile.h * scale;
    if (core_w <= 0 || core_h <= 0) {
        return;
    }

    // Feather ramp widths from the ACTUAL per-side overlap of this tile
    // (layout computes them; even stride distribution may change them
    // from the requested overlap).
    const float fw = static_cast<float>(tile.ov_l) * scale;
    const float fe = static_cast<float>(tile.ov_r) * scale;
    const float fn = static_cast<float>(tile.ov_t) * scale;
    const float fs = static_cast<float>(tile.ov_b) * scale;

    for (int32_t dy = 0; dy < core_h; ++dy) {
        const int32_t oy = dst_oy + dy;
        if (oy < 0 || oy >= out_h_) {
            continue;
        }
        float* acc_row = acc_.data() +
                         (static_cast<size_t>(oy) * out_w_) * 4;
        float* w_row = wsum_.data() + static_cast<size_t>(oy) * out_w_;

        const uint8_t* src = tile_output +
                             static_cast<size_t>(src_oy + dy) * tile_output_stride +
                             static_cast<size_t>(src_ox) * 4;
        const float wy = std::min(smoothstep_ramp(static_cast<float>(dy), fn),
                                  smoothstep_ramp(static_cast<float>(core_h - 1 - dy), fs));

        for (int32_t dx = 0; dx < core_w; ++dx) {
            const int32_t ox = dst_ox + dx;
            if (ox < 0 || ox >= out_w_) {
                continue;
            }
            const float wx = std::min(smoothstep_ramp(static_cast<float>(dx), fw),
                                      smoothstep_ramp(static_cast<float>(core_w - 1 - dx), fe));
            const float w = wx * wy;
            const size_t si = static_cast<size_t>(dx) * 4;
            const size_t di = static_cast<size_t>(ox) * 4;
            acc_row[di + 0] += w * src[si + 0];
            acc_row[di + 1] += w * src[si + 1];
            acc_row[di + 2] += w * src[si + 2];
            acc_row[di + 3] += w * src[si + 3];
            w_row[ox] += w;
        }
    }
}

bool FeatherBlender::normalize_to(uint8_t* out, int32_t out_stride) const {
    if (out == nullptr) {
        return false;
    }
    for (int32_t y = 0; y < out_h_; ++y) {
        const float* acc_row = acc_.data() + (static_cast<size_t>(y) * out_w_) * 4;
        const float* w_row = wsum_.data() + static_cast<size_t>(y) * out_w_;
        uint8_t* dst = out + static_cast<size_t>(y) * out_stride;
        for (int32_t x = 0; x < out_w_; ++x) {
            const float w = w_row[x];
            if (w <= 0.0f) {
                return false; // coverage hole — layout invariant broken
            }
            const float inv = 1.0f / w;
            const size_t i = static_cast<size_t>(x) * 4;
            const auto quant = [inv](float v) -> uint8_t {
                const float q = v * inv + 0.5f;
                return static_cast<uint8_t>(q < 0.0f ? 0.0f : (q > 255.0f ? 255.0f : q));
            };
            dst[i + 0] = quant(acc_row[i + 0]);
            dst[i + 1] = quant(acc_row[i + 1]);
            dst[i + 2] = quant(acc_row[i + 2]);
            dst[i + 3] = quant(acc_row[i + 3]);
        }
    }
    return true;
}

double FeatherBlender::total_weight() const {
    double total = 0.0;
    for (float w : wsum_) {
        total += w;
    }
    return total;
}

} // namespace rs
