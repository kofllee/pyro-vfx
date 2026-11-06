# Pyro VFX — Engine Re-architecture RFC

Status: **proposal / not yet implemented**
Date: 2026-11-01
Target: Minecraft 1.21.1, NeoForge 21.1, Java 21
Baseline revision: `c44bbbb` (main)
Purpose: one combined **correctness, stability and extreme-performance** milestone before the in-game editor and public library API.

## 1. Product goal and non-goals

Build a lightweight Minecraft VFX engine that keeps JSON-driven authoring, expression/curve/event semantics, sprites and model particles, and supports both everyday particles and extraordinary high-density displays.

"One million" is a stretch target for a **defined, simple GPU visual workload on measured hardware**, not a universal guarantee for one million colliding/event-generating models or zero FPS loss. Rendering, simulation, CPU, GPU, fill rate and TPS are separate metrics.

Prioritize:
- visual-effect semantics and existing `pyro_vfx:1` content;
- low CPU time, low allocations/GC, low GPU cost, stable frame pacing;
- bounded behavior under invalid or malicious VFX definitions;
- client-only visual simulation and no visual work on dedicated servers;
- graceful capability-based degradation, particularly when GPU compute is unavailable.

Defer the in-game editor, Fabric port, node graph, bone animation and cross-loader architecture until this milestone is verified.

## 2. Source audit (confirmed, not benchmarked)

- `ClientVfxManager` owns a global list of `ClientVfxInstance`.
- `ClientVfxInstance` holds `ClientVfxEmitter` objects. Every emitter holds an `ArrayList<ClientVfxParticle>`. Each particle stores numerous `Vec3` and other objects. This is **not a data-oriented particle ECS**.
- `ClientVfxInstance.particles()` allocates and copies a combined list; `VfxRenderer.render` invokes it each frame. `getActiveParticleCount` also uses it.
- `ClientVfxParticle.tick` reconstructs a particle expression context twice; `VfxExpressionContext` creates per-context hash maps, boxed values and parent chains; expressions use string-key lookups.
- `SpriteVfxParticleRenderer` does camera/rotation transforms with a `PoseStack` per particle, and submits four vertices on the CPU per particle.
- `CustomModelVfxParticleRenderer` resolves a model, creates a new `RandomSource` and obtains model quads during per-particle rendering.
- Collision queries Minecraft block states/shapes against AABBs per colliding particle; it uses the bounds of shapes (not necessarily exact voxel collision geometry), checks axis movement and has no swept collision.
- `ClientVfxEmitter.tickParticleTriggers` invokes events during iteration. `emitManual` can append to the same particle `ArrayList`; this is a **potential concurrent modification/reentrant mutation bug**, requiring a reproducing test.
- `ClientVfxEmitter` enforces `maxParticles` in steady emission, but instant/manual emission goes through `spawnParticles` without that cap. There is no global budget.
- `VfxModelSpawnShapeSampler.sample` executes `System.out.println` on every model-shape sample. Its `parseElement` reads rotation fields from the wrong JSON object (`json` instead of `rotationJson`) and requires regression tests.
- The resource registry clears before reloading; a robust new registry should be built and validated, then atomically swapped. Tests should cover reload errors and surviving active instances.
- `VfxEventRunner` has a recursion depth limit; breadth/particle spawn caps are still required.
- Local `.\gradlew.bat clean build --no-daemon` **passed**, but `test NO-SOURCE`: automated Java tests are not checked in.
- CI workflow exists and its latest inspected run succeeded. The note in `docs/STATUS.md` that CI is absent is stale.

These are code-review findings, **not measured hot-spot percentages**.

## 3. Architectural decision: hybrid data-oriented execution

Retain high-level effect/emitter controllers, but separate particle simulation and rendering:

