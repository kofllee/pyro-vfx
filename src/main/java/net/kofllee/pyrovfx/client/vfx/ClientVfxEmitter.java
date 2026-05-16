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
    private boolean instantEmitted;
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


    private final List<ClientVfxParticle> particles = new ArrayList<>();

    public ClientVfxEmitter(
            VfxEmitterDefinition definition,
            Vec3 emitterPosition,
            VfxExpressionContext effectStartContext,
            RandomSource random
    ) {

        this.definition = definition;

        VfxExpressionContext emitterStartContext = ClientVfxExpressionContexts.emitterStart(
                effectStartContext,
                emitterPosition,
                random
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
            RandomSource random
    ) {
        VfxExpressionContext emitterContext = ClientVfxExpressionContexts.emitterTick(
                effectContext,
                emitterPosition,
                age,
                delayTicks,
                activeTicks,
                emittedParticles
        );

        tickTriggers(
                level,
                effectPosition,
                emitterPosition,
                effectContext,
                random,
                events,
                emittersById
        );

        if(canSpawn && isActive()){
            tickSpawnAmount(level, effectPosition, emitterPosition, emitterContext, random);
        }

        for(var particleIterator = particles.iterator(); particleIterator.hasNext(); ) {
            ClientVfxParticle particle = particleIterator.next();

            particle.tick(emitterContext);

            if(particle.isDead()) {
                particleIterator.remove();
            }
        }

        if(canSpawn){
            age++;
        }
    }

    private void tickTriggers(
            ClientLevel level,
            Vec3 effectPosition,
            Vec3 emitterPosition,
            VfxExpressionContext effectContext,
            RandomSource random,
            Map<String, VfxEventDefinition> events,
            Map<String, ClientVfxEmitter> emittersById
    ) {
        if (definition.emitterLifetime().mode() == VfxEmitterLifetimeMode.MANUAL) {
            return;
        }

        if (age == 0 && !creationTriggersFired) {
            creationTriggersFired = true;

            for (VfxTriggerDefinition trigger : definition.triggers()) {
                if (trigger.type() == VfxTriggerType.ON_CREATION) {
                    VfxEventRunner.run(trigger.eventId(), events, emittersById, level, effectPosition, emitterPosition, effectContext, random);
                }
            }
        }

        if (isActive()) {
            for (VfxTriggerDefinition trigger : definition.triggers()) {
                if (trigger.type() == VfxTriggerType.TIMELINE
                        && age == trigger.timeTicks()) {
                    VfxEventRunner.run(trigger.eventId(), events, emittersById, level, effectPosition, emitterPosition, effectContext, random);
                }
            }
        }

        if (!expirationTriggersFired && isEmitterLifetimeFinished()) {
            expirationTriggersFired = true;

            for (VfxTriggerDefinition trigger : definition.triggers()) {
                if (trigger.type() == VfxTriggerType.ON_EXPIRATION) {
                    VfxEventRunner.run(trigger.eventId(), events, emittersById, level, effectPosition, emitterPosition, effectContext, random);
                }
            }
        }
    }

    private boolean isActive() {
        VfxEmitterLifetimeDefinition lifetime = definition.emitterLifetime();

        if(lifetime.mode() == VfxEmitterLifetimeMode.MANUAL) {
            return false;
        }

        if (age < delayTicks) {
            return false;
        }

        int localAge = age - delayTicks;

        if (lifetime.mode() == VfxEmitterLifetimeMode.ONCE) {
            return localAge < activeTicks;
        }

        if (lifetime.mode() == VfxEmitterLifetimeMode.LOOPING) {
            int cycleTicks = activeTicks + sleepTicks;

            if (cycleTicks <= 0) {
                return false;
            }

            if (loops > 0) {
                int completedCycles = localAge / cycleTicks;

                if (completedCycles >= loops) {
                    return false;
                }
            }

            int cycleAge = localAge % cycleTicks;
            return cycleAge < activeTicks;
        }

        return false;
    }


    private void tickSpawnAmount(
            ClientLevel level,
            Vec3 effectPosition,
            Vec3 emitterPosition,
            VfxExpressionContext emitterContext,
            RandomSource random
    ) {
        VfxSpawnAmountDefinition spawnAmount = definition.spawnAmount();

        if(spawnAmount.mode() == VfxSpawnAmountMode.INSTANT){
            tickInstantSpawnAmount(level, effectPosition, emitterPosition, emitterContext, random);
        }

        if(spawnAmount.mode() == VfxSpawnAmountMode.STEADY){
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
        if(particles.size() >= maxParticles) {
            return;
        }

        double rate = Math.max(0.0, definition.spawnAmount().rate().evaluate(emitterContext));
        spawnAccumulator += (float) (rate / 20.0);

        int amount = (int) spawnAccumulator;

        if(amount <= 0) {
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
            RandomSource random
    ) {
        if(instantEmitted) {
            return;
        }

        instantEmitted = true;

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
        for(int i = 0; i < count; i++){
            VfxExpressionContext preSpawnContext = ClientVfxExpressionContexts.particleSpawn(
                    emitterContext,
                    emitterPosition,
                    random
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
                    random
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

            if(definition.render().type() == VfxRenderType.MINECRAFT_PARTICLE){
                VanillaParticleBridge.spawn(level, definition.render(), particlePosition, velocity);
                emittedParticles++;
                continue;
            }

            if(definition.render().type() != VfxRenderType.SPRITE){
                emittedParticles++;
                continue;
            }

            int lifeTime = Math.max(1, (int) Math.round(definition.particleLifetime().maxAgeTicks().evaluate(particleSpawnContext)));

            double particleRandom = random.nextDouble();

            particles.add(new ClientVfxParticle(
                    definition,
                    particlePosition,
                    velocity,
                    rotation,
                    angularVelocity,
                    lifeTime,
                    particleRandom
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
    ){
        VfxSpawnAmountDefinition spawnAmount = definition.spawnAmount();

        int amount = Math.max(0, (int) Math.round(spawnAmount.amount().evaluate(emitterContext)));

        if(amount == 0) {
            return;
        }

        spawnParticles(level, effectPosition, emitterPosition, emitterContext, random, amount);
    }

    public boolean isFinished(){
        if(definition.emitterLifetime().mode() == VfxEmitterLifetimeMode.MANUAL) {
            return particles.isEmpty();
        }

        if(definition.emitterLifetime().mode() == VfxEmitterLifetimeMode.ONCE) {
            return age >= delayTicks + activeTicks;
        }

        if (definition.emitterLifetime().mode() == VfxEmitterLifetimeMode.LOOPING && loops > 0) {
            int cycleTicks = activeTicks + sleepTicks;
            return cycleTicks <= 0 || age >= delayTicks + cycleTicks * loops;
        }

        return false;
    }

    private boolean isEmitterLifetimeFinished() {
        if (definition.emitterLifetime().mode() == VfxEmitterLifetimeMode.MANUAL) {
            return false;
        }

        if (definition.emitterLifetime().mode() == VfxEmitterLifetimeMode.ONCE) {
            return age >= delayTicks + activeTicks;
        }

        if (definition.emitterLifetime().mode() == VfxEmitterLifetimeMode.LOOPING && loops > 0) {
            int cycleTicks = activeTicks + sleepTicks;
            return cycleTicks <= 0 || age >= delayTicks + cycleTicks * loops;
        }

        return false;
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
}
