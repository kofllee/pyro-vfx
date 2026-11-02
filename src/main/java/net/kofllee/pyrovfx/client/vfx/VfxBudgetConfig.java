package net.kofllee.pyrovfx.client.vfx;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * User-configurable, bounded admission limits. These control requests, not
 * guaranteed resident GPU bytes; expensive CPU/world-collision effects still
 * need separate per-backend cost accounting before automatic quality scaling.
 *
 * The defaults preserve the measured M5 safety baseline. Increasing the
 * limits requires an explicit client configuration change.
 */
public final class VfxBudgetConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue GLOBAL_PARTICLES;
    private static final ModConfigSpec.IntValue GLOBAL_SPAWNS;
    private static final ModConfigSpec.IntValue EFFECT_PARTICLES;
    private static final ModConfigSpec.IntValue EFFECT_SPAWNS;
    private static final ModConfigSpec.IntValue ACTIVE_EFFECTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("All values are safety ceilings, not performance targets.",
                        "High counts are practical only for effects and GPUs that can handle them.")
                .push("particle_budgets");
        GLOBAL_PARTICLES = builder.comment("Maximum concurrently live Pyro particles across all effects.")
                .defineInRange("global_particles", VfxGlobalBudget.MAX_ACTIVE_PARTICLES, 1_000, 4_000_000);
        GLOBAL_SPAWNS = builder.comment("Maximum new particles accepted by the client each game tick.")
                .defineInRange("global_spawns_per_tick", VfxGlobalBudget.MAX_SPAWNS_PER_TICK, 100, 1_000_000);
        EFFECT_PARTICLES = builder.comment("Maximum live particles allocated to one VFX instance.")
                .defineInRange("effect_particles", VfxRuntimeBudget.MAX_ACTIVE_PARTICLES, 100, 4_000_000);
        EFFECT_SPAWNS = builder.comment("Maximum new particles accepted for one effect per tick.")
                .defineInRange("effect_spawns_per_tick", VfxRuntimeBudget.MAX_SPAWNS_PER_TICK, 100, 1_000_000);
        ACTIVE_EFFECTS = builder.comment("Maximum concurrently running VFX instances.")
                .defineInRange("active_effects", VfxGlobalBudget.MAX_INSTANCES, 1, 4_096);
        builder.pop();
        SPEC = builder.build();
    }

    private VfxBudgetConfig() {}

    // Client configs are not loaded by a dedicated server running GameTests.
    // The shared budget code must also work before the client config loads.
    private static int value(ModConfigSpec.IntValue setting, int defaultValue) {
        if (!FMLEnvironment.dist.isClient()) return defaultValue;
        try {
            return setting.get();
        } catch (IllegalStateException notLoadedYet) {
            return defaultValue;
        }
    }

    public static int globalParticles() { return value(GLOBAL_PARTICLES, VfxGlobalBudget.MAX_ACTIVE_PARTICLES); }
    public static int globalSpawns() { return value(GLOBAL_SPAWNS, VfxGlobalBudget.MAX_SPAWNS_PER_TICK); }
    public static int effectParticles() { return value(EFFECT_PARTICLES, VfxRuntimeBudget.MAX_ACTIVE_PARTICLES); }
    public static int effectSpawns() { return value(EFFECT_SPAWNS, VfxRuntimeBudget.MAX_SPAWNS_PER_TICK); }
    public static int activeEffects() { return value(ACTIVE_EFFECTS, VfxGlobalBudget.MAX_INSTANCES); }
}