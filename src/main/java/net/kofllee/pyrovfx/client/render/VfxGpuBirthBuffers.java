package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayDeque;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;

/**
 * Owns resident analytic birth VBO generations and deferred GL deletion.
 * Simulation is shared by native sprite/model geometry sources; no fragment
 * shaders or independent final render backend are owned here.
 */
public final class VfxGpuBirthBuffers {
    private static final ArrayDeque<Integer> RETIRED = new ArrayDeque<>();
    private static int generation;
    private static volatile boolean reloadPending;

    private VfxGpuBirthBuffers() {}

    public static boolean gpuPossible() {
        if (Boolean.getBoolean("pyrovfx.analytic.forceCpu")
                || Boolean.getBoolean("pyrovfx.render.forceLegacy")) return false;
        try {
            return GL.getCapabilities() != null && GL.getCapabilities().OpenGL33;
        } catch (IllegalStateException unavailableContext) {
            return false;
        }
    }

    public static int generation() { return generation; }

    /** Reload callbacks may run off-thread; destruction always runs on render thread. */
    public static void invalidateAfterReload() { reloadPending = true; }

    /** Buffer ownership is transferred into the retirement queue on the client thread. */
    public static void retireBuffer(int id) {
        if (id > 0) RETIRED.addLast(id);
    }

    public static void maintain() {
        RenderSystem.assertOnRenderThread();
        if (reloadPending) {
            generation++;
            reloadPending = false;
        }
        while (!RETIRED.isEmpty()) GL15.glDeleteBuffers(RETIRED.removeFirst());
    }
}
