# Interactive runtime acceptance showcase

Launch on the developer Windows machine with `./gradlew.bat runClientShowcase`. It opens the dedicated `Pyro VFX Museum` save, builds physical stands and teleports the player to the runtime-test row. **No benchmark timer or auto-exit**. This is an interactive visual review, not a substitute for GameTests.

The existing 35 museum exhibits remain available. Seven new `runtime_*` exhibits are explicitly opt-in development content. Six are aligned on the final row; `runtime_birth` fills the last stand on the preceding row.

| Stand | Expected visible behavior | Expected runtime path |
|---|---|---|
| `runtime_birth` | Variable-radius cyan sphere bursts, spreading at different initial speeds | GPU Analytic; expression sampling at birth |
| `runtime_orbit` | Amber points drift together along an age-driven 3D orbit, not frozen at initial position | GPU Analytic resident, shared parametric offset |
| `runtime_spin` | Thin, asymmetrical amber rectangles turn together around 3 axes | GPU Analytic resident, shared dynamic rotation |
| `runtime_collision` | Sparks fall onto real orange blocks, bounce/settle, increment `param.hit` on contact | CPU Dense + ordered `on_collision` callbacks |
| `runtime_self_emit` | One falling parent hits the stage, generating a bounded cyan second-generation burst | CPU Dense collision callback + event-mediated child birth |
| `runtime_coalesced` | Three simultaneous amber bursts and a visibly delayed fourth burst | GPU Analytic, three same-tick births share a cohort (last burst separate) |
| `runtime_lit` | Orange particles shade using Minecraft world light; look in daylight and at night | CPU Dense simulation + GPU-instanced sprite lightmap |

While inside the museum, effects restart naturally after their finite durations. Each stand has physical ON/OFF, SOLO and INFO buttons. Text panels show backend routing and particle count. Use `/pyrovfx museum solo runtime_orbit` to isolate one. Use `/pyrovfx museum info runtime_orbit` or `/pyrovfx backend` to diagnose current selection. Use `/pyrovfxmuseum runtime` to return to the new row, and `/pyrovfxmuseum visit` to return to the entrance. Use `/pyrovfx museum mode auto` to restore automatic backend selection.

When reporting a bug, note **stand ID, camera angle, lighting/time of day, selected backend shown in the panel, expected vs observed behavior and whether it changes with `/pyrovfx museum mode cpu`**. A runtime model's FPS and shaderpack behavior still need separate acceptance. Avoid judging whether GPU cohorts are merged from the visual alone; the [client benchmark evidence](benchmarks/runtime-extensions-2026-11-05/README.md) establishes that via actual GPU dataset upload counters.

This showcase only runs in development. It does not change production particle routing or force CPU fallback for shaderpacks.

## Walkable glass and water comparison

The showcase launch also builds five neighboring, freely explorable stages after the existing gallery. Every stage loops the same two strongly colored alpha sprites so a player can compare visibility and layering from different angles:

- `/pyrovfxmuseum transparency clear`: unobstructed reference.
- `/pyrovfxmuseum transparency glass`: a complete glass pane wall between camera and effects.
- `/pyrovfxmuseum transparency water`: a sealed water wall between camera and effects.
- `/pyrovfxmuseum transparency submerged`: the particles are inside a water tank, observed from dry air.
- `/pyrovfxmuseum transparency underwater`: the camera and particles are in the **same** water volume.

`/pyrovfxmuseum transparency` returns to the glass stage, and `/pyrovfxmuseum runtime` returns to the original backend exhibits. The client opens in the **underwater** test stage in Creative mode when launched via `runClientShowcase`, so it can be explored without drowning. The staging geometry and looping visuals are restricted to the explicit development launch profile.

## Newer GPU examples on demand

Use `/pyrovfx museum preview <id>` to play a real benchmark definition eight blocks in front of the player. Available IDs: `analytic_moving_light`, `analytic_random_life`, `analytic_random_uv`, `analytic_model_surface`, `analytic_velocity`, `analytic_world_lit`, `model_shared_color`. Only one on-demand preview remains active; `/pyrovfx museum preview stop` stops it.

The underwater renderer restores Minecraft's captured world fog even when the late world stage disables the game's global shader fog. Its shader path now handles water fog for GPU sprites and GPU models; RenderType fallback receives the same restored fog. Early/late selection is **per emitter**, based on a sampled real particle, so independent air and water emitters can coexist within one effect instance.

This is not per-pixel compositing: an emitter with particles spread simultaneously across a water boundary is still classified as one group, and shaderpacks with independent translucent attachments require their own visual acceptance.
