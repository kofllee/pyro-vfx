package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxColorExpression;
import net.kofllee.pyrovfx.vfx.expression.VfxEvaluationMode;
import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.value.VfxColor;

public record VfxParticleAppearanceDefinition (VfxColorExpression color, VfxNumberExpression size) {
    public static VfxParticleAppearanceDefinition defaultAppearance() {
        return new VfxParticleAppearanceDefinition(
                constantColor(new VfxColor(1.0, 1.0, 1.0, 1.0)),
                VfxNumberExpression.constant(1.0, VfxEvaluationMode.PARTICLE_TICK)
        );
    }

    private static VfxColorExpression constantColor(VfxColor vfxColor) {
        return new VfxColorExpression(
                VfxNumberExpression.constant(vfxColor.r(), VfxEvaluationMode.PARTICLE_TICK),
                VfxNumberExpression.constant(vfxColor.g(), VfxEvaluationMode.PARTICLE_TICK),
                VfxNumberExpression.constant(vfxColor.b(), VfxEvaluationMode.PARTICLE_TICK),
                VfxNumberExpression.constant(vfxColor.a(), VfxEvaluationMode.PARTICLE_TICK),
                VfxEvaluationMode.PARTICLE_TICK
        );
    }

}
