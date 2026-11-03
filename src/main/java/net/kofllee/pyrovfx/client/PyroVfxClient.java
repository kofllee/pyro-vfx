package net.kofllee.pyrovfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.client.dev.VfxBenchHarness;
import net.kofllee.pyrovfx.client.render.VfxRenderer;
import net.kofllee.pyrovfx.client.render.VfxGpuTelemetry;
import net.kofllee.pyrovfx.client.render.VfxInstancedSpriteRenderer;
import net.kofllee.pyrovfx.client.render.VfxInstancedModelRenderer;
import net.kofllee.pyrovfx.client.render.VfxAnalyticSpriteRenderer;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.client.vfx.sampling.VfxModelSpawnShapeSampler;
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
        event.registerReloadListener(new VfxReloadListener(() -> {
            VfxModelSpawnShapeSampler.clearCache();
            VfxInstancedSpriteRenderer.invalidateAfterReload();
            VfxInstancedModelRenderer.invalidateAfterReload();
            VfxAnalyticSpriteRenderer.invalidateAfterReload();
            VfxGpuTelemetry.requestReset();
        }));
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        long start = VfxBenchHarness.beforeTick();
        ClientVfxNetwork.tick();
        ClientVfxManager.tick();
        VfxBenchHarness.afterTick(start);
    }

    @SubscribeEvent
    public static void renderLevel(RenderLevelStageEvent event) {
        // AFTER_PARTICLES is before vanilla's clouds/weather composite in 1.21.1,
        // causing clouds behind alpha smoke to paint over it. Render against
        // the finished world depth/color, before hand and HUD instead.
        if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        long start = VfxBenchHarness.beforeRender();
        // AFTER_LEVEL is dispatched after LevelRenderer has unwound the
        // camera view transform. Using RenderSystem.getModelViewMatrix() here
        // without restoring the view makes camera-relative particle positions
        // appear glued to the screen as the player turns.
        //
        // Pyro geometry is already camera-relative (world - camera.position).
        // Reapply ONLY the inverse camera rotation, not camera translation,
        // and restore Minecraft's original stack even if rendering fails.
        var viewStack = RenderSystem.getModelViewStack();
        viewStack.pushMatrix();
        try {
            viewStack.identity().rotate(
                    net.kofllee.pyrovfx.vfx.math.VfxCameraRelativeView.rotation(event.getCamera().rotation()));
            RenderSystem.applyModelViewMatrix();
            VfxRenderer.render(new PoseStack(), event.getCamera(),
                    event.getPartialTick().getGameTimeDeltaPartialTick(false));
        } finally {
            viewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            VfxBenchHarness.afterRender(start);
        }
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