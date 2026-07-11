# Performance Notes

Pyro VFX has not yet been benchmarked or optimized as a production particle engine.

This document records the current runtime shape and the most likely optimization targets if development resumes.

## No current performance guarantee

The current runtime does not guarantee:

- a fixed number of particles at 60 FPS;
- performance parity across sprite and model particles;
- stable performance with collision enabled;
- stable performance with heavy per-particle expressions;
- stable performance with many active effects;
- bounded memory or CPU cost under invalid content.

A statement such as "1000 particles run without FPS loss" should only be made after repeatable profiling on defined hardware and effect configurations.

## Different particle costs

These cases are not equivalent:

```text
1000 additive fullbright sprites
1000 alpha-blended lit sprites
1000 sprites with complex expressions
1000 colliding particles
1000 custom models
1000 item models
1000 entity models
```

Any benchmark must identify the exact render type, material behavior, expression load and collision load.

## Current runtime structure

Particles are stored inside each `ClientVfxEmitter`.

Every client tick:

1. every active effect creates an effect context;
2. every emitter creates an emitter context;
3. every emitter evaluates spawn logic;
4. every custom particle is ticked;
5. collision may query block geometry;
6. dead particles are removed.

Every render frame:

1. the renderer walks active instances;
2. `ClientVfxInstance.particles()` combines emitter particle lists;
3. every particle is dispatched by render type;
4. the particle renderer interpolates transform and appearance;
5. vertices or models are submitted;
6. the shared buffer source ends its batch.

## Known allocation hotspot

`ClientVfxInstance.particles()` currently creates a new list and copies all emitter particle references every time it is called.

The renderer calls this method every frame.

Recommended first fix:

- expose emitter particle lists for iteration;
- or add a particle visitor;
- or let each instance render/iterate emitters without producing a combined list.

Do not redesign all particle storage before measuring the effect of this simpler change.

## Expression contexts

Runtime contexts are built frequently.

Potential costs include:

- builder allocation;
- map allocation;
- parent context chains;
- repeated vector registration;
- repeated particle speed calculation;
- repeated resolution of string keys.

Recommended profiling questions:

- how much allocation occurs per tick;
- how much time is spent building contexts;
- how much time is spent looking up variables;
- how many expressions run for each particle per tick;
- whether constant expressions are evaluated unnecessarily.

Possible future optimizations:

- reusable mutable contexts;
- indexed variables instead of string maps in hot paths;
- expression dependency analysis;
- constant and stage classification;
- curve lookup tables for heavily sampled curves.

These changes should follow profiling rather than precede it.

## Sprite rendering

The current renderer uses a shared `MultiBufferSource` and ends the batch once after all particles.

This is already better than flushing once per particle.

Potential remaining costs:

- calling `getBuffer` for every particle;
- one `PoseStack` push/pop per particle;
- creating or normalizing vectors;
- creating quaternions for velocity facing;
- repeated texture and blend-mode lookup;
- environment light lookup per particle;
- no frustum culling;
- no distance culling;
- no grouping by render key before traversal.

Recommended profiling sequence:

1. remove combined particle list allocation;
2. measure render time;
3. measure allocations;
4. cache render definition references in runtime particles if needed;
5. pre-group particles by sprite render key if draw-state lookup is significant;
6. replace per-particle `PoseStack` work only if it remains a bottleneck;
7. consider direct camera-basis vertex generation;
8. consider instancing only after ordinary batching is insufficient.

## Model rendering

Model particles are significantly more expensive than simple sprites.

Treat these sources separately:

```text
custom
block
item
entity
```

Recommended future budgets:

- high budget for sprites;
- lower budget for simple custom models;
- lower budget for block and item models;
- very low or experimental budget for entity models.

Recommended safeguards:

- cache baked model references;
- avoid resource lookup per frame;
- cull by distance;
- limit translucent models;
- limit entity models;
- group by model and render layer where possible.

## Collision

Collision is optional and should remain optional.

Potential costs:

- block range calculation;
- collision shape queries;
- shape intersection;
- axis resolution;
- repeated contact on following ticks;
- collision-triggered event spawning.

Recommended future changes:

- global collision particle budget;
- per-effect collision budget;
- `once_per_particle`;
- trigger cooldown;
- minimum impact speed;
- disable collision after rest;
- cache block collision shapes for nearby particles;
- test swept collision only for fast particles;
- skip collision at long distance.

## Particle budgets

The current runtime has an emitter-level `max_particles` for steady spawning, but no global limit.

A future budget system should distinguish:

```text
sprite particles
model particles
collision particles
active effects
particles spawned per tick
```

Suggested priorities:

```text
critical
gameplay
normal
decorative
ambient
```

When over budget, reduce or reject lower-priority work first.

## Culling and LOD

Not currently implemented.

Potential levels:

### Effect culling

Skip starting effects outside a configured distance.

### Emitter culling

Stop or reduce spawning when the emitter is far away.

### Particle culling

Skip rendering particles outside the frustum or beyond a maximum distance.

### Update LOD

Update distant decorative effects less frequently.

### Spawn LOD

Reduce rate or burst amount by distance.

## Benchmark plan

If work resumes, add repeatable definitions:

```text
benchmark_sprite_static_1000
benchmark_sprite_animated_1000
benchmark_sprite_expression_1000
benchmark_sprite_lit_1000
benchmark_model_custom_100
benchmark_model_item_100
benchmark_collision_200
benchmark_many_emitters
```

Record:

```text
hardware
resolution
graphics settings
baseline FPS
average FPS
1% low FPS
client tick time
render time
active particles
spawned particles per second
collision particles
memory allocation
draw batches
```

## Reasonable first target

A suitable initial target would be:

- 1000 simple sprite particles at stable frame pacing on the selected development machine;
- no large per-frame allocation from particle list aggregation;
- bounded spawning;
- predictable degradation through culling and budgets;
- no claim that all particle types have equal cost.

## Editor integration

A future in-game editor should display:

```text
active effects
active emitters
active particles
sprite particles
model particles
collision particles
spawn rate
tick time
render time
culled particles
budget usage
```

Performance feedback should be a core editor feature, not a separate late-stage tool.
