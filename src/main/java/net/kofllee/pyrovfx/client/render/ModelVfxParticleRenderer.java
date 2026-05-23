package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.kofllee.pyrovfx.vfx.definition.VfxModelRenderDefinition;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;


public final class ModelVfxParticleRenderer {
    private ModelVfxParticleRenderer() {}

    public static void render(
            ClientVfxParticle particle,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Camera camera,
            float partialTick
    ){
        VfxModelRenderDefinition model = particle.emitterDefinition().render().model();

        if(model == null) return;

        switch (model.source()) {
            case CUSTOM -> CustomModelVfxParticleRenderer.render(particle, poseStack, bufferSource, camera, partialTick);
            /*case BLOCK -> BlockModelVfxParticleRenderer.render(particle, poseStack, bufferSource, camera, partialTick);
            case ITEM -> ItemModelVfxParticleRenderer.render(particle, poseStack, bufferSource, camera, partialTick);
            case ENTITY -> EntityModelVfxParticleRenderer.render(particle, poseStack, bufferSource, camera, partialTick);*/
        }
    }
}
