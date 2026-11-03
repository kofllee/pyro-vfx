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

## Client-only custom sources and transform composition

```java
VfxTransformProvider nozzle = Vfx.at(rocket)
        .offset(0, 1.0, -0.5)
        .rotate(0, 90, 0);
VfxHandle local = Vfx.play(smoke, nozzle);
local.attach(nozzle.offset(0, 0, 0.5));
```

`Vfx.at(Entity)`, `Vfx.at(BlockPos)`, `Vfx.at(BlockEntity)`, and
`Vfx.play(id, VfxTransformProvider)` are **client-local**. They support
source-local offsets, rotation and animated model-part callbacks. Java
providers (including chained `offset` / `rotate`) cannot be transmitted
as server attachments by the existing networking protocol. Do **not**
call `Vfx.play(id, Vfx.at(entity).offset(...))` in common gameplay code
expecting it to broadcast; the server gets an inactive handle.

For ordinary server Entity/Block attachment use the direct overload.
For a fixed rotated server effect, pass
`Vfx.play(id, level, new VfxTransform(position, rotation))`.
For a server-owned moving model-part/socket pose requiring explicit
synchronization:

```java
VfxHandle socket = Vfx.playStreamed(smoke, serverLevel, initialPose,
        VfxOptions.DEFAULT.withRadius(64));
socket.updatePose(currentPose);      // call as the socket moves on the server
socket.attachStreamed(otherPose);    // switch to a new streamed anchor
socket.stop();
```

Updates are filtered and throttled by the network layer (at most 10 Hz).
`updatePose` / `attachStreamed` return false for a non-streaming/client
handle. A server handle's `attach(VfxTransformProvider)` returns false,
while `attach(Entity)`, `attach(BlockPos)`, `attach(BlockEntity)`,
`attach(Vec3)` and `attach(VfxTransform)` work on both sides.
`detach()` freezes the last pose without ending the instance.

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

See [NETWORKING.md](NETWORKING.md) for recipients, transport, retry
window, lifecycle and other protocol limitations.

## Verification

`VfxUnifiedApiGameTests` checks automatic Entity and BlockEntity routing,
world-aware position/block playback, unified server controls, invalid
sources, configured lease expiry and explicit streaming. The prior
`publicVfxApiRemainsInertOnLogicalServer` test still guards the old
client-only position overload. Real-client Test Harness scenarios
`m7_api_handles`, `m7_network_sync`, and `m7_network_attachments`
exercise the new public route and the actual integrated-server packet flow.

```powershell
.\gradlew.bat build runGameTestServer --offline --console=plain
.\gradlew.bat runClientBench '-PbenchScenario=m7_api_handles,m7_network_sync,m7_network_attachments' -PbenchWidth=1280 -PbenchHeight=720 --offline --console=plain
```