```text
Existing assets/<namespace>/vfx/**/*.json
       |
Parsing and semantic validation
       |
Compiled VfxProgram (immutable)
  - variable slots and expression IR
  - evaluation-frequency classification and dependency tracking
  - emitter scheduling / events / render descriptors
  - capability requirements and supported backend variants
       |
EffectScheduler (client-controlled; deterministic tick boundaries)
  - instances, parameters, looping, timelines, manual events
  - command/event queues and spawn requests
  - global/per-effect/per-backend budgets
       |
  +---- CPU Data-Oriented Backend ------+
  | dense SoA particle pools            |
  | optional collision, precise events  |
  | selective job parallelism           |
  +-------------------------------------+
  +---- GPU Analytic Backend -----------+
  | spawn parameters and seed uploaded  |
  | shader computes age/transforms      |
  | no readback/per-particle CPU ticks   |
  +-------------------------------------+
  +---- Optional GPU Compute Backend ---+
  | GL 4.3 or verified extensions       |
  | persistent buffers, dispatch/update |
  | shader-supported dynamic motion     |
  +-------------------------------------+
       |
Renderer: grouping, instancing, culling, LOD, material state
       |
Minecraft/NeoForge render-stage integration
```

### ECS/data layout

Don't implement a generic entity-component framework merely to say "ECS". Use dense archetype- or feature-specific particle pools (SoA, or chunked AoSoA where profiling benefits justify it):
- sprite/basic motion pool;
- collision-enabled pool;
- model pool;
- optional GPU-resident pools.

Keep hot fields in primitive arrays or direct/off-heap buffers **only if benchmarked**. Choose float where acceptable; maintain double-precision world origin or use camera-relative/local coordinates to avoid far-from-origin jitter. Use indices, generation counters, free lists and safe stable handles where exposed. Use a field-presence or archetype mask so simple sprites do not carry data for collision, complex rotation or dynamic events. Avoid per-particle allocations and virtual dispatch in hot loops.

The scheduler is not itself one entity per particle; effects and emitters remain rich high-level owners. Particle data can be processed in batches.

### Expression compiler and semantic preservation

Existing strings/expressions are a user-facing language and should remain valid.
- Parse once, compile to immutable program/IR.
- Resolve named variables to numeric slots; avoid per-tick hash lookups.
- Classify expressions into definition constant, effect start, emitter start, particle spawn, particle tick or render-frame; classify dependencies on mutable parameters and random calls explicitly.
- Constant fold and cache only values whose dependencies justify it. `set_param` must invalidate the right cached results.
- For GPU-compatible expressions, compile a verified subset into shaders. Unsupported functions, random behavior, curve types or events must trigger CPU routing or a documented fallback, **not silent behavior loss**.
- Preserve tick ages, first-frame semantics, event ordering and random seeds. Define tolerances for CPU float/double and GLSL output comparisons.
- A per-particle expression that uses world-block reads, collision events or CPU callbacks is **not safely GPU-only** without a new explicit contract.

### CPU execution

Schedule phases:
1. collect new play/stop/set_param/trigger commands;
2. freeze current tick inputs; advance effect and emitter schedules;
3. generate bounded spawn requests with deterministic seeds;
4. update particle pools by enabled systems;
5. gather collision/event outputs into bounded queues;
6. process ordered event commands at a defined barrier (current-tick or next-tick contract), then compact dead particles;
7. publish immutable/safe render-visible snapshot.

Never mutate a particle pool during iteration. Thread off-main only data-only particle math; Minecraft world/block reads remain on the authorized game thread or use a carefully captured immutable snapshot. Avoid mixing render and tick mutation. Add event and spawn budgets, overflow counters, recovery behavior and priority levels.

### GPU execution: two distinct strategies

**Analytic instanced GPU path (broad compatibility):** store birth time, start transform/velocity, seed and small material data; calculate current position, scale and color in a vertex shader each rendered frame. Suitable for static, linear, parametric and expressible ballistic/drag trajectories when their semantics match. Needs no compute shader and no per-particle Java ticking; feature checks and real-world OpenGL 3.2 compatibility testing required. It cannot generally support arbitrary feedback-based motion/collision/events.

**Compute GPU path (optional):** for capable OpenGL 4.3 contexts, simulate mutable particles in GPU buffers using compute shaders, with explicit buffer ownership and synchronization. Support only verified expression features; do not read back all particles per frame. Benchmark/guard synchronization and driver differences.

The fallback is efficient CPU SoA + batched rendering. Capability selection must be visible in diagnostics and optional overrideable for testing. Avoid requiring OpenGL 4.3 just to load the mod.

