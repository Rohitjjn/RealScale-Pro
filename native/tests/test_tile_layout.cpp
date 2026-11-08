// RealScale-Pro — tile layout unit tests.
//
// Copyright 2026 RealScale-Pro contributors. MIT License.
#include "rs_test.h"
#include "tile_layout.h"

#include <cstdint>
#include <set>
#include <vector>

using rs::TileLayout;
using rs::compute_tile_layout;

namespace {

// Verifies the core invariant: tile cores tile the image exactly
// (union == full rectangle, no hole). Uses a coverage bitmap.
bool cores_cover(const TileLayout& l, int32_t expect_tiles_ge) {
    std::vector<uint8_t> cover(static_cast<size_t>(l.image_w) * l.image_h, 0);
    int64_t total = 0;
    for (const auto& t : l.tiles) {
        RS_ASSERT(t.w > 0 && t.h > 0);
        RS_ASSERT(t.x >= 0 && t.y >= 0);
        RS_ASSERT(t.x + t.w <= l.image_w);
        RS_ASSERT(t.y + t.h <= l.image_h);
        for (int32_t y = t.y; y < t.y + t.h; ++y) {
            for (int32_t x = t.x; x < t.x + t.w; ++x) {
                uint8_t& c = cover[static_cast<size_t>(y) * l.image_w + x];
                RS_ASSERT(c < 255);
                if (c == 0) {
                    ++total;
                }
                ++c;
            }
        }
    }
    RS_ASSERT_EQ(total, static_cast<int64_t>(l.image_w) * l.image_h);
    RS_ASSERT(static_cast<int32_t>(l.tiles.size()) >= expect_tiles_ge);
    return true;
}

} // namespace

RS_TEST(layout_single_tile_when_image_fits) {
    auto l = compute_tile_layout(100, 80, 128, 12, 4);
    RS_ASSERT_EQ(l.tiles.size(), 1u);
    RS_ASSERT_EQ(l.tiles[0].w, 100);
    RS_ASSERT_EQ(l.tiles[0].h, 80);
    RS_ASSERT_EQ(l.tiles[0].x, 0);
    RS_ASSERT_EQ(l.tiles[0].y, 0);
    return cores_cover(l, 1);
}

RS_TEST(layout_exact_multiple) {
    auto l = compute_tile_layout(256, 256, 128, 16, 4);
    RS_ASSERT_EQ(l.cols, 3); // overlap 16 -> stride 112 -> 3 tiles
    RS_ASSERT_EQ(l.rows, 3);
    // Anchors: 0, 128 (even stride 128 == tile), 128... last = 256-128=128
    // -> anchors 0, 128, 128? distribute_axis: n computed with stride 112
    // gives n=3 (0+112*1+128=240 < 256? -> while (n-1)*stride+tile < extent:
    // n=2: 128+112=240 <256 -> n=3: 2*112+128=352 >= 256). even_stride =
    // last/(n-1) = 128/2 = 64 -> anchors 0, 64, 128.
    RS_ASSERT_EQ(l.tiles[0].x, 0);
    RS_ASSERT_EQ(l.tiles[1].x, 64);
    RS_ASSERT_EQ(l.tiles[2].x, 128);
    return cores_cover(l, 9);
}

RS_TEST(layout_covers_odd_dimensions) {
    for (int32_t w : {1, 7, 13, 127, 129, 255, 257, 1000}) {
        for (int32_t h : {1, 3, 129, 511}) {
            auto l = compute_tile_layout(w, h, 64, 8, 4);
            if (!cores_cover(l, 1)) {
                return false;
            }
        }
    }
    return true;
}

RS_TEST(layout_no_sliver_last_tile) {
    // The last tile must be full-size and anchored at the edge.
    auto l = compute_tile_layout(300, 100, 64, 8, 4);
    for (int32_t c = 0; c < l.cols; ++c) {
        const auto& t = l.tiles[static_cast<size_t>(c)];
        if (c == l.cols - 1) {
            RS_ASSERT_EQ(t.w, 64);
            RS_ASSERT_EQ(t.x + t.w, 300);
        } else {
            RS_ASSERT(t.w == 64);
        }
    }
    return cores_cover(l, 1);
}

RS_TEST(layout_context_clamped_at_borders) {
    auto l = compute_tile_layout(200, 200, 64, 8, 10);
    for (const auto& t : l.tiles) {
        RS_ASSERT(t.cx >= 0 && t.cy >= 0);
        RS_ASSERT(t.cx + t.cw <= 200 && t.cy + t.ch <= 200);
        RS_ASSERT(t.cw >= t.w && t.ch >= t.h);
        RS_ASSERT(t.cx <= t.x && t.x + t.w <= t.cx + t.cw);
    }
    return true;
}

RS_TEST(layout_interior_overlap_present) {
    auto l = compute_tile_layout(512, 512, 128, 16, 4);
    RS_ASSERT(l.cols >= 2 && l.rows >= 2);
    for (const auto& t : l.tiles) {
        if (t.col > 0) {
            RS_ASSERT(t.ov_l > 0);
        } else {
            RS_ASSERT_EQ(t.ov_l, 0);
        }
        if (t.col < l.cols - 1) {
            RS_ASSERT(t.ov_r > 0);
        }
        if (t.row > 0) {
            RS_ASSERT(t.ov_t > 0);
        }
        if (t.row < l.rows - 1) {
            RS_ASSERT(t.ov_b > 0);
        }
    }
    return cores_cover(l, 4);
}

RS_TEST(layout_degenerate_arguments_are_clamped) {
    auto l = compute_tile_layout(50, 50, 0, -5, -3); // tile 0 -> clamped
    RS_ASSERT_EQ(l.tile_size, 8);
    RS_ASSERT_EQ(l.overlap, 0);
    RS_ASSERT_EQ(l.context, 0);
    RS_ASSERT(!l.tiles.empty());
    return cores_cover(l, 1);
}

RS_TEST(layout_very_small_images) {
    for (int32_t w : {1, 2, 3, 5}) {
        for (int32_t h : {1, 2, 3, 5}) {
            auto l = compute_tile_layout(w, h, 64, 8, 4);
            RS_ASSERT_EQ(l.tiles.size(), 1u);
            if (!cores_cover(l, 1)) {
                return false;
            }
        }
    }
    return true;
}

RS_TEST(layout_large_image_grid_shape) {
    auto l = compute_tile_layout(4096, 2048, 256, 24, 8);
    RS_ASSERT(l.tiles.size() > 100);
    RS_ASSERT_EQ(l.image_w, 4096);
    return cores_cover(l, 100);
}

int main() {
    return rs::run_all_tests() ? 0 : 1;
}
