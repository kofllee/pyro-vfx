package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxEvaluationMode;
import net.kofllee.pyrovfx.vfx.expression.VfxVec3Expression;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public record VfxParametricMotionDefinition(
        VfxVec3Expression offset,
        VfxVec3Expression direction
) {
    public static VfxParametricMotionDefinition none() {
        return new VfxParametricMotionDefinition(
                VfxVec3Expression.constant(VfxVec3.ZERO, VfxEvaluationMode.PARTICLE_TICK),
                VfxVec3Expression.constant(new VfxVec3(0.0, 1.0, 0.0), VfxEvaluationMode.PARTICLE_TICK)
        );
    }

    public static VfxParametricMotionDefinition of(VfxVec3Expression offset, VfxVec3Expression direction) {
        return new VfxParametricMotionDefinition(offset, direction);
    }
}
