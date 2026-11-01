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

    public void beginTick(int activeParticles, boolean heavyBenchmark) {
        remainingEvents = MAX_EVENTS_PER_TICK;
        int limit = heavyBenchmark ? HEAVY_PARTICLES : MAX_ACTIVE_PARTICLES;
        remainingSpawns = heavyBenchmark ? HEAVY_PARTICLES : MAX_SPAWNS_PER_TICK;
        remainingActive = Math.max(0, limit - Math.max(0, activeParticles));
    }

    public boolean hasEventBudget() {
        return remainingEvents > 0;
    }

    public boolean enterEvent() {
        if (remainingEvents <= 0) {
            return false;
        }
        remainingEvents--;
        return true;
    }

    public int reserveSpawns(int requested) {
        if (requested <= 0) {
            return 0;
        }
        int granted = Math.min(requested, Math.min(remainingSpawns, remainingActive));
        remainingSpawns -= granted;
        remainingActive -= granted;
        return granted;
    }
}
