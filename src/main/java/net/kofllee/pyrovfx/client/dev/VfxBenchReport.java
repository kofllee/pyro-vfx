package net.kofllee.pyrovfx.client.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.nio.file.Files;
import java.nio.file.Path;
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
        root.addProperty("cpuParticleBackend", Boolean.getBoolean("pyrovfx.cpu.forceLegacy")
                ? "legacy-forced" : "auto-dense-with-feature-fallback");
        root.addProperty("effect", run.scenario.effect().toString());
        root.addProperty("effectSha256", effectHash(run.scenario.effect()));
        root.addProperty("seed", run.scenario.seed());
        root.addProperty("requestedParticles", run.scenario.particles());
        root.addProperty("peakActiveParticles", run.peakParticles);
        root.addProperty("collisionContacts", run.collisionContacts);
        root.addProperty("collisionEventObserved", run.collisionEventObserved);
        root.addProperty("reloadCompleted", run.reloadCompleted);
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
        root.addProperty("drawCalls", (Integer) null);
        root.addProperty("serverTps", (Double) null);
        root.addProperty("screenshot", run.screenshotPath);
        root.addProperty("gpuTimingNote", run.gpuSamples > 0
                ? "Asynchronous GL_TIME_ELAPSED queries around VfxRenderer.render, excludes the rest of the Minecraft frame."
                : "No completed OpenGL timer queries; GPU time unavailable.");
        root.addProperty("frameTimingNote", "Wall-clock interval between AFTER_PARTICLES render-stage callbacks; not a GPU timer.");
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
        environment.addProperty("width", minecraft.getWindow().getWidth());
        environment.addProperty("height", minecraft.getWindow().getHeight());
        environment.addProperty("world", minecraft.getSingleplayerServer() == null
                ? "unknown" : minecraft.getSingleplayerServer().getWorldData().getLevelName());
        environment.addProperty("renderDistance", minecraft.options.renderDistance().get());
        environment.addProperty("fov", minecraft.options.fov().get());
        environment.addProperty("vsync", minecraft.options.enableVsync().get());
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
