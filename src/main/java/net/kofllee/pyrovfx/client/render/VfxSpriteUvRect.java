package net.kofllee.pyrovfx.client.render;

public record VfxSpriteUvRect(
        float u0,
        float v0,
        float u1,
        float v1
) {
    public static VfxSpriteUvRect full() {
        return new VfxSpriteUvRect(0.0F, 0.0F, 1.0F, 1.0F);
    }
}