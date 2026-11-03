# Visibility and LOD — quality-first runtime decision (2026-11-03)

## Policy

Distance alone **never** terminates a Pyro effect, drops existing particles, slows
particle simulation, suspends source transforms, or skips triggers/collisions.
There is no fixed render-distance cutoff, no quality-tier switch, and no
distance-based emission thinning. Flares, rocket trails and large smoke remain
eligible for drawing wherever their geometry actually intersects the camera.

The runtime does only geometry-aware, render-time rejection:

- GPU instanced sprites already cull individual out-of-frustum sprites; their
  subpixel threshold defaults to **0.20 pixel** and can be disabled with
  `-Dpyrovfx.render.minDiameterPx=0`. This remains a single shared threshold,
  not a set of quality tiers.
- GPU Analytic sprites with shared particle appearance track birth position
  and velocity extrema; a conservative, motion-aware bounding sphere allows
  an entire offscreen cohort to skip GPU upload and draw. Local-space source
  transforms move its center with the emitter. Bounds include sprite size
  and a conservative margin, and are re-evaluated every rendered frame.
- Analytic particles with per-birth randomized appearance, arbitrary baked
  model geometry and other cases without a safe cohort bound keep the
  previous draw behavior. Existing Dense, CPU fallback and GPU model paths
  remain intact.

These tests do not change effect JSON, networking payloads or public handles.
Particles still tick and expire normally while offscreen; returning to view
shows their **current** animated state instead of restarting an effect.
Use `-Dpyrovfx.render.debugNoCulling=true` as a development-only A/B control.
No model/detail LOD tiers were introduced: camera-dependent thinning of smoke
or rocket trails could produce visible gaps and would not be justified by the
measured bottleneck.

## Reproducible in-game validation

Use the existing secondary-monitor launcher and integrated Minecraft client.
The `distributed_analytic_64` benchmark emits 64 deterministically seeded cohorts in an
8-by-8 world grid, requesting 500 particles per instance (32,000 requested,
30,000 admitted under the current global limit). It runs with the normal
effect definitions, no hidden particle-state replication or benchmark-only
render replacement.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario 'distributed_analytic_64' -World 'Pyro VFX Museum' -Width 1280 -Height 720 -Uncapped -DisableCulling -GpuTelemetry -RenderStateAudit
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario 'distributed_analytic_64' -World 'Pyro VFX Museum' -Width 1280 -Height 720 -Uncapped -GpuTelemetry -RenderStateAudit
```

### One matched A/B pair (real Minecraft 1.21.1, NeoForge)

| Metric | Culling disabled | Culling enabled |
|---|---:|---:|
| Peak living particles | 30,000 | 30,000 |
| Median CPU render submission | 0.2821 ms | 0.1434 ms |
| Full-frame P95 | 2.0482 ms | 1.7557 ms |
| Full-frame P99 | 3.3001 ms | 2.9264 ms |
| GPU Analytic draw calls (accumulated) | 237,600 | 108,048 |
| GPU Analytic birth-state uploads | 720,000 bytes | 288,000 bytes |

Source reports: `run/pyrovfx-benchmarks/distributed_analytic_64-20261103-191026.json`
and `.../distributed_analytic_64-20261103-190916.json`, respectively.
The reports use the same seed, effect SHA-256 and 1280 × 720 viewport
in the same museum world. Their saved screenshots differ at 191/921,600
pixels (0.0207%; mean absolute per-channel error 0.000126 on the 0–255 scale).
This is strong **snapshot** evidence, not a claim of guaranteed pixel identity
at every instant or proof of shaderpack parity.

Only one matched A/B pair is reported here. Full-frame timing includes
Minecraft and driver work; the dedicated GPU telemetry is asynchronous and
these reports do not establish a stable GPU-time speedup. Repeated A/B runs
and different GPUs are required for general performance claims.

### Regression and acceptance scope

- `runGameTestServer`: 88/88 required GameTests passed, including local-space
  source motion, near-frustum bounds and particle preservation.
- Integrated client scenarios: `sprite_frustum`, `sprite_lod`,
  `museum_smoke_camera_turn_90`, `museum_source_entity_local`,
  `museum_model_entity_close_pan`; completed with real rendering.
- CPU fallback `sprite_lit_fallback` (1,000 dense particles) and
  `museum_smoke_alpha` (240 dense particles): a proposed per-particle fallback
  culling check saved no particles in these visible scenes and increased median
  render submission by approximately 6–7%. This **was rolled back** rather
  than imposing an overhead on visible smoke. Existing fallback rendering is
  unchanged.

Still unverified by this change: sustained multi-hour or many-player sessions,
high-end shader combinations, other GPUs/drivers, fast-moving cameras at 4K
and per-frame image identity. No renderer compatibility gate was relaxed.
Resource reload, network delivery and effect lifecycle remain on their
existing independent paths.