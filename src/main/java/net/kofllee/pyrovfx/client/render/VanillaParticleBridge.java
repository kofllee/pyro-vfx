package net.kofllee.pyrovfx.client.render;

import net.kofllee.pyrovfx.vfx.definition.VfxParticleDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;


public final class VanillaParticleBridge {
    private VanillaParticleBridge() {}

    public static void spawn(ClientLevel level, VfxParticleDefinition particle, Vec3 position, Vec3 velocity) {
        ParticleOptions particleOptions = resolveMinecraftParticle(particle.appearance().minecraftParticleId());

        if (particleOptions == null) {
            return;
        }

        level.addParticle(
                particleOptions,
                position.x,
                position.y,
                position.z,
                velocity.x,
                velocity.y,
                velocity.z
        );
    }

    private static ParticleOptions resolveMinecraftParticle(ResourceLocation id){
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(id);

        if(type instanceof ParticleOptions options) {
            return options;
        }

        return null;
    }

    private static double randomTriangle(RandomSource random) {
        return random.nextDouble() - random.nextDouble();
    }
}
