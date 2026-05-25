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

        if (spawnShape.type() == VfxSpawnShapeType.LINE) {
            return sampleLine(
                    emitterPosition,
                    spawnShape.height().evaluate(context),
                    spawnShape.axis().evaluate(context).toVec3(),
                    random
            );
        }

        if (spawnShape.type() == VfxSpawnShapeType.DISC) {
            return sampleDisc(
                    emitterPosition,
                    spawnShape.radius().evaluate(context),
                    spawnShape.axis().evaluate(context).toVec3(),
                    spawnShape.edgeThickness().evaluate(context),
                    random
            );
        }

        if (spawnShape.type() == VfxSpawnShapeType.RING) {
            return sampleRing(
                    emitterPosition,
                    spawnShape.innerRadius().evaluate(context),
                    spawnShape.radius().evaluate(context),
                    spawnShape.axis().evaluate(context).toVec3(),
                    random
            );
        }

        if (spawnShape.type() == VfxSpawnShapeType.CONE) {
            return sampleCone(
                    emitterPosition,
                    spawnShape.radius().evaluate(context),
                    spawnShape.height().evaluate(context),
                    spawnShape.axis().evaluate(context).toVec3(),
                    spawnShape.edgeThickness().evaluate(context),
                    random
            );
        }

        if (spawnShape.type() == VfxSpawnShapeType.MODEL) {
            Vec3 local = VfxModelSpawnShapeSampler.sample(
                    spawnShape.model(),
                    spawnShape.edgeThickness().evaluate(context),
                    random
            );



            Vec3 scale = spawnShape.scale().evaluate(context).toVec3();

            return emitterPosition.add(
                    local.x * scale.x,
                    local.y * scale.y,
                    local.z * scale.z
            );
        }

        return emitterPosition;
    }

    private static Vec3 sampleCone(Vec3 center, double radius, double height, Vec3 axis, double edgeThickness, RandomSource random) {
        if (radius <= 0.0 || height <= 0.0) {
            return center;
        }

        Vec3 normal = axis.normalize();
        Basis basis = Basis.fromNormal(normal);

        double y = random.nextDouble() * height;
        double t = y / height;
        double sliceRadius = radius * (1.0 - t);

        double distance;
        if(edgeThickness <= 0.0) {
            distance = sliceRadius;
        }
        else {
            double inner = sliceRadius * (1 - edgeThickness);
            distance = Math.sqrt(VfxRandom.between(random, inner * inner, sliceRadius * sliceRadius));
        }

        double angle = random.nextDouble() * 2 * Math.PI;
        return center.add(basis.xAxis.scale(Math.cos(angle) * distance)).add(basis.zAxis.scale(Math.sin(angle) * distance)).add(normal.scale(y));
    }

    private static Vec3 sampleRing(Vec3 center, double innerRadius, double outerRadius, Vec3 axis, RandomSource random) {
        if (outerRadius <= 0.0) {
            return center;
        }

        double inner = Math.clamp(innerRadius, 0.0, outerRadius);
        Vec3 normal = axis.normalize();
        Basis basis = Basis.fromNormal(normal);

        double angle = random.nextDouble() * 2 * Math.PI;
        double minArea = inner * inner;
        double maxArea = outerRadius * outerRadius;
        double distance = Math.sqrt(VfxRandom.between(random, minArea, maxArea));

        return center.add(basis.xAxis.scale(Math.cos(angle) * distance)).add(basis.zAxis.scale(Math.sin(angle) * distance));
    }

    private static Vec3 sampleDisc(Vec3 center, double radius, Vec3 axis, double edgeThickness, RandomSource random) {
        if (radius <= 0.0) {
            return center;
        }

        Vec3 normal = axis.normalize();
        Basis basis = Basis.fromNormal(normal);

        double angle = random.nextDouble() * 2 * Math.PI;

        double distance;
        if(edgeThickness <= 0.0) {
            distance = Math.sqrt(random.nextDouble()) * radius;
        }
        else{
            double inner = radius * (1.0 - edgeThickness);
            double minArea = inner * inner;
            double maxArea = radius * radius;
            double area = VfxRandom.between(random, minArea, maxArea);
            distance = Math.sqrt(area);
        }

        return center.add(basis.xAxis.scale(Math.cos(angle) * distance)).add(basis.zAxis.scale(Math.sin(angle) * distance));
    }

    private static Vec3 sampleLine(Vec3 emitterPosition, Double evaluate, Vec3 axis, RandomSource random) {
        Vec3 normal = axis.normalize();
        double halfLength = evaluate * 0.5;
        double distance = VfxRandom.between(random, -halfLength, halfLength);
        return emitterPosition.add(normal.scale(distance));
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

    private record Basis(Vec3 xAxis, Vec3 zAxis) {
        public static Basis fromNormal(Vec3 normal) {
            Vec3 helper = Math.abs(normal.y) > 0.9
                    ? new Vec3(1.0, 0.0, 0.0)
                    : new Vec3(0.0, 1.0, 0.0);

            Vec3 xAxis = helper.cross(normal).normalize();
            Vec3 zAxis = normal.cross(xAxis).normalize();

            return new Basis(xAxis, zAxis);
        }
    }
}
