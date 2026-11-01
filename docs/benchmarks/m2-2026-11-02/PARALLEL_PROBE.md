# Isolated CPU parallelization probe

**Purpose:** Investigate worker-thread break-even **without** granting threads access to Minecraft world state, live parameters, shared RNG or the renderer.

Command: `java tools/ParallelKernelProbe.java` on local Windows Java 21.0.2, 32 available processors, ForkJoin common pool parallelism 31. One math-only experiment (JVM warmed in-process, 7 timed trials, 24 update steps per trial). Median elapsed per step:

| Elements | Sequential | JDK parallel stream | Speedup (sequential / parallel) |
|---|---:|---:|---:|
| 1,000 | 0.00165 ms | 0.00401 ms | 0.41× |
| 10,000 | 0.00264 ms | 0.01706 ms | 0.15× |
| 100,000 | 0.02543 ms | 0.07599 ms | 0.33× |
| 1,000,000 | 1.16173 ms | 0.20204 ms | 5.75× |

The calculation is strictly independent `velocity[i] = (velocity[i] - acceleration) * drag; position[i] += velocity[i]`. Results **do not** measure Minecraft CPU time, allocations/GC, render scheduling, the cost of copying inputs, context construction, dynamic expressions or collision events. Single sample per workload family is insufficient to establish a universal hardware threshold; use only as an initial architectural spike.

**Decision:** Do not parallelize existing client simulation at typical 1k–10k workloads; measured overhead dominates. Parallel 1M may help with a pure dense math workload, but this is reserved for a separate future experiment with immutable input, deterministic per-particle RNG, thread-safe effect snapshots, a bounded worker pool, render-thread publication and GPU/CPU synchronization accounting. Minecraft block/entity queries and GL calls must never move into worker threads.
