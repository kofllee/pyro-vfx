# Pyro VFX Museum

The museum is a **real Minecraft 1.21.1 / NeoForge** world for testing effects and backend choices before authoring a visual editor. The original 5×12 gallery contains **58 distinct demo effects**; subsequent M7 transform/attachment stands bring the current manifest to **66 effects**. The initial grid begins at **X 512, Y 140, Z 512**. It is not a visual editor or an automated proof of shader compatibility.

## Open and use

1. Optionally create an isolated save with `python tools/create_museum_world.py` (never overwrites an existing save), then launch `.\gradlew.bat runClientMuseum` or open the world manually.
2. In a disposable/operator-controlled world, use `/pyrovfxmuseum build` to place the stands. This places thousands of blocks; **do not run it on a survival base**. Use `/pyrovfxmuseum visit` to travel to the entrance.
3. At each stand, press **PLAY**, **STOP** or **COMPARE**. Comparison restarts the same effect/seed with different eligible backend paths; unsupported behavior must not be silently accelerated.
4. Use `/pyrovfx museum list`, `/pyrovfx museum play <id>`, `/pyrovfx museum info <id>` and `/pyrovfx backend` to inspect the registered effects and routing. Development commands vary by context; the in-world stand and `/pyrovfxmuseum` commands remain part of the harness.

The catalog covers six sprite orientations, custom/block/item/entity models, material and lighting modes, UV and shared-age animation, event chains, curves and expressions, eight spawn shapes, motion and rotation, collision/expiry, CPU/GPU fallbacks and M7 attachment sources. Source assets are in `src/main/resources/assets/pyro_vfx/vfx/museum/`; the manifest is `src/main/resources/pyro_vfx_museum_exhibits.tsv`. `tools/generate_museum_effects.py` regenerates definitions; do not delete these files as historical benchmark noise.

## Reproducible capture

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -World 'Pyro VFX Museum' -MuseumBuild -Scenario 'museum_model_custom,museum_smoke_alpha,museum_gpu_curves' -Width 1280 -Height 720 -Uncapped -RenderStateAudit
```

This is **optional** and uses a real Minecraft window. The benchmark report includes backend route, FPS, frame P95/P99, CPU/GPU timing, budgets and image references; results are stored under gitignored `run/pyrovfx-benchmarks/`. Representative source JSON, [museum aggregate CSV](../benchmarks/museum-final-2026-11-03/museum-results.csv), [graphics profile evidence](../benchmarks/museum-final-2026-11-03/compatibility/profile-results.json) and [paired visual fixes](../PERFORMANCE.md) are retained in Git. Other old captures remain accessible in Git history.

Visual corrections include item cutout transparency, depth interaction between Pyro smoke and Minecraft clouds, neutral model tint, correct world-space camera motion and bounded self-emission. These are measured snapshot fixes, not cross-driver pixel-identity guarantees.

`tools/museum_budget_profile.py` can inspect and explicitly apply/restore developer budget profiles; it preserves user TOML settings. `tools/museum_compat_profile.py` and `tools/verify-museum-renderer-profiles.ps1` perform isolated, reversible short graphics-profile tests. Never install client-only graphics jars on the dedicated GameTest server.

The museum supports editor preview and manual inspection, but editor state, save/reload and property controls belong to the [next phase](../ROADMAP.md). See [Test Harness](../TEST_HARNESS.md) and [Compatibility](../COMPATIBILITY_MATRIX.md) for limitations.
