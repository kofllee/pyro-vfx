package net.kofllee.pyrovfx.client.vfx.util;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class VfxRandom {
    private VfxRandom() {}

    public static double between(RandomSource random, double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    public static double triangle(RandomSource random) {
        return random.nextDouble() - random.nextDouble();
    }

    public static double sign(RandomSource random) {
        return random.nextBoolean() ? 1.0 : -1.0;
    }

    public static Vec3 direction(RandomSource random) {
        double y = between(random, -1.0, 1.0);
        double angle = random.nextDouble() * Math.PI * 2.0;

        double horizontalRadius = Math.sqrt(1.0 - y * y);

        double x = Math.cos(angle) * horizontalRadius;
        double z = Math.sin(angle) * horizontalRadius;

        return new Vec3(x, y, z);
    }

    public static double speed(RandomSource random, double speed, double speedRandom) {
        return Math.max(0.0, speed + triangle(random) * speedRandom);
    }
}
