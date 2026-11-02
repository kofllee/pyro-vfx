# Translucent baked models: Dense CPU simulation with Minecraft renderer

Six independent real Minecraft 1.21.1 client starts, RTX 4090 Laptop GPU, 1280×720, uncapped, same effect SHA-256 and seed. Three CPU Dense vs three forced CPU Legacy. All six completed.

| Metric | Dense median | Legacy median |
|---|---:|---:|
| CPU particle simulation | 0.111 ms | 0.131 ms |
| CPU VFX rendering | 1.098 ms | 1.093 ms |
| Pyro GPU time | 0.0358 ms | 0.0358 ms |
| Full-frame P95 | 3.650 ms | 3.677 ms |

The improvement is limited to simulation and storage, not rendering: both backends still require Minecraft's translucent model rendering, and Dense materializes compatibility snapshots during render. The difference is small enough that it should not be advertised as a meaningful full-frame FPS improvement. The new route avoids per-particle legacy simulation objects.

The same-scene screenshot comparison with `tools/compare-bench-images.ps1` found 720/921600 different pixels (0.0781%) between the first matched pair. Independent client starts can differ in world/sky rendering. No per-pixel equality guarantee is claimed.

Report JSON files were generated with uncommitted source and record the preceding HEAD; associate with the completion commit.
