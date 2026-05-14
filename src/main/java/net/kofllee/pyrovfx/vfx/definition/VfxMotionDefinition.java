package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public record VfxMotionDefinition (VfxVelocityDefinition velocity, VfxVec3 acceleration, double gravity, double drag, VfxMotionCollisionDefinition collision) {
    public static VfxMotionDefinition none() {
        return new VfxMotionDefinition(
                VfxVelocityDefinition.none(),
                VfxVec3.ZERO,
                0.0,
                0.0,
                VfxMotionCollisionDefinition.none()
        );
    }

    public static VfxMotionDefinition simple(VfxVelocityDefinition velocity) {
        return new VfxMotionDefinition(
                velocity,
                VfxVec3.ZERO,
                0.0,
                0.0,
                VfxMotionCollisionDefinition.none()
        );
    }

    public static VfxMotionDefinition withForces(
            VfxVelocityDefinition velocity,
            VfxVec3 acceleration,
            double gravity,
            double drag,
            VfxMotionCollisionDefinition collision
    ) {
        return new VfxMotionDefinition(
                velocity,
                acceleration,
                gravity,
                drag,
                collision
        );
    }
}
