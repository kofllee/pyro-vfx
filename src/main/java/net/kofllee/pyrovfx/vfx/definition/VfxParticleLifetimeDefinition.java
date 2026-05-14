package net.kofllee.pyrovfx.vfx.definition;

public record VfxParticleLifetimeDefinition(int maxAgeTicks) {
    public static VfxParticleLifetimeDefinition of(int maxAgeTicks) {
        return new VfxParticleLifetimeDefinition(Math.max(1, maxAgeTicks));
    }

    public static VfxParticleLifetimeDefinition defaultLifetime() {
        return new VfxParticleLifetimeDefinition(20);
    }
}
