#!/usr/bin/env python3
"""Verify archived *real Minecraft client* reload/telemetry soak reports."""
from __future__ import annotations
import argparse
import json
import sys
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reports", type=Path, nargs="+")
    args = parser.parse_args()
    if not args.reports:
        raise ValueError("At least one measured report is required")
    for path in args.reports:
        run = json.loads(path.read_text(encoding="utf-8"))
        checks = {
            "real completed client run": run.get("status") == "completed" and not run.get("error"),
            "correct mixed workload": run.get("scenario") == "soak_mixed",
            "three full reloads": run.get("reloadCyclesCompleted") == 3,
            "stable live effects": run.get("peakActiveParticles", 0) >= 50,
            "nonblocking GPU samples": run.get("runtimeGpuTelemetrySamples", 0) >= 20,
            "actual measurement duration": run.get("measurementTickSamples", 0) >= 600,
            "samples are from real frames": run.get("measurementFrameSamples", 0) >= 100,
            "second monitor": run.get("environment", {}).get("backgroundSecondMonitorRequested") is True,
            "mouse not grabbed": run.get("environment", {}).get("mousePassthrough") is True,
            "1280x720 actual size": (run.get("environment", {}).get("width"),
                                      run.get("environment", {}).get("height")) == (1280, 720),
            "GPU query valid": run.get("runtimeGpuTelemetryLastMs", -1) > 0,
            "CPU timing valid": run.get("medianSimulationMs", -1) >= 0,
            "frame timing valid": run.get("p95FrameMs", -1) > 0
        }
        failures = [name for name, ok in checks.items() if not ok]
        print(f"{path.name}: {run.get('measurementTickSamples')} ticks, "
              f"{run.get('reloadCyclesCompleted')} reloads, "
              f"{run.get('peakActiveParticles')} peak particles, "
              f"GPU query samples {run.get('runtimeGpuTelemetrySamples')}, "
              f"heap peak {run.get('maxObservedHeapBytes', 0) / 1024**2:.1f} MiB, "
              f"GC {run.get('gcCollections')}")
        if failures:
            raise ValueError(f"{path.name}: failed checks: {', '.join(failures)}")
    print("PASS: archived mixed-client reload and nonblocking telemetry tests")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, KeyError, OSError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)