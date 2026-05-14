package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxEmitterLifetimeMode;

public record VfxEmitterLifetimeDefinition (VfxEmitterLifetimeMode mode, int delayTicks, int activeTicks, int sleepTicks, int loops) {
    public static VfxEmitterLifetimeDefinition once(int delayTicks, int activeTicks) {
        return new VfxEmitterLifetimeDefinition(
                VfxEmitterLifetimeMode.ONCE,
                delayTicks,
                activeTicks,
                0,
                1
        );
    }

    public static VfxEmitterLifetimeDefinition looping(int delayTicks, int activeTicks, int sleepTicks, int loops) {
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
                0,
                0,
                0,
                0
        );
    }

}
