package net.kofllee.pyroui.mixin;

import net.kofllee.pyroui.PyroScreen;
import net.kofllee.pyroui.GameClickAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft normally skips vanilla gameplay key processing whenever a Screen
 * exists. Only a captured Pyro UI game viewport enables it again, preserving
 * the editor Screen and vanilla attack/use/hotbar/perspective behavior.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftGameplayMixin implements GameClickAccess {
    @Shadow private void handleKeybinds() {}
    @Shadow private void continueAttack(boolean active) {}
    @Shadow private boolean startAttack() {return false;}
    @Shadow private void startUseItem() {}
    @Shadow private void pickBlock() {}
    @Unique private Screen pyroUi$suspendedWorkspace;

    /**
     * Process a click on the input event, rather than deferring a clickCount
     * until the next 20Hz client tick. The original methods retain NeoForge
     * interaction hooks, ray hit handling, cooldowns and packet semantics.
     */
    @Override public void pyroUi$clickGame(int button) {
        Minecraft mc=(Minecraft)(Object)this;
        if(!(mc.screen instanceof PyroScreen screen) || !screen.gameplayCaptured()
                || mc.player==null || mc.level==null)return;
        switch(button) {
            case 0 -> startAttack();
            case 1 -> startUseItem();
            case 2 -> pickBlock();
            default -> {}
        }
    }

    /**
     * Inventory, chat, recipe and other vanilla screens may temporarily take
     * over the display during gameplay. On their normal close, return to the
     * dock workspace instead of losing it and entering fullscreen gameplay.
     */
    @Inject(method="setScreen",at=@At("HEAD"),cancellable=true)
    private void pyroUi$preserveWorkspace(Screen next,CallbackInfo ci) {
        Minecraft mc=(Minecraft)(Object)this;
        if(mc.screen instanceof PyroScreen editor && mc.player!=null && mc.level!=null) {
            if(next==null && editor.nestedGameScreenOpen()) {
                editor.closeNestedGameScreen();
                ci.cancel();
                return;
            }
            if(next!=null && next!=editor &&
                    !(next instanceof net.minecraft.client.gui.screens.TitleScreen) &&
                    (editor.gameplayCaptured() || editor.nestedGameScreenOpen())) {
                if(editor.openNestedGameScreen(next)) {
                    ci.cancel();
                    return;
                }
            }
        }
        if(next==null && mc.screen instanceof PyroScreen editor &&
                editor.gameplayCaptured() && mc.level!=null) {
            // Some vanilla actions ask for the normal in-game view while a
            // Screen is installed. Never discard the Pyro workspace for that.
            editor.exitGameplayFocus();
            ci.cancel();
            return;
        }
        if(mc.screen instanceof PyroScreen editor && editor.gameplayCaptured()
                && next!=null && next!=editor) {
            pyroUi$suspendedWorkspace=editor;
            editor.suspendForVanillaScreen();
            return;
        }
        if(next==null && pyroUi$suspendedWorkspace!=null
                && mc.screen!=pyroUi$suspendedWorkspace) {
            Screen resume=pyroUi$suspendedWorkspace;
            pyroUi$suspendedWorkspace=null;
            if(mc.level!=null && mc.player!=null) {
                ((PyroScreen)resume).resumeFromVanillaScreen();
                mc.setScreen(resume);
                ci.cancel();
            }
        } else if(next instanceof PyroScreen) {
            ((PyroScreen)next).resumeFromVanillaScreen();
            pyroUi$suspendedWorkspace=null;
        }
    }

    @Inject(method="tick",at=@At("HEAD"))
    private void pyroUi$pollGameplayModifiers(CallbackInfo ci) {
        Minecraft mc=(Minecraft)(Object)this;
        if(mc.screen instanceof PyroScreen screen && screen.gameplayCaptured())
            screen.pollGameplayModifiers();
    }

    @Inject(method="tick",at=@At("TAIL"))
    private void pyroUi$gameplayWindowTick(CallbackInfo ci) {
        Minecraft minecraft=(Minecraft)(Object)this;
        if(!(minecraft.screen instanceof PyroScreen screen)
                || !screen.gameplayCaptured()
                || minecraft.player==null || minecraft.level==null)return;
        handleKeybinds();
        // Vanilla handleKeybinds uses screen == null as a guard for continuous
        // mining; restore it only for a gameplay-focused embedded viewport.
        // A vanilla inventory/chat action may have replaced the editor while
        // handling keys above: never keep mining after that transition.
        if(minecraft.screen==screen && screen.gameplayCaptured())
            continueAttack(minecraft.options.keyAttack.isDown());
    }
}
