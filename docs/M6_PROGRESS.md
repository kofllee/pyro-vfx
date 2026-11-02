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

5. **Collision query cache.** The existing CPU collision resolution still uses
   the same discrete motion, axis order, shape bounds and event semantics.
   A direct-mapped 8192-entry per-client-tick cache reuses the local collision
   AABB for a queried block position. The cache resets every VFX client tick
   and on manager clear/world exit; it owns no worker threads and no data
   survives a tick. The unchanged world-query path can be forced with
   `-PbenchDisableCollisionCache=true`. The benchmark report records cache
   hits/misses and the debug command `/pyrovfx backend` exposes last-tick
   counters. One new GameTest compares the new bounds-intersection predicate
   with the original AABB.move/intersects rule over 1024 deterministic cases.
   **This optimizes repeated block queries, not broadphase or swept collision.**

## Test evidence

- `.\gradlew.bat build runGameTestServer --no-daemon --console=plain`
  succeeded: **30/30 required NeoForge GameTests**, including ownership
  rejection, scalable admission, deterministic analytic UV frame selection
  and collision AABB math equivalence.
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

### Collision A/B (real client)

GPU: RTX 4090 Laptop, Windows 11, fixed 1920x1080 actual window, VSync off,
identical JSON SHA and seed, original collision fixture and camera.
Three separate Minecraft client starts per cache route were collected for
`collision_no_events_10k`. The 1k variants each have one real client launch
per route. Every run completed after fixing the harness classification of
the intentional no-event 10k fixture.

| Scenario | Cache off → on (median simulation CPU) | Speed ratio | Cache off → on (frame P95) | Independent runs per route |
|---|---:|---:|---:|---:|
| Collision without events, 1k | 0.4364 → 0.2633 ms | 1.66× | 1.805 → 1.902 ms | 1/1 |
| Collision without events, 10k | 4.1362 → 1.9705 ms | 2.10× | 9.896 → 7.792 ms | **3/3** |
| Collision with event, 1k | 0.3905 → 0.2374 ms | 1.65× | 1.368 → 1.479 ms | 1/1 |

Across all six 10k runs the harness recorded **1,019,870 identical collision
contacts** per run. Without cache, the sample window performed 4,385,637
world block queries; with cache, only 1,800 world queries and 4,383,837
cache hits per run. At 1k, event and no-event semantics and contact counts
also matched. The 10k CPU simulation improvement is repeatable on the
measured hardware; the 1k frame P95 did **not** improve.

The 10 raw collision JSON files and two representative client screenshots
are preserved in [benchmarks/m6-2026-11-02](benchmarks/m6-2026-11-02/).
Reproduce the checks with:

```powershell
python tools/verify_m6_collision.py --folder docs/benchmarks/m6-2026-11-02 --after 20261102-024000
```

The *full-frame* screenshot comparator reported RGB RMS **0.161587**
(threshold 0.03, failed), primarily because the two captures visibly differ
in scene brightness/vignette outside the effect. This is **not** evidence of
full-image visual equivalence. A cropped 160x100 particle-region comparison
at source image coordinates `x=900, y=515` yielded RMS **0.002998**,
with zero pixels beyond the comparator threshold (passed). Both original
screenshots and these cropped regions are archived; this localized test does
not verify all scene pixels or every possible particle state.

These results do not establish correctness during in-tick block mutations,
moving block collision geometries, shader-mod interactions or extremely
large/unloaded world regions. World collision still runs on the client thread.

## Not yet implemented or verified

- General appearance/expression/curve compilation to GPU, independent
  particle age/rotation, alpha transparency ordering, continuous and
  event-driven analytic simulation, and stateful GPU compute evaluation.
- GPU-model instancing expansion for all block/item/entity variants.
- New collision broadphase and swept collision (the repeated per-block shape
  lookup is now cached and measured); broader edge-case correctness and
  compatibility for stateful/moving block shapes.
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
.\gradlew.bat runClientBench '-PbenchScenario=collision_no_events,collision_no_events_10k,collision_1k' -PbenchWidth=1920 -PbenchHeight=1080 -PbenchUncapped=true -PbenchDisableCollisionCache=true
.\gradlew.bat runClientBench '-PbenchScenario=collision_no_events,collision_no_events_10k,collision_1k' -PbenchWidth=1920 -PbenchHeight=1080 -PbenchUncapped=true -PbenchDisableCollisionCache=false
python tools/verify_m6_collision.py --folder docs/benchmarks/m6-2026-11-02 --after 20261102-024000
```

Next priority: implement and profile GPU-compiled common age-based
appearance expressions and safe alpha behavior; profile collision under
state changes, non-full collision shapes and dense/rare world geometry;
add client-side cost-aware admission and verify GPU/driver compatibility
on actual modded clients.