# Pre-M0 legacy renderer baseline — 2026-11-01

These are **real Minecraft 1.21.1 NeoForge 21.1.229 client results**, recorded before M0–M5 architecture changes on an NVIDIA RTX 4090 Laptop GPU, Windows 11, OpenGL 4.6, 854×480 window, from the integrated-server world `Test VFX`.

The five JSON reports cover static sprites (1k, 10k), dynamic sprites (1k), model particles (1k), and a world-collision/event scenario (1k). The collision scenario uses a temporary 9×9 stone platform at Y=139 and is only considered valid if it reports actual contacts **and** firing of the `on_collision` parameter event. It restores previous blocks and player state on normal completion.

`sprite_1k.png` and `collision_1k.png` are captured in-game visuals. Other PNG screenshots remain under the local development run directory, not under Git. See `../../TEST_HARNESS.md` for commands and metric definitions.

**Caveats:** This is a single recording per scenario at the fixed seed. GPU timing is limited to the effect's GPU workload, not full GPU frame duration. No 100k or 1M measurements were attempted on the object-heavy legacy runtime. These numbers document a starting point but are **not yet a statistically repeatable performance acceptance gate**; reproduce identical runs, inspect the visual fixtures, and gather repeated measurements before any M0 implementation.

The Git commit recorded inside each JSON is the parent engine revision (`c44bbbb8b24a0ea4cb0817f934b706ac4d6636d9`), because the harness had not been committed when the baseline was taken. Different test fixture SHA-256 values mean the results are not comparable.
