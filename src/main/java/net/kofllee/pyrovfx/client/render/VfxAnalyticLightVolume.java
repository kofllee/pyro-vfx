package net.kofllee.pyrovfx.client.render;

import net.minecraft.world.phys.AABB;

/**
 * Bounded, exact integer-block light probe volume for GPU-resident sprites.
 * The GPU resolves particle world positions in the vertex shader, then
 * samples one prepacked pair of Minecraft lightmap coordinates per particle.
 * If a spread-out cohort would cost more probes than its CPU fallback,
 * the caller must reject the volume instead of sampling approximate lighting.
 */
public final class VfxAnalyticLightVolume {
    public static final int MAX_CELLS = 2_048;

    private VfxAnalyticLightVolume() {}

    public record Region(int x, int y, int z, int width, int height, int depth) {
        public int cells() { return width * height * depth; }

        /** Returns -1 if this exact world block is outside the volume. */
        public int index(double worldX, double worldY, double worldZ) {
            if (!Double.isFinite(worldX + worldY + worldZ)) return -1;
            long px = (long) Math.floor(worldX) - x;
            long py = (long) Math.floor(worldY) - y;
            long pz = (long) Math.floor(worldZ) - z;
            if (px < 0 || px >= width || py < 0 || py >= height || pz < 0 || pz >= depth)
                return -1;
            return (int) ((pz * height + py) * width + px);
        }
    }

    public static Region plan(AABB bounds, int particles) {
        if (bounds == null || particles <= 0
                || !Double.isFinite(bounds.minX + bounds.maxX + bounds.minY + bounds.maxY
                                   + bounds.minZ + bounds.maxZ)) return null;
        // Leave one sample on each side for float32 shader rounding and
        // interpolation close to an integer block boundary.
        long x0 = (long) Math.floor(bounds.minX) - 1;
        long y0 = (long) Math.floor(bounds.minY) - 1;
        long z0 = (long) Math.floor(bounds.minZ) - 1;
        long x1 = (long) Math.floor(bounds.maxX) + 1;
        long y1 = (long) Math.floor(bounds.maxY) + 1;
        long z1 = (long) Math.floor(bounds.maxZ) + 1;
        long width = x1 - x0 + 1;
        long height = y1 - y0 + 1;
        long depth = z1 - z0 + 1;
        long budget = Math.min(MAX_CELLS, Math.max(64L, particles * 2L));
        if (x0 < Integer.MIN_VALUE || x1 > Integer.MAX_VALUE
                || y0 < Integer.MIN_VALUE || y1 > Integer.MAX_VALUE
                || z0 < Integer.MIN_VALUE || z1 > Integer.MAX_VALUE
                || width < 1 || height < 1 || depth < 1
                || width > budget || height > budget || depth > budget
                || width * height * depth > budget) return null;
        return new Region((int)x0, (int)y0, (int)z0,
                (int)width, (int)height, (int)depth);
    }
}
