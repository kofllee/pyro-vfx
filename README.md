# Pyro VFX

**JSON-defined effects runtime for Minecraft 1.21.1 / NeoForge 21.1.229 / Java 21.**

Pyro VFX handles sprite and Minecraft-model particles, parameters, expressions, curves, event triggers, block collision, attachments, multiplayer synchronization and adaptive resource budgets. Its rendering backends are chosen by **effect capabilities**: GPU Analytic for supported closed-form simulation; CPU Dense/Legacy for world-dependent or stateful behavior; Minecraft-native GPU geometry with RenderType fallback for drawing.

This is a **runtime prototype**, not a certified multi-driver or shaderpack release. Pyro UI is the first integrated native-Minecraft editor UI foundation; full JSON editing is a subsequent phase.

## Documentation

| Topic | Reference |
|---|---|
| Implemented functionality, open limitations and verification | [Current status](docs/STATUS.md) |
| Subsystems, ownership and design decisions | [Architecture](docs/ARCHITECTURE.md) |
| GPU/CPU rendering, blending and depth | [Native render bridge](docs/NATIVE_RENDER_BRIDGE.md) |
| JSON definitions and expressions | [JSON format](docs/JSON_FORMAT.md), [expressions](docs/EXPRESSIONS.md) |
| Public use from another mod | [API](docs/PUBLIC_API.md), [examples](docs/EXAMPLES.md) |
| Multiplayer and attachment identities | [Networking](docs/NETWORKING.md) |
| Graphics compatibility and remaining issues | [Compatibility](docs/COMPATIBILITY_MATRIX.md), [open work](docs/RENDERING_AND_OPTIMIZATION_BACKLOG.md) |
| Actual measurements (historical and current baselines) | [Performance](docs/PERFORMANCE.md) |
| Build, developer gallery and test commands | [Test harness](docs/TEST_HARNESS.md) |
| Native editor UI foundation | [Pyro UI architecture](docs/PYRO_UI_ARCHITECTURE.md), [original icon geometry audit](docs/PYRO_UI_GRAPHICS_QUALITY_REPORT.md), [Ignition design tokens](docs/PYRO_UI_IGNITION.md) |
| Next implementation stage | [Visual editor roadmap](docs/ROADMAP.md) |

## Pyro UI showcase

Enable the client config option `editor.enable_editor`, enter a Minecraft world, and run `/pyrovfx editor` to open the integrated dockable Pyro UI Showcase. The UI is an actual Java/NeoForge Minecraft screen, not a web or image mockup. Its interactive controls edit demonstration model data rather than writing VFX JSON yet. `:pyro-ui` is a standalone Minecraft-aware NeoForge UI mod with **vanilla Minecraft typography** and original Pixelarticons rendered with correct per-icon ink bounds. It is distributed inside the Pyro VFX release through NeoForge Jar-in-Jar; other mods can depend on Pyro UI without using Pyro VFX.

For a direct development profile, use `.\\gradlew.bat runClientUiShowcase` (the optional `-PuiCapture=true -PuiAutoExit=true` produces true-client UI screenshots with a development-only probe).

The Minecraft world is now a proper **dockable and resizable gameplay window**,
not the UI backdrop: desktop gaps are opaque, and the native GPU-rendered world
is visible only inside the `Minecraft` panel. Click its body to capture
first-person gameplay input (WASD, mouse look, attack/use and hotbar); press
`Esc` to release focus back to the editor. Vanilla inventory/chat screens
return to the workspace when closed. The current viewport center-crops the
native camera output to the dock aspect ratio; it does not perform a second
perspective-correct world render. The docking workspace uses independent
rectangular zones with local/outer-edge placement and live reflow previews.

Use `/pyrovfx typography` or `.\\gradlew.bat runClientUiGallery` to inspect all 22 original Pixelarticons, their measured visible dimensions, and vanilla Mojangles font samples. The gallery supports icon/text scaling and an ink-bounds overlay toggled with `B`.

## Effect definitions

Place JSON resources at `assets/<namespace>/vfx/<path>.json`. The identifier becomes `namespace:path` and the required format is `pyro_vfx:1`. Examples are in [the development resources](src/development/resources/assets/pyro_vfx/vfx/examples/).

The side-aware public API exposes play, parameters, triggers and stop:

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

Call `Vfx.play` on the **logical server** for one multiplayer gameplay event. It distributes effect instructions to eligible clients; the server never simulates visual particles. Use client-local playback for purely local UI or preview effects.

## Build and verify

```powershell
.\gradlew.bat build developmentJar verifyReleaseJar --no-daemon
.\gradlew.bat runGameTestServer --no-daemon
```

Use `./gradlew` on Unix. Release artifacts appear in `build/libs`. Only `src/main` ships in the release JAR; `src/development` contains the separate visual gallery, benchmark fixtures, debug commands and GameTests. The release-JAR verifier rejects leaked developer fixtures.

For a **single-purpose rendering gallery** (glass, water, tinted glass, opaque walls, overlapping alpha and real vanilla clouds), run:

```powershell
.\gradlew.bat runClientRenderGallery
```

This developer client uses its own `Pyro VFX Render Lab` save and always exercises the regular renderer; no old/new switches are required. The optional Iris and Complementary client profiles use separate ignored run directories and require compatible locally installed graphics mods/shaderpacks. See [test harness](docs/TEST_HARNESS.md).

## Rendering boundaries

- Alpha and additive sprites use shared depth-sorted submission among **Pyro-owned** sprites. GPU Analytic Alpha is a hybrid: closed-form simulation with CPU depth ordering and native GPU geometry; it is not a GPU sorting implementation.
- Opaque/cutout and additive baked models use native GPU mesh processing when eligible. Translucent baked models use ordered GPU submissions with CPU sorting; animated entity models and unsupported material semantics retain Minecraft RenderType.
- Fabulous can use a late **depth-only replay** to compare effects with real Minecraft clouds. Additive model RGB uses additive blending, but the final Fabulous blend is an approximation because its transparency compositor also uses layer alpha.
- Iris and shaderpack behavior remains configuration-specific. Passing GameTests does not certify visual correctness on all shaders, GPUs or modpacks.
- GPU Compute is intentionally not implemented.

The code and documentation are licensed under the [MIT License](LICENSE).
