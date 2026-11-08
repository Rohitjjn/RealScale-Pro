// RealScale-Pro — feather blending / seam-quality tests.
//
// Copyright 2026 RealScale-Pro contributors. MIT License.
#include "rs_test.h"
#include "feather_blend.h"
#include "tile_layout.h"

#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>

using rs::FeatherBlender;
using rs::TileLayout;
using rs::compute_tile_layout;

namespace {

std::vector<uint8_t> constant_tile(int32_t w, int32_t h, uint8_t v) {
    std::vector<uint8_t> px(static_cast<size_t>(w) * h * 4, v);
    return px;
}

// Simulates a "model" that scales a region by repeating pixels.
std::vector<uint8_t> scale_region(const uint8_t* src, int32_t w, int32_t h,
                                  int32_t stride, int32_t scale) {
    const int32_t ow = w * scale;
    const int32_t oh = h * scale;
    std::vector<uint8_t> out(static_cast<size_t>(ow) * oh * 4, 0);
    for (int32_t y = 0; y < oh; ++y) {
        const uint8_t* srow = src + static_cast<size_t>(y / scale) * stride;
        uint8_t* drow = out.data() + static_cast<size_t>(y) * ow * 4;
        for (int32_t x = 0; x < ow; ++x) {
            const uint8_t* s = srow + static_cast<size_t>(x / scale) * 4;
            uint8_t* d = drow + static_cast<size_t>(x) * 4;
            d[0] = s[0];
            d[1] = s[1];
            d[2] = s[2];
            d[3] = s[3];
        }
    }
    return out;
}

// Full synthetic pipeline: nearest-scale each tile context region and
// blend. On success fills `out` and returns true.
bool tiled_compose(const TileLayout& l,
                   const std::vector<uint8_t>& image,
                   int32_t image_stride, int32_t scale,
                   std::vector<uint8_t>& out) {
    FeatherBlender blender;
    blender.reset(l.image_w * scale, l.image_h * scale);
    for (const auto& t : l.tiles) {
        std::vector<uint8_t> region(static_cast<size_t>(t.cw) * t.ch * 4);
        for (int32_t y = 0; y < t.ch; ++y) {
            std::memcpy(region.data() + static_cast<size_t>(y) * t.cw * 4,
                        image.data() + static_cast<size_t>(t.cy + y) * image_stride +
                            static_cast<size_t>(t.cx) * 4,
                        static_cast<size_t>(t.cw) * 4);
        }
        std::vector<uint8_t> scaled = scale_region(
            region.data(), t.cw, t.ch, t.cw * 4, scale);
        blender.accumulate(t, scaled.data(), t.cw * scale * 4, scale);
    }
    out.assign(static_cast<size_t>(l.image_w * scale) *
               (l.image_h * scale) * 4, 0);
    return blender.normalize_to(out.data(), l.image_w * scale * 4);
}

} // namespace

RS_TEST(blend_single_tile_identity) {
    auto l = compute_tile_layout(32, 32, 64, 8, 4); // single tile
    const std::vector<uint8_t> img = constant_tile(32, 32, 200);
    std::vector<uint8_t> out;
    RS_ASSERT(tiled_compose(l, img, 32 * 4, 4, out));
    // A single tile must reproduce its content exactly.
    for (size_t i = 0; i < out.size(); i += 4) {
        RS_ASSERT_EQ(out[i], 200);
        RS_ASSERT_EQ(out[i + 3], 200);
    }
    return true;
}

RS_TEST(blend_constant_field_is_seamless) {
    auto l = compute_tile_layout(300, 300, 64, 8, 4); // multi-tile grid
    const std::vector<uint8_t> img = constant_tile(300, 300, 137);
    std::vector<uint8_t> out;
    RS_ASSERT(tiled_compose(l, img, 300 * 4, 4, out));
    // Every pixel must be exactly the constant value: normalized
    // feathering of identical tiles reproduces the field with no seam.
    for (size_t i = 0; i < out.size(); ++i) {
        RS_ASSERT_EQ(out[i], 137);
    }
    return true;
}

RS_TEST(blend_gradient_seam_quality) {
    // Horizontal gradient; the tiled+blended result must stay close to
    // the directly scaled reference (max deviation bound).
    const int32_t W = 290, H = 160, S = 4;
    std::vector<uint8_t> img(static_cast<size_t>(W) * H * 4);
    for (int32_t y = 0; y < H; ++y) {
        for (int32_t x = 0; x < W; ++x) {
            const uint8_t v = static_cast<uint8_t>(x * 255 / (W - 1));
            uint8_t* p = img.data() + (static_cast<size_t>(y) * W + x) * 4;
            p[0] = v;
            p[1] = v;
            p[2] = v;
            p[3] = 255;
        }
    }
    auto l = compute_tile_layout(W, H, 64, 8, 4);
    std::vector<uint8_t> out;
    RS_ASSERT(tiled_compose(l, img, W * 4, S, out));
    int max_dev = 0;
    for (int32_t y = 0; y < H * S; ++y) {
        for (int32_t x = 0; x < W * S; ++x) {
            const int ref = (x / S) * 255 / (W - 1);
            const int got = out[(static_cast<size_t>(y) * W * S + x) * 4];
            const int dev = std::abs(got - ref);
            if (dev > max_dev) {
                max_dev = dev;
            }
        }
    }
    // Smoothstep blending of a linear ramp introduces bounded deviation.
    RS_ASSERT(max_dev <= 2);
    return true;
}

