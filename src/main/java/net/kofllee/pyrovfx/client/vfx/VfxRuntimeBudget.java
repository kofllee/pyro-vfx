package net.kofllee.pyrovfx.client.vfx;

/**
 * Per-effect, per-tick work budget. All emitter and event paths share it.
 * The opt-in heavy benchmark is the only way to lift the particle limits.
 */
public final class VfxRuntimeBudget {
    public static final int MAX_EVENTS_PER_TICK = 4_096;
    public static final int MAX_SPAWNS_PER_TICK = 20_000;
    public static final int MAX_ACTIVE_PARTICLES = 20_000;
    public static final int HEAVY_PARTICLES = 1_000_000;

    private int remainingEvents;
    private int remainingSpawns;
    private int remainingActive;
    private VfxGlobalBudget global;
    private double priorityScale = 1.0;

    public void beginTick(int activeParticles, boolean heavyBenchmark) {
        beginTick(activeParticles, heavyBenchmark, null);
    }

    public void beginTick(int activeParticles, boolean heavyBenchmark, VfxGlobalBudget global) {
        this.global = global;
        remainingEvents = MAX_EVENTS_PER_TICK;
        int limit = VfxBudgetConfig.effectParticles();
        int spawns = VfxBudgetConfig.effectSpawns();
        if (heavyBenchmark) {
            limit = Math.max(limit, HEAVY_PARTICLES);
            spawns = Math.max(spawns, HEAVY_PARTICLES);
        }
        remainingSpawns = spawns;
        remainingActive = Math.max(0, limit - Math.max(0, activeParticles));
    }

    public boolean hasEventBudget() {
        return remainingEvents > 0 && (global == null || global.hasEventBudget());
    }

    public boolean enterEvent() {
        if (remainingEvents <= 0) {
            if (global != null) global.rejectLocalEvent();
            return false;
        }
        if (global != null && !global.enterEvent()) return false;
        remainingEvents--;
        return true;
    }

    public int reserveSpawns(int requested) {
        return reserveSpawns(requested, VfxParticleCostClass.CPU_BASIC);
    }

    public int reserveSpawns(int requested, VfxParticleCostClass group) {
        if (requested <= 0) {
            return 0;
        }
        int priorityAdjusted = Math.min(requested, Math.max(1,
                (int) Math.floor(requested * priorityScale)));
        int granted = Math.min(priorityAdjusted, Math.min(remainingSpawns, remainingActive));
        if (global != null) {
            global.rejectLocalSpawns(requested - granted);
            granted = global.reserveSpawns(granted, group);
        }
        remainingSpawns -= granted;
        remainingActive -= granted;
        return granted;
    }

    /**
     * The global pressure is applied once at the per-effect budget. Global
     * admission retains only the hard cap to avoid throttling twice.
     */
    public void qualityScale(double scale) {
        priorityScale = Double.isFinite(scale) ? Math.clamp(scale, 0.01, 1.0) : 1.0;
    }
}
