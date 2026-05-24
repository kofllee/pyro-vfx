package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.kofllee.pyrovfx.vfx.value.VfxColor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ItemModelVfxParticleRenderer {
    private ItemModelVfxParticleRenderer() {}

    private static final Map<ResourceLocation, ItemStack> ITEM_STACK_CACHE = new HashMap<>();

    public static void render(
            ClientVfxParticle particle,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Camera camera,
            float partialTick
    ) {
        var modelDefinition = particle.emitterDefinition().render().model();

        ItemStack stack = ITEM_STACK_CACHE.computeIfAbsent(
                modelDefinition.model(),
                id -> new ItemStack(BuiltInRegistries.ITEM.get(id))
        );

        Minecraft minecraft = Minecraft.getInstance();
        int light = particle.emitterDefinition().render().environmentLighting()
                    ? VfxRenderLight.getParticleLight(particle, partialTick)
                    : LightTexture.FULL_BRIGHT;

        RenderType renderType = VfxModelRenderTypes.get(modelDefinition.renderLayer());
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        BakedModel model = minecraft.getItemRenderer().getModel(
                stack,
                minecraft.level,
                null,
                0
        );

        VfxColor color = particle.interpolatedColor(partialTick);

        poseStack.pushPose();

        VfxRenderTransforms.applyParticleTransform(
                poseStack,
                particle,
                camera,
                partialTick
        );

        poseStack.translate(-0.5F, -0.5F, -0.5F);

        renderItemBakedModel(
                model,
                poseStack,
                consumer,
                color,
                light,
                renderType
        );

        poseStack.popPose();
    }

    private static void renderItemBakedModel(BakedModel model, PoseStack poseStack, VertexConsumer consumer, VfxColor color, int light, RenderType renderType) {
        RandomSource random = RandomSource.create(42L);

        for (Direction direction : Direction.values()) {
            random.setSeed(42L);

            renderQuads(
                    model.getQuads(null, direction, random, ModelData.EMPTY, renderType),
                    poseStack,
                    consumer,
                    color,
                    light
            );
        }

        random.setSeed(42L);

        renderQuads(
                model.getQuads(null, null, random, ModelData.EMPTY, renderType),
                poseStack,
                consumer,
                color,
                light
        );
    }

    private static void renderQuads(List<BakedQuad> quads, PoseStack poseStack, VertexConsumer consumer, VfxColor color, int light) {
        PoseStack.Pose pose = poseStack.last();

        float r = (float) color.r();
        float g = (float) color.g();
        float b = (float) color.b();
        float a = (float) color.a();

        float[] brightness = new float[]{1.0F, 1.0F, 1.0F, 1.0F};
        int[] lights = new int[]{light, light, light, light};

        for (BakedQuad quad : quads) {
            consumer.putBulkData(
                    pose,
                    quad,
                    brightness,
                    r,
                    g,
                    b,
                    a,
                    lights,
                    OverlayTexture.NO_OVERLAY,
                    false
            );
        }
    }
}
