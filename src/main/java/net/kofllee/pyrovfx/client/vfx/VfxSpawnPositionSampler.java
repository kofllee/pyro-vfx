package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.client.vfx.util.VfxRandom;
import net.kofllee.pyrovfx.vfx.VfxEmitterShape;
import net.kofllee.pyrovfx.vfx.VfxEmitterShapeDefinition;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class VfxSpawnPositionSampler {
    private VfxSpawnPositionSampler() {}

    public static Vec3 sample(Vec3 emitterPosition, VfxEmitterShapeDefinition shape, RandomSource random) {
        Vec3 base = emitterPosition.add(shape.offset().toVec3());

        if(shape.shape() == VfxEmitterShape.POINT){
            return base;
        }
        if(shape.shape() == VfxEmitterShape.SPHERE){
            return sampleSphere(base, shape.radius(), shape.surfaceOnly(), random);
        }

        if(shape.shape() == VfxEmitterShape.BOX){
            return sampleBox(base, shape.halfExtents().toVec3(), shape.surfaceOnly(), random);
        }

        return base;
    }

    private static Vec3 sampleBox(Vec3 center, Vec3 halfExtents, boolean surfaceOnly, RandomSource random) {
        double x = VfxRandom.between(random, -halfExtents.x, halfExtents.x);
        double y = VfxRandom.between(random, -halfExtents.y, halfExtents.y);
        double z = VfxRandom.between(random, -halfExtents.z, halfExtents.z);

        if(!surfaceOnly){
            return center.add(x, y, z);
        }

        int axis = random.nextInt(3);
        double sign = random.nextBoolean() ? 1 : -1;

        if(axis == 0){
            x = halfExtents.x * sign;
        }
        else if(axis == 1){
            y = halfExtents.y * sign;
        }
        else if(axis == 2){
            z = halfExtents.z * sign;
        }

        return center.add(x, y, z);
    }

    private static Vec3 sampleSphere(Vec3 center, double radius, boolean surfaceOnly, RandomSource random) {
        if(radius <= 0){
            return center;
        }

        Vec3 direction = VfxRandom.direction(random);

        if(surfaceOnly){
            return center.add(direction.scale(radius));
        }

        double distance = radius * Math.cbrt(random.nextDouble());
        return center.add(direction.scale(distance));
    }
}
