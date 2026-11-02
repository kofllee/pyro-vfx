package net.kofllee.pyrovfx.client.vfx;

/**
 * Conservative admission pressure from OPTIONAL, device-wide free VRAM.
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
}