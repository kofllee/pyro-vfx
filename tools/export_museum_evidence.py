#!/usr/bin/env python3
"""Freeze actual client museum benchmark evidence for code review.

Copies only completed real Minecraft runs; never creates benchmark values.
Run when no Minecraft benchmark is currently writing reports.
"""
import csv
import json
import shutil
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = root / "run/pyrovfx-benchmarks"
dest = root / "docs/benchmarks/museum-final-2026-11-03"
manifest = root / "src/development/resources/pyro_vfx_museum_exhibits.tsv"
scenario_names = [r.split("|")[0] for r in manifest.read_text(encoding="utf-8").splitlines()
                  if r and not r.startswith("#")]
rows = []
for exhibit in scenario_names:
    candidates = sorted(source.glob(f"museum_{exhibit}-*.json"),
                        key=lambda p: p.stat().st_mtime, reverse=True)
    if not candidates:
        rows.append(dict(exhibit=exhibit, status="missing"))
        continue
    path = candidates[0]
    data = json.loads(path.read_text(encoding="utf-8"))
    assert data.get("scenario") == f"museum_{exhibit}", path
    status = data.get("status")
    if status == "completed" and data.get("peakActiveParticles", 0) < 1:
        status = "zero visible particles"
    rows.append(dict(
        exhibit=exhibit, status=status, backend=data.get("selectedParticleBackend", ""),
        renderer=data.get("selectedRenderer", ""), peak=data.get("peakActiveParticles", 0),
        medianFps=data.get("medianFps", ""), p95FrameMs=data.get("p95FrameMs", ""),
        p99FrameMs=data.get("p99FrameMs", ""), medianSimulationMs=data.get("medianSimulationMs", ""),
        medianRenderSubmissionMs=data.get("medianRenderSubmissionMs", ""),
        maxHeapBytes=data.get("maxObservedHeapBytes", ""),
        particleStateBytes=data.get("allocatedParticleStateBytes", ""),
        spawnCpuMs=data.get("spawnCpuMs", ""), gpuMedianMs=data.get("gpuTimeMs"),
        gpuP95Ms=data.get("gpuP95Ms"), gpuSamples=data.get("gpuSamples", 0),
        effectSha256=data.get("effectSha256", ""), sourceReport=path.name))
    target = dest / "vanilla" / path.name
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(path, target)
    if exhibit in {"model_item", "model_custom", "model_translucent",
                   "smoke_alpha", "spawn_ring", "spawn_cone", "gpu_curves",
                   "model_entity", "self_emit"}:
        screenshot = data.get("screenshot")
        if screenshot and Path(screenshot).exists():
            shutil.copy2(screenshot, dest / "vanilla" / (exhibit + ".png"))
source_compat = source / "museum-compatibility"
if source_compat.is_dir():
    target_compat = dest / "compatibility"
    target_compat.mkdir(parents=True, exist_ok=True)
    summary = source_compat / "profile-results.json"
    if summary.exists():
        shutil.copy2(summary, target_compat / "profile-results.json")
    for p in source_compat.iterdir():
        if not p.is_dir(): continue
        target_dir = target_compat / p.name
        target_dir.mkdir(parents=True, exist_ok=True)
        for path in p.glob("*.json"):
            shutil.copy2(path, target_dir / path.name)
            report = json.loads(path.read_text(encoding="utf-8"))
            screenshot = report.get("screenshot")
            if screenshot and Path(screenshot).exists() and (
                    "smoke_alpha" in path.name or "model_custom" in path.name):
                shutil.copy2(screenshot, target_dir / (report["scenario"] + ".png"))
dest.mkdir(parents=True, exist_ok=True)
with (dest / "museum-results.csv").open("w", encoding="utf-8", newline="") as f:
    cols = list(dict.fromkeys(k for row in rows for k in row.keys()))
    w = csv.DictWriter(f, fieldnames=cols)
    w.writeheader()
    w.writerows(rows)
complete = [r for r in rows if r["status"] == "completed"]
print(f"Archived {len(complete)}/{len(rows)} completed exhibits")
for row in rows:
    if row["status"] != "completed": print("NOT VERIFIED", row)
if len(complete) != len(rows):
    raise SystemExit("Incomplete museum coverage; do not publish readiness claim")
