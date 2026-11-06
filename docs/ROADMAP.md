# Next phase: visual editor

**Status:** Pyro VFX has a working runtime and public integration API. The next task is the **in-game visual editor and settings UI**, not another runtime overhaul. The integrated renderer is a CPU/GPU hybrid with capability fallback; unresolved compositor and performance questions are recorded in [Rendering work](RENDERING_AND_OPTIMIZATION_BACKLOG.md). Treat [JSON format](JSON_FORMAT.md) and [Public API](PUBLIC_API.md) as existing contracts.

## First editor slice

Open or create a `pyro_vfx:1` resource; browse/search effects by namespace; select emitters; edit lifetime, spawn shape/count, motion, rotation, sprite/model settings and numeric/curve/expression values; preview in the existing museum/Test Harness; display precise parser/validation errors; save JSON and reload. Show dirty and invalid states distinctly.

## Interaction model

Use an effect browser, emitter tree, property inspector, curve editor and event timeline, plus optional in-world spawn-shape/attachment gizmos. Keep settings (quality budgets, backend diagnostics) distinct from effect authoring. Design for readable field labels, explicit units and reversible edits rather than a general node graph from day one.

## Architectural prerequisites

- Introduce an **editor document/draft** separate from immutable runtime definitions; preview must go through the same parser/validator and runtime as shipped files.
- Preserve `pyro_vfx:1` serialization and existing default semantics. Unknown extension metadata should be preserved where possible; warn before destructive rewrites.
- Reuse existing registry, resource reload, Test Harness, museum, public handles, transform providers and diagnostics. Do not build duplicate simulation or rendering.
- Display which backend will execute and why a fallback occurs; surface per-effect resource costs without promising GPU/CPU equality.
- Keep editor-only UI/data out of release gameplay, packet payloads and the common definition layer unless explicitly required.
- Define save/reload, unsaved changes, missing resources, server vs local preview, world switches and invalid JSON behavior before implementation.

## Deferred

New GPU Compute, shader graph, cross-loader port, speculative JSON v2, online marketplace and fully deterministic networked show replay are not editor prerequisites. Only extend the runtime when a concrete authoring workflow exposes a measured missing capability.
