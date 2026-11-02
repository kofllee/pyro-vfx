# Routing, GPU-resident models, budgets and Minecraft regression audit — 2026-11-02

## Routing decision

The final GameTest suite exercises 32 combinations of sprite/model × world lighting × collisions × rotation × alpha/translucent appearance. It verifies that event-free, non-entity combinations can use CPU Dense rather than unnecessarily falling back to object-per-particle Legacy. It also checks that incompatible GPU-analytic combinations do not claim GPU eligibility.

* GPU-analytic birth-state: additive sprites with a supported, shared-age motion model; also unlit opaque/cutout/additive baked 3D models with shared-age motion, constant opaque color and optional **shared constant-parameter angular motion**. One GPU birth-state upload per cohort; no compute shader.
* CPU Dense: sprites and non-entity baked models even with world lighting, rotating sprites, translucent layers, and world collisions **without particle callbacks**. The rendering path separately chooses instancing or Minecraft compatibility for lighting, alpha and unsupported baked meshes.
* CPU Legacy: entity model behavior, ordered particle events or callbacks, and any condition not accurately implemented by the Dense component. Collision callbacks are explicitly guarded by a GameTest so synchronous event delivery cannot be silently bypassed.

Full 54/54 required GameTests passed. The routing matrix is representative, not a formal exhaustive proof of all possible expressions, textures, external renderer modifications or custom events.

## Measured Minecraft A/B outcomes (three independent launches per backend)

Reports in sibling folders preserve all source JSON, seeds, asset hashes, hardware, GPU query timing, FPS/CPU/frame samples and captured screenshot paths. Median FPS values below come from the reports' **median frame interval**; do not treat them as guaranteed framerates on other systems.

| Scene | Baseline median FPS | New median FPS | Baseline P95 frame | New P95 frame |
|---|---:|---:|---:|---:|
| 10k rotating additive sprites (CPU RenderType → instanced) | 197.8 | 741.2 | 7.394 ms | 2.520 ms |
| 10k collision particles, no callbacks (Legacy → Dense) | 232.0 | 780.5 | 8.324 ms | 2.011 ms |
| 100k unlit models (Dense → GPU-resident) | 135.0 | 252.0 | 13.941 ms | 5.424 ms |
| 100k shared-rotation unlit models (Dense → GPU-resident) | 83.8 | 223.9 | 21.055 ms | 6.240 ms |
| 1k translucent models (Legacy → Dense simulation) | 462.0 | 465.3 | 3.677 ms | 3.650 ms |

The translucent-model route improves CPU state management but does not materially accelerate Minecraft's transparent renderer. No extra shader was added for this case. The control `sprite_uv_instanced` selected GPU analytic in its default run and CPU Dense in its forced fallback; it is **not** a controlled estimate of nonrotating sprite instancing layout overhead.

## Adaptive budgets

* Existing CPU simulation time EMA and asynchronous GPU timing remain separate; budget controls **new spawn admission only**, never existing particle physics or events.
* Optional NVIDIA device-wide free VRAM pressure remains a coarse safety signal; unknown unsupported readings cannot throttle.
* Newly added JVM pressure guard samples every 20 client ticks and activates only if tracked particle state is at least 16 MiB **and** used JVM heap exceeds 85% of its maximum. It scales new spawn admission to 80% at 85% heap, or 50% at 92% heap, subject to the existing combined floor. This avoids throttling from unrelated Minecraft heap usage when observed VFX storage is tiny. JVM heap measurements are not direct estimates of GPU driver memory, and tracked state intentionally excludes unknown Legacy object overhead.
* Both deterministic and benchmark overrides preserve the previous opt-in behavior.

## Real-world regression checks after routing changes

Six additional raw Minecraft benchmark reports are included alongside this document. All six `status=completed`. The regular run covered `soak_mixed` (three resource reload cycles), `model_reload_active` (reload observed), `model_rotating_1k` (CPU Dense, world lighting) and `collision_1k` (CPU Legacy; exactly 102,006 contacts, collision callbacks retained). The opt-in adaptive run covered `adaptive_steady` (minimum spawn scale 0.64, 3,734 async GPU timing samples, adaptive heap scale 1.0) and `soak_mixed` (three reloads, adaptive heap scale 1.0, 6,877 GPU timing samples). New heap admission thresholds were separately exercised in GameTests; ordinary real-client workloads did not exceed them.

Image comparison script `tools/compare-bench-images.ps1` verifies identical effect hash, seed and dimensions and reports pixel differences. GPU/CPU screenshots visually match the respective scene layouts, but are **not pixel-identical**: observed changed-pixel fractions range from about 0.08% to 1.14% for the archived pairs. Repeated camera-synchronized capture, model-specific per-axis image tests, 4K, shader-mod compatibility and non-NVIDIA GPUs remain unverified. Those are quality-validation limits, not hidden claims of completed exhaustive platform verification.

## Implementation principle

No separate compute backend was introduced. Existing Dense SoA, GPU analytic birth-state buffers, model instancing, collision cache, GPU timer and budget systems were extended where measured performance justified it. All features have CPU/Minecraft compatibility fallbacks.
