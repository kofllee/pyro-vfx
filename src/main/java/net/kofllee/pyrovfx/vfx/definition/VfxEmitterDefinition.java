package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxVec3Expression;

import java.util.List;

public record VfxEmitterDefinition(
        String id,
        List<VfxTriggerDefinition> triggers,
        List<VfxTriggerDefinition> particleTriggers,
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
