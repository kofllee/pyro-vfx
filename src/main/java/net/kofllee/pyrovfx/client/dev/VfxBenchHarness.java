package net.kofllee.pyrovfx.client.dev;

import com.mojang.blaze3d.platform.NativeImage;
import com.sun.management.ThreadMXBean;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.client.vfx.sampling.VfxModelSpawnShapeSampler;
import net.kofllee.pyrovfx.client.vfx.VfxPlayOptions;
import net.kofllee.pyrovfx.client.vfx.VfxRuntimeBudget;
import net.kofllee.pyrovfx.client.vfx.VfxGlobalBudget;
import net.kofllee.pyrovfx.client.render.VfxAnalyticSpriteRenderer;
import net.kofllee.pyrovfx.vfx.definition.VfxDefinition;
import net.kofllee.pyrovfx.vfx.resource.VfxRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import org.lwjgl.opengl.GL11;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class VfxBenchHarness {
    private static final boolean ENABLED = !FMLEnvironment.production;
    // A clear air volume above terrain avoids invisible, block-occluded particles.
    private static final Vec3 ORIGIN = new Vec3(0.5, 140.0, 0.5);
    private static final Vec3 CAMERA = new Vec3(0.5, 141.5, -12.0);
    private static final String[] AUTO = System.getProperty("pyrovfx.bench.auto", "").split(",");
    private static final ThreadMXBean ALLOCATIONS = ENABLED ? allocationBean() : null;

    private static Run current;
    private static int autoIndex;
    private static boolean museumBuiltThisSession;
    private static int autoExitTicks = -1;

    private VfxBenchHarness() {}

    private static net.kofllee.pyrovfx.museum.VfxMuseumCatalog.Exhibit museumExhibit(String scenario) {
        if (scenario.equals("museum_model_entity_close_pan"))
            return net.kofllee.pyrovfx.museum.VfxMuseumCatalog.find("model_entity");
        if (scenario.startsWith("museum_translucency_"))
            return net.kofllee.pyrovfx.museum.VfxMuseumCatalog.find("model_translucent");
        if (scenario.equals("museum_camera_front") || scenario.equals("museum_camera_turn_90"))
            return net.kofllee.pyrovfx.museum.VfxMuseumCatalog.find("model_custom");
        if (scenario.equals("museum_smoke_camera_front") || scenario.equals("museum_smoke_camera_turn_90"))
            return net.kofllee.pyrovfx.museum.VfxMuseumCatalog.find("smoke_alpha");
        if (scenario.equals("museum_self_emit_near"))
            return net.kofllee.pyrovfx.museum.VfxMuseumCatalog.find("self_emit");
        return scenario.startsWith("museum_")
                ? net.kofllee.pyrovfx.museum.VfxMuseumCatalog.find(scenario.substring(7)) : null;
    }

    private static Vec3 museumCamera(String scenario,
            net.kofllee.pyrovfx.museum.VfxMuseumCatalog.Exhibit exhibit) {
        if (scenario.equals("museum_model_entity_close_pan"))
            return new Vec3(exhibit.effectOrigin().getX() + 0.5,
                    exhibit.effectOrigin().getY() + 1.5, exhibit.effectOrigin().getZ() + 5.5);
        if (scenario.equals("museum_self_emit_near"))
            return new Vec3(exhibit.effectOrigin().getX() + 0.5,
                    exhibit.effectOrigin().getY() + 0.5,
                    exhibit.effectOrigin().getZ() + 2.0);
        return new Vec3(exhibit.base().getX() + 7.5,
                exhibit.base().getY() + 3.0, exhibit.base().getZ() + 13.3);
    }

    private static float museumYaw(String scenario, boolean inMuseum) {
        if (scenario.endsWith("camera_turn_90")) return 90;
        return inMuseum ? 180 : 0;
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static String start(String name) {
        if (!ENABLED) {
            return "Benchmark harness is disabled in production.";
        }
        if (current != null) {
            return "Benchmark already running: " + current.scenario.name();
        }
        VfxBenchScenarios.Scenario scenario = VfxBenchScenarios.get(name);
        if (scenario == null) {
            return "Unknown benchmark: " + name + ". Available: " + VfxBenchScenarios.all().keySet();
        }
        if (scenario.requiresOptIn() && !Boolean.getBoolean("pyrovfx.bench.allowHeavy")) {
            return "Refusing " + name + " on legacy runtime. Opt in with -Dpyrovfx.bench.allowHeavy=true.";
        }
        if (name.equals("global_budget_spike") && !Boolean.getBoolean("pyrovfx.bench.globalStress")) {
            return "Global-budget stress must be explicitly enabled with -PbenchGlobalStress=true.";
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return "A loaded integrated-server test world is required (no multiplayer benchmarking).";
        }
        VfxDefinition definition = VfxRegistry.get(scenario.effect());
        if (definition == null) {
            return "VFX definition not loaded: " + scenario.effect();
        }

        ClientVfxManager.clear();
        Run run = new Run(scenario, definition);
        int expectedWidth = Integer.getInteger("pyrovfx.bench.expectedWidth", -1);
        int expectedHeight = Integer.getInteger("pyrovfx.bench.expectedHeight", -1);
        if (expectedWidth > 0 && expectedHeight > 0
                && (minecraft.getWindow().getWidth() != expectedWidth
                    || minecraft.getWindow().getHeight() != expectedHeight)) {
            run.windowMismatch = "Requested benchmark window " + expectedWidth + "x" + expectedHeight
                    + " differs from actual window " + minecraft.getWindow().getWidth()
                    + "x" + minecraft.getWindow().getHeight();
        }
        if (Boolean.getBoolean("pyrovfx.bench.uncapped")) {
            run.originalVsync = minecraft.options.enableVsync().get();
            run.originalFpsLimit = minecraft.options.framerateLimit().get();
            minecraft.options.enableVsync().set(false);
            minecraft.getWindow().updateVsync(false);
            minecraft.options.framerateLimit().set(260);
        }
        current = run;
        UUID playerId = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                run.error = "Test player missing on integrated server";
                return;
            }
            run.originalPosition = player.position();
            run.originalYaw = player.getYRot();
            run.originalPitch = player.getXRot();
            run.originalGameMode = player.gameMode.getGameModeForPlayer();
            run.originalDayTime = player.serverLevel().getDayTime();
            player.serverLevel().setDayTime(6000L);
            if (scenario.name().startsWith("museum_")
                    && Boolean.getBoolean("pyrovfx.bench.museumBuild")
                    && !museumBuiltThisSession) {
                net.kofllee.pyrovfx.museum.VfxMuseumWorld.build(player.serverLevel());
                museumBuiltThisSession = true;
            }
            if (scenario.name().equals("museum_source_block")) {
                // Verify the REAL block provider, including chunk/block
                // lifetime checks. A missing museum must not silently turn
                // this into a fixed-position effect.
                var stand = net.kofllee.pyrovfx.museum.VfxMuseumCatalog.find("source_block");
                BlockPos anchor = stand.base().offset(7, 0, 5);
                BlockState original = player.serverLevel().getBlockState(anchor);
                if (original.isAir()) {
                    run.originalBlocks.put(anchor, original);
                    player.serverLevel().setBlockAndUpdate(anchor, Blocks.STONE.defaultBlockState());
                }
            }
            if (scenario.name().contains("collision") || scenario.name().equals("adaptive_steady")
                    || scenario.name().equals("soak_mixed")) {
                // Deterministic solid obstacle, independent of the test world's terrain.
                for (int x = -4; x <= 4; x++) {
                    for (int z = -4; z <= 4; z++) {
                        BlockPos pos = new BlockPos(x, 139, z);
                        run.originalBlocks.put(pos, player.serverLevel().getBlockState(pos));
                        player.serverLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                    }
                }
            }
            player.setGameMode(GameType.SPECTATOR);
            var museumExhibit = museumExhibit(scenario.name());
            Vec3 camera = museumExhibit == null ? CAMERA
                    : museumCamera(scenario.name(), museumExhibit);
            player.teleportTo(camera.x, camera.y, camera.z);
            player.setYRot(museumYaw(scenario.name(), museumExhibit != null));
            player.setXRot(museumExhibit == null ? 14 : 8);
        });
        return "Started " + name + " (seed=" + scenario.seed() + "). Results: run/pyrovfx-benchmarks/";
    }

    public static long beforeTick() {
        if (!ENABLED) {
            return 0;
        }
        if (!VfxBenchWindowPolicy.apply(Minecraft.getInstance())) {
            Minecraft.getInstance().stop();
            return 0;
        }
        if (autoExitTicks > 0 && --autoExitTicks == 0) {
            Minecraft.getInstance().stop();
            return 0;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (current == null && autoIndex < AUTO.length && !AUTO[autoIndex].isBlank()
                && minecraft.level != null && minecraft.player != null && minecraft.getSingleplayerServer() != null
                && VfxRegistry.contains(VfxBenchScenarios.get(AUTO[autoIndex]) == null
                    ? net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("pyro_vfx", "__missing")
                    : VfxBenchScenarios.get(AUTO[autoIndex]).effect())) {
            System.out.println("[Pyro VFX Bench] " + start(AUTO[autoIndex++]));
        }
        Run run = current;
        if (run == null || run.finishedTick) {
            return 0;
        }
        if (minecraft.player != null) {
            var museumExhibit = museumExhibit(run.scenario.name());
            float yaw = museumYaw(run.scenario.name(), museumExhibit != null);
            float pitch = museumExhibit == null ? 14 : 8;
            if (run.scenario.name().equals("museum_model_entity_close_pan")) {
                // Deliberately pan the camera while the particle/model's own
                // rotation is zero to distinguish animation from raster shimmer.
                yaw += (float) Math.sin(run.elapsedTicks * 0.075) * 6.0f;
                minecraft.player.yRotO = 180.0f
                        + (float) Math.sin(Math.max(0, run.elapsedTicks - 1) * 0.075) * 6.0f;
                pitch = 0;
            } else {
                minecraft.player.yRotO = yaw;
            }
            minecraft.player.setYRot(yaw);
            minecraft.player.setXRot(pitch);
            minecraft.player.xRotO = pitch;
        }
        if (Minecraft.getInstance().level == null) {
            if (run.scenario.name().equals("m7_lifecycle_world_exit")
                    && run.worldExitRequested) {
                // The next manager tick must observe the actual null world.
                return System.nanoTime();
            }
            run.error = "World was unloaded during test";
            run.finishedTick = true;
            return 0;
        }
        if (run.error != null) {
            run.finishedTick = true;
            return 0;
        }
        if (!run.spawned) {
            if (++run.setupTicks < 40) {
                return 0;
            }
            var museum = museumExhibit(run.scenario.name());
            if (museum != null) {
                Vec3 expected = museumCamera(run.scenario.name(), museum);
                if (minecraft.player.position().distanceToSqr(expected) > 12) {
                    if (run.setupTicks > 650)
                        run.error = "Museum camera/stand never finished loading: " + museum.id();
                    return 0;
                }
            }
            if (run.scenario.name().equals("model_rotation")) {
                // A narrow pillar 4/16 away from its pivot, rotated by +45 degrees
                // around Y, must sample near (0.1768, *, -0.1768).
                ResourceLocation fixture = ResourceLocation.fromNamespaceAndPath("pyro_vfx", "bench/rotated_shape");
                RandomSource rng = RandomSource.create(run.scenario.seed());
                for (int i = 0; i < 16; i++) {
                    Vec3 point = VfxModelSpawnShapeSampler.sample(fixture, 0.0, rng);
                    if (point.x < 0.16 || point.x > 0.20 || point.z > -0.15 || point.z < -0.20) {
                        run.error = "Rotated model sampling failed: " + point;
                        break;
                    }
                }
            }
            if (run.scenario.name().equals("model_inheritance")) {
                ResourceLocation child = ResourceLocation.fromNamespaceAndPath("pyro_vfx", "bench/child_pillar");
                RandomSource rng = RandomSource.create(run.scenario.seed());
                for (int i = 0; i < 32; i++) {
                    Vec3 point = VfxModelSpawnShapeSampler.sample(child, 0.0, rng);
                    if (point.x < 0.245 || point.x > 0.255 || Math.abs(point.z) > 0.005) {
                        run.error = "Child elements were mixed with parent geometry: " + point;
                        break;
                    }
                }
            }
            // A single seeded effect allows reproducible spawn RNG without changing normal play().
            long start = System.nanoTime();
            VfxPlayOptions options = run.scenario.particles() > 0
                    ? VfxPlayOptions.builder().param("count", run.scenario.particles()).build()
                    : VfxPlayOptions.empty();
            Vec3 effectPosition = museum == null ? ORIGIN
                    : new Vec3(museum.effectOrigin().getX() + 0.5,
                    museum.effectOrigin().getY() + 0.5,
                    museum.effectOrigin().getZ() + 0.5);
            if (run.scenario.name().equals("global_budget_spike")) {
                // Heavy override remains DISABLED: exercise production global admission.
                var request = VfxPlayOptions.builder().param("count",
                        VfxGlobalBudget.MAX_SPAWNS_PER_TICK / 2).build();
                for (int i = 0; i < 3; i++) {
                    ClientVfxManager.playSeeded(run.definition, ORIGIN, request, run.scenario.seed() + i);
                }
            } else if (VfxM7LifecycleProbe.accepts(run.scenario.name())) {
                try {
                    run.lifecycle = VfxM7LifecycleProbe.attach(
                            run.scenario.name(), minecraft.level, effectPosition);
                    ClientVfxManager.playSeeded(run.definition,
                            run.lifecycle.provider(), options, run.scenario.seed());
                    run.lifecycle.bind();
                } catch (Exception exception) {
                    run.error = "M7 lifecycle setup: " + exception;
                }
            } else if (museum != null && museum.id().startsWith("source_")) {
                ClientVfxManager.playSeeded(run.definition,
                        VfxMuseumClient.benchmarkProviderFor(museum.id(), effectPosition,
                                museum.base().offset(7, 0, 5)), options, run.scenario.seed());
            } else {
                ClientVfxManager.playSeeded(run.definition, effectPosition, options, run.scenario.seed());
            }
            run.spawnNs = System.nanoTime() - start;
            run.spawned = true;
            if (run.scenario.name().equals("museum_source_block")
                    && ClientVfxManager.getActiveInstanceCount() == 0)
                run.error = "Source block provider could not attach to the physical fixture";
            // Report an invalid client run only after spawning; otherwise the
            // harness has no active render callback to finalize and auto-exit.
            if (run.windowMismatch != null) run.error = run.windowMismatch;
            run.lastFrameNs = 0;
        }
        run.tickAllocStart = allocatedBytes();
        return System.nanoTime();
    }

    public static void afterTick(long startNs) {
        Run run = current;
        if (run == null || !run.spawned || run.finishedTick || startNs == 0) {
            return;
        }
        if (run.scenario.name().equals("m7_lifecycle_world_exit")
                && run.worldExitRequested && Minecraft.getInstance().level == null) {
            run.worldExitVerified = ClientVfxManager.getActiveInstanceCount() == 0
                    && ClientVfxManager.getActiveParticleCount() == 0
                    && run.worldExitEmitter != null
                    && run.worldExitEmitter.analyticPools().isEmpty()
                    && run.worldExitEmitter.gpuResidentBirthBytes() == 0;
            if (!run.worldExitVerified)
                run.error = "World exit did not close VFX instances or retire GPU birth data";
            try {
                Path result = VfxBenchReport.write(run);
                System.out.println("[Pyro VFX Bench] " + run.scenario.name()
                        + ": " + result + " verified=" + run.worldExitVerified);
            } catch (Exception ex) {
                System.err.println("[Pyro VFX Bench] World-exit report failed: " + ex);
            }
            current = null;
            Minecraft.getInstance().stop();
            return;
        }
        long elapsed = System.nanoTime() - startNs;
        long allocatedAfter = allocatedBytes();
        int index = run.elapsedTicks++;
        if (run.scenario.name().equals("m7_lifecycle_world_exit")
                && index == 20 && !run.worldExitRequested) {
            if (ClientVfxManager.getActiveInstanceCount() != 1
                    || ClientVfxManager.getActiveParticleCount() <= 0) {
                run.error = "World-exit fixture had no active VFX to retire";
            } else {
                run.worldExitEmitter = ClientVfxManager.instances()
                        .getFirst().emitters().getFirst();
                run.worldExitRequested = true;
                // The regular in-game menu initiates integrated-server
                // shutdown before waiting for disconnection. In an automated
                // client tick no menu action precedes this; request a
                // nonblocking stop explicitly or disconnect waits forever.
                var integrated = Minecraft.getInstance().getSingleplayerServer();
                if (integrated != null) integrated.halt(false);
                // Disconnect outside the client tick callback: Minecraft's
                // normal shutdown path runs nested progress-screen frames
                // while waiting for the integrated server to stop.
                Minecraft.getInstance().tell(() -> Minecraft.getInstance().disconnect());
            }
        }
        if (run.lifecycle != null && run.error == null) {
            try {
                run.lifecycle.afterTick(index);
            } catch (Exception exception) {
                run.error = "M7 lifecycle check: " + exception;
            }
        }
        boolean soak = run.scenario.name().equals("soak_mixed");
        if (((run.scenario.name().equals("reload_active")
                || run.scenario.name().equals("model_reload_active")
                || run.scenario.name().equals("gpu_expression_reload_active")) && index == 10)
                || (soak && (index == 60 || index == 240 || index == 450))) {
            if (run.reloadFuture != null) {
                run.error = "Previous resource reload still in flight";
            }
            run.reloadCompleted = false;
            run.reloadFuture = Minecraft.getInstance().reloadResourcePacks();
        }
        if (run.reloadFuture != null && run.reloadFuture.isDone() && !run.reloadCompleted) {
            if (run.reloadFuture.isCompletedExceptionally()) {
                run.error = "Minecraft client resource reload failed";
            } else if (VfxRegistry.get(run.scenario.effect()) == null) {
                run.error = "VFX definitions disappeared after client resource reload";
            } else if (ClientVfxManager.instances().size() != 1
                    || ClientVfxManager.instances().getFirst().definition() != run.definition) {
                run.error = "Active VFX instance did not survive client resource reload";
            } else {
                run.reloadCompleted = true;
                run.reloadCyclesCompleted++;
                if (soak) run.reloadFuture = null;
            }
        }
        if (index >= run.scenario.warmupTicks()) {
            var adaptive = ClientVfxManager.adaptiveSpawns();
            run.minimumAdaptiveScale = Math.min(run.minimumAdaptiveScale, adaptive.spawnScale());
            run.peakAdaptiveCpuEmaMs = Math.max(run.peakAdaptiveCpuEmaMs, adaptive.cpuEmaMs());
            if (adaptive.spawnScale() < 0.999999) run.adaptiveConstrainedTickSamples++;
            run.collisionCacheHits += net.kofllee.pyrovfx.client.vfx.VfxCollisionQueryCache.hits();
            run.collisionCacheMisses += net.kofllee.pyrovfx.client.vfx.VfxCollisionQueryCache.misses();
            if (run.tickCount < run.simulationNs.length) {
                run.simulationNs[run.tickCount++] = elapsed;
            }
            if (run.tickAllocStart >= 0 && allocatedAfter >= run.tickAllocStart) {
                run.tickAllocatedBytes = Math.max(0, run.tickAllocatedBytes) + allocatedAfter - run.tickAllocStart;
            }
        }
        if (index % 5 == 0) {
            run.peakParticles = Math.max(run.peakParticles, ClientVfxManager.getActiveParticleCount());
            run.peakHeapBytes = Math.max(run.peakHeapBytes, VfxBenchReport.usedHeap());
        }
        if (run.scenario.name().startsWith("collision")) {
            for (var instance : ClientVfxManager.instances()) {
                for (var particle : instance.particles()) {
                    if (particle.collidedThisTick()) {
                        run.collisionContacts++;
                    }
                }
                for (var emitter : instance.emitters()) {
                    if (emitter.densePool() != null) {
                        run.collisionContacts += emitter.densePool().collisionContactsLastTick();
                    }
                }
                if (instance.parameters().getOrDefault("hit", 0.0) > 0.5) {
                    run.collisionEventObserved = true;
                }
            }
        }
        if (run.scenario.name().equals("self_emit_collision")) {
            if (ClientVfxManager.getActiveInstanceCount() != 1) {
                run.error = "Self-emission during collision removed the active instance";
            } else if (ClientVfxManager.instances().getFirst().parameters().getOrDefault("hit", 0.0) > 0.5) {
                run.collisionEventObserved = true;
            }
        }
        if (run.scenario.name().equals("event_order") && index == 0) {
            double stage = ClientVfxManager.instances().getFirst().parameters().getOrDefault("stage", -1.0);
            if (stage != 2.0) {
                run.error = "Nested set_param events did not observe earlier mutation: stage=" + stage;
            }
        }
        if (run.elapsedTicks >= run.scenario.warmupTicks() + run.scenario.sampleTicks()) {
            if (run.lifecycle != null && !run.lifecycle.verified() && run.error == null)
                run.error = "M7 lifecycle teardown did not complete";
            if (run.scenario.name().equals("soak_mixed")
                    && (run.reloadCyclesCompleted != 3 || run.peakParticles < 50)) {
                run.error = "Mixed workload did not complete all 3 reloads or produce enough particles: "
                        + run.reloadCyclesCompleted + " / " + run.peakParticles;
            }
            if (Boolean.getBoolean("pyrovfx.bench.gpuTelemetry") && run.runtimeGpuSamples == 0) {
                run.error = "Async runtime GPU query did not produce a real completed sample";
            }
            if (Boolean.getBoolean("pyrovfx.bench.requireGpuAnalytic")) {
                boolean steady = run.scenario.name().equals("analytic_steady");
                if ((steady ? run.maxAnalyticInstances <= 0
                        || run.analyticDatasetUploads < 10
                        || run.analyticDrawCalls < run.scenario.sampleTicks()
                        : run.maxAnalyticInstances != run.scenario.particles())
                        || run.analyticDatasetUploads == 0
                        || run.analyticDrawCalls == 0) {
                    run.error = "GPU analytic backend failed to render expected particle count: "
                            + run.maxAnalyticInstances + "/" + run.scenario.particles()
                            + " uploads=" + run.analyticDatasetUploads;
                }
            }
            if ((run.scenario.name().equals("reload_active")
                    || run.scenario.name().equals("model_reload_active")
                    || run.scenario.name().equals("gpu_expression_reload_active")) && !run.reloadCompleted) {
                run.error = "Client resource reload not observed before benchmark ended";
            }
            if (Boolean.getBoolean("pyrovfx.bench.requireGpuInstancing")) {
                String name = run.scenario.name();
                if ((name.equals("sprite_1k") || name.equals("sprite_10k")
                        || name.equals("sprite_uv_instanced") || name.equals("sprite_world_z")
                        || name.equals("sprite_horizontal")
                        || name.equals("sprite_rotating_1k") || name.equals("sprite_rotating_10k"))
                        && net.kofllee.pyrovfx.client.render.VfxInstancedSpriteRenderer.drawCalls() == 0
                        && net.kofllee.pyrovfx.client.render.VfxAnalyticSpriteRenderer.drawCalls() == 0) {
                    run.error = "Expected actual sprite GPU instancing, got no draws: " + name;
                }
                if ((name.equals("model_1k") || name.equals("model_reload_active"))
                        && net.kofllee.pyrovfx.client.render.VfxInstancedModelRenderer.lastDrawCalls() == 0) {
                    run.error = "Expected actual model GPU instancing, got no draws: " + name;
                }
                if ((name.equals("dynamic_1k") || name.equals("sprite_lit_fallback"))
                        && net.kofllee.pyrovfx.client.render.VfxInstancedSpriteRenderer.drawCalls() != 0) {
                    run.error = "Alpha or world-lit sprite bypassed required RenderType fallback";
                }
                if (name.equals("sprite_frustum")
                        && (net.kofllee.pyrovfx.client.render.VfxInstancedSpriteRenderer.culledParticles() == 0
                            || net.kofllee.pyrovfx.client.render.VfxInstancedSpriteRenderer.submittedParticles() == 0)) {
                    run.error = "GPU frustum test did not both cull and render visible particles";
                }
                if (name.equals("sprite_lod")
                        && net.kofllee.pyrovfx.client.render.VfxInstancedSpriteRenderer.subpixelCulledParticles() == 0) {
                    run.error = "Subpixel GPU LOD failed to discard tiny distant sprites";
                }
            }
            if (run.scenario.name().equals("budget_spike")
                    && run.peakParticles != VfxRuntimeBudget.MAX_ACTIVE_PARTICLES) {
                run.error = "Shared spawn budget expected " + VfxRuntimeBudget.MAX_ACTIVE_PARTICLES
                        + " active particles, observed " + run.peakParticles;
            }
            if (run.scenario.name().equals("global_budget_spike")) {
                var global = ClientVfxManager.globalBudget();
                if (run.peakParticles != net.kofllee.pyrovfx.client.vfx.VfxGlobalBudget.MAX_SPAWNS_PER_TICK
                        || global.admittedSpawns() != VfxGlobalBudget.MAX_SPAWNS_PER_TICK
                        || global.rejectedSpawns() != VfxGlobalBudget.MAX_SPAWNS_PER_TICK / 2
                        || ClientVfxManager.getActiveInstanceCount() != 3) {
                    run.error = "Global stress admission mismatch: peak=" + run.peakParticles
                            + " admitted=" + global.admittedSpawns()
                            + " rejected=" + global.rejectedSpawns()
                            + " effects=" + ClientVfxManager.getActiveInstanceCount();
                }
            }
            // Both 1k and 10k fixtures intentionally have no on_collision event.
            if (run.scenario.name().startsWith("collision_no_events")
                    && run.collisionContacts == 0) {
                run.error = "Collision geometry was never exercised without event handlers";
            } else if (run.scenario.name().startsWith("collision")
                    && !run.scenario.name().startsWith("collision_no_events")
                    && (run.collisionContacts == 0 || !run.collisionEventObserved)) {
                run.error = "Collision scenario did not observe a block contact and its event";
            }
            if (run.scenario.name().equals("self_emit_collision") && !run.collisionEventObserved) {
                run.error = "Self-emission scenario never delivered on_collision";
            }
            run.finishedTick = true;
        }
        int screenshotTick = run.scenario.warmupTicks()
                + (run.scenario.name().startsWith("collision") ? 50 : 5);
        if (run.screenshotPath == null && index == screenshotTick) {
            // Capture the previous *completed* Minecraft frame, not an unfinished level pass.
            captureScreenshot(run);
            run.lastFrameNs = 0;
        }
    }

    public static long beforeRender() {
        Run run = current;
        if (run == null || !run.spawned) {
            return 0;
        }
        run.renderAllocStart = allocatedBytes();
        if (!Boolean.getBoolean("pyrovfx.bench.gpuTelemetry")
                && !run.finishedTick && run.elapsedTicks >= run.scenario.warmupTicks()) {
            if (run.gpuTimer == null) {
                run.gpuTimer = new VfxBenchGpuTimer();
            }
            run.gpuTimer.begin();
        }
        return System.nanoTime();
    }

    public static void afterRender(long startNs) {
        Run run = current;
        if (run == null || !run.spawned || startNs == 0) {
            return;
        }
        if ((run.scenario.name().equals("museum_model_entity")
                || run.scenario.name().equals("museum_model_entity_close_pan"))
                && !run.entityIsolationVerified
                && ClientVfxManager.getActiveParticleCount() >= 2
                && !ClientVfxManager.instances().isEmpty()) {
            var models = ClientVfxManager.instances().getFirst().particles();
            if (models.size() >= 2) {
                run.entityIsolationVerified = net.kofllee.pyrovfx.client.render
                        .EntityModelVfxParticleRenderer.hasIndependentRenderEntities(
                                models.get(0), models.get(1))
                        && net.kofllee.pyrovfx.client.render.EntityModelVfxParticleRenderer
                                .hasStableInitialHeadPose(models.get(0))
                        && net.kofllee.pyrovfx.client.render.EntityModelVfxParticleRenderer
                                .hasStableInitialHeadPose(models.get(1));
                if (!run.entityIsolationVerified) {
                    run.error = "Entity-model particles share a dummy entity or head pose interpolation is unsynchronized";
                }
            }
        }
        if (run.gpuTimer != null) {
            run.gpuTimer.end();
            run.gpuTimer.poll();
        }
        long now = System.nanoTime();
        if (run.scenario.name().equals("museum_model_entity_close_pan")
                && run.elapsedTicks >= run.scenario.warmupTicks()
                && run.renderCount > 0 && run.renderCount % 28 == 0
                && run.debugHeadFrames < 9) {
            try {
                Path folder = Minecraft.getInstance().gameDirectory.toPath()
                        .resolve("pyrovfx-benchmarks").resolve("head-motion-inspection");
                Files.createDirectories(folder);
                Path png = folder.resolve("pan-" + run.debugHeadFrames + ".png");
                try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                    image.writeToFile(png);
                }
                run.debugHeadFrames++;
            } catch (Exception exception) {
                run.error = "Head-motion screenshot capture failed: " + exception;
            }
        }
        run.analyticUploadBytes += VfxAnalyticSpriteRenderer.uploadedBytes();
        run.analyticDatasetUploads += VfxAnalyticSpriteRenderer.uploadedDatasets();
        run.analyticDrawCalls += VfxAnalyticSpriteRenderer.drawCalls();
        run.maxAnalyticInstances = Math.max(run.maxAnalyticInstances, VfxAnalyticSpriteRenderer.instances());
        if (Boolean.getBoolean("pyrovfx.bench.gpuTelemetry")) {
            run.runtimeGpuSamples = net.kofllee.pyrovfx.client.render.VfxGpuTelemetry.sampleCount();
            run.runtimeGpuLastMs = net.kofllee.pyrovfx.client.render.VfxGpuTelemetry.latestSampleNs() / 1_000_000.0;
        }
        if (run.gpuRenderer == null) {
            run.gpuRenderer = safeGlString(GL11.GL_RENDERER);
            run.gpuVendor = safeGlString(GL11.GL_VENDOR);
            run.glVersion = safeGlString(GL11.GL_VERSION);
            run.observedCamera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().toString();
            run.observedYaw = Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
            run.observedPitch = Minecraft.getInstance().gameRenderer.getMainCamera().getXRot();
        }
        if (!run.finishedTick && run.elapsedTicks >= run.scenario.warmupTicks()) {
            if (run.renderCount < run.renderNs.length) {
                run.renderNs[run.renderCount++] = now - startNs;
            }
            long afterAllocated = allocatedBytes();
            if (run.renderAllocStart >= 0 && afterAllocated >= run.renderAllocStart) {
                run.renderAllocatedBytes = Math.max(0, run.renderAllocatedBytes)
                        + afterAllocated - run.renderAllocStart;
            }
            if (run.lastFrameNs > 0 && run.frameCount < run.framesNs.length) {
                run.framesNs[run.frameCount++] = now - run.lastFrameNs;
            }
        }
        run.lastFrameNs = now;
        if (run.finishedTick) {
            if ((run.scenario.name().equals("museum_model_entity")
                    || run.scenario.name().equals("museum_model_entity_close_pan"))
                    && !run.entityIsolationVerified && run.error == null) {
                run.error = "Entity-model animation isolation/rest-pose synchronization was never observed";
            }
            if (run.screenshotPath == null) {
                captureScreenshot(run);
            }
            finish(run);
        }
    }

    private static void captureScreenshot(Run run) {
        try {
            Path folder = Minecraft.getInstance().gameDirectory.toPath().resolve("pyrovfx-benchmarks");
            Files.createDirectories(folder);
            Path png = folder.resolve(run.scenario.name() + "-" + System.currentTimeMillis() + ".png");
            try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                image.writeToFile(png);
            }
            run.screenshotPath = png.toAbsolutePath().toString();
        } catch (Exception error) {
            run.error = "Screenshot failed: " + error;
            run.screenshotPath = "capture failed: " + error;
        }
    }

    private static void finish(Run run) {
        if (current != run) {
            return;
        }
        current = null;
        if (run.gpuTimer != null) {
            run.gpuMedianMs = run.gpuTimer.medianMs();
            run.gpuP95Ms = run.gpuTimer.p95Ms();
            run.gpuSamples = run.gpuTimer.sampleCount();
            run.gpuTimer.close();
        }
        try {
            Path path = VfxBenchReport.write(run);
            System.out.println("[Pyro VFX Bench] " + run.scenario.name() + ": " + path);
        } catch (Exception error) {
            System.err.println("[Pyro VFX Bench] Failed to save results: " + error);
        } finally {
            ClientVfxManager.clear();
            if (run.lifecycle != null) run.lifecycle.cleanup();
            if (run.originalVsync != null) {
                var minecraft = Minecraft.getInstance();
                minecraft.options.enableVsync().set(run.originalVsync);
                minecraft.getWindow().updateVsync(run.originalVsync);
                minecraft.options.framerateLimit().set(run.originalFpsLimit);
            }
            if (autoIndex == AUTO.length && Boolean.getBoolean("pyrovfx.bench.autoExit")) {
                autoExitTicks = 40;
            }
            var server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null && run.originalPosition != null) {
                UUID playerId = Minecraft.getInstance().player.getUUID();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        for (var entry : run.originalBlocks.entrySet()) {
                            player.serverLevel().setBlockAndUpdate(entry.getKey(), entry.getValue());
                        }
                        player.serverLevel().setDayTime(run.originalDayTime);
                        player.teleportTo(run.originalPosition.x, run.originalPosition.y, run.originalPosition.z);
                        player.setYRot(run.originalYaw);
                        player.setXRot(run.originalPitch);
                        player.setGameMode(run.originalGameMode);
                    }
                });
            }
        }
    }

    private static ThreadMXBean allocationBean() {
        var bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof ThreadMXBean hotSpot) || !hotSpot.isThreadAllocatedMemorySupported()) {
            return null;
        }
        try {
            if (!hotSpot.isThreadAllocatedMemoryEnabled()) {
                hotSpot.setThreadAllocatedMemoryEnabled(true);
            }
            return hotSpot;
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private static long allocatedBytes() {
        return ALLOCATIONS == null ? -1 : ALLOCATIONS.getThreadAllocatedBytes(Thread.currentThread().threadId());
    }

    private static String safeGlString(int param) {
        try {
            return GL11.glGetString(param);
        } catch (Exception exception) {
            return "unavailable";
        }
    }

    static final class Run {
        final VfxBenchScenarios.Scenario scenario;
        final VfxDefinition definition;
        final long[] simulationNs;
        final long[] renderNs;
        final long[] framesNs;
        final long startGcCollections = VfxBenchReport.gcCollections();
        final long startGcTimeMs = VfxBenchReport.gcTimeMs();

        int setupTicks;
        int elapsedTicks;
        int tickCount;
        int renderCount;
        int frameCount;
        int peakParticles;
        long peakHeapBytes = VfxBenchReport.usedHeap();
        long spawnNs;
        long tickAllocStart = -1;
        long renderAllocStart = -1;
        long tickAllocatedBytes = -1;
        long renderAllocatedBytes = -1;
        long lastFrameNs;
        boolean spawned;
        boolean finishedTick;
        String gpuVendor;
        String gpuRenderer;
        String glVersion;
        String observedCamera;
        float observedYaw;
        float observedPitch;
        String screenshotPath;
        String error;
        String windowMismatch;
        VfxBenchGpuTimer gpuTimer;
        Double gpuMedianMs;
        Double gpuP95Ms;
        int gpuSamples;
        long analyticUploadBytes;
        long analyticDrawCalls;
        long runtimeGpuSamples;
        double runtimeGpuLastMs;
        int analyticDatasetUploads;
        int maxAnalyticInstances;
        Vec3 originalPosition;
        float originalYaw;
        float originalPitch;
        GameType originalGameMode;
        long originalDayTime;
        final Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();
        long collisionContacts;
        long collisionCacheHits;
        long collisionCacheMisses;
        double minimumAdaptiveScale = 1.0;
        double peakAdaptiveCpuEmaMs;
        int adaptiveConstrainedTickSamples;
        boolean collisionEventObserved;
        boolean entityIsolationVerified;
        VfxM7LifecycleProbe lifecycle;
        boolean worldExitRequested;
        boolean worldExitVerified;
        net.kofllee.pyrovfx.client.vfx.ClientVfxEmitter worldExitEmitter;
        int debugHeadFrames;
        CompletableFuture<Void> reloadFuture;
        boolean reloadCompleted;
        int reloadCyclesCompleted;
        Boolean originalVsync;
        Integer originalFpsLimit;

        Run(VfxBenchScenarios.Scenario scenario, VfxDefinition definition) {
            this.scenario = scenario;
            this.definition = definition;
            simulationNs = new long[Math.max(1000, scenario.sampleTicks())];
            renderNs = new long[Math.max(4000, scenario.sampleTicks() * 8)];
            framesNs = new long[Math.max(4000, scenario.sampleTicks() * 8)];
        }
    }
}
