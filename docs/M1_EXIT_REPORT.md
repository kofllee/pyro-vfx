# M1 Exit Report — compiled VFX programs and expressions

**Status:** M1 complete, based on measured client results and behavioral regression tests. Target: Minecraft 1.21.1 / NeoForge 21.1.229 / Java 21. **M2 has not started.**

## Compile-on-load program

`VfxDefinitionParser.parse` opens a per-thread compilation scope for the duration of each JSON definition load. Every numerical expression is parsed exactly once into the existing Java expression AST, with variable reads bound to integer slots in an immutable `VfxVariableLayout`. The completed definition contains an immutable `VfxProgram` with:

- an indexed symbol layout (all standard `effect.*`, `emitter.*`, `particle.*`, `spawn.*` variables and the effect's declared `param.*` names);
- compiled expression descriptions with their source, referenced variables, minimum dependency stage and `parameterDependent`, `randomCall`, `curveCall` flags;
- prepared emitter and event definitions and per-parameter numeric slot bindings.

The program is frozen with defensive `List.copyOf` / `Map.copyOf` snapshots. Active effects retain their compiled program across resource reload; new JSON definitions compile into new programs. Existing `pyro_vfx:1` JSON syntax and effect semantics are unchanged.

## Dependency model and safe caching

Each compiled numerical expression records a minimum dependency classification: `DEFINITION`, `EFFECT_START`, `EMITTER_START`, `PARTICLE_SPAWN`, `EFFECT_TICK`, `EMITTER_TICK` or `PARTICLE_TICK`. These are dependency descriptions, **not automatic cache instructions**: the location where an expression is actually invoked also matters. A separate flag indicates mutable `param.*` dependencies, random function calls, and named curves.

- **Pure mathematical constants** have no variable, curve, random or mutable-parameter dependencies; evaluate once at JSON load and store their numeric result.
- Constant RGB(A) and vec3 expression groups reuse their evaluated immutable values rather than allocate a new object every particle.
- Known `curve.name(x)` calls bind a direct immutable `VfxCurveDefinition` at compile time; unknown curves keep the old error-at-evaluation behavior.
- **Never cache `random.*` calls**; they retain their original RNG draw sequence, even with short-circuiting and nested functions.
- **Never cache mutable `param.*` expressions**; `set_param` updates both the instance's primitive parameter array and the currently executing expression scope, preserving same-tick event order and subsequent tick behavior.
- Other expressions continue to be evaluated at their original call sites. No speculative expression sharing that might reorder random draws or overwrite parameters was introduced.

## Hot-path changes

- `VfxExpressionContext`: index-based primitive `double[]` slots and presence masks; string-key maps are lazily used only in ad-hoc legacy contexts or unknown symbols. Parent scope shadowing and `set_param` lookup semantics are preserved.
- `ClientVfxInstance`: uses precompiled `VfxProgram` emitters/events, stores parameter values and slot indices in primitive arrays, avoiding hash map enumeration and `param.` string concatenation on every tick.
- `ClientVfxExpressionContexts`: standard variables write directly to known slot indices, without rebuilding dynamic symbol names or boxing values.
- `ClientVfxParticle.tick`: updates a single particle context for physics and appearance rather than creating two; the same context still observes updated position/velocity/rotation before rendering expressions.
- `VfxNumberExpression.evaluateDouble`: a primitive fast path used by motion, lifetime, spawn, collision and event expressions.
- Existing typed expression functions, curve interpolation, branching, random helpers, timing, emitter behavior and collision/event semantics are retained.

No SoA pool, batched renderer, GPU compute, shader backend or altered event scheduler was introduced: those belong to M2–M5.

## Verification

`./gradlew.bat clean build runGameTestServer --no-daemon --console=plain`: **20/20 NeoForge GameTests passed**.

New tests include compiled dependency classification, pure constant folding, direct curve binding and immutable expression program data, byte-for-byte equality of original and compiled expression results (including randomized sequences), immediate inherited parameter mutation and updates to live particle appearance from `set_param`.

Real Minecraft client: **three independent launches for each of five workloads**, plus separate client checks for `event_order`, `self_emit_collision`, `model_rotation`, `model_inheritance`, `reload_active`, `curves`, `events`, and `uv_animation`; all completed.

See `docs/benchmarks/m1-2026-11-02/README.md` for raw reports, GC/P95 and exact comparison checks. Each M0/M1 pair uses identical JSON SHA-256, fixed seed, observed particle count, Minecraft version, NVIDIA RTX 4090 Laptop GPU and 854x480 test resolution.

| Effect | M0 median CPU simulation | M1 median CPU simulation | Improvement | M0 tick allocation | M1 tick allocation |
|---|---:|---:|---:|---:|---:|
| sprite_1k | 1.028 ms | 0.117 ms | 8.8x | 6.42 MiB | 0.45 MiB |
| dynamic_1k | 1.070 ms | 0.122 ms | 8.8x | 6.56 MiB | 0.61 MiB |
| collision_1k | 1.424 ms | 0.450 ms | 3.2x | 7.52 MiB | 1.54 MiB |
| model_1k | 1.004 ms | 0.074 ms | 13.6x | 6.49 MiB | 0.53 MiB |
| sprite_10k | 10.227 ms | 0.512 ms | 20.0x | 64.17 MiB | 4.50 MiB |

The values are medians of 3 runs each. These measurements show a major improvement in this test environment, not a universal FPS guarantee. Frame-time P95 for sprite 10k was 22.42 ms at M0 and 10.88 ms at M1, though total frame timing is affected by Minecraft and driver behavior.

## M1 exit and M2 handoff

M1 exit criteria are met: a frozen compile-on-load program exists, all golden behavior tests pass, and representative real-client expression workloads show a repeated measurable improvement.

**Remaining optimization targets for M2:** primitive SoA/AoSoA particle storage; current object-per-particle state and primitive context arrays still allocate ~4.50 MiB/tick for 10k sprites. Use recorded allocation/collision/GC distributions as the M2 baseline. Shader compatibility and very high particle counts are separate later milestones.

The benchmark reports were captured with a working tree under development; their embedded Git SHA is insufficient alone to identify the final source. Keep the completion commit together with this document.
