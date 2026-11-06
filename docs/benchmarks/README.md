# Pyro VFX M−1: unoptimized runtime baseline

Collected 2026-11-01 on Windows 11, Java 21.0.5, Minecraft 1.21.1, NeoForge 21.1.229. The JSON files in `baseline-2026-11-01/` were exported automatically from the **actual Minecraft client** by `runClientBench`.

Hardware: NVIDIA GeForce RTX 4090 Laptop GPU, OpenGL 4.6 (driver 610.74), Intel CPU Family 6 Model 183 (32 hardware threads reported). Window: 854×480, render distance 12, dedicated test world `Test VFX`, fixed effect seed 1353719846. Git revision recorded in reports: `c44bbbb8b24a0ea4cb0817f934b706ac4d6636d9` (pre-harness code). **Not** a 1080p or FPS-target performance claim.

| Effect | Peak particles | P95 frame interval | Median CPU particle tick | Median CPU VFX render submission | Median GPU VFX timing |
|---|---:|---:|---:|---:|---:|
| static sprite | 1,000 | 12.00 ms | 1.08 ms | 0.33 ms | 0.0051 ms |
| dynamic sprite | 1,000 | 11.39 ms | 1.13 ms | 0.37 ms | 0.0061 ms |
| collision sprite | 1,000 | 10.95 ms | 1.19 ms | 0.40 ms | 0.0010 ms |
| custom model | 1,000 | 10.79 ms | 1.04 ms | 1.11 ms | 0.0481 ms |
| static sprite | 10,000 | 23.15 ms | 10.59 ms | 2.83 ms | 0.1219 ms |

All durations are milliseconds; the full machine-readable records contain sample counts, frame percentiles, allocations, garbage collections, heap, actual OpenGL version, effect JSON SHA-256 and exact image filenames. These results reflect the **legacy object-per-particle runtime** with instrumentation and seeded spawning. They do not quantify any M1–M5 speedup.

`baseline-2026-11-01/sprite_1k.png` is the reviewed baseline image: 1,000 visible particles in open air above the forest, unobstructed by leaves. The other screenshot paths are recorded in their JSON files and their captures live under the local `run/pyrovfx-benchmarks` directory (not all binary images are copied into Git to limit repository growth).

The GPU measurements use asynchronous OpenGL timer queries. For small effects, timer resolution/driver behavior may dominate the few-microsecond timings; compare GPU timing distributions only with similarly-configured runs. The full-frame P95 numbers include all Minecraft rendering and should not be attributed solely to VFX. `1M` was **not attempted** on the legacy engine; 100k and 1M scenarios are explicit opt-in.

For future measurements run `runClientBench` with the same VFX JSON hash and GPU settings; compare a baseline JSON with the candidate using `tools/compare_bench.py`. The saved baseline is one measurement pass (not a statistical study); confirm substantive changes with repeated A/B runs. For the required next steps (M0–M5), see `docs/ENGINE_REARCHITECTURE_RFC.md` and `docs/TEST_HARNESS.md`.
