package net.kofllee.pyrovfx.client.vfx.sampling;

import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.type.VfxSpawnShapeType;
import net.kofllee.pyrovfx.vfx.definition.VfxSpawnShapeDefinition;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class VfxSpawnPositionSampler {
    private VfxSpawnPositionSampler() {}

    public static Vec3 sample(
            Vec3 emitterPosition,
            VfxSpawnShapeDefinition spawnShape,
            VfxExpressionContext context,
            RandomSource random
    ) {
        if(spawnShape.type() == VfxSpawnShapeType.POINT){
            return emitterPosition;
        }

        if(spawnShape.type() == VfxSpawnShapeType.SPHERE){
            return sampleSphere(
                    emitterPosition,
                    spawnShape.radius().evaluate(context),
                    spawnShape.edgeThickness().evaluate(context),
                    random
            );
        }

        if(spawnShape.type() == VfxSpawnShapeType.BOX){
            return sampleBox(
                    emitterPosition,
                    spawnShape.halfExtents().evaluate(context).toVec3(),
                    spawnShape.edgeThickness().evaluate(context),
                    random
            );
        }

        return emitterPosition;
    }

    private static Vec3 sampleBox(Vec3 center, Vec3 halfExtents, double edgeThickness, RandomSource random) {
        if (edgeThickness <= 0.0) {
            return sampleBoxSurface(center, halfExtents, random);
        }

        double minX = halfExtents.x * (1.0 - edgeThickness);
        double minY = halfExtents.y * (1.0 - edgeThickness);
        double minZ = halfExtents.z * (1.0 - edgeThickness);

        for (int attempt = 0; attempt < 16; attempt++) {
            double x = VfxRandom.between(random, -halfExtents.x, halfExtents.x);
            double y = VfxRandom.between(random, -halfExtents.y, halfExtents.y);
            double z = VfxRandom.between(random, -halfExtents.z, halfExtents.z);

            if (Math.abs(x) >= minX || Math.abs(y) >= minY || Math.abs(z) >= minZ) {
                return center.add(x, y, z);
            }
        }

        return sampleBoxSurface(center, halfExtents, random);
    }

    private static Vec3 sampleBoxSurface(Vec3 center, Vec3 halfExtents, RandomSource random) {
        double areaX = halfExtents.y * halfExtents.z;
        double areaY = halfExtents.x * halfExtents.z;
        double areaZ = halfExtents.x * halfExtents.y;

        double totalArea = areaX + areaY + areaZ;
        double pick = random.nextDouble() * totalArea;

        double x = VfxRandom.between(random, -halfExtents.x, halfExtents.x);
        double y = VfxRandom.between(random, -halfExtents.y, halfExtents.y);
        double z = VfxRandom.between(random, -halfExtents.z, halfExtents.z);

        if (pick < areaX) {
            x = halfExtents.x * VfxRandom.sign(random);
        } else if (pick < areaX + areaY) {
            y = halfExtents.y * VfxRandom.sign(random);
        } else {
            z = halfExtents.z * VfxRandom.sign(random);
        }

        return center.add(x, y, z);
    }

    private static Vec3 sampleSphere(Vec3 center, double radius, double edgeThickness, RandomSource random) {
        if(radius <= 0){
            return center;
        }

        Vec3 direction = VfxRandom.direction(random);

        if (edgeThickness <= 0.0) {
            return center.add(direction.scale(radius));
        }

        double minRadius = radius * (1.0 - edgeThickness);
        double minVolume = minRadius * minRadius * minRadius;
        double maxVolume = radius * radius * radius;

        double volume = VfxRandom.between(random, minVolume, maxVolume);
        double distance = Math.cbrt(volume);

        return center.add(direction.scale(distance));
    }
}
