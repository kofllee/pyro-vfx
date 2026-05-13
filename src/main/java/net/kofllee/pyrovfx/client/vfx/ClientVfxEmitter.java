package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.client.render.VanillaParticleBridge;
import net.kofllee.pyrovfx.vfx.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.VfxEmitterMode;
import net.kofllee.pyrovfx.vfx.VfxParticleRenderType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class ClientVfxEmitter {
    private final VfxEmitterDefinition definition;
    private boolean hasBurst;

    public ClientVfxEmitter(final VfxEmitterDefinition definition) {
        this.definition = definition;
    }

    public void tick(ClientLevel level, Vec3 emitterPosition, RandomSource random) {
        if (definition.emitterMode() == VfxEmitterMode.BURST){
            tickBurst(level, emitterPosition, random);
        }
    }

    private void tickBurst(ClientLevel level, Vec3 emitterPosition, RandomSource random) {
        if(hasBurst){
            return;
        }

        hasBurst = true;

        for(int i = 0; i < definition.count(); i++){
            Vec3 particlePosition = VfxSpawnPositionSampler.sample(emitterPosition, definition.shape(), random);

            Vec3 velocity = VfxVelocitySampler.sample(definition.particle().motion().velocity(), emitterPosition, particlePosition, random);

            if(definition.particle().appearance().renderType() == VfxParticleRenderType.MINECRAFT_PARTICLE){
                VanillaParticleBridge.spawn(level, definition.particle(), particlePosition, velocity);
            }
        }
    }
}
