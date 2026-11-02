# Pyro VFX — Minecraft Test Harness (M−1)

This is an opt-in, **development-only** runner. GameTests exercise common definitions on a real NeoForge Minecraft server; client benchmarks run the actual Minecraft renderer with an OpenGL context. A successful headless GameTest does **not** measure the renderer, GPU, or FPS.

## Tests

```powershell
.\gradlew.bat runGameTestServer --no-daemon
python tools/test_compare_bench.py
.\gradlew.bat clean build
```

The twenty-six NeoForge tests cover expression language, curves, compiled dependency classification and constant folding; parser and parameter validation; once/looping/manual lifetimes; all eight spawn shapes; registry snapshots and invalid-resource reload fallback; inherited and live particle parameter updates; shared event/spawn budgets; world identity transitions; exact per-tick motion/rotation/UV/lifetime goldens; event fan-out; and event trigger order. They do **not** yet establish full runtime equivalence for every effect/event/motion/reload behavior.

## GPU client

With a working graphical Windows session, the existing `run/saves/Test VFX` world:

```powershell
.\gradlew.bat runClientBench -PbenchScenario=sprite_1k
.\gradlew.bat runClientBench '-PbenchScenario=sprite_1k,dynamic_1k,collision_1k,model_1k,sprite_10k'
```

Or run the regular Gradle `runClient` configuration, open an integrated-server world and enter:

```
/pyrovfx bench sprite_1k
/pyrovfx bench dynamic_1k
/pyrovfx bench collision_1k
/pyrovfx bench collision_no_events
/pyrovfx bench self_emit_collision
/pyrovfx bench model_rotation
/pyrovfx bench model_inheritance
/pyrovfx bench event_order
/pyrovfx bench budget_spike
/pyrovfx bench reload_active
/pyrovfx bench model_1k
/pyrovfx bench sprite_10k
```

The `-PbenchWorld=<world directory name>` property selects the Quick Play world. `runClientBench` also closes Minecraft automatically after the full scenario list and lets its integrated server save the world. Tests are deterministic **with respect to the seeded VFX effect**, not necessarily world chunk updates, driver scheduling, Minecraft lighting, shaders, render order or operating-system frame timing.

The `sprite_100k` and `sprite_1m` scenarios now test the GPU analytic renderer when the strict capability selector accepts the effect. The old CPU backend may still exhaust the client at these counts. They must be explicitly enabled with `-PbenchHeavy=true`. This is an **opt-in**, not a guarantee that the test can finish. Do not use high-count scenarios until profiling shows the workload fits the machine and acceptable frame-time limits. Heavy tests use the same JSON effects and are not approximated with fake particle counts.

The harness requires a local/integrated server; it will not run against a multiplayer server. It switches the player temporarily to spectator, moves the camera near the fixed effect at `0.5 140 -12` (camera at `0.5 141.5 -12`, 14-degree pitch), targets daytime, runs the warmup and sampling windows, captures a PNG and restores the original player position/mode/daytime. Avoid running inside a valuable survival save; use a dedicated benchmark world.

## Results

Actual client outputs are generated under `run/pyrovfx-benchmarks/`:

- `<scenario>-<UTC timestamp>.json`: scenarios, Git commit, Minecraft/Java/OS, renderer/GPU/OpenGL, resolution, seed and measurements.
- `<scenario>-<timestamp>.png`: screenshot for human visual review or external diffing.
- `results.csv`: machine-readable row per execution.

Frame metrics are wall-clock intervals measured between successive `AFTER_PARTICLES` render-stage callbacks. They are not graphics-card timings or a claim about whole-game FPS when background tasks run. CPU simulation and render submission are delimited wall-clock measurements of the *Pyro VFX* tick/render callbacks. Thread allocations are HotSpot allocation counters for the calling thread only; heap is process-wide. GC counts and elapsed GC time are JVM-wide. GPU time, when present, comes from **nonblocking OpenGL `GL_TIME_ELAPSED` queries**, measuring GPU work inside the Pyro VFX render call; it may be unavailable on incompatible contexts or shader drivers. A missing metric is `null`, not 0. TPS, total Minecraft draw-call counters, VRAM and GPU hardware counters are not yet instrumented. GPU VFX instanced draw calls, submitted particles and culling counts are now separately recorded in renderer-enabled reports; the legacy total-draw-call field remains null.

