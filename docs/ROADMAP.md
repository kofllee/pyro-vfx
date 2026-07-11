# Possible Future Development

> Pyro VFX is currently paused.
>

## Product direction

The strongest standalone direction for Pyro VFX is:

> A complete in-game VFX editor for Minecraft, backed by the existing JSON runtime.

The JSON format should remain the source format, while the editor provides visual authoring, preview, validation and profiling.

## Phase 1: runtime stability

- Add automated parser and curve tests.
- Improve load error messages.
- Verify dedicated server class loading.
- Improve validation coverage.
- Add focused regression effects.

## Phase 2: profiling and runtime cleanup

- Add reproducible benchmarks.
- Remove per-frame combined particle list allocation.
- Measure expression context allocation.
- Measure sprite render cost.
- Measure model render cost.
- Measure collision cost.
- Add active effect and particle statistics.
- Add global particle budgets.
- Add collision budgets.
- Add distance culling.
- Add frustum culling.
- Add effect priority.
- Add spawn and update LOD.

## Phase 3: in-game editor vertical slice

The first editor version should support:

- open an existing effect;
- create a new effect;
- display emitter list;
- select an emitter;
- edit lifetime;
- edit spawn amount;
- edit spawn shape;
- edit motion;
- edit rotation;
- edit render fields;
- edit color and scale;
- restart live preview;
- save JSON;
- reload the saved effect.

This vertical slice is enough to prove the main product.

## Phase 4: editor structure

### Effect browser

- search;
- namespace filter;
- create;
- duplicate;
- rename;
- delete;
- saved/modified/invalid state;
- validation errors.

### Emitter tree

- add;
- remove;
- duplicate;
- rename;
- reorder;
- enable/disable;
- solo preview.

### Inspector

- numeric value mode;
- expression mode;
- vector controls;
- color control;
- resource selectors;
- enum selectors;
- contextual help.

### Live preview

- play;
- pause;
- stop;
- restart;
- loop;
- manual emit;
- preview position;
- preview rotation;
- isolated emitter preview.

## Phase 5: visual authoring

### Curve editor

- point creation and deletion;
- drag points;
- linear, step, Catmull and smooth modes;
- preview cursor;
- copy and paste;
- presets;
- property assignment.

### Event timeline

- effect triggers;
- emitter triggers;
- manual emit events;
- set parameter events;
- draggable timeline markers;
- nested sequence display.

### World gizmos

- point;
- sphere;
- box;
- line;
- disc;
- ring;
- cone;
- model sampling bounds;
- origin;
- direction axis;
- preview velocity.

## Phase 6: editor performance tools

The editor should display:

- active effects;
- active emitters;
- active particles;
- sprite particles;
- model particles;
- collision particles;
- particles spawned per second;
- tick cost;
- render cost;
- budget usage;
- culled particles;
- expensive emitters.

The editor should warn when an effect:

- uses too many model particles;
- enables collision on too many particles;
- uses many per-tick expressions;
- creates many render groups;
- has unlimited loops;
- has no practical particle cap.

## Phase 7: integration API

Possible API:

```java
VfxHandle handle = PyroVfxApi.play(level, effectId, position);
VfxHandle handle = PyroVfxApi.play(level, effectId, position, parameters);

PyroVfxApi.stop(handle);
PyroVfxApi.trigger(handle, eventId);
PyroVfxApi.setParameter(handle, parameterId, value);
```

The API should prevent external mods from depending directly on client runtime implementation classes.

## Phase 8: attachments

Possible targets:

- fixed world position;
- block position;
- entity;
- projectile;
- player;
- custom transform provider.

Possible API:

```java
VfxHandle handle = PyroVfxApi.playAttached(entity, effectId, offset);
```

Attached effects need:

- follow position;
- optional follow rotation;
- local offset;
- missing-target behavior;
- stop behavior;
- lifecycle ownership.

## Phase 9: networking

The server should send effect events, not individual particles.

Possible packets:

```text
play effect
play attached effect
stop effect
trigger event
set parameter
```

Packet data:

```text
effect id
position or target
rotation or direction
parameters
visibility radius
runtime handle id
```

Particles remain client-simulated.

## Phase 10: format and tooling polish

- JSON schema.
- Generated reference documentation.
- Better validation paths.
- Unknown field warnings.
- Compatibility policy for `pyro_vfx:1`.
- Future `pyro_vfx:2` migration plan.
- Resource pack examples.
- Example integration mod.
- Maven publication.
- Public API documentation.

## Features intentionally deferred

- GPU particle simulation;
- custom shader graph;
- Fabric port;
- cross-loader abstraction;
- full rigid-body particle physics;
- bone-animated particle models;
- external desktop editor;
- online effect marketplace.

These should not be started until the in-game editor and runtime performance are proven.
