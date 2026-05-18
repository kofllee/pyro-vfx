package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.type.VfxEmitterLifetimeMode;
import net.kofllee.pyrovfx.vfx.type.VfxLifetimeMode;

public final class VfxLifetimeRuntime {
    private VfxLifetimeRuntime () {}

    public static VfxLifetimeState effect(VfxLifetimeMode mode, int age, int delayTicks, int activeTicks, int sleepTicks, int loops) {
        if(age < delayTicks) {
            return new VfxLifetimeState(age, 0, 0, 0, false, false);
        }

        int afterDelayAge = age - delayTicks;
        if(mode == VfxLifetimeMode.ONCE) {
            int activeAge = Math.min(activeTicks, afterDelayAge);
            double normalizedAge = normalizedAge(activeAge, activeTicks);
            boolean active = afterDelayAge < activeTicks;
            boolean finished = afterDelayAge >= activeTicks;

            return new VfxLifetimeState(age, afterDelayAge, activeAge, normalizedAge, active, finished);
        }

        if(mode == VfxLifetimeMode.LOOPING) {
            int cycleTicks = activeTicks + sleepTicks;

            if (cycleTicks <= 0) {
                return new VfxLifetimeState(age, 0, 0, 1.0, false, true);
            }

            int completedCycles = afterDelayAge / cycleTicks;

            if (loops > 0 && completedCycles >= loops) {
                return new VfxLifetimeState(age, cycleTicks, activeTicks, 1.0, false, true);
            }

            int localAge = afterDelayAge % cycleTicks;
            int activeAge = Math.min(localAge, activeTicks);
            boolean active = localAge < activeTicks;

            return new VfxLifetimeState(age, localAge, activeAge, normalizedAge(activeAge, activeTicks), active, false);
        }

        return new VfxLifetimeState(age, 0, 0, 0.0, false, false);
    }

    public static VfxLifetimeState emitter(
            VfxEmitterLifetimeMode mode,
            int age,
            int delayTicks,
            int activeTicks,
            int sleepTicks,
            int loops
    ) {
        if (mode == VfxEmitterLifetimeMode.MANUAL) {
            return new VfxLifetimeState(age, 0, 0, 0.0, false, false);
        }

        if (mode == VfxEmitterLifetimeMode.ONCE) {
            return effect(VfxLifetimeMode.ONCE, age, delayTicks, activeTicks, sleepTicks, loops);
        }

        if (mode == VfxEmitterLifetimeMode.LOOPING) {
            return effect(VfxLifetimeMode.LOOPING, age, delayTicks, activeTicks, sleepTicks, loops);
        }

        return new VfxLifetimeState(age, 0, 0, 0.0, false, false);
    }

    private static double normalizedAge(int activeAge, int activeTicks) {
        if(activeTicks <= 0){
            return 1.0;
        }

        return Math.min(1.0, activeAge / (double) activeTicks);
    }
}
