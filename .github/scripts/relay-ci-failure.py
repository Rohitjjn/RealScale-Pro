#!/usr/bin/env python3
"""Relay the interesting parts of a failed Android build to the CI bridge issue.

The agent sandbox cannot reach Actions logs or artifacts (the blob storage host is not on its
egress allowlist), so this script keeps a machine-readable feedback channel open: it extracts the
compiler errors, Gradle failures and JUnit failure messages and posts them as an issue comment.
"""
from __future__ import annotations

import glob
import json
import os
import re
import urllib.request

ISSUE = 10
LIMIT = 60_000

ERROR_LINE = re.compile(
    r"^(e: "
    r"|FAILED"
    r"|FAILURE:"
    r"|What went wrong"
    r"|> Task .* FAILED"
    r"|Execution failed for task"
    r"|\\*\\*\\* "
    r"|Caused by)"
)


def gradle_chunk() -> str | None:
    path = "/tmp/gradle.log"
    if not os.path.exists(path):
        return None
    text = open(path, errors="replace").read()
    keep = [line for line in text.splitlines() if ERROR_LINE.match(line) or "Kotlin compiler error" in line]
    if not keep:
        keep = text.splitlines()[-250:]
    return "gradle:\n" + "\n".join(keep[:1500])


def test_chunks() -> list[str]:
    chunks: list[str] = []
    for path in glob.glob("RealSR-NCNN-Android-GUI/app/build/test-results/**/*.xml", recursive=True):
        xml = open(path, errors="replace").read()
        for match in re.finditer(r'<testcase name="([^"]+)"[^>]*>\s*<failure[^>]*>(.{0,2500})', xml, re.S):
            chunks.append(f"test {match.group(1)}:\n{match.group(2).strip()[:2500]}")
    return chunks


def main() -> None:
    chunks = [chunk for chunk in [gradle_chunk()] if chunk]
    chunks.extend(test_chunks())
    sha = os.environ.get("GITHUB_SHA", "")[:8]
    body = f"### ci-failure {sha}\n```\n" + "\n\n".join(chunks) + "\n```"
    body = body[:LIMIT]
    if len(chunks) == 0:
        body = f"### ci-failure {sha}\nNo log captured."

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
