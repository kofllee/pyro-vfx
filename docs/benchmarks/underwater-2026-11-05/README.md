# Water-medium GPU / RenderType comparison — 5 November 2026

Real Minecraft 1.21.1 NeoForge graphical-client evidence, 1280 × 720, identical VFX definition SHA-256 and seed (`1353719846`) in every pair. Iris 1.8.12 / Sodium 0.6.13 with **Complementary Reimagined r5.9.3** actually loaded (verified in `run/pyrovfx-benchmarks/secondary-bench-20261105-234141322.log` and `secondary-bench-20261105-234224065.log`). Both GPU and forced Minecraft RenderType runs completed with GL state audit. User's renderer profile was restored after both runs.

| Camera / particles | GPU screenshot | Minecraft RenderType screenshot | Whole-frame RMS | Fraction of pixels different by ≥8/255 |
|---|---|---|---:|---:|
| Camera and both particles inside water | [GPU](alpha_camera_and_particle_in_water-1793911326866.png) | [RenderType](alpha_camera_and_particle_in_water-1793911372330.png) | 0.004343 | 0.3349% |
| One particle inside water, other in air; camera in air | [GPU](alpha_mixed_water_media-1793911335316.png) | [RenderType](alpha_mixed_water_media-1793911380778.png) | 0.006446 | 0.9789% |

The four original JSON reports are included here next to their images; their `screenshot` fields retain the source-machine absolute benchmark path. The screenshot filenames are unchanged.

Both emitters in the mixed-media case were processed through **distinct early and late world stages**, as shown by the per-run pass counts. Underwater camera GPU uniforms used fog start `-8.0`, end `96.0`; shaders subsequently changed from linear fog interpolation to Minecraft's `smoothstep` calculation for better parity. The vanilla water, glass and mixed-media benchmark regressions were also rerun after that change.

**Scope:** these are matching single-run images for these particular fixtures, not evidence of universally correct translucent ordering, particle clouds spanning water boundaries, or perfect compatibility with all shaderpack pipelines. Whole-frame RMS is affected by the rest of the Minecraft scene; it is not a per-particle pixel-parity metric.
