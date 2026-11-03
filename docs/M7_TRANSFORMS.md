# M7.1 — Transform providers and spatial simulation

M7.1 adds source transforms to the existing runtime without introducing named
attachment slots, provider registration, effect handles, networking, or an editor.
Public handles and server packet orchestration belong to subsequent milestones.

## Client integration

```java
ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example", "rocket_trail");
ClientVfxManager.tryPlay(id, partialTick -> mechanism.getMuzzleTransform(partialTick));
ClientVfxManager.tryPlay(id, VfxTransformProviders.entity(projectile));
ClientVfxManager.tryPlay(id, VfxTransformProviders.block(clientLevel, pos));
ClientVfxManager.tryPlay(id, VfxTransformProviders.blockEntity(blockEntity));
ClientVfxManager.tryPlay(id, VfxTransformProviders.fixed(worldPosition));
```

This is a **client-only M7.1 entrypoint**, not the M7.2 stable cross-mod API.
`VfxTransformProvider` is a single-method Strategy returning a
`VfxTransform` (world position + quaternion) or `null` if unavailable.
Providers are sampled at `partialTick=1` in the client game tick; render-only
sampling uses the actual partial tick. No events occur during rendering.
Missing providers stop new emissions and release the callback reference; live
particles finish normally. World changes clear the client manager.

Entity strategies track **entity id plus UUID** in the original client level,
not a strong entity reference. Block providers stop on block removal or
replacement or chunk unload. Block entity providers additionally check the
actual block entity identity. Resource reload preserves active instances and
rebuilds render resources through existing reload paths.

### Model parts and bones

Vanilla `ModelPart` does not offer a universal public
`Entity -> world-space bone transform` lookup. Animation mods do not share a
unified bone API. Use `VfxTransformProviders.modelPart(entity, callback)` and
sample the **actual part pose from the owning renderer/animation system**
after animation evaluation, returning a world-space transform. The museum
head-anchor example is intentionally an eye/head proxy, **not** automatic
retrieval of an animated bone pose.

## JSON emitter properties

```json
{
  "id": "trail",
  "offset": [0, 0.75, -0.4],
  "emitter_rotation": [0, 90, 0],
  "space": "local"
}
```

`offset` is in the source's local coordinates and rotates with its pose.
`emitter_rotation` is the additional emitter-local Euler XYZ rotation in
degrees. The existing `rotation` object still controls **individual
particle rotation**; it is unrelated to the emitter's orientation.

- `world` (default): source and emitter move, particles stay in world space
  after birth. Their spawn shapes, initial velocities and emitter offsets
  respect the source/emitter rotation.
- `local`: born particles remain relative to the emitter transform. Source
  translations and rotations carry their live positions, velocities and
  birth origins; physical collisions resolve against world blocks.

CPU Dense retains its structure-of-arrays storage. Eligible GPU Analytic
sprites and models keep resident birth data; local movement is a shader
uniform transform, not a per-frame particle upload. Unsupported effects
continue to use established CPU / Minecraft render fallbacks. Render-time
provider interpolation cannot generate extra simulation/collision steps.

## Distance triggers

`travel_distance` fires once after the configured threshold.
`travel_distance_looping` fires at every multiple, including several
events along one tick's path, ordered by traversal position and subject to
existing event budgets (plus a 128-crossing-per-tick bound).
Effect triggers measure the effect source; emitter triggers measure the
emitter's world-space origin, including animated offsets.
Stop = no distance; reversed movement accumulates its absolute length.
Segments exceeding 64 blocks in one tick are treated as teleports:
no triggers fire along the jump; looping spacing restarts at destination.
This is an explicit heuristic, not a Minecraft teleport notification.
Distance is sampled from game-tick endpoint positions (piecewise linear),
independent of renderer FPS.

## Verification and limitations

### Targeted visual follow-up (2026-11-03)

- Fixed the analytic GPU render pose: it previously used the parent effect's
  transform without the emitter-local offset or rotation. It now composes the
  interpolated emitter-relative pose with the render-sampled source, so
  `source_block` keeps its two-block offset and `source_custom` follows its
  smoothly sampled callback between client ticks.
