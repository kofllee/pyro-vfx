> Historical proof-of-concept research. For the implemented production architecture and updated validation, see [Native Render Bridge](../NATIVE_RENDER_BRIDGE.md). Screenshots referenced below may be in the external preserved PoC archive.

# Native GPU Bridge — migration readiness, actual test results

**Date:** 2026-11-06
**State:** **READY TO BEGIN STAGED, FEATURE-GATED MIGRATION**. **NOT** cleared for unconditional replacement of the entire engine.
**Environment:** Minecraft 1.21.1 / NeoForge 21.1.229 / Iris 1.8.12 + Sodium 0.6.13 + active Complementary Reimagined r5.9.3; GPU RTX 4090 Laptop. Vanilla Fast/Fancy/Fabulous and Iris tests documented in earlier [BRIDGE_FIX_VALIDATION_2026-11-06.md](BRIDGE_FIX_VALIDATION_2026-11-06.md).

## Architectural result

The proof of concept makes the target render architecture technically feasible:

```text
Pyro effect / existing particle pools
 -> resident analytic birth VBO (CPU upload only on cohort birth / reload)
 -> bounded GL3.3 Transform Feedback geometry generation (GPU only)
 -> native Minecraft PARTICLE vertex data (GPU VBO, compact packed layout)
 -> Minecraft-managed ShaderInstance (Iris-compatible native final pixels)
 -> Minecraft/Iris's world composition
```

It does not create Minecraft Particle Java objects for each Pyro GPU instance. Production simulator and JSON format are unchanged. A *real vanilla CLOUD control* exists only in this research harness to test whether shaderpack transparency behavior is Pyro-specific.

**Actual code is only in the scratch worktree:**
`C:\Users\valle\AppData\Roaming\LocalCoderStudio\profiles\scratch\pyro-vfx\misc\stage-target-poc`

The original checkout's uncommitted production edits are preserved; no production migration/commit/push occurred.

## Blockers removed and proofs

1. **Shaderpack final composition:** GPU computed vertices are drawn with Minecraft `GameRenderer.getParticleShader()`, `ShaderInstance.apply` and native vertex attributes. This path renders in active Iris+Complementary, unlike the original direct custom fragment GLSL renderer. For additive sprites, switching to NeoForge `AFTER_BLOCK_ENTITIES` provides early insertion before water/glass, preserving opaque depth. The stage is chosen by **material**, not any block name or raycast.
2. **Opaque occlusion and transparent visibility, additive:** 1k scenes confirm additive sprites blocked by a stone wall, shown through glass, visible (though tinted/muted) inside a fully water-filled glass tank in Vanilla Fast, Fancy, Fabulous and Iris/Complementary. GL depth testing remains enabled.
3. **Quad topology / visual dimensions:** fixed two-triangle winding, then switched to 4 vertices + 6 indices, match existing `[-0.5,+0.5]` sprite quad scale. Shared per-cohort particle-age color/size is updated before TF generation with `pool.updateAppearance(partial)`; `analytic_age_1k` moving/fading effect is visible.
4. **Bounded GPU geometry and no per-frame CPU particle upload:** 4 vertices × 28B + 6 index entries ×4B = **136B/particle** amortized within the output buffer, down from 264B/particle. For 100k: one 2.4MB immutable birth-state upload, 400k generated GPU vertices per frame, two TF/draw chunks capped at 65,536 particles each, output/indices capacity **8,912,896B**; `glError=0`, no repeated cohort data uploads. Bench `analytic_uv_100k` completed (required explicit `-Heavy` opt-in). This does **not** prove faster GPU frame time: existing benchmarking timer does not include experimental TF callback; a native bridge GPU profiler is still needed before performance signoff.
5. **GL state and reload:** saves/restores framebuffer, indexed and generic TF buffer binding, shader, VAO, textures, blend/depth/cull, rasterizer discard. `RenderStateAudit` validated 1k/10k/100k without exception. On world change, shader-generation change and client logout, bridge releases its TF program, VAOs, output and index buffers. It does not delete pool-owned birth VBO. Test `stage_analytic_reload_active` report `status=completed`, `reloadCompleted=true`, `reloadCyclesCompleted=1`, selected backend `gpu_analytic`, GL error zero; birth VBO reloaded on resource reload.
6. **Unsupported effect preservation:** `VfxNativeParticleTfProbe.supports(pool)` is a conservative feature gate. Local transforms, random-start UV, per-particle lifetime, arbitrary per-particle appearance, model geometry, unsupported facing/rotation, world lighting and alpha sorting are not falsely passed to the partial GPU bridge. Scratch `VfxRenderer` routes those analytic cohorts through existing Minecraft `RenderType` fallback. Scenario `sprite_gpu_motion_expr_10k` completed with visible geometry and report `selectedRenderer=Minecraft RenderType: native bridge feature fallback`. In production, retain/expand old batch/instancing backends rather than dropping features.
7. **Underwater alpha parity benchmark:** Complementary config sets `particles.ordering=mixed`. In the same sealed full-water tank, even **actual vanilla `minecraft:cloud` particles**, created by `ClientLevel.addParticle` and drawn by Minecraft `ParticleEngine`, are **not visible**. They are clearly visible in the identical open-air control. Therefore this underwater visibility failure is also present in vanilla+shaderpack, and cannot reasonably be an unconditional Pyro migration blocker if the acceptance criterion is **no worse than vanilla**. Experimental GPU alpha does not solve it through early, late, forced Iris phase or disabled depth. Do not invent a fake fix.

