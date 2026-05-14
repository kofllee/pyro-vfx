package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.client.render.VanillaParticleBridge;
import net.kofllee.pyrovfx.client.vfx.sampling.VfxSpawnPositionSampler;
import net.kofllee.pyrovfx.client.vfx.sampling.VfxVelocitySampler;
import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxSpawnAmountDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxEmitterTimingDefinition;
import net.kofllee.pyrovfx.vfx.type.VfxSpawnAmountMode;
import net.kofllee.pyrovfx.vfx.type.VfxEmitterTimingType;
import net.kofllee.pyrovfx.vfx.type.VfxParticleRenderType;
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

        tickSpawnAmount(level, emitterPosition, random);
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

    private void tickSpawnAmount(ClientLevel level, Vec3 emitterPosition, RandomSource random) {
        VfxSpawnAmountDefinition spawnAmount = definition.spawnAmount();

        if(spawnAmount.mode() == VfxSpawnAmountMode.INSTANT){
            tickInstantSpawnAmount(level, emitterPosition, random, spawnAmount);
        }

        if(spawnAmount.mode() == VfxSpawnAmountMode.STEADY){
            tickSteadySpawnAmount(level, emitterPosition, random, spawnAmount);
        }

        if(spawnAmount.mode() == VfxSpawnAmountMode.MANUAL){
            return;
        }
    }

    private void tickSteadySpawnAmount(ClientLevel level, Vec3 emitterPosition, RandomSource random, VfxSpawnAmountDefinition spawnAmount) {
        if(emittedParticles >= spawnAmount.maxParticles()) {
            return;
        }

        spawnAccumulator += spawnAmount.rate() / 20.0F;

        int amount = (int) spawnAccumulator;

        if(amount <= 0) {
            return;
        }

        int remaining = spawnAmount.maxParticles() - emittedParticles;
        amount = Math.min(amount, remaining);

        spawnAccumulator -= amount;
        emittedParticles += amount;

        spawnParticles(level, emitterPosition, random, amount);
    }


    private void tickInstantSpawnAmount(ClientLevel level, Vec3 emitterPosition, RandomSource random, VfxSpawnAmountDefinition spawnAmount) {
        if(instantEmitted) {
            return;
        }

        instantEmitted = true;
        emittedParticles += spawnAmount.amount();

        spawnParticles(level, emitterPosition, random, spawnAmount.amount());
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
