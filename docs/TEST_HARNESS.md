# Pyro VFX — Minecraft Test Harness (M−1)

This is an opt-in, **development-only** runner. GameTests exercise common definitions on a real NeoForge Minecraft server; client benchmarks run the actual Minecraft renderer with an OpenGL context. A successful headless GameTest does **not** measure the renderer, GPU, or FPS.

## Tests

```powershell
.\gradlew.bat runGameTestServer --no-daemon
python tools/test_compare_bench.py
.\gradlew.bat clean build
```

The NeoForge tests currently cover mutable expression variables, curve endpoints (linear, step, Catmull and Bézier), parser acceptance of collision/particle events and validation of event parameters. They do **not** yet establish full runtime equivalence for every effect/event/motion/reload behavior.

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
/pyrovfx bench model_1k
/pyrovfx bench sprite_10k
```

The `-PbenchWorld=<world directory name>` property selects the Quick Play world. `runClientBench` also closes Minecraft automatically after the full scenario list and lets its integrated server save the world. Tests are deterministic **with respect to the seeded VFX effect**, not necessarily world chunk updates, driver scheduling, Minecraft lighting, shaders, render order or operating-system frame timing.

The `sprite_100k` and `sprite_1m` scenarios exist, but the object-per-particle legacy runtime may exhaust the client. They must be explicitly enabled with `-PbenchHeavy=true`. This is an **opt-in**, not a guarantee that the test can finish. Do not use high-count scenarios until profiling shows the workload fits the machine and acceptable frame-time limits. Heavy tests use the same JSON effects and are not approximated with fake particle counts.

The harness requires a local/integrated server; it will not run against a multiplayer server. It switches the player temporarily to spectator, moves the camera near the fixed effect at `0.5 140 -12` (camera at `0.5 141.5 -12`, 14-degree pitch), targets daytime, runs the warmup and sampling windows, captures a PNG and restores the original player position/mode/daytime. Avoid running inside a valuable survival save; use a dedicated benchmark world.

## Results

Actual client outputs are generated under `run/pyrovfx-benchmarks/`:

- `<scenario>-<UTC timestamp>.json`: scenarios, Git commit, Minecraft/Java/OS, renderer/GPU/OpenGL, resolution, seed and measurements.
- `<scenario>-<timestamp>.png`: screenshot for human visual review or external diffing.
- `results.csv`: machine-readable row per execution.

Frame metrics are wall-clock intervals measured between successive `AFTER_PARTICLES` render-stage callbacks. They are not graphics-card timings or a claim about whole-game FPS when background tasks run. CPU simulation and render submission are delimited wall-clock measurements of the *Pyro VFX* tick/render callbacks. Thread allocations are HotSpot allocation counters for the calling thread only; heap is process-wide. GC counts and elapsed GC time are JVM-wide. GPU time, when present, comes from **nonblocking OpenGL `GL_TIME_ELAPSED` queries**, measuring GPU work inside the Pyro VFX render call; it may be unavailable on incompatible contexts or shader drivers. A missing metric is `null`, not 0. TPS, draw-call counters, VRAM and GPU hardware counters are not yet instrumented.

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
