# Pyro VFX JSON Definition Format

This document describes the current `pyro_vfx:1` format.

It intentionally separates working runtime behavior from parsed-only fields and future plans.

## 1. Resource path

Definitions are loaded from:

```text
assets/<namespace>/vfx/<effect_path>.json
```

Example file:

```text
assets/example_mod/vfx/smoke/burst.json
```

Effect id:

```text
example_mod:smoke/burst
```

A root-level `id` field is not used. The id comes from the resource location.

## 2. Minimal definition

```json
{
  "format": "pyro_vfx:1",
  "emitters": []
}
```

`format` and `emitters` are required.

Recommended minimal definition:

```json
{
  "format": "pyro_vfx:1",
  "metadata": {
    "author": "example",
    "description": "A small example effect."
  },
  "lifetime": {
    "mode": "once",
    "delay_ticks": 0,
    "active_ticks": 20
  },
  "emitters": []
}
```

## 3. Root fields

```text
format       required string
metadata     optional object
lifetime     optional object
parameters   optional object
curves       optional object
events       optional object
triggers     optional array
emitters     required array
```

Full root shape:

```json
{
  "format": "pyro_vfx:1",
  "metadata": {},
  "parameters": {},
  "curves": {},
  "lifetime": {},
  "events": {},
  "triggers": [],
  "emitters": []
}
```

## 4. Metadata

```json
"metadata": {
  "author": "example",
  "description": "Description for developers and future editor tooling."
}
```

Current fields:

```text
author
description
```

Metadata does not affect runtime behavior.

## 5. Value forms

### Number

A numeric field accepts either a JSON number:

```json
"speed": 1.25
```

or an expression string:

```json
"speed": "0.5 + particle.random * 1.5"
```

### Vec3

A vector is a three-element array. Every element may be a number or expression string.

```json
"acceleration": [0, -4.0, 0]
```

```json
"offset": [
  "math.sin(effect.age * 0.1) * 0.25",
  0.5,
  0
]
```

### Color

A color may be a hex string:

```json
"color": "#FFAA44"
```

```json
"color": "#FFAA44CC"
```

or an RGBA expression array:

```json
"color": [
  1.0,
  "0.5 + particle.random * 0.2",
  0.2,
  "1.0 - particle.normalized_age"
]
```

RGBA arrays use the `0.0..1.0` range.

## 6. Parameters

Parameters are named numeric values stored per effect instance.

```json
"parameters": {
  "power": 1.0,
  "amount": "8 + effect.random * 4"
}
```

Parameter defaults are evaluated at effect startup.

Use them through:

```text
param.<id>
```

Example:

```json
"amount": "12 + param.power * 8"
```

Command override:

```text
/pyrovfx play example_mod:effect {power:2.0}
```

Only numeric NBT values are accepted by the command.

Unknown command parameters are ignored with a warning.

## 7. Curves

Curves are declared at the root:

```json
"curves": {
  "fade": {
    "type": "linear",
    "points": [
      [0.0, 1.0],
      [1.0, 0.0]
    ]
  }
}
```

Current curve types:

```text
linear
step
catmull
bezier
```

Sample a curve from an expression:

```text
curve.fade(particle.normalized_age)
```

Example:

```json
"scale": [
  "curve.size(particle.normalized_age)",
  "curve.size(particle.normalized_age)",
  1.0
]
```

Curve points are sorted by their x coordinate at load time.

See [EXPRESSIONS.md](EXPRESSIONS.md) for more detail.

## 8. Effect lifetime

### Once

```json
"lifetime": {
  "mode": "once",
  "delay_ticks": 0,
  "active_ticks": 40
}
```

### Looping

```json
"lifetime": {
  "mode": "looping",
  "delay_ticks": 0,
  "active_ticks": 20,
  "sleep_ticks": 10,
  "loops": 3
}
```

Fields:

```text
mode
delay_ticks
active_ticks
sleep_ticks
loops
```

Modes:

```text
once
looping
```

`loops: 0` means unlimited loops.

