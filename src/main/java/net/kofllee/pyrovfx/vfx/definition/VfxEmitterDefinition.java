package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxVec3Expression;

public record VfxEmitterDefinition(
        VfxEmitterLifetimeDefinition emitterLifetime,
        VfxSpawnAmountDefinition spawnAmount,
        VfxVec3Expression offset,
        VfxSpawnShapeDefinition spawnShape,
        VfxParticleLifetimeDefinition particleLifetime,
        VfxMotionDefinition motion,
        VfxRotationDefinition rotation,
        VfxRenderDefinition render
) {
}
