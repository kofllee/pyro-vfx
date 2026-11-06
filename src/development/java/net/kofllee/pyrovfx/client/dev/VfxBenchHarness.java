package net.kofllee.pyrovfx.client.dev;

import com.mojang.blaze3d.platform.NativeImage;
import com.sun.management.ThreadMXBean;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.client.vfx.sampling.VfxModelSpawnShapeSampler;
import net.kofllee.pyrovfx.client.vfx.VfxPlayOptions;
import net.kofllee.pyrovfx.client.vfx.VfxRuntimeBudget;
import net.kofllee.pyrovfx.client.vfx.VfxGlobalBudget;
import net.kofllee.pyrovfx.client.vfx.VfxBudgetConfig;
import net.kofllee.pyrovfx.client.render.VfxGpuBirthBuffers;
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
            return net.kofllee.pyrovfx.museum.VfxMuseumCatalog.find("model_custom");
        if (scenario.startsWith("museum_translucency_"))
            return net.kofllee.pyrovfx.museum.VfxMuseumCatalog.find("model_translucent");
        if (scenario.equals("museum_camera_front") || scenario.equals("museum_camera_turn_90"))
            return net.kofllee.pyrovfx.museum.VfxMuseumCatalog.find("model_custom");
        if (scenario.equals("museum_smoke_camera_front") || scenario.equals("museum_smoke_camera_turn_90"))
            return net.kofllee.pyrovfx.museum.VfxMuseumCatalog.find("sprite_additive");
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
        run.stageEarlyBaseline = net.kofllee.pyrovfx.client.render.VfxRenderer.totalEarlyPasses();
        run.stageLateBaseline = net.kofllee.pyrovfx.client.render.VfxRenderer.totalLatePasses();
        run.stageMixedBaseline = net.kofllee.pyrovfx.client.render.VfxRenderer.totalMixedFrames();
        run.nativeInstanceDrawsAtStart = net.kofllee.pyrovfx.client.render.VfxNativeGpuGeometryBridge.totalInstanceDrawCalls();
        run.nativeInstanceUploadsAtStart = net.kofllee.pyrovfx.client.render.VfxNativeGpuGeometryBridge.totalInstanceUploadBytes();
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
            boolean glassForeground = scenario.name().equals("alpha_world_overlap")
                    || scenario.name().equals("alpha_world_glass");
            boolean waterForeground = scenario.name().equals("alpha_world_overlap")
                    || scenario.name().equals("alpha_world_water")
                    || scenario.name().equals("alpha_visibility_water")
                    || scenario.name().equals("alpha_visibility_control");
            if (glassForeground) {
                // A complete, camera-facing glass plane in front of ALL three
                // effect layers (nearest sprite center is at z=-1.5).
                for (int x = -4; x <= 4; x++)
                    for (int y = 137; y <= 146; y++)
                        placeBenchmarkBlock(run, player.serverLevel(),
                                new BlockPos(x, y, -5), Blocks.GLASS.defaultBlockState());
            }
            if (scenario.name().equals("alpha_tinted_partial")
                    || scenario.name().equals("alpha_tinted_clouds")
                    || scenario.name().equals("alpha_cloud_layer_partial")) {
                // A single 12-block-wide quad crosses the tinted-glass
                // boundary. Its center is NOT behind glass, so classifying
                // the whole emitter by one origin ray gives an invalid pass.
                for (int x = -5; x <= -1; x++)
                    for (int y = scenario.name().startsWith("alpha_cloud_layer") ? 177 : 137;
                            y <= (scenario.name().startsWith("alpha_cloud_layer") ? 206 : 160); y++)
                        placeBenchmarkBlock(run, player.serverLevel(),
                                new BlockPos(x, y, -5),
                                Blocks.TINTED_GLASS.defaultBlockState());
                Vec3 testEye = CAMERA.add(0,
                        (scenario.name().startsWith("alpha_cloud_layer") ? 40 : 0) + 1.62, 0);
                Vec3 targetCenter = ORIGIN.add(0,
                        scenario.name().startsWith("alpha_cloud_layer") ? 49 : 2, 2);
                Vec3 targetLeft = targetCenter.add(-6, 0, 0);
                double rayFraction = (-4.5 - testEye.z) / (targetCenter.z - testEye.z);
                BlockPos centerBlock = BlockPos.containing(testEye.lerp(targetCenter, rayFraction));
                BlockPos leftBlock = BlockPos.containing(testEye.lerp(targetLeft, rayFraction));
                if (player.serverLevel().getBlockState(centerBlock).is(Blocks.TINTED_GLASS)
                        || !player.serverLevel().getBlockState(leftBlock).is(Blocks.TINTED_GLASS))
                    run.error = "Tinted partial fixture needs a clear center ray and occluded left corner: "
                            + centerBlock + " / " + leftBlock;
            }
            if (waterForeground) {
                // Old fixture had ONE water source behind a glass wall and
                // off the actual line of sight. This is a closed 7x7 source
                // wall between the camera (z=-12) and all effects (z>=-1.5).
                // Glass front/back and perimeter prevent water spreading into
                // the benchmark save; every original block is restored.
                for (int x = -3; x <= 3; x++)
                    for (int y = 138; y <= 144; y++) {
                        placeBenchmarkBlock(run, player.serverLevel(),
                                new BlockPos(x, y, -8), Blocks.GLASS.defaultBlockState());
                        placeBenchmarkBlock(run, player.serverLevel(),
                                new BlockPos(x, y, -6), Blocks.GLASS.defaultBlockState());
                    }
                for (int z = -8; z <= -6; z++)
                    for (int y = 137; y <= 145; y++)
                        for (int x = -4; x <= 4; x++)
                            if (x == -4 || x == 4 || y == 137 || y == 145)
                                placeBenchmarkBlock(run, player.serverLevel(),
                                        new BlockPos(x, y, z), Blocks.GLASS.defaultBlockState());
                for (int x = -3; x <= 3; x++)
                    for (int y = 138; y <= 144; y++)
                        placeBenchmarkBlock(run, player.serverLevel(),
                                new BlockPos(x, y, -7), Blocks.WATER.defaultBlockState());

                // Assert geometry, not just "a water block exists somewhere".
                // Both rays from the actual camera eye to the foreground and
                // background sprite centers must traverse WATER at z=-6.5.
                Vec3 cameraEye = CAMERA.add(0, 1.62, 0);
                run.waterForegroundVerified = true;
                Vec3[] targets = scenario.name().startsWith("alpha_visibility_")
                        ? new Vec3[]{
                            ORIGIN.add(-1.75, 1.5, -2),
                            ORIGIN.add(1.75, 1.5, 2)}
                        : new Vec3[]{
                            new Vec3(ORIGIN.x, ORIGIN.y, -1.5),
                            new Vec3(ORIGIN.x, ORIGIN.y, 2.5)};
                for (Vec3 target : targets) {
                    double fraction = (-6.5 - cameraEye.z) / (target.z - cameraEye.z);
                    BlockPos rayBlock = BlockPos.containing(cameraEye.lerp(target, fraction));
                    if (!player.serverLevel().getBlockState(rayBlock).is(Blocks.WATER)) {
                        run.waterForegroundVerified = false;
                        run.error = "Water foreground does not intersect camera-to-effect ray at " + rayBlock;
                        break;
                    }
                }
            }
            if (scenario.name().equals("alpha_particle_in_water")
                    || scenario.name().equals("alpha_camera_and_particle_in_water")) {
                boolean cameraSubmerged = scenario.name().equals("alpha_camera_and_particle_in_water");
                // Unlike the old foreground-wall test, the ACTUAL sprite
                // birth positions are submerged. In the second case the
                // camera eye and both sprites occupy the same continuous
                // water body. Glass walls are placed first to avoid flow.
                int nearZ = cameraSubmerged ? -15 : -5;
                int farZ = 5;
                for (int x = -5; x <= 5; x++)
                    for (int y = 138; y <= 146; y++)
                        for (int z = nearZ; z <= farZ; z++)
                            if (x == -5 || x == 5 || y == 138 || y == 146
                                    || z == nearZ || z == farZ)
                                placeBenchmarkBlock(run, player.serverLevel(),
                                        new BlockPos(x, y, z), Blocks.GLASS.defaultBlockState());
                for (int x = -4; x <= 4; x++)
                    for (int y = 139; y <= 145; y++)
                        for (int z = nearZ + 1; z < farZ; z++)
                            placeBenchmarkBlock(run, player.serverLevel(),
                                    new BlockPos(x, y, z), Blocks.WATER.defaultBlockState());
                Vec3[] submerged = new Vec3[]{
                        ORIGIN.add(-1.75, 1.5, -2),
                        ORIGIN.add(1.75, 1.5, 2)};
                for (Vec3 position : submerged) {
                    if (!player.serverLevel().getFluidState(BlockPos.containing(position))
                            .is(net.minecraft.tags.FluidTags.WATER))
                        run.error = "Particle center not submerged: " + position;
                }
                boolean cameraInWater = player.serverLevel().getFluidState(
                        BlockPos.containing(CAMERA.add(0, 1.62, 0)))
                        .is(net.minecraft.tags.FluidTags.WATER);
                if (cameraInWater != cameraSubmerged)
                    run.error = "Camera medium mismatch: expectedWater=" + cameraSubmerged;
            }
            if (scenario.name().equals("alpha_mixed_water_media")
                    || scenario.name().equals("alpha_mixed_underwater_view")) {
                boolean cameraWet = scenario.name().equals("alpha_mixed_underwater_view");
                // Only the red emitter is submerged; yellow remains in air.
                // Both emitters are in one effect instance, so the renderer
                // must submit early AND late groups, not globally choose one.
                sealedWaterTank(run, player.serverLevel(),
                        -4, cameraWet ? 4 : -2,
                        cameraWet ? -14 : -4, 0);
                Vec3 red = ORIGIN.add(-1.75, 1.5, -2);
                Vec3 yellow = ORIGIN.add(1.75, 1.5, 2);
                var level = player.serverLevel();
                if (!level.getFluidState(BlockPos.containing(red)).is(net.minecraft.tags.FluidTags.WATER)
                        || level.getFluidState(BlockPos.containing(yellow))
                        .is(net.minecraft.tags.FluidTags.WATER))
                    run.error = "Mixed media fixture must contain wet red and dry yellow particles";
                if (level.getFluidState(BlockPos.containing(CAMERA.add(0, 1.62, 0)))
                        .is(net.minecraft.tags.FluidTags.WATER) != cameraWet)
                    run.error = "Mixed media camera state does not match requested test";
            }
            player.setGameMode(GameType.SPECTATOR);
            var museumExhibit = museumExhibit(scenario.name());
            Vec3 camera = scenario.name().startsWith("alpha_cloud_layer")
                    ? CAMERA.add(0, 40, 0) : museumExhibit == null ? CAMERA
                    : museumCamera(scenario.name(), museumExhibit);
            player.teleportTo(camera.x, camera.y, camera.z);
            player.setYRot(museumYaw(scenario.name(), museumExhibit != null));
            player.setXRot(scenario.name().startsWith("alpha_cloud_layer") ? -24 : museumExhibit == null ? 14 : 8);
        });
        return "Started " + name + " (seed=" + scenario.seed() + "). Results: run/pyrovfx-benchmarks/";
    }

    private static void placeBenchmarkBlock(Run run,
            net.minecraft.server.level.ServerLevel level, BlockPos pos, BlockState state) {
        run.originalBlocks.putIfAbsent(pos, level.getBlockState(pos));
        level.setBlockAndUpdate(pos, state);
    }

    private static void sealedWaterTank(Run run,
            net.minecraft.server.level.ServerLevel level,
            int x0, int x1, int z0, int z1) {
        for (int x = x0 - 1; x <= x1 + 1; x++)
            for (int y = 138; y <= 146; y++)
                for (int z = z0 - 1; z <= z1 + 1; z++)
                    if (x == x0 - 1 || x == x1 + 1 || y == 138 || y == 146
                            || z == z0 - 1 || z == z1 + 1)
                        placeBenchmarkBlock(run, level, new BlockPos(x, y, z),
                                Blocks.GLASS.defaultBlockState());
        for (int x = x0; x <= x1; x++)
            for (int y = 139; y <= 145; y++)
                for (int z = z0; z <= z1; z++)
                    placeBenchmarkBlock(run, level, new BlockPos(x, y, z),
                            Blocks.WATER.defaultBlockState());
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
                    : run.scenario.name().equals("alpha_visibility_control")
                    ? VfxPlayOptions.builder().param("count", 0).build()
                    : VfxPlayOptions.empty();
            Vec3 effectPosition = run.scenario.name().startsWith("alpha_cloud_layer")
                    ? ORIGIN.add(0, 43, 0) : museum == null ? ORIGIN
                    : new Vec3(museum.effectOrigin().getX() + 0.5,
                    museum.effectOrigin().getY() + 0.5,
                    museum.effectOrigin().getZ() + 0.5);
            if (run.scenario.name().equals("m7_api_handles")) {
                try {
                    run.api = VfxM7ApiProbe.start(run.scenario.effect(), effectPosition);
                } catch (Exception exception) {
                    run.error = "Public API setup: " + exception;
                }
            } else if (run.scenario.name().equals("m7_network_sync")) {
                try {
                    run.network = VfxM7NetworkProbe.start(run.scenario.effect(), effectPosition);
                } catch (Exception exception) {
                    run.error = "Network API setup: " + exception;
                }
            } else if (run.scenario.name().equals("m7_network_attachments")) {
                try {
                    run.networkAttachments = VfxM7NetworkAttachmentsProbe.start(
                            run.scenario.effect(), effectPosition);
                } catch (Exception exception) {
                    run.error = "Network attachments setup: " + exception;
                }
            } else if (run.scenario.name().equals("m7_network_auto")) {
                try {
                    run.networkAuto = VfxM7AutoStreamProbe.start(run.scenario.effect(), effectPosition);
                } catch (Exception exception) {
                    run.error = "Network auto-stream setup: " + exception;
                }
            } else if (run.scenario.name().equals("m7_network_sockets")) {
                try {
                    run.networkSockets = VfxM7SocketProbe.start(run.scenario.effect(), effectPosition);
                } catch (Exception exception) {
                    run.error = "Network sockets setup: " + exception;
                }
            } else if (run.scenario.name().equals("m7_network_late")) {
                try {
                    run.networkLate = VfxM7LateJoinProbe.start(run.scenario.effect(), effectPosition);
                } catch (Exception exception) {
                    run.error = "Late viewer setup: " + exception;
                }
            } else if (run.scenario.name().equals("global_budget_spike")) {
                // Three independent requests for 20k particles. Do not
                // derive the requested amount from a mutable global limit:
                // changing the config must not silently change this fixture.
                var request = VfxPlayOptions.builder().param("count", 20_000).build();
                for (int i = 0; i < 3; i++) {
                    ClientVfxManager.playSeeded(run.definition, ORIGIN, request, run.scenario.seed() + i);
                }
            } else if (run.scenario.name().equals("distributed_analytic_64")) {
                // Same 32k particles and same seeds in both A/B modes. Their
                // distant positions stay alive; only render work may be skipped.
                var distributed = VfxPlayOptions.builder().param("count", 500).build();
                for (int i = 0; i < 64; i++) {
                    double x = (i % 8 - 3.5) * 24;
                    double z = (i / 8 - 3.5) * 24;
                    ClientVfxManager.playSeeded(run.definition,
                            ORIGIN.add(x, 0, z), distributed, run.scenario.seed() + i);
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
        if (run.api != null && run.error == null) {
            try {
                run.api.afterTick(index);
            } catch (Exception exception) {
                run.error = "Public API check: " + exception;
            }
        }
        if (run.network != null && run.error == null) {
            try {
                run.network.afterTick(index);
            } catch (Exception exception) {
                run.error = "Network synchronization check: " + exception;
            }
        }
        if (run.networkAttachments != null && run.error == null) {
            try {
                run.networkAttachments.afterTick(index);
            } catch (Exception exception) {
                run.error = "Network source tracking check: " + exception;
            }
        }
        if (run.networkAuto != null && run.error == null) {
            try {
                run.networkAuto.afterTick(index);
            } catch (Exception exception) {
                run.error = "Automatic network streaming check: " + exception;
            }
        }
        if (run.networkSockets != null && run.error == null) {
            try {
                run.networkSockets.afterTick(index);
            } catch (Exception exception) {
                run.error = "Vanilla/custom socket check: " + exception;
            }
        }
        if (run.networkLate != null && run.error == null) {
            try {
                run.networkLate.afterTick(index);
            } catch (Exception exception) {
                run.error = "Late viewer phase check: " + exception;
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
                if (run.scenario.name().equals("collision_ordered"))
                    run.collisionOrderedHitCount = instance.parameters()
                            .getOrDefault("hit", 0.0);
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
            String transparencyScenario = run.scenario.name();
            // The material contract is independent of neighboring glass, water,
            // cloud blocks or the observer's location. All alpha test fixtures
            // contain alpha-only emitters (including translucent models).
            // Validate this stable stage routing; the screenshots are separate
            // visual evidence of world-compositor intersection limitations.
            if (transparencyScenario.startsWith("alpha_")
                    && !net.kofllee.pyrovfx.client.render.VfxRenderer.lastWorldStage()
                            .equals("after_level"))
                run.error = "Alpha material was routed outside the shared after-world pass";
            if (run.lifecycle != null && !run.lifecycle.verified() && run.error == null)
                run.error = "M7 lifecycle teardown did not complete";
            if (run.scenario.name().equals("m7_api_handles")
                    && (run.api == null || !run.api.verified()) && run.error == null)
                run.error = "M7 public API verification did not complete";
            if (run.scenario.name().equals("m7_network_sync")
                    && (run.network == null || !run.network.verified()) && run.error == null)
                run.error = "Network play/update/stop verification did not complete";
            if (run.scenario.name().equals("m7_network_attachments")
                    && (run.networkAttachments == null || !run.networkAttachments.verified()) && run.error == null)
                run.error = "Network entity/block attachment verification did not complete";
            if (run.scenario.name().equals("m7_network_auto")
                    && (run.networkAuto == null || !run.networkAuto.verified()) && run.error == null)
                run.error = "Automatic network callback streaming verification did not complete";
            if (run.scenario.name().equals("m7_network_sockets")
                    && (run.networkSockets == null || !run.networkSockets.verified()) && run.error == null)
                run.error = "Vanilla/custom model socket verification did not complete";
            if (run.scenario.name().equals("m7_network_late")
                    && (run.networkLate == null || !run.networkLate.verified()) && run.error == null)
                run.error = "Late viewer phase verification did not complete";
            if (run.scenario.name().equals("soak_mixed")
                    && (run.reloadCyclesCompleted != 3 || run.peakParticles < 50)) {
                run.error = "Mixed workload did not complete all 3 reloads or produce enough particles: "
                        + run.reloadCyclesCompleted + " / " + run.peakParticles;
            }
            if (Boolean.getBoolean("pyrovfx.bench.gpuTelemetry") && run.runtimeGpuSamples == 0) {
                run.error = "Async runtime GPU query did not produce a real completed sample";
            }
            if (Boolean.getBoolean("pyrovfx.bench.requireGpuAnalytic")) {
                boolean repeatedBirths = run.scenario.name().equals("analytic_steady")
                        || run.scenario.name().equals("analytic_looping")
                        || run.scenario.name().equals("analytic_manual");
                int requiredUploads = run.scenario.name().equals("analytic_steady") ? 10 : 5;
                if ((repeatedBirths ? run.maxAnalyticInstances <= 0
                        || run.analyticDatasetUploads < requiredUploads
                        || run.analyticDrawCalls < run.scenario.sampleTicks()
                        : run.scenario.name().equals("analytic_coalesced")
                            ? run.maxAnalyticInstances != 256
                                || run.analyticDatasetUploads != 2
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
                        || name.equals("sprite_lit_fallback") || name.equals("dynamic_1k")
                        || name.equals("sprite_rotating_1k") || name.equals("sprite_rotating_10k"))
                        && net.kofllee.pyrovfx.client.render.VfxNativeSpriteBatcher.drawCalls() == 0
                        && net.kofllee.pyrovfx.client.render.VfxRenderer.analyticDrawCalls() == 0) {
                    run.error = "Expected actual sprite GPU instancing, got no draws: " + name;
                }
                if ((name.equals("model_1k") || name.equals("model_reload_active"))
                        && net.kofllee.pyrovfx.client.render.VfxNativeModelRenderer.lastDrawCalls() == 0) {
                    run.error = "Expected actual model GPU instancing, got no draws: " + name;
                }
                if (name.equals("sprite_frustum")
                        && (net.kofllee.pyrovfx.client.render.VfxNativeSpriteBatcher.culledParticles() == 0
                            || net.kofllee.pyrovfx.client.render.VfxNativeSpriteBatcher.submittedParticles() == 0)) {
                    run.error = "GPU frustum test did not both cull and render visible particles";
                }
                if (name.equals("sprite_lod")
                        && net.kofllee.pyrovfx.client.render.VfxNativeSpriteBatcher.subpixelCulledParticles() == 0) {
                    run.error = "Subpixel GPU LOD failed to discard tiny distant sprites";
                }
            }
            if (run.scenario.name().equals("budget_spike")) {
                boolean heavy = Boolean.getBoolean("pyrovfx.bench.allowHeavy");
                int admitted = heavy ? 40_000 : Math.min(40_000, Math.min(
                        Math.min(VfxBudgetConfig.effectParticles(), VfxBudgetConfig.effectSpawns()),
                        Math.min(Math.min(VfxBudgetConfig.globalParticles(), VfxBudgetConfig.globalSpawns()),
                                VfxBudgetConfig.classLimit(net.kofllee.pyrovfx.client.vfx.VfxParticleCostClass.GPU_ANALYTIC))));
                if (run.peakParticles != admitted)
                    run.error = "Shared spawn budget expected " + admitted
                            + " active particles, observed " + run.peakParticles;
            }
            if (run.scenario.name().equals("global_budget_spike")) {
                var global = ClientVfxManager.globalBudget();
                boolean heavy = Boolean.getBoolean("pyrovfx.bench.allowHeavy");
                String backend = ClientVfxManager.instances().isEmpty() ? ""
                        : ClientVfxManager.instances().getFirst()
                                .emitters().getFirst().selectedBackend();
                int perEffect = heavy ? 20_000 : Math.min(20_000,
                        Math.min(VfxBudgetConfig.effectParticles(), VfxBudgetConfig.effectSpawns()));
                int admitted = heavy ? 60_000 : Math.min(3 * perEffect, Math.min(
                        Math.min(VfxBudgetConfig.globalParticles(), VfxBudgetConfig.globalSpawns()),
                        VfxBudgetConfig.classLimit(
                                backend.equals("cpu_dense")
                                        ? net.kofllee.pyrovfx.client.vfx.VfxParticleCostClass.CPU_BASIC
                                        : net.kofllee.pyrovfx.client.vfx.VfxParticleCostClass.GPU_ANALYTIC)));
                if (run.peakParticles != admitted
                        || global.admittedSpawns() != admitted
                        || global.rejectedSpawns() != 60_000 - admitted
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
            if (run.scenario.name().equals("self_emit_collision")
                    && ClientVfxManager.globalBudget().admittedSpawns() <= 1)
                run.error = "Self-emission did not create a subsequent particle";
            if (run.scenario.name().equals("collision_ordered")
                    && run.collisionOrderedHitCount != run.collisionContacts)
                run.error = "Immediate collision set_param lost ordering: hits="
                        + run.collisionOrderedHitCount + " contacts=" + run.collisionContacts;
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
        run.analyticUploadBytes += net.kofllee.pyrovfx.client.render.VfxRenderer.analyticUploadedBytes();
        run.analyticDatasetUploads += net.kofllee.pyrovfx.client.render.VfxRenderer.analyticDatasetUploads();
        run.analyticDrawCalls += net.kofllee.pyrovfx.client.render.VfxRenderer.analyticDrawCalls();
        run.analyticSkippedCohorts += net.kofllee.pyrovfx.client.render.VfxRenderer.culledAnalyticCohorts();
        run.analyticSkippedParticles += net.kofllee.pyrovfx.client.render.VfxRenderer.culledAnalyticParticles();
        run.maxAnalyticInstances = Math.max(run.maxAnalyticInstances, net.kofllee.pyrovfx.client.render.VfxRenderer.analyticInstances());
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
        boolean waterForegroundVerified;
        long stageEarlyBaseline, stageLateBaseline, stageMixedBaseline;
        long nativeInstanceDrawsAtStart, nativeInstanceUploadsAtStart;
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
        long analyticSkippedCohorts;
        long analyticSkippedParticles;
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
        double collisionOrderedHitCount;
        boolean entityIsolationVerified;
        VfxM7LifecycleProbe lifecycle;
        VfxM7ApiProbe api;
        VfxM7NetworkProbe network;
        VfxM7NetworkAttachmentsProbe networkAttachments;
        VfxM7AutoStreamProbe networkAuto;
        VfxM7SocketProbe networkSockets;
        VfxM7LateJoinProbe networkLate;
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
