#!/usr/bin/env python3
"""Reject visually invalid/incompatible runs and compare real Minecraft M2/M3 rendering."""
from __future__ import annotations

import argparse
import json
import shutil
import statistics
import sys
from pathlib import Path

SCENARIOS = ("sprite_1k", "sprite_10k", "dynamic_1k", "collision_1k", "model_1k")
GPU_EXPECTED = {"sprite_1k": "gpuInstancedDrawCallsLastFrame",
                "sprite_10k": "gpuInstancedDrawCallsLastFrame",
                "model_1k": "gpuModelDrawCallsLastFrame"}
SMOKE = ("sprite_uv_instanced", "sprite_world_z", "sprite_horizontal", "sprite_frustum",
         "sprite_lod", "sprite_lit_fallback",
         "reload_active", "model_reload_active", "uv_animation", "event_order",
         "self_emit_collision", "collision_no_events", "budget_spike", "model_rotation",
         "model_inheritance", "curves", "events")


def read(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def median(items: list[dict], key: str) -> float | None:
    nums = [i.get(key) for i in items]
    return statistics.median(nums) if all(isinstance(x, (int,float)) for x in nums) else None


def fmt(x: float | None) -> str:
    return f"{x:.3f}" if x is not None else "unavailable"


def difference(old: float | None, new: float | None) -> str:
    return f"{(new/old - 1)*100:+.1f}%" if old not in (0,None) and new is not None else "unavailable"


def copy_screenshot(report: dict, dest: Path) -> bool:
    s = report.get("screenshot")
    if not s: return False
    file = Path(s)
    if not file.is_file(): return False
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(file, dest)
    return True


def main() -> None:
    arg = argparse.ArgumentParser()
    arg.add_argument("--after", required=True, help="earliest UTC YYYYMMDD-HHMMSS for M3 runs")
    arg.add_argument("--output", default="docs/benchmarks/m3-2026-11-02")
    args = arg.parse_args()
    oldbase = Path("docs/benchmarks/m2-2026-11-02")
    source = Path("run/pyrovfx-benchmarks")
    out = Path(args.output)
    pending = []
    lines = [
        "# Minecraft GPU renderer: CPU and GPU comparison against the frozen CPU runtime",
        "",
        "Each result below is the median of **three independent full Minecraft launches**, "
        "tested with the same VFX JSON SHA-256, seed, particle count, warmup duration, "
        "Minecraft version, resolution and GPU. The old renderer submits CPU quads. "
        "The new renderer batches eligible additive sprites and suitable unlit custom baked models "
        "into GPU instanced draws; the rest retain Minecraft RenderTypes.",
        "",
        "| Scenario | M2 render CPU ms | GPU-stage render CPU ms | Change | M2 measured GPU ms | GPU-stage measured GPU ms | Change | Verified instanced draws |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    tails = [
        "", "## Render P95, total-frame P95 and Java render-thread allocations", "",
        "| Scenario | M2 P95 render ms | M3 P95 render ms | M2 P95 frame ms | M3 P95 frame ms | M2 alloc MiB/frame | M3 alloc MiB/frame |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    for name in SCENARIOS:
        original = [oldbase / f"{name}-run-{i}.json" for i in (1,2,3)]
        candidates = sorted(x for x in source.glob(f"{name}-*.json")
                            if x.stem[len(name)+1:] >= args.after)
        if len(candidates) != 3:
            raise SystemExit(f"{name}: expected 3 independent candidate runs, found {len(candidates)}: {candidates}")
        old, new = [read(x) for x in original], [read(x) for x in candidates]
        ref = old[0]
        for report in old + new:
            if report.get("status") != "completed":
                raise SystemExit(f"{name}: invalid result {report.get('error')}")
            for field in ("effectSha256", "seed", "requestedParticles", "peakActiveParticles",
                          "warmupTicks", "measurementTicks"):
                if report.get(field) != ref.get(field):
                    raise SystemExit(f"{name}: incompatible {field}")
            for field in ("minecraft", "gpuRenderer", "width", "height"):
                if report["environment"].get(field) != ref["environment"].get(field):
                    raise SystemExit(f"{name}: incompatible environment {field}")
            if report.get("gpuSamples",0) <= 0:
                raise SystemExit(f"{name}: GPU timer samples missing")
            if name == "collision_1k" and not report.get("collisionEventObserved"):
                raise SystemExit("Collision trigger disappeared")
        for report in new:
            field = GPU_EXPECTED.get(name)
            if field and report.get(field,0) < 1:
                raise SystemExit(f"{name}: real GPU instancing was not observed")
            if name == "dynamic_1k" and report.get("gpuInstancedDrawCallsLastFrame",0) != 0:
                raise SystemExit("Alpha sprites unexpectedly bypassed the RenderType fallback")
        for i,path in enumerate(candidates,1):
            pending.append((path, out / f"{name}-run-{i}.json"))
        cpu0,cpu1 = median(old,"medianRenderSubmissionMs"),median(new,"medianRenderSubmissionMs")
        gpu0,gpu1 = median(old,"gpuTimeMs"),median(new,"gpuTimeMs")
        draws = median(new, GPU_EXPECTED[name]) if name in GPU_EXPECTED else 0
        lines.append(f"| {name} | {fmt(cpu0)} | {fmt(cpu1)} | {difference(cpu0,cpu1)} "
                     f"| {fmt(gpu0)} | {fmt(gpu1)} | {difference(gpu0,gpu1)} | {fmt(draws)} |")
        oldalloc = statistics.median(r["renderAllocatedBytes"] / r["measurementFrameSamples"] / 1048576
                                     for r in old)
        newalloc = statistics.median(r["renderAllocatedBytes"] / r["measurementFrameSamples"] / 1048576
                                     for r in new)
        tails.append(f"| {name} | {fmt(median(old,'p95RenderSubmissionMs'))} "
                     f"| {fmt(median(new,'p95RenderSubmissionMs'))} "
                     f"| {fmt(median(old,'p95FrameMs'))} | {fmt(median(new,'p95FrameMs'))} "
                     f"| {fmt(oldalloc)} | {fmt(newalloc)} |")
        if name in GPU_EXPECTED:
            pending.append((original[0], out / "reference" / f"{name}-m2.json"))
            copy_screenshot(ref, out / "screenshots" / f"{name}-m2.png")
            copy_screenshot(new[0], out / "screenshots" / f"{name}-m3.png")

    lines += tails
    lines += ["", "## Client regression and GL state tests", ""]
    for name in SMOKE:
        matches = sorted(x for x in source.glob(f"{name}-*.json")
                         if x.stem[len(name)+1:] >= args.after)
        if not matches:
            raise SystemExit(f"Missing graphical smoke case {name}")
        report = read(matches[-1])
        if report.get("status") != "completed":
            raise SystemExit(f"{name}: {report.get('error')}")
        if name in ("reload_active","model_reload_active") and not report.get("reloadCompleted"):
            raise SystemExit(f"{name}: resource reload was not confirmed")
        if name == "budget_spike" and report.get("peakActiveParticles") != 20_000:
            raise SystemExit("Shared effect spawn budget changed")
        if name == "sprite_uv_instanced" and report.get("gpuInstancedDrawCallsLastFrame",0) < 1:
            raise SystemExit("Animated additive UV did not use instancing")
        if name == "sprite_world_z" and report.get("gpuInstancedDrawCallsLastFrame",0) < 1:
            raise SystemExit("WORLD_Z facing did not use instancing")
        if name == "sprite_horizontal" and report.get("gpuInstancedDrawCallsLastFrame",0) < 1:
            raise SystemExit("Camera-horizontal facing did not use instancing")
        if name == "sprite_frustum" and (report.get("gpuFrustumCulledLastFrame",0) < 1
                                         or report.get("gpuInstancedParticlesLastFrame",0) < 1):
            raise SystemExit("Frustum culling was not observed on a real-world VFX")
        if name == "sprite_lod" and report.get("gpuSubpixelCulledLastFrame",0) < 1:
            raise SystemExit("Subpixel LOD not observed in real client")
        if name == "sprite_lit_fallback" and report.get("gpuInstancedDrawCallsLastFrame",0) != 0:
            raise SystemExit("World-lit sprite bypassed Minecraft lighting")
        pending.append((matches[-1],out / f"{name}.json"))
        lines.append(f"- `{name}`: completed.")
    out.mkdir(parents=True,exist_ok=True)
    for s,d in pending:
        d.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(s,d)
    lines += ["", "## Interpretation and limits", "",
              "- Render submission timings measure CPU wall time in `VfxRenderer.render`; "
              "GPU timings are nonblocking GL timer queries around the VFX pass. "
              "They exclude the rest of Minecraft's frame.",
              "- Shader/optimization mods are **not** installed or validated by this baseline. "
              "The custom GPU path intentionally declines Iris/Oculus/Sodium/Embeddium "
              "and preserves the vanilla RenderType fallback.",
              "- Alpha, world-lit particles, velocity-facing sprites and complex model types "
              "remain on the original renderer. Only additive compatible sprites and "
              "untinted unlit solid custom baked models are GPU-instanced.",
              "- Frustum culling uses a conservative projected sphere; LOD excludes only "
              "subpixel particles, and can be disabled by setting the threshold to zero.",
              "- The GL state audit compares the state immediately before and after each "
              "draw in an independent real-client smoke run. It does not prove arbitrary "
              "third-party renderer integration.",
              "- Renderer benchmark distributions may vary with GPU clocks, driver load, "
              "JIT warmup and frame pacing; no broad FPS or modpack compatibility claims.",
              "- Raw benchmark reports were captured before the source commit: preserve "
              "the final completion commit with the report.",
              ""]
    (out/"README.md").write_text("\n".join(lines),encoding="utf-8")
    if hasattr(sys.stdout,"reconfigure"):sys.stdout.reconfigure(encoding="utf-8")
    print("\n".join(lines))


if __name__=="__main__":
    main()
