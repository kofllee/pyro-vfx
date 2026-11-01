# CPU runtime redesign — verification report

**Status:** A feature-routed dense CPU runtime has been integrated, benchmarked and regression-tested. This is a **hybrid data-oriented implementation**, not a claim that advanced model/collision/rotation effects have all been converted to primitive arrays.

Target: Minecraft 1.21.1 / NeoForge 21.1.229 / Java 21.

## What changed

The implementation and safety boundaries are documented in [CPU_RUNTIME_REDESIGN.md](CPU_RUNTIME_REDESIGN.md).

- `VfxDenseSpritePool`: resizable SoA arrays, sparse dynamic-motion channels, one reusable evaluation scope per emitter tick, no per-particle Java object for eligible static/dynamic sprites, order-preserving two-pointer compacting, released stale UV references and retained capacity for looping effects.
- `VfxSpriteParticleView`: render interface with one mutable cursor for dense particles and the original particle implementation for complex features; eliminates copying the entire effect particle list every rendered frame.
- `VfxEventCommandQueue`: bounded per-emitter collision event FIFO drained at each particle boundary. `set_param` and seeded RNG ordering remain synchronous; spawning back into the active emitter stages particles and appends them only after that emitter's update/compaction phase.
- Optimized legacy collision: cached mutable block-position scratch only for collision-enabled particles, and skip `BlockState.isAir()` before acquiring voxel collision shapes. Complex collision geometry and response remain unchanged.
- Explicit dev-only benchmark selector: `-PbenchLegacyCpu=true`. All ordinary effects continue to obey the per-effect safety budgets and recursion depth limits.
- World queries, effect runtime mutation, rendering and resource lifecycle remain client-thread-owned; parallelism is not enabled. A measured math-only parallelization probe is archived separately.

## Correctness evidence

- **23/23** NeoForge GameTests, including new dense/static and dynamic parity with the prior particle implementation (position, velocity, previous position, interpolation, scale, alpha, age, RNG consumption, lifetime), stable compaction order, sparse backend selection and collision FIFO parameter order.
- **5/5** Python benchmark comparator tests.
- **3 independent client starts × 5 workloads = 15** raw reports compared with M1; source JSON SHA-256, seed, particle count, sample window, Minecraft version, resolution and GPU are verified before the report is generated.
- **10/10** real-client smoke scenarios passed: `event_order`, `self_emit_collision`, `collision_no_events`, `budget_spike`, `model_rotation`, `model_inheritance`, `reload_active`, `curves`, `events`, `uv_animation`.
- A separate real-client start with **5/5** scenarios forced through the old CPU path also completed; JSON hashes, seeds and peak particle counts match the new backend. This is one A/B cross-check, not a new statistical sample.

## Performance: M1 → current CPU runtime

Actual Minecraft benchmark at 854×480 / RTX 4090 Laptop. Median of three client launches per scenario:

| Scenario | Simulation CPU M1 | Simulation CPU now | Δ | Java allocations per tick M1 | Java allocations now |
|---|---:|---:|---:|---:|---:|
| sprite_1k | 0.117 ms | 0.063 ms | −46.4% | 0.451 MiB | 0.002 MiB |
| dynamic_1k | 0.122 ms | 0.080 ms | −34.5% | 0.612 MiB | 0.086 MiB |
| collision_1k | 0.450 ms | 0.464 ms | +3.1% | 1.541 MiB | 1.450 MiB |
| model_1k | 0.074 ms | 0.074 ms | −0.3% | 0.528 MiB | 0.528 MiB |
| sprite_10k | 0.512 ms | 0.258 ms | −49.6% | 4.503 MiB | 0.002 MiB |

The CPU simulation regression in collision_1k (+3.1%) is recorded rather than hidden; the path remains semantically compatible and has lower allocations, but the measured CPU result is not an improvement. The sprite_1k P95 simulation rose (0.207 → 0.451 ms), while the 10k P95 improved (0.873 → 0.350 ms). Whole-frame P95 changes also include Minecraft/renderer/driver contributions.

Full comparable results and raw JSON are archived in [benchmarks/m2-2026-11-02](benchmarks/m2-2026-11-02/). Exact A/B forced-backend verification is in `LEGACY_AB.md`; isolated multithreading results are in `PARALLEL_PROBE.md`.

## Concurrency decision

Serial math is faster for 1k–100k in the isolated probe; 1M pure-math elements favor parallel (5.75×), but no Minecraft world query, shared expression RNG or mutable events are in that experiment. Keep client simulation single-threaded unless a safe immutable snapshot and per-particle deterministic RNG are introduced and proved by real client profiling. No claims about massive 100k/1M effect workloads or shader compatibility.

## Remaining boundaries

- Model particles, collision-enabled or event-enabled effects, rotations and environment-lit sprites use the precise legacy representation; they are **not** SoA yet. This is explicit capability routing, not silent loss of functionality.
- The current sprite renderer still performs CPU vertex submission, per-particle transforms and material selection. Rendering improvements and GPU resident particle storage belong to a later milestone.
- Very high density tests (100k/1M), multiplayer reload/dimension soak, long-term memory pressure, and shader/render-mod combinations are not established by these results.
- Reports were collected with local uncommitted source changes; the final source commit must be retained with this report to identify the corresponding code.

**Decision:** The functional dense sprite backend, bounded event/spawn pipeline, collision memory improvement, deterministic compatibility tests and quantified tradeoffs satisfy the agreed CPU-runtime scope while retaining deliberate fallbacks for unsupported features. A full SoA rewrite of arbitrary colliding models is *not* being misrepresented as complete.
