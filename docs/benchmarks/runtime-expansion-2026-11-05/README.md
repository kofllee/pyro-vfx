# Runtime feature expansion: real-client evidence, 5 November 2026

Environment: Minecraft 1.21.1, NeoForge 21.1.229, Java 21, Windows 11, RTX 4090 Laptop, 1280x720, VSync off, second monitor. All runs used the graphical client and `-RenderStateAudit`. **No shaderpack was enabled**, so this is not Iris/BSL/Photon compatibility certification.

**Provenance warning:** These runs used a modified working tree. The JSON `environment.gitCommit` reports the last committed parent `437a303`, not the source including the then-uncommitted changes. Thus these records reproduce the *effective fixtures, seed, hardware, and rendering settings*, but do not establish a complete clean-checkout Git SHA. Later matched A/B tests should use clean, named commits. The alpha/rotation “before” runs were collected earlier on the same day before these changes; these are one run per condition and should not be presented as proof of a causal FPS improvement.

## Rendering and correctness

| Scenario | Actual renderer | Samples / outcome | Frame P95 | Source |
|---|---|---|---:|---|
| World-lit sprites, GPU | GPU additive instancing, 1,000 instances | Completed, 1 draw; 100 ticks / 4,000 frame samples | 1.304 ms | [report](sprite_lit_fallback-20261105-144717.json), [image](lit-gpu.png) |
| World-lit sprites, forced RenderType | CPU Dense / Minecraft RenderType | Completed; same effect SHA and seed as GPU | 1.754 ms | [report](sprite_lit_fallback-20261105-145002.json), [image](lit-cpu.png) |
| CPU Dense translucent models | Minecraft model RenderType (Dense simulation) | Completed without model snapshots in the render loop; 1,000 particles | 3.343 ms | [report](model_translucent_1k-20261105-144750.json) |
| GPU/CPU museum side-by-side | Both backend paths in the combined exhibit | Completed: GPU analytic particle submissions and CPU Dense path both observed | 6.019 ms | [report](museum_gpu_sprite-20261105-144819.json) |
| Continuous GPU Analytic | GPU Analytic | Completed, 130 resident birth uploads, up to 7,800 particles | 1.408 ms | [report](analytic_steady-20261105-145035.json) |
| LOOPING instant | GPU Analytic | Completed, **13** resident datasets, 200 peak particles | 1.377 ms | [report](analytic_looping-20261105-145606.json), [image](looping-gpu.png) |
| MANUAL via effect events | GPU Analytic | Completed, **9** resident datasets, 150 peak particles | 1.350 ms | [report](analytic_manual-20261105-145638.json), [image](manual-gpu.png) |

**Matched lit-sprite image comparison:** `tools/compare-bench-images.ps1`, same `effectSha256=d58e74aada5b866b8868b878a04f3dada416f4f181b6f371d45bef4b8d7f4cc8` and seed `1353719846`: **256 / 921,600 changed pixels (0.0278%)**; mean absolute RGB channel error `0.000979` (0-255). The client GL-state audit finished without reporting a leak. This is *one* daylight scene, not proof for caves, colored lights, world motion, or shaderpacks.

## Single-run before/after observations (no statistical speedup claim)

| Unchanged scenario / SHA and seed | P95 frame before / after | CPU render submission median before / after | Image differences |
|---|---:|---:|---:|
| `sprite_alpha_smoke_1k` | 1.411 / 1.314 ms | 0.1326 / 0.1349 ms | 85/921,600 pixels (0.0092%) |
| `sprite_rotating_1k` | 1.314 / 1.113 ms | 0.0771 / 0.0747 ms | 53/921,600 pixels (0.0058%) |

These runs preserved the effect SHA-256, seed, 1280x720 window and GPU environment. Their code and clock provenance are not pinned to two clean revisions; even the render-submission comparison is mixed (alpha slightly slower). Do not infer a performance win. Full reports: [alpha before](sprite_alpha_smoke_1k-20261105-140359.json), [alpha after](sprite_alpha_smoke_1k-20261105-145134.json), [rotating before](sprite_rotating_1k-20261105-140325.json), [rotating after](sprite_rotating_1k-20261105-145205.json).

## Benchmark verifier and remaining acceptance

`python tools/verify_m5.py` now selects a coherent archived same-commit M5 session and **passes** for historic commit `946d3c8` (2 November). `python tools/verify_m5.py --after 20261105-000000` intentionally returns `INCOMPLETE` (exit 2): no new complete M5 campaign was produced. `python tools/test_verify_m5.py` has three passing selection-policy tests. The archival verifier does **not** certify present-day engine revisions.

New GPU Analytic birth-time expressions and per-cohort parameter lifetime have 2 new dedicated GameTests (overall **93/93** required tests passed). Actual real-client LOOPING/MANUAL repeated births now have concrete GPU reports above; their CPU forced-backend screenshot parity and event causality across varying runtimes are still pending. Dynamic light probes, long runs, multi-driver comparisons, active shaderpacks, cross-mod compositing and full 35-stand visual coverage are still outstanding.
