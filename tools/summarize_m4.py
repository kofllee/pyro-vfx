#!/usr/bin/env python3
"""Archive/validate independent Minecraft analytical GPU stress tests, never extrapolate."""
import argparse
import json
import shutil
import statistics
import sys
from pathlib import Path

COUNTS={"sprite_10k":10_000, "sprite_100k":100_000, "sprite_1m":1_000_000}

def read(p): return json.loads(p.read_text(encoding="utf-8"))
def med(reports,k):
    values=[r.get(k) for r in reports]
    return statistics.median(values) if all(isinstance(v,(int,float)) for v in values) else None
def fmt(v,d=3): return f"{v:.{d}f}" if v is not None else "unavailable"

def main():
    arg=argparse.ArgumentParser()
    arg.add_argument("--after",required=True)
    arg.add_argument("--output",default="docs/benchmarks/m4-2026-11-02")
    opt=arg.parse_args()
    base=Path("run/pyrovfx-benchmarks")
    out=Path(opt.output)
    oldBase=Path("docs/benchmarks/m3-2026-11-02")
    lines=["# Analytic GPU particles — real Minecraft stress tests","",
      "Three separate Minecraft JVM/client launches per particle count. "
      "No extrapolated/estimated samples. Every result must have observed GPU draws "
      "and exact peak particle count or the script fails.","",
      "| Particles | CPU simulation median (ms) | CPU render submission median (ms) | GPU VFX median (ms) | GPU VFX P95 (ms) | Full-frame P95 (ms) | Draw calls | Birth-state upload (MiB) |",
      "|---:|---:|---:|---:|---:|---:|---:|---:|"]
    pending=[]
    allsets={}
    for name,count in COUNTS.items():
        candidates=sorted(p for p in base.glob(name+"-*.json")
                          if p.stem[len(name)+1:]>=opt.after)
        # A/B verification may reuse the same fixture name with an explicitly
        # forced CPU backend. Those are not GPU stress samples.
        candidates=[p for p in candidates
                    if read(p).get("selectedParticleBackend") == "gpu_analytic"]
        if len(candidates)!=3:
            raise SystemExit(f"{name}: need 3 launches; found {len(candidates)}: {candidates}")
        reports=[read(p) for p in candidates]
        first=reports[0]
        for r in reports:
            if r.get("status")!="completed":
                raise SystemExit(f"{name}: invalid: {r.get('error')}")
            if r.get("selectedParticleBackend")!="gpu_analytic":
                raise SystemExit(f"{name}: no GPU analytic backend")
            if r.get("requestedParticles")!=count or r.get("peakActiveParticles")!=count:
                raise SystemExit(f"{name}: expected {count}, got {r.get('peakActiveParticles')}")
            if r.get("gpuAnalyticMaxInstancesSubmitted") != count or r.get("gpuAnalyticDrawCallsTotal",0)<1:
                raise SystemExit(f"{name}: render not confirmed")
            if r.get("gpuAnalyticDatasetUploadsTotal")!=1:
                raise SystemExit(f"{name}: birth dataset not uploaded exactly once: {r.get('gpuAnalyticDatasetUploadsTotal')}")
            if r.get("gpuAnalyticUploadBytesTotal") != count*24:
                raise SystemExit(f"{name}: wrong byte count {r.get('gpuAnalyticUploadBytesTotal')}")
            if r.get("gpuSamples",0)<5 or r.get("measurementTickSamples",0)<20:
                raise SystemExit(f"{name}: missing measured samples")
            for field in ("effectSha256","seed","requestedParticles","warmupTicks","measurementTicks"):
                if r.get(field)!=first.get(field): raise SystemExit(f"{name}: input mismatch: {field}")
            for field in ("minecraft","gpuRenderer","width","height","vsync","renderDistance"):
                if r["environment"].get(field)!=first["environment"].get(field):
                    raise SystemExit(f"{name}: hardware/env mismatch: {field}")
        allsets[name]=reports
        upload=med(reports,"gpuAnalyticUploadBytesTotal")/1048576
        lines.append(f"| {count:,} | {fmt(med(reports,'medianSimulationMs'))} "
                     f"| {fmt(med(reports,'medianRenderSubmissionMs'))} "
                     f"| {fmt(med(reports,'gpuTimeMs'))} "
                     f"| {fmt(med(reports,'gpuP95Ms'))} "
                     f"| {fmt(med(reports,'p95FrameMs'))} "
                     f"| {fmt(med(reports,'gpuAnalyticDrawCallsLastFrame'),0)} "
                     f"| {fmt(upload,2)} |")
        for i,path in enumerate(candidates,1):
            pending.append((path,out/f"{name}-run-{i}.json"))
    m3=[read(oldBase/f"sprite_10k-run-{i}.json") for i in (1,2,3)]
    m4=allsets["sprite_10k"]
    if m3[0].get("effectSha256")!=m4[0].get("effectSha256")           or m3[0].get("seed")!=m4[0].get("seed"):
        raise SystemExit("M3/M4 10k baseline input mismatch")
    lines += ["","## M3 versus M4, same frozen 10k JSON and seed", "",
       "| Metric | M3 median | M4 median |", "|---|---:|---:|"]
    for key,label in [("medianSimulationMs","CPU simulation, ms"),
                      ("medianRenderSubmissionMs","CPU render submission, ms"),
                      ("gpuTimeMs","GPU VFX, ms"),
                      ("p95FrameMs","Full frame P95, ms")]:
        lines.append(f"| {label} | {fmt(med(m3,key))} | {fmt(med(m4,key))} |")
    e=m4[0]["environment"]
    lines += ["","## Test environment", "",
       f"- Minecraft {e['minecraft']}, Java {e['java']}, {e['os']}.",
       f"- CPU {e['cpu']}; {e['threads']} logical processors; JVM max heap {e['maxHeapBytes']/1073741824:.1f} GiB.",
       f"- GPU {e['gpuRenderer']} ({e['gpuVendor']}); OpenGL {e['openGlVersion']}.",
       f"- Window {e['width']}×{e['height']}, render distance {e['renderDistance']}, FOV {e['fov']}, VSync {e['vsync']}.",
       "- Integrated singleplayer 'Test VFX', camera fixed at (0.5,141.5,-12), effect origin (0.5,140,0.5); seed 0x50B02026.",
       "- All measurements use 30 warmup ticks, 100 measured ticks. Heavy particle limit enabled **explicitly for 100k/1M only**.",
       "- Each GPU dataset contains a constant-colour/scale additive sprite, static motion, no collisions or events. "
         "The complete 24-byte initial-position/velocity buffer is GPU resident, never streamed once per frame.",
       "- 1M is a **real completed run** here, not a guarantee on other GPUs or shader/optimization mods.",
       "- VSync and Minecraft's wider world workload make total-frame P95 a broader metric than GPU VFX time. "
         "Startup shader compilation, one-time VBO upload, initial particle sampling and potential driver stalls "
         "are not captured by steady-state median simulation/render measurements.",
       "- CPU fallback for unsupported effects and modded render pipelines remains in use. "
         "No 1M colliding/alpha sprites, GPU compute, or cross-driver shader-mod compatibility is claimed.",
       ""]
    out.mkdir(parents=True,exist_ok=True)
    for s,d in pending: shutil.copyfile(s,d)
    (out/"README.md").write_text("\n".join(lines),encoding="utf-8")
    if hasattr(sys.stdout,"reconfigure"):sys.stdout.reconfigure(encoding="utf-8")
    print("\n".join(lines))

if __name__=="__main__": main()
