package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.client.render.VanillaParticleBridge;
import net.kofllee.pyrovfx.client.vfx.sampling.VfxRotationSampler;
import net.kofllee.pyrovfx.client.vfx.sampling.VfxSpawnPositionSampler;
import net.kofllee.pyrovfx.client.vfx.sampling.VfxMotionSampler;
import net.kofllee.pyrovfx.vfx.definition.*;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.type.VfxEmitterLifetimeMode;
import net.kofllee.pyrovfx.vfx.type.VfxSpawnAmountMode;
import net.kofllee.pyrovfx.vfx.type.VfxRenderType;
import net.kofllee.pyrovfx.vfx.type.VfxTriggerType;
import net.kofllee.pyrovfx.vfx.value.VfxColor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ClientVfxEmitter {
    private final VfxEmitterDefinition definition;
    private int age;
    private int lastInstantAge = -1;
    private int emittedParticles;
    private float spawnAccumulator;

    private final int delayTicks;
    private final int activeTicks;
    private final int sleepTicks;
    private final int loops;
    private final int instantAmount;
    private final int maxParticles;

    private boolean creationTriggersFired = false;
    private boolean expirationTriggersFired = false;

    private final double emitterRandom;


    private final List<ClientVfxParticle> particles = new ArrayList<>();

    public ClientVfxEmitter(
            VfxEmitterDefinition definition,
            Vec3 emitterPosition,
            VfxExpressionContext effectStartContext,
            RandomSource random
    ) {

        this.definition = definition;

        this.emitterRandom = random.nextDouble();

        VfxExpressionContext emitterStartContext = ClientVfxExpressionContexts.emitterStart(
                effectStartContext,
                emitterPosition,
                emitterRandom
        );

        VfxEmitterLifetimeDefinition lifetime = definition.emitterLifetime();
        VfxSpawnAmountDefinition spawnAmount = definition.spawnAmount();

        this.delayTicks = Math.max(0, (int) Math.round(lifetime.delayTicks().evaluate(emitterStartContext)));
        this.activeTicks = Math.max(0, (int) Math.round(lifetime.activeTicks().evaluate(emitterStartContext)));
        this.sleepTicks = Math.max(0, (int) Math.round(lifetime.sleepTicks().evaluate(emitterStartContext)));
        this.loops = Math.max(0, (int) Math.round(lifetime.loops().evaluate(emitterStartContext)));

        this.instantAmount = Math.max(0, (int) Math.round(spawnAmount.amount().evaluate(emitterStartContext)));
        this.maxParticles = Math.max(0, (int) Math.round(spawnAmount.maxParticles().evaluate(emitterStartContext)));
    }

    public void tick(
            ClientLevel level,
            Vec3 effectPosition,
            Vec3 emitterPosition,
            VfxExpressionContext effectContext,
            boolean canSpawn,
            Map<String, VfxEventDefinition> events,
            Map<String, ClientVfxEmitter> emittersById,
            VfxEventRuntime eventRuntime,
            RandomSource random
    ) {
        VfxLifetimeState lifetimeState = VfxLifetimeRuntime.emitter(
                definition.emitterLifetime().mode(),
                age,
                delayTicks,
                activeTicks,
                sleepTicks,
                loops
        );

        VfxExpressionContext emitterContext = ClientVfxExpressionContexts.emitterTick(
                effectContext,
                emitterPosition,
                lifetimeState,
                emittedParticles,
                emitterRandom
        );

        tickTriggers(
                level,
                effectPosition,
                emitterPosition,
                emitterContext,
                random,
                events,
                emittersById,
                lifetimeState,
                eventRuntime
        );

        if (canSpawn && lifetimeState.active()) {
            tickSpawnAmount(level, effectPosition, emitterPosition, emitterContext, lifetimeState, random);
        }

        for (var particleIterator = particles.iterator(); particleIterator.hasNext(); ) {
            ClientVfxParticle particle = particleIterator.next();

            particle.tick(emitterContext);

            if (particle.isDead()) {
                particleIterator.remove();
            }
        }

        if (canSpawn) {
            age++;
        }
    }

    private void tickTriggers(
            ClientLevel level,
            Vec3 effectPosition,
            Vec3 emitterPosition,
            VfxExpressionContext emitterContext,
            RandomSource random,
            Map<String, VfxEventDefinition> events,
            Map<String, ClientVfxEmitter> emittersById,
            VfxLifetimeState lifetimeState,
            VfxEventRuntime eventRuntime
    ) {
        if (definition.emitterLifetime().mode() == VfxEmitterLifetimeMode.MANUAL) {
            return;
        }

        if (age == 0 && !creationTriggersFired) {
            creationTriggersFired = true;

            for (VfxTriggerDefinition trigger : definition.triggers()) {
                if (trigger.type() == VfxTriggerType.ON_CREATION) {
                    VfxEventRunner.run(trigger.eventId(), events, emittersById, level, effectPosition, emitterPosition, emitterContext, eventRuntime, random);
                }
            }
        }

        if (lifetimeState.active()) {
            for (VfxTriggerDefinition trigger : definition.triggers()) {
                if (trigger.type() == VfxTriggerType.TIMELINE
                        && age == trigger.timeTicks()) {
                    VfxEventRunner.run(trigger.eventId(), events, emittersById, level, effectPosition, emitterPosition, emitterContext, eventRuntime, random);
                }
            }
        }

        if (!expirationTriggersFired && lifetimeState.finished()) {
            expirationTriggersFired = true;

            for (VfxTriggerDefinition trigger : definition.triggers()) {
                if (trigger.type() == VfxTriggerType.ON_EXPIRATION) {
                    VfxEventRunner.run(trigger.eventId(), events, emittersById, level, effectPosition, emitterPosition, effectContext, eventRuntime, random);
                }
            }
        }
    }

    private void tickSpawnAmount(
            ClientLevel level,
            Vec3 effectPosition,
            Vec3 emitterPosition,
            VfxExpressionContext emitterContext,
            VfxLifetimeState lifetimeState,
            RandomSource random
    ) {
        VfxSpawnAmountDefinition spawnAmount = definition.spawnAmount();

        if (spawnAmount.mode() == VfxSpawnAmountMode.INSTANT) {
            tickInstantSpawnAmount(level, effectPosition, emitterPosition, emitterContext, lifetimeState, random);
        }

        if (spawnAmount.mode() == VfxSpawnAmountMode.STEADY) {
            tickSteadySpawnAmount(level, effectPosition, emitterPosition, emitterContext, random);
        }
    }

    private void tickSteadySpawnAmount(
            ClientLevel level,
            Vec3 effectPosition,
            Vec3 emitterPosition,
            VfxExpressionContext emitterContext,
            RandomSource random
    ) {
        if (particles.size() >= maxParticles) {
            return;
        }

        double rate = Math.max(0.0, definition.spawnAmount().rate().evaluate(emitterContext));
        spawnAccumulator += (float) (rate / 20.0);

        int amount = (int) spawnAccumulator;

        if (amount <= 0) {
            return;
        }

        int remaining = maxParticles - particles.size();
        amount = Math.min(amount, remaining);

        spawnAccumulator -= amount;

        spawnParticles(level, effectPosition, emitterPosition, emitterContext, random, amount);
    }


    private void tickInstantSpawnAmount(
            ClientLevel level,
            Vec3 effectPosition,
            Vec3 emitterPosition,
            VfxExpressionContext emitterContext,
            VfxLifetimeState lifetimeState,
            RandomSource random
    ) {
        if (definition.emitterLifetime().mode() == VfxEmitterLifetimeMode.LOOPING) {
            if (lifetimeState.localAge() != 0) {
                return;
            }

            if (lastInstantAge == age) {
                return;
            }

            lastInstantAge = age;

            spawnParticles(level, effectPosition, emitterPosition, emitterContext, random, instantAmount);
            return;
        }

        if (lastInstantAge >= 0) {
            return;
        }

        lastInstantAge = lifetimeState.localAge();

        spawnParticles(level, effectPosition, emitterPosition, emitterContext, random, instantAmount);
    }

    private void spawnParticles(
            ClientLevel level,
            Vec3 effectPosition,
            Vec3 emitterPosition,
            VfxExpressionContext emitterContext,
            RandomSource random,
            int count
    ) {
        for (int i = 0; i < count; i++) {
            double particleRandom = random.nextDouble();

            VfxExpressionContext preSpawnContext = ClientVfxExpressionContexts.particleSpawn(
                    emitterContext,
                    emitterPosition,
                    particleRandom
            );

            Vec3 particlePosition = VfxSpawnPositionSampler.sample(
                    emitterPosition,
                    definition.spawnShape(),
                    preSpawnContext,
                    random
            );

            VfxExpressionContext particleSpawnContext = ClientVfxExpressionContexts.particleSpawn(
                    emitterContext,
                    particlePosition,
                    particleRandom
            );


            Vec3 velocity = VfxMotionSampler.sampleInitialVelocity(
                    definition.motion(),
                    emitterPosition,
                    particlePosition,
                    particleSpawnContext,
                    random
            );

            Vec3 rotation = VfxRotationSampler.sampleInitialRotation(
                    definition.rotation(),
                    particleSpawnContext
            );

            Vec3 angularVelocity = VfxRotationSampler.sampleInitialAngularVelocity(
                    definition.rotation(),
                    particleSpawnContext
            );

            if (definition.render().type() == VfxRenderType.MINECRAFT_PARTICLE) {
                VanillaParticleBridge.spawn(level, definition.render(), particlePosition, velocity);
                emittedParticles++;
                continue;
            }

            if (definition.render().type() != VfxRenderType.SPRITE) {
                emittedParticles++;
                continue;
            }

            int lifeTime = Math.max(1, (int) Math.round(definition.particleLifetime().maxAgeTicks().evaluate(particleSpawnContext)));

            VfxExpressionContext initialRenderContext = ClientVfxExpressionContexts.particleTick(
                    emitterContext,
                    particlePosition,
                    particlePosition,
                    particlePosition,
                    velocity,
                    rotation,
                    angularVelocity,
                    0,
                    lifeTime,
                    particleRandom,
                    new Vec3(1.0, 1.0, 1.0)
            );

            Vec3 initialScale = definition.render().appearance().scale()
                    .evaluate(initialRenderContext)
                    .toVec3();

            VfxColor initialColor = definition.render().appearance().color()
                    .evaluate(initialRenderContext);

            particles.add(new ClientVfxParticle(
                    definition,
                    particlePosition,
                    velocity,
                    rotation,
                    angularVelocity,
                    lifeTime,
                    particleRandom,
                    initialScale,
                    initialColor,
                    particleSpawnContext
            ));

            emittedParticles++;
        }
    }

    public void emitManual(
            ClientLevel level,
            Vec3 effectPosition,
            Vec3 emitterPosition,
            VfxExpressionContext emitterContext,
            RandomSource random
    ) {
        VfxSpawnAmountDefinition spawnAmount = definition.spawnAmount();

        int amount = Math.max(0, (int) Math.round(spawnAmount.amount().evaluate(emitterContext)));

        if (amount == 0) {
            return;
        }

        spawnParticles(level, effectPosition, emitterPosition, emitterContext, random, amount);
    }

    public boolean isFinished() {
        if (definition.emitterLifetime().mode() == VfxEmitterLifetimeMode.MANUAL) {
            return particles.isEmpty();
        }

        return lifetimeState().finished() && particles.isEmpty();
    }

    public VfxEmitterDefinition definition() {
        return definition;
    }

    public List<ClientVfxParticle> particles() {
        return Collections.unmodifiableList(particles);
    }

    public int age() {
        return age;
    }

    public int activeTicks() {
        return activeTicks;
    }

    public int delayTicks() {
        return delayTicks;
    }

    public int emittedParticles() {
        return emittedParticles;
    }

    public VfxLifetimeState lifetimeState() {
        return VfxLifetimeRuntime.emitter(
                definition.emitterLifetime().mode(),
                age,
                delayTicks,
                activeTicks,
                sleepTicks,
                loops
        );
    }

    public double emitterRandom() {
        return emitterRandom;
    }
}
