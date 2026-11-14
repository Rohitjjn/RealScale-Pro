#!/usr/bin/env python3
"""Join the upstream engine assets with the Hugging Face mirror.

Output (`model-facts.json`) is the single source of truth for the download manifest: for every model
directory it lists the file names the engine needs, the exact byte size, the SHA-256 of the bytes,
and the Hugging Face path those bytes live at — proven, not assumed:

* LFS files publish their SHA-256 in the tree API (`lfs.oid`), compared directly.
* Plain files publish a git blob SHA-1 (`oid`); recomputed here as sha1("blob <size>\\\\0" + bytes).
"""
from __future__ import annotations

import hashlib
import json
import os

MODEL_DIRS = [
    "models-RealeSR-general-v3",
    "models-ESRGAN-AnimeSharpLite",
    "models-ESRGAN-AnimeSharp",
    "models-ESRGAN-Remacri",
    "models-ESRGAN-UltraMix_Balanced",
    "models-ESRGAN-Nomos8kSC",
    "models-ESRGAN-SourceBook_v1",
    "models-MNNSR/ESRGAN-Nomos8kSC-x4.mnn",
    "models-MNNSR/MoeSR-ESRGAN-jp_Illustration-fix1-d-x4.mnn",
    "models-upconv_7_anime_style_art_rgb",
    "models-cunet",
    "models-srmd",
]


def sha256_of(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def git_blob_sha1(path: str) -> str:
    size = os.path.getsize(path)
    digest = hashlib.sha1()
    digest.update(f"blob {size}\\0".encode())
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def find_local_root() -> str:
    candidates = []
    for base, dirs, _files in os.walk("/tmp/assets"):
        if os.path.basename(base) == "realsr" or any(d.startswith("models-") for d in dirs):
            candidates.append(base)
    return sorted(set(candidates), key=len)[0]


def main() -> None:
    root = find_local_root()
    tree = json.load(open("/tmp/hf-tree.json"))
    remote = {}
    for entry in tree:
        if entry.get("type") != "file":
            continue
        lfs = entry.get("lfs") or {}
        remote[entry["path"]] = {
            "size": lfs.get("size") or entry.get("size"),
            "sha256": lfs.get("oid"),
            "gitOid": None if lfs else entry.get("oid"),
        }

    by_name_size = {}
    for path, meta in remote.items():
        by_name_size.setdefault((os.path.basename(path), meta["size"]), []).append(path)

    out = {"hfRepo": "tumuyan2/realsr-models", "hfRevision": "main", "dirs": {}, "unverified": []}
    for target in MODEL_DIRS:
        local = os.path.join(root, target)
        if not os.path.exists(local):
            out["unverified"].append({"dir": target, "reason": "not present in the upstream archive"})
            continue
        names = [os.path.basename(local)] if os.path.isfile(local) else sorted(os.listdir(local))
        files = []
        total = 0
        for name in names:
            path = local if os.path.isfile(local) else os.path.join(local, name)
            if not os.path.isfile(path):
                continue
            size = os.path.getsize(path)
            total += size
            candidates = by_name_size.get((name, size), [])
            best = next((c for c in candidates if c.startswith(target.rsplit("/", 1)[0])), candidates[0] if candidates else None)
            sha = sha256_of(path)
            verified = False
            if best:
                meta = remote[best]
                verified = (meta["sha256"] == sha) or (meta["gitOid"] == git_blob_sha1(path))
            else:
                best = None
            if not verified:
                out["unverified"].append({"dir": target, "file": name, "remote": best})
            files.append({
                "fileName": name,
                "sizeBytes": size,
                "sha256": sha,
                "remotePath": best,
                "verified": verified,
            })
        out["dirs"][target] = {"totalBytes": total, "files": files}

    json.dump(out, open("model-facts.json", "w"), indent=1, sort_keys=True)
    for target, info in out["dirs"].items():
        good = sum(1 for f in info["files"] if f["verified"])
        print(f"{target:56s} {info['totalBytes']/1048576:7.2f} MB  verified {good}/{len(info['files'])}")
    print("unverified:", len(out["unverified"]))
    for item in out["unverified"][:20]:
        print("  !", item)


if __name__ == "__main__":
    main()
