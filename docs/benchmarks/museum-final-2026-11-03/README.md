# Museum readiness and Minecraft compatibility — 3 November 2026

## Decision

**Ready as a stable *experimental* foundation for beginning a Pyro VFX visual editor**, with explicit renderer limitations. The editor itself has **not** been implemented. Do not advertise shaderpack GPU integration or production-level stability. The engine's current JSON format, routing diagnostics and resource reload flow can be used as the editor's first integration surface.

This report is based on *actual* Minecraft 1.21.1 / NeoForge 21.1.229 client reports captured on Windows 11 with an NVIDIA RTX 4090 Laptop GPU (OpenGL 4.6), **1280×720 uncapped**, not on synthetic Java-only throughput estimates. See [per-exhibit CSV](museum-results.csv), [58 raw reports](vanilla/), [six renderer profiles](compatibility/) and screenshot evidence.

## Museum

- **58 physically buildable exhibits**, 5 columns × 12 rows, at x=512 y=140 z=512. Category-colored stands, 3 buttons per stand (**PLAY, STOP, COMPARE**), in-world expected-observation signs, full description and actual backend in chat; same-seed CPU/GPU replay and separate benchmark commands.
- Sprites and all six facing modes; 3D custom, block, item, entity; solid, cutout, translucent and additive layers; smoke, lighting, UV, age gradients, expressions, four curve types, all eight spawn shapes (with shell variants), dynamic/parametric/static motion and rotation, sphere and box collisions, spawn/expiry callbacks and event sequences, steady GPU cohorts, and all three simulation families.
- Large **demo-only** model dimensions on all XYZ axes, neutral opaque cube tints, explicit item cutout texture, 20%-alpha translucent cube, bounded self-emit children, and camera-correct world-space rendering after cloud compositing. Original benchmark fixtures remain intact.
- Verified **58/58** latest per-exhibit reports with **status=completed** and **peakActiveParticles > 0**. Routed to **24 GPU Analytic, 29 CPU Dense, 5 CPU Legacy** backends. The renderer is an independent decision: CPU Dense can still use GPU instancing.
- Per-scene P95 frame interval **1.03–3.92 ms**, median of the 58 P95 figures **1.63 ms**. Median FPS across scenes ranged **520–1367** on this hardware and test configuration. These are short low-particle scenes, **not** a controlled relative speedup or universal hardware requirement.
- Maximum observed JVM heap within these reports **~1.62 GiB**. Spawn request cost median **~0.116 ms**, maximum **~9.60 ms** (includes early allocations and setup). See individual reports for memory/state, GPU queries, spawn, CPU and P95/P99; do not interpret JVM heap as GPU VRAM consumption.

## Reproducible graphics configurations

The profile tool downloads checksum-verified Modrinth jars and shaderpacks, installs one configuration at a time, restores the user's Iris settings and removes only its own test jars. **18/18** short client scenarios were completed, comprising 3 exhibit choices × 6 isolated configurations. Renderer reports include actual mod id/version, GPU vendor, GL version and active shader compatibility decision.

| Profile | Resident custom model | Alpha smoke | Translucent model | Observed result |
| --- | --- | --- | --- | --- |
| Sodium 0.8.13 | GPU Analytic | GPU depth-sorted instancing | Minecraft RenderType | All 3 completed |
| Embeddium 1.0.15 | GPU Analytic | GPU depth-sorted instancing | Minecraft RenderType | All 3 completed |
| Iris 1.8.12 + Sodium 0.6.13, shader disabled | GPU Analytic | GPU depth-sorted instancing | Minecraft RenderType | All 3 completed |
| Iris + Complementary Reimagined r5.9.3 | Minecraft RenderType | Minecraft RenderType | Minecraft RenderType | All 3 completed; fallback |
| Iris + BSL 10.1.8 | Minecraft RenderType | Minecraft RenderType | Minecraft RenderType | All 3 completed; fallback |
| Iris + Photon 1.4 | Minecraft RenderType | Minecraft RenderType | Minecraft RenderType | All 3 completed; fallback |

When a shaderpack is **active**, the unresolved framebuffer/depth/composite integration excludes **raw custom GL rendering**, not the engine's entire CPU/GPU simulation capability. Installing Sodium/Embeddium/Iris alone never triggers blanket GPU disabling. The shaderpack screenshots show effects rendered, but do not demonstrate pixel-exact all-frame equivalence or fast-path interoperability.

**Not verified:** ATM10, Craftoria, AMD/Intel hardware, Oculus and other loaders, full modpack/other-mod translucency sorting, in-session live shader toggling, full 36,000-tick long soak, multiplayer, 4K and driver variations. We have one workstation; these are recorded test gaps, not compatibility claims.

## Resource reload, duration, budgets

- Real `reload_active` completed with **1 resource reload** and **100 measured ticks**. Real `soak_mixed` completed with **600 measured ticks**, **3 reload cycles**, **2,388 peak active particles**, and an observed P95 frame interval **1.525 ms**, P99 **3.878 ms**. This is a *limited-duration* stability check, not a 30-minute leak proof.
- The separate GPU elapsed-query benchmark path reported 4096 asynchronous samples for the soak, with observed P95 **0.0328 ms** for Pyro rendering in this fixture, excluding the rest of Minecraft's GPU frame. P95/P99 frame intervals and CPU simulation timings have different meanings; do not interchange them.
- The user's existing TOML contains effective **80k global / 20k per-effect / 10k model ceiling**, while the modern defaults are **240k / 120k / 120k**. `tools/museum_budget_profile.py show` reads settings without modifying them. An explicit temporary `modern` application + `restore` was tested and produced an **identical SHA-256** afterward: `C0FDFD9F4AF8B56FE2A650CA10D4B4C14748C69A579BEE7E5B12F57561460A65`. No automatic TOML migration was performed.
- Global budget, per-effect, collision and memory safety mechanisms remain governed by configured caps and adaptive measurements; increasing these is not equivalent to guaranteeing stable performance on weaker GPUs.

## Verification, reproducibility and next editor boundary

1. `python tools/generate_museum_effects.py` regenerates demonstration JSON from the manifest; original gameplay and benchmark definitions are unaffected.
2. `./gradlew build runGameTestServer` validates definition parsing, shape/catalog coverage, callbacks, backend compatibility rules, runtime and budget invariants; **64/64 GameTests passed** on this workstation.
3. `powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -World 'Pyro VFX Museum' -MuseumBuild -Scenario 'museum_spawn_ring,museum_smoke_alpha,museum_model_translucent' -RenderStateAudit` runs selected real client scenarios.
4. `powershell -NoProfile -ExecutionPolicy Bypass -File tools/verify-museum-renderer-profiles.ps1` repeats isolated graphics profiles. Do not leave client-only jars in the dedicated GameTestServer's mod folder.
5. `python tools/export_museum_evidence.py` copies completed Minecraft reports and key screenshots; refuses to present incomplete coverage as success.

The future editor should use the existing JSON compiler/validation and observed renderer eligibility, **not** introduce its own expression language or claim every effect can run GPU-resident. The remaining shaderpack integration and hardware test matrix are explicit engineering work, not reasons to rework the functioning particle core before beginning editor prototyping.
