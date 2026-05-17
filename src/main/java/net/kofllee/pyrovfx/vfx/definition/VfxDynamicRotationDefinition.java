package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.expression.VfxVec3Expression;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public record VfxDynamicRotationDefinition(
        VfxVec3Expression startRotation,
        VfxVec3Expression angularVelocity,
        VfxVec3Expression angularAcceleration,
        VfxNumberExpression angularDrag
) {
    public static VfxDynamicRotationDefinition none() {
        return new VfxDynamicRotationDefinition(
                VfxVec3Expression.constant(VfxVec3.ZERO),
                VfxVec3Expression.constant(VfxVec3.ZERO),
                VfxVec3Expression.constant(VfxVec3.ZERO),
                VfxNumberExpression.constant(0.0)
        );
    }

    public static VfxDynamicRotationDefinition of(
            VfxVec3Expression startRotation,
            VfxVec3Expression angularVelocity,
            VfxVec3Expression angularAcceleration,
            VfxNumberExpression angularDrag
    ) {
        return new VfxDynamicRotationDefinition(
                startRotation,
                angularVelocity,
                angularAcceleration,
                angularDrag
        );
    }
}
