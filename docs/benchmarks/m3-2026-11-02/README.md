# Minecraft GPU renderer: CPU and GPU comparison against the frozen CPU runtime

Each result below is the median of **three independent full Minecraft launches**, tested with the same VFX JSON SHA-256, seed, particle count, warmup duration, Minecraft version, resolution and GPU. The old renderer submits CPU quads. The new renderer batches eligible additive sprites and suitable unlit custom baked models into GPU instanced draws; the rest retain Minecraft RenderTypes.

| Scenario | M2 render CPU ms | GPU-stage render CPU ms | Change | M2 measured GPU ms | GPU-stage measured GPU ms | Change | Verified instanced draws |
|---|---:|---:|---:|---:|---:|---:|---:|
| sprite_1k | 0.279 | 0.108 | -61.4% | 0.005 | 0.004 | -20.0% | 1.000 |
| sprite_10k | 2.519 | 0.448 | -82.2% | 0.114 | 0.024 | -79.3% | 1.000 |
| dynamic_1k | 0.273 | 0.276 | +0.8% | 0.005 | 0.006 | +20.0% | 0.000 |
| collision_1k | 0.414 | 0.395 | -4.5% | 0.005 | 0.005 | +0.0% | 0.000 |
| model_1k | 1.022 | 0.119 | -88.3% | 0.050 | 0.018 | -63.3% | 1.000 |

## Render P95, total-frame P95 and Java render-thread allocations

| Scenario | M2 P95 render ms | M3 P95 render ms | M2 P95 frame ms | M3 P95 frame ms | M2 alloc MiB/frame | M3 alloc MiB/frame |
|---|---:|---:|---:|---:|---:|---:|
| sprite_1k | 0.452 | 0.183 | 10.737 | 10.410 | 0.331 | 0.001 |
| sprite_10k | 3.280 | 0.771 | 10.287 | 10.294 | 4.205 | 0.001 |
| dynamic_1k | 0.412 | 0.478 | 10.005 | 10.051 | 0.368 | 0.330 |
| collision_1k | 0.569 | 0.553 | 10.225 | 10.652 | 0.460 | 0.460 |
| model_1k | 1.446 | 0.187 | 9.920 | 9.986 | 1.018 | 0.001 |

## Client regression and GL state tests

- `sprite_uv_instanced`: completed.
- `sprite_world_z`: completed.
- `sprite_horizontal`: completed.
- `sprite_frustum`: completed.
- `sprite_lod`: completed.
- `sprite_lit_fallback`: completed.
- `reload_active`: completed.
- `model_reload_active`: completed.
- `uv_animation`: completed.
- `event_order`: completed.
- `self_emit_collision`: completed.
- `collision_no_events`: completed.
- `budget_spike`: completed.
- `model_rotation`: completed.
- `model_inheritance`: completed.
- `curves`: completed.
- `events`: completed.

## Interpretation and limits

- Render submission timings measure CPU wall time in `VfxRenderer.render`; GPU timings are nonblocking GL timer queries around the VFX pass. They exclude the rest of Minecraft's frame.
- Shader/optimization mods are **not** installed or validated by this baseline. The custom GPU path intentionally declines Iris/Oculus/Sodium/Embeddium and preserves the vanilla RenderType fallback.
- Alpha, world-lit particles, velocity-facing sprites and complex model types remain on the original renderer. Only additive compatible sprites and untinted unlit solid custom baked models are GPU-instanced.
- Frustum culling uses a conservative projected sphere; LOD excludes only subpixel particles, and can be disabled by setting the threshold to zero.
- The GL state audit compares the state immediately before and after each draw in an independent real-client smoke run. It does not prove arbitrary third-party renderer integration.
- Renderer benchmark distributions may vary with GPU clocks, driver load, JIT warmup and frame pacing; no broad FPS or modpack compatibility claims.
- Raw benchmark reports were captured before the source commit: preserve the final completion commit with the report.
