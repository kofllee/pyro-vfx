package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.kofllee.pyrovfx.vfx.type.VfxModelRenderLayer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class ModelVfxParticleRenderer {
    private ModelVfxParticleRenderer() {}

    public static void render(
            ClientVfxParticle particle,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Camera camera,
            float partialTick
    ){
        if (particle.emitterDefinition().render().model() == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        ResourceLocation modelId = particle.emitterDefinition().render().model().model();

        BakedModel model = minecraft.getModelManager().getModel(
                ModelResourceLocation.standalone(modelId)
        );

        Vec3 cameraPos = camera.getPosition();
        Vec3 particlePos = particle.interpolatedPosition(partialTick);

        double x = particlePos.x - cameraPos.x;
        double y = particlePos.y - cameraPos.y;
        double z = particlePos.z - cameraPos.z;

        Vec3 scale = particle.interpolatedScale(partialTick);
        Vec3 rotation = particle.interpolatedRotation(partialTick);

        int light = particle.emitterDefinition().render().environmentLighting()
                ? getEnvironmentLight(particle, partialTick)
                : LightTexture.FULL_BRIGHT;

        poseStack.pushPose();

        poseStack.translate(x, y, z);

        poseStack.mulPose(Axis.XP.rotationDegrees((float) rotation.x));
        poseStack.mulPose(Axis.YP.rotationDegrees((float) rotation.y));
        poseStack.mulPose(Axis.ZP.rotationDegrees((float) rotation.z));

        poseStack.scale((float) scale.x, (float) scale.y, (float) scale.z);

        poseStack.translate(-0.5F, -0.5F, -0.5F);
        BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();

        RenderType renderType = getRenderType(
                particle.emitterDefinition().render().model().renderLayer()
        );

        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        blockRenderer.getModelRenderer().renderModel(
                poseStack.last(),
                consumer,
                null,
                model,
                1.0F,
                1.0F,
                1.0F,
                light,
                OverlayTexture.NO_OVERLAY
        );

        poseStack.popPose();
    }

    private static int getEnvironmentLight(ClientVfxParticle particle, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();

        if(minecraft.level  == null) {
            return LightTexture.FULL_BRIGHT;
        }

        Vec3 position = particle.interpolatedPosition(partialTick);
        BlockPos blockPos = BlockPos.containing(position.x, position.y, position.z);

        return LevelRenderer.getLightColor(minecraft.level, blockPos);
    }

    private static RenderType getRenderType(VfxModelRenderLayer layer) {
        return switch (layer) {
            case SOLID -> net.minecraft.client.renderer.Sheets.solidBlockSheet();
            case CUTOUT -> net.minecraft.client.renderer.Sheets.cutoutBlockSheet();
            case TRANSLUCENT -> net.minecraft.client.renderer.Sheets.translucentCullBlockSheet();
        };
    }
}