### Rendering and geometry

- Replace per-particle `PoseStack` and four-vertex CPU work with shared quad geometry/instance data for the supported fast path.
- Batch by material/texture atlas, blend/depth state, model and render mode.
- For models, cache baked geometry/render descriptors and invalidate on resource reload; use GPU instancing for compatible simple models. Keep a lower-cap legacy fallback for complex Minecraft baked models and entity models.
- Implement coarse effect/emitter bounds, distance culling, optional frustum testing, per-quality spawn LOD, and visibility-aware priority.
- Transparency can dominate performance through overdraw; profile opaque/additive/alpha separately. Correct blend ordering must be specified, especially for translucent effects.
- Respect render-thread ownership, Minecraft GL state, shader/resource reloads, world unload, dimension change, and compatibility with major shader/render optimization mods.
- Use async GPU timer queries and deferred results for profiling; avoid blocking readback.

## 4. Semantics contract and test fixtures

Build a golden-reference suite from the *existing* implementation before replacing the engine. Preserve definitions and representative results; then run old and new runtime against identical fixed seeds and tick inputs.

Required scenarios:
- point/sphere/box/line/disc/ring/cone/model spawn distributions;
- once/looping/manual emitters and effect lifetimes, delays, sleeps and zero-loop semantics;
- named curves (linear, step, catmull, bezier) and expressions;
- parameter overrides and mid-effect `set_param`;
- on_creation, timeline, on_expiration, nested sequence/randomize;
- particle on_collision event position and repeated-contact controls;
- motion, rotation, dynamic drag, appearance/UV animation;
- resource reload while effects are active, invalid JSON and invalid GPU features;
- stop, world exit, scene reload, server-only load, low-feature GPU fallback.

Track spawn counts, random sequences where required, normalized ages, positions/colors within tolerance, event order and emitted-effect limits. Add integration captures/manual visual review for effects impossible to judge by unit tests alone.

## 5. Benchmarks, evidence and target levels

Record hardware, GPU driver, OpenGL version, 1080p graphics preset, shader-mod state, baseline FPS / 1%-low, client MSPT, CPU time, GPU time, heap/allocations, GC, active particles, visible particles, spawn rate, draw calls, VRAM, server TPS. Run warm-up and repeated samples. Report medians and variance; run with and without relevant optimization/shader mods.

Workloads:
1. 1k / 10k / 100k / 1M simple static/additive sprites;
2. same with alpha overdraw and 4K;
3. 10k / 100k dynamic sprites with basic expressions;
4. 1k / 10k / 100k model particles (split custom/block/item/entity);
5. 1k / 10k colliding particles, rare vs dense block scenes;
6. 100 / 1k active emitters and event-dense effects;
7. looping for 30 minutes and world/resource reload stress;
8. multiplayer server TPS versus a no-VFX baseline.

1M * 64 bytes ~ 64 MB raw particle state, before rendering buffers, allocator overhead, model/texture data and snapshots. Report actual allocation rather than assuming a 64-byte format.

**Proposed gates, not pre-existing results:**
- A CPU slice beats the current engine for matching workloads without changing outputs and eliminates recurring hot-path allocations.
- Rendering optimizations produce measured improvement at 10k and 100k visible sprites, without shader breakage.
- GPU analytic 1M workload is demonstrated with a documented hardware/scene/FPS baseline; report regression rather than claiming no cost.
- All workloads have enforced budgets; pathological inputs do not crash or permanently stall the client.
- Dedicated server remains independent from client classes/VFX simulation and retains baseline TPS.

Do not commit to a fixed FPS number before picking reference hardware and running a baseline.

## 6. Migration milestones and exit criteria

### M0 — Instrumentation and behavior freeze
- Add headless/isolated parser/expression/lifetime/event tests plus controlled in-game benchmarks; gather baseline recordings.
- Fix known correctness bugs, intrusive logging and dangerous reentrant mutations.
- Establish exact observable contracts and test suite; no broad rewrite yet.
**Exit:** baseline and golden fixtures, test commands and profiler captures exist.

