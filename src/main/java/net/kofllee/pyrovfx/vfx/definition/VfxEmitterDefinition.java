package net.kofllee.pyrovfx.vfx.definition;

public record VfxEmitterDefinition(
        VfxEmitterLifetimeDefinition emitterLifetime,
        VfxSpawnAmountDefinition spawnAmount,
        VfxSpawnShapeDefinition spawnShape,
        VfxMotionDefinition motion,
        VfxRenderDefinition render
) {
}
