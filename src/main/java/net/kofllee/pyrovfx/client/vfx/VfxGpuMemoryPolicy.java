package net.kofllee.pyrovfx.client.vfx;

/**
 * Conservative admission pressure from optional device-wide VRAM and
 * substantial tracked particle state alongside JVM heap pressure.
 * Unknown/unsupported readings must never be treated as zero free memory.
 */
public final class VfxGpuMemoryPolicy {
    private VfxGpuMemoryPolicy() {}

    public static double spawnScale(int freeMiB, int totalMiB) {
        if (freeMiB < 0 || totalMiB <= 0 || freeMiB > totalMiB) return 1.0;
        if (freeMiB < 128) return 0.25;
        if (freeMiB < 256) return 0.5;
        if (freeMiB < Math.max(512, totalMiB / 20)) return 0.8;
        return 1.0;
    }

    /**
     * Conservative new-spawn admission when both substantial tracked Pyro
     * particle state and JVM heap pressure are observed. This does not claim
     * to attribute all used heap to VFX, nor to measure GPU driver allocations.
     * Missing measurements never throttle particles.
     */
    public static double heapScale(long particleStateBytes, long usedHeapBytes, long maxHeapBytes) {
        if (particleStateBytes < 16L * 1024 * 1024
                || usedHeapBytes < 0 || maxHeapBytes <= 0 || usedHeapBytes > maxHeapBytes) return 1.0;
        double pressure = (double) usedHeapBytes / maxHeapBytes;
        if (pressure >= 0.92) return 0.5;
        if (pressure >= 0.85) return 0.8;
        return 1.0;
    }
}