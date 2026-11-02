# M6 implementation checkpoint — 2026-11-02

**Status: partial implementation, not M6 completion and not production-ready.**

Target: Minecraft 1.21.1, NeoForge 21.1.229, Java 21.
Baseline: [M5 verification](M5_DECISION_AND_VERIFICATION.md).
The untracked user-authored `docs/ENGINE_REARCHITECTURE_RFC.md` and
`docs/benchmarks/README.md` were deliberately preserved, not incorporated
into this work.

## Implemented

1. **Independent particle lifecycle.** Deleted `VanillaParticleBridge`,
   `VfxMinecraftParticleRenderDefinition`, the render enum value and related
   parser/emitter code. Definitions with `render.type=minecraft_particle`
   now fail with an explicit migration message. The existing Minecraft
   `RenderType` fallback still renders *Pyro-owned* particles.
   This is a deliberate breaking change to that removed JSON render type.

2. **Configurable admission ceilings.** Added client config
   `pyro_vfx-client.toml` with `particle_budgets.global_particles`,
   `global_spawns_per_tick`, `effect_particles`,
   `effect_spawns_per_tick` and `active_effects`.
   M5 defaults are preserved (80k global / 20k per-effect active,
   30k global / 20k per-effect spawn/tick, 256 instances).
   Upper *configurable* bounds: 4M active, 1M spawn/tick and 4096 instances.
   Values before config load and on dedicated test servers fall back to M5
   defaults. Separate explicit-limit test exercises 2M active and 500k/tick.
   This is admission control, **not proof of safe 4M-particle rendering**.

3. **GPU analytic UV animation.** Eligible additive, fullbright,
   instant-once sprite emitters now support constant-parameter `full`,
   `static` and `animated` UV configurations. Frame progression uses
   the existing sprite UV scheduler once per emitter render; a `uUvRect`
   shader uniform selects the frame. Birth data remains six floats
   (24 bytes) per particle and is uploaded once per emitter. Nonconstant
   UV expressions and per-particle random starts retain the CPU fallback.
   This is a fast path for *shared-age bursts*, not general GPU sprite
   expression compilation or continuous emission.

4. **Harness improvements.** Added separate `analytic_uv_1k` and
   `analytic_uv_10k` scenarios and a new JSON benchmark fixture, without
   changing older fixtures. `runClientBench` accepts opt-in
   `-PbenchWidth`, `-PbenchHeight`, `-PbenchUncapped=true`. The latter
   temporarily disables VSync, raises the FPS setting and restores both
   settings after the scenario. New runs with an explicit requested size
   will be marked invalid when the real window size differs.

## Test evidence

- `.\gradlew.bat build runGameTestServer --no-daemon --console=plain`
  succeeded: **29/29 required NeoForge GameTests**, including ownership
  rejection, scalable admission and deterministic analytic UV frame selection.
- Actual integrated-server Minecraft client benchmark (OpenGL 4.6 NVIDIA
  RTX 4090 Laptop, Windows 11, Minecraft 1.21.1, Java 21.0.5).
- The CSV and JSON metrics below describe **real measured client runs**
  of the new shared-UV analytic scenario. Each row is **one run**; results
  are not a three-run statistically controlled M5 comparison.

| Scenario/backend | Actual window | VSync | Particles | P95 frame ms | Median CPU simulation ms | Median GPU VFX ms |
|---|---|---|---:|---:|---:|---:|
| analytic_uv_1k / gpu_analytic | 854x480 | on | 1,000 | 10.250 | 0.03185 | 0.002048 |
| analytic_uv_1k / cpu_dense forced | 854x480 | on | 1,000 | 10.269 | 0.07460 | 0.002048 |
| analytic_uv_1k / gpu_analytic | 1920x1080 | off | 1,000 | 1.324 | 0.02880 | 0.002048 |
| analytic_uv_1k / gpu_analytic | 2560x1440 | off | 1,000 | 1.584 | 0.02900 | 0.002048 |
| analytic_uv_10k / gpu_analytic | 2560x1440 | off | 10,000 | 1.335 | 0.02730 | 0.012288 |
| analytic_uv_1k / gpu_analytic | **3840x1581** | off | 1,000 | 1.578 | 0.03030 | 0.002048 |
| analytic_uv_10k / gpu_analytic | **3840x1581** | off | 10,000 | 1.555 | 0.02740 | 0.013312 |

