package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

public final class VfxRenderTransforms {
    private VfxRenderTransforms() {}

    public static void applyParticleTransform(
            PoseStack poseStack,
            ClientVfxParticle particle,
            Camera camera,
            float partialTick
    ) {
        Vec3 cameraPos = camera.getPosition();
        Vec3 particlePos = particle.interpolatedPosition(partialTick);
        Vec3 scale = particle.interpolatedScale(partialTick);
        Vec3 rotation = particle.interpolatedRotation(partialTick);

        poseStack.translate(
                particlePos.x - cameraPos.x,
                particlePos.y - cameraPos.y,
                particlePos.z - cameraPos.z
        );

        poseStack.mulPose(Axis.XP.rotationDegrees((float) rotation.x));
        poseStack.mulPose(Axis.YP.rotationDegrees((float) rotation.y));
        poseStack.mulPose(Axis.ZP.rotationDegrees((float) rotation.z));

        poseStack.scale((float) scale.x, (float) scale.y, (float) scale.z);
    }
}
