package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.expression.VfxContextBuilder;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class ClientVfxExpressionContexts {
    private ClientVfxExpressionContexts() {}

    public static VfxExpressionContext effectStart(Vec3 effectPosition, RandomSource random) {
        return VfxContextBuilder.create()
                .number("effect.age", 0)
                .number("effect.active_age", 0)
                .number("effect.normalized_age", 0)
                .number("effect.random", random.nextDouble())
                .vec3("effect.pos", effectPosition)
                .build();
    }

    public static VfxExpressionContext effectTick(
            Vec3 effectPosition,
            int effectAge,
            int effectDelayTicks,
            int effectActiveTicks
    ) {
        int effectActiveAge = Math.max(0, effectAge - effectDelayTicks);
        double effectNormalizedAge = effectActiveTicks <= 0
                ? 1.0
                : Math.min(1.0, effectActiveAge / (double) effectActiveTicks);

        return VfxContextBuilder.create()
                .number("effect.age", effectAge)
                .number("effect.active_age", effectActiveAge)
                .number("effect.normalized_age", effectNormalizedAge)
                .vec3("effect.pos", effectPosition)
                .build();
    }

    public static VfxExpressionContext emitterStart(VfxExpressionContext effectContext, Vec3 emitterPosition, RandomSource random) {
        return VfxContextBuilder.childOf(effectContext)
                .number("emitter.age", 0)
                .number("emitter.active_age", 0)
                .number("emitter.normalized_age", 0)
                .number("emitter.random", random.nextDouble())
                .number("emitter.spawned_particles", 0)
                .vec3("emitter.pos", emitterPosition)
                .build();
    }

    public static VfxExpressionContext emitterTick(
            VfxExpressionContext effectContext,
            Vec3 emitterPosition,
            int emitterAge,
            int emitterDelayTicks,
            int emitterActiveTicks,
            int emittedParticles
    ) {
        int emitterActiveAge = Math.max(0, emitterAge - emitterDelayTicks);
        double emitterNormalizedAge = emitterActiveTicks <= 0
                ? 1.0
                : Math.min(1.0, emitterActiveAge / (double) emitterActiveTicks);

        return VfxContextBuilder.childOf(effectContext)
                .number("emitter.age", emitterAge)
                .number("emitter.active_age", emitterActiveAge)
                .number("emitter.normalized_age", emitterNormalizedAge)
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