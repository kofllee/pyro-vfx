package net.kofllee.pyrovfx.client.ui;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.client.vfx.VfxBudgetConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.kofllee.pyroui.PyroUiTypographyGalleryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Editor command is client-local and never needs dedicated-server registration. */
@EventBusSubscriber(modid = PyroVfx.MOD_ID, value = Dist.CLIENT)
public final class PyroUiClientCommands {
    private static boolean autoShown;
    private PyroUiClientCommands() {}

    @SubscribeEvent public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pyrovfx")
                .then(Commands.literal("editor").executes(context -> {
                    if (!VfxBudgetConfig.editorEnabled()) {
                        context.getSource().sendFailure(Component.literal(
                                "Enable editor.enable_editor in the Pyro VFX client config first."));
                        return 0;
                    }
                    Minecraft.getInstance().execute(() ->
                            Minecraft.getInstance().setScreen(new PyroUiShowcaseScreen()));
                    return 1;
                }))
                .then(Commands.literal("typography").executes(context -> {
                    Minecraft.getInstance().execute(() ->
                            Minecraft.getInstance().setScreen(new PyroUiTypographyGalleryScreen()));
                    return 1;
                }))
                .then(Commands.literal("ui_gallery").executes(context -> {
                    Minecraft.getInstance().execute(() ->
                            Minecraft.getInstance().setScreen(new PyroUiTypographyGalleryScreen()));
                    return 1;
                })));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        boolean gallery = Boolean.getBoolean("pyrovfx.ui.gallery");
        boolean showcase = Boolean.getBoolean("pyrovfx.ui.showcase")
                && VfxBudgetConfig.editorEnabled();
        if (autoShown || (!showcase && !gallery)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && mc.screen == null) {
            autoShown = true;
            mc.setScreen(gallery ? new PyroUiTypographyGalleryScreen() : new PyroUiShowcaseScreen());
        }
    }
}
