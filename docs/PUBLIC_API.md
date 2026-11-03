# Pyro VFX public API

A consuming mod needs only `net.kofllee.pyrovfx.api.Vfx` and
`VfxHandle` for normal playback. There is no networking registration,
packet construction, client renderer or internal manager to access.

## Normal playback — the same API on both logical sides

```java
import net.kofllee.pyrovfx.api.Vfx;
import net.kofllee.pyrovfx.api.VfxHandle;

ResourceLocation smoke = ResourceLocation.fromNamespaceAndPath("pyrotechnica", "rocket_trail");

VfxHandle trail = Vfx.play(smoke, rocket);                  // Entity
Vfx.play(smoke, blockEntity);                              // BlockEntity
Vfx.play(smoke, level, hitPosition);                        // fixed Vec3
Vfx.play(smoke, level, blockPos);                           // tracking block
Vfx.play(smoke, level, new VfxTransform(hitPosition, rotation));

trail.setParameter("density", 2.0);
trail.trigger("burst");
trail.attach(otherEntity);
trail.detach();  // freeze last position and rotation
trail.stop();
```

A world-aware call on a **logical server game thread** creates a server
effect instance and broadcasts its commands to eligible tracking players.
On a **client game thread**, the same call creates a local effect only.
Entity and BlockEntity already carry a Level; a plain Vec3 or BlockPos does
not, so pass `level` explicitly. Wrong-side, stale-world and unavailable
sources return an inactive handle instead of switching worlds or silently
sending a packet.

**Important:** this is side-aware dispatch, not duplicate suppression. If
your mod runs the same gameplay callback on both client and server, invoking
`Vfx.play` in both callbacks creates a local effect **and** a server-broadcast
effect. For shared gameplay events, call it on the logical server; for
exclusively client-visual events, call it on the client.

## Optional settings

```java
import net.kofllee.pyrovfx.api.VfxOptions;

VfxHandle smoke = Vfx.play(smokeId, rocket, Map.of("density", 0.75));

VfxOptions settings = VfxOptions.DEFAULT
        .withParameters(Map.of("density", 0.75))
        .withRadius(64)             // server audience, in blocks
        .withDurationTicks(200);    // server lease, in ticks
VfxHandle remoteOrLocal = Vfx.play(smokeId, rocket, settings);
```

The defaults are **96 blocks**, **1200 ticks (60 seconds)** and no initial
parameter overrides. Local playback uses the parameter map but ignores radius
and server lease. A server instance is automatically released after its
lease, when its attached source is removed, or when its world unloads.
This is a server/network bookkeeping lifetime, not a replacement for the
effect's own JSON lifetime. `-1` is an opt-in indefinite server lease:
call `stop()` yourself. The networking backend enforces the existing
limits (256-block maximum radius, 10-minute maximum finite lease,
32 parameters and 4096 live instances per dimension).

## Transform Providers: offset/rotate work on both sides

```java
VfxTransformProvider nozzle = Vfx.at(rocket)
        .offset(0, 1.0, -0.5)
        .rotate(0, 90, 0)
        .offset(0.25, 0, 0);
VfxHandle smokeTrail = Vfx.play(smoke, nozzle);
smokeTrail.attach(Vfx.at(otherRocket).offset(0, 0, -1));
smokeTrail.detach();

// BlockPos / fixed Vec3 do not carry a dimension: give them a Level.
Vfx.play(smoke, Vfx.at(level, blockPos).offset(0, 0.5, 0));
Vfx.play(smoke, Vfx.at(level, hitPosition).rotate(0, 90, 0));
Vfx.play(smoke, Vfx.at(blockEntity).offset(0, 0.5, 0));
```

All built-in `Vfx.at(Entity)`, `Vfx.at(BlockEntity)`,
`Vfx.at(Level, BlockPos)` and `Vfx.at(Level, Vec3/VfxTransform)`
providers preserve **source identity + composed local pose**, including
arbitrarily chained `offset`, `rotate` and `compose`. On the server the network
sends the entity UUID/runtime ID or block position and this composed local
transform with Start/Rebind. The client follows the vanilla source and
interpolates motion without receiving a pose packet every tick.
The order of transforms matters: `offset(...).rotate(...)` moves along the
original source axes before rotating; `rotate(...).offset(...)` rotates the
new offset as well. Entity rotation follows vanilla yaw/pitch. Block sources
start at the block center with world-aligned axes.

