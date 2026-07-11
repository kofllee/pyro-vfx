# Pyro VFX

Pyro VFX is an experimental JSON-driven visual effects runtime for Minecraft NeoForge.

It provides a reusable effect format with programmable emitters, expressions, curves, sprite particles, model particles, collision, events, triggers and resource reload support.

> **Project status: paused prototype**
>
> The current runtime is functional and demonstrates the intended architecture, but it is not production-ready. The main possible future direction is an in-game VFX editor built on top of the existing JSON format.

## Current scope

Pyro VFX currently includes:

- JSON effect definitions loaded from resource packs and mods;
- runtime parameters and command-line parameter overrides;
- numeric, vector and color expressions;
- named curves;
- effect and emitter lifetimes;
- effect, emitter and particle triggers;
- event-driven manual emission;
- point, sphere, box, line, disc, ring, cone and model spawn shapes;
- static, dynamic and parametric motion;
- block collision and collision-triggered events;
- dynamic and parametric rotation;
- Minecraft particle fallback rendering;
- custom sprite particles;
- custom, block, item and entity model particles;
- sprite UV regions and sprite-sheet animation;
- alpha and additive blending;
- development commands for playing and inspecting registered effects.

A detailed feature status is available in [docs/STATUS.md](docs/STATUS.md).

## Compatibility

- Minecraft: `1.21.1`
- Mod loader: NeoForge
- Java: `21`
- VFX definition format: `pyro_vfx:1`

The project currently targets the versions configured in `gradle.properties`.

## Resource location

VFX definitions are loaded from:

```text
assets/<namespace>/vfx/<effect_path>.json
```

Example:

```text
assets/example_mod/vfx/smoke/burst.json
```

The resulting effect id is:

```text
example_mod:smoke/burst
```

## Minimal effect

```json
{
  "format": "pyro_vfx:1",
  "metadata": {
    "author": "example",
    "description": "A minimal sprite burst."
  },
  "lifetime": {
    "mode": "once",
    "delay_ticks": 0,
    "active_ticks": 20
  },
  "emitters": [
    {
      "id": "main",
      "emitter_lifetime": {
        "mode": "once",
        "delay_ticks": 0,
        "active_ticks": 1
      },
      "spawn_amount": {
        "mode": "instant",
        "amount": 16
      },
      "spawn_shape": {
        "type": "sphere",
        "radius": 0.25,
        "edge_thickness": 0.0
      },
      "particle_lifetime": {
        "max_age_ticks": 30
      },
      "motion": {
        "mode": "dynamic",
        "dynamic": {
          "direction": "outward",
          "speed": 0.8,
          "acceleration": [0, 0.1, 0],
          "linear_drag": 1.5
        }
      },
      "rotation": {
        "mode": "none"
      },
      "render": {
        "type": "sprite",
        "facing": "camera",
        "environment_lighting": false,
        "sprite": {
          "texture": "minecraft:textures/particle/generic_0.png",
          "blend_mode": "alpha"
        },
        "color": [0.7, 0.7, 0.7, "1.0 - particle.normalized_age"],
        "scale": [
          "0.1 + particle.normalized_age * 0.3",
          "0.1 + particle.normalized_age * 0.3",
          1.0
        ]
      }
    }
  ]
}
```

## Development commands

```text
/pyrovfx list
/pyrovfx play <effect>
/pyrovfx play <effect> <parameters>
/pyrovfx play_at <effect> <position>
/pyrovfx play_at <effect> <position> <parameters>
/pyrovfx stop_all
```

Examples:

```text
/pyrovfx list
/pyrovfx play pyro_vfx:examples/basic_sprite
/pyrovfx play pyro_vfx:examples/curves
/pyrovfx play pyro_vfx:examples/event_sequence
/pyrovfx play_at pyro_vfx:examples/basic_sprite ~ ~1 ~
/pyrovfx stop_all
```

Command parameters use an NBT compound and must be numeric.

Minecraft client resource reload also reloads VFX definitions.


## Included examples

The repository includes example definitions under:

```text
src/main/resources/assets/pyro_vfx/vfx/examples/
```

Available effects:

```text
pyro_vfx:examples/basic_sprite
pyro_vfx:examples/curves
pyro_vfx:examples/event_sequence
pyro_vfx:examples/collision_sparks
pyro_vfx:examples/animated_sprite
pyro_vfx:examples/model_particle
pyro_vfx:examples/model_spawn_shape
```

See [docs/EXAMPLES.md](docs/EXAMPLES.md) for requirements and test commands.

## Documentation

- [Current project status](docs/STATUS.md)
- [JSON definition format](docs/JSON_FORMAT.md)
- [Expressions and curves](docs/EXPRESSIONS.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Performance notes](docs/PERFORMANCE.md)
- [Examples](docs/EXAMPLES.md)
- [Possible future development](docs/ROADMAP.md)

## Current limitations

The current prototype does not include:

- an in-game VFX editor;
- a public integration API;
- effect handles;
- attached effects;
- entity or projectile following;
- networking;
- effect-level profiling;
- global particle budgets;
- distance culling or LOD;
- stable performance guarantees for large particle counts;
- dedicated documentation tooling or JSON schema generation.

`travel_distance` and `travel_distance_looping` are accepted by the parser, but are not currently executed by the runtime.

`on_collision` is supported as a particle trigger. It is not implemented as a normal effect or emitter trigger.

## Building

Clone the repository and run:

```bash
./gradlew build
```

The built JAR is written to:

```text
build/libs/
```

## License

Pyro VFX is licensed under the MIT License. See [LICENSE](LICENSE).
