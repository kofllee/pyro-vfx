package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.kofllee.pyrovfx.vfx.value.VfxColor;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class SpriteVfxParticleRenderer {
    private SpriteVfxParticleRenderer() {}

    public static void render(
            ClientVfxParticle particle,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Camera camera,
            float partialTick
    ){
        if(particle.emitterDefinition().render().sprite() == null)
            return;

        Vec3 cameraPosition = camera.getPosition();
        Vec3 particlePosition = particle.position();

        double x = particlePosition.x - cameraPosition.x;
        double y = particlePosition.y - cameraPosition.y;
        double z = particlePosition.z - cameraPosition.z;

        float size = (float) particle.size();

        if(size <= 0)
            return;

        VfxColor color = particle.color();

        int r = toColorByte(color.r());
        int g = toColorByte(color.g());
        int b = toColorByte(color.b());
        int a = toColorByte(color.a());

        if(a <= 0)
            return;

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(particle.emitterDefinition().render().sprite().texture()));

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(camera.rotation());


        Matrix4f matrix = poseStack.last().pose();
        float halfSize = size * 0.5f;

        vertex(consumer, matrix, -halfSize, -halfSize, 0.0F, 0.0F, 1.0F, r, g, b, a);
        vertex(consumer, matrix,  halfSize, -halfSize, 0.0F, 1.0F, 1.0F, r, g, b, a);
        vertex(consumer, matrix,  halfSize,  halfSize, 0.0F, 1.0F, 0.0F, r, g, b, a);
        vertex(consumer, matrix, -halfSize,  halfSize, 0.0F, 0.0F, 0.0F, r, g, b, a);

        poseStack.popPose();
    }

    private static void vertex(
            VertexConsumer consumer,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            float u,
            float v,
            int r,
            int g,
            int b,
            int a
    ) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private static int toColorByte(double value) {
        value = Math.clamp(value, 0.0, 1.0);

        return (int) (value * 255);
    }
}
