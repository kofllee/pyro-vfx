# GPU-resident 3D model simulation — real Minecraft A/B, 2026-11-02

Twelve real Minecraft 1.21.1 NeoForge client reports: three independent client launches for each scenario/backend. Windows 11, RTX 4090 Laptop, 1280×720, uncapped, identical VFX JSON SHA-256, seed 1353719846, GPU driver and test world. `status=completed` for all 12. GPU model draw counts and live particle counts confirmed 10,000 or 100,000. GL state audit was enabled. Forced CPU comparator used `-ForceCpuAnalytic`.

| Scenario | Backend | CPU simulation median (ms) | CPU render submission median (ms) | GPU VFX median (ms) | Full-frame P95 median (ms) |
|---|---|---:|---:|---:|---:|
| Model 10k | GPU-resident analytic | 0.047 | 0.039 | 0.267 | 1.527 |
| Model 10k | CPU Dense + instancing | 0.448 | 0.638 | 0.280 | 2.522 |
| Model 100k | GPU-resident analytic | 0.044 | 0.066 | 3.363 | 5.424 |
| Model 100k | CPU Dense + instancing | 4.442 | 5.756 | 4.963 | 13.941 |

The GPU-resident backend extends existing analytic per-cohort birth-state buffering to unlit baked models. OpenGL 3.3 vertex shader reads a one-time 24-byte position/velocity payload per particle; trajectory advances via a shared-age closed-form recurrence. No per-frame model instance streaming and no GPU Compute. Supported: non-translucent custom/block/item baked models with constant opaque colour, supported shared-age scale, no particle rotation/collision/events. Unsupported combinations keep CPU Dense / Legacy and ordinary instanced or Minecraft rendering. Geometry bake/fallback is retained.

## Visual comparison

`tools/compare-bench-images.ps1` verifies matching scenario, SHA, seed and particle count, then compares captured PNGs channel by channel. For model 10k (`165307` CPU vs `165104` GPU) only 842 of 921,600 pixels differ (0.0914%, mean absolute RGB channel difference 0.00978/255). For model 100k (`165315` CPU vs `165112` GPU), 1202 pixels differ (0.1304%, mean difference 0.00608/255); a second same-scene pair (`165437` CPU vs `165234` GPU) differed by 4754 pixels (0.5158%). Screenshots viewed in Minecraft show the same model sphere, but no assertion of pixel identity: independent starts differ in sky/time/environment and float evaluation.

## Reproduction

```powershell
1..3 | ForEach-Object { powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario 'model_10k,model_100k' -Width 1280 -Height 720 -Uncapped -Heavy -RenderStateAudit }
1..3 | ForEach-Object { powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario 'model_10k,model_100k' -Width 1280 -Height 720 -Uncapped -Heavy -ForceCpuAnalytic -RenderStateAudit }
powershell -NoProfile -ExecutionPolicy Bypass -File tools/compare-bench-images.ps1 -Baseline run/pyrovfx-benchmarks/model_100k-20261102-165315.json -Candidate run/pyrovfx-benchmarks/model_100k-20261102-165112.json
```

Archived reports were measured from an uncommitted implementation and consequently identify the preceding commit in their `environment.gitCommit`. The completion commit records the verified source changes. These samples are not full soak, shader-mod, non-NVIDIA or 4K quality guarantees.
