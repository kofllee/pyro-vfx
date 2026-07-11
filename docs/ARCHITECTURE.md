# Architecture

Pyro VFX separates effect description, resource loading, runtime simulation and rendering.

The central rule is:

```text
Definitions describe an effect.
Runtime objects simulate an effect.
Renderers draw runtime particles.
Development tools invoke and inspect the system.
```

## Data flow

```text
JSON resource
    -> VfxJsonLoader
    -> VfxDefinitionParser
    -> VfxDefinition
    -> VfxRegistry
    -> ClientVfxInstance
    -> ClientVfxEmitter
    -> ClientVfxParticle
    -> Sprite, model or Minecraft particle rendering
```

## Main packages

```text
net.kofllee.pyrovfx
net.kofllee.pyrovfx.vfx.definition
net.kofllee.pyrovfx.vfx.expression
net.kofllee.pyrovfx.vfx.curve
net.kofllee.pyrovfx.vfx.resource
net.kofllee.pyrovfx.vfx.type
net.kofllee.pyrovfx.vfx.value
net.kofllee.pyrovfx.client
net.kofllee.pyrovfx.client.vfx
net.kofllee.pyrovfx.client.vfx.sampling
net.kofllee.pyrovfx.client.render
net.kofllee.pyrovfx.client.dev
```

## Mod entry point

`PyroVfx` is the NeoForge mod entry point.

Its current responsibility is minimal:

- declare the mod id;
- initialize the mod;
- log startup.

The current class is not a public integration facade.

## Definition layer

Package:

```text
net.kofllee.pyrovfx.vfx.definition
```

Definitions represent parsed immutable effect data.

Main objects:

```text
VfxDefinition
VfxMetadataDefinition
VfxParameterDefinition
VfxLifetimeDefinition
VfxEmitterDefinition
VfxEmitterLifetimeDefinition
VfxSpawnAmountDefinition
VfxSpawnShapeDefinition
VfxParticleLifetimeDefinition
VfxMotionDefinition
VfxMotionCollisionDefinition
VfxRotationDefinition
VfxRenderDefinition
VfxEventDefinition
VfxTriggerDefinition
```

Definitions should not:

- tick;
- render;
- access the active Minecraft client;
- create particles by themselves;
- own mutable effect runtime state.

## Type layer

Package:

```text
net.kofllee.pyrovfx.vfx.type
```

Enums define serialized modes and runtime branches.

Examples:

```text
VfxLifetimeMode
VfxEmitterLifetimeMode
VfxSpawnAmountMode
VfxSpawnShapeType
VfxMotionMode
VfxDirectionMode
VfxCollisionType
VfxRotationMode
VfxRenderType
VfxFacingMode
VfxBlendMode
VfxUvMode
VfxModelSourceType
VfxModelRenderLayer
VfxEventType
VfxTriggerType
VfxCurveType
```

The parser normalizes serialized strings to enum values.

## Resource layer

Package:

```text
net.kofllee.pyrovfx.vfx.resource
```

### `VfxJsonLoader`

Responsibilities:

- find `vfx/**/*.json` resources;
- derive effect ids;
- parse JSON;
- register valid definitions;
- log invalid files;
- report the total loaded count.

### `VfxDefinitionParser`

Responsibilities:

- enforce `pyro_vfx:1`;
- parse all current definition objects;
- parse numeric and vector expressions;
- parse colors;
- validate event references;
- validate emitter ids;
- validate event-to-emitter references;
- validate trigger-to-event references;
- validate `set_param` references.

### `VfxRegistry`

Responsibilities:

- map effect ids to definitions;
- expose registered ids;
- clear and rebuild on reload.

### `VfxReloadListener`

Runs the loader during client resource reload.

## Expression layer

Package:

```text
net.kofllee.pyrovfx.vfx.expression
```

### Compilation

Expression source strings are converted to `VfxExpressionNode` trees by:

```text
VfxExpressionCompiler
VfxExpressionParser
```

Runtime fields store expression objects rather than raw strings.

### Context

`VfxExpressionContext` exposes:

- named numbers;
- grouped parameter values;
- vectors through component paths;
- named curves;
- a random source.

`VfxContextBuilder` constructs parent-child context scopes.

### Runtime context creation

`ClientVfxExpressionContexts` creates contexts for:

- effect start;
- effect tick;
- emitter start;
- emitter tick;
- particle spawn;
- particle tick.

This keeps field evaluation tied to the correct runtime stage.

## Curve layer

Package:

```text
net.kofllee.pyrovfx.vfx.curve
```

Main objects:

```text
VfxCurveSet
VfxCurveDefinition
VfxCurvePoint
```

Curves are stored on `VfxDefinition` and sampled through expression functions.

## Client integration

Package:

```text
net.kofllee.pyrovfx.client
```

`PyroVfxClient` registers:

