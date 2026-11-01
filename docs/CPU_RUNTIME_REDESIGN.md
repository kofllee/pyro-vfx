# CPU particle runtime — data-oriented implementation

**Status:** Implemented for collision-free sprite features, with exact-feature legacy fallbacks. Minecraft 1.21.1, NeoForge 21.1.229, Java 21. The authored `pyro_vfx:1` surface and compiled M1 expression programs remain unchanged.

## Data layout and selection

`VfxDenseSpritePool` holds dense, reused primitive arrays (SoA) of current positions, spawn positions, lifetime, age, per-particle seed, current/previous scale and color, plus UV descriptors. Dynamic motion owns *additional* velocity and previous-position channels; static motion does not allocate these arrays. The pool grows geometrically and compacts live particles in insertion order with a two-pointer pass. It clears vacated UV references so expired particle animation state can be collected.

Backend selection is per-emitter and made once during initialization:

- **Dense CPU:** sprite rendering, static or noncolliding dynamic motion, no dynamic/parametric rotation, no particle collision trigger, no environment-lighting lookup.
- **Precise legacy CPU:** model particles, collision-enabled motion, particle collision events, rotation, environment-lighted sprites, other unsupported combinations.

Unsupported features never disappear or silently change behavior: they take the already-tested CPU fallback. Global/per-effect spawn and event budgets introduced earlier still apply.

`VfxSpriteParticleView` decouples sprite rendering from particle object storage. The dense pool presents a **single reusable render cursor**; no per-particle render facade is allocated. `VfxRenderer` traverses each emitter's pool/list directly, avoiding the old temporary combined `instance.particles()` list every frame. The existing public inspection API still returns `ClientVfxParticle` snapshots **on demand** for dense particles, but rendering, particle counting and lifecycle termination avoid this expensive compatibility path.

## Update pipeline and events

1. Advance effect scheduling and update parameters in slot-backed expression contexts.
2. For each emitter, execute creation/timeline/expiry triggers in the original order.
3. Reserve bounded spawn work; initialize particles, appending to the dense pool or a legacy list.
4. Update the dense pool as a batch with one reusable scope for expressions and order-preserving in-place compaction. Expressions using `random.*` still draw sequentially in spawn order.
5. Update precise legacy particles on the client thread. For collision triggers, enqueue `eventId` and caller position in a bounded FIFO then **drain at the particle boundary** before advancing to the next particle. This preserves the observable immediate `set_param` and RNG/event order, rather than incorrectly delaying the event to the following tick.
6. During an emitter's legacy particle traversal, any reentrant `emit` targeting that same emitter is placed in `pendingParticles`, not appended to the iterated list. After updating/compacting expired particles, flush pending spawns in insertion order. They begin simulation next tick, as in the established M0 contract.
7. Publish the resulting live arrays/lists for direct renderer traversal on Minecraft's client thread.

All event execution remains bounded by the existing 4,096-node/tick budget and recursion depth 16; all spawns use the same effect-level reservation budget (20,000/tick ordinary cap). The queue itself is bounded and reused, avoiding per-collision command allocations.

## Collision and memory changes

- A cached `MutableBlockPos` scratch cursor belongs only to legacy particles with collision enabled; simple sprites don't allocate it.
- Air blocks are skipped before requesting collision shapes. Non-air shapes still use the original collision AABB and per-axis response, preserving bouncing and triggers.
- Dense pools keep capacity for reuse across loops. Their expired UV references are released; when the effect ends, the pool and all arrays are freed with that effect. Memory and batch work remain bounded by the existing per-effect spawn/active-particle limit, except for explicit heavy benchmark opt-in.
- Legacy lists compact expired particles only when necessary. During iteration, spawns are queued rather than changing the active collection.

## Thread safety and parallelism

The *current production implementation remains single-threaded for simulation and rendering* and does not submit world queries to background workers. This is intentional:

- Minecraft block states, collision shapes, resource/model access and `ClientLevel` are not safe to query from arbitrary worker threads.
- Expression evaluation can consume a shared seeded `RandomSource`; splitting particles over threads changes RNG order without a deterministically indexed random strategy.
- `set_param` must be visible within the same tick; many expressions are mutable and have ordered event side effects.
- The dense renderer uses a reused mutable cursor; it must not race with simulation or resource reload.

A **math-only** isolated primitive-array experiment is provided in `tools/ParallelKernelProbe.java`; it measures serial vs JDK parallel-stream chunk processing for 1k/10k/100k/1M elements, without world/RNG/events. It cannot establish runtime thread safety or guarantee overall client performance. Only add multithreading after an immutable per-tick snapshot, per-particle deterministic RNG, a published render snapshot and measured break-even point are established.

## Reproducibility and acceptance

Headless: `./gradlew.bat clean build runGameTestServer --no-daemon --console=plain` plus `python tools/test_compare_bench.py`.

Client: `./gradlew.bat runClientBench '-PbenchScenario=sprite_1k,dynamic_1k,collision_1k,model_1k,sprite_10k'` (three independent starts). For an A/B safety switch, add `'-PbenchLegacyCpu=true'` to force the old backend without changing JSON. The report JSON records `cpuParticleBackend`.

Raw M1-to-SoA benchmark results, exact input parity checks and all ten client smoke tests are archived in `docs/benchmarks/m2-2026-11-02/`. Three client launches show the static sprite 10k simulation median improving **0.512 → 0.258 ms** (−49.6%) and HotSpot tick allocations **4.503 → about 0.002 MiB/tick**; 1k dynamic sprite simulation improves **0.122 → 0.080 ms** and tick allocations **0.612 → 0.086 MiB**. Model is unchanged by design, collision CPU median changes **0.450 → 0.464 ms** (+3.1%; not a demonstrated CPU win), and static sprite 1k P95 simulation is higher, which should be profiled rather than concealed. The renderer itself remains the old vertex-submission path pending GPU-stage work.

**Scope boundary:** not every particle category uses SoA yet; model/complex rotation and collision/event particles intentionally use exact-feature CPU fallback. This is a hybrid architectural deliverable, not a claim that all effects now run on a fully parallelized ECS or that 100k/1M performance has been proven.
