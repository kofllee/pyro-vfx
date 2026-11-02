#!/usr/bin/env python3
"""Create a small independent museum Minecraft save from an existing dev-world seed.

Never overwrites an existing save; copies only level.dat and serverconfig,
not terrain/chunks/playerdata or user builds. The museum itself is generated
in-world on first visit via /pyrovfxmuseum build or bench -MuseumBuild.
"""
from pathlib import Path
import gzip
import shutil
import sys

root = Path(__file__).resolve().parents[1]
source = root / "run/saves/Test VFX"
target = root / "run/saves/Pyro VFX Museum"
if target.exists():
    print("Museum world already exists, preserving:", target)
    sys.exit(0)
if not (source / "level.dat").is_file():
    sys.exit("Missing seed world: run/saves/Test VFX/level.dat")
target.mkdir(parents=True)
raw = gzip.decompress((source / "level.dat").read_bytes())
old = b"\x08\x00\x09LevelName\x00\x08Test VFX"
new_name = b"Pyro VFX Museum"
new = b"\x08\x00\x09LevelName" + len(new_name).to_bytes(2,"big") + new_name
if raw.count(old) != 1:
    sys.exit("Could not safely locate original level name; do not rewrite save")
raw = raw.replace(old, new)
(target / "level.dat").write_bytes(gzip.compress(raw))
if (source / "serverconfig").is_dir():
    shutil.copytree(source / "serverconfig", target / "serverconfig")
print("Created independent world:", target)
