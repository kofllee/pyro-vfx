package net.kofllee.pyrovfx.vfx.definition;

public record VfxEmitterDefinition(
        VfxEmitterLifetimeDefinition emitterLifetime,
        VfxSpawnAmountDefinition spawnAmount,
        VfxSpawnShapeDefinition spawnShape,
        VfxParticleLifetimeDefinition particleLifetime,
        VfxMotionDefinition motion,
        VfxRenderDefinition render
) {
}
