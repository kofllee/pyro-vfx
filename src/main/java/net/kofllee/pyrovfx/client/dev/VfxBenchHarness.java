package net.kofllee.pyrovfx.client.dev;

import com.mojang.blaze3d.platform.NativeImage;
import com.sun.management.ThreadMXBean;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.client.vfx.sampling.VfxModelSpawnShapeSampler;
import net.kofllee.pyrovfx.client.vfx.VfxPlayOptions;
import net.kofllee.pyrovfx.client.vfx.VfxRuntimeBudget;
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
    private static int autoExitTicks = -1;

    private VfxBenchHarness() {}

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
            if (scenario.name().contains("collision")) {
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
            player.teleportTo(CAMERA.x, CAMERA.y, CAMERA.z);
            player.setYRot(0);
            player.setXRot(14);
        });
        return "Started " + name + " (seed=" + scenario.seed() + "). Results: run/pyrovfx-benchmarks/";
    }

    public static long beforeTick() {
        if (!ENABLED) {
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
            minecraft.player.setYRot(0.0F);
            minecraft.player.setXRot(14.0F);
            minecraft.player.yRotO = 0.0F;
            minecraft.player.xRotO = 14.0F;
        }
        if (Minecraft.getInstance().level == null) {
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
            ClientVfxManager.playSeeded(run.definition, ORIGIN, options, run.scenario.seed());
            run.spawnNs = System.nanoTime() - start;
            run.spawned = true;
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
        long elapsed = System.nanoTime() - startNs;
        long allocatedAfter = allocatedBytes();
        int index = run.elapsedTicks++;
        if (run.scenario.name().equals("reload_active") && index == 10) {
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
            }
        }
        if (index >= run.scenario.warmupTicks()) {
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
            if (run.scenario.name().equals("reload_active") && !run.reloadCompleted) {
                run.error = "Client resource reload not observed before benchmark ended";
            }
            if (run.scenario.name().equals("budget_spike")
                    && run.peakParticles != VfxRuntimeBudget.MAX_ACTIVE_PARTICLES) {
                run.error = "Shared spawn budget expected " + VfxRuntimeBudget.MAX_ACTIVE_PARTICLES
                        + " active particles, observed " + run.peakParticles;
            }
            if (run.scenario.name().equals("collision_no_events") && run.collisionContacts == 0) {
                run.error = "Collision geometry was never exercised without event handlers";
            } else if (run.scenario.name().startsWith("collision")
                    && !run.scenario.name().equals("collision_no_events")
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
        if (!run.finishedTick && run.elapsedTicks >= run.scenario.warmupTicks()) {
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
        if (run.gpuTimer != null) {
            run.gpuTimer.end();
            run.gpuTimer.poll();
        }
        long now = System.nanoTime();
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
        final long[] simulationNs = new long[1000];
        final long[] renderNs = new long[4000];
        final long[] framesNs = new long[4000];
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
        VfxBenchGpuTimer gpuTimer;
        Double gpuMedianMs;
        Double gpuP95Ms;
        int gpuSamples;
        Vec3 originalPosition;
        float originalYaw;
        float originalPitch;
        GameType originalGameMode;
        long originalDayTime;
        final Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();
        long collisionContacts;
        boolean collisionEventObserved;
        CompletableFuture<Void> reloadFuture;
        boolean reloadCompleted;

        Run(VfxBenchScenarios.Scenario scenario, VfxDefinition definition) {
            this.scenario = scenario;
            this.definition = definition;
        }
    }
}
