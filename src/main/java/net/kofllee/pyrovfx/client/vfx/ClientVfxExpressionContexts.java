package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.curve.VfxCurveSet;
import net.kofllee.pyrovfx.vfx.expression.VfxContextBuilder;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.expression.VfxVariableLayout;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/** Standard VFX variables use fixed primitive slots in compiled programs. */
public final class ClientVfxExpressionContexts {
    private ClientVfxExpressionContexts() {}

    public static VfxExpressionContext effectStart(Vec3 position, double effectRandom,
                                                  Map<String, Double> parameters, VfxCurveSet curves,
                                                  RandomSource random) {
        return effectStart(position, effectRandom, parameters, curves, random, null);
    }

    public static VfxExpressionContext effectStart(Vec3 position, double effectRandom,
                                                  Map<String, Double> parameters, VfxCurveSet curves,
                                                  RandomSource random, VfxVariableLayout layout) {
        return effectContext(position, 0, 0, 0, 0, effectRandom, parameters, curves, random, layout);
    }

    public static VfxExpressionContext effectTick(Vec3 position, VfxLifetimeState lifetime,
                                                 double effectRandom, Map<String, Double> parameters,
                                                 VfxCurveSet curves, RandomSource random) {
        return effectTick(position, lifetime, effectRandom, parameters, curves, random, null);
    }

    public static VfxExpressionContext effectTick(Vec3 position, VfxLifetimeState lifetime,
                                                 double effectRandom, Map<String, Double> parameters,
                                                 VfxCurveSet curves, RandomSource random,
                                                 VfxVariableLayout layout) {
        return effectContext(position, lifetime.age(), lifetime.localAge(), lifetime.activeAge(),
                lifetime.normalizedAge(), effectRandom, parameters, curves, random, layout);
    }

    /** Compiled per-instance parameter values; no per-tick Map traversal or String concatenation. */
    public static VfxExpressionContext effectTick(Vec3 position, VfxLifetimeState lifetime,
                                                 double effectRandom, int[] parameterSlots,
                                                 double[] parameterValues, VfxCurveSet curves,
                                                 RandomSource random, VfxVariableLayout layout) {
        VfxContextBuilder builder = VfxContextBuilder.create(layout).curves(curves).random(random);
        for (int i = 0; i < parameterSlots.length; i++) {
            builder.slot(parameterSlots[i], null, parameterValues[i]);
        }
        return builder
                .slot(VfxVariableLayout.EFFECT_AGE, "effect.age", lifetime.age())
                .slot(VfxVariableLayout.EFFECT_LOCAL_AGE, "effect.local_age", lifetime.localAge())
                .slot(VfxVariableLayout.EFFECT_ACTIVE_AGE, "effect.active_age", lifetime.activeAge())
                .slot(VfxVariableLayout.EFFECT_NORMALIZED_AGE, "effect.normalized_age", lifetime.normalizedAge())
                .slot(VfxVariableLayout.EFFECT_RANDOM, "effect.random", effectRandom)
                .vec3Slots(VfxVariableLayout.EFFECT_POS, "effect.pos", position)
                .build();
    }

    private static VfxExpressionContext effectContext(Vec3 pos, double age, double localAge,
                                                       double activeAge, double normalizedAge,
                                                       double effectRandom, Map<String, Double> parameters,
                                                       VfxCurveSet curves, RandomSource random,
                                                       VfxVariableLayout layout) {
        VfxContextBuilder builder = layout == null ? VfxContextBuilder.create()
                : VfxContextBuilder.create(layout);
        builder.curves(curves).random(random);
        if (layout != null) {
            for (var entry : parameters.entrySet()) {
                int slot = layout.slot("param." + entry.getKey());
                if (slot >= 0) {
                    builder.slot(slot, null, entry.getValue());
                } else {
                    builder.number("param." + entry.getKey(), entry.getValue());
                }
            }
        } else {
            builder.numbers("param", parameters);
        }
        return builder
                .slot(VfxVariableLayout.EFFECT_AGE, "effect.age", age)
                .slot(VfxVariableLayout.EFFECT_LOCAL_AGE, "effect.local_age", localAge)
                .slot(VfxVariableLayout.EFFECT_ACTIVE_AGE, "effect.active_age", activeAge)
                .slot(VfxVariableLayout.EFFECT_NORMALIZED_AGE, "effect.normalized_age", normalizedAge)
                .slot(VfxVariableLayout.EFFECT_RANDOM, "effect.random", effectRandom)
                .vec3Slots(VfxVariableLayout.EFFECT_POS, "effect.pos", pos)
                .build();
    }

