# Rendering and optimization backlog

Updated 2026-11-05. Scope: development checkout of Pyro VFX on top of `dfd23a2`; the remote `origin/main` was `6127008` when the audit started. **Do not confuse code review, server GameTests, GPU visual acceptance and matched performance evidence.** This is an ongoing engineering ledger, not a claim that every backend combination is solved.

## 6 November 2026: native bridge cutover

The former custom fragment/instancing renderer references below are **dated
historical investigations**, not the current production architecture.
The source of truth is [Native Render Bridge](NATIVE_RENDER_BRIDGE.md).
Rendering now uses native Minecraft ShaderInstance after GPU Transform
Feedback; per-material pass selection replaces glass/water raycast heuristics
and cloud-depth-cover. Remaining unverified scope includes long-duration
shaderpack switching, multi-driver perf, and pixel-exact transparency ordering
across external mods. Do not revive old shader paths for these cases.

## Third implementation pass (5 November 2026) — feature parity and cohort batching

- **Dense collision callbacks** are now executed synchronously at each particle boundary rather than globally deferred. `on_collision` may run through CPU Dense while preserving `set_param` ordering and self-emission into the same pool. Legacy and Dense produced the **same 102,006 contacts**, plus identical `self_emit_collision` admitted spawns/events (16/45), and a counter-based ordering fixture matched **6,495 events and param increments** on both. Workloads may be faster *or slower* in single-run frame samples.
- **GPU shared-age parametric motion** uses a single per-cohort uniform offset after evaluating expressions in CPU once per draw. Birth data remains resident; CPU Dense/GPU screenshot parity in the 1,000-sprite orbit was **0.0205% changed pixels**. Per-particle/random/world-dependent motion still uses CPU.
- **GPU shared angular rotation for sprites** accepts the same constant dynamic angular coefficients as resident models. The shader rotates corners around the billboard basis, matching the forced CPU path at **0.0073% changed pixels**. Randomized individual rotations still require CPU.
- **Same-tick burst coalescing** appends to the latest unuploaded GPU cohort only when age, motion/lifetime plan and source pose are identical. A four-event manual fixture allocated **2** birth datasets, rather than one per event, because the last event occurred in a later tick. Separate source/lifetime/age restrictions have a GameTest.
- **Museum cross-path comparison** switched its CPU branch from a now-GPU-eligible zero-spin rotation to a mutable color parameter. The physical test fixture succeeded with `-MuseumBuild`; still no full 35/35 stand audit. A previous museum benchmark's external Windows-focus error is not counted as GPU correctness failure.
- **Verification:** 96/96 required GameTests and release-JAR check passed. Source/test changes and attached `runClientBench` JSON/PNG are cataloged in [runtime-extensions evidence](benchmarks/runtime-extensions-2026-11-05/README.md). They were generated from an uncommitted working tree; one pass per profile, not statistically significant speedup evidence.

## Second implementation pass (5 November 2026)

- **GPU Analytic birth values:** `VfxAnalyticPlan` now allows non-model spawn-shape expressions and initial speed/custom direction expressions. Shape and velocity are evaluated on CPU once per birth, packed into resident state, and compared against CPU deterministic sampling with the same RNG sequence in `analyticBirthExpressionsMatchCpuSamplingAndRandomStream`. Per-emitter/parameter **lifetime is frozen per birth cohort**, with different life spans covered by `analyticEmitterBirthLifetimeKeepsIndependentCohortAges`. Individual randomized life spans, per-particle/world-dependent parametric motion and mutable drag/acceleration still require CPU simulation.
- **Lightmap GPU instancing:** `VfxDenseParticlePool.writeInstances` samples the actual Minecraft packed world light through the shared render-frame cache, uploads two light coordinates per particle, and `VfxInstancedSpriteRenderer` samples texture unit 2 only for world-lit batches. RGB/alpha state, both texture bindings and lightmap shader selection are restored. **This is GPU rendering with CPU Dense simulation, not GPU-resident analytic lighting.** Same-effect/seed daylight GPU-vs-RenderType screenshot difference: 256/921600 pixels (0.0278%). Shaderpacks, caves and colored-light parity unverified.
- **Dense model fallback:** BLOCK/ITEM/CUSTOM RenderType models now read a reusable SoA cursor rather than allocating a `ClientVfxParticle` snapshot per frame. ENTITY still uses Legacy and an independent model entity per particle. One translucent model client benchmark completed; no before/after allocation or FPS comparison isolates the effect.
- **Repeated births:** `analytic_looping` and `analytic_manual` are now two real-client fixtures with 13 and 9 GPU birth-state uploads and nonzero draws. Continuous `analytic_steady` completed with 130 uploads. All used GL-state audit, but CPU-forced timing/visual parity for every birth schedule is not proven.
- **M5 verification:** `tools/verify_m5.py` no longer treats all 731 historical reports as one benchmark cohort. It selects a complete same-commit 15-minute suite, returns `INCOMPLETE` for absent recent coverage, and has three selection-regression tests. Historical 2 November M5 session passes; **a new complete current-code M5 session does not exist**.
- **Museum:** current gallery contains 35 stands with sub-emitter comparisons (7 catalog categories). The `gpu_sprite` stand retains genuinely different GPU Analytic/CPU Dense backend routes after the birth-expression eligibility change. One standalone museum client scenario completed; **35/35 full visual reacceptance was not performed**.

