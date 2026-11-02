#!/usr/bin/env python3
"""Temporarily install strictly separated NeoForge 1.21.1 renderer test profiles.

Only files listed in run/museum-profile-state.json are removed on restore.
Refuses to run if user mods already exist. Original Iris settings are backed
up byte-for-byte; downloads are SHA512 verified against Modrinth metadata.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import shutil
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / "run"
MODS = RUN / "mods"
PACKS = RUN / "shaderpacks"
CONFIG = RUN / "config/iris.properties"
STATE = RUN / "museum-profile-state.json"
CACHE = RUN / "museum-profile-cache"
PROFILES = {
    "vanilla": [],
    "sodium": ["uMOpc5uV"],             # NeoForge 1.21.1 0.8.13
    "embeddium": ["J7b96IEd"],           # NeoForge 1.21.1 1.0.15
    "iris": ["Pb3OXVqC", "t3ruzodq"], # NeoForge 1.21.1 Sodium 0.6.13 + Iris 1.8.12
}
SHADERS = {
    "complementary": "complementary-reimagined",
    "bsl": "bsl-shaders",
    "photon": "photon-shader",
}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={
        "User-Agent": "PyroVFX-Museum-CompatibilityHarness/1.0"
    }), timeout=60) as response:
        return response.read()


def download(file, local):
    local.parent.mkdir(parents=True, exist_ok=True)
    if not local.exists() or hashlib.sha512(local.read_bytes()).hexdigest() != file["hashes"]["sha512"]:
        local.write_bytes(get(file["url"]))
    assert hashlib.sha512(local.read_bytes()).hexdigest() == file["hashes"]["sha512"]
    return local


def restore():
    if not STATE.exists():
        print("No installed museum profile")
        return
    state = json.loads(STATE.read_text(encoding="utf-8"))
    for name in state["installed_mods"]:
        path = MODS / name
        if path.exists():
            path.unlink()
    for name in state["installed_packs"]:
        path = PACKS / name
        if path.exists():
            path.unlink()
    backup = RUN / "museum-iris-settings.backup"
    if state["iris_config_existed"]:
        if not backup.exists():
            raise RuntimeError("Iris config backup missing, refusing to discard user settings")
        CONFIG.write_bytes(backup.read_bytes())
        backup.unlink()
    else:
        if CONFIG.exists(): CONFIG.unlink()
    STATE.unlink()
    print("Restored renderer profile. Original config preserved.")


def setup(profile, shader):
    if profile not in PROFILES: raise ValueError(profile)
    if shader != "none" and profile != "iris":
        raise ValueError("Shader packs require Iris profile; Sodium/Embeddium are separate")
    if STATE.exists():
        raise RuntimeError("Restore the previous profile before switching")
    MODS.mkdir(exist_ok=True, parents=True)
    PACKS.mkdir(exist_ok=True, parents=True)
    existing = list(MODS.glob("*.jar"))
    if existing:
        raise RuntimeError("User-installed jars already present; refusing to change run/mods: "
                           + ", ".join(path.name for path in existing))
    current = {"profile": profile, "shader": shader,
               "installed_mods": [], "installed_packs": [],
               "iris_config_existed": CONFIG.exists()}
    backup = RUN / "museum-iris-settings.backup"
    if current["iris_config_existed"]:
        if backup.exists(): raise RuntimeError("Iris settings backup already exists; refusing overwrite")
        backup.write_bytes(CONFIG.read_bytes())
    try:
        for version_id in PROFILES[profile]:
            version = json.loads(get("https://api.modrinth.com/v2/version/" + version_id))
            if "neoforge" not in version["loaders"] or "1.21.1" not in version["game_versions"]:
                raise ValueError("Wrong loader/game version: " + version_id)
            file = next(file for file in version["files"] if file.get("primary", False))
            cached = download(file, CACHE / file["filename"])
            shutil.copy2(cached, MODS / file["filename"])
            current["installed_mods"].append(file["filename"])
        if shader != "none":
            versions = json.loads(get("https://api.modrinth.com/v2/project/"
                    + SHADERS[shader] + "/version"))
            versions = [v for v in versions if "1.21.1" in v["game_versions"]
                        and v["version_type"] == "release"]
            if not versions: raise RuntimeError("No release compatible with 1.21.1 for " + shader)
            version = versions[0]
            file = next(file for file in version["files"] if file.get("primary", False))
            cached = download(file, CACHE / file["filename"])
            shutil.copy2(cached, PACKS / file["filename"])
            current["installed_packs"].append(file["filename"])
            CONFIG.write_text("enableShaders=true\nshaderPack=" + file["filename"] + "\n",
                              encoding="utf-8")
        elif profile == "iris":
            CONFIG.write_text("enableShaders=false\n", encoding="utf-8")
        STATE.write_text(json.dumps(current, indent=2), encoding="utf-8")
        print(json.dumps(current, indent=2))
    except Exception:
        for name in current["installed_mods"]:
            (MODS / name).unlink(missing_ok=True)
        for name in current["installed_packs"]:
            (PACKS / name).unlink(missing_ok=True)
        if current["iris_config_existed"] and backup.exists():
            CONFIG.write_bytes(backup.read_bytes())
            backup.unlink()
        elif not current["iris_config_existed"]:
            CONFIG.unlink(missing_ok=True)
        raise


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("setup", "restore"))
    parser.add_argument("--profile", choices=PROFILES.keys(), default="vanilla")
    parser.add_argument("--shader", choices=["none", *SHADERS], default="none")
    args = parser.parse_args()
    if args.command == "restore": restore()
    else: setup(args.profile, args.shader)
