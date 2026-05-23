package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.kofllee.pyrovfx.vfx.definition.VfxModelRenderDefinition;
import net.kofllee.pyrovfx.vfx.value.VfxColor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.List;

public final class BlockModelVfxParticleRenderer {
    private BlockModelVfxParticleRenderer() {
    }

    public static void render(
            ClientVfxParticle particle,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Camera camera,
            float partialTick
    ) {
        var modelDefinition = particle.emitterDefinition().render().model();

        BlockState state = resolveBlockState(modelDefinition);

        Minecraft minecraft = Minecraft.getInstance();

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

        poseStack.translate(-0.5F, -0.5F, -0.5F);

        RenderType renderType = VfxModelRenderTypes.get(modelDefinition.renderLayer());
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        BakedModel bakedModel = minecraft.getBlockRenderer().getBlockModel(state);
        VfxColor color = particle.interpolatedColor(partialTick);

        renderBakedBlockModel(
                minecraft,
                state,
                bakedModel,
                poseStack,
                consumer,
                color,
                light,
                particle.interpolatedPosition(partialTick),
                renderType
        );

        poseStack.popPose();
    }

    private static void renderBakedBlockModel(
            Minecraft minecraft,
            BlockState state,
            BakedModel model,
            PoseStack poseStack,
            VertexConsumer consumer,
            VfxColor color,
            int light,
            Vec3 particlePosition,
            RenderType renderType
    ) {
        RandomSource random = RandomSource.create(42L);

        for(Direction direction : Direction.values()) {
            random.setSeed(42L);

            renderQuads(
                    minecraft,
                    state,
                    model.getQuads(state, direction, random, ModelData.EMPTY, renderType),
                    poseStack,
                    consumer,
                    color,
                    light,
                    particlePosition
            );
        }

        random.setSeed(42L);
        renderQuads(
                minecraft,
                state,
                model.getQuads(state, null, random, ModelData.EMPTY, renderType),
                poseStack,
                consumer,
                color,
                light,
                particlePosition
        );
    }

    private static void renderQuads(
            Minecraft minecraft,
            BlockState state,
            List<BakedQuad> quads,
            PoseStack poseStack,
            VertexConsumer consumer,
            VfxColor color,
            int light,
            Vec3 particlePosition
    ) {
        PoseStack.Pose pose = poseStack.last();

        for(BakedQuad quad : quads) {
            float r = (float) color.r();
            float g = (float) color.g();
            float b = (float) color.b();
            float a = (float) color.a();

            if(quad.isTinted() && minecraft.level != null){
                int tint = minecraft.getBlockColors().getColor(state, minecraft.level, BlockPos.containing(particlePosition), quad.getTintIndex());

                float tintR = ((tint >> 16) & 255) / 255.0F;
                float tintG = ((tint >> 8) & 255) / 255.0F;
                float tintB = (tint & 255) / 255.0F;

                r *= tintR;
                g *= tintG;
                b *= tintB;
            }

            float[] brightness = new float[] {1.0F, 1.0F, 1.0F, 1.0F};
            int[] lights = new int[] {light, light, light, light};

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

    private static BlockState resolveBlockState(VfxModelRenderDefinition modelDefinition) {
        if (modelDefinition.blockState() == null || modelDefinition.blockState().isBlank()) {
            Block block = BuiltInRegistries.BLOCK.get(modelDefinition.model());
            return block.defaultBlockState();
        }

        try {
            BlockStateParser.BlockResult result = BlockStateParser.parseForBlock(
                    BuiltInRegistries.BLOCK.asLookup(),
                    modelDefinition.blockState(),
                    false
            );

            return result.blockState();

        } catch (CommandSyntaxException e) {
            ResourceLocation fallbackId = ResourceLocation.parse(modelDefinition.blockState().split("\\[", 2)[0]);
            Block fallbackBlock = BuiltInRegistries.BLOCK.get(fallbackId);
            return fallbackBlock.defaultBlockState();
        }
    }
}
