package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public final class EntityModelVfxParticleRenderer {
    private EntityModelVfxParticleRenderer() {}

    public static void render(
            ClientVfxParticle particle,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Camera camera,
            float partialTick
    ) {
        var modelDefinition = particle.emitterDefinition().render().model();

        Minecraft minecraft = Minecraft.getInstance();

        if(minecraft.level == null) {
            return;
        }

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(modelDefinition.model());
        Entity entity = entityType.create(minecraft.level);

        if(entity == null) {
            return;
        }

        int light = particle.emitterDefinition().render().environmentLighting()
                ? VfxRenderLight.getParticleLight(particle, partialTick)
                : LightTexture.FULL_BRIGHT;

        poseStack.pushPose();

        VfxRenderTransforms.applyParticleTransform(
                poseStack,
                particle,
                camera,
                partialTick
        );

        minecraft.getEntityRenderDispatcher().render(
                entity,
                0.0,
                0.0,
                0.0,
                0.0F,
                partialTick,
                poseStack,
                bufferSource,
                light
        );

        poseStack.popPose();
    }
}
