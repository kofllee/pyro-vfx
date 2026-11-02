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
                    var analytic = emitter.analyticPool();
                    if (analytic != null && analytic.liveCount() > 0) {
                        if (gpuFrame) VfxInstancedSpriteRenderer.flush();
                        bufferSource.endBatch();
                        queuedVanilla = false;
                        if (!VfxAnalyticSpriteRenderer.render(analytic, poseStack, camera, partialTick)) {
                            analytic.renderFallback(poseStack, bufferSource, camera, partialTick);
                            queuedVanilla = true;
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
                    } else {
                        if (gpuFrame) VfxInstancedSpriteRenderer.flush();
                        if (pool != null) {
                            pool.render(poseStack, bufferSource, camera, partialTick);
                            queuedVanilla |= pool.size() > 0;
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
                        if (modelInstanced) continue;
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
