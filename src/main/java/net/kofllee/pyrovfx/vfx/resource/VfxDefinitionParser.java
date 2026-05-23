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

import java.util.*;

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

        Map<String, VfxParameterDefinition> parameters = json.has("parameters")
                ? parseParameters(getObject(json, "parameters"))
                : Map.of();

        List<VfxEmitterDefinition> emitters = new ArrayList<>();
        JsonArray array = getArray(json, "emitters");

        for(int i = 0; i < array.size(); ++i) {
            JsonElement emitter = array.get(i);

            if(!emitter.isJsonObject()) {
                throw new IllegalArgumentException("Emitter must be an object");
            }

            emitters.add(parseEmitter(emitter.getAsJsonObject(), i));
        }

        Map<String, VfxEventDefinition> events = json.has("events")
                ? parseEventsMap(getObject(json, "events"))
                : Map.of();

        List<VfxTriggerDefinition> triggers = json.has("triggers")
                ? parseTriggers(getArray(json, "triggers"))
                : List.of();

        validateEventReferences(events, triggers);
        validateEmitterIds(emitters);
        validateEventEmitterReferences(events, emitters);
        validateScopedTriggerReferences(triggers, events, "effect");
        validateEmitterTriggerReferences(emitters, events);
        validateEventParameterReferences(events, parameters);

        return new VfxDefinition(location, format, metadata, lifetime, parameters, emitters, events, triggers);
    }

    private static void validateEventParameterReferences(Map<String, VfxEventDefinition> events, Map<String, VfxParameterDefinition> parameters) {
        for(Map.Entry<String, VfxEventDefinition> entry : events.entrySet()) {
            VfxEventDefinition event = entry.getValue();

            if (event.type() == VfxEventType.SET_PARAM
                    && !parameters.containsKey(event.parameterId())) {
                throw new IllegalArgumentException("Event parameter '" + entry.getKey() + "' does not exist");
            }
        }
    }

    private static Map<String, VfxParameterDefinition> parseParameters(JsonObject json) {
        Map<String, VfxParameterDefinition> parameters = new HashMap<>();

        for(Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String id = entry.getKey();

            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Parameter id must not be empty");
            }

            if (parameters.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate parameter id: " + id);
            }

            JsonElement value = entry.getValue();

            VfxNumberExpression expression = null;

            if (value.isJsonPrimitive()) {
                if (value.getAsJsonPrimitive().isNumber()) {
                    expression = VfxNumberExpression.constant(value.getAsDouble());
                } else if (value.getAsJsonPrimitive().isString()) {
                    expression = VfxNumberExpression.expression(value.getAsString());
                } else {
                    throw new IllegalArgumentException("Parameter must be number or expression string: " + id);
                }
            }

            parameters.put(id, new VfxParameterDefinition(id, expression));
        }

        return Map.copyOf(parameters);
    }

    private static List<VfxTriggerDefinition> parseTriggers(JsonArray array) {
        List<VfxTriggerDefinition> triggers = new ArrayList<>();

        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Trigger must be an object");
            }

            triggers.add(parseTrigger(element.getAsJsonObject()));
        }

        return List.copyOf(triggers);
    }

    private static VfxTriggerDefinition parseTrigger(JsonObject json) {
        VfxTriggerType type = parseEnum(
                VfxTriggerType.class,
                getString(json, "type", "timeline"),
                "trigger type"
        );

        String eventId = getRequiredString(json, "event");

        return switch (type) {
            case ON_CREATION -> VfxTriggerDefinition.onCreation(eventId);

            case ON_EXPIRATION -> VfxTriggerDefinition.onExpiration(eventId);

            case TIMELINE -> {
                int timeTicks = getInt(json, "time_ticks", 0);

                if (timeTicks < 0) {
                    throw new IllegalArgumentException("Trigger time_ticks must be >= 0");
                }

                yield VfxTriggerDefinition.timeline(timeTicks, eventId);
            }

            case TRAVEL_DISTANCE -> {
                double distance = getDouble(json, "distance", 0.0);

                if (distance <= 0.0) {
                    throw new IllegalArgumentException("Travel distance trigger distance must be > 0");
                }

                yield VfxTriggerDefinition.travelDistance(distance, eventId, false);
            }

            case TRAVEL_DISTANCE_LOOPING -> {
                double distance = getDouble(json, "distance", 0.0);

                if (distance <= 0.0) {
                    throw new IllegalArgumentException("Travel distance looping trigger distance must be > 0");
                }

                yield VfxTriggerDefinition.travelDistance(distance, eventId, true);
            }
        };
    }

    private static void validateEmitterIds(List<VfxEmitterDefinition> emitters) {
        Set<String> ids = new HashSet<>();

        for(VfxEmitterDefinition emitter : emitters) {
            String id = emitter.id();

            if(id == null || id.isBlank()) {
                throw new IllegalArgumentException("Emitter id must not be empty");
            }

            if(!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate emitter id '" + id + "'");
            }
        }
    }

    private static void validateEventReferences(Map<String, VfxEventDefinition> events, List<VfxTriggerDefinition> triggers) {
        for (VfxTriggerDefinition trigger : triggers) {
            if (!events.containsKey(trigger.eventId())) {
                throw new IllegalArgumentException(
                        "Unknown event id in trigger: " + trigger.eventId()
                );
            }
        }

        for (Map.Entry<String, VfxEventDefinition> entry : events.entrySet()) {
            VfxEventDefinition event = entry.getValue();

            if (event.type() == VfxEventType.SEQUENCE || event.type() == VfxEventType.RANDOMIZE) {
                for (String nestedEventId : event.eventIds()) {
                    if (!events.containsKey(nestedEventId)) {
                        throw new IllegalArgumentException(
                                "Unknown nested event id in event '" + entry.getKey() + "': " + nestedEventId
                        );
                    }
                }
            }
        }
    }

    private static void validateEventEmitterReferences(Map<String, VfxEventDefinition> events, List<VfxEmitterDefinition> emitters) {
        Set<String> emitterIds = new HashSet<>();

        for (VfxEmitterDefinition emitter : emitters) {
            emitterIds.add(emitter.id());
        }

        for (Map.Entry<String, VfxEventDefinition> entry : events.entrySet()) {
            VfxEventDefinition event = entry.getValue();

            if (event.type() == VfxEventType.EMIT && !emitterIds.contains(event.emitterId())) {
                throw new IllegalArgumentException(
                        "Unknown emitter id in event '" + entry.getKey() + "': " + event.emitterId()
                );
            }
        }
    }

    private static void validateScopedTriggerReferences(List<VfxTriggerDefinition> triggers, Map<String, VfxEventDefinition> events, String scopeName) {
        for (VfxTriggerDefinition trigger : triggers) {
            if (!events.containsKey(trigger.eventId())) {
                throw new IllegalArgumentException(
                        "Unknown event id in " + scopeName + " trigger: " + trigger.eventId()
                );
            }
        }
    }

    private static void validateEmitterTriggerReferences(List<VfxEmitterDefinition> emitters, Map<String, VfxEventDefinition> events) {
        for (VfxEmitterDefinition emitter : emitters) {
            validateScopedTriggerReferences(
                    emitter.triggers(),
                    events,
                    "emitter '" + emitter.id() + "'"
            );

            validateScopedTriggerReferences(
                    emitter.particleTriggers(),
                    events,
                    "particle trigger of emitter '" + emitter.id() + "'"
            );
        }
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

        VfxNumberExpression delayTicks = getNumberExpression(json, "delay_ticks", 0.0);
        VfxNumberExpression activeTicks = getNumberExpression(json, "active_ticks", 1.0);
        VfxNumberExpression sleepTicks = getNumberExpression(json, "sleep_ticks", 0.0);

        VfxNumberExpression loops = getNumberExpression(json, "loops", 1.0);

        return switch (mode) {
            case ONCE -> VfxLifetimeDefinition.once(delayTicks, activeTicks);
            case LOOPING -> VfxLifetimeDefinition.looping(delayTicks, activeTicks, sleepTicks, loops);
        };
    }

    private static VfxEmitterDefinition parseEmitter(JsonObject json, int index) {
        String id = getString(json, "id", "emitter_" + index);

        List<VfxTriggerDefinition> triggers = json.has("triggers")
                ? parseTriggers(getArray(json, "triggers"))
                : List.of();

        List<VfxTriggerDefinition> particleTriggers = json.has("particle_triggers")
                ? parseTriggers(getArray(json, "particle_triggers"))
                : List.of();

        VfxEmitterLifetimeDefinition emitterLifetime = json.has("emitter_lifetime")
                ? parseEmitterLifetime(getObject(json, "emitter_lifetime"))
                : VfxEmitterLifetimeDefinition.none();
        VfxSpawnAmountDefinition spawnAmount = json.has("spawn_amount")
                ? parseSpawnAmount(getObject(json, "spawn_amount"))
                : VfxSpawnAmountDefinition.defaultInstant();

        VfxVec3Expression offset = getVec3Expression(
                json,
                "offset",
                VfxVec3.ZERO
        );

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

        return new VfxEmitterDefinition(id, triggers, particleTriggers, emitterLifetime, spawnAmount, offset, spawnShape, particleLifetime, motion, rotation, render);
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
                getVec3Expression(json, "start_rotation", VfxVec3.ZERO),
                getVec3Expression(json, "angular_velocity", VfxVec3.ZERO),
                getVec3Expression(json, "angular_acceleration", VfxVec3.ZERO),
                getNumberExpression(json, "angular_drag", 0.0)
        );
    }

    private static VfxParametricRotationDefinition parseParametricRotation(JsonObject json) {
        return VfxParametricRotationDefinition.of(
                getVec3Expression(json, "rotation", VfxVec3.ZERO)
        );
    }


    private static VfxParticleLifetimeDefinition parseParticleLifetime(JsonObject json) {
        return VfxParticleLifetimeDefinition.of(
                getNumberExpression(json, "max_age_ticks", 20.0)
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
                    getNumberExpression(json, "amount", 1.0)
            );
            case STEADY -> VfxSpawnAmountDefinition.steady(
                    getNumberExpression(json, "rate", 20.0),
                    getNumberExpression(json, "max_particles", 256.0)
            );
            case MANUAL -> VfxSpawnAmountDefinition.manual(
                    getNumberExpression(json, "amount", 1.0)
            );
        };
    }

    private static VfxEmitterLifetimeDefinition parseEmitterLifetime(JsonObject json) {
        VfxEmitterLifetimeMode mode = parseEnum(
                VfxEmitterLifetimeMode.class,
                getString(json, "mode", "once"),
                "emitter lifetime mode"
        );
        VfxNumberExpression delayTicks = getNumberExpression(json, "delay_ticks", 0.0);
        VfxNumberExpression activeTicks = getNumberExpression(json, "active_ticks", 1.0);
        VfxNumberExpression sleepTicks = getNumberExpression(json, "sleep_ticks", 0.0);

        VfxNumberExpression loops = getNumberExpression(json, "loops", 1.0);

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
                getVec3Expression(json, "offset", VfxVec3.ZERO),
                getVec3Expression(json, "direction", new VfxVec3(0.0, 1.0, 0.0))
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
                getVec3Expression(json, "custom_direction", new VfxVec3(0.0, 1.0, 0.0)),
                getNumberExpression(json, "speed", 0.0),
                getVec3Expression(json, "acceleration", VfxVec3.ZERO),
                getNumberExpression(json, "linear_drag", 0.0)
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
                getVec3Expression(json, "collision_size", defaultSize),
                getNumberExpression(json, "collision_drag", 0.0),
                getNumberExpression(json, "bounciness", 0.0),
                getBoolean(json, "expire_on_contact", false)
        );
    }

    private static Map<String, VfxEventDefinition> parseEventsMap(JsonObject json) {
        Map<String, VfxEventDefinition> events = new HashMap<>();

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String id = entry.getKey();

            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Event id must not be empty");
            }

            if (!entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException("Event must be an object: " + id);
            }

            if (events.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate event id: " + id);
            }

            events.put(id, parseEvent(entry.getValue().getAsJsonObject(), id));
        }


        return Map.copyOf(events);
    }

    private static VfxEventDefinition parseEvent(JsonObject json, String id) {
        VfxEventType type = parseEnum(
                VfxEventType.class,
                getString(json, "type", "emit"),
                "event type"
        );


        return switch (type) {
            case EMIT -> VfxEventDefinition.emit(
                    getRequiredString(json, "emitter")
            );

            case SEQUENCE -> VfxEventDefinition.sequence(
                    parseStringList(getArray(json, "events"), "sequence event list: " + id)
            );

            case RANDOMIZE -> VfxEventDefinition.randomize(
                    parseStringList(getArray(json, "events"), "randomize event list: " + id)
            );

            case SET_PARAM -> VfxEventDefinition.setParam(
                    getRequiredString(json, "param"),
                    getNumberExpression(json, "value", 0.0)
            );
        };
    }

    private static VfxTimelineEventDefinition parseTimelineEvent(JsonObject json) {
        int timeTicks = getInt(json, "time_ticks", 0);

        if (timeTicks < 0) {
            throw new IllegalArgumentException("Timeline event time_ticks must be >= 0");
        }

        VfxEventType type = parseEnum(
                VfxEventType.class,
                getString(json, "type", "emit"),
                "timeline event type"
        );

        if (type == VfxEventType.EMIT) {
            String emitterId = getRequiredString(json, "emitter");

            return new VfxTimelineEventDefinition(
                    timeTicks,
                    type,
                    emitterId
            );
        }

        throw new IllegalArgumentException("Unsupported event type: " + type);
    }


    private static VfxRenderDefinition parseRender(JsonObject json) {
        VfxRenderType type = parseEnum(
                VfxRenderType.class,
                getString(json, "type", "minecraft_particle"),
                "render type"
        );

        VfxFacingMode facing = parseEnum(
                VfxFacingMode.class,
                getString(json, "facing", "camera"),
                "render facing mode"
        );

        boolean environmentLighting = getBoolean(json, "environment_lighting", false);

        VfxMaterialDefinition material = json.has("material")
                ? parseMaterial(getObject(json, "material"))
                : VfxMaterialDefinition.defaultMaterial();


        return switch (type) {
            case MINECRAFT_PARTICLE -> VfxRenderDefinition.minecraftParticle(
                    parseMinecraftParticleRender(getObject(json, "minecraft_particle"))
            );
            case SPRITE -> VfxRenderDefinition.sprite(
                    facing,
                    material,
                    environmentLighting,
                    parseSpriteRender(getObject(json, "sprite")),
                    parseParticleAppearance(json)
            );
            case MODEL -> VfxRenderDefinition.model(
                    material,
                    environmentLighting,
                    parseModelRender(getObject(json, "model")),
                    parseParticleAppearance(json)
            );
        };
    }

    private static VfxModelRenderDefinition parseModelRender(JsonObject json) {
        VfxModelSourceType source = parseEnum(
                VfxModelSourceType.class,
                getString(json, "source", "custom"),
                "model source"
        );

        ResourceLocation model = ResourceLocation.parse(
                getString(json, "model", "minecraft:block/stone")
        );

        VfxModelRenderLayer renderLayer = parseEnum(
                VfxModelRenderLayer.class,
                getString(json, "render_layer", "cutout"),
                "model render layer"
        );

        return new VfxModelRenderDefinition(source, model, renderLayer);
    }

    private static VfxMaterialDefinition parseMaterial(JsonObject json) {
        VfxBlendMode blendMode = parseEnum(
                VfxBlendMode.class,
                getString(json, "blend_mode", "alpha"),
                "material blend mode"
        );

        return new VfxMaterialDefinition(blendMode);
    }

    private static VfxParticleAppearanceDefinition parseParticleAppearance(JsonObject json) {
        return new VfxParticleAppearanceDefinition(
                getColorExpression(
                        json,
                        "color",
                        new VfxColor(1.0, 1.0, 1.0, 1.0)
                ),
                getVec3Expression(
                        json,
                        "scale",
                        new VfxVec3(1.0, 1.0, 1.0)
                )
        );
    }

    private static VfxSpriteRenderDefinition parseSpriteRender(JsonObject json) {
        return new VfxSpriteRenderDefinition(
                ResourceLocation.parse(getString(json, "texture", "minecraft:textures/particle/generic_0.png")),
                json.has("uv")
                        ? parseSpriteUv(getObject(json, "uv"))
                        : VfxSpriteUvDefinition.full()
        );
    }

    private static VfxSpriteUvDefinition parseSpriteUv(JsonObject json) {
        VfxUvMode mode = parseEnum(
                VfxUvMode.class,
                getString(json, "mode", "full"),
                "sprite uv mode"
        );

        if (mode == VfxUvMode.FULL) {
            return VfxSpriteUvDefinition.full();
        }

        VfxVec3Expression textureSize = getVec3Expression(
                json,
                "texture_size",
                new VfxVec3(16.0, 16.0, 0.0)
        );

        VfxVec3Expression uvStart = getVec3Expression(
                json,
                "uv_start",
                VfxVec3.ZERO
        );

        VfxVec3Expression uvSize = getVec3Expression(
                json,
                "uv_size",
                new VfxVec3(16.0, 16.0, 0.0)
        );

        VfxVec3Expression uvStep = getVec3Expression(
                json,
                "uv_step",
                new VfxVec3(16.0, 0.0, 0.0)
        );

        VfxNumberExpression frameCount = getNumberExpression(
                json,
                "frame_count",
                1.0
        );

        VfxNumberExpression fps = getNumberExpression(
                json,
                "fps",
                0.0
        );

        return new VfxSpriteUvDefinition(
                mode,
                textureSize,
                uvStart,
                uvSize,
                uvStep,
                frameCount,
                fps,
                getBoolean(json, "stretch_to_lifetime", false),
                getBoolean(json, "loop", false),
                getBoolean(json, "random_start_frame", false)
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

        VfxNumberExpression edgeThickness = getNumberExpression(
                json,
                "edge_thickness",
                0.0
        );

        return switch (type) {
            case POINT -> VfxSpawnShapeDefinition.point();
            case SPHERE -> VfxSpawnShapeDefinition.sphere(
                    getNumberExpression(json, "radius", 0.25),
                    edgeThickness
            );
            case BOX -> VfxSpawnShapeDefinition.box(
                    getVec3Expression(json, "half_extents", new VfxVec3(0.25, 0.25, 0.25)),
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

    private static List<String> parseStringList(JsonArray array, String fieldName) {
        List<String> result = new ArrayList<>();

        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Expected string in " + fieldName);
            }

            String value = element.getAsString();

            if (value.isBlank()) {
                throw new IllegalArgumentException("Empty string in " + fieldName);
            }

            result.add(value);
        }

        return List.copyOf(result);
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
            double fallback
    ) {
        if (!json.has(key)) {
            return VfxNumberExpression.constant(fallback);
        }

        JsonElement element = json.get(key);

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return VfxNumberExpression.constant(element.getAsDouble());
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return VfxNumberExpression.expression(element.getAsString());
        }

        throw new IllegalArgumentException("Expected number or expression string field: " + key);
    }

    private static VfxVec3Expression getVec3Expression(
            JsonObject json,
            String key,
            VfxVec3 fallback
    ) {
        if (!json.has(key)) {
            return VfxVec3Expression.constant(fallback);
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
                parseNumberExpressionElement(array.get(0)),
                parseNumberExpressionElement(array.get(1)),
                parseNumberExpressionElement(array.get(2))
        );
    }

    private static VfxNumberExpression parseNumberExpressionElement(
            JsonElement element
    ) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return VfxNumberExpression.constant(element.getAsDouble());
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return VfxNumberExpression.expression(element.getAsString());
        }

        throw new IllegalArgumentException("Expected number or expression string");
    }

    private static VfxColorExpression getColorExpression(
            JsonObject json,
            String key,
            VfxColor fallback
    ) {
        if (!json.has(key)) {
            return constantColorExpression(fallback);
        }

        JsonElement element = json.get(key);

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString();

            if (raw.startsWith("#")) {
                return constantColorExpression(parseHexColor(raw));
            }

            throw new IllegalArgumentException("Color string must be hex color: " + raw);
        }

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();

            if (array.size() != 4) {
                throw new IllegalArgumentException("Expected color array with 4 values: " + key);
            }

            return new VfxColorExpression(
                    parseNumberExpressionElement(array.get(0)),
                    parseNumberExpressionElement(array.get(1)),
                    parseNumberExpressionElement(array.get(2)),
                    parseNumberExpressionElement(array.get(3))
            );
        }

        throw new IllegalArgumentException("Expected hex color or color expression array field: " + key);
    }

    private static VfxColorExpression constantColorExpression(VfxColor color) {
        return new VfxColorExpression(
                VfxNumberExpression.constant(color.r()),
                VfxNumberExpression.constant(color.g()),
                VfxNumberExpression.constant(color.b()),
                VfxNumberExpression.constant(color.a())
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
