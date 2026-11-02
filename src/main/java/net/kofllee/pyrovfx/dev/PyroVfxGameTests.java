package net.kofllee.pyrovfx.dev;

import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.client.vfx.VfxLifetimeRuntime;
import net.kofllee.pyrovfx.client.vfx.VfxRuntimeBudget;
import net.kofllee.pyrovfx.client.vfx.VfxGlobalBudget;
import net.kofllee.pyrovfx.client.vfx.VfxAdaptiveSpawnController;
import net.kofllee.pyrovfx.client.vfx.VfxCollisionQueryCache;
import net.kofllee.pyrovfx.client.vfx.VfxAnalyticPlan;
import net.kofllee.pyrovfx.client.vfx.VfxAnalyticSpritePool;
import net.kofllee.pyrovfx.client.vfx.VfxWorldIdentity;
import net.kofllee.pyrovfx.client.vfx.ClientVfxInstance;
import net.kofllee.pyrovfx.client.vfx.VfxPlayOptions;
import net.kofllee.pyrovfx.client.render.VfxSpriteUvSampler;
import net.kofllee.pyrovfx.client.render.VfxRenderVisibility;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import net.kofllee.pyrovfx.vfx.curve.VfxCurveDefinition;
import net.kofllee.pyrovfx.vfx.curve.VfxCurvePoint;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionCompiler;
import net.kofllee.pyrovfx.vfx.expression.VfxProgram;
import net.kofllee.pyrovfx.client.vfx.ClientVfxExpressionContexts;
import net.kofllee.pyrovfx.client.vfx.VfxDenseSpritePool;
import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.kofllee.pyrovfx.client.vfx.VfxEventCommandQueue;
import net.kofllee.pyrovfx.client.vfx.VfxEventRuntime;
import net.kofllee.pyrovfx.vfx.definition.VfxEventDefinition;
import net.minecraft.util.RandomSource;
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
    public static void adaptiveSpawnsBackOffAndRecoverWithoutBreakingHardBudgets(GameTestHelper helper) {
        var adaptive = new VfxAdaptiveSpawnController();
        var global = new VfxGlobalBudget();
        for (int tick = 0; tick < 36; tick++) {
            adaptive.observe(9_000_000L, 10_000, true, 3.0, 25);
        }
        if (adaptive.spawnScale() >= 0.5 || adaptive.spawnScale() < 0.25
                || adaptive.effectiveSpawnLimit(20_000) > 10_000
                || adaptive.constrainedTicks() == 0) {
            throw new AssertionError("Persistent expensive Pyro CPU work did not reduce spawn throughput safely");
        }
        global.beginTick(12_000, false, 80_000, adaptive.effectiveSpawnLimit(30_000));
        global.setAdaptiveSpawnScale(adaptive.spawnScale());
        int limit = global.spawnLimit();
        if (limit < 1 || limit > 30_000 || global.activeLimit() != 80_000
                || global.reserveSpawns(50_000) != limit
                || global.remainingActive() != 68_000 - limit
                || !global.enterEvent()) {
            throw new AssertionError("Adaptive policy bypassed global active cap or throttled events");
        }
        global.beginTick(4_000, false, 80_000, adaptive.effectiveSpawnLimit(30_000));
        global.setAdaptiveSpawnScale(adaptive.spawnScale());
        var perEffect = new VfxRuntimeBudget();
        perEffect.beginTick(0, false, global);
        perEffect.qualityScale(net.kofllee.pyrovfx.vfx.type.VfxQualityPriority.NORMAL
                .scale(global.adaptiveSpawnScale()));
        int smallRequestGranted = perEffect.reserveSpawns(300);
        if (smallRequestGranted <= 0 || smallRequestGranted >= 300) {
            throw new AssertionError("Adaptive spawn policy did not throttle smaller steady requests");
        }
        for (int tick = 0; tick < 720; tick++) {
            adaptive.observe(150_000L, 8_000, true, 3.0, 25);
        }
        if (adaptive.effectiveSpawnLimit(30_000) != 30_000
                || Math.abs(adaptive.spawnScale() - 1.0) > 0.00001) {
            throw new AssertionError("Adaptation did not restore full spawn capacity after recovery");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void qualityPrioritiesConserveEventsAndHardCaps(GameTestHelper helper) {
        var id = ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "priority");
        var definition = VfxDefinitionParser.parse(id, JsonParser.parseString("""
                {"format":"pyro_vfx:1","metadata":{"priority":"important"},
                 "emitters":[{"id":"e","spawn_shape":{"type":"point"},
                   "render":{"type":"sprite","sprite":{"texture":"minecraft:textures/particle/generic_0.png"}}}]}
                """).getAsJsonObject());
        if (definition.metadata().priority()
                != net.kofllee.pyrovfx.vfx.type.VfxQualityPriority.IMPORTANT) {
            throw new AssertionError("Quality priority definition lost");
        }
        try {
            VfxDefinitionParser.parse(id, JsonParser.parseString("""
                {"format":"pyro_vfx:1","metadata":{"priority":"invented"},
                 "emitters":[{"id":"e","spawn_shape":{"type":"point"},
                   "render":{"type":"sprite","sprite":{"texture":"minecraft:textures/particle/generic_0.png"}}}]}
                """).getAsJsonObject());
            throw new AssertionError("Invalid priority parsed silently");
        } catch (IllegalArgumentException expected) { /* must reject */ }

        var global = new VfxGlobalBudget();
        for (var priority : net.kofllee.pyrovfx.vfx.type.VfxQualityPriority.values()) {
            global.beginTick(0, false, 4000, 1000);
            global.setAdaptiveSpawnScale(0.25);
            var effect = new VfxRuntimeBudget();
            effect.beginTick(0, false, global);
            effect.qualityScale(priority.scale(global.adaptiveSpawnScale()));
            int admitted = effect.reserveSpawns(800);
            int expected = Math.min(800,
                    (int) Math.floor(800 * priority.scale(0.25)));
            if (admitted != expected || !effect.enterEvent()
                    || global.remainingActive() != 4000 - expected) {
                throw new AssertionError("Incorrect priority weight/event integrity for " + priority);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void measuredPerEffectCpuCostReducesOnlyOverloadedEffects(GameTestHelper helper) {
        var policy = net.kofllee.pyrovfx.client.vfx.VfxCostAdmissionPolicy.class;
        var normal = net.kofllee.pyrovfx.vfx.type.VfxQualityPriority.NORMAL;
        var important = net.kofllee.pyrovfx.vfx.type.VfxQualityPriority.IMPORTANT;
        var critical = net.kofllee.pyrovfx.vfx.type.VfxQualityPriority.CRITICAL;
        double healthy = net.kofllee.pyrovfx.client.vfx.VfxCostAdmissionPolicy.scale(
                1.0, 100.0, 3.0, 3, normal);
        double heavy = net.kofllee.pyrovfx.client.vfx.VfxCostAdmissionPolicy.scale(
                0.5, 8.0, 3.0, 3, normal);
        double cheap = net.kofllee.pyrovfx.client.vfx.VfxCostAdmissionPolicy.scale(
                0.5, 0.2, 3.0, 3, normal);
        if (healthy != 1.0 || !(heavy < cheap && cheap <= 0.5)
                || net.kofllee.pyrovfx.client.vfx.VfxCostAdmissionPolicy.scale(
                    0.5, 8, 3, 3, important) <= heavy
                || net.kofllee.pyrovfx.client.vfx.VfxCostAdmissionPolicy.scale(
                    0.5, 8, 3, 3, critical) != 1.0) {
            throw new AssertionError("Measured per-effect CPU quality policy violated priority or recovery");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownGpuMemoryNeverLooksLikeOutOfMemory(GameTestHelper helper) {
        if (net.kofllee.pyrovfx.client.vfx.VfxGpuMemoryPolicy.spawnScale(-1, -1) != 1.0
                || net.kofllee.pyrovfx.client.vfx.VfxGpuMemoryPolicy.spawnScale(2048, 1024) != 1.0
                || net.kofllee.pyrovfx.client.vfx.VfxGpuMemoryPolicy.spawnScale(100, 8192) != 0.25
                || net.kofllee.pyrovfx.client.vfx.VfxGpuMemoryPolicy.spawnScale(240, 8192) != 0.5
                || net.kofllee.pyrovfx.client.vfx.VfxGpuMemoryPolicy.spawnScale(370, 8192) != 0.8
                || net.kofllee.pyrovfx.client.vfx.VfxGpuMemoryPolicy.spawnScale(4000, 8192) != 1.0) {
            throw new AssertionError("VRAM fallback or bounded pressure classification failed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void independentCostClassBudgetsCannotBeBypassed(GameTestHelper helper) {
        var gpu = net.kofllee.pyrovfx.client.vfx.VfxParticleCostClass.GPU_ANALYTIC;
        var cpu = net.kofllee.pyrovfx.client.vfx.VfxParticleCostClass.CPU_BASIC;
        var collision = net.kofllee.pyrovfx.client.vfx.VfxParticleCostClass.CPU_COLLISION;
        var model = net.kofllee.pyrovfx.client.vfx.VfxParticleCostClass.CPU_MODEL;
        var global = new VfxGlobalBudget();
        int[] active = new int[4];
        active[collision.ordinal()] = 19_900;
        int modelLimit = net.kofllee.pyrovfx.client.vfx.VfxBudgetConfig.classLimit(model);
        active[model.ordinal()] = modelLimit - 10;
        global.beginTick(19_900 + modelLimit - 10, false,
                19_900 + modelLimit + 50_000, 30_000, active);
        if (global.reserveSpawns(300, collision) != 100
                || global.reserveSpawns(100, model) != 10
                || global.reserveSpawns(300, cpu) != 300
                || global.reserveSpawns(1_000, gpu) != 1_000
                || global.remainingFor(collision) != 0
                || global.remainingFor(model) != 0
                || global.admittedFor(gpu) != 1_000
                || global.rejectedFor(collision) != 200
                || !global.enterEvent()) {
            throw new AssertionError("Global per-cost-class admission or event safety was bypassed");
        }
        global.beginTick(79_999, false, 80_000, 30_000, new int[4]);
        if (global.reserveSpawns(100, gpu) != 1
                || global.reserveSpawns(100, cpu) != 0) {
            throw new AssertionError("Cost-class capacity bypassed global absolute ceiling");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modelWithCollisionConsumesBothResourceBudgets(GameTestHelper helper) {
        var model = net.kofllee.pyrovfx.client.vfx.VfxParticleCostClass.CPU_MODEL;
        var collision = net.kofllee.pyrovfx.client.vfx.VfxParticleCostClass.CPU_COLLISION;
        var basic = net.kofllee.pyrovfx.client.vfx.VfxParticleCostClass.CPU_BASIC;
        var global = new VfxGlobalBudget();
        int[] active = new int[4];
        active[model.ordinal()] = net.kofllee.pyrovfx.client.vfx.VfxBudgetConfig.classLimit(model) - 3;
        active[collision.ordinal()] = net.kofllee.pyrovfx.client.vfx.VfxBudgetConfig.classLimit(collision) - 20;
        global.beginTick(active[model.ordinal()] + 50, false, 500_000, 10_000, active);
        int mask = model.bit() | collision.bit() | basic.bit();
        if (global.reserveSpawns(10, mask) != 3
                || global.remainingFor(model) != 0
                || global.remainingFor(collision) != 17
                || global.admittedFor(model) != 3
                || global.admittedFor(collision) != 3
                || global.admittedFor(basic) != 3) {
            throw new AssertionError("Combined model/collision workload escaped its shared resource limits");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void parametricRotatingModelUsesDenseWithLegacyParity(GameTestHelper helper) {
        var definition = VfxDefinitionParser.parse(
                ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "dense_rotating_parametric"),
                JsonParser.parseString("""
                {"format":"pyro_vfx:1","lifetime":{"active_ticks":12},"emitters":[{
                  "id":"model","emitter_lifetime":{"active_ticks":1},
                  "spawn_amount":{"mode":"instant","amount":1},
                  "spawn_shape":{"type":"point"},
                  "particle_lifetime":{"max_age_ticks":10},
                  "motion":{"mode":"parametric","parametric":{"offset":["particle.age * 0.2",0,0]}},
                  "rotation":{"mode":"dynamic","dynamic":{
                    "start_rotation":[0,12,0],"angular_velocity":[0,90,0],
                    "angular_acceleration":[0,20,0],"angular_drag":0.3}},
                  "render":{"type":"model","model":{"source":"custom",
                    "model":"pyro_vfx:vfx/smoke_cube","render_layer":"solid"},
                    "color":["1.0 - particle.normalized_age",0.8,0.7,1],
                    "scale":[1,1,1]}
                }]}
                """).getAsJsonObject());
        ClientVfxInstance dense = new ClientVfxInstance(
                definition, Vec3.ZERO, VfxPlayOptions.empty(), 19L);
        String original = System.getProperty("pyrovfx.cpu.forceLegacy");
        ClientVfxInstance legacy;
        try {
            System.setProperty("pyrovfx.cpu.forceLegacy", "true");
            legacy = new ClientVfxInstance(definition, Vec3.ZERO, VfxPlayOptions.empty(), 19L);
        } finally {
            if (original == null) System.clearProperty("pyrovfx.cpu.forceLegacy");
            else System.setProperty("pyrovfx.cpu.forceLegacy", original);
        }
        if (dense.emitters().getFirst().densePool() == null
                || legacy.emitters().getFirst().densePool() != null)
            throw new AssertionError("Rotating parametric model did not select expected CPU backend");
        for (int tick = 0; tick < 5; tick++) {
            dense.tick(null);
            legacy.tick(null);
            var a = dense.emitters().getFirst().densePool().snapshot(0);
            var b = legacy.emitters().getFirst().legacyParticles().getFirst();
            checkClose(b.position().x, a.position().x);
            checkClose(b.rotation().y, a.rotation().y);
            checkClose(b.interpolatedRotation(0.5f).y, a.interpolatedRotation(0.5f).y);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void worldLitSpritesAndModelsKeepDenseSimulation(GameTestHelper helper) {
        var definition = VfxDefinitionParser.parse(
                ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "dense_lit"),
                JsonParser.parseString("""
                {"format":"pyro_vfx:1","emitters":[
                  {"id":"lit_sprite","spawn_shape":{"type":"point"},
                   "render":{"type":"sprite","environment_lighting":true,
                     "sprite":{"texture":"minecraft:textures/particle/generic_0.png"}}},
                  {"id":"lit_model","spawn_shape":{"type":"point"},
                   "render":{"type":"model","environment_lighting":true,
                     "model":{"source":"custom","model":"pyro_vfx:vfx/smoke_cube",
                              "render_layer":"solid"}}}
                ]}
                """).getAsJsonObject());
        for (var emitter : definition.emitters()) {
            if (!VfxDenseSpritePool.supports(emitter)) {
                throw new AssertionError("Light sampling should belong to renderer, not force Legacy simulation");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void backendReportsExplainSimulationAndRenderingConstraints(GameTestHelper helper) {
        var definition = VfxDefinitionParser.parse(
                ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "routing_explanation"),
                JsonParser.parseString("""
                {"format":"pyro_vfx:1","emitters":[
                  {"id":"lit_rotating","spawn_shape":{"type":"point"},
                   "rotation":{"mode":"dynamic","dynamic":{"angular_velocity":[0,90,0]}},
                   "render":{"type":"sprite","environment_lighting":true,
                      "sprite":{"texture":"minecraft:textures/particle/generic_0.png"}}},
                  {"id":"colliding_model","spawn_shape":{"type":"point"},
                   "motion":{"mode":"dynamic","dynamic":{"speed":0.5},"collision":{"collide":true}},
                   "render":{"type":"model","model":{"source":"custom",
                     "model":"pyro_vfx:vfx/smoke_cube","render_layer":"solid"}}}
                ]}
                """).getAsJsonObject());
        var instance = new ClientVfxInstance(definition, Vec3.ZERO, VfxPlayOptions.empty(), 1L);
        var a = instance.emitters().get(0);
        var b = instance.emitters().get(1);
        if (!a.selectedBackend().equals("cpu_dense")
                || !a.backendReason().contains("lighting")
                || !a.backendReason().contains("rotation")
                || !b.selectedBackend().equals("cpu_legacy")
                || !b.backendReason().contains("collision")
                || (b.particleCostMask() & net.kofllee.pyrovfx.client.vfx.VfxParticleCostClass.CPU_MODEL.bit()) == 0) {
            throw new AssertionError("Emitter route lacks actionable backend and cost diagnostics");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rotatedAdditiveSpritesAreEligibleForInstancing(GameTestHelper helper) {
        var definition = VfxDefinitionParser.parse(
                ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "sprite_rotation_gpu"),
                JsonParser.parseString("""
                {"format":"pyro_vfx:1","emitters":[{
                  "id":"rotating","spawn_shape":{"type":"point"},
                  "rotation":{"mode":"dynamic","dynamic":{"angular_velocity":[23,45,67]}},
                  "render":{"type":"sprite","facing":"camera",
                    "sprite":{"texture":"minecraft:textures/particle/generic_0.png",
                              "blend_mode":"additive"}}}
                ]}
                """).getAsJsonObject());
        var emitter = definition.emitters().getFirst();
        if (!VfxDenseSpritePool.supports(emitter)
                || !net.kofllee.pyrovfx.client.render.VfxInstancedSpriteRenderer.supportsGeometry(emitter))
            throw new AssertionError("Rotated additive sprite unnecessarily requires CPU quad rendering");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void additiveAndCutoutModelsKeepDenseSimulation(GameTestHelper helper) {
        var definition = VfxDefinitionParser.parse(
                ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "dense_model_layers"),
                JsonParser.parseString("""
                {"format":"pyro_vfx:1","emitters":[
                  {"id":"additive","spawn_shape":{"type":"point"},
                    "render":{"type":"model","model":{"source":"custom",
                      "model":"pyro_vfx:vfx/ember_cube","render_layer":"additive"}}},
                  {"id":"cutout","spawn_shape":{"type":"point"},
                    "render":{"type":"model","model":{"source":"custom",
                      "model":"pyro_vfx:vfx/ember_cube","render_layer":"cutout"}}}
                ]}
                """).getAsJsonObject());
        for (var emitter : definition.emitters()) {
            if (!VfxDenseSpritePool.supports(emitter))
                throw new AssertionError("Non-translucent model render layer should not force Legacy simulation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void denseMemoryTelemetryTracksAllocatedCapacity(GameTestHelper helper) {
        var definition = VfxDefinitionParser.parse(
                ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "memory_dense"),
                JsonParser.parseString("""
                {"format":"pyro_vfx:1","emitters":[{
                  "id":"main","spawn_shape":{"type":"point"},
                  "motion":{"mode":"dynamic","dynamic":{"speed":1}},
                  "rotation":{"mode":"dynamic","dynamic":{"angular_velocity":[0,25,0]}},
                  "render":{"type":"sprite","sprite":{"texture":"minecraft:textures/particle/generic_0.png"}}
                }]}
                """).getAsJsonObject());
        var emitter = definition.emitters().getFirst();
        var pool = new VfxDenseSpritePool(emitter);
        if (pool.allocatedStateBytes() != 0) throw new AssertionError("Empty dense pool allocated particle storage");
        var effect = ClientVfxExpressionContexts.effectStart(Vec3.ZERO, 0.0,
                Map.of(), definition.curves(), RandomSource.create(2L), definition.program().variables());
        var context = ClientVfxExpressionContexts.emitterStart(effect, Vec3.ZERO, 0.5);
        pool.add(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 10, 0.5,
                new Vec3(1,1,1), new net.kofllee.pyrovfx.vfx.value.VfxColor(1,1,1,1),
                ClientVfxExpressionContexts.particleSpawn(context, Vec3.ZERO, 0.5));
        long allocated = pool.allocatedStateBytes();
        if (allocated <= 0 || allocated < pool.capacity() * 24L * Double.BYTES)
            throw new AssertionError("Memory telemetry missed dense SoA channels");
        pool.tick(context);
        if (pool.allocatedStateBytes() != allocated)
            throw new AssertionError("Memory telemetry should measure capacity, not active count");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void adaptiveSpawnsResetOnWorldExitAndDisable(GameTestHelper helper) {
        var adaptive = new VfxAdaptiveSpawnController();
        for (int tick = 0; tick < 30; tick++) {
            adaptive.observe(10_000_000L, 2_000, true, 2.0, 35);
        }
        if (adaptive.spawnScale() >= 1.0) throw new AssertionError("Missing overload detection");
        adaptive.observe(10_000_000L, 0, true, 2.0, 35);
        if (adaptive.spawnScale() != 1.0 || adaptive.samples() != 0
                || adaptive.constrainedTicks() != 0) {
            throw new AssertionError("Adaptive state leaked across world transitions");
        }
        for (int tick = 0; tick < 30; tick++) {
            adaptive.observe(10_000_000L, 2_000, true, 2.0, 35);
        }
        adaptive.observe(10_000_000L, 2_000, false, 2.0, 35);
        if (adaptive.spawnScale() != 1.0 || adaptive.effectiveSpawnLimit(0) != 0
                || adaptive.effectiveSpawnLimit(1) != 1) {
            throw new AssertionError("Disabling adaptation did not restore configured ceilings");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void adaptiveSpawnsIgnoreIsolatedCpuSpike(GameTestHelper helper) {
        var adaptive = new VfxAdaptiveSpawnController();
        for (int i = 0; i < 12; i++) adaptive.observe(250_000L, 2_000, true, 3.0, 25);
        adaptive.observe(100_000_000L, 2_000, true, 3.0, 25);
        for (int i = 0; i < 40; i++) adaptive.observe(250_000L, 2_000, true, 3.0, 25);
        if (adaptive.spawnScale() != 1.0 || adaptive.effectiveSpawnLimit(30_000) != 30_000) {
            throw new AssertionError("One CPU stall throttled many subsequent healthy ticks");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cachedCollisionBoundsMatchOriginalAabbIntersections(GameTestHelper helper) {
        var random = RandomSource.create(0xC0111510L);
        for (int i = 0; i < 1024; i++) {
            int x = random.nextIntBetweenInclusive(-100, 100);
            int y = random.nextIntBetweenInclusive(-30, 260);
            int z = random.nextIntBetweenInclusive(-100, 100);
            double minX = random.nextDouble() * 2 - 0.5;
            double minY = random.nextDouble() * 2 - 0.5;
            double minZ = random.nextDouble() * 2 - 0.5;
            var local = new AABB(minX,minY,minZ,
                    minX + 0.02 + random.nextDouble(),
                    minY + 0.02 + random.nextDouble(),
                    minZ + 0.02 + random.nextDouble());
            double deltaX = random.nextDouble() * 3 - 1.5;
            double deltaY = random.nextDouble() * 3 - 1.5;
            double deltaZ = random.nextDouble() * 3 - 1.5;
            var box = local.move(x + deltaX, y + deltaY, z + deltaZ);
            boolean original = local.move(x,y,z).intersects(box);
            boolean optimized = VfxCollisionQueryCache.intersects(local,x,y,z,box);
            if (original != optimized) {
                throw new AssertionError("Collision shape bound translation changed intersection semantics");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void multipartCollisionRejectsEmptyGaps(GameTestHelper helper) {
        var bounds = new AABB(0, 0, 0, 1, 1, 1);
        var parts = new AABB[] {
                new AABB(0, 0, 0, 0.2, 1, 1),
                new AABB(0.8, 0, 0, 1, 1, 1)
        };
        var middle = new AABB(12.4, 5.1, 3.2, 12.6, 5.9, 3.8);
        var left = new AABB(12.01, 5.1, 3.2, 12.15, 5.9, 3.8);
        if (net.kofllee.pyrovfx.client.vfx.VfxCollisionQueryCache.intersectsShape(
                    bounds, parts, 12, 5, 3, middle)
                || !net.kofllee.pyrovfx.client.vfx.VfxCollisionQueryCache.intersectsShape(
                    bounds, parts, 12, 5, 3, left)
                || net.kofllee.pyrovfx.client.vfx.VfxCollisionQueryCache.intersectsShape(
                    bounds, parts, 12, 5, 3, new AABB(15, 5, 3, 16, 6, 4))) {
            throw new AssertionError("Multipart collision broadphase gave a false positive or miss");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void renderDefinitionsAlwaysUsePyroOwnedParticles(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "render_ownership");
        JsonObject source = JsonParser.parseString("""
                {"format":"pyro_vfx:1","emitters":[{
                  "id":"one","spawn_shape":{"type":"point"},
                  "render":{"type":"sprite","sprite":{
                    "texture":"minecraft:textures/particle/generic_0.png"}}
                }]}
                """).getAsJsonObject();
        var owned = VfxDefinitionParser.parse(id, source);
        if (owned.emitters().getFirst().render().type()
                != net.kofllee.pyrovfx.vfx.type.VfxRenderType.SPRITE) {
            throw new AssertionError("Sprite is not owned by Pyro VFX");
        }
        source.getAsJsonArray("emitters").get(0).getAsJsonObject()
                .getAsJsonObject("render").addProperty("type", "minecraft_particle");
        try {
            VfxDefinitionParser.parse(id, source);
            throw new AssertionError("Removed vanilla ParticleEngine bridge was accepted");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("was removed")) throw expected;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void globalBudgetIsSharedAcrossIndependentEffects(GameTestHelper helper) {
        var global = new VfxGlobalBudget();
        // Explicit historical thresholds validate *sharing* independently
        // of the now much higher configurable production ceilings.
        global.beginTick(0, false, 80_000, 30_000);
        var first = new VfxRuntimeBudget();
        var second = new VfxRuntimeBudget();
        first.beginTick(0, false, global);
        second.beginTick(0, false, global);
        if (first.reserveSpawns(20_000) != 20_000
                || second.reserveSpawns(20_000) != 10_000
                || global.admittedSpawns() != 30_000
                || global.rejectedSpawns() != 10_000) {
            throw new AssertionError("Shared per-tick spawn admission failed");
        }
        global.beginTick(79_900, false, 80_000, 30_000);
        first.beginTick(0, false, global);
        second.beginTick(0, false, global);
        if (first.reserveSpawns(80) != 80
                || second.reserveSpawns(80) != 20
                || global.remainingActive() != 0) {
            throw new AssertionError("Global active cap bypassed by concurrent effects");
        }
        global.beginTick(0, false);
        first.beginTick(0, false, global);
        second.beginTick(0, false, global);
        for (int i = 0; i < VfxRuntimeBudget.MAX_EVENTS_PER_TICK; i++) {
            if (!first.enterEvent() || !second.enterEvent()) {
                throw new AssertionError("Event budget exhausted too early");
            }
        }
        var third = new VfxRuntimeBudget();
        third.beginTick(0, false, global);
        if (third.hasEventBudget() || third.enterEvent()
                || global.admittedEvents() != VfxGlobalBudget.MAX_EVENTS_PER_TICK
                || global.rejectedEvents() != 1) {
            throw new AssertionError("Global event cap bypassed");
        }
        if (global.admitInstance(VfxGlobalBudget.MAX_INSTANCES)
                || global.rejectedInstances() != 1) {
            throw new AssertionError("Active effect admission not bounded");
        }
        global.reset();
        global.beginTick(0, true);
        if (global.reserveSpawns(VfxRuntimeBudget.HEAVY_PARTICLES)
                != VfxRuntimeBudget.HEAVY_PARTICLES
                || global.rejectedSpawns() != 0) {
            throw new AssertionError("Explicit developer heavy benchmark was capped");
        }
        if (global.admitInstance(VfxGlobalBudget.MAX_INSTANCES)) {
            throw new AssertionError("Heavy benchmark must not disable the effect-count cap");
        }
        global.reset();
        global.beginTick(0, false, 2_000_000, 500_000);
        if (global.reserveSpawns(400_000) != 400_000
                || global.reserveSpawns(200_000) != 100_000
                || global.remainingActive() != 1_500_000
                || global.remainingSpawns() != 0) {
            throw new AssertionError("Configurable normal-gameplay budgets did not scale safely");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void analyticMotionMatchesDiscreteCpuRecurrence(GameTestHelper helper) {
        var plan = new VfxAnalyticPlan(true,"test",0.005,-0.002,0.001,Math.exp(-0.4/20.0),140);
        double px=0, vx=0.125;
        for(int n=1;n<=140;n++) {
            vx=(vx+plan.accelerationX())*plan.dragMultiplier();
            px+=vx;
            double[] c=plan.coefficientsAt(n);
            checkClose(px,0.125*c[0]+plan.accelerationX()*c[1]);
        }
        double[] previous=plan.coefficientsAt(16);
        double[] next=plan.coefficientsAt(17);
        double[] half=plan.coefficients(17,0.5f);
        checkClose((previous[0]+next[0])*0.5,half[0]);
        checkClose((previous[1]+next[1])*0.5,half[1]);
        var noDrag=new VfxAnalyticPlan(true,"test",0.025,0,0,1,200);
        checkClose(10.0,noDrag.coefficientsAt(10)[0]);
        checkClose(55.0,noDrag.coefficientsAt(10)[1]);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void analyticCapabilitySelectionPreservesFallback(GameTestHelper helper) {
        String json = """
            {"format":"pyro_vfx:1",
             "parameters":{"count":20},
             "emitters":[{"id":"particles","emitter_lifetime":{"mode":"once","active_ticks":1},
               "spawn_amount":{"mode":"instant","amount":"param.count"},
               "spawn_shape":{"type":"sphere","radius":2},
               "particle_lifetime":{"max_age_ticks":40},
               "motion":{"mode":"static"},
               "render":{"type":"sprite","environment_lighting":false,
                         "sprite":{"texture":"minecraft:textures/particle/generic_0.png",
                                   "blend_mode":"additive"},
                         "color":"#FFFFFFFF","scale":[0.2,0.2,1]}}]}
            """;
        JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
        var id=ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID,"analytic_selection");
        var definition=VfxDefinitionParser.parse(id,parsed);
        var plan=VfxAnalyticPlan.analyse(definition.emitters().getFirst(),true);
        if(!plan.supported()) throw new AssertionError(plan.reason());
        if(VfxAnalyticPlan.analyse(definition.emitters().getFirst(),false).supported())
            throw new AssertionError("GPU unavailable must use CPU");
        var pool=new VfxAnalyticSpritePool(definition.emitters().getFirst(),plan);
        var start=ClientVfxExpressionContexts.effectStart(Vec3.ZERO,0,
                Map.of("count",20.0),definition.curves(),RandomSource.create(1),
                definition.program().variables());
        var scope=ClientVfxExpressionContexts.emitterStart(start,Vec3.ZERO,0.5);
        RandomSource first=RandomSource.create(100), second=RandomSource.create(100);
        pool.spawn(20,Vec3.ZERO,scope,first);
        for(int i=0;i<20;i++) {
            second.nextDouble();
            net.kofllee.pyrovfx.client.vfx.sampling.VfxSpawnPositionSampler.sample(
                    Vec3.ZERO,definition.emitters().getFirst().spawnShape(),scope,second);
        }
        checkClose(second.nextDouble(),first.nextDouble());
        if(pool.liveCount()!=20 || pool.data().remaining()!=20*24)
            throw new AssertionError("GPU birth state count or packed stride mismatch");
        for(int t=1;t<=40;t++) pool.tick();
        if(pool.liveCount()!=0) throw new AssertionError("Analytic particle lifetime mismatch");

        parsed.getAsJsonArray("emitters").get(0).getAsJsonObject()
                .getAsJsonObject("render").add("scale",JsonParser.parseString("[\"param.count\",0.2,1]"));
        var mutable=VfxDefinitionParser.parse(id,parsed);
        boolean rejected=!VfxAnalyticPlan.analyse(mutable.emitters().getFirst(),true).supported();
        if(!rejected) throw new AssertionError("Mutable appearance must remain CPU");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void analyticUvAnimationSharesFramesAndRejectsRandomStarts(GameTestHelper helper) {
        String source = """
            {"format":"pyro_vfx:1","emitters":[{
              "id":"uv","emitter_lifetime":{"mode":"once","active_ticks":1},
              "spawn_shape":{"type":"point"},
              "particle_lifetime":{"max_age_ticks":40},
              "motion":{"mode":"static"},
              "render":{"type":"sprite","sprite":{
                "texture":"minecraft:textures/particle/generic_0.png",
                "blend_mode":"additive",
                "uv":{"mode":"animated","texture_size":[64,16,0],
                  "uv_start":[0,0,0],"uv_size":[16,16,0],
                  "uv_step":[16,0,0],"frame_count":4,"fps":20,
                  "loop":true,"random_start_frame":false}
              },"color":"#FFFFFFFF","scale":[0.2,0.2,1]}
            }]}
            """;
        JsonObject parsed = JsonParser.parseString(source).getAsJsonObject();
        var id = ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "analytic_uv");
        var effect = VfxDefinitionParser.parse(id, parsed);
        var emitter = effect.emitters().getFirst();
        var plan = VfxAnalyticPlan.analyse(emitter, true);
        if (!plan.supported()) throw new AssertionError(plan.reason());
        var pool = new VfxAnalyticSpritePool(emitter, plan);
        var scope = ClientVfxExpressionContexts.effectStart(Vec3.ZERO, 0,
                Map.of(), effect.curves(), RandomSource.create(4), effect.program().variables());
        pool.spawn(1, Vec3.ZERO, scope, RandomSource.create(5));
        checkClose(0, pool.uvRect().u0());
        checkClose(0.25, pool.uvRect().u1());
        pool.tick();
        pool.tick();
        checkClose(0.5, pool.uvRect().u0());
        checkClose(0.75, pool.uvRect().u1());
        pool.close();
        parsed.getAsJsonArray("emitters").get(0).getAsJsonObject()
                .getAsJsonObject("render").getAsJsonObject("sprite")
                .getAsJsonObject("uv").addProperty("random_start_frame", true);
        var random = VfxDefinitionParser.parse(id, parsed);
        if (VfxAnalyticPlan.analyse(random.emitters().getFirst(), true).supported()) {
            throw new AssertionError("Per-particle random UV phase cannot share one GPU uniform");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void analyticSharedAgeAppearanceMatchesCpuTickInterpolation(GameTestHelper helper) {
        var id = ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "analytic_age");
        JsonObject json = JsonParser.parseString("""
            {"format":"pyro_vfx:1","emitters":[{
              "id":"burst","emitter_lifetime":{"mode":"once","active_ticks":1},
              "spawn_shape":{"type":"point"},
              "particle_lifetime":{"max_age_ticks":40},"motion":{"mode":"static"},
              "render":{"type":"sprite","sprite":{
                  "texture":"minecraft:textures/particle/generic_0.png","blend_mode":"additive"},
                "color":[1,0.5,0.25,"0.8 - particle.normalized_age * 0.5"],
                "scale":["0.5 + particle.age * 0.1",0.2,1]}
            }]}
            """).getAsJsonObject();
        var definition = VfxDefinitionParser.parse(id, json);
        var plan = VfxAnalyticPlan.analyse(definition.emitters().getFirst(), true);
        if (!plan.supported()) throw new AssertionError(plan.reason());
        var pool = new VfxAnalyticSpritePool(definition.emitters().getFirst(), plan);
        var scope = ClientVfxExpressionContexts.effectStart(Vec3.ZERO, 0,
                Map.of(), definition.curves(), RandomSource.create(21),
                definition.program().variables());
        pool.spawn(1, Vec3.ZERO, scope, RandomSource.create(22));
        pool.updateAppearance(0.5f);
        checkClose(0.5, pool.scale().x);
        checkClose(0.8, pool.color().a());
        pool.tick(); // first tick evaluates expression at age=0
        pool.updateAppearance(0.5f);
        checkClose(0.5, pool.scale().x);
        pool.tick(); // second tick evaluates age=1; interpolate previous/current
        pool.updateAppearance(0.5f);
        checkClose(0.55, pool.scale().x);
        checkClose(0.8 - 0.25 / 40, pool.color().a());
        pool.close();

        var render = json.getAsJsonArray("emitters").get(0).getAsJsonObject()
                .getAsJsonObject("render");
        render.add("color", JsonParser.parseString("[1,0.5,0.25,\"particle.random\"]"));
        if (VfxAnalyticPlan.analyse(VfxDefinitionParser.parse(id, json)
                .emitters().getFirst(), true).supported()) {
            throw new AssertionError("Particle random cannot be a shared GPU uniform");
        }
        render.add("color", JsonParser.parseString("[1,0.5,0.25,\"particle.pos.x\"]"));
        if (VfxAnalyticPlan.analyse(VfxDefinitionParser.parse(id, json)
                .emitters().getFirst(), true).supported()) {
            throw new AssertionError("Particle position cannot be a shared GPU uniform");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void analyticSharedAgeCurvesUseUniformWithoutRandomFallback(GameTestHelper helper) {
        var id = ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "analytic_curve");
        var base = JsonParser.parseString("""
                {"format":"pyro_vfx:1",
                 "curves":{"fade":{"type":"linear","points":[[0,1],[1,0]]}},
                 "emitters":[{"id":"curve","emitter_lifetime":{"mode":"once","active_ticks":1},
                   "spawn_shape":{"type":"point"},
                   "particle_lifetime":{"max_age_ticks":40},
                   "motion":{"mode":"static"},
                   "render":{"type":"sprite","sprite":{
                     "texture":"minecraft:textures/particle/generic_0.png","blend_mode":"additive"},
                     "color":[1,1,1,"curve.fade(particle.normalized_age)"],
                     "scale":[0.1,0.1,1]}}]}
                """).getAsJsonObject();
        var definition = VfxDefinitionParser.parse(id, base);
        var plan = VfxAnalyticPlan.analyse(definition.emitters().getFirst(), true);
        if (!plan.supported()) throw new AssertionError(plan.reason());
        var pool = new VfxAnalyticSpritePool(definition.emitters().getFirst(), plan);
        var scope = ClientVfxExpressionContexts.effectStart(Vec3.ZERO, 0,
                Map.of(), definition.curves(), RandomSource.create(31),
                definition.program().variables());
        pool.spawn(1, Vec3.ZERO, scope, RandomSource.create(32));
        pool.tick();
        pool.updateAppearance(0.5f);
        checkClose(1.0, pool.color().a());
        pool.tick();
        pool.updateAppearance(0.5f);
        checkClose(1.0 - 0.5 / 40, pool.color().a());

        base.getAsJsonArray("emitters").get(0).getAsJsonObject()
                .getAsJsonObject("render")
                .add("color", JsonParser.parseString("[1,1,1,\"curve.fade(particle.random)\"]"));
        if (VfxAnalyticPlan.analyse(VfxDefinitionParser.parse(id, base)
                .emitters().getFirst(), true).supported()) {
            throw new AssertionError("Random-dependent curve was admitted to shared GPU uniforms");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void conservativeRenderFrustumAndSubpixelLod(GameTestHelper helper) {
        Matrix4f identity = new Matrix4f();
        if (!VfxRenderVisibility.visible(identity, 0, 0, 0, 0.25)
                || !VfxRenderVisibility.visible(identity, 1.05, 0, 0, 0.10)
                || VfxRenderVisibility.visible(identity, 3, 0, 0, 0.10)
                || VfxRenderVisibility.visible(identity, 0, 0, 4, 0.10)) {
            throw new AssertionError("Frustum culling discarded a boundary sprite or kept a far-off sprite");
        }
        if (!VfxRenderVisibility.exceedsPixelFootprint(identity, 0, 0, 0,
                0.001, 480, 0.25)
                || VfxRenderVisibility.exceedsPixelFootprint(identity, 0, 0, 0,
                0.00001, 480, 0.25)
                || !VfxRenderVisibility.exceedsPixelFootprint(identity, 0, 0, 0,
                0.00001, 480, 0)) {
            throw new AssertionError("LOD should remove only subpixel geometry when enabled");
        }
        helper.succeed();
    }

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

    @GameTest(template = "empty")
    public static void denseSpriteReplayMatchesLegacyPhysicsAppearanceAndRandom(GameTestHelper helper) {
        for (String motionMode : new String[]{"static", "dynamic"}) {
            String dynamicFields = motionMode.equals("dynamic")
                    ? ",\"dynamic\":{\"direction\":\"custom\",\"custom_direction\":[0,1,0],"
                    + "\"speed\":1,\"acceleration\":[\"random.value()*0.1\",-0.1,0],"
                    + "\"linear_drag\":\"0.4+random.value()*0.1\"}" : "";
            String source = """
                    {"format":"pyro_vfx:1","parameters":{"power":3},
                    "emitters":[{"id":"main","spawn_shape":{"type":"point"},
                    "particle_lifetime":{"max_age_ticks":6},
                    "motion":{"mode":"MODE"FIELDS},
                    "render":{"type":"sprite","sprite":{
                    "texture":"minecraft:textures/particle/generic_0.png"},
                    "scale":["param.power+particle.normalized_age+random.value()*0.01",0.25,1],
                    "color":[1,0.25,0.5,"1-particle.normalized_age+random.value()*0.01"]}}]}
                    """.replace("MODE", motionMode).replace("FIELDS", dynamicFields);
            var definition = VfxDefinitionParser.parse(ResourceLocation.fromNamespaceAndPath(
                    PyroVfx.MOD_ID, "dense_replay_" + motionMode),
                    JsonParser.parseString(source).getAsJsonObject());
            var emitter = definition.emitters().getFirst();
            if (!VfxDenseSpritePool.supports(emitter)) {
                throw new AssertionError("Expected eligible dense sprite: " + motionMode);
            }
            RandomSource denseRandom = RandomSource.create(983221L);
            RandomSource oldRandom = RandomSource.create(983221L);
            var denseEffect = ClientVfxExpressionContexts.effectStart(Vec3.ZERO, 0,
                    Map.of("power", 3.0), definition.curves(), denseRandom,
                    definition.program().variables());
            var oldEffect = ClientVfxExpressionContexts.effectStart(Vec3.ZERO, 0,
                    Map.of("power", 3.0), definition.curves(), oldRandom,
                    definition.program().variables());
            var denseContext = ClientVfxExpressionContexts.emitterStart(denseEffect, Vec3.ZERO, 0.5);
            var oldContext = ClientVfxExpressionContexts.emitterStart(oldEffect, Vec3.ZERO, 0.5);
            Vec3 spawn = new Vec3(1, 7, 3);
            Vec3 velocity = motionMode.equals("dynamic") ? new Vec3(0.01, 0.02, 0.03) : Vec3.ZERO;
            var denseSpawn = ClientVfxExpressionContexts.particleSpawn(denseContext, spawn, 0.25);
            var oldSpawn = ClientVfxExpressionContexts.particleSpawn(oldContext, spawn, 0.25);
            var denseStart = ClientVfxExpressionContexts.particleTick(
                    denseContext, spawn, spawn, spawn, velocity, Vec3.ZERO, Vec3.ZERO,
                    0, 6, 0.25, new Vec3(1,1,1));
            var oldStart = ClientVfxExpressionContexts.particleTick(
                    oldContext, spawn, spawn, spawn, velocity, Vec3.ZERO, Vec3.ZERO,
                    0, 6, 0.25, new Vec3(1,1,1));
            var initialDenseScale = emitter.render().appearance().scale().evaluate(denseStart).toVec3();
            var initialDenseColor = emitter.render().appearance().color().evaluate(denseStart);
            var initialOldScale = emitter.render().appearance().scale().evaluate(oldStart).toVec3();
            var initialOldColor = emitter.render().appearance().color().evaluate(oldStart);
            VfxDenseSpritePool pool = new VfxDenseSpritePool(emitter);
            pool.add(spawn, velocity, 6, 0.25, initialDenseScale, initialDenseColor, denseSpawn);
            ClientVfxParticle old = new ClientVfxParticle(emitter, spawn, velocity, Vec3.ZERO,
                    Vec3.ZERO, 6, 0.25, initialOldScale, initialOldColor, oldSpawn);
            for (int tick = 0; tick < 6; tick++) {
                old.tick(null, oldContext);
                pool.tick(denseContext);
                if (tick == 5) {
                    if (pool.size() != 0 || !old.isDead()) {
                        throw new AssertionError("Dense compaction lifetime mismatch");
                    }
                    break;
                }
                var dense = pool.snapshot(0);
                checkClose(old.position().x, dense.position().x);
                checkClose(old.position().y, dense.position().y);
                checkClose(old.position().z, dense.position().z);
                checkClose(old.velocity().y, dense.velocity().y);
                checkClose(old.scale().x, dense.scale().x);
                checkClose(old.color().a(), dense.color().a());
                checkClose(old.previousPosition().y, dense.previousPosition().y);
                checkClose(old.interpolatedScale(0.25f).x, dense.interpolatedScale(0.25f).x);
                if (old.age() != dense.age()) throw new AssertionError("Dense age mismatch");
            }
            checkClose(oldRandom.nextDouble(), denseRandom.nextDouble());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void collisionCommandQueueDrainsInOrderAndRespectsBudget(GameTestHelper helper) {
        var budget = new VfxRuntimeBudget();
        budget.beginTick(0, false);
        var context = VfxExpressionContext.root().setNumber("param.stage", 0);
        var queue = new VfxEventCommandQueue();
        var runtime = new VfxEventRuntime() {
            @Override public boolean enterEvent() { return budget.enterEvent(); }
            @Override public boolean hasEventBudget() { return budget.hasEventBudget(); }
            @Override public int reserveSpawns(int amount) { return budget.reserveSpawns(amount); }
            @Override public void setParameter(String id, double value) {}
        };
        Map<String, VfxEventDefinition> events = Map.of(
                "increment", VfxEventDefinition.setParam("stage",
                        VfxNumberExpression.expression("param.stage+1")));
        queue.enqueue("increment", Vec3.ZERO);
        queue.enqueue("increment", Vec3.ZERO);
        queue.drain(events, Map.of(), null, Vec3.ZERO, context, runtime, RandomSource.create(12L));
        checkClose(2, context.getNumber("param.stage"));
        if (queue.size() != 0) throw new AssertionError("Event queue retained drained commands");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void compiledProgramRecordsDependenciesAndFoldsOnlyPureMath(GameTestHelper helper) {
        VfxProgram program;
        VfxNumberExpression constant;
        VfxNumberExpression changing;
        try (var compilation = VfxExpressionCompiler.beginProgram()) {
            constant = VfxNumberExpression.expression("math.lerp(2, 10, 0.25)");
            changing = VfxNumberExpression.expression("param.stage * 2 + particle.normalized_age");
            VfxNumberExpression.expression("effect.age + emitter.age");
            VfxNumberExpression.expression("particle.random + spawn.pos.x");
            VfxNumberExpression.expression("random.value()");
            VfxNumberExpression.expression("curve.ease(particle.age)");
            program = compilation.program();
        }
        if (program.expressions().size() != 6 || !constant.isConstant()
                || changing.isConstant()) {
            throw new AssertionError("Pure-math folding or program expression count is incorrect");
        }
        checkClose(4.0, constant.evaluateDouble(null));
        if (!program.expressions().get(0).constant()
                || program.expressions().get(1).frequency() != VfxProgram.Frequency.PARTICLE_TICK
                || !program.expressions().get(1).parameterDependent()
                || program.expressions().get(2).frequency() != VfxProgram.Frequency.EMITTER_TICK
                || program.expressions().get(3).frequency() != VfxProgram.Frequency.PARTICLE_SPAWN
                || !program.expressions().get(4).randomCall()
                || program.expressions().get(4).constant()
                || !program.expressions().get(5).curveCall()) {
            throw new AssertionError("Compiled program dependency analysis is inaccurate");
        }
        VfxExpressionContext root = VfxExpressionContext.root(program.variables())
                .setNumber("param.stage", 3.0);
        VfxExpressionContext child = root.child()
                .setNumber("particle.normalized_age", 0.25);
        checkClose(6.25, changing.evaluateDouble(child));
        child.updateNumber("param.stage", 7.0);
        checkClose(14.25, changing.evaluateDouble(child));
        checkClose(7.0, root.getNumber("param.stage"));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void compiledExpressionLanguageMatchesLegacyAndRandomOrder(GameTestHelper helper) {
        String[] sources = {
                "math.sin(particle.age) + math.cos(param.x) * 3",
                "particle.age < 2 && param.x > 1 ? math.pow(3, 2) : math.abs(-5)",
                "math.clamp(param.x + particle.age, 0, 8)",
                "math.smoothstep(0, 10, particle.age)",
                "math.wrap_degrees(320) + math.angle_delta(350, 10)",
                "math.lerprotate(350, 10, 0.25)",
                "random.range(2, 5) + random.value()",
                "random.int(1, 6) + random.die(0, 1, 3)",
                "random.die_int(1, 4, 2)",
                "0 && random.value() ? 15 : 7"
        };
        for (String source : sources) {
            VfxNumberExpression legacy = VfxNumberExpression.expression(source);
            VfxNumberExpression compiled;
            VfxProgram program;
            try (var compilation = VfxExpressionCompiler.beginProgram()) {
                compiled = VfxNumberExpression.expression(source);
                program = compilation.program();
            }
            var legacyContext = VfxExpressionContext.root()
                    .setNumber("particle.age", 3.0).setNumber("param.x", 2.5)
                    .setRandom(RandomSource.create(556677L));
            var compiledContext = VfxExpressionContext.root(program.variables())
                    .setNumber("particle.age", 3.0).setNumber("param.x", 2.5)
                    .setRandom(RandomSource.create(556677L));
            for (int sample = 0; sample < 10; sample++) {
                double expected = legacy.evaluateDouble(legacyContext);
                double actual = compiled.evaluateDouble(compiledContext);
                if (Double.doubleToLongBits(expected) != Double.doubleToLongBits(actual)) {
                    throw new AssertionError("Expression mismatch: " + source
                            + " expected " + expected + ", got " + actual);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void immutableProgramBindsNamedCurvesAndSurvivesJsonMutation(GameTestHelper helper) {
        JsonObject json = JsonParser.parseString("""
                {"format":"pyro_vfx:1",
                 "curves":{"ease":{"type":"linear","points":[[0,0],[10,20]]}},
                 "parameters":{"strength":"curve.ease(effect.age)"},
                 "emitters":[]}
                """).getAsJsonObject();
        var definition = VfxDefinitionParser.parse(
                ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "compiled_curve"), json);
        if (definition.program().expressions().size() != 1
                || !definition.program().expressions().getFirst().curveCall()) {
            throw new AssertionError("Named curve was not included in compiled program metadata");
        }
        var context = ClientVfxExpressionContexts.effectStart(Vec3.ZERO, 0.0,
                Map.of(), definition.curves(), RandomSource.create(1L),
                definition.program().variables());
        context.setNumber("effect.age", 5.0);
        checkClose(10.0, definition.parameters().get("strength").value().evaluateDouble(context));

        json.getAsJsonObject("curves").getAsJsonObject("ease")
                .getAsJsonArray("points").get(1).getAsJsonArray().set(1, new com.google.gson.JsonPrimitive(999));
        checkClose(10.0, definition.parameters().get("strength").value().evaluateDouble(context));
        try {
            definition.program().expressions().clear();
            throw new AssertionError("Compiled expression list was mutable");
        } catch (UnsupportedOperationException expected) {
            // immutable compiled state
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void compiledParameterChangesReachParticleExpressionsOnNextTick(GameTestHelper helper) {
        String source = """
                {"format":"pyro_vfx:1","parameters":{"power":1},
                 "lifetime":{"mode":"once","active_ticks":6},
                 "events":{"boost":{"type":"set_param","param":"power","value":4}},
                 "triggers":[{"type":"timeline","time_ticks":1,"event":"boost"}],
                 "emitters":[{"id":"main","emitter_lifetime":{"mode":"once","active_ticks":1},
                 "spawn_amount":{"mode":"instant","amount":1},
                 "spawn_shape":{"type":"point"},
                 "particle_lifetime":{"max_age_ticks":6},
                 "motion":{"mode":"static"},
                 "render":{"type":"sprite","sprite":{"texture":"minecraft:textures/particle/generic_0.png"},
                           "scale":["param.power",1,1],"color":"#FFFFFFFF"}}]}
                """;
        var definition = VfxDefinitionParser.parse(
                ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "param_compiled_golden"),
                JsonParser.parseString(source).getAsJsonObject());
        var instance = new ClientVfxInstance(definition, Vec3.ZERO, VfxPlayOptions.empty(), 7L);
        instance.tick(null);
        checkClose(1, instance.particles().getFirst().scale().x);
        instance.tick(null);
        checkClose(4, instance.particles().getFirst().scale().x);
        instance.tick(null);
        checkClose(4, instance.particles().getFirst().scale().x);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void denseCompactionPreservesInsertionOrderAndSelectsSparseFallback(GameTestHelper helper) {
        JsonObject json = JsonParser.parseString("""
                {"format":"pyro_vfx:1","emitters":[{
                "id":"dense","spawn_shape":{"type":"point"},
                "motion":{"mode":"static"},
                "render":{"type":"sprite","sprite":{"texture":"minecraft:textures/particle/generic_0.png"}}}]}
                """).getAsJsonObject();
        var def = VfxDefinitionParser.parse(ResourceLocation.fromNamespaceAndPath(
                PyroVfx.MOD_ID, "dense_compaction"), json);
        var emitter = def.emitters().getFirst();
        if (!VfxDenseSpritePool.supports(emitter)) {
            throw new AssertionError("Static sprite should route to dense backend");
        }
        var effect = ClientVfxExpressionContexts.effectStart(Vec3.ZERO, 0,
                Map.of(), def.curves(), RandomSource.create(2L), def.program().variables());
        var context = ClientVfxExpressionContexts.emitterStart(effect, Vec3.ZERO, 0.5);
        var pool = new VfxDenseSpritePool(emitter);
        for (int i = 1; i <= 3; i++) {
            Vec3 position = new Vec3(i, 4, 0);
            pool.add(position, Vec3.ZERO, i == 1 ? 1 : 3, 0.5,
                    new Vec3(1, 1, 1),
                    new net.kofllee.pyrovfx.vfx.value.VfxColor(1, 1, 1, 1),
                    ClientVfxExpressionContexts.particleSpawn(context, position, 0.5));
        }
        int capacity = pool.capacity();
        pool.tick(context);
        if (pool.size() != 2 || pool.capacity() != capacity) {
            throw new AssertionError("Dense compaction broke capacity reuse");
        }
        checkClose(2, pool.snapshot(0).position().x);
        checkClose(3, pool.snapshot(1).position().x);
        pool.tick(context);
        if (pool.size() != 2) {
            throw new AssertionError("Unexpected early expiry");
        }
        pool.tick(context);
        if (pool.size() != 0) {
            throw new AssertionError("Compaction failed to remove expired particles");
        }
        JsonObject emitterJson = json.getAsJsonArray("emitters").get(0).getAsJsonObject();
        emitterJson.add("motion", JsonParser.parseString("""
                {"mode":"dynamic","dynamic":{"speed":1}, "collision":{"collide":true}}
                """));
        var colliding = VfxDefinitionParser.parse(ResourceLocation.fromNamespaceAndPath(
                PyroVfx.MOD_ID, "collision_fallback"), json);
        if (VfxDenseSpritePool.supports(colliding.emitters().getFirst())) {
            throw new AssertionError("Colliding sprites must not enter the world-free dense backend");
        }
        helper.succeed();
    }
}
