package net.kofllee.pyrovfx.client.vfx.sampling;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static net.kofllee.pyrovfx.client.vfx.sampling.VfxRandom.between;

public final class VfxModelSpawnShapeSampler {
    private static final Map<ResourceLocation, CachedModelShape> CACHE = new HashMap<>();

    private VfxModelSpawnShapeSampler() {}

    public static Vec3 sample(ResourceLocation modelId, double edgeThickness, RandomSource random) {
        CachedModelShape shape = CACHE.computeIfAbsent(modelId, VfxModelSpawnShapeSampler::loadShape);

        System.out.println("Sampling model: " + modelId + " with " + shape.elements().size() + " elements and total surface area: " + shape.totalSurfaceArea());

        if (shape.elements().isEmpty() || shape.totalSurfaceArea() <= 0.0) {
            return Vec3.ZERO;
        }

        ModelElement element = pickElement(shape, random);
        return sampleElement(element, edgeThickness, random).subtract(0.5, 0.5, 0.5);
    }

    private static Vec3 sampleElement(ModelElement element, double edgeThickness, RandomSource random) {
        double thickness = Math.clamp(edgeThickness, 0.0, 1.0);

        Vec3 point = thickness <= 0.0
                ? sampleBoxSurface(element.min(), element.max(), random)
                : sampleBoxShell(element.min(), element.max(), thickness, random);
        return element.rotation().apply(point);
    }

    private static Vec3 sampleBoxShell(Vec3 min, Vec3 max, double thickness, RandomSource random) {
        Vec3 center = min.add(max).scale(0.5);
        Vec3 half = max.subtract(min).scale(0.5);

        double innerX = half.x * (1.0 - thickness);
        double innerY = half.y * (1.0 - thickness);
        double innerZ = half.z * (1.0 - thickness);

        for (int attempt = 0; attempt < 16; attempt++) {
            double x = between(random, -half.x, half.x);
            double y = between(random, -half.y, half.y);
            double z = between(random, -half.z, half.z);

            if (Math.abs(x) >= innerX || Math.abs(y) >= innerY || Math.abs(z) >= innerZ) {
                return center.add(x, y, z);
            }
        }

        return sampleBoxSurface(min, max, random);
    }

    private static Vec3 sampleBoxSurface(Vec3 min, Vec3 max, RandomSource random) {
        double sx = max.x - min.x;
        double sy = max.y - min.y;
        double sz = max.z - min.z;

        double areaX = sy * sz;
        double areaY = sx * sz;
        double areaZ = sx * sy;
        double totalArea = areaX + areaY + areaZ;

        if (totalArea <= 0.0) {
            return min.add(max).scale(0.5);
        }

        double x = between(random, min.x, max.x);
        double y = between(random, min.y, max.y);
        double z = between(random, min.z, max.z);

        double pick = random.nextDouble() * totalArea;

        if (pick < areaX) {
            x = random.nextBoolean() ? min.x : max.x;
        } else if (pick < areaX + areaY) {
            y = random.nextBoolean() ? min.y : max.y;
        } else {
            z = random.nextBoolean() ? min.z : max.z;
        }

        return new Vec3(x, y, z);
    }


    private static ModelElement pickElement(CachedModelShape shape, RandomSource random) {
        double pick = random.nextDouble() * shape.totalSurfaceArea();
        double passed = 0.0;

        for (ModelElement element : shape.elements()) {
            passed += element.surfaceArea();

            if (pick <= passed) {
                return element;
            }
        }

        return shape.elements().getLast();
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static CachedModelShape loadShape(ResourceLocation modelId) {
        List<ModelElement> elements = new ArrayList<>();
        loadJsonModelElements(modelId, elements, 0);

        double totalSurfaceArea = 0.0;
        for (ModelElement element : elements) {
            totalSurfaceArea += element.surfaceArea();
        }

        return new CachedModelShape(List.copyOf(elements), totalSurfaceArea);
    }

    private static void loadJsonModelElements(ResourceLocation modelId, List<ModelElement> elements, int depth) {
        if(depth > 8) return;

        ResourceLocation modelResource = ResourceLocation.fromNamespaceAndPath(modelId.getNamespace(), "models/" + modelId.getPath() + ".json");

        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(modelResource);

        if(resource.isEmpty()) {
            return;
        }

        try(Reader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)){
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            if(json.has("parent")){
                ResourceLocation parent = ResourceLocation.parse(json.get("parent").getAsString());
                loadJsonModelElements(parent, elements, depth + 1);
            }

            if (!json.has("elements") || !json.get("elements").isJsonArray()) {
                return;
            }

            JsonArray rawElements = json.getAsJsonArray("elements");

            for(JsonElement rawElement : rawElements) {
                if(!rawElement.isJsonObject()) {
                    continue;
                }

                ModelElement element = parseElement(rawElement.getAsJsonObject());

                if (element.surfaceArea() > 1e-8) {
                    elements.add(element);
                }
            }
        }
        catch (Exception ignored){
        }
    }

