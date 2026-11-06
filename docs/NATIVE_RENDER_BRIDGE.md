# Native render bridge

**Target:** Minecraft 1.21.1 / NeoForge 21.1.229 / Java 21. This is the **current routing design**, not an experimental comparison suite. The mod produces its own geometry but uses Minecraft-managed final fragment shaders.

## Data path

```text
JSON definition -> emitter -> simulation selection
  GPU Analytic: compact immutable per-birth VBO + closed-form motion
  CPU Dense/Legacy: ordered per-particle state, events and world queries
       |
       v
VfxRenderMaterial: opaque/cutout early, alpha/additive late
       |
       +-- native sprite batching + shared depth queue (Pyro-owned)
       +-- cached baked-model mesh + native transform-feedback vertex expansion
       +-- translucent baked-model callbacks ordered with Pyro alpha geometry
       +-- Minecraft RenderType fallback for unsupported capabilities/geometry
       |
       v
native Minecraft ShaderInstance + Minecraft/NeoForge frame composition
```

## Backends

- `VfxAnalyticPlan` performs conservative eligibility checks. Closed-form sprite/model motion uses resident birth buffers when safe. Alpha sprites remain hybrid because sorting requires per-view positions on the CPU.
- `VfxNativeSpriteBatcher` performs global center-depth ordering of alpha/additive sprites across Pyro emitters and interleaves opaque-compatible transparent model callbacks. CPU Dense/Legacy instances are compacted, sorted and expanded using native GPU geometry.
- `VfxNativeGpuGeometryBridge` generates Minecraft-compatible sprite vertices via OpenGL Transform Feedback. Tiny analytic cohorts can reuse CPU instance batching to avoid disproportionate driver submissions.
- `VfxNativeModelRenderer` caches baked block/item/custom geometry. Solid/cutout models use the native entity material shader and correctly mapped light/overlay/UV attributes; translucent models are submitted individually in global center-depth order. Dynamic entity-model rendering uses Minecraft RenderType instead.
- `VfxRenderMaterial` chooses **early** `AFTER_BLOCK_ENTITIES` for opaque/cutout and **late** `AFTER_PARTICLES` for transparent alpha/additive materials. Material selection does not raycast for particular glass/water block IDs.
- `VfxGpuFog` preserves world fog state over late draws, including underwater rendering. Resource reload/world exit invalidates geometry caches, shaders and buffers.

## Transparency, clouds and limits

Alpha uses back-to-front center sorting. Mixed alpha/additive sprites share one Pyro queue while retaining separate GL blend factors. This does **not** reorder vanilla particles or foreign translucent renderers.

In Fabulous, Minecraft draws clouds and particle layers into separate targets and later combines them according to depth. Transparent GPU drawing can blend color without recording depth. The native renderer therefore replays eligible sprites and baked alpha/additive models with color writes disabled and depth writes enabled **after their visible color passes**, only when the native particles depth target exists. The replay is a compositor aid, not a physically exact multi-layer depth solution.

Fabulous combines targets as `result = background * (1 - layerAlpha) + layerRGB`. The `Model Additive` GPU path retains RGB factors `SRC_ALPHA, ONE` and limits layer alpha accumulation for a near-additive result. This is **not mathematically exact** through every shaderpack/compositor. Some effects or lightmap combinations use a CPU/RenderType fallback for semantics; overlapping translucent faces are not sorted per triangle.

The bridge requires compatible OpenGL 3.3 and uses GL state capture/restore around native submissions (framebuffers, transform feedback, shader, texture/light units, blend state, vertex arrays, masks). Shaderpack activation alone does not disable GPU; real shaderpack compatibility remains scene-specific.

## Evidence and development tools

Source-level and dedicated-server tests cover routing eligibility, basic material semantics and API behavior. Historical actual-client benchmarks are in [Performance](PERFORMANCE.md); the separate developer [render gallery](TEST_HARNESS.md) provides visual cases for glass, water, tinted glass, opaque walls, translucent intersections and vanilla clouds.

**Not yet demonstrated:** matched performance improvement of the newly unified transparency paths on multiple GPUs, all shaderpacks, order-independent transparency, GPU-only alpha sorting, or long stress stability. GPU Compute remains deferred rather than implemented.
