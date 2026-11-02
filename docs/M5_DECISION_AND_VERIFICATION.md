# M5 — backend decision, global safety and verification

Date: 2026-11-02. Target: Minecraft 1.21.1 / NeoForge 21.1.229 / Java 21.

## GPU Compute decision: DEFER

M4 already renders 1M strictly eligible additive analytic sprites in a real client on RTX 4090 Laptop at 854×480, with three-run medians of 0.009 ms VFX simulation CPU and 1.004 ms VFX GPU time (16 draws). M4 also analytically integrates the exact discrete acceleration/drag recurrence for supported dynamic motion. Arbitrary mutable expressions, collisions, complex rotations, models and event-driven particles retain CPU/backed Minecraft rendering.

An OpenGL 4.3 compute path would add buffer ownership, resource barriers, ping-pong buffers, lifecycle complexity and compatibility risks without demonstrating a benefit for the measured M4 workloads. Neither M0–M4 nor this milestone contains an apples-to-apples stateful GPU compute prototype. Therefore **do not claim compute is inherently slower, and do not ship it without evidence**.

Reconsider GPU compute only when a realistic 10k–100k **stateful** dynamic workload (not already analytically expressible) demonstrably benefits after including dispatch/barrier/transfer cost; verify visual and event semantics, CPU/GPU timing, frame P95/1% lows, memory, reload and world switch behavior, forced fallback and at least two driver families. World-dependent collisions and synchronous CPU events must not silently disappear when routing to compute. Until that gate is met, retain CPU dense/legacy, GPU instanced and GPU analytic routes.

## M5 global runtime safety

New `VfxGlobalBudget` is owned by the client manager and shared between all active effects each tick. This complements, rather than replaces, the existing per-effect budget:

| Admission control | Per effect | Normal client, global | Explicit heavy benchmark |
|---|---:|---:|---:|
| Active VFX instances | — | 256 | 256 (never lifted) |
| Live custom particles | 20,000 | 80,000 | 1,000,000 |
| Spawn requests per tick | 20,000 | 30,000 | 1,000,000 |
| Event evaluations per tick | 4,096 | 8,192 | 8,192 |

The heavy override is unavailable in production (`FMLEnvironment.production`), and is explicitly set only by a development benchmark. Global admission runs in deterministic existing effect/emit order; newer work is rejected first. Existing live particles are not evicted when over budget. Active particle accounting includes analytic, CPU dense and legacy Pyro VFX pools, but **not externally owned vanilla Minecraft particles** after `minecraft_particle` delegates them to the game. The spawn rate of those delegated effects is bounded per tick.

Global counters track admitted spawn/events and admission rejections for spawns, events and new effect instances. Counters are reset on `/pyrovfx stop_all`, world change and benchmark reset. The event-rejection counter covers rejected `enterEvent` calls, **not every command omitted by a bounded queue or early `hasEventBudget` loop break**. The recorded global peak counts active particles at the start of each tick (not the instantaneous intra-tick peak). Memory budgets are particle-count budgets, not guaranteed VRAM/native/heap byte limits.

Operational diagnostics: `/pyrovfx backend` prints the global budget, active effects, backend decisions and latest GPU analytic draw/upload counters. Benchmark JSON includes `globalPeakObservedParticles`, `globalAdmittedSpawns`, `globalAdmittedEvents`, `globalRejectedSpawns`, `globalRejectedEvents`, `globalRejectedEffects`.

## Reproducible validation

- `.\\gradlew.bat clean build runGameTestServer --no-daemon`: **27 / 27 GameTests passed** after global-budget integration, including concurrent effects' aggregate spawning, event quotas, 256-instance admission, explicit heavy override and original correctness checks.
- Python comparator checks: `python tools/test_compare_bench.py` (**5/5 passed**).
- Real client safety suite: `.\\gradlew.bat runClientBench '-PbenchScenario=global_budget_spike,analytic_dynamic_10k,collision_1k,reload_active,budget_spike' -PbenchGlobalStress=true -PbenchRenderStateAudit=true`.
- `global_budget_spike` is opt-in with **-PbenchGlobalStress=true**, separate from the heavy override: three simultaneous 20k spawn requests of the same real sprite effect must produce 30k total admits, 30k rejections, and exactly three still-live effects. Failures mark its benchmark JSON `status=invalid`.
- A repeatable high-density analytic path remains `.\\gradlew.bat runClientBench '-PbenchScenario=sprite_10k,sprite_100k,sprite_1m' -PbenchHeavy=true -PbenchRequireGpuAnalytic=true -PbenchRenderStateAudit=true` (opt-in; only on suitable hardware).

## M5 real-client evidence — 2026-11-02

**12/12** completed real Minecraft client benchmark reports across three independent launches. The dedicated global safety scenario completed **3/3** times: two on `gpu_analytic`, one on forced `cpu_dense` fallback. Every run reached 30,000 active particles, admitted 30,000 spawns and rejected 30,000, without rejecting any effect instances. The median of the three per-run full-frame P95 values was **10.448 ms** at 854×480 (not a performance improvement claim).

Also completed: `analytic_dynamic_10k` (both GPU analytic and CPU dense routes), `collision_1k`, `reload_active`, `budget_spike`, `model_1k`, `sprite_lit_fallback`, `events` and `uv_animation`. All reports were obtained with an actual Minecraft client, not a synthetic/offline approximation. Global admission was exercised with the normal 30k spawn cap, **not** the 1M developer override. Resource reload, GL-state audit and backend fallback were exercised as described in the run commands.

The 12 machine-readable reports are preserved under [`benchmarks/m5-2026-11-02`](benchmarks/m5-2026-11-02/). Revalidate with `python tools/verify_m5.py --folder docs/benchmarks/m5-2026-11-02 --min-global-runs 3`. Some recordings precede the final heavy-effect-count safeguard check; the final build/GameTests were repeated afterward. Screenshots remain in the local `run/pyrovfx-benchmarks/` directory; no blind pixel-equivalence claim is made.

## Compatibility / scope

| Environment / feature | Policy | Verification level |
|---|---|---|
| NeoForge 1.21.1 / Java 21 | Supported target | Build and GameTests |
| GL 3.3 compatible GPU, eligible sprite | GPU analytic / instanced | M3/M4 NVIDIA client evidence |
| No compatible GPU / forced fallback | CPU simulation + Minecraft RenderType | M3/M4 forced-fallback client tests |
| Collision, events, mutable curves, alpha/lit sprites, advanced models | Preserve precise CPU/RenderType path | M0–M4 goldens/client cases |
| Reload / world exit | Close GPU resources, preserve valid active effects across resource reload | M4 reload smoke; world identity GameTest |
| Iris / Oculus / Sodium / Embeddium | Conservative legacy render fallback by mod detection | **Not integration-tested with actual mod versions** |
| Non-NVIDIA GPU, multiplayer, dedicated-server TPS | Must not claim parity | **Not yet empirically measured** |
| 1080p/4K and alpha overdraw at 1M | Separate GPU/fill-rate bottlenecks | **Not measured** |
| 30-minute mixed-effect soak, repeated dimension transitions | Required for production-level confidence | **Not yet completed** |

This is a **verified development foundation**, not a universal performance guarantee or finished production release. Priorities/weighted quality tiers, per-render-type active limits, full native-memory budgets, driver/mod matrix and long-running soak remain legitimate next hardening work. No claim that all of M5's original ambitious exit criteria are met is warranted until those validations are recorded.
