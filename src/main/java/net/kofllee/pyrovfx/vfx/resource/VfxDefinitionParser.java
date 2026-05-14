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

        VfxEmitterLifetimeDefinition emitterLifetime = json.has("emitter_lifetime")
                ? parseEmitterLifetime(getObject(json, "emitter_lifetime"))
                : VfxEmitterLifetimeDefinition.once(0, 1);
        VfxSpawnAmountDefinition spawnAmount = json.has("spawn_amount")
                ? parseSpawnAmount(getObject(json, "spawn_amount"))
                : VfxSpawnAmountDefinition.instant(1);

        VfxSpawnShapeDefinition spawnShape = parseSpawnShape(getObject(json, "spawn_shape"));

        VfxParticleLifetimeDefinition particleLifetime = json.has("particle_lifetime")
                ? parseParticleLifetime(getObject(json, "particle_lifetime"))
                : VfxParticleLifetimeDefinition.defaultLifetime();

        VfxMotionDefinition motion = json.has("motion")
                ? parseMotion(getObject(json, "motion"))
                : VfxMotionDefinition.none();

        VfxRenderDefinition render = parseRender(getObject(json, "render"));

        return new VfxEmitterDefinition(emitterLifetime, spawnAmount, spawnShape, particleLifetime, motion, render);
    }

    private static VfxParticleLifetimeDefinition parseParticleLifetime(JsonObject json) {
        return VfxParticleLifetimeDefinition.of(
                getInt(json, "max_age_ticks", 20)
        );
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

    private static VfxEmitterLifetimeDefinition parseEmitterLifetime(JsonObject json) {
        VfxEmitterLifetimeMode mode = parseEnum(
                VfxEmitterLifetimeMode.class,
                getString(json, "mode", "once"),
                "emitter lifetime mode"
        );
        int delayTicks = getInt(json, "delay_ticks", 0);
        int activeTicks = getInt(json, "active_ticks", 1);
        int sleepTicks = getInt(json, "sleep_ticks", 0);
        int loops = getInt(json, "loops", 1);

        return switch(mode) {
            case ONCE -> VfxEmitterLifetimeDefinition.once(delayTicks, activeTicks);
            case LOOPING -> VfxEmitterLifetimeDefinition.looping(delayTicks, activeTicks, sleepTicks, loops);
            case MANUAL -> VfxEmitterLifetimeDefinition.manual();
        };
    }

    private static VfxMotionDefinition parseMotion(JsonObject json) {
        VfxVelocityDefinition velocity = json.has("velocity")
                ? parseVelocity(getObject(json, "velocity"))
                : VfxVelocityDefinition.none();

        VfxVec3 acceleration = getVec3(json, "acceleration", VfxVec3.ZERO);
        double gravity = getDouble(json, "gravity", 0.0);
        double drag = getDouble(json, "drag", 0.0);

        VfxMotionCollisionDefinition collision = json.has("collision")
                ? parseMotionCollision(getObject(json, "collision"))
                : VfxMotionCollisionDefinition.none();

        return new VfxMotionDefinition(velocity, acceleration, gravity, drag, collision);
    }

    private static VfxMotionCollisionDefinition parseMotionCollision(JsonObject json) {
        boolean collide = getBoolean(json, "collide", false);

        VfxCollisionType collisionType = parseEnum(
                VfxCollisionType.class,
                getString(json, "collision_type", "sphere"),
                "collision type"
        );

        VfxVec3 defaultSize = collisionType == VfxCollisionType.SPHERE
                ? new VfxVec3(0.05, 0.05, 0.05)
                : new VfxVec3(0.05, 0.05, 0.05);

        return VfxMotionCollisionDefinition.of(
                collide,
                collisionType,
                getVec3(json, "collision_size", defaultSize),
                getDouble(json, "collision_drag", 0.0),
                getDouble(json, "bounciness", 0.0),
                getBoolean(json, "expire_on_contact", false),
                json.has("events")
                        ? parseEvents(getObject(json, "events"))
                        : VfxEventsDefinition.empty()
        );
    }

    private static VfxEventsDefinition parseEvents(JsonObject json) {
        return VfxEventsDefinition.empty();
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

    private static VfxRenderDefinition parseRender(JsonObject json) {
        VfxRenderType type = parseEnum(
                VfxRenderType.class,
                getString(json, "type", "minecraft_particle"),
                "render type"
        );


        return switch (type) {
            case MINECRAFT_PARTICLE -> VfxRenderDefinition.minecraftParticle(
                    parseMinecraftParticleRender(getObject(json, "minecraft_particle"))
            );
            case SPRITE -> throw new IllegalArgumentException("Sprite render is not implemented yet");
            case MODEL -> throw new IllegalArgumentException("Model render is not implemented yet");
        };
    }

    private static VfxMinecraftParticleRenderDefinition parseMinecraftParticleRender(JsonObject json) {
        return new VfxMinecraftParticleRenderDefinition(
                ResourceLocation.parse(getString(json, "particle", "minecraft:smoke"))
        );
    }

    private static VfxSpawnShapeDefinition parseSpawnShape(JsonObject json) {
        VfxSpawnShapeType type = parseEnum(
                VfxSpawnShapeType.class,
                getString(json, "type", "point"),
                "spawn shape type"
        );

        VfxVec3 offset = getVec3(json, "offset", VfxVec3.ZERO);
        double edgeThickness = getDouble(json, "edge_thickness", 0.0);

        return switch (type) {
            case POINT -> VfxSpawnShapeDefinition.point(offset);
            case SPHERE -> VfxSpawnShapeDefinition.sphere(
                    offset,
                    getDouble(json, "radius", 0.25),
                    edgeThickness
            );
            case BOX -> VfxSpawnShapeDefinition.box(
                    offset,
                    getVec3(json, "half_extents", new VfxVec3(0.25, 0.25, 0.25)),
                    edgeThickness
            );
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
