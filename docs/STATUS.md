# Project Status

This document describes the current capabilities and limitations of Pyro VFX.

Pyro VFX is currently a paused experimental prototype. It contains a functional client-side VFX runtime, but it is not a finished public tool or production dependency.

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
- Expressions are parsed into executable nodes before runtime evaluation.

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

- `minecraft_particle`
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

### Minecraft particle fallback

`minecraft_particle` delegates spawning to Minecraft's particle system. Pyro VFX does not keep those particles in its own particle list after spawning.

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
- Global particle budgets.
- Per-render-type budgets.
- Frustum culling.
- Distance culling.
- LOD.
- Dedicated reload command.
- In-game validation report.
- JSON schema.
- Automated visual regression tests.
- Performance guarantees.

## Known technical concerns

- `ClientVfxInstance.particles()` creates a new combined list when called.
- Rendering walks every active instance and every returned particle every frame.
- Sprite rendering performs per-particle transform work through `PoseStack`.
- Expression contexts are built repeatedly during runtime.
- There is no global cap on active effects or custom particles.
- Collision can trigger repeatedly while a particle remains in contact.
- No profiling data is included in the repository.
- The project does not currently have CI on the public branch.

## Recommended repository label

Use the following wording consistently:

```text
Paused experimental prototype
```

Avoid describing the current version as a finished library, released tool or production-ready particle engine.
