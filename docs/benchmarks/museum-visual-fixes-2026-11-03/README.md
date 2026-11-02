# Minecraft museum visual regressions — 2026-11-03

**Context:** Minecraft 1.21.1 NeoForge, actual standalone museum world, NVIDIA RTX 4090 Laptop GPU, 1280×720, uncapped frame intervals, GL state audit. All archived reports have `status=completed`, distinct source hashes where demonstration JSON changed, identical camera/seed for GPU/CPU fallback within each new fixture. Values are **short-run observations**, not universal FPS claims.

## 1. Black transparent item pixels — fixed

The item mesh fixture was using `render_layer: solid`. Minecraft item textures contain cutout-alpha atlas texels. The instancing fragment shader previously did not discard those texels and could write an opaque black rectangle. The model definition now selects effective CUTOUT when an ITEM declares SOLID, and the GPU renderer uses the corrected layer. The Minecraft item fallback likewise requests CUTOUT geometry and obtains quads using a null model-specific RenderType (matching the existing baked cache).

The museum's `model_item` explicitly declares CUTOUT. GPU analytic and forced Minecraft model RenderType completed with **5/5** items. See [GPU item](item-gpu.png) and [Minecraft fallback](item-cpu.png). Both screenshots show clear gaps outside the item silhouette, without opaque black cards. This fix does not imply weighted transparency for semitransparent item models.

## 2. Alpha smoke versus Minecraft clouds — stage corrected

Pyro previously rendered at `RenderLevelStageEvent.Stage.AFTER_PARTICLES`; Minecraft's later clouds/weather composition could appear over alpha smoke even when the smoke should be in front. Pyro's world renderer now uses `AFTER_LEVEL` so it blends against the composed world color/depth before hand/HUD rendering. No blanket GPU ban was added. Ordinary 3D world depth testing, alpha blending and GL-state restoration remain enabled.

The 240-particle museum smoke scene completed on both the GPU depth-sorted sprite path and forced Minecraft RenderType. See [GPU smoke](smoke-gpu.png) / [CPU smoke](smoke-cpu.png); vanilla clouds appear behind smoke and framing geometry. P95 frame: **1.706 ms GPU instancing**, **1.411 ms Minecraft fallback** for this *small* scene, so the results are correctness evidence, not a speed advantage at 240 particles. The earlier heavy 100k smoke A/B remains the performance reference.

Caveat: shaderpacks and modded framebuffer composition may need separate integration; the stage fix is validated on the actual available vanilla graphics pipeline, not every graphics mod.

## 3. Self-emit approach-only FPS collapse — bounded museum chain

The *original M0 regression fixture* deliberately sends `on_collision -> emit_again -> main`. In the museum, `main` spawned **24** colliding particles; each collision created 24 more colliding particles, producing exponential growth across ticks even without directly looking at the particles. The engine's hard budgets constrained the damage but do not make the museum fixture suitable as an ordinary interactive demonstration.

The museum now redirects the event to `child`, a **manual, noncolliding emitter** spawning exactly two short-lived particles per hit, without recursively triggering more events. The original regression fixture is untouched; true recursive emission remains a valid capability under existing budgets.

| Identical museum initial seed/count | Old recursive | New bounded |
|---|---:|---:|
| Peak particles | 13,824 | 48 |
| Total admitted spawns | 60,600 | 72 |
| CPU simulation median | 0.986 ms | 0.065 ms |
| P95 frame interval | 6.160 ms | 1.718 ms |
| P99 frame interval | 10.773 ms | 2.845 ms |

A dedicated **near-camera (approximately two blocks)** test scenario `museum_self_emit_near` was completed: 48 peak particles, 72 admitted total spawns, **0.067 ms** median CPU simulation, **2.765 ms** P95 and **4.814 ms** P99. It retained the event behavior without the explosive frame cost. Old/new reports do not share the same effect SHA, because changing the event graph is the point of the fix. Benchmarks describe the *museum*, not a rewrite of generic event semantics.

## 4. Warm 3D cube tint — neutral visual controls

Historical stress fixtures such as `bench/model_rotating` and `bench/model_rotating_unlit` explicitly specified `color: [1, 0.85, 0.75, 1]`. The museum generator previously inherited this orange tint, making it difficult to distinguish texture, lighting and color errors. All *museum* model effect colors are now `#FFFFFFFF`. The original deliberately tinted benchmarks remain unchanged. The model test asset `ember_cube` uses Minecraft's stone texture, not an orange texture. The [lit GPU screenshot](model-lit-gpu.png) shows the intended neutral gray. A GameTest guards against further accidental museum model tinting.

The dynamic lightmap remains active for world-lit models and intentionally disabled for fullbright variants; the fix does not change world lighting algorithms.

## Validation

- 63/63 GameTests passed on dedicated NeoForge GameTestServer after the routing and museum changes.
- Real Minecraft client: GPU item + CPU Dense item, GPU alpha + Minecraft alpha, GPU-lit + Minecraft-lit models, original/new self-emit; all completed with GL state audit.
- Raw JSON reports and relevant client screenshots are archived alongside this README.
- No shaderpack, other GPU hardware or global alpha-OIT claims are made here. For the static material/view, correctness is supported by inspected frames; strict synchronized multi-frame pixel equivalence is still unverified.
