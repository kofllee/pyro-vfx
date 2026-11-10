package net.kofllee.pyroui;

import net.minecraft.resources.ResourceLocation;

/** One subrectangle of a Minecraft-managed PNG. Coordinates are PNG pixels, not GUI units. */
public record UiTextureRegion(ResourceLocation texture, int textureWidth, int textureHeight,
                              int u, int v, int width, int height) {
    public UiTextureRegion {
        if (texture == null || textureWidth <= 0 || textureHeight <= 0
                || u < 0 || v < 0 || width <= 0 || height <= 0
                || (long) u + width > textureWidth || (long) v + height > textureHeight)
            throw new IllegalArgumentException("Invalid texture region");
    }

    public static UiTextureRegion full(ResourceLocation texture, int width, int height) {
        return new UiTextureRegion(texture, width, height, 0, 0, width, height);
    }

    public float u0() { return u / (float) textureWidth; }
    public float v0() { return v / (float) textureHeight; }
    public float u1() { return (u + width) / (float) textureWidth; }
    public float v1() { return (v + height) / (float) textureHeight; }

    public UiTextureRegion subRegion(int x, int y, int w, int h) {
        if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > width || y + h > height)
            throw new IllegalArgumentException("Subregion outside sprite");
        return new UiTextureRegion(texture, textureWidth, textureHeight, u + x, v + y, w, h);
    }
}