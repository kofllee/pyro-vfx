package net.kofllee.pyroui.mixin;

import net.kofllee.pyroui.PyroScreen;
import net.kofllee.pyroui.GameKeyAccess;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * IN_GAME key mappings normally appear released whenever a Screen is open.
 * A captured Minecraft viewport is still a real gameplay context. Override
 * only this condition, never GUI or custom mod key contexts.
 */
@Mixin(KeyMapping.class)
public abstract class GameViewportKeyMappingMixin implements GameKeyAccess {
    @Shadow private boolean isDown;
    @Shadow private int clickCount;

    @Override public void pyroUi$setGameKey(boolean pressed,boolean click) {
        if(pressed && isDown)click=false; // Ignore OS key-repeat clicks.
        isDown=pressed;
        if(click)clickCount++;
    }
    @Override public boolean pyroUi$rawGameKeyDown() {return isDown;}

    @Inject(method="isDown",at=@At("HEAD"),cancellable=true)
    private void pyroUi$gameFocusedKeyState(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof PyroScreen screen && screen.gameplayCaptured()) {
            KeyMapping mapping=(KeyMapping)(Object)this;
            if(mapping.getKeyConflictContext()==KeyConflictContext.IN_GAME ||
                    mapping.getKeyConflictContext()==KeyConflictContext.UNIVERSAL)
                cir.setReturnValue(isDown);
        }
    }
}
