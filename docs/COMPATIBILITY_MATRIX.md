# Compatibility evidence

**Scope:** Minecraft 1.21.1, NeoForge 21.1.229, Java 21. Development evidence was recorded on **Windows 11 / NVIDIA RTX 4090 Laptop / OpenGL 4.6**. A completed short scenario establishes only the precise configuration tested, not general modpack stability.

| Configuration | Recorded observation | Boundary |
|---|---|---|
| Vanilla NeoForge client on RTX 4090 Laptop | GPU Analytic, instanced sprites/baked models, alpha smoke, CPU Dense/Legacy and reload fixtures completed | Short real-world scenes; other GPU drivers not evaluated |
| Sodium 0.8.13 | Three museum models/smoke scenarios completed; eligible GPU paths remained active | Not a sustained soak |
| Embeddium 1.0.15 | Same three scenarios completed; GPU paths remained active | Tested separately from Sodium |
| Iris 1.8.12 + Sodium 0.6.13, shaders **disabled** | Same scenarios completed; eligible GPU paths remained active | Does not cover shaderpack activation |
| Iris + Complementary Reimagined r5.9.3 | Three short scenarios completed with Minecraft RenderType fallback | Custom GL compositor/depth integration deliberately disabled |
| Iris + BSL 10.1.8 | Three short scenarios completed with Minecraft fallback | No in-session shaderpack toggle validation |
| Iris + Photon 1.4 | Three short scenarios completed with Minecraft fallback | No long shader session |
| Dedicated NeoForge GameTest server | Server-only class loading, effect leasing and source lifecycle tested | Not render/FPS evidence |
| Two independent localhost Minecraft clients | 8/8 checks per client for remote instance lifecycle, re-entry, dimensions and sockets | No real Internet latency or high-player-count bandwidth test |

**Not verified:** AMD, Intel GPUs, low-feature graphics drivers, Oculus as a separately configured loader, ATM10/Craftoria and other large modpacks, 4K under load, extended thermal/GC/leak soaks, live shader switching, high-latency public multiplayer and large-player network throughput.

## Why fallback matters

CPU simulation remains authoritative for collision/event/stateful features; Minecraft RenderType preserves a compatible render route when Pyro's own OpenGL fast paths cannot safely interact with the active pipeline. An installed but inactive shader loader does not necessarily force fallback. An **active** shaderpack does until its framebuffer, depth and blend behavior is independently tested.

GPU timers use asynchronous queries of Pyro's own work, not total GPU frame time. Optional NVIDIA device-wide free-VRAM probes are off by default; readings are not Pyro-owned memory. A successful local check is not a performance guarantee for the same effect on other hardware.

## Evidence and reproduction

A representative profile ledger remains at [profile-results.json](benchmarks/museum-final-2026-11-03/compatibility/profile-results.json); representative images and effect JSON are retained alongside it. The fuller conclusions and caveats are in [Performance](PERFORMANCE.md).

Run isolated graphics profile checks only when explicitly needed:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/verify-museum-renderer-profiles.ps1
```

The script restores modified Iris settings and uses separate graphics profiles. It is **not** a routine Gradle or documentation check.
