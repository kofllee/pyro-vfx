package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public record VfxParametricRotationDefinition(
        VfxVec3 rotation
) {
    public static VfxParametricRotationDefinition none() {
        return new VfxParametricRotationDefinition(VfxVec3.ZERO);
    }

    public static VfxParametricRotationDefinition of(VfxVec3 rotation) {
        return new VfxParametricRotationDefinition(rotation);
    }
}