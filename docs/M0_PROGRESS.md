# M0 — Stability and behavior freeze (in progress)

**Project:** Pyro VFX, Minecraft 1.21.1 / NeoForge 21.1.229 / Java 21. This document tracks actual completed work and known gaps. M1–M5 have **not** started.

## Reproducible pre-M0 baseline

The original runtime was measured in Minecraft on an NVIDIA RTX 4090 Laptop GPU at 854×480. Three independent client starts per scenario are archived in `docs/benchmarks/baseline-2026-11-01/` (original JSON plus `repeats/`). The recorded scenarios are `sprite_1k`, `dynamic_1k`, `collision_1k`, `model_1k`, `sprite_10k`. All 15 completed, with matching effect SHA-256, particle counts, and GPU. CPU-median coefficient of variation across starts was 2.5–6.6%; P95 frame-time CV was 0.7–2.8%. See `REPEATABILITY.md` for actual measurements.

The first-generation 10k static-sprite runtime required ~9.4–10.6 ms of CPU simulation median. No claim about 100k or 1M feasibility is made.

## M0 completed: event mutation regression

**Precondition:** An emitter's collision trigger resolves `emit` back to that same emitter. The event calls `ClientVfxEmitter.emitManual` while its particle update loop is iterating an `ArrayList`. An expired particle causes the iterator's `remove()` to throw `ConcurrentModificationException`; `ClientVfxManager` catches the exception and drops the complete effect.

**RED reproduction:** `/pyrovfx bench self_emit_collision` using `assets/pyro_vfx/vfx/bench/self_emit_collision.json`. Original runtime yielded `status=invalid` with `Self-emission during collision removed the active instance`; client log contained a `ConcurrentModificationException` at `ClientVfxEmitter.tick:135`.

**Change:** Iterate over the particle count captured at the start of the tick by index, allowing event-generated particles to append. After the update and all triggers, compact expired particles using `ArrayList.removeIf`. Newly emitted particles first tick on the next game tick. `set_param` events retain their immediate execution contract. No new scheduler or general queue is added before its semantics can be tested.

**GREEN reproduction:** Same client command produced `status=completed`, `collisionEventObserved=true`, 100 sampled simulation ticks, and one surviving effect throughout the test. This regression scenario is now part of the development harness.

## Additional headless behavior fixtures

NeoForge `runGameTestServer` currently runs eight tests: mutable expression variables; curve endpoints/interpolation; parser collision/event/parameters; unknown event parameter rejection; once lifetime delay and end boundary; looping active/sleep/finite vs zero-loop behavior; manual-emitter lifetime; and parsing all eight spawn shape types. On 2026-11-01 the full GameTestServer run completed with **8/8 pass** and the Gradle build succeeded.

## Post-fix performance validation

The original five scenarios ran three more times after the indexed-iteration fix. All 15 were `completed` with matching JSON hashes and particle counts. Comparing medians of three runs with medians of the three baseline runs: CPU simulation was −2.4% (sprite 1k), −2.7% (dynamic 1k), −4.8% (collision 1k), +0.4% (model 1k), and −6.1% (sprite 10k). P95 frame time for sprite 10k was −5.6%. These small performance differences are provisional, not conclusive.

P95 model CPU/render submission rose in these measurements even as median times remained nearly unchanged; the result needs deeper profiling and repeatability checks. A follow-up code path skips `removeIf` when no particle has expired, avoiding an unnecessary second scan during ordinary ticks. This path has a separate real-client self-emission regression check plus model 1k and sprite 10k smoke benchmarks, but not yet three full replicated runs.

Full comparison and recorded raw reports: `docs/benchmarks/m0-2026-11-01/`.

## Still open in M0

- Capture golden per-tick positions, lifetimes, event ordering and parameters for representative scripted effects, not just parser tests.
- Expand test cases for `on_creation`, timelines, `on_expiration`, nested/random events, UV animation, motion and rotation, plus resource reload and world change.
- Validate event breadth/instant and manual spawn budgets against malformed or adversarial JSON, beyond the existing recursion depth cap.
- Verify `VfxModelSpawnShapeSampler` rotated-element JSON and eliminate intrusive per-particle logging with a reproducing test.
- Investigate atomic reload behavior when any effect JSON fails and when active effects survive reload.
- Measure and attribute specific hot-path allocations and JVM GC via reproducible profiling; do not infer allocations solely from general heap telemetry.
- Validate no unsafe dedicated-server classloading, world access or GPU resource lifecycle; further test shader-mod compatibility.
- Compare repeated post-fix performance with the pre-M0 distribution, including P95, before claiming performance gain or loss.

The scope is explicitly M0; do not silently start a compiled-expression or SoA migration until behavioral fixtures are sufficient.
