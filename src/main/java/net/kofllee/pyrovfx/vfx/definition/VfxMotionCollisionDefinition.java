package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxCollisionType;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public record  VfxMotionCollisionDefinition(
        boolean collide,
        VfxCollisionType collisionType,
        VfxVec3 collisionSize,
        double collisionDrag,
        double bounciness,
        boolean expireOnContact,
        VfxEventsDefinition events
) {
    public static VfxMotionCollisionDefinition none() {
        return new VfxMotionCollisionDefinition(
                false,
                VfxCollisionType.SPHERE,
                new VfxVec3(0.05, 0.05, 0.05),
                0.0,
                0.0,
                false,
                VfxEventsDefinition.empty()
        );
    }

    public static VfxMotionCollisionDefinition of(
            boolean collide,
            VfxCollisionType collisionType,
            VfxVec3 collisionSize,
            double collisionDrag,
            double bounciness,
            boolean expireOnContact,
            VfxEventsDefinition events
    ) {
        return new VfxMotionCollisionDefinition(
                collide,
                collisionType,
                clampSize(collisionSize),
                Math.clamp(collisionDrag, 0.0, 1.0),
                Math.clamp(bounciness, 0.0, 1.0),
                expireOnContact,
                events
        );
    }

    private static VfxVec3 clampSize(VfxVec3 size) {
        return new VfxVec3(
                Math.max(0.001, size.x()),
                Math.max(0.001, size.y()),
                Math.max(0.001, size.z())
        );
    }
}
