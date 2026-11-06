#!/usr/bin/env python3
"""Check documentation links, package manifest and publication hygiene.

Only local paths are checked; historical evidence may remain archived, but
published navigation must not reference missing files. Standard library only.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
LINK = re.compile(r"(?<!!)\[[^\]\n]+\]\(([^)\n]+)\)")
INLINE_IMAGE = re.compile(r"!\[[^\]\n]*\]\(([^)\n]+)\)")
DOCS = [ROOT / "README.md", *sorted((ROOT / "docs").rglob("*.md"))]
SKIP_PREFIXES = ("https://", "http://", "mailto:", "data:", "app://")
errors: list[str] = []

def check_target(source: Path, raw: str) -> None:
    target = raw.strip().strip("<>").split(' "')[0].strip()
    if not target or target.startswith(("#", *SKIP_PREFIXES)):
        return
    path = unquote(target.split("#", 1)[0].split("?", 1)[0])
    if not path:
        return
    if not (source.parent / path).resolve().exists():
        errors.append(f"{source.relative_to(ROOT)}: missing {target}")

for document in DOCS:
    text = document.read_text(encoding="utf-8")
    for pattern in (LINK, INLINE_IMAGE):
        for match in pattern.finditer(text):
            check_target(document, match.group(1))

manifest_path = ROOT / "PACKAGE_MANIFEST.json"
try:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for entry in manifest["documentation"]:
        if not (ROOT / entry).is_file():
            errors.append(f"PACKAGE_MANIFEST.json: missing documentation {entry}")
    for source in manifest["source_sets"].values():
        if not (ROOT / source).is_dir():
            errors.append(f"PACKAGE_MANIFEST.json: missing source set {source}")
except (KeyError, json.JSONDecodeError) as exc:
    errors.append(f"PACKAGE_MANIFEST.json: {exc}")

# No separate renderer mode should need opt-in system properties in shipped code.
for java in (ROOT / "src" / "main").rglob("*.java"):
    if "pyrovfx.experimental." in java.read_text(encoding="utf-8"):
        errors.append(f"{java.relative_to(ROOT)}: unpromoted experimental property")

if errors:
    print("\n".join(errors))
    print(f"Repository integrity check FAILED: {len(errors)} problem(s)", file=sys.stderr)
    sys.exit(1)
print(f"Repository integrity check passed: {len(DOCS)} Markdown files, manifest and render flags.")
