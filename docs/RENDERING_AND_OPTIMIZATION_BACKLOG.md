# Rendering: remaining work

This is the **current** prioritized worklist. For previous measured experiments, see [Performance](PERFORMANCE.md) and archived benchmark evidence; old alternative render pipelines are not active.

| Priority | Area | What remains / acceptance criterion |
|---|---|---|
| P0 | Iris without active shaderpack | Recheck baked solid and item cutout textures with final native entity-shader vertex mapping. Record screenshot/GL audit and compare expected atlas colors. |
| P0 | Additive mesh blending | Visually compare additive overlap and real sky clouds with and without Fabulous/Iris. Current approach approximates exact cross-target additive color because the final composite uses alpha. |
| P0 | Transparent-world intersections | Check glass, water, camera underwater, tinted glass, opaque occlusion, real clouds and mixed particles in the development gallery; document reproducible defects. |
| P0 | Release verification | Run `build developmentJar verifyReleaseJar`, GameTests, archived-evidence checks and repository-integrity guard on a clean checkout; confirm CI on GitHub after push. |
| P1 | New unified routing performance | Matched tests against prior commit and existing fallback: effects/hashes, camera, counts, CPU submission, GPU timer, frame P95/P99, allocations and draw count. Sorting/extra depth replay can be expensive. |
| P1 | Long-session compatibility | Reload, leave/join, resource pack changes and shader switching across Vanilla, Iris/Complementary, Sodium and other documented supported setups. |
| P1 | Additional GPUs/modpacks | AMD/Intel and public modpacks; identify capability-dependent fallback and any shaderpack-specific material mismatch. |
| P2 | Larger transparency architectures | Investigate GPU sorting or OIT **only if** real particle scenes prove the current hybrid approach insufficient. |
| P2 | GPU Compute | Deferred until a representative stateful workload shows total benefit including synchronization and driver constraints. |

**Current strategy:** accept the CPU/GPU hybrid as the main engine renderer with explicit fallback; do not replace functioning subsystems or add new backends for nominal technology coverage. Do not describe the unmeasured final alpha/model routing as a performance win.
