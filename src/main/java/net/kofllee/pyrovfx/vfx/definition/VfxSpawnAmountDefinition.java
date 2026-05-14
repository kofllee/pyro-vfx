package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxSpawnAmountMode;

public record VfxSpawnAmountDefinition(VfxSpawnAmountMode mode, int amount, float rate, int maxParticles) {
    public static VfxSpawnAmountDefinition instant(int amount) {
        return new VfxSpawnAmountDefinition(
                VfxSpawnAmountMode.INSTANT,
                amount,
                0.0F,
                amount
        );
    }

    public static VfxSpawnAmountDefinition steady(float rate, int maxParticles) {
        return new VfxSpawnAmountDefinition(
                VfxSpawnAmountMode.STEADY,
                0,
                rate,
                maxParticles
        );
    }

    public static VfxSpawnAmountDefinition manual(int amount) {
        return new VfxSpawnAmountDefinition(
                VfxSpawnAmountMode.MANUAL,
                amount,
                0.0F,
                amount
        );
    }
}
