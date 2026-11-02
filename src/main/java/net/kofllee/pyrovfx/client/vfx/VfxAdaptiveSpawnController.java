package net.kofllee.pyrovfx.client.vfx;

/**
 * Bounded adaptive spawn admission driven exclusively by elapsed Pyro CPU
 * simulation time. It never deletes live particles, throttles event delivery,
 * changes particle physics, or increases user-configured safety ceilings.
 *
 * Deliberately NOT a GPU/VRAM controller: OpenGL work is asynchronous and the
 * client tick alone cannot measure its cost accurately.
 */
public final class VfxAdaptiveSpawnController {
    private static final int OVER_BUDGET_TICKS = 4;
    private static final int UNDER_BUDGET_TICKS = 30;
    private static final double SMOOTHING = 0.2;

    private double cpuEmaMs;
    private double spawnScale = 1.0;
    private int highTicks;
    private int lowTicks;
    private int samples;
    private long constrainedTicks;

    /**
     * @param elapsedNs wall-clock time spent in Pyro's client tick only
     * @param liveParticles particles remaining after that tick
     * @param enabled false for disabled adaptive policy or deterministic benchmarks
     * @param targetMs optional CPU budget of the Pyro tick (not full frame)
     * @param floorPercent minimum percentage of the configured spawn throughput
     */
    public void observe(long elapsedNs, int liveParticles, boolean enabled,
                        double targetMs, int floorPercent) {
        if (!enabled || liveParticles <= 0 || !Double.isFinite(targetMs) || targetMs <= 0) {
            reset();
            return;
        }
        double floor = Math.clamp(floorPercent / 100.0, 0.01, 1.0);
        spawnScale = Math.max(floor, spawnScale);
        // Prevent single debugger/GC stalls from poisoning the moving average.
        double measured = Math.max(0.0, Math.min(targetMs * 8.0, elapsedNs / 1_000_000.0));
        cpuEmaMs = samples == 0 ? measured
                : cpuEmaMs + SMOOTHING * (measured - cpuEmaMs);
        samples++;

        // Both the fresh sample and its trend must indicate overload.
        // A single GC/debugger stall must not cause throttling four ticks later
        // while actual Pyro CPU work has already returned to normal.
        if (measured > targetMs * 1.12 && cpuEmaMs > targetMs * 1.12) {
            highTicks++;
            lowTicks = 0;
            if (highTicks >= OVER_BUDGET_TICKS) {
                // Adapt in small, predictable steps; a costly live population
                // may not become cheaper just by limiting future spawns.
                spawnScale = Math.max(floor, spawnScale * 0.8);
                highTicks = 0;
            }
        } else if (cpuEmaMs < targetMs * 0.65) {
            lowTicks++;
            highTicks = 0;
            if (lowTicks >= UNDER_BUDGET_TICKS) {
                spawnScale = Math.min(1.0, spawnScale + 0.05);
                lowTicks = 0;
            }
        } else {
            highTicks = lowTicks = 0;
        }
        if (spawnScale < 0.999999) constrainedTicks++;
    }

    /** Floor at least one spawn, except for a user-configured zero ceiling. */
    public int effectiveSpawnLimit(int configuredLimit) {
        if (configuredLimit <= 0) return 0;
        return Math.min(configuredLimit,
                Math.max(1, (int) Math.floor(configuredLimit * spawnScale)));
    }

    public void reset() {
        cpuEmaMs = 0;
        spawnScale = 1;
        highTicks = lowTicks = samples = 0;
        constrainedTicks = 0;
    }

    public double cpuEmaMs() { return cpuEmaMs; }
    public double spawnScale() { return spawnScale; }
    public long constrainedTicks() { return constrainedTicks; }
    public int samples() { return samples; }
}