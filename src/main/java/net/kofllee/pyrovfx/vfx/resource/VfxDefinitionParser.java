package net.kofllee.pyrovfx.vfx.resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kofllee.pyrovfx.vfx.*;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VfxDefinitionParser {
    private VfxDefinitionParser() {}

    public static VfxDefinition parse(ResourceLocation location, JsonObject json) {
        int lifetimeTicks = getInt(json, "lifetime_ticks", 20);

        List<VfxEmitterDefinition> emitters = new ArrayList<>();
        JsonArray array = getArray(json, "emitters");

        for(JsonElement element : array) {
            if(!element.isJsonObject()) {
                throw new IllegalArgumentException("Emitter must be an object");
            }

            emitters.add(parseEmitter(element.getAsJsonObject()));
        }

        return new VfxDefinition(location, lifetimeTicks, emitters);
    }

    private static VfxEmitterDefinition parseEmitter(JsonObject json) {
        VfxEmitterShapeDefinition shape = parseShape(getObject(json, "shape"));
        VfxEmitterMode mode = parseEnum(
                VfxEmitterMode.class,
                getString(json, "mode", "burst"),
                "emitter mode"
        );

        int count = getInt(json, "count", 1);
        VfxParticleDefinition particle = parseParticle(getObject(json, "particle"));

        return new VfxEmitterDefinition(shape, mode, count, particle);
    }

    private static VfxParticleDefinition parseParticle(JsonObject json) {
        VfxAppearanceDefinition appearance = parseAppearance(getObject(json, "appearance"));
        VfxMotionDefinition motion = json.has("motion")
                ? parseMotion(getObject(json, "motion"))
                : VfxMotionDefinition.none();

        return new VfxParticleDefinition(appearance, motion);
    }

    private static VfxMotionDefinition parseMotion(JsonObject json) {
        VfxVelocityDefinition velocity = json.has("velocity")
                ? parseVelocity(getObject(json, "velocity"))
                : VfxVelocityDefinition.none();

        VfxVec3 acceleration = getVec3(json, "acceleration", VfxVec3.ZERO);
        double gravity = getDouble(json, "gravity", 0.0);
        double drag = getDouble(json, "drag", 0.0);

        return new VfxMotionDefinition(velocity, acceleration, gravity, drag);
    }

    private static VfxVelocityDefinition parseVelocity(JsonObject json) {
        VfxVelocityMode mode = parseEnum(
                VfxVelocityMode.class,
                getString(json, "mode", "none"),
                "velocity mode"
        );

        double speed = getDouble(json, "speed", 0.0);
        double speedRandom = getDouble(json, "speed_random", 0.0);

        return switch (mode) {
            case NONE -> VfxVelocityDefinition.none();
            case CONSTANT -> VfxVelocityDefinition.constant(
                    getVec3(json, "direction", new VfxVec3(0.0, 1.0, 0.0)),
                    speed,
                    speedRandom
            );
            case RANDOM -> VfxVelocityDefinition.random(speed, speedRandom);
            case SPHERICAL -> VfxVelocityDefinition.spherical(speed, speedRandom);
        };
    }

    private static VfxAppearanceDefinition parseAppearance(JsonObject json) {
        VfxParticleRenderType renderType = parseEnum(
                VfxParticleRenderType.class,
                getString(json, "render_type", "minecraft_particle"),
                "particle render type"
        );

        ResourceLocation minecraftParticleId = ResourceLocation.parse(
                getString(json, "minecraft_particle", "minecraft:smoke")
        );

        return new VfxAppearanceDefinition(renderType, minecraftParticleId);
    }

    private static VfxEmitterShapeDefinition parseShape(JsonObject json) {
        VfxEmitterShape shape = parseEnum(VfxEmitterShape.class, getString(json, "type", "point"), "emitter shape");

        VfxVec3 offset = getVec3(json, "offset", VfxVec3.ZERO);
        boolean surfaceOnly = getBoolean(json, "surface_only", false);

        return switch (shape){
            case POINT -> VfxEmitterShapeDefinition.point(offset);
            case SPHERE -> VfxEmitterShapeDefinition.sphere(offset, getDouble(json, "radius", 0.25), surfaceOnly);
            case BOX -> VfxEmitterShapeDefinition.box(offset, getVec3(json, "half_extents", new VfxVec3(0.25, 0.25, 0.25)), surfaceOnly);
        };
    }



    private static <T extends Enum<T>> T parseEnum(Class<T> enumClass, String rawValue, String fieldName) {
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);

        try {
            return Enum.valueOf(enumClass, normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + fieldName + ": " + rawValue);
        }
    }

    private static VfxVec3 getVec3(JsonObject json, String key, VfxVec3 fallback) {
        if (!json.has(key)) {
            return fallback;
        }

        JsonElement element = json.get(key);

        if(!element.isJsonArray()) {
            throw new IllegalArgumentException("Expected vec3 array field: " + key);
        }

        JsonArray array = element.getAsJsonArray();

        if(array.size() != 3) {
            throw new IllegalArgumentException("Expected vec3 array with 3 numbers: " + key);
        }

        return new VfxVec3(
                array.get(0).getAsDouble(),
                array.get(1).getAsDouble(),
                array.get(2).getAsDouble()
        );
    }

    private static double getDouble(JsonObject json, String key, double fallback) {
        if (!json.has(key)) {
            return fallback;
        }

        return json.get(key).getAsDouble();
    }

    private static boolean getBoolean(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) {
            return fallback;
        }

        return json.get(key).getAsBoolean();
    }


    private static JsonObject getObject(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonObject()) {
            throw new IllegalArgumentException("Expected object field: " + key);
        }

        return json.getAsJsonObject(key);
    }

    private static String getString(JsonObject json, String key, String fallback) {
        if (!json.has(key)) {
            return fallback;
        }

        return json.get(key).getAsString();
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        if (!json.has(key)) {
            return fallback;
        }

        return json.get(key).getAsInt();
    }

    private static JsonArray getArray(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            throw new IllegalArgumentException("Expected array field: " + key);
        }

        return json.getAsJsonArray(key);
    }
}
