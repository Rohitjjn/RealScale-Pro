// RealScale-Pro — tile layout implementation.
//
// Copyright 2026 RealScale-Pro contributors. MIT License.
#include "tile_layout.h"

#include <algorithm>
#include <cmath>

namespace rs {

namespace {

// Evenly distributes `n` tile anchors over the axis [0, extent).
// Each tile has size `tile` (clamped to extent), the first tile starts
// at 0 and the last tile ends exactly at `extent`. Returns the anchors.
std::vector<int32_t> distribute_axis(int32_t extent, int32_t tile, int32_t overlap) {
    // Clamp tile to the axis; an axis smaller than one tile becomes one tile.
    if (tile >= extent) {
        return {0};
    }
    if (overlap >= tile) {
        overlap = tile / 2; // defensive clamp; planner should never do this
    }
    const int32_t stride = tile - overlap; // >= 1 because overlap < tile
    // Number of tiles needed so that 0 + (n-1)*stride + tile >= extent.
    int32_t n = 1;
    while ((n - 1) * stride + tile < extent) {
        ++n;
    }
    std::vector<int32_t> anchors(static_cast<size_t>(n));
    anchors[0] = 0;
    if (n == 1) {
        return anchors;
    }
    // Even distribution: keep full tiles, recompute the stride so the
    // last anchor lands at extent - tile (no sliver tile).
    const int32_t last = extent - tile;
    const int32_t even_stride = last / (n - 1);
    for (int32_t i = 1; i < n - 1; ++i) {
        anchors[static_cast<size_t>(i)] = std::min(i * even_stride, last);
    }
    anchors[static_cast<size_t>(n - 1)] = last;
    return anchors;
}

inline int32_t clampi(int32_t v, int32_t lo, int32_t hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

} // namespace

TileLayout compute_tile_layout(int32_t image_w, int32_t image_h,
                               int32_t tile_size, int32_t overlap,
                               int32_t context) {
    TileLayout layout;
    layout.image_w = std::max<int32_t>(image_w, 1);
    layout.image_h = std::max<int32_t>(image_h, 1);
    layout.tile_size = std::max<int32_t>(tile_size, 8);
    layout.overlap = std::max<int32_t>(overlap, 0);
    layout.context = std::max<int32_t>(context, 0);
    if (layout.overlap >= layout.tile_size / 2) {
        layout.overlap = layout.tile_size / 4 > 0 ? layout.tile_size / 4 : 0;
    }

    const std::vector<int32_t> xs =
        distribute_axis(layout.image_w, layout.tile_size, layout.overlap);
    const std::vector<int32_t> ys =
        distribute_axis(layout.image_h, layout.tile_size, layout.overlap);

    layout.cols = static_cast<int32_t>(xs.size());
    layout.rows = static_cast<int32_t>(ys.size());
    layout.tiles.reserve(xs.size() * ys.size());

    // Per-axis core sizes (full tile except when the image itself is
    // smaller than one tile).
    auto core_sizes = [](const std::vector<int32_t>& anchors,
                         int32_t tile, int32_t extent) {
        std::vector<int32_t> sizes(anchors.size(), tile);
        if (!anchors.empty()) {
            sizes.back() = std::min(tile, extent - anchors.back());
        }
        return sizes;
    };
    const std::vector<int32_t> ws = core_sizes(xs, layout.tile_size, layout.image_w);
    const std::vector<int32_t> hs = core_sizes(ys, layout.tile_size, layout.image_h);

    for (int32_t r = 0; r < layout.rows; ++r) {
        for (int32_t c = 0; c < layout.cols; ++c) {
            Tile t;
            t.col = c;
            t.row = r;
            t.x = xs[static_cast<size_t>(c)];
            t.y = ys[static_cast<size_t>(r)];
            t.w = ws[static_cast<size_t>(c)];
            t.h = hs[static_cast<size_t>(r)];

            // Actual overlap with neighbours (may differ from the
            // requested overlap because of even stride distribution).
            if (c > 0) {
                t.ov_l = std::max(0, xs[static_cast<size_t>(c - 1)] +
                                         ws[static_cast<size_t>(c - 1)] - t.x);
            }
            if (c + 1 < layout.cols) {
                t.ov_r = std::max(0, t.x + t.w - xs[static_cast<size_t>(c + 1)]);
            }
            if (r > 0) {
                t.ov_t = std::max(0, ys[static_cast<size_t>(r - 1)] +
                                         hs[static_cast<size_t>(r - 1)] - t.y);
            }
            if (r + 1 < layout.rows) {
                t.ov_b = std::max(0, t.y + t.h - ys[static_cast<size_t>(r + 1)]);
            }

            // Context region: core grown by the margin, clamped to image.
            t.cx = clampi(t.x - layout.context, 0, layout.image_w);
            t.cy = clampi(t.y - layout.context, 0, layout.image_h);
            const int32_t right =
                clampi(t.x + t.w + layout.context, 0, layout.image_w);
            const int32_t bottom =
                clampi(t.y + t.h + layout.context, 0, layout.image_h);
            t.cw = right - t.cx;
            t.ch = bottom - t.cy;
            layout.tiles.push_back(t);
        }
    }
    return layout;
}

} // namespace rs