## Remaining correctness boundaries — cannot be honestly marked solved

- **Glass and water order for all intersections is not solved.** The new `stage_analytic_glass_front` fixture, with glass actually **behind** the particle sphere, shows glass grid overlaid onto particles drawn by the early pass — an imperfect spatial order. The `stage_analytic_water_boundary` mixed-media fixture likewise demonstrates the usual translucent composition ambiguity. A single early pass is suitable for the **additive subset**, but cannot provide universal pixel-correct ordering for every surface or shaderpack. Material-specific routing + native/CPU fallback is mandatory. No block-name/raycast hacks; no global depth disable.
- **GPU ALPHA has no production-quality depth sorting or guaranteed water composite.** Existing production alpha uses CPU Dense + depth-sorted GPU instancing. Keep GPU Analytic Alpha **disabled** outside the research opt-in `-Dpyrovfx.trace.alphaAnalytic=true`. The experimental alpha sphere is for diagnostics only. The shaderpack/vanilla underwater limitation can be documented, not 'fixed' by hidden special rules.
- **Partial bridge feature support:** the current compact TF generator is intentionally only CAMERA-facing, world-space, unlit, common-age unrotated sprites without random UV or per-particle lifetimes. Material/models/lighting/local-space/per-instance expression parity and instancing paths have not yet been migrated. These MUST be handled by fallback until their native variants are implemented.
- **Performance parity** is unknown until accurate GPU timer around the bridge, memory/overdraw comparison across 1k/10k/100k, and shaderpack switching tests. Reporting successful draw commands and GL=0 is **not** equivalent to a 100k high-FPS result.
- **Multiple shaderpacks and runtime toggling** beyond Complementary are not yet certified. No universal promises on arbitrary shaders.

## Gate decision

**Start a staged migration under a feature flag**, not a global switch:

1. Move the compact TF/native draw from scratch into a production `NativeGpuGeometryBridge` scoped to supported additive sprite cohorts; preserve existing GPU birth VBO and current GL-state auditing.
2. Keep current CPU dense, GPU instanced (including sorted alpha), RenderType and model renderers as **first-class fallback within the new rendering architecture**. Unhandled features must be visible, never silently dropped.
3. Route through documented *material stages*, not emitter-local block checks. Validate opaque occlusion and front/behind/crossing glass and water, accepting shaderpack limitations only when proven to match genuine vanilla particles.
4. Add GPU timer around generation+native draw, resource reload/world change/shader toggle, and multi-cohort performance/visual regression before deleting old code.
5. Cut over more material families only after parity; do not delete the old router or custom backend until supported material coverage and benchmarks pass.

**The architectural decision is no longer blocked by Iris native geometry compatibility, arbitrary 10k VBO cap, GL state, resource reload, or unsupported-feature disappearance.** Global flawless transparency, full feature parity, and cost are **still unresolved engineering work**; the safe feature-gated migration path means they need not hold up the *start* of migration, but they absolutely hold up an unconditional release/cutover.

## Evidence

Copies in `docs/research/native-bridge-migration-2026-11-06/`:
- `vanilla-cloud-air-complementary.png`, `vanilla-cloud-water-complementary.png`: real vanilla CLOUD visibility parity.
- `gpu-100k-compact.png`: bounded GPU geometry stress.
- `gpu-shared-age-dynamic.png`: shared-age appearance/motion.
- `gpu-glass-behind-particles.png`, `gpu-water-air-boundary.png`: order limitations.
- `unsupported-feature-minecraft-fallback-10k.png`: conservative feature fallback.

Scratch source changes and full benchmark logs/JSON remain isolated and uncommitted. Related architecture history: [PYRO_VFX_NATIVE_RENDER_BRIDGE_ADR_2026-11-06.md](PYRO_VFX_NATIVE_RENDER_BRIDGE_ADR_2026-11-06.md).
