# Runtime extension evidence — 5 November 2026

These are recorded **real graphical-client** reports and their exact screenshots, not fabricated or headless benchmarks. Windows 11, Minecraft 1.21.1 / NeoForge, NVIDIA RTX 4090 Laptop GPU, Java 21, 1280 × 720, VSync off. Render GL-state audit enabled. **No active shaderpack in these runs.**

**Provenance:** Reports were captured with live, locally modified source. The recorded `environment.gitCommit` is the parent revision and therefore **not** a checksum of the uncommitted implementation used. Fixtures, effect SHA-256, seed and graphics settings are recorded in individual reports. Timing is one run per condition: do not infer a statistically significant speedup. Real shaderpack / AMD / Intel compatibility, long-duration allocation trends and complete museum visual acceptance remain untested.

## Dense synchronous collision events

| Same effect, seed | Legacy CPU | Dense CPU | Parity evidence |
|---|---|---|---|
| `collision_1k`, 1,000 particles | [legacy](collision_1k-20261105-151729.json), median simulation 0.307 ms, P95 frame 1.482 ms | [dense](collision_1k-20261105-152304.json), median simulation 0.280 ms, P95 frame 1.119 ms | **102,006** contacts and the collision event observed in both; [legacy PNG](collision_1k-20261105-151729.png) / [dense PNG](collision_1k-20261105-152304.png), pixel diff below comparison tool's threshold |
| `self_emit_collision` | [legacy](self_emit_collision-20261105-151801.json) | [dense](self_emit_collision-20261105-152336.json) | Both admitted **16 particle births** and **45 event evaluations**, including reentrant `on_collision → emit`; [legacy PNG](self_emit_collision-20261105-151801.png) / [dense PNG](self_emit_collision-20261105-152336.png) |
| `collision_ordered`, 64 particles | [legacy](collision_ordered-20261105-152942.json), median simulation 0.123 ms | [dense](collision_ordered-20261105-153013.json), median simulation 0.172 ms | **6,495** contacts, **6,495** events, `param.hit=6495` on both; [legacy PNG](collision_ordered-20261105-152942.png) / [dense PNG](collision_ordered-20261105-153013.png) |

The larger `collision_1k` scene is faster in its single Dense run, but `collision_ordered` is slower: same-tick script dispatch still costs CPU. The change is **capability and per-particle object reduction**, not a blanket speed-up claim. Callbacks execute immediately after each simulated particle, respecting parameter and random-stream order. New births are not simulated until the next tick. Entity-model rendering and unsupported callbacks remain on Legacy.

## GPU Analytic shared-age motion and rotation

| Same effect, seed | GPU Analytic | Forced CPU Dense | Pixel parity |
|---|---|---|---|
| `analytic_parametric`, 1,000 additive sprites | [GPU](analytic_parametric-20261105-153459.json): 1 birth upload, median simulation **0.063 ms**, P95 **1.287 ms** | [CPU](analytic_parametric-20261105-153534.json): median simulation **0.185 ms**, P95 **1.356 ms** | 189/921,600 pixels (**0.0205%**) different; [GPU PNG](analytic_parametric-20261105-153459.png), [CPU PNG](analytic_parametric-20261105-153534.png) |
| `analytic_sprite_spin`, 1,000 additive sprites | [GPU](analytic_sprite_spin-20261105-154120.json): median simulation **0.060 ms**, P95 **1.239 ms** | [CPU](analytic_sprite_spin-20261105-154152.json): median simulation **0.144 ms**, P95 **1.188 ms** | 67/921,600 pixels (**0.0073%**) different; [GPU PNG](analytic_sprite_spin-20261105-154120.png), [CPU PNG](analytic_sprite_spin-20261105-154152.png) |

GPU parametric motion is restricted to expressions of `particle.age`, `particle.normalized_age`, or shared `particle.lifetime` (including supported functions/curves). The common motion offset is sampled once per frame and passed as a uniform. GPU dynamic sprite rotation supports constant initial angles, angular velocity/acceleration and drag, shared by the cohort. Per-particle random rotations and world-dependent expressions still need CPU evaluation. Both paths used GL-state audit.

## GPU cohorts and physical museum

`analytic_coalesced`: [completed client report](analytic_coalesced-20261105-155716.json), [PNG](analytic_coalesced-20261105-155716.png). A MANUAL emitter received three same-tick `emit` events (3 × 64 particles) and one later timeline event (+64). It rendered **256 GPU resident particles in 2 cohorts / VBO uploads**, rather than allocating four cohorts. Four event evaluations and 256 admitted births were observed. Runtime merging requires same birth age, motion/lifetime plan, source pose and no uploaded VBO. Different-age cohorts are not merged.

The [physical GPU/CPU museum stand run](museum_gpu_sprite-20261105-155001.json) was completed with `-MuseumBuild`, [PNG](museum_gpu_sprite-20261105-155001.png). It exercised the mixed museum with real world blocks and both backend pathways. A prior standalone museum run had an external Windows-focus failure and missing block-provider warnings and is **not** counted as complete acceptance. This single stand does not establish full 35/35 museum coverage.

## Verification

The latest source set contains **96/96 passing GameTests**, `verifyReleaseJar`, plus the Python benchmark verifier tests. Added tests cover ordered-callback routing, GPU shared-age parametric interpolation, constant dynamic sprite rotation and safe cohort merge eligibility. These do not replace real GPU framebuffer comparisons.

Outstanding: active shaderpack compositing, cross-backend translucency, GPU-resident world lighting, parametric expressions that differ per particle, full M5 current-revision campaign, long stress/reload and multi-driver runs.
