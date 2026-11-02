# Performance and capacity — M5

Pyro VFX is a hybrid Minecraft 1.21.1 / NeoForge VFX engine with multiple runtime routes. Do not compare particle counts without matching effect definitions, GPU, driver, resolution, rendering mode, world and seed.

## Measured backend progression

| Milestone | Measured result (real client, RTX 4090 Laptop, 854×480) |
|---|---|
| M1 compiled expressions | 10k simple sprite CPU simulation median 10.227 → 0.512 ms (relative to M0) |
| M2 dense CPU sprites | 10k simple sprite CPU simulation median 0.512 → 0.258 ms (relative to M1) |
| M3 GPU instancing | 10k sprite rendering CPU median 2.519 → 0.448 ms (relative to original renderer) |
| M4 GPU analytic | 10k sprite simulation CPU median 0.260 → 0.026 ms (relative to M3) |
| M4 1M analytic additive sprites | 0.009 ms CPU simulation, 1.004 ms VFX GPU, 16 draws; full-frame P95 9.839 ms |

All figures are documented three-launch medians in `docs/benchmarks/` and M1–M4 verification reports. Full-frame P95 includes the whole Minecraft environment; VFX GPU timing is not whole-frame GPU time. The 1M result is a narrow high-end-hardware, simple additive effect demonstration, not a universal particle performance target.

## Backend routing

- `gpu_analytic`: stable initial-state additive sprites with supported constant appearance and closed-form motion, no per-particle CPU simulation after spawn. Initial 24-byte/particle data is also retained on the CPU for reload/fallback. Runs only where the GL 3.3+ and renderer compatibility probe succeeds.
- `cpu_dense`: allocation-conscious SoA for eligible sprites, with normal Minecraft-compatible render fallback as needed.
- `cpu_legacy`: collision, event, model, alpha/lit, mutable/expression-heavy cases outside the accelerated capabilities.
- GPU instanced sprite/baked model rendering is separately capability-gated; shader/optimization mods activate a conservative fallback policy.
- GPU Compute is **not implemented**: no measured workload currently justifies the extra driver and synchronization complexity. See `M5_DECISION_AND_VERIFICATION.md`.

## Capacity and overload

Ordinary clients enforce **256 active effects**, **80k active Pyro-managed particles**, **30k spawns/tick** and **8,192 event evaluations/tick**, in addition to the existing **20k active/spawns per-effect** and **4,096 events per-effect/tick**. Limits reject newer requests deterministically. Dedicated benchmark-only opt-in raises particle limits to 1M; it is not available in production.

The custom-particle active budget excludes vanilla particles already handed to Minecraft; this is a compatibility boundary rather than an end-to-end RAM cap. Bound CPU work separately for expensive collisions/models; the aggregate particle cap alone does not make 80k complex effects cheap. No separate per-render-type or byte-level VRAM limits are implemented.

View live backend selection and global counters with `/pyrovfx backend`; benchmark JSON includes admission counts, CPU simulation, render submission, GPU queries, heap, GC, allocations and frame intervals.

## Running verified tests

```powershell
.\gradlew.bat clean build runGameTestServer --no-daemon
python tools/test_compare_bench.py
.\gradlew.bat runClientBench '-PbenchScenario=global_budget_spike,analytic_dynamic_10k,collision_1k,reload_active,budget_spike' -PbenchGlobalStress=true -PbenchRenderStateAudit=true
```

The new global stress fixture launches three independent 20k effects and validates **30k admitted / 30k rejected** with the normal global limit. See `docs/M5_DECISION_AND_VERIFICATION.md` and `docs/TEST_HARNESS.md` for the opt-in rules and results. Actual NVIDIA measurements are not evidence of comparable non-NVIDIA driver behavior.

## Outstanding performance gates

Before production use, gather repeated A/B 1080p and 4K tests, alpha-overdraw/complex-model/collision and event-heavy workloads, 30-minute memory/GC soaks, dimension cycling, actual shader-mod versions, other GPUs and multiplayer/dedicated-server TPS. The 1M count should remain an explicit benchmark scenario, not a default gameplay budget.
