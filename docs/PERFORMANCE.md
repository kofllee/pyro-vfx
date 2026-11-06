# Performance and retained benchmark evidence

**Publication note (6 November 2026):** The figures below are retained measurements of
earlier render configurations and their original workloads. The new unified
alpha/additive stage, translucent GPU model ordering and Fabulous depth replay
were promoted afterward. **No matched speedup or regression measurement of that
final combination has yet been established.** In particular, previously
measured tiny-cohort optimization paths may be bypassed when the unified
sorting route is active. Do not present the old FPS or draw-call reductions as
current production performance. The remaining verification is tracked in
[Rendering work](RENDERING_AND_OPTIMIZATION_BACKLOG.md).

## 6 November 2026: real museum frame cost and Fabulous depth

The original material-cohort GPU batching reduced final ShaderInstance
submissions but did not remove the CPU/driver overhead of generating hundreds
of *tiny* analytic cohorts in the 35-stand museum. The renderer now routes
eligible additive cohorts of up to 12 particles through the native **shared
sprite instance batch**, avoiding a separate Transform Feedback launch per tiny
cohort. Each particle's facing, rotation and world-light policy is encoded
per-instance, so sprites sharing one texture can be submitted together without
erasing individual geometry semantics. Large/cohort-expression GPU work is
unchanged.

A controlled 1280x720 real-client gallery A/B with all exhibits enabled in
Fabulous (uncapped FPS, fixed camera) recorded **375 TF launches, 66 Minecraft
FPS median** before tiny-cohort routing, versus **10 TF launches, 93 FPS
median** after. CPU VFX render median was 1.395 ms before and 0.485 ms
after. In the latter same-client benchmark, the ALL OFF control was 101 FPS.
This is a **short single-run comparison**, not a universal 93 FPS guarantee;
the earlier run's baseline controls and frame conditions still differed.
Retained JSON reports and depth evidence live under
[Fabulous + tiny cohort comparison](benchmarks/fabulous-depth-tiny-cohorts-2026-11-06/README.md).

Fabulous alpha particles had been drawn during AFTER_LEVEL, after Minecraft
had already composited its independent particles target. Moving the alpha
stage to `AFTER_PARTICLES`, while Minecraft has the target and copied opaque
depth bound, fixes opaque-wall visibility in the tested alpha and additive
stone-wall fixtures. Explicit `GL_LEQUAL` is required on native raw-GL
submissions. Intersecting transparent glass/water and arbitrary shaderpack
composition still require separate validation and are not claimed pixel-perfect.

## 6 November 2026: native GPU material batching

The native bridge now accumulates consecutive additive GPU Analytic cohorts
sharing the same texture in a bounded GPU Transform Feedback output buffer.
Each cohort retains its own geometry-generation draw (and expression uniforms),
but compatible output is rendered by one Minecraft ShaderInstance draw per
material group. Incompatible materials, full batches, models and CPU/fallback
draws retain explicit flush boundaries and GL state restoration.

In a single 1280x720 museum scenario with matching effect SHA-256 and seed,
the earlier native sample vs the optimized native sample measured:

| Metric | Earlier native | Batched native |
| --- | ---: | ---: |
| GPU Analytic final Minecraft draws (last sampled frame) | 300 | **5** |
| TF generation calls (last sampled frame) | about 300 | **299** |
| Render-submission CPU median | 4.054 ms | **2.075 ms** |
| Render-submission CPU P95 | 9.813 ms | **3.308 ms** |

These are **one-run measurements**, not a controlled guarantee of FPS.
The optimized run's renderer state audit passed. In a separately staged Iris
1.8.12 + Complementary r5.9.3 check, the same scenario reported **2.640 ms**
median and **5.871 ms** P95, versus the prior **4.054 ms** median and
**9.813 ms** P95 sample. Vanilla and Iris profile checks both completed
three museum scenarios and restored the user's original configuration.
The 104 required GameTests and release-JAR verification passed.
The separate 100k GPU Analytic UV stress case passed with two TF chunks,
two native submissions and bounded 65,536-particle output capacity.

