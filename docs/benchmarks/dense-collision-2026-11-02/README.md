# Collision without particle triggers — Dense CPU A/B, 2026-11-02

Three independent Minecraft 1.21.1 NeoForge client launches before (CPU Legacy) and three after (CPU Dense collision component); RTX 4090 Laptop GPU, 1280×720, uncapped, same benchmark definition SHA-256, camera and seed. All 12 archived JSON reports have status completed.

| Workload | CPU simulation median of 3 runs | CPU VFX render submission median | P95 full-frame median |
|---|---:|---:|---:|
| 10k collision, no particle events — Legacy | 1.826 ms | 2.775 ms | 8.324 ms |
| 10k collision, no particle events — Dense | 1.101 ms | 0.398 ms | 2.011 ms |
| 1k collision with events — Legacy before | 0.303 ms | 0.287 ms | 1.993 ms |
| 1k collision with events — Legacy after | 0.276 ms | 0.253 ms | 1.547 ms |

The 10k scene had exactly **1,019,870** observed contacts in each of six runs. The eventful 1k scene had exactly **102,006** in every run, and remained on CPU Legacy. The new Dense collision path uses the same cached block-shape lookup and ordered axis bounce policy; the no-event restriction preserves synchronous collision-event semantics.

These numbers describe the recorded single-player benchmark setup, not multiplayer throughput or a universal FPS target. Equal contact counts do not mathematically establish coordinate-by-coordinate or pixel-by-pixel parity; matching image captures and long stress/reload testing remain open.

Raw reports include the full GPU timing, heap/GC, frame latency, screenshot path, seed, SHA-256 and hardware provenance. The 10k collision candidate was recorded before its completion commit, so associate the reports with that commit and these notes.