`Vfx.at(BlockPos)` without a Level remains a **client-only shorthand**.
`Vfx.play(id, Vec3)` and `VfxTransformProvider.fixed(Vec3)` without a Level
also remain local for backwards compatibility. For networked playback from a
fixed position use `Vfx.play(id, level, position)` or `Vfx.at(level, position)`.

## Arbitrary Java callbacks: automatic streaming with a world

```java
// A server-owned moving attachment that has no vanilla Entity/Block identity.
VfxHandle muzzle = Vfx.play(smoke, level,
        partialTick -> mechanism.getMuzzleTransform(partialTick));

// Can also switch an existing server instance to an arbitrary callback.
muzzle.attach(partialTick -> mechanism.getAlternativeMuzzleTransform(partialTick));
```

The callback is evaluated once per server game tick; the engine sends only
changed pose snapshots through its existing 10 Hz throttled, interpolated
streaming channel. You do **not** need to call `updatePose()` each tick.
An arbitrary callback passed without a world (`Vfx.play(id, provider)`) still
has no way to identify its dimension: that overload remains client-local.
Server callback code must use server-available state; a client renderer-only
`ModelPart` or bone callback cannot run on the dedicated server. In that case
use a server-defined socket transform, or play the callback locally where the
renderer has the animation data.

Explicit streaming remains available when the mod already has an external
pose synchronization schedule:

```java
VfxHandle socket = Vfx.playStreamed(smoke, serverLevel, initialPose,
        VfxOptions.DEFAULT.withRadius(64));
socket.updatePose(currentPose);
socket.attachStreamed(otherPose);
```

`updatePose` / `attachStreamed` are for explicit streamed server handles;
`attach(VfxTransformProvider)` automatically selects networked built-in
attachment or server-side streaming. `detach()` freezes the last position
and rotation. Replacing the provider stops sampling the old callback.

For old integrations, `Vfx.play(ServerLevel, id, source, parameters,
radius, durationTicks)` and `VfxRemoteHandle` remain supported; new
integrations need neither import.

## Contract and limitations

- Always use the owning logical game thread. Off-thread operations are not
  implicitly queued onto the client/server executor.
- A `VfxHandle` controls **one** instance. Server handles reference a
  server lease, while local handles reference a client instance. Neither
  stores a particle or holds its source entity/world strongly.
- `stop()` is idempotent. Expiration, a failed start, a world unload or
  a completed local effect makes `isActive()` false for the respective
  owner. On a server handle `isActive()` means **server lease active**,
  not an acknowledgment that a client successfully rendered it.
- For client-local playback, missing effect resources, unknown parameters
  and missing event names are rejected by the client runtime. On the
  server, packet-safe name/value bounds are validated, but the server does
  not load client effect definitions or know individual client budgets.
- Client-local `stop()` is safe from a transform callback; an explicit
  Stop closes remote client particles immediately. Natural local source
  loss stops new emission and allows surviving particles to finish.
- The old `Vfx.play(id, Vec3)`, `Vfx.play(id, BlockPos)`, and
  `Vfx.play(id, Vec3, rotation, ...)` overloads remain **client-local**
  for compatibility because they do not carry a Level.
- Source identity + local rotation/offset uses NeoForge payload protocol **v2**.
  The server and connecting clients need compatible Pyro VFX versions; version
  1 payloads cannot be decoded as version 2.

See [NETWORKING.md](NETWORKING.md) for recipients, transport, retry
window, lifecycle and other protocol limitations.

## Verification

`VfxUnifiedApiGameTests` checks automatic Entity/BlockEntity routing,
chained offset/rotate order, world-aware block/fixed sources, source removal,
server-side rebind, automatic Java callback streaming, lease expiry and
explicit streaming. The prior
`publicVfxApiRemainsInertOnLogicalServer` test still guards the old
client-only position overload. Real-client Test Harness scenarios
`m7_api_handles`, `m7_network_sync`, and `m7_network_attachments`
exercise the new public route and actual integrated-server packet flow,
including client pose/rotation matching and chained rebind. The separate
multi-client test procedure remains documented in NETWORKING.md; a single
integrated client does not establish two-client compatibility.

```powershell
.\gradlew.bat build runGameTestServer --offline --console=plain
.\gradlew.bat runClientBench '-PbenchScenario=m7_api_handles,m7_network_sync,m7_network_attachments,m7_network_auto' -PbenchWidth=1280 -PbenchHeight=720 --offline --console=plain
```
