# Native GPU material batching — 6 November 2026

## Why

The 6 November Minecraft-native cutover generated each GPU Analytic cohort with Transform Feedback, then immediately applied the Minecraft particle shader and drew that cohort. The technical museum reached roughly 300 final draws per sampled frame, with repeated GL state snapshots and shader binds. This introduced measurable render-thread overhead.

## Implementation

`VfxNativeGpuGeometryBridge` now appends each cohort's GPU-generated vertices to a shared **bounded TF buffer range** using `glBindBufferRange`. Per-cohort expression, age, motion, source transform, UV and lighting uniforms are still evaluated individually. Consecutive **additive, equal-texture** cohorts are submitted in **one** Minecraft `ShaderInstance` draw; no per-particle CPU processing or readback. The batch flushes before texture changes, output-capacity boundaries, CPU/dense/model/fallback work, and at stage end. GL state is captured/restored around the compatible run, not every cohort. A separate TF generation counter prevents mistaking final material submissions for total OpenGL calls.

## Evidence

All runs are real Minecraft 1.21.1 / NeoForge 21.1.229 clients on the same local GPU, 1280x720. The museum GPU sprite comparison uses matching effect SHA-256 `d3bc66cef042c7bc72672c8350567a3766e37c5296d1ce8c288e887cdc6b15c2`, seed `1573107102`, and a 60-tick measurement.

| Sample | TF calls last frame | Final Minecraft analytic draws last frame | CPU render median | CPU render P95 |
| --- | ---: | ---: | ---: | ---: |
| [Earlier native, Iris + Complementary](before-iris-museum-gpu-sprite.json) | ~300 | 300 | 4.054 ms | 9.813 ms |
| [Optimized, default restored profile](after-default-museum-gpu-sprite.json) | 299 | 5 | 2.075 ms | 3.308 ms |
| [Optimized, controlled Vanilla](after-vanilla-museum-gpu-sprite.json) | 299 | 5 | 2.106 ms | 4.040 ms |
| [Optimized, controlled Iris + Complementary](after-iris-museum-gpu-sprite.json) | 299 | 5 | 2.640 ms | 5.871 ms |

Additional after-change [Iris additive](after-iris-museum-additive.json) and [Iris custom model](after-iris-museum-model.json) museum reports completed. The explicit profile matrix ran all three scenarios in **Vanilla** and **Iris 1.8.12 / Complementary Reimagined r5.9.3**, with render-state audit and profile restoration. `verifyReleaseJar` succeeded and **104/104** NeoForge GameTests passed.

The [100,000-particle GPU Analytic UV stress case](after-analytic-uv-100k.json)
also completed with `-RequireGpuAnalytic -Heavy -RenderStateAudit`: 100,000
active resident particles, two TF generation calls and two native submissions
through bounded 65,536-particle output capacity (8,912,896 output/index bytes).
This specifically exercises the batch-full flush/continuation route.

These are independent short runs; do **not** interpret the differences as a controlled, repeatable FPS guarantee. Captured frames can differ in active particle lifetimes, lighting, shaderpack state and camera timing; no quantitative pixel-parity claim is made. GPU fragment submission is now dramatically less frequent, but **per-cohort TF generation remains** (299), and CPU fallback/alpha sorting retain their existing costs.
