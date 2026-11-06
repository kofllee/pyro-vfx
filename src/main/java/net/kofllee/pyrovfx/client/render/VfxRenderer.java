package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.kofllee.pyrovfx.client.vfx.ClientVfxInstance;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.kofllee.pyrovfx.client.vfx.ClientVfxEmitter;
import net.kofllee.pyrovfx.vfx.type.VfxRenderType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

public final class VfxRenderer {
    private VfxRenderer() {}
    private static int culledAnalyticCohorts;
    private static int culledAnalyticParticles;
    private static String lastWorldStage = "none";
    private static boolean earlyRendered, lateRendered;
    private static long totalEarlyPasses, totalLatePasses, totalMixedFrames;
    public static int culledAnalyticCohorts() { return culledAnalyticCohorts; }
    public static int culledAnalyticParticles() { return culledAnalyticParticles; }
    public static int analyticDrawCalls() {
        return VfxNativeGpuGeometryBridge.drawCalls();
    }
    public static int analyticInstances() {
        return VfxNativeGpuGeometryBridge.instances();
    }
    public static int analyticUploadedBytes() {
        return VfxNativeGpuGeometryBridge.uploadedBytes();
    }
    public static int analyticDatasetUploads() {
        return VfxNativeGpuGeometryBridge.uploadedDatasets();
    }
    public static String lastWorldStage() { return lastWorldStage; }
    public static long totalEarlyPasses() { return totalEarlyPasses; }
    public static long totalLatePasses() { return totalLatePasses; }
    public static long totalMixedFrames() { return totalMixedFrames; }
    public static void beginWorldFrame() {
        earlyRendered = lateRendered = false;
        VfxNativeSpriteBatcher.resetFrameStats();
        VfxNativeModelRenderer.resetFrameStats();
        VfxNativeGpuGeometryBridge.resetFrameStats();
        culledAnalyticCohorts = culledAnalyticParticles = 0;
    }
    public static void markWorldStage(boolean early) {
        if (early) {
            earlyRendered = true;
            totalEarlyPasses++;
        } else {
            lateRendered = true;
            totalLatePasses++;
            if (earlyRendered) totalMixedFrames++;
        }
        lastWorldStage = earlyRendered && lateRendered ? "mixed"
                : early ? "before_translucent_world" : "after_level";
    }

