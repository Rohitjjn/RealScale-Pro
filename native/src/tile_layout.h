// RealScale-Pro — tile layout geometry.
//
// Computes a grid of overlapping tiles that covers an input image
// exactly once. Design goals:
//   * every output pixel receives contributions from >= 1 tile,
//   * tiles are distributed evenly (no sliver tiles at the right/bottom
//     edge — the stride is recomputed so the last tile is full-size and
//     anchored at the image edge),
//   * each tile can carry an extra "context margin" that is fed to the
//     model but cropped away after inference,
//   * odd dimensions and images smaller than one tile degrade to a
//     single padded tile.
//
// Copyright 2026 RealScale-Pro contributors. MIT License.
#ifndef RS_TILE_LAYOUT_H
#define RS_TILE_LAYOUT_H

#include <cstdint>
#include <vector>

namespace rs {

// One planned tile in INPUT coordinates.
//   x, y        — top-left of the tile CORE (the region that must be
//                 reconstructed for the final image).
//   w, h        — core size (<= tile_size; smaller only when the image
//                 itself is smaller than tile_size).
//   cx, cy, cw, ch — core + context margin region actually extracted
//                 (clamped to the image bounds).
struct Tile {
    int32_t x = 0, y = 0;         // core position (input space)
    int32_t w = 0, h = 0;         // core size (input space)
    int32_t cx = 0, cy = 0;       // context region position
    int32_t cw = 0, ch = 0;       // context region size
    int32_t col = 0, row = 0;     // grid position (for diagnostics)
    // Actual overlap with neighbouring tiles (input pixels, >= 0).
    // Zero on outer borders. Used for feather ramp widths.
    int32_t ov_l = 0, ov_r = 0, ov_t = 0, ov_b = 0;
};

struct TileLayout {
    std::vector<Tile> tiles;
    int32_t image_w = 0;
    int32_t image_h = 0;
    int32_t tile_size = 0;   // requested core tile edge
    int32_t overlap = 0;     // overlap used between adjacent cores
    int32_t context = 0;     // context margin used
    int32_t cols = 0;
    int32_t rows = 0;
};

// Computes the tile layout.
//   image_w, image_h — input image size (>= 1).
//   tile_size        — core tile edge (>= 8). Values larger than the
//                      image are clamped; images smaller than the tile
//                      produce a single tile covering the whole image.
//   overlap          — overlap between adjacent tile CORES (>= 0,
//                      < tile_size / 2 recommended; clamped).
//   context          — extra context margin around the core (>= 0),
//                      clamped independently per side at image borders.
//
// Invariant: the union of the tile cores equals the full image rectangle
// (verified by unit tests), and interior overlap regions are exactly
// `overlap` pixels wide when the axis needs more than one tile.
TileLayout compute_tile_layout(int32_t image_w, int32_t image_h,
                               int32_t tile_size, int32_t overlap,
                               int32_t context);

// Given a tile's context-region output (cw*scale x ch*scale RGBA),
// returns the offset (in OUTPUT pixels) at which the core starts.
inline int32_t core_offset(int32_t core_pos, int32_t ctx_pos, int32_t scale) {
    return (core_pos - ctx_pos) * scale;
}

} // namespace rs

#endif // RS_TILE_LAYOUT_H
