#!/usr/bin/env python3
"""Summarize current reproducible Minecraft museum benchmark reports.
Source JSON is never edited; only status=completed reports count as verified.
"""
import csv
import json
import statistics
import sys
from collections import defaultdict
from pathlib import Path
root=Path(__file__).resolve().parents[1]
manifest=root/"src/main/resources/pyro_vfx_museum_exhibits.tsv"
reports=root/"run/pyrovfx-benchmarks"
rows=[]
for line in manifest.read_text(encoding="utf-8").splitlines():
    if line and not line.startswith("#"):
        name,category,title,source,count,scale,desc=line.split("|")
        found=[]
        for f in reports.glob("museum_"+name+"-*.json"):
            try:
                obj=json.loads(f.read_text(encoding="utf-8"))
                if obj.get("scenario")=="museum_"+name:
                    found.append((f,obj))
            except Exception:
                continue
        chosen=max(found,key=lambda x:x[0].stat().st_mtime)[1] if found else {}
        rows.append(dict(exhibit=name,category=category,title=title,
             status=chosen.get("status","unverified"),backend=chosen.get("selectedParticleBackend",""),
             renderer=chosen.get("selectedRenderer",""),
             peak_particles=chosen.get("peakActiveParticles",""),
             median_fps=round(chosen["medianFps"],2) if "medianFps" in chosen else "",
             p95_ms=round(chosen["p95FrameMs"],3) if "p95FrameMs" in chosen else "",
             p99_ms=round(chosen["p99FrameMs"],3) if "p99FrameMs" in chosen else "",
             cpu_sim_ms=round(chosen["medianSimulationMs"],4) if "medianSimulationMs" in chosen else "",
             cpu_render_ms=round(chosen["medianRenderSubmissionMs"],4) if "medianRenderSubmissionMs" in chosen else "",
             gpu_vfx_ms=round(chosen["gpuTimeMs"],4) if "gpuTimeMs" in chosen else "",
             state_bytes=chosen.get("allocatedParticleStateBytes",""),
             spawn_request_ms=chosen.get("spawnCpuMs",""),
             screenshot=chosen.get("screenshot",""),
             gpu=chosen.get("environment",{}).get("gpuRenderer",""),
             effect_sha=chosen.get("effectSha256","")))
if len(sys.argv)>1:
    output=Path(sys.argv[1])
    output.parent.mkdir(parents=True,exist_ok=True)
    with output.open("w",encoding="utf-8",newline="") as f:
        writer=csv.DictWriter(f,fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    print("Wrote",output)
else:
    for row in rows:
        print(row["exhibit"],row["status"],row["backend"],row["renderer"],"p95:",row["p95_ms"])
completed=sum(r["status"]=="completed" for r in rows)
print(f"Completed {completed}/{len(rows)}, invalid {sum(r['status'] not in ('completed','unverified') for r in rows)}")