### M1 — Compile-on-load definitions
- Immutable `VfxProgram` IR, slot-indexed variable lookup, expression dependency analysis.
- Freeze/capture current behavior, prevent runtime parsing/evaluation allocations.
- Keep `pyro_vfx:1` loader and authoring surface unchanged.
**Exit:** all golden tests pass; representative expression benchmarks improved.

### M2 — CPU data-oriented storage
- Particle pools with SoA/AoSoA choice based on benchmark.
- Fixed phase scheduler and bounded spawn/event queues.
- CPU collision and optional parallel math, with no unsafe world access.
**Exit:** stable memory/GC, event ordering, replay consistency, lower CPU cost.

### M3 — Fast GPU-compatible rendering
- GPU instancing of sprites and simple models, cache material/model data.
- Coarse culling, LOD, correct transparency policy, proper GL resource lifecycle.
**Exit:** measurable gain, correct visuals, no render-state leaks or major shader-mod regressions.

### M4 — GPU analytic simulation
- Shader-compatible expression subset, age-based dynamics, GPU-side rendering from spawn attributes.
- Defined fallback for unsupported/mutable features.
**Exit:** documented 100k and aspirational 1M simple-particle workload, stable memory and fallback.

### M5 — Optional compute backend
- GL4.3 capability probe, selected stateful simulation kernels, buffer synchronization, dynamic backend selection.
- Prototype first: don't implement if the analytic backend already achieves all required effects and performance.
**Exit:** significant measured benefit on realistic dynamic effects and no driver stability regressions.

### M6 — Production hardening
- Global per-type/per-effect particle and event budgets, priority and overload behavior.
- Live counters/recordings, reload and lifecycle tests, CI tests, documented performance/compatibility matrix.
**Exit:** `./gradlew clean build` and tests pass; representative worlds and mod combinations pass long soak tests.

Do not delete legacy execution until new paths pass the same golden suite. Use branch-based migration and selective flags to compare backends inside one build. Keep `main` releasable whenever possible.

## 7. Specific spike experiments before committing to a full rewrite

1. Benchmark current `particles()` list copy versus direct emitter traversal.
2. Replace expression-context hash lookups with slot arrays in a branch and measure at 10k/100k.
3. Benchmark CPU SoA particle update with matching motion against object-per-particle.
4. Prototype sprite rendering via instanced quad and compare with legacy vertex submission.
5. Prototype one GPU analytic effect for simple particles on GL3.2-capable hardware.
6. Probe GL4.3 and benchmark a minimal compute ping-pong state update; decide whether it offers material benefit.
7. Benchmark model quad caching + instancing versus per-particle baked-quad access.
8. Run visual comparisons and compatibility smoke tests after every spike.

No promising an ECS rewrite, GPU compute path or backend complexity in the final architecture until these spikes justify it.

## 8. External references

- Minecraft Particle Core: https://github.com/fzzyhmstrs/pc
- Photon editor: https://github.com/Low-Drag-MC/Photon
- Unity CPU vs GPU VFX: https://docs.unity.com/en-us/engine/6000.0/manual/visual-effects/particle-systems/choosing-your-particle-system
- Unreal Niagara CPU-only event handlers: https://dev.epicgames.com/documentation/unreal-engine/events-and-event-handlers-in-niagara-effects-for-unreal-engine
- Khronos GL compute shader (GL4.3): https://wikis.khronos.org/opengl/Compute_Dispatch
- Khronos instanced drawing (GL3.1): https://wikis.khronos.org/opengl/GLAPI/glDrawElementsInstanced
- Khronos per-instance attributes (GL3.3): https://wikis.khronos.org/opengl/GLAPI/glVertexAttribDivisor
- NeoForge profiling with Spark: https://docs.neoforged.net/user/docs/faq/

## 9. Decisions still to validate

- GPU capability baseline and mod/shader compatibility requirements.
- What `random` functions and mutable parameters are eligible for shader compilation.
- Collision event timing and how deferred events preserve current behavior.
- Which kinds of model particles are eligible for GPU instancing.
- Reference hardware and explicit acceptance thresholds for 1M particles.
- Whether GPU compute's extra maintenance is justified after the analytic path.

No gameplay or runtime source files have been changed as part of this RFC.
