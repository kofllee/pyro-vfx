#!/usr/bin/env python3
"""Summarize three verified real Minecraft client runs versus the frozen pre-M0 baseline."""
from __future__ import annotations

import argparse
import json
import shutil
import statistics
import sys
from pathlib import Path

NAMES = ("sprite_1k", "dynamic_1k", "collision_1k", "model_1k", "sprite_10k")
METRICS = ("medianSimulationMs", "p95SimulationMs", "p95RenderSubmissionMs",
           "p95FrameMs", "gpuTimeMs")


def read(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def median_metric(reports: list[dict], metric: str) -> float | None:
    values = [r.get(metric) for r in reports]
    if any(v is None for v in values):
        return None
    return statistics.median(values)


def format_delta(original: float | None, candidate: float | None) -> str:
    if original is None or candidate is None or original == 0:
        return "unavailable"
    return f"{(candidate / original - 1) * 100:+.1f}%"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--prefix", required=True,
                        help="UTC filename timestamp prefix, e.g. 20261101-221")
    parser.add_argument("--output", default="docs/benchmarks/m0-final-2026-11-02")
    args = parser.parse_args()

    baseline = Path("docs/benchmarks/baseline-2026-11-01")
    source = Path("run/pyrovfx-benchmarks")
    output = Path(args.output)
    pending = []
    markdown = [
        "# M0 — final real-client benchmark report",
        "",
        "All metrics come from **three independent Minecraft client launches per scenario** "
        "after safety and lifecycle fixes, against the frozen three-run pre-M0 distribution. "
        "Fixed VFX seed and effect SHA-256, matching peak count, resolution, GPU and Minecraft version "
        "are verified for each comparison.",
        "",
        "The run's embedded Git SHA is the repository HEAD when measured. "
        "If source edits were uncommitted during a run, that SHA alone does not identify the entire worktree; "
        "use the M0 completion commit together with these recorded reports.",
        "",
        "| Scenario | Pre CPU median ms | M0 CPU median ms | Δ CPU | Pre P95 frame ms | M0 P95 frame ms | Δ P95 frame |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    details = [
        "",
        "## Tail latency, render submissions, allocations and GC",
        "",
        "| Scenario | P95 sim pre → M0 (ms) | P95 render pre → M0 (ms) | Tick allocated M0 (MiB/tick) | GC collections (3 runs) | GC time (ms, 3 runs) |",
        "|---|---:|---:|---:|---:|---:|",
    ]
    for name in NAMES:
        old_paths = [baseline / f"{name}.json"] + sorted(
            (baseline / "repeats").glob(f"{name}-repeat-*.json"))
        new_paths = sorted(source.glob(f"{name}-{args.prefix}*.json"))
        if len(old_paths) != 3 or len(new_paths) != 3:
            raise SystemExit(f"{name}: expected 3 baseline and 3 candidate reports, "
                             f"found {len(old_paths)} and {len(new_paths)}: {new_paths}")
        previous = [read(p) for p in old_paths]
        candidate = [read(p) for p in new_paths]
        reference = previous[0]
        for report in previous + candidate:
            if report["status"] != "completed":
                raise SystemExit(f"{name}: incomplete run: {report.get('error')}")
            for key in ("effectSha256", "seed", "peakActiveParticles"):
                if report.get(key) != reference.get(key):
                    raise SystemExit(f"{name}: incompatible {key}")
            for key in ("gpuRenderer", "width", "height", "minecraft"):
                envkey = key if key != "minecraft" else "minecraft"
                if report["environment"].get(envkey) != reference["environment"].get(envkey):
                    raise SystemExit(f"{name}: incompatible environment {key}")
            if name == "collision_1k" and not report.get("collisionEventObserved"):
                raise SystemExit(f"{name}: collision trigger was never observed")
        for path, report in zip(new_paths, candidate):
            pending.append((path, output / f"{name}-run-{new_paths.index(path)+1}.json"))

        old_cpu = median_metric(previous, "medianSimulationMs")
        new_cpu = median_metric(candidate, "medianSimulationMs")
        old_frame = median_metric(previous, "p95FrameMs")
        new_frame = median_metric(candidate, "p95FrameMs")
        markdown.append(
            f"| {name} | {old_cpu:.3f} | {new_cpu:.3f} | {format_delta(old_cpu,new_cpu)} "
            f"| {old_frame:.2f} | {new_frame:.2f} | {format_delta(old_frame,new_frame)} |")

        cpu_p95_before = median_metric(previous, "p95SimulationMs")
        cpu_p95_after = median_metric(candidate, "p95SimulationMs")
        render_before = median_metric(previous, "p95RenderSubmissionMs")
        render_after = median_metric(candidate, "p95RenderSubmissionMs")
        allocated = [r["tickAllocatedBytes"] / r["measurementTickSamples"] / (1024 ** 2)
                     for r in candidate if r.get("tickAllocatedBytes") is not None
                     and r.get("measurementTickSamples")]
        allocation_note = f"{statistics.median(allocated):.2f}" if len(allocated) == 3 else "unavailable"
        gc_events = ", ".join(str(r.get("gcCollections", "n/a")) for r in candidate)
        gc_times = ", ".join(str(r.get("gcTimeMs", "n/a")) for r in candidate)
        details.append(
            f"| {name} | {cpu_p95_before:.3f} → {cpu_p95_after:.3f} "
            f"({format_delta(cpu_p95_before,cpu_p95_after)}) | "
            f"{render_before:.3f} → {render_after:.3f} "
            f"({format_delta(render_before,render_after)}) | {allocation_note} "
            f"| {gc_events} | {gc_times} |")
    output.mkdir(parents=True, exist_ok=True)
    for source_path, destination in pending:
        shutil.copyfile(source_path, destination)
    for name in ("budget_spike", "reload_active"):
        candidates = sorted(source.glob(f"{name}-20261101-22*.json"))
        if not candidates:
            raise SystemExit(f"Missing client verification {name}")
        record = read(candidates[-1])
        if record.get("status") != "completed":
            raise SystemExit(f"Safety/lifecycle verification failed for {name}")
        if name == "budget_spike" and record["peakActiveParticles"] != 20_000:
            raise SystemExit("Budget test failed to enforce 20k cap")
        if name == "reload_active" and not record.get("reloadCompleted"):
            raise SystemExit("Active-instance client resource reload not completed")
        shutil.copyfile(candidates[-1], output / (name + ".json"))
    markdown += details
    markdown += [
        "",
        "## Interpretation and limits",
        "",
        "- CPU/GC values measure JVM and renderer behavior on this one Windows hardware setup; "
        "three repeats are suitable for screening, not formal statistical significance.",
        "- Thread allocation counters record Java bytes allocated by the render/tick thread "
        "during measured VFX callbacks; they do not identify allocation sites. "
        "GC collections and times are whole-JVM counters.",
        "- `gpuTimeMs` is only the renderer GPU slice. Small microsecond differences are "
        "subject to timer quantization and scheduling noise; do not claim GPU speedups.",
        "- Comparing P95 distributions against a three-run median can reveal possible regressions, "
        "not prove their cause. Investigate sustained >15% tail changes in M1 profiling.",
        "- Neither 100k nor 1M was run. Explicit opt-in is required and can still run out of memory.",
        "- `reload_active.json` proves an actual client resource reload survived while a VFX "
        "instance was active. A complete multiplayer/world-transition soak or shader-mod matrix "
        "was not performed.",
        "- `budget_spike.json` checks two concurrent emitters requesting 40k particles total, "
        "capped at 20k within one effect.",
        "",
    ]
    (output / "README.md").write_text("\n".join(markdown), encoding="utf-8")
    # Windows PowerShell may expose cp1252 to subprocesses; reports are UTF-8.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    print("\n".join(markdown))


if __name__ == "__main__":
    main()
