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

Every exhibit runs automatically as you walk near it. At most five nearby effects are active at once, keeping load predictable. One-shot effects restart periodically; effects and backend overrides are cleaned up upon leaving the museum. The normal VFX runtime is not globally cleared.

Each stand has four physical Minecraft buttons: **REPLAY**, **PAUSE**, **SOLO** (isolates a single exhibit and toggles comparison mode) and **INFO** (opens technical details on the panel). The dark-backed, high-contrast title/status/detail panels are **minecraft:text_display** entities, not clickable text. They only update when text changes. No commands or chat messages are required for normal operation.

Moving effect sources are real visible animated Husk entities. Local and World Space use contrasting emitters on the same moving model. The animated head socket resolves the actual vanilla ModelPart, not an estimated camera/head transform. Block anchors and BlockEntity anchors are real copper blocks and barrels. Moving custom and travel-distance sources are visually apparent.

## Developer files and verification

The museum catalog and generated JSON fixtures are under **src/development/resources**. The source is **src/development/java**. Generator: **tools/generate_museum_effects.py**. Reusable attachment source definitions are kept in the development-only **vfx/fixtures/attachments** folder, not in the release.

Use **gradlew developmentJar verifyReleaseJar** to produce the developer variant and verify that the ordinary release JAR contains no museum, GameTests, diagnostic commands or benchmarks. GameTests are exercised through **gradlew runGameTestServer**.

The historical screenshot and performance reports in docs/benchmarks remain archived; the redesign does not imply new FPS claims. See ../TEST_HARNESS.md and ../COMPATIBILITY_MATRIX.md.
