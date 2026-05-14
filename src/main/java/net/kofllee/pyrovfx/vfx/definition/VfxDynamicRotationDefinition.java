package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxEvaluationMode;
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
                VfxVec3Expression.constant(VfxVec3.ZERO, VfxEvaluationMode.PARTICLE_SPAWN),
                VfxVec3Expression.constant(VfxVec3.ZERO, VfxEvaluationMode.PARTICLE_SPAWN),
                VfxVec3Expression.constant(VfxVec3.ZERO, VfxEvaluationMode.TICK),
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.TICK)
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
