# Entity-rendered particle investigation — Minecraft 1.21.1 (2026-11-02)

Four completed independent RTX 4090 Laptop GPU client benchmark reports, 1280×720, same fixture and seed. One cached Pig Entity is reused by the Minecraft EntityRenderDispatcher for each particle. This does not create 1,000 distinct Entity objects, but it **does** invoke the Minecraft renderer per particle.

| Pig particles | Median CPU render submission (ms) | GPU VFX (ms) | Median FPS | P95 frame (ms) |
|---:|---:|---:|---:|---:|
| 250 | 0.521 | 0.076 | ~740 | ~2.35 |
| 1,000 | 2.179 | 0.451 | ~300 | ~4.97 |

The entity particle renderer is unlike baked custom/block/item quads. Minecraft entity models may change skeletal poses, textures/skins, living-model layers, emissive features, render states and effects per tick, and model geometry can vary by entity state. Extracting the renderer's current mesh into a persistent GPU VBO is not generally safe. A generic fake “entity instanced renderer” would discard these semantics and shader/mod interoperability.

There is no proven GPU A/B instancing candidate for this family. Instead preserve CPU Legacy + vanilla EntityRenderDispatcher, with cached entity per type and a pure routing test that rejects GPU analytic, SoA and baked model instancing for Entity sources. Static *baked model* particles already use GPU instancing and are recommended when the effect author does not require entity behavior.

No extra GPU skinning, entity mesh extraction or shader-backend was added. These are deliberately unimplemented pending a separately scoped and visually validated static-entity subset. Existing performance numbers are a measured reference, not an acceleration claim. Raw JSON capture contains CPU/GPU, P95/P99, memory and spawn telemetry; screenshots are available at paths recorded in the JSON.
