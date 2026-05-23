package net.kofllee.pyrovfx.client.render;

import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class VfxRenderLight {
    private VfxRenderLight() {}

    public static int getParticleLight(ClientVfxParticle particle, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return LightTexture.FULL_BRIGHT;
        }

        Vec3 position = particle.interpolatedPosition(partialTick);
        BlockPos blockPos = BlockPos.containing(position.x, position.y, position.z);

        return LevelRenderer.getLightColor(minecraft.level, blockPos);
    }
}
