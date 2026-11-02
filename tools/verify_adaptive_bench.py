#!/usr/bin/env python3
"""Validate a real-client adaptive-spawn A/B pair without inventing speedups."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def load(path: Path) -> dict:
    record = json.loads(path.read_text(encoding="utf-8"))
    if record.get("status") != "completed" or record.get("error"):
        raise ValueError(f"{path.name}: benchmark is invalid: {record.get('error')}")
    if record.get("scenario") != "adaptive_steady":
        raise ValueError(f"{path.name}: wrong benchmark scenario")
    return record


def check(off: dict, on: dict) -> None:
    fields = ("effect", "effectSha256", "seed", "requestedParticles",
              "warmupTicks", "measurementTicks", "selectedParticleBackend",
              "collisionCacheDisabled")
    environment = ("minecraft", "gpuRenderer", "os", "width", "height",
                   "vsync", "world", "renderDistance", "fov", "mousePassthrough")
    for field in fields:
        if off.get(field) != on.get(field):
            raise ValueError(f"Mismatch in {field}")
    for field in environment:
        if off.get("environment", {}).get(field) != on.get("environment", {}).get(field):
            raise ValueError(f"Mismatch in environment.{field}")
    if off.get("adaptiveEnabledForBenchmark") is not False:
        raise ValueError("Control run was not adaptive-off")
    if on.get("adaptiveEnabledForBenchmark") is not True:
        raise ValueError("Second run was not adaptive-on")
    if off.get("minimumAdaptiveSpawnScale") != 1.0:
        raise ValueError("Control run throttled unexpectedly")
    if not 0.05 <= on.get("minimumAdaptiveSpawnScale", -1) < 1.0:
        raise ValueError("Adaptive run did not show bounded throttling")
    if on.get("adaptiveConstrainedTickSamples", 0) < 5:
        raise ValueError("Too few recorded adaptive ticks")
    if on.get("globalRejectedSpawns", 0) <= off.get("globalRejectedSpawns", 0):
        raise ValueError("No observed adaptive spawn rejection")
    for record in (off, on):
        if record.get("measurementTickSamples", 0) < 50:
            raise ValueError("Too few CPU simulation samples")
        if record.get("peakActiveParticles", 0) <= 0:
            raise ValueError("No live particles observed")
        if record.get("globalRejectedEvents", 0) != 0:
            raise ValueError("Event safety budget was unexpectedly exceeded")
        if record.get("environment", {}).get("backgroundSecondMonitorRequested") is not True:
            raise ValueError("Benchmark did not request passive secondary display")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("control", type=Path)
    ap.add_argument("adaptive", type=Path)
    args = ap.parse_args()
    off, on = load(args.control), load(args.adaptive)
    check(off, on)
    cpu0, cpu1 = off["medianSimulationMs"], on["medianSimulationMs"]
    frame0, frame1 = off["p95FrameMs"], on["p95FrameMs"]
    print(f"Verified identical fixture/seed/client conditions; real particles: "
          f"{off['peakActiveParticles']} off, {on['peakActiveParticles']} on")
    print(f"CPU simulation median: {cpu0:.5f} -> {cpu1:.5f} ms ({cpu0 / cpu1:.2f}x)")
    print(f"Full-frame P95: {frame0:.3f} -> {frame1:.3f} ms")
    print(f"Minimum spawn scale: {on['minimumAdaptiveSpawnScale']:.3f}; "
          f"constrained samples: {on['adaptiveConstrainedTickSamples']}")
    print("PASS. This is one A/B pair, not a statistically significant FPS result.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, KeyError, OSError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)