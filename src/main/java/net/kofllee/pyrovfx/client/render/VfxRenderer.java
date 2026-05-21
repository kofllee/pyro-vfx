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

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

        for(ClientVfxInstance instance: ClientVfxManager.instances()){
            for(ClientVfxParticle particle: instance.particles()){
                if(particle.emitterDefinition().render().type() == VfxRenderType.SPRITE){
                    SpriteVfxParticleRenderer.render(
                            particle,
                            poseStack,
                            bufferSource,
                            camera,
                            partialTick
                    );
                }


                if (particle.emitterDefinition().render().type() == VfxRenderType.MODEL) {
                    ModelVfxParticleRenderer.render(
                            particle,
                            poseStack,
                            bufferSource,
                            camera,
                            partialTick
                    );
                }
            }
        }

        bufferSource.endBatch();
    }
}
