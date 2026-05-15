package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxEvaluationMode;
import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.type.VfxLifetimeMode;

public record VfxLifetimeDefinition (
        VfxLifetimeMode mode,
        VfxNumberExpression delayTicks,
        VfxNumberExpression activeTicks,
        VfxNumberExpression sleepTicks,
        VfxNumberExpression loops
) {
    public static VfxLifetimeDefinition once(VfxNumberExpression delayTicks, VfxNumberExpression activeTicks) {
        return new VfxLifetimeDefinition(
                VfxLifetimeMode.ONCE,
                delayTicks,
                activeTicks,
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.EFFECT_START),
                VfxNumberExpression.constant(1.0, VfxEvaluationMode.EFFECT_START)
        );
    }

    public static VfxLifetimeDefinition looping(
            VfxNumberExpression delayTicks,
            VfxNumberExpression activeTicks,
            VfxNumberExpression sleepTicks,
            VfxNumberExpression loops
    ) {
        return new VfxLifetimeDefinition(
                VfxLifetimeMode.LOOPING,
                delayTicks,
                activeTicks,
                sleepTicks,
                loops
        );
    }

    public static VfxLifetimeDefinition none(){
        return new VfxLifetimeDefinition(VfxLifetimeMode.ONCE,
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.EFFECT_START),
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.EFFECT_START),
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.EFFECT_START),
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.EFFECT_START));
    }

}
