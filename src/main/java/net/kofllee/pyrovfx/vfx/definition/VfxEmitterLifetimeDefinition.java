package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxEvaluationMode;
import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.type.VfxEmitterLifetimeMode;

public record VfxEmitterLifetimeDefinition (VfxEmitterLifetimeMode mode, VfxNumberExpression delayTicks, VfxNumberExpression activeTicks, VfxNumberExpression sleepTicks, VfxNumberExpression loops) {
    public static VfxEmitterLifetimeDefinition once(VfxNumberExpression delayTicks, VfxNumberExpression activeTicks) {
        return new VfxEmitterLifetimeDefinition(
                VfxEmitterLifetimeMode.ONCE,
                delayTicks,
                activeTicks,
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.EMITTER_START),
                VfxNumberExpression.constant(1.0, VfxEvaluationMode.EMITTER_START)
                );
    }

    public static VfxEmitterLifetimeDefinition none() {
        return new VfxEmitterLifetimeDefinition(
                VfxEmitterLifetimeMode.ONCE,
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.EMITTER_START),
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.EMITTER_START),
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.EMITTER_START),
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.EMITTER_START)
        );
    }

    public static VfxEmitterLifetimeDefinition looping(VfxNumberExpression delayTicks, VfxNumberExpression activeTicks, VfxNumberExpression sleepTicks, VfxNumberExpression loops) {
        return new VfxEmitterLifetimeDefinition(
                VfxEmitterLifetimeMode.LOOPING,
                delayTicks,
                activeTicks,
                sleepTicks,
                loops
        );
    }

    public static VfxEmitterLifetimeDefinition manual() {
        return new VfxEmitterLifetimeDefinition(
                VfxEmitterLifetimeMode.MANUAL,
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.EMITTER_START),
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.EMITTER_START),
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.EMITTER_START),
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.EMITTER_START)
        );
    }

}