Effect timeline triggers use global effect age and do not restart for every loop.

## 9. Events

Events are named actions:

```json
"events": {
  "emit_smoke": {
    "type": "emit",
    "emitter": "smoke"
  },
  "flash_then_smoke": {
    "type": "sequence",
    "events": ["emit_flash", "emit_smoke"]
  },
  "random_spark": {
    "type": "randomize",
    "events": ["spark_a", "spark_b", "spark_c"]
  },
  "increase_power": {
    "type": "set_param",
    "param": "power",
    "value": 2.0
  }
}
```

Current event types:

### `emit`

```json
{
  "type": "emit",
  "emitter": "manual_emitter"
}
```

Runs a manual spawn on the referenced emitter.

### `sequence`

```json
{
  "type": "sequence",
  "events": ["first", "second", "third"]
}
```

Runs the referenced events in order during the same tick.

### `randomize`

```json
{
  "type": "randomize",
  "events": ["choice_a", "choice_b", "choice_c"]
}
```

Runs one referenced event.

### `set_param`

```json
{
  "type": "set_param",
  "param": "power",
  "value": "param.power + 1"
}
```

Changes a declared runtime parameter.

## 10. Triggers

Trigger object:

```json
{
  "type": "timeline",
  "time_ticks": 5,
  "event": "emit_smoke"
}
```

Every trigger references a named root event.

### Working effect trigger types

```text
on_creation
timeline
on_expiration
```

Examples:

```json
{
  "type": "on_creation",
  "event": "start"
}
```

```json
{
  "type": "timeline",
  "time_ticks": 10,
  "event": "middle"
}
```

```json
{
  "type": "on_expiration",
  "event": "finish"
}
```

### Parsed-only trigger types

```text
travel_distance
travel_distance_looping
```

The parser accepts:

```json
{
  "type": "travel_distance",
  "distance": 1.0,
  "event": "trail_puff"
}
```

but the current runtime does not execute it.

### Collision trigger scope

`on_collision` is currently useful only inside an emitter's `particle_triggers`.

## 11. Emitter structure

```json
{
  "id": "main",
  "triggers": [],
  "particle_triggers": [],
  "emitter_lifetime": {},
  "spawn_amount": {},
  "offset": [0, 0, 0],
  "spawn_shape": {},
  "particle_lifetime": {},
  "motion": {},
  "rotation": {},
  "render": {}
}
```

Fields:

```text
id                  optional, defaults to emitter_<index>
triggers            optional
particle_triggers   optional
emitter_lifetime    optional
spawn_amount        optional
offset              optional
spawn_shape         required
particle_lifetime   optional
motion              optional
rotation            optional
render              required
```

Use explicit emitter ids whenever events reference emitters.

Emitter ids must be unique.

## 12. Emitter lifetime

### Once

```json
"emitter_lifetime": {
  "mode": "once",
  "delay_ticks": 0,
  "active_ticks": 1
}
```

### Looping

```json
"emitter_lifetime": {
  "mode": "looping",
  "delay_ticks": 0,
  "active_ticks": 5,
  "sleep_ticks": 5,
  "loops": 4
}
```

### Manual

```json
"emitter_lifetime": {
  "mode": "manual"
}
```

Modes:

```text
once
looping
manual
```

Manual emitters do not run their normal lifetime triggers and do not spawn automatically.

## 13. Emitter triggers

Emitter triggers use the same object format as effect triggers.

Working types:

```text
on_creation
timeline
on_expiration
```

Example:

```json
"triggers": [
  {
    "type": "timeline",
    "time_ticks": 5,
    "event": "secondary_burst"
  }
]
```

## 14. Particle triggers

Current working particle trigger:

```text
on_collision
```

Example:

```json
"particle_triggers": [
  {
    "type": "on_collision",
    "event": "impact_puff"
  }
]
```

Requirements:

- particle motion collision must be enabled;
- the particle must collide during that tick;
- the event runs at the reported collision position.

A particle that remains in contact may trigger again on later collision ticks.

## 15. Spawn amount

### Instant

