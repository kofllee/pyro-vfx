package net.kofllee.pyrovfx.dev;

import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.client.vfx.VfxLifetimeRuntime;
import net.kofllee.pyrovfx.client.vfx.VfxRuntimeBudget;
import net.kofllee.pyrovfx.client.vfx.VfxWorldIdentity;
import net.kofllee.pyrovfx.client.vfx.ClientVfxInstance;
import net.kofllee.pyrovfx.client.vfx.VfxPlayOptions;
import net.kofllee.pyrovfx.client.render.VfxSpriteUvSampler;
import net.minecraft.world.phys.Vec3;
import net.kofllee.pyrovfx.vfx.curve.VfxCurveDefinition;
import net.kofllee.pyrovfx.vfx.curve.VfxCurvePoint;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.resource.VfxDefinitionParser;
import net.kofllee.pyrovfx.vfx.resource.VfxRegistry;
import net.kofllee.pyrovfx.vfx.resource.VfxJsonLoader;
import net.kofllee.pyrovfx.vfx.type.VfxCurveType;
import net.kofllee.pyrovfx.vfx.type.VfxEmitterLifetimeMode;
import net.kofllee.pyrovfx.vfx.type.VfxLifetimeMode;
import net.kofllee.pyrovfx.vfx.type.VfxSpawnShapeType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;

