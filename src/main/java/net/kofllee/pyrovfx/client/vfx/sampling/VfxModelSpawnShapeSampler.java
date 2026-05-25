package net.kofllee.pyrovfx.client.vfx.sampling;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.*;

public final class VfxModelSpawnShapeSampler {
    private static final Map<ResourceLocation, CachedModelShape> CACHE = new HashMap<>();

    private VfxModelSpawnShapeSampler() {}

    public static Vec3 sample(ResourceLocation modelId, double edgeThickness, RandomSource random){
        CachedModelShape shape = CACHE.computeIfAbsent(modelId, VfxModelSpawnShapeSampler::bakeShape);

        if(shape.triangles.isEmpty() || shape.totalArea <= 0.0){
            return Vec3.ZERO;
        }

        ModelTriangle triangle = pickTriangle(shape, random);
        Vec3 surfacePoint = sampleTriangle(triangle, random).subtract(0.5, 0.5, 0.5);

        if (edgeThickness <= 0.0) {
            return surfacePoint;
        }

        double thickness = Math.min(edgeThickness, 1.0);
        double inward = random.nextDouble() * thickness;

        return surfacePoint.lerp(Vec3.ZERO, inward);
    }

    private static Vec3 sampleTriangle(ModelTriangle triangle, RandomSource random) {
        double u = random.nextDouble();
        double v = random.nextDouble();

        if (u + v > 1.0) {
            u = 1.0 - u;
            v = 1.0 - v;
        }

        Vec3 ab = triangle.b().subtract(triangle.a());
        Vec3 ac = triangle.c().subtract(triangle.a());

        return triangle.a().add(ab.scale(u)).add(ac.scale(v));
    }

    private static ModelTriangle pickTriangle(CachedModelShape shape, RandomSource random) {
        double pick = random.nextDouble() * shape.totalArea();
        double passed = 0.0;

        for (ModelTriangle triangle : shape.triangles()) {
            passed += triangle.area();

            if (pick <= passed) {
                return triangle;
            }
        }

        return shape.triangles().get(shape.triangles().size() - 1);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static CachedModelShape bakeShape(ResourceLocation modelId) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(
                ModelResourceLocation.standalone(modelId)
        );

        List<ModelTriangle> triangles = new ArrayList<>();
        RandomSource random = RandomSource.create(42L);

        for(Direction dir : Direction.values()){
            addQuads(model.getQuads(null, dir, random, ModelData.EMPTY, null), triangles);
        }

        addQuads(model.getQuads(null, null, random, ModelData.EMPTY, null), triangles);

        double totalArea = 0;
        for(ModelTriangle triangle : triangles){
            totalArea += triangle.area();
        }

        return new CachedModelShape(List.copyOf(triangles), totalArea);
    }

    private static void addQuads(List<BakedQuad> quads, List<ModelTriangle> triangles) {
        for(BakedQuad quad : quads){
            int[] vertices = quad.getVertices();

            Vec3 v0 = readVertex(vertices, 0);
            Vec3 v1 = readVertex(vertices, 1);
            Vec3 v2 = readVertex(vertices, 2);
            Vec3 v3 = readVertex(vertices, 3);

            addTriangle(v0, v1, v2, triangles);
            addTriangle(v0, v2, v3, triangles);
        }
    }

    private static void addTriangle(Vec3 a, Vec3 b, Vec3 c, List<ModelTriangle> triangles) {
        double area = b.subtract(a).cross(c.subtract(a)).length() * 0.5;
        if(area > 1e-6){
            triangles.add(new ModelTriangle(a, b, c, area));
        }
    }

    private static Vec3 readVertex(int[] vertices, int vertexIndex) {
        int stride = vertices.length / 4;
        int base = vertexIndex * stride;

        return new Vec3(
                Float.intBitsToFloat(vertices[base]),
                Float.intBitsToFloat(vertices[base + 1]),
                Float.intBitsToFloat(vertices[base + 2])
        );
    }

    private record CachedModelShape(List<ModelTriangle> triangles, double totalArea) {}
    private record ModelTriangle(Vec3 a, Vec3 b, Vec3 c, double area) {}
}
