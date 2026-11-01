#!/usr/bin/env python3
"""Verify the exact same client effects through auto-SoA and force-legacy selectors."""
from pathlib import Path
import argparse
import json
import shutil
import sys


SCENARIOS = ("sprite_1k", "dynamic_1k", "collision_1k", "model_1k", "sprite_10k")


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--after", required=True, help="Earliest timestamp of forced legacy client run")
    parser.add_argument("--output", default="docs/benchmarks/m2-2026-11-02")
    args = parser.parse_args()
    root = Path(args.output)
    source = Path("run/pyrovfx-benchmarks")
    lines = ["# CPU backend selector A/B verification", "",
             "One independent real Minecraft client launch with `-PbenchLegacyCpu=true` "
             "compared with the archived median of three automatic dense/fallback launches. "
             "This is a functional A/B input/identity verification, not a new statistical comparison.", "",
             "| Scenario | M1 → auto dense median (ms) | Forced legacy (ms) | Observed particle count |",
             "|---|---:|---:|---:|"]
    for name in SCENARIOS:
        old = [load(root / f"{name}-run-{i}.json") for i in range(1, 4)]
        candidates = sorted(path for path in source.glob(f"{name}-*.json")
                            if path.stem[len(name)+1:] >= args.after)
        candidates = [p for p in candidates if load(p).get("cpuParticleBackend") == "legacy-forced"]
        if len(candidates) != 1:
            raise SystemExit(f"Expected exactly 1 forced-legacy {name} run: {candidates}")
        report = load(candidates[0])
        if report.get("status") != "completed":
            raise SystemExit(f"{name} failed legacy backend: {report.get('error')}")
        for key in ("effectSha256", "seed", "requestedParticles", "peakActiveParticles"):
            if report.get(key) != old[0].get(key):
                raise SystemExit(f"{name}: mismatch {key}")
        for key in ("minecraft", "gpuRenderer", "width", "height"):
            if report["environment"].get(key) != old[0]["environment"].get(key):
                raise SystemExit(f"{name}: environment mismatch {key}")
        if name == "collision_1k" and not report.get("collisionEventObserved"):
            raise SystemExit("Collision event missing on forced backend")
        root.joinpath("forced-legacy").mkdir(parents=True, exist_ok=True)
        shutil.copyfile(candidates[0], root / "forced-legacy" / f"{name}.json")
        import statistics
        dense = statistics.median(r["medianSimulationMs"] for r in old)
        lines.append(f"| {name} | {dense:.3f} | {report['medianSimulationMs']:.3f} "
                     f"| {report['peakActiveParticles']} |")
    root.joinpath("LEGACY_AB.md").write_text("\n".join(lines)+"\n", encoding="utf-8")
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
