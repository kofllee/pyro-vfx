# Native Render Bridge

**Runtime target:** Minecraft 1.21.1, NeoForge 21.1.229, Java 21. The Pyro VFX
effect format, simulation ownership, event ordering, networking and public API
remain unchanged. This document describes the production rendering route as
of 6 November 2026, not the earlier standalone proof of concept.

## Render pipeline

```text
VFX definitions -> effect/emitter lifecycle -> existing simulation pools
                  |
                  +-- GPU Analytic: resident cohort birth VBO, GPU motion/appearance
                  |
                  +-- CPU Dense/Legacy: compact sorted sprite instances
                  |
                  +-- baked block/item/custom model: cached Minecraft mesh + instances
                  |
                  v
          GL 3.3 Transform Feedback: geometry only
                  |
                  v
          Minecraft vertex format and native ShaderInstance
                  |
                  v
          Minecraft/NeoForge world composition (including Iris, when supported)
```

Pyro does **not** use a separate fragment shader or framebuffer for its final
color. It does not create Minecraft Particle objects for every GPU-resident
particle. GPU birth data is uploaded on creation/reload, not per animation
frame. CPU Dense/Legacy instances upload per frame by design.

## Owned components

- `VfxRenderer`: the single renderer router, transforms, visibility gates,
  shared BufferSource boundaries and material pass selection.
- `VfxRenderMaterial`: material semantics; solid/cutout/additive before world
  translucency, sorted alpha after the world. No glass/water block IDs, camera
  raycasts, special cloud depth writes or always-off depth testing.
- `VfxNativeGpuGeometryBridge`: GPU analytic sprite vertex generation and
  CPU batched sprite expansion into native Minecraft particle vertices.
- `VfxNativeSpriteBatcher`: same-mesh/material batching, cross-emitter stable
  depth sorting of CPU/Dense alpha and ordered translucent-model callbacks.
  This retains sorting when the native GL path cannot be used.
- `VfxNativeModelRenderer`: transforms stable Minecraft baked meshes on GPU
  using bounded Transform Feedback output and native final ShaderInstance.
  Dynamic entity models and other unsupported model semantics use RenderType.
- `VfxGpuBirthBuffers`: GPU birth VBO generations and deferred retirement.
  No separate GPU Analytic sprite final renderer exists.
- `VfxGpuFog`: temporarily restores Minecraft's world fog for native or
  RenderType late-stage submissions, then restores the caller's fog state.

Shaders used by Transform Feedback only calculate **geometry and vertex
attributes**. Their dummy fragment stage never draws a pixel.

## Correctness and fallback rules

GPU Analytic fast path supports additive sprites with shared-time size/color,
supported birth expressions, local/world transforms, rotation and facing modes,
random UV starts, per-particle expiry masking, and exact bounded Minecraft
lightmap sampling. Wide world-lit clouds that exceed the exact light-volume
budget use the Minecraft RenderType fallback instead of approximated light.

Alpha sprites are sorted by camera depth on the CPU across eligible Dense and
Legacy pools, with transparent model callbacks interleaved in depth order.
Unsorted GPU Analytic alpha is intentionally **not** enabled. Models retain
native batching for deterministic baked quads; tint-dependent entity geometry,
translucent models, unsupported light/appearance combinations and animated
models use the existing CPU/RenderType semantics.

Graphics without OpenGL 3.3 or after an initialization failure use the
CPU/RenderType route. The fallback is part of the **single architecture**, not
an independent competing custom GPU fragment pipeline.

GL state, indexed transform-feedback buffers, framebuffers, shader bindings,
VAOs, texture units, blend/depth/cull states and the shader reload generation
are restored or invalidated on the render thread.

## Validation and limits

At 1280×720 on the RTX 4090 Laptop test machine, short real-client native
scenarios covered 1k/10k/100k analytic sprites, alpha smoke, baked models
(including rotating, block and item), local/UV/motion expressions, world
lighting, model reload and GPU-expression reload with GL state auditing.
The dedicated GameTest server passed all 104 required tests. A three-scenario native Iris + Complementary museum profile completed, with original renderer settings restored afterward. Release JAR and
development compilation succeeded. Reports are generated under the ignored
`run/pyrovfx-benchmarks/` directory; retained baseline evidence is described
in `PERFORMANCE.md`.

The exact material intersection order of arbitrary alpha geometry against
glass/water/clouds is not pixel-perfect, and Minecraft/Iris shaderpacks impose
their own compositing limits. The original PoC found that in Complementary
Reimagined even genuine vanilla CLOUD particles are invisible in a particular
sealed underwater tank. This is not evidence that arbitrary shaderpacks are
universally correct. GPU time for native TF generation **plus** the final draw
must be compared with matched settings; successful rendering or low draw counts
alone are not proof of a performance improvement.

The isolated 6 November 2026 research is historical evidence, not the
production implementation. Retest Vanilla Fast/Fancy/Fabulous and the active
Iris shaderpack pipeline when changing native vertex layout or stage routing.

## Safe runtime checks

```powershell
.\gradlew.bat compileDevelopmentJava verifyReleaseJar runGameTestServer --offline --no-daemon
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario 'analytic_uv_1k,sprite_alpha_smoke_1k,model_1k' -Width 1280 -Height 720 -RenderStateAudit
```

The Windows benchmark launcher targets only newly created Java/GLFW Minecraft
windows on the secondary **physical monitor**. It does not restore browser
focus or activate another Windows virtual desktop.
