# Rendering and runtime capability verification — 5 November 2026

## Provenance

Real Minecraft 1.21.1 / NeoForge graphical-client executions, Windows 11, NVIDIA RTX 4090 Laptop GPU, 1280×720, VSync off, GL-state audit enabled. These runs used **locally modified source**; the `environment.gitCommit` recorded in the reports points to the committed parent, not a checksum of the uncommitted feature work. Seed, effect SHA-256 and settings are in each report. Every screenshot was captured from the actual client framebuffer. The numbers below are individual samples, **not statistically significant performance comparisons or proof of universal visual parity**.

## GPU resident birth, animation and lighting

| Same effect/seed, compared to forced CPU Dense | GPU report | CPU report | 1280×720 full-frame difference | Outcome |
|---|---|---|---|---|
| Independent random lifetimes; birth-time shader expiry and live-count histogram | [GPU](analytic_random_life-20261105-165839.json) | [CPU](analytic_random_life-20261105-170015.json) | 0.0014% pixels | Completed; single immutable VBO |
| CPU model-surface sampling with resident GPU motion | [GPU](analytic_model_surface-20261105-165848.json) | [CPU](analytic_model_surface-20261105-170024.json) | 0.0012% pixels | Completed; single immutable VBO |
| Exact common lightmap sample for POINT / STATIC sprites | [GPU](analytic_world_lit-20261105-165831.json) | [CPU](analytic_world_lit-20261105-170007.json) | 0.0359% pixels | Completed; one light probe per cohort draw |
| Per-particle random UV start frame with emitter-bound fps | [GPU](analytic_random_uv-20261105-171115.json) | [CPU](analytic_random_uv-20261105-171148.json) | 0.0005% pixels | Completed; single immutable VBO |

Each of these four comparisons uses matching effect hashes and seeds; individual PNG captures are stored beside their corresponding JSON reports. The GPU lighting route **does not apply** to moving or spatially spread-out particles, which continue to use per-particle CPU Dense lighting. Random-lifetime GPU support requires constant appearance and full UV; no general lifetime-driven color/scale expressions have been admitted.

## Translucency and VELOCITY facing

- [Mixed alpha](alpha_mixed-20261105-165904.json) completed with one CPU Legacy alpha sprite, one Dense alpha sprite and a translucent baked model. They are submitted to one view-depth-sorted Pyro pass; model draws retain Minecraft RenderType and are interleaved with sprite instancing through explicit flush barriers. The result is not equivalent to depth-peeling or globally sorting other Minecraft/mod transparent rendering.
- [GPU velocity](analytic_velocity-20261105-165856.json) completed with 1,000 GPU-resident motion-oriented sprites. CPU Dense has also completed with identical effect hash and seed. A former whole-frame comparison was inconclusive (large Minecraft-world differences); exact visual equivalence at varied rotations is **not claimed**.
- GPU Dense sprite instancing also accepts VELOCITY facing with per-instance velocity attributes. This has a separate structural GameTest; the active speed/orientation client comparison was run on the GPU resident route.

## Active shaderpacks and budget validation

Temporary, fully restored Iris + Sodium profiles ran with Complementary Reimagined, BSL and Photon shaderpacks. Each profile executed GPU-resident analytic motion and the mixed-alpha test with GL-state audit. All six profile-specific reports completed successfully. The captured report's `graphicsMods` proves Iris and Sodium presence; active shaderpack selection is set by the temporary `iris.properties` configuration used by the profile launcher. These runs prove GPU routing and absence of detected GL-state errors **for the tested scenes**, not pixel-perfect shader pipeline composition with all transparent Minecraft surfaces.

- [Graphics mod + shader GPU](analytic_velocity-20261105-170438.json) and [mixed alpha](alpha_mixed-20261105-170446.json) — BSL profile.
- [Graphics mod + shader GPU](analytic_velocity-20261105-170525.json) and [mixed alpha](alpha_mixed-20261105-170534.json) — Photon profile.
- Complementary Reimagined: [GPU orbit](analytic_parametric-20261105-163647.json), [GPU rotation](analytic_sprite_spin-20261105-163656.json), [lit sprite instancing](sprite_lit_fallback-20261105-163704.json) and [mixed museum](museum_gpu_sprite-20261105-163709.json) all completed under the active profile.

The real client budget checks were corrected for configured limits and rechecked: [global simultaneous spawns](global_budget_spike-20261105-170313.json) accepted 30,000 and rejected 30,000 of 60,000 requests, while [two emitters in one effect](budget_spike-20261105-170321.json) accepted 20,000 and rejected 20,000. Historical verifier expectations must not be mixed with customized current settings.

## Open acceptance work

- Depth ordering against Minecraft glass, water, particle engine and shaderpack translucent attachments remains unverified.
- Random UV and individual random lifetime support is intentionally restricted; arbitrary per-particle animation functions and world-sensitive expressions remain CPU.
- Dynamic vertex-tinted baked models and per-particle world lighting for moving GPU-resident clouds are not implemented.
- Compatible same-tick bursts coalesce; different-tick/independent-emitter batch merging still requires cost evidence.
- Clean-commit, statistically repeated before/after performance comparisons and all-platform driver acceptance remain outstanding.
- A complete live-client networking/parser/reload/lifecycle campaign is still separate from the successful dedicated-server GameTests.

This document is an evidence record, not a claim that the engine is fully finalized.
