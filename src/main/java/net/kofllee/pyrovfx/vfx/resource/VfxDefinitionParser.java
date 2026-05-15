package net.kofllee.pyrovfx.vfx.resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kofllee.pyrovfx.vfx.definition.*;
import net.kofllee.pyrovfx.vfx.expression.*;
import net.kofllee.pyrovfx.vfx.type.*;
import net.kofllee.pyrovfx.vfx.value.VfxColor;
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
                : VfxLifetimeDefinition.none();

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

        VfxNumberExpression delayTicks = getNumberExpression(json, "delay_ticks", 0.0, VfxEvaluationMode.EFFECT_START);
        VfxNumberExpression activeTicks = getNumberExpression(json, "active_ticks", 1.0, VfxEvaluationMode.EFFECT_START);
        VfxNumberExpression sleepTicks = getNumberExpression(json, "sleep_ticks", 0.0, VfxEvaluationMode.EFFECT_START);

        VfxNumberExpression loops = getNumberExpression(json, "loops", 1.0, VfxEvaluationMode.EFFECT_START);

        return switch (mode) {
            case ONCE -> VfxLifetimeDefinition.once(delayTicks, activeTicks);
            case LOOPING -> VfxLifetimeDefinition.looping(delayTicks, activeTicks, sleepTicks, loops);
        };
    }

    private static VfxEmitterDefinition parseEmitter(JsonObject json) {

        VfxEmitterLifetimeDefinition emitterLifetime = json.has("emitter_lifetime")
                ? parseEmitterLifetime(getObject(json, "emitter_lifetime"))
                : VfxEmitterLifetimeDefinition.none();
        VfxSpawnAmountDefinition spawnAmount = json.has("spawn_amount")
                ? parseSpawnAmount(getObject(json, "spawn_amount"))
                : VfxSpawnAmountDefinition.defaultInstant();

        VfxSpawnShapeDefinition spawnShape = parseSpawnShape(getObject(json, "spawn_shape"));

        VfxParticleLifetimeDefinition particleLifetime = json.has("particle_lifetime")
                ? parseParticleLifetime(getObject(json, "particle_lifetime"))
                : VfxParticleLifetimeDefinition.defaultLifetime();

        VfxMotionDefinition motion = json.has("motion")
                ? parseMotion(getObject(json, "motion"))
                : VfxMotionDefinition.none();

        VfxRenderDefinition render = parseRender(getObject(json, "render"));

        VfxRotationDefinition rotation = json.has("rotation")
                ? parseRotation(getObject(json, "rotation"))
                : VfxRotationDefinition.none();

        return new VfxEmitterDefinition(emitterLifetime, spawnAmount, spawnShape, particleLifetime, motion, rotation, render);
    }

    private static VfxRotationDefinition parseRotation(JsonObject json) {
        VfxRotationMode mode = parseEnum(
                VfxRotationMode.class,
                getString(json, "mode", "none"),
                "rotation mode"
        );

        return switch (mode) {
            case NONE -> VfxRotationDefinition.none();
            case DYNAMIC -> VfxRotationDefinition.dynamic(
                    parseDynamicRotation(getObject(json, "dynamic"))
            );
            case PARAMETRIC -> VfxRotationDefinition.parametric(
                    parseParametricRotation(getObject(json, "parametric"))
            );
        };
    }

    private static VfxDynamicRotationDefinition parseDynamicRotation(JsonObject json) {
        return VfxDynamicRotationDefinition.of(
                getVec3Expression(json, "start_rotation", VfxVec3.ZERO, VfxEvaluationMode.PARTICLE_SPAWN),
                getVec3Expression(json, "angular_velocity", VfxVec3.ZERO, VfxEvaluationMode.PARTICLE_SPAWN),
                getVec3Expression(json, "angular_acceleration", VfxVec3.ZERO, VfxEvaluationMode.PARTICLE_TICK),
                getNumberExpression(json, "angular_drag", 0.0, VfxEvaluationMode.PARTICLE_TICK)
        );
    }

    private static VfxParametricRotationDefinition parseParametricRotation(JsonObject json) {
        return VfxParametricRotationDefinition.of(
                getVec3Expression(json, "rotation", VfxVec3.ZERO, VfxEvaluationMode.PARTICLE_TICK)
        );
    }


    private static VfxParticleLifetimeDefinition parseParticleLifetime(JsonObject json) {
        return VfxParticleLifetimeDefinition.of(
                getNumberExpression(json, "max_age_ticks", 20.0, VfxEvaluationMode.PARTICLE_SPAWN)
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
                    getNumberExpression(json, "amount", 1.0, VfxEvaluationMode.EMITTER_START)
            );
            case STEADY -> VfxSpawnAmountDefinition.steady(
                    getNumberExpression(json, "rate", 20.0, VfxEvaluationMode.EMITTER_TICK),
                    getNumberExpression(json, "max_particles", 256.0, VfxEvaluationMode.EMITTER_START)
            );
            case MANUAL -> VfxSpawnAmountDefinition.manual(
                    getNumberExpression(json, "amount", 1.0, VfxEvaluationMode.EMITTER_START)
            );
        };
    }

    private static VfxEmitterLifetimeDefinition parseEmitterLifetime(JsonObject json) {
        VfxEmitterLifetimeMode mode = parseEnum(
                VfxEmitterLifetimeMode.class,
                getString(json, "mode", "once"),
                "emitter lifetime mode"
        );
        VfxNumberExpression delayTicks = getNumberExpression(json, "delay_ticks", 0.0, VfxEvaluationMode.EMITTER_START);
        VfxNumberExpression activeTicks = getNumberExpression(json, "active_ticks", 1.0, VfxEvaluationMode.EMITTER_START);
        VfxNumberExpression sleepTicks = getNumberExpression(json, "sleep_ticks", 0.0, VfxEvaluationMode.EMITTER_START);

        VfxNumberExpression loops = getNumberExpression(json, "loops", 1.0, VfxEvaluationMode.EMITTER_START);

        return switch(mode) {
            case ONCE -> VfxEmitterLifetimeDefinition.once(delayTicks, activeTicks);
            case LOOPING -> VfxEmitterLifetimeDefinition.looping(delayTicks, activeTicks, sleepTicks, loops);
            case MANUAL -> VfxEmitterLifetimeDefinition.manual();
        };
    }

    private static VfxMotionDefinition parseMotion(JsonObject json) {
        VfxMotionMode mode = parseEnum(
                VfxMotionMode.class,
                getString(json, "mode", "static"),
                "motion mode"
        );

        VfxMotionCollisionDefinition collision = json.has("collision")
                ? parseMotionCollision(getObject(json, "collision"))
                : VfxMotionCollisionDefinition.none();

        return switch (mode) {
            case STATIC -> VfxMotionDefinition.statik(collision);
            case DYNAMIC -> VfxMotionDefinition.dynamic(
                    parseDynamicMotion(getObject(json, "dynamic")),
                    collision
            );
            case PARAMETRIC -> VfxMotionDefinition.parametric(
                    parseParametricMotion(getObject(json, "parametric")),
                    collision
            );
        };
    }

    private static VfxParametricMotionDefinition parseParametricMotion(JsonObject json) {
        return VfxParametricMotionDefinition.of(
                getVec3Expression(json, "offset", VfxVec3.ZERO, VfxEvaluationMode.PARTICLE_TICK),
                getVec3Expression(json, "direction", new VfxVec3(0.0, 1.0, 0.0), VfxEvaluationMode.PARTICLE_TICK)
        );
    }

    private static VfxDynamicMotionDefinition parseDynamicMotion(JsonObject json) {
        VfxDirectionMode direction = parseEnum(
                VfxDirectionMode.class,
                getString(json, "direction", "custom"),
                "motion direction"
        );

        return VfxDynamicMotionDefinition.of(
                direction,
                getVec3Expression(json, "custom_direction", new VfxVec3(0.0, 1.0, 0.0), VfxEvaluationMode.PARTICLE_SPAWN),
                getNumberExpression(json, "speed", 0.0, VfxEvaluationMode.PARTICLE_SPAWN),
                getVec3Expression(json, "acceleration", VfxVec3.ZERO, VfxEvaluationMode.PARTICLE_TICK),
                getNumberExpression(json, "linear_drag", 0.0, VfxEvaluationMode.PARTICLE_TICK)
        );
    }

    private static VfxMotionCollisionDefinition parseMotionCollision(JsonObject json) {
        VfxCollisionType collisionType = parseEnum(
                VfxCollisionType.class,
                getString(json, "collision_type", "sphere"),
                "collision type"
        );

        VfxVec3 defaultSize = new VfxVec3(0.05, 0.05, 0.05);

        return VfxMotionCollisionDefinition.of(
                getBoolean(json, "collide", false),
                collisionType,
                getVec3Expression(json, "collision_size", defaultSize, VfxEvaluationMode.PARTICLE_SPAWN),
                getNumberExpression(json, "collision_drag", 0.0, VfxEvaluationMode.PARTICLE_TICK),
                getNumberExpression(json, "bounciness", 0.0, VfxEvaluationMode.PARTICLE_TICK),
                getBoolean(json, "expire_on_contact", false),
                json.has("events")
                        ? parseEvents(getObject(json, "events"))
                        : VfxEventsDefinition.empty()
        );
    }

    private static VfxEventsDefinition parseEvents(JsonObject json) {
        return VfxEventsDefinition.empty();
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
            case SPRITE -> VfxRenderDefinition.sprite(
                    parseSpriteRender(getObject(json, "sprite")),
                    parseParticleAppearance(json)
            );
            case MODEL -> throw new IllegalArgumentException("Model render is not implemented yet");
        };
    }

    private static VfxParticleAppearanceDefinition parseParticleAppearance(JsonObject json) {
        return new VfxParticleAppearanceDefinition(
                getColorExpression(
                        json,
                        "color",
                        new VfxColor(1.0, 1.0, 1.0, 1.0),
                        VfxEvaluationMode.PARTICLE_TICK
                ),
                getNumberExpression(
                        json,
                        "alpha",
                        1.0,
                        VfxEvaluationMode.PARTICLE_TICK
                ),
                getNumberExpression(
                        json,
                        "size",
                        1.0,
                        VfxEvaluationMode.PARTICLE_TICK
                )
        );
    }

    private static VfxSpriteRenderDefinition parseSpriteRender(JsonObject json) {
        return new VfxSpriteRenderDefinition(
                ResourceLocation.parse(getString(json, "texture", "minecraft:textures/particle/generic_0.png"))
        );
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

        VfxVec3Expression offset = getVec3Expression(
                json,
                "offset",
                VfxVec3.ZERO,
                VfxEvaluationMode.EMITTER_TICK
        );
        VfxNumberExpression edgeThickness = getNumberExpression(
                json,
                "edge_thickness",
                0.0,
                VfxEvaluationMode.EMITTER_TICK
        );

        return switch (type) {
            case POINT -> VfxSpawnShapeDefinition.point(offset);
            case SPHERE -> VfxSpawnShapeDefinition.sphere(
                    offset,
                    getNumberExpression(json, "radius", 0.25, VfxEvaluationMode.EMITTER_TICK),
                    edgeThickness
            );
            case BOX -> VfxSpawnShapeDefinition.box(
                    offset,
                    getVec3Expression(json, "half_extents", new VfxVec3(0.25, 0.25, 0.25), VfxEvaluationMode.EMITTER_TICK),
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

    private static VfxNumberExpression getNumberExpression(
            JsonObject json,
            String key,
            double fallback,
            VfxEvaluationMode evaluationMode
    ) {
        if (!json.has(key)) {
            return VfxNumberExpression.constant(fallback, evaluationMode);
        }

        JsonElement element = json.get(key);

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return VfxNumberExpression.constant(element.getAsDouble(), evaluationMode);
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return VfxNumberExpression.expression(element.getAsString(), evaluationMode);
        }

        throw new IllegalArgumentException("Expected number or expression string field: " + key);
    }

    private static VfxVec3Expression getVec3Expression(
            JsonObject json,
            String key,
            VfxVec3 fallback,
            VfxEvaluationMode evaluationMode
    ) {
        if (!json.has(key)) {
            return VfxVec3Expression.constant(fallback, evaluationMode);
        }

        JsonElement element = json.get(key);

        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("Expected vec3 expression array field: " + key);
        }

        JsonArray array = element.getAsJsonArray();

        if (array.size() != 3) {
            throw new IllegalArgumentException("Expected vec3 expression array with 3 values: " + key);
        }

        return new VfxVec3Expression(
                parseNumberExpressionElement(array.get(0), evaluationMode),
                parseNumberExpressionElement(array.get(1), evaluationMode),
                parseNumberExpressionElement(array.get(2), evaluationMode),
                evaluationMode
        );
    }

    private static VfxNumberExpression parseNumberExpressionElement(
            JsonElement element,
            VfxEvaluationMode evaluationMode
    ) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return VfxNumberExpression.constant(element.getAsDouble(), evaluationMode);
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return VfxNumberExpression.expression(element.getAsString(), evaluationMode);
        }

        throw new IllegalArgumentException("Expected number or expression string");
    }

    private static VfxColorExpression getColorExpression(
            JsonObject json,
            String key,
            VfxColor fallback,
            VfxEvaluationMode evaluationMode
    ) {
        if (!json.has(key)) {
            return constantColorExpression(fallback, evaluationMode);
        }

        JsonElement element = json.get(key);

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString();

            if (raw.startsWith("#")) {
                return constantColorExpression(parseHexColor(raw), evaluationMode);
            }

            throw new IllegalArgumentException("Color string must be hex color: " + raw);
        }

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();

            if (array.size() != 4) {
                throw new IllegalArgumentException("Expected color array with 4 values: " + key);
            }

            return new VfxColorExpression(
                    parseNumberExpressionElement(array.get(0), evaluationMode),
                    parseNumberExpressionElement(array.get(1), evaluationMode),
                    parseNumberExpressionElement(array.get(2), evaluationMode),
                    parseNumberExpressionElement(array.get(3), evaluationMode),
                    evaluationMode
            );
        }

        throw new IllegalArgumentException("Expected hex color or color expression array field: " + key);
    }

    private static VfxColorExpression constantColorExpression(VfxColor color, VfxEvaluationMode evaluationMode) {
        return new VfxColorExpression(
                VfxNumberExpression.constant(color.r(), evaluationMode),
                VfxNumberExpression.constant(color.g(), evaluationMode),
                VfxNumberExpression.constant(color.b(), evaluationMode),
                VfxNumberExpression.constant(color.a(), evaluationMode),
                evaluationMode
        );
    }

    private static VfxColor parseHexColor(String raw) {
        String hex = raw.substring(1);

        if (hex.length() != 6 && hex.length() != 8) {
            throw new IllegalArgumentException("Expected #RRGGBB or #RRGGBBAA color: " + raw);
        }

        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        int a = hex.length() == 8 ? Integer.parseInt(hex.substring(6, 8), 16) : 255;

        return VfxColor.rgba(
                r / 255.0,
                g / 255.0,
                b / 255.0,
                a / 255.0
        );
    }

}
