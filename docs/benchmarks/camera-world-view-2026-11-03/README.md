# AFTER_LEVEL world-space camera regression — Minecraft A/B, 2026-11-03

## Root cause and correction

The smoke/cloud compositing change moved Pyro VFX from `AFTER_PARTICLES` to `RenderLevelStageEvent.Stage.AFTER_LEVEL` (commit `b7d7957`). At that late stage, Minecraft has already unwound the world camera's view transform from `RenderSystem.getModelViewStack()`. Pyro's CPU RenderType and custom instancing shaders still supplied **camera-relative positions** (`worldPosition - cameraPosition`), but the missing **inverse camera rotation** made those positions behave as if they were glued to the camera/screen, especially when turning the head. The effects were not actually changing their world coordinates.

The late-stage renderer now **temporarily** installs a model-view rotation derived from `camera.rotation().conjugate()` before rendering any CPU/GPU particles. It does not add a second camera translation. A `finally` block restores Minecraft's original model-view stack and calls `RenderSystem.applyModelViewMatrix()`. The stage remains `AFTER_LEVEL` to avoid reintroducing the previous cloud-over-smoke compositing error.

A common, pure-math `VfxCameraRelativeView.rotation` is used so the camera-space transform is covered by server-side GameTest without depending on client-only OpenGL classes.

## Real Minecraft controls

Minecraft 1.21.1 / NeoForge, NVIDIA RTX 4090 Laptop GPU, 1280×720. Identical museum worlds, geometry, seed and player position. Exactly two camera angles are tested: **front (180°)** versus **90° side view**. Every report has `status=completed` and OpenGL render state audit enabled.

| Fixture | Rendering path | Face forward | Turn 90° | Result |
| --- | --- | --- | --- | --- |
| `museum/model_custom` | GPU analytic model, resident birth VBO | [front](museum_camera_front-20261102-235851-screen.png) | [turned](museum_camera_turn_90-20261102-235856-screen.png) | Model leaves view with world geometry |
| `museum/model_custom` | CPU Dense + Minecraft model RenderType | [front](museum_camera_front-20261103-000010-screen.png) | [turned](museum_camera_turn_90-20261103-000016-screen.png) | Same world alignment |
| `museum/smoke_alpha` | CPU Dense + GPU depth-sorted alpha instancing | [front](museum_smoke_camera_front-20261103-000202-screen.png) | [turned](museum_smoke_camera_turn_90-20261103-000207-screen.png) | Smoke leaves view; background clouds remain world anchored |

This directly checks the reported symptom on real client frames, not merely static JSON or a numerical backend routing test. Snapshot/camera positions are fixed by the Test Harness; attached raw JSON captures FPS, CPU/GPU time, P95/P99, effect hash and actual renderer. These two angles do not by themselves prove every shaderpack's view transformation, every camera bob effect, or third-person rendering; those require further compatibility runs.

## Regression prevention

- Added `cameraRelativeParticlesMustFollowCameraRotation` GameTest to reject a view transform that leaves a world offset unchanged after a 90° camera turn.
- Added two-angle model and smoke scenarios to the existing Minecraft client benchmark harness, supporting force-legacy-model and render comparison.
- The entire model-view restoration is exception-safe and applies to GPU analytic, GPU instancing, and Minecraft fallback paths.
- No particle definitions, coordinates, physics or JSON scale semantics were altered.
