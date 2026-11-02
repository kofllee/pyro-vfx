package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.type.VfxQualityPriority;

/**
 * Per-effect measured CPU costs refine, but never raise, global pressure.
 * With a healthy global tick, every emitter retains its normal spawn rate.
 * Priority protection still applies; CRITICAL bypasses quality adaptation,
 * but never bypasses the fixed global/per-effect active and spawn ceilings.
 */
public final class VfxCostAdmissionPolicy {
    private VfxCostAdmissionPolicy() {}

    public static double scale(double globalScale, double effectCpuEmaMs,
                               double cpuTargetMs, int activeEffects,
                               VfxQualityPriority priority) {
        double pressure = Math.clamp(globalScale, 0.01, 1.0);
        if (pressure < 0.99999 && Double.isFinite(effectCpuEmaMs) && effectCpuEmaMs > 0
                && Double.isFinite(cpuTargetMs) && cpuTargetMs > 0) {
            double fairShare = cpuTargetMs / Math.max(1, activeEffects);
            pressure *= Math.clamp(fairShare / effectCpuEmaMs, 0.35, 1.0);
        }
        return priority.scale(pressure);
    }
}