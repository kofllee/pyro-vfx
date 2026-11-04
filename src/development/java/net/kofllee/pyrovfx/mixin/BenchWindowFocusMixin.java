package net.kofllee.pyrovfx.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Development benchmark only. GLFW otherwise defaults to focusing a newly
 * created window before our later tick hook can release its mouse.
 * The hook is placed after Minecraft resets its GLFW hints and before
 * NeoForge creates the native window (including the early loading screen).
 * Normal Minecraft launches are completely unaffected.
 */
@Mixin(Window.class)
public abstract class BenchWindowFocusMixin {
    @Inject(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J",
            shift = At.Shift.BEFORE), require = 1)
    private void pyro$passiveWindow(CallbackInfo ci) {
        if (!Boolean.getBoolean("pyrovfx.bench.secondMonitorBackground")) return;
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
    }
}