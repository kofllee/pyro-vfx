# M6 implementation checkpoint — 2026-11-02

**Current implementation addendum (2026-11-03).** This file preserves the
historical M6 checkpoint below. Later work added depth-sorted GPU alpha
instancing, sprite/model SoA and analytic pool renames, a baked model GL mesh
cache, and an expression parser-integrated GLSL appearance subset with
immutable curves, birth random, age and closed-form motion variables.
Historical fallback limitations below reflect their earlier date.
The real-client A/B reports and remaining restrictions are recorded in
[`alpha smoke`](benchmarks/alpha-smoke-instancing-2026-11-02/README.md),
[`GPU expressions`](benchmarks/gpu-expressions-2026-11-02/README.md)
and [`Entity renderer`](benchmarks/entity-model-study-2026-11-02/README.md).
Existing `pyro_vfx-client.toml` files retain user-edited budgets; only
newly generated configs receive the current defaults from the config spec.
External driver/shaderpack compatibility and long reliability soaks remain
unverified; this is not a production-readiness claim.

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

6. **Shared-age appearance on GPU Analytic.** Once-only additive bursts now
   accept color/scale expressions depending exclusively on
   `particle.age`, `particle.lifetime` and `particle.normalized_age`.
   Expression compilation retains per-number dependency metadata for
   conservative backend selection. One CPU uniform update per emitter
   draw matches the pre-increment CPU age evaluation and previous/current
   partial-tick interpolation. This avoids per-particle appearance evaluation
   on eligible GPU effects, without claiming arbitrary GPU expression
   compilation. Randomness, curves, position-dependent appearance and
   mutable parameters retain the CPU path.

7. **Second-monitor, cursor-safe benchmark launch.** On Windows,
   `tools/run-client-bench-secondary.ps1` refuses to launch unless an
   independent second monitor is present and the requested window fits.
   The development-only GLFW window hook requests an unfocused window at
   creation; the tick policy relocates it, releases Minecraft mouse grab
   and enables mouse passthrough. A Win32 watchdog applies
   `WS_EX_NOACTIVATE`, pushes it behind other windows and restores the
   previous app if Minecraft becomes foreground. The regular game launch
   is unchanged. Measured window position, mouse passthrough and GLFW
   focus flags are included in client benchmark reports. **A platform
   focus switch shorter than the watchdog polling interval cannot be
   categorically ruled out.**

8. **Measured CPU-time-driven adaptive spawn admission.** Added
   `VfxAdaptiveSpawnController` with CPU VFX tick-time EMA, hysteresis,
   4-tick sustained overload detection (requiring both current and smoothed
   CPU time to exceed target), 30-tick recovery intervals and bounded
   multiplicative backoff. It adjusts both global spawn capacity and the
   admitted fraction of each spawn request (so steady emitters below the
   original global cap also respond). The default is enabled in normal
   client gameplay at 3.0 ms target with a 25% minimum throughput.
   `pyro_vfx-client.toml` contains
   `adaptive_spawns.enabled`, `cpu_tick_target_ms` and
   `minimum_spawn_percent`. The user-set hard caps on active particles,
   event delivery and maximum spawn rate remain in force; *existing
   particles are never deleted*. Adaptation is reset on world transition,
   clearing effects and disabling the feature. Automatic client benchmarks
   keep the policy disabled unless `-PbenchAdaptive=true` is supplied.
   The developer-only `-PbenchAdaptiveTargetMs` lets a test deliberately
   exercise a tighter CPU budget without changing user configuration.
   This was initially CPU-only adaptive spawn admission; subsequent work
   (item 10) adds optional GPU-time and free-memory signals, but not automatic
   texture quality or a full cross-backend cost model.

9. **Prioritized admission and per-effect cost diagnostics.** Optional
   `metadata.priority` values `ambient`, `normal`, `important`,
   `critical` control how strongly new particle spawns are reduced during
   overload, without changing effect tick order, physics or events. The
   `critical` tier bypasses only adaptive quality reduction and still
   obeys all hard caps. Per-effect CPU tick EMA and selected backends
   are displayed by `/pyrovfx backend`. Execution order remains the
   original insertion order; this is not strict priority scheduling.

