# CPU backend selector A/B verification

One independent real Minecraft client launch with `-PbenchLegacyCpu=true` compared with the archived median of three automatic dense/fallback launches. This is a functional A/B input/identity verification, not a new statistical comparison.

| Scenario | M1 → auto dense median (ms) | Forced legacy (ms) | Observed particle count |
|---|---:|---:|---:|
| sprite_1k | 0.063 | 0.118 | 1000 |
| dynamic_1k | 0.080 | 0.099 | 1000 |
| collision_1k | 0.464 | 0.525 | 1000 |
| model_1k | 0.074 | 0.075 | 1000 |
| sprite_10k | 0.258 | 0.504 | 10000 |
