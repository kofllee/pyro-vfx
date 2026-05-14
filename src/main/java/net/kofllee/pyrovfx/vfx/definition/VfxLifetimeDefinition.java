package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxLifetimeMode;

public record VfxLifetimeDefinition (
        VfxLifetimeMode mode,
        int delayTicks,
        int activeTicks,
        int sleepTicks,
        int loops
) {
    public static VfxLifetimeDefinition once(int delayTicks, int activeTicks) {
        return new VfxLifetimeDefinition(
                VfxLifetimeMode.ONCE,
                delayTicks,
                activeTicks,
                0,
                1
        );
    }

    public static VfxLifetimeDefinition looping(int delayTicks, int activeTicks, int sleepTicks, int loops) {
        return new VfxLifetimeDefinition(
                VfxLifetimeMode.LOOPING,
                delayTicks,
                activeTicks,
                sleepTicks,
                loops
        );
    }

}