```json
"spawn_amount": {
  "mode": "instant",
  "amount": 24
}
```

Spawns one burst.

For looping emitters, an instant burst happens at the start of every emitter cycle.

### Steady

```json
"spawn_amount": {
  "mode": "steady",
  "rate": 20,
  "max_particles": 256
}
```

`rate` is particles per second.

`max_particles` limits currently alive custom particles in that emitter.

### Manual

```json
"spawn_amount": {
  "mode": "manual",
  "amount": 16
}
```

Manual spawn is called by an `emit` event.

Modes:

```text
instant
steady
manual
```

## 16. Spawn shapes

### Point

```json
"spawn_shape": {
  "type": "point"
}
```

### Sphere

```json
"spawn_shape": {
  "type": "sphere",
  "radius": 0.5,
  "edge_thickness": 0.0
}
```

### Box

```json
"spawn_shape": {
  "type": "box",
  "half_extents": [0.5, 0.5, 0.5],
  "edge_thickness": 0.0
}
```

### Line

```json
"spawn_shape": {
  "type": "line",
  "length": 2.0,
  "axis": [0, 1, 0]
}
```

### Disc

```json
"spawn_shape": {
  "type": "disc",
  "radius": 1.0,
  "axis": [0, 1, 0],
  "edge_thickness": 0.0
}
```

### Ring

```json
"spawn_shape": {
  "type": "ring",
  "radius": 1.0,
  "inner_radius": 0.8,
  "axis": [0, 1, 0]
}
```

### Cone

```json
"spawn_shape": {
  "type": "cone",
  "radius": 0.5,
  "height": 1.5,
  "axis": [0, 1, 0],
  "edge_thickness": 0.0
}
```

### Model

```json
"spawn_shape": {
  "type": "model",
  "model": "example_mod:vfx/stone_chunk",
  "scale": [1.0, 1.0, 1.0],
  "edge_thickness": 0.0
}
```

Model shape sampling uses cuboid elements from a baked custom model.

This path is experimental.

## 17. Particle lifetime

```json
"particle_lifetime": {
  "max_age_ticks": 40
}
```

Expression example:

```json
"particle_lifetime": {
  "max_age_ticks": "20 + particle.random * 30"
}
```

The value is evaluated at particle spawn and then fixed for that particle.

## 18. Motion

Modes:

```text
static
dynamic
parametric
```

### Static

```json
"motion": {
  "mode": "static"
}
```

### Dynamic

```json
"motion": {
  "mode": "dynamic",
  "dynamic": {
    "direction": "outward",
    "custom_direction": [0, 1, 0],
    "speed": 1.5,
    "acceleration": [0, -4.0, 0],
    "linear_drag": 0.5
  }
}
```

Direction modes:

```text
custom
outward
inward
up
down
```

Units:

```text
speed          blocks per second
acceleration   blocks per second squared
linear_drag    exponential damping coefficient per second
```

Gravity is represented through acceleration:

```json
"acceleration": [0, -9.8, 0]
```

### Parametric

```json
"motion": {
  "mode": "parametric",
  "parametric": {
    "offset": [
      "math.sin(particle.age * 0.2) * 0.25",
      "particle.age * 0.02",
      0
    ],
    "direction": [0, 1, 0]
  }
}
```

Parametric position is evaluated relative to the particle spawn position.

## 19. Collision

Collision is nested under `motion`.

```json
"motion": {
  "mode": "dynamic",
  "dynamic": {
    "direction": "outward",
    "speed": 2.0,
    "acceleration": [0, -4.0, 0],
    "linear_drag": 0.4
  },
  "collision": {
    "collide": true,
    "collision_type": "sphere",
    "collision_size": [0.04, 0.04, 0.04],
    "collision_drag": 1.0,
    "bounciness": 0.2,
    "expire_on_contact": false
  }
}
```

Fields:

```text
collide
collision_type
collision_size
collision_drag
bounciness
expire_on_contact
```

Current collision types:

```text
sphere
box
```

Current behavior:

