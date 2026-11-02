# Analytic GPU renderer — acceptance report

**Outcome:** A million simple additive sprites were rendered in real Minecraft, not simulated by a fake count. This is a narrowly selected analytic GPU backend, not an implementation of arbitrary GPU expressions or collisions.

## Verification

- Minecraft 1.21.1, NeoForge 21.1.229, Java 21; clean Gradle build plus **26/26 NeoForge GameTests**, **5/5** Python comparator tests.
- Headless analytical motion golden checks the same discrete velocity/position recurrence as the original M2 dynamic simulation, including linear drag, acceleration, prior/current position interpolation, and q=1 behavior. Deterministic seeded spawn-order and explicit backend selection/fallback tests pass.
- Three **independent full-client launches** with **10k, 100k and 1M** particles each. The report validator requires complete status, exact requested/peak particle counts, a fully submitted GPU instance count, and **exactly one initial VBO upload** of 24 bytes per particle.
- Real-client compatibility scenarios completed: active particle resource reload, mutable curves, events, UV animation, collision, models, alpha blending, self-emission collision, baked model rotation and inherited model sampling. The analytic resource reload case recorded **two** dataset uploads (original plus reload) and retained its active instance.
- Forced analytical CPU fallback via `-PbenchLegacyAnalytic=true` completed for static and dynamic 10k workloads, selecting `cpu_dense` and the previous M3 instanced renderer. The dynamic GPU/CPU screenshot comparison returned **RGB RMS 0.035209** against a threshold of 0.05. The screenshot comparison includes Minecraft's scene background, not only particles.

## Stress results — three-launch medians

| Particles | Simulation CPU (ms) | Render submission CPU (ms) | GPU VFX (ms) | GPU VFX P95 (ms) | Full-frame P95 (ms) | Draws | Initial GPU dataset |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 10,000 | 0.026 | 0.079 | 0.020 | 0.023 | 10.407 | 1 | 0.23 MiB |
| 100,000 | 0.024 | 0.068 | 0.202 | 0.203 | 10.172 | 2 | 2.29 MiB |
| **1,000,000** | **0.009** | **0.071** | **1.004** | **2.048** | **9.839** | **16** | **22.89 MiB** |

For the same frozen 10k JSON and seed, M3 → analytic M4: CPU simulation **0.260 → 0.026 ms**, CPU VFX render submission **0.448 → 0.079 ms**, GPU VFX median **0.024 → 0.020 ms**. Full-frame P95 was **10.294 → 10.407 ms**, so overall frame-tail improvement is **not claimed**.

Hardware: **Intel Family 6 Model 183 / 32 threads; RTX 4090 Laptop GPU; OpenGL 4.6.0 NVIDIA 610.74; Windows 11; Java 21.0.5; 854 × 480; 12-chunk render distance, 70° FOV, VSync on.** Integrated `Test VFX` world, fixed seed 0x50B02026, effect at (0.5, 140, 0.5), camera at (0.5, 141.5, -12), 30 warmup ticks and 100 measurement ticks. 100k and 1M **explicitly enabled** the development-only heavy particle limit; ordinary Minecraft still uses the regular 20k per-effect safety budget.

Raw source JSON reports and screenshot references are archived under `docs/benchmarks/m4-2026-11-02/`; `tools/summarize_m4.py` rejects missing/invalid samples or unconfirmed GPU instances. The reports were captured before the code was committed, so the embedded `gitCommit` is the last baseline commit, **not** the final M4 source revision. Preserve the completion commit alongside this report.

## Limits and follow-up

The analytic backend supports constant-appearance, constant-lifetime sprites with static or closed-form dynamic motion, not arbitrary mutable `set_param` expressions, per-tick RNG, changing curves, alpha sorting, models or collisions. Those are routed to existing CPU and M3 render paths. See `GPU_ANALYTIC_ARCHITECTURE.md` for the precise feature gate and `/pyrovfx backend` diagnostics.

The initial GPU VBO is 24 MB at 1M and the same-size CPU birth dataset is retained for reload/fallback. The launch-time spawn sampling, shader creation, VBO upload and allocation are **not represented by steady-state tick medians**. The 1M outcome is verified only on the documented high-end GPU, simple fixture, resolution and VSync configuration. No shader/optimization-mod test matrix, 1M colliding particles, compute shader support, or memory soak is claimed. Known incompatible/untested render-mod environments route to Minecraft RenderType fallback.
