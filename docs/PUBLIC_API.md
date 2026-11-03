# Pyro VFX public API (M7.2)

Use `net.kofllee.pyrovfx.api.Vfx` to play a JSON-defined effect. Every successful
call returns a `VfxHandle` for **that one instance**, without exposing the
internal manager, emitter or renderer.

```java
import net.kofllee.pyrovfx.api.Vfx;
import net.kofllee.pyrovfx.api.VfxHandle;
import net.kofllee.pyrovfx.vfx.transform.VfxTransformProvider;

ResourceLocation smoke = ResourceLocation.fromNamespaceAndPath("pyrotechnica", "rocket_trail");

// Simple playback with built-in Minecraft sources:
Vfx.play(smoke, hitPosition);
Vfx.play(smoke, rocket);
Vfx.play(smoke, blockPos);
Vfx.play(smoke, blockEntity);
Vfx.play(smoke, hitPosition, rotation, Map.of("power", 1.5));

// Vfx.at converts an entity, block or block entity into a transform provider.
// Offsets and rotations are applied relative to the tracked source:
VfxHandle trail = Vfx.play(smoke,
    Vfx.at(rocket).offset(0, 1.0, -0.5).rotate(0, 90, 0),
    Map.of("power", 1.5));
Vfx.play(smoke, Vfx.at(blockPos).offset(0, 0.5, 0));

// Already have a provider? Pass it directly. Vfx.at(provider) is unnecessary:
VfxTransformProvider muzzleProvider = partialTick -> mechanism.getMuzzleTransform(partialTick);
Vfx.play(smoke, muzzleProvider);
Vfx.play(smoke, muzzleProvider.offset(0, 0, 0.5));

trail.setParameter("power", 2.0);
trail.trigger("burst");
trail.attach(Vfx.at(blockEntity).offset(0, 0.5, 0).rotate(0, 90, 0));
trail.attach(muzzleProvider);
trail.detach();  // Keep the last sampled world pose; continue playing there.
trail.stop();
```

All simple `play` overloads accept `Map<String, Double>` effect parameters;
the fixed-position + quaternion overload does too. For source-local translation
and rotation, compose a `VfxTransformProvider` using `offset(x, y, z)`,
`offset(Vec3)`, `rotate(xDegrees, yDegrees, zDegrees)` or
`rotate(Quaternionf)`. These methods return a new provider and may be chained;
their order matters: `offset(...).rotate(...)` translates along the original
source axes, whereas `rotate(...).offset(...)` rotates the offset as well.

`Vfx.at(entity)`, `Vfx.at(blockPos)` and `Vfx.at(blockEntity)` each return
a `VfxTransformProvider`; there is intentionally no `Vfx.at(provider)`.
`Vfx.play(id, provider)` and `handle.attach(provider)` accept it directly.
Entity offsets follow yaw/pitch. Block and block-entity sources are centered on
the block with world-aligned axes (not automatically rotated by block facing).
For animated model parts, supply a callback returning the actual part's
world-space pose (see M7.1); no generic bone lookup is claimed.
`VfxTransformProvider.get(partialTick)` returns a `VfxTransform` or `null`
if the source becomes unavailable. An `at(...)` call made outside a valid
client world/thread yields an unavailable provider rather than throwing.
There are no attachment-name registries, named slots or JSON-level source IDs.

## Semantics

- **Client-side only in M7.2.** Call from the Minecraft client game thread,
  after a client world has loaded. Calling from a dedicated server, from the
  integrated logical server thread or off the client thread returns an inactive
  handle / `false`; it does *not* broadcast to players. Server networking is M7.3.
- `handle.isActive()` is `false` after normal expiration, explicit stop, budget
  or start rejection, world exit, or `ClientVfxManager.clear()` (used by dev
  tools). The handle stores a numeric lookup token, not an instance reference.
- Calling `stop()` from a transform callback during an effect tick or render
  pass is supported: the handle becomes inactive immediately, and the manager
  releases the instance at the safe end of the current pass.
- Missing JSON effect, invalid/missing source, unknown or non-finite start
  parameters, or the effect-count budget limit yield an inactive handle.
  `stop()` is idempotent: only the first successful call returns `true`.
- `setParameter(name, value)` returns `false` for unknown names, non-finite
  values or inactive handles. Changes affect future evaluations; expressions
  already cached at particle birth are not retroactively changed.
- `trigger(eventName)` returns `false` for unknown events, an unavailable
  source, an inactive handle, or a full 128-entry event queue. Valid events run
  **on the next client game tick**, using the same per-effect/global event and
  spawn budgets as JSON triggers. They are not rendered or dispatched mid-frame.
- `attach(provider)` first samples the replacement at `partialTick=1`; `false`
  leaves the existing attachment unchanged. A successful rebind starts at the
  new pose and resets distance-trigger spacing (without rearming one-shot
  distance events). `detach()` freezes the last position *and rotation*,
  rather than treating the source as lost, so emitters can continue playing.
  Existing world-space particles remain in world space, while local-space
  particles follow the newly selected source.
- A provider disappearing naturally **stops new emission**, releases its
  callback and allows surviving particles to finish (M7.1 behavior). Explicit
  `stop()` instead closes all resources immediately. Resource reload preserves
  instances; changing/unloading a world invalidates handles.

## Verification

The server GameTest `publicVfxApiRemainsInertOnLogicalServer` guards the
dedicated/logical server path. The real Minecraft Test Harness scenario
`m7_api_handles` checks missing effects, unavailable providers, start overrides,
parameter updates, queued events, reattachment/detachment, idempotent stop,
resource cleanup, stale handle invalidation and reentrant stop callbacks.

Run the client check with:

```powershell
.\gradlew.bat runClientBench '-PbenchScenario=m7_api_handles' -PbenchWidth=1280 -PbenchHeight=720 --console=plain
```

Run the server checks with:

```powershell
.\gradlew.bat runGameTestServer --console=plain
```