    private static ModelElement parseElement(JsonObject json) {
        Vec3 from = readVec3(json.getAsJsonArray("from")).scale(1.0 / 16.0);
        Vec3 to = readVec3(json.getAsJsonArray("to")).scale(1.0 / 16.0);

        Vec3 min = new Vec3(
                Math.min(from.x, to.x),
                Math.min(from.y, to.y),
                Math.min(from.z, to.z)
        );

        Vec3 max = new Vec3(
                Math.max(from.x, to.x),
                Math.max(from.y, to.y),
                Math.max(from.z, to.z)
        );

        ModelElementRotation rotation = ModelElementRotation.none();
        if (json.has("rotation") && json.get("rotation").isJsonObject()) {
            JsonObject rotationJson = json.getAsJsonObject("rotation");

            Vec3 origin = rotationJson.has("origin")
                    ? readVec3(json.getAsJsonArray("origin")).scale(1.0 / 16.0)
                    : new Vec3(0.5, 0.5, 0.5);


            String axis = rotationJson.has("axis")
                    ? json.get("axis").getAsString()
                    : "y";

            double angle = rotationJson.has("angle")
                    ? json.get("angle").getAsDouble()
                    : 0.0;

            rotation = new ModelElementRotation(origin, axis, Math.toRadians(angle));
        }

        return ModelElement.of(min, max, rotation);
    }

    private static Vec3 readVec3(JsonArray array) {
        if (array == null || array.size() != 3) {
            return Vec3.ZERO;
        }

        return new Vec3(
                array.get(0).getAsDouble(),
                array.get(1).getAsDouble(),
                array.get(2).getAsDouble()
        );
    }

    private record CachedModelShape(List<ModelElement> elements, double totalSurfaceArea) {}
    private record ModelElement(Vec3 min, Vec3 max, ModelElementRotation rotation, double surfaceArea) {
        private static ModelElement of(Vec3 min, Vec3 max, ModelElementRotation rotation) {
            double sx = Math.max(0.0, max.x - min.x);
            double sy = Math.max(0.0, max.y - min.y);
            double sz = Math.max(0.0, max.z - min.z);

            double surfaceArea = 2.0 * (sx * sy + sx * sz + sy * sz);
            return new ModelElement(min, max, rotation, surfaceArea);
        }
    }
    private record ModelElementRotation(Vec3 origin, String axis, double radians) {
        private static ModelElementRotation none() {
            return new ModelElementRotation(Vec3.ZERO, "y", 0.0);
        }

        private Vec3 apply(Vec3 point) {
            if (Math.abs(radians) < 1e-8) {
                return point;
            }

            Vec3 local = point.subtract(origin);

            double sin = Math.sin(radians);
            double cos = Math.cos(radians);

            Vec3 rotated = switch (axis) {
                case "x" -> new Vec3(
                        local.x,
                        local.y * cos - local.z * sin,
                        local.y * sin + local.z * cos
                );
                case "z" -> new Vec3(
                        local.x * cos - local.y * sin,
                        local.x * sin + local.y * cos,
                        local.z
                );
                default -> new Vec3(
                        local.x * cos + local.z * sin,
                        local.y,
                        -local.x * sin + local.z * cos
                );
            };

            return origin.add(rotated);
        }
    }
}
