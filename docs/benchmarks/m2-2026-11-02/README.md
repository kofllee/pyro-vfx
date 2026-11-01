# Data-oriented CPU runtime: M1 comparison

Three independent actual Minecraft client starts per scenario, matching frozen JSON, seed, peak particle count, Minecraft version, GPU and resolution. Numbers are medians of reported per-run measurements, not an offline microbenchmark.

| Scenario | M1 CPU ms | Dense CPU ms | Δ CPU | M1 alloc MiB/tick | Dense alloc MiB/tick | Δ alloc | M1 P95 frame ms | Dense P95 frame ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| sprite_1k | 0.117 | 0.063 | -46.4% | 0.451 | 0.002 | -99.7% | 10.343 | 10.737 |
| dynamic_1k | 0.122 | 0.080 | -34.5% | 0.612 | 0.086 | -86.0% | 10.264 | 10.005 |
| collision_1k | 0.450 | 0.464 | +3.1% | 1.541 | 1.450 | -5.9% | 10.422 | 10.225 |
| model_1k | 0.074 | 0.074 | -0.3% | 0.528 | 0.528 | +0.0% | 9.991 | 9.920 |
| sprite_10k | 0.512 | 0.258 | -49.6% | 4.503 | 0.002 | -100.0% | 10.876 | 10.287 |

## Tail simulation and JVM GC

| Scenario | M1 P95 sim ms | Dense P95 sim ms | M1 GC collections | Dense GC collections |
|---|---:|---:|---|---|
| sprite_1k | 0.207 | 0.451 | 15, 17, 20 | 17, 18, 18 |
| dynamic_1k | 0.217 | 0.138 | 4, 1, 1 | 1, 5, 3 |
| collision_1k | 0.673 | 0.716 | 6, 5, 8 | 8, 6, 6 |
| model_1k | 0.143 | 0.132 | 4, 4, 6 | 6, 4, 6 |
| sprite_10k | 0.873 | 0.350 | 14, 10, 11 | 11, 14, 14 |

## Regression and safety client probes

- `event_order`: completed.
- `self_emit_collision`: completed.
- `collision_no_events`: completed.
- `budget_spike`: completed.
- `model_rotation`: completed.
- `model_inheritance`: completed.
- `reload_active`: completed.
- `curves`: completed.
- `events`: completed.
- `uv_animation`: completed.

## Limits and interpretation

- The dense CPU path currently covers sprite particles with static or dynamic collision-free motion, no particle collision triggers, no dynamic rotation and no environment lighting. Other feature combinations and model particles use the existing precise CPU path.
- The global CPU backend policy (auto feature routing or forced legacy) is recorded in each benchmark. Emitter-level selection follows VfxDenseSpritePool.supports(). The `benchLegacyCpu=true` Gradle flag forces the old backend for replay.
- Particle events remain synchronous per collision boundary: FIFO events are drained before the next particle, while self-emitted particles are placed in a bounded pending-spawn buffer until the current emitter update finishes.
- All Minecraft world/block access and rendering remain on the client thread. Worker-thread simulation is deliberately not enabled without isolated RNG, immutable parameter input and thread-safe render snapshots.
- JVM warmup, OS scheduling, Minecraft frame load and GPU driver variation limit causal interpretation of P95/frame metrics. No broad 100k/1M or shader-compatibility claims are made.
- Report JSON was captured before final commit; keep report and source revision together.
