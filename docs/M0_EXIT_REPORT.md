# M0 exit report — Pyro VFX

**Status:** Acceptance criteria met; M1 may begin after this report is committed. M1–M5 have not started.

Target: Minecraft 1.21.1 / NeoForge 21.1.229 / Java 21. Reproducible client environment: Windows, NVIDIA RTX 4090 Laptop GPU, 854×480. All five final performance scenarios were executed **three times in independent Minecraft client launches**, in addition to three frozen pre-M0 launches per scenario.

## 1. Runtime safety — passed

- One shared **per-effect** work budget applies across emitters and event branches: 4,096 evaluated event nodes/tick, 20,000 spawned particles/tick, 20,000 tracked active particles per effect. Recursive event depth remains independently capped at 16. The budgets are safety limits, not performance targets.
- All normal instant/steady/manual spawns and event `emit` go through the same spawn reservation. A sequence of >6,000 nested event references stops after the shared event budget; the behavior is checked against the exact resulting parameter value in headless GameTests.
- A real client test `budget_spike` requests 20,000 particles from each of two emitters and observes exactly 20,000 in total (`status=completed`).
- The previous dev-only heavy opt-in `-PbenchHeavy=true` remains available; it lifts particle caps for isolated high-volume experiments only. It does **not** guarantee 100k/1M will succeed. Ordinary effects do not bypass caps.
- Resource parser errors are logged; a malformed definition retains the previous valid definition when available.

## 2. Behavioral golden tests — passed

Seeded headless GameTests assert **exact per-tick** values for:

- Dynamic position: +0.1 blocks on X per tick for 2 blocks/second, zero drag and zero acceleration.
- Dynamic Y rotation: +1 degree per tick for 20 degrees/second.
- Animated UV rectangle: four frames, `u0 = 0.25 / 0.50 / 0.75` at particle ages 1/2/3, with precise bounds.
- Particle expiration after its fourth tick.
- Effect creation trigger at tick 0; timeline trigger at age 1; expiration trigger at age 3, fired only once.
- Nested `set_param` sequence, lifetime modes, eight spawn shapes, expression curves, and more.

Graphical RED/GREEN fixture evidence exists for reentrant self-emission, rotated model geometry, inherited model elements and sequential event parameter visibility in `docs/benchmarks/m0-2026-11-01/`.

## 3. Reload and world lifecycle — passed for defined scope

- `VfxRegistry` publishes immutable snapshots rather than exposing partially updated definitions; loader stages the next snapshot.
- A headless test performs a valid initial load, a malformed-JSON reload with fallback to the last valid definition and another valid effect, then removal of a missing resource. A pre-existing `ClientVfxInstance` retains its original definition and can tick after reload.
- Actual Minecraft graphical client test `reload_active` initiates `Minecraft.reloadResourcePacks()` while a VFX instance is active. The test reports `status=completed`, `reloadCompleted=true` and the instance retains its pre-reload definition.
- Model-geometry sampler caches are cleared on resource reload.
- `ClientVfxManager` clears instances on `ClientLevel` identity changes, including world-to-world transitions where neither world is null; a headless identity test checks the transition cases.

**Unverified coverage:** a real dimension/world transition soak, multiplayer-specific behavior, shader-mod compatibility and repeated live editing of resource packs are not included in M0's automated test matrix. They are future compatibility tests, not demonstrated passes.

## 4. Final baseline, CPU allocations, GC and P95 — recorded

Full report and 15 raw client reports: `docs/benchmarks/m0-final-2026-11-02/`. Automated source-to-source comparison: `python tools/summarize_m0.py --prefix 20261101-221 --output docs/benchmarks/m0-final-2026-11-02`.

| Scenario | Pre-M0 median simulation (ms) | M0 median simulation (ms) | M0 P95 frame (ms) | M0 allocations per tick (MiB) |
|---|---:|---:|---:|---:|
| sprite_1k | 1.077 | 1.028 | 11.29 | 6.42 |
| dynamic_1k | 1.129 | 1.070 | 11.80 | 6.56 |
| collision_1k | 1.445 | 1.424 | 11.78 | 7.52 |
| model_1k | 1.006 | 1.004 | 10.96 | 6.49 |
| sprite_10k | 10.579 | 10.227 | 22.42 | 64.17 |

Values are medians of three client-run aggregate metrics, not evidence of causal speedup. The very high Java allocation volume at 10k (about **64 MiB/tick**) and GC pressure are major M1 optimization targets. The legacy per-particle expression/context creation and intermediate objects warrant allocation-site profiling; this source-level suspicion is not a JFR-measured attribution.

Earlier `model_1k` P95 concerns did not reproduce in the final three-run comparison: P95 simulation was 1.527 ms before M0 and 1.381 ms after M0 (−9.6%). The `sprite_1k` P95 simulation increased from 1.364 ms to 1.652 ms (+21.1%), while its frame P95 improved from 11.53 to 11.29 ms. Keep this tail anomaly in the M1 profiling backlog; current measurements cannot attribute its cause.

GPU timing covers the VFX renderer slice only, not a full Minecraft frame. No 100k/1M, 30-minute soak, shader compatibility matrix or formal statistical significance claim is made. Final reports were captured with source edits not yet committed; their embedded Git SHA alone is insufficient as a binary-identity guarantee. The completion commit must be recorded and retained with these reports.

## M0 decision

The four agreed M0 deliverables are complete enough to advance without prematurely redesigning the renderer. The next stage is **M1**, using the frozen behavior tests and recorded baselines to measure actual architectural changes. Do not claim any M1 implementation or result on the basis of this report.
