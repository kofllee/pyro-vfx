# Graphics compatibility and evidence

**Target:** Minecraft 1.21.1, NeoForge 21.1.229, Java 21. Evidence from Windows 11 / NVIDIA RTX 4090 Laptop / OpenGL 4.6.

**Read the scope:** a short client launch or an old benchmark does not prove perfect alpha ordering, exact shaderpack additive behavior, or multi-hour stability. The final unified material routing was promoted after the recorded older measurements.

| Environment | What has actually been observed | Important boundary |
|---|---|---|
| Vanilla NeoForge (Fast/Fancy/Fabulous) | Prior GPU Analytic/Dense/native-model and smoke scenarios executed. Opaque depth and background controls were exercised. | New shared alpha/additive, cloud replay and shader transition need final matched visual review. |
| Sodium 0.6.13 + Iris 1.8.12, shaders disabled | An independent interactive client loaded the new visual gallery on 6 Nov; gallery definitions and world were loaded. | Earlier **black block/item** observation led to a native entity-material shader fix; no final screenshot validation after this fix. |
| Sodium 0.6.13 + Iris 1.8.12 + Complementary Reimagined r5.9.3 | Independent client entered the world with the shaderpack active. Earlier native bridge material scenarios also ran on this profile. | Iris logged shaderpack-dependent unknown-uniform/block-map warnings; current model cutout and additive visual parity is not certified. |
| Sodium 0.8.13 / Embeddium 1.0.15 | Prior short GPU-model/smoke museum scenarios completed separately. | These were older runtime code; not a matrix for every new material. |
| Iris with BSL 10.1.8 / Photon 1.4 | Prior short profile scenarios completed. | Not retested for newly unified alpha, model, cloud replay or Iris entity-shader mapping. |
| Headless NeoForge GameTest server | 104/104 required tests passed after renderer routing changes. | Verifies material policy and runtime semantics, **not** OpenGL frame output. |
| Two localhost multiplayer clients | Earlier 8/8 protocol/lifecycle checks per client. | Does not prove public-network latency or renderer visuals. |

## Renderer contract

- The GPU bridge uses Minecraft `ShaderInstance`, selected by material; Iris activation does not categorically disable GPU drawing.
- In Fabulous, separate particle/cloud targets are composited with their depth. Eligible Pyro geometry can replay depth without rewriting blended color.
- Alpha and additive are ordered among Pyro-owned submissions only. Neither foreign particles nor fluid/glass triangles participate in one shared Pyro OIT queue.
- Additive baked-model RGB is truly summed in its immediate render pass; Fabulous' subsequent compositing is a **near-additive approximation** because its target-alpha equation still attenuates background.
- Block/item baked meshes use an entity-material vertex path (different from particle shader). Visual validation with shaderpacks is still required.

## Unsupported / unverified

AMD and Intel GPUs; Mac/Linux client drivers; modpacks with other post-processing mods; shaderpack switching within one session; all cloud, weather and water configurations; exact cross-target additive; 4K/high particle counts; sustained play; high-latency networking.

For a reproducible scene, use the separate [render gallery](TEST_HARNESS.md) rather than modifying a player's save. Historical screenshots and measured evidence are retained under [Performance](PERFORMANCE.md) and `docs/benchmarks/`; do not treat those as post-consolidation performance data.
