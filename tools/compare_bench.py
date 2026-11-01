#!/usr/bin/env python3
"""Compare two real Pyro VFX client benchmark JSON reports; no external dependencies."""
import argparse
import json
import sys
from pathlib import Path

METRICS = (
    "medianFrameMs",
    "p95FrameMs",
    "medianSimulationMs",
    "p95SimulationMs",
    "medianRenderSubmissionMs",
    "p95RenderSubmissionMs",
    "gpuTimeMs",
    "gpuP95Ms",
)
IDENTITY = ("scenario", "effect", "effectSha256", "seed", "requestedParticles", "warmupTicks", "measurementTicks")
ENVIRONMENT = ("minecraft", "gpuRenderer", "openGlVersion", "width", "height", "world", "renderDistance", "fov", "vsync")


def compare(base, candidate, threshold):
    problems = []
    if not base.get("effectSha256") or not candidate.get("effectSha256"):
        problems.append("Missing effectSha256; cannot verify identical VFX fixtures")
    for field in IDENTITY:
        if base.get(field) != candidate.get(field):
            problems.append(f"{field}: {base.get(field)} != {candidate.get(field)}")
    for field in ENVIRONMENT:
        before = base.get("environment", {}).get(field)
        after = candidate.get("environment", {}).get(field)
        if before != after:
            problems.append(f"environment.{field}: {before} != {after}")
    if base.get("status") != "completed" or candidate.get("status") != "completed":
        problems.append("Both reports must have status=completed")
    if base.get("measurementFrameSamples", 0) < 30 or candidate.get("measurementFrameSamples", 0) < 30:
        problems.append("Insufficient frame samples (30 required)")
    if base.get("measurementTickSamples", 0) < 30 or candidate.get("measurementTickSamples", 0) < 30:
        problems.append("Insufficient tick samples (30 required)")
    if problems:
        return 3, {"status": "incomparable", "problems": problems}

    metrics = {}
    regressions = []
    for name in METRICS:
        old, new = base.get(name), candidate.get(name)
        if old is None or new is None or old <= 0:
            metrics[name] = {"status": "unavailable"}
            continue
        change = (new / old) - 1.0
        metrics[name] = {"baseline": old, "candidate": new, "changePercent": round(change * 100, 2)}
        if change > threshold:
            regressions.append(name)
    return (2 if regressions else 0), {
        "status": "regression" if regressions else "pass",
        "thresholdPercent": round(threshold * 100, 1),
        "metrics": metrics,
        "regressions": regressions,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--threshold", type=float, default=0.15, help="Fractional slowdown (default 0.15)")
    parser.add_argument("--output", type=Path, help="Optional machine-readable comparison output")
    args = parser.parse_args()
    if not 0 < args.threshold < 1:
        parser.error("--threshold must be between 0 and 1")
    base = json.loads(args.baseline.read_text(encoding="utf-8"))
    candidate = json.loads(args.candidate.read_text(encoding="utf-8"))
    code, report = compare(base, candidate, args.threshold)
    report.update({"baselineFile": str(args.baseline), "candidateFile": str(args.candidate)})
    output = json.dumps(report, indent=2)
    print(output)
    if args.output:
        args.output.write_text(output + "\n", encoding="utf-8")
    sys.exit(code)


if __name__ == "__main__":
    main()
