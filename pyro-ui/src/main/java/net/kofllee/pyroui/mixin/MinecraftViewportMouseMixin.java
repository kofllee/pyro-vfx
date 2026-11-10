package net.kofllee.pyroui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.kofllee.pyroui.PyroScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Let the vanilla MouseHandler turn the player when a docked Minecraft view
 * has focus. It receives physical GLFW deltas and applies all vanilla/NeoForge
 * sensitivity, scoped zoom, invert-Y and cinematic smoothing calculations.
 *
 * Only the one isMouseGrabbed() check in handleAccumulatedMovement is widened:
 * the editor remains an active Screen and gameplay capture doesn't close it.
 */
@Mixin(MouseHandler.class)
public abstract class MinecraftViewportMouseMixin {
    @ModifyExpressionValue(
        method = "handleAccumulatedMovement",
        at = @At(value="INVOKE",
            target="Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z")
    )
    private boolean pyroUi$turnPlayerInFocusedViewport(boolean nativeGrabbed) {
        if(nativeGrabbed)return true;
        Minecraft mc=Minecraft.getInstance();
        return mc.screen instanceof PyroScreen screen
                && screen.gameplayCaptured() && mc.player!=null && mc.level!=null;
    }
}
