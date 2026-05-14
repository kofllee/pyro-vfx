package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public record VfxDynamicRotationDefinition(
        VfxVec3 startRotation,
        VfxVec3 angularVelocity,
        VfxVec3 angularAcceleration,
        double angularDrag
) {
    public static VfxDynamicRotationDefinition none() {
        return new VfxDynamicRotationDefinition(
                VfxVec3.ZERO,
                VfxVec3.ZERO,
                VfxVec3.ZERO,
                0.0
        );
    }

    public static VfxDynamicRotationDefinition of(
            VfxVec3 startRotation,
            VfxVec3 angularVelocity,
            VfxVec3 angularAcceleration,
            double angularDrag
    ) {
        return new VfxDynamicRotationDefinition(
                startRotation,
                angularVelocity,
                angularAcceleration,
                Math.clamp(angularDrag, 0.0, 1.0)
        );
    }
}
