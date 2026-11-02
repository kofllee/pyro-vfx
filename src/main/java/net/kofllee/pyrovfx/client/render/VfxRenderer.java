package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kofllee.pyrovfx.client.vfx.ClientVfxInstance;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.kofllee.pyrovfx.vfx.type.VfxRenderType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

public final class VfxRenderer {
    private VfxRenderer() {}

    public static void render(PoseStack poseStack, Camera camera, float partialTick){
        VfxRenderLight.beginFrame();
        Minecraft minecraft = Minecraft.getInstance();
        // Do not start expensive GL driver timer queries in ordinary worlds
        // where Pyro has nothing to render.
        boolean telemetryFrame = ClientVfxManager.getActiveParticleCount() > 0;
        if (telemetryFrame) VfxGpuTelemetry.begin();
        VfxInstancedSpriteRenderer.resetFrameStats();
        VfxInstancedModelRenderer.resetFrameStats();
        VfxAnalyticSpriteRenderer.resetFrameStats();
        VfxAnalyticSpriteRenderer.maintain();

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

        boolean gpuFrame = false;
        boolean queuedVanilla = false;
        try {
            for(ClientVfxInstance instance: ClientVfxManager.instances()){
                for(var emitter : instance.emitters()) {
                    for (var analytic : emitter.analyticPools()) {
                        if (analytic.liveCount() == 0) continue;
                        if (gpuFrame) VfxInstancedSpriteRenderer.flush();
                        bufferSource.endBatch();
                        queuedVanilla = false;
                        boolean rendered = analytic.model()
                                ? VfxInstancedModelRenderer.renderAnalytic(analytic, poseStack, camera, partialTick)
                                : VfxAnalyticSpriteRenderer.render(analytic, poseStack, camera, partialTick);
                        if (!rendered) {
                            emitter.recordRenderer("Minecraft sprite fallback (analytic GPU unavailable)");
                            analytic.renderFallback(poseStack, bufferSource, camera, partialTick);
                            queuedVanilla = true;
                        } else {
                            emitter.recordRenderer(analytic.model()
                                    ? "GPU analytic model (resident birth data)"
                                    : "GPU analytic sprite (resident birth data)");
                        }
                    }
                    var pool = emitter.densePool();
                    if (pool != null && pool.size() > 0
                            && VfxInstancedSpriteRenderer.matches(pool.definition())) {
                        // World vertex buffers must flush before raw GL draws,
                        // preserving Minecraft's RenderType ordering.
                        if (queuedVanilla || !gpuFrame) {
                            // The shared BufferSource may also contain work
                            // submitted by another mod before our event.
                            bufferSource.endBatch();
                            queuedVanilla = false;
                        }
                        if (!gpuFrame) {
                            VfxInstancedSpriteRenderer.begin(poseStack, camera, partialTick);
                            gpuFrame = true;
                        }
                        VfxInstancedSpriteRenderer.append(pool);
                        emitter.recordRenderer("GPU instanced sprite (per-frame instance upload)");
                    } else {
                        if (gpuFrame) VfxInstancedSpriteRenderer.flush();
                        if (pool != null) {
                            boolean modelInstanced = false;
                            if (pool.model() && VfxInstancedModelRenderer.accepts(pool.definition())) {
                                bufferSource.endBatch();
                                queuedVanilla = false;
                                modelInstanced = VfxInstancedModelRenderer.renderDense(
                                        pool, poseStack, camera, partialTick);
                                if (modelInstanced) {
                                    emitter.recordRenderer("GPU instanced model (per-frame instance upload)");
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
                        if (gpuFrame) VfxInstancedSpriteRenderer.flush();
                        var legacy = emitter.legacyParticles();
                        boolean modelInstanced = false;
                        if (VfxInstancedModelRenderer.accepts(emitter.definition())) {
                            // Flush *all* Minecraft-managed geometry, including
                            // upstream submissions not owned by this renderer.
                            bufferSource.endBatch();
                            queuedVanilla = false;
                            modelInstanced = VfxInstancedModelRenderer.render(
                                    legacy, poseStack, camera, partialTick);
                        }
                        if (modelInstanced) {
                            emitter.recordRenderer("GPU instanced model (legacy CPU simulation)");
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
                if (gpuFrame) VfxInstancedSpriteRenderer.finish();
                bufferSource.endBatch();
            } finally {
                if (telemetryFrame) VfxGpuTelemetry.end();
            }
        }
    }
}
