package net.kofllee.pyrovfx.client.render;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL11;
import net.kofllee.pyrovfx.client.vfx.VfxBudgetConfig;
import java.util.ArrayDeque;

/**
 * Asynchronous GPU cost of Pyro's render stage, NOT total frame GPU time.
 * All OpenGL operations occur on the render thread; query results are read
 * only if available, never with a blocking GL_QUERY_RESULT wait.
 */
public final class VfxGpuTelemetry {
    private static final int MAX_QUERIES = 8;
    private static final ArrayDeque<Integer> FREE = new ArrayDeque<>();
    private static final ArrayDeque<Integer> PENDING = new ArrayDeque<>();
    private static boolean initialized;
    private static boolean unsupported;
    private static volatile boolean resetRequested;
    private static int active;
    private static volatile long lastSampleNs;
    private static volatile long samples;
    private static int skipped;
    private static volatile int freeVramMiB = -1;
    private static volatile int totalVramMiB = -1;
    private static int frames;

    private VfxGpuTelemetry() {}

    private static boolean enabled() {
        if (!VfxBudgetConfig.gpuTimingEnabled()) return false;
        // Bench harness already owns GL_TIME_ELAPSED queries; nesting is invalid.
        return System.getProperty("pyrovfx.bench.auto", "").isBlank()
                || Boolean.getBoolean("pyrovfx.bench.gpuTelemetry");
    }

    public static void begin() {
        if (!enabled() || unsupported) return;
        try {
            if (resetRequested) release();
            if (!initialized) {
                initialized = true;
                if (!GL.getCapabilities().OpenGL33) {
                    unsupported = true;
                    return;
                }
                for (int i = 0; i < MAX_QUERIES; i++) FREE.addLast(GL33.glGenQueries());
            }
            if (!(VfxBudgetConfig.nvidiaMemoryProbe()
                    || Boolean.getBoolean("pyrovfx.bench.vramProbe"))) {
                freeVramMiB = totalVramMiB = -1;
                frames = 0;
            } else if (++frames >= 120) {
                frames = 0;
                if (GL.getCapabilities().GL_NVX_gpu_memory_info) {
                    // NVX values are kibibytes of DEVICE memory, not Pyro-owned bytes.
                    int availableKiB = GL11.glGetInteger(0x9049);
                    int totalKiB = GL11.glGetInteger(0x9048);
                    if (availableKiB > 0 && totalKiB > 0 && availableKiB <= totalKiB) {
                        freeVramMiB = availableKiB / 1024;
                        totalVramMiB = totalKiB / 1024;
                    }
                }
            }
            poll();
            if (active != 0) return;
            if (FREE.isEmpty()) {
                skipped++;
                return;
            }
            active = FREE.removeFirst();
            GL33.glBeginQuery(GL33.GL_TIME_ELAPSED, active);
        } catch (RuntimeException error) {
            unsupported = true;
            release();
        }
    }

    public static void end() {
        if (active == 0) return;
        try {
            GL33.glEndQuery(GL33.GL_TIME_ELAPSED);
            PENDING.addLast(active);
            active = 0;
            poll();
        } catch (RuntimeException error) {
            unsupported = true;
            release();
        }
    }

    private static void poll() {
        while (!PENDING.isEmpty()) {
            int id = PENDING.peekFirst();
            if (GL33.glGetQueryObjecti(id, GL33.GL_QUERY_RESULT_AVAILABLE) == 0) break;
            lastSampleNs = Math.max(0, GL33.glGetQueryObjecti64(id, GL33.GL_QUERY_RESULT));
            samples++;
            FREE.addLast(PENDING.removeFirst());
        }
    }

    /** Mark on reload/unload; GL resource deletion happens at next render. */
    public static void requestReset() { resetRequested = true; }

    private static void release() {
        if (active != 0) {
            try { GL33.glEndQuery(GL33.GL_TIME_ELAPSED); }
            catch (RuntimeException ignored) { }
            FREE.addLast(active);
            active = 0;
        }
        for (int id : FREE) GL33.glDeleteQueries(id);
        for (int id : PENDING) GL33.glDeleteQueries(id);
        FREE.clear();
        PENDING.clear();
        initialized = false;
        resetRequested = false;
        lastSampleNs = 0;
        samples = 0;
        skipped = 0;
        freeVramMiB = totalVramMiB = -1;
        frames = 0;
    }

    public static long latestSampleNs() { return lastSampleNs; }
    public static long sampleCount() { return samples; }
    public static int skippedQueries() { return skipped; }
    public static boolean supported() { return initialized && !unsupported; }
    public static int freeVramMiB() { return freeVramMiB; }
    public static int totalVramMiB() { return totalVramMiB; }
}