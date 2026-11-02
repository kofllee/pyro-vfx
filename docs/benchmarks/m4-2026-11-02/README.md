# Analytic GPU particles — real Minecraft stress tests

Three separate Minecraft JVM/client launches per particle count. No extrapolated/estimated samples. Every result must have observed GPU draws and exact peak particle count or the script fails.

| Particles | CPU simulation median (ms) | CPU render submission median (ms) | GPU VFX median (ms) | GPU VFX P95 (ms) | Full-frame P95 (ms) | Draw calls | Birth-state upload (MiB) |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 10,000 | 0.026 | 0.079 | 0.020 | 0.023 | 10.407 | 1 | 0.23 |
| 100,000 | 0.024 | 0.068 | 0.202 | 0.203 | 10.172 | 2 | 2.29 |
| 1,000,000 | 0.009 | 0.071 | 1.004 | 2.048 | 9.839 | 16 | 22.89 |

## M3 versus M4, same frozen 10k JSON and seed

| Metric | M3 median | M4 median |
|---|---:|---:|
| CPU simulation, ms | 0.260 | 0.026 |
| CPU render submission, ms | 0.448 | 0.079 |
| GPU VFX, ms | 0.024 | 0.020 |
| Full frame P95, ms | 10.294 | 10.407 |

## Test environment

- Minecraft 1.21.1, Java 21.0.5, Windows 11 10.0.
- CPU Intel64 Family 6 Model 183 Stepping 1, GenuineIntel; 32 logical processors; JVM max heap 7.9 GiB.
- GPU NVIDIA GeForce RTX 4090 Laptop GPU/PCIe/SSE2 (NVIDIA Corporation); OpenGL 4.6.0 NVIDIA 610.74.
- Window 854×480, render distance 12, FOV 70, VSync True.
- Integrated singleplayer 'Test VFX', camera fixed at (0.5,141.5,-12), effect origin (0.5,140,0.5); seed 0x50B02026.
- All measurements use 30 warmup ticks, 100 measured ticks. Heavy particle limit enabled **explicitly for 100k/1M only**.
- Each GPU dataset contains a constant-colour/scale additive sprite, static motion, no collisions or events. The complete 24-byte initial-position/velocity buffer is GPU resident, never streamed once per frame.
- 1M is a **real completed run** here, not a guarantee on other GPUs or shader/optimization mods.
- VSync and Minecraft's wider world workload make total-frame P95 a broader metric than GPU VFX time. Startup shader compilation, one-time VBO upload, initial particle sampling and potential driver stalls are not captured by steady-state median simulation/render measurements.
- CPU fallback for unsupported effects and modded render pipelines remains in use. No 1M colliding/alpha sprites, GPU compute, or cross-driver shader-mod compatibility is claimed.
