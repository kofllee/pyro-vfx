package net.kofllee.pyrovfx.client.vfx;

/**
 * Client-wide admission control. The manager starts one budget window before
 * ticking any effect, so the sum of independently safe effects stays bounded.
 * Existing effects are updated in insertion order; when overloaded the newer
 * spawn/event requests are rejected, without evicting existing particles.
 */
public final class VfxGlobalBudget {
    public static final int MAX_INSTANCES = 256;
    public static final int MAX_ACTIVE_PARTICLES = 80_000;
    public static final int MAX_SPAWNS_PER_TICK = 30_000;
    public static final int MAX_EVENTS_PER_TICK = 8_192;

    private int remainingActive;
    private int remainingSpawns;
    private int remainingEvents;
    private long rejectedSpawns;
    private long rejectedEvents;
    private long rejectedInstances;
    private long admittedSpawns;
    private long admittedEvents;
    private int peakObservedParticles;

    public void beginTick(int currentParticles, boolean heavy) {
        int activeLimit = heavy ? VfxRuntimeBudget.HEAVY_PARTICLES : MAX_ACTIVE_PARTICLES;
        remainingActive = Math.max(0, activeLimit - Math.max(0, currentParticles));
        remainingSpawns = heavy ? VfxRuntimeBudget.HEAVY_PARTICLES : MAX_SPAWNS_PER_TICK;
        remainingEvents = MAX_EVENTS_PER_TICK;
        peakObservedParticles = Math.max(peakObservedParticles, currentParticles);
    }

    public int reserveSpawns(int requested) {
        if (requested <= 0) return 0;
        int granted = Math.min(requested, Math.min(remainingSpawns, remainingActive));
        remainingActive -= granted;
        remainingSpawns -= granted;
        admittedSpawns += granted;
        rejectedSpawns += (long) requested - granted;
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
        if (activeInstances < MAX_INSTANCES) return true;
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

    public void reset() {
        remainingActive = remainingSpawns = remainingEvents = 0;
        rejectedSpawns = rejectedEvents = rejectedInstances = 0;
        admittedSpawns = admittedEvents = 0;
        peakObservedParticles = 0;
    }
}