10. **Asynchronous GPU cost and optional VRAM pressure.** Added a bounded
    8-query OpenGL 3.3 `GL_TIME_ELAPSED` ring, polling only available
    results. The GPU policy uses a separate EMA and only new GPU samples;
    the effective adaptive spawn scale is the minimum of CPU, GPU and
    optional VRAM pressure. On unsupported drivers the GPU signal is
    ignored, never treated as a zero-time or out-of-memory reading.
    Query resources are invalidated on reload/world reset on the render
    thread. GPU timings cover **Pyro VFX render submission**, not total
    scene GPU time, and default policy targets 2.0 ms. The optional
    NVIDIA-only `GL_NVX_gpu_memory_info` probe is off by default because
    it measures device-wide available memory and may add driver overhead.
    On unsupported hardware VRAM remains unknown and does not throttle.

11. **Mixed render/reload and CI hardening.** Added the `soak_mixed`
    real-client fixture (analytic GPU sprite, translucent CPU sprites,
    colliding CPU particles), three automated resource reloads, and
    report fields for reload cycles and asynchronous GPU samples.
    The benchmark supports 300–36,000 measured ticks without unbounded
    sample-array growth. `build.yml` now requires headless GameTests and
    archive integrity checks alongside compilation. A configured CI step
    is **not** equivalent to a verified successful GitHub Actions run.

12. **Per-render-cost hard ceilings.** New spawn requests are classified
    into `GPU_ANALYTIC`, `CPU_BASIC`, `CPU_COLLISION` and `CPU_MODEL`.
    `pyro_vfx-client.toml` exposes `gpu_analytic_particles` (80k default),
    `cpu_basic_particles` (80k), `cpu_collision_particles` (20k) and
    `cpu_model_particles` (10k). These caps apply *in addition to* the
    global active and per-instance limits and do not delete particles.
    Budget accounting is shared across all effects, including manual or
    event-driven spawn requests. `/pyrovfx backend` and benchmark JSON
    show per-class admitted/rejected counts. The classification is a
    **coarse safety class**, not calibrated CPU/GPU time per individual
    particle. Fallback of a previously GPU-analytic pool to CPU rendering
    remains a special case; do not interpret caps as guaranteed FPS.

13. **Dense 3D model streaming and general baked-quad instancing.**
    Eligible solid `custom`, `block` and `item` models now use the
    primitive-array SoA physics path and stream position, scale, color
    and baked vertex tint to the GPU in batches (16,384 instances/draw).
    Rotating models are GPU-instanced with per-particle quaternion and
    lightmap coordinates, while their more complex simulation remains
    object-based. Minecraft lightmap binding is explicit and restored after
    rendering; unlit models bypass the lightmap sample. Translucent
    depth-sorted models and animated `entity` geometry retain the legacy
    path. These paths have **not** established equivalence for every
    resource-pack model, item component, shaderpack or render layer.

14. **Measured adaptive cost and larger ceilings.** New client config
    defaults are 240,000 global active particles, 120,000 per-effect
    active particles, 100,000 new particles/tick globally and per effect,
    with 240,000 GPU analytic, 120,000 CPU sprite, 20,000 collision and
    **120,000 model** particle class ceilings. These are hard *upper*
    bounds, not safe throughput guarantees; existing config files retain
    their explicit previous values. Per-effect CPU EMA now tightens
    admission for particularly costly effects when the global quality
    controller detects overload, respecting `metadata.priority`. It
    does not delete live particles, slow physics or suppress events.

