package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxEmitterTimingType;

public record VfxEmitterTimingDefinition (VfxEmitterTimingType timingType, int delayTicks, int activeTicks, int sleepTicks) {
    public static VfxEmitterTimingDefinition once(int delayTicks, int activeTicks) {
        return new VfxEmitterTimingDefinition(
                VfxEmitterTimingType.ONCE,
                delayTicks,
                activeTicks,
                0
        );
    }

    public static VfxEmitterTimingDefinition loop(int delayTicks, int activeTicks, int sleepTicks) {
        return new VfxEmitterTimingDefinition(
                VfxEmitterTimingType.LOOPING,
                delayTicks,
                activeTicks,
                sleepTicks
        );
    }
}
