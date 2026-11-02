#!/usr/bin/env python3
"""Validate real-client M6 collision cache A/B recordings.

Both routes use exactly the same effect JSON and deterministic seed. Reports
with invalid status, missing measurements, different windows or missing world
contacts are *not* acceptable evidence of an optimization. This tool only
compares recorded client runs; it never produces synthetic timing values.
"""
from __future__ import annotations

import argparse
import json
import statistics
import sys
from pathlib import Path

SCENARIOS = {
    "collision_no_events": (1000, False),
    "collision_no_events_10k": (10000, False),
    "collision_1k": (1000, True),
}
IDENTITY = ("effectSha256", "seed", "effect", "requestedParticles",
            "warmupTicks", "measurementTicks")
ENVIRONMENT = ("minecraft", "gpuRenderer", "width", "height",
               "vsync", "renderDistance", "fov", "world")


def load(folder: Path, name: str, after: str) -> list[dict]:
    records = []
    for path in sorted(folder.glob(f"{name}-*.json")):
        if path.stem[len(name) + 1:] < after:
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        data["_file"] = path.name
        records.append(data)
    return records


def median(values: list[dict], key: str) -> float:
    return statistics.median(float(r[key]) for r in values)


def main() -> int:
    args = argparse.ArgumentParser(description=__doc__)
    args.add_argument("--folder", type=Path, default=Path("run/pyrovfx-benchmarks"))
    args.add_argument("--after", default="20261102-024000",
                      help="Ignore older/invalid harness runs")
    args.add_argument("--min-per-route", type=int, default=1)
    opt = args.parse_args()
    errors = []
    rows = []
    for name, (count, event_expected) in SCENARIOS.items():
        records = load(opt.folder, name, opt.after)
        off = [r for r in records if r.get("collisionCacheDisabled") is True]
        on = [r for r in records if r.get("collisionCacheDisabled") is False]
        if min(len(off), len(on)) < opt.min_per_route:
            errors.append(f"{name}: need {opt.min_per_route} real runs on each route "
                          f"(found off={len(off)}, on={len(on)})")
            continue
        reference = off[0]
        for r in records:
            prefix = f"{r['_file']}"
            if r.get("status") != "completed" or r.get("error"):
                errors.append(f"{prefix}: invalid: {r.get('error')}")
            if r.get("peakActiveParticles") != count:
                errors.append(f"{prefix}: expected {count} peak particles")
            if r.get("collisionContacts", 0) <= 0:
                errors.append(f"{prefix}: no observed collision contacts")
            if r.get("collisionEventObserved") != event_expected:
                errors.append(f"{prefix}: event semantics mismatch")
            if r.get("measurementFrameSamples", 0) < 50 or r.get("measurementTickSamples", 0) < 50:
                errors.append(f"{prefix}: insufficient real-frame samples")
            if r.get("collisionCacheDisabled") is True and r.get("collisionCacheHits", -1) != 0:
                errors.append(f"{prefix}: disabled cache reported hits")
            if r.get("collisionCacheDisabled") is False and r.get("collisionCacheHits", 0) <= 0:
                errors.append(f"{prefix}: enabled cache never hit")
            for key in IDENTITY:
                if r.get(key) != reference.get(key):
                    errors.append(f"{prefix}: mismatched {key}")
            for key in ENVIRONMENT:
                if r.get("environment", {}).get(key) != reference.get("environment", {}).get(key):
                    errors.append(f"{prefix}: mismatched environment {key}")
            if r.get("collisionContacts") != reference.get("collisionContacts"):
                errors.append(f"{prefix}: changed contact count: "
                              f"{r.get('collisionContacts')} vs {reference.get('collisionContacts')}")
        cpu_off = median(off, "medianSimulationMs")
        cpu_on = median(on, "medianSimulationMs")
        p95_off = median(off, "p95FrameMs")
        p95_on = median(on, "p95FrameMs")
        rows.append((name, len(off), len(on), cpu_off, cpu_on, p95_off, p95_on))

    print("M6 collision cache verification — real Minecraft client only")
    print("Scenario | runs uncached/cached | CPU simulation medians ms | frame P95 medians ms")
    for name, n_off, n_on, cpu_off, cpu_on, p95_off, p95_on in rows:
        print(f"{name} | {n_off}/{n_on} | {cpu_off:.4f} -> {cpu_on:.4f} "
              f"({cpu_off / cpu_on:.2f}x) | {p95_off:.3f} -> {p95_on:.3f}")
    if errors:
        print("FAILED:")
        for error in errors:
            print("  - " + error)
        return 1
    print("PASS: matching effect, seed, world, resolution, contacts and event semantics")
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())