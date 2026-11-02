#!/usr/bin/env python3
"""Verify the RenderType fallback with unchanged JSON inputs after custom GPU instancing."""
import argparse
import json
import shutil
import statistics
from pathlib import Path

NAMES = ("sprite_1k", "sprite_10k", "dynamic_1k", "collision_1k", "model_1k")


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--after", required=True)
    parser.add_argument("--output", default="docs/benchmarks/m3-2026-11-02")
    args = parser.parse_args()
    out = Path(args.output)
    source = Path("run/pyrovfx-benchmarks")
    lines = ["# Forced Minecraft RenderType fallback (A/B)",
             "", "One real-client run of the same frozen VFX definitions with "
             "`-PbenchLegacyRender=true`. Shader/optimization mod selection follows "
             "this same functional fallback, but no third-party shader mod is "
             "installed or verified here.", "",
             "| Scenario | M3 GPU-aware median CPU render (ms) | Forced legacy CPU render (ms) |",
             "|---|---:|---:|"]
    records = []
    for name in NAMES:
        new = [read(out / f"{name}-run-{i}.json") for i in (1,2,3)]
        files = sorted(p for p in source.glob(f"{name}-*.json")
                       if p.stem[len(name)+1:] >= args.after)
        if len(files) != 1:
            raise SystemExit(f"{name}: expected 1 forced fallback run, found {len(files)}")
        record = read(files[0])
        if record.get("status") != "completed":
            raise SystemExit(f"{name}: invalid: {record.get('error')}")
        if record.get("gpuInstancedDrawCallsLastFrame",0) != 0                 or record.get("gpuModelDrawCallsLastFrame",0) != 0:
            raise SystemExit(f"{name}: forced renderer fallback still submitted raw GPU draw")
        for field in ("effectSha256", "seed", "requestedParticles", "peakActiveParticles"):
            if record.get(field) != new[0].get(field):
                raise SystemExit(f"{name}: incompatible inputs {field}")
        for field in ("minecraft", "gpuRenderer", "width", "height"):
            if record["environment"].get(field) != new[0]["environment"].get(field):
                raise SystemExit(f"{name}: incompatible hardware {field}")
        if name == "collision_1k" and not record.get("collisionEventObserved"):
            raise SystemExit("Collision events missing on forced fallback")
        records.append((files[0],out / "forced-fallback" / f"{name}.json"))
        before = statistics.median(x["medianRenderSubmissionMs"] for x in new)
        lines.append(f"| {name} | {before:.3f} | {record['medianRenderSubmissionMs']:.3f} |")
    for s,d in records:
        d.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(s,d)
    (out/"FALLBACK_AB.md").write_text("\n".join(lines)+"\n",encoding="utf-8")
    print("\n".join(lines))


if __name__=="__main__":
    main()
