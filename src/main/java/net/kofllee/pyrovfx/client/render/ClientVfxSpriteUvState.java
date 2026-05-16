package net.kofllee.pyrovfx.client.render;

import net.kofllee.pyrovfx.vfx.type.VfxUvMode;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public record ClientVfxSpriteUvState(
        VfxUvMode mode,
        VfxVec3 textureSize,
        VfxVec3 uvStart,
        VfxVec3 uvSize,
        VfxVec3 uvStep,
        int frameCount,
        double fps,
        boolean stretchToLifetime,
        boolean loop,
        int randomStartFrame
) {
    public static ClientVfxSpriteUvState full() {
        return new ClientVfxSpriteUvState(
                VfxUvMode.FULL,
                new VfxVec3(1.0, 1.0, 0.0),
                VfxVec3.ZERO,
                new VfxVec3(1.0, 1.0, 0.0),
                VfxVec3.ZERO,
                1,
                0.0,
                false,
                false,
                0
        );
    }
}
