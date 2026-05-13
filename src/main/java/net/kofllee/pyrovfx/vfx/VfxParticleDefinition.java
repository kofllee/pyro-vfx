package net.kofllee.pyrovfx.vfx;

import net.minecraft.core.particles.ParticleOptions;

public record VfxParticleDefinition(VfxParticleRenderType renderType, ParticleOptions particleOptions, double speed, double spread) {
}