RS_TEST(blend_output_size_matches) {
    for (int32_t w : {37, 128, 300}) {
        for (int32_t h : {41, 128}) {
            auto l = compute_tile_layout(w, h, 64, 8, 4);
            const std::vector<uint8_t> img = constant_tile(w, h, 90);
            std::vector<uint8_t> out;
            RS_ASSERT(tiled_compose(l, img, w * 4, 4, out));
            RS_ASSERT_EQ(out.size(),
                         static_cast<size_t>(w * 4) * (h * 4) * 4);
        }
    }
    return true;
}

RS_TEST(blend_corners_and_edges_present) {
    auto l = compute_tile_layout(200, 200, 64, 8, 4);
    std::vector<uint8_t> img(200 * 200 * 4);
    for (int32_t y = 0; y < 200; ++y) {
        for (int32_t x = 0; x < 200; ++x) {
            uint8_t* p = img.data() + (static_cast<size_t>(y) * 200 + x) * 4;
            p[0] = static_cast<uint8_t>(x);
            p[1] = static_cast<uint8_t>(y);
            p[2] = 128;
            p[3] = 255;
        }
    }
    std::vector<uint8_t> out;
    RS_ASSERT(tiled_compose(l, img, 200 * 4, 4, out));
    // Corner pixels must match the reference mapping exactly.
    const auto px = [&](int32_t x, int32_t y) {
        return out[(static_cast<size_t>(y) * 800 + x) * 4];
    };
    RS_ASSERT_EQ(px(0, 0), 0);
    RS_ASSERT_EQ(px(799, 0), 199);
    RS_ASSERT_EQ(px(0, 799), 0);
    RS_ASSERT_EQ(px(799, 799), 199);
    // Edge rows/cols fully covered (alpha 255 everywhere).
    for (int32_t x = 0; x < 800; ++x) {
        RS_ASSERT_EQ(out[(static_cast<size_t>(x) * 4) + 3], 255);
        RS_ASSERT_EQ(out[(static_cast<size_t>(799 * 800 + x)) * 4 + 3], 255);
    }
    return true;
}

RS_TEST(blend_odd_dimensions_and_tiny_images) {
    for (int32_t w : {1, 5, 33}) {
        for (int32_t h : {1, 3, 17}) {
            auto l = compute_tile_layout(w, h, 32, 6, 2);
            const std::vector<uint8_t> img = constant_tile(w, h, 77);
            std::vector<uint8_t> out;
            RS_ASSERT(tiled_compose(l, img, w * 4, 3, out));
            RS_ASSERT_EQ(out.size(), static_cast<size_t>(w * 3) * (h * 3) * 4);
            for (size_t i = 0; i < out.size(); ++i) {
                RS_ASSERT_EQ(out[i], 77);
            }
        }
    }
    return true;
}

RS_TEST(blend_alpha_preserved) {
    auto l = compute_tile_layout(128, 128, 64, 8, 4);
    std::vector<uint8_t> img(128 * 128 * 4);
    for (size_t i = 0; i < img.size(); i += 4) {
        img[i] = 10;
        img[i + 1] = 20;
        img[i + 2] = 30;
        img[i + 3] = static_cast<uint8_t>(128); // half alpha everywhere
    }
    std::vector<uint8_t> out;
    RS_ASSERT(tiled_compose(l, img, 128 * 4, 2, out));
    for (size_t i = 3; i < out.size(); i += 4) {
        RS_ASSERT_EQ(out[i], 128);
    }
    return true;
}

RS_TEST(smoothstep_ramp_bounds) {
    RS_ASSERT(rs::smoothstep_ramp(-1.0f, 4.0f) == 0.0f);
    RS_ASSERT(rs::smoothstep_ramp(0.0f, 4.0f) == 0.0f);
    RS_ASSERT(rs::smoothstep_ramp(4.0f, 4.0f) == 1.0f);
    RS_ASSERT(rs::smoothstep_ramp(9.0f, 4.0f) == 1.0f);
    RS_ASSERT(rs::smoothstep_ramp(2.0f, 4.0f) > 0.0f);
    RS_ASSERT(rs::smoothstep_ramp(2.0f, 4.0f) < 1.0f);
    RS_ASSERT(rs::smoothstep_ramp(2.0f, 0.0f) == 1.0f); // degenerate ramp
    return true;
}

int main() {
    return rs::run_all_tests() ? 0 : 1;
}