The last two rows requested 3840x2160, but Windows created a
**3840x1581** window. They are **not 4K benchmarks**. They were collected
before automatic requested-size validation was added; the actual dimensions
are preserved in the original reports.

The 854x480 A/B pair uses the same JSON SHA, seed, world, window and VSync
setting. Median CPU simulation time fell from 0.07460 to 0.03185 ms in
this *single* comparison (about 2.34x), while end-to-end P95 frametime was
essentially unchanged. The GPU time query captures only Pyro VFX render
work, not total scene GPU time. The screenshots look visually consistent
by manual inspection, but there is **no automated pixel-equivalence test**.
The analytic run recorded 1,000 GPU instances with a single 24,000-byte
dataset upload. This evidence does not establish multi-category speedup.

An eighth client run **after** installing the resolution validation correctly
produced `status=invalid` with `Requested benchmark window 3840x2160
differs from actual window 3840x1581`, and exited normally. This verifies
the harness does not silently accept a clamped 4K window.

Raw JSON for all eight recorded runs (seven completed, one intentionally
invalid) and two CPU/GPU screenshot examples
are in [benchmarks/m6-2026-11-02](benchmarks/m6-2026-11-02/).
No historical M5 benchmark output was overwritten.

## Not yet implemented or verified

- General appearance/expression/curve compilation to GPU, independent
  particle age/rotation, alpha transparency ordering, continuous and
  event-driven analytic simulation, and stateful GPU compute evaluation.
- GPU-model instancing expansion for all block/item/entity variants.
- Measured improvement to collision broadphase, swept collision and
  per-cell shape caching; correctness remains on the existing CPU route.
- Automatic adaptive quality based on CPU/GPU timings, VRAM, spawn
  throughput, memory and effect priorities; configurable caps alone are
  insufficient for safe 4M-particle behavior.
- Dedicated mixed-workload tests with alpha overdraw at 1080p/1440p,
  **true** 3840x2160 results, cold-start/frame-stall distributions,
  long-running soak/GL resource leak checks, and repeated reload/world
  transitions.
- Direct testing on Intel/AMD GPUs, Iris/Oculus/Sodium/Embeddium
  configurations and multiple shaderpacks; multiplayer/TPS validation.
- Repeatable multi-run A/B comparisons across all realistic categories.

Do **not** infer universal compatibility, renderer parity, production
readiness or completion of M6 from the successful builds or partial results.

## Reproduction

```powershell
.\gradlew.bat build runGameTestServer --no-daemon --console=plain
.\gradlew.bat runClientBench '-PbenchScenario=analytic_uv_1k' -PbenchRequireGpuAnalytic=true -PbenchRenderStateAudit=true
.\gradlew.bat runClientBench '-PbenchScenario=analytic_uv_1k' -PbenchLegacyAnalytic=true -PbenchRenderStateAudit=true
.\gradlew.bat runClientBench '-PbenchScenario=analytic_uv_1k,analytic_uv_10k' -PbenchWidth=2560 -PbenchHeight=1440 -PbenchUncapped=true -PbenchRequireGpuAnalytic=true -PbenchRenderStateAudit=true
```

Next priority: implement and profile GPU-compiled common age-based
appearance expressions and safe alpha behavior; separately optimize
and A/B-test CPU collision world lookups; add client-side cost-aware
admission and verify GPU/driver compatibility on actual modded clients.