- the reload listener;
- client tick handling;
- world render handling;
- additional custom VFX models.

All active simulation and custom rendering are client-side.

## Runtime layer

Package:

```text
net.kofllee.pyrovfx.client.vfx
```

### `ClientVfxManager`

Responsibilities:

- store active effect instances;
- add new instances;
- tick all instances;
- remove failed or finished instances;
- clear instances when no client level exists;
- expose basic active instance and particle counts.

Current constraints:

- global static storage;
- no handles;
- no global budgets;
- no attachment support;
- no spatial indexing.

### `ClientVfxInstance`

Represents one playing effect.

State includes:

- definition;
- fixed world position;
- runtime emitters;
- runtime parameters;
- effect random value;
- resolved effect lifetime;
- effect age;
- trigger state.

Responsibilities:

- resolve startup parameters;
- resolve effect lifetime;
- create runtime emitters;
- create effect contexts;
- tick effect triggers;
- update emitter positions from offset expressions;
- tick emitters;
- finish after lifetime, emitters and particles complete.

The current effect origin is fixed after creation.

### `ClientVfxEmitter`

Represents one runtime emitter.

State includes:

- definition;
- lifetime values;
- spawn accumulator;
- emitted particle count;
- active particle list;
- emitter random value;
- trigger state.

Responsibilities:

- compute emitter lifetime;
- create emitter contexts;
- run emitter triggers;
- run spawn amount logic;
- create particles;
- tick particles;
- run collision particle triggers;
- remove dead particles.

### `ClientVfxParticle`

Represents one custom sprite or model particle.

State includes:

- current and previous position;
- spawn position;
- velocity;
- age and lifetime;
- scale and color state;
- rotation and angular velocity;
- collision state;
- render definition access.

Minecraft fallback particles are not represented by this class after spawning.

### Lifetime runtime

`VfxLifetimeRuntime` converts lifetime definitions and age into:

```text
VfxLifetimeState
```

The state reports:

- global age;
- local cycle age;
- active age;
- normalized age;
- active state;
- finished state.

## Event runtime

Main classes:

```text
VfxEventRunner
VfxEventRuntime
```

Events are stored on the root definition.

Triggers call events by id.

Event execution can:

- emit from a manual emitter;
- run nested events in sequence;
- choose one nested event;
- mutate a runtime parameter.

## Sampling layer

Package:

```text
net.kofllee.pyrovfx.client.vfx.sampling
```

Main samplers:

```text
VfxSpawnPositionSampler
VfxMotionSampler
VfxRotationSampler
```

Responsibilities:

- sample a point inside or on a configured shape;
- derive initial velocity;
- derive initial rotation;
- derive initial angular velocity.

Spawn sampling is separated from emitter control flow.

## Rendering layer

Package:

```text
net.kofllee.pyrovfx.client.render
```

### `VfxRenderer`

The world render entry point:

- obtains Minecraft's buffer source;
- walks active instances;
- walks active particles;
- dispatches by render type;
- ends the buffer batch once after traversal.

### Sprite rendering

Main classes:

```text
SpriteVfxParticleRenderer
VfxSpriteRenderTypes
VfxSpriteUvSampler
ClientVfxSpriteUvResolver
VfxRenderLight
VfxRenderTransforms
```

Sprite rendering evaluates interpolation, facing, rotation, UVs, lighting and vertex output.

### Model rendering

Main dispatcher:

```text
ModelVfxParticleRenderer
```

Source-specific renderers:

```text
CustomModelVfxParticleRenderer
BlockModelVfxParticleRenderer
ItemModelVfxParticleRenderer
EntityModelVfxParticleRenderer
```

Model lookup and draw behavior depend on the configured source.

### Minecraft particle bridge

`VanillaParticleBridge` delegates fallback particles to the Minecraft level particle system.

## Development commands

Package:

```text
net.kofllee.pyrovfx.client.dev
```

`PyroVfxClientCommands` currently provides:

- `play`;
- `play_at`;
- `list`;
- `stop_all`;
- effect id suggestions;
- command parameter parsing.

## Current boundaries

### Client-only runtime

The active runtime depends on Minecraft client classes.

There is no server-side effect instance.

### No networking

A server cannot currently send a general Pyro VFX command to clients.

### No attachment abstraction

Effects store a fixed `Vec3` origin.

Moving effects must currently be implemented outside this runtime or recreated manually.

### No public API layer

External code would currently need to reference internal registry and client runtime classes.

A future API should isolate callers from those implementation details.

## Future editor boundary

A future editor should not mutate runtime definitions directly.

Recommended flow:

```text
VfxEditorDocument
    -> serialize JSON
    -> VfxDefinitionParser
    -> VfxDefinition
    -> Client runtime preview
```

Editor-only state should remain outside runtime definitions unless explicitly stored as ignorable metadata.
