# GPU Analytic Particle Runtime

Minecraft 1.21.1 / NeoForge 21.1.229 / Java 21. This is a **separate additive sprite backend**, not a replacement for every particle or a GPU compute system.

## Execution and state

An emitter is classified once by `VfxAnalyticPlan.analyse`. Supported emitters store only initial XYZ and velocity XYZ as six floats per particle (24 bytes). These values are generated at spawn with the original shape and seeded RNG sampling. The entire packed dataset is uploaded to one persistent GPU buffer. Its CPU copy is kept for resource reload and fallback. Each subsequent tick advances **one integer age per emitter**, not every particle. At render time the shader evaluates each position using its initial velocity and common analytic motion coefficients. Shared quad geometry is GPU-resident. Batches contain up to 65,536 instances.

The original M2 dynamic integrator uses `v[n] = (v[n-1] + a * dt²) * q`, `p[n] = p[n-1] + v[n]`, with `q = exp(-drag*dt)`, `dt=1/20`. Its closed-form position is `p[n] = p0 + v0*Cv(n) + a*dt²*Ca(n)`, where `Cv=q*(1-q^n)/(1-q)` and `Ca=q*(n-Cv)/(1-q)`. For q=1, `Cv=n` and `Ca=n(n+1)/2`. Minecraft partial-tick interpolation blends coefficients at n-1 and n. Birth data and uniforms use 32-bit floats on GPU; small differences from double-precision CPU physics are possible.

## Capability routing

**gpu_analytic** requires available OpenGL 3.3 and a compatible Minecraft renderer; a full-bright additive sprite with camera, camera-horizontal or world-Z facing; instant once-only spawning; no collision, particle/emitter events or rotation; and compile-time constant life, shape and dynamic speed/acceleration/drag/direction expressions. Static motion is also supported.

M6 additionally accepts `full`, `static` and `animated` sprite UVs with constant sheet parameters and no per-particle random start. One UV rectangle is evaluated once per emitter render using the established CPU frame scheduler and passed to the vertex shader as `uUvRect`. This retains the 24-byte birth-state stride and one-time GPU upload for animated bursts. Random starts, variable sheet parameters and per-particle UV phase remain on CPU paths; arbitrary appearance expressions and alpha blending are still unsupported by this analytic backend.

M6 now also supports additive color and scale expressions referencing only
`particle.age`, `particle.lifetime` and `particle.normalized_age`.
Every particle in one instant burst has the same age, so the engine evaluates
the expression once or twice **per emitter render** (including CPU-compatible
previous/current age interpolation) and sends color/scale as GPU uniforms.
This is not a general GPU expression compiler. Mutable parameters, random,
curves, particle position/velocity and other per-particle state still use
the CPU backends. Birth-state memory and upload frequency remain unchanged.

Pure constant arithmetic is folded by M1 before selection. The initial spawn count can use a parameter such as `param.count`. Expressions depending on time, mutable parameters, per-particle random values, state, or nonconstant curves are deliberately **not** compiled to GPU and remain supported on the **cpu_dense** or **cpu_legacy** fallback. Dynamic analytical motion never performs world collision queries. Alpha blending, world lighting, models and advanced rotations retain their existing backend.

Runtime decisions can be inspected with `/pyrovfx backend`. Benchmark JSON includes the chosen backend, fallback reason, instanced draws/particles and one-time upload counters. `-Dpyrovfx.analytic.forceCpu=true` disables analytic selection; `-PbenchLegacyAnalytic=true` is its Gradle equivalent.

## Render thread and reload

The analytic renderer uses Minecraft's AFTER_PARTICLES render stage. It flushes pending Minecraft vertices before native GL, restores the modified GL state with the M3 safety wrapper, and optionally asserts correct restoration with `-PbenchRenderStateAudit=true`. GPU resources are created/deleted on the render thread. Reload invalidates shared GL resources; a live emitter detects the new generation and uploads its retained initial-state dataset again. Old GPU buffers are queued for render-thread retirement.

Known shader/optimization mods (Iris, Oculus, Sodium, Embeddium) trigger the original CPU/Minecraft RenderType rendering pipeline. This is safe functional routing, **not verified per-version integration**. Unexpected GL failure invokes a slower RenderType fallback from retained birth state rather than silently hiding particles.

## Validation and limits

Use `runClientBench` with `sprite_10k`, `sprite_100k`, `sprite_1m`; 100k and 1M require explicit `-PbenchHeavy=true`. For shared UV animation use `analytic_uv_1k`; for age-dependent appearance use `analytic_age_1k`. Compare with `-PbenchLegacyAnalytic=true` using identical seed and video settings. Archive real client reports through `tools/summarize_m4.py` and verify the observed count and exactly one 24-byte-per-particle GPU upload. Normal gameplay particle caps are configurable from M6 onward; developer-only heavy benchmarks still use the explicit override.

Do not extrapolate the result to transparency-heavy effects, colliding particles, arbitrary curves, shader packs, GPU compute or every driver. GPU simulation and render CPU timing must be distinguished from end-to-end frame P95.
