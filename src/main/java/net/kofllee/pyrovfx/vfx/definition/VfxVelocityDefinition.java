package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.value.VfxVec3;
import net.kofllee.pyrovfx.vfx.type.VfxVelocityMode;

public record VfxVelocityDefinition(VfxVelocityMode mode, VfxVec3 direction, double speed, double speedRandom) {
    public static VfxVelocityDefinition none() {
        return new VfxVelocityDefinition(
                VfxVelocityMode.NONE,
                VfxVec3.ZERO,
                0.0,
                0.0
        );
    }

    public static VfxVelocityDefinition constant(VfxVec3 direction, double speed, double speedRandom) {
        return new VfxVelocityDefinition(
                VfxVelocityMode.CONSTANT,
                direction,
                speed,
                speedRandom
        );
    }

    public static VfxVelocityDefinition random(double speed, double speedRandom) {
        return new VfxVelocityDefinition(
                VfxVelocityMode.RANDOM,
                VfxVec3.ZERO,
                speed,
                speedRandom
        );
    }

    public static VfxVelocityDefinition spherical(double speed, double speedRandom) {
        return new VfxVelocityDefinition(
                VfxVelocityMode.SPHERICAL,
                VfxVec3.ZERO,
                speed,
                speedRandom
        );
    }
}