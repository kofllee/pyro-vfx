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
    private static final ModConfigSpec.IntValue GPU_ANALYTIC_LIMIT;
    private static final ModConfigSpec.IntValue CPU_BASIC_LIMIT;
    private static final ModConfigSpec.IntValue CPU_COLLISION_LIMIT;
    private static final ModConfigSpec.IntValue CPU_MODEL_LIMIT;
    private static final ModConfigSpec.BooleanValue ADAPTIVE_SPAWNS;
    private static final ModConfigSpec.DoubleValue CPU_TICK_BUDGET_MS;
    private static final ModConfigSpec.IntValue MIN_ADAPTIVE_SPAWN_PERCENT;
    private static final ModConfigSpec.BooleanValue GPU_TIMING_ENABLED;
    private static final ModConfigSpec.DoubleValue GPU_RENDER_BUDGET_MS;
    private static final ModConfigSpec.BooleanValue VRAM_PROBE;
    private static final ModConfigSpec.BooleanValue ENABLE_EDITOR;

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
        GPU_ANALYTIC_LIMIT = builder.comment("Separate live-particle ceiling for GPU analytic bursts. Global cap still applies.")
                .defineInRange("gpu_analytic_particles", 240_000, 100, 4_000_000);
        CPU_BASIC_LIMIT = builder.comment("Separate live ceiling for regular CPU sprite particles.")
                .defineInRange("cpu_basic_particles", 120_000, 100, 4_000_000);
        CPU_COLLISION_LIMIT = builder.comment("Live collision particles are expensive; protect main-thread block queries.")
                .defineInRange("cpu_collision_particles", 20_000, 100, 1_000_000);
        CPU_MODEL_LIMIT = builder.comment("Adaptive admission safety ceiling for dense-instanced and complex models.",
                        "Real CPU/GPU cost throttles new spawns before the ceiling; this is not a 10k model limit.")
                .defineInRange("cpu_model_particles", 120_000, 100, 4_000_000);
        builder.pop();
        builder.comment("Optional dynamic throttling of NEW particle spawns when Pyro CPU simulation is expensive.",
                        "Never removes existing particles or throttles redstone/event triggers.")
                .push("adaptive_spawns");
        ADAPTIVE_SPAWNS = builder.comment("Automatically reduce the spawn rate if Pyro CPU simulation exceeds its target.")
                .define("enabled", true);
        CPU_TICK_BUDGET_MS = builder.comment("Target milliseconds of Pyro VFX CPU simulation per game tick (not total frame time).")
                .defineInRange("cpu_tick_target_ms", 3.0, 0.25, 20.0);
        MIN_ADAPTIVE_SPAWN_PERCENT = builder.comment("Smallest allowed fraction of configured spawn capacity under sustained overload.")
                .defineInRange("minimum_spawn_percent", 25, 5, 100);
        GPU_TIMING_ENABLED = builder.comment("Use asynchronous Pyro VFX GPU render time, if OpenGL timer queries work. No GPU readback stalls.")
                .define("gpu_timing_enabled", true);
        GPU_RENDER_BUDGET_MS = builder.comment("Pyro VFX GPU render target per frame; NOT total GPU frame time.")
                .defineInRange("gpu_render_target_ms", 2.0, 0.1, 20.0);
        VRAM_PROBE = builder.comment("Opt in to sampling NVIDIA device-wide free VRAM every 120 frames (not Pyro-owned memory).",
                        "Unsupported GPUs return unknown. This extra GL query may have driver overhead.")
                .define("nvidia_memory_probe", false);
        builder.pop();
        builder.comment("Optional interactive VFX editor; disabling never changes effect playback.").push("editor");
        ENABLE_EDITOR = builder.comment("Enable /pyrovfx editor and the integrated UI showcase.")
                .define("enable_editor", true);
        builder.pop();
        SPEC = builder.build();
    }

    private VfxBudgetConfig() {}

    public static boolean editorEnabled() {
        if (!FMLEnvironment.dist.isClient()) return false;
        try { return ENABLE_EDITOR.get(); }
        catch (IllegalStateException notLoadedYet) { return true; }
    }

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
    public static int classLimit(VfxParticleCostClass group) {
        return switch (group) {
            case GPU_ANALYTIC -> value(GPU_ANALYTIC_LIMIT, 240_000);
            case CPU_BASIC -> value(CPU_BASIC_LIMIT, 120_000);
            case CPU_COLLISION -> value(CPU_COLLISION_LIMIT, 20_000);
            case CPU_MODEL -> value(CPU_MODEL_LIMIT, 120_000);
        };
    }
    public static boolean adaptiveSpawns() {
        if (!FMLEnvironment.dist.isClient()) return false;
        try { return ADAPTIVE_SPAWNS.get(); }
        catch (IllegalStateException notLoadedYet) { return true; }
    }
    public static double cpuTickTargetMs() {
        if (!FMLEnvironment.dist.isClient()) return 3.0;
        try { return CPU_TICK_BUDGET_MS.get(); }
        catch (IllegalStateException notLoadedYet) { return 3.0; }
    }
    public static int minimumAdaptiveSpawnPercent() {
        return value(MIN_ADAPTIVE_SPAWN_PERCENT, 25);
    }
    public static boolean gpuTimingEnabled() {
        if (!FMLEnvironment.dist.isClient()) return false;
        try { return GPU_TIMING_ENABLED.get(); }
        catch (IllegalStateException notLoadedYet) { return true; }
    }
    public static double gpuRenderTargetMs() {
        if (!FMLEnvironment.dist.isClient()) return 2.0;
        try { return GPU_RENDER_BUDGET_MS.get(); }
        catch (IllegalStateException notLoadedYet) { return 2.0; }
    }
    public static boolean nvidiaMemoryProbe() {
        if (!FMLEnvironment.dist.isClient()) return false;
        try { return VRAM_PROBE.get(); }
        catch (IllegalStateException notLoadedYet) { return false; }
    }
}