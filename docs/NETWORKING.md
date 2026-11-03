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

// A client-resolved animated point: only its name + entity identity cross the wire.
VfxHandle head = Vfx.play(smoke,
        Vfx.at(projectile).socket("head").offset(0, 0.2, -0.3), options);

// Arbitrary server-owned transforms need no explicit updatePose calls.
VfxHandle muzzle = Vfx.play(smoke, serverLevel,
        partialTick -> mechanism.getMuzzleTransform(partialTick), options);
```

On the logical server, the world-aware overload broadcasts to tracking clients; on the client game thread it plays locally. A plain position requires an explicit Level. The default is **96-block radius and a finite 1200-tick server lease**. Existing explicit `Vfx.play(ServerLevel, id, ..., radius, durationTicks)` overloads and `VfxRemoteHandle` remain available for compatibility, but are not needed by ordinary mods.

This dispatch does not deduplicate a gameplay callback invoked on both logical sides. Call shared gameplay VFX on the server once, or purely client-side visuals on the client. A `VfxHandle` is a client instance token on the client and a server lease token on the server; the latter cannot guarantee a particular client successfully displayed the effect.

For a fixed source, pass a `Vec3` or `VfxTransform` with Level; for a moving vanilla source, pass an `Entity`, `BlockPos` or `BlockEntity`. **Built-in provider chains now preserve their identity and local pose**, so `Vfx.play(smoke, Vfx.at(projectile).offset(0, 0, -0.5).rotate(0, 90, 0))` works from the server without custom packet code. For a block, write `Vfx.at(level, blockPos)`; the old `Vfx.at(blockPos)` shorthand without Level is client-local.

Arbitrary Java callbacks cannot be serialized as code. When you supply their Level explicitly — `Vfx.play(smoke, level, partialTick -> calculatePose(partialTick))` — the logical server automatically samples them on ticks and uses the existing bounded stream protocol. Built-in sources still use vanilla synchronization and no regular pose traffic. The old `Vfx.play(id, Vec3)` and `Vfx.play(id, BlockPos)` overloads remain local because they have no Level. See [PUBLIC_API.md](PUBLIC_API.md).

## Source identities and payloads

Every live remote effect has one random UUID per instance. The client maps that UUID to its existing local numeric effect handle. The network UUID is not a particle ID and is not reused across instances. Each command includes its dimension identity.

The **v3** `RegisterPayloadHandlersEvent` registration declares six **clientbound** NeoForge `CustomPacketPayload` types using `StreamCodec<RegistryFriendlyByteBuf, ...>`. Version 3 extends the v2 local pose with a named socket and an elapsed timeline tick on Start. Client and server must run compatible Pyro VFX protocol versions; this is not wire-compatible with v1/v2:

| Payload | Meaning |
|---|---|
| `Start` | Effect ResourceLocation, instance UUID, dimension, attachment, initial numeric parameters, server elapsed ticks |
| `Stop` | Stop a specific remote instance |
| `Parameter` | Update one numeric parameter |
| `Trigger` | Queue an existing named event for the next client tick |
| `Rebind` | Switch this instance to a new attachment |
| `Pose` | Supply a current pose only for an explicitly streamed source |

Attachment `kind` is **fixed**, **entity**, **block**, **block_entity**, or **stream**. All carry a pose snapshot for startup, but client Entity/Block sources resolve the actual tracked object:

- Entity: vanilla runtime entity ID plus UUID to guard against recycled numeric IDs, **plus composed local transform**. The client fetches the live entity in its own level, applies vanilla yaw/pitch interpolation, then composes the offset/rotation.
- Block: immutable BlockPos **plus composed local transform**, sampled only from a loaded client chunk. The built-in block provider rejects air/replacement with a different block type.
- BlockEntity: BlockPos **plus composed local transform**; resolves the actual client BlockEntity in the chunk and applies the same local offset/rotation.
- Fixed: immutable position and quaternion; no ongoing synchronization.
- Stream: initial position and quaternion, followed by explicit `updatePose` snapshots. Client keeps previous/current target poses and interpolates during render.

**Named sockets:** An Entity or BlockEntity Anchor may contain a socket name in addition to its identity and local translation/rotation. `Vfx.at(entity).socket("head")` automatically looks up vanilla `ModelPart` hierarchies on each client, including nested paths such as `root/head`. Standard humanoid models expose fields such as `head` and `right_arm`. Vanilla chest and ender chest BlockEntity renderers expose `lid`, `lock` and `bottom`. The client samples these points even if their owners are outside the camera frustum. The server does not need the bone pose and sends no per-frame bone transforms. For custom or modded renderers, register a client resolver using `VfxSockets.registerEntity("modid:muzzle", (entity, partial) -> pose)` or `VfxSockets.registerBlockEntity(...)`; the callback returns the **world-space** `VfxTransform`. Other mods must provide the same socket name and compatible animation state on every client. Unknown socket names never silently fall back to the entity center.

Vanilla living-model fallback re-evaluates standard `prepareMobModel/setupAnim` and restores renderer-owned `ModelPart` poses. It does not reproduce every custom renderer's special pre-transforms, custom animation library, renderer layer, or baby-model layout exactly; supply an explicit resolver for those. Built-in vanilla chest lid/lock paths follow their local opening angle; double-chest neighbor openness is not yet perfectly reconstructed. Arbitrary Java callbacks implemented solely inside client renderers are not serialized. If a custom transform *does* exist on the server, a world-aware Java callback is instead **automatically sampled/streamed** without `updatePose()`.

## Audience, timing and traffic

For each live server instance, the server checks the **same dimension**, **radius** from the composed world position (96 blocks by default), and the player's **vanilla chunk-tracking view** for the *original source* chunk. For client-only animated sockets the server can only estimate distance using the owner pose and local offset; huge visual socket displacements need a suitable audience radius. On entering the audience, a client receives one complete Start snapshot, including current parameter values and elapsed age; on leaving, one Stop. Stationary effects do not send a transform every tick.

To reduce server load, candidate players are grouped in 64-block spatial bins once per dimension/tick. Stationary and slow-moving sources recheck recipients periodically (nominally every four server ticks, with instances spread over separate ticks); fast motion or source-position changes force an immediate recheck. This can delay an audience Start/Stop by several ticks, rather than polling all players per effect every tick. `/pyrovfxmuseum network status` reports measured previous-tick server VFX CPU time, live instances, player candidate checks and Start/Stop/Pose packet counts. It does **not** measure real packet bytes, network latency or 50-/100-player server throughput.

Entity movement uses Minecraft's existing entity synchronization. Block/BlockEntity positions use Minecraft's vanilla world updates. Their source-local offset/rotation is sent only with Start or Rebind, not every tick. For streamed custom poses, whether automatic or explicit, updates are capped to one packet per **two server ticks (10 Hz)** and suppressed below 0.01 blocks of displacement and negligible quaternion changes. The client samples poses on game ticks, interpolating render samples without adding new particle network traffic. Very fast streamed motion may still show latency, and this is not a lock-step animation protocol.

Numeric parameter maps are limited to **32** keys and names/events to **64** characters; effect leases are limited to **4,096 per dimension**, radius **256 blocks**, and maximum finite lease **10 minutes**. `durationTicks == -1` explicitly means an indefinite lease requiring a stop/source removal/world unload. On packet receipt, the client's regular effect, event and particle budgets remain authoritative and can reject playback; the server does not override local client budgets.

For late-arriving entities/chunks the client retries source resolution for up to **80 client ticks**. Parameter changes and a bounded queue of events are held while awaiting a source. A remote Start now carries server elapsed ticks. A client joining an already-running effect positions its effect/emitter lifetime at that phase, rather than replaying early timeline triggers and burst emissions. This aligns future event scheduling, **not** the complete historical particle field: already emitted smoke is not reconstructed, random state is not deterministic across clients, and transient collision history is not replayed. A source removed or unloaded later is handled by existing provider lifecycle and server-side Stop. Networked Stop **immediately closes** the effect and its particles; unlike natural local provider loss it does not wait for particles to fade out.

Client world exit, switching dimensions, resource reload, server level unload and repeated stops do not retain stale local effect handles. The server has no client rendering or particle classes in its simulation path. Server APIs only work from the owning logical server thread; received payloads are delivered by NeoForge to the client game thread.

## Limitations (intentional, not verified claims)

- Clients must have the effect's JSON resources. Server registration does not transfer effect definitions or validate client-known parameter names/events; clients reject missing effects/invalid parameters.
- The server does not track client-side natural effect expiration. New viewers align the effect/emitter timeline but **do not reconstruct previously emitted live particles or historic events**; pixel-identical synchronized shows require a separate deterministic seed/state synchronization mechanism.
- Start packets cannot force-load entity or block sources; missing sources time out after 80 ticks. There is no client acknowledgment/retry after that timeout.
- Server-side block identity detects air and changed block **types**, not a same-type block being broken and replaced instantly.
- Recipient filtering uses the **source chunk**, radial distance and dimension, not arbitrary emitter/particle bounds or camera-frustum occlusion. M7.4 will investigate more detailed distance/visibility LOD.
- **Two independently connected real clients on a localhost-only dedicated development server are now tested** (see acceptance results below). This does not establish bandwidth percentiles, dozens of active players, high-latency Internet compatibility, large modpacks or shaderpacks. Do not treat this short localhost acceptance run as a production multiplayer soak test.

## Reproducing tests

```powershell
.\gradlew.bat build runGameTestServer --offline --console=plain
.\gradlew.bat runClientBench '-PbenchScenario=m7_network_sync,m7_network_attachments,m7_network_auto,m7_network_sockets,m7_network_late' -PbenchWidth=1280 -PbenchHeight=720 --offline --console=plain
```

The dedicated NeoForge GameTests check network-anchor encoding (including socket IDs), bounded decoding, unified public API dispatch, server-only leases, idempotent Stop, finite duration, source removal and a stress fixture with **1,000 concurrent static server leases**. In the zero-player headless stress run, a sampled tick processed 266 audience scans in 0.678 ms. The Test Harness executes actual NeoForge packets through an integrated Minecraft server and client: the socket probe tests vanilla head, registered custom socket, chest lid, rebind and source removal; the late-join probe leaves the radius and returns to verify continued age. Benchmark JSON files (including status/error fields) are under `run/pyrovfx-benchmarks/`.

### Dedicated server with two independent clients (verified, 3 November 2026)

The explicit development-only acceptance runner launches one NeoForge server bound to `127.0.0.1:25575` and two **independent Minecraft client processes** (`PyroA`, `PyroB`), each with a separate working directory. It uses an isolated temporary offline-mode localhost test world, not a user's normal saves or an exposed production server. The server test hook only runs with the `pyrovfx.multiclient.server=true` development system property; client hooks likewise require their explicit A/B opt-in.

**Observed results:** `PyroA RESULT: PASS (8 checks, 0 failures)` and `PyroB RESULT: PASS (8 checks, 0 failures)` from their separate client logs. The phases covered Start, B leaving the audience, B returning at the current age, B switching to Nether, B returning to Overworld, source Entity removal, recreation with a new Entity and fresh VFX, and Stop/cleanup. Both clients independently checked actual `socket("head")` world poses and that neither duplicated nor leaked a remote effect. The B return after dimension transfer reported effect age 158 rather than zero.

The same dedicated server also sampled **1,000 simultaneously active server effects with 2 real players**. One recorded server VFX tick reported `audienceScans=254`, `playerChecks=508`, `starts=0`, `stops=0`, `posePackets=0`, `networkTickNanos=310300` (**0.310 ms**). All load effects were intentionally out of both clients' 1-block recipient radii. Thus this measures candidate filtering and server bookkeeping with two connected players, **not 1,000 packet broadcasts, bandwidth, a sustained p95/p99 or a high-player-count test**.

To reproduce, prepare an isolated `run-network-test-server/server.properties` containing `server-ip=127.0.0.1`, `server-port=25575`, and `online-mode=false` (strictly for localhost development). Start each Gradle task independently, with server first:

```powershell
.\gradlew.bat runNetworkTestServer --offline --console=plain --no-configuration-cache
.\gradlew.bat runNetworkTestClientA --offline --console=plain --no-configuration-cache
.\gradlew.bat runNetworkTestClientB --offline --console=plain --no-configuration-cache
```

New client profiles may first require clicking Minecraft's one-time Accessibility welcome **Continue** button; once loaded, `--quickPlayMultiplayer` joins automatically. Check `run-network-test-client-a/logs/latest.log`, `run-network-test-client-b/logs/latest.log` and `run-network-test-server/logs/latest.log` for `PYRO_2CLIENT` results. Test clients auto-exit; stop the dedicated server after the test. Test profiles/worlds are excluded from Git.

For interactive tests with 2+ independently connected clients, operator commands on a shared server are:

```text
/pyrovfxmuseum network static
/pyrovfxmuseum network entity
/pyrovfxmuseum network offset
/pyrovfxmuseum network socket
/pyrovfxmuseum network rebind
/pyrovfxmuseum network block
/pyrovfxmuseum network stream
/pyrovfxmuseum network auto
/pyrovfxmuseum network event
/pyrovfxmuseum network trigger
/pyrovfxmuseum network parameter
/pyrovfxmuseum network stop
/pyrovfxmuseum network status
```

These demos use `assets/pyro_vfx/vfx/...` museum resources and only broadcast to nearby tracking clients. `offset` demonstrates a moving entity-local, rotated effect; `rebind` changes its local pose mid-flight; `auto` demonstrates a custom server callback that is sampled and streamed automatically; `stream` is the manual explicit pose baseline.

`/pyrovfx sockets` (client command) lists vanilla `ModelPart` socket names for the targeted entity, falling back to your player. In code, call `VfxSockets.vanillaParts(entity, partialTick)` or `VfxSockets.vanillaParts(blockEntity)` to inspect recognized parts. These are diagnostics; modded `registerEntity/registerBlockEntity` names are owned by the providing mod.

**Additional manual/long-run coverage still needed:** Two clients on a LAN/Internet server with real authentication, repeated joins/leaves under latency/loss, shader/mod compatibility, moving model/animation fidelity across multiple rigs, moving fast out of view, and measurement of actual packet bytes and latency distribution while numerous observers are receiving stream packets. `network auto`, `network rebind` and `network offset` museum commands support manual checks. Pixel-identical fireworks show playback still requires deterministic initial seed/particle-history rules beyond phase alignment.

NeoForge API reference: https://docs.neoforged.net/docs/1.21.1/networking/payload/
