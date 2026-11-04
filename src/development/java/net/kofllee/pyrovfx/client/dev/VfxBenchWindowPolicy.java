package net.kofllee.pyrovfx.client.dev;

import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

/**
 * Development-only, opt-in second-monitor benchmark policy.
 * Keep the Minecraft cursor released and pass mouse clicks through to the
 * user's other applications. Does not focus or iconify the benchmark window.
 *
 * The Windows launcher restores the previously focused application after
 * Minecraft creates its GLFW window; moving a window cannot prevent all
 * initial OS focus changes by itself.
 */
final class VfxBenchWindowPolicy {
    private static final boolean ENABLED = Boolean.getBoolean("pyrovfx.bench.secondMonitorBackground");
    private static boolean positioned;
    private static boolean failed;

    private VfxBenchWindowPolicy() {}

    static boolean apply(Minecraft minecraft) {
        if (!ENABLED) return true;
        if (failed) return false;
        if (!positioned) {
            long handle = minecraft.getWindow().getWindow();
            long primary = GLFW.glfwGetPrimaryMonitor();
            PointerBuffer monitors = GLFW.glfwGetMonitors();
            long secondary = 0L;
            if (monitors != null) {
                for (int i = 0; i < monitors.limit(); i++) {
                    long candidate = monitors.get(i);
                    if (candidate != primary) {
                        secondary = candidate;
                        break;
                    }
                }
            }
            if (secondary == 0L) {
                System.err.println("[Pyro VFX Bench] Secondary monitor unavailable; refusing GUI benchmark.");
                failed = true;
                return false;
            }
            GLFWVidMode mode = GLFW.glfwGetVideoMode(secondary);
            if (mode == null) {
                failed = true;
                return false;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer x = stack.mallocInt(1);
                IntBuffer y = stack.mallocInt(1);
                GLFW.glfwGetMonitorPos(secondary, x, y);
                int width = minecraft.getWindow().getWidth();
                int height = minecraft.getWindow().getHeight();
                if (width > mode.width() || height > mode.height()) {
                    System.err.println("[Pyro VFX Bench] Benchmark window larger than secondary monitor.");
                    failed = true;
                    return false;
                }
                GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
                GLFW.glfwSetWindowPos(handle,
                        x.get(0) + Math.max(0, (mode.width() - width) / 2),
                        y.get(0) + Math.max(0, (mode.height() - height) / 2));
                GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_MOUSE_PASSTHROUGH, GLFW.GLFW_TRUE);
                positioned = true;
                System.out.println("[Pyro VFX Bench] Secondary display, passive cursor, window: "
                        + width + "x" + height);
            }
        }
        // Minecraft may re-grab the mouse while loading the integrated world.
        minecraft.mouseHandler.releaseMouse();
        return true;
    }
}