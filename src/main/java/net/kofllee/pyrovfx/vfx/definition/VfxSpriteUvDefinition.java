package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.expression.VfxVec3Expression;
import net.kofllee.pyrovfx.vfx.type.VfxUvMode;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;

import static net.kofllee.pyrovfx.vfx.expression.VfxEvaluationMode.DEFINITION;
import static net.kofllee.pyrovfx.vfx.expression.VfxEvaluationMode.PARTICLE_SPAWN;

public record VfxSpriteUvDefinition(
        VfxUvMode mode,
        VfxVec3Expression textureSize,
        VfxVec3Expression uvStart,
        VfxVec3Expression uvSize,
        VfxVec3Expression uvStep,
        VfxNumberExpression frameCount,
        VfxNumberExpression fps,
        boolean stretchToLifetime,
        boolean loop,
        boolean randomStartFrame
) {
    public static VfxSpriteUvDefinition full() {
        return new VfxSpriteUvDefinition(
                VfxUvMode.FULL,
                VfxVec3Expression.constant(new VfxVec3(1.0, 1.0, 0.0), DEFINITION),
                VfxVec3Expression.constant(VfxVec3.ZERO, PARTICLE_SPAWN),
                VfxVec3Expression.constant(new VfxVec3(1.0, 1.0, 0.0), PARTICLE_SPAWN),
                VfxVec3Expression.constant(VfxVec3.ZERO, PARTICLE_SPAWN),
                VfxNumberExpression.constant(1.0, PARTICLE_SPAWN),
                VfxNumberExpression.constant(0.0, PARTICLE_SPAWN),
                false,
                false,
                false
        );
    }
}