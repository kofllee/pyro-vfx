package net.kofllee.pyroui.mixin;

import net.kofllee.pyroui.GameKeyAccess;
import net.kofllee.pyroui.PyroScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sneak and sprint use ToggleKeyMapping, whose isDown() OVERRIDES the normal
 * KeyMapping implementation. Without this bridge it always sees the open
 * editor Screen as an inactive in-game key context.
 */
@Mixin(ToggleKeyMapping.class)
public abstract class GameViewportToggleKeyMappingMixin {
    @Inject(method="isDown",at=@At("HEAD"),cancellable=true)
    private void pyroUi$capturedToggleBinding(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc=Minecraft.getInstance();
        if(!(mc.screen instanceof PyroScreen screen) || !screen.gameplayCaptured())return;
        KeyMapping binding=(KeyMapping)(Object)this;
        if(binding.getKeyConflictContext()==KeyConflictContext.IN_GAME ||
                binding.getKeyConflictContext()==KeyConflictContext.UNIVERSAL)
            cir.setReturnValue(((GameKeyAccess)(Object)this).pyroUi$rawGameKeyDown());
    }
}