Evidence and limitations: [same-day client expansion reports](benchmarks/runtime-expansion-2026-11-05/README.md). The second server suite has **93/93 required GameTests passed**, including two new birth/lifetime tests. A passing GL-state audit and short screenshot comparison do not establish shaderpack or multi-driver correctness.

## Fixed & Verified (specific evidence)

| Issue | Cause | Change | Verification | Residual |
|---|---|---|---|---|
| Steady emitter replays capped emission | `spawnAccumulator` subtracted only admitted births, keeping rejected integer debt | `ClientVfxEmitter.tickSteadySpawnAmount` now advances fractional phase but drops births that exceed `maxParticles`; non-finite rates do not poison the accumulator | `saturatedSteadyEmissionDoesNotAccumulateDelayedBirthDebt`, dedicated GameTests | Long-running, highly variable rates still need real-client profiling |
| Unnecessary GPU Analytic rejection of `LOOPING`/`MANUAL` lifetimes and `MANUAL` spawn mode | Eligibility conflated CPU-side scheduling and GPU-side particle simulation | `VfxAnalyticPlan.analyse` permits compatible schedules; each burst has independent cohort birth age | `analyticSchedulingAcceptsLoopingAndManualBirthCohorts`, dedicated GameTests (routing policy) | Repeated manual and LOOPING GPU cohorts have actual client uploads and draws; CPU-forced pixel parity for each schedule remains open |
| Empty primary analytic cohort could be removed before delayed birth; later emit could reuse an aged/removed cohort | Primary cohort was inserted into the active list in constructor, including while empty; subsequent births reused same primary | `ClientVfxEmitter` now enrolls primary cohort only on first spawn and creates subsequent independent cohorts | Code-path inspection, compilation and 91/91 server GameTests | Repeated-manual and LOOPING GPU client scenarios passed; a specifically delayed-primary-burst visual case remains open |
| `WORLD_X`/`WORLD_Y` sprites denied GPU path | Missing basis vectors and incomplete facing eligibility despite corresponding CPU transforms | Added facing basis to instanced and analytic renderers, and plan/geometry eligibility | `analyticWorldPlaneFacingMatchesSupportedSpriteModes` checks analytic eligibility; compilation and dedicated GameTests | Client screenshot comparison for both orientations required |
| Delayed server lease tests sometimes failed despite correct UUID cleanup | Two tests compared the shared `ServerVfxNetwork.liveCount(level)` across a 5-tick delay while 91 GameTests concurrently created/stopped unrelated effects | `VfxUnifiedApiGameTests.levelPositionAndBlockOverloadsUseServerLease` and `VfxNetworkGameTests.remoteEffectExpiresWithoutClientOrParticleSimulation` now check their own UUID handles after the delay; same-tick population checks remain | One observed false failure, then **3 consecutive 91/91 GameTest suites** passed after the fix | Test harness fix, not a claim that production lease scheduling changed |

Latest prior verification: **three consecutive suites of 91/91 required GameTests passed** using `runGameTestServer verifyReleaseJar --offline --no-daemon` on 2026-11-05; Gradle exited 0 for all three. This is not an OpenGL framebuffer, FPS, shaderpack, or GPU-driver test.

## Implemented, client verification pending

