# Fabulous depth and tiny-cohort optimization — 6 Nov 2026

Target: Minecraft 1.21.1, NeoForge 21.1.229, Java 21, Windows 11,
NVIDIA RTX 4090 Laptop, OpenGL 4.6. Short real-client test runs.
These are local observations, not a cross-hardware FPS guarantee.

## Why this change

Fabulous has its own particles render target with copied opaque-world depth.
Rendering Pyro ALPHA at `AFTER_LEVEL` is too late; the target has already
been composited. Production ALPHA now renders at `AFTER_PARTICLES`, whereas
additive materials retain their pre-translucent stage. Explicit GL_LEQUAL
protects native GL draw paths. There are no occluder block-ID or raycast hacks.

The 35-exhibit museum repeatedly submitted hundreds of small GPU Analytic
cohorts (often one or two instances each). A per-cohort Transform Feedback
launch was expensive even after final Minecraft shader submissions had been
combined. Eligible additive cohorts of at most 12 particles now append to
the shared native sprite instance batch, retaining per-instance facing,
rotation and lighting. Large, birth-expression or incompatible cohorts remain
on the original GPU path.

## Controlled full-client FPS A/B

All runs below used the same museum world, fixed gallery camera, 1280×720,
Fabulous, VSync temporarily disabled, fixed daylight, native renderer, and
full GL state audit. The test launcher restores VSync and the user's
settings. The reported FPS is Minecraft's own FPS counter, sampled once per
20 game ticks; not the misleading interval between Pyro render callbacks.
Both the effects-off control and effects-on gallery have been captured.

| Scenario | Threshold | Median Minecraft FPS | Median Pyro CPU render | TF geometry calls (last frame) | Batched tiny cohorts |
|---|---:|---:|---:|---:|---:|
| Gallery off | 0 | 99 | 0.040 ms | 0 | 0 |
| Gallery all on | 0 | 68 | 1.267 ms | 376 | 0 |
| Gallery off | 12 | 97 | 0.036 ms | 0 | 0 |
| Gallery all on | 12 | 88 | 0.507 ms | 10 | 365 |

Source reports: `unoptimized-off.json`, `unoptimized-all.json`,
`optimized-off.json`, and `optimized-all.json`. A previous default-threshold
run recorded 93 FPS (101 FPS without effects), 0.485 ms Pyro CPU render
and 10 TF calls. Multiple runs are deliberately retained: sampling noise
and client warmup matter. Measurements are not paired by simultaneous scene
rendering; they are independently launched controlled repeats.

## Opaque wall correctness

The harness inserts a full stone wall between the camera and the effect,
then restores the blocks at test completion. The corresponding unobstructed
control uses exactly the same effect, seed, camera and time.

- Vanilla Fabulous native additive: visible without wall, hidden behind wall.
- Vanilla Fabulous alpha: visible without wall, hidden behind wall.
- Iris 1.8.12 + Sodium 0.6.13 + Complementary Reimagined r5.9.3:
  `depth_opaque_alpha` completed, screenshot shows stone wall without
  particle bleed. This confirms the specific alpha test, not every shaderpack
  or material combination.

Source reports: `fabulous-depth-clear-alpha.json`,
`fabulous-depth-alpha.json`, `iris-depth-alpha.json`.
Screenshots: `fabulous-depth-alpha.png`, `iris-depth-alpha.png`.
The profile runner also completed 4/4 scenarios in vanilla and 4/4 in Iris,
then restored the user's original shader-profile configuration.

## Tiny-cohort visual control

The `analytic_uv_tiny_8` diagnostic runs the exact same seeded eight-spark
effect and camera through two backends. The normal GPU route
(`-TinyAnalyticThreshold 0`) completed with one TF generation call;
the shared sprite instance batch (`-TinyAnalyticThreshold 12`) completed
with no TF call and one tiny cohort batched. Both screenshots show the
same visible spark arrangement; background frame differences prevent a
pixel-identical claim. Retained evidence:
`tiny-legacy-gpu.json`, `tiny-shared-batch.json`,
`tiny-legacy-gpu.png`, and `tiny-shared-batch.png`.

### Limits

Transparent glass/water interpenetration and arbitrary shaderpack
compositing are **not** established as pixel-perfect by these tests.
The FPS improvement is caused by less driver work for tiny cohorts; it does
not imply the entire Minecraft frame is GPU-bound or that this FPS carries
over to other resolutions, hardware, or modpacks.
