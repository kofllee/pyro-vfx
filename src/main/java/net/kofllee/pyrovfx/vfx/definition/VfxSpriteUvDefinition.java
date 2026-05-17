package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.expression.VfxVec3Expression;
import net.kofllee.pyrovfx.vfx.type.VfxUvMode;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;

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
                VfxVec3Expression.constant(new VfxVec3(1.0, 1.0, 0.0)),
                VfxVec3Expression.constant(VfxVec3.ZERO),
                VfxVec3Expression.constant(new VfxVec3(1.0, 1.0, 0.0)),
                VfxVec3Expression.constant(VfxVec3.ZERO),
                VfxNumberExpression.constant(1.0),
                VfxNumberExpression.constant(0.0),
                false,
                false,
                false
        );
    }
}