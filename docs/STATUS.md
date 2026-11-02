# Project Status

This document describes the current capabilities and limitations of Pyro VFX.

Pyro VFX is an actively developed experimental VFX engine. The M0–M4 CPU/GPU architecture and the M5 global-budget safety layer are implemented and have targeted tests; it is **not** yet a finished public tool or a production-reliability guarantee. See [M5 verification and outstanding limits](M5_DECISION_AND_VERIFICATION.md).

The most recent hardening adds opt-in/automatic admission budgets,
`metadata.priority` classes, asynchronous GPU timing and an optional NVIDIA
free-VRAM probe, plus repeatable client reload/soak fixtures. See
[`M6_PROGRESS.md`](M6_PROGRESS.md) and
[`COMPATIBILITY_MATRIX.md`](COMPATIBILITY_MATRIX.md). Passing local tests
does not establish AMD/Intel, shaderpack or multiplayer compatibility.
Recent Minecraft A/B benchmarks cover 100,000 GPU-resident unlit models,
shared model rotation, block/item model variants, sorted instanced
full-bright alpha sprites (including dense animated smoke), and GPU
appearance expressions/curves. Supported paths are selected independently
for simulation and rendering. World-lit alpha, translucent baked models,
animated entity renderers, stateful expression calls, mutable parameters,
world queries and unsupported GPU semantics retain CPU/Minecraft fallbacks.

## Stable prototype features

These systems are implemented and form the current core of the project.

### Resource loading

- VFX files are loaded from `assets/<namespace>/vfx/**/*.json`.
- Effect ids are derived from the resource path.
- Definitions are stored in a registry.
- Client resource reload rebuilds the registry.
- Invalid definitions are rejected and logged without crashing the entire reload.

### Definition format

- Required format identifier: `pyro_vfx:1`.
- Metadata.
- Effect lifetime.
- Named parameters.
- Named curves.
- Emitters.
- Named events.
- Effect-level triggers.

### Parameters

- Numeric parameter defaults.
- Parameter defaults may use expressions.
- Parameters are evaluated at effect startup.
- Command-provided numeric overrides.
- Runtime mutation through `set_param` events.
- Unknown command overrides are ignored with a warning.
- Unknown `set_param` targets are rejected by validation.

### Expressions

- Numeric literals.
- Arithmetic operators.
- Comparisons.
- Boolean operators.
- Conditional expressions.
- Math functions.
- Random functions.
- Named curve sampling.
- Effect, emitter, particle and spawn contexts.
- Expressions are compiled into immutable programs with indexed variable slots and dependency-based constant evaluation; unsupported dynamic features retain compatible CPU execution.

### Curves

Current curve types:

- `linear`
- `step`
- `catmull`
- `bezier`

Curves are referenced through:

```text
curve.<curve_id>(input)
```

### Effect lifetime

Current modes:

- `once`
- `looping`

Supported fields:

- `delay_ticks`
- `active_ticks`
- `sleep_ticks`
- `loops`

`loops: 0` means unlimited loops.

### Emitter lifetime

Current modes:

- `once`
- `looping`
- `manual`

Manual emitters do not spawn from lifetime timing and are intended for event-driven emission.

### Spawn amount

Current modes:

- `instant`
- `steady`
- `manual`

Steady emitters enforce their own `max_particles` limit.

### Spawn shapes

Current shapes:

- `point`
- `sphere`
- `box`
- `line`
- `disc`
- `ring`
- `cone`
- `model`

The model spawn shape samples cuboid elements from a baked model.

### Motion

Current modes:

- `static`
- `dynamic`
- `parametric`

Dynamic motion supports:

- custom, outward, inward, up and down directions;
- initial speed;
- acceleration;
- linear drag.

Parametric motion evaluates an offset from the particle spawn position.

### Collision

Current collision features:

- collision against block collision shapes;
- sphere-like collision represented through bounds;
- box collision;
- axis-based movement resolution;
- collision drag;
- bounciness;
- expiration on contact;
- collision position;
- particle `on_collision` events.

Collision is functional but should still be treated as prototype-quality for difficult cases such as high speed, repeated contact and complex block geometry.

### Rotation

Current modes:

- `none`
- `dynamic`
- `parametric`

Dynamic rotation supports:

- initial rotation;
- angular velocity;
- angular acceleration;
- angular drag.

### Render types

Current render types:

- `sprite`
- `model`

### Sprite rendering

- custom texture;
- alpha blending;
- additive blending;
- fullbright or environment lighting;
- facing modes;
- color expressions;
- scale expressions;
- full, static and animated UV modes;
- fixed frame rate or lifetime-stretched animation;
- looping and randomized starting frame.

### Model rendering

Current model sources:

- `custom`
- `block`
- `item`
- `entity`

Current model render layers:

- `solid`
- `cutout`
- `translucent`
- `additive`

### Events

Current event types:

- `emit`
- `sequence`
- `randomize`
- `set_param`

### Working triggers

Effect triggers:

- `on_creation`
- `timeline`
- `on_expiration`

Emitter triggers:

- `on_creation`
- `timeline`
- `on_expiration`

Particle triggers:

- `on_collision`

### Development commands

- `play`
- `play_at`
- `list`
- `stop_all`
- `backend` (active backend selection, GPU counters, global limits and overload diagnostics)

### Runtime execution and limits

