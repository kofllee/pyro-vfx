package net.kofllee.pyrovfx.client;

import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.client.render.VfxRenderer;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.vfx.resource.VfxReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(
        modid = PyroVfx.MOD_ID,
        value = Dist.CLIENT
)
public final class PyroVfxClient {
    private PyroVfxClient() {}

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new VfxReloadListener());
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        ClientVfxManager.tick();
    }

    @SubscribeEvent
    public static void renderLevel(RenderLevelStageEvent event) {
        if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        VfxRenderer.render(event.getPoseStack(), event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        Minecraft minecraft = Minecraft.getInstance();

        minecraft.getResourceManager()
                .listResources("models/vfx", id -> id.getPath().endsWith(".json"))
                .keySet()
                .forEach(id -> {
                    String path = id.getPath();
                    String modelPath = path.substring("models/".length(), path.length() - ".json".length());

                    event.register(ModelResourceLocation.standalone(
                            ResourceLocation.fromNamespaceAndPath(id.getNamespace(), modelPath)
                    ));
                });
    }
}