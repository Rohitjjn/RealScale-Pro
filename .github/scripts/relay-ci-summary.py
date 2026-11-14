#!/usr/bin/env python3
"""Relay a compact instrumentation summary to the CI bridge issue.

`relay-ci-failure.py` only speaks when something breaks. The agent sandbox also cannot read a green
run's log, so this companion reports *which* instrumented tests actually executed, whether any of
them was skipped by an assumption (the network-dependent download test skips instead of failing on a
sandboxed runner) and the final `OK (...)` line.

Only the tail is posted: the full stream stays in Actions.
"""
from __future__ import annotations

import json
import os
import re
import urllib.error
import urllib.request

ISSUE = 10
LIMIT = 20_000


def post(body: str) -> None:
    repo = os.environ.get("GITHUB_REPOSITORY", "Rohitjjn/RealScale-Pro")
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if not token:
        raise SystemExit("no GitHub token in the environment")
    request = urllib.request.Request(
        f"https://api.github.com/repos/{repo}/issues/{ISSUE}/comments",
        data=json.dumps({"body": body[:LIMIT]}).encode(),
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/json",
            "User-Agent": "realscale-ci-relay",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        print("relayed summary:", response.status)


def main() -> None:
    path = "/tmp/instrument.log"
    if not os.path.exists(path):
        print("no instrument log, nothing to relay")
        return
    text = open(path, errors="replace").read()
    lines = text.splitlines()

    tests: list[str] = []
    current_class = ""
    for line in lines:
        class_match = re.search(r"INSTRUMENTATION_STATUS: class=(\S+)", line)
        if class_match:
            current_class = class_match.group(1).split(".")[-1]
            continue
        test_match = re.search(r"INSTRUMENTATION_STATUS: test=(\S+)", line)
        if test_match and current_class:
            entry = f"{current_class}#{test_match.group(1)}"
            if entry not in tests:
                tests.append(entry)

    skipped = sorted(set(re.findall(r"AssumptionViolatedException: ([^\\\n]{0,120})", text)))
    tail = [line for line in lines if line.startswith("OK (") or line.startswith("FAILURES")
            or line.startswith("Time:") or "Tests run:" in line]
    stack_heads = [line for line in lines if re.match(r"\s*(java|org|kotlin|androidx)\..*Exception", line)]

    body = [
        f"### ci-instrumentation {os.environ.get('GITHUB_SHA', '')[:8]}",
        "```",
        "ran:",
        *[f"  {entry}" for entry in tests] or ["  (none)"],
        "",
        f"count: {len(tests)}",
        "assumptions:",
        *[f"  {item}" for item in skipped] or ["  (none)"],
    ]
    if stack_heads:
        body += ["exception-heads:", *[f"  {line.strip()[:160]}" for line in stack_heads[:10]]]
    body += ["", "result:", *[f"  {line}" for line in tail[-6:]] or ["  (no OK/FAILURES line)"], "```"]
    post("\n".join(body))


if __name__ == "__main__":
    try:
        main()
    except urllib.error.URLError as error:  # pragma: no cover - network only
        print("relay skipped:", error)
