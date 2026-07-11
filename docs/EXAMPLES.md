# Included Examples

Pyro VFX includes ready-to-use example definitions under:

```text
src/main/resources/assets/pyro_vfx/vfx/examples/
```

## `basic_sprite.json`

Effect id:

```text
pyro_vfx:examples/basic_sprite
```

Demonstrates:

- sprite rendering;
- instant spawning;
- sphere spawn shape;
- dynamic outward motion;
- per-particle random lifetime;
- alpha fading;
- scale growth.

Run:

```text
/pyrovfx play pyro_vfx:examples/basic_sprite
```

## `curves.json`

Effect id:

```text
pyro_vfx:examples/curves
```

Demonstrates:

- root-level named curves;
- Catmull interpolation;
- linear interpolation;
- curve sampling from expressions;
- curve-driven scale and opacity.

Run:

```text
/pyrovfx play pyro_vfx:examples/curves
```

## `event_sequence.json`

Effect id:

```text
pyro_vfx:examples/event_sequence
```

Demonstrates:

- root events;
- `on_creation` trigger;
- `sequence` event;
- manual emitters;
- additive flash and smoke emission.

Run:

```text
/pyrovfx play pyro_vfx:examples/event_sequence
```

## `collision_sparks.json`

Effect id:

```text
pyro_vfx:examples/collision_sparks
```

Demonstrates:

- dynamic spark motion;
- block collision;
- `expire_on_contact`;
- particle `on_collision` trigger;
- event-driven impact particles.

Run:

```text
/pyrovfx play pyro_vfx:examples/collision_sparks
```

## `animated_sprite.json`

Effect id:

```text
pyro_vfx:examples/animated_sprite
```

Demonstrates:

- animated UV configuration;
- lifetime-stretched animation;
- disc spawn shape.

Run:

```text
/pyrovfx play pyro_vfx:examples/animated_sprite
```

The included definition uses a one-frame vanilla particle texture as a valid format example. Replace the texture and UV values with a real sprite sheet to demonstrate visible animation.

## `model_particle.json`

Effect id:

```text
pyro_vfx:examples/model_particle
```

Demonstrates:

- custom model particle rendering;
- dynamic rotation;
- model particle collision;
- bouncing debris.

Required model:

```text
pyro_vfx:vfx/ember_cube
```

Run:

```text
/pyrovfx play pyro_vfx:examples/model_particle
```

## `model_spawn_shape.json`

Effect id:

```text
pyro_vfx:examples/model_spawn_shape
```

Demonstrates:

- sampling spawn positions from a baked model;
- model surface sampling;
- shell sampling through `edge_thickness`;
- delayed emitter activation.

Required model:

```text
pyro_vfx:vfx/stone_chunk
```

Run:

```text
/pyrovfx play pyro_vfx:examples/model_spawn_shape
```

## Validation

Reload client resources:

```text
F3 + T
```

Then verify the registered effects:

```text
/pyrovfx list
```

Expected ids:

```text
pyro_vfx:examples/basic_sprite
pyro_vfx:examples/curves
pyro_vfx:examples/event_sequence
pyro_vfx:examples/collision_sparks
pyro_vfx:examples/animated_sprite
pyro_vfx:examples/model_particle
pyro_vfx:examples/model_spawn_shape
```

The complete field reference is available in [JSON_FORMAT.md](JSON_FORMAT.md).
