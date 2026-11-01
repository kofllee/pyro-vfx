# M1 — compiled VFX program performance comparison

Three independent real Minecraft client launches for each scenario before (M0) and after (M1) expression compilation/slot execution. Both sets include fixed VFX seed, JSON SHA-256, Minecraft version, GPU and resolution.

| Scenario | M0 simulation median (ms) | M1 simulation median (ms) | CPU reduction | M0 alloc (MiB/tick) | M1 alloc (MiB/tick) | Allocation reduction |
|---|---:|---:|---:|---:|---:|---:|
| sprite_1k | 1.028 | 0.117 | -88.6% | 6.42 | 0.45 | -93.0% |
| dynamic_1k | 1.070 | 0.122 | -88.6% | 6.56 | 0.61 | -90.7% |
| collision_1k | 1.424 | 0.450 | -68.4% | 7.52 | 1.54 | -79.5% |
| model_1k | 1.004 | 0.074 | -92.6% | 6.49 | 0.53 | -91.9% |
| sprite_10k | 10.227 | 0.512 | -95.0% | 64.17 | 4.50 | -93.0% |

## Tail latency, rendering, GC and raw measurements

| Scenario | M0 P95 simulation (ms) | M1 P95 simulation (ms) | M0 P95 frame (ms) | M1 P95 frame (ms) | GC M0 (collections) | GC M1 (collections) |
|---|---:|---:|---:|---:|---|---|
| sprite_1k | 1.652 | 0.207 | 11.292 | 10.343 | 22, 22, 18 | 15, 17, 20 |
| dynamic_1k | 1.835 | 0.217 | 11.796 | 10.264 | 5, 4, 6 | 4, 1, 1 |
| collision_1k | 2.303 | 0.673 | 11.784 | 10.422 | 14, 6, 8 | 6, 5, 8 |
| model_1k | 1.381 | 0.143 | 10.962 | 9.991 | 11, 5, 5 | 4, 4, 6 |
| sprite_10k | 14.383 | 0.873 | 22.417 | 10.876 | 44, 29, 27 | 14, 10, 11 |

## Additional correctness checks

Prior to declaring M1 complete, verify the following independent client scenarios and record their `status=completed`: `event_order`, `self_emit_collision`, `model_rotation`, `model_inheritance`, `reload_active`, `curves`, `events` and `uv_animation`. Server GameTests additionally assert exact deterministic motion, rotation, animated-UV, event order, parameter updates and legacy/compiled random sequences.

- `event_order`: completed (event_order-20261101-224202.json).
- `self_emit_collision`: completed (self_emit_collision-20261101-224211.json).
- `model_rotation`: completed (model_rotation-20261101-224219.json).
- `model_inheritance`: completed (model_inheritance-20261101-224228.json).
- `reload_active`: completed (reload_active-20261101-224236.json).
- `curves`: completed (curves-20261101-224245.json).
- `events`: completed (events-20261101-224253.json).
- `uv_animation`: completed (uv_animation-20261101-224301.json).

## What is and is not measured

- Simulation time is the actual Pyro VFX Minecraft client tick callback, not an offline math microbenchmark. Tick allocations use HotSpot thread-local allocated bytes during the measured callback; JVM-wide GC is also recorded.
- These are medians of three independent client launches each, not a controlled randomized experiment. JIT, driver load, Minecraft frame pacing, thermal clocks and unrelated JVM activity can affect the distribution.
- P95 wall-clock FPS and renderer GPU time must not be confused with simulation cost. The renderer remains legacy after M1.
- Test inputs are frozen `pyro_vfx:1` fixture JSON files; no visual or simulation-feature downgrade is intentionally allowed.
- Neither 100k/1M nor shader-mod compatibility is claimed by this milestone.
- M1 report JSon files were captured in a working tree before commit, so record the final source commit with this report.