- Corrected a legacy local particle rotation interpolation reset. Made the
  block/custom museum examples larger and denser to make their behavior easier
  to inspect without changing their attachment semantics.
- The Minecraft client benchmark for `museum_source_block` now exercises a
  real placed block provider instead of silently substituting a fixed point.
  A temporary fixture is restored when the museum has not been built.
- Model particles with `model.source=entity` are **not** entity attachments:
  they remain on vanilla EntityRenderDispatcher/CPU Legacy. The cached dummy
  entity is positioned at its origin only once, not rebuilt for every render.
  A new GameTest verifies sub-tick trajectory interpolation and fallback
  routing; this does not assert automatic limb animation or GPU instancing.
- Follow-up: entity dummy animation state is now cached **per particle** rather
  than per entity type, because successive particles may have different ages
  and must not overwrite each other's mutable render pose. The client benchmark
  for `museum_model_entity` asserts that the two pigs use distinct dummies;
  weak keys release their state after particles expire. In uncapped 1280×720
  runs, median CPU render submission at 1,000 pigs changed from ~2.28 ms to
  ~2.47 ms, with P95 frame time ~5.26 ms to ~5.48 ms. The improvement in pose
  isolation is verified; whether perceived head shimmer also involves
  texture aliasing must be judged in a moving-camera visual check.
- **Confirmed head jitter root cause (2026-11-03):** newly created vanilla
  dummy pigs can initialize `yHeadRot` to a nonzero random value while
  `yHeadRotO` remains zero (observed **5.3759117 vs 0 degrees** in the
  running Minecraft client). Because entity-model particle dummies are not
  ticked, vanilla's partial-tick head interpolation repeatedly traversed
  this angle range each game tick. `VfxEntityPoseInitializer` now aligns
  previous/current pitch, head yaw and body yaw **once at dummy creation**,
  leaving current pose and per-particle age intact. The new server GameTest
  verifies a stable pose at five partial-tick values; the real-client
  `museum_model_entity` and controlled-camera
  `museum_model_entity_close_pan` benchmarks both completed with explicit
  head-pose and per-particle dummy isolation assertions. This addresses the
  oscillating head pose rather than masking it with disabled animation or
  GPU/model substitutions. Final consolidated regression is recorded below.
- Real 1280×720 client runs completed for GPU block/custom effects and
  entity-model workloads.
  At 1,000 entity models, the uncapped client measured median VFX CPU render
  submission of ~2.28 ms, P95 full-frame ~5.26 ms on the recorded RTX 4090
  Laptop setup. This is not a frame-sequence visual-parity proof; further
  animation/modpack testing remains open.

## M7.1 final acceptance — 2026-11-03

### Lifecycle and cleanup

| Real-client scenario | Removed source | Result |
| --- | --- | --- |
| `m7_lifecycle_entity` | Entity removed by id | PASS |
| `m7_lifecycle_block` | Block replaced by air | PASS |
| `m7_lifecycle_block_entity` | Attached block entity destroyed | PASS |
| `m7_lifecycle_chunk` | Client chunk unloaded with `ClientChunkCache.drop` | PASS |
| `m7_lifecycle_world_exit` | Integrated server stopped, client world disconnected | PASS |

All five scenarios completed in the **real Minecraft 1.21.1 NeoForge client**,
at 1280×720, with explicit assertions (see the corresponding
`run/pyrovfx-benchmarks/m7_lifecycle_*.json` files, which are not committed).
Before detachment the emitter must have spawned live particles; afterwards it
cannot admit new births. The old particles finish naturally, and both the
instance and its GPU analytic cohorts/reported resident bytes return to zero.
World exit likewise checks the global manager after Minecraft's actual client
level becomes null. The existing `worldIdentityDetectsDirectWorldSwitches`
GameTest also covers a non-null world identity being replaced by another.

