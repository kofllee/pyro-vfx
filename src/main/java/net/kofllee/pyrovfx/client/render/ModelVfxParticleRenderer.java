package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.kofllee.pyrovfx.client.vfx.VfxSpriteParticleView;
import net.kofllee.pyrovfx.vfx.definition.VfxModelRenderDefinition;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;


public final class ModelVfxParticleRenderer {
    private ModelVfxParticleRenderer() {}

    public static void render(
            VfxSpriteParticleView particle,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Camera camera,
            float partialTick
    ){
        VfxModelRenderDefinition model = particle.emitterDefinition().render().model();

        if(model == null) return;

        switch (model.source()) {
            case CUSTOM -> CustomModelVfxParticleRenderer.render(particle, poseStack, bufferSource, camera, partialTick);
            case BLOCK -> BlockModelVfxParticleRenderer.render(particle, poseStack, bufferSource, camera, partialTick);
            case ITEM -> ItemModelVfxParticleRenderer.render(particle, poseStack, bufferSource, camera, partialTick);
            // Entity models keep a distinct renderer-owned dummy Entity per
            // legacy particle. Dense deliberately never admits ENTITY models.
            case ENTITY -> {
                if (particle instanceof ClientVfxParticle legacy)
                    EntityModelVfxParticleRenderer.render(legacy, poseStack, bufferSource, camera, partialTick);
            }
        }
    }
}
