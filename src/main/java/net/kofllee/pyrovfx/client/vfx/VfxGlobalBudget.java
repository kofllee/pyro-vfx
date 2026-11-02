package net.kofllee.pyrovfx.client.vfx;

/**
 * Client-wide admission control. The manager starts one budget window before
 * ticking any effect, so the sum of independently safe effects stays bounded.
 * Existing effects are updated in insertion order; when overloaded the newer
 * spawn/event requests are rejected, without evicting existing particles.
 */
public final class VfxGlobalBudget {
    private static final VfxParticleCostClass[] COST_CLASSES = VfxParticleCostClass.values();
    public static final int MAX_INSTANCES = 256;
    public static final int MAX_ACTIVE_PARTICLES = 240_000;
    public static final int MAX_SPAWNS_PER_TICK = 100_000;
    public static final int MAX_EVENTS_PER_TICK = 8_192;

    private int remainingActive;
    private int remainingSpawns;
    private int remainingEvents;
    private int activeLimit;
    private int spawnLimit;
    private double adaptiveSpawnScale = 1.0;
    private final int[] remainingByClass = new int[VfxParticleCostClass.values().length];
    private final long[] rejectedByClass = new long[VfxParticleCostClass.values().length];
    private final long[] admittedByClass = new long[VfxParticleCostClass.values().length];
    private long rejectedSpawns;
    private long rejectedEvents;
    private long rejectedInstances;
    private long admittedSpawns;
    private long admittedEvents;
    private int peakObservedParticles;

    public void beginTick(int currentParticles, boolean heavy) {
        beginTick(currentParticles, heavy, VfxBudgetConfig.globalParticles(), VfxBudgetConfig.globalSpawns());
    }

    /** Explicit limits make high-capacity admission testable without changing global config. */
    public void beginTick(int currentParticles, boolean heavy, int requestedActiveLimit, int requestedSpawnLimit) {
        beginTick(currentParticles, heavy, requestedActiveLimit, requestedSpawnLimit, null);
    }

    public void beginTick(int currentParticles, boolean heavy, int requestedActiveLimit,
                          int requestedSpawnLimit, int[] activeByClass) {
        activeLimit = heavy ? Math.max(VfxRuntimeBudget.HEAVY_PARTICLES, requestedActiveLimit)
                : Math.max(0, requestedActiveLimit);
        spawnLimit = heavy ? Math.max(VfxRuntimeBudget.HEAVY_PARTICLES, requestedSpawnLimit)
                : Math.max(0, requestedSpawnLimit);
        remainingActive = Math.max(0, activeLimit - Math.max(0, currentParticles));
        remainingSpawns = spawnLimit;
        remainingEvents = MAX_EVENTS_PER_TICK;
        peakObservedParticles = Math.max(peakObservedParticles, currentParticles);
        for (var kind : COST_CLASSES) {
            // Standalone admission tests use the original global-budget API;
            // no category snapshot means no category-specific policy.
            int categoryMax = activeByClass == null ? activeLimit
                    : heavy ? Math.max(VfxRuntimeBudget.HEAVY_PARTICLES,
                        VfxBudgetConfig.classLimit(kind)) : VfxBudgetConfig.classLimit(kind);
            int active = activeByClass == null ? 0 : activeByClass[kind.ordinal()];
            remainingByClass[kind.ordinal()] = Math.max(0, categoryMax - Math.max(0, active));
        }
    }

    public int reserveSpawns(int requested) {
        return reserveSpawns(requested, VfxParticleCostClass.CPU_BASIC);
    }

    public int reserveSpawns(int requested, VfxParticleCostClass group) {
        return reserveSpawns(requested, group.bit());
    }

    /** Charge all occupied resource dimensions, not one exclusive particle type. */
    public int reserveSpawns(int requested, int costMask) {
        if (requested <= 0) return 0;
        int granted = Math.min(requested, Math.min(remainingSpawns, remainingActive));
        for (var kind : COST_CLASSES) {
            if ((costMask & kind.bit()) != 0) {
                granted = Math.min(granted, remainingByClass[kind.ordinal()]);
            }
        }
        remainingActive -= granted;
        remainingSpawns -= granted;
        admittedSpawns += granted;
        rejectedSpawns += (long) requested - granted;
        for (var kind : COST_CLASSES) {
            if ((costMask & kind.bit()) != 0) {
                int index = kind.ordinal();
                remainingByClass[index] -= granted;
                admittedByClass[index] += granted;
                rejectedByClass[index] += (long) requested - granted;
            }
        }
        return granted;
    }

    public void rejectLocalSpawns(int count) {
        if (count > 0) rejectedSpawns += count;
    }

    public boolean hasEventBudget() {
        return remainingEvents > 0;
    }

    public boolean enterEvent() {
        if (remainingEvents <= 0) {
            rejectedEvents++;
            return false;
        }
        remainingEvents--;
        admittedEvents++;
        return true;
    }

    public void rejectLocalEvent() {
        rejectedEvents++;
    }

    public boolean admitInstance(int activeInstances) {
        // Even an opt-in particle stress run must not disable effect-count safety.
        if (activeInstances < VfxBudgetConfig.activeEffects()) return true;
        rejectedInstances++;
        return false;
    }

    public long rejectedSpawns() { return rejectedSpawns; }
    public long rejectedEvents() { return rejectedEvents; }
    public long rejectedInstances() { return rejectedInstances; }
    public long admittedSpawns() { return admittedSpawns; }
    public long admittedEvents() { return admittedEvents; }
    public int peakObservedParticles() { return peakObservedParticles; }
    public int remainingSpawns() { return remainingSpawns; }
    public int remainingActive() { return remainingActive; }
    public int activeLimit() { return activeLimit; }
    public int remainingFor(VfxParticleCostClass group) { return remainingByClass[group.ordinal()]; }
    public long rejectedFor(VfxParticleCostClass group) { return rejectedByClass[group.ordinal()]; }
    public long admittedFor(VfxParticleCostClass group) { return admittedByClass[group.ordinal()]; }
    public int spawnLimit() { return spawnLimit; }
    public double adaptiveSpawnScale() { return adaptiveSpawnScale; }
    public void setAdaptiveSpawnScale(double scale) {
        adaptiveSpawnScale = Double.isFinite(scale) ? Math.clamp(scale, 0.01, 1.0) : 1.0;
    }

    public void reset() {
        remainingActive = remainingSpawns = remainingEvents = activeLimit = spawnLimit = 0;
        rejectedSpawns = rejectedEvents = rejectedInstances = 0;
        admittedSpawns = admittedEvents = 0;
        peakObservedParticles = 0;
        adaptiveSpawnScale = 1.0;
        java.util.Arrays.fill(remainingByClass, 0);
        java.util.Arrays.fill(admittedByClass, 0L);
        java.util.Arrays.fill(rejectedByClass, 0L);
    }
}