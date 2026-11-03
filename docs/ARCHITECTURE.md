# Architecture and design decisions

Pyro VFX separates **effect data**, **client simulation**, **render submission**, **transform sources**, **network commands** and **developer tooling**. The current format and public entrypoints are stable integration contracts for the coming editor phase; internal implementation remains experimental.

## Runtime path

```text
assets/<namespace>/vfx/**/*.json
  -> JSON parser / validator / registry
  -> immutable compiled definition and expression programs
  -> side-aware Vfx.play / VfxHandle
     -> client instance manager (or server instance lease + clientbound commands)
     -> Transform Provider -> Effect -> Emitters -> Particle pools
     -> backend-capability routing -> sprite / baked-model / fallback renderer
```

- `net.kofllee.pyrovfx.vfx.definition`, `vfx.resource`, `vfx.expression`, `vfx.curve` and `vfx.type` describe and compile effects without embedding Minecraft client rendering. Numeric expressions are compiled once into dependency-classified programs; constant expressions and per-birth values are cached when safe, while dynamic dependencies retain their correct evaluation points.
- `client.vfx` owns active instances, effect/emitter lifetime, event ordering, budgets, collision, particle pools and source sampling. Effect / emitter / particle context is not interchangeable. A particle belongs to Pyro for its whole lifetime; fallback rendering may still use Minecraft `RenderType`.
- `client.render` consumes snapshots/interpolated transforms. Render never causes game events or particle simulation ticks. GL resources are owned by renderer caches and recreated on resource reload; world exit and source loss clean up their respective handles.
- `client.dev`, `museum` and `tools/` contain developer commands, benchmark fixtures, independent screenshot tools and the interactive museum. They are not required for normal consuming-mod effect playback.

The effect parser, resource location rules and expression semantics are specified in [JSON format](JSON_FORMAT.md) and [expressions](EXPRESSIONS.md). The editor should use these same definitions and validators, not fork the execution model.

## CPU and GPU routes

**Simulation and rendering are separate choices**. The routing checks required semantics before selecting a fast path.

| Route | Use | When it cannot replace the fallback |
|---|---|---|
| CPU Legacy | General per-particle behavior, callbacks, complex collision/events, incompatible models | Correctness and synchronous event semantics are prioritized |
| CPU Dense (SoA) | Eligible sprite/model pools, collision without incompatible event hooks, lower allocations | Arbitrary stateful runtime logic may require Legacy |
| GPU Analytic | Immutable birth data, supported expressions/curves and closed-form discrete motion, including eligible sprites and baked models | Stateful expressions, game-world collision and unpredictable CPU events cannot be silently moved to GPU |
| GPU instanced rendering | Eligible additive sprites, sorted fullbright alpha sprites and baked custom/block/item models | World-lit alpha, arbitrary entity models, translucent models, unsupported facing/layers and shaderpack integration require conservative Minecraft RenderType |
| Minecraft rendering fallback | Compatible drawing under unsupported GL/mod/shader combinations | Can cost more CPU; still Pyro-owned lifetime and simulation |

The GPU Analytic route uploads compact resident birth state (24 bytes per simple sprite in the measured path) and animates from shared time/uniforms; it does **not** copy and simulate each particle on the CPU every frame. CPU source state and fallback are retained where necessary. Baked model caches avoid Java draw objects per particle. Fullbright alpha requires a stable depth sort and straight-alpha blending; it sorts particle centers, not global translucent fragments across mods.

OpenGL 3.3 feature probing and render-state audits protect fast paths. Active Iris shaderpacks use Minecraft RenderType while unshaded Sodium/Embeddium/Iris profiles can retain fast paths. No shaderpack framebuffer replacement, weighted blended OIT or universal depth-order integration is claimed. GPU timer queries are asynchronous and measure Pyro rendering, not the whole frame.

### Why no GPU Compute

