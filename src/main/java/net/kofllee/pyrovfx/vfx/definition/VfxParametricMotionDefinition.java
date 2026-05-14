package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public record VfxParametricMotionDefinition(
        VfxVec3 offset,
        VfxVec3 direction
) {
    public static VfxParametricMotionDefinition none() {
        return new VfxParametricMotionDefinition(
                VfxVec3.ZERO,
                new VfxVec3(0.0, 1.0, 0.0)
        );
    }

    public static VfxParametricMotionDefinition of(VfxVec3 offset, VfxVec3 direction) {
        return new VfxParametricMotionDefinition(offset, direction);
    }
}
