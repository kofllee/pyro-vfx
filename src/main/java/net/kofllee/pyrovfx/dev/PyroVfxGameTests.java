package net.kofllee.pyrovfx.dev;

import com.google.gson.JsonParser;
import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.client.vfx.VfxLifetimeRuntime;
import net.kofllee.pyrovfx.vfx.curve.VfxCurveDefinition;
import net.kofllee.pyrovfx.vfx.curve.VfxCurvePoint;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.resource.VfxDefinitionParser;
import net.kofllee.pyrovfx.vfx.type.VfxCurveType;
import net.kofllee.pyrovfx.vfx.type.VfxEmitterLifetimeMode;
import net.kofllee.pyrovfx.vfx.type.VfxLifetimeMode;
import net.kofllee.pyrovfx.vfx.type.VfxSpawnShapeType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

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

    private static void checkClose(double expected, double actual) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 1.0e-6) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