Per-cohort geometry-generation draws remain; this work reduces the
**native final material submissions** and repeated expensive state queries.
It does not claim a reduction in all OpenGL draw calls or a universal GPU
frame-time gain. See [native material batching evidence](benchmarks/native-material-batching-2026-11-06/README.md).

This is the **current compact summary**, replacing the per-milestone performance narratives. Historical measurements below come from real Minecraft client runs on 1–3 November 2026; **no measurements were repeated during consolidation**. New 5 November client verification runs are reported separately.

## 6 November 2026 native renderer cutover

The production path was converted to Transform Feedback geometry generation
with final Minecraft ShaderInstance submission. Original custom fragment
renderers for Analytic sprites and direct instanced sprites/models were removed.
The 104/104 required GameTests, development compilation, release JAR checks
and short native client validation completed. Tests cover model variants,
GPU expressions, light probes, alpha sorting and reload.

A 100k additive-analytic native sample used one 2.4 MiB birth-state upload,
approximately 8.9 MiB bounded output/index storage and two draw calls per
frame. Earlier matched-run single GPU timer observations were approximately
0.17 ms native generation+draw vs 0.065 ms for the old dedicated GPU shader.
This is a **cost regression in that sample**, not proof of a universal speedup,
and separate full-frame/distribution measurements are still required. The
legacy figures below describe the previous pipeline, not the new GPU renderer.
See [Native Render Bridge](NATIVE_RENDER_BRIDGE.md).

## 5 November 2026 routing and correctness checks

The initial 5 November changes restore RGB/alpha blend-equation state after raw GL passes, add GPU world-plane sprite facing, remove artificial GPU Analytic rejection of LOOPING/MANUAL birth schedules and prevent delayed steady-emission bursts under capacity pressure. Dedicated GameTests passed 91/91 and `verifyReleaseJar` succeeded. Later same-day client work additionally repaired unsuccessful GL shader/VBO initialization and reduced single-birth direct-buffer minimum from 4,096 to 256 bytes.

**New, separate real-client evidence:** six completed NVIDIA/vanilla scenarios at 1280×720, with GL state audit, are preserved under [5 November runtime audit](benchmarks/runtime-audit-2026-11-05/README.md). The matched `sprite_horizontal` GPU vs forced RenderType screenshots (same effect hash and seed) differed at 279 of 921,600 pixels (0.0303%); this is strong evidence for that single static scene, **not** universal visual parity. Model Analytic, rotating additive instancing and sorted alpha smoke instancing also completed and rendered visible particles. Shaderpack runs, fault-injected GL recovery and long stress tests were **not** repeated here. Frame P95/P99 are single-run observations, not proven before/after speedups; GPU timer data is not established by these reports.

A second, explicitly separated set of [5 November runtime expansion results](benchmarks/runtime-expansion-2026-11-05/README.md) covers the newly implemented GPU birth-time expressions, lit sprite instancing, snapshot-free Dense model rendering, and actual Manual/Looping GPU cohorts. Its one-run GPU-vs-RenderType lit scene differs by 0.0278% of pixels; its earlier/later alpha and rotation tests have matching effect hashes and seeds but **no repeatable clean-commit before/after FPS proof**. GPU query and shaderpack compositor validation remain open.

A further [runtime extension comparison](benchmarks/runtime-extensions-2026-11-05/README.md) records synchronized CPU Dense collision callbacks vs Legacy, GPU-resident age-driven motion/rotation vs Dense, and a coalesced same-tick manual-burst fixture. Repeated birth uploads fell from four event-created cohorts to two age-compatible cohorts in that specific scene; **no controlled aggregate FPS/VRAM win is claimed**. The only numeric runtime comparisons are short independent client runs, with the CPU Dense ordered-event scene slower than Legacy.

The next real-client matrix and unresolved research are tracked in [Rendering and Optimization Backlog](RENDERING_AND_OPTIMIZATION_BACKLOG.md).

## Hardware and interpretation

