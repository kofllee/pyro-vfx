package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxColorExpression;
import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.expression.VfxVec3Expression;
import net.kofllee.pyrovfx.vfx.value.VfxColor;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public record VfxParticleAppearanceDefinition (VfxColorExpression color, VfxVec3Expression scale) {
    public static VfxParticleAppearanceDefinition defaultAppearance() {
        return new VfxParticleAppearanceDefinition(
                constantColor(new VfxColor(1.0, 1.0, 1.0, 1.0)),
                VfxVec3Expression.constant(new VfxVec3(1.0, 1.0, 1.0))
        );
    }

    private static VfxColorExpression constantColor(VfxColor vfxColor) {
        return new VfxColorExpression(
                VfxNumberExpression.constant(vfxColor.r()),
                VfxNumberExpression.constant(vfxColor.g()),
                VfxNumberExpression.constant(vfxColor.b()),
                VfxNumberExpression.constant(vfxColor.a())
        );
    }

}