@GameTestHolder(PyroVfx.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PyroVfxGameTests {
    private PyroVfxGameTests() {}

    @GameTest(template = "empty")
    public static void expressionVariables(GameTestHelper helper) {
        VfxExpressionContext context = VfxExpressionContext.root()
                .setNumber("param.power", 2.0)
                .setNumber("effect.age", 0.0);
        checkClose(4.0, VfxNumberExpression.expression("param.power * 2 + math.sin(effect.age)").evaluate(context));
        context.setNumber("param.power", 3.0);
        checkClose(6.0, VfxNumberExpression.expression("param.power * 2").evaluate(context));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void childParameterMutationUpdatesOwningScope(GameTestHelper helper) {
        VfxExpressionContext effect = VfxExpressionContext.root().setNumber("param.stage", 1.0);
        VfxExpressionContext emitter = effect.child().setNumber("emitter.age", 10);
        emitter.updateNumber("param.stage", 2.0);
        checkClose(2.0, effect.getNumber("param.stage"));
        checkClose(2.0, emitter.getNumber("param.stage"));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void curveEndpoints(GameTestHelper helper) {
        List<VfxCurvePoint> points = List.of(new VfxCurvePoint(0.0, 0.2), new VfxCurvePoint(1.0, 0.8));
        for (VfxCurveType type : VfxCurveType.values()) {
            VfxCurveDefinition curve = new VfxCurveDefinition(type, points);
            checkClose(0.2, curve.sample(-1.0));
            checkClose(0.8, curve.sample(1.0));
            checkClose(0.8, curve.sample(2.0));
        }
        checkClose(0.5, new VfxCurveDefinition(VfxCurveType.LINEAR, points).sample(0.5));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void parseCollisionEventsAndParameters(GameTestHelper helper) {
        String json = """
            {
              "format":"pyro_vfx:1",
              "parameters":{"power":2},
              "events":{"hit":{"type":"set_param","param":"power","value":3}},
              "emitters":[{
                "id":"spark",
                "particle_triggers":[{"type":"on_collision","event":"hit"}],
                "spawn_shape":{"type":"sphere","radius":0.5},
                "motion":{"mode":"dynamic","dynamic":{"speed":1},
                  "collision":{"collide":true,"expire_on_contact":true}},
                "render":{"type":"sprite","sprite":{"texture":"minecraft:textures/particle/generic_0.png"}}
              }]
            }
            """;
        var definition = VfxDefinitionParser.parse(
                ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "gametest"),
                JsonParser.parseString(json).getAsJsonObject());
        if (definition.emitters().size() != 1 || definition.events().size() != 1
                || !definition.emitters().getFirst().motion().collision().collide()
                || definition.emitters().getFirst().particleTriggers().size() != 1) {
            throw new AssertionError("Parser lost collision or event definitions");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectUnknownEventParameter(GameTestHelper helper) {
        String json = """
            {"format":"pyro_vfx:1","emitters":[],
             "events":{"bad":{"type":"set_param","param":"unknown","value":1}}}
            """;
        try {
            VfxDefinitionParser.parse(
                    ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "invalid"),
                    JsonParser.parseString(json).getAsJsonObject());
        } catch (IllegalArgumentException expected) {
            helper.succeed();
            return;
        }
        throw new AssertionError("Invalid parameter reference was accepted");
    }

    @GameTest(template = "empty")
    public static void onceLifetimeHonorsDelayAndBoundaries(GameTestHelper helper) {
        var before = VfxLifetimeRuntime.effect(VfxLifetimeMode.ONCE, 1, 2, 3, 0, 1);
        var first = VfxLifetimeRuntime.effect(VfxLifetimeMode.ONCE, 2, 2, 3, 0, 1);
        var last = VfxLifetimeRuntime.effect(VfxLifetimeMode.ONCE, 4, 2, 3, 0, 1);
        var finished = VfxLifetimeRuntime.effect(VfxLifetimeMode.ONCE, 5, 2, 3, 0, 1);
        if (before.active() || before.finished() || !first.active() || !last.active()
                || finished.active() || !finished.finished()) {
            throw new AssertionError("Once lifetime does not preserve delay/end boundary");
        }
        checkClose(2.0 / 3.0, last.normalizedAge());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loopingLifetimeHonorsSleepAndFiniteLoops(GameTestHelper helper) {
        var active = VfxLifetimeRuntime.effect(VfxLifetimeMode.LOOPING, 1, 1, 2, 1, 2);
        var sleep = VfxLifetimeRuntime.effect(VfxLifetimeMode.LOOPING, 3, 1, 2, 1, 2);
        var nextCycle = VfxLifetimeRuntime.effect(VfxLifetimeMode.LOOPING, 4, 1, 2, 1, 2);
        var finished = VfxLifetimeRuntime.effect(VfxLifetimeMode.LOOPING, 7, 1, 2, 1, 2);
        var unlimited = VfxLifetimeRuntime.effect(VfxLifetimeMode.LOOPING, 70, 1, 2, 1, 0);
        if (!active.active() || sleep.active() || sleep.finished() || !nextCycle.active()
                || !finished.finished() || unlimited.finished()) {
            throw new AssertionError("Looping lifetime changed sleep, repetition, or zero-loop semantics");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void manualEmitterDoesNotAutoExpire(GameTestHelper helper) {
        var state = VfxLifetimeRuntime.emitter(VfxEmitterLifetimeMode.MANUAL, 1000, 0, 2, 0, 1);
        if (state.active() || state.finished()) {
            throw new AssertionError("Manual emitters must remain event-driven");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void parseAllSpawnShapes(GameTestHelper helper) {
        String[] shapes = {
                "{\"type\":\"point\"}",
                "{\"type\":\"sphere\",\"radius\":2}",
                "{\"type\":\"box\",\"half_extents\":[1,2,3]}",
                "{\"type\":\"line\",\"length\":2}",
                "{\"type\":\"disc\",\"radius\":2}",
                "{\"type\":\"ring\",\"radius\":2,\"inner_radius\":1}",
                "{\"type\":\"cone\",\"radius\":2,\"height\":3}",
                "{\"type\":\"model\",\"model\":\"minecraft:block/stone\"}"
        };
        VfxSpawnShapeType[] expected = {
                VfxSpawnShapeType.POINT,
                VfxSpawnShapeType.SPHERE,
                VfxSpawnShapeType.BOX,
                VfxSpawnShapeType.LINE,
                VfxSpawnShapeType.DISC,
                VfxSpawnShapeType.RING,
                VfxSpawnShapeType.CONE,
                VfxSpawnShapeType.MODEL
        };
        if (VfxSpawnShapeType.values().length != expected.length) {
            throw new AssertionError("Update coverage for newly supported spawn shapes");
        }
        for (int i = 0; i < shapes.length; i++) {
            String source = """
                    {"format":"pyro_vfx:1","emitters":[{
                        "spawn_shape":%s,
                        "render":{"type":"sprite",
                          "sprite":{"texture":"minecraft:textures/particle/generic_0.png"}}
                    }]}
                    """.formatted(shapes[i]);
            var definition = VfxDefinitionParser.parse(
                    ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "shape_test"),
                    JsonParser.parseString(source).getAsJsonObject());
            VfxSpawnShapeType type = definition.emitters().getFirst().spawnShape().type();
            if (type != expected[i]) {
                throw new AssertionError("Unexpected shape parsed: " + type);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void registrySnapshotDoesNotChangeMidReload(GameTestHelper helper) {
        var previous = new LinkedHashMap<net.minecraft.resources.ResourceLocation,
                net.kofllee.pyrovfx.vfx.definition.VfxDefinition>();
        for (var id : VfxRegistry.ids()) {
            previous.put(id, VfxRegistry.get(id));
        }
        try {
            var snapshot = VfxRegistry.ids();
            var test = VfxDefinitionParser.parse(
                    ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "registry_snapshot_test"),
                    JsonParser.parseString("""
                            {"format":"pyro_vfx:1","emitters":[]}
                            """).getAsJsonObject());
            VfxRegistry.register(test);
            if (snapshot.contains(test.id())) {
                throw new AssertionError("Previously returned registry snapshot mutated during reload");
            }
        } finally {
            VfxRegistry.clear();
            previous.values().forEach(VfxRegistry::register);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidReloadKeepsLastValidEffect(GameTestHelper helper) {
        Map<ResourceLocation, net.kofllee.pyrovfx.vfx.definition.VfxDefinition> previous = new LinkedHashMap<>();
        for (ResourceLocation id : VfxRegistry.ids()) {
            previous.put(id, VfxRegistry.get(id));
        }
        ResourceLocation original = ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "vfx/reload_original.json");
        ResourceLocation added = ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "vfx/reload_added.json");
        String valid = "{\"format\":\"pyro_vfx:1\",\"emitters\":[]}";
        try {
            VfxJsonLoader.reload(inMemoryResources(Map.of(original, valid)));
            ResourceLocation originalId = ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "reload_original");
            ResourceLocation addedId = ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "reload_added");
            var priorDefinition = VfxRegistry.get(originalId);
            if (priorDefinition == null) {
                throw new AssertionError("Initial valid resource did not load");
            }
            ClientVfxInstance active = new ClientVfxInstance(priorDefinition, Vec3.ZERO, VfxPlayOptions.empty(), 23);
            VfxJsonLoader.reload(inMemoryResources(Map.of(original, "{invalid json", added, valid)));
            if (VfxRegistry.get(originalId) != priorDefinition || !VfxRegistry.contains(addedId)) {
                throw new AssertionError("Reload lost the last valid definition or the newly loaded effect");
            }
            if (active.definition() != priorDefinition) {
                throw new AssertionError("Active effect definition changed while reloading resources");
            }
            active.tick(null);
            VfxJsonLoader.reload(inMemoryResources(Map.of(added, valid)));
            if (VfxRegistry.contains(originalId) || !VfxRegistry.contains(addedId)) {
                throw new AssertionError("Reload failed to remove a resource that no longer exists");
            }
        } finally {
            VfxRegistry.replaceAll(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void runtimeBudgetBoundsEventBreadthAndSpawns(GameTestHelper helper) {
        VfxRuntimeBudget budget = new VfxRuntimeBudget();
        budget.beginTick(0, false);
        for (int i = 0; i < VfxRuntimeBudget.MAX_EVENTS_PER_TICK; i++) {
            if (!budget.enterEvent()) {
                throw new AssertionError("Budget exhausted too early");
            }
        }
        if (budget.enterEvent() || budget.hasEventBudget()) {
            throw new AssertionError("Nested event sequence can run beyond allowed budget");
        }
        if (budget.reserveSpawns(Integer.MAX_VALUE) != VfxRuntimeBudget.MAX_SPAWNS_PER_TICK
                || budget.reserveSpawns(100) != 0) {
            throw new AssertionError("Malicious instant/manual amount was not clamped");
        }
        budget.beginTick(VfxRuntimeBudget.MAX_ACTIVE_PARTICLES - 3, false);
        if (budget.reserveSpawns(Integer.MAX_VALUE) != 3) {
            throw new AssertionError("Effect active-particle limit was bypassed");
        }
        budget.beginTick(0, true);
        if (budget.reserveSpawns(VfxRuntimeBudget.HEAVY_PARTICLES) != VfxRuntimeBudget.HEAVY_PARTICLES) {
            throw new AssertionError("Explicit heavy benchmark opt-in was silently blocked");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void worldIdentityDetectsDirectWorldSwitches(GameTestHelper helper) {
        VfxWorldIdentity identity = new VfxWorldIdentity();
        Object overworld = new Object();
        Object nether = new Object();
        if (!identity.update(overworld) || identity.update(overworld)
                || !identity.update(nether) || identity.update(nether)
                || !identity.update(null) || identity.update(null)
                || !identity.update(overworld)) {
            throw new AssertionError("World identity guard failed to detect a world transition");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eventFanoutUsesSharedInstanceBudget(GameTestHelper helper) {
        JsonObject source = JsonParser.parseString("""
                {"format":"pyro_vfx:1","parameters":{"stage":0},
                 "lifetime":{"mode":"once","active_ticks":5},
                 "events":{"inc":{"type":"set_param","param":"stage","value":"param.stage+1"},
                           "start":{"type":"sequence","events":[]}},
                 "triggers":[{"type":"on_creation","event":"start"}],
                 "emitters":[]}
                """).getAsJsonObject();
        JsonArray branches = source.getAsJsonObject("events").getAsJsonObject("start").getAsJsonArray("events");
        for (int i = 0; i < VfxRuntimeBudget.MAX_EVENTS_PER_TICK + 2_000; i++) {
            branches.add("inc");
        }
        var definition = VfxDefinitionParser.parse(
                ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "fanout_golden"), source);
        ClientVfxInstance instance = new ClientVfxInstance(definition, Vec3.ZERO, VfxPlayOptions.empty(), 1234L);
        instance.tick(null);
        checkClose(VfxRuntimeBudget.MAX_EVENTS_PER_TICK - 1,
                instance.parameters().getOrDefault("stage", -1.0));
        instance.tick(null);
        checkClose(VfxRuntimeBudget.MAX_EVENTS_PER_TICK - 1,
                instance.parameters().getOrDefault("stage", -1.0));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void goldenDynamicMotionRotationUvAndExpiration(GameTestHelper helper) {
        String source = """
                {
                  "format":"pyro_vfx:1",
                  "lifetime":{"mode":"once","active_ticks":10},
                  "emitters":[{
                    "id":"golden",
                    "emitter_lifetime":{"mode":"once","active_ticks":1},
                    "spawn_amount":{"mode":"instant","amount":1},
                    "spawn_shape":{"type":"point"},
                    "particle_lifetime":{"max_age_ticks":4},
                    "motion":{"mode":"dynamic","dynamic":{
                      "direction":"custom","custom_direction":[1,0,0],
                      "speed":2,"acceleration":[0,0,0],"linear_drag":0
                    }},
                    "rotation":{"mode":"dynamic","dynamic":{
                      "start_rotation":[0,0,0],"angular_velocity":[0,20,0],
                      "angular_acceleration":[0,0,0],"angular_drag":0
                    }},
                    "render":{"type":"sprite",
                      "sprite":{"texture":"minecraft:textures/particle/generic_0.png",
                        "uv":{"mode":"animated","texture_size":[64,16,0],
                          "uv_start":[0,0,0],"uv_size":[16,16,0],
                          "uv_step":[16,0,0],"frame_count":4,"fps":20,
                          "stretch_to_lifetime":true,"loop":false}},
                      "scale":[1,1,1],"color":"#FFFFFFFF"}
                  }]
                }
                """;
        var definition = VfxDefinitionParser.parse(
                ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "golden_motion"),
                JsonParser.parseString(source).getAsJsonObject());
        ClientVfxInstance instance = new ClientVfxInstance(definition, Vec3.ZERO, VfxPlayOptions.empty(), 42L);
        for (int tick = 1; tick <= 4; tick++) {
            instance.tick(null); // no collisions/vanilla particles: pure deterministic runtime
            if (tick == 4) {
                if (!instance.particles().isEmpty()) {
                    throw new AssertionError("Particle did not expire at its fourth tick");
                }
                break;
            }
            if (instance.particles().size() != 1) {
                throw new AssertionError("Expected exactly one deterministic particle");
            }
            var particle = instance.particles().getFirst();
            checkClose(tick * 0.1, particle.position().x);
            checkClose(0.0, particle.position().y);
            checkClose(0.0, particle.position().z);
            checkClose(tick, particle.rotation().y);
            if (particle.age() != tick) {
                throw new AssertionError("Particle age changed: " + particle.age());
            }
            var uv = VfxSpriteUvSampler.sample(particle);
            checkClose(tick * 0.25, uv.u0());
            checkClose((tick + 1) * 0.25, uv.u1());
            checkClose(0, uv.v0());
            checkClose(1, uv.v1());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void goldenCreationTimelineAndExpirationEventOrder(GameTestHelper helper) {
        String source = """
                {"format":"pyro_vfx:1",
                 "parameters":{"stage":0},
                 "lifetime":{"mode":"once","active_ticks":3},
                 "events":{"born":{"type":"set_param","param":"stage","value":1},
                           "timed":{"type":"set_param","param":"stage","value":2},
                           "expired":{"type":"set_param","param":"stage","value":3}},
                 "triggers":[{"type":"on_creation","event":"born"},
                             {"type":"timeline","time_ticks":1,"event":"timed"},
                             {"type":"on_expiration","event":"expired"}],
                 "emitters":[]}
                """;
        var definition = VfxDefinitionParser.parse(
                ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "golden_triggers"),
                JsonParser.parseString(source).getAsJsonObject());
        ClientVfxInstance instance = new ClientVfxInstance(definition, Vec3.ZERO, VfxPlayOptions.empty(), 9L);
        int[] expectedStageAfterTick = {1, 2, 2, 3, 3};
        for (int expected : expectedStageAfterTick) {
            instance.tick(null);
            checkClose(expected, instance.parameters().get("stage"));
        }
        if (!instance.isFinished()) {
            throw new AssertionError("Effect should be finished after its expiration trigger");
        }
        helper.succeed();
    }

    private static ResourceManager inMemoryResources(Map<ResourceLocation, String> sources) {
        PackResources pack = (PackResources) Proxy.newProxyInstance(
                PackResources.class.getClassLoader(),
                new Class<?>[]{PackResources.class},
                (proxy, method, args) -> null);
        Map<ResourceLocation, Resource> resources = new LinkedHashMap<>();
        for (var entry : sources.entrySet()) {
            byte[] bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
            resources.put(entry.getKey(), new Resource(pack, () -> new ByteArrayInputStream(bytes)));
        }
        return (ResourceManager) Proxy.newProxyInstance(
                ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("listResources")) {
                        return resources;
                    }
                    throw new UnsupportedOperationException("Unexpected resource manager call: " + method.getName());
                });
    }

    private static void checkClose(double expected, double actual) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 1.0e-6) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