- `VfxInstancedSpriteRenderer.GlState` now captures, restores and audits **RGB and alpha blend equations**, not just blend factors. Instanced sprites, analytic sprites and instanced additive models explicitly set `FUNC_ADD` while drawing. Dedicated GameTests cannot test an actual GL context. Verify using `-PbenchRenderStateAudit=true` and mixed alpha/additive overlays on a graphical client.
- Instanced `WORLD_X`/`WORLD_Y` bases match CPU `Axis.YP(90)`/`Axis.XP(90)` orientation by inspection. Must compare camera movement, particle rotation and pixel footprints with forced RenderType.
- Active Iris shaderpacks **do not** automatically disable GPU paths. This is the established project policy and aligns with user-observed visual improvements. Capability failures and explicit user/debug overrides may still select safe fallback. There is no new per-shaderpack GL or screenshot verification in this audit; vanilla GPU/RenderType parity and GPU state-audit runs are archived in [5 November client evidence](benchmarks/runtime-audit-2026-11-05/README.md).
- Three shader-program initialization paths (`VfxInstancedSpriteRenderer.initialize`, `VfxInstancedModelRenderer.initialize`, `VfxAnalyticSpriteRenderer.prepare` / `appearanceProgram`) now release already compiled shader objects and unsuccessful program links even when the second shader fails. Both resident GPU VBO uploads likewise delete newly allocated buffers on upload exceptions. These are confirmed code-level leak paths; injected GL failures and reload/error recovery need real-client tests.
- `VfxInstancedModelRenderer.renderDense` and `render` no longer silently suppress the current frame when initialization fails **before** any draw is attempted. They return false to allow an immediate Minecraft fallback. If a GPU draw was already attempted, they conservatively avoid double-submitting particles. The latter path intentionally cannot guarantee partial-batch visibility on a failed GL driver.

## Optimized & Measured

For a newly allocated GPU analytic cohort, `VfxAnalyticParticlePool.spawn` previously reserved **4,096 bytes** of direct memory for even a single 24-byte particle. The initial minimum has been reduced to **256 bytes** (16x smaller for one-particle cohorts); 20 regular particles allocate **480 bytes** rather than 4,096 bytes. The `analyticCapabilitySelectionPreservesFallback` GameTest asserts the compact capacity and correct growth. These are deterministic **native allocation-capacity** comparisons, not measured frame-time/GC/driver-memory improvements.

**No matched before/after-code-change performance benchmark has been recorded for these fixes.** A matched GPU-vs-RenderType **backend comparison** of `sprite_horizontal` is archived in [the 5 November runtime audit](benchmarks/runtime-audit-2026-11-05/README.md) with identical effect hash, seed and resolution. The GPU and RenderType samples are single runs, not a statistically robust speedup claim. Historical M0–M7 figures remain in [PERFORMANCE.md](PERFORMANCE.md), with their original hardware and sample caveats.

Required future paired test: record pre-change commit and patched commit with identical effect hashes, seeds, window geometry, graphical settings, hardware and GPU profiles. Capture CPU simulation, CPU render submission, GPU query time, P95/P99 frame, allocation/GC, draw calls, live cohorts, buffers, uploads, memory, chosen backend and visual screenshots. Report regressions as well as wins.

## Known Bugs

No additional *reproducible* unresolved runtime bug was established during the initial targeted pass. The archived M5 verifier subsequently proved incorrectly broad (it mixed 731 historical client reports); it now groups a coherent same-commit benchmark campaign. No full current-code M5 campaign has been run. A failed GPU visual parity check or unstable active-shaderpack compositor is a **suspected problem** until reproduced with a specific driver, configuration and effect; do not silently label all shaderpacks incompatible.

## Known Limitations

- Alpha ordering is global only among eligible instanced alpha sprites in Pyro's own pass; it is not a cross-backend, cross-mod or translucent-surface sort.
- GPU Analytic now accepts non-model spawn-shape birth expressions, initial speed/direction expressions, and emitter-scoped lifetime evaluated per cohort. It still rejects **per-particle random lifetimes**, individual/world-dependent parametric motion, dynamic acceleration/drag, collision queries, and world-lit analytic particles; **cohort-shared age-only parametric offsets and constant-coefficient dynamic sprite rotations are GPU eligible**. GPU sprite instancing supports CPU-sampled Minecraft lightmaps (not GPU-resident analytic world lighting).
- CPU Dense now supports **synchronous ON_COLLISION callbacks** with per-particle ordered parameter/RNG updates and reentrant emit; three real-client Legacy/Dense fixtures verify identical contacts, mutation count and birth/event budgets. Other unsupported particle callbacks and Entity models remain Legacy. The GPU Analytic path still rejects stateful particle callbacks.
- Minecraft RenderType is a legitimate fallback for unsupported materials/geometry and failed hardware capabilities, but shaderpack activation alone is **not** a reason to force it.
- Existing GPU cohorts preserve per-birth timing. Multiple compatible births within one tick now coalesce into one unuploaded buffer; different ticks, source poses and lifetime plans remain separate. No statistically controlled proof of frame-time benefit or general cross-emitter merging.
- OpenGL state audit covers the explicitly mutated state; whole-pipeline framebuffer, viewport, external shader hooks and color-space invariants are not fully proven.

