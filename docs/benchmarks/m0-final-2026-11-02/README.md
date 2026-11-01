# M0 — final real-client benchmark report

All metrics come from **three independent Minecraft client launches per scenario** after safety and lifecycle fixes, against the frozen three-run pre-M0 distribution. Fixed VFX seed and effect SHA-256, matching peak count, resolution, GPU and Minecraft version are verified for each comparison.

The run's embedded Git SHA is the repository HEAD when measured. If source edits were uncommitted during a run, that SHA alone does not identify the entire worktree; use the M0 completion commit together with these recorded reports.

| Scenario | Pre CPU median ms | M0 CPU median ms | Δ CPU | Pre P95 frame ms | M0 P95 frame ms | Δ P95 frame |
|---|---:|---:|---:|---:|---:|---:|
| sprite_1k | 1.077 | 1.028 | -4.6% | 11.53 | 11.29 | -2.0% |
| dynamic_1k | 1.129 | 1.070 | -5.3% | 11.40 | 11.80 | +3.5% |
| collision_1k | 1.445 | 1.424 | -1.5% | 11.81 | 11.78 | -0.2% |
| model_1k | 1.006 | 1.004 | -0.3% | 11.17 | 10.96 | -1.9% |
| sprite_10k | 10.579 | 10.227 | -3.3% | 22.87 | 22.42 | -2.0% |

## Tail latency, render submissions, allocations and GC

| Scenario | P95 sim pre → M0 (ms) | P95 render pre → M0 (ms) | Tick allocated M0 (MiB/tick) | GC collections (3 runs) | GC time (ms, 3 runs) |
|---|---:|---:|---:|---:|---:|
| sprite_1k | 1.364 → 1.652 (+21.1%) | 0.509 → 0.535 (+5.0%) | 6.42 | 22, 22, 18 | 98, 87, 91 |
| dynamic_1k | 1.742 → 1.835 (+5.4%) | 0.572 → 0.588 (+2.9%) | 6.56 | 5, 4, 6 | 26, 15, 20 |
| collision_1k | 2.736 → 2.303 (-15.8%) | 0.601 → 0.585 (-2.8%) | 7.52 | 14, 6, 8 | 48, 17, 32 |
| model_1k | 1.527 → 1.381 (-9.6%) | 1.543 → 1.409 (-8.7%) | 6.49 | 11, 5, 5 | 24, 20, 19 |
| sprite_10k | 14.605 → 14.383 (-1.5%) | 4.268 → 4.092 (-4.1%) | 64.17 | 44, 29, 27 | 88, 71, 62 |

## Interpretation and limits

- CPU/GC values measure JVM and renderer behavior on this one Windows hardware setup; three repeats are suitable for screening, not formal statistical significance.
- Thread allocation counters record Java bytes allocated by the render/tick thread during measured VFX callbacks; they do not identify allocation sites. GC collections and times are whole-JVM counters.
- `gpuTimeMs` is only the renderer GPU slice. Small microsecond differences are subject to timer quantization and scheduling noise; do not claim GPU speedups.
- Comparing P95 distributions against a three-run median can reveal possible regressions, not prove their cause. Investigate sustained >15% tail changes in M1 profiling.
- Neither 100k nor 1M was run. Explicit opt-in is required and can still run out of memory.
- `reload_active.json` proves an actual client resource reload survived while a VFX instance was active. A complete multiplayer/world-transition soak or shader-mod matrix was not performed.
- `budget_spike.json` checks two concurrent emitters requesting 40k particles total, capped at 20k within one effect.
