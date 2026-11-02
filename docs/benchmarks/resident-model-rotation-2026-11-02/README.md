# GPU-resident shared angular model animation — Minecraft A/B

Twelve completed Minecraft 1.21.1 NeoForge benchmark reports: three separate client starts each for 10k and 100k unlit baked models, GPU analytic shared rotation versus forced CPU Dense, RTX 4090 Laptop GPU, 1280×720, uncapped, fixed JSON SHA-256 and seed 1353719846.

| Model count | Simulation backend | CPU simulation median (ms) | CPU render submission median (ms) | GPU VFX median (ms) | Full-frame P95 median (ms) |
|---|---|---:|---:|---:|---:|
| 10,000 | GPU resident | 0.0464 | 0.0404 | 0.2755 | 1.7930 |
| 10,000 | CPU Dense | 0.5942 | 0.9995 | 0.2847 | 3.5321 |
| 100,000 | GPU resident | 0.0439 | 0.0725 | 3.7673 | 6.2403 |
| 100,000 | CPU Dense | 6.2813 | 10.0047 | 7.9319 | 21.0548 |

Both paths rendered the exact expected number of models and completed without GL state audit failures. CPU and GPU share discrete angular drag/acceleration coefficients; the vertex shader receives one interpolated quaternion per birth cohort. Dynamic rotation is eligible for GPU-resident only when initial angle, angular velocity, angular acceleration and angular drag are all constant. Random/per-particle/parametric rotations fall back to CPU Dense. This is not GPU Compute.

Real screenshot A/B using `tools/compare-bench-images.ps1`: 10k models (`171528` CPU vs `171325` GPU) differ in 2,277/921,600 pixels (0.2471%, mean absolute RGB channel error 0.0147/255); 100k (`171536` CPU vs `171334` GPU) differ in 10,464 pixels (1.1354%, mean error 0.0301/255). Inspected captures exhibit the same rotated-model sphere. Independent world/sky raster frames and floating-point precision prevent a pixel-identical claim.

The archived JSON files were captured before the completion commit and embed the preceding HEAD. Long soaks, shader mods and non-NVIDIA GPUs are not validated by this report.
