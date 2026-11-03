# Renderer and platform compatibility evidence

This is a verification ledger, **not** a marketing compatibility claim.
The current project targets Minecraft 1.21.1, NeoForge 21.1.229 and Java 21.
All entries must come from an actual launch, build or headless test.

| Platform / configuration | Check | Status | Limitations |
|---|---|---|---|
| Windows 11, RTX 4090 Laptop, OpenGL 4.6, vanilla NeoForge dev client | Real 1280x720 additive GPU analytic, CPU dense/legacy, alpha, collisions, async GPU timer | **Verified on this device** | One hardware/driver configuration; screenshots and raw metrics archived |
| Windows 11, same GPU, mixed workloads and three repeated client resource reloads | Integrated world and `soak_mixed` benchmark | **Verified for recorded duration only** | Does not establish 30-minute leak-free operation unless the full 36,000-tick soak is separately recorded |
| Windows 11, same GPU, independent particle cost-class caps | Repeated 600-tick real client mixed benchmark with category counters | **Verified on this device** | GPU 600, CPU basic 7560, collision 9450 admitted; no category saturation in this moderate fixture |
| Windows 11, RTX 4090 Laptop, 1280x720, revised custom/block/item 3D models | Short real 10k model benchmarks and developer-opt-in 100k custom model | **Verified on this device** | Raw measured reports under `universal-short`; no full 4K or older GPU validation |
| Windows 11, same device, legacy-vs-instanced model screenshot parity | Paired 1k item and rotating-lit comparisons | **Verified within RMS 0.03 threshold** | Item RMS 0.028090; rotating RMS 0.014616; not exact pixel parity |
| Windows 11, same device, GPU analytic steady cohorts | 7,800 steady live particles, short GPU-vs-CPU A/B | **Verified for this fixture** | Visual RMS 0.000570; unsorted alpha and per-particle mutable expressions still unsupported |
| Windows 11, RTX 4090 Laptop GPU, 1280x720, depth-sorted alpha smoke | 1k/10k/100k CPU RenderType vs GPU instancing, real GL timing and state audit | **Verified for these fixtures** | Fullbright alpha only; strict inter-mod translucent ordering and shaderpack compositing not tested; heavy screenshot parity still needs synchronized-capture comparison |
| Windows 11, same GPU, immutable GPU expressions and curves | 1k/10k/100k CPU/GPU A/B, screenshot comparisons, GPU-specific resource reload | **Verified for these fixtures** | Stateful expressions fall back; low-count GPU can lose; bright screenshots are not rigorous all-frame pixel equivalence; driver differences unknown |
| Windows 11, same GPU, Minecraft EntityRenderDispatcher | 250 and 1k pig entity particles | **Verified CPU Legacy timings only** | No generic GPU instancing candidate; static baked models are a distinct supported GPU path |
| Dedicated NeoForge server without graphics context | `runGameTestServer` suite | **Verified locally** | Not a realistic multi-user TPS benchmark |
| Ubuntu, Java 21, CI server | `./gradlew build`, then `runGameTestServer` | **Configured in CI** | A workflow is not a passing result until GitHub Actions completes successfully |
| Intel integrated GPU, OpenGL 3.3+ | GPU instancing, shader compiler, fallback and reload | **Not verified** | Run real client and archive device/driver/GL info |
| AMD GPU, OpenGL 3.3+ | GPU instancing, query support, fallback and reload | **Not verified** | Run real client and archive device/driver/GL info |
| Sodium 0.8.13 NeoForge 1.21.1 | Museum custom 3D, translucent 3D, alpha smoke; GL state audit | **Verified in three short real-client runs on RTX 4090 Laptop** | GPU analytic/instancing still enabled; no full soak or modpack test |
| Embeddium 1.0.15 NeoForge 1.21.1 | Same 3 museum effects; GL state audit | **Verified in three short real-client runs on RTX 4090 Laptop** | GPU fast paths remain enabled; not installed alongside Sodium |
| Iris 1.8.12 + Sodium 0.6.13 without shaderpack | Same 3 museum effects | **Verified in three short real-client runs on RTX 4090 Laptop** | Iris installed but disabled shaderpack does not force slow renderer |
| Iris 1.8.12 + Complementary Reimagined r5.9.3 | Same 3 effects, active shaderpack, screenshots | **Verified through Minecraft fallback in three short runs** | Framebuffer/compositor integration for Pyro custom GPU path not established; shaderpack reported some unrelated custom uniform warnings |
| Iris 1.8.12 + BSL 10.1.8 | Same 3 effects, active shaderpack, screenshots | **Verified through Minecraft fallback in three short runs** | Not an in-session shaderpack toggle or long soak |
| Iris 1.8.12 + Photon 1.4 | Same 3 effects, active shaderpack, screenshots | **Verified through Minecraft fallback in three short runs** | Not an in-session shaderpack toggle or long soak |
| Oculus / other shaderpack loaders | Actual NeoForge compatible release | **Not verified** | No available separate tested configuration |
| ATM10 / Craftoria large modpacks | Full client, reload and compatibility interaction | **Not verified** | Test packs were not installed; do not infer compatibility from NeoForge alone |
| Multiplayer (dedicated server plus several clients) | No client visual simulation on server, TPS and packet behavior | **Not verified** | Requires an actual multi-client integration environment |
| NVIDIA extension `GL_NVX_gpu_memory_info` | Device VRAM available memory | **Optional / unverified in production** | Client config disabled by default; never infer that device-wide free memory is Pyro-owned |
| Low-feature OpenGL / unsupported GPU timing | CPU fallback and unknown-memory policy | **Logic tested, real low-feature driver unverified** | GPU fast paths use capability checks and fallback |

## Reproduction on Windows without cursor interruption

Run from the repository directory. The launcher refuses to start if no
second monitor is available, puts Minecraft behind other windows, passes
mouse clicks through and restores the previously foreground app if needed.
Window-manager focus switches under the watchdog polling interval cannot
be ruled out.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario analytic_age_1k -RequireGpuAnalytic -RenderStateAudit
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario soak_mixed -SoakTicks 600 -GpuTelemetry -Uncapped -RenderStateAudit
# 36,000 measured ticks is the optional full ~30-minute game-time equivalent.
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 -Scenario soak_mixed -SoakTicks 36000 -GpuTelemetry -Uncapped -RenderStateAudit
```

To call a driver or shader combination *verified*, attach the raw client
report with renderer/GL version, real resolution, render-state audit outcome,
resource reload outcome, sample durations and screenshots. Do not copy the
RTX 4090 results to other GPUs. GL timer queries are asynchronous and only
measure Pyro draw work; they are not a whole-frame GPU profiler.

The November 3 six-profile regression results are reproducible with
`tools/verify-museum-renderer-profiles.ps1`; it installs one graphics profile
at a time with verified Modrinth SHA-512 downloads, preserves and restores the
client's Iris settings, and stores JSON evidence under
`run/pyrovfx-benchmarks/museum-compatibility/`. The three museum effects are
short targeted checks, not general shaderpack-compositor integration proof.
Active shaderpack detection disables only raw Pyro GL paths with unresolved
framebuffer/stage assumptions, not GPU simulation merely because Iris is
installed. The shaderpack fallback retains correctness using Minecraft-managed
RenderType, with a measurable performance cost.