#!/usr/bin/env python3
"""Check that every file the app promises to download actually exists.

`app/src/main/assets/model_manifest.json` is the download truth: one fully qualified URL per file.
The Hugging Face mirror is not stable — directories get renamed or pruned — and a dead URL only
shows up as a failed download on a user's phone. This script HEADs every URL (following the CDN
redirect) and writes a short report to /tmp/manifest-check.txt, which the relay step posts to the
CI bridge issue.

Exit code is always 0: a dead URL is a finding to fix in the data, not a reason to fail the build.
"""
from __future__ import annotations

import json
import pathlib
import urllib.error
import urllib.request

MANIFEST = pathlib.Path("RealSR-NCNN-Android-GUI/app/src/main/assets/model_manifest.json")
REPORT = pathlib.Path("/tmp/manifest-check.txt")


def head(url: str) -> tuple[int, int]:
    """Returns (status, content-length). 0 means the request itself failed."""
    request = urllib.request.Request(
        url,
        method="HEAD",
        headers={"User-Agent": "realscale-manifest-check"},
    )
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            return response.status, int(response.headers.get("Content-Length") or 0)
    except urllib.error.HTTPError as error:
        return error.code, 0
    except Exception as error:  # noqa: BLE001 - a network failure is a finding too
        print(f"error {url}: {error}")
        return 0, 0


def main() -> None:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    lines: list[str] = [f"repo {manifest['sourceRepo']} · manifest v{manifest['manifestVersion']}"]
    dead: list[str] = []
    checked = 0
    for model in manifest["models"]:
        for spec in model["files"]:
            checked += 1
            status, length = head(spec["url"])
            expected = spec.get("sizeBytes") or 0
            problem = None
            if status != 200:
                problem = f"HTTP {status}"
            elif expected and length and length != expected:
                problem = f"size {length} != {expected}"
            if problem:
                dead.append(f"{model['id']}/{spec['fileName']}: {problem}")
    lines.append(f"{checked} files checked, {len(dead)} problems")
    lines.extend(dead[:120])
    REPORT.write_text("\n".join(lines), encoding="utf-8")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
