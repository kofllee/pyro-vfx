# Pyro VFX

**JSON-driven visual effects runtime for Minecraft 1.21.1 / NeoForge 21.1.229 / Java 21.** Pyro VFX creates sprites and Minecraft-style 3D model particles from resource-pack definitions, with expressions, curves, events, collisions, emitter shapes, transform attachments and networking. It is an **experimental engine**, not a production-certified release. The next development phase is a visual editor; no editor is implemented yet.

## Start here

| Need | Read |
|---|---|
| Capabilities, verified scope, limitations | [Status](docs/STATUS.md) |
| Runtime, CPU/GPU rendering, transforms, architecture decisions | [Architecture](docs/ARCHITECTURE.md) |
| JSON fields and expressions | [JSON format](docs/JSON_FORMAT.md), [expressions and curves](docs/EXPRESSIONS.md) |
| Trigger an effect from another mod | [Public API](docs/PUBLIC_API.md) |
| Server/client synchronization | [Networking](docs/NETWORKING.md) |
| Examples and in-game preview | [Examples](docs/EXAMPLES.md), [Test Harness](docs/TEST_HARNESS.md) |
| Measured performance and retained evidence | [Performance](docs/PERFORMANCE.md) |
| Tested graphics configurations | [Compatibility](docs/COMPATIBILITY_MATRIX.md) |
| Next stage: visual editor | [Roadmap](docs/ROADMAP.md) |

## How it works

Definitions live at `assets/<namespace>/vfx/<path>.json`; their resource location becomes the effect ID (for example `example_mod:smoke/burst`). `format` must be `pyro_vfx:1`. See the [working examples](src/main/resources/assets/pyro_vfx/vfx/examples/) and the full [format reference](docs/JSON_FORMAT.md).

Code in a consuming mod uses the public side-aware API:

```java
import net.kofllee.pyrovfx.api.Vfx;
import net.kofllee.pyrovfx.api.VfxHandle;

VfxHandle trail = Vfx.play(ROCKET_TRAIL,
        Vfx.at(rocket).offset(0, 0, -0.5));
Vfx.play(IMPACT, level, impactPosition);
trail.setParameter("density", 1.5);
trail.trigger("burst");
trail.stop();
```

`Vfx.play` on the logical server broadcasts to eligible clients; on the client it creates a local effect. Do not call it on both logical sides for the same gameplay event. Built-in Entity/BlockEntity sources and named sockets synchronize by identity; arbitrary server callbacks can stream sampled poses. The server never simulates visual particles. [Full API contract](docs/PUBLIC_API.md).

## Build and inspect

On Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer --no-daemon
```

On Unix-like systems, use `./gradlew`. Output: `build/libs/`. For interactive museum and optional client benchmarks, see [Test Harness](docs/TEST_HARNESS.md). Neither Minecraft clients nor benchmark runs are needed for documentation-only changes.

Client commands include `/pyrovfx list`, `/pyrovfx play <id>`, `/pyrovfx play_at <id> <pos>`, `/pyrovfx stop_all`, `/pyrovfx sockets` and `/pyrovfx backend`. Client resource reload refreshes effect definitions.

## Important boundaries

CPU Dense/Legacy and GPU Analytic/instanced paths are selected by effect capabilities and graphics environment. Active shaderpacks use conservative Minecraft rendering fallback. GPU Compute has deliberately **not** been added; complex collision, event and stateful effects retain CPU processing. Particle admission budgets limit resource use but are not VRAM or FPS guarantees.

The 66-effect museum, targeted GameTests, and two independent localhost clients provide useful development evidence, **not** proof of cross-driver, high-player-count, 4K or long-running modpack stability. Details and source measurements are in the links above.

Licensed under the [MIT License](LICENSE).
