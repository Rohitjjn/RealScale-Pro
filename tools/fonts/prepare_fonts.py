#!/usr/bin/env python3
"""Prepares the bundled font resources from github.com/google/fonts (OFL 1.1).

The prototype self-hosts:
  - Cormorant Garamond 500 / 600   (display serif)
  - Inter Variable 100..900        (body sans)
  - JetBrains Mono Variable 100..800 (tabular numbers, file names, console)

google/fonts only ships *variable* fonts, and minSdk is 24, where variable font instancing is not
reliable. So this script downloads the variables with the GitHub contents API, pins the static
instances we use with fontTools, and subsets them to the glyphs the UI needs.

Output: RealSR-NCNN-Android-GUI/app/src/main/res/font/*.ttf  (committed; ~0.4 MB total)

Usage: python3 tools/fonts/prepare_fonts.py        (needs `pip install fonttools brotli`)
"""
import json
import pathlib
import subprocess
import sys
import urllib.request

REPO = pathlib.Path(__file__).resolve().parents[2]
OUT = REPO / "RealSR-NCNN-Android-GUI/app/src/main/res/font"
CACHE = pathlib.Path("/tmp/realscale-fonts")

LATIN = ("U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,"
         "U+2000-206F,U+20AC,U+2122,U+2190-2193,U+2212,U+2215,U+FEFF,U+FFFD,U+00D7,U+2713")
DEVANAGARI = ("U+0900-097F,U+1CD0-1CF9,U+A830-A839,U+200C-200D,U+25CC,U+0020-007F,U+00A0,U+2013-2014,U+2018-201D,U+2026")

FONTS = [
    # (google/fonts path, cache name, axes, [(resource name, weight or None)], unicodes, features)
    ("cormorantgaramond/CormorantGaramond%5Bwght%5D.ttf", "cormorant.ttf", ["wght"],
     [("cormorant_garamond_500", {"wght": 500}), ("cormorant_garamond_600", {"wght": 600})], LATIN),
    ("inter/Inter%5Bopsz,wght%5D.ttf", "inter.ttf", ["wght", "opsz"],
     [("inter_400", {"wght": 400, "opsz": 14}), ("inter_500", {"wght": 500, "opsz": 14}),
      ("inter_600", {"wght": 600, "opsz": 14}), ("inter_700", {"wght": 700, "opsz": 14})], LATIN),
    ("jetbrainsmono/JetBrainsMono%5Bwght%5D.ttf", "jbmono.ttf", ["wght"],
     [("jetbrains_mono_400", {"wght": 400}), ("jetbrains_mono_500", {"wght": 500})], LATIN),
    ("notosansdevanagari/NotoSansDevanagari%5Bwdth,wght%5D.ttf", "noto-dev.ttf", ["wdth", "wght"],
     [("noto_sans_devanagari_400", {"wght": 400, "wdth": 100}),
      ("noto_sans_devanagari_500", {"wght": 500, "wdth": 100})], DEVANAGARI),
]


def download(github_path: str, cache_name: str) -> pathlib.Path:
    CACHE.mkdir(parents=True, exist_ok=True)
    target = CACHE / cache_name
    if target.exists() and target.stat().st_size > 10_000:
        return target
    url = f"https://api.github.com/repos/google/fonts/contents/ofl/{github_path}"
    req = urllib.request.Request(url, headers={"Accept": "application/vnd.github.raw", "User-Agent": "realscale-build"})
    with urllib.request.urlopen(req, timeout=120) as response:
        target.write_bytes(response.read())
    return target


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    for path, cache_name, _axes, instances, unicodes in FONTS:
        source = download(path, cache_name)
        for resource_name, location in instances:
            instanced = CACHE / f"{resource_name}.ttf"
            loc = [f"{k}={v}" for k, v in location.items()]
            subprocess.run([sys.executable, "-m", "fontTools.varLib.instancer", str(source), *loc,
                            "-o", str(instanced)], check=True,
                           stdout=subprocess.DEVNULL)
            subprocess.run([sys.executable, "-m", "fontTools.subset", str(instanced),
                            f"--unicodes={unicodes}", "--layout-features=kern,liga,tnum,onum,lnum,ccmp,mark,mkmk",
                            "--drop-tables+=DSIG", "--name-IDs=*", f"--output-file={OUT / (resource_name + '.ttf')}"],
                           check=True, stdout=subprocess.DEVNULL)
            print(f"{resource_name}.ttf  {(OUT / (resource_name + '.ttf')).stat().st_size // 1024} kB")
        # keep the licence text next to the fonts, as OFL requires
        licence = CACHE / (cache_name.replace(".ttf", "") + "-OFL.txt")
        if not licence.exists():
            url = f"https://api.github.com/repos/google/fonts/contents/ofl/{path.rsplit('/', 1)[0]}/OFL.txt"
            req = urllib.request.Request(url, headers={"Accept": "application/vnd.github.raw", "User-Agent": "realscale-build"})
            with urllib.request.urlopen(req, timeout=60) as response:
                licence.write_bytes(response.read())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
