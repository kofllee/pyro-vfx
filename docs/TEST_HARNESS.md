# Development checks, museum and reproducible measurements

Pyro VFX includes real NeoForge GameTests, an interactive museum and an **opt-in** Minecraft client benchmark harness. GameTests do not establish FPS or shader compatibility; client benchmarks use an actual OpenGL context. None are required to inspect or edit documentation.

## Default checks

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer --no-daemon
python tools/test_compare_bench.py
```

The project also runs GameTests from its CI workflow. Run them after Java changes; for documentation and retained benchmark selection, link validation and `git diff --check` are sufficient. The latest recorded run in the project documents was 88/88 GameTests; do not claim new test results without executing them.

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