15. **Continuous GPU analytic and collision precision.** Steady once-lived
    analytic emitters now create birth cohorts; every cohort has its own
    age uniform and once-uploaded position/velocity buffer. Immutable
    shared-age curves are evaluated per cohort, not per particle; GPU
    instances remain in GPU memory between render frames. Complex
    per-particle expressions, transparent alpha sorting, external events
    and arbitrary parametric motion still require fallback. Minecraft
    multipart collision shapes are now cached as individual sub-boxes
    (8,192-entry per-tick cache) with a bounds broadphase, preventing false
    hits in cavities of complex collision shapes.

## Test evidence

- `.\gradlew.bat build runGameTestServer --no-daemon --console=plain`
  succeeded: **40/40 required NeoForge GameTests**, including ownership
  rejection, scalable admission, deterministic analytic UV frame selection
  and collision AABB math equivalence, plus age-only appearance interpolation
  and random/position-dependent fallback rejection, and bounded adaptive
  backoff/recovery, single-stall immunity and separate event and
  existing-particle safety checks, priority weight behavior,
  per-cost-class global caps and safe VRAM unknown-value fallback.
  The new tests cover multipart collision gaps, shared-age analytic
  curves and measured per-effect CPU admission.

### Short 3D and continuous-emitter A/B (RTX 4090 Laptop, 1280x720)

The following are **actual client benchmark reports**, with no long soak.
All particle counts refer to observed peak live particles; 100k model
was run with the existing developer heavy-benchmark opt-in. Screenshots
were compared at equal definition SHA, seed, resolution and measurement
window. The data is archived in `docs/benchmarks/m6-2026-11-02/universal-short/`.

| Effect | Active particles | Model GPU instances | P95 full frame | Median CPU simulation |
|---|---:|---:|---:|---:|
| Custom model / dense | 10,000 | 10,000 | 1.667 ms | 0.407 ms |
| Rotating, world-lit model | 10,000 | 10,000 | 4.740 ms | 0.747 ms |
| Block model / dense | 10,000 | 10,000 | 1.432 ms | 0.396 ms |
| Item model / dense | 10,000 | 10,000 | 2.220 ms | 0.420 ms |
| Custom model / dense | 100,000 | 100,000 | 10.895 ms | 4.069 ms |
| Continuous analytic GPU | 7,800 | n/a (GPU sprite cohorts) | 1.512 ms | 0.116 ms |
| Continuous CPU fallback | 7,800 | n/a | 1.737 ms | 0.704 ms |

At 1,000 item models, GPU instancing vs legacy P95 was 1.434 vs
8.409 ms (visual RMS 0.028090, threshold 0.03); at 1,000 rotating
world-lit models 1.393 vs 4.030 ms (RMS 0.014616).
For continuous GPU analytic vs CPU dense, RMS was 0.000570.
**These are single paired runs**, not repeatability statistics; the
comparison is not a guarantee of a particular FPS on other hardware.
GPU analytic continuous emits many draw calls (one per active cohort),
so reducing cohort draw overhead remains a potential optimization.

The post-lighting-fix 100k custom-model test recorded 7 GPU model
draws in its sampled frame, 4.103 ms median Pyro-only GPU work and
991 MiB peak JVM heap. No isolated cold-start GPU compilation latency is
reported, and heap is not an estimate of Pyro's exclusive memory.

To verify archived evidence and three independent screenshot comparisons:

```powershell
python tools/verify_universal_short.py
java tools/CompareScreenshots.java docs/benchmarks/m6-2026-11-02/universal-short/screenshots/continuous_cpu.png docs/benchmarks/m6-2026-11-02/universal-short/screenshots/continuous_gpu.png 0.03
java tools/CompareScreenshots.java docs/benchmarks/m6-2026-11-02/universal-short/screenshots/item_legacy.png docs/benchmarks/m6-2026-11-02/universal-short/screenshots/item_gpu.png 0.03
java tools/CompareScreenshots.java docs/benchmarks/m6-2026-11-02/universal-short/screenshots/rotating_legacy.png docs/benchmarks/m6-2026-11-02/universal-short/screenshots/rotating_gpu.png 0.03
```
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

### Shared-age appearance and desktop-safe launcher

