package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;

public record VfxParticleLifetimeDefinition(VfxNumberExpression maxAgeTicks) {
    public static VfxParticleLifetimeDefinition of(VfxNumberExpression maxAgeTicks) {
        return new VfxParticleLifetimeDefinition(maxAgeTicks);
    }

    public static VfxParticleLifetimeDefinition defaultLifetime() {
        return new VfxParticleLifetimeDefinition(VfxNumberExpression.constant(20.0));
    }
}
