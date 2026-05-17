package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.type.VfxSpawnAmountMode;

public record VfxSpawnAmountDefinition(
        VfxSpawnAmountMode mode,
        VfxNumberExpression amount,
        VfxNumberExpression rate,
        VfxNumberExpression maxParticles
) {
    public static VfxSpawnAmountDefinition instant(VfxNumberExpression amount) {
        return new VfxSpawnAmountDefinition(
                VfxSpawnAmountMode.INSTANT,
                amount,
                VfxNumberExpression.constant(0.0),
                amount
        );
    }

    public static VfxSpawnAmountDefinition defaultInstant() {
        return instant(VfxNumberExpression.constant(1.0));
    }

    public static VfxSpawnAmountDefinition steady(
            VfxNumberExpression rate,
            VfxNumberExpression maxParticles
    ) {
        return new VfxSpawnAmountDefinition(
                VfxSpawnAmountMode.STEADY,
                VfxNumberExpression.constant(0.0),
                rate,
                maxParticles
        );
    }

    public static VfxSpawnAmountDefinition manual(VfxNumberExpression amount) {
        return new VfxSpawnAmountDefinition(
                VfxSpawnAmountMode.MANUAL,
                amount,
                VfxNumberExpression.constant(0.0),
                amount
        );
    }
}