- `VfxDenseParticlePool`: SoA CPU simulation for sprite and baked-model particles, including rotating, lit and event-free colliding variants. `VfxAnalyticParticlePool`: GPU birth-state simulation for eligible sprite and baked-model cohorts. Ordered event callbacks and entity models retain Legacy.
- GPU instancing for additive sprites, depth-sorted alpha sprites, and eligible baked 3D models. Fullbright alpha is depth-sorted across emitters; world-lit alpha and shader-mod pipelines use Minecraft RenderType fallback.
- GPU-resident closed-form motion for eligible additive sprites and opaque non-world-lit baked 3D models, optionally including shared constant-parameter rotation. For eligible GPU additive sprites, color and XY scale can be compiled from the existing expression parser to bounded GLSL, including birth random, age, supported immutable curves, position, previous position, velocity, speed and spawn position. No GPU Compute or arbitrary world-state expression execution.
- A 100k curve-heavy Minecraft A/B improved P95 frame intervals from 24.06 ms to 1.73 ms; a 100k motion-expression case improved from 26.62 ms to 1.23 ms in a single paired test. These are synthetic RTX 4090 Laptop results, not production guarantees. See [raw GPU expression reports](benchmarks/gpu-expressions-2026-11-02/README.md) and [entity-model investigation](benchmarks/entity-model-study-2026-11-02/README.md). A dedicated GPU expression reload scenario passes.
- **Default client configuration:** 120,000 live particles and 100,000 new spawns/tick per effect, 4,096 events/effect/tick; globally 240,000 live particles, 100,000 spawns/tick, 8,192 events/tick, 256 effects. Per-dimension defaults: GPU analytic 240k, CPU basic 120k, collisions 20k, models 120k. These are safety ceilings, not expected performance.
- **Existing TOML settings take precedence:** older `run/config/pyro_vfx-client.toml` files may retain deliberately reduced 20k/80k/10k limits. Do not overwrite or migrate the file merely to match current defaults; `VfxBudgetConfig` still uses the same section/key names, and NeoForge reads existing values.
- Adaptive admission measures CPU tick time and asynchronous GPU VFX time; sampled heap pressure supplements this only for substantial tracked VFX state. Unknown VRAM or JVM measurements never imply zero available memory.
- Dedicated developer-only heavy-count benchmark override; no automatic heavy override in production.
- Deterministic reject-new-work behavior under load, observable rejection counters and JSON benchmark telemetry.

## Experimental features

These features exist, but should not be described as stable production behavior.

- Entity model particles.
- Translucent model particles.
- Additive model rendering.
- Model spawn shape surface sampling.
- Bouncing particles.
- Repeated collision-triggered events.
- Large event graphs with nested sequence and randomize events.
- Large particle counts.
- Large numbers of model particles.
- Infinite looping effects.
- Heavy use of expressions on every particle tick.

## Parsed but not executed

The parser accepts these trigger types:

- `travel_distance`
- `travel_distance_looping`

The current effect and emitter runtime does not execute them.

They should not be used in normal effect definitions until runtime support is implemented.

## Context-specific behavior

### `on_collision`

`on_collision` should currently be used only in an emitter's `particle_triggers`.

The parser can represent the same enum value in other trigger scopes, but the runtime does not execute it as an effect or emitter trigger.

### Effect timeline

Effect `timeline` triggers use the global effect age.

They do not restart for every lifetime loop.

### Emitter timeline

Emitter `timeline` triggers use the emitter age.

Manual emitters do not run their normal lifetime triggers.

### Particle ownership

Pyro VFX owns the lifetime and simulation of every particle that it creates.
The previous `minecraft_particle` renderer is removed; this does not remove
Minecraft `RenderType` rendering as a compatibility fallback.

## Not implemented

- In-game VFX editor.
- Effect browser.
- Emitter inspector.
- Visual curve editor.
- Event timeline editor.
- World-space shape gizmos.
- Public integration API.
- Play-by-id API independent of client internals.
- Effect handles.
- Stop-by-handle.
- Attached effects.
- Entity attachment.
- Projectile attachment.
- Block attachment lifecycle.
- Custom transform providers.
- Server-to-client networking.
- Visibility radius packets.
- Per-effect performance statistics.
- Per-emitter performance statistics.
- Fully cost-weighted per-render-type GPU/VRAM budgets (a global quality
  controller and per-effect quality priority are implemented).
- Distance culling.
- Full quality-tier and distance-based spawn LOD (render-only size LOD is implemented).
- Dedicated reload command.
- In-game validation report.
- JSON schema.
- Automated visual regression tests.
- Performance guarantees.

## Known technical concerns

- `ClientVfxInstance.particles()` creates a new combined list when called.
- Compatibility/complex particle rendering still uses CPU transforms; the fast sprite path avoids a combined-particle-list render allocation.
- Collision/model workloads remain substantially more expensive than simple GPU sprites.
- Global limits count Pyro-managed particles, not the lifetime count of particles delegated to vanilla Minecraft.
- Collision can trigger repeatedly while a particle remains in contact.
- M0–M4 real-client benchmark reports are archived in `docs/benchmarks/`; M5 checks and limitations are recorded in `M5_DECISION_AND_VERIFICATION.md`.
- Automated tests and CI exist; cross-driver/modpack and long-running mixed-world soak validation are still missing.

## Recommended repository label

Use the following wording consistently:

```text
Active experimental VFX engine — not a production release
```

Avoid describing the current version as a finished library, released tool or production-ready particle engine.
