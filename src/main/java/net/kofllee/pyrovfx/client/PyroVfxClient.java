package net.kofllee.pyrovfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.client.render.VfxRenderer;
import net.kofllee.pyrovfx.client.render.VfxGpuTelemetry;
import net.kofllee.pyrovfx.client.render.VfxNativeSpriteBatcher;
import net.kofllee.pyrovfx.client.render.VfxNativeModelRenderer;
import net.kofllee.pyrovfx.client.render.VfxGpuBirthBuffers;
import net.kofllee.pyrovfx.client.render.VfxNativeGpuGeometryBridge;
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
            VfxNativeSpriteBatcher.invalidateAfterReload();
            VfxNativeModelRenderer.invalidateAfterReload();
            VfxGpuBirthBuffers.invalidateAfterReload();
            VfxNativeGpuGeometryBridge.invalidateAfterReload();
            VfxGpuTelemetry.requestReset();
        }));
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        long start = VfxDevHooks.beforeTick();
        ClientVfxNetwork.tick();
        ClientVfxManager.tick();
        // Tick still fires after disconnecting to the title screen. Release
        // world-bound native geometry even if no later world render occurs.
        if (RenderSystem.isOnRenderThread()) {
            VfxGpuBirthBuffers.maintain();
            VfxNativeGpuGeometryBridge.maintain();
            VfxNativeModelRenderer.maintain();
            VfxNativeSpriteBatcher.maintain();
        }
        VfxDevHooks.afterTick(start);
    }

    @SubscribeEvent
    public static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            // Preserve the fog Minecraft computed while rendering world
            // geometry; AFTER_LEVEL may clear FogStart before our late pass.
            net.kofllee.pyrovfx.client.render.VfxGpuFog.captureWorldFog();
            VfxRenderer.beginWorldFrame();
        }
        // Geometry is routed by material semantics, not scene raycasts,
        // camera occluders, glass/water IDs or a special cloud-depth pass.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES
                && event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        boolean early = event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // Each material selects its supported NeoForge stage and native
        // material state independently of the particle's source backend.
        if (!VfxRenderer.hasActiveForStage(event.getCamera(), partial, early)) {
            if (!early) VfxGpuBirthBuffers.maintain();
            return;
        }
        VfxRenderer.markWorldStage(early);

        long start = VfxDevHooks.beforeRender();
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
        try (var restoredFog = net.kofllee.pyrovfx.client.render.VfxGpuFog.openWorldPass()) {
            viewStack.identity().rotate(
                    net.kofllee.pyrovfx.vfx.math.VfxCameraRelativeView.rotation(event.getCamera().rotation()));
            RenderSystem.applyModelViewMatrix();
            VfxRenderer.render(new PoseStack(), event.getCamera(), partial, early);
        } finally {
            viewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            VfxDevHooks.afterRender(start);
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