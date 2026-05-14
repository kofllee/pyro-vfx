package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxDirectionMode;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public record VfxDynamicMotionDefinition(
        VfxDirectionMode direction,
        VfxVec3 customDirection,
        double speed,
        VfxVec3 acceleration,
        double gravity,
        double linearDrag
) {
    public static VfxDynamicMotionDefinition none() {
        return new VfxDynamicMotionDefinition(
                VfxDirectionMode.CUSTOM,
                new VfxVec3(0.0, 0.0, 0.0),
                0.0,
                new VfxVec3(0.0, 0.0, 0.0),
                0.0,
                0.0
        );
    }

    public static VfxDynamicMotionDefinition of(
            VfxDirectionMode direction,
            VfxVec3 customDirection,
            double speed,
            VfxVec3 acceleration,
            double gravity,
            double linearDrag
    ) {
        return new VfxDynamicMotionDefinition(
                direction,
                customDirection,
                Math.max(0.0, speed),
                acceleration,
                Math.max(0.0, gravity),
                Math.clamp(linearDrag, 0.0, 1.0)
        );
    }
}
