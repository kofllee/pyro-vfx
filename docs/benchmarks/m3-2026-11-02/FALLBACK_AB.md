# Forced Minecraft RenderType fallback (A/B)

One real-client run of the same frozen VFX definitions with `-PbenchLegacyRender=true`. Shader/optimization mod selection follows this same functional fallback, but no third-party shader mod is installed or verified here.

| Scenario | M3 GPU-aware median CPU render (ms) | Forced legacy CPU render (ms) |
|---|---:|---:|
| sprite_1k | 0.108 | 0.279 |
| sprite_10k | 0.448 | 2.350 |
| dynamic_1k | 0.276 | 0.274 |
| collision_1k | 0.395 | 0.399 |
| model_1k | 0.119 | 1.032 |
