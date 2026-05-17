package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.expression.VfxVec3Expression;
import net.kofllee.pyrovfx.vfx.type.VfxDirectionMode;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public record VfxDynamicMotionDefinition(
        VfxDirectionMode direction,
        VfxVec3Expression customDirection,
        VfxNumberExpression speed,
        VfxVec3Expression acceleration,
        VfxNumberExpression linearDrag
) {
    public static VfxDynamicMotionDefinition none() {
        return new VfxDynamicMotionDefinition(
                VfxDirectionMode.CUSTOM,
                VfxVec3Expression.constant(VfxVec3.ZERO),
                VfxNumberExpression.constant(0.0),
                VfxVec3Expression.constant(VfxVec3.ZERO),
                VfxNumberExpression.constant(0.0)
        );
    }

    public static VfxDynamicMotionDefinition of(
            VfxDirectionMode direction,
            VfxVec3Expression customDirection,
            VfxNumberExpression speed,
            VfxVec3Expression acceleration,
            VfxNumberExpression linearDrag
    ) {
        return new VfxDynamicMotionDefinition(
                direction,
                customDirection,
                speed,
                acceleration,
                linearDrag
        );
    }
}