- collision is tested against block collision shapes;
- movement is resolved by axis;
- velocity may be reflected;
- collision drag reduces velocity;
- `expire_on_contact` immediately kills the particle;
- the collision position is available to particle triggers.

Collision is prototype-quality and not a complete rigid-body simulation.

## 20. Rotation

Modes:

```text
none
dynamic
parametric
```

### None

```json
"rotation": {
  "mode": "none"
}
```

### Dynamic

```json
"rotation": {
  "mode": "dynamic",
  "dynamic": {
    "start_rotation": [0, 0, 0],
    "angular_velocity": [0, 180, 0],
    "angular_acceleration": [0, 0, 0],
    "angular_drag": 0.5
  }
}
```

Angular velocity uses degrees per second.

Angular acceleration uses degrees per second squared.

### Parametric

```json
"rotation": {
  "mode": "parametric",
  "parametric": {
    "rotation": [0, "particle.age * 8", 0]
  }
}
```

## 21. Render types

Current render types:

```text
minecraft_particle
sprite
model
```

## 22. Minecraft particle rendering

```json
"render": {
  "type": "minecraft_particle",
  "minecraft_particle": {
    "particle": "minecraft:smoke"
  }
}
```

This delegates spawning to Minecraft.

Pyro VFX does not retain or render the resulting vanilla particle itself.

## 23. Sprite rendering

```json
"render": {
  "type": "sprite",
  "facing": "camera",
  "environment_lighting": false,
  "sprite": {
    "texture": "example_mod:textures/vfx/smoke.png",
    "blend_mode": "alpha",
    "uv": {
      "mode": "full"
    }
  },
  "color": "#777777CC",
  "scale": [1.0, 1.0, 1.0]
}
```

Facing modes:

```text
camera
camera_horizontal
world_x
world_y
world_z
velocity
```

Blend modes:

```text
alpha
additive
```

Lighting:

```text
environment_lighting: false   fullbright
environment_lighting: true    world lighting
```

## 24. Sprite UV modes

### Full

```json
"uv": {
  "mode": "full"
}
```

### Static

```json
"uv": {
  "mode": "static",
  "texture_size": [64, 64, 0],
  "uv_start": [16, 0, 0],
  "uv_size": [16, 16, 0]
}
```

### Animated

```json
"uv": {
  "mode": "animated",
  "texture_size": [64, 64, 0],
  "uv_start": [0, 0, 0],
  "uv_size": [16, 16, 0],
  "uv_step": [16, 0, 0],
  "frame_count": 4,
  "fps": 20,
  "stretch_to_lifetime": false,
  "loop": false,
  "random_start_frame": false
}
```

UV modes:

```text
full
static
animated
```

## 25. Model rendering

```json
"render": {
  "type": "model",
  "environment_lighting": true,
  "model": {
    "source": "custom",
    "model": "example_mod:vfx/stone_chunk",
    "block_state": "",
    "render_layer": "cutout"
  },
  "color": "#FFFFFFFF",
  "scale": [0.25, 0.25, 0.25]
}
```

Model sources:

```text
custom
block
item
entity
```

Render layers:

```text
solid
cutout
translucent
additive
```

### Custom model

Expected model resource:

```text
assets/<namespace>/models/vfx/<name>.json
```

Example:

```json
"model": {
  "source": "custom",
  "model": "example_mod:vfx/stone_chunk",
  "render_layer": "cutout"
}
```

### Block model

```json
"model": {
  "source": "block",
  "model": "minecraft:oak_leaves",
  "block_state": "minecraft:oak_leaves[persistent=true]",
  "render_layer": "cutout"
}
```

### Item model

```json
"model": {
  "source": "item",
  "model": "minecraft:paper",
  "render_layer": "cutout"
}
```

### Entity model

```json
"model": {
  "source": "entity",
  "model": "minecraft:creeper",
  "render_layer": "solid"
}
```

Entity model particles are experimental and are not recommended as the default path for real effects.

## 26. Evaluation timing

The runtime evaluates fields at different stages.