- Windows 11; NVIDIA **RTX 4090 Laptop GPU**, OpenGL 4.6 (initial baseline reported driver 610.74); Intel Family 6 Model 183 CPU with 32 hardware threads reported; Minecraft **1.21.1**, NeoForge **21.1.229**, Java **21**.
- Original baseline and early M0–M5 progression: **854×480**, historical test world and seed. Later sprite/model/alpha comparisons and museum: commonly **1280×720**, often uncapped FPS and different worlds. **Do not compare across these setups as a speedup.**
- `medianSimulationMs` is Pyro CPU simulation, render submission is Pyro CPU render work, `medianGpu...` is asynchronous **Pyro-only GPU** time, `p95FrameMs` / `p99FrameMs` measure the **whole Minecraft frame interval**. FPS is affected by scene, vsync and limits. Peak Java heap is not particle memory or dedicated GPU VRAM. All values must be compared only between matched effect hashes, seeds, hardware and settings.

## Significant measured changes

| Matched scenario / evidence | Before | After | Interpretation |
|---|---:|---:|---|
| 10k sprites, compiled expressions (M0 → M1, 854×480) | CPU simulation **10.227 ms** | **0.512 ms** | Compile and cache expressions once; 64.17 → 4.50 MiB allocated/tick |
| 10k sprites, Dense SoA (M1 → M2, same setup) | CPU simulation **0.512 ms** | **0.258 ms** | Low-allocation simulation (roughly 0.002 MiB allocated/tick in this fixture) |
| 10k sprites, instanced rendering (M2 → M3, same setup) | Render submission **2.519 ms** | **0.448 ms** | GPU instancing reduces CPU vertex submission |
| 10k simple sprites, GPU analytic (M3 → M4, same setup) | CPU simulation **0.260 ms** | **0.026 ms** | Resident birth state; frame P95 did **not** materially improve (10.294 → 10.407 ms) |
| 100k GPU-compatible expression sprites, 1280×720 | CPU simulation **6.557 ms**, frame P95/P99 **14.096/16.808 ms** | **0.029 ms**, **1.141/1.990 ms** | Birth random + age A/B; Pyro GPU **2.643 → 0.143 ms**; other expression shapes also verified |
| 100k unlit baked models, 1280×720 | CPU simulation **4.442 ms**, frame P95 **13.941 ms** | **0.044 ms**, **5.424 ms** | GPU-resident model path; GPU time **4.963 → 3.363 ms** in reported medians |
| 100k fullbright alpha smoke, 1280×720 | Render submission **34.054 ms**, frame P95 **49.16 ms** | **9.593 ms**, **22.256 ms** | Stable radix-sorted GPU instancing; optimized candidate was **one run**, not a repeated sample |
| 10k non-event collision sprites, 1280×720 | CPU simulation **1.826 ms**, P95 **8.324 ms** | **1.101 ms**, **2.011 ms** | Dense collision routing; eventful collisions retain compatible callback path |

The alpha-smoke optimized run improved CPU work but **GPU time was 2.80 ms vs 2.34 ms baseline**; GPU acceleration of that particular heavy blend workload was **not** demonstrated. The 100k screenshots were not synchronized enough for strict pixel-parity proof.

**1,000,000** eligible additive GPU Analytic sprites were measured in a narrowly configured 854×480 stress scenario: three-run median ~**0.009 ms** CPU simulation, **1.004 ms** Pyro GPU time and **16 draws**, with full-frame P95 ~**9.839 ms**. A retained single report recorded median FPS **119.5**, P95 **10.122 ms**, P99 **21.688 ms**, and max Java heap **892.9 MiB**. This is **not** a 1M-particle gameplay guarantee. A retained 100k-model CPU Dense sample recorded FPS **201.0**, P95 **10.895 ms**, P99 **11.624 ms**, peak heap **990.8 MiB** under different settings. Heap values include the whole Minecraft JVM and cannot isolate VFX allocations.

