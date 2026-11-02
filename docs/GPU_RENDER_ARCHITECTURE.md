# High-performance GPU render path — Pyro VFX (Minecraft 1.21.1)

The mod retains its `pyro_vfx:1` JSON/effect semantics. Rendering is selected separately from the existing compiled expression and data-oriented CPU systems. Unsupported cases **do not lose features**: they remain on the original `RenderType` pipeline.

## Rendering contract

Rendering is invoked from NeoForge `RenderLevelStageEvent.Stage.AFTER_PARTICLES` on Minecraft's render thread, with the event camera, world pose stack and partial tick. The instanced shaders use the same projection × Minecraft model-view × event pose matrix composition as Minecraft's ordinary render shaders. The implementation was visually compared against actual Minecraft screenshots; omitting the Minecraft camera model-view initially projected all particles behind the camera and was corrected before performance acceptance.

`VfxRenderer` preserves emitter ordering. Before any direct GL draw, it flushes the shared Minecraft `MultiBufferSource.BufferSource`, including vertices that might have been queued by other renderers, and flushes pending GPU batches before switching back to a legacy renderer. It finishes the remaining RenderType batches at the end of the pass.

The raw GL implementations are isolated in `VfxInstancedSpriteRenderer` and `VfxInstancedModelRenderer`. Each draw captures and restores the following OpenGL state inside a `finally` block: active program, VAO, array-buffer binding, active texture unit, texture-0 2D binding, blending enabled and separate blending factors, depth test enabled and comparison function, depth-write mask, and face culling. No framebuffer binds, scissor changes, viewport changes, polygon mode changes or global RenderSystem shader selection changes are made.

**Audit:** `-PbenchRenderStateAudit=true` performs readback verification of the altered GL state after every raw draw in real Minecraft. A verified audit is a local vanilla/NeoForge assertion, **not** evidence that every render mod or driver implements identical semantics.

## Sprite instancing

A single cached four-vertex unit quad is uploaded as immutable VBO geometry. Per-particle data is packed into one interleaved streaming buffer: camera-relative XYZ, XY scale, RGBA color and UV rectangle (13 floats / 52 bytes per instance). The vertex shader expands camera-facing billboards; the fragment shader samples the effect texture and multiplies alpha/color.

Contiguous emitters with the same texture and facing can share a batch; maximum instanced draw size is 16,384 sprites before flushing. No geometry is repeated on the CPU or uploaded per-particle.

Supported sprite subset: dense CPU sprites, additive blending, full-bright lighting, and `camera`, `camera_horizontal` or `world_z` facing. Animated and random-start UV states reuse the existing `VfxSpriteUvSampler` at the current partial tick. Transparent **alpha** sprites, world-lit sprites, velocity-facing sprites and arbitrarily rotated sprites remain on the Minecraft RenderType path. The unsorted GPU additive path is not used for order-sensitive alpha blending.

The renderer conservatively culls only sprites whose projected bounding sphere is outside the view frustum. Screen-space LOD removes sprites with projected diameter below 0.20 px (configurable as `-Dpyrovfx.render.minDiameterPx=0` to disable), with no fixed-distance cutoff or color/UV change for remaining particles.

## Model instancing

Only eligible custom baked SOLID models are GPU-instanced: unlit, untinted, uncolored-vertex quad geometry, no dynamic/parametric rotation and fully opaque per-particle appearance. Cached resource geometry is gathered using Minecraft's baked-model `getQuads` path, triangulated with original UVs, and uploaded **once per resource load** to a model-specific GPU VBO; subsequent model instances upload only XYZ, XYZ scale and RGBA (10 floats / 40 bytes per instance). The renderer uses the block texture atlas, just like the existing solid model RenderType.

Any unsupported face tint, shader/lighting requirement, translucent render layer, rotation, source type (entity, item, block), or missing/broken baked geometry **automatically takes the original model renderer**. This is a deliberately strict capability boundary, not a general replacement for Minecraft's model pipeline. A conservative model frustum test avoids submitting offscreen copies. Atlas texture bindings are resolved at draw time to avoid retaining invalid GL texture IDs across resource reload.

## Cache ownership and resource reload

Shaders, quad VBO, model VBOs, instance buffers and VAOs are created lazily on the render thread. Buffer capacities are reused; the streaming instance buffer is re-uploaded per frame, while geometry stays GPU-resident. Compiled shader uniforms and render batch material keys are cached.

On any client VFX resource reload, the reload listener marks both GPU caches invalid with `volatile` flags. GL cleanup and reinitialization are deferred until the next render-thread use; no GL deletion happens from an async resource reload worker. New model geometry is rebaked from the new model manager; active particle definitions keep their previous frozen simulation semantics. Actual client reload scenarios exercise both sprite and model paths.

## Shader and optimization mod policy

Raw OpenGL state integration cannot be presumed safe when shader/optimization mods replace or intercept Minecraft's shader targets. For the currently **unverified integrations**, installing any of these known mod IDs disables custom instancing while leaving complete VFX effects on Minecraft RenderTypes: `iris`, `oculus`, `neoculus`, `forgeoculus`, `sodium`, `embeddium`.

This avoids pretending that a shader pack with a custom material/render target has been validated. It is **not** an incompatibility block: effects still render through the same ordinary Minecraft path as before. For other unknown render mods, users can force the fallback through the JVM property `-Dpyrovfx.render.forceLegacy=true`. The corresponding development benchmark flag is `-PbenchLegacyRender=true`.

A future explicit adapter to a compatible rendering API (for example, shader-mod-aware instancing with known render targets) can re-enable acceleration only after live testing. No downloaded shader/optimization mods or version-specific external shader packs were installed for this run.

## Verification

Production visual behavior was compared with the frozen CPU baseline using screenshots of real Minecraft at the same effect seed and camera for both sprites and the custom model cube; alpha, lighting, UV, orientation and reload behavior were checked in separate client scenarios. Additional headless tests validate conservative frustum/LOD math. Raw client benchmark archives and side-by-side screenshot evidence are saved in `docs/benchmarks/m3-2026-11-02/`, with the comparison tool `python tools/summarize_gpu_render.py --after <UTC timestamp>`.

**Limitations:** Not every model can use GPU instancing; alpha sorting and per-pixel world lighting are deliberately left to RenderType, no shader/optimization mod test matrix has been executed, and P95 frame time is not itself the GPU draw time. GPU particle simulation (analytic or compute) is out of scope: only rendering has moved to instancing.
