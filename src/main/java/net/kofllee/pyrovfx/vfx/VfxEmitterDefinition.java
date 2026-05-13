package net.kofllee.pyrovfx.vfx;

public record VfxEmitterDefinition(VfxEmitterTimingDefinition timing, VfxEmitterRateDefinition rate, VfxEmitterShapeDefinition shape, VfxParticleDefinition particle) {
}