Other confirmed safeguards: a 30k global-spawn scenario admitted 30k / rejected 30k on both GPU and forced CPU routes; the 66-effect museum and shader profile short runs completed on the tested machine; matched analytic-cohort visibility checks reduced draw work without reducing living particle count. A single visibility A/B pair recorded P95 **2.048 → 1.756 ms** and P99 **3.300 → 2.926 ms**, but this is not multi-driver evidence.

## What was kept

A deliberately small, inspectable subset of source evidence remains under `docs/benchmarks/`. Old tracked records are still retrievable from normal Git history (no history rewrite or external archive). Useful entrypoints:

| Question | Retained raw evidence |
|---|---|
| Pre-optimization baseline | [static 10k](benchmarks/baseline-2026-11-01/sprite_10k.json), [1k screenshot](benchmarks/baseline-2026-11-01/sprite_1k.png) |
| Compiled expression / Dense / instancing progression | [M1](benchmarks/m1-2026-11-02/sprite_10k-run-1.json), [M2](benchmarks/m2-2026-11-02/sprite_10k-run-1.json), [M3](benchmarks/m3-2026-11-02/sprite_10k-run-1.json) |
| Extreme analytic stress | [1M sprites](benchmarks/m4-2026-11-02/sprite_1m-run-1.json), [image](benchmarks/m4-2026-11-02/screenshots/sprite_1m-analytic.png) |
| Real CPU/GPU expressions | [CPU](benchmarks/gpu-expressions-2026-11-02/sprite_expressions_100k-20261102-212343.json), [GPU](benchmarks/gpu-expressions-2026-11-02/sprite_expressions_100k-20261102-213410.json) |
| 100k baked models | [GPU](benchmarks/resident-model-2026-11-02/model_100k-20261102-165112.json), [CPU](benchmarks/resident-model-2026-11-02/model_100k-20261102-165315.json) |
| Alpha smoke A/B | [baseline](benchmarks/alpha-smoke-instancing-2026-11-02/sprite_alpha_smoke_100k-20261102-205643.json), [optimized](benchmarks/alpha-smoke-instancing-2026-11-02/sprite_alpha_smoke_100k-20261102-210506.json) |
| Collision and budgets | [collision Legacy](benchmarks/dense-collision-2026-11-02/collision_no_events_10k-20261102-163818.json), [Dense](benchmarks/dense-collision-2026-11-02/collision_no_events_10k-20261102-164200.json), [global budget](benchmarks/m5-2026-11-02/global_budget_spike-20261102-012321.json) |
| Models, shader compatibility, museum | [museum aggregate CSV](benchmarks/museum-final-2026-11-03/museum-results.csv), [graphics profile results](benchmarks/museum-final-2026-11-03/compatibility/profile-results.json), [smoke screenshot](benchmarks/museum-final-2026-11-03/vanilla/smoke_alpha.png) |

The surviving M6 `universal-short` records preserve all eleven reports expected by `tools/verify_universal_short.py`; M5 evidence preserves the coverage required by `tools/verify_m5.py`. Scripts for new benchmarks remain available; the archive is **representative**, not an exhaustive recreation of every historical three-run series.

## Reproduction, only if new evidence is needed

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer --no-daemon
python tools/test_compare_bench.py

# Optional real-client measurements; not a default build/test.
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario 'sprite_10k' -Width 1280 -Height 720 -Uncapped -RenderStateAudit
python tools/compare_bench.py before.json after.json
python tools/verify_universal_short.py
```

The launcher needs a graphical Windows session with a second monitor; exact command options and GPU conditions are in [Test Harness](TEST_HARNESS.md). For valid A/B, compare `status=completed`, `effectSha256`, `seed`, actual window resolution, renderer/backend selections and tick/frame sample counts. Do not infer visual parity from timings alone.

## Limits to carry forward

No repeatable 1080p/4K across multiple GPUs; no proven heavy cross-mod alpha sorting; no real Internet high-player-count network throughput; no long-term leak/GC proof for high-complexity effects. GPU timers measure only Pyro, optional NVIDIA VRAM telemetry is device-wide and off by default. Complex collision, world-lit smoke, translucent and entity models can retain expensive CPU/fallback paths. GPU Compute remains deferred until measured stateful workloads justify it.
