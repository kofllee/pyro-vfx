# Sorted GPU alpha smoke — Minecraft A/B (2026-11-02)

14 completed Minecraft 1.21.1 NeoForge runs, 1280×720, uncapped, same scene SHA and seed. Two CPU RenderType baselines and two initial GPU instancer runs per count; one additional optimized stable radix-sort run at 10k/100k. All use real GPU timer queries and GL state audit.

| Alpha smoke particles | CPU RenderType submission (median, ms) | Initial GPU sorted submission (median, ms) | Radix GPU submission (ms, one run) | P95 frame baseline (ms) | P95 radix (ms) |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1,000 | 0.281 | 0.107 | — | 1.721 | — |
| 10,000 | 2.944 | 0.936 | 0.680 | 6.557 | 3.33 |
| 100,000 | 34.054 | 12.479 | 9.593 | 49.16 | 22.256 |

The heavy 100k case improved CPU submission about **3.55x** against the old renderer (single radix run versus two baselines); the original comparator achieved about **2.73x** with two runs. Median FPS in the 100k baseline was ~23; the radix candidate ~87. Runtime CPU simulation remains CPU Dense: this change optimizes blending, ordering and draw submission, not simulation. The GPU timer for the single 100k radix run was 2.80 ms versus 2.34 ms baseline, so **GPU-time improvement is not established** in the heavy case. P99 frame was ~55/54 ms before and ~25 ms in the radix run.

## Semantics and limitations

- Alpha particles are sorted across eligible emitters by projected camera depth, stable on ties, and submitted in contiguous material groups using OpenGL 3.3 instancing; no per-particle Java render objects. Additive retains its separate batching. GPU smoke uses the **same straight-alpha blending** as the supported Minecraft path, with depth test enabled and depth writes disabled.
- Compatibility: unsupported facing modes, world-lit sprites, Iris/Oculus/Embeddium/Sodium, unavailable GL 3.3, and explicit legacy override retain Minecraft RenderType. No WBOIT, framebuffer replacement, shaderpack integration or external translucent world-depth sorting was added.
- The engine's VFX alpha pass renders after queued Minecraft buffers. It sorts particle **centers**, not individual overlapping fragments or triangles; cross-mod global alpha ordering is not guaranteed. WBOIT is a possible later quality option only with a measured need and correct interaction with Minecraft's depth/compositing pipeline.
- Identical source hashes and seed do not guarantee pixel identity across independent clients. A 10k baseline-vs-GPU screenshot pair differed in 1.50% of full-frame pixels; the 100k pair differed in 82.48%, dominated by a sky/environment change between captures. Visual inspection showed the same smoke sphere/landscape, but **a synchronized same-frame smoke-only image difference is still needed** to verify strict blend parity. This is an explicit limitation.
- Bench JSON was captured before the commit and embeds the previous HEAD. Full reports include FPS, p95/p99, CPU/GPU, particles, memory and spawn counters. Hardware used: NVIDIA RTX 4090 Laptop.

## Reproduction

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario 'sprite_alpha_smoke_1k,sprite_alpha_smoke_10k,sprite_alpha_smoke_100k' -Width 1280 -Height 720 -Uncapped -Heavy -RenderStateAudit
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario 'sprite_alpha_smoke_1k,sprite_alpha_smoke_10k,sprite_alpha_smoke_100k' -Width 1280 -Height 720 -Uncapped -Heavy -ForceLegacyRender -RenderStateAudit
```
