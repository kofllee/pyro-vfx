# Networked effect instances (NeoForge 1.21.1)

Pyro VFX networking is **server-authoritative for effect commands** and **client-owned for simulation and rendering**. It extends M7.1 Transform Providers and M7.2 Effect Handles without replacing their implementation or selecting a new render backend.

## Public API: networking is automatic for world-aware calls

```java
import net.kofllee.pyrovfx.api.Vfx;
import net.kofllee.pyrovfx.api.VfxHandle;
import net.kofllee.pyrovfx.api.VfxOptions;

ResourceLocation smoke = ResourceLocation.fromNamespaceAndPath("pyrotechnica", "smoke_charge");

VfxHandle effect = Vfx.play(smoke, projectile);
Vfx.play(smoke, blockEntity);
Vfx.play(smoke, level, fixedPosition); // Level + Vec3
Vfx.play(smoke, level, blockPos);      // Level + BlockPos

effect.setParameter("density", 0.3);
effect.trigger("burst");
effect.attach(blockPos);
effect.detach();
effect.stop();

// Optional server audience/lease and initial parameters:
VfxOptions options = VfxOptions.DEFAULT.withRadius(64)
        .withDurationTicks(200).withParameters(Map.of("density", 0.75));
VfxHandle configured = Vfx.play(smoke, projectile, options);

// A custom server-owned moving socket publishes explicit poses (not Java code):
VfxHandle muzzle = Vfx.playStreamed(smoke, serverLevel,
        mechanism.getMuzzleTransform(1.0f), options);
muzzle.updatePose(mechanism.getMuzzleTransform(1.0f));
```

On the logical server, the world-aware overload broadcasts to tracking clients; on the client game thread it plays locally. A plain position requires an explicit Level. The default is **96-block radius and a finite 1200-tick server lease**. Existing explicit `Vfx.play(ServerLevel, id, ..., radius, durationTicks)` overloads and `VfxRemoteHandle` remain available for compatibility, but are not needed by ordinary mods.

This dispatch does not deduplicate a gameplay callback invoked on both logical sides. Call shared gameplay VFX on the server once, or purely client-side visuals on the client. A `VfxHandle` is a client instance token on the client and a server lease token on the server; the latter cannot guarantee a particular client successfully displayed the effect.

For a fixed source, pass a `Vec3` or `VfxTransform` with Level; for a moving vanilla source, pass an `Entity`, `BlockPos` or `BlockEntity`. Java `VfxTransformProvider`, including `Vfx.at(entity).offset(...).rotate(...)`, stays **client-local**; its callback and local offset chain cannot be transmitted under the current protocol. For such server-owned moving parts, explicitly stream their world-space pose. The old `Vfx.play(id, Vec3)` and `Vfx.play(id, BlockPos)` overloads also remain client-only because they have no Level. See [PUBLIC_API.md](PUBLIC_API.md).

## Source identities and payloads

Every live remote effect has one random UUID per instance. The client maps that UUID to its existing local numeric effect handle. The network UUID is not a particle ID and is not reused across instances. Each command includes its dimension identity.

The versioned `RegisterPayloadHandlersEvent` registration declares six **clientbound** NeoForge `CustomPacketPayload` types using `StreamCodec<RegistryFriendlyByteBuf, ...>`:

| Payload | Meaning |
|---|---|
| `Start` | Effect ResourceLocation, instance UUID, dimension, attachment, initial numeric parameters |
| `Stop` | Stop a specific remote instance |
| `Parameter` | Update one numeric parameter |
| `Trigger` | Queue an existing named event for the next client tick |
| `Rebind` | Switch this instance to a new attachment |
| `Pose` | Supply a current pose only for an explicitly streamed source |

Attachment `kind` is **fixed**, **entity**, **block**, **block_entity**, or **stream**. All carry a pose snapshot for startup, but client Entity/Block sources resolve the actual tracked object:

- Entity: vanilla runtime entity ID plus UUID to guard against recycled numeric IDs. The client fetches the live entity in its own level and builds the ordinary Entity Transform Provider.
- Block: immutable BlockPos, sampled only from a loaded client chunk. The built-in block provider rejects air/replacement with a different block type.
- BlockEntity: BlockPos, resolves the actual client BlockEntity in the chunk, then uses the ordinary BlockEntity provider.
- Fixed: immutable position and quaternion; no ongoing synchronization.
- Stream: initial position and quaternion, followed by explicit `updatePose` snapshots. Client keeps previous/current target poses and interpolates during render.

