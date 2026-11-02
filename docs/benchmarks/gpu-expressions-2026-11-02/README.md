# GPU expressions and curves — measured Minecraft A/B (2026-11-02/03)

**Environment:** actual Minecraft 1.21.1 NeoForge 21.1.229 clients, Windows 11, Java 21.0.5, NVIDIA RTX 4090 Laptop GPU / OpenGL 4.6, 1280×720, uncapped VSync off, fixed camera/seed. Same effect SHA in every GPU-vs-forced-CPU pair. Measurements are short, synthetic, deliberately high-count workloads; not general FPS expectations or complete shaderpack/driver compatibility proof.

The existing expression parser now attaches an optional GLSL representation to supported CPU expression nodes; there is no second expression language or independent parser. Supported appearances use arithmetic, a selected subset of math functions, immutable `particle.random`, shared age, closed-form `particle.pos`, `particle.prev_pos`, `particle.vel`, `particle.speed` and `spawn.pos`. Immutable curves (linear, step, bezier, catmull, at most eight points) compile from the same `VfxCurveDefinition` representation. Each shader computes both previous/current appearance states, then interpolates. CPU semantics still execute through the original compiled evaluator.

Unsupported expressions (stateful random function calls, mutable params, arbitrary world queries, collisions, unsupported math/curves and per-particle rotation) retain CPU Dense/Legacy. RGB/scale shader calculations use 32-bit GLSL floats rather than CPU doubles. Exact bitwise parity is not guaranteed.

## A/B results

Each value for the first two groups below is the **median of two independent completed runs per backend**. The motion-dependent group has **one completed GPU/CPU A/B pair** plus one additional GPU-only 100k sanity run.

| Scenario (100k) | Backend | CPU simulation median ms | CPU render submission median ms | GPU VFX elapsed ms | P95 frame interval ms | P99 frame interval ms |
|---|---|---:|---:|---:|---:|---:|
| Birth random + age | CPU Dense | 6.557 | 4.230 | 2.643 | 14.096 | 16.808 |
| Birth random + age | GPU Analytic | 0.029 | 0.034 | 0.143 | 1.141 | 1.990 |
| Birth random + Catmull/Bezier curves | CPU Dense | 15.152 | 4.618 | 3.033 | 24.060 | 28.338 |
| Birth random + Catmull/Bezier curves | GPU Analytic | 0.044 | 0.036 | 0.298 | 1.727 | 2.635 |
| Position/previous position/velocity/spawn | CPU Dense | 17.626 | 4.321 | 2.679 | 26.624 | 29.517 |
| Position/previous position/velocity/spawn | GPU Analytic | 0.046 | 0.037 | 0.101 | 1.231 | 2.236 |

At 1k the birth-random GPU route was **not faster overall** in this short test (CPU Dense p95 1.21 ms; GPU p95 1.32 ms). Startup/uniform/draw overhead can outweigh wins for tiny effects; the backend is still selected for its semantics/low residency. The driver-specific break-even point is not established; do not claim GPU always wins.

- **100k curve workload:** moving evaluation to GPU reduced CPU simulation time ≈344×, GPU VFX time ≈10× and P95 frame interval ≈14× in these isolated tests. This is a fixture-specific observation, not an application-wide speedup.
- **100k motion appearance:** moving evaluation to GPU reduced CPU simulation time ≈387×, GPU VFX time ≈26× and P95 frame interval ≈22× in this one A/B pair.
- CPU Dense state allocation for 100k was around 27.46 MB. GPU Analytic birth-state data measured 2.4 MB (motion expressions with no random attribute) or 2.8 MB (random attribute). This excludes driver overhead and whole-process VRAM.
- Both backends admitted 100k particles in each heavy test, with no rejected spawns. `spawnCpuMs` was around 0.1–0.2 ms: harness cost of submitting a spawn request, **not** necessarily complete subsequent particle population costs. `maxObservedHeapBytes` varies with Java GC and should not be treated as tracked particle memory.
- Screenshot differences (single paired captures at identical camera, deterministic seed): 100k random-age **0.129%** differing full-frame pixels; 100k curves **0.063%**; 100k position/velocity **0.066%**. The bright additive particle sphere visually matches, but near-saturation and asynchronously captured frames can mask differences. Per-pixel and multi-frame appearance validation remains open.

`gpu_expression_reload_active` was completed with an actual Minecraft client resource reload: active effect preserved, GPU expression renderer resubmitted all 1,000 instances and reuploaded its data twice. GL state audit enabled on real-client runs. Compile or GPU capability failures revert to the original CPU evaluator; partially submitted GPU frames are not redrawn a second time.

## Reproduction

Run from repo root with real second display (the launcher intentionally enforces this on Windows).

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario 'sprite_expressions_1k,sprite_expressions_10k,sprite_expressions_100k,sprite_gpu_curves_10k,sprite_gpu_curves_100k,sprite_gpu_motion_expr_10k,sprite_gpu_motion_expr_100k,gpu_expression_reload_active' -Width 1280 -Height 720 -Uncapped -Heavy -RenderStateAudit
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario 'sprite_expressions_1k,sprite_expressions_10k,sprite_expressions_100k,sprite_gpu_curves_10k,sprite_gpu_curves_100k,sprite_gpu_motion_expr_10k,sprite_gpu_motion_expr_100k' -Width 1280 -Height 720 -Uncapped -Heavy -ForceCpuAnalytic -RenderStateAudit
```

Raw JSON records in this directory include scenario/effect hashes, spawn costs, accepted/rejected counts, frame sample sizes, CPU/GPU and P95/P99 timings, particle state/heap counters, driver and exact screenshot paths. These reports capture code before the closing commit, so the embedded `environment.gitCommit` points at the baseline HEAD rather than the final commit. No shaderpacks, AMD/Intel drivers or multiplayer clients were available for this verification.