A real Minecraft client (Windows 11, RTX 4090 Laptop, NeoForge 1.21.1)
completed the new `analytic_age_1k` scenario with a matching CPU fallback
using the same SHA-256, seed, 1280x720 window and VSync setting. The shared-age
GPU route recorded 1,000 GPU analytic sprites and **0.03170 ms** median CPU
simulation; forced `cpu_dense` recorded **0.10185 ms**. End-to-end frame
P95 was **9.999 ms** vs **10.158 ms**, so this single matched pair does
not establish a significant full-frame FPS benefit. The screenshot comparator
returned RGB RMS **0.006248** (threshold 0.03, passed) with a changed pixel
fraction of 0.000229. The two raw JSON reports and exact screenshots are
stored in the M6 benchmark archive.

Windows reported an existing Chrome window as foreground before and after
the final second-monitor A/B launch. The watchdog detected one Minecraft
window and **zero** 50-ms foreground-restore attempts; that is evidence
that Windows foreground did not appear to transfer to Minecraft during
the sampled intervals. GLFW independently returned `windowFocused=true`
in its internal report, while `mousePassthrough=true` and the window
was moved to the secondary display. Thus do not equate GLFW's internal
focused flag with a verified foreground-app switch. A sub-50-ms initial
focus transition remains unexcluded, and the launcher is Windows-specific.

