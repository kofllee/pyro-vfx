# Pyro VFX public API (M7.2)

Use `net.kofllee.pyrovfx.api.Vfx` to play a JSON-defined effect. Every successful
call returns a `VfxHandle` for **that one instance**, without exposing the
internal manager, emitter or renderer.

```java
import net.kofllee.pyrovfx.api.Vfx;
import net.kofllee.pyrovfx.api.VfxHandle;

ResourceLocation smoke = ResourceLocation.fromNamespaceAndPath("pyrotechnica", "rocket_trail");

// Position, entity, block, block entity, or any user-provided callback:
Vfx.play(smoke, hitPosition);
VfxHandle trail = Vfx.play(smoke, rocket, Map.of("power", 1.5));
Vfx.play(smoke, blockPos);
Vfx.play(smoke, blockEntity);
Vfx.play(smoke, hitPosition, rotation);
Vfx.play(smoke, partialTick -> mechanism.getMuzzleTransform(partialTick));

trail.setParameter("power", 2.0);
trail.trigger("burst");
trail.attach(partialTick -> mechanism.getMuzzleTransform(partialTick));
trail.detach();  // Keep the last sampled world pose; continue playing there.
trail.stop();
```

The same `Map<String, Double>` parameter overload works for position, entity,
block, block entity and custom provider. The rotated-position overload accepts
a quaternion. For animated model parts, supply a callback returning the actual
part's world-space pose (see M7.1); no generic bone lookup is claimed.
`VfxTransformProvider` is the existing functional interface; return a
`VfxTransform` or `null` when the source becomes unavailable. There are no
attachment-name registries, named slots, or JSON-level source identifiers.

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