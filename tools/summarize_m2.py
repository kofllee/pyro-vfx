#!/usr/bin/env python3
"""Verify three M1 vs data-oriented CPU client samples and archive raw evidence."""
import argparse
import json
import shutil
import statistics
import sys
from pathlib import Path

SCENARIOS = ("sprite_1k", "dynamic_1k", "collision_1k", "model_1k", "sprite_10k")
SMOKES = ("event_order", "self_emit_collision", "collision_no_events", "budget_spike", "model_rotation",
          "model_inheritance", "reload_active", "curves", "events", "uv_animation")


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def median(records, key):
    xs = [r.get(key) for r in records]
    return statistics.median(xs) if all(isinstance(x, (int, float)) for x in xs) else None


def alloc(records):
    samples = []
    for r in records:
        if r.get("tickAllocatedBytes") is None or not r.get("measurementTickSamples"):
            return None
        samples.append(r["tickAllocatedBytes"] / r["measurementTickSamples"] / 1048576)
    return statistics.median(samples)


def val(n):
    return "n/a" if n is None else f"{n:.3f}"


def change(before, after):
    return "n/a" if before in (None, 0) or after is None else f"{100*(after/before - 1):+.1f}%"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--after", required=True, help="UTC YYYYMMDD-HHMMSS earliest candidate")
    parser.add_argument("--output", default="docs/benchmarks/m2-2026-11-02")
    args = parser.parse_args()
    m1 = Path("docs/benchmarks/m1-2026-11-02")
    current = Path("run/pyrovfx-benchmarks")
    output = Path(args.output)
    rows = [
        "# Data-oriented CPU runtime: M1 comparison",
        "",
        "Three independent actual Minecraft client starts per scenario, matching frozen JSON, "
        "seed, peak particle count, Minecraft version, GPU and resolution. "
        "Numbers are medians of reported per-run measurements, not an offline microbenchmark.",
        "",
        "| Scenario | M1 CPU ms | Dense CPU ms | Δ CPU | M1 alloc MiB/tick | Dense alloc MiB/tick | Δ alloc | M1 P95 frame ms | Dense P95 frame ms |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    tails = ["", "## Tail simulation and JVM GC", "",
             "| Scenario | M1 P95 sim ms | Dense P95 sim ms | M1 GC collections | Dense GC collections |",
             "|---|---:|---:|---|---|"]
    pending = []
    for name in SCENARIOS:
        old_files = [m1 / f"{name}-run-{i}.json" for i in range(1, 4)]
        new_files = sorted(p for p in current.glob(f"{name}-*.json")
                           if p.stem[len(name)+1:] >= args.after and
                           p.stem[len(name)+1:].count("-") == 1)
        # Exclude any separate forced-legacy runs. An auto backend is mandatory here.
        new_files = [p for p in new_files
                     if load(p).get("cpuParticleBackend") == "auto-dense-with-feature-fallback"]
        if len(new_files) != 3:
            raise SystemExit(f"{name}: expected 3 candidate files, found {len(new_files)}: {new_files}")
        old = [load(p) for p in old_files]
        new = [load(p) for p in new_files]
        ref = old[0]
        for report in old + new:
            if report.get("status") != "completed" or not report.get("measurementTickSamples"):
                raise SystemExit(f"{name}: incomplete report: {report.get('error')}")
            for field in ("effectSha256", "seed", "requestedParticles", "peakActiveParticles",
                          "warmupTicks", "measurementTicks"):
                if report.get(field) != ref.get(field):
                    raise SystemExit(f"{name}: input mismatch {field}")
            for field in ("minecraft", "gpuRenderer", "width", "height"):
                if report["environment"].get(field) != ref["environment"].get(field):
                    raise SystemExit(f"{name}: environment mismatch {field}")
            if name == "collision_1k" and not report.get("collisionEventObserved"):
                raise SystemExit(f"{name}: collision not observed")
        for i, file in enumerate(new_files, 1):
            pending.append((file, output / f"{name}-run-{i}.json"))
        cpu0, cpu1 = median(old, "medianSimulationMs"), median(new, "medianSimulationMs")
        alloc0, alloc1 = alloc(old), alloc(new)
        rows.append(f"| {name} | {val(cpu0)} | {val(cpu1)} | {change(cpu0,cpu1)} "
                    f"| {val(alloc0)} | {val(alloc1)} | {change(alloc0,alloc1)} "
                    f"| {val(median(old,'p95FrameMs'))} | {val(median(new,'p95FrameMs'))} |")
        tails.append(f"| {name} | {val(median(old,'p95SimulationMs'))} "
                     f"| {val(median(new,'p95SimulationMs'))} "
                     f"| {', '.join(str(x.get('gcCollections')) for x in old)} "
                     f"| {', '.join(str(x.get('gcCollections')) for x in new)} |")
    rows += tails
    rows += ["", "## Regression and safety client probes", ""]
    for name in SMOKES:
        files = sorted(p for p in current.glob(f"{name}-*.json")
                       if p.stem[len(name)+1:] >= args.after)
        if not files:
            raise SystemExit(f"Missing smoke fixture: {name}")
        r = load(files[-1])
        if r.get("status") != "completed":
            raise SystemExit(f"{name}: {r.get('error')}")
        if name == "budget_spike" and r.get("peakActiveParticles") != 20000:
            raise SystemExit("Shared spawn budget was bypassed")
        if name == "reload_active" and not r.get("reloadCompleted"):
            raise SystemExit("Live client reload not verified")
        pending.append((files[-1], output / f"{name}.json"))
        rows.append(f"- `{name}`: completed.")
    output.mkdir(parents=True, exist_ok=True)
    for source, dest in pending:
        shutil.copyfile(source, dest)
    rows += ["", "## Limits and interpretation", "",
             "- The dense CPU path currently covers sprite particles with static or dynamic "
             "collision-free motion, no particle collision triggers, no dynamic rotation and "
             "no environment lighting. Other feature combinations and model particles use "
             "the existing precise CPU path.",
             "- The global CPU backend policy (auto feature routing or forced legacy) is recorded "
             "in each benchmark. Emitter-level selection follows VfxDenseSpritePool.supports(). "
             "The `benchLegacyCpu=true` Gradle flag forces the old backend for replay.",
             "- Particle events remain synchronous per collision boundary: FIFO events are "
             "drained before the next particle, while self-emitted particles are placed in "
             "a bounded pending-spawn buffer until the current emitter update finishes.",
             "- All Minecraft world/block access and rendering remain on the client thread. "
             "Worker-thread simulation is deliberately not enabled without isolated RNG, "
             "immutable parameter input and thread-safe render snapshots.",
             "- JVM warmup, OS scheduling, Minecraft frame load and GPU driver variation "
             "limit causal interpretation of P95/frame metrics. No broad 100k/1M or "
             "shader-compatibility claims are made.",
             "- Report JSON was captured before final commit; keep report and source revision together.",
             ""]
    output.joinpath("README.md").write_text("\n".join(rows), encoding="utf-8")
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    print("\n".join(rows))


if __name__ == "__main__":
    main()
