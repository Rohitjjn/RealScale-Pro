// RealScale-Pro — normalized feather blending for tile reconstruction.
//
// Tiles are accumulated into float buffers with per-pixel weights
// derived from a smoothstep ramp across each tile's overlap zones. The
// final image is weight-normalized, which guarantees:
//   * full coverage (every pixel has weight_sum > 0 when the layout
//     covers the image — enforced by compute_tile_layout),
//   * no visible seams for smooth or textured content (C0-continuous
//     weighting),
//   * exact reproduction of a constant tile field (verified by tests),
//   * correct alpha channel handling.
//
// Copyright 2026 RealScale-Pro contributors. MIT License.
#ifndef RS_FEATHER_BLEND_H
#define RS_FEATHER_BLEND_H

#include <cstdint>
#include <vector>

#include "tile_layout.h"

namespace rs {

class FeatherBlender {
public:
    // Prepares accumulation buffers for an output image of
    // out_w x out_h RGBA pixels.
    void reset(int32_t out_w, int32_t out_h);

    // Accumulates one processed tile.
    //   tile            — layout entry (input-space coords).
    //   tile_output     — RGBA pixels of the tile's CONTEXT region after
    //                     inference, cw_out x ch_out where
    //                     cw_out = tile.cw * scale, ch_out = tile.ch * scale.
    //   scale           — upscale factor applied by the model.
    // The core region of the tile is extracted from tile_output relative
    // to the context region and blended into the output buffers.
    void accumulate(const Tile& tile,
                    const uint8_t* tile_output,
                    int32_t tile_output_stride,
                    int32_t scale);

    // Divides accumulated color by accumulated weight and writes RGBA8.
    // `out` must have room for out_w*out_h*4 bytes (stride out_w*4).
    // Returns false when any pixel had zero total weight (coverage hole).
    bool normalize_to(uint8_t* out, int32_t out_stride) const;

    int32_t out_width() const { return out_w_; }
    int32_t out_height() const { return out_h_; }

    // Diagnostics: total weight mass accumulated (for tests).
    double total_weight() const;

private:
    int32_t out_w_ = 0;
    int32_t out_h_ = 0;
    std::vector<float> acc_;  // rgba accumulation
    std::vector<float> wsum_; // weight accumulation
};

// Smoothstep ramp: 0 at 0, 1 at >= ramp, C1-continuous in between.
float smoothstep_ramp(float t, float ramp);

} // namespace rs

#endif // RS_FEATHER_BLEND_H
