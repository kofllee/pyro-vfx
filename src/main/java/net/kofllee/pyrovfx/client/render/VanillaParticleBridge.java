package net.kofllee.pyrovfx.client.render;

import net.kofllee.pyrovfx.vfx.VfxParticleDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class VanillaParticleBridge {
    private VanillaParticleBridge() {}

    public static void spawn(ClientLevel level, Vec3 pos, VfxParticleDefinition particle, RandomSource random) {
        double speed = particle.speed();

        double vx = randomTriangle(random) * speed;
        double vy = random.nextDouble() * speed;
        double vz = randomTriangle(random) * speed;

        level.addParticle(
                particle.particleOptions(),
                pos.x, pos.y, pos.z,
                vx, vy, vz
        );
    }

    private static double randomTriangle(RandomSource random) {
        return random.nextDouble() - random.nextDouble();
    }
}
