# Translucent world foreground — verified client repro

Actual Minecraft 1.21.1 NeoForge development client, 1280×720, real framebuffer captures. The local source was not committed at the time the screenshots were captured; each JSON's `environment.gitCommit` therefore identifies its **parent commit**, not the complete working tree. Do not treat screenshot percentages alone as evidence of occlusion correctness.

## Three independent assertions

| Test | Real client outcome | Report / screenshot |
|---|---|---|
| Clear sky, red/yellow reference particles | Both targets clearly visible; `vfxWorldRenderStage=after_level` | [JSON](alpha_visibility_clear-20261105-191639.json) · [PNG](alpha_visibility_clear-20261105-191639.png) |
| Foreground Minecraft glass wall | Graphics completed, selected `before_translucent_world` instead of the previous incorrect late pass | [JSON](alpha_world_glass-20261105-191647.json) · [PNG](alpha_world_glass-20261105-191647.png) |
| Foreground 7×7 water wall enclosed with glass | Red/yellow targets visible **through the actual water** with attenuation; ray intersection checked; `before_translucent_world` | [JSON](alpha_visibility_water-20261105-191655.json) · [PNG](alpha_visibility_water-20261105-191655.png) |

The additional [water-only control JSON](alpha_visibility_control-20261105-185008.json) and [screenshot](alpha_visibility_control-20261105-185008.png) contain **zero active particles**. The [original failing water JSON](alpha_visibility_water-20261105-185016.json) and [screenshot](alpha_visibility_water-20261105-185016.png) had two live particles yet showed no red/yellow shapes through water.

The benchmark positions a sealed water volume *between* the actual camera and both particle targets, validates both line-of-sight samples, and restores every edited world block. This fixes the earlier misleading scene where a single water source was off-axis. Client harness now rejects an incorrect early/late stage for the dedicated cases; a green status means the stage assertion and OpenGL state audit both passed, not just that the effect was allocated.

## Scope and limitations

This is a **conditional whole-frame stage choice** based on a collision raycast from the camera to active emitter source positions. When a visible translucent block is in front, Pyro draws before the world translucency pass so water/glass can blend over its sprites; otherwise it keeps the late stage that preserves the intended relation to vanilla clouds/weather. It is *not* per-pixel depth peeling or a global depth order shared with Minecraft, other mods and shaderpacks. In particular, particles on both sides of the same water wall, wide effects whose source center lies outside the foreground, changing camera perspectives and active Iris shaderpacks still need visual acceptance before claiming complete correctness.

GL-state restoration, networking and particle lifecycle have their own separate test reports. No frame-time improvement is claimed from these captures.