Analytic GPU rendering is documented in `docs/GPU_ANALYTIC_ARCHITECTURE.md`. `/pyrovfx backend` prints live per-emitter selection and fallback reasons. Stress: `runClientBench '-PbenchScenario=sprite_10k,sprite_100k,sprite_1m' '-PbenchHeavy=true' '-PbenchRequireGpuAnalytic=true' '-PbenchRenderStateAudit=true'`. For an exact CPU fallback check, add `-PbenchLegacyAnalytic=true` without the analytic requirement. Compare three real client launches using `python tools/summarize_m4.py --after <YYYYMMDD-HHMMSS>` and review archived raw JSON in `docs/benchmarks/m4-2026-11-02/`.

The GPU-instanced sprite/custom-model renderer is documented in `docs/GPU_RENDER_ARCHITECTURE.md`. For live renderer state auditing and active instancing verification use `-PbenchRequireGpuInstancing=true -PbenchRenderStateAudit=true`. Dedicated scenarios: `sprite_uv_instanced`, `sprite_world_z`, `sprite_horizontal`, `sprite_frustum`, `sprite_lod`, `sprite_lit_fallback` and `model_reload_active`. To force the original RenderType path use `-PbenchLegacyRender=true` or the JVM property `-Dpyrovfx.render.forceLegacy=true`. Compare three runs against the frozen CPU baseline with `python tools/summarize_gpu_render.py --after <YYYYMMDD-HHMMSS>`; raw reports and side-by-side screenshot evidence live in `docs/benchmarks/m3-2026-11-02/`.

The data-oriented CPU implementation is documented in `docs/CPU_RUNTIME_REDESIGN.md` and benchmarked in `docs/benchmarks/m2-2026-11-02/`. Use `-PbenchLegacyCpu=true` with `runClientBench` to force the original CPU particle backend, and compare both against unchanged JSON. `tools/summarize_m2.py` verifies three independent client runs against the frozen M1 distribution; `tools/verify_cpu_backend_ab.py` checks the forced-legacy smoke run. Run `java tools/ParallelKernelProbe.java` for the separate no-Minecraft worker-thread break-even probe; it does not imply that the live runtime can safely run off-thread.

M1 immutable program and slot-expression comparison is archived in `docs/benchmarks/m1-2026-11-02/` with 15 raw client reports. To reproduce the measured M0 vs M1 summary after three independent client starts, run `python tools/summarize_m1.py --after <YYYYMMDD-HHMMSS>`. This verifies JSON SHA-256, seed, particle count, GPU, resolution and Minecraft version before producing the comparison. See `docs/M1_EXIT_REPORT.md` for scope and remaining work.

The image and benchmark conditions must be compared together: fixed seed alone is not sufficient for a meaningful A/B performance comparison. Repeat multiple times; run the same Minecraft graphics options, screen resolution, shader/optimization mods, GPU clocks, world and camera, and compare distributions before changing architecture.

## Regression comparison

```powershell
python tools/compare_bench.py path/to/baseline.json path/to/candidate.json --threshold 0.15 --output comparison.json
```

Exit codes: 0 no regression above threshold; 2 significant slowdown; 3 incomparable reports (scenario, seed, measured samples, resolution, GPU, Minecraft version or status differs). Reports with missing metrics will explicitly list them as unavailable. This comparison is a **screening threshold**, not a significance test; repeated identical runs are required before inferring causality. Compare screenshots with the JDK, without external image libraries:

```powershell
java tools/CompareScreenshots.java path/to/baseline.png path/to/candidate.png 0.03
```

This compares RGB RMS error with an allowed threshold, returns 0 for a match, 2 for a significant difference and 3 for missing or incompatible images. Minecraft weather, lighting, chunks and camera must match; screenshot changes alone do not establish a rendering bug. Minecraft resource reload, renderer shader-mod compatibility and 30-minute soak tests are not yet included.

## Baseline policy

M0 may begin only after reproducible real-client baseline captures are stored and reviewed. The first successful smoke test of the unmodified runtime (apart from deterministic/instrumentation hooks) produced a `sprite_1k` report on an RTX 4090 Laptop GPU, OpenGL 4.6 and 854×480. Those early reports are **instrumentation smoke tests** until final unobstructed-camera baseline captures are recorded and reviewed. SHA-256 of the effect JSON is included in subsequent reports to prevent accidentally comparing changed test fixtures. Update this document and record actual hashes/results on every backend change.
