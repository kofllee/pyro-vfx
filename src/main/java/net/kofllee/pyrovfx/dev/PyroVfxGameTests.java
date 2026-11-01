package net.kofllee.pyrovfx.dev;

import com.google.gson.JsonParser;
import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.client.vfx.VfxLifetimeRuntime;
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
            VfxJsonLoader.reload(inMemoryResources(Map.of(original, "{invalid json", added, valid)));
            if (VfxRegistry.get(originalId) != priorDefinition || !VfxRegistry.contains(addedId)) {
                throw new AssertionError("Reload lost the last valid definition or the newly loaded effect");
            }
            VfxJsonLoader.reload(inMemoryResources(Map.of(added, valid)));
            if (VfxRegistry.contains(originalId) || !VfxRegistry.contains(addedId)) {
                throw new AssertionError("Reload failed to remove a resource that no longer exists");
            }
        } finally {
            VfxRegistry.replaceAll(previous);
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
