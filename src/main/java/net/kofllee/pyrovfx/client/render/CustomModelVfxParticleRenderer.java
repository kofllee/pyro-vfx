package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.kofllee.pyrovfx.vfx.type.VfxModelSourceType;
import net.kofllee.pyrovfx.vfx.value.VfxColor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.List;

public final class CustomModelVfxParticleRenderer {
    private CustomModelVfxParticleRenderer() {}

    public static void render(
            ClientVfxParticle particle,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Camera camera,
            float partialTick
    ){
        var modelDefinition = particle.emitterDefinition().render().model();

        if (modelDefinition == null || modelDefinition.source() != VfxModelSourceType.CUSTOM) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        BakedModel model = minecraft.getModelManager().getModel(
                ModelResourceLocation.standalone(modelDefinition.model())
        );

        VfxColor color = particle.interpolatedColor(partialTick);

        int light = particle.emitterDefinition().render().environmentLighting()
                ? VfxRenderLight.getParticleLight(particle, partialTick)
                : LightTexture.FULL_BRIGHT;

        RenderType renderType = VfxModelRenderTypes.get(modelDefinition.renderLayer());
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        poseStack.pushPose();

        VfxRenderTransforms.applyParticleTransform(
                poseStack,
                particle,
                camera,
                partialTick
        );

        poseStack.translate(-0.5F, -0.5F, -0.5F);

        renderBakedModelQuads(
                model,
                poseStack,
                consumer,
                color,
                light,
                renderType
        );

        poseStack.popPose();
    }

    private static void renderBakedModelQuads(
            BakedModel model,
            PoseStack poseStack,
            VertexConsumer consumer,
            VfxColor color,
            int light,
            RenderType renderType
    ) {
        RandomSource random = RandomSource.create(42L);

        for (Direction direction : Direction.values()) {
            renderQuads(
                    model.getQuads(null, direction, random, ModelData.EMPTY, renderType),
                    poseStack,
                    consumer,
                    color,
                    light
            );
        }

        renderQuads(
                model.getQuads(null, null, random, ModelData.EMPTY, renderType),
                poseStack,
                consumer,
                color,
                light
        );
    }
    private static void renderQuads(
            List<BakedQuad> quads,
            PoseStack poseStack,
            VertexConsumer consumer,
            VfxColor color,
            int light
    ) {
        PoseStack.Pose pose = poseStack.last();

        for (BakedQuad quad : quads) {
            consumer.putBulkData(
                    pose,
                    quad,
                    (float) color.r(),
                    (float) color.g(),
                    (float) color.b(),
                    (float) color.a(),
                    light,
                    OverlayTexture.NO_OVERLAY
            );
        }
    }
}
