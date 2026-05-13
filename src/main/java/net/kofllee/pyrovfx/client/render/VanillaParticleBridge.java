package net.kofllee.pyrovfx.client.render;

import net.kofllee.pyrovfx.vfx.VfxParticleDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public final class VanillaParticleBridge {
    private VanillaParticleBridge() {}

    public static void spawn(ClientLevel level, Vec3 pos, VfxParticleDefinition particle, RandomSource random) {
        ParticleOptions particleOptions = resolveMinecraftParticle(particle.appearance().minecraftParticleId());

        if (particleOptions == null) {
            return;
        }

        double speed = particle.speed();

        double vx = randomTriangle(random) * speed;
        double vy = random.nextDouble() * speed;
        double vz = randomTriangle(random) * speed;

        level.addParticle(
                particleOptions,
                pos.x, pos.y, pos.z,
                vx, vy, vz
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