To run without stealing the main display or cursor:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario analytic_age_1k -Width 1280 -Height 720 -RequireGpuAnalytic -RenderStateAudit
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario analytic_age_1k -Width 1280 -Height 720 -ForceCpuAnalytic -RenderStateAudit
```

### Adaptive steady-emission A/B

Two *actual Minecraft client runs* with the same steady colliding sprite
definition SHA, seed, CPU fallback, 1280×720 secondary display, uncapped
frame rate and VSync disabled were recorded on RTX 4090 Laptop.
The benchmark intentionally used a **developer-only 0.5 ms CPU target** in
its adaptive run, substantially lower than the normal client default of
3.0 ms. This exercises the controller and is **not** a claim that a normal
client will throttle the same scene or achieve the same performance.

| Metric | Adaptation off | Adaptation on (test target 0.5 ms) |
|---|---:|---:|
| Peak active particles | 10,000 | 9,946 |
| CPU simulation median | 1.39735 ms | 0.84575 ms |
| Full-frame P95 | 6.90592 ms | 4.98017 ms |
| Minimum admitted spawn fraction | 100% | 25% |
| Adaptive-constrained measurement ticks | 0 | 100 |
| Total admitted spawns | 36,000 | 24,769 |
| Total rejected spawn requests | 0 | 53,231 |

This is a single A/B pair, and lowering emitted particle quantity changes
the visual workload: **it is not a rendering speedup at equal particle
counts**. No visual equality claim is made. The `adaptive_steady` JSON
fixture, two raw measured reports and screenshots are archived in
`docs/benchmarks/m6-2026-11-02/`. The file matching, environment, measured
samples, controller activation and safe budget checks are reproducible:

```powershell
python tools/verify_adaptive_bench.py docs/benchmarks/m6-2026-11-02/adaptive_steady-20261102-132543.json docs/benchmarks/m6-2026-11-02/adaptive_steady-20261102-132645.json
```

The CPU target protects client responsiveness from sustained
VFX simulation cost. This historical A/B predates the later GPU-time and
VRAM signals; these are verified separately below.
It does not promise any global FPS threshold, equality between effect
instances under saturation, or immunity to short bursts.

### Final renderer telemetry and mixed-workload verification

Real client, NVIDIA RTX 4090 Laptop, Windows 11, NeoForge 1.21.1,
1280x720, VSync off and the secondary-monitor/mouse-passthrough launcher.

| Scenario | Measured ticks | Resource reloads | GPU query samples | Peak particles | CPU median | Frame P95 |
|---|---:|---:|---:|---:|---:|---:|
| `soak_mixed` | 600 | 3/3 | 5,794 | 2,388 | 0.244 ms | 2.546 ms |
| `soak_mixed` | 1,800 | 3/3 | 46,722 | 2,388 | 0.223 ms | 2.619 ms |

The mixed fixture simultaneously exercises an analytic GPU additive burst,
CPU translucency and colliding particles. The longer run recorded 14,400
real frames and **completed successfully**. Peak heap was 1,238 MiB
and 157 Java GC collections were observed during the run; these are
whole-client figures, not allocated memory belonging solely to Pyro.
The two durations are not enough to assert zero leaks over 30 minutes.

Following the introduction of independent cost-class budgets, the mixed
workload was executed again for 600 ticks and three full reloads, with a
successful render-state audit. The run recorded **2,388** peak live
particles, **5,205** completed GPU queries and the following spawn totals
in the new cost-class counters: GPU analytic **600**, CPU basic **7,560**,
CPU collision **9,450**, CPU model **0**. No category exceeded its limit,
and no spawn requests were rejected in this moderate-load test. The
raw report `soak_mixed-20261102-135159.json` and screenshot are archived.

A separate `analytic_age_10k` run with explicit
`-GpuTelemetry -ProbeVram` succeeded with 10,000 GPU analytic instances,
7,052 nonblocking timer results and an NVIDIA-only free memory reading
of **14,275 MiB free / 16,376 MiB total**. This is device-wide available
VRAM, **not Pyro's VRAM usage**. On devices without that NVIDIA extension,
the measurement remains unknown and the memory-pressure heuristic is
disabled. The probe is *off by default* in client configuration.

Raw JSON, screenshot samples and source fixtures are archived under
`docs/benchmarks/m6-2026-11-02/`. Verification:

```powershell
python tools/verify_soak_bench.py docs/benchmarks/m6-2026-11-02/soak_mixed-20261102-134026.json docs/benchmarks/m6-2026-11-02/soak_mixed-20261102-134356.json
```

**Release gate assessment:** local code builds and gameplay tests pass,
and documented NVIDIA client scenarios work. Production/driver-agnostic
certification is **not complete**: a 36,000-tick soak, Intel/AMD GPUs,
shaderpacks and multiplayer clients are still not verified. See
[`COMPATIBILITY_MATRIX.md`](COMPATIBILITY_MATRIX.md) rather than treating
unavailable hardware tests as passed.

## Not yet implemented or verified

- General appearance/curve compilation to GPU beyond shared-age uniform
  evaluation, independent
  particle age/rotation, alpha transparency ordering, continuous and
  event-driven analytic simulation, and stateful GPU compute evaluation.
- Geometry skinning and animations for `entity` models and
  depth-sorted translucent 3D meshes; basic static baked custom/block/item
  geometry is now batched, with lighting and per-instance rotation.
- Swept collision/time-of-impact integration, moving block/state
  invalidation within the same game tick, and chunk-aware collision
  broadphase. Multipart voxel-shape intersection is implemented but does
  not imply full rigid-body continuous collision detection.
- Remaining adaptive-quality work: per-effect GPU attribution,
  visibility-aware cost control and precise Pyro-owned VRAM accounting.
  Current GPU timing is global and optional NVIDIA VRAM telemetry is
  device-wide. Neither is a guarantee of safe 4M-particle effects.
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
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario adaptive_steady -Width 1280 -Height 720 -Uncapped
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario adaptive_steady -Width 1280 -Height 720 -Uncapped -AdaptiveSpawns -AdaptiveCpuTargetMs 0.5
python tools/verify_adaptive_bench.py docs/benchmarks/m6-2026-11-02/adaptive_steady-20261102-132543.json docs/benchmarks/m6-2026-11-02/adaptive_steady-20261102-132645.json
```

Next priority: reduce GPU analytic cohort draw overhead, then verify
fallback and render-state parity on actual integrated GPUs and real
shader/optimization mod combinations. Do not infer 4K or cross-driver
compatibility from 720p Nvidia results. No long-duration soak was run
for this extension.