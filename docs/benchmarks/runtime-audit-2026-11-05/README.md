# Runtime rendering audit: 5 November 2026

Source: real Minecraft 1.21.1 / NeoForge 21.1.229 client on Windows 11, NVIDIA RTX 4090 Laptop, OpenGL 4.6; 1280×720, vsync off. The working checkout had **uncommitted fixes on top of `82e88a9`** when these tests were run: reports record that Git commit, but it is *not* the full working-tree content. Do not compare these samples to M0–M7 historical checkpoints as matched A/B optimization evidence.

All six results recorded `status=completed`, 100 measurement ticks and 4,000 frame samples. The launcher used a secondary monitor and `-RenderStateAudit`. No active shaderpack or graphics integration mod was present in these samples. One successful run does not prove every GL failure or shaderpack integration path.

| Scenario | Backend / renderer | Frame P95 / P99 | CPU simulation median | Render submission median | Evidence |
|---|---|---|---|---|---|
| `analytic_uv_1k` | GPU Analytic sprite, 1,000 instances | 1.157 / 2.420 ms | 0.0639 ms | 0.0410 ms | [JSON](analytic_uv_1k-20261105-135719.json) |
| `model_1k` | GPU Analytic model, 1,000 instances | 1.496 / 2.549 ms | 0.0638 ms | 0.0473 ms | [JSON](model_1k-20261105-135931.json), [screenshot](model_1k-gpu.png) |
| `sprite_horizontal`, GPU | GPU Analytic sprite | 1.545 / 2.701 ms | 0.0618 ms | 0.0409 ms | [JSON](sprite_horizontal-20261105-140004.json), [screenshot](sprite_horizontal-gpu.png) |
| `sprite_horizontal`, forced RenderType | CPU Dense + Minecraft sprite RenderType | 1.674 / 3.130 ms | 0.1028 ms | 0.2592 ms | [JSON](sprite_horizontal-20261105-140143.json), [screenshot](sprite_horizontal-render-type.png) |
| `sprite_rotating_1k` | CPU Dense + GPU additive sprite instancing, 1,000 instances | 1.314 / 3.175 ms | 0.1698 ms | 0.0771 ms | [JSON](sprite_rotating_1k-20261105-140325.json) |
| `sprite_alpha_smoke_1k` | CPU Dense + GPU depth-sorted alpha instancing, 1,000 instances | 1.411 / 3.246 ms | 0.1531 ms | 0.1326 ms | [JSON](sprite_alpha_smoke_1k-20261105-140359.json), [screenshot](sprite_alpha_smoke-gpu.png) |

**Visual comparison:** GPU vs forced RenderType `sprite_horizontal` used identical effect SHA-256 `8290c33ac5020ba4f51c04760f5771f45ec4e9883678fa6d301b6addb448f2ee`, seed `1353719846`, requested particle count and image resolution. `tools/compare-bench-images.ps1` reported **279 / 921,600 different pixels (0.0303%)**, mean absolute RGB channel error **0.000335** (0–255 scale), max per-pixel RGB sum difference **120**. Screenshots are extremely close, but not a pixel-identical reference across all camera angles and particle ages.

GPU state-audit runs completed without a reported state leak. However, the `gpuSamples` field was 0 for the first run despite runtime telemetry samples: no validated elapsed-GPU-query number should be inferred from that test. Frame times are whole render-stage intervals / wall-clock measurements, not isolated GPU time.

## Reproduce

From repo root, on a Windows machine with a second display and test save `Test VFX`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario analytic_uv_1k -Width 1280 -Height 720 -Uncapped -RequireGpuAnalytic -RenderStateAudit -GpuTelemetry
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario model_1k -Width 1280 -Height 720 -Uncapped -RenderStateAudit -GpuTelemetry
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario sprite_horizontal -Width 1280 -Height 720 -Uncapped -RenderStateAudit
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario sprite_horizontal -Width 1280 -Height 720 -Uncapped -ForceLegacyRender -RenderStateAudit
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario sprite_rotating_1k -Width 1280 -Height 720 -Uncapped -RequireGpuInstancing -RenderStateAudit
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario sprite_alpha_smoke_1k -Width 1280 -Height 720 -Uncapped -RequireGpuInstancing -RenderStateAudit
powershell -NoProfile -ExecutionPolicy Bypass -File tools/compare-bench-images.ps1 -Baseline <new-gpu-report.json> -Candidate <new-render-type-report.json>
```

These scripts write full working-run output to gitignored `run/pyrovfx-benchmarks/`. The selected JSON and screenshots in this folder are snapshots of that output. Reports include the original screenshot absolute paths, which are specific to the test machine; use the neighboring retained PNG files instead for archival review.
