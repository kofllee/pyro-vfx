package net.kofllee.pyrovfx.vfx.type;

/** Relative protection of new particles under adaptive quality reduction. */
public enum VfxQualityPriority {
    AMBIENT, NORMAL, IMPORTANT, CRITICAL;

    public double scale(double globalScale) {
        double fraction = Math.clamp(globalScale, 0.01, 1.0);
        return switch (this) {
            case AMBIENT -> fraction * fraction;
            case NORMAL -> fraction;
            case IMPORTANT -> Math.sqrt(fraction);
            case CRITICAL -> 1.0;
        };
    }
}