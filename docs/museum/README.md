# Pyro VFX technical museum

Development-only Minecraft 1.21.1 / NeoForge technical laboratory. The old 66-effect gallery has been reduced to **35 exhibits** in a denser 6-column layout anchored at X=512, Y=140, Z=512.

| Technical category | Exhibits | Coverage |
|---|---:|---|
| Emission | 8 | Point, filled/shell sphere, filled/shell box, line, disc, ring, cone, model and steady |
| Motion & Rotation | 4 | Dynamic forces, parametric motion, 3D and procedural rotation |
| Appearance & Rendering | 7 | Billboards, UV, additive and translucent materials, custom/block/item/entity meshes |
| Curves & Expressions | 3 | Bezier, linear, step and Catmull; random, age and movement expressions |
| Events & Collisions | 3 | Contact and expiry, child emissions, event order |
| Transforms & Attachments | 7 | Entity Local/World, animated head socket, Block, BlockEntity, custom and moving providers, distance triggers |
| CPU / GPU | 3 | Analytic sprites, resident model paths and CPU/legacy fallback |
| **Total** | **35** | Related features share a stand |

## How to visit

Create the dedicated save once with python tools/create_museum_world.py, then launch with **gradlew runClientMuseum**. The gallery automatically builds and teleports the player when this dedicated profile starts. Avoid running the optional build command in a survival world.

On entering the museum, **all 35 exhibits are enabled and started simultaneously**, without a museum-specific activation distance or active-stand cap. Demonstrations that benefit from continuous emission use looping effect/emitter lifetimes; finite effects automatically restart on the first client tick after they fully finish. GPU-analytic and attachment fixtures keep their original backend eligibility and event timing. General engine safety budgets and Minecraft's normal chunk/frustum visibility still apply; no unrelated VFX instances are cleared.

The entrance has a shared physical console with **ALL ON** (enable every stand) and **ALL OFF** (stop every stand). Every exhibit has three labeled physical Minecraft buttons on colored pedestals: **ON/OFF** (toggle that stand), **SOLO** (show only that stand; press again to restore the previous selection) and **INFO** (send the exhibit's feature description, comparison legend and backend to Minecraft chat). The wall shows a readable fixed-orientation title, running state and technique description; side-by-side technical demonstrations have their own legend. Control actions also show immediate action-bar feedback. Buttons are recognized through direct interaction with a synchronized redstone-pulse fallback.

Moving effect sources are real visible animated Husk entities. Local and World Space use contrasting emitters on the same moving model. The animated head socket resolves the actual vanilla ModelPart, not an estimated camera/head transform. Block anchors and BlockEntity anchors are real copper blocks and barrels. Moving custom and travel-distance sources are visually apparent.

## Reading the technical comparisons

- **Billboard orientation**: Four separately labeled, single-sprite **alpha-blended** tinted samples — camera-facing, cylindrical (yaw-only), world-X and world-Y planes. Each expires before its next emission, avoiding additive overexposure and overlapping duplicates. Walk around each plane to see how its orientation changes.
- **Curves & expressions**: The wall prints the effect's actual parameter expressions (using `t = particle.normalized_age`); INFO repeats them in chat. Each sample uses an isolated stationary sprite where motion would obscure the tested interpolation.
- **Collision: falling, bouncing and expiry**: Three color-coded, physically separated drop lanes share the same spawn height and gravity. The **blue** particle hits a blue pad and stops without bouncing; the **yellow** particle rebounds from a yellow pad with 0.78 restitution; the **red** particle vanishes immediately on impact with a red pad. Only three small particles are born in each lane, with fixed point-like origin. Labels and explicit collision values are provided on the wall and via INFO. No global physics or benchmark fixture changes.
- **Trigger sequencing**: A red, yellow and cyan pulse appears in order at ticks 0, 30 and 60. The `sequence` events mutate `stage` and then emit the matching sample; the definition keeps real creation/timeline triggers.
- **GPU versus CPU**: The GPU sprite stand has two actual emitters with the same physics and geometry: left is GPU-analytic eligible, right uses a dynamic radius parameter to force CPU Dense. INFO prints their *actual selected backend* at runtime, including fallback if GPU is unavailable.
- **Entity attachment**: Tagged, glowing Husks identify real moving sources without a huge floating nameplate. Amber local-space particles move with the source; cyan world-space particles remain as a trail. The model-local stand uses six small rotating cubes in a ring and one independently rendered source-position sprite marker. The head-socket stand binds sparks to the actual model part. Each source stand exposes live particle counts on its status panel and the source's sampled world position through INFO, to distinguish missing simulation from rendering faults.
- **Transparent sprites**: Alpha sprites are depth-sorted and blended without writing Z. Additive sprites also do not write Z, including resident GPU bursts and the Minecraft RenderType fallback. Depth testing against terrain still applies. BlockEntity's pink halo has smaller particle size and lower opacity to leave the barrel visible.

**Repeated rebuilds:** Before deleting tagged old museum entities, the builder loads all chunks occupied by the current gallery. This avoids duplicating persistent Text Displays on subsequent launches that rebuild the museum before the player arrives. Item, rotating-model and GPU-model samples are positioned individually, rather than spawning a heap of identical geometry at one location.

## Developer files and verification

The museum catalog and generated JSON fixtures are under **src/development/resources**. The source is **src/development/java**. Generator: **tools/generate_museum_effects.py**. Reusable attachment source definitions are kept in the development-only **vfx/fixtures/attachments** folder, not in the release.

Use **gradlew developmentJar verifyReleaseJar** to produce the developer variant and verify that the ordinary release JAR contains no museum, GameTests, diagnostic commands or benchmarks. GameTests are exercised through **gradlew runGameTestServer**.

The historical screenshot and performance reports in docs/benchmarks remain archived; the redesign does not imply new FPS claims. See ../TEST_HARNESS.md and ../COMPATIBILITY_MATRIX.md.
