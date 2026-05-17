package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxVec3Expression;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public record VfxParametricRotationDefinition(
        VfxVec3Expression rotation
) {
    public static VfxParametricRotationDefinition none() {
        return new VfxParametricRotationDefinition(
                VfxVec3Expression.constant(VfxVec3.ZERO)
        );
    }

    public static VfxParametricRotationDefinition of(VfxVec3Expression rotation) {
        return new VfxParametricRotationDefinition(rotation);
    }

}