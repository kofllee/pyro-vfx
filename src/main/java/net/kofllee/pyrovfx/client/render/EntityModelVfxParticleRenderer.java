package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.IdentityHashMap;
import java.util.Map;

public final class EntityModelVfxParticleRenderer {
    private EntityModelVfxParticleRenderer() {}

    private static final Map<EntityType<?>, Entity> CACHE = new IdentityHashMap<>();
    private static ClientLevel cachedLevel;

    public static void render(
            ClientVfxParticle particle,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Camera camera,
            float partialTick
    ) {
        var modelDefinition = particle.emitterDefinition().render().model();

        Minecraft minecraft = Minecraft.getInstance();

        if (cachedLevel != minecraft.level) {
            CACHE.clear();
            cachedLevel = minecraft.level;
        }

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(modelDefinition.model());
        Entity entity = CACHE.computeIfAbsent(entityType, type -> type.create(cachedLevel));


        if(entity == null) {
            return;
        }

        entity.tickCount = particle.age();
        entity.setPos(0.0, 0.0, 0.0);

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
