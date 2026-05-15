package net.kofllee.pyrovfx.client.vfx;

public final class VfxTime {
    public static final double TICKS_PER_SECOND = 20.0;
    public static final double SECONDS_PER_TICK = 1.0 / TICKS_PER_SECOND;

    private VfxTime() {}

    public static double blocksPerSecondToBlocksPerTick(double value) {
        return value * SECONDS_PER_TICK;
    }

    public static double blocksPerSecondSquaredToBlocksPerTickSquared(double value) {
        return value * SECONDS_PER_TICK * SECONDS_PER_TICK;
    }
}
