package net.kofllee.pyrovfx.client.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.kofllee.pyrovfx.client.render.VfxNativeSpriteBatcher;
import net.kofllee.pyrovfx.client.render.VfxNativeModelRenderer;
import net.kofllee.pyrovfx.client.render.VfxGpuBirthBuffers;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.IntBuffer;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class VfxBenchReport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private VfxBenchReport() {}

    static double percentile(long[] values, int count, double quantile) {
        if (count <= 0) {
            return Double.NaN;
        }
        long[] sorted = Arrays.copyOf(values, count);
        Arrays.sort(sorted);
        double rank = Math.max(0.0, Math.min(1.0, quantile)) * (count - 1);
        int lower = (int) rank;
        int upper = Math.min(count - 1, lower + 1);
        return (sorted[lower] + (sorted[upper] - sorted[lower]) * (rank - lower)) / 1_000_000.0;
    }

    static long usedHeap() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    static long gcCollections() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount)
                .filter(value -> value >= 0).sum();
    }

    static long gcTimeMs() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime)
                .filter(value -> value >= 0).sum();
    }

    static Path write(VfxBenchHarness.Run run) throws IOException {
        Minecraft minecraft = Minecraft.getInstance();
        Path output = minecraft.gameDirectory.toPath().resolve("pyrovfx-benchmarks");
        Files.createDirectories(output);

        String id = run.scenario.name() + "-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(java.time.ZoneOffset.UTC).format(Instant.now());
        JsonObject root = new JsonObject();
        root.addProperty("schema", "pyrovfx-bench-v1");
        root.addProperty("status", run.error == null ? "completed" : "invalid");
        root.addProperty("error", run.error);
        root.addProperty("scenario", run.scenario.name());
        if (run.scenario.name().equals("alpha_world_overlap")
                || run.scenario.name().equals("alpha_world_water")
                || run.scenario.name().equals("alpha_visibility_water")
                || run.scenario.name().equals("alpha_visibility_control"))
            root.addProperty("waterForegroundRayVerified", run.waterForegroundVerified);
        root.addProperty("vfxWorldRenderStage",
                net.kofllee.pyrovfx.client.render.VfxRenderer.lastWorldStage());
        root.addProperty("vfxEarlyPasses",
                net.kofllee.pyrovfx.client.render.VfxRenderer.totalEarlyPasses()
                        - run.stageEarlyBaseline);
        root.addProperty("vfxLatePasses",
                net.kofllee.pyrovfx.client.render.VfxRenderer.totalLatePasses()
                        - run.stageLateBaseline);
        root.addProperty("vfxMixedFrames",
                net.kofllee.pyrovfx.client.render.VfxRenderer.totalMixedFrames()
                        - run.stageMixedBaseline);
        if (run.scenario.name().contains("water")) {
            var client = net.minecraft.client.Minecraft.getInstance();
            if (client.level != null) {
                var eye = client.gameRenderer.getMainCamera().getPosition();
                root.addProperty("cameraInsideWater",
                        client.level.getFluidState(net.minecraft.core.BlockPos.containing(eye))
                                .is(net.minecraft.tags.FluidTags.WATER));
                root.addProperty("nearParticleInsideWater",
                        client.level.getFluidState(net.minecraft.core.BlockPos.containing(
                                new net.minecraft.world.phys.Vec3(-1.25, 141.5, -1.5)))
                                .is(net.minecraft.tags.FluidTags.WATER));
                root.addProperty("farParticleInsideWater",
                        client.level.getFluidState(net.minecraft.core.BlockPos.containing(
                                new net.minecraft.world.phys.Vec3(2.25, 141.5, 2.5)))
                                .is(net.minecraft.tags.FluidTags.WATER));
            }
            root.addProperty("minecraftFogStartAtReport",
                    com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart());
            root.addProperty("minecraftFogEndAtReport",
                    com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd());
            root.addProperty("vfxGpuAppliedFogStart",
                    net.kofllee.pyrovfx.client.render.VfxGpuFog.lastAppliedStart());
            root.addProperty("vfxGpuAppliedFogEnd",
                    net.kofllee.pyrovfx.client.render.VfxGpuFog.lastAppliedEnd());
        }
        if (run.lifecycle != null)
            root.addProperty("lifecycleVerified", run.lifecycle.verified());
        if (run.scenario.name().equals("m7_lifecycle_world_exit"))
            root.addProperty("worldExitVerified", run.worldExitVerified);
        root.addProperty("cpuParticleBackend", Boolean.getBoolean("pyrovfx.cpu.forceLegacy")
                ? "legacy-forced" : "auto-dense-with-feature-fallback");
        root.addProperty("gpuInstancingRequested", !Boolean.getBoolean("pyrovfx.render.forceLegacy"));
        root.addProperty("gpuInstancedDrawCallsLastFrame", VfxNativeSpriteBatcher.drawCalls());
        root.addProperty("gpuInstancedParticlesLastFrame", VfxNativeSpriteBatcher.submittedParticles());
        root.addProperty("gpuFrustumCulledLastFrame", VfxNativeSpriteBatcher.culledParticles());
        root.addProperty("gpuSubpixelCulledLastFrame", VfxNativeSpriteBatcher.subpixelCulledParticles());
        root.addProperty("gpuModelDrawCallsLastFrame", VfxNativeModelRenderer.lastDrawCalls());
        root.addProperty("gpuModelInstancesLastFrame", VfxNativeModelRenderer.lastInstances());
        root.addProperty("gpuModelResidentUploadsLastFrame", VfxNativeModelRenderer.residentUploads());
        root.addProperty("gpuModelResidentUploadBytesLastFrame", VfxNativeModelRenderer.residentUploadBytes());
        root.addProperty("gpuAnalyticDrawCallsLastFrame", net.kofllee.pyrovfx.client.render.VfxRenderer.analyticDrawCalls());
        root.addProperty("tinyAnalyticCohortsBatchedLastFrame",
                net.kofllee.pyrovfx.client.render.VfxRenderer.tinyAnalyticCohorts());
        root.addProperty("tinyAnalyticParticlesBatchedLastFrame",
                net.kofllee.pyrovfx.client.render.VfxRenderer.tinyAnalyticParticles());
        root.addProperty("gpuAnalyticInstancesLastFrame", net.kofllee.pyrovfx.client.render.VfxRenderer.analyticInstances());
        root.addProperty("gpuAnalyticUploadedBytesLastFrame", net.kofllee.pyrovfx.client.render.VfxRenderer.analyticUploadedBytes());
        root.addProperty("gpuAnalyticDatasetsUploadedLastFrame", net.kofllee.pyrovfx.client.render.VfxRenderer.analyticDatasetUploads());
        root.addProperty("gpuAnalyticUploadBytesTotal", run.analyticUploadBytes);
        root.addProperty("gpuAnalyticDatasetUploadsTotal", run.analyticDatasetUploads);
        root.addProperty("gpuAnalyticDrawCallsTotal", run.analyticDrawCalls);
        root.addProperty("gpuAnalyticCulledCohortsTotal", run.analyticSkippedCohorts);
        root.addProperty("gpuAnalyticCulledParticlesTotal", run.analyticSkippedParticles);
        root.addProperty("gpuAnalyticMaxInstancesSubmitted", run.maxAnalyticInstances);
        root.addProperty("gpuAnalyticBirthStateStrideBytes", 24);
        root.addProperty("nativeBridgeInstanceDrawCallsThisRun",
                net.kofllee.pyrovfx.client.render.VfxNativeGpuGeometryBridge.totalInstanceDrawCalls()
                        - run.nativeInstanceDrawsAtStart);
        root.addProperty("nativeBridgeInstanceUploadedBytesThisRun",
                net.kofllee.pyrovfx.client.render.VfxNativeGpuGeometryBridge.totalInstanceUploadBytes()
                        - run.nativeInstanceUploadsAtStart);
        root.addProperty("nativeBridgeDrawCallsLastFrame",
                net.kofllee.pyrovfx.client.render.VfxNativeGpuGeometryBridge.drawCalls());
        root.addProperty("nativeBridgeTfGenerationCallsLastFrame",
                net.kofllee.pyrovfx.client.render.VfxNativeGpuGeometryBridge.generationCallsThisFrame());
        root.addProperty("nativeBridgeMaterialSubmissionsLastFrame",
                net.kofllee.pyrovfx.client.render.VfxNativeGpuGeometryBridge.nativeSubmissionsThisFrame());
        root.addProperty("nativeBridgeInstancesLastFrame",
                net.kofllee.pyrovfx.client.render.VfxNativeGpuGeometryBridge.instances());
        root.addProperty("nativeBridgeOutputCapacityParticles",
                net.kofllee.pyrovfx.client.render.VfxNativeGpuGeometryBridge.outputCapacityParticles());
        root.addProperty("nativeBridgeAllocatedGeometryBytes",
                net.kofllee.pyrovfx.client.render.VfxNativeGpuGeometryBridge.geometryBytes());
        root.addProperty("nativeBridgeTotalResidentBirthUploadBytes",
                net.kofllee.pyrovfx.client.render.VfxNativeGpuGeometryBridge.uploadedBirthBytes());
        var globalBudget = ClientVfxManager.globalBudget();
        root.addProperty("globalPeakObservedParticles", globalBudget.peakObservedParticles());
        root.addProperty("globalAdmittedSpawns", globalBudget.admittedSpawns());
        root.addProperty("globalAdmittedEvents", globalBudget.admittedEvents());
        root.addProperty("globalRejectedSpawns", globalBudget.rejectedSpawns());
        root.addProperty("globalRejectedEvents", globalBudget.rejectedEvents());
        root.addProperty("globalRejectedEffects", globalBudget.rejectedInstances());
        JsonObject classBudgets = new JsonObject();
        for (var kind : net.kofllee.pyrovfx.client.vfx.VfxParticleCostClass.values()) {
            JsonObject counters = new JsonObject();
            counters.addProperty("admittedSpawns", globalBudget.admittedFor(kind));
            counters.addProperty("rejectedSpawns", globalBudget.rejectedFor(kind));
            counters.addProperty("remainingSlots", globalBudget.remainingFor(kind));
            classBudgets.add(kind.name().toLowerCase(java.util.Locale.ROOT), counters);
        }
        root.add("costClassBudgets", classBudgets);
        root.addProperty("adaptiveEnabledForBenchmark",
                Boolean.getBoolean("pyrovfx.bench.adaptive"));
        root.addProperty("adaptiveCpuTargetOverrideMs",
                System.getProperty("pyrovfx.bench.adaptiveTargetMs", "0"));
        root.addProperty("minimumAdaptiveSpawnScale", run.minimumAdaptiveScale);
        root.addProperty("lastAdaptiveHeapSpawnScale", ClientVfxManager.adaptiveMemoryScale());
        root.addProperty("peakAdaptiveCpuEmaMs", run.peakAdaptiveCpuEmaMs);
        root.addProperty("adaptiveConstrainedTickSamples", run.adaptiveConstrainedTickSamples);
        if (!ClientVfxManager.instances().isEmpty() && !ClientVfxManager.instances().getFirst().emitters().isEmpty()) {
            var emitter = ClientVfxManager.instances().getFirst().emitters().getFirst();
            root.addProperty("selectedParticleBackend", emitter.selectedBackend());
            root.addProperty("backendSelectionReason", emitter.backendReason());
            root.addProperty("selectedRenderer", emitter.selectedRenderer());
            root.addProperty("gpuResidentBirthBytes", emitter.gpuResidentBirthBytes());
            root.addProperty("allocatedParticleStateBytes", emitter.allocatedParticleStateBytes());
        } else {
            root.addProperty("selectedParticleBackend", "instance expired");
            root.addProperty("backendSelectionReason", "instance not retained at report collection");
        }
        root.addProperty("effect", run.scenario.effect().toString());
        root.addProperty("effectSha256", effectHash(run.scenario.effect()));
        root.addProperty("seed", run.scenario.seed());
        root.addProperty("requestedParticles", run.scenario.particles());
        root.addProperty("peakActiveParticles", run.peakParticles);
        root.addProperty("collisionContacts", run.collisionContacts);
        root.addProperty("collisionOrderedHitCount", run.collisionOrderedHitCount);
        root.addProperty("collisionCacheHits", run.collisionCacheHits);
        root.addProperty("collisionCacheMisses", run.collisionCacheMisses);
        root.addProperty("collisionCacheDisabled", Boolean.getBoolean("pyrovfx.collision.disableCache"));
        root.addProperty("collisionEventObserved", run.collisionEventObserved);
        root.addProperty("reloadCompleted", run.reloadCompleted);
        root.addProperty("reloadCyclesCompleted", run.reloadCyclesCompleted);
        root.addProperty("warmupTicks", run.scenario.warmupTicks());
        root.addProperty("measurementTicks", run.scenario.sampleTicks());
        root.addProperty("measurementFrameSamples", run.frameCount);
        root.addProperty("measurementTickSamples", run.tickCount);
        root.addProperty("position", "0.5,140.0,0.5");
        root.addProperty("camera", "0.5,141.5,-12.0 yaw=0 pitch=14");
        root.addProperty("observedCamera", run.observedCamera);
        root.addProperty("observedYaw", run.observedYaw);
        root.addProperty("observedPitch", run.observedPitch);
        root.addProperty("targetWorldDayTime", 6000);
        root.addProperty("spawnCpuMs", run.spawnNs / 1_000_000.0);
        root.addProperty("medianFrameMs", finite(percentile(run.framesNs, run.frameCount, 0.5)));
        root.addProperty("p95FrameMs", finite(percentile(run.framesNs, run.frameCount, 0.95)));
        root.addProperty("p99FrameMs", finite(percentile(run.framesNs, run.frameCount, 0.99)));
        double medianFrame = percentile(run.framesNs, run.frameCount, 0.5);
        root.addProperty("medianFps", medianFrame > 0 && Double.isFinite(medianFrame) ? 1000.0 / medianFrame : null);
        int[] fps = java.util.Arrays.copyOf(run.minecraftFpsSamples, run.minecraftFpsCount);
        java.util.Arrays.sort(fps);
        root.addProperty("minecraftReportedFpsMedian", fps.length == 0 ? null : fps[fps.length / 2]);
        root.addProperty("minecraftReportedFpsMin", fps.length == 0 ? null : fps[0]);
        root.addProperty("minecraftReportedFpsMax", fps.length == 0 ? null : fps[fps.length - 1]);
        root.addProperty("minecraftReportedFpsSamples", fps.length);
        root.addProperty("medianSimulationMs", finite(percentile(run.simulationNs, run.tickCount, 0.5)));
        root.addProperty("p95SimulationMs", finite(percentile(run.simulationNs, run.tickCount, 0.95)));
        root.addProperty("medianRenderSubmissionMs", finite(percentile(run.renderNs, run.renderCount, 0.5)));
        root.addProperty("p95RenderSubmissionMs", finite(percentile(run.renderNs, run.renderCount, 0.95)));
        root.addProperty("tickAllocatedBytes", run.tickAllocatedBytes >= 0 ? run.tickAllocatedBytes : null);
        root.addProperty("renderAllocatedBytes", run.renderAllocatedBytes >= 0 ? run.renderAllocatedBytes : null);
        root.addProperty("maxObservedHeapBytes", run.peakHeapBytes);
        root.addProperty("gcCollections", gcCollections() - run.startGcCollections);
        root.addProperty("gcTimeMs", gcTimeMs() - run.startGcTimeMs);
        root.addProperty("gpuTimeMs", run.gpuMedianMs);
        root.addProperty("gpuP95Ms", run.gpuP95Ms);
        root.addProperty("gpuSamples", run.gpuSamples);
        root.addProperty("runtimeGpuTelemetryRequested", Boolean.getBoolean("pyrovfx.bench.gpuTelemetry"));
        root.addProperty("runtimeGpuTelemetrySamples", run.runtimeGpuSamples);
        root.addProperty("runtimeGpuTelemetryLastMs", run.runtimeGpuLastMs);
        root.addProperty("runtimeFreeVramMiB",
                net.kofllee.pyrovfx.client.render.VfxGpuTelemetry.freeVramMiB());
        root.addProperty("runtimeTotalVramMiB",
                net.kofllee.pyrovfx.client.render.VfxGpuTelemetry.totalVramMiB());
        root.addProperty("drawCalls", (Integer) null);
        root.addProperty("serverTps", (Double) null);
        root.addProperty("screenshot", run.screenshotPath);
        root.addProperty("gpuTimingNote", run.gpuSamples > 0
                ? "Asynchronous GL_TIME_ELAPSED queries around VfxRenderer.render, excludes the rest of the Minecraft frame."
                : "No completed OpenGL timer queries; GPU time unavailable.");
        root.addProperty("frameTimingNote", "Wall-clock interval between consecutive Pyro VFX render-stage callbacks; mixed early/late stages can occur within one frame. Use minecraftReportedFpsMedian for full-client FPS, not medianFrameMs.");
        root.addProperty("renderTimingNote", "CPU wall-clock time of VfxRenderer.render; may contain driver calls, not GPU execution.");
        root.addProperty("allocationNote", "Current render/tick thread allocations if supported by HotSpot ThreadMXBean.");
        root.addProperty("recordedAtUtc", Instant.now().toString());

        JsonObject environment = new JsonObject();
        environment.addProperty("gitCommit", gitRevision());
        environment.addProperty("minecraft", SharedConstants.getCurrentVersion().getName());
        environment.addProperty("java", System.getProperty("java.version"));
        environment.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        environment.addProperty("cpu", System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "unavailable"));
        environment.addProperty("threads", Runtime.getRuntime().availableProcessors());
        environment.addProperty("maxHeapBytes", Runtime.getRuntime().maxMemory());
        environment.addProperty("gpuVendor", run.gpuVendor);
        environment.addProperty("gpuRenderer", run.gpuRenderer);
        environment.addProperty("openGlVersion", run.glVersion);
        environment.addProperty("vfxRendererCompatibility",
                net.kofllee.pyrovfx.client.render.VfxRenderCompatibility.reason());
        var graphicsMods = new com.google.gson.JsonArray();
        var installedMods = net.neoforged.fml.ModList.get();
        if (installedMods != null) {
            for (var mod : installedMods.getMods()) {
                var modId = mod.getModId();
                if (java.util.Set.of("sodium", "embeddium", "iris", "oculus",
                        "neoculus", "forgeoculus", "neopoculus").contains(modId)) {
                    graphicsMods.add(modId + "@" + mod.getVersion());
                }
            }
        }
        environment.add("graphicsMods", graphicsMods);
        environment.addProperty("width", minecraft.getWindow().getWidth());
        environment.addProperty("height", minecraft.getWindow().getHeight());
        environment.addProperty("world", minecraft.getSingleplayerServer() == null
                ? "unknown" : minecraft.getSingleplayerServer().getWorldData().getLevelName());
        environment.addProperty("renderDistance", minecraft.options.renderDistance().get());
        environment.addProperty("fov", minecraft.options.fov().get());
        environment.addProperty("vsync", minecraft.options.enableVsync().get());
        long benchWindow = minecraft.getWindow().getWindow();
        environment.addProperty("backgroundSecondMonitorRequested",
                Boolean.getBoolean("pyrovfx.bench.secondMonitorBackground"));
        environment.addProperty("windowFocused",
                GLFW.glfwGetWindowAttrib(benchWindow, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE);
        environment.addProperty("mousePassthrough",
                GLFW.glfwGetWindowAttrib(benchWindow, GLFW.GLFW_MOUSE_PASSTHROUGH) == GLFW.GLFW_TRUE);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer x = stack.mallocInt(1);
            IntBuffer y = stack.mallocInt(1);
            GLFW.glfwGetWindowPos(benchWindow, x, y);
            environment.addProperty("windowX", x.get(0));
            environment.addProperty("windowY", y.get(0));
        }
        root.add("environment", environment);

        Path jsonFile = output.resolve(id + ".json");
        Files.writeString(jsonFile, GSON.toJson(root), StandardCharsets.UTF_8);
        Path csvFile = output.resolve("results.csv");
        String header = "id,commit,scenario,requestedParticles,peakParticles,medianFrameMs,p95FrameMs,medianSimulationMs,medianRenderSubmissionMs,gcCollections,heapPeakBytes,status\n";
        if (!Files.exists(csvFile)) {
            Files.writeString(csvFile, header, StandardCharsets.UTF_8);
        }
        String row = String.join(",", id, environment.get("gitCommit").getAsString(), run.scenario.name(),
                String.valueOf(run.scenario.particles()), String.valueOf(run.peakParticles),
                asCsv(root, "medianFrameMs"), asCsv(root, "p95FrameMs"),
                asCsv(root, "medianSimulationMs"), asCsv(root, "medianRenderSubmissionMs"),
                asCsv(root, "gcCollections"), asCsv(root, "maxObservedHeapBytes"),
                root.get("status").getAsString()) + "\n";
        Files.writeString(csvFile, row, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        return jsonFile;
    }

    private static Double finite(double value) {
        return Double.isFinite(value) ? value : null;
    }

    private static String effectHash(net.minecraft.resources.ResourceLocation effect) {
        try {
            net.minecraft.resources.ResourceLocation resourcePath = net.minecraft.resources.ResourceLocation
                    .fromNamespaceAndPath(effect.getNamespace(), "vfx/" + effect.getPath() + ".json");
            var resource = Minecraft.getInstance().getResourceManager().getResource(resourcePath);
            if (resource.isEmpty()) {
                return "unknown";
            }
            try (var input = resource.get().open()) {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            }
        } catch (Exception error) {
            return "unknown";
        }
    }

    private static String asCsv(JsonObject object, String key) {
        return object.get(key).isJsonNull() ? "" : object.get(key).getAsString();
    }

    private static String gitRevision() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(Minecraft.getInstance().gameDirectory.getParentFile())
                    .redirectErrorStream(true).start();
            if (process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0) {
                String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (result.matches("[a-fA-F0-9]{40}")) {
                    return result;
                }
            } else {
                process.destroyForcibly();
            }
        } catch (Exception ignored) {
            // Missing git is a metadata limitation, not a reason to drop the benchmark report.
        }
        return "unknown";
    }
}
