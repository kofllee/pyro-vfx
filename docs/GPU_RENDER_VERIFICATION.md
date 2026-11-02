# GPU renderer validation and results

**Target:** Minecraft 1.21.1 / NeoForge 21.1.229, Java 21, Windows / NVIDIA RTX 4090 Laptop GPU, 854 × 480. The `pyro_vfx:1` effect schema is unchanged. This milestone delivers fast GPU **rendering** (not GPU particle simulation).

## Implemented

- GPU-instanced additive sprite renderer: a shared unit quad cached on the GPU, interleaved 52-byte per-instance transform/colour/UV data, material/facing batching, up to 16,384 instances per draw.
- GPU-instanced custom solid model renderer for strictly eligible unlit, un-tinted, opaque, nonrotating baked models. Baked quad geometry is uploaded **once per model resource reload**, with 40-byte per-instance position/scale/RGBA. Other model types still use Minecraft's native model renderer.
- Conservative frustum culling for sprites/models and configurable **0.20-pixel projected sprite diameter** LOD; neither mechanism changes simulation, life spans, events, or state of culled particles.
- Full per-draw capture/restore of modified OpenGL state, combined with Minecraft `MultiBufferSource` flushes when crossing the native-GL/RenderType boundary. Real client GL-state audit checks resource bindings, blend parameters, depth test/mask and cull state after every draw.
- Client resource reload correctly invalidates both GPU resource caches; GL resources are released/recreated on the Minecraft render thread, and active effects retain their existing compiled VFX state.
- Shader/optimization mod **fallback policy**, not unsupported modpack integration: when Iris/Oculus/Sodium/Embeddium is loaded, custom instancing is disabled but effects still render through ordinary Minecraft `RenderType`/baked-model paths.

## Tests

- `./gradlew.bat clean build runGameTestServer`: **24/24** NeoForge GameTests passed, including conservative frustum and LOD math.
- `python tools/test_compare_bench.py`: **5/5** comparator tests.
- **15/15** real-Minecraft main benchmark reports completed, across three independent client starts for each of five workload types. Every report is checked for identical resource JSON SHA-256, seed, requested and peak particle count, warmup/sample ticks, Minecraft version, GPU, and resolution.
- **17/17** extra client regression cases completed under `-PbenchRequireGpuInstancing=true -PbenchRenderStateAudit=true`, including additive UV, three facing modes, world-lit and alpha fallbacks, actual sprite frustum/LOD, both active-resource reload paths, collision events, budget caps, models and normal event/curve behavior.
- In the frustum-specific real-client scenario, **739 of 1000** simulated sprite particles were frustum culled, and **261 were submitted** to one instanced draw. The LOD test kept **1000 live simulated sprites** but excluded all 1000 subpixel sprites from GPU submission. These are deliberate visual tests, not hidden reductions in the 1k/10k performance workload.
- GPU-model path for `model_1k` submitted 1000 matching baked models in **one GPU draw**, without per-particle mesh uploads; the corresponding CPU/render comparisons were run using the previous snapshot.
- A separate forced-legacy renderer run with `-PbenchLegacyRender=true` completed **5/5** client workloads. Both sprite and model instanced draw counters remained zero; JSON hashes, seeds, resolution, GPU and particle counts match the accelerated runs. Raw reports and the functional comparison are archived in `benchmarks/m3-2026-11-02/FALLBACK_AB.md`.
- `java tools/CompareScreenshots.java` (RMS threshold 0.05) passed for sprite1k, sprite10k and model1k snapshots: **0.001562**, **0.034947** and **0.024099** normalized RGB RMS, respectively. These images include the surrounding Minecraft scene and should not be taken as pixel-perfect proof of shader equivalence.

## Performance (three launches, median of aggregate per-run metrics)

| Fixture | Original renderer CPU (ms) | Instanced renderer CPU (ms) | Relative | Original VFX GPU timer (ms) | New VFX GPU timer (ms) | Relative |
|---|---:|---:|---:|---:|---:|---:|
| sprite_1k | 0.279 | 0.108 | −61.4% | 0.005 | 0.004 | −20.0% |
| sprite_10k | 2.519 | 0.448 | **−82.2%** | 0.114 | 0.024 | **−79.3%** |
| model_1k | 1.022 | 0.119 | **−88.3%** | 0.050 | 0.018 | **−63.3%** |
| dynamic_1k (alpha fallback) | 0.273 | 0.276 | +0.8% | 0.005 | 0.006 | +20.0% |
| collision_1k (legacy fallback) | 0.414 | 0.395 | −4.5% | 0.005 | 0.005 | 0.0% |

CPU values measure CPU time inside `VfxRenderer.render`. GPU values are asynchronous `GL_TIME_ELAPSED` queries around this VFX render section, not GPU time for the entire Minecraft frame. Tiny 1k GPU timing differences should **not** be interpreted as significant improvements. Alpha/collision CPU changes are within normal measurement variability and are not claimed as speedups.

Render-thread Java allocations per rendered frame decrease dramatically in GPU cases: `sprite_10k` **4.205 MiB → 0.001 MiB**, `model_1k` **1.018 MiB → 0.001 MiB**; these reflect Java allocations only, not native driver staging or video RAM. Model and sprite rendering still pay the required per-frame instance upload cost.

The complete comparison, raw machine-readable reports, P95 statistics, GPU draw counters and old/new screenshots are in [benchmarks/m3-2026-11-02](benchmarks/m3-2026-11-02/). The data was recorded before the final source commit, so the current Git SHA embedded in an individual benchmark report alone is insufficient to reproduce all sources: retain this report together with the completion commit.

## Compatibility and limitations

The renderer uses the NeoForge `AFTER_PARTICLES` event, the event's camera and pose matrix, and the Minecraft render target inherited from the event. The active projection × Minecraft camera model-view × event pose composition was validated by actual screenshots, after an initial bug had rendered sprites behind the camera. The new normal sprite screenshots show the same effect geometry as the prior render path, and the new baked-model screenshot shows the same geometry and scattering; day/weather/other background variations prevent treating unaligned screenshot differences as pixel-perfect proof.

Instancing is **not applied to alpha-sorted sprites, environment-lit sprites, arbitrary rotations, entity/item/block models, tinted baked geometry or incompatible render-mod configurations**. These features retain the tested Minecraft render path rather than disappearing.

Shader/optimization compatibility is **conservative fallback by mod presence**, not demonstrated integration: this environment did not install and test particular Iris/Oculus/Sodium/Embeddium versions, shaderpacks or OpenGL driver vendors other than the listed NVIDIA setup. Unknown rendering mods can force the full legacy path with `-Dpyrovfx.render.forceLegacy=true` (Gradle development equivalent `-PbenchLegacyRender=true`). A proper cross-mod rendering matrix remains separate compatibility work.

The renderer does not claim to solve per-pixel transparency sorting, GPU simulation, framebuffer postprocessing coordination, global game FPS scaling, arbitrary 1M+ particle performance or all modded render target contracts.