## Future Research

For each topic: *P* = priority (P0 correctness, P1 major production impact, P2 research), fallback = valid current behavior when an experiment is not safe or helpful.

| Topic | Current technical cause / status | Next research or solution and expected benefit | Risk / priority / safe fallback |
|---|---|---|---|
| 1. Cross-backend transparency correctness | Alpha sorted only inside instanced sprite path; analytic additive and baked model passes are independent | Paired overlapping smoke/sparks/transparent-model scenes; inspect ordering and possibly a shared draw planner | More draws and state transitions; **P0**; current separate passes with documented ordering |
| 2. Iris/Sodium/Embeddium and shaderpacks | No verified integration contract for external framebuffer/depth/compositing across all pipelines; shaderpacks remain GPU-eligible | Compare vanilla/three shaderpacks with and without GPU and GL state audit; consider scoped compositor integration only for proven defects | Regression across mods; **P0**; capability/error fallback, **not** blanket shaderpack fallback |
| 3. World-dependent GPU simulation | Block collision and entity/fluid/weather queries require Minecraft Level state unavailable to the resident kernel | Prototype bounded CPU-provided collision fields or batch queries; compare against CPU collision ground truth | Stale world data and sync cost; **P1**; CPU Dense/Legacy simulation |
| 4. Stateful particle events and CPU/GPU sync | Callbacks can mutate parameters, spawn event particles and depend on exact order/random stream | Prototype sparse GPU event readbacks or CPU event proxies, benchmark synchronization against eventful smoke/sparks | GPU stalls, determinism; **P1**; CPU Legacy with ordered queue |
| 5. GPU sorting, OIT, depth peeling | Transparent overlapping planes cannot be fully composed by emitter-level sorting | Profile stable global sort vs weighted blended OIT/depth peeling for dense alpha smoke | Bandwidth, artifacts, incompatibility; **P1**; current radix-sorted fullbright alpha with limits |
| 6. Transparent 3D models | Baked translucent quads and intersecting internal faces are not globally sorted | Gather intersecting glass/paper/item meshes; evaluate mesh-level partitioning and per-fragment methods | Expensive draw decomposition; **P1**; Minecraft translucent RenderType |
| 7. Dynamic environment lighting on GPU | Resident particles avoid CPU per-particle world lightmap sampling | Test packed-light updates by moving through caves/day/night; explore tiled/per-chunk light probes and cache invalidation | Incorrect light during movement, CPU overhead; **P1**; CPU-updated world-lit renderer |
| 8. GPU Compute feasibility | Discrete analytic kernel avoids compute; mutable state requires buffers and barriers | Matched 10k/100k collision-free stateful workload with buffer lifecycle, fallback and GPU timers | Barrier/readback overhead and portability; **P2**; CPU Dense or existing GPU Analytic |
| 9. Cross-mod rendering, fluids/weather/entities | Minecraft and mods schedule independently rendered translucent layers and fluid surfaces | Captured scenes intersecting water, glass, entities, shaderpacks and external render passes | Third-party compatibility; **P1**; Minecraft RenderType for unsupported material cases |
| 10. AMD/Intel/NVIDIA and long stress tests | Existing published client numbers concentrated on NVIDIA and short runs | Repeat 30–120 minute play/reload/world-exit stress with 3 drivers and heap/direct-memory/VRAM trend | Hardware variability, slow reproductions; **P1**; conservative resource caps and renderer error fallback |

## Client verification checklist

1. Run pure checks: `gradlew compileJava compileDevelopmentJava verifyReleaseJar runGameTestServer`.
2. Launch interactive museum and compare `sprite_horizontal`, `gpu_sprite`, alpha smoke and overlapping additive sparks with/without forced legacy render; use identical camera angles. Repeat while moving and toggling resource reload.
3. With Iris shaderpack active, keep GPU rendering **enabled**, record screenshot/GL audit and check alpha vs additive and depth before deciding a specific combination is invalid. Verify shaderpack toggles separately.
4. For emission compare ONCE/LOOPING/MANUAL: delayed instant, repeated manual emit, multiple cohorts, local/world-space moving source and post-expiry GPU buffer cleanup.
5. Capture matched timing only after visual acceptance; never infer correctness from faster FPS.
