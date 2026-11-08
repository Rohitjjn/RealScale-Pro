#!/usr/bin/env python3
"""RealScale-Pro — launcher icon + splash generator.

Generates the Android launcher icon set (legacy mipmaps + adaptive
foreground) and the splash logo from the Paper-Crafted palette using
pure geometry (no external assets, nothing to license).

Palette (lib/core/theme.dart):
  paper ivory  #F7F3EC   terracotta #B4552D   ink #2B2723
  graphite     #211F1D   border    #DCD3C3

Usage: python3 tools/generate_icon.py  (from the project root)
"""
from PIL import Image, ImageDraw
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

IVORY = (247, 243, 236, 255)
TERRA = (180, 85, 45, 255)
INK = (43, 39, 35, 255)
BORDER = (200, 190, 172, 255)

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}


def _glyph(size, fg, stroke_w):
    """The 'enlarge' mark: two viewfinder brackets + diagonal arrow,
    drawn with rounded caps at integer-friendly coordinates."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size
    m = s * 0.20            # bracket inset
    l = s * 0.22            # bracket arm length
    w = stroke_w
    r = w * 0.6

    def line(x0, y0, x1, y1):
        d.line([(x0, y0), (x1, y1)], fill=fg, width=w)

    # rounded-cap helper
    def cap(x, y):
        d.ellipse([x - r, y - r, x + r, y + r], fill=fg)

    # top-left bracket
    line(m, m + l, m, m)
    line(m, m, m + l, m)
    cap(m, m + l)
    cap(m + l, m)
    cap(m, m)
    # bottom-right bracket
    line(s - m - l, s - m, s - m, s - m)
    line(s - m, s - m, s - m, s - m + l)
    cap(s - m - l, s - m)
    cap(s - m, s - m + l)
    cap(s - m, s - m)

    # diagonal arrow (bottom-left -> upper-right)
    ax0, ay0 = m + s * 0.10, s - m - s * 0.10
    ax1, ay1 = s - m - s * 0.10, m + s * 0.10
    line(ax0, ay0, ax1, ay1)
    cap(ax0, ay0)
    cap(ax1, ay1)
    # arrowhead (two short strokes at the tip)
    hw = s * 0.13
    line(ax1, ay1, ax1 - hw, ay1)
    line(ax1, ay1, ax1, ay1 + hw)
    cap(ax1 - hw, ay1)
    cap(ax1, ay1 + hw)
    return img


def _rounded(size, radius, fill, outline=None, ow=0):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius,
                        fill=fill, outline=outline, width=ow)
    return img


def legacy_icon(px):
    """Full-bleed-ish legacy icon: ivory rounded tile + border + glyph."""
    bg = _rounded(px, int(px * 0.18), IVORY, BORDER, max(1, int(px * 0.02)))
    glyph = _glyph(px, TERRA, max(2, int(px * 0.085)))
    bg.alpha_composite(glyph)
    return bg


def adaptive_foreground(px):
    """Adaptive foreground: glyph only, centered in the 66% safe zone."""
    img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    inner = int(px * 0.62)
    glyph = _glyph(inner, TERRA, max(2, int(inner * 0.10)))
    # small ink dot accent under the glyph (paper-crafted detail)
    d = ImageDraw.Draw(glyph)
    dr = inner * 0.035
    cx = inner * 0.5
    cy = inner * 0.5 + inner * 0.05
    d.ellipse([cx - dr, cy - dr, cx + dr, cy + dr], fill=INK)
    img.alpha_composite(glyph, ((px - inner) // 2, (px - inner) // 2))
    return img


def splash_logo(px):
    """Splash mark: glyph on transparent background (theme supplies bg)."""
    return _glyph(px, TERRA, max(2, int(px * 0.085)))


def main():
    # Legacy launcher icons
    for dpi, scale in DENSITIES.items():
        px = int(48 * scale)
        out = os.path.join(RES, f"mipmap-{dpi}")
        os.makedirs(out, exist_ok=True)
        legacy_icon(px).save(os.path.join(out, "ic_launcher.png"))
        # adaptive foreground (108dp grid)
        fpx = int(108 * scale)
        adaptive_foreground(fpx).save(os.path.join(out, "ic_launcher_foreground.png"))

    # Splash logo (bitmap used by launch_background layer-lists)
    splash = os.path.join(RES, "mipmap-xxxhdpi")
    splash_logo(432).save(os.path.join(splash, "splash_logo.png"))

    # Adaptive icon definition (API 26+)
    anydpi = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    with open(os.path.join(anydpi, "ic_launcher.xml"), "w") as f:
        f.write(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
            '    <background android:drawable="@color/launcher_background"/>\n'
            '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
            "</adaptive-icon>\n")

    # Adaptive background color
    values = os.path.join(RES, "values")
    os.makedirs(values, exist_ok=True)
    with open(os.path.join(values, "launcher_colors.xml"), "w") as f:
        f.write(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<resources>\n"
            '    <color name="launcher_background">#F7F3EC</color>\n'
            "</resources>\n")
    print("icons written to", RES)


if __name__ == "__main__":
    main()
