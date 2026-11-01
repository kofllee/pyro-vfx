# Repeatability results ? legacy runtime

Hardware: RTX 4090 Laptop GPU, 854x480, same JSON SHA-256 and fixed seed for all runs.

| Scenario | Runs | CPU simulation median (ms), by run | CPU CV | Frame P95 (ms), by run | Frame P95 CV |
|---|---:|---|---:|---|---:|
| sprite_1k | 3 | 1.077 / 0.991 / 1.165 | 6.6% | 12.00 / 11.22 / 11.53 | 2.8% |
| dynamic_1k | 3 | 1.129 / 1.033 / 1.144 | 4.5% | 11.39 / 11.40 / 11.79 | 1.6% |
| collision_1k | 3 | 1.601 / 1.380 / 1.445 | 6.3% | 11.81 / 12.04 / 11.56 | 1.7% |
| model_1k | 3 | 1.041 / 0.979 / 1.006 | 2.5% | 10.79 / 11.17 / 11.31 | 2.0% |
| sprite_10k | 3 | 10.587 / 9.425 / 10.579 | 5.3% | 23.15 / 22.76 / 22.87 | 0.7% |

CV is population standard deviation / mean, calculated over these three independent client starts. GPU timing only measures VFX render slice, not full frame. No 100k/1M claim. Small changes below run-to-run variation need more repetitions and controlled experiment conditions.
