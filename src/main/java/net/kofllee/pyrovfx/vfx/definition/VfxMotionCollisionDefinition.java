package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxEvaluationMode;
import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.expression.VfxVec3Expression;
import net.kofllee.pyrovfx.vfx.type.VfxCollisionType;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public record  VfxMotionCollisionDefinition(
        boolean collide,
        VfxCollisionType collisionType,
        VfxVec3Expression collisionSize,
        VfxNumberExpression collisionDrag,
        VfxNumberExpression bounciness,
        boolean expireOnContact,
        VfxEventsDefinition events
) {
    public static VfxMotionCollisionDefinition none() {
        return new VfxMotionCollisionDefinition(
                false,
                VfxCollisionType.SPHERE,
                VfxVec3Expression.constant(new VfxVec3(0.05, 0.05, 0.05), VfxEvaluationMode.PARTICLE_SPAWN),
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.PARTICLE_TICK),
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.PARTICLE_TICK),

                false,
                VfxEventsDefinition.empty()
        );
    }

    public static VfxMotionCollisionDefinition of(
            boolean collide,
            VfxCollisionType collisionType,
            VfxVec3Expression collisionSize,
            VfxNumberExpression collisionDrag,
            VfxNumberExpression bounciness,
            boolean expireOnContact,
            VfxEventsDefinition events
    ) {
        return new VfxMotionCollisionDefinition(
                collide,
                collisionType,
                collisionSize,
                collisionDrag,
                bounciness,
                expireOnContact,
                events
        );
    }
}
