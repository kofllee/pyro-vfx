package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.client.render.VanillaParticleBridge;
import net.kofllee.pyrovfx.client.vfx.sampling.VfxSpawnPositionSampler;
import net.kofllee.pyrovfx.client.vfx.sampling.VfxMotionSampler;
import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxEmitterLifetimeDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxSpawnAmountDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.type.VfxEmitterLifetimeMode;
import net.kofllee.pyrovfx.vfx.type.VfxSpawnAmountMode;
import net.kofllee.pyrovfx.vfx.type.VfxRenderType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

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

        if(isActive()){
            tickSpawnAmount(level, effectPosition, emitterPosition, emitterContext, random);
        }

        age++;
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
        if(emittedParticles >= maxParticles) {
            return;
        }

        double rate = Math.max(0.0, definition.spawnAmount().rate().evaluate(emitterContext));
        spawnAccumulator += (float) (rate / 20.0);

        int amount = (int) spawnAccumulator;

        if(amount <= 0) {
            return;
        }

        int remaining = maxParticles  - emittedParticles;
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

            emittedParticles++;

            if(definition.render().type() == VfxRenderType.MINECRAFT_PARTICLE){
                VanillaParticleBridge.spawn(level, definition.render(), particlePosition, velocity);
            }
        }
    }

    public boolean isFinished(){
        if(definition.emitterLifetime().mode() == VfxEmitterLifetimeMode.MANUAL) {
            return true;
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
}
