#!/usr/bin/env python3
"""Validate that an agent branch changed only its assigned paths."""

from __future__ import annotations

import argparse
import fnmatch
import json
import subprocess
import sys
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--branch", required=True, help="Pull-request head branch")
    parser.add_argument("--base", required=True, help="Git base ref, e.g. origin/main")
    parser.add_argument("--head", default="HEAD", help="Git head ref")
    parser.add_argument(
        "--config",
        default=".github/agent-scopes.json",
        help="Path to the scope configuration",
    )
    return parser.parse_args()


def changed_files(base: str, head: str) -> list[str]:
    command = ["git", "diff", "--name-only", "--diff-filter=ACMRTUXB", f"{base}...{head}"]
    result = subprocess.run(command, check=False, capture_output=True, text=True)
    if result.returncode != 0:
        print(result.stderr, file=sys.stderr)
        raise SystemExit("Unable to calculate pull-request changes")
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def matches(path: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatchcase(path, pattern) for pattern in patterns)


def main() -> int:
    args = parse_args()

    if not args.branch.startswith("agent/"):
        print(f"Scope validation skipped for non-agent branch: {args.branch}")
        return 0

    config_path = Path(args.config)
    try:
        scopes = json.loads(config_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        print(f"Failed to read {config_path}: {error}", file=sys.stderr)
        return 2

    patterns = scopes.get(args.branch)
    if not patterns:
        print(
            f"No path scope is registered for {args.branch}. "
            "Ask the integration owner to add the branch before implementation.",
            file=sys.stderr,
        )
        return 1

    files = changed_files(args.base, args.head)
    violations = [path for path in files if not matches(path, patterns)]

    print(f"Branch: {args.branch}")
    print("Allowed patterns:")
    for pattern in patterns:
        print(f"  - {pattern}")
    print("Changed files:")
    for path in files:
        print(f"  - {path}")

    if violations:
        print("Files outside the assigned scope:", file=sys.stderr)
        for path in violations:
            print(f"  - {path}", file=sys.stderr)
        return 1

    print("All changed files are within the assigned scope.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
