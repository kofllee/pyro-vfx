package net.kofllee.pyrovfx.client.dev;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33;

import java.util.ArrayDeque;

/**
 * Asynchronous GPU timing for one Pyro VFX render submission. Call only on render thread.
 * A bounded query pool prevents unbounded driver queue growth and never waits for the GPU.
 */
final class VfxBenchGpuTimer {
    private static final int QUERY_POOL_SIZE = 12;

    private final ArrayDeque<Integer> available = new ArrayDeque<>();
    private final ArrayDeque<Integer> inFlight = new ArrayDeque<>();
    private final long[] samplesNs = new long[4096];
    private final boolean supported;
    private int active;
    private int count;

    VfxBenchGpuTimer() {
        supported = GL.getCapabilities().OpenGL33;
        if (supported) {
            for (int i = 0; i < QUERY_POOL_SIZE; i++) {
                available.add(GL33.glGenQueries());
            }
        }
    }

    void begin() {
        if (!supported) {
            return;
        }
        poll();
        if (!available.isEmpty()) {
            active = available.removeFirst();
            GL33.glBeginQuery(GL33.GL_TIME_ELAPSED, active);
        }
    }

    void end() {
        if (active != 0) {
            GL33.glEndQuery(GL33.GL_TIME_ELAPSED);
            inFlight.addLast(active);
            active = 0;
        }
    }

    void poll() {
        if (!supported) {
            return;
        }
        while (!inFlight.isEmpty()) {
            int id = inFlight.peekFirst();
            if (GL33.glGetQueryObjecti(id, GL33.GL_QUERY_RESULT_AVAILABLE) == 0) {
                break;
            }
            long nanos = GL33.glGetQueryObjecti64(id, GL33.GL_QUERY_RESULT);
            if (count < samplesNs.length) {
                samplesNs[count++] = nanos;
            }
            available.addLast(inFlight.removeFirst());
        }
    }

    Double medianMs() {
        poll();
        double result = VfxBenchReport.percentile(samplesNs, count, 0.5);
        return Double.isFinite(result) ? result : null;
    }

    Double p95Ms() {
        poll();
        double result = VfxBenchReport.percentile(samplesNs, count, 0.95);
        return Double.isFinite(result) ? result : null;
    }

    int sampleCount() {
        return count;
    }

    void close() {
        if (!supported) {
            return;
        }
        end();
        for (int id : available) {
            GL33.glDeleteQueries(id);
        }
        for (int id : inFlight) {
            GL33.glDeleteQueries(id);
        }
        available.clear();
        inFlight.clear();
    }
}
