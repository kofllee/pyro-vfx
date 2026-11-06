# Current runtime status

**Checkpoint: 6 November 2026.** Minecraft 1.21.1 / NeoForge 21.1.229 / Java 21. Pyro VFX provides the integrated runtime for authoring and playing `pyro_vfx:1` effects. Its next major deliverable is the visual editor. **The engine is still a prototype**, not a production-certified cross-shaderpack release.

## Available functionality

- **Data:** JSON resources, validation, compiled numeric/vector/color expressions, named curves, scoped parameters, emitters, event graphs and resource reload.
- **Particles:** point, sphere, box, line, disc, ring, cone and sampled-model spawn shapes; instant/steady/manual bursts; static/dynamic/parametric motion, rotation, block collision, particle events and lifetime rules.
- **Runtime:** CPU Legacy, CPU Dense SoA and eligible GPU Analytic birth/cohort simulation, with budgets and deterministic fallback for collision/stateful effects.
- **Rendering:** GPU geometry generation by OpenGL 3.3 Transform Feedback and Minecraft native ShaderInstance; sprites with alpha/additive blending; custom/block/item/entity model sources with solid, cutout, additive or translucent materials; lightmap, UV and multiple sprite facing modes.
- **Transparency:** shared Pyro alpha/additive sprite ordering, CPU-sorted translucent model submissions through GPU where supported, and Fabulous particle-target depth replay for real vanilla cloud composition. This is not global cross-mod order-independent transparency.
- **Transforms:** fixed, Entity, Block, BlockEntity, model-part/socket resolvers and callback transforms; local/world particle space and travel-distance triggers.
- **Integration:** side-aware `Vfx.play` / `VfxHandle` API and NeoForge v3 synchronization for Start, Stop, Parameter, Trigger, Rebind and Pose. Server handles commands; client simulates visible particles.

The release JAR excludes all developer commands, fixtures and museum effects. There is no visual editor yet.

## Rendering choices

| Effect | Path |
|---|---|
| GPU-compatible additive/alpha sprite, supported closed-form motion | GPU Analytic simulation; alpha and mixed additive use CPU depth ordering + GPU geometry |
| Lit/world-dependent sprites | CPU Dense simulation with native GPU geometry where compatible |
| Solid, cutout, additive baked block/item/custom models | Native GPU mesh route where eligible; Minecraft RenderType fallback |
| Translucent baked model | CPU-position sorting plus individual native GPU submission when supported |
| Entity-model particles and animated/dynamic meshes | Existing Minecraft RenderType route |
| Stateful collision, per-particle callbacks or unsupported expressions | CPU Dense/Legacy, preserving event order |

These are **capability-dependent**, not guarantees that every definition selects GPU. Debug/backend overrides and hardware capability failures retain their established fallbacks. OpenGL 4.3 GPU Compute was not added.

## Verification at this checkpoint

- **104/104 required dedicated server GameTests passed** after the unified material routing updates (6 November 2026).
- `build developmentJar verifyReleaseJar` passed; the release JAR does not package development samples or debugging code.
- Independent interactive Iris/Sodium and Iris/Sodium + Complementary Reimagined r5.9.3 clients opened the visual gallery. These are **manual observations**, not a pixel-perfect or sustained acceptance test.
- Earlier performance measurements and per-driver observations are archived at [Performance](PERFORMANCE.md) and [Compatibility](COMPATIBILITY_MATRIX.md). They measured earlier commits: **do not quote them as the speedup of the finalized 6 November alpha/model routing**.

## Known limitations / honest release boundaries

1. The current material ordering is center-depth sorting of Pyro submissions. Intersecting transparent faces, Minecraft glass/water, other mods' particles and shaderpack-provided passes cannot be guaranteed pixel-perfect.
2. Fabulous merges transparency layers with a layer-alpha equation. The additive model color pass uses an alpha approximation to avoid darkening the world: exact cross-target additive equivalence is **not established**.
3. Native depth replay adds geometry work. A matched performance campaign after promoting the new renderer is **not yet complete**.
4. GPU Analytic requires supported expressions/lifetimes and no world collision or ordered stateful events. Translucent models still involve CPU sorting and per-model draw work.
5. Shaderpack warnings and black/unlit baked model reports have occurred in testing; a native entity-material shader path was integrated, but **no final cross-profile visual confirmation has been recorded after that change**.
6. AMD/Intel GPU behavior, long-duration stress, large modpacks, full live shaderpack toggles, 4K and public-server latency remain unverified.

Future work is tracked in [Rendering work](RENDERING_AND_OPTIMIZATION_BACKLOG.md) and [Editor roadmap](ROADMAP.md). Testing instructions are in [Test harness](TEST_HARNESS.md).
