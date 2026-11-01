#!/usr/bin/env python3
"""Strict M0 vs M1 real-Minecraft benchmark comparison with saved raw evidence."""
from __future__ import annotations

import argparse
import json
import shutil
import statistics
import sys
from pathlib import Path

SCENARIOS = ("sprite_1k", "dynamic_1k", "collision_1k", "model_1k", "sprite_10k")
METRICS = ("medianSimulationMs", "p95SimulationMs", "medianRenderSubmissionMs",
           "p95RenderSubmissionMs", "medianFrameMs", "p95FrameMs", "gpuTimeMs")


def read(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def median(reports: list[dict], metric: str) -> float | None:
    values = [x.get(metric) for x in reports]
    return statistics.median(values) if all(isinstance(v, (int, float)) for v in values) else None


def metric(value: float | None, precision: int = 3) -> str:
    return "unavailable" if value is None else f"{value:.{precision}f}"


def delta(before: float | None, after: float | None) -> str:
    return "unavailable" if before in (None, 0) or after is None else f"{(after/before-1)*100:+.1f}%"


def alloc_per_tick(r: dict) -> float | None:
    bytes_ = r.get("tickAllocatedBytes")
    ticks = r.get("measurementTickSamples")
    return bytes_ / ticks / (1024 * 1024) if isinstance(bytes_, (int, float)) and ticks else None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--after", required=True, help="minimum benchmark filename UTC timestamp YYYYMMDD-HHMMSS")
    parser.add_argument("--output", default="docs/benchmarks/m1-2026-11-02")
    args = parser.parse_args()

    m0 = Path("docs/benchmarks/m0-final-2026-11-02")
    candidates = Path("run/pyrovfx-benchmarks")
    output = Path(args.output)

    table = [
        "# M1 — compiled VFX program performance comparison",
        "",
        "Three independent real Minecraft client launches for each scenario before (M0) "
        "and after (M1) expression compilation/slot execution. Both sets include fixed "
        "VFX seed, JSON SHA-256, Minecraft version, GPU and resolution.",
        "",
        "| Scenario | M0 simulation median (ms) | M1 simulation median (ms) | CPU reduction | M0 alloc (MiB/tick) | M1 alloc (MiB/tick) | Allocation reduction |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    tail = ["", "## Tail latency, rendering, GC and raw measurements", "",
            "| Scenario | M0 P95 simulation (ms) | M1 P95 simulation (ms) | M0 P95 frame (ms) | M1 P95 frame (ms) | GC M0 (collections) | GC M1 (collections) |",
            "|---|---:|---:|---:|---:|---|---|"]
    raw = []
    found = []
    for scenario in SCENARIOS:
        old_paths = [m0 / f"{scenario}-run-{i}.json" for i in range(1, 4)]
        new_paths = sorted(x for x in candidates.glob(f"{scenario}-*.json")
                           if x.stem[len(scenario) + 1:] >= args.after)
        if len(new_paths) != 3:
            raise SystemExit(f"{scenario}: expected 3 M1 runs, found {len(new_paths)}: {new_paths}")
        old = [read(x) for x in old_paths]
        new = [read(x) for x in new_paths]
        ref = old[0]
        for report in old + new:
            if report.get("status") != "completed":
                raise SystemExit(f"{scenario}: invalid benchmark: {report.get('error')}")
            for key in ("effectSha256", "seed", "requestedParticles", "peakActiveParticles"):
                if report.get(key) != ref.get(key):
                    raise SystemExit(f"{scenario}: baseline mismatch {key}")
            for key in ("minecraft", "gpuRenderer", "width", "height"):
                if report["environment"].get(key) != ref["environment"].get(key):
                    raise SystemExit(f"{scenario}: incompatible environment {key}")
            if not report.get("measurementTickSamples"):
                raise SystemExit(f"{scenario}: no tick samples")
            if scenario == "collision_1k" and not report.get("collisionEventObserved"):
                raise SystemExit("Collision event never observed")
        alloc_old = statistics.median(alloc_per_tick(x) for x in old)
        alloc_new = statistics.median(alloc_per_tick(x) for x in new)
        before = median(old, "medianSimulationMs")
        after = median(new, "medianSimulationMs")
        table.append(
            f"| {scenario} | {metric(before)} | {metric(after)} | {delta(before,after)} "
            f"| {metric(alloc_old,2)} | {metric(alloc_new,2)} | {delta(alloc_old,alloc_new)} |")
        tail.append(
            f"| {scenario} | {metric(median(old,'p95SimulationMs'))} "
            f"| {metric(median(new,'p95SimulationMs'))} "
            f"| {metric(median(old,'p95FrameMs'))} | {metric(median(new,'p95FrameMs'))} "
            f"| {', '.join(str(x['gcCollections']) for x in old)} "
            f"| {', '.join(str(x['gcCollections']) for x in new)} |")
        for index, source in enumerate(new_paths, start=1):
            raw.append((source, output / f"{scenario}-run-{index}.json"))
            found.append((scenario, source.name))

    output.mkdir(parents=True, exist_ok=True)
    for source, dest in raw:
        shutil.copyfile(source, dest)

    table += tail
    table += [
        "",
        "## Additional correctness checks",
        "",
        "Prior to declaring M1 complete, verify the following independent client scenarios "
        "and record their `status=completed`: `event_order`, `self_emit_collision`, "
        "`model_rotation`, `model_inheritance`, `reload_active`, "
        "`curves`, `events` and `uv_animation`. Server GameTests additionally "
        "assert exact deterministic motion, rotation, animated-UV, event order, parameter "
        "updates and legacy/compiled random sequences.",
        "",
    ]

    checks = ("event_order", "self_emit_collision", "model_rotation", "model_inheritance",
              "reload_active", "curves", "events", "uv_animation")
    for scenario in checks:
        options = sorted(x for x in candidates.glob(f"{scenario}-*.json")
                         if x.stem[len(scenario)+1:] >= args.after)
        if not options:
            raise SystemExit(f"Missing post-M1 smoke: {scenario}")
        last = options[-1]
        report = read(last)
        if report.get("status") != "completed":
            raise SystemExit(f"Post-M1 smoke failed: {scenario}: {report.get('error')}")
        if scenario == "reload_active" and not report.get("reloadCompleted"):
            raise SystemExit("Actual client resource reload did not complete")
        shutil.copyfile(last, output / f"{scenario}.json")
        table.append(f"- `{scenario}`: completed ({last.name}).")

    table += [
        "",
        "## What is and is not measured",
        "",
        "- Simulation time is the actual Pyro VFX Minecraft client tick callback, "
        "not an offline math microbenchmark. Tick allocations use HotSpot thread-local "
        "allocated bytes during the measured callback; JVM-wide GC is also recorded.",
        "- These are medians of three independent client launches each, not a controlled "
        "randomized experiment. JIT, driver load, Minecraft frame pacing, thermal clocks "
        "and unrelated JVM activity can affect the distribution.",
        "- P95 wall-clock FPS and renderer GPU time must not be confused with simulation "
        "cost. The renderer remains legacy after M1.",
        "- Test inputs are frozen `pyro_vfx:1` fixture JSON files; no visual or "
        "simulation-feature downgrade is intentionally allowed.",
        "- Neither 100k/1M nor shader-mod compatibility is claimed by this milestone.",
        "- M1 report JSon files were captured in a working tree before commit, "
        "so record the final source commit with this report.",
        "",
    ]
    output.joinpath("README.md").write_text("\n".join(table), encoding="utf-8")
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    print("\n".join(table))


if __name__ == "__main__":
    main()
