# Development checks, museum and reproducible measurements

Pyro VFX has separate `src/main` (release engine) and `src/development` (GameTests, museum, dev commands, examples and optional benchmarks) source sets. These load under one mod ID during development; no second mod is created. GameTests do not establish FPS or shader compatibility; client benchmarks use an actual OpenGL context. None are required to inspect or edit documentation.

## Default checks

```powershell
.\gradlew.bat build developmentJar verifyReleaseJar
.\gradlew.bat runGameTestServer --no-daemon
python tools/test_compare_bench.py
```

CI checks the release JAR for development-content leaks and runs the development GameTests. Run them after Java changes; for documentation and retained benchmark selection, link validation and `git diff --check` are sufficient. The latest executed verification (5 November 2026) passed **96/96 GameTests** with `verifyReleaseJar`; earlier same-day verification passed three consecutive 91/91 suites. New tests cover GPU analytic birth-expression/RNG parity, independent parameter lifetimes, shared parametric offsets and rotating sprites, as well as safe same-tick cohort merge eligibility and Dense synchronous collision-event routing. An earlier run intermittently failed because delayed tests compared the shared level's total VFX lease count while other tests were active; those delayed checks now verify their own UUID leases, without weakening the immediate population checks. Newly added server tests check analytic world-plane facing, GPU-eligible LOOPING/MANUAL emission schedules and discard of saturated steady spawn debt. These test policy/simulation; they do not render into a GPU framebuffer.

The expanded [5 November client evidence](benchmarks/runtime-expansion-2026-11-05/README.md) includes actual GPU LOOPING/MANUAL cohorts (13/9 uploads), GPU world-lit Dense sprite instancing vs same-hash RenderType (0.0278% changed pixels), and snapshot-free Dense model rendering. `python tools/verify_m5.py` validates a coherent historical campaign rather than 731 mixed reports; use `--after YYYYMMDD-HHMMSS` to require fresh evidence (exit 2 means incomplete). Run `python tools/test_verify_m5.py` for campaign-selection unit tests. The additional [runtime extensions evidence](benchmarks/runtime-extensions-2026-11-05/README.md) contains real-client Legacy-vs-Dense collision event ordering, self-emission, GPU-vs-Dense shared-age parametric motion/rotation and two-upload coalesced manual bursts. The full current-code M5 campaign and 35-stand visual acceptance remain pending.

Six additional 5 November real-client checks exercised GPU Analytic sprites, resident models, rotating additive instancing, sorted alpha instancing and a paired forced-RenderType orientation scenario. All completed on a secondary display with GL state audit. The matched GPU/RenderType screenshots differed at 0.0303% of pixels. See [runtime audit evidence](benchmarks/runtime-audit-2026-11-05/README.md); these short runs do not prove shaderpack or injected GL-failure behavior.

## Client and museum (opt-in)

```powershell
# Interactive museum save and effect stands
.\gradlew.bat runClientMuseum

# Real client timing; requires an existing local benchmark world
.\gradlew.bat runClientBench '-PbenchScenario=sprite_10k'

# Optional unobtrusive Windows second-monitor launcher
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario sprite_10k -Width 1280 -Height 720 -Uncapped -RenderStateAudit
```

The launcher requires two monitors. It attempts not to take input focus and restores the prior foreground window, but extremely brief OS focus switches cannot be excluded. Outputs go to `run/pyrovfx-benchmarks/` (gitignored); games and timed workloads are **not** launched during ordinary cleanup. The museum is generated/checked using `tools/generate_museum_effects.py`, `tools/create_museum_world.py` and `tools/export_museum_evidence.py`. Full graphics-profile automation lives in `tools/verify-museum-renderer-profiles.ps1`.

Useful benchmark scenarios include `sprite_10k`, `analytic_age_1k`, `model_100k`, `sprite_alpha_smoke_100k`, `collision_no_events_10k`, `adaptive_steady`, `soak_mixed` and the `museum_*` family. Heavy counts, global stress, fallback and long soaks require explicit benchmark options, not normal client budget increases. See task configuration and available options in `build.gradle` and `tools/run-client-bench-secondary.ps1`.

## Inspecting historical evidence without rerunning it

The intentionally small, retained report set is indexed in [Performance](PERFORMANCE.md). Reports contain `status`, real scenario/settings, backend selection, completed tick/frame sample counts, effect hash, seed, frame percentiles, CPU time, Pyro GPU timing, allocations, heap and screenshots when available. Comparisons must match workload and environment.

```powershell
python tools/verify_universal_short.py
python tools/verify_m5.py --folder docs/benchmarks/m5-2026-11-02 --min-global-runs 3
python tools/verify_adaptive_bench.py docs/benchmarks/m6-2026-11-02/adaptive_steady-20261102-132543.json docs/benchmarks/m6-2026-11-02/adaptive_steady-20261102-132645.json
python tools/verify_soak_bench.py docs/benchmarks/m6-2026-11-02/soak_mixed-20261102-134026.json docs/benchmarks/m6-2026-11-02/soak_mixed-20261102-134356.json
```

These scripts **validate saved evidence**, not the current executable, and do not launch Minecraft. `tools/compare_bench.py` compares compatible source JSON for new A/B recordings. `tools/compare-bench-images.ps1` / `tools/CompareScreenshots.java` are optional paired visual checks. Small standalone kernel experiments are not proof of runtime multithreading safety.

## Networking acceptance (opt-in, not routine)

```powershell
.\gradlew.bat runNetworkTestServer --offline --console=plain --no-configuration-cache
.\gradlew.bat runNetworkTestClientA --offline --console=plain --no-configuration-cache
.\gradlew.bat runNetworkTestClientB --offline --console=plain --no-configuration-cache
```

Start the localhost-only server first, then clients as separate processes. See [Networking](NETWORKING.md) for isolated `127.0.0.1:25575` setup, test-only offline mode and prior 8/8 results from each client. This is intentionally **not** part of a documentation cleanup, standard Gradle build or routine GameTests.
