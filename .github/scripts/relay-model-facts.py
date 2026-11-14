#!/usr/bin/env python3
"""Post the joined model facts (and the manifest integrity report) to the CI bridge issue.

The agent sandbox cannot read Actions logs or artifacts, so this is the only channel that carries
model truth back: the HF tree join and the per-URL existence check both end up in one comment.
"""
from __future__ import annotations

import json
import os
import urllib.request

ISSUE = 10
LIMIT = 60_000


def read(path: str) -> str | None:
    if not os.path.exists(path):
        return None
    text = open(path, errors="replace").read()
    return text if text.strip() else None


def main() -> None:
    chunks: list[str] = []
    facts = read("model-facts.json")
    if facts:
        try:
            facts = json.dumps(json.loads(facts), indent=1, sort_keys=True)
        except json.JSONDecodeError:
            pass
        chunks.append("facts:\n" + facts)
    manifest = read("/tmp/manifest-check.txt")
    if manifest:
        chunks.append("manifest:\n" + manifest)
    sha = os.environ.get("GITHUB_SHA", "")[:8]
    if not chunks:
        body = f"### model-facts {sha}\nNo facts captured."
    else:
        body = f"### model-facts {sha}\n```\n" + "\n\n".join(chunks) + "\n```"
    body = body[:LIMIT]

    request = urllib.request.Request(
        f"https://api.github.com/repos/{os.environ['GITHUB_REPOSITORY']}/issues/{ISSUE}/comments",
        data=json.dumps({"body": body}).encode(),
        headers={
            "Authorization": f"Bearer {os.environ['GH_TOKEN']}",
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/json",
            "User-Agent": "realscale-ci",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        print("relayed", response.status, len(body), "chars")


if __name__ == "__main__":
    main()
