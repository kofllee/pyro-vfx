# Changelog

All notable changes to Pyro VFX are recorded here.

## 0.1.0-prototype

This version preserves the current integrated runtime as a documented prototype.

### 6 November rendering consolidation

- Unified material pass selection: opaque/cutout early and Pyro alpha/additive later with shared depth ordering among supported sprites.
- Promoted analytic alpha hybrid submission and sorted translucent baked-model GPU drawing from the visual-only experiment into normal capability-based routing.
- Added native Fabulous particle-target depth replay for eligible sprites and baked models when a particles depth target exists; this addresses vanilla sky-cloud ordering but is not general OIT.
- Preserved additive model RGB blending while limiting layer alpha accumulation in Fabulous. The final cloud/compositor result remains an approximation, not guaranteed exact additive across shaderpacks.
- Added a native entity-material shader/vertex route for baked solid and cutout models. Iris/no-shaderpack visual parity requires renewed confirmation.
- Kept CPU Dense/Legacy and Minecraft RenderType fallback for unsupported events, collisions, animated entity models, GL failures and model semantics.
- Consolidated the development-only render gallery: 12 compact sample groups across glass, water, underwater, opaque/tinted materials and the actual cloud height, with no A/B toggles.
- Updated routing GameTests for the unified materials; **104/104** required server tests passed.

### Resource system

- Added JSON-defined VFX resources.
- Added registry loading through client resource reload.
- Added format validation for `pyro_vfx:1`.
- Added reference validation for events, emitters, triggers and parameters.

### Runtime

- Added effect instances.
- Added emitter instances.
- Added custom particle instances.
- Added effect and emitter lifetime handling.
- Added looping and delayed lifetimes.
- Added runtime parameter values.
- Added command parameter overrides.
- Added mutable parameters through events.

### Expressions and curves

- Added compiled numeric expressions.
- Added arithmetic, comparison, boolean and conditional operators.
- Added math helper functions.
- Added random helper functions.
- Added effect, emitter, particle and spawn contexts.
- Added named curves.
- Added linear, step, Catmull and smooth Bezier-like curve modes.

### Events and triggers

- Added `emit` events.
- Added `sequence` events.
- Added `randomize` events.
- Added `set_param` events.
- Added creation, timeline and expiration triggers.
- Added particle collision triggers.

### Emitters

- Added instant spawning.
- Added steady spawning.
- Added manual spawning.
- Added point spawn shape.
- Added sphere spawn shape.
- Added box spawn shape.
- Added line spawn shape.
- Added disc spawn shape.
- Added ring spawn shape.
- Added cone spawn shape.
- Added model spawn shape sampling.

### Motion and collision

- Added static motion.
- Added dynamic motion.
- Added parametric motion.
- Added directional initial velocity.
- Added acceleration and linear drag.
- Added block collision.
- Added sphere-like and box collision bounds.
- Added axis-based collision resolution.
- Added collision drag.
- Added bounciness.
- Added expiration on contact.
- Added collision event positions.

### Rotation

- Added dynamic rotation.
- Added angular velocity.
- Added angular acceleration.
- Added angular drag.
- Added parametric rotation.

### Rendering

- Added Minecraft particle fallback rendering.
- Added custom sprite rendering.
- Added alpha and additive sprite blending.
- Added fullbright and environment-lit particles.
- Added multiple sprite facing modes.
- Added static and animated UV regions.
- Added custom model particle rendering.
- Added block model particle rendering.
- Added item model particle rendering.
- Added entity model particle rendering.
- Added model render layers.

### Development commands

- Added `/pyrovfx list`.
- Added `/pyrovfx play`.
- Added `/pyrovfx play_at`.
- Added `/pyrovfx stop_all`.
- Added effect id suggestions.
- Added numeric command parameter overrides.

### Documentation

- Added project overview.
- Added current feature status.
- Added JSON format reference.
- Added expressions and curves reference.
- Added architecture documentation.
- Added performance notes.
- Added ready-to-use example JSON files for sprites, curves, event sequences, collision, UV animation, model particles and model spawn sampling.
- Added possible future roadmap.

### Known limitations

- The project is a paused prototype.
- No in-game VFX editor.
- No public integration API.
- No effect handles.
- No attachments.
- No networking.
- No global particle budgets.
- No culling or LOD.
- No performance guarantees.
- `travel_distance` and `travel_distance_looping` are parsed but not executed.
- Entity model particles and model spawn sampling are experimental.
- Collision can produce repeated events during repeated contact.
