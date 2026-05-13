package net.kofllee.pyrovfx.vfx;

public record VfxEmitterRateDefinition (VfxEmitterRateType type, int count, float particlePerTicks, int maxParticles) {
    public static VfxEmitterRateDefinition instant(int count) {
        return new VfxEmitterRateDefinition(VfxEmitterRateType.INSTANT, count, 0, count);
    }

    public static VfxEmitterRateDefinition steady(float particlePerTicks, int maxParticles) {
        return new VfxEmitterRateDefinition(VfxEmitterRateType.STEADY, 0, particlePerTicks, maxParticles);
    }
}
