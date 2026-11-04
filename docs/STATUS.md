# Current status

**As of 3 November 2026:** Pyro VFX is an experimental, usable JSON VFX runtime for Minecraft 1.21.1, NeoForge 21.1.229 and Java 21. M0–M7 engine systems have been integrated; **visual editing and settings UI remain the next phase**. Passing targeted tests does not make this a production stability guarantee.

## Implemented

- **Definitions:** `pyro_vfx:1` resource loading and validation; numeric/vector/color expressions; compiled expression slots, named curves, parameters, event graphs; effect/emitter lifetimes and triggers; resource reload.
- **Emission and particles:** eight spawn shapes (point, box, sphere, line, disc, ring, cone, model surface), bursts and steady spawning, dynamic/static/parametric motion, rotation, block collision and particle collision events. `on_collision` applies to particle triggers, not top-level effect or emitter triggers.
- **Rendering:** Pyro-owned particle lifetimes; sprites, custom/block/item/entity models, UV-sheet animation, world lighting, alpha/additive materials; CPU Legacy/Dense and eligible GPU Analytic, instanced sprite and baked model rendering. Unsupported paths use CPU and Minecraft RenderType fallback.
- **Transforms:** fixed positions, Entity/Block/BlockEntity, vanilla named model-part/chest sockets and custom resolvers, arbitrary callbacks, composable local offsets/rotations, world/local particle space. `travel_distance` and `travel_distance_looping` run on game-tick source movement; neither is merely parsed-only.
- **Integration:** side-aware `Vfx.play`, `VfxOptions`, `VfxHandle`, attach/rebind/detach, parameter update, event trigger and stop. NeoForge v3 Start/Stop/Parameter/Trigger/Rebind/Pose payloads; dimension/source/audience filtering and bounded custom pose streaming. The server handles effect commands, not particle simulation.
- **Development:** Test Harness, opt-in client scenarios, GameTests, 66-effect physical museum, graphics profile tools, backend diagnostics and performance counters. No visual editor.

## Evidence and confidence

The latest reported visibility/regression suite passed **88/88 GameTests**. A prior M7 dedicated-server suite passed **87/87**; these are different checkpoints, not a cumulative test count. Two independent localhost clients passed **8/8 acceptance checks each**, including audience exit/return, dimension changes, sockets and source removal. The current development-only technical museum has **35 exhibits**, reduced from 66. The earlier 88/88 and 87/87 results refer to prior checkpoints, not to the redesigned gallery.

On RTX 4090 Laptop hardware, short real-client checks were recorded for vanilla rendering and Sodium, Embeddium, Iris without shaders, and Iris with Complementary, BSL and Photon (shaderpacks use fallback). See [compatibility ledger](COMPATIBILITY_MATRIX.md), [network contract](NETWORKING.md), and [performance evidence](PERFORMANCE.md). These were **not rerun during repository consolidation**.

## Boundaries

- **No editor:** effect browser, inspector, curve/timeline gizmos, authoring workflow, JSON schema and integrated editor diagnostics are future work.
- **Graphics:** no GPU Compute backend; no guaranteed fast GPU path for world-lit alpha, translucent models, entity renderer animation, collisions, arbitrary stateful expressions or active shaderpacks. Material/depth/compositing parity across all mods is unverified.
- **Budgets:** defaults include 256 simultaneous effects, 80,000 live managed particles, 30,000 spawn requests/tick and 8,192 event evaluations/tick globally; per-effect caps remain 20,000 particles/spawns and 4,096 events/tick. Per-cost-class, adaptive and TOML controls also apply. Numbers are admission controls, not exact VRAM or frame-rate guarantees.
- **Visibility:** frustum/size-aware draw rejection does not delete live particles, suppress events, or thin emissions by distance. No blanket distance-kill or tiered particle LOD.
- **Networking:** late viewers join the current timeline phase without reconstructing earlier smoke or RNG state. Streams are bounded; nonlocal latency, many players, packet-byte throughput and deterministic pixel-identical replay are unverified.
- **Lifecycle:** missing/unloaded source providers stop producing new particles; natural cleanup allows already spawned particles to expire. Remote Stop closes the effect immediately. Teleports exceeding 64 blocks per tick do not emit intervening distance triggers.
- **Validation scope:** AMD/Intel graphics, large modpacks, live shaderpack switching, 4K, sustained 30-minute/multi-hour and realistic Internet multiplayer still require measurements.

See [Architecture](ARCHITECTURE.md) for design decisions, [Test Harness](TEST_HARNESS.md) for verification, and [Roadmap](ROADMAP.md) for the editor handoff.
