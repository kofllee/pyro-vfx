# Pyro VFX Museum — repeatable Minecraft validation world

This is a real Java Edition **1.21.1 NeoForge** testing gallery built from normal world blocks and 35 standalone JSON effects. The physical **5 × 7 exhibition grid** begins at **X 512, Y 140, Z 512**, with palette-coded categories, signs, illuminated platforms, view frames and connecting stone paths. It is a test environment, not a proposed visual editor.

## Open or build

The checked-in generator can reproduce it in any singleplayer or operator-controlled NeoForge world:

1. Run `python tools/create_museum_world.py` to make a separate, small *Pyro VFX Museum* save from the existing `Test VFX` seed, if present. Never overwrites an existing save.
2. Launch Minecraft 1.21.1 with Pyro VFX, open **Pyro VFX Museum** and execute `/pyrovfxmuseum build` (operator/cheats required). The museum is created at a fixed coordinate in the current dimension; **it places thousands of blocks**, so do not run on an important survival base.
3. `/pyrovfxmuseum visit` teleports to the entrance. The world spawn is set to the museum entrance when built.
4. Press the **PLAY**, **STOP**, or **COMPARE** stone button on any stand. These are ordinary synced Minecraft buttons. Playback and backend comparison happen on the local client, so they work in a dedicated-server world as long as Pyro VFX is installed client-side.
5. Signs give category, effect, count and controls. Full names, descriptions and backend reasoning are available via chat commands.

### Commands

- `/pyrovfx museum list` — full catalog
- `/pyrovfx museum play <id> [count]` — play at its stand with repeatable seed, stop prior Pyro effects
- `/pyrovfx museum stop` — stop Pyro effects only
- `/pyrovfx museum compare <id>` — alternate AUTO GPU and forced CPU (same seed), restarting the same effect
- `/pyrovfx museum mode auto|cpu|legacy` — select available GPU path, Dense only, or CPU Legacy for the next play
- `/pyrovfx museum info <id>`, `/pyrovfx museum status`, `/pyrovfx backend` — selected simulation/backend/renderer/reasons and measured CPU/GPU state
- `/pyrovfx museum budgets` — **actual loaded** TOML caps, class caps, adaptive throttling
- `/pyrovfx bench museum_<id>` — game-dev-only measured automatic scenario with JSON report. `-World 'Pyro VFX Museum'` selects the dedicated save.

A physical COMPARE button toggles the full GPU path versus CPU simulation + Minecraft renderer; no changes to global animation/scale semantics. Choosing a backend never claims that unsupported entity/world interactions can run on GPU.

## Exhibit groups

Sprites, 3D Models (custom, block, item, entity), Transparency (alpha smoke, deep smoke, translucent models), Lighting, Rotation, Animations (UV, age), Expressions (random, position, velocity), Curves (Catmull/Bezier), Motion, Collisions (with/without callbacks), Events, and Backends.

All demo textures/models are existing repo assets. Each effect lives in `assets/pyro_vfx/vfx/museum/<id>.json`; all are generated from an explicit manifest (`src/main/resources/pyro_vfx_museum_exhibits.tsv`) by `python tools/generate_museum_effects.py`. Unlike benchmark fixtures, these have large **demo-only** scale multipliers and reduced spatial spread. Smoke demo billboards retain readable faces; CPU/GPU comparisons use identical effect definitions and seed.

## Measurements and profiles

Bench output is written to `run/pyrovfx-benchmarks/` with active particles, FPS, P95/P99 frame interval, CPU tick, GPU VFX time, tracked particle bytes, heap and spawn request time. GPU timing is async **Pyro rendering only**, not whole-frame GPU. Screenshots and report hash are recorded for visual comparisons.

```powershell
python tools/museum_budget_profile.py show
# Explicit temporary test only; backup preserved until restore:
python tools/museum_budget_profile.py apply --profile modern
python tools/museum_budget_profile.py restore
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -World 'Pyro VFX Museum' -MuseumBuild -Scenario 'museum_model_custom,museum_smoke_alpha,museum_gpu_curves' -Width 1280 -Height 720 -Uncapped -RenderStateAudit
```

`modern` uses the new safety ceilings (240k global, 120k per effect, 100k spawn/tick, 120k model). `legacy` reproduces the locally retained 80k global, 20k per-effect, 10k model settings. **No automatic migration or overwrite occurs**. Existing TOML comments and unknown keys are preserved; only explicit profile application writes settings, followed by byte-perfect restore. Heavy counts require existing benchmark opt-ins.

For real client graphics-mod tests, use `python tools/museum_compat_profile.py setup --profile sodium|embeddium|iris [--shader complementary|bsl|photon]`, launch one profile, and **always** `python tools/museum_compat_profile.py restore` afterward. The script refuses to mix existing user jars, verifies downloaded Modrinth release hashes and restores Iris settings. Do not attempt to run client-only Sodium/Iris jars in dedicated GameTestServer.

## Validation gates before a visual editor

The gallery tests feature semantics and profiler integration but does not certify all hardware/shaderpacks or modpacks. Record status, loaded mod versions, actual active shaderpack (not merely installed), GL state audit, screenshots, reload and soak results for each exact environment. Full compatibility status: [museum verification](../COMPATIBILITY_MATRIX.md). No editor-specific UI or data format is introduced in this phase.