    /**
     * Material-level pass routing shared by every simulation backend.
     * This does not inspect the camera, sampled particle locations, block IDs,
     * tags or raycast hits. Pixel-perfect alpha intersection sorting is outside
     * what the vanilla renderer and arbitrary shaderpacks promise.
     */
    public static boolean hasActiveForStage(Camera camera, float partialTick, boolean early) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || ClientVfxManager.getActiveParticleCount() == 0) return false;
        for (var effect : ClientVfxManager.instances()) {
            for (var emitter : effect.emitters()) {
                if (emitter.liveParticleCount() > 0 && materialEarly(emitter) == early)
                    return true;
            }
        }
        return false;
    }

    private static boolean materialEarly(ClientVfxEmitter emitter) {
        return VfxRenderMaterial.of(emitter.definition()).pass()
                == VfxRenderMaterial.Pass.BEFORE_TRANSLUCENT_WORLD;
    }

    public static void render(PoseStack poseStack, Camera camera, float partialTick,
                              boolean earlyStage){
        VfxRenderLight.beginFrame();
        Minecraft minecraft = Minecraft.getInstance();
        // Do not start expensive GL driver timer queries in ordinary worlds
        // where Pyro has nothing to render.
        boolean telemetryFrame = ClientVfxManager.getActiveParticleCount() > 0;
        if (telemetryFrame) VfxGpuTelemetry.begin();
        VfxGpuBirthBuffers.maintain();
        VfxNativeGpuGeometryBridge.maintain();

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

        Matrix4f frameMvp = null; // Only build the matrix if an analytic cohort needs testing.
        boolean gpuFrame = false;
        boolean queuedVanilla = false;
        // The first draw must drain other mods' shared BufferSource, but an
        // empty endBatch() for every later resident cohort/independent emitter
        // is pure render-thread overhead. Only repeat after our own RenderType
        // geometry has actually been queued.
        boolean sharedBufferFlushed = false;
        ClientVfxManager.beginRender();
        try {
            for(ClientVfxInstance instance: ClientVfxManager.instances()){
                var sourceTransform = instance.renderTransform(partialTick);
                for(var emitter : instance.emitters()) {
                    if (emitter.liveParticleCount() == 0
                            || materialEarly(emitter) != earlyStage)
                        continue;
                    emitter.setRenderTransform(sourceTransform, partialTick);
                    for (var analytic : emitter.analyticPools()) {
                        if (analytic.liveCount() == 0) continue;
                        if (frameMvp == null) {
                            frameMvp = new Matrix4f(RenderSystem.getProjectionMatrix())
                                    .mul(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());
                        }
                        if (!analytic.mayBeVisible(frameMvp, camera.getPosition(), partialTick)) {
                            culledAnalyticCohorts++;
                            culledAnalyticParticles += analytic.liveCount();
                            continue;
                        }
                        if (gpuFrame) VfxNativeSpriteBatcher.flush();
                        if (!sharedBufferFlushed || queuedVanilla) {
                            bufferSource.endBatch();
                            sharedBufferFlushed = true;
                            queuedVanilla = false;
                        }
                        // Native Minecraft shader submission for supported GPU-resident geometry.
                        if (analytic.model()) VfxNativeGpuGeometryBridge.flush();
                        boolean nativeDrawn = !analytic.model()
                                && VfxNativeGpuGeometryBridge.render(analytic, camera, partialTick);
                        boolean rendered = nativeDrawn || (analytic.model()
                                && VfxNativeModelRenderer.renderAnalytic(analytic, poseStack, camera, partialTick));
                        if (!rendered) {
                            // A fallback/opaque model may alter depth or shared
                            // Minecraft buffers. Submit pending additive geometry first.
                            VfxNativeGpuGeometryBridge.flush();
                            emitter.recordRenderer("Minecraft sprite fallback (analytic GPU unavailable)");
                            analytic.renderFallback(poseStack, bufferSource, camera, partialTick);
                            queuedVanilla = true;
                        } else {
                            emitter.recordRenderer(nativeDrawn ? "Native Minecraft GPU geometry bridge"
                                    : analytic.model() ? "GPU analytic model (resident birth data)"
                                    : "GPU analytic sprite (resident birth data)");
                        }
                    }
                    var pool = emitter.densePool();
                    if (pool != null && pool.size() > 0) VfxNativeGpuGeometryBridge.flush();
                    if (pool != null && pool.size() > 0
                            && VfxNativeSpriteBatcher.matches(pool.definition())) {
                        // World vertex buffers must flush before raw GL draws,
                        // preserving Minecraft's RenderType ordering.
                        if (queuedVanilla || !sharedBufferFlushed) {
                            // The shared BufferSource may also contain work
                            // submitted by another mod before our event.
                            bufferSource.endBatch();
                            sharedBufferFlushed = true;
                            queuedVanilla = false;
                        }
                        if (!gpuFrame) {
                            VfxNativeSpriteBatcher.begin(poseStack, camera, partialTick);
                            gpuFrame = true;
                        }
                        VfxNativeSpriteBatcher.append(pool);
                        emitter.recordRenderer(pool.definition().render().sprite().blendMode()
                                == net.kofllee.pyrovfx.vfx.type.VfxBlendMode.ALPHA
                                ? "Native Minecraft alpha sprite batch (sorted)"
                                : "Native Minecraft additive sprite batch");
                    } else {
                        if (gpuFrame) VfxNativeSpriteBatcher.flush();
                        if (pool != null) {
                            boolean modelInstanced = false;
                            boolean translucentModel = pool.model()
                                    && pool.definition().render().model() != null
                                    && pool.definition().render().model().renderLayer()
                                        == net.kofllee.pyrovfx.vfx.type.VfxModelRenderLayer.TRANSLUCENT
                                    && VfxNativeSpriteBatcher.compatibleEnvironment()
                                    && VfxNativeSpriteBatcher.ready();
                            if (translucentModel) {
                                if (!gpuFrame) {
                                    if (!sharedBufferFlushed || queuedVanilla) {
                                        bufferSource.endBatch();
                                        sharedBufferFlushed = true;
                                        queuedVanilla = false;
                                    }
                                    VfxNativeSpriteBatcher.begin(poseStack, camera, partialTick);
                                    gpuFrame = true;
                                }
                                for (int i = 0; i < pool.size(); i++) {
                                    int index = i;
                                    VfxNativeSpriteBatcher.appendTransparentModel(
                                            pool.interpolatedPosition(index, partialTick),
                                            () -> {
                                                pool.renderIndex(index, poseStack, bufferSource,
                                                        camera, partialTick);
                                                bufferSource.endBatch();
                                            });
                                }
                                emitter.recordRenderer("Minecraft translucent model (globally sorted)");
                                modelInstanced = true; // submitted as ordered model draws, not instancing
                            } else if (pool.model() && VfxNativeModelRenderer.accepts(pool.definition())) {
                                if (!sharedBufferFlushed || queuedVanilla) {
                                    bufferSource.endBatch();
                                    sharedBufferFlushed = true;
                                    queuedVanilla = false;
                                }
                                modelInstanced = VfxNativeModelRenderer.renderDense(
                                        pool, poseStack, camera, partialTick);
                                if (modelInstanced) {
                                    emitter.recordRenderer("Native Minecraft GPU baked model (batched)");
                                }
                            }
                            if (!modelInstanced) {
                                pool.render(poseStack, bufferSource, camera, partialTick);
                                emitter.recordRenderer(pool.model()
                                        ? "Minecraft model RenderType (dense simulation)"
                                        : "Minecraft sprite RenderType (dense simulation)");
                                queuedVanilla |= pool.size() > 0;
                            }
                        }
                    }

                    if (!emitter.legacyParticles().isEmpty()) {
                        VfxNativeGpuGeometryBridge.flush();
                        if (gpuFrame) VfxNativeSpriteBatcher.flush();
                        var legacy = emitter.legacyParticles();
                        // Alpha from Legacy particle simulation must share
                        // the same globally sorted pass as Dense GPU alpha.
                        // Do not batch those particles by emitter or their
                        // relative depths can be inverted.
                        if (emitter.definition().render().type() == VfxRenderType.SPRITE
                                && emitter.definition().render().sprite().blendMode()
                                   == net.kofllee.pyrovfx.vfx.type.VfxBlendMode.ALPHA
                                && VfxNativeSpriteBatcher.matches(emitter.definition())) {
                            if (!gpuFrame) {
                                if (!sharedBufferFlushed || queuedVanilla) {
                                    bufferSource.endBatch();
                                    sharedBufferFlushed = true;
                                    queuedVanilla = false;
                                }
                                VfxNativeSpriteBatcher.begin(poseStack, camera, partialTick);
                                gpuFrame = true;
                            }
                            for (ClientVfxParticle particle : legacy)
                                VfxNativeSpriteBatcher.appendAlpha(particle);
                            emitter.recordRenderer("Native Minecraft alpha sprite batch (legacy CPU)");
                            continue;
                        }
                        boolean transparentModel = emitter.definition().render().type() == VfxRenderType.MODEL
                                && emitter.definition().render().model() != null
                                && emitter.definition().render().model().renderLayer()
                                    == net.kofllee.pyrovfx.vfx.type.VfxModelRenderLayer.TRANSLUCENT
                                && VfxNativeSpriteBatcher.compatibleEnvironment()
                                && VfxNativeSpriteBatcher.ready();
                        if (transparentModel) {
                            if (!gpuFrame) {
                                if (!sharedBufferFlushed || queuedVanilla) {
                                    bufferSource.endBatch();
                                    sharedBufferFlushed = true;
                                    queuedVanilla = false;
                                }
                                VfxNativeSpriteBatcher.begin(poseStack, camera, partialTick);
                                gpuFrame = true;
                            }
                            for (ClientVfxParticle particle : legacy) {
                                VfxNativeSpriteBatcher.appendTransparentModel(
                                        particle.interpolatedPosition(partialTick),
                                        () -> {
                                            ModelVfxParticleRenderer.render(particle, poseStack,
                                                    bufferSource, camera, partialTick);
                                            bufferSource.endBatch();
                                        });
                            }
                            emitter.recordRenderer("Minecraft translucent model (globally sorted)");
                            continue;
                        }
                        boolean modelInstanced = false;
                        if (VfxNativeModelRenderer.accepts(emitter.definition())) {
                            // Flush *all* Minecraft-managed geometry, including
                            // upstream submissions not owned by this renderer.
                            if (!sharedBufferFlushed || queuedVanilla) {
                                bufferSource.endBatch();
                                sharedBufferFlushed = true;
                                queuedVanilla = false;
                            }
                            modelInstanced = VfxNativeModelRenderer.render(
                                    legacy, poseStack, camera, partialTick);
                        }
                        if (modelInstanced) {
                            emitter.recordRenderer("Native Minecraft GPU baked model (legacy CPU)");
                            continue;
                        }
                        emitter.recordRenderer(emitter.definition().render().type() == VfxRenderType.MODEL
                                ? "Minecraft model RenderType (legacy CPU simulation)"
                                : "Minecraft sprite RenderType (legacy CPU simulation)");
                        for (ClientVfxParticle particle : legacy) {
                            if(particle.emitterDefinition().render().type() == VfxRenderType.SPRITE){
                                SpriteVfxParticleRenderer.render(
                                        particle, poseStack, bufferSource, camera, partialTick);
                            }
                            if (particle.emitterDefinition().render().type() == VfxRenderType.MODEL) {
                                ModelVfxParticleRenderer.render(
                                        particle, poseStack, bufferSource, camera, partialTick);
                            }
                        }
                        queuedVanilla = true;
                    }
                }
            }
        } finally {
            try {
                VfxNativeGpuGeometryBridge.flush();
                if (gpuFrame) {
                    // Minecraft's queued translucent geometry must land before
                    // the depth-sorted Pyro alpha pass.
                    if (queuedVanilla) {
                        bufferSource.endBatch();
                        queuedVanilla = false;
                    }
                    VfxNativeSpriteBatcher.finish();
                }
                if (!sharedBufferFlushed || queuedVanilla) bufferSource.endBatch();
            } finally {
                try {
                    if (telemetryFrame) VfxGpuTelemetry.end();
                } finally {
                    ClientVfxManager.endRender();
                }
            }
        }
    }
}
