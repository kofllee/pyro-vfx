# M0 client benchmark comparison

Three independent client starts per workload before and after the self-emission fix. Same effect SHA-256 and expected particle counts for every matched workload. The post-fix sample uses the version with unconditional `removeIf`; the subsequent conditional-compaction variant has separate validation.

| Scenario | Pre CPU median of runs, ms | Post CPU median of runs, ms | Delta | Pre frame P95, ms | Post frame P95, ms | Delta |
|---|---:|---:|---:|---:|---:|---:|
| sprite_1k | 1.077 | 1.051 | -2.4% | 11.53 | 11.45 | -0.7% |
| dynamic_1k | 1.129 | 1.098 | -2.7% | 11.40 | 11.47 | +0.6% |
| collision_1k | 1.445 | 1.376 | -4.8% | 11.81 | 12.27 | +3.9% |
| model_1k | 1.006 | 1.010 | +0.4% | 11.17 | 11.38 | +1.9% |
| sprite_10k | 10.579 | 9.936 | -6.1% | 22.87 | 21.59 | -5.6% |

M0 single-run comparator flagged P95 outliers (especially model render P95); post-fix model render P95 is higher on its 3-run median despite nearly unchanged median render submission. This is **unresolved**, not evidence of an overall speedup. Small relative GPU changes in the 1?10 microsecond regime are quantization/noise-sensitive. No claim of GPU gain.

The RED test demonstrates `ConcurrentModificationException` when on_collision emits into the same emitter. Both GREEN tests complete with `collisionEventObserved=true`. No 100k/1M test was run.
