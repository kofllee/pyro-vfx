# M5 real-client safety and backend compatibility evidence (2026-11-02)

**12/12 benchmark JSON reports completed**, from three separate Windows Minecraft client launches with the integrated `Test VFX` world. Minecraft 1.21.1, NeoForge 21.1.229, Java 21, RTX 4090 Laptop, OpenGL 4.6, 854×480.

Global overload stress was run **three independent times**:

| Global scenario | Particle backend | Peak live particles | Admitted spawns | Rejected spawns |
|---|---|---:|---:|---:|
| `global_budget_spike-20261102-012321.json` | gpu_analytic | 30,000 | 30,000 | 30,000 |
| `global_budget_spike-20261102-012522.json` | gpu_analytic | 30,000 | 30,000 | 30,000 |
| `global_budget_spike-20261102-012748.json` | cpu_dense, forced fallback | 30,000 | 30,000 | 30,000 |

The median of their full-frame P95 values was **10.448 ms**; this is a compatibility/safety smoke measurement, not a claimed performance gain, 1080p target or universal FPS guarantee.

Other completed scenarios: `analytic_dynamic_10k` (GPU and forced CPU), `collision_1k`, `reload_active`, `budget_spike`, `model_1k`, `sprite_lit_fallback`, `events`, `uv_animation`. The source JSON reports preserve requested/peak particle counts, dynamic backend selection, GPU context, seed, hashes, timing and rejection counters. Screenshots remain in the working client benchmark folder.

Validate the archived evidence independently:

```powershell
python tools/verify_m5.py --folder docs/benchmarks/m5-2026-11-02 --min-global-runs 3
```

A separate final `.\\gradlew.bat clean build runGameTestServer --no-daemon` passed **27/27 GameTests**. Python comparator tests passed **5/5**.

Limitations: this does not cover 30-minute soak, multiplayer TPS, shader-pack matrices, non-NVIDIA GPUs, 1080p/4K alpha overdraw, or the cost of a GPU Compute implementation. See `docs/M5_DECISION_AND_VERIFICATION.md`. All JSON reports were captured with uncommitted M5 changes before the completion commit; their embedded Git SHA refers to the previous M4 baseline. Associate results with the M5 completion commit and this report.