**Bugs found and fixed:** In Minecraft 1.21.1,
`ClientLevel.hasChunk(int,int)` returns `true` regardless of whether the
client currently holds that chunk. Block and block-entity providers now query
`ClientChunkCache.getChunk(x,z,ChunkStatus.FULL,false)` so unloading truly
detaches them. Once a source disappears, dynamically evaluated emitter offsets
and rotations stop changing; its retained local-space particles remain at
their last source pose. Remaining particle collisions may still physically
resolve but cannot re-enter the emission event graph after source loss.

The world-exit benchmark uses a nonblocking integrated-server halt followed by
normal `Minecraft.disconnect()` outside the client-tick callback. Invoking
disconnect directly inside a tick was found to recurse into the progress
screen and stall the test; the corrected path completed without a hang.
These isolated client tests do not establish cross-dimension attachment
migration or multiplayer packet synchronization.

### Three-backend parity

The same museum JSON, source seed, effect SHA-256 and particle counts were
tested in separate **GPU Analytic**, **CPU Dense** and **CPU Legacy** runs:

| Effect | GPU | Dense | Legacy | Peak particles |
| --- | --- | --- | --- | ---: |
| `museum_source_entity_world` | PASS | PASS | PASS | 52 |
| `museum_source_entity_local` | PASS | PASS | PASS | 52 |
| `museum_source_rotation` | PASS | PASS | PASS | 52 |
| `museum_source_custom` | PASS | PASS | PASS | 105 |
| `museum_source_model_local` | PASS | PASS | PASS | 24 |

`movingModelSpaceAndRotationMatchAnalyticDenseAndLegacy` compares world and
local transformed positions and model Euler rotations at five successive ticks
with a translated/rotating parent, covering all three simulation models.
An earlier `analyticMotionMatchesDiscreteCpuRecurrence` tests closed-form
motion against discrete CPU physics. Collision requires real world block
queries, so it **must not route to GPU Analytic**. `collision_no_events`
selected Dense or Legacy as configured and reported exactly **102,006**
contacts in each real-client 1,000-particle run; ordered collision callbacks
continue to require Legacy.

The benchmark-only `benchmarkProviderFor` resets animated attachment phase
when each scenario begins; it does not affect interactive museum providers.
Final screenshot comparisons of GPU vs CPU for these moving sources show
visually consistent behavior. The single-frame PSNR after phase alignment was
about **56–74 dB** for entity-attached sources, **55–62 dB** for custom motion
and source rotation, and **31–35 dB** for the rotating 3D-model cluster.
Those values are **not pixel-exact parity assertions**: independent runs can
capture different render partial ticks and hence different orientations of
moving small models. Numerical per-tick transform parity and actual backend
selection are checked separately, and human inspection remains appropriate
for animated material.

### Final regressions and scope

`./gradlew.bat build runGameTestServer --console=plain` completed successfully
with **74/74 required GameTests** including the new detached-source and
three-backend transform tests. Dedicated real-client lifecycle and backend
scenarios completed; the final combined client regression is recorded as
separate JSON reports. `git diff --check` checks whitespace; changes are
left uncommitted, with unrelated existing files untouched.

M7.1 is accepted as a locally tested spatial/attachment subsystem, **not**
production certification. There is no GPU collision backend (CPU fallback is
intentional), cross-driver/shaderpack parity proof, automatic animated-bone
discovery, multiplayer network lifecycle test, or long-duration multiworld
soak. M7.2–M7.4 remain outside this work.

The GameTest suite covers fixed/source transforms, inverse and interpolation,
world/local particle following, emitter orientation, event traversal order,
stops/reversals/teleportation, and museum JSON/backend eligibility.
Eight museum exhibits provide interactive examples (entity world trail,
entity local aura, block, rotating source, custom lambda, eye/head callback,
travel-distance bursts and a rotating GPU model cluster). Run `./gradlew runGameTestServer` and
`./gradlew runClientMuseum`, then `/pyrovfx museum play source_entity_local`.
Use `/pyrovfx museum mode cpu` and `mode auto` for path comparisons.

There is no serializable custom Java provider, no automatic bone introspection,
no cross-dimension attachment migration, no exact curved-path length between
ticks, and no M7.2 handle API or M7.3 network propagation yet. Multiplayer
and external animation/modpack combinations require separate integration
testing after those milestones.