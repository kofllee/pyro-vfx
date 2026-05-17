package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.type.VfxEmitterLifetimeMode;

public record VfxEmitterLifetimeDefinition (VfxEmitterLifetimeMode mode, VfxNumberExpression delayTicks, VfxNumberExpression activeTicks, VfxNumberExpression sleepTicks, VfxNumberExpression loops) {
    public static VfxEmitterLifetimeDefinition once(VfxNumberExpression delayTicks, VfxNumberExpression activeTicks) {
        return new VfxEmitterLifetimeDefinition(
                VfxEmitterLifetimeMode.ONCE,
                delayTicks,
                activeTicks,
                VfxNumberExpression.constant(0.0),
                VfxNumberExpression.constant(1.0)
                );
    }

    public static VfxEmitterLifetimeDefinition none() {
        return new VfxEmitterLifetimeDefinition(
                VfxEmitterLifetimeMode.ONCE,
                VfxNumberExpression.constant(0.0),
                VfxNumberExpression.constant(0.0),
                VfxNumberExpression.constant(0.0),
                VfxNumberExpression.constant(0.0)
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
                VfxNumberExpression.constant(0.0),
                VfxNumberExpression.constant(0.0),
                VfxNumberExpression.constant(0.0),
                VfxNumberExpression.constant(0.0)
        );
    }

}
