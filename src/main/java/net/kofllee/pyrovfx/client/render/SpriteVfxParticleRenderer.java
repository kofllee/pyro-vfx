package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.kofllee.pyrovfx.vfx.type.VfxFacingMode;
import net.kofllee.pyrovfx.vfx.value.VfxColor;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

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
        Vec3 particlePosition = particle.interpolatedPosition(partialTick);

        double x = particlePosition.x - cameraPosition.x;
        double y = particlePosition.y - cameraPosition.y;
        double z = particlePosition.z - cameraPosition.z;

        Vec3 scale = particle.interpolatedScale(partialTick);

        float width = (float) scale.x;
        float height = (float) scale.y;

        VfxColor color = particle.interpolatedColor(partialTick);

        int r = toColorByte(color.r());
        int g = toColorByte(color.g());
        int b = toColorByte(color.b());
        int a = toColorByte(color.a());

        if(a <= 0)
            return;

        VertexConsumer consumer = bufferSource.getBuffer(VfxSpriteRenderTypes.get(
                particle.emitterDefinition().render().sprite().texture(),
                particle.emitterDefinition().render().sprite().blendMode()
        ));

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        applyFacing(particle, poseStack, camera, partialTick);
        applyParticleRotation(particle, poseStack, partialTick);

        Matrix4f matrix = poseStack.last().pose();

        float halfWidth = width * 0.5F;
        float halfHeight = height * 0.5F;

        int light = particle.emitterDefinition().render().environmentLighting()
                ? VfxRenderLight.getParticleLight(particle, partialTick)
                : LightTexture.FULL_BRIGHT;

        VfxSpriteUvRect uv = VfxSpriteUvSampler.sample(particle);

        vertex(consumer, matrix, -halfWidth, -halfHeight, 0.0F, uv.u0(), uv.v1(), r, g, b, a, light);
        vertex(consumer, matrix,  halfWidth, -halfHeight, 0.0F, uv.u1(), uv.v1(), r, g, b, a, light);
        vertex(consumer, matrix,  halfWidth,  halfHeight, 0.0F, uv.u1(), uv.v0(), r, g, b, a, light);
        vertex(consumer, matrix, -halfWidth,  halfHeight, 0.0F, uv.u0(), uv.v0(), r, g, b, a, light);

        poseStack.popPose();
    }

    private static void applyParticleRotation(ClientVfxParticle particle, PoseStack poseStack, float partialTick) {
        Vec3 rotation = particle.interpolatedRotation(partialTick);

        poseStack.mulPose(Axis.XP.rotationDegrees((float) rotation.x));
        poseStack.mulPose(Axis.YP.rotationDegrees((float) rotation.y));
        poseStack.mulPose(Axis.ZP.rotationDegrees((float) rotation.z));
    }

    private static void applyFacing(ClientVfxParticle particle, PoseStack poseStack, Camera camera, float partialTick) {
        VfxFacingMode mode = particle.emitterDefinition().render().facing();

        if(mode == VfxFacingMode.CAMERA) {
            poseStack.mulPose(camera.rotation());
            return;
        }

        if (mode == VfxFacingMode.CAMERA_HORIZONTAL) {
            poseStack.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
            return;
        }

        if(mode == VfxFacingMode.WORLD_X){
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            return;
        }

        if(mode == VfxFacingMode.WORLD_Y){
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            return;
        }

        if(mode == VfxFacingMode.WORLD_Z){
            return;
        }

        if(mode == VfxFacingMode.VELOCITY){
            Vec3 velocity = particle.velocity();

            if(velocity.lengthSqr() < 1e-6) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                return;
            }

            applyNormalFacing(poseStack, velocity.normalize());
            return;
        }
    }

    private static void applyNormalFacing(PoseStack poseStack, Vec3 normal) {
        Vec3 from = new Vec3(0.0, 0.0, 1.0);
        Vec3 to = normal.normalize();

        double dot = from.dot(to);

        poseStack.mulPose(new Quaternionf().rotationTo(
                0.0F, 0.0F, 1.0F,
                (float) to.x, (float) to.y, (float) to.z
        ));
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
            int a,
            int light
    ) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private static int toColorByte(double value) {
        value = Math.clamp(value, 0.0, 1.0);

        return (int) (value * 255);
    }
}
