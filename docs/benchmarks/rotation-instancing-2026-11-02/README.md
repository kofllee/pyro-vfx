# Rotating sprite GPU instancing — real Minecraft A/B

Six independent Minecraft launches (three GPU instanced, three forced RenderType) on Windows / RTX 4090 Laptop GPU, 1280×720, uncapped, same seed and scene definition. Every report has `status=completed`; the GPU path submitted one draw for each rotating-sprite scenario, while forced RenderType submitted none. GL state audit was enabled.

| Scenario | GPU instancing CPU render median of runs | Forced RenderType CPU render median of runs | GPU instancing VFX GPU median | RenderType VFX GPU median |
|---|---:|---:|---:|---:|
| 1k rotating additive sprites | 0.077 ms | 0.336 ms | 0.002 ms | 0.003 ms |
| 10k rotating additive sprites | 0.446 ms | 3.301 ms | 0.015 ms | 0.089 ms |

These are measurements, not a universal FPS guarantee. Original JSON outputs contain full frame percentiles, CPU simulation, allocations, GPU timer samples, engine revision, hardware and screenshots, and allow offline comparison. A/B comparisons use fixed effect SHA-256 and seed; benchmark captures were taken against uncommitted changes before the final commit.

The `sprite_uv_instanced` control scene was included in both launcher configurations. Its GPU run selected the *GPU analytic* backend and forced-legacy-render selected CPU Dense; this does **not** isolate the shader layout cost and must not be used as proof of zero regression to ordinary nonrotating instanced sprites.

The two renderer paths were not demonstrated pixel-identical: world/camera, raster blending and timing can differ. For visual acceptance, inspect the corresponding screenshot names recorded in the JSON, compare rotating asymmetric rectangles/axis order, and perform a controlled same-tick image-diff capture before declaring visual parity.

No GPU-resident 3D model simulation, compute shader, or fully accurate collision SoA is claimed by this milestone.