    public static VfxExpressionContext emitterStart(VfxExpressionContext effectContext,
                                                    Vec3 position, double emitterRandom) {
        return emitterContext(effectContext, position, 0, 0, 0, 0, 0, emitterRandom);
    }

    public static VfxExpressionContext emitterTick(VfxExpressionContext effectContext,
                                                   Vec3 position, VfxLifetimeState lifetime,
                                                   int emittedParticles, double emitterRandom) {
        return emitterContext(effectContext, position, lifetime.age(), lifetime.localAge(),
                lifetime.activeAge(), lifetime.normalizedAge(), emittedParticles, emitterRandom);
    }

    private static VfxExpressionContext emitterContext(VfxExpressionContext effectContext,
                                                      Vec3 pos, double age, double localAge,
                                                      double activeAge, double normalizedAge,
                                                      double spawned, double emitterRandom) {
        return VfxContextBuilder.childOf(effectContext)
                .slot(VfxVariableLayout.EMITTER_AGE, "emitter.age", age)
                .slot(VfxVariableLayout.EMITTER_LOCAL_AGE, "emitter.local_age", localAge)
                .slot(VfxVariableLayout.EMITTER_ACTIVE_AGE, "emitter.active_age", activeAge)
                .slot(VfxVariableLayout.EMITTER_NORMALIZED_AGE, "emitter.normalized_age", normalizedAge)
                .slot(VfxVariableLayout.EMITTER_RANDOM, "emitter.random", emitterRandom)
                .slot(VfxVariableLayout.EMITTER_SPAWNED, "emitter.spawned_particles", spawned)
                .vec3Slots(VfxVariableLayout.EMITTER_POS, "emitter.pos", pos)
                .build();
    }

    public static VfxExpressionContext particleSpawn(VfxExpressionContext emitterContext,
                                                     Vec3 position, double particleRandom) {
        return VfxContextBuilder.childOf(emitterContext)
                .slot(VfxVariableLayout.PARTICLE_RANDOM, "particle.random", particleRandom)
                .vec3Slots(VfxVariableLayout.SPAWN_POS, "spawn.pos", position)
                .build();
    }

    public static VfxExpressionContext particleTick(VfxExpressionContext emitterContext,
                                                    Vec3 spawnPosition, Vec3 position,
                                                    Vec3 previousPosition, Vec3 velocity,
                                                    Vec3 rotation, Vec3 angularVelocity,
                                                    int age, int lifetime,
                                                    double particleRandom, Vec3 scale) {
        return updateParticleTick(particleSpawn(emitterContext, spawnPosition, particleRandom),
                spawnPosition, position, previousPosition, velocity, rotation,
                angularVelocity, age, lifetime, particleRandom, scale);
    }

    /** Reuses the same primitive context for physics and render expressions in one tick. */
    public static VfxExpressionContext updateParticleTick(VfxExpressionContext particleContext,
                                                           Vec3 spawnPosition, Vec3 position,
                                                           Vec3 previousPosition, Vec3 velocity,
                                                           Vec3 rotation, Vec3 angularVelocity,
                                                           int age, int lifetime,
                                                           double particleRandom, Vec3 scale) {
        double normalized = lifetime <= 0 ? 1.0 : Math.min(1.0, age / (double) lifetime);
        particleContext.setSlot(VfxVariableLayout.PARTICLE_AGE, "particle.age", age);
        particleContext.setSlot(VfxVariableLayout.PARTICLE_LIFETIME, "particle.lifetime", lifetime);
        particleContext.setSlot(VfxVariableLayout.PARTICLE_NORMALIZED_AGE, "particle.normalized_age", normalized);
        particleContext.setSlot(VfxVariableLayout.PARTICLE_RANDOM, "particle.random", particleRandom);
        particleContext.setVec3Slots(VfxVariableLayout.PARTICLE_SCALE, "particle.scale", scale);
        particleContext.setVec3Slots(VfxVariableLayout.PARTICLE_ROTATION, "particle.rotation", rotation);
        particleContext.setVec3Slots(VfxVariableLayout.PARTICLE_ANGULAR_VELOCITY, "particle.angular_velocity", angularVelocity);
        particleContext.setVec3Slots(VfxVariableLayout.SPAWN_POS, "spawn.pos", spawnPosition);
        particleContext.setVec3Slots(VfxVariableLayout.PARTICLE_POS, "particle.pos", position);
        particleContext.setVec3Slots(VfxVariableLayout.PARTICLE_PREV_POS, "particle.prev_pos", previousPosition);
        particleContext.setVec3Slots(VfxVariableLayout.PARTICLE_VEL, "particle.vel", velocity);
        particleContext.setSlot(VfxVariableLayout.PARTICLE_SPEED, "particle.speed", velocity.length());
        return particleContext;
    }
}
