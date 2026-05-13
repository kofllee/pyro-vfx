package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.client.render.VanillaParticleBridge;
import net.kofllee.pyrovfx.vfx.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class ClientVfxEmitter {
    private final VfxEmitterDefinition definition;
    private int age;
    private boolean instantEmitted;
    private int emittedParticles;
    private float spawnAccumulator;

    public ClientVfxEmitter(final VfxEmitterDefinition definition) {
        this.definition = definition;
    }

    public void tick(ClientLevel level, Vec3 emitterPosition, RandomSource random) {
        if(!isActive()){
            age++;
            return;
        }

        tickRate(level, emitterPosition, random);
    }

    private boolean isActive() {
        VfxEmitterTimingDefinition timing = definition.timing();

        if(age < timing.delayTicks()){
            return false;
        }

        int localAge = age - timing.delayTicks();

        if(timing.timingType() == VfxEmitterTimingType.ONCE){
            return localAge < timing.activeTicks();
        }

        if(timing.timingType() == VfxEmitterTimingType.LOOPING){
            int cycleTicks = timing.activeTicks() + timing.sleepTicks();

            if(cycleTicks <= 0){
                return false;
            }

            int cycleAge = localAge % cycleTicks;
            return cycleAge < timing.activeTicks();
        }

        return false;
    }

    private void tickRate(ClientLevel level, Vec3 emitterPosition, RandomSource random) {
        VfxEmitterRateDefinition rate = definition.rate();

        if(rate.type() == VfxEmitterRateType.INSTANT){
            tickInstantRate(level, emitterPosition, random, rate);
        }

        if(rate.type() == VfxEmitterRateType.STEADY){
            tickSteadyRate(level, emitterPosition, random, rate);
        }
    }

    private void tickSteadyRate(ClientLevel level, Vec3 emitterPosition, RandomSource random, VfxEmitterRateDefinition rate) {
        if(emittedParticles >= rate.maxParticles())
            return;

        spawnAccumulator += rate.particlePerTicks();

        int amount = (int) spawnAccumulator;

        if(amount < 0)
            return;

        int remaining = rate.maxParticles() - emittedParticles;
        amount = Math.min(amount, remaining);

        spawnAccumulator -= amount;
        emittedParticles += amount;

        spawnParticles(level, emitterPosition, random, amount);
    }

    private void tickInstantRate(ClientLevel level, Vec3 emitterPosition, RandomSource random, VfxEmitterRateDefinition rate) {
        if(instantEmitted)
            return;

        instantEmitted = true;
        emittedParticles += rate.count();

        spawnParticles(level, emitterPosition, random, rate.count());
    }

    private void spawnParticles(ClientLevel level, Vec3 emitterPosition, RandomSource random, int count) {
        for(int i = 0; i < count; i++){
            Vec3 particlePosition = VfxSpawnPositionSampler.sample(emitterPosition, definition.shape(), random);

            Vec3 velocity = VfxVelocitySampler.sample(definition.particle().motion().velocity(), emitterPosition, particlePosition, random);

            if(definition.particle().appearance().renderType() == VfxParticleRenderType.MINECRAFT_PARTICLE){
                VanillaParticleBridge.spawn(level, definition.particle(), particlePosition, velocity);
            }
        }
    }
}