An OpenGL 4.3 compute path would introduce mutable GPU buffers, ping-pong ownership, memory barriers, readback and reload complexity. Existing measured workloads already benefit from analytic GPU motion or CPU Dense. There is **no matched stateful compute prototype proving a net gain**. Revisit only for a realistic 10k–100k non-analytic workload with dispatch/barrier cost, collision/event parity, fallback and multiple GPU-driver families measured. Compute is deferred, not proven inferior.

### Threads and overload

Simulation and server instance bookkeeping run on the respective Minecraft game threads; client rendering/GL work remains on the render thread. Synchronous collision-triggered events and mutable expression contexts prevent naïvely parallelizing the update loop. The legacy math-only `tools/ParallelKernelProbe.java` does not establish production thread safety.

A client-global budget and per-effect limits reject excess **new** work deterministically without retroactively deleting already-live particles. Defaults are documented in [Status](STATUS.md); adaptive CPU/GPU-aware spawn admission reduces *new steady emissions*, not existing physics or event delivery. Render-time frustum/subpixel and conservative analytic-cohort rejection do not change simulation or effect age. A proven visible-scene CPU fallback culling overhead was rolled back rather than introducing a quality regression.

## Transforms and attachment lifetime

`VfxTransformProvider` samples world position and quaternion. Built-ins support fixed, Entity, Block, BlockEntity, named vanilla model-part/chest sockets, custom client socket resolvers and Java callbacks. `Vfx.at(source).offset(...).rotate(...)` composes transforms in call order; custom callbacks are sampled at client ticks and interpolated for rendering.

- `world` (default) particles are born at the transformed emitter but subsequently remain world anchored.
- `local` particles follow emitter translation/rotation; collision still resolves against world blocks. Eligible GPU birth-state geometry uses a composed shader transform rather than a per-frame upload.
- Providers identify entity ID **and UUID**, loaded chunks and block/block-entity identity. Unavailable sources stop further emission; live particles naturally expire. Resource reload preserves valid active effects while rebuilding GL data.
- `travel_distance` fires once, `travel_distance_looping` at successive thresholds, including multiple crossings/tick (maximum 128). Accumulated distance follows tick-sampled source/emitter movement; jumps over 64 blocks in a tick are treated as teleports, without triggering intermediate events.

Vanilla named sockets are sampled from client animation models. Modded animation libraries require a supplied resolver; a generic bone lookup does not exist.

## Network boundary

A logical-server `Vfx.play` creates a bounded server lease. The server distributes effect **commands**, not particles. NeoForge protocol v3 uses six clientbound payloads: Start, Stop, Parameter, Trigger, Rebind and Pose. Start includes resource ID, instance UUID, dimension, source identity/local pose, parameters and elapsed timeline; source type may be fixed, entity, block, block entity or stream.

Audience membership requires matching dimension, radius and original source chunk tracking; 64-block player spatial bins and staggered checks avoid every-player/every-instance scans each tick. Built-in source movement relies on vanilla synchronization; arbitrary sampled server callbacks use bounded 10 Hz pose snapshots. Clients resolve late entities/chunks briefly, join the current effect age without reconstructing old particles, and may independently reject an effect under local budgets. A server handle is not proof that a viewer rendered it. The full contract and test scope are in [Networking](NETWORKING.md).

## Editor boundary

Build an editor-side mutable document and serialize it back into `pyro_vfx:1`. Feed previews through the existing parser and client runtime. Do not attach UI state to server payloads, mutate shared loaded definitions in-place, or duplicate the expression compiler. The [editor plan](ROADMAP.md) describes this handoff.

## Evidence and limits

See [Performance](PERFORMANCE.md), [Compatibility](COMPATIBILITY_MATRIX.md) and [Test Harness](TEST_HARNESS.md). Results concern specific hardware, client settings and durations. Large modpacks, AMD/Intel, production Internet multiplayer and long-duration reliability are not established.
