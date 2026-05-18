package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.expression.VfxContextBuilder;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public final class ClientVfxExpressionContexts {
    private ClientVfxExpressionContexts() {}

    public static VfxExpressionContext effectStart(Vec3 effectPosition, double effectRandom, Map<String, Double> parameters) {
        return VfxContextBuilder.create()
                .numbers("param", parameters)
                .number("effect.age", 0)
                .number("effect.active_age", 0)
                .number("effect.normalized_age", 0)
                .number("effect.random", effectRandom)
                .vec3("effect.pos", effectPosition)
                .build();
    }

    public static VfxExpressionContext effectTick(
            Vec3 effectPosition,
            int effectAge,
            int effectDelayTicks,
            int effectActiveTicks,
            double effectRandom,
            Map<String, Double> parameters
    ) {
        int effectActiveAge = Math.max(0, effectAge - effectDelayTicks);
        double effectNormalizedAge = effectActiveTicks <= 0
                ? 1.0
                : Math.min(1.0, effectActiveAge / (double) effectActiveTicks);

        return VfxContextBuilder.create()
                .numbers("param", parameters)
                .number("effect.age", effectAge)
                .number("effect.active_age", effectActiveAge)
                .number("effect.normalized_age", effectNormalizedAge)
                .number("effect.random", effectRandom)
                .vec3("effect.pos", effectPosition)
                .build();
    }

    public static VfxExpressionContext emitterStart(VfxExpressionContext effectContext, Vec3 emitterPosition, double emitterRandom) {
        return VfxContextBuilder.childOf(effectContext)
                .number("emitter.age", 0)
                .number("emitter.active_age", 0)
                .number("emitter.normalized_age", 0)
                .number("emitter.random", emitterRandom)
                .number("emitter.spawned_particles", 0)
                .vec3("emitter.pos", emitterPosition)
                .build();
    }

    public static VfxExpressionContext emitterTick(
            VfxExpressionContext effectContext,
            Vec3 emitterPosition,
            VfxLifetimeState lifetime,
            int emittedParticles,
            double emitterRandom
    ) {
        return VfxContextBuilder.childOf(effectContext)
                .number("emitter.age", lifetime.age())
                .number("emitter.local_age", lifetime.localAge())
                .number("emitter.active_age", lifetime.activeAge())
                .number("emitter.normalized_age", lifetime.normalizedAge())
                .number("emitter.random", emitterRandom)
                .number("emitter.spawned_particles", emittedParticles)
                .vec3("emitter.pos", emitterPosition)
                .build();
    }

    public static VfxExpressionContext particleSpawn(VfxExpressionContext emitterContext, Vec3 spawnPosition, double particleRandom) {
        return VfxContextBuilder.childOf(emitterContext)
                .number("particle.random", particleRandom)
                .vec3("spawn.pos", spawnPosition)
                .build();
    }

    public static VfxExpressionContext particleTick(
            VfxExpressionContext emitterContext,
            Vec3 spawnPosition,
            Vec3 particlePosition,
            Vec3 previousParticlePosition,
            Vec3 velocity,
            Vec3 rotation,
            Vec3 angularVelocity,
            int particleAge,
            int particleLifetime,
            double particleRandom,
            Vec3 particleScale
    ) {
        double normalizedAge = particleLifetime <= 0
                ? 1.0
                : Math.min(1.0, particleAge / (double) particleLifetime);

        return VfxContextBuilder.childOf(emitterContext)
                .number("particle.age", particleAge)
                .number("particle.lifetime", particleLifetime)
                .number("particle.normalized_age", normalizedAge)
                .number("particle.random", particleRandom)
                .vec3("particle.scale", particleScale)
                .vec3("particle.rotation", rotation)
                .vec3("particle.angular_velocity", angularVelocity)
                .vec3("spawn.pos", spawnPosition)
                .vec3("particle.pos", particlePosition)
                .vec3("particle.prev_pos", previousParticlePosition)
                .vec3("particle.vel", velocity)
                .number("particle.speed", velocity.length())
                .build();
    }

}