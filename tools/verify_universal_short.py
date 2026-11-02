#!/usr/bin/env python3
"""Check recorded short real-client model and analytic performance evidence.

Does not pretend to verify GPU implementations by replaying JSON: it ensures
archived runs actually used the intended backend, resolution, fixture and
particle counts. Screenshot parity is checked separately with Java.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ARCHIVE = ROOT / "docs/benchmarks/m6-2026-11-02/universal-short"
CASES = {
    "model_10k-20261102-152044.json": ("model_10k", 10_000, 10_000),
    "model_rotating_10k-20261102-151942.json": ("model_rotating_10k", 10_000, 10_000),
    "model_block_10k-20261102-152052.json": ("model_block_10k", 10_000, 10_000),
    "model_item_10k-20261102-151934.json": ("model_item_10k", 10_000, 10_000),
    "model_100k-20261102-151925.json": ("model_100k", 100_000, 100_000),
    "model_item_1k-20261102-151002.json": ("model_item_1k", 1_000, 1_000),
    "model_item_1k-20261102-150622.json": ("model_item_1k", 1_000, 0),
    "model_rotating_1k-20261102-151425.json": ("model_rotating_1k", 1_000, 1_000),
    "model_rotating_1k-20261102-151232.json": ("model_rotating_1k", 1_000, 0),
    "analytic_steady-20261102-150213.json": ("analytic_steady", 7_800, None),
    "analytic_steady-20261102-150416.json": ("analytic_steady", 7_800, None),
}
PAIRS = (
    ("model_item_1k-20261102-150622.json", "model_item_1k-20261102-151002.json"),
    ("model_rotating_1k-20261102-151232.json", "model_rotating_1k-20261102-151425.json"),
    ("analytic_steady-20261102-150416.json", "analytic_steady-20261102-150213.json"),
)


def fail(message):
    raise ValueError(message)


def check():
    runs = {}
    for filename, (scenario, particles, gpu_models) in CASES.items():
        report = json.loads((ARCHIVE / filename).read_text(encoding="utf-8"))
        env = report.get("environment") or {}
        if report.get("status") != "completed" or report.get("error"):
            fail(f"{filename}: not a completed benchmark")
        if report.get("scenario") != scenario or report.get("peakActiveParticles") != particles:
            fail(f"{filename}: workload or admitted particle count differs")
        if report.get("measurementTickSamples", 0) < 90 or report.get("measurementFrameSamples", 0) < 100:
            fail(f"{filename}: inadequate real tick/frame samples")
        if env.get("width") != 1280 or env.get("height") != 720:
            fail(f"{filename}: actual resolution not the recorded 1280x720")
        if env.get("backgroundSecondMonitorRequested") is not True or env.get("mousePassthrough") is not True:
            fail(f"{filename}: passive secondary-screen requirement not recorded")
        if gpu_models is not None and report.get("gpuModelInstancesLastFrame") != gpu_models:
            fail(f"{filename}: model GPU instance count mismatch")
        if scenario == "analytic_steady":
            if report.get("selectedParticleBackend") not in ("gpu_analytic", "cpu_dense"):
                fail(f"{filename}: unexpected analytic backend")
            if report["selectedParticleBackend"] == "gpu_analytic":
                if report.get("gpuAnalyticDatasetUploadsTotal", 0) < 10:
                    fail(f"{filename}: no continuous GPU cohorts uploaded")
            elif report.get("gpuAnalyticDrawCallsTotal", 0) != 0:
                fail(f"{filename}: CPU comparison accidentally used the GPU backend")
        runs[filename] = report

    for reference, candidate in PAIRS:
        a, b = runs[reference], runs[candidate]
        for identity in ("effectSha256", "seed", "requestedParticles",
                         "warmupTicks", "measurementTicks"):
            if a.get(identity) != b.get(identity):
                fail(f"Unmatched A/B fixture: {reference}, {candidate}: {identity}")
        if a.get("environment", {}).get("gpuRenderer") != b.get("environment", {}).get("gpuRenderer"):
            fail("A/B was collected on different GPU hardware")
        old, new = a["p95FrameMs"], b["p95FrameMs"]
        if old <= 0 or new <= 0:
            fail(f"Invalid frame P95 in pair {reference}, {candidate}")
        print(f"{a['scenario']}: P95 frame {old:.3f} -> {new:.3f} ms; "
              f"CPU median {a['medianSimulationMs']:.3f} -> {b['medianSimulationMs']:.3f} ms")

    print(f"PASS: {len(runs)} actual short client reports; "
          "screenshot RMS comparisons must also pass independently.")


if __name__ == "__main__":
    try:
        check()
    except (OSError, ValueError, KeyError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)