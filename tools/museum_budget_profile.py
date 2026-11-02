#!/usr/bin/env python3
"""Inspect/apply/revert exact museum benchmark budget profiles.

'show' never edits user config. 'apply' requires explicit profile and creates
a byte-perfect one-time backup; 'restore' restores it. Unknown TOML keys and
comments are preserved. Never silently migrate old 80k/20k limits.
"""
import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "run/config/pyro_vfx-client.toml"
BACKUP = ROOT / "run/config/pyro_vfx-client.toml.museum-backup"
DEFAULT = {
    "global_particles": 240000, "global_spawns_per_tick": 100000,
    "effect_particles": 120000, "effect_spawns_per_tick": 100000,
    "active_effects": 256, "gpu_analytic_particles": 240000,
    "cpu_basic_particles": 120000, "cpu_collision_particles": 20000,
    "cpu_model_particles": 120000,
}
LEGACY = dict(DEFAULT, global_particles=80000, global_spawns_per_tick=30000,
              effect_particles=20000, effect_spawns_per_tick=20000,
              gpu_analytic_particles=80000, cpu_basic_particles=80000,
              cpu_model_particles=10000)
PROFILES = {"modern": DEFAULT, "legacy": LEGACY}


def values(data):
    return {k: int(v) for k, v in re.findall(
        r"(?m)^\s*([a-z_]+)\s*=\s*(\d+)\s*(?:#.*)?$", data)
        if k in DEFAULT}


def show():
    if not CONFIG.exists():
        print("No local TOML; current NeoForge defaults will be generated")
        return
    actual = values(CONFIG.read_text(encoding="utf-8"))
    for k, default in DEFAULT.items():
        print(f"{k}: active={actual.get(k, '<new default>')} default={default}"
              + (" (legacy/custom override)" if k in actual and actual[k] != default else ""))
    print("No values modified.")


def apply(profile):
    if BACKUP.exists():
        raise SystemExit("A museum backup already exists. Run restore before new profile.")
    if not CONFIG.exists():
        raise SystemExit("Config not found. Launch Minecraft once first.")
    data = CONFIG.read_bytes()
    raw = data.decode("utf-8")
    new = raw
    for key, value in PROFILES[profile].items():
        pattern = rf"(?m)^(\s*{re.escape(key)}\s*=\s*)\d+(\s*(?:#.*)?)$"
        new, count = re.subn(pattern, lambda m: m.group(1) + str(value) + m.group(2), new)
        if count != 1: raise SystemExit("Unable to precisely replace TOML entry " + key)
    BACKUP.write_bytes(data)
    try: CONFIG.write_bytes(new.encode("utf-8"))
    except Exception:
        CONFIG.write_bytes(data)
        BACKUP.unlink()
        raise
    print("Temporarily installed", profile, "museum profile. Use restore afterward.")


def restore():
    if not BACKUP.exists(): raise SystemExit("No museum backup to restore")
    CONFIG.write_bytes(BACKUP.read_bytes())
    BACKUP.unlink()
    print("Original user TOML restored exactly.")


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("action", choices=["show","apply","restore"])
    p.add_argument("--profile", choices=PROFILES, default="modern")
    a = p.parse_args()
    if a.action == "show": show()
    elif a.action == "apply": apply(a.profile)
    else: restore()