| Field | Evaluation |
|---|---|
| parameter default | effect start |
| command parameter override | effect start after defaults |
| effect lifetime | effect start |
| emitter lifetime | emitter start |
| emitter offset | effect start for construction, then effect tick |
| instant amount | emitter start |
| steady rate | emitter tick |
| max particles | emitter start |
| spawn shape | particle spawn |
| particle lifetime | particle spawn |
| initial direction and speed | particle spawn |
| acceleration and drag | particle tick |
| collision fields | particle tick |
| initial rotation and angular velocity | particle spawn |
| angular acceleration and drag | particle tick |
| parametric rotation | particle tick |
| render color and scale | particle tick |
| UV setup | particle spawn |
| model source and render layer | definition |

## 27. Full reference skeleton

```json
{
  "format": "pyro_vfx:1",
  "metadata": {
    "author": "",
    "description": ""
  },
  "parameters": {
    "power": 1.0
  },
  "curves": {
    "curve_id": {
      "type": "linear",
      "points": [
        [0.0, 0.0],
        [1.0, 1.0]
      ]
    }
  },
  "lifetime": {
    "mode": "once",
    "delay_ticks": 0,
    "active_ticks": 20,
    "sleep_ticks": 0,
    "loops": 1
  },
  "events": {
    "event_id": {
      "type": "emit",
      "emitter": "emitter_id"
    }
  },
  "triggers": [
    {
      "type": "on_creation",
      "event": "event_id"
    }
  ],
  "emitters": [
    {
      "id": "emitter_id",
      "triggers": [],
      "particle_triggers": [],
      "emitter_lifetime": {
        "mode": "once",
        "delay_ticks": 0,
        "active_ticks": 1,
        "sleep_ticks": 0,
        "loops": 1
      },
      "spawn_amount": {
        "mode": "instant",
        "amount": 1,
        "rate": 20,
        "max_particles": 256
      },
      "offset": [0, 0, 0],
      "spawn_shape": {
        "type": "point"
      },
      "particle_lifetime": {
        "max_age_ticks": 20
      },
      "motion": {
        "mode": "static",
        "dynamic": {
          "direction": "custom",
          "custom_direction": [0, 1, 0],
          "speed": 0,
          "acceleration": [0, 0, 0],
          "linear_drag": 0
        },
        "parametric": {
          "offset": [0, 0, 0],
          "direction": [0, 1, 0]
        },
        "collision": {
          "collide": false,
          "collision_type": "sphere",
          "collision_size": [0.05, 0.05, 0.05],
          "collision_drag": 0,
          "bounciness": 0,
          "expire_on_contact": false
        }
      },
      "rotation": {
        "mode": "none",
        "dynamic": {
          "start_rotation": [0, 0, 0],
          "angular_velocity": [0, 0, 0],
          "angular_acceleration": [0, 0, 0],
          "angular_drag": 0
        },
        "parametric": {
          "rotation": [0, 0, 0]
        }
      },
      "render": {
        "type": "sprite",
        "facing": "camera",
        "environment_lighting": false,
        "sprite": {
          "texture": "minecraft:textures/particle/generic_0.png",
          "blend_mode": "alpha",
          "uv": {
            "mode": "full",
            "texture_size": [16, 16, 0],
            "uv_start": [0, 0, 0],
            "uv_size": [16, 16, 0],
            "uv_step": [16, 0, 0],
            "frame_count": 1,
            "fps": 0,
            "stretch_to_lifetime": false,
            "loop": false,
            "random_start_frame": false
          }
        },
        "model": {
          "source": "custom",
          "model": "minecraft:stone",
          "block_state": "",
          "render_layer": "cutout"
        },
        "color": "#FFFFFFFF",
        "scale": [1, 1, 1]
      }
    }
  ]
}
```

Only fields required by the selected modes need to be present.

## 28. Parsed-only and experimental summary

Parsed but not executed:

```text
travel_distance
travel_distance_looping
```

Current but experimental:

```text
entity model source
translucent model particles
additive model particles
model spawn shape
collision bounce behavior
large particle counts
```
