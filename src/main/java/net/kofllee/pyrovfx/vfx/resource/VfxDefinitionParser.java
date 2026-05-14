package net.kofllee.pyrovfx.vfx.resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kofllee.pyrovfx.vfx.definition.*;
import net.kofllee.pyrovfx.vfx.type.*;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VfxDefinitionParser {
    private VfxDefinitionParser() {}

    public static VfxDefinition parse(ResourceLocation location, JsonObject json) {
        String format = getRequiredString(json, "format");

        if (!format.equals("pyro_vfx:1")) {
            throw new IllegalArgumentException("Unsupported VFX format: " + format);
        }

        VfxMetadataDefinition metadata = json.has("metadata")
                ? parseMetadata(getObject(json, "metadata"))
                : VfxMetadataDefinition.empty();

        VfxLifetimeDefinition lifetime = json.has("lifetime")
                ? parseLifetime(getObject(json, "lifetime"))
                : VfxLifetimeDefinition.once(0, 20);

        List<VfxEmitterDefinition> emitters = new ArrayList<>();
        JsonArray array = getArray(json, "emitters");

        for(JsonElement element : array) {
            if(!element.isJsonObject()) {
                throw new IllegalArgumentException("Emitter must be an object");
            }

            emitters.add(parseEmitter(element.getAsJsonObject()));
        }

        return new VfxDefinition(location, format, metadata, lifetime, emitters);
    }

    private static VfxMetadataDefinition parseMetadata(JsonObject json) {
        return new VfxMetadataDefinition(
                getString(json, "author", ""),
                getString(json, "description", "")
        );
    }

    private static VfxLifetimeDefinition parseLifetime(JsonObject json) {
        VfxLifetimeMode mode = parseEnum(
                VfxLifetimeMode.class,
                getString(json, "mode", "once"),
                "effect lifetime mode"
        );

        int delayTicks = getInt(json, "delay_ticks", 0);
        int activeTicks = getInt(json, "active_ticks", 20);
        int sleepTicks = getInt(json, "sleep_ticks", 0);
        int loops = getInt(json, "loops", 1);

        return switch (mode) {
            case ONCE -> VfxLifetimeDefinition.once(delayTicks, activeTicks);
            case LOOPING -> VfxLifetimeDefinition.looping(delayTicks, activeTicks, sleepTicks, loops);
        };
    }

    private static VfxEmitterDefinition parseEmitter(JsonObject json) {

        VfxEmitterTimingDefinition timing = json.has("timing")
                ? parseTiming(getObject(json, "timing"))
                : VfxEmitterTimingDefinition.once(0, 1);
        VfxSpawnAmountDefinition spawnAmount = json.has("spawn_amount")
                ? parseSpawnAmount(getObject(json, "spawn_amount"))
                : VfxSpawnAmountDefinition.instant(1);

        VfxEmitterShapeDefinition shape = parseShape(getObject(json, "shape"));
        VfxParticleDefinition particle = parseParticle(getObject(json, "particle"));

        return new VfxEmitterDefinition(timing, spawnAmount, shape, particle);
    }

    private static VfxSpawnAmountDefinition parseSpawnAmount(JsonObject json) {
        VfxSpawnAmountMode mode = parseEnum(
                VfxSpawnAmountMode.class,
                getString(json, "mode", "instant"),
                "spawn amount mode"
        );

        return switch (mode) {
            case INSTANT -> VfxSpawnAmountDefinition.instant(
                    getInt(json, "amount", 1)
            );
            case STEADY -> VfxSpawnAmountDefinition.steady(
                    getFloat(json, "rate", 20.0F),
                    getInt(json, "max_particles", 256)
            );
            case MANUAL -> VfxSpawnAmountDefinition.manual(
                    getInt(json, "amount", 1)
            );
        };
    }

    private static VfxEmitterTimingDefinition parseTiming(JsonObject json) {
        VfxEmitterTimingType type = parseEnum(
                VfxEmitterTimingType.class,
                getString(json, "type", "once"),
                "emitter timing type"
        );
        int delayTicks = getInt(json, "delay_ticks", 0);
        int activeTicks = getInt(json, "active_ticks", 1);
        int sleepTicks = getInt(json, "sleep_ticks", 0);

        return switch(type) {
            case ONCE -> VfxEmitterTimingDefinition.once(delayTicks, activeTicks);
            case LOOPING -> VfxEmitterTimingDefinition.loop(delayTicks, activeTicks, sleepTicks);
        };
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
        double edgeThickness = getDouble(json, "edge_thickness", 1.0);

        return switch (shape){
            case POINT -> VfxEmitterShapeDefinition.point(offset);
            case SPHERE -> VfxEmitterShapeDefinition.sphere(offset, getDouble(json, "radius", 0.25), edgeThickness);
            case BOX -> VfxEmitterShapeDefinition.box(offset, getVec3(json, "half_extents", new VfxVec3(0.25, 0.25, 0.25)), edgeThickness);
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

    private static float getFloat(JsonObject json, String key, float fallback) {
        if (!json.has(key)) {
            return fallback;
        }

        return json.get(key).getAsFloat();
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

    private static String getRequiredString(JsonObject json, String key) {
        if (!json.has(key)) {
            throw new IllegalArgumentException("Missing required string field: " + key);
        }

        if (!json.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("Expected string field: " + key);
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