An animated bone or custom Java transform remains entirely local. For networked animations, use a client-side mechanism/animation system which already has the relevant model transform, or explicitly stream the computed position and quaternion. The protocol does not encode Java code or arbitrary mod-owned objects.

## Audience, timing and traffic

For each live server instance, the server checks the **same dimension**, **radius** (96 blocks by default for world-aware calls), and the player's **vanilla chunk-tracking view** for the source chunk. On entering the audience, a client receives one complete Start snapshot, including current parameter values; on leaving, one Stop. Stationary effects do not send a transform every tick.

Entity movement uses Minecraft's existing entity synchronization. Block/BlockEntity positions use Minecraft's vanilla world updates. For streamed custom poses, updates are capped to one packet per **two server ticks (10 Hz)** and suppressed below 0.01 blocks of displacement and negligible quaternion changes. The client samples poses on game ticks, interpolating render samples without adding new particle network traffic. Very fast streamed motion may still show latency, and this is not a lock-step animation protocol.

Numeric parameter maps are limited to **32** keys and names/events to **64** characters; effect leases are limited to **4,096 per dimension**, radius **256 blocks**, and maximum finite lease **10 minutes**. `durationTicks == -1` explicitly means an indefinite lease requiring a stop/source removal/world unload. On packet receipt, the client's regular effect, event and particle budgets remain authoritative and can reject playback; the server does not override local client budgets.

For late-arriving entities/chunks the client retries source resolution for up to **80 client ticks**. Parameter changes and a bounded queue of events are held while awaiting a source. A source removed or unloaded later is handled by existing provider lifecycle and server-side Stop. Networked Stop **immediately closes** the effect and its particles; unlike natural local provider loss it does not wait for particles to fade out. For a disappearing-source fade-out, the gameplay mod should spawn a separate short-lived fixed-position effect before stopping the attached one.

Client world exit, switching dimensions, resource reload, server level unload and repeated stops do not retain stale local effect handles. The server has no client rendering or particle classes in its simulation path. Server APIs only work from the owning logical server thread; received payloads are delivered by NeoForge to the client game thread.

## Limitations (intentional, not verified claims)

- Clients must have the effect's JSON resources. Server registration does not transfer effect definitions or validate client-known parameter names/events; clients reject missing effects/invalid parameters.
- The server does not track client-side natural effect expiration. New viewers of a still-leased instance can start its animation from the beginning, rather than from the original creator's exact elapsed phase.
- Start packets cannot force-load entity or block sources; missing sources time out after 80 ticks. There is no client acknowledgment/retry after that timeout.
- Server-side block identity detects air and changed block **types**, not a same-type block being broken and replaced instantly.
- Recipient filtering uses the **source chunk**, radial distance and dimension, not arbitrary emitter/particle bounds or camera-frustum occlusion. M7.4 will investigate more detailed distance/visibility LOD.
- The initial tests do not establish bandwidth percentiles or correctness across independent multi-client connections, large modpacks, shaderpacks or unstable Internet connections. Do not treat an integrated-server test as a full multiplayer soak test.

## Reproducing tests

```powershell
.\gradlew.bat build runGameTestServer --offline --console=plain
.\gradlew.bat runClientBench '-PbenchScenario=m7_network_sync,m7_network_attachments' -PbenchWidth=1280 -PbenchHeight=720 --offline --console=plain
```

The dedicated NeoForge GameTests check network-anchor encoding, bounded decoding, unified public API dispatch, server-only leases, idempotent Stop, finite duration, and removal of Entity/Block sources. The Test Harness executes actual NeoForge packets through an integrated Minecraft server and client via the unified Vfx.play API: start, parameters, named events, stop and matching Entity/Block transforms. Benchmark JSON files (including status/error fields) are under `run/pyrovfx-benchmarks/`.

For interactive tests with 2+ independently connected clients, operator commands on a shared server are:

```text
/pyrovfxmuseum network static
/pyrovfxmuseum network entity
/pyrovfxmuseum network block
/pyrovfxmuseum network stream
/pyrovfxmuseum network event
/pyrovfxmuseum network trigger
/pyrovfxmuseum network parameter
/pyrovfxmuseum network stop
/pyrovfxmuseum network status
```

These demos use `assets/pyro_vfx/vfx/...` museum resources and only broadcast to nearby tracking clients. The `stream` example drives an orbiting position/rotation on the logical server, with no Java callback transfer. Do not classify those scenarios as *multi-client verified* unless they have been exercised with actual separate clients.

NeoForge API reference: https://docs.neoforged.net/docs/1.21.1/networking